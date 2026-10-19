# M7/7D 점검표 — 브라우저 인증

## 1. 우회 ①~⑨ 와 변이 실측

계약 설계 검토 (2) 의 우회 아홉에 각각 test 를 붙이고, **그 test 가 바꿔치우는 변이 아래에서
RED 인지**를 쟀다. 변이는 더하기가 아니라 바꿔치우기이고(초록인 변이는 아무것도 증명하지
않는다), 복원은 사본에서 했다(`git checkout --` 금지 — 미커밋 산출물을 함께 지운다).
변이별 적용 사실은 **사본 대조**(`git diff --no-index --numstat <사본> <현재>`)로 센다 — 추적되지
않는 새 파일에는 보통의 `git diff --numstat` 이 아무것도 내지 않고, 추적 파일에서는 변이가 아니라
이 slice 전체의 변경을 센다(dry-run 에서 실측해 고쳤다). 결과는 **종료 코드**로 받는다.
**아래 「변이 아래」 열의 정본은 `commands.md` 의 「변이 실측」 절**이고, 이 표는 그 결과를 옮긴다.

**변이 실측의 전제는 「변이 전 그 test 가 초록」이다.** 그 초록은 같은 라운드의 **전건 실행**이
낸다 — 전제가 깨진 라운드의 RED 는 변이에 귀속되지 않는다. 1라운드에서 실제로 그 일이 있었다:
web 단위 test 가 환경 문제(Node·jsdom 의 `Request` 가 절대 URL 을 요구한다)로 깨진 상태였고,
그 라운드의 web 변이 둘(M12·M13)은 RED 였지만 **변이 때문이 아니었다**. 그 전제를 고친 뒤
전건을 다시 돌리고 변이도 다시 쟀다(`commands.md` 두 라운드).

| 우회 | 막는 것 | test (클래스 · 이름) | 변이(바꿔치움) | 변이 아래 |
| --- | --- | --- | --- | --- |
| ① 쿠키 없이 상태 변경 | 기본 거부 | `BrowserSessionAuthenticationTest` 「우회 ① — 쿠키 없이 상태를 바꾸는 요청은 401 이다」 | 필터의 거부 분기를 `chain.doFilter` 로(기본 허용) | **RED**(M1) |
| ② CSRF 없음·틀림 | 상태 변경 보호 | 같은 클래스 「우회 ② — 쿠키는 있고 CSRF 헤더가 없거나 틀리면 거부한다」·「우회 ② — 쿠키만으로는 기존 쓰기 endpoint 도 부를 수 없다」·「우회 ② — 읽기 메서드는 CSRF 를 요구하지 않는다(면제 집합이 그 셋이다)」 · `BrowserAccessWorkflowTest` 「우회 ② …」 | 면제 판정을 상수 참으로(전 메서드 면제) | **RED**(M2) |
| ③ 로그아웃 뒤 재사용 | 서버 쪽 폐기 | `BrowserAccessWorkflowTest` 「우회 ③ …」 · `ProductionBrowserAccessTest` 「우회 ③ …」 | `signOut` 의 폐기를 사용 시각 갱신으로 | **RED**(M3) |
| ④ 만료 세션 | 유휴·절대 두 축 | `BrowserAccessWorkflowTest` 「우회 ④ — 유휴 창…」·「우회 ④ — 계속 써도 절대 창…」 | 유휴 판정을 상수 참으로(M4) / 절대 축은 **기제가 둘**이라 단독 변이가 초록이다(M4b·M4c) — 둘을 함께 걷는 M4d | **RED**(M4 · M4d) · 아래 주 |
| ⑤ 세션 고정 | 로그인 재발급 | `BrowserAccessWorkflowTest` 「우회 ⑤ …」 · `BrowserSessionAuthenticationTest` 「우회 ⑤ …」 | 재발급의 기존 세션 폐기를 무관한 갱신으로 | **RED**(M5) |
| ⑥ 연속 실패 | 계수 + 잠금 | `BrowserAccessWorkflowTest` 「우회 ⑥ — 연속 실패가 상한에…」·「우회 ⑥ — 상한 직전까지는…」 · `ProductionBrowserAccessTest` 「우회 ⑥ …」 | 잠금 분기를 「계수만 올림」으로 | **RED**(M6) |
| ⑦ 인증 없이 열린 endpoint | 구성 + 허용 목록 하나 | `BrowserSessionAuthenticationTest` 「우회 ⑦ — 무인증 허용 목록은 로그인 하나다(집합 등식)」·「…등록된 모든 매핑이 무자격 요청을 거부한다(기계 전수)」·「…흉내 낸 표기로 다른 endpoint 에 닿지 못한다」 · `OperatorAuthenticationTest`(기존, 무편집) | 허용 목록을 둘로 넓힘 | **RED**(M7) |
| ⑧ DB 평문 세션 식별자 | 타입이 해시만 받는다 | `AccessStorePersistenceTest` 「저장된 세션 식별자는 쿠키 값의 해시다」 · `ProductionBrowserAccessTest` 「우회 ⑧ — DB 에 쿠키 평문이 없다(열 전수)」 · `BrowserSessionAuthenticationTest` 「저장되는 것은 쿠키 값의 해시뿐이다」 | `digest()` 를 평문 그대로 반환으로 | **RED**(M8) |
| ⑨ 응답·로그 노출 | 단방향 값만 내보낸다 | `BrowserSessionAuthenticationTest` 「우회 ⑨ …」 · `ProductionBrowserAccessTest` 「우회 ⑨ …」 | 응답의 CSRF 자리에 쿠키 값을 싣기 | **RED**(M9) |

