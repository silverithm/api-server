#!/usr/bin/env python3
"""배포 전환 직전, 유휴(idle) 컨테이너에 STOMP 소켓 인증이 살아있는지 확인한다.

2026-09-14 배포에서 STOMP CONNECT 인증 규칙이 조여지면서(만료 토큰 거절) 이미 배포된
웹이 재연결을 못 해 40분간 채팅이 먹통이었는데, 헬스체크(`/health`)는 이 문제를 전혀
잡아내지 못했다 — HTTP 헬스만 보고 트래픽을 넘겼다. 이 스크립트는 헬스체크 통과 직후·
트래픽 전환 전에 유휴 컨테이너의 실제 STOMP CONNECT 동작을 4가지 시나리오로 확인해서,
같은 종류의 사고를 배포 단계에서 막는다.

표준 라이브러리만 쓴다(서버에 websocket 모듈이 없음). 토큰은 이 스크립트가 컨테이너의
서명 키로 직접 민팅한다 — 로그인 없이, 실사용자 계정 없이 검증할 수 있다. 이유는 아래
"실사용자가 필요 없는 이유" 참고.

사용법:
    python3 verify/stomp_smoke.py --port 8082 --secret "$JWT_SECRET_B64"
    # 또는 시크릿을 stdin으로:
    echo "$JWT_SECRET_B64" | python3 verify/stomp_smoke.py --port 8082 --secret -

종료 코드: 0 = 4개 시나리오 모두 기대대로 통과. 1 = 하나라도 기대와 다름(사유를 stdout에 출력).
2 = 연결/설정 자체가 안 됨(포트 안 열림, 시크릿 형식 오류 등 — 스모크 자체가 성립하지 않음).

실사용자가 필요 없는 이유:
    JwtTokenProvider.getAuthentication()은 subject(sub) 클레임으로 CarevPrincipal을 만들 뿐,
    CONNECT 단계에서 DB 조회를 하지 않는다(auth 클레임이 있으면 통과). 인증 실패/성공 여부는
    서명·만료·클레임 형태만으로 결정되므로, 존재하지 않는 subject("deploy-smoke@internal")로도
    이 스크립트가 검증하려는 4가지 분기(정상/만료/위조/누락)를 그대로 재현한다. 실제 사용자
    데이터를 만들 필요가 없다 — 단, 이 스모크가 검증하지 못하는 것은 "그 사용자가 실재하는지"에
    기대는 하위 로직(메시지 브로드캐스트 등 CONNECT 이후 단계)이다. 그 영역까지 확인하려면
    전용 스모크 계정을 만들어 실제 로그인 토큰 발급 경로까지 태워야 한다(별도 작업 제안).
"""

import argparse
import base64
import hashlib
import hmac
import json
import os
import socket
import struct
import sys
import time
import uuid
from typing import Optional

DEFAULT_PATH = "/ws/chat/websocket"
DEFAULT_SUBJECT = "deploy-smoke@internal"


# ─── JWT (HS256) ───

def _b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def _decode_secret(secret_b64: str) -> bytes:
    """Java의 io.jsonwebtoken.io.Decoders.BASE64와 같은 표준(비-URL-safe) Base64 디코드.

    운영 .env에 패딩(`=`) 없이 저장돼 있을 수 있어 보정한다.
    """
    s = secret_b64.strip()
    padding = (-len(s)) % 4
    return base64.b64decode(s + ("=" * padding))


def mint_jwt(key_bytes: bytes, subject: str, *, expired: bool, ttl_sec: int = 1800) -> str:
    """JwtTokenProvider.generateToken과 같은 클레임 형태(auth/type/sub/iat/exp)로 HS256 토큰을 만든다."""
    now = int(time.time())
    exp = now - 60 if expired else now + ttl_sec
    header = {"alg": "HS256", "typ": "JWT"}
    payload = {
        "sub": subject,
        "auth": "ROLE_ADMIN",
        "type": "access",
        "iat": now,
        "exp": exp,
    }
    signing_input = (
        _b64url(json.dumps(header, separators=(",", ":")).encode("utf-8"))
        + "."
        + _b64url(json.dumps(payload, separators=(",", ":")).encode("utf-8"))
    )
    sig = hmac.new(key_bytes, signing_input.encode("ascii"), hashlib.sha256).digest()
    return signing_input + "." + _b64url(sig)


def mint_forged_jwt(subject: str) -> str:
    """진짜 키가 아닌 임의의 키로 서명한 토큰 — 서명 검증 자체가 실패해야 한다."""
    wrong_key = hashlib.sha256(b"not-the-real-signing-key").digest()
    return mint_jwt(wrong_key, subject, expired=False)


