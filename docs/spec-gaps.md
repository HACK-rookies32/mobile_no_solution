# 명세서 ↔ 피그마 갭 목록 (kidhack)

작성일: 2026-08-25  
근거: 부모 피그마 덤프(`ParentApp` / `PairingScreen` 등 14화면), 자녀 피그마 덤프(`CRegisterScreen` / `CCameraScreen` 등 6화면), 요구사항·API 명세서 v0.1(2026-08-24), 초기 설계 계획 8절 잠정안.

이 문서는 **구현 전에 확정이 필요한 불일치**만 다룬다. 존재하지 않는 엔드포인트를 가정해 호출하지 않는다. 잠정안은 코드를 막지 않기 위한 기본값이며, 백엔드·기획 합의로 뒤집힐 수 있다.

정본 우선순위(충돌 시):

1. 명세서의 요구사항 ID(REQ-M-*)와 모바일 API 4장
2. 피그마 카피·더미 데이터(화면 라벨, 토글, 금액)
3. 설계 계획 8절 잠정안 — 합의 전까지 Fake/로컬로만 우회

---

## 1. 개요

피그마는 부모 14화면 + 자녀 6화면 + 공용 인증 3화면을 보여 주지만, 명세서 모바일 API는 **인증·위치·차단 키워드·캡처 업로드·커뮤니티 CRUD·포인트/구독·사용량 보고**에 편중되어 있다. 그 결과:

- 화면에 있는 기능 중 **앱 통제·취침/스크린타임 정책·현금 지갑·카메라 업로드·문의 작성·모바일 공지**는 API가 없다.
- API가 있는 기능 중 **지오펜스 CRUD, 커뮤니티 작성/상세/댓글, 활동 내역**은 피그마 화면이 없다.
- 같은 개념이 **세 가지 어휘**(설정 키 4개 / 피그마 토글 5개 / 알림 `type` enum 5개)로 불린다.
- 페어링은 명세(`POST /pairing/code` 부모 발급)와 부모 `PairingScreen`(자녀 코드 `382194` 입력)이 **정반대**다. 가입 플로우 카피는 명세와 같다.

총 **17건**. 아래 12건은 요청 목록을 검증한 것이고, **GAP-13~17은 소스에서 추가로 확인한 항목**이다.

---

## 2. 요약

| ID | 유형 | 심각도 | 결정 주체 |
| --- | --- | --- | --- |
| GAP-01 | 설계↔명세 역전 | 치명 | 기획 + 백엔드 (명세 정본) |
| GAP-02 | 식별자/소셜 로그인 | 높음 | 기획 + 백엔드 |
| GAP-03 | API 부재 (최대 갭) | 치명 | 백엔드 (REQ-M-18) |
| GAP-04 | 모델 불일치 | 높음 | 백엔드 + 클라이언트 |
| GAP-05 | 도메인 불일치 | 높음 | 기획 + 백엔드 |
| GAP-06 | API 부재 | 중간 | 기획 (로컬 유지 가능) |
| GAP-07 | API 부재 | 중간 | 백엔드 |
| GAP-08 | 모바일 엔드포인트 부재 | 중간 | 백엔드 |
| GAP-09 | 세 어휘 + REQ 충돌 | 높음 | 기획 + 백엔드 |
| GAP-10 | 화면 부재 | 중간 | 디자인 + Android |
| GAP-11 | 푸시 계약 미정의 | 높음 | 백엔드 |
| GAP-12 | 플랫폼 한계 | 치명 | 기획 (범위 축소) |
| GAP-13 | 복구 플로우 불일치 | 중간 | 기획 + 백엔드 |
| GAP-14 | 프로필 필드 불일치 | 낮음 | 백엔드 또는 디자인 |
| GAP-15 | 채팅 부가기능 부재 | 낮음 | 기획 |
| GAP-16 | 페어링 시점 불일치 | 높음 | 백엔드 (인증 규약) |
| GAP-17 | 온보딩 화면 부재 | 높음 | 디자인 + Android |

유형 범례: **API 부재** = 명세서 4장에 대응 경로가 없음. **화면 부재** = API는 있으나 피그마에 화면이 없음. **역전/불일치** = 양쪽 다 있으나 계약이 다름.

---

## 3. 갭 상세

### GAP-01 페어링 방향 역전

**설계 증거**

- 부모 `PairingScreen` step 1 카피: 「연결 코드를 입력하세요」 / 「자녀 기기의 루키즈 앱에 표시된 6자리 코드」. 더미 값 `["3","8","2","1","9","4"]` → `382194`. 하단 버튼 「연결하기」, 「코드 다시 받기」(부모가 자녀 쪽 코드를 재요청하는 UI).
- 같은 파일 `RegisterScreen` 자녀 step 1: 「부모님 연결 코드」 / 「다음 단계에서 부모님이 보내준 코드를 입력해요」. step 2 6칸 입력 + 「부모님 앱에 표시된 6자리 코드를 입력해요」. 가입 완료 카드 더미 `example@email.com`.
- 자녀 `CRegisterScreen` step 1: 「부모님 연결 코드」 / 「가입 후 부모님이 보내준 코드를 입력하면 연결돼요」. 코드 입력칸은 없음(가입 이후로 미룸).
- **피그마 내부 모순**: 부모 `PairingScreen`은 자녀→부모 입력, 가입 화면(부모/자녀)은 부모→자녀 입력.

**명세 증거**

- REQ-M-04: 초대 코드 발급/입력으로 부모-자녀 계정 연결.
- `POST /pairing/code` (PARENT): `{ "pairingCode": "A1B2C3", "expiresIn": 300 }`.
- `POST /pairing/connect` (CHILD): `{ "pairingCode": "A1B2C3" }` → 연결된 부모 정보.

**Android 영향**

- 부모 페어링 UI를 코드 **표시 + 300초 타이머**로 뒤집어야 한다. 피그마의 6칸 입력·「코드 다시 받기」는 부모 화면에 두지 않는다.
- 자녀는 가입 후(또는 로그인 후) `pairingCode`를 보낸다. 만료 시 부모가 `POST /pairing/code`를 다시 호출한다.

**권장 해소안 (설계 8절 잠정안)**

