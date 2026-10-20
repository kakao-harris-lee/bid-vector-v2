# M7/7D — 브라우저 인증 (착수 계약, 2026-10-16, 팀장)

- **근거**: `milestone-7.md` 7D · 운영자 결정 R-2((c) 소유 경계 선설계 + 단일 운영자 구현). 운영자 지시 2026-10-16 「병렬로 진행 가능한 M7
  작업」이 7D 를 골랐다. 권장 순서(7B → 7C → 7D)를 앞당기고 `milestone-7.md` 「선행 조건」(M6 완료 조건 성립)의 **운영자 예외**로 연다.
  그 결과 **`account` 표를 7B 보다 먼저 이 slice 가 세운다** — 7B 의 사용자 소유 표는 이 표를 참조한다.
- **되돌리기 어려운 경로**(인증 + 마이그레이션). 검토는 verifier(opus) + code-reviewer(sonnet) 병렬 + `migration-reviewer` 관점 + 개인정보
  관점(이 장비에 `privacy-gate` 정의가 없다 — verifier 표적으로 넣는다). **Codex 심판은 운영자가 범위·비용을 승인하지 않았으므로 타지 않는다**
  (승인하면 Phase 4 뒤 한 번).
- **base**: main `77850903` · worktree `bid-vector-v2-m7-7d` · 브랜치 `m7-7d/2026-10-16`.

## 산출물

1. **마이그레이션 V20**(다음 번호 — 병렬 레인 중 마이그레이션을 만드는 것은 이 slice 뿐):
   - `account` — 다수 계정을 전제로 한 표(R-2). 행은 하나만 만든다. 로그인 식별자 · 비밀번호 해시(평문·가역 암호 0) · 생성/갱신 시각 ·
     잠금 상태.
   - `browser_session` — 서버 세션. 세션 식별자는 **해시로만 저장**(쿠키 값 평문 저장 0) · `account_id` · 생성·마지막 사용·만료 시각 ·
     CSRF 토큰(해시 또는 세션 비밀로부터 유도) · 폐기 시각.
   - 감사(`api_request_audit`)의 주체가 계정을 가리킬 수 있게 한다(기존 열을 바꾸지 않고 값 규약으로 — 바꿔야 하면 계약 갱신).
2. **계정 부트스트랩**: 가입 화면 없음. 단일 계정은 **운영자가 명시적으로 실행하는 한 번의 명령**(환경변수로 받은 초기 비밀번호를 해시해
   저장, 이미 있으면 거부)으로 만든다. 기본 비밀번호 · 하드코딩 값 0.
3. **HTTP**:
   - `POST /api/session`(로그인) · `DELETE /api/session`(로그아웃) · `GET /api/session`(현재 세션 · CSRF 토큰 전달).
   - 쿠키: `HttpOnly` · `Secure` · `SameSite=Strict`(또는 Lax — 근거를 적고 하나로) · `Path=/` · 만료.
   - **CSRF**: 상태를 바꾸는 메서드(POST·PUT·PATCH·DELETE)는 세션에 묶인 토큰을 헤더로 요구한다. 로그인 자체는 로그인 CSRF 대책을 적는다.
   - 실패는 401 하나(사유를 나누지 않는다 — 6A 필터의 규율). 로그인 실패 회수 제한(계정 잠금 또는 지연) — 값은 정책으로 적는다.
   - 세션 만료(유휴·절대) · 로그아웃 시 서버 쪽 폐기 · 비밀번호가 바뀌면 그 계정 세션 전부 폐기.
4. **인증 필터의 결합**: 기존 `OperatorCredentialFilter`(모든 경로 기본 거부)를 깨지 않는다. 요청은 **세션 쿠키(+CSRF)** 또는 **기존 운영자
   자격 헤더** 중 하나로 인증된다 — 두 경로 모두 **기본 거부 하나의 등록**에서 판정되고, 새 endpoint 가 어느 경로에도 걸리지 않고 열리는 일이
   구조로 없어야 한다. 로그인 endpoint 만 인증 없이 열린다(허용 목록이 하나뿐임을 test 로 잠근다).
