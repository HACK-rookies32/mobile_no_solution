# 자녀 앱 푸시 계약 (Firebase 미사용)

GAP-11 「캡처 요청 202 푸시 페이로드 미정의」에 대한 **Android 측 제안 계약**이다.
**상주 WebSocket(실시간)** 으로 부모 → 자녀 요청을 전달하고, 처리 결과는 REST ack 으로 회신한다.

> **[2026-08-28] REST 폴링 백업 경로 제거.** 서버에 대응 엔드포인트가 없었고,
> 백엔드가 FCM 사용을 결정하면서 존재 이유가 사라졌다. 자세한 내용은 2장.

백엔드가 키 이름을 바꾸고 싶다면 아래 4개 파일만 고치면 된다.

| 계약 | 파일 |
|---|---|
| WebSocket 프레임 | `data/ws/ChildCommandFrame.kt` |
| REST DTO | `data/remote/dto/Dtos.kt` (`RemoteCommandAckRequest`) |
| REST 경로 | `data/remote/api/Apis.kt` (`CommandApi`, `PhotoApi`) |
| 타입 문자열 매핑 | `data/repository/Mappers.kt` (`String.toRemoteCommandType`) |

---

## 1. WebSocket `wss://{origin}/ws/commands`

인증은 채팅 소켓과 동일하게 `Authorization: Bearer {accessToken}` 헤더 + `?token=` 쿼리 둘 다 붙인다.
클라이언트는 30초 ping, 끊기면 최대 30초까지 지수 백오프로 재연결한다.

### 서버 → 자녀

```json
{ "type": "CAPTURE_REQUEST", "requestId": "req-1", "childId": "child-1", "requestedBy": "엄마", "requestedAt": "2026-08-25T10:00:00Z" }
{ "type": "CAMERA_REQUEST",  "requestId": "req-2", "childId": "child-1", "requestedBy": "엄마" }
{ "type": "PING" }
```

`type` 은 아래 값을 모두 허용한다(대소문자 무시).

- 화면 캡처: `SCREEN_CAPTURE` | `CAPTURE` | `CAPTURE_REQUEST` | `SCREENSHOT`
- 카메라 촬영: `CAMERA_SHOT` | `CAMERA` | `CAMERA_REQUEST` | `PHOTO`

### 자녀 → 서버

```json
{ "type": "HELLO", "childId": "child-1" }
{ "type": "PONG" }
{ "type": "ACK", "requestId": "req-1", "status": "DONE" }
```

`status`: `RECEIVED` | `DONE` | `DENIED` | `FAILED`

---

## 2. REST ack (신규)

> **[2026-08-28] 폴링 백업 경로 제거.** `GET /children/{childId}/commands/pending` 은
> 서버에 대응 엔드포인트가 없고, 백엔드가 FCM 사용을 결정하면서 존재 이유가 사라져 걷어냈다.
> 수신 경로는 **WebSocket(1장) + FCM** 두 가지다. 폴링 워커(`ChildCommandPollWorker`)와
> 수신 DTO(`RemoteCommandDto`)도 함께 삭제했다.

처리 결과만 REST 로 되돌린다. **아직 서버에 없어도 앱은 정상 동작한다** — 실패 시 무시한다.

```
POST /children/{childId}/commands/{requestId}/ack
     body: { "status": "DONE", "message": null }
```

`status`: `RECEIVED` | `DONE` | `DENIED` | `FAILED`

소켓으로도 같은 ACK 프레임을 보내므로 서버는 **멱등 처리**해야 한다.
다만 FCM 이 소켓 꺼진 상태로 도착하면 소켓 ACK 은 나가지 않으므로,
**이 REST 경로가 유일한 회신 수단이 된다** — FCM 을 주 경로로 켤 때 함께 구현이 필요하다.

## 3. 루키즈페이 용돈 지갑 (신규 · GAP-05)

명세의 `points` 경로는 **구독 적립 포인트**라 「부모 → 자녀 송금」 필드가 없다.
자녀 QR 결제는 송금자·수신자가 분명해야 하므로 별도 지갑 경로를 제안한다.

```
GET  /children/{childId}/allowance             -> { "balance": 12000, "updatedAt": "..." }
GET  /children/{childId}/allowance/history     -> [ { "id", "type", "amount", "description", "dateLabel" } ]
POST /children/{childId}/allowance/send        { "amount": 10000, "memo": "이번 주 용돈" }   # 부모
POST /children/{childId}/allowance/withdraw    { "amount": 3000,  "memo": null }             # 부모
POST /children/{childId}/allowance/payments    { "token", "amount", "merchant" }             # 자녀 QR
```

`type`: `SEND` | `WITHDRAW` | `PAYMENT` (부호 있는 `amount` — 받으면 +, 회수·결제면 −)

**잔액 검증의 최종 권한은 서버**다. 자녀가 QR 을 띄워 둔 사이 부모가 회수할 수 있으므로,
`payments` 는 잔액을 다시 확인하고 부족하면 거절해야 한다. 클라이언트도 결제 확정 직전에
`GET .../allowance` 를 한 번 더 호출하지만 그건 UX 용 방어일 뿐이다.

