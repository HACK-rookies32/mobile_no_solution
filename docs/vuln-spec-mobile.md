# 모바일 클라이언트 의도 취약점 명세 (KIDHACK Mobile)

> 대상: `mobile/` (Android 네이티브 Kotlin + Compose, 별도 git 저장소)
> 진단 축: **OWASP Mobile Top 10 (2024) + OWASP MASVS + 전자금융기반시설 MOB-FIN**
> 이 문서는 백엔드 API 명세서 §10 "알려진 의도된 취약점"의 **모바일(클라이언트) 대응판**이다.
> 서버측 취약점(`[VULN-*]`, IDOR/금액변조/BFLA 등)은 백엔드 §10 및 소스 `[VULN-*]` 주석을 참조한다.

## 0. 규약 (백엔드 체계 이식)

- **태깅**: 코드에 `// [VULN-MOBILE-<도메인>-<n>] …` 인라인 주석. 백엔드 `[VULN-*]` 방식과 동일하게 "보존: 고치지 말 것" 규약을 함께 명시한다.
- **대조군**: `[SAFE-MOBILE-CARD]` — 카드 등록 E2E 암호화(`data/crypto/CardCrypto.kt`). 백엔드 `/api/wallet/cards` 대조군과 1:1 대응. **취약점을 넣지 않는 안전 구현**으로, 취약/안전 비교 학습용.
- **밀도**: 핵심 집중형 **13개**. 백엔드(~65 API축)와 총량이 다른 이유는 모바일이 MASVS/Mobile Top 10의 10개 카테고리 축이라 표면이 좁기 때문. 결제·자격증명 저장에 밀집(백엔드 결제 편중 이식).
- **검증**: 각 항목의 "검증방법" 컬럼 참조. **이번 배치는 네이티브 루팅/무결성/Frida 탐지(방어-우회 워크스트림)를 제외**했으므로, Frida 우회가 전제인 재현은 "방어 단계 이후"로 표기한다.

## 1. 취약점 표