- **명세를 정본으로 채택.** 부모는 코드 표시 + `expiresIn: 300` 카운트다운, 자녀는 6자리 입력.
- 피그마 `382194`는 더미일 뿐 프로토콜이 아니다. Fake 리포지토리는 명세 예시 `A1B2C3` / 300초를 쓴다.

**결정 주체:** 기획(카피 수정) + 백엔드(계약 유지). Android는 명세 방향으로 구현.

---

### GAP-02 `loginId` vs 이메일, Google 버튼

**설계 증거**

- 부모 `LoginScreen`: `Input label="이메일" type="email" placeholder="example@email.com"`. 구분선 「또는」 아래 「G Google로 계속하기」.
- 부모 `RegisterScreen` 부모/자녀 step 1 모두 라벨 「이메일」, 플레이스홀더 `example@email.com`. 완료 카드에도 `example@email.com`.
- 부모 `ForgotScreen`: 「가입하신 이메일로 재설정 링크를 보내드려요」, 「이메일로 찾기」.
- 자녀 `CLoginScreen` / `CRegisterScreen` / `CForgotScreen` 동일: 라벨 「이메일」, Google 버튼(로그인만), 재설정 링크.
- 마이페이지 더미: 부모 `kim@email.com`, 자녀 `minjun@email.com`.

**명세 증거**

- `POST /auth/signup` 필수: `loginId`, `password`, `name`, `role`, `phone`. `email`은 **선택(N)**.
- 예시: `{ "loginId": "parent01", "email": "p@ex.com" }`.
- `POST /auth/login` 필수: `loginId` + `password`만. Google/OAuth 경로 없음.
- `POST /auth/find-id`는 `name` + `phone`으로 `loginId` 반환. 이메일 링크 API 없음.

**Android 영향**

- 로그인 필드를 `loginId`(아이디)로 바꾸고 `type="email"`을 제거해야 한다. Google 버튼을 누르면 호출할 API가 없다.
- 회원가입에서 이메일을 받더라도 서버는 optional이다. 아이디 규칙은 명세서에 길이/문자 제약이 없어 클라이언트 validation을 임의로 정하면 안 된다.

**권장 해소안 (설계 8절 잠정안)**

- 식별자는 `loginId`. 피그마 라벨 「이메일」→「아이디」.
- **Google 로그인은 초기 구현에서 제외** (버튼 숨김 또는 disabled + 미지원 안내). 소셜 로그인을 넣을 거면 백엔드가 별도 계약을 추가해야 한다. 이 문서에서 경로를 만들지 않는다.

**결정 주체:** 기획(라벨) + 백엔드(소셜 여부). Android는 `loginId`로 바인딩.

---

### GAP-03 앱 통제 · 취침 · 스크린타임 — API 전무 (최대 갭)

**설계 증거**

- 부모 `PARENT_SCREENS`: `{ id: "appcontrol", label: "앱 통제" }`. `AppControlScreen` 필터 칩 「전체 / 차단 / 허용 / 제한」.
- 앱 더미: YouTube(동영상, 허용, 제한 `1시간`), TikTok(차단), 카카오톡(메신저, `무제한`), Instagram(차단), Chrome(`2시간`), 게임(차단). 토글은 빨강=차단 / 초록=허용. 「+ 앱 추가」.
- 부모 `DashboardScreen`: 「오늘 스크린타임」 `3h 24m`, 「차단된 앱」 `12개`, 「루키즈 앱 사용량 제한 도달」.
- 자녀 `CHomeScreen`: 스크린타임 `1h 24m / 3시간`, 「1시간 36분 남음」, 「제한: 3시간」. 보호 서비스 「기기 제한」 desc `취침 모드: 밤 10시`, 상태 **꺼짐**.
- 자녀 `CServicesScreen` 「기기 제한」: 스크린타임 제한 `하루 3시간`, 취침 모드 `밤 10시 ~ 오전 7시`, 현재 상태 `사용 가능`.
- 자녀 `CMypageScreen` 오늘 기록: 스크린타임 `1h 24m`.

**명세 증거**

- REQ-M-18(H): 「자녀 위치 확인, **앱 통제**, 유해 사이트 블랙리스트 관리, 자녀 프로필 관리」 — 요구사항에는 있으나 4장에 통제 API가 없다.
- REQ-M-17은 **사용량 기록/조회**다. 있는 API는 `POST/GET /children/{childId}/app-usage` (`usages[].appName`, `durationSec`, `groupBy=HOUR/APP/DAY`)뿐. 차단·일일 한도·취침 윈도우·앱 설치 목록 동기화 경로가 없다.
- 4장 목차 전체를 대조해도 `app-control`, `bedtime`, `screentime-limit`, `blocked-apps`에 해당하는 경로가 없다.

**Android 영향**

- `AppControlScreen`과 자녀 기기 제한 UI를 실서버에 붙일 수 없다. 사용량 바(YouTube `1h 20m` 등)만 `app-usage`로 채울 수 있다.
- 실제 차단·취침은 UsageStats + Accessibility / Device Owner / 디지털 웰빙 API가 필요하고, 서버 정책이 없으면 부모 설정이 자녀 기기에 전달되지 않는다. 플랫폼 한계는 GAP-12와 겹친다.

**권장 해소안 (설계 8절 잠정안)**

- `AppControlRepository` 인터페이스만 두고 **로컬 DataStore + Fake**로 피그마 더미를 재현.
- 백엔드에 정책 CRUD(앱별 block/limit, 일일 스크린타임, 취침 시작/종료)가 생기기 전에는 Retrofit 메서드를 **선언하지 않는다**.
- 대시보드 「차단된 앱 12개」는 Fake 상수. `app-usage`와 합치지 말 것(사용량 ≠ 통제).

**결정 주체:** 백엔드(엔드포인트 신설 여부). 기획은 REQ-M-18 범위를 유지할지 1차에서 뺄지 확정.

---

### GAP-04 유해 카테고리/AI vs 평탄 BLACK/WHITE 키워드

**설계 증거**

