#!/usr/bin/env bash
# 블루그린 무중단 배포 스크립트 (EC2에서 ec2-user로 실행: ./deploy.sh)
#
# 흐름: 유휴 색 빌드·기동 → 헬스체크 → nginx upstream 전환(reload) → 드레인 → 구버전 중지
# 실패 시: 유휴 색만 중지하고 기존 색이 계속 서빙 (무중단 롤백)
# 각 단계는 Slack(SLACK_MONITORING_WEBHOOK_URL)으로 알림.
set -euo pipefail
cd "$(dirname "$0")"

UPSTREAM_CONF="data/nginx/upstream.conf"
HEALTH_TIMEOUT_SEC=${HEALTH_TIMEOUT_SEC:-180}
DRAIN_SEC=10

# ─── Slack ───
SLACK_URL=$(grep -E '^SLACK_MONITORING_WEBHOOK_URL=' .env | head -1 | cut -d= -f2- | tr -d '"' || true)
notify() {
  local text="$1"
  if [ -n "${SLACK_URL:-}" ]; then
    curl -s -m 10 -X POST -H 'Content-type: application/json' \
      --data "{\"text\":\"${text}\"}" "$SLACK_URL" >/dev/null || true
  fi
  echo "[deploy] $text"
}

# ─── 지금 코드가 정말 최신인지 먼저 확인한다 ───
#
# 예전에 한 번, git pull이 실패했는데 그걸 모르고 배포를 태워서 옛 코드를 그대로 다시
# 올린 적이 있다. 배포는 "성공"으로 끝나고 슬랙에도 성공이라 떠서, 로그의 커밋 해시를
# 눈으로 확인하지 않았으면 몰랐을 일이다.
# (원인은 `git pull | tail` 처럼 파이프를 걸어 실패 종료코드가 삼켜진 것이었다 —
#  부르는 쪽 실수라도 여기서 막아 준다.)
#
# 배포 전에 origin과 대조해서 뒤처져 있으면 멈춘다.
#   --check-only  : 확인만 하고 배포하지 않는다 (이 가드를 시험할 때 쓴다)
#   SKIP_FRESHNESS_CHECK=1 : 급할 때 건너뛴다
CHECK_ONLY=0
if [ "${1:-}" = "--check-only" ]; then
  CHECK_ONLY=1
fi

if [ "${SKIP_FRESHNESS_CHECK:-0}" != "1" ] && git rev-parse --git-dir >/dev/null 2>&1; then
  BRANCH=$(git rev-parse --abbrev-ref HEAD)
  [ "$BRANCH" = "HEAD" ] && BRANCH=main
  git fetch --quiet origin "$BRANCH" 2>/dev/null || true
  BEHIND=$(git rev-list --count "HEAD..origin/$BRANCH" 2>/dev/null || echo 0)

  if [ "$BEHIND" -gt 0 ]; then
    notify ":octagonal_sign: [배포 중단] 코드가 origin/$BRANCH 보다 ${BEHIND}커밋 뒤처져 있다 — 옛 코드를 올릴 뻔했다. git pull 후 다시 실행 (건너뛰려면 SKIP_FRESHNESS_CHECK=1)"
    exit 1
  fi

  DIRTY=$(git status --porcelain --untracked-files=no 2>/dev/null | grep -v "deploy.sh" || true)
  if [ -n "$DIRTY" ]; then
    notify ":warning: [배포 주의] 서버에 커밋되지 않은 변경이 있다 — 배포되는 코드가 저장소와 다를 수 있다"
  fi
fi

if [ "$CHECK_ONLY" = "1" ]; then
  echo "[deploy] 최신 확인만 하고 끝낸다 (--check-only)"
  exit 0
fi

# ─── 활성/유휴 색 판단 (upstream.conf 기준, 없거나 legacy면 blue부터) ───
ACTIVE="legacy"
if [ -f "$UPSTREAM_CONF" ]; then
  if grep -q "silverithm-backend-blue" "$UPSTREAM_CONF"; then
    ACTIVE="blue"
  elif grep -q "silverithm-backend-green" "$UPSTREAM_CONF"; then
    ACTIVE="green"
  fi
fi

if [ "$ACTIVE" = "blue" ]; then
  IDLE="green"; IDLE_PORT=8082
else
  IDLE="blue"; IDLE_PORT=8081
fi