5. **OpenAPI** 에 세 operation 과 쿠키·CSRF 보안 스킴을 더하고 `web/src/api/generated/operator-api.ts` 를 재생성한다.
6. **web**: 로그인 화면 하나(`web/src` 의 라우트 자리 · 폼 · 실패 표시)와 클라이언트가 쿠키를 싣고 CSRF 헤더를 붙이는 설정(`src/api/client.ts`
   안에서만 — ADR 0011 D-3 의 사용처 등식). 화면 다섯은 만들지 않는다(7E).

## 설계 검토 (Phase 2.5, 팀장)

- **(0) 위협 모델**: 방어 — 비밀번호 추측(온라인) · 세션 탈취 후 재사용(쿠키 평문 저장·로그 노출) · CSRF(다른 사이트가 상태 변경 요청) ·
  로그인 CSRF · 세션 고정 · 로그아웃 뒤 재사용 · 인증 없이 열린 endpoint · 감사 누락 · 비밀번호·세션 값의 로그·응답·evidence 노출.
  방어하지 않음 — 운영자 단말 탈취 · TLS 종단 이전 구간(7F 의 TLS 가 맡는다; 로컬 http 개발 실행에서 `Secure` 쿠키 처리는 설정으로 적는다) ·
  다수 사업자 격리(후속).
- **(1) 구성**: 기본 거부 필터 하나의 등록에 두 인증 경로를 넣는다(열거가 아니라 구성). 허용 목록은 로그인 하나 — 집합 등식 test.
  비밀번호 해시와 세션 비교는 **타입**으로 감싼다(6A `OperatorCredential` 의 규율: raw 문자열이 필터 class 안에 존재하지 않는다).
- **(2) 우회 ≥5** — 레인이 각각 test 로 잰다: ① 쿠키 없이 상태 변경 → 401 ② 쿠키는 있고 CSRF 헤더 없음/틀림 → 거부 ③ 로그아웃한 세션
  쿠키 재사용 → 401 ④ 만료 세션 → 401 ⑤ 로그인 전 받은 세션 식별자가 로그인 뒤에도 유효(세션 고정) → 로그인 시 재발급 ⑥ 비밀번호 연속 실패
  → 제한 동작 ⑦ 새 endpoint 를 더해도 인증 없이 열리지 않는다(구성 test) ⑧ DB 에 세션 식별자 평문이 없다(해시만) ⑨ 응답·로그에 비밀번호·
  세션 값이 없다.
- **(2b) 값 획득 축** — 새 public 표면을 전수하고 각각이 밖에 허락하는 것을 적는다(구현 레인이 evidence 에 표로):
  세션 생성 함수(누가 부를 수 있나 — 로그인 use case 밖에서 세션을 만들 수 있으면 **없던 권한** = high) · 계정 부트스트랩 명령(재실행·
  덮어쓰기 불가) · 세션 조회 포트(식별자 해시만 받는가) · 비밀번호 검증 타입. 주입 자리(시계·난수)는 `internal` 주 생성자 + public 보조
  생성자 관례로 밖에서 못 꽂게 한다.
- **(3) 과잉·미달**: 가입·초대·비밀번호 재설정 메일·다수 계정 UI 는 하지 않는다(R-2). 미달 금지 — 기존 운영자 자격 헤더 경로 무회귀 · 감사
  유지 · 로그아웃 서버 폐기.

## in_scope / out_scope

- in: V20 마이그레이션 · 인증 관련 Kotlin(app·adapters 의 해당 패키지와 test) · 부트스트랩 명령 · `openapi/bidvector-operator-api.yaml` ·
  `web/src/api/generated/operator-api.ts` · `web/src/api/client.ts` · 로그인 화면 파일(`web/src` 아래, 경로는 레인이 기계 목록으로) ·
  필요한 의존 추가(빌드 파일, 근거와 함께) · `reports/evidence/m7/7d/**`.
