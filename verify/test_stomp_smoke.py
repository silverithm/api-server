#!/usr/bin/env python3
"""stomp_smoke.py 자체 단위 테스트 — 표준 라이브러리 unittest만 쓴다(네트워크 없이 실행).

    python3 verify/test_stomp_smoke.py
"""

import base64
import hashlib
import hmac
import json
import socket
import struct
import threading
import unittest

import stomp_smoke as m


class FakeSocket:
    """RawWebSocket이 기대하는 recv()/sendall()만 흉내 낸다."""

    def __init__(self, incoming: bytes = b""):
        self._incoming = incoming
        self.sent = b""

    def recv(self, n: int) -> bytes:
        chunk, self._incoming = self._incoming[:n], self._incoming[n:]
        return chunk

    def sendall(self, data: bytes) -> None:
        self.sent += data

    def settimeout(self, *_):
        pass


def make_ws_with_fake_socket(incoming: bytes = b"") -> m.RawWebSocket:
    ws = object.__new__(m.RawWebSocket)
    ws.sock = FakeSocket(incoming)
    return ws


def encode_server_text_frame(text: str) -> bytes:
    """서버->클라이언트 프레임(마스크 없음)을 손으로 인코딩한다 — recv_frame 테스트용."""
    payload = text.encode("utf-8")
    length = len(payload)
    header = bytearray([0x80 | 0x1])
    if length < 126:
        header.append(length)
    elif length < 65536:
        header.append(126)
        header += struct.pack(">H", length)
    else:
        header.append(127)
        header += struct.pack(">Q", length)
    return bytes(header) + payload


class SecretDecodingTest(unittest.TestCase):
    def test_standard_base64_roundtrip(self):
        raw = b"0123456789abcdef0123456789abcdef"
        encoded = base64.b64encode(raw).decode()
        self.assertEqual(m._decode_secret(encoded), raw)

    def test_missing_padding_is_tolerated(self):
        raw = b"0123456789abcdef0123456789abcdef"
        encoded = base64.b64encode(raw).decode().rstrip("=")
        self.assertEqual(m._decode_secret(encoded), raw)


class JwtMintingTest(unittest.TestCase):
    def setUp(self):
        self.key = hashlib.sha256(b"test-key-for-unit-tests").digest()
        self.subject = "unit-test@internal"

    def _parts(self, token: str):
        h, p, s = token.split(".")

        def b64d(x: str) -> bytes:
            pad = "=" * (-len(x) % 4)
            return base64.urlsafe_b64decode(x + pad)

        return json.loads(b64d(h)), json.loads(b64d(p)), b64d(s)

    def test_valid_token_structure_and_signature(self):
        token = m.mint_jwt(self.key, self.subject, expired=False, ttl_sec=1800)
        header, payload, sig = self._parts(token)

        self.assertEqual(header, {"alg": "HS256", "typ": "JWT"})
        self.assertEqual(payload["sub"], self.subject)
        self.assertIn("auth", payload)
        self.assertGreater(payload["exp"], payload["iat"])

        signing_input = token.rsplit(".", 1)[0]
        expected_sig = hmac.new(self.key, signing_input.encode("ascii"), hashlib.sha256).digest()
        self.assertEqual(sig, expected_sig)

    def test_expired_token_has_exp_in_past(self):
        token = m.mint_jwt(self.key, self.subject, expired=True)
        _, payload, _ = self._parts(token)
        self.assertLess(payload["exp"], payload["iat"])

    def test_forged_token_uses_different_key(self):
        genuine = m.mint_jwt(self.key, self.subject, expired=False)
        forged = m.mint_forged_jwt(self.subject)

        # 같은 subject를 담아도 서명이 달라야 한다(다른 키로 서명했으므로).
        g_header, g_payload, g_sig = self._parts(genuine)
        f_header, f_payload, f_sig = self._parts(forged)
        self.assertEqual(g_payload["sub"], f_payload["sub"])
        self.assertNotEqual(g_sig, f_sig)

        # 진짜 키로 재서명한 결과와도 다르다는 것을 직접 확인한다.
        forged_signing_input = forged.rsplit(".", 1)[0]
        recomputed_with_real_key = hmac.new(
            self.key, forged_signing_input.encode("ascii"), hashlib.sha256
        ).digest()
        self.assertNotEqual(f_sig, recomputed_with_real_key)


