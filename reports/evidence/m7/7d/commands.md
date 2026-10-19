# M7/7D 실행 명령과 종료 코드

acceptance 는 `.github/workflows/ci.yml` 의 **job 명령 그대로**다(계약 「acceptance」). 마이그레이션이
있으므로 `container` job 도 돈다 — 이미지가 마이그레이션을 싣는다. `ml-engine` 은 입력 변경 0 이라
생략한다(그 job 의 입력 경로를 이 slice 가 건드리지 않는다).

호스트는 레인 넷이 공유하므로 모든 Gradle·Testcontainers·docker·npm 명령을
`flock ~/.local/bid-vector-build.lock <명령>` 으로 감쌌다. 아래 `cmd` 는 그 래퍼 안쪽 명령이다.

**마지막 HEAD 의 결과 정본은 이 문서가 아니라 verifier 와 PR 조치 코멘트다**(evidence 는 자기
마지막 커밋의 post-state 를 담을 수 없다 — 그 줄을 적으려면 커밋이 하나 더 필요하다).

<!-- 아래 표는 실행 직후 append 한다. 출력 전문을 붙이지 않는다(핵심 결과 한 줄). -->

## 2026-10-16 (KST, 구현 중 표적 실측 — acceptance 전건은 아래 절)

- cmd: `./gradlew :workflow:test --tests '*access*'`
- exit: 0
- 핵심 결과: `BrowserAccessWorkflowTest` 12 · `AccountProvisioningTest` 4, 실패 0

- cmd: `./gradlew :app:test --tests '*BrowserSessionAuthenticationTest*'`
- exit: 0
- 핵심 결과: 15 tests, 실패 0(쿠키 속성·CSRF·허용 목록·기계 전수 포함)

- cmd: `./gradlew :app:test --tests '*AppHttpDependencyGateTest*' --tests '*CollectionArchitectureGateTest*' --tests '*ArchitectureGateTest*' --tests '*CollectionDepthGateTest*'`
- exit: 1
- 핵심 결과: **RED 가 하네스 정책 갱신을 지목했다** — 주입 표면 등식 · 운반 타입 예외 · 자격증명 참조자 · 러너·로거 참조자 · 패키지 순환(`app.http` ↔ `app.wiring`)

- cmd: 같은 명령(정책·배선 수정 뒤, `ConstantTimeComparisonStructureTest` 포함)
- exit: 0
- 핵심 결과: 게이트 다섯 전부 통과

- cmd: `./gradlew :adapters:test :app:test`
- exit: 1
- 핵심 결과: `CleanMigrationPrivilegeTest` 축9 가 **측정으로 정정을 요구**했다 — 열 단위 UPDATE 는 `has_table_privilege` 에 세어지지 않아 두 표가 `update=false` 다(기대값을 그 측정으로 고쳤다). `TransportSurfaceGateTest` 다섯은 필터 이름 변경 등재 누락

- cmd: `./gradlew :app:test --tests '*TransportSurfaceGateTest*'`
- exit: 0
- 핵심 결과: 전송 표면 쌍·보유자 멤버 등식 통과(이름 변경 등재 뒤)

## 2026-10-16 (KST) 비밀값 스캔

- cmd: `grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m7/7d/`
- exit: 1
- 핵심 결과: **매치 0 = 통과**(패턴 어휘를 evidence 에 축어로 적지 않는다 — 정책 키를 인용할
  자리는 접미만 적었다)

- cmd: 같은 명령을 **산출물 경로**(in_scope 의 Kotlin·SQL·web·openapi)에
- exit: 0
- 핵심 결과: 매치 일곱, **전부 정당**하고 육안 확인했다 — HTML 표준 속성값 둘(입력칸 가림·암호
  관리자 힌트) · OpenAPI 표준 보안 스킴 타입 셋 · JDK 클래스 이름 하나(채택 근거 문단) · 정책 키
  이름 인용 하나(KDoc). 값·자격·해시 문면은 하나도 없다. 소스 트리는 `leakPatternGate` 의 스캔
  대상이 아니다(그 게이트의 scanRoot 는 evidence 디렉터리다 — 그 근거는 그 task 의 KDoc).
