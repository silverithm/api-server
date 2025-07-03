# 🔔 Grafana Slack 알림 설정 가이드

## 1. Slack 앱 생성
1. [Slack API](https://api.slack.com/apps) 접속
2. "Create New App" 클릭
3. "From scratch" 선택
4. 앱 이름 입력 (예: "Silverithm Monitoring")
5. 워크스페이스 선택

## 2. 웹훅 설정
1. 생성된 앱에서 "Incoming Webhooks" 클릭
2. "Activate Incoming Webhooks" 토글을 ON으로 변경
3. "Add New Webhook to Workspace" 클릭
4. 알림을 받을 채널 선택
5. "Allow" 클릭

## 3. 웹훅 URL 복사
- 생성된 웹훅 URL을 복사 (예: `https://hooks.slack.com/services/T.../B.../...`)

## 4. 환경변수 설정
`.env` 파일에 다음과 같이 설정:

```bash
# Slack 웹훅 URLs (실제 URL로 교체 필요)
SLACK_WEBHOOK_URL=https://hooks.slack.com/services/YOUR/WEBHOOK/URL
SLACK_MONITORING_WEBHOOK_URL=https://hooks.slack.com/services/YOUR/MONITORING/URL
SLACK_PAYMENT_WEBHOOK_URL=https://hooks.slack.com/services/YOUR/PAYMENT/WEBHOOK_URL
SLACK_SIGNUP_WEBHOOK_URL=https://hooks.slack.com/services/YOUR/SIGNUP/WEBHOOK_URL
SLACK_API_FAILURE_WEBHOOK_URL=https://hooks.slack.com/services/YOUR/API_FAILURE/WEBHOOK_URL

# 기존 환경변수들
MYSQL_NAME=silverithm
MYSQL_USER=silverithm_user
MYSQL_PASSWORD=your_mysql_password
MYSQL_ROOT_PASSWORD=your_root_password
MYSQL_PROD_URL=jdbc:mysql://db:3306/silverithm

RABBITMQ_HOST=rabbitmq  
RABBITMQ_PORT=5672
RABBITMQ_USER=silverithm_rabbitmq
RABBITMQ_PASSWORD=your_rabbitmq_password

MAIL_HOST=smtp.gmail.com
MAIL_USERNAME=your_email@gmail.com
MAIL_PASSWORD=your_email_password
```

## 5. 테스트
```bash
curl -X POST YOUR_WEBHOOK_URL \
  -H 'Content-Type: application/json' \
  -d '{"text": "테스트 메시지입니다."}'
```

## 보안 주의사항
- ⚠️ **절대로 실제 웹훅 URL을 Git에 커밋하지 마세요!**
- 웹훅 URL이 노출되면 Slack에서 보안상 비활성화됩니다
- `.env` 파일은 `.gitignore`에 포함되어 있어야 합니다

## 6. Grafana 재시작

```bash
docker-compose restart grafana
```

## 7. 알림 규칙

설정된 알림 규칙들:

### 🔥 Grafana 알림 (자동 감지)
- **서버 다운**: 1분 이상 서버 무응답
- **높은 에러율**: 5분간 5xx 에러율 5% 초과
- **높은 응답시간**: 95th percentile이 2초 초과 (3분 지속)
- **높은 메모리 사용량**: JVM 힙 메모리 80% 초과 (5분 지속)

### 🤖 Spring Boot 알림 (애플리케이션 레벨)
- **서버 시작**: 애플리케이션 시작 후 30초 후 알림
- **메모리 사용량 높음**: 5분마다 체크, 80% 초과 시 알림
- **에러율 높음**: 5분마다 체크, 5% 초과 시 알림  
- **응답시간 높음**: 5분마다 체크, 2초 초과 시 알림
- **주간 리포트**: 매주 월요일 오전 9시 시스템 상태 리포트

### 📊 알림 채널
- **채널**: `#서버-상태-알림`
- **사용자명**: `Grafana` 또는 `Silverithm Bot`
- **아이콘**: `:warning:` (경고), `:red_circle:` (위험), `:information_source:` (정보)
- **반복 주기**: 12시간 (중복 알림 방지)

## 8. 테스트

알림이 제대로 작동하는지 테스트하려면:

1. 서버를 잠시 중단: `docker-compose stop app`
2. 1분 후 Slack에서 "서버 다운" 알림 확인
3. 서버 재시작: `docker-compose start app`

## 9. 문제 해결

### Slack 메시지가 오지 않는 경우:
1. Grafana UI에서 Alerting > Contact points 확인
2. 웹훅 URL이 올바른지 확인  
3. Slack 채널이 존재하는지 확인
4. Grafana 로그 확인: `docker logs grafana`

### 알림 규칙 수정:
`cfg/grafana/provisioning/alerting/slack-notifications.yaml` 파일 수정 후 Grafana 재시작

### 모니터링 설정 변경:
`src/main/resources/application.properties` 파일에서 다음 설정 조정:
```properties
# 시스템 모니터링 설정
monitoring.enabled=true                    # 모니터링 활성화/비활성화
monitoring.memory.threshold=80             # 메모리 사용량 임계값 (%)
monitoring.error.rate.threshold=5          # 에러율 임계값 (%)
monitoring.response.time.threshold=2       # 응답시간 임계값 (초)
```

## 10. 모니터링 기능 상세

### 🔍 실시간 모니터링
- **주기**: 5분마다 자동 체크
- **메트릭**: 메모리, 에러율, 응답시간
- **중복 알림 방지**: 동일 문제에 대해 연속 알림 차단

### 📈 헬스 체크 엔드포인트
```
GET /actuator/health
```
시스템 상태를 JSON으로 반환:
```json
{
  "status": "UP",
  "components": {
    "systemMonitoring": {
      "status": "UP",
      "details": {
        "memory.used": 536870912,
        "memory.max": 1073741824,
        "memory.usage.percent": "50.00%",
        "timestamp": "2024-01-15T10:30:00"
      }
    }
  }
}
```

### 🎯 알림 종류별 임계값
| 알림 유형 | 임계값 | 지속 시간 | 심각도 |
|-----------|--------|-----------|--------|
| 서버 다운 | 응답 없음 | 1분 | CRITICAL |
| 에러율 | 5% 초과 | 2분 | WARNING |
| 응답시간 | 2초 초과 | 3분 | WARNING |
| 메모리 | 80% 초과 | 5분 | WARNING | 