# ─── WebSocket(RFC6455) 최소 클라이언트 ───

class WebSocketError(RuntimeError):
    pass


class RawWebSocket:
    """표준 라이브러리만으로 handshake + text frame 송수신을 구현한 최소 클라이언트.

    STOMP는 텍스트 프레임 하나에 담기는 짧은 메시지라 단편화(fragmentation)는 다루지 않는다.
    """

    def __init__(self, host: str, port: int, path: str, timeout: float):
        self.sock = socket.create_connection((host, port), timeout=timeout)
        self.sock.settimeout(timeout)
        self._handshake(host, port, path)

    def _handshake(self, host: str, port: int, path: str) -> None:
        key = base64.b64encode(os.urandom(16)).decode("ascii")
        req = (
            f"GET {path} HTTP/1.1\r\n"
            f"Host: {host}:{port}\r\n"
            "Upgrade: websocket\r\n"
            "Connection: Upgrade\r\n"
            f"Sec-WebSocket-Key: {key}\r\n"
            "Sec-WebSocket-Version: 13\r\n"
            "\r\n"
        )
        self.sock.sendall(req.encode("ascii"))
        resp = self._read_http_headers()
        if " 101 " not in resp.split("\r\n", 1)[0]:
            raise WebSocketError(f"핸드셰이크 실패(101 아님): {resp.splitlines()[0] if resp else '(빈 응답)'}")

    def _read_http_headers(self) -> str:
        buf = b""
        while b"\r\n\r\n" not in buf:
            chunk = self.sock.recv(4096)
            if not chunk:
                raise WebSocketError("핸드셰이크 중 연결 끊김")
            buf += chunk
        return buf.decode("iso-8859-1", errors="replace")

    def send_text(self, text: str) -> None:
        payload = text.encode("utf-8")
        length = len(payload)
        header = bytearray()
        header.append(0x80 | 0x1)  # FIN + text opcode
        mask_bit = 0x80
        if length < 126:
            header.append(mask_bit | length)
        elif length < 65536:
            header.append(mask_bit | 126)
            header += struct.pack(">H", length)
        else:
            header.append(mask_bit | 127)
            header += struct.pack(">Q", length)
        mask_key = os.urandom(4)
        header += mask_key
        masked = bytes(b ^ mask_key[i % 4] for i, b in enumerate(payload))
        self.sock.sendall(bytes(header) + masked)

    def recv_frame(self) -> Optional[tuple]:
        """(opcode, payload_bytes) 하나를 읽는다. 연결이 끊기면 None."""
        first2 = self._recv_exact(2)
        if first2 is None:
            return None
        b0, b1 = first2[0], first2[1]
        opcode = b0 & 0x0F
        masked = bool(b1 & 0x80)
        length = b1 & 0x7F
        if length == 126:
            length = struct.unpack(">H", self._recv_exact(2))[0]
        elif length == 127:
            length = struct.unpack(">Q", self._recv_exact(8))[0]
        mask_key = self._recv_exact(4) if masked else None
        payload = self._recv_exact(length) if length else b""
        if masked and payload:
            payload = bytes(b ^ mask_key[i % 4] for i, b in enumerate(payload))
        return opcode, payload

    def _recv_exact(self, n: int) -> Optional[bytes]:
        buf = b""
        while len(buf) < n:
            chunk = self.sock.recv(n - len(buf))
            if not chunk:
                return None if not buf else buf
            buf += chunk
        return buf

    def close(self) -> None:
        try:
            self.sock.close()
        except OSError:
            pass


# ─── STOMP ───

def build_connect_frame(token: Optional[str]) -> str:
    lines = ["CONNECT", "accept-version:1.1,1.2", "heart-beat:0,0"]
    if token is not None:
        lines.append(f"Authorization:Bearer {token}")
    return "\n".join(lines) + "\n\n\x00"


def stomp_connect_and_get_response(host: str, port: int, path: str, token: Optional[str],
                                    timeout: float) -> str:
    """CONNECT를 보내고 첫 STOMP 응답 프레임의 커맨드("CONNECTED"/"ERROR")를 돌려준다."""
    ws = RawWebSocket(host, port, path, timeout)
    try:
        ws.send_text(build_connect_frame(token))
        deadline = time.time() + timeout
        while time.time() < deadline:
            frame = ws.recv_frame()
            if frame is None:
                raise WebSocketError("응답 전에 연결이 끊김")
            opcode, payload = frame
            if opcode == 0x8:  # close
                raise WebSocketError(f"서버가 연결을 닫음: {payload!r}")
            if opcode == 0x9:  # ping -> pong
                continue
            if opcode != 0x1:  # text만 본다
                continue
            text = payload.decode("utf-8", errors="replace")
            command = text.split("\n", 1)[0].strip()
            if command in ("CONNECTED", "ERROR"):
                return text
            # heart-beat(개행/빈 프레임) 등은 건너뛴다
        raise WebSocketError("타임아웃: CONNECTED/ERROR 응답을 못 받음")
    finally:
        ws.close()


