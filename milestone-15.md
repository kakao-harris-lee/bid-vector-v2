# 마일스톤 15 — 열린 가입 · 계정별 전략/프로필 · 사업자번호 선택

> **지위: 계약(2026-11-21, 세션 모델 단독 저작 · main `99f2fb25` 위).** 운영자 지시는 2026-11-21 셋이다.
> 「가입 경로가 있어야 해」, 「초대 코드 + 운영자 승인 **없이** 바로 가입해서 사용할 수 있도록」, 「사업자번호
> 필수 사항도 옵션으로 — 가입·사용 조건을 완화해서 테스트가 쉽게」. 같은 날 「지금은 개발 단계」라는 지시로
> 운영 DB 권한 검사를 풀었다. 이 마일스톤은 **테스트 단계 진입용**이다 — 아래 「승인 경계」의 되돌림 조건을
> 함께 읽는다.

## 입력 — 지금의 계정 모델 (main `99f2fb25` 실측)

- **인증**: 손으로 짠 필터 `RequestAuthenticationFilter` 가 전 경로를 기본 거부하고, 공개 경로는
  `OPEN_ROUTES = { POST /api/session }` 하나다(집합 등식 test 로 잠김). 저장은 V20 `account`(login_name UNIQUE ·
  bcrypt) · `browser_session`(쿠키·CSRF 해시).
- **계정 생성**: 운영 명령(`--bidvector.account.mode=create`)만이 만들고, 계정이 하나라도 있으면 거부한다
  (`AccountProvisioning.create` → `RefusedAccountExists`). app 역할에는 `account` INSERT 권한이 **의도적으로 없다**
  (V20 주석: 「app 이 뚫려도 계정을 만들 수 없다」).
- **이미 계정별인 것**: `browser_session` · 입찰함(`bid_case` 외 V21 셋) · `account_business_profile` ·
  `account_construction_capacity`(V22) · `notification_inbox`(V23).
- **단일 행인 것**: `operator_strategy`·`operator_strategy_revision`(V9 `CHECK (id = 1)`) · `operator_profile`
  (V12 `CHECK (id = 1)`) · `edit_session`(V8, 계정 칸 없음). 제안(`SuggestionController`)·평가 커밋·투찰가 요청이
  이 단일 행을 읽는다. `ProfileOwnershipE2ETest` 가 「자격 사실은 계정 경계 밖이다」를 잠근다.
- **알림 수신자**: `JdbcNotificationAudience` 가 `SELECT id FROM account` — 새 후보 알림이 **모든 계정**에 간다.
  Telegram 경로는 전역 chat id 하나(`OPEN-STR-07`).
- **사업자번호**: 지금 **어디에도 필수가 아니고 저장하지도 않는다**(V22 에 칸이 없다 — scope P4). 화면 A·설정의
  「사업자번호 확인」은 「준비 중」 자리다. 그래서 지시 「필수 → 선택」은 **선택 입력 칸을 새로 두는 것**으로 읽는다.

**왜 가입만 열면 안 되는가**: 단일 행 전략·프로필을 둔 채 가입을 열면, 가입자가 운영자의 전략·면허를 보고
고치며(`ProfileOwnershipE2ETest` 의 그 문장이 그대로 사고가 된다) 운영자의 후보 알림을 전부 받는다. 그래서
**계정별 분리(15A)가 가입(15B)보다 먼저 머지된다.**

## slice

### 15A — 전략·프로필·편집 세션의 계정별 분리 (DB 마이그레이션 · 서버)

- **V28**: `operator_strategy` · `operator_strategy_revision` · `operator_profile` · `edit_session` 에 `account_id`
  (FK `account`)를 두고 `CHECK (id = 1)` 을 걷는다. 기존 단일 행은 **가장 작은 `account.id`**(지금의 운영 계정)에
  붙인다 — 계정이 0 이면 그 행을 계정 없이 둘 수 없으므로 마이그레이션이 **이관 규칙을 SQL 안에서 결정적으로**
  내고, 그 갈래를 test 로 잰다(계정 0 · 1 · 2 이상).
  각 표에 계정당 하나(전략·프로필) 또는 계정 범위 키(리비전·편집 세션)의 유일 제약.
- **행이 없는 계정의 기본값**: 새 계정은 전략·프로필 행이 없다. 읽기는 「아직 설정 안 함」(빈 전략 = 감시 조건
  없음, 빈 프로필)을 내고 **기존 단일 행을 빌려 오지 않는다.** 첫 저장이 행을 만든다.
- **계정 범위로 읽기**: `Sql.kt` 의 `WHERE id = 1` 을 계정 키로. 전략 읽기·편집, 프로필 읽기·쓰기, 제안 목록,
  투찰가 요청이 `authenticatedAccount()` 의 계정으로 읽는다. 요청이 계정 id 를 실어 오면 지금처럼
  `AccountBindingGuard` 가 거부한다.
- **평가 커밋(일회 러너)**: 계정마다 그 계정의 전략·프로필로 평가하고, 후보 알림은 **그 계정의** 알림함에만
  쌓는다(`JdbcNotificationAudience` 를 계정 인자로). 전략이 없는 계정은 평가를 건너뛰고 그 사실을 실행 계수로 낸다.