- 육안 확인(패턴으로 못 잡는 축): Telegram id·사업자 번호·실 계정 값 0 · test 고정값은 전부
  `…-test-fixture-…` 문구이고 운영 값과 겹치지 않는다.

## 2026-10-16 (KST) acceptance — `container` job

`ci.yml` 의 `container` job 을 그 job 의 **step `run` 본문 그대로** 돌렸다(명령을 손으로 옮겨
적지 않고 워크플로를 파싱해 꺼냈다 — 옮겨 적은 사본은 낡는다). GitHub 전용 요소만 로컬 등가로
바꿨다: `uses:` step 은 건너뜀(이 장비에 이미 있다) · `$GITHUB_ENV` 는 임시 파일 · 일회성 값 둘은
job 과 같은 방식(`openssl rand`)으로 실행 시점에 만들었다.

- step 3·4 (일회성 DB·운영자 자격 값 생성) exit 0
- step 5 (ml-serving 이미지) exit 0 · step 6 (`:app:bootJar`) exit 0 · step 7 (앱 이미지) exit 0
- step 8·9 (이미지 위생 게이트 둘) exit 0 — **V20 을 실은 앱 이미지가 그 게이트를 지난다**
- step 10 (앱 이미지 거부 스모크) exit 0 — 「운반 이름공간 조기 거부」 통과
- step 11 (trivy 설치) **exit 1** · step 12·13 (취약점 게이트 둘) **exit 2** — 이 장비에서 도구
  설치가 실패했고 게이트는 「도구 부재」로 끊겼다(자기 판정이 아니라 전제 부재다). **CI 는 이
  자리에서 멈춘다** — 이 slice 가 의존 하나를 더했으므로 그 축은 CI 에서 다시 봐야 한다(알려진
  제한). 그 뒤 step 들은 이 slice 의 계약 항목이라 로컬에서 이어 돌렸다.
- step 15 (compose 기동) exit 0 — app·ml-serving·postgres 가 **healthy 로 수렴**했다. 즉
  **V20 을 실은 이미지가 기동하면서 마이그레이션이 적용된다**(기동이 그 부수효과다)
- step 16 (컨테이너 스모크: 준비 상태·**인증 경계**·health 격리·수집 꺼짐) exit 0 — 바뀐 인증
  필터 아래에서도 그 경계가 선다
- step 17 (**백업·복원·되돌림 리허설** `tools/db-rehearsal.sh docker/compose.yaml postgres`) exit 0 —
  계약이 요구한 측정이다. 핵심 결과 한 줄: 「복원 등식 10가지 · forward-fix · 데이터 손실 왕복」
  통과. **V20 을 지난다**는 것은 그 산출물의 수치가 말한다 — 백업이 적은 Flyway 이력의 top 이
  **20**(표 20개)이고, 복원본의 `validate()` 가 같은 이력에서 통과했다. **역할·권한 행렬도
  그 산출물에 든다**(「역할 2 · 권한 행렬 1역할」) — 리허설은 그 둘을 백업·복원 등식의 일부로
  잰다. probe 마이그레이션은 V21·V22 로 **도출**됐다(스크립트가 최대 번호에서 계산한다 — V20 과
  충돌하지 않는다)
- step 18 (실 Kotlin gateway ↔ 컨테이너의 실 Python 서버) exit 0
- step 19 (정리 — 볼륨까지) exit 0 — 컨테이너·네트워크·볼륨이 지워졌다(개발 DB 컨테이너는
  이 compose 프로젝트 밖이라 건드리지 않는다)

**step 11~13 의 중단 때문에 뒤 step 들이 한 번 돌지 않았다** — 그것을 CI 와 같은 순서로 이어
돌렸고(같은 job 환경 값·같은 step 본문), 그 과정에서 **로컬 러너의 결함 하나**를 고쳤다: step
스크립트가 러너의 표준 입력(step 목록)을 먹어 **뒤 step 들이 조용히 사라졌다**(16 에서 끝나고
17~19 가 돌지 않았다). `< /dev/null` 로 끊었다 — 「안 돌린 게이트는 아무것도 막지 못한다」의
로컬 판이다.

## 2026-10-16 (KST) acceptance — `check` job

`ci.yml` 의 `check` job 은 명령 셋이다(`./gradlew --no-daemon check` · `./gradlew --no-daemon
qualityBaseline` · `./tools/one-command-check.sh`). 앞의 둘은 그 job 의 step `run` 본문 그대로다.