- 부모 `HarmfulScreen` 제목 「유해사이트 관리」. 카드 「성인 콘텐츠 자동 차단」 + 카피 「AI가 자동으로 유해 콘텐츠를 탐지하여 차단합니다」(토글 ON).
- 「차단 카테고리」 4개, 전부 ON: `성인·음란물`, `도박·사행성`, `폭력·혐오`, `마약·약물`. 키워드/URL 입력 UI 없음. 화이트리스트 UI 없음.
- 자녀 `CServicesScreen`: 동일 4카테고리 「성인·음란물 차단」 등, 「오늘 차단된 사이트: **3건**」.
- 부모 공지 더미: 「루키즈 2.5.0 업데이트 - AI 유해사이트 감지 강화」.

**명세 증거**

- REQ-M-11: 접근성 서비스 기반 URL 텍스트 **블랙리스트**, 부모 추가 차단, 적용 순서 **블랙리스트 → 화이트리스트**. AI/카테고리 언급 없음.
- `GET /children/{childId}/blocklist` 예시: `{ "blacklist": ["gambling.com", "성인"], "whitelist": ["school.ac.kr"] }`.
- `POST .../blocklist`: `listType` = `BLACK` | `WHITE`, `keyword` = URL 또는 키워드.
- `DELETE .../blocklist/{itemId}` — GET 응답은 문자열 배열이라 **itemId가 응답에 없다**(명세 내부 구멍).
- `POST /harmful-access/report`: `{ "matchedText", "accessedAt" }`.
- 관리자 `GET/POST/DELETE /admin/harmful-keywords`는 모바일 인증으로 쓸 수 없다.

**Android 영향**

- HarmfulScreen을 명세대로 구현하면 카테고리 토글이 아니라 키워드 리스트 + WHITE 입력이 된다.
- AccessibilityService는 텍스트 매칭만 가능. 「AI 자동 탐지」는 온디바이스 ML 또는 서버 분류 API가 없는데 둘 다 없다.
- DELETE를 쓰려면 GET이 id를 줘야 한다. 지금은 삭제 UI를 실호출로 연결하기 어렵다.

**권장 해소안 (설계 8절 잠정안)**

- 4카테고리를 **클라이언트 사전 키워드 묶음**으로 BLACK에 매핑하는 우회. AI 카피는 「키워드 기반 차단」으로 바꾸거나 Fake에서만 유지.
- WHITE 추가 UI는 HarmfulScreen에 없어도 리포지토리 계약에는 넣는다.
- itemId 문제는 백엔드가 GET 스키마를 `{ id, listType, keyword }[]`로 고치기 전까지 DELETE 실호출을 하지 않는다.

**결정 주체:** 백엔드(스키마·AI 여부) + 클라이언트(카테고리 매핑).

---

### GAP-05 루키즈 페이 현금 지갑·송금 vs 포인트/결제, 프리미엄 플랜 목록 없음

> **[2026-08-28 갱신] 구독/프리미엄 기능 미채택 — 코드에서 제거 완료.**
> `SubscriptionApi`·`SubscriptionRepository`·`Subscription` 모델·마이페이지 「프리미엄 회원」 배지를 모두 삭제했다.
> 아래 구독 관련 서술(REQ-M-16, `/subscriptions/*`, 플랜 목록 부재)은 **당시 기록**이며 현재 코드에는 대응물이 없다.
> `payments/*`(checkout·history)는 호출부 없는 죽은 코드로 남겨 두었다.

**설계 증거**

- 부모 `PayScreen` 제목 「루키즈 페이」. 「현재 잔액」 **`₩ 45,000`**. 탭 「충전 / 송금 / 내역」.
- 거래 더미: 「민준 용돈 송금」 `-10,000`(08.20), 「충전」 `+50,000`(08.15), 「구독 결제」 `-9,900`(08.01).
- 부모 `MypageScreen`: 배지 「프리미엄 회원」, 메뉴 「루키즈 페이」, 「결제 내역」. 사용자 `김지영 님`.

**명세 증거**

- REQ-M-15: 토스페이 **모방**, 실제 현금 교류 없이 DB 기록, 포인트 적립·사용.
- REQ-M-16: 구독은 결제되는 것처럼 처리(모방).
- 4.9: `POST /payments/checkout` (`amount`, `method` 예: `TOSS`, `productId`) → `{ "status": "PAID", "pointEarned": 1000 }`.
- `GET /points/balance` 예시 `{ "balance": 3000 }` — **원 단위 현금이 아닌 포인트**.
- `POST /points/use` `{ "productId": 9001, "point": 500 }` — 송금 상대(자녀) 필드 없음.
- `POST /subscriptions` `{ "planId": 1, "method": "TOSS" }`. `GET/DELETE /subscriptions/me`.
- **플랜 목록 API 없음** (`GET /subscriptions/plans` 같은 경로를 만들지 말 것). `planId`를 클라이언트가 알 방법이 명세에 없다.
- 4장에 송금·지갑·P2P 경로 없음.

**Android 영향**

- `₩ 45,000`을 `points.balance`에 그대로 넣으면 단위가 틀린다(명세 예시는 `3000`).
- 「송금」버튼은 바인딩할 필드가 없다. 「프리미엄 회원」은 `GET /subscriptions/me`의 `status`로만 근사 가능하고, 플랜명·가격표 UI는 하드코드 또는 Fake.

**권장 해소안 (설계 8절 잠정안)**

- 충전/내역은 `payments` + `points`로 근사. 잔액은 포인트로 표시(「P」 또는 「포인트」).
- **송금은 비활성**. 용돈 송금 더미는 Fake 전용.
- 구독 CTA는 Fake `planId = 1`만 사용. 플랜 목록 엔드포인트를 발명하지 않는다.

**결정 주체:** 기획(페이 제품 정의) + 백엔드(포인트 vs 현금, 플랜 목록).

---

### GAP-06 자녀 카메라/갤러리 API 없음 (화면 캡처와 별개)

**설계 증거**

- 자녀 탭 `CHILD_NAV_TABS`: `{ id: "camera", icon: "📷", label: "카메라" }`. `CHILD_SCREENS`: 「카메라 촬영」.
- `CCameraScreen` 탭 「카메라 / 갤러리」. 카메라: 「카메라 프리뷰」, 촬영 완료 토스트 「촬영 완료!」, 셔터/전환/썸네일 버튼.
- 갤러리 「최근 촬영」 더미: `학교에서`, `셀카`, `점심`, `공부 중`, `강아지`, `노을`.
- `CMypageScreen` 서비스 메뉴: 「카메라 갤러리」.
- 부모 `RegisterScreen` 자녀 역할 설명: 「카메라 사용 등을 해요」.
- 부모 `ScreenshotScreen` / 자녀 `CScreenshotScreen`은 「화면 캡처」이며 카메라와 화면이 분리되어 있다.