QR 페이로드 형식(가맹점 단말이 파싱):

```
KIDHACKPAY1|{childId}|{amount}|{token}|{expiresAtMillis}
```

`token` 은 1회용 난수이고 발급 후 180초 뒤 만료된다.

---

## 4. 업로드

- 화면 캡처: **기존 명세 그대로** `POST /children/{childId}/screenshots`
  (multipart: `file`, `capturedAt`, `trigger=SCHEDULED|MANUAL`)
- 카메라 촬영: **신규** `POST /children/{childId}/photos`
  (multipart: `file`, `capturedAt`, `requestId` — `requestId` 는 자녀가 스스로 찍은 경우 생략)

스크린샷 엔드포인트를 재사용하고 싶다면 `PhotoApi` 를 지우고
`ChildCameraViewModel` 이 `ScreenshotRepository.upload(..., trigger = "MANUAL")` 을 부르게 하면 된다.

---

## 5. 안드로이드 제약 (기획·백엔드 공유 필요)

1. **MediaProjection 토큰은 저장할 수 없다.** 안드로이드는 화면 녹화를 매번 사용자 동의로만 허용한다.
   따라서 「부모 요청 → 자동 캡처」는 불가능하고, 반드시 **자녀가 알림을 눌러 동의**해야 한다.
   REQ-M-12 의 1시간 주기 캡처도 같은 이유로 「1시간마다 동의 유도 알림」으로 구현돼 있다.
2. **카메라도 동일**하다. 백그라운드에서 몰래 찍을 수 없으며 자녀가 셔터를 눌러야 전송된다.
3. `ChildCommandService` 는 `dataSync` 포그라운드 타입을 쓴다.
   안드로이드 15 부터 `dataSync` 는 하루 6시간 제한이 있으므로, 스토어 배포 시
   `specialUse` 선언 + 심사가 필요하다. (`AndroidManifest.xml` 주석 참고)
4. 알림 권한(`POST_NOTIFICATIONS`)이 없으면 요청이 도착해도 자녀에게 보여 줄 방법이 없다.
   온보딩 2단계에서 반드시 받도록 되어 있다.

---

## 6. 서버 없이 테스트하기

디버그 빌드(`USE_MOCK=true`)에서 자녀 계정으로 로그인 후
**서비스 현황 탭 → 「요청 테스트 (개발용)」** 카드의 버튼을 누르면
실제 알림이 뜨고 캡처/촬영 전체 흐름이 그대로 돈다.

## 4. 자녀 안전 사건 동기화 (신규 · 제안)

자녀 홈 「최근 알림」에 남는 안전 사건을 보호자 기기로 나르는 경로다.
FCM 이 없으므로 자녀가 올리고 보호자가 폴링한다(15분 주기 + 대시보드 새로고침).

```
POST /children/{childId}/alerts            ← 자녀 앱 (사건 감지 즉시 + 15분 백업)
     body: [ { "id", "type", "title", "body", "occurredAt" } ]
     id 는 기기가 만든 고유값 — 같은 id 재업로드는 무시해야 한다.

GET  /children/{childId}/alerts?size=50    ← 보호자 앱
     → { "success": true, "data": [ …같은 스키마… ] }
```

`type`: `HARMFUL_BLOCK` | `GEOFENCE_EXIT` | `GEOFENCE_ENTER` | `SCREEN_TIME_LIMIT` | `GPS_OFF`

**아직 서버에 없어도 앱은 정상 동작한다.** 업로드 실패는 다음 주기에 재시도하고
조회 실패는 빈 목록으로 처리한다. 이 경우에도 보호자 앱은 기존 읽기 API 로
이탈·사용량·위치 끊김 3종을 스스로 판정한다(`ParentChildWatcher`).
단 **유해 사이트 차단은 이 경로가 없으면 보호자에게 전해질 수 없다**
(`POST /harmful-access/report` 는 쓰기 전용이라 보호자가 읽을 수 없다).
보호자 앱은 사건 id 로 합치므로 서버 알림과 겹쳐도 중복 푸시는 없다.

| 클라이언트 코드 | 역할 |
|---|---|
| `data/local/ChildAlertStore.kt` | 원본(자녀 홈 목록) + 업로드 대기열 |
| `background/ChildAlertUploadWorker.kt` | 자녀 → 서버 업로드 |
| `background/alert/ParentChildWatcher.kt` | 보호자 폴링 + 파생 판정 |

## 5. 카메라 촬영 요청·조회 (신규 · 제안)

부모 마이페이지 「카메라 촬영」이 쓰는 경로다. `screenshots` 와 완전히 대칭이다.

```
POST /children/{childId}/photos/request     ← 부모 앱. 202 를 기대한다.
     서버는 1절의 소켓/pending 큐로 CAMERA_REQUEST 를 자녀에게 민다.

GET  /children/{childId}/photos?page&size   ← 부모 앱 갤러리.
     → PageResponse<{ "id", "capturedAt", "imageUrl" }>
```

업로드(`POST /children/{childId}/photos`, multipart)는 기존 계약 그대로다.