| ID                       | 위치                                                                                                                 | OWASP Mobile / MOB-FIN                  | 유형                 | 내용                                                                                               | 익스플로잇 개요                                                                            | 검증방법                                                          | 백엔드 짝        |
| ------------------------ | -------------------------------------------------------------------------------------------------------------------- | --------------------------------------- | -------------------- | -------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------ | ----------------------------------------------------------------- | ---------------- |
| `VULN-MOBILE-STORE-1`  | `data/local/LocalStores.kt` (`SessionRepositoryImpl.save`)                                                       | M9 Insecure Data Storage                | 안전하지 않은 저장   | access/refresh 토큰을 DataStore`session`에 평문 저장. Keystore/EncryptedSharedPreferences 미사용 | 기기 파일 접근 시`files/datastore/session.preferences_pb`에서 토큰 탈취 → 세션 하이재킹 | 에뮬레이터/루팅기기에서`adb pull`로 pb 파일 확보 후 문자열 추출 | —               |
| `VULN-MOBILE-STORE-2`  | `AndroidManifest.xml` (`allowBackup=true`) + `res/xml/backup_rules.xml`, `data_extraction_rules.xml`         | M9                                      | 백업 노출            | 백업/기기이전 규칙 부재로 앱 데이터 전체가 백업 대상                                               | `adb backup`(또는 기기이전)으로 세션 토큰 포함 데이터 통째 추출                          | 미루팅 기기에서`adb backup` → `ab`언팩 → 토큰 확인          | —               |
| `VULN-MOBILE-STORE-3`  | `data/local/ChatMessageStore.kt`, `CapturedShotStore.kt`                                                         | M9                                      | 안전하지 않은 저장   | 채팅 대화 전문·화면 캡처 원본을`filesDir`에 평문 JSON/이미지 저장                               | 기기 접근 시 대화·캡처 유출(2차 피해: 아동 정보 노출)                                     | `adb pull files/chat`, `files/captures`                       | —               |
| `VULN-MOBILE-NET-1`    | `app/build.gradle.kts` (api 변형 `usesCleartextTraffic=true`) + `AndroidManifest.xml`                          | M5 Insecure Communication               | 평문통신/피닝없음    | 평문 HTTP 허용 +`networkSecurityConfig` 미설정 + 인증서 피닝 없음                                | 동일망 MITM으로 요청/응답·토큰 가로채기·변조                                             | mitmproxy/Burp 프록시 경유 트래픽 평문 확인                       | —               |
| `VULN-MOBILE-NET-2`    | `data/ws/ChildCommandFrame.kt` (`WsUrls.of`)                                                                     | M5                                      | 토큰 URL 노출        | 자녀 명령 WebSocket이 JWT를 쿼리스트링(`?token=`)에 실음                                         | 프록시·서버 액세스 로그에 JWT 잔존 → 토큰 재사용                                         | 프록시 로그/서버 access log에서`token=` 캡처                    | —               |
| `VULN-MOBILE-LOG-1`    | `di/AppModule.kt` (`Level.BODY`) + `data/ws/ChatWebSocketClient.kt`                                            | M9 / 민감정보 로그                      | 로그 노출            | api 변형에서 HTTP 본문 +`Authorization` 헤더 + 채팅 payload를 logcat 출력                        | logcat 접근(디버그 브리지/악성앱)으로 토큰·대화 수집                                      | `adb logcat`에서 `Authorization: Bearer`·payload 확인        | —               |
| `VULN-MOBILE-RE-1`     | `app/build.gradle.kts` (`optimization { enable = false }`)                                                       | M7 Insufficient Binary Protection       | 리버싱 용이          | R8 축소·난독화 완전 비활성 + release signingConfig 없음                                           | jadx/apktool로 평문 소스 복원 → 로직·엔드포인트·상수 분석                               | jadx로 APK 디컴파일, 클래스/문자열 평문 확인                      | —               |
| `VULN-MOBILE-SECRET-1` | `app/build.gradle.kts` (Maps 키 주입) + `AndroidManifest.xml`, `google-services.json`→`strings.xml`         | M2 Inadequate Supply Chain / M8         | 시크릿 하드코딩      | Google Maps·Firebase API 키가 Manifest/리소스에 박혀 APK에서 추출                                 | APK 언팩으로 키 확보 → 키 오남용/과금                                                     | apktool로 Manifest·`values.xml`에서 키 추출                    | —               |
| `VULN-MOBILE-AUTHZ-1`  | `feature/auth/register/RegisterViewModel.kt` + `RealRepositories.kt` (`signup`)                                | M3 Insecure Auth/Authz                  | 클라이언트 신뢰      | 가입`role`을 클라이언트가 결정해 전송                                                            | 요청 변조로`role=SUPER_ADMIN` 가입(서버 VULN-7 신뢰)                                     | 프록시로 signup 요청 role 변조 → 관리자 계정 생성                | `VULN-7`       |
| `VULN-MOBILE-PAY-2`    | `feature/parent/PayScreen.kt` (`charge`/`send`/`reclaim`) + QR 승인                                          | M4 Insufficient Input/Output Validation | 금액 클라이언트 결정 | 충전·이체·회수·QR 금액을 클라이언트 입력값 그대로 전송                                          | 서버 미검증(WALLET-1/7)과 결합해 금액 변조                                                 | 프록시로 amount 변조 후 서버 반영 확인                            | `WALLET-1/7`   |
| `VULN-MOBILE-PHONEVERIFY-1` | `feature/auth/register/RegisterViewModel.kt` (`confirmPhoneCode`) + `RealRepositories.kt` (`verifyPhoneCode`) + `Apis.kt` (`auth/phone/verify`) | M3 Insecure Auth/Authz | 응답 신뢰(변조 우회) | 코드는 send-code 응답에 노출(1). 인증은 `POST /auth/phone/verify` 의 `{valid}` 를 **클라가 신뢰**해 진행하고, signup 은 인증 여부를 확인하지 않음 | Burp 로 verify 응답 `valid:false→true` 변조로 틀린 코드 통과, 또는 verify 생략하고 signup 직행 | 프록시로 verify 응답 `valid` 변조 후 진행 / send-code 응답 `code` 확인 | `VULN-PHONEVERIFY-1/2` |
| `VULN-MOBILE-PAY-1`    | `navigation/Route.kt` (숨은 `dev/terminal`) + `feature/terminal/StoreTerminalScreen.kt` + `StoreTerminalApi` | M3 / M8                                 | 결제 진입점 노출     | 숨은 라우트에서`X-Store-Terminal-Token`만으로 임의 qrToken·금액 승인 UI 제공                    | 일반 유저가 단말 토큰 발급(WALLET-11)→토큰 없이/임의 승인(WALLET-15)→자녀 지갑 차감      | 앱 내`dev/terminal` 도달 후 임의 qrToken·금액 승인 시연        | `WALLET-11/15` |
| `VULN-MOBILE-COMP-1`   | `AndroidManifest.xml` (`ChildBootReceiver` exported·permission 없음, 과도 권한)                                 | M8 Security Misconfiguration / M6       | 컴포넌트/권한 노출   | exported 리시버 permission 미지정 + SYSTEM_ALERT_WINDOW·PACKAGE_USAGE_STATS 과도 권한             | 타 앱이 명시적 인텐트로 리시버 호출, 오버레이/사용통계 오남용                              | `adb shell am broadcast`로 리시버 호출, drozer 컴포넌트 점검    | —               |