**명세 증거**

- 4.6 화면 캡처만 존재: 업로드 `POST /children/{childId}/screenshots` (multipart, `trigger=SCHEDULED/MANUAL`), 목록 GET(부모), 요청 POST.
- 카메라/갤러리/앨범/업로드 경로 없음. REQ 목록에도 자녀 카메라가 없다.

**Android 영향**

- CameraX + 로컬 MediaStore로만 구현 가능. 부모가 자녀 사진을 볼 API가 없다(캡처 목록과 합치면 안 됨).
- `CAMERA` 권한은 매니페스트에 필요하나, 서버 연동 TODO를 넣지 않는다.

**권장 해소안 (설계 8절 잠정안)**

- **순수 로컬 CameraX**. 갤러리 더미 6장은 Fake/로컬 파일. 업로드 API를 만들지 않는다.

**결정 주체:** 기획(원격 열람이 필요한지). 불필요하면 Android만으로 닫힌다.

---

### GAP-07 모바일 문의 작성·FAQ API 없음

**설계 증거**

- 부모 `ContactScreen` 제목 「관리자 문의」. 2×2 카드: 「1:1 채팅 문의」(빠른 답변), 「이메일 문의」(1~2일 이내), 「전화 상담」(평일 9~18시), 「FAQ」(자주 묻는 질문).
- 자녀 `CMypageScreen` 기타: 「도움말」.

**명세 증거**

- 모바일 4장에 `POST /inquiries`, `GET /faqs` 없음.
- 관리자 5.4만 존재: `GET /admin/inquiries`, `GET /admin/inquiries/{inquiryId}`, `POST /admin/inquiries/{inquiryId}/answer`. 문의 **생성** API가 관리자에도 없다(목록/상세/답변만).
- FAQ 전용 경로 없음.

**Android 영향**

- ContactScreen 네 칸은 전부 딥링크·정적 화면이거나 Fake. 1:1 채팅 문의를 기존 `/ws/chat`(부모-자녀)에 붙이면 역할이 섞인다.
- 전화/이메일은 `tel:` / `mailto:`로만 우회 가능. FAQ 본문을 가져올 방법이 없다.

**권장 해소안 (설계 8절 잠정안)**

- UI는 피그마대로 두고 탭 시 「준비 중」 또는 외부 채널. **문의 POST를 발명하지 않는다.**
- 백엔드가 모바일 작성 + 내 문의 목록을 추가하기 전에는 Retrofit에 admin 경로를 호출하지 않는다(관리자 JWT 필요).

**결정 주체:** 백엔드(모바일 문의 생성) + 기획(FAQ 콘텐츠 위치).

---

### GAP-08 모바일 공지 GET 없음 (`/admin/notices` + `exposeMobile`만)

**설계 증거**

- 부모 `NoticeScreen` 제목 「공지사항」. 더미:
  - `[필독] 개인정보처리방침 개정 안내` (2024.08.20, 필독 뱃지)
  - `루키즈 2.5.0 업데이트 - AI 유해사이트 감지 강화` (2024.08.15)
  - `자녀 기기 원격 잠금 기능 추가 안내` (2024.08.10) — **원격 잠금 API도 4장에 없음**(카피일 뿐, 기능 GAP로 승격하지 않음).
- 자녀 `CMypageScreen` 기타: 「공지사항」.

**명세 증거**

- 모바일 4장에 `GET /notices` 없음.
- `GET /admin/notices`는 Base `/api/v1/admin`, 관리자 인증.
- `POST /admin/notices` 필드 `exposeMobile` (boolean, N): 「모바일 공지 페이지 노출 여부」. 읽기 쪽 공개 API는 정의되지 않음.

**Android 영향**

- 공지 화면을 실호출하면 관리자 토큰이 필요하다. 부모 JWT로 `/admin/notices`를 치면 403이 예상된다.
- Fake에 위 3건을 넣고, Real 바인딩은 모바일 GET이 생긴 뒤로 미룬다.

**권장 해소안 (설계 8절 잠정안)**

- 모바일용 공개(또는 사용자 JWT) 공지 목록이 필요. 경로 이름은 백엔드가 정한다. 클라이언트가 `/admin/notices`를 재사용하지 않는다.

**결정 주체:** 백엔드.

---

### GAP-09 알림 설정 세 어휘, 「기기 전원 꺼짐」은 REQ-M-10과 충돌

**설계 증거**

- 부모 `NotificationsScreen` 「알림 설정」 토글 5개:
  1. 기기 전원 꺼짐 알림 — ON
  2. GPS 꺼짐 알림 — ON
  3. 유해사이트 접속 알림 — ON
  4. 금지 앱 실행 알림 — **OFF**
  5. 안전구역 이탈 알림 — ON
- 하단탭 라벨 「알림」은 설정 화면과 목록 화면이 한 id(`notifications`)로 붙어 있다. 대시보드 「최근 알림」은 별도 카드(유해 차단, 학교 도착, 사용량 제한).

**명세 증거 — 어휘 A: 설정 키 4개**

- `GET /users/me/notification-settings`: `{ "location": true, "harmfulAccess": true, "appUsage": false, "notice": true }`.
- `PATCH` 예: `{ "appUsage": true }`.
- REQ-M-06: 알림 설정(토글).

**명세 증거 — 어휘 B: 알림 목록 `type` enum**

- `GET /notifications?type=LOCATION|GPS_OFF|HARMFUL|ARRIVAL|NOTICE`.
- 설정 키 `location` / `appUsage` / `notice`와 enum `LOCATION` / `GPS_OFF` / `ARRIVAL` / `NOTICE`가 1:1이 아니다. `appUsage`에 대응하는 type이 없고, 피그마 「금지 앱 실행」도 type에 없다.

**명세 증거 — REQ-M-10 충돌**