COMMIT=$(git rev-parse --short HEAD)
COMMIT_MSG=$(git log -1 --pretty=%s)

notify ":rocket: [배포 시작] ${IDLE} 기동 (활성: ${ACTIVE}) — ${COMMIT} ${COMMIT_MSG}"

# ─── 빌드 ───
if ! ./gradlew build -x test > /tmp/deploy-gradle.log 2>&1; then
  notify ":x: [배포 실패] gradle 빌드 실패 — ${ACTIVE} 계속 서빙 중 (무중단). 로그: /tmp/deploy-gradle.log"
  exit 1
fi

if ! sudo docker-compose build "app-${IDLE}" > /tmp/deploy-docker-build.log 2>&1; then
  notify ":x: [배포 실패] 도커 이미지 빌드 실패 — ${ACTIVE} 계속 서빙 중 (무중단). 로그: /tmp/deploy-docker-build.log"
  exit 1
fi

# ─── 유휴 색 기동 ───
sudo docker-compose up -d "app-${IDLE}"

# ─── 헬스체크 ───
HEALTHY=0
for i in $(seq 1 $((HEALTH_TIMEOUT_SEC / 3))); do
  if curl -sf -m 2 "http://localhost:${IDLE_PORT}/health" | grep -q '"UP"'; then
    HEALTHY=1
    break
  fi
  sleep 3
done

if [ "$HEALTHY" != "1" ]; then
  notify ":x: [배포 실패] ${IDLE} 헬스체크 ${HEALTH_TIMEOUT_SEC}초 초과 — ${ACTIVE} 계속 서빙 중 (무중단). \`sudo docker logs silverithm-backend-${IDLE}\` 확인 필요"
  sudo docker-compose stop "app-${IDLE}" >/dev/null 2>&1 || true
  exit 1
fi

notify ":stethoscope: [헬스체크 통과] ${IDLE} 기동 완료 — 소켓 스모크 시작"

# ─── 소켓 인증 스모크 테스트 (헬스체크 통과 직후, 트래픽 전환 전) ───
#
# 2026-09-14 배포에서 헬스체크(HTTP /health)는 "떴다"만 확인했다. 그런데 실제로 조여진
# 건 STOMP CONNECT 인증 규칙이었고, 그건 헬스체크가 전혀 보지 않는 경로다 — 만료 토큰을
# 쓰던 이미 배포된 웹이 전환 직후부터 40분간 소켓을 못 붙였고, 사람이 알아챈 뒤에야
# 롤백했다. 여기서 유휴 컨테이너에 직접 STOMP CONNECT 4가지 분기(정상/만료/위조/누락
# 토큰)를 태워, 하나라도 기대와 다르면 트래픽을 넘기지 않는다.
#
# 토큰은 유휴 컨테이너의 실제 서명 키로 스모크 스크립트가 직접 민팅한다(로그인 불필요 —
# 이유는 verify/stomp_smoke.py 상단 주석). 키는 컨테이너 환경변수에는 없고
# src/main/resources/application-prod.yaml의 jwt.secretKey에 평문(base64)으로 들어
# 있다(이 파일은 .gitignore 대상이라 저장소 클론이 아니라 서버 체크아웃 자체에 배치돼
# 있음 — .env와 같은 성격). 혹시 나중에 환경변수로 옮겨질 수도 있으니 env를 먼저 보고,
# 없으면 yaml을 본다. **키 값은 절대 echo/notify(슬랙)하지 않는다** — 변수에 담아
# 스크립트로 그대로 흘려보내기만 한다.
JWT_SECRET_RAW=$(sudo docker inspect "silverithm-backend-${IDLE}" \
    --format '{{range .Config.Env}}{{println .}}{{end}}' 2>/dev/null \
    | grep -E '^(JWT_SECRETKEY|JWT_SECRET_KEY)=' | head -1 | cut -d= -f2- || true)

PROD_YAML="src/main/resources/application-prod.yaml"
if [ -z "$JWT_SECRET_RAW" ] && [ -f "$PROD_YAML" ]; then
  JWT_SECRET_RAW=$(grep -E '^[[:space:]]*secretKey:' "$PROD_YAML" | head -1 \
      | sed -E 's/^[[:space:]]*secretKey:[[:space:]]*//' | tr -d "\"'" || true)
fi

