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

## 하네스 레인 변경 (상시)

- (착수 시점 없음)