- cmd: `./gradlew --no-daemon check`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL · 350 actionable tasks — 컴파일·ktlint·detekt·sizeGate·
  architecture test·`compatibilitySmoke`·`jarContentGate`·등재/실행 게이트 전부 초록

- cmd: `./gradlew --no-daemon qualityBaseline`
- exit: 0
- 핵심 결과: 기준선 산출 통과(게이트가 아니라 측정이다 — 워크플로 주석)

- cmd: `./tools/one-command-check.sh` (S-20 — 완료 조건 1)
- exit: 0
- 핵심 결과: 「Kotlin 전건 + Python 전건 + web 전건 통과」 — 이 명령이 `check` job 의 셋째 step 이고
  그 안에서 web 레인 전건(W-1~W-11)을 다시 돈다

**게이트 등재 장부가 두 라운드를 만들었다**(실패 이력을 남긴다 — 검증이 실제로 작동했다는
증거다). `gateRegistrationGate` 는 모듈별 양방향 등식이라 모듈 하나가 붉으면 그 뒤 모듈의
같은 게이트가 **돌지 않는다**: 먼저 `:workflow:gateRegistrationGate` 가 신설 패키지의 test
둘을 잡았고, 그것을 등재한 다음 실행에서 `:app:gateRegistrationGate` 가 또 둘을 잡았다.
「안 돌린 게이트는 아무것도 막지 못한다」가 **한 build 안에서도** 성립한다.

## 2026-10-16 (KST) acceptance — `web` job

`ci.yml` 의 `web` job 을 step `run` 본문 그대로 돌렸다(`uses:` step 은 건너뜀 — 이 장비에 이미
있다).

- step 2~10 (W-1 설치 · W-2 eslint · W-3 typecheck · W-4 컴파일 경계 전제 · W-5 생성 타입
  **바이트 등식** · W-6 vitest · W-7 게이트 test 실행 확인 · W-8 build · W-9 번들 경계 카나리)
  전부 exit 0
- step 11 (Playwright **시스템 의존** 설치 — CI 전용) **exit 1** — 이 장비의 apt 권한 제약이고
  브라우저 자체는 이미 설치돼 있다(알려진 제한 · CI 에서는 이 step 이 러너 권한으로 선다)
- step 12·13 (W-10 chromium 설치 · W-11 Playwright 스모크) exit 0 — 스모크 2 passed

## 2026-10-16 (KST) 변이 실측

변이는 **바꿔치우기**다 — 더하기만 한 변이는 초록이 되므로 사본 대조(`git diff --no-index
--numstat <사본> <파일>`)로 **교체임을 계수**하고 복원은 사본에서 했다(`git checkout --` 을
쓰지 않는다 — 같은 파일의 미커밋 편집을 함께 지운다). 종료 코드 1 = RED.

| id | 자리 | numstat | exit |
| --- | --- | --- | --- |
| M1 | 필터의 기본 거부 → 기본 허용 | 1/1 | 1 |
| M2 | CSRF 면제 판정 → 상수 참 | 1/1 | 1 |
| M3 | `signOut` 의 폐기 → 사용 시각 갱신 | 1/1 | 1 |
| M4 | 유휴 만료 판정 → 상수 참 | 1/1 | 1 |
| M4b | **절대** 만료 판정 → 상수 참 | 1/1 | 0 |
| M4c | 유휴 시한의 **클램프** 제거 | 1/1 | 0 |
| M4d | 위 둘을 **함께** | 2/2 | 1 |
| M5 | 재발급의 기존 세션 폐기 → 무관한 갱신 | 1/1 | 1 |
| M6 | 상한 도달 시 잠금 → 계수만 올림 | 1/1 | 1 |
| M7 | 무인증 허용 목록을 둘로 | 1/1 | 1 |
| M8 | 세션 저장 키를 해시 → 평문 | 1/1 | 1 |
| M9 | 응답의 CSRF 자리에 쿠키 값 | 1/1 | 1 |
| M10 | V20 의 열 단위 UPDATE → 표 단위 | 1/1 | 1 |
| M11 | 쿠키의 `HttpOnly` → 끔 | 1/1 | 1 |
| M12 | 화면 클라이언트의 `credentials` → `include` | 1/1 | 1 |
| M13 | 화면 클라이언트의 CSRF 면제 판정 → 상수 참 | 1/1 | 1 |