- REQ-M-10: 「GPS 전원을 앱에서 임의로 끄지 못하도록 통제, 트래킹 불가 시 별도 알림 전송 **(기기 전원은 통제 대상 아님)**」.
- REQ-N-09: 「GPS 전원은 앱에서 임의 종료 불가하도록 통제」.
- `POST /devices/gps-status` 바디: `{ "gpsEnabled": false, "reportedAt": "..." }`. 설명: GPS 비활성 등 트래킹 불가. **전원(power) 필드 없음.** 엔드포인트 제목만 「GPS/전원 상태 보고」.

**Android 영향**

- 설정 화면 5토글을 4키에 우겨 넣어야 한다. 「기기 전원 꺼짐」을 켜도 서버에 보낼 키가 없고, 스펙은 전원 통제를 명시적으로 제외한다.
- `GET /notifications` 목록과 설정 화면을 한 탭에 섞으면 type 필터 UI가 설정 토글과 또 달라진다.
- 기기 전원 OFF는 앱이 실행되지 않아 자기 보고가 불가능하다. GPS OFF만 `gps-status`로 보고 가능하다.

**권장 해소안 (설계 8절 잠정안)**

- 설정 정본은 **4키**. 피그마 매핑:
  - `location` ← GPS 꺼짐 + (표시만) 안전구역 이탈
  - `harmfulAccess` ← 유해사이트 접속
  - `appUsage` ← 금지 앱 실행(키 이름은 다르지만 가장 가까운 슬롯)
  - `notice` ← 피그마 5토글에 없음. 설정 UI에 「공지 알림」을 추가하거나 숨김 기본 ON
- **「기기 전원 꺼짐」토글은 제거 또는 항상 비활성** — REQ-M-10 정본. 전원 알림 API를 발명하지 않는다.
- 목록 화면은 `GET /notifications`의 type enum을 그대로 쓰고, 설정 키와 별도 매핑 표를 코드 주석/이 문서로만 유지한다.

**결정 주체:** 기획(5토글 정리) + 백엔드(키·enum 정렬, gps-status 필드).

---

### GAP-10 명세에 있으나 피그마에 없는 화면: 지오펜스 · 커뮤니티 상세/작성/댓글 · 활동 내역

**설계 증거**

- 부모 `LocationScreen`: 지도 플레이스홀더 「지도 영역」, 현재 위치 `서울시 강남구 학동로 12길 (학교 근처)`, 오늘 경로 집→학교 도착→학원→현재. **지정 장소 추가/반경/목록 UI 없음.** 대시보드 「학교 도착 확인」은 알림 카피만.
- `NotificationsScreen`에 「안전구역 이탈 알림」토글만 있고, 구역을 그리는 화면은 없다.
- `CommunityScreen`: 목록 3건만 (`행복한엄마` 댓글 8/좋아요 24 등). 작성 FAB, 상세, 댓글 입력 없음. 카드 탭 핸들러도 없음.
- 부모 `MypageScreen` 메뉴: 내 정보 수정, 비밀번호 변경, 알림 설정, 페이, 결제 내역, 약관, 앱 버전 `2.5.0`. **활동 내역(게시글/좋아요/댓글) 없음.**

**명세 증거**

- 지오펜스: `GET/POST /children/{childId}/geofences`, `DELETE .../{geofenceId}`. POST 필드 `name`, `lat`, `lng`, `radius`, `notifyOnArrival`. REQ-M-09 도착 알림.
- 커뮤니티: `GET/POST /community/posts`, `GET/PATCH/DELETE .../{postId}`, `POST .../comments`, `POST .../like`, `POST /community/attachments`. REQ-M-13. **댓글 목록 GET은 별도 경로가 없고 상세에 포함된다고 가정할 수밖에 없음**(상세 응답 스키마 미기재).
- `GET /users/me/activities?type=POST|LIKE|COMMENT`. REQ-M-06.

**Android 영향**

- 위치 탭만 구현하면 지오펜스 API가 죽은 코드가 된다. GeofencingClient를 쓰려면 부모 UI에서 장소 CRUD가 필요하다.
- 커뮤니티를 목록만 띄우면 작성/좋아요/댓글 Retrofit이 호출되지 않는다.
- 활동 내역은 마이페이지 하위로 넣을 자리가 피그마에 없다.

**권장 해소안 (설계 8절 잠정안)**

- 지오펜스: 위치 화면 하위 「지정 장소 관리」신규 화면.
- 커뮤니티: 목록 → 상세/작성/댓글 신규 화면.
- 활동 내역: 마이페이지 「기타」또는 내 정보 하위 신규 행.
- 피그마에 없는 화면의 시각은 기존 `KidCard` 패턴으로 맞추고, 엔드포인트는 명세 4.4/4.7/4.2만 사용.

**결정 주체:** 디자인(화면 추가) + Android(구현). 백엔드 계약은 이미 있다. 댓글 GET 스키마만 백엔드 확인.

---

### GAP-11 캡처 요청 202 푸시 페이로드 미정의, 1시간 주기 설정 UI/API 없음

**설계 증거**

- 부모 `ScreenshotScreen`: 「실시간 화면 캡처」, 「자녀 기기의 현재 화면을 캡처 요청합니다」, 버튼 「캡처 요청 보내기」→ 「캡처 요청 중...」. 주기 설정 없음.
- 최근 내역 더미: `오늘 14:22` YouTube 시청 중, `오늘 11:05` 카카오톡, `어제 18:40` Chrome. 간격이 1시간이 아님.
- 자녀 `CScreenshotScreen`: 배너 「화면 캡처 요청이 왔어요!」 / 「부모님이 현재 화면을 캡처했어요 · 방금 전」. 안내 「부모님이 요청할 때 내 화면이 자동으로 캡처돼요」. 내역 배지 전부 「엄마 요청」/「아빠 요청」— **SCHEDULED 자동 캡처 행이 없음**.
- 자녀 홈 최근 알림: 「부모님이 화면 캡처를 요청했어요」.

**명세 증거**