- out: 7B·7C 의 표와 endpoint · 화면 다섯 · `.github/**`(필요하면 계약 갱신) · `docker/**`(7F 레인).

## acceptance

`ci.yml` 의 `check` job 과 `web` job 명령 그대로. 마이그레이션이 있으므로 `container` job 도 돈다(path-limited 생략 불가 — 이미지가
마이그레이션을 싣는다). `ml-engine` 은 입력 변경 0 이면 생략.

## rollback

마이그레이션은 되돌리기 어려운 경로다 — **forward-fix 가 정본**(6B-2 runbook 관례): 되돌림은 V20 이 만든 표를 지우는 새 마이그레이션이
아니라, 머지 전이면 in_scope 경로 한정 복원(`git restore --source=77850903 …`)이고 머지 뒤면 PR revert + 개발 DB 재생성이다. rollback.md 에
두 경우를 따로 적고, 머지 전 경우의 ①~⑥ 을 마지막 산출물 커밋에서 실측한다. 6B-2 백업·복원 리허설(`tools/db-rehearsal.sh`)이 V20 을 지나는지
(역할·권한 행렬 포함) 실측한다.

## 검토 레인

verifier(opus) + code-reviewer(sonnet) 병렬. verifier 표적: 우회 ①~⑨ 재현과 새 우회 고안 · (2b) 표 · 마이그레이션의 권한(역할 `bidvector_app`
의 GRANT) · 개인정보(비밀번호·세션 값이 로그·audit·evidence 에 0). 병렬 레인 7C① 도 OpenAPI·필터 등록 근처를 만진다 — 기존 operation 문면은
두 레인 모두 바꾸지 않고, 충돌은 나중 머지 쪽이 정리한다.

## 호스트 빌드

모든 Gradle·Testcontainers·docker build·npm 빌드 명령은 `flock ~/.local/bid-vector-build.lock <명령>` 으로 감싼다(레인 넷 병렬). 점검 셋은
별도 호출로 먼저. 00:30~03:00(6G 수집 창)에는 무거운 빌드를 시작하지 않는다.

## 계약 갱신 이력

**D-7D-1 (2026-10-16, 팀장 — 착수 중 경로 갱신).**
- 인증 값·포트·정책·use case 를 `workflow` 에 둔다. `app.http` 와 `adapters` 가 둘 다 이름으로 불러야 하는데, 의존 게이트상 공유할 수 있는
  자리는 `bidvector.workflow.*` 뿐이다(`EditSessionRepository` 선례). 산출물 1~6 은 그대로이고 늘어난 것은 경로뿐이다.
- **in_scope 추가**:
  - `workflow/src/{main,test}/kotlin/bidvector/workflow/access/**`
  - `config/quality/architecture-policy.properties`(새 키 · 표면 · 주입 · 허용 목록)
  - `gradle/libs.versions.toml` · `adapters/build.gradle.kts` · `app/build.gradle.kts`(해시 라이브러리 좌표 · compatibilitySmoke 등재)
  - `adapters` 의 `CleanMigration*Test` 다섯(표·열·CHECK·인덱스·권한 행렬 등식)
  - `RetentionTableRegistry`(새 표 둘의 보존 분류)
- **게이트 설정(`architecture-policy.properties`)을 바꾸므로** 그 변경은 verifier 의 표적 재검증 대상이다. 허용 목록을 넓히는 줄마다 근거를
  evidence 에 적는다.

**D-7D-2 (2026-10-16, 팀장 — 구현 수령 · 동결).** 판정 대상은 산출물 `12221080` · evidence `a4611af8` 다.
- **in_scope 추가를 수용한다**(레인 보고 「계약 r1 목록 밖」 여섯):
  - `config/quality/gate-tests.properties`(FQN 여섯)
  - `PersistenceTestSupport`·`RetentionFixture`(새 표 둘)
  - `app` architecture test 넷(새 비밀값 축 키 · 면제 수)
  - KDoc 이름 반영 넷 · 상수·필터 이름 반영뿐인 기존 app test 열셋 · `RetentionWiringTest`
  - `web/vite.config.ts`(vitest 환경의 절대 기준 주소)
  - 게이트 test·설정 데이터는 verifier 표적이다.