**④ 축의 주 — 기제가 둘이라 단독 변이로는 RED 가 되지 않는다(측정으로 드러났다).** 절대 창은
두 자리가 지킨다: ⓐ 유휴 시한을 절대 시한으로 **클램프**하는 것 ⓑ 절대 시한을 직접 보는 것.
ⓐ만 걷으면 ⓑ가 받아내고(M4c 초록 — 실측), ⓑ만 걷으면 ⓐ가 받아낸다(M4b 초록 — 실측) —
**둘을 함께** 걷는 변이(M4d)는 **RED** 다(실측) — 그 둘이 기제의 전부다. 그 사실을 알고
test 도 고쳤다: 앞 판은 마지막에 절대 창 길이를 한 번에 밀어 **유휴도 함께** 만료시켰고
(거부의 사유가 절대 창이 아니었다), 지금은 유휴 창보다 짧은 한 걸음으로 절대 시한만 넘는다.
심층 방어의 대가는 「단독 변이가 안 잡힌다」이고, 그 대가를 문장이 아니라 세
측정(M4b·M4c·M4d)으로 등재한다.

**덧붙인 두 축**(계약의 아홉 밖, 같은 방법으로 실측)

| 축 | test | 변이 | 변이 아래 |
| --- | --- | --- | --- |
| 쿠키 속성 | `BrowserSessionAuthenticationTest` 「로그인은 쿠키를 HttpOnly·Secure·SameSite Strict·Path 로 내고…」 | `httpOnly(true)` → `false` | **RED**(M11) |
| 열 단위 GRANT | `AccessPrivilegeTest` 아홉 | V20 의 열 단위 UPDATE 를 표 단위로 넓힘 | **RED**(M10) |
| 쿠키의 교차 출처 전송 | `client.test.ts` 「쿠키를 교차 출처로 보내지 않는다 — include 가 아니다」 | `credentials` 를 `'include'` 로(M12) | **RED**(M12) |
| 화면의 CSRF 헤더 | `client.test.ts` 「상태를 바꾸는 요청에는 기억한 CSRF 값을 붙인다」 | 면제 판정을 상수 참으로(M13) | **RED**(M13) |

**code-review r1 수정 라운드가 더한 다섯 축**(같은 방법으로 실측 — `commands.md` r2 절이 정본)

| 축 | test | 변이 | 변이 아래 |
| --- | --- | --- | --- |
| 실패 계수의 **원자성** | `AccessConcurrencyTest` 「동시 실패 K 건은 계수를 K 올린다」 | 계수 증가를 읽은 값에서 계산한 절대값으로(M14) | **RED**(M14) |
| 늦게 끝난 요청이 **잠금을 지우지 않는다** | 같은 클래스 「늦게 끝난 실패는…」·「늦게 끝난 성공은…」 | 상한 아래 잠금 보존 → NULL 덮어쓰기(M15) / 성공 초기화의 잠금 조건 제거(M16) | **RED**(M15 · M16) |
| 긴 비밀 문구의 **거부 모양** | `AccessStorePersistenceTest` 「한계를 넘는 긴…」·「긴 비밀 문구의 로그인은…」 · `ProductionBrowserAccessTest` 「한계를 넘는 긴…401…」 | 긴 입력 전략을 던지는 쪽(옛 기본값)으로(M17) | **RED**(M17) |
| 로그인이 **커넥션을 쥐지 않는다** | `AccessConcurrencyTest` 「로그인의 느린 검증은 커넥션을 쥐지 않는다 — 풀이 하나여도…」 | 로그인 경계를 한 트랜잭션(옛 모양)으로(M18) | **RED**(M18) |
| 재발급의 **폐기가 실 DB 에서 선다** | `AccessStorePersistenceTest` 「로그인 재발급은 앞 세션을 폐기한다 — 포트 한 호출이다」 | 폐기 문장의 계정 바인딩을 무관한 값으로(M19) | **RED**(M19) |
| 문서의 **CSRF 선언**(상태 변경 전수) | `OpenApiContractTest` 「문서가 무인증으로 여는…집합 등식」·「그 밖의 상태 변경 operation 은…쿠키 단독 대안 0」 | operation 하나에서 CSRF 요구 제거(M20) / 로그인의 무인증 선언 제거(M21) | **RED**(M20 · M21) |

### 아홉을 **구조로** 닫은 자리 (문자열 술어가 아닌 층)

- **세션 식별자는 해시로만 저장된다 — 타입이 진다.** 저장 포트의 서명이 `BrowserSessionDigest`
  라 어댑터에 원문이 닿을 **통로가 없다**(주석 규율이 아니다). ⑧ 의 변이는 그래서 값 타입의
  `digest()` 를 고쳐야 했고, 그 한 줄이 세 test 를 동시에 붉혔다.
- **세션을 만드는 길이 하나다.** `NewBrowserSession` 의 생성자가 `internal` 이라 모듈 밖에서는
  세션을 만들 수 없고, 모듈 안 호출자는 `signIn` 하나다. 「로그인 use case 밖에서 세션을 만든다」가
  타입 체계에서 표현되지 않는다.
- **요청 경로는 계정을 만들거나 해시를 바꿀 수 없다 — 두 겹.** ① 포트가 갈려 있다
  (`AccountStorePort` 에 insert·replaceHash 가 없다) ② V20 의 열 단위 GRANT(앱 역할은 `account`
  에 INSERT 가 없고 UPDATE 는 실패 계수·잠금 두 열에만). `AccessPrivilegeTest` 가 둘째 겹을
  직접 쏘아 잰다.
- **app 은 접근 포트를 직접 부를 수 없다.** `app.port-call.ports` 에 접근 포트 다섯을 더하고
  허용 쌍은 **하나도** 더하지 않았다 — 세션을 열고 폐기하는 것은 use case 뿐이다.
- **꽂힌 인증 경로를 실측으로 잠근다.** 기본 거부 필터가 인증 경로를 **구성으로** 받으므로
  (계약 설계 검토 (1)), 그 자리는 「다른 몸통을 꽂을 수 있다」는 뜻이다. 출하 조립에 실제로
  꽂힌 인증기 집합을 `ProductionBrowserAccessTest` 가 순서까지 등식으로 잰다.