## 2. 대조군 (SAFE)

| ID                   | 위치                          | 내용                                                                                                                                                                                                                        |
| -------------------- | ----------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `SAFE-MOBILE-CARD` | `data/crypto/CardCrypto.kt` | 카드 등록 E2E 하이브리드 암호화(AES-256-GCM + RSA-OAEP(SHA-256) + nonce/timestamp 재전송 방지).**의도적으로 취약점 없는 안전 구현.** 백엔드 `/api/wallet/cards` 대조군과 1:1. 다른 `VULN-MOBILE-*`와 비교 학습용. |

## 3. 이번 배치에서 의도적으로 미커버한 영역

"전부 커버한 것처럼" 읽히지 않도록 명시한다(백엔드의 대조군·범위 명시 방식 이식).

| 영역                                              | 사유                                                                                                                                                     |
| ------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 네이티브 루팅/무결성/Frida 탐지 (방어→우회 실습) | 프로젝트 목표#3의 핵심이나 NDK/.so 워크스트림이라 **별도 단계**로 분리(사용자 결정). 본 명세의 검증에서 Frida 전제 항목은 "방어 단계 이후"로 표기. |
| WebView / JS 브리지 (M7 일부)                     | 앱에 WebView가 없다(토스 결제창 제거 이력). 실습을 위한 인위적 재도입은 배제.                                                                            |
| M10 Insufficient Cryptography (카드 외)           | 카드 경로는 안전 대조군, 그 외 앱 자체 추가 암호 로직이 적어 별도 취약점 미배치.                                                                         |
| M1 Improper Credential Usage 심화                 | STORE-1/LOG-1으로 자격증명 저장/노출은 커버하되, 하드코딩 크리덴셜 등 심화는 미배치.                                                                     |

## 4. 도메인별 밀도 (백엔드 대비)

| 도메인(모바일)                     |    개수 | 백엔드 대응                                 |
| ---------------------------------- | ------: | ------------------------------------------- |
| 저장/자격증명 (STORE)              |       3 | 백엔드엔 없음(클라이언트 고유)              |
| 통신 (NET)                         |       2 | 백엔드엔 없음(클라이언트 고유)              |
| 결제 (PAY)                         |       2 | wallet/qrpay 19개 편중을 진입점으로 대응    |
| 로깅/시크릿/리버싱 (LOG/SECRET/RE) |       3 | 백엔드 설정 취약점(시크릿 커밋)과 성격 유사 |
| 인가 클라이언트 신뢰 (AUTHZ)       |       1 | VULN-7                                      |
| 인증 클라이언트 전용 (PHONEVERIFY) |       1 | VULN-PHONEVERIFY-1/2                        |
| 플랫폼/컴포넌트 (COMP)             |       1 | 백엔드엔 없음(클라이언트 고유)              |
| **대조군 (CARD)**            | 0(SAFE) | `/api/wallet/cards`                       |

## 5. 재현 절차 표본 (Frida 불필요)

1. **STORE-1/2 — 평문 토큰**: 에뮬레이터 부팅 → 로그인 → `adb pull /data/data/com.hack.kidhack.api/files/datastore/session.preferences_pb ./` → `strings`로 `access_token`/`refresh_token` 값 확인.
2. **LOG-1 — 로그 노출**: `adb logcat | grep -i "Authorization: Bearer"` 로 요청 헤더 노출 확인, 채팅 화면에서 `stomp recv … payload=` 확인.
3. **NET-1 — MITM**: mitmproxy 실행 + 기기 프록시 지정 → 로그인 트래픽 평문 확인(피닝 없음이라 인증서 신뢰만으로 복호).
4. **SECRET-1/RE-1 — APK 추출**: `apktool d app-api-debug.apk` → `AndroidManifest.xml`/`res/values/values.xml`에서 API 키, 평문 클래스명 확인.
5. **PAY-1 — 숨은 단말**: 앱에서 `dev/terminal` 라우트 도달 → 단말 발급 → 임의 qrToken·금액 승인 시연(서버 WALLET-11/15와 연동).
6. **PHONEVERIFY-1 — 휴대폰 인증 우회**: §6 참조.