- REQ-M-12: **1시간 단위** 캡처, 부모 희망 시 추가 촬영, S3.
- `POST .../screenshots` form: `file`, `capturedAt`, `trigger=SCHEDULED|MANUAL`.
- `POST .../screenshots/request`: 「부모가 즉시 캡처를 요청, 자녀 앱에 푸시 트리거」, **Response 202**, 응답 바디·FCM data 키 미기재.
- 주기(분)를 바꾸는 API/설정 키 없음.

**Android 영향**

- 부모 요청 → FCM → `MediaProjection` 캡처 → multipart 업로드 파이프를 짜려면 data 메시지 스키마(`childId`, `requestId` 등)가 필요하다. 지금은 202 Accepted만 있고 페이로드가 없다.
- 1시간 주기는 WorkManager 하드코드. 부모가 끄거나 간격을 바꿀 UI가 없고, 자녀 목록 UI도 MANUAL만 보여 준다.
- 자녀 캡처 내역 화면용 GET은 부모 전용이다. 자녀는 로컬 기록 또는 미표시.

**권장 해소안**

- 주기는 **1시간 고정**(명세). 설정 API를 발명하지 않는다.
- 푸시 페이로드는 백엔드가 문서화하기 전까지 FCM data를 Fake로만 시뮬레이션(`trigger=MANUAL`).
- 202 바디가 없어도 부모 UI는 「요청 중」→ 목록 폴링(`GET .../screenshots`)으로 완료를 기다린다.

**결정 주체:** 백엔드(FCM 스키마, 202 바디). Android는 폴링으로 우회 가능.

---

### GAP-12 플랫폼: GPS 강제 ON 불가, 취침/기기 제한은 접근성·Device Owner 필요

**설계 증거**

- 자녀 `CServicesScreen` 「위치 추적」: 「GPS 켜짐 ✓」, 「● 실행 중」. 토글로 GPS를 잠그는 UI는 없고 상태 배지만 있다.
- 같은 화면 「기기 제한」실행 중 + 취침 `밤 10시 ~ 오전 7시`. 홈에서는 기기 제한이 **꺼짐**.
- 부모 `AppControlScreen`은 앱 단위 차단 토글.

**명세 증거**

- REQ-M-10 / REQ-N-09: GPS를 앱에서 임의 종료 못 하게 **통제**.
- 실제 보고 API는 `POST /devices/gps-status` — 꺼진 것을 **보고**할 뿐 강제 ON이 아니다.
- REQ-M-11: 유해 차단 = 접근성 서비스.
- REQ-M-18 앱 통제, REQ-M-12 캡처 — 시스템 권한에 의존하나 비기능에 Device Owner/MDM 요구가 없다.

**Android 영향 (구현 한계, API 갭이 아님)**

- 일반 앱은 `LocationManager`로 GPS를 켤 수 없다. Settings 패널로 안내 + `gps-status` 보고가 전부다. 삭제 방지 정도는 Device Admin 정도로만 가능하고, GPS 잠금은 Device Owner/커스텀 ROM 수준이다.
- 취침·앱 차단을 실제로 걸려면 Accessibility(홈 복귀/오버레이), `PACKAGE_USAGE_STATS`, 또는 디지털 웰빙/Device Owner가 필요하다. Play 정책·삼성/픽셀 제약으로 1차에서 「완전 통제」를 약속하면 실패한다.
- 캡처는 `MediaProjection` 사용자 동의 + `foregroundServiceType=mediaProjection`. 동의 철회 시 1시간 주기도 멈춘다.

**권장 해소안 (설계 9절)**

- GPS: 강제 ON 없음. 상태 관찰 → `POST /devices/gps-status`. 부모 알림은 서버가 생성.
- 기기 제한: 1차는 상태 표시 + 접근성 기반 소프트 차단. Device Owner는 별도 제품 결정.
- 자녀 최초 로그인 권한 게이트(위치 → 백그라운드 위치 → 알림 → 사용정보 → 접근성 → 오버레이)는 피그마에 없으므로 GAP-17.

**결정 주체:** 기획(약속 문구 축소). Android는 한계를 코드/온보딩에 명시.

---

### GAP-13 (추가) 비밀번호 찾기: 이메일 링크 vs `loginId`+인증코드

**설계 증거**

- 부모 `ForgotScreen`: 헤더 「가입하신 이메일로 재설정 링크를 보내드려요」. 「이메일로 찾기」+ `재설정 링크 보내기`. 구분선 아래 「휴대폰 번호로 찾기」+ `인증번호 받기` (`010-0000-0000`).
- 자녀 `CForgotScreen`: 이메일 경로만. 「재설정 링크 보내기」, 「부모님께 도움을 요청할 수도 있어요」. 휴대폰 찾기 없음.
- 아이디 찾기 화면 자체가 피그마에 없다.

**명세 증거**

- REQ-M-03: 이메일/휴대폰 인증 기반 계정 복구.
- `POST /auth/find-id`: `{ "name", "phone" }` → `{ "loginId": "parent01" }`.
- `POST /auth/reset-password`: `{ "loginId", "verifyCode", "newPassword" }`. 「인증 코드 검증 후」라고만 되어 있고, **인증번호 발송 API가 없다**.
- 이메일 매직링크·토큰 경로 없음.

**Android 영향**

- 「재설정 링크 보내기」를 누르면 호출할 경로가 없다. 휴대폰 「인증번호 받기」도 발송 API가 없다.
- 아이디 찾기는 API만 있고 화면이 없다.

**권장 해소안**

- 화면을 명세에 맞춰 재구성: (1) 아이디 찾기 = name+phone, (2) 재설정 = loginId + verifyCode + newPassword.
- 인증번호 발송 경로가 명세에 없으므로, 발송 버튼을 넣지 않거나 Fake에서만 통과시킨다. 이메일 링크 API를 발명하지 않는다.

**결정 주체:** 기획 + 백엔드(발송 API 추가 여부).

---

### GAP-14 (추가) 자녀 프로필 필드: 생년월일·학교·기기 vs `alias`

**설계 증거**

- 부모 `ProfileScreen` 「자녀 프로필 관리」: 이름 `김민준`, 「12세 · 초등학교 6학년」. 기본 정보 행: 이름, **생년월일 `2014년 3월 15일`**, **학교 `강남 초등학교`**, **기기 모델 `Samsung Galaxy A54`**. 「프로필 사진 변경」.
- 페어링 완료 카드: `김민준`, `Galaxy A54`.
- 자녀 `CMypageScreen` 배지 「초등학교 6학년」.