- **실패 계수는 스냅숏에 없다**(r2, H-1). 「읽어서 더한 값을 되쓰는」 형태가 다시 서려면 포트의
  읽기 모델에 그 값이 있어야 하는데, 그 필드를 **없앴다**. 계수의 계산은 SQL 한 문장 안에만
  있고, 그 문장이 상한·잠금을 같은 조건으로 가른다.
- **문서의 무인증 집합이 필터 상수와 등식이다**(r2, M-3). 「어느 operation 이 CSRF 를
  선언했는가」를 이름 목록으로 세지 않는다 — 문서에서 그 자리를 모아 `OPEN_ROUTES` 와 양방향
  으로 견주고, 「상태를 바꾸는 메서드」의 정의도 production 상수의 **여집합**으로 받는다.
  목록을 두 벌 두면 그 둘이 갈린다.
- **느슨한 경계의 호출자가 하나로 못박힌다**(r2, M-2). 로그인만 문장별 트랜잭션으로 도는 자리
  (`inSignInScope`)는 use case 를 빌려주는 열린 모양이지만, 그것을 부를 수 있는 쌍이
  `app.adapter.member-call-pairs` 에 **하나**뿐이다(fail-closed) — 다른 긴 작업이 그 자리로
  들어오려면 게이트 설정을 먼저 바꿔야 한다.

## 2. (2b) 값 획득 축 — 새 public 표면 전수

「경계로 처리」 행도 실측했다. 등급은 **밖에 허락하는 것**의 크기다.

### workflow/access — 값 타입

| 표면 | 밖에 허락하는 것 | 등급·닫는 자리 |
| --- | --- | --- |
| `LoginName(String)` · `.value` | 로그인 식별자를 감싸고 읽는다 | 낮음 — 비밀이 아니다(UNIQUE 제약이 중복을 막는다) |
| `AccountId(Long)` · `.value` | 계정 번호 | 낮음 — 추측해도 권한이 없다(감사 라벨에 그대로 쓴다) |
| `ClearPassphrase.wrap(String)` | 자기 값을 감싼다 | 낮음 — 감싸는 것만으로 아무것도 얻지 못한다 |
| `ClearPassphrase.hashInput()` | **원문 사본을 얻는다** | **높음** — 이 slice 의 유일한 원문 획득 표면. **production 호출자는 해시 어댑터 하나(두 메서드)뿐**이고 그 둘은 쓰고 나서 버퍼를 0 으로 덮는다(실측: 호출 자리 전수). 보유자 집합의 상한을 새 게이트가 등식으로 잠근다(비밀값 축의 새 키 쌍 `…passphrase-types`·`…passphrase-referencers`, 뿌리 셋) |
| `ClearPassphrase.toString()` | 가린 문면(`***`) | 닫는다 — 재정의로 원문이 로그에 실리지 않는다 |
| `PassphraseHash(String)` · `.encoded` | 해시 문면을 짓고 읽는다 | 중간 — 되돌릴 수 없고 비교를 제공하지 않는다(검증은 어댑터의 라이브러리 경로 하나) |
| `BrowserSessionKey(String)` | 임의 값을 세션 키로 감싼다 | 낮음 — 저장소에 그 해시가 없으면 거부된다(값은 256비트 난수) |
| `BrowserSessionKey.cookieValue()` | 쿠키에 실을 원문 | 중간 — 소비자는 쿠키 쓰기 한 자리. 그 자리를 test 가 응답 노출로 잰다(⑨) |
| `BrowserSessionKey.digest()` · `.deriveStamp()` | 단방향 유도값 | 낮음 — 둘 다 SHA-256 이라 원문을 되돌릴 수 없다 |
| `BrowserSessionDigest(String)` · `.hex` / `CsrfDigest(String)` · `.hex` | 해시 값을 짓고 읽는다 | 낮음 — 해시를 지어도 그 원문을 모른다 |
| `CsrfDigest.matches(CsrfDigest)` | 상수 시간 비교 | 닫는다 — 비교의 유일 경로이고 `MessageDigest.isEqual` 만 쓴다 |
| `CsrfStamp.wrapOrNull(String?)` · `.headerValue()` · `.digest()` | CSRF 값을 감싸고 내보낸다 | 낮음 — 세션 값에서 유도된 단방향 값이라 그것으로 세션이 되지 않는다 |
| `BROWSER_ACCESS_POLICY` | 정책 값을 읽는다 | 낮음 — 값의 정본은 evidence 문서다 |

### workflow/access — 포트·use case

| 표면 | 밖에 허락하는 것 | 등급·닫는 자리 |
| --- | --- | --- |
| `NewBrowserSession` 생성자 | **세션 한 벌을 만든다** | **차단** — `internal`. 모듈 밖 0, 모듈 안 호출자 = `signIn` 하나 |
| `AccountSnapshot` · `BrowserSessionSnapshot` 생성자 | 스냅숏을 짓는다 | 낮음 — 지어도 포트가 반환한 값이 아니면 판정에 들어가지 않는다(저장 층이 만드는 읽기 모델). r2: `AccountSnapshot` 에서 **실패 계수 필드를 뺐다**(읽어서 더하는 형태의 자리를 없앤다) |
| `AccountStorePort.registerFailure` · `.clearFailures`(r2 서명 변경) | 실패 하나를 **올린다** / 잠겨 있지 않을 때만 비우고 비웠는지를 돌려준다 | 중간 — 포트를 쥔 주체만(app 은 `app.port-call.ports` 로 직접 호출이 막혀 있다). 서명이 「이 값으로 둔다」가 아니라 「올린다」라 절대값 쓰기가 표면에 없다 |
| `BrowserAccessWorkflow(...)` 생성자 | use case 를 조립한다 | 중간 — 포트 **구현**을 쥐어야 한다. app 은 포트를 쥐지 못한다(의존 게이트 + port-call 게이트) |
| `.signIn` | 올바른 비밀 문구로만 세션 발급 | 설계된 표면 — 잠금·계수·재발급이 함께 돈다 |
| `.authenticate` · `.describe` · `.signOut` | **이미 세션 값을 쥔** 호출자의 판정·조회·폐기 | 낮음 — 인자가 그 값이다(새 권한을 만들지 않는다) |
| `AccountProvisioning(...)` · `.create` · `.rotate` | **계정 생성·해시 교체** | **높음** — 조립이 소유자 연결만 주고 그 조립은 조건부 설정(명령 모드)에만 있다. DB 권한이 2차. `rotate` 는 세션 폐기를 **함께** 진다(분리 상태가 표면에 없다) |
| `ProvisionOutcome` 넷 | 결과 어휘 | 낮음 |
| `SessionDescription` · `IssuedSession` 생성자 | 값 묶음 | 낮음 |
| `CsrfDemand.Exempt` · `.Required(CsrfStamp?)` | CSRF 요구를 만든다 | 중간 — `Exempt` 를 지어 넘기면 CSRF 를 건너뛴다. 그 호출자는 세션 인증기 하나이고 ② 의 변이가 바로 그 형태다(RED) |