> 참고: 백엔드 `[VULN-*]`와 결합하는 항목(AUTHZ-1↔VULN-7, PAY-1↔WALLET-11/15, PAY-2↔WALLET-1/7, PHONEVERIFY-1↔VULN-PHONEVERIFY-1/2)은 서버 동작이 함께 있어야 완전한 익스플로잇이 성립한다. 백엔드 명세: `3. 개발/API 명세/자녀보호앱_요구사항_API명세서_v1.4.md` §10.

## 6. VULN-PHONEVERIFY 상세 (휴대폰 인증 우회 · 백엔드 짝 · 캡처 증적)

### 6.1 개요

회원가입 시 "휴대폰 인증"이 있는 것처럼 보인다. 서버가 코드를 실제로 저장·대조하지만
(`POST /api/auth/phone/verify` → `{valid}`), **그 결과를 가입과 서버에서 묶지 않는다.** 인증 성사 여부는
클라이언트가 verify 응답을 신뢰하는 것으로 끝나고, `signup` 은 인증 여부를 전혀 확인하지 않는다.
게다가 코드는 send-code **응답 패킷에 그대로 노출**된다(화면·알림에는 코드를 절대 표시하지 않음 —
남의 번호면 SMS 를 못 받는다는 전제를 시뮬레이션).

### 6.2 관련 태그

| 태그 | 위치 | 내용 |
| --- | --- | --- |
| `VULN-PHONEVERIFY-1` (backend) | `PhoneVerificationService#sendCode`, `AuthController#sendPhoneVerifyCode`, `dto/PhoneVerifySendCodeResponse` | send-code 응답 body 에 6자리 코드 노출. 코드는 `email_verification`(Purpose=SIGNUP_PHONE)에 저장(V25) |
| `VULN-PHONEVERIFY-1b` (backend) | `PhoneVerificationService#verify` | 검증 시도 횟수 제한 없음(무제한 brute force) |
| `VULN-PHONEVERIFY-2` (backend) | `AuthController#verifyPhoneCode` → `{valid}`, `AuthService#signup` | 인증 결과가 가입과 서버에서 묶이지 않음: verify 는 `{valid}` 만 돌려주고 signup 은 인증을 확인 안 함 → verify 응답변조/생략으로 우회 |
| `VULN-MOBILE-PHONEVERIFY-1` (mobile) | `RegisterViewModel#confirmPhoneCode`, `RealRepositories#verifyPhoneCode`, `Apis.auth/phone/verify` | 클라가 verify 서버 응답(`valid`)을 신뢰해 진행 → 응답 변조로 우회 |

### 6.3 정상 흐름

1. 정보입력 화면에서 번호 입력 → 「인증요청」 → `POST /api/auth/phone/send-code`. 서버가 6자리 생성·저장(TTL 180초)하고 응답 `data.code` 로 반환(+ `[MOCK SMS]` 로그). 앱은 코드를 화면에 **표시하지 않는다**.
2. 사용자가 6자리 입력 → 「확인」 → `POST /api/auth/phone/verify {phoneNumber, code}` → 서버가 대조해 `{valid:true}` → 화면에 "✓ 인증되었습니다".
3. 「다음」은 클라 `phoneVerified` 가 true 여야 진행(UX 게이트). 이후 `signup` 은 인증 여부를 확인하지 않는다.

### 6.4 우회 (캡처 증적)

전제: `api` 변형 빌드(cleartext ON, `USE_MOCK=false`), 로컬 백엔드 `:8080` 연동, Burp/mitmproxy 경유(NET-1로 평문·피닝 없음). Nox 는 호스트 LAN IP 로 자동 연동(10.0.2.2 불가), adb 는 `nox_adb.exe` 로 통일.

- **① 코드 노출(VULN-PHONEVERIFY-1)**: 「인증요청」 후 `POST /api/auth/phone/send-code` 응답 본문에 `"code":"NNNNNN"` 가 실려 오는 것을 캡처. 화면에는 코드가 없는데 패킷에는 있다.
- **② 응답 변조(VULN-PHONEVERIFY-2)**: **틀린 코드**(예: 000000)를 입력하고 「확인」 → `POST /api/auth/phone/verify` 응답을 가로채 `"valid": false` → `true` 로 변조 → 앱이 "✓ 인증되었습니다" 로 통과하는 것을 캡처.
- **③ 인증 미결합**: verify 를 아예 거치지 않고 `POST /api/auth/signup` 만 호출해도 가입 성공(서버가 인증을 확인하지 않음)을 캡처.

> 서버가 코드를 실제로 검증하더라도, 그 결과가 가입과 묶이지 않고 클라가 응답을 신뢰하는 한 우회된다.
> 핵심 관문은 verify 응답이며, 이를 `valid:true` 로 바꾸면 틀린 코드로도 인증이 통과한다.