class StompFrameBuildingTest(unittest.TestCase):
    def test_connect_frame_with_token(self):
        frame = m.build_connect_frame("abc.def.ghi")
        self.assertTrue(frame.startswith("CONNECT\n"))
        self.assertIn("Authorization:Bearer abc.def.ghi\n", frame)
        self.assertTrue(frame.endswith("\n\n\x00"))

    def test_connect_frame_without_token_omits_header(self):
        frame = m.build_connect_frame(None)
        self.assertNotIn("Authorization", frame)
        self.assertTrue(frame.endswith("\n\n\x00"))


class WebSocketFramingTest(unittest.TestCase):
    def test_send_text_masks_payload_and_sets_length(self):
        ws = make_ws_with_fake_socket()
        ws.send_text("hello")
        sent = ws.sock.sent

        self.assertEqual(sent[0], 0x81)  # FIN + text opcode
        self.assertTrue(sent[1] & 0x80)  # 클라이언트->서버는 반드시 마스크
        length = sent[1] & 0x7F
        self.assertEqual(length, 5)
        mask_key = sent[2:6]
        masked_payload = sent[6:]
        unmasked = bytes(b ^ mask_key[i % 4] for i, b in enumerate(masked_payload))
        self.assertEqual(unmasked, b"hello")

    def test_send_text_extended_length_126(self):
        ws = make_ws_with_fake_socket()
        payload = "x" * 200
        ws.send_text(payload)
        sent = ws.sock.sent
        self.assertEqual(sent[1] & 0x7F, 126)
        declared_len = struct.unpack(">H", sent[2:4])[0]
        self.assertEqual(declared_len, 200)

    def test_recv_frame_parses_small_unmasked_text_frame(self):
        raw = encode_server_text_frame("CONNECTED\nversion:1.2\n\n\x00")
        ws = make_ws_with_fake_socket(raw)
        opcode, payload = ws.recv_frame()
        self.assertEqual(opcode, 0x1)
        self.assertTrue(payload.decode("utf-8").startswith("CONNECTED"))

    def test_recv_frame_parses_extended_length_frame(self):
        text = "ERROR\nmessage:401 Unauthorized\n\n" + ("y" * 300) + "\x00"
        raw = encode_server_text_frame(text)
        ws = make_ws_with_fake_socket(raw)
        opcode, payload = ws.recv_frame()
        self.assertEqual(opcode, 0x1)
        self.assertEqual(payload.decode("utf-8"), text)

    def test_recv_frame_returns_none_on_closed_connection(self):
        ws = make_ws_with_fake_socket(b"")
        self.assertIsNone(ws.recv_frame())


class ScenarioExpectationsTest(unittest.TestCase):
    """run_scenario의 판정 로직만 검증한다(네트워크 없이) — stomp_connect_and_get_response를 스텁으로 바꾼다."""

    def setUp(self):
        self.key = hashlib.sha256(b"scenario-test-key").digest()
        self._orig = m.stomp_connect_and_get_response

    def tearDown(self):
        m.stomp_connect_and_get_response = self._orig

    def _stub(self, response_text):
        def fake(host, port, path, token, timeout):
            return response_text

        m.stomp_connect_and_get_response = fake

    def test_valid_scenario_pass_when_connected(self):
        self._stub("CONNECTED\nversion:1.2\n\n\x00")
        ok, expect, actual, _ = m.run_scenario("valid", "h", 1, "/p", self.key, "sub", 1.0)
        self.assertTrue(ok)
        self.assertEqual(expect, "CONNECTED")
        self.assertEqual(actual, "CONNECTED")

    def test_forged_scenario_fails_if_server_connects_anyway(self):
        self._stub("CONNECTED\nversion:1.2\n\n\x00")
        ok, expect, actual, _ = m.run_scenario("forged", "h", 1, "/p", self.key, "sub", 1.0)
        self.assertFalse(ok)
        self.assertEqual(expect, "ERROR")
        self.assertEqual(actual, "CONNECTED")

    def test_forged_scenario_pass_requires_401_in_body(self):
        self._stub("ERROR\nmessage:something else\n\n\x00")
        ok, *_ = m.run_scenario("forged", "h", 1, "/p", self.key, "sub", 1.0)
        self.assertFalse(ok, "401이 본문/헤더에 없으면 앱 재연결 판단이 못 잡으므로 실패 처리해야 한다")

        self._stub("ERROR\nmessage:401 Unauthorized: 토큰이 유효하지 않습니다\n\n\x00")
        ok, *_ = m.run_scenario("forged", "h", 1, "/p", self.key, "sub", 1.0)
        self.assertTrue(ok)

    def test_missing_scenario_expects_error(self):
        self._stub("ERROR\nmessage:401 Unauthorized: 인증이 필요합니다\n\n\x00")
        ok, expect, actual, _ = m.run_scenario("missing", "h", 1, "/p", self.key, "sub", 1.0)
        self.assertTrue(ok)
        self.assertEqual(expect, "ERROR")