### adapters/access

| 표면 | 밖에 허락하는 것 | 등급·닫는 자리 |
| --- | --- | --- |
| `JdbcAccountStore(ConnectionSource)` | 계정 읽기·실패 계수 갱신 | 중간 — `ConnectionSource` 는 `sealed` 이고 구현이 `internal` 이라 모듈 밖에서 임의 연결을 공급할 수 없다 |
| `JdbcAccountProvisioningStore(...)` | **계정 INSERT · 해시 UPDATE** | 높음 — 같은 이유로 연결을 쥔 주체만. 앱 역할로는 두 문장이 42501 이다(실측) |
| `JdbcBrowserSessionStore(...)` | 세션 행 쓰기·조회 | 중간 — `openReplacing()` 의 인자를 만들 수 없다(`internal` 생성자). r2: 폐기와 등록이 **한 메서드**라 「폐기 없이 여는」 호출이 표면에 없다 |
| `BcryptPassphraseHasher(Int)` | 해시·검증 | 낮음 — 입력을 받아 값을 낸다(저장하지 않는다) |
| `SecureBrowserSessionKeySource()` | 난수 세션 값 발급 | 낮음 — 값만 만든다(저장은 포트의 일). **주입 자리는 `internal` 주 생성자**라 난수원을 밖에서 못 꽂는다 |
| `BrowserAccessTransaction` · `AccountProvisioningTransaction` (interface) | use case 를 트랜잭션 안에서 대여 | 설계된 경계 — 저장소·연결을 돌려주는 공개 경로가 없다 |
| `JdbcBrowserAccessTransaction(...)` · `JdbcAccountProvisioningTransaction(...)` | 경계 조립 | 중간 — `TransactionBoundary`(즉 `DataSource`)를 쥔 주체만. 요청 경로와 명령 경로가 **다른 타입**이라 서로 주입될 수 없다. r2: 앞엣것이 `DataSource` 를 하나 더 받는다(로그인의 문장별 공급원) — **같은 풀**이고, 그 공급원 타입은 `internal` 이라 밖에서 다른 것을 꽂을 수 없다 |
| `BrowserAccessTransaction.inSignInScope`(r2) | **문장별 트랜잭션** 아래에서 use case 를 빌린다 | 중간 → **쌍으로 닫는다** — 호출자가 `app.adapter.member-call-pairs` 에 하나뿐이다(fail-closed). 원자성이 필요한 묶음은 포트 한 호출 안이라 이 느슨함이 쪼갤 쌍이 없다 |

### app

| 표면 | 밖에 허락하는 것 | 등급·닫는 자리 |
| --- | --- | --- |
| `RequestAuthenticator` (interface) | 인증 경로를 구현한다 | 중간 — 꽂는 것은 조립뿐이고 **꽂힌 집합을 출하 조립 test 가 순서까지 등식으로** 잰다 |
| `AuthenticationResult.Authenticated(String)` | 통과 판정을 짓는다 | 중간 — 지어도 필터가 부른 인증기가 반환하지 않으면 효과 0 |
| `RequestAuthenticationFilter(List<RequestAuthenticator>)` | 기본 거부 필터를 조립한다 | 중간 — 등록 자리는 조립 근 하나이고 표면 census 가 Filter 집합 등식으로 잰다. 빈 목록은 생성자가 거부 |
| `.OPEN_ROUTES` · `.routeOf` · `.UNAUTHENTICATED_SUBJECT` | 허용 목록·경로 키·감사 라벨 읽기 | 낮음 — 읽기뿐이고 집합 등식 test 가 그 값을 잠근다 |
| `.configuredAuthenticators` | 꽂힌 인증기 목록 | `internal` — 모듈 밖 0. production 의 어느 코드도 읽지 않는다 |
| `OperatorCredentialAuthenticator(OperatorCredential)` · `.CREDENTIAL_HEADER` · `.OPERATOR_SUBJECT` | 헤더 경로 | 6A 와 같음(타입이 비교를 가둔다) — 클래스가 `Filter` 가 아니게 된 것만 바뀌었다 |
| `BrowserSessionAuthenticator(BrowserAccessExecutor)` · `.CSRF_HEADER` · `.CSRF_EXEMPT_METHODS` · `.subjectOf` | 쿠키 경로·헤더 이름·면제 집합·감사 라벨 | 낮음 — 면제 집합은 **열거이고 그 밖 전부가 요구 쪽**이다(새 메서드가 조용히 면제되지 않는다) |
| `SessionCookieWriter(Boolean, Duration)` · `.issued` · `.cleared` · `.NAME` · `.read` | 쿠키 문자열 생성·판독 | 중간 — 쿠키 문자열을 만들 수 있으나 저장소에 그 해시가 없으면 거부된다. 속성 넷을 test 가 잠근다 |
| `OperatorSessionController(...)` | endpoint 셋 | 설계된 표면 — 인증은 이 클래스가 하지 않는다(필터가 판정) |
| `SessionResponse(String, String)` | 응답 값 | 낮음 — `data class` 가 **아니다**(합성 `equals` 가 CSRF 값의 빠른 비교가 된다) |
| `BrowserAccessExecutor(BrowserAccessTransaction)` · 넷 | HTTP 가 부르는 실행기 | 중간 — 경계를 쥔 주체만 만들 수 있고, 포트를 밖에 내지 않는다 |
| `AccountCommandRunner(...)` · `AccountCommandLog` · `AccountCommandTermination` · `AccountCommandMode` | 명령 레인 | 높음 → **③ 층(러너 레인)으로 분류**해 컨트롤러·② 층의 참조가 의존 게이트 위반이 된다. 러너는 조건부 설정에만 등록되고 러너는 프로세스당 하나다(`OneShotRunnerGuard`) |
| `AccessCookieProperties(Boolean)` | 쿠키 Secure 설정 | 낮음 — `data class` 가 아니다(설정 객체 문자열화 규율). 기본값은 안전한 쪽(`true`) |
| `AccountCommandProperties(...)` | 명령 설정 | 중간 — 초기 비밀 문구를 담는다. `data class` 가 아니고 기본값이 없다(누락이면 기동 실패) |