**명세 증거**

- `PATCH /children/{childId}`: `name`, `profileImageUrl`, `alias` 등. birthDate/school/deviceModel 필드 없음.
- `GET /children/{childId}` 설명은 「자녀 프로필·기기 상태」뿐, 스키마 미기재.
- 기기 목록은 관리자 `GET /admin/members/{memberId}/devices`만.

**Android 영향**

- 생년월일/학교/기기 행을 저장할 모바일 PATCH 필드가 없다. 표시는 GET 상세에 있으면 읽기 전용, 없으면 Fake.

**권장 해소안**

- 1차는 `name` + `profileImageUrl`만 수정. 나머지 행은 읽기 전용 Fake. 필드 추가를 클라이언트에서 추측해 보내지 않는다.

**결정 주체:** 백엔드(필드 추가) 또는 디자인(행 삭제).

---

### GAP-15 (추가) 채팅 음성통화·첨부 vs 텍스트 WS

**설계 증거**

- 자녀 `CChatScreen`: 헤더 우측 `📞` 버튼. 입력줄 좌측 `📎`. 상대 표시 「엄마」.
- 부모 `ChatScreen`: 텍스트 입력 + 전송만. 통화/첨부 없음(부모-자녀 UI 비대칭).

**명세 증거**

- REQ-M-14: 웹소켓 1:1 채팅, 메시지 NoSQL.
- WS 이벤트: `SEND` / `MESSAGE` / `READ` / `ERROR`. 페이로드 `content` 문자열.
- 커뮤니티 첨부는 `POST /community/attachments`이지 채팅용이 아니다. 음성/WebRTC 경로 없음.

**Android 영향**

- `📞` `📎`를 누르면 연결할 API가 없다. 부모 화면과 맞추려면 자녀 쪽 아이콘을 빼는 편이 안전하다.

**권장 해소안**

- 1차는 텍스트+읽음만. 통화/첨부는 숨김. 채팅 파일 업로드 경로를 발명하지 않는다.

**결정 주체:** 기획.

---

### GAP-16 (추가) 페어링 시점: 가입 중 입력 vs `CHILD` JWT 필수

GAP-01(방향)과 별개로 **언제** 코드를 보내는지가 갈린다.

**설계 증거**

- 부모 `RegisterScreen` 자녀 step 2: 가입 완료 전에 6자리 「부모님 연결 코드」입력 후 「완료 →」.
- 자녀 `CRegisterScreen` step 1: 「**가입 후** 부모님이 보내준 코드를 입력하면 연결돼요」 — 입력칸 없이 「가입 완료!」.

**명세 증거**

- `POST /pairing/connect` 인증: **필요(CHILD)**. 가입(`POST /auth/signup`)은 인증 불필요. 즉 연결은 자녀 로그인 이후다.
- signup 바디에 `pairingCode` 필드가 없다.

**Android 영향**

- 가입 폼에 코드를 넣어도 signup 요청에 실을 필드가 없다. 가입 → 로그인 → connect 순서가 강제된다.
- 부모 가입 3step에는 코드가 없고, 이후 `PairingScreen`(GAP-01대로 코드 표시)에서 발급한다.

**권장 해소안**

- 자녀: 가입 완료 후 페어링 게이트 화면(6자리 입력)을 AuthGraph 또는 ChildGraph 진입 전에 둔다. signup에 코드를 넣지 않는다.
- 피그마 부모 가입의 자녀 step 2 코드 입력은 **이동**한다(가입 후).

**결정 주체:** 백엔드 규약이 이미 정본. 디자인이 가입 마법사를 수정.

---

### GAP-17 (추가) 자녀 권한 온보딩 화면 없음

**설계 증거**

- 자녀 화면 목록: 로그인, 회원가입, 비밀번호 찾기, 홈, 채팅, 백그라운드 서비스, 화면 캡처, 카메라, 마이페이지. **권한 설명/요청 마법사 없음**.
- `CServicesScreen`은 권한이 이미 켜진 상태를 보여 준다(위치 실행 중, GPS 켜짐, 기기 제한 실행 중, 유해 차단 실행 중).

**명세 증거**

- REQ-M-07~12, 17은 위치(백그라운드), 접근성, 사용정보 접근, 캡처(MediaProjection), 알림에 의존.
- 피그마/명세 모두 온보딩 스텝을 정의하지 않음. 없으면 자녀 기능이 권한 거부 상태로 빈 화면이 된다.

**Android 영향**

- 런타임 권한을 홈에서 흩어 요청하면 Play 정책·UX가 깨진다. 접근성/사용정보/오버레이는 설정 앱으로 보내는 전용 화면이 필요하다.

**권장 해소안 (설계 9절)**

- 자녀 최초 로그인 후 게이트: 위치 → 백그라운드 위치 → 알림 → 사용정보 접근 → 접근성 → 오버레이. 피그마에 없으므로 디자인 시스템 토큰으로 신규 화면.

**결정 주체:** 디자인 + Android. 백엔드 API는 불필요.

---

## 4. 근시일 구현 차단 체크리스트

아래 순서대로 막힌다. Fake로 화면만 띄울 때는 1·2·5만 먼저 합의해도 3단계(인증·페어링)까지 갈 수 있다. Real 전환은 3·4·8이 막는다.