- **새 의존**: `at.favre.lib:bcrypt:0.10.2`(+ 전이 `at.favre.lib:bytes`). 근거는 어댑터 KDoc 에 있다. `compatibilitySmoke` 에 등재됐다.
- **로컬에서 못 잰 축 둘**:
  - container job 의 trivy 설치 실패 → 취약점 게이트 둘
  - web job 의 Playwright 시스템 의존 step(apt 권한)
  - 둘 다 **PR CI 가 정본**이다.
- 변이 ④(만료)는 두 기제가 지켜 단독 변이가 초록이고, 둘을 함께 걷어야 RED 다. 심층 방어로 등재했다.
- 남은 알려진 제한은 13 이다(checklist 5절). 후속 하나: `adapters/audit` KDoc 의 옛 필터 이름.

**D-7D-3 (2026-10-19, 팀장 — 수정 r1 수령 · 동결).** 판정 대상은 마지막 산출물 `b8541a80` · evidence `2ddb5f21` 다. 입력은 code-reviewer
r1(REQUEST_CHANGES: H-1 · M-1~M-3 · low 여섯)이다. 그 리포트 파일과 verifier r1 의 판정은 2026-10-17 세션 종료로 남지 않았다. 그래서 검토는
verifier r1(전건)과 code-reviewer r2(전건 + 네 지적 표적)를 새로 연다.
- **닫힘을 수령한 것(레인 실측)**: H-1 `5aa11166`(원자 갱신 · 조건부 초기화 · 스냅숏에서 계수 필드 제거) · M-1 `0d309f42`(긴 문구 →
  같은 경로의 401) · M-3 `34a398d2`(CSRF 스킴 선언 · 개방 경로 집합 등식) · M-2 `ed5b47bf`+`b8541a80`(로그인 문장별 트랜잭션 · 검증 중
  커넥션 미보유). 변이 여덟은 모두 RED 였다.
- **in_scope 추가를 수용한다**: `adapters` 의 `AccessConcurrencyTest`(신설) · `config/quality/architecture-policy.properties` 의
  `app.adapter.member-call-pairs` 한 줄. 게이트 설정 데이터이므로 verifier 표적이다.
- **알려진 제한**: 14~17 신설을 수용한다 — 긴 문구 거동 변화 · 검증 중 잠긴 계정의 올바른 로그인 거부 · 로그인 세 단계 사이 비원자(모두
  「세션 없음」 쪽) · 아래 게이트 데이터 형식. 제한 7 의 정정도 수용한다.
- **반입 조건(7F 본편)**: 프록시의 `/api/session` 유량 제한. M-2 로 풀 고갈 증폭은 사라졌지만 CPU 축(동시 로그인 N 건 = KDF N 회)은
  남는다. 7F-local 계약 D-7F-3 에도 후보로 적었다.
- **후속(이 slice 밖)**: `app.adapter.member-call-pairs` 는 목록 구분자와 서술자 구분자가 같은 쉼표다. 그래서 매개변수가 둘 이상인 어댑터
  멤버를 적을 수 없고, 로그인 경계는 람다 하나를 받는 `inSignInScope` 로 두었다. 게이트 데이터 형식을 고치는 별도 slice 로 남긴다.
- cr r1 의 low 여섯은 문면이 남지 않았다. 「새로고침 뒤 CSRF 재획득 경로 없음」은 레인이 `GET /api/session` 의 재유도로 이미 구현돼
  있다고 보고했다. cr r2 가 전건 리뷰로 low 를 다시 낸다.