### web

| 표면 | 밖에 허락하는 것 | 등급·닫는 자리 |
| --- | --- | --- |
| `rememberCsrf(string \| null)` · `hasCsrf()` | CSRF 값을 기억·조회 | 낮음 — 값은 **메모리에만** 있고 `hasCsrf` 는 존재만 낸다(값을 내지 않는다). 브라우저 저장소에 쓰지 않는다 |
| `loginRoute` | 로그인 자리 | 낮음 — 화면 자리 표 **밖**이라 셸 전역 탐색에 뜨지 않는다 |
| `LoginScreen` | 폼·실패 표시 | 낮음 — 실패 문면이 하나다(서버가 숨긴 사유를 화면이 되돌려 말하지 않는다) |

## 3. 마이그레이션 V20 — 권한과 제약

- **번호**: 착수 시점 main 최대 V19, 병행 레인 중 마이그레이션을 만드는 것은 이 slice 뿐(계약
  산출물 1). PR 직전 재확인은 `commands.md` 에 한 줄.
- **GRANT**: `account` = SELECT + UPDATE(실패 계수·잠금 **두 열만**) · `browser_session` =
  SELECT·INSERT + UPDATE(사용 시각·유휴 시한·폐기 시각 **세 열만**). DELETE·TRUNCATE 없음.
  권한 행렬(축9)은 **열 단위 부여를 세지 않는다**(실측) — 그래서 그 행렬의 두 표는
  `update=false` 로 나오고, 그것이 「표 전체를 갱신할 권한은 없다」는 더 강한 사실이다.
  「어느 열이 열려 있는가」는 `AccessPrivilegeTest` 가 아홉 문장으로 직접 쏜다.
- **제약**: `account` 셋(식별자 공백 금지·해시 공백 금지·계수 음수 금지) + UNIQUE(로그인 식별자) ·
  `browser_session` 셋(해시 공백 금지 둘 + 유휴 ≤ 절대) + FK(계정, CASCADE 없음).
  UNIQUE 축은 V20 까지 **공집합**이었다 — 그 자리를 등재 집합 하나로 바꿨고 근거는 R-2(다수
  계정 전제)다. UNIQUE **인덱스**로 두면 그 축이 조용히 통과하므로 제약으로 뒀다.
- **보존 분류**: `account` = 제외(새 사유 — 계정의 수명은 시간이 아니라 결정이 정한다) ·
  `browser_session` = 파기(적재 시각 축). 분류 레지스트리는 스키마와 집합 등식이라 분류를
  적지 않으면 파기 자체가 멈춘다.

## 3b. 운영자 명령 사용법 (계정 부트스트랩·비밀 문구 교체)

`docs/runbook/**` 은 이 slice 의 in_scope 밖이라 절차서를 새로 쓰지 않는다 — 명령의 모양만
여기 남긴다(7F 의 반입 절차서가 그 자리를 들 때 옮긴다).

```
# ① 계정 생성(한 번) — 계정이 하나라도 있으면 거부하고 exit 1 이다
SPRING_MAIN_WEB_APPLICATION_TYPE=none BIDVECTOR_ACCOUNT_INITIAL_VALUE="$INITIAL" \
  java -jar app.jar --bidvector.account.mode=create --bidvector.account.login=<로그인 식별자>

# ② 비밀 문구 교체 — 그 계정의 세션을 전부 폐기한다(같은 트랜잭션)
SPRING_MAIN_WEB_APPLICATION_TYPE=none BIDVECTOR_ACCOUNT_INITIAL_VALUE="$NEXT" \
  java -jar app.jar --bidvector.account.mode=rotate --bidvector.account.login=<식별자>
```

- **`SPRING_MAIN_WEB_APPLICATION_TYPE=none` 은 선택이 아니다**(code-review r2 M-2 · 이 저장소의
  일회 러너 절차 전부가 쓴다). 러너는 `ApplicationRunner` 라 **컨텍스트가 완전히 뜬 뒤**에 돌고,
  web 타입을 끄지 않으면 둘 중 하나가 난다: 서비스가 떠 있으면 `server.port` 바인딩 실패로
  **명령 자체를 못 돌리고**, 서비스를 내리고 돌리면 명령이 도는 수 초 동안 **무인증
  `POST /api/session` 을 포함한 API 표면이 운영 포트에 뜬다**(아직 계정이 없거나, 교체 직전의
  옛 해시로 열려 있는 상태에서). 끄면 **서비스를 내리지 않고도** 돌릴 수 있다.

- 세 설정 키 모두 **기본값이 없고 빈 값도 거부한다** — 누락·공백이면 설정 객체가 서지 않아
  기동이 실패한다(비-0 종료). 변수를 빠뜨린 실행이 **빈 비밀 문구로 계정을 만들던** 자리다
  (verifier r1 F3).