# ─── 시나리오 ───

SCENARIOS = ("valid", "expired", "forged", "missing")


def run_scenario(name: str, host: str, port: int, path: str, key_bytes: bytes, subject: str,
                  timeout: float) -> tuple:
    """(성공여부, 기대, 실제, 상세) 반환. 성공여부는 '기대한 분기와 일치했는가'."""
    if name == "valid":
        token = mint_jwt(key_bytes, subject, expired=False)
        expect = "CONNECTED"
    elif name == "expired":
        token = mint_jwt(key_bytes, subject, expired=True)
        expect = "CONNECTED"  # 구버전 웹 호환 — 서명만 맞으면 '나'를 채워 받아준다
    elif name == "forged":
        token = mint_forged_jwt(subject)
        expect = "ERROR"
    elif name == "missing":
        token = None
        expect = "ERROR"
    else:
        raise ValueError(name)

    try:
        response = stomp_connect_and_get_response(host, port, path, token, timeout)
    except WebSocketError as e:
        return False, expect, "EXCEPTION", str(e)

    actual = response.split("\n", 1)[0].strip()
    ok = actual == expect
    detail = response.replace("\x00", "").strip()
    if actual == "ERROR" and "401" not in detail:
        ok = False
        detail += "  [경고: 'ERROR'이지만 본문/헤더에 401이 없음 — 앱의 재연결 판단(looksLikeAuthFailure)이 못 잡을 수 있음]"
    return ok, expect, actual, detail


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--host", default="localhost")
    parser.add_argument("--port", type=int, required=True, help="유휴 컨테이너의 호스트 포트 (예: 8082)")
    parser.add_argument("--path", default=DEFAULT_PATH, help=f"기본 {DEFAULT_PATH}")
    parser.add_argument("--secret", required=True,
                         help="jwt.secretKey 값(Base64). '-'면 stdin에서 한 줄 읽는다")
    parser.add_argument("--subject", default=DEFAULT_SUBJECT,
                         help="토큰 subject. 실재하지 않아도 CONNECT 검증에는 영향 없음")
    parser.add_argument("--timeout", type=float, default=5.0)
    parser.add_argument("--scenario", choices=SCENARIOS, action="append",
                         help="특정 시나리오만 실행(반복 가능). 기본은 4개 전부")
    args = parser.parse_args()

    secret_raw = sys.stdin.readline().strip() if args.secret == "-" else args.secret
    if not secret_raw:
        print("[stomp_smoke] 시크릿이 비어 있다", file=sys.stderr)
        return 2
    try:
        key_bytes = _decode_secret(secret_raw)
    except Exception as e:  # noqa: BLE001 - CLI 진입점, 원인 그대로 보여주면 충분
        print(f"[stomp_smoke] 시크릿 Base64 디코드 실패: {e}", file=sys.stderr)
        return 2
    if len(key_bytes) * 8 < 256:
        print(f"[stomp_smoke] 경고: 키가 {len(key_bytes)*8}비트 — HS256 권장(256비트) 미만", file=sys.stderr)

    subject = f"{args.subject}-{uuid.uuid4().hex[:8]}"
    scenarios = args.scenario or list(SCENARIOS)

    all_ok = True
    for name in scenarios:
        try:
            ok, expect, actual, detail = run_scenario(
                name, args.host, args.port, args.path, key_bytes, subject, args.timeout)
        except (OSError, ConnectionError) as e:
            print(f"[stomp_smoke] {name}: 연결 실패 — {e}")
            return 2
        status = "PASS" if ok else "FAIL"
        print(f"[stomp_smoke] {name}: {status} (기대={expect}, 실제={actual}) {detail[:200]}")
        all_ok = all_ok and ok

    if all_ok:
        print("[stomp_smoke] 모든 시나리오 통과")
        return 0
    print("[stomp_smoke] 실패한 시나리오가 있음 — 전환하지 않아야 한다")
    return 1


if __name__ == "__main__":
    sys.exit(main())