**D-7D-4 (2026-10-19, 팀장 — 검토 수령: verifier r1 not-ready · code-reviewer r2 APPROVE).** 재작업 2/5.
- **수정 r2 에서 닫는 것**
  - **vr F1(high) — 교체 경합으로 남는 세션**: 산출물 3 「비밀번호가 바뀌면 그 계정 세션 전부 폐기」를 깬다. 발급 문장(실패 초기화 ·
    `openReplacing`)이 **읽은 자격 해시(또는 그 판)가 그대로일 때만** 성립하게 한다. 같은 자리에서 **cr M-1**(동시 로그인 둘 → live 세션
    둘, `rotate` 같은 창)도 닫는다 — 계정 행 잠금(`FOR UPDATE`) 같은 직렬화 한 기제로 둘을 함께 닫는 것을 권한다. vr 의 probe(옛 값 로그인을
    검증 안에 세워 두고 교체 커밋)를 회귀 test 로 들이고, 조건을 걷은 변이가 RED 여야 한다. 알려진 제한 16 의 문면을 실측에 맞게 고친다.
  - **vr F2 — 행위자 귀속**: 세션 경로의 outbox actor 가 감사 주체(계정)와 같게. 「감사 주체 == outbox 행위자」 불변식을 세션 경로에서도 잰다.
  - **vr F3 — 계정 명령 러너**: KDoc 이 인용하는 test 를 실재하게 하고, 빈 초기값을 거부(비-0 종료)한다.
  - **vr F4 + cr L-8 — OpenAPI 등식 범위**: 개방 집합 등식을 **모든 operation** 으로, 자격 헤더 대안의 소실도 잡는 방향까지.
  - **cr M-2**: checklist 의 계정 명령에 웹 표면을 끄는 설정을 넣는다(가동 중 포트 충돌 · 내린 뒤 무인증 표면 둘 다 닫힘).
  - **cr M-3**: 세션 쿠키를 `__Host-` 접두로(Secure · Path=/ · Domain 없음 — 로컬 TLS 에서 성립하는지 실측).
  - **low 중 바로 닫는 것**: vr F5(계정 간 격리 test) · vr F6(`created_at` 열 권한 등식) · vr F9(문면) · cr L-2(로그인 화면 네트워크 실패
    처리) · cr L-3(404 문구) · vr F7=cr L-1 은 **주석을 현재 사실로 고치고** 제한 등재(쓰기 화면이 생기는 slice 에서 재획득 호출을 붙인다).
- **알려진 제한으로 등재**: vr F8(로그인 성공 감사 행의 계정 · 잠금/rotate 감사 행 부재 — 감사 확장 후속) · cr L-4(만료 세션의 로그아웃) ·
  cr L-5(요청당 커넥션 두 번) · cr L-6(원문 힙 수명) · cr L-7(예외 시 명시 롤백 — 기존 코드) · cr L-9(해제 뒤 첫 주기 시도 수) · vr F10(캐시
  적중 기록 — 레인은 acceptance 의 `:workflow:test` 를 `--rerun-tasks` 또는 캐시 없이 한 번 더 잰다).
- 수정 뒤 verifier 표적 r2(F1~F4 · cr M-1·M-3 · 게이트 등식) + code-reviewer 표적. rollback ①~⑥ 은 새 마지막 산출물 커밋에서.

**D-7D-5 (2026-10-19, 팀장 — 수정 r2 수령 · 동결).** 판정 대상은 마지막 산출물 `e3b0b4d3` · evidence `4b526a7a` 다.
- **in_scope 추가를 수용한다**: 신규 `adapters` 의 `JdbcSignInCompletion.kt` · `app` test `AccountCommandRunnerTest.kt`. 기존 파일 편집
  `StrategyEditController.kt` · `StrategyEditExecutor.kt` · `StrategyEditExecutorRaceTest.kt` 도 수용한다. 이 셋은 6A-2b 소관이지만 결함(F2,
  세션 경로의 행위자 귀속)은 7D 가 만든 것이라 여기서 닫는다.
- **알려진 제한 16 은 정정으로 바꾼다**: 「세션 없는 쪽으로만 치우친다」는 거짓이었다(F1 이 권한이 새는 반례를 실 DB 에서 재현). 18~24 신설을
  수용한다. 그중 하나는 「OpenAPI 로더가 중복 YAML 키를 조용히 받아들인다」이다 — 문서에 선언을 **더하는** 변이는 앞으로 판정 근거로 쓰지
  않는다(M27 이 그래서 초록이었고, 교체 변이 M27b 로 RED 를 확인했다).