if [ -z "$JWT_SECRET_RAW" ]; then
  notify ":x: [배포 실패] JWT 서명 키를 못 찾음(컨테이너 env, ${PROD_YAML} 둘 다) — 소켓 스모크를 할 수 없어 전환하지 않음"
  sudo docker-compose stop "app-${IDLE}" >/dev/null 2>&1 || true
  exit 1
fi

# --secret -로 stdin에 흘려보낸다 — 인자로 주면 ps로 다른 프로세스에 노출된다.
SMOKE_OUTPUT=$(echo "$JWT_SECRET_RAW" | python3 verify/stomp_smoke.py --port "$IDLE_PORT" --secret - 2>&1) || SMOKE_STATUS=$?
SMOKE_STATUS=${SMOKE_STATUS:-0}
echo "$SMOKE_OUTPUT"

if [ "$SMOKE_STATUS" != "0" ]; then
  notify ":x: [배포 실패] ${IDLE} 소켓 인증 스모크 실패(코드 ${SMOKE_STATUS}) — ${ACTIVE} 계속 서빙 중 (무중단). 상세는 배포 로그 참고"
  sudo docker-compose stop "app-${IDLE}" >/dev/null 2>&1 || true
  exit 1
fi

notify ":closed_lock_with_key: [소켓 스모크 통과] ${IDLE} CONNECT 인증 4분기(정상/만료/위조/누락) 정상 — 트래픽 전환 시작"

# ─── nginx upstream 전환 ───
echo "upstream backend_upstream { server silverithm-backend-${IDLE}:8080; }" > "$UPSTREAM_CONF"

if ! sudo docker exec nginx-proxy nginx -t >/dev/null 2>&1; then
  # 설정 오류 시 원복
  if [ "$ACTIVE" != "legacy" ]; then
    echo "upstream backend_upstream { server silverithm-backend-${ACTIVE}:8080; }" > "$UPSTREAM_CONF"
  fi
  notify ":x: [배포 실패] nginx 설정 검증 실패 — upstream 원복, ${ACTIVE} 계속 서빙 중 (무중단)"
  sudo docker-compose stop "app-${IDLE}" >/dev/null 2>&1 || true
  exit 1
fi

sudo docker exec nginx-proxy nginx -s reload
notify ":arrows_counterclockwise: [트래픽 전환] ${ACTIVE} → ${IDLE} 완료 — 전환 후 감시 시작"

# ─── 전환 후 감시 ───
#
# 스모크는 전환 "직전"의 유휴 컨테이너를 몇 번의 왕복으로만 본다. 실제 트래픽에만 있는
# 조건(구버전 앱 비중, 동시 접속 규모 등)에서 문제가 생길 수 있어 전환 "직후"에도 잠깐
# 지켜본다. 전환 시점부터 누적으로, ${MONITOR_WINDOW_SEC}초 동안 ${MONITOR_INTERVAL_SEC}초
# 간격으로 새 활성(${IDLE}) 컨테이너 로그의 "연결 거부" 건수와, nginx-proxy 로그에서 같은
# IP가 ws/chat 핸드셰이크를 반복하는 횟수(최댓값)를 본다. 임계를 넘으면 슬랙 경고 — 구버전
# (${ACTIVE}) 컨테이너를 아직 끄지 않은 이 시점이라야 AUTO_ROLLBACK=1일 때 되돌릴 수 있어서,
# 구버전 중지는 이 감시 뒤로 옮겼다. 기본은 경고만 하고 그대로 진행한다(무중단 성질 유지).
MONITOR_WINDOW_SEC=${MONITOR_WINDOW_SEC:-90}
MONITOR_INTERVAL_SEC=${MONITOR_INTERVAL_SEC:-15}
REJECT_THRESHOLD=${REJECT_THRESHOLD:-20}
IP_REPEAT_THRESHOLD=${IP_REPEAT_THRESHOLD:-12}
AUTO_ROLLBACK=${AUTO_ROLLBACK:-0}