- M4b·M4c 가 **0**(초록)인 것은 결함이 아니라 **기제가 둘**이라는 사실이다 — checklist 의 ④ 주.
- 변이 전 그 test 가 초록이라는 전제는 같은 라운드의 전건 실행이 낸다. 앞 두 라운드의 web
  변이(M12·M13)는 그 전제가 깨진 상태였으므로 **다시 쟀다**.

## 2026-10-16 (KST) 되돌림 ①~⑥ 실측

- cmd: 임시 clone 에서 `rollback.md` 의 복원 명령과 ④⑤⑥ (스크립트는 scratchpad)
- exit: 0 (①~⑥ 전부)
- 핵심 결과: 복원 **D 30 · M 44 · R 1** 뒤 base 대비 diff 빈 출력 · 남은 차이는 evidence
  디렉터리뿐 · compile·test·게이트 17 전부 초록. 같은 clone 에서 evidence 까지 포함해 복원한
  판은 **base 와 트리 해시가 같다**(전수 diff 0 행)

## 2026-10-16 (KST) clean-tree 판정

- cmd: `git status --porcelain -- <산출물 in_scope 경로들을 개별 인자로>`
- exit: 0 · **빈 출력(0 행)** — 구현 diff 가 전부 커밋돼 base/head 가 고정됐다
- 양성 대조 1회: 같은 경로의 파일 하나에 주석 한 줄을 심으니 ` M …` 한 행이 잡혔고, **사본에서
  복원**해 다시 0 행이 됐다(`git checkout --` 을 쓰지 않는다 — 같은 파일의 미커밋 편집을 함께
  지운다). 경로는 **개별 인자**로 넘겼다(변수 하나로 묶으면 pathspec 이 하나가 되어 아무것도
  매치하지 않고 「빈 출력·exit 0」이 더러운 트리와 구별되지 않는다)
- 이 판정의 대상은 **산출물 경로**다. evidence 디렉터리를 포함한 판정은 그 마지막 커밋의
  post-state 라 이 문서가 담을 수 없다 — **정본은 verifier 와 PR 조치 코멘트**다(이 문서 머리말).


## 2026-10-19 (KST) code-review r1 수정 라운드 — 표적 실측

지적 넷(H-1 · M-1 · M-2 · M-3)을 **지적별 커밋**으로 고치고, 그때마다 표적 test 를 돌렸다.
호스트 점검 셋(다른 레인의 Gradle · 가용 메모리 · 상위 RSS)은 매 빌드 전 **별도 호출**로 먼저
봤고, 모든 Gradle 명령은 `flock` 안이다(레인 넷 병렬 — 이 라운드에는 한 번에 열 분 넘게 lock 을
쥐지 않으려고 **호출마다** 잡고 놓았다).

- cmd: `./gradlew :workflow:test --tests '*access*' :adapters:test --tests 'bidvector.adapters.access.*' :app:test --tests '*BrowserSessionAuthenticationTest*' --tests '*ProductionBrowserAccessTest*' --tests '*OpenApiContractTest*' --tests '*OperatorAuthenticationTest*'`
- exit: 0
- 핵심 결과: H-1·M-1·M-3 의 새 test 포함 전부 통과 — 동시성 3 · 저장 11 · 권한 9 · 계약 11 · 세션 16 · 운영자 4 · use case 16

- cmd: 같은 형태로 M-2 뒤(게이트 test 넷과 기존 쓰기 E2E 를 더해서)
- exit: 0
- 핵심 결과: 동시성 4(풀 축 추가) · 의존/구조 게이트 21+13+24 · 감사 4 · 전략 편집 E2E 6 전부 통과

## 2026-10-19 (KST) acceptance — `check` job (r2)

- cmd: `./gradlew --no-daemon check` · exit **0**
- cmd: `./gradlew --no-daemon qualityBaseline` · exit **0**
- cmd: `./tools/one-command-check.sh` (S-20) · exit **0** — Kotlin 전건 + Python 전건 + web
  전건(W-1~W-11, Playwright 스모크 2 passed 포함)