1. **GAP-01 + GAP-16 페어링** — 부모 코드 표시(300초) / 자녀는 로그인 후 connect. 이 결정 없으면 AuthGraph를 그릴 수 없다.
2. **GAP-02 로그인 식별자** — `loginId` 라벨, Google 제외. 로그인 화면의 첫 필드.
3. **GAP-03 앱 통제 API** — REQ-M-18 핵심인데 경로가 없다. Fake 범위로 명시하지 않으면 부모 14화면 중 앱 통제가 공허하다. 백엔드 일정과 분리해 로컬 정책을 허용할지 확정.
4. **GAP-12 + GAP-17 권한·플랫폼** — 자녀 실기기에서 위치/차단/캡처가 동작하려면 온보딩과 「GPS 강제 불가」카피가 먼저다.
5. **GAP-13 계정 복구** — Forgot 화면을 이메일 링크로 구현하면 실호출이 불가능하다. 아이디 찾기 화면 추가 여부.
6. **GAP-09 알림 5토글** — 「기기 전원 꺼짐」제거와 4키 매핑. 알림 탭 구현 직전.
7. **GAP-04 유해 카테고리 vs 키워드** — HarmfulScreen 컴포넌트 구조(토글 vs 리스트). itemId 스키마.
8. **GAP-11 캡처 푸시** — 202 페이로드. 없으면 부모 요청은 폴링+Fake FCM.
9. **GAP-08 모바일 공지 GET** — NoticeScreen Real 바인딩 금지.
10. **GAP-07 문의/FAQ** — ContactScreen은 정적. 작성 API 기다림.
11. **GAP-05 페이/구독** — 송금 비활성, 잔액=포인트, plan 목록 없음. PayScreen 카피.
12. **GAP-10 없는 화면** — 지오펜스·커뮤니티 상세·활동. API는 있으므로 화면만 추가하면 Real 가능.
13. **GAP-06 카메라** — 로컬만. 서버 대기 없음.
14. **GAP-14 프로필 필드 · GAP-15 채팅 부가** — Fake/숨김으로 1차를 막지 않음.

합의 기록은 이 파일을 수정해 ID별로 「결정: … / 일자」한 줄을 남긴다. 엔드포인트가 새로 생기면 명세 버전을 인용하고, 여기 적히지 않은 경로를 클라이언트에 넣지 않는다.

## 결정 기록

- GPS 상태 보고(reportGpsStatus, POST /devices/gps-status) (2026-08-26): 위치기반 서비스 v0.2 도메인 분리와 무관하게 LocationRepository에 임시 유지. 추후 백엔드에 /device/event 등 기기 상태 전용 엔드포인트가 생기면 DeviceRepository로 이관 검토.
- 장소(지오펜스/목적지) 삭제 API (2026-08-26): v0.2 명세에는 없으나 팀 자체 프로젝트 특성상 백엔드팀에 DELETE /location/places/{placeId} 신설을 요청함. 클라이언트는 이 계약을 선반영해두었고, 백엔드 구현 전까지는 Fake로만 정상 동작. Real 연동 시 실제 경로/응답 형식을 백엔드와 재확인 필요.
- 실시간 추적 상태 조회 API 부재 (2026-08-26): PATCH /location/tracking/{childId}/realtime로 켜고 끌 수는 있으나 현재 상태를 조회하는 GET이 명세에 없음. 부모 화면은 진입 시 항상 OFF로 시작(서버 실제 상태와 불일치 가능). 백엔드에 GET /location/tracking/{childId} 신설 또는 GET /children/{childId}/dashboard(4.18)에 realtimeEnabled 필드 추가를 요청 예정. 삭제하지 않고 요청으로 해결하는 정책 유지.
- 위치기반 서비스 v0.2 반영 완료 (2026-08-26): 공유 레이어(Dtos/Models/Apis/Repositories) + 부모 화면(Location/Geofence) + 자녀 백그라운드(Tracking 단건화, 온디바이스 지오펜싱/배치큐 제거) 전 단계 완료. 신규 요청한 백엔드 API 2건 — DELETE /location/places/{placeId}(이미 반영), GET 실시간 상태 조회(요청 예정).
- 도착 알림 토글(notifyOnArrival) 필드 부재 (2026-08-26): POST /location/places 명세에 대응 필드 없음. UI 숨김 처리, 서버 전송값은 true 고정. 백엔드에 필드 추가 요청 예정, 추가되면 스위치 UI 복구.
- 장소 타입(GEOFENCE/DESTINATION) UI 단순화 (2026-08-26): 제품 요구사항상 모든 등록 장소가 진입+이탈 알림(도착/이동) 모두 필요하여, DESTINATION(도착만 감지) 개념은 사용하지 않기로 함. 클라이언트는 항상 type=GEOFENCE로 전송, 부모에게 타입 선택 UI 노출하지 않음. 명세 자체는 변경 없음(GEOFENCE는 기존 유효 값).
- GPS 상태 보고 /device/event 마이그레이션 (2026-08-27): POST /devices/gps-status(구버전) → POST /device/event(type=GPS_OFF)로 전환 완료. DeviceRepository 신설. GPS 재활성화(ON) 이벤트는 명세에 정의되어 있지 않아 전송하지 않음. POWER_OFF(기기 전원 꺼짐) 이벤트는 일반 앱이 안정적으로 감지할 방법이 없어(Device Owner 필요) 이번 작업 범위에서 제외. API 계약(DeviceEventRequest.type="POWER_OFF")만 준비해두고 실제 트리거는 기획/백엔드와 별도 논의 필요.
- 대시보드 통합 API 연동 (2026-08-27): GET /children/{childId}/dashboard 신설 반영. DashboardViewModel이 location/notifications를 개별 호출하던 것을 통합 호출로 교체(DashboardRepository/ChildDashboard 신설). usages(앱 사용량)는 응답에 없어 기존 app-usage 호출 유지. blockRules(필터링 규칙)는 데이터만 받아오고 화면 미노출 — 기존 HarmfulScreen의 BlocklistApi(blacklist/whitelist 구조)와는 다른 도메인이라 통합하지 않음(GAP-04 별개 사안).
- 앱 사용량 API /monitoring/usage 마이그레이션 + 상세 화면 신규 제작 (2026-08-27): children/{childId}/app-usage(구버전) → POST/GET /monitoring/usage(v0.2)로 전환. AppUsageRepository.list() 반환 타입을 groupBy 의미를 반영한 AppUsageStat(key, durationSec)로 변경. report()는 시그니처 유지, recordedAt은 리포지토리 계층에서 채워 자녀 측 AppUsageWorker.kt 변경 불필요. 대시보드 '전체 보기' 버튼이 앱 통제 화면으로 잘못 연결되어 있던 것을 신규 AppUsageDetailScreen(APP/HOUR/DAY 전환 가능)으로 정정. (부수: AppUsageCollector의 서버 폴백이 list()를 쓰므로 AppUsageStat→AppUsage 매핑 1줄만 반영 — 명세의 "AppUsageCollector 무영향" 전제 보정.)