SWITCH_EPOCH=$(date +%s)
ROLLBACK_NEEDED=0
ELAPSED=0
while [ "$ELAPSED" -lt "$MONITOR_WINDOW_SEC" ]; do
  sleep "$MONITOR_INTERVAL_SEC"
  ELAPSED=$((ELAPSED + MONITOR_INTERVAL_SEC))

  REJECT_COUNT=$(sudo docker logs --since "$SWITCH_EPOCH" "silverithm-backend-${IDLE}" 2>&1 \
      | grep -c "연결 거부") || true
  REJECT_COUNT=${REJECT_COUNT:-0}

  IP_MAX_REPEAT=$(sudo docker logs --since "$SWITCH_EPOCH" nginx-proxy 2>&1 \
      | grep "ws/chat" | awk '{print $1}' | sort | uniq -c | sort -rn | head -1 \
      | awk '{print $1+0}') || true
  IP_MAX_REPEAT=${IP_MAX_REPEAT:-0}

  echo "[deploy] 감시 ${ELAPSED}/${MONITOR_WINDOW_SEC}s (누적, 전환시점부터) — 연결거부=${REJECT_COUNT} IP반복최대=${IP_MAX_REPEAT}"

  if [ "$REJECT_COUNT" -ge "$REJECT_THRESHOLD" ] || [ "$IP_MAX_REPEAT" -ge "$IP_REPEAT_THRESHOLD" ]; then
    ROLLBACK_NEEDED=1
    notify ":warning: [전환 후 경고] ${IDLE} 활성화 후 소켓 이상 신호 — 연결거부 누적 ${REJECT_COUNT}건(임계 ${REJECT_THRESHOLD}), 동일 IP 핸드셰이크 반복 최대 ${IP_MAX_REPEAT}회(임계 ${IP_REPEAT_THRESHOLD}). AUTO_ROLLBACK=${AUTO_ROLLBACK}"
    break
  fi
done

if [ "$ROLLBACK_NEEDED" = "1" ] && [ "$AUTO_ROLLBACK" = "1" ] && [ "$ACTIVE" != "legacy" ]; then
  echo "upstream backend_upstream { server silverithm-backend-${ACTIVE}:8080; }" > "$UPSTREAM_CONF"
  if sudo docker exec nginx-proxy nginx -t >/dev/null 2>&1; then
    sudo docker exec nginx-proxy nginx -s reload
    notify ":leftwards_arrow_with_hook: [자동 롤백] upstream을 ${ACTIVE}(으)로 되돌림 — ${IDLE}은 조사를 위해 계속 띄워둠(수동 정리 필요)"
  else
    notify ":x: [자동 롤백 실패] nginx 설정 검증 실패 — upstream이 ${IDLE}에 남아있을 수 있음, 즉시 수동 확인 필요"
  fi
  exit 1
fi

if [ "$ROLLBACK_NEEDED" = "1" ]; then
  notify ":warning: [진행] AUTO_ROLLBACK 미설정이라 경고만 하고 배포를 계속 진행함 — 로그를 확인할 것"
fi

# ─── 드레인 후 구버전 중지 ───
sleep "$DRAIN_SEC"

if [ "$ACTIVE" = "legacy" ]; then
  # 최초 전환: 구 단일 컨테이너 제거
  sudo docker rm -f silverithm-backend >/dev/null 2>&1 || true
else
  sudo docker-compose stop "app-${ACTIVE}" >/dev/null 2>&1 || true
fi

notify ":white_check_mark: [배포 완료] ${IDLE} 활성 (${COMMIT} ${COMMIT_MSG}) — 구버전(${ACTIVE}) 종료, 무중단 전환 성공"

# ─── 배포 찌꺼기 정리 ───
# 배포마다 이미지 한 벌(≈470MB)과 빌드 캐시가 쌓인다. 정리하지 않으면 몇 달 만에
# 디스크가 찬다 — 실제로 2026-08 점검에서 빌드 캐시 15GB, 태그 없는 이미지 101개가
# 쌓여 있었다(디스크 37% 중 대부분). 방금 띄운 색과 대기 색 이미지는 태그가 있어
# 남고, 태그 없는 찌꺼기만 지운다.
PRUNED=$(sudo docker image prune -f 2>/dev/null | tail -1)
# 빌드 캐시는 docker system df가 실제보다 작게 보고하므로 별도로 비운다.
# 7일보다 오래된 캐시만 지워 최근 배포의 증분 빌드 속도는 유지한다.
CACHE_PRUNED=$(sudo docker builder prune -af --filter "until=168h" 2>/dev/null | tail -1)
DISK_LEFT=$(df -h / | awk 'NR==2 {print $4" 여유 ("$5" 사용)"}')
notify ":broom: [정리] ${PRUNED:-이미지 정리 완료} / 빌드캐시 ${CACHE_PRUNED:-정리 완료} — 디스크 ${DISK_LEFT}"