- **Telegram**: 바꾸지 않는다 — 전역 경로는 **가장 작은 계정(운영 계정)의 후보**에만 쓴다. 계정별 채널은 범위 밖.
- **test 의 주장 뒤집기**: `ProfileOwnershipE2ETest` 의 「B 가 쓰면 A 에도 보인다」는 지우지 않고 주장을 바꾼다
  — 「B 가 쓴 전략·프로필은 A 의 조회·제안·평가에 보이지 않는다」. 전략 편집 세션도 같은 쌍 test.

### 15B — 열린 가입 (인증 · 권한 · 화면)

- **operation**: `POST /api/accounts` — 본문 `loginName` · `passphrase`. 성공하면 계정을 만들고 **바로 로그인
  세션을 낸다**(`POST /api/session` 과 같은 쿠키·CSRF 응답). 초대 코드·운영자 승인 **없음**.
  - 검증: loginName 4~32자 `[a-z0-9._-]` · passphrase 10자 이상 **1024자 이하** · 중복은
    `409`(어느 계정이 있는지 다른 정보 없이).
    _2026-11-21 계약 갱신: 첫 판의 「bcrypt 72바이트 상한 초과는 거부」는 이 저장소에서 거짓이었다 —
    `BcryptPassphraseHasher` 가 긴 입력을 SHA-512 로 먼저 줄여 자르지 않는다. 상한은 입력 위생 경계(글자 수)다
    (PR #188 cr M-1)._
  - 남용 상한: 같은 원격 주소에서 가입 **시도** 시간당 `bidvector.signup.attempts-per-hour`(기본 **30**)건
    (메모리 내 창 — 재기동이면 초기화, 테스트 단계 상한). 초과는 `429` + 식별값 없는 로그 한 줄.
    _2026-11-21 계약 갱신: 첫 판은 「시간당 10건」 고정이었다. 운영은 역방향 프록시 뒤라 원격 주소가 하나로 보여
    상한이 사실상 전역이다 — 그래서 설정 손잡이로 빼고 기본을 30 으로 올렸다. 신뢰 프록시 + X-Forwarded-For 는
    `OPEN-M15-SIGNUP-HARDENING`(PR #188 cr H-1)._
- **V29**: app 역할에 `GRANT INSERT (login_name, passphrase_hash) ON account` 와 identity 시퀀스 사용 권한.
  V20 의 결정(「app 은 계정을 만들 수 없다」)을 **뒤집는 줄**이므로 마이그레이션 주석에 이 계약과 날짜를 적고,
  `CleanMigrationPrivilegeTest` 의 기대를 같은 커밋에서 바꾼다(열 범위 밖 INSERT 는 여전히 거부 — 음성 대조).
- **운영 명령 `create`**: 「계정이 있으면 거부」를 「같은 login_name 이 있으면 거부」로 완화한다(운영자가 손으로
  두 번째 계정을 만들 수 있게). `rotate` 는 그대로.
- **공개 경로**: `OPEN_ROUTES` 에 `POST /api/accounts` 를 더하고 집합 등식 test 두 곳(`BrowserSessionAuthenticationTest`
  · `OpenApiSecurityContractTest`)을 같은 커밋에서 갱신한다.
- **계약·승인 목록**: `openapi/bidvector-operator-api.yaml` 에 operation · 생성 타입 재생성 ·
  `web/src/api/approvedOperations.ts` 등재 · `queries.ts` 호출(같은 커밋).
- **화면**: 로그인 화면 아래 「계정 만들기」 → 가입 화면(아이디 · 비밀번호 · 비밀번호 확인) → 성공하면 곧장
  「시작하기」. 오류는 칸 옆 문장(중복 · 형식 · 상한 초과). 셸·카드 문법은 M14 를 따른다.
- **사용 조건**: 가입 직후 **모든 화면에 들어갈 수 있다**. 설정이 비었으면 각 화면이 지금처럼 빈 얼굴 + 사유
  (「감시 조건을 아직 정하지 않았습니다 → 설정」)를 보이고, 막지 않는다. 막는 자리가 발견되면 이 slice 가 푼다.

### 15C — 사업자번호 선택 입력

- **V30**: `account_business_profile` 에 `business_registration_number` (NULL 허용, 숫자 10자리 CHECK).
- 프로필 쓰기 요청에 선택 칸 `businessRegistrationNumber`(하이픈 허용 입력 → 숫자 10자리로 정규화, 형식이 틀리면
  `400`). **검증 번호(체크섬)는 걸지 않는다** — 완화 지시. 국세청 진위 확인 API 는 범위 밖(G3 그대로 「준비 중」).
- 화면: 설정의 사업자 정보 카드에 선택 입력 칸 · 시작하기의 「사업자번호」 절은 **필수 표시 없이** 「입력함 /
  비워 둠(선택)」. 비워 두어도 체크리스트 완료를 막지 않는다.
- **누출 게이트와의 관계**: 기존 e2e 의 「사업자번호 모양 값이 화면에 0」은 **남의 업체** 값을 막는 게이트다.
  **자기 계정 설정 칸의 자기 값**만 예외로 두고, 그 예외는 testId 하나로 좁힌다(다른 화면의 0 은 유지 — 양성
  대조로 그 게이트가 여전히 붉어지는지 잰다). 로그·알림함·Telegram 문면에는 싣지 않는다.

## 게이트

- 각 slice 의 acceptance 는 CI job 명령 그대로(Kotlin `check` · `web` · 해당 시 `container`).
- 15A: 계정 둘 fixture 로 **전략 · 프로필 · 편집 세션 · 제안 · 평가 알림** 다섯 축의 격리 test(양방향).
  마이그레이션은 계정 0·1·2 이관 test + clean migration 열·권한 test.
- 15B: 공개 경로 집합 등식 · 가입 → 즉시 세션 → 보호 경로 200 여정 · 중복 409 · 상한 429 · 열 밖 INSERT 거부.
  e2e 여정 하나(가입 → 시작하기 → 설정 저장 → 오늘의 공고).
- 15C: 정규화·형식 거부 test · 자기 설정 칸 예외의 범위 test.

## 머지 순서와 병렬

15A·15B·15C 는 각자 worktree 에서 병렬로 만들되 **머지는 15A → 15B → 15C**. 마이그레이션 번호는
위 배정(V28 · V29 · V30)으로 고정해 충돌을 막는다. 뒤 slice 는 앞 slice 머지 뒤 main 을 병합하고 전건을 다시 잰다.
빌드는 `~/.local/bin/bv-build.sh` 로만(2026-11-21 부터 동시 셋).

_2026-11-21 계약 갱신: 첫 판은 「15C 는 언제든」이었다. 그것은 거짓이다 — `migrationOrderGate` 가 결번을 막고,
Flyway `outOfOrder=false` 아래에서 V30 이 먼저 운영 DB 에 들어가면 뒤에 오는 V28·V29 가 기동을 멈춘다(PR #189 cr B-1)._

_2026-11-21 계약 갱신(15A 착수 판정): ① 계정 id 타입 `AccountId` 를 `bidvector.workflow.access` 에서
`bidvector.sharedkernel` 로 옮긴다 — 전략 포트가 계정을 받으며 생긴 패키지 순환 넷을 끊는 유일한 자리다.
② 계정 전용 뿌리에 `/api/evaluation-dry-runs` 가 더해진다(전략을 읽으므로 계정 없이 읽으면 남의 전략이다) — 그 결과 그
넷(전략 · 제안 · 투찰가 요청 · dry-run)은 운영자 자격 헤더로 401 이다. ③ 「`JdbcNotificationAudience` 를 계정 인자로」
대신 그것은 계정 열거자로 두고 run 마다 청중을 그 계정 하나로 고정한다 — 계정 전수를 읽는 자리가 하나로 남는다._

## 리뷰 경로

인증·권한·DB 마이그레이션은 Codex 심판 대상 경로다. Codex 는 운영자 비용 승인이 있을 때만 탄다 — 그 승인이 없으면
`verifier` ∥ `code-reviewer`(sonnet)로 가고, **PR 코멘트에 「Codex 미경유(개발 단계 운영자 지시)」를 적는다.**

## 승인 경계 — 테스트 단계 한정

- 열린 가입 · 남용 상한(시간당 기본 30, 프록시 뒤 사실상 전역) · 체크섬 없는 사업자번호는 **테스트 단계의 완화**다. 상용 전에 다시 정할 것을
  OPEN 으로 등재한다: `OPEN-M15-SIGNUP-HARDENING`(초대/승인·이메일 확인·영속 rate limit·약관 동의·탈퇴 파기).
- 운영 반입은 각 slice 머지 뒤 `prod-release` → dry-run 깨끗하면 apply(운영자 2026-11-21 「Deploy if dry-run is
  clean」). V28 이관이 끼므로 반입 전 DB 백업은 반입 스크립트가 한다 — 그 뒤 이전 이미지로의 되돌림은 막힌다
  (runbook §7.3).

## 완료 조건

1. 바깥망에서 bidvector.kr 에 새 아이디로 가입하면 곧장 시작하기 화면이 뜨고, 전략·프로필을 저장해 오늘의 공고에서
   자기 제안을 본다.
2. 그 가입자는 운영 계정의 전략·프로필·제안·입찰함·알림을 보지 못하고, 운영 계정도 가입자의 것을 보지 못한다.
3. 사업자번호를 비워 둔 채 모든 화면을 쓸 수 있고, 넣으면 설정에 남는다.
4. slice 셋 머지 + 운영 반입 + 종결 절 기록.

## 범위 밖

- 계정별 Telegram 채널 · 이메일 · 비밀번호 찾기 · 탈퇴 · 요금 · 팀(한 사업자 다계정).
- 사업자번호 진위 확인(국세청) · 체크섬 검증.
- 운영 공고 수집 타이머 · relay 스크립트(별 작업 — 키는 2026-11-21 `koneps.env`·`relay.env` 로 갖췄다).
