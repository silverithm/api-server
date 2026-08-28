#!/usr/bin/env bash
# 블루그린 무중단 배포 스크립트 (EC2에서 ec2-user로 실행: ./deploy.sh)
#
# 흐름: 유휴 색 빌드·기동 → 헬스체크 → nginx upstream 전환(reload) → 드레인 → 구버전 중지
# 실패 시: 유휴 색만 중지하고 기존 색이 계속 서빙 (무중단 롤백)
# 각 단계는 Slack(SLACK_MONITORING_WEBHOOK_URL)으로 알림.
set -euo pipefail
cd "$(dirname "$0")"

UPSTREAM_CONF="data/nginx/upstream.conf"
HEALTH_TIMEOUT_SEC=180
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

notify ":stethoscope: [헬스체크 통과] ${IDLE} 기동 완료 — 트래픽 전환 시작"

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
notify ":arrows_counterclockwise: [트래픽 전환] ${ACTIVE} → ${IDLE} 완료"

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