`container` job 은 이 라운드에서 다시 돌리지 않았다 — 이 라운드의 변경이 **마이그레이션·
Dockerfile·compose 를 건드리지 않았고**(산출물 경로는 Kotlin·OpenAPI·게이트 설정뿐) r1 에서
그 job 의 결과와 알려진 제한(도구 설치 실패 둘)이 이미 등재돼 있다. 의존은 더하지 않았다.

## 2026-10-19 (KST) 변이 실측 (r2)

같은 방법이다 — **바꿔치우기**, 사본 대조(`git diff --no-index --numstat`)로 교체임을 계수,
복원은 사본에서. 이 라운드는 **변이 전 기준선을 같은 스크립트가 먼저 돌려** 그 전제를 실측했다
(네 표적 전부 exit 0 · 실패 0). 종료 코드 1 = RED.

| id | 자리 | numstat | exit |
| --- | --- | --- | --- |
| M14 | 계수 증가 → 읽은 값에서 계산한 절대값 | 1/1 | 1 |
| M15 | 상한 아래 잠금 보존 → NULL 덮어쓰기 | 1/1 | 1 |
| M16 | 성공 초기화의 잠금 조건 제거(무조건 비움) | 1/1 | 1 |
| M17 | 긴 입력 전략 → 던지는 쪽(옛 기본값) | 1/1 | 1 |
| M18 | 로그인 경계를 문장별 → 한 트랜잭션(옛 모양) | 2/1 | 1 |
| M19 | 재발급의 기존 세션 폐기를 무관한 계정으로 | 1/1 | 1 |
| M20 | 상태 변경 operation 하나에서 CSRF 요구 제거(기본 상속) | 0/4 | 1 |
| M21 | 로그인의 무인증 선언 제거(열린 집합이 필터와 갈림) | 0/1 | 1 |

- M20·M21 의 numstat 이 `0/N` 인 것은 **삭제 변이**이기 때문이다. 이 둘이 닫는 결함이 「선언이
  없다」라서, 더하기가 아니라 **빼기**가 그 결함의 재현이다(초록이 되는 더하기 변이와 다르다).
- M14 는 동시성 test 셋을 한꺼번에 붉힌다 — 절대값 쓰기는 H-1 의 세 축(계수·늦은 실패·늦은
  성공) 전부의 공통 원인이다.
- 변이 뒤 복원은 `git status --porcelain -- <변이 파일 다섯>` **빈 출력**으로 확인했다.

## 2026-10-19 (KST) 비밀값 스캔 (r2)

- cmd: `grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m7/7d/`
- exit: 1 — **매치 0 = 통과**
- cmd: 같은 명령을 이 라운드가 바꾼 **산출물 경로**에
- exit: 0 — 매치는 전부 정당하고 육안 확인했다: 채택한 KDF 라이브러리의 **긴-입력 전략 타입
  이름 둘**(API 이름이다 — 값이 아니다) · 채택 근거 문단의 JDK 클래스 이름 하나 · OpenAPI 표준
  보안 스킴 타입 · 기존 Testcontainers 고정값 줄(이 라운드 밖). 값·자격·해시 문면은 0.
- 그 API 이름 둘은 **이 문서에 축어로 적지 않는다** — evidence 는 `leakPatternGate` 의 스캔
  대상이고, 적는 순간 그 게이트가 붉어진다(소스 트리는 그 게이트의 scanRoot 가 아니다).

## 2026-10-19 (KST) clean-tree 판정 (r2)

- cmd: `git status --porcelain -- <산출물 in_scope 경로들을 개별 인자로>`
- exit: 0 · **빈 출력** — 지적 넷의 수정이 전부 커밋돼 base/head 가 고정됐다
- 이 판정의 대상은 **산출물 경로**다(evidence 를 포함한 판정은 이 문서를 담는 커밋의 post-state
  라 담을 수 없다 — 정본은 verifier 와 PR 조치 코멘트, 이 문서 머리말).

## 크기 게이트

- cmd: `git diff --numstat 77850903..HEAD -- . ':!CLAUDE.md' ':!.claude' ':!reports/evidence'`
- 핵심 결과(r2 재측정): 산출물 **+5255 / −117 줄** 대 evidence **+692 줄** — evidence ≤ 산출물.