- 이 명령은 **소유자 연결**로 돈다(앱 역할은 `account` 에 INSERT 가 없다). 그래서
  `bidvector.persistence.*` 값이 함께 필요하다(평소 기동과 같은 값).
- 러너는 프로세스당 하나다 — 수집·중계 모드와 함께 켜면 기동이 실패한다(`OneShotRunnerGuard`).
- 값을 **argv 로 넘기지 않는다** — 그러면 프로세스 표에 보인다. 위 두 줄처럼 환경변수
  (`BIDVECTOR_ACCOUNT_INITIAL_VALUE`)로 준다. Spring 의 relaxed binding 이 같은 키를 읽는다
  (6C 의 서비스 키 관례와 같은 축).

## 3c. 게이트 설정 변경 — 줄마다의 근거 (계약 D-7D-1 요구)

`config/quality/architecture-policy.properties` 는 게이트의 정본이라, **넓힌 줄마다** 사유를 적는다.
아래 「등식」 표시는 그 목록이 관측과 양방향으로 같아야 하는 키다(죽은 항목도 RED).

| 키 | 변경 | 근거 |
| --- | --- | --- |
| `app.http.allowed-packages` | `+bidvector.workflow.access` | 컨트롤러·인증기가 값·결과 타입을 이름 부른다. 이 패키지에 **쓰기 능력 포트가 없다**(포트는 use case 가 쥐고, 아래 `app.port-call.ports` 가 app 의 직접 호출을 막는다) |
| `app.http.allowed-classes` | `+BrowserAccessExecutor` | 제한 층이 받는 것은 **실행기 하나**다(D-6A2b-8 의 형태 그대로 — 저장소·경계는 들어오지 않는다) |
| `app.injection.allowed-types`(등식) | `+BrowserAccessTransaction` `+SessionCookieWriter` `+BrowserAccessExecutor` `+java.time.Duration` | 앞 셋은 7D 가 만든 주입 자리 전부. `Duration` 은 쿠키 Max-Age(정책 값)를 받는 생성자 인자 — 목록의 기존 성격(`Instant`·`String`·`Long`)과 같은 축 |
| `app.injection.carrier-exemptions` | `+RequestAuthenticationFilter\|RequestAuthenticator` | 기본 거부 필터가 인증 경로를 **구성으로** 받는 자리(계약 설계 검토 (1)). 그 자리가 여는 것은 출하 조립 test 의 인증기 집합 등식이 닫는다 |
| `app.assembly.tier1-bootstrap-classes` | `+BrowserAccessWiring` `+AccountCommandWiring` `+AccountCommandProperties` `+AccessCookieProperties` | 조립과 그 설정 타입. ① 층의 성질(어댑터·JDBC 를 쥔다)이 그대로 맞고, HTTP 확장 API 의존 금지는 그대로 받는다 |
| `app.assembly.tier2-request-scoped-classes` | `+BrowserAccessExecutor` | 컨트롤러·필터가 받는 실행기. ② 층의 허용 목록 ⊆ 와 능력 포트 금지를 함께 받는다 |
| `app.assembly.tier2.allowed-workflow-types`(등식) | `+access` 타입 여덟 | 실행기 서명에 실제로 나타나는 타입 전수. **쓰기 능력 포트는 들어 있지 않다**(포트는 use case 생성자에만 있다) |
| `app.assembly.tier3-collection-classes` | `+계정 명령 레인 넷` | 러너 레인의 성질이 같다(cron·명령이 프로세스로 돌리고 HTTP 로 닿지 않는다). 이 분류가 **컨트롤러·② 층의 참조를 위반으로 만든다** |
| `app.surface.api.handlers` | `+OperatorSessionController` | 출하 조립에 실제로 꽂힌 handler 집합의 실측값 |
| `app.surface.api.filters` | `OperatorCredentialFilter` → `RequestAuthenticationFilter` | 같은 자리의 **이름 변경**이다 — 등록되는 필터가 기본 거부 하나라는 사실은 그대로이고, 그 클래스가 더 이상 `Filter` 가 아니어서 이름이 바뀌었다 |
| `app.adapter.member-call-pairs` | `+BrowserAccessExecutor\|BrowserAccessTransaction\|inTransaction\|Function1` | 편집 실행기의 같은 형태(경계의 유일 메서드). 서술자까지 적어 오버로드가 같은 자리로 접히지 않게 한다 |
| `app.adapter.member-call-pairs`(r2) | `+BrowserAccessExecutor\|BrowserAccessTransaction\|inSignInScope\|Function1` | 로그인만 문장별 트랜잭션으로 도는 자리의 **유일 호출자**를 못박는다(fail-closed). 쌍 하나 외에는 아무도 그 느슨한 경계로 들어올 수 없다 |
| `app.port-call.ports` | `+접근 포트 다섯` · **허용 쌍 0** | **넓히는 변경이 아니라 좁히는 변경이다** — app production 의 어느 클래스도 이 포트의 메서드를 직접 부르지 못하게 못박는다 |
| 자격증명 참조자 키(`…operator-credential-referencers`) | 이름 변경 하나 | 위 필터 이름 변경의 같은 축(집합 크기 불변) |
| 비밀값 축의 새 키 쌍(`…passphrase-types`·`…passphrase-referencers`) + `collection.depth.passphrase` | **새 키 셋** | 원문 비밀 문구 타입의 보유자 집합을 등식으로 잠근다(자격증명 축과 **키를 나눠** 한쪽 사유가 다른 쪽을 열지 않게) |
| `collection.external.allowed-packages.adapters`(등식) | `+at.favre.lib.crypto.bcrypt` | 채택한 해시 라이브러리의 **유일한** 패키지. Spring 좌표를 쓰지 않은 이유가 이 목록(Spring 0건)이다 |
| `collection.key-hash.holders`(등식) | `+BrowserSessionKey` `+CsrfStamp` | hex 형태를 짓는 자리를 하나로 두려고 기존 함수를 재사용한다 — 그 둘은 **공고 키를 짓지 않는다**(세션·CSRF 값의 해시다) |
| `collection.transport.holders.http-response`·`collection.transport.member-surface`(등식) | 이름 변경 둘 | 401 본문 writer 를 쥔 클래스와 그 송신 멤버의 이름이 바뀌었다(같은 자리·같은 멤버·같은 서술자) |
| `app.runner.allowed-referencers` | `+AccountCommandRunner` `+AccountCommandWiring` | 프로세스를 자동 시작하는 러너 타입을 참조하는 자리의 실측값 |
| `app.logging.allowed-users` | `+AccountCommandWiring` | 러너의 한 줄 출구를 만드는 배선(기존 러너 레인 다섯과 같은 자리) |
| `EXPECTED_EXEMPT_COUNT`(test 상수) | 51 → 60 | 면제 목록의 크기를 못박는 수. 늘린 아홉의 내역은 그 상수 KDoc 이 든다 |
| `CleanMigrationTest` 축5 | 공집합 → 등재 하나 | `account.login_name` UNIQUE. R-2 가 다수 계정을 전제하므로 PK 와 로그인 식별자가 다른 축이다 — UNIQUE **인덱스**로 두면 이 축이 조용히 통과하므로 제약으로 두고 등재했다 |