- **후속(이 slice 밖)**: `OwnTransactionConnectionSource` 의 명시 롤백(L-7) · 7E 가 `ensureCsrf()` 를 붙이는 자리(L-1).
- 검토: verifier 표적 r2 + code-reviewer 표적 r3.

**D-7D-6 (2026-10-20, 팀장 — 검토 수령: verifier r2 ready-for-review · code-reviewer r3 APPROVE).** 재작업 3/5. 마지막 배치 뒤 PR 로 간다.
- container job 재생의 skipped 7 은 `uses:` 4 + trivy 3(등재된 로컬 제한, PR CI 가 정본)이다 — 매달린 조건이 아니다.
- **마지막 배치에서 닫는 것**
  - **vr r2 medium — 동시 로그인 회귀 test 가 확률적이다**: 잠금을 걷은 변이가 1회차 초록이었고 20회 검출률은 14~15/20 이다. 래치로 두 확정을
    같은 순간에 세우는 **결정적** test 로 바꾼다(반복 루프는 보조로만). 잠그지 않는 확정 변이(R-M23a)가 **매 실행** RED 여야 한다 — 같은 변이를
    여러 번 돌려 검출률 100% 를 적는다.
  - **cr r3 권고 — OpenAPI 로더의 중복 키**: 로더를 중복 키 거부로 바꾼다(`ArchitecturePolicy` 의 `duplicateKeys` 가드가 같은 함정을 닫은 선례다).
    그러면 제한 24 는 닫히고 「더하기 변이」가 판정 근거로 돌아온다. 중복 키를 심은 변이가 RED 여야 한다.
  - **cr r3 L-1**: 쿠키 조회 주석을 사실대로(상수 시간 성질은 그대로이고, 바뀐 것은 구조 게이트 술어를 지나는 모양이다).
  - **cr r3 L-2**: 로그인 확정의 행 잠금에 `lock_timeout` 을 설정 값으로 둔다(커넥션을 쥔 채 무한 대기하지 않게). 시한 초과는 로그인 거부로.
  - **vr r2 low**: 성공 로그인이 실 DB 의 실패 계수를 비우는 test.
  - **cr r3 L-4**: `cookie-secure` 를 뒤집으면 쿠키 이름이 바뀌어 전 세션이 무효가 된다 — runbook 과 7F 반입 절차에 한 줄.
- **알려진 제한으로 등재**: cr r3 L-3(자격 해시를 동시성 토큰으로 씀 — 거동은 정확) · cr r3 L-5(`account:` 접두 규약의 공유 상수 — 감사 확장 slice) ·
  vr r2 low 둘(경로를 섞은 편집의 행위자 가드 거부 — 의도된 거동 · `__Host-` 쿠키의 실 브라우저·TLS 측정 — 7F 반입 때).
- 배치 뒤 verifier 표적 r3(결정적 test · 중복 키 · lock_timeout · rollback). high·medium 0 이면 PR.

**D-7D-7 (2026-10-20, 팀장 — 마지막 배치 수령 · 동결).** 판정 대상은 마지막 산출물 `d73b91c6` · evidence `f7b347fc` 다.
- **결정적 동시성 test 를 수령한다**: test 가 다른 커넥션으로 계정 행을 먼저 잠그고(커밋 없이) 그 상태에서 로그인을 세운다. 잠그지 않는 확정
  변이(R-M23a)는 10회 반복에서 10/10 RED 다.
- **대기 상한 값(`lockWait`)을 정책 데이터(`BrowserAccessPolicyData`)에 둔 선택을 수용한다**: 이 값이 바꾸는 것은 운영자에게 보이는 거동(기다릴지
  거부할지)이라 효력일과 함께 움직인다. 환경마다 달라야 할 요구가 생기면 설정 키로 올린다. D-7D-6 의 「설정 값」 문면은 이 뜻으로 읽는다.