class _FakeWsStompServer:
    """실제 소켓으로 handshake→CONNECT 수신→CONNECTED/ERROR 응답을 하는 최소 서버.

    앱의 STOMP 인증 로직은 흉내 내지 않는다(그건 JwtPythonSmokeCompatTest·
    StompRejectionLogNoiseTest가 Java 쪽에서 이미 검증한다) — 여기서는 stomp_smoke.py의
    handshake/프레임 송수신 코드가 실제 TCP 위에서 다른 구현체와 붙는지만 본다.
    Authorization 헤더가 있으면 CONNECTED, 없으면 ERROR(401)를 돌려준다.
    """

    MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

    def __init__(self):
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.bind(("127.0.0.1", 0))
        self.sock.listen(1)
        self.port = self.sock.getsockname()[1]
        self.thread = threading.Thread(target=self._serve_one, daemon=True)
        self.thread.start()

    def _serve_one(self):
        conn, _ = self.sock.accept()
        try:
            conn.settimeout(5)
            request = b""
            while b"\r\n\r\n" not in request:
                request += conn.recv(4096)
            headers_text = request.decode("iso-8859-1")
            key_line = [l for l in headers_text.split("\r\n") if l.lower().startswith("sec-websocket-key")][0]
            client_key = key_line.split(":", 1)[1].strip()
            accept = base64.b64encode(
                hashlib.sha1((client_key + self.MAGIC).encode("ascii")).digest()
            ).decode("ascii")
            response = (
                "HTTP/1.1 101 Switching Protocols\r\n"
                "Upgrade: websocket\r\n"
                "Connection: Upgrade\r\n"
                f"Sec-WebSocket-Accept: {accept}\r\n\r\n"
            )
            conn.sendall(response.encode("ascii"))

            frame_bytes = self._read_client_frame(conn)
            stomp_text = frame_bytes.decode("utf-8")
            has_auth = "Authorization:" in stomp_text

            if has_auth:
                reply = "CONNECTED\nversion:1.2\n\n\x00"
            else:
                reply = "ERROR\nmessage:401 Unauthorized: 인증이 필요합니다\n\ncontent-type:text/plain\n\n401 Unauthorized: 인증이 필요합니다\x00"
            self._send_server_frame(conn, reply)
        finally:
            conn.close()
            self.sock.close()

    @staticmethod
    def _read_client_frame(conn) -> bytes:
        b0b1 = conn.recv(2)
        length = b0b1[1] & 0x7F
        if length == 126:
            length = struct.unpack(">H", conn.recv(2))[0]
        elif length == 127:
            length = struct.unpack(">Q", conn.recv(8))[0]
        mask_key = conn.recv(4)
        payload = b""
        while len(payload) < length:
            payload += conn.recv(length - len(payload))
        return bytes(b ^ mask_key[i % 4] for i, b in enumerate(payload))

    @staticmethod
    def _send_server_frame(conn, text: str) -> None:
        conn.sendall(encode_server_text_frame(text))


class EndToEndSocketTest(unittest.TestCase):
    """실제 TCP 소켓으로 handshake→CONNECT→응답까지 왕복해 클라이언트 구현을 검증한다."""

    def test_connect_with_authorization_gets_connected(self):
        server = _FakeWsStompServer()
        response = m.stomp_connect_and_get_response(
            "127.0.0.1", server.port, "/ws/chat/websocket", "fake.jwt.token", timeout=5.0)
        self.assertTrue(response.startswith("CONNECTED"))

    def test_connect_without_authorization_gets_error_401(self):
        server = _FakeWsStompServer()
        response = m.stomp_connect_and_get_response(
            "127.0.0.1", server.port, "/ws/chat/websocket", None, timeout=5.0)
        self.assertTrue(response.startswith("ERROR"))
        self.assertIn("401", response)


if __name__ == "__main__":
    unittest.main()