## 4. 리뷰 요청 조건

- [x] 구현 diff 가 커밋되어 base/head 고정 — `commands.md` 의 clean-tree 판정(양성 대조 1회 포함)
- [x] acceptance 가 전부 exit 0 — `commands.md`(`check` · `web` · `container` 세 job 명령 그대로)
- [x] test/lint/type/architecture/contract 전건 통과 — 위와 같은 자리(부분 게이트로 줄이지 않았다)
- [x] 정책 version 의 근거 — `policy-values.md` §1(정책 데이터의 `source` 가 그 문서를 가리킨다)
- [x] 비밀값 스캔 — 패턴 파일 참조형으로 산출물·evidence 경로에 실행(`commands.md`). 어휘를
      이 문서에 축어로 적지 않는다
- [x] 알려진 제한과 rollback — 아래 5절과 `rollback.md`(r2 에서 **실측 HEAD 를 다시 냈다**)
- [x] code-review r1 의 blocker/medium 넷(H-1·M-1·M-2·M-3) — 지적별 커밋 + 지적별 변이 실측
      (`commands.md` r2 절). low 여섯은 리포트가 유실돼 축어를 받지 못했다 — 받으면 그 자리에서
      처리하거나 「알려진 제한」으로 등재한다

## 5. 알려진 제한

1. **로그인 식별자 열거는 막지 않는다.** 계정이 없을 때와 비밀 문구가 틀릴 때의 응답은 같지만
   (401 하나) **소요 시간은 다르다** — 계정이 없으면 해시 검증을 돌지 않는다. 계정이 하나뿐이고
   그 식별자는 비밀이 아니므로(운영자가 직접 정한다) 이득이 없는 축이다. 더미 해시로 시간을
   맞추는 것은 하드코딩 해시 상수를 들이는 일이라 하지 않았다.
2. **잠금은 서비스 거부의 문이다.** 식별자를 아는 공격자가 5회 실패로 15분 잠금을 반복할 수
   있다. 단일 운영자 환경에서 「추측을 막는 것」이 「잠금이 반복되는 것」보다 비싸다고 보고
   잠금을 골랐다(계약이 「계정 잠금 또는 지연」을 선택지로 뒀다). 지연으로 바꾸려면 정책 값
   둘(상한·창)과 판정 한 곳을 고친다.
3. **요청마다 세션 행을 갱신한다**(유휴 시한 밀기). 단일 운영자 부하에서는 비용이 없지만
   다수 사용자 전환 때는 밀기 주기를 두는 것이 답이다(지금 두면 측정 없는 복잡도다).
4. **앱 프로세스 장악은 방어하지 않는다**(계약 위협 모델). 앱 역할은 세션 INSERT 권한이
   필요하므로, 그 권한을 쥔 주체는 자기가 고른 값의 해시로 세션을 심을 수 있다. 열 단위
   GRANT 가 좁히는 것은 **앱 결함**의 폭이다(해시 덮어쓰기·계정 생성·행 삭제는 못 한다).
5. **로그인 CSRF 는 두 겹으로만 막는다** — `application/json` 요구와 `SameSite=Strict`.
   `Origin`·`Sec-Fetch-Site` 검사는 더하지 않았다(측정된 필요 없이 층을 늘리지 않는다).
6. **쿠키 `Secure` 를 내리는 설정이 있다**(로컬 http 개발). 운영 배포에서 그 값을 내리면
   평문 구간으로 쿠키가 나간다 — 기본값이 안전한 쪽이고, 7F 의 반입 설정이 그 값을 건드리지
   않는 것이 전제다.
7. **요청 자체의 유량 제한은 없다.** 실패 제한은 **계정 단위 계수**이고, 들어오는 요청 수에
   상한을 두는 층(역프록시·IP 단위 제한)은 이 slice 에 없다 — 없는 로그인 식별자로 쏘는 요청은
   계수에도 닿지 않는다(해시 검증 전에 끝난다). 그 층은 반입 형태(7F)의 일이고, 지금 없는 것이
   이 slice 의 방어를 무르게 하지는 않는다(추측은 계정 계수가, 쿠키 위조는 256비트 난수가 막는다).
   **r2 정정** — 「무르게 하지 않는다」가 한 축에서 틀렸었다: 로그인이 검증 내내 풀 커넥션을 쥐고
   있었으므로, 유량 제한이 없는 상태에서 그 경로가 **다른 모든 요청을 굶길 수 있었다**. 그 증폭은
   M-2 가 없앴다(커넥션을 쥐는 시간이 문장 하나의 길이다). 남는 것은 CPU 축이다 — 동시 로그인
   N 건이 N 번의 KDF 를 돌리는 것 자체는 이 slice 가 막지 않는다. **7F 프록시의 `/api/session`
   유량 제한을 운영 반입 조건으로** 두자는 것이 이 레인의 제안이고, 계약에 적는 것은 팀장 몫이다.