- **제한 24 는 닫혔다**(OpenAPI 로더가 중복 키를 거부한다 — 「더하기」 변이 R-DUP 이 RED). 제한 25~28 신설을 수용한다.
- 7F 반입 절차의 `cookie-secure` 한 줄은 7F 본편 계약으로 넘긴다.
- 검토: verifier 표적 r3 + code-reviewer 표적 r4. 둘 다 high·medium 0 이면 PR.

**D-7D-8 (2026-10-20, 팀장 — 검토 수령: verifier r3 ready-for-review(0/0) · code-reviewer r4 REQUEST_CHANGES(M 1)).** 재작업 4/5.
- **닫는 것**
  - **cr r4 M-1 — 대기 상한이 반쪽이다**: 상한이 `complete()`(올바른 문구)에만 걸렸다. 같은 계정 행을 잠그는 `registerFailure()`(틀린 문구 —
    **자격 없이 칠 수 있는 경로**)는 여전히 무한 대기한다. 그래서 cr r3 L-2 를 낸 이유(커넥션을 쥔 채 대기 → 풀 고갈)가 더 싼 입구로 남는다.
    상한을 `inSignInScope` 의 연결 래퍼로 올려 그 scope 의 모든 문장을 덮는다. 회귀 test: 계정 행을 잠근 채 **틀린** 문구 로그인이 상한 안에
    끝난다(지금은 끝나지 않는다).
  - **vr r3 R3-1**: 상한 test 에 JUnit `@Timeout` — 상한이 사라지면 CI 가 360 분이 아니라 test 시한에 붉어진다.
  - **cr r4 L-1**: 잠금 대기 초과(55P03)를 삼키는 자리 — 경합과 오타가 구별되게 최소한 감사/로그에 사유를 남긴다(값·비밀 없이).
  - **cr r4 L-5**: `OpenApiSpecSupport` KDoc 의 소비자 수(넷).
- **알려진 제한·후속**: vr r3 R3-2(결정적 test 는 쪼갠 트랜잭션 변이를 못 잡고 장벽 test 가 잡는다 — 두 test 의 역할로 등재) · cr r4 L-2(`lockWait`
  는 배포 설정으로 못 바꾼다 — 수용한 대가) · cr r4 L-3(제한 22 의 상시화) · cr r4 L-4(`SharedKernelCorpusConformanceTest` 의 다른 YAML 로더에도
  같은 중복 키 함정 — 이 slice 밖, 후속).
- 배치 뒤 verifier 표적 r4 + code-reviewer 표적 r5. high·medium 0 이면 PR.

**D-7D-9 (2026-10-20, 팀장 — 작은 배치 수령 · 동결).** 판정 대상은 마지막 산출물 `75cce8b6` · evidence `f075a9d8` 다.
- **M-1 처방으로 `ConnectionSource` 래퍼 대신 access 패키지의 좁은 타입(`SignInConnections`)을 둔 선택을 수용한다.** `ConnectionSource` 는
  sealed 라 같은 패키지(`adapters.persistence`, in_scope 밖)에서만 구현할 수 있다. 효과는 같고(로그인 scope 의 모든 문장이 대기 상한 아래),
  어댑터 둘이 `internal` 로 좁아졌다. in_scope 추가: `adapters/.../access/SignInConnections.kt`.
- **`@Timeout` 은 `SEPARATE_THREAD` 여야 한다**: 기본 `SAME_THREAD` 는 돌아온 뒤에 재는 사후 판정이라, 블록된 JDBC 호출을 끊지 못한다. 변이
  짝(R-R31a 240초 멈춤 · R-R31b 30초 RED)이 그 차이다.
- 제한 29~32 신설을 수용한다. cr r4 L-1 의 잔여(`Contended` 를 기록하는 소비자가 아직 없다)는 7F 본편·감사 확장의 후속으로 둔다.
- 검토: verifier 표적 r4 + code-reviewer 표적 r5.

## 하네스 레인 변경 (상시)

- 없음(range 에 `.claude/**`·`CLAUDE.md` 변경 0).