8. **비밀 문구 교체 경로는 운영자 명령 하나다.** 화면·endpoint 가 없다(가입·재설정 메일은
   R-2 범위 밖). 계약 산출물 3 의 「비밀번호가 바뀌면 그 계정 세션 전부 폐기」를 실제로 촉발할
   경로가 그것뿐이라 명령에 두 번째 모드를 뒀다 — 범위 판단이므로 완료 보고에 밝힌다.
9. **`workflow` 경로는 착수 계약의 in_scope 목록에 없었다**(계약 r1 D-7D-1 로 갱신됐다). 값·포트는 HTTP 층과 저장 어댑터가
   둘 다 이름 불러야 하고 그 둘이 공유할 수 있는 자리가 `workflow` 뿐이다(의존 게이트). 계약
   갱신을 오케스트레이터에 요청했고 산출물 자체는 1~6 그대로다.
10. **`config/quality/gate-tests.properties` 등재도 계약 r1 목록 밖이다.** `gateRegistrationGate` 는
    모듈별 **양방향 등식**이라 새 test class 가 생기는 것 자체가 등재 사유다(등재하지 않으면 그
    test 가 지워져도 `check` 가 초록이다). 더한 것은 모듈 키 셋(`adapters`·`workflow`·`app`) 아래
    FQN 여섯 줄과 각 키의 등재 사유 문단이고, **값·정책 version 변경은 0** 이다. 범위 판단이므로
    완료 보고에 밝힌다.
11. **`container` job 의 취약점 게이트 둘은 이 장비에서 끊겼다** — 도구 설치가 실패했고 게이트가
    「도구 부재」로 멈췄다(자기 판정이 아니다). 이 slice 는 의존을 하나 더하므로 **그 축은 CI 에서
    다시 봐야 한다**. 그 뒤 step 들(기동·스모크·리허설·정리)은 같은 순서로 이어 돌려 전부 exit 0
    이다(`commands.md`).
12. **`web` job 의 Playwright 시스템 의존 설치 step 은 이 장비에서 exit 1** 이다(apt 권한). 브라우저
    자체는 설치돼 있어 W-10·W-11 은 돌았고, CI 에서는 그 step 이 러너 권한으로 선다.
13. **다른 패키지의 KDoc 한 줄이 옛 이름을 현재형으로 가리킨다.** 필터가 `OperatorCredentialFilter`
    → `RequestAuthenticationFilter` 로 바뀌면서, 감사 행 타입(`adapters/audit`)의 KDoc 이 말하는
    「subject 라벨을 정하는 자리」가 이제 그 이름이 아니다. 그 파일은 인증 패키지가 아니라 이
    slice 의 in_scope 밖이고, **지금 고치면 rollback 복원 목록이 늘어 실측 HEAD 가 낡는다** —
    그래서 고치지 않고 등재한다(한 낱말짜리 후속). 코드 참조가 아니라 산문이므로 컴파일·게이트에
    걸리지 않는다. 역방향 좌표 점검(`grep -rn '<stem>:[0-9]'`)은 **0 건**이다 — 이 slice 가 편집한
    파일을 `file:line` 으로 가리키는 곳이 저장소에 없다.

### code-review r1 수정 라운드가 더한 제한 넷

14. **긴 비밀 문구가 이제 거부되지 않고 쓰인다**(M-1 의 거동 변화). 한계를 넘는 입력은 SHA-512 로
    접혀 KDF 에 들어가므로, 운영자가 72바이트를 넘는 문구로 계정을 만들 수 있고 그 문구로
    로그인한다. 앞 판에서는 **만들 수도 쓸 수도 없었다**(명령이 예외로 죽고 로그인이 500 이었다).
    절단이 아니므로 입력 전체가 결과에 들어간다. 한계 **아래** 입력의 해시 문면과 검증 결과는
    바뀌지 않는다(전략이 길이 검사를 먼저 하고 그 아래면 입력을 그대로 넘긴다 — 실측).
15. **검증 중에 잠긴 계정의 올바른 로그인은 거부된다**(H-1 의 선택). 느린 검증이 도는 사이에
    다른 요청이 상한을 채우면, 늦게 끝난 그 성공은 세션을 받지 못한다. 반대로 하면 늦은 성공
    하나가 방금 걸린 잠금을 푸는 길이 되므로 이쪽을 골랐다 — 사용자에게는 「잠긴 동안의 시도」와
    같은 401 이고, 잠금이 풀린 뒤 다시 로그인하면 된다.
16. **로그인의 세 단계는 서로 원자가 아니다**(M-2 의 대가). 조회·갱신·발급이 각자 트랜잭션이라,
    중간에 프로세스가 죽으면 ⓐ 실패 계수만 오르거나 ⓑ 계수만 비워지고 세션이 없거나 ⓒ 앞
    세션만 폐기된 상태가 남을 수 있다. 셋 다 **다시 로그인하면 해소**되고 권한이 새지 않는다
    (세션이 없는 쪽으로만 치우친다). 반대로 **한 커밋이어야 안전한 쌍**(폐기+등록 · 계수+잠금)은
    포트 한 호출 안에 묶여 있어 이 느슨함의 영향을 받지 않는다.
17. **게이트 설정이 매개변수 둘 이상인 어댑터 멤버를 표현하지 못한다.**
    `app.adapter.member-call-pairs` 는 **목록 구분자와 서술자 구분자가 같은 쉼표**라
    `signIn(LoginName, ClearPassphrase)` 같은 쌍을 적을 수 없다(적으면 항목 둘로 쪼개진다).
    그래서 로그인 경계의 서명을 람다 하나 받는 형태로 뒀다 — 거동에는 영향이 없지만, 「서명을
    고정해 오용을 막는다」는 선택지가 이 게이트의 데이터 형식 때문에 닫혀 있다. 구분자를
    가르는 것은 이 slice 의 in_scope 밖이라 **후속 제안**으로 남긴다.
