# commands.md — M4/4A

명령과 종료 코드만 남긴다(출력 전문 금지, evidence-pack 스킬 규격). 감사자는 명령을 다시 돌린다.

## 2026-09-08T13:40Z
- cmd: `./gradlew --no-daemon :workflow:compileKotlin`
- exit: 0
- 핵심 결과: `StrategyRepository` internal 시도 → 공개 `EditStrategyWorkflow` 생성자가 노출해 컴파일 에러(실측, 설계 검토 (2) #3) — public 으로 되돌린 뒤 성공.

## 2026-09-08T13:55Z
- cmd: `./gradlew --no-daemon :workflow:test`
- exit: 0
- 핵심 결과: EditSessionTransitionTableTest 8 · EditSessionActorAndTimeoutTest 6 · EditSessionIdempotencyPropertyTest 4 · EditSessionImportBoundaryTest 3 · EditStrategyWorkflowTest 5, 합계 26 tests, 0 failed.

## 2026-09-08T13:56Z
- cmd: `./gradlew --no-daemon :workflow:moduleDependencyGate :workflow:sizeGate :workflow:cpdCheck`
- exit: 0

## 2026-09-08T14:00Z
- cmd: `./gradlew --no-daemon :workflow:ktlintCheck :workflow:detekt`
- exit: 1 (초기) → 0 (재실행)
- 핵심 결과: ktlint brace 일관성 1건·detekt `MatchingDeclarationName`(파일명 불일치)·`ReturnCount`(`expireIfDue` 3·`apply` 6, 한도 2) 4건·`MaxLineLength` 3건 — `ktlintFormat` + `expireIfDue`/`apply` 를 guard 함수 조합(`expiryRejection`·`duplicateOutcome`·`actorRejection`)으로 재구성해 해소.

## 2026-09-08T14:05Z
- cmd: `./gradlew --no-daemon :app:test --tests '*Conformance*'`
- exit: 1(초기, 숫자 fixture 필드가 quoted string 이라 `decimalValue()` 코어스 실패 5건) → 0(재실행)
- 핵심 결과: 74 tests, 0 failed(기존 69 + strategy-edit 5). fixture 입력의 `min`/`max`/`bidNowThreshold` 등 숫자 필드를 quoted string 에서 JSON 숫자 리터럴로 정정(기존 strategy-validation-001 관례와 정합) + sha256 재계산.

## 2026-09-08T14:08Z
- cmd: `./gradlew --no-build-cache clean check`(S-1, 저장소 루트)
- exit: 1(초기, `app` 소스의 ktlint MaxLineLength) → 1(2차, `app:sizeGate` 함수 50줄 초과 — `strategyEditExecutor` 55줄) → 0(3차)
- 핵심 결과: `strategyEditExecutor` 를 `strategyEditCaseFrom`·`runStrategyEdit`·`projectionOf` 로 분해해 해소. 3차 `BUILD SUCCESSFUL`(344 actionable tasks).

## 2026-09-08T14:12Z
- cmd: `./gradlew --no-daemon :workflow:test`(S-2)
- exit: 0

## 2026-09-08T14:12Z
- cmd: `./gradlew --no-daemon :workflow:moduleDependencyGate :workflow:sizeGate :workflow:cpdCheck`(S-3)
- exit: 0

## 2026-09-08T14:13Z
- cmd: `./gradlew --no-daemon :workflow:test --tests '*EditSessionImportBoundaryTest*'`(S-3b)
- exit: 0

## 2026-09-08T14:13Z
- cmd: `./gradlew --no-daemon :app:test --tests '*Conformance*'`(S-4)
- exit: 0

## 2026-09-08T14:14Z
- cmd: `./gradlew --no-daemon qualityBaseline`(S-5)
- exit: 0

## 2026-09-08T14:14Z
- cmd: `./gradlew --no-daemon :app:gateExecutionGate`(S-6)
- exit: 0

## 2026-09-08T14:25Z
- cmd: `d=$(mktemp -d) && git clone --quiet --no-hardlinks --branch m4/2026-09-08 --single-branch <repo> "$d/repo" && (cd "$d/repo" && ./gradlew --no-build-cache clean check)`(S-0)
- exit: 0
- 핵심 결과: 임시 clone(커밋만 담음)에서 root `clean check` 전건 통과 — S-1 재현.

## 2026-09-08T14:28Z — secret 스캔 (리뷰 요청 조건)
- cmd: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m4/4a/ --exclude=commands.md`
- exit: 1(매치 없음)
- 핵심 결과: 매치 0건. `--exclude=commands.md` 없이 돌리면 이 파일 자신이 위 grep 명령 텍스트를 담고 있어 `token`·`secret`·`password` 패턴에 자기 인용으로 걸린다(2건, 전부 developer 구간 밖 — 명령 원문 인용) — 실제 값이 아니라 이 절 자체를 재스캔한 자기참조 오탐이라 별도 실행으로 판독했다. Telegram id·사업자 정보 없음(육안 확인 — fixture 는 corpus 합성 식별자 `corpus-operator`/`corpus-session` 뿐).

## 2026-09-08T14:29Z — clean-tree 게이트 (양성 대조 포함)
- cmd: `git status --porcelain -- <in_scope 경로 27개, 개별 인자>`
- exit: 0, 출력 없음
- 양성 대조: `Transition.kt` 에 개행 한 줄을 추가한 뒤 같은 명령을 그 경로 하나로 재실행 → `M workflow/.../Transition.kt` 관측(exit 0, 비어있지 않음) → `git checkout -- Transition.kt` 로 되돌리고 재확인(비어있음). 경로를 변수 하나에 담지 않고 개별 인자로 넘겼다(2026-09-03 pathspec 함정 회피).

## 2026-09-08T14:30Z — S-0 확인 재실행
- cmd: `d=$(mktemp -d) && git clone --quiet --no-hardlinks --branch m4/2026-09-08 --single-branch /Users/harris/Development/private/bid-vector-v2-m4 "$d/repo" && (cd "$d/repo" && ./gradlew --no-build-cache clean check)`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL in 50s`, 353 actionable tasks 전부 executed(캐시 없는 임시 clone).

# 수정 라운드 1 — verifier not-ready(H-1·H-2) 반영 (2026-09-08~09)

verifier 리포트: `_workspace/m4-4a/03_verifier_report.md`. 처리: H-1/H-2(`33c7f97`) ·
N-1(`f11a5e1`) · M-1(`ef7a8fc`) · M-2(`7f7a638`) · M-3(`022629b`).

## 2026-09-08T21:10Z — H-1/H-2 실측 (a) 우회가 이제 컴파일되지 않는다
- cmd: 임시 clone(`33c7f97` 포함 head)에 `app/src/test/kotlin/bidvector/app/verifyprobe/BypassProbe.kt`
  (verifier 가 지목한 것과 같은 형태 — `StrategyRepository` 구현 + `validate()` 결과를
  `AppliedStrategy(...)` 로 감싸 `save()` 에 전달) 심고 `./gradlew --no-daemon :app:compileTestKotlin`
- exit: 1
- 핵심 결과: `Cannot access 'constructor(strategy: OperatorStrategy): AppliedStrategy': it is internal in 'bidvector.workflow.strategy.AppliedStrategy'` — 우회 지점 정확히 그 줄에서 거부.

## 2026-09-08T21:15Z — H-1/H-2 실측 (b) 양성 대조 — 정상 배선은 그대로 선다
- cmd: 같은 clone 에서 probe 삭제 뒤 `./gradlew --no-daemon :app:compileTestKotlin :workflow:test :app:test --tests '*Conformance*'`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL`(34 actionable tasks). `EditStrategyWorkflow` 경유 정상 경로는 영향 없음. worktree 는 건드리지 않음(임시 clone 전용).

## 2026-09-08T21:30Z — M-1 변이 실측 — 판정 순서(만료→중복) 판별 case
- cmd: `Transition.kt` 의 `expiryRejection(...) ?: duplicateOutcome(...)` 를 `duplicateOutcome(...) ?: expiryRejection(...)` 로 교환 후 `./gradlew --no-daemon :workflow:test`
- exit: 1
- 핵심 결과: 새 판별 case(`판정 순서는 만료가 먼저다 — accepted 로 lastCommand 가 채워진 뒤에도...`) 1건만 FAILED(29건 중), 나머지 28건 초록. 변이 원상 복구 후 재실행 exit 0(26→29건 전부 초록, `git diff` 로 원복 확인).

## 2026-09-08T21:40Z — M-2 변이 실측 — 정책 생성 불변식
- cmd: `EditSessionPolicyData` 의 두 `require`(양수·상한)를 `require(true)` 로 교환 후 `./gradlew --no-daemon :workflow:test`
- exit: 1
- 핵심 결과: `EditSessionPolicyDataTest` 3건(0/음수/상한 초과) FAILED, 나머지 30건 초록. 변이 원상 복구 후 재실행 exit 0(원복 확인).

## 2026-09-08T21:50Z — N-1·M-3 회귀 확인
- cmd: `./gradlew --no-daemon :workflow:test`(N-1 begin() 가드 test 2건, M-3 failure-injection test 2건 포함)
- exit: 0
- 핵심 결과: `EditStrategyWorkflowTest` 5→9건(N-1 2건 + M-3 2건 추가). **verifier r2 B-5 정정
  (2026-09-09)** — 이 시점 `:workflow:test` 전체는 class 별 6·5·3·4·8·9 = **35건**이었다(「전체
  33건」은 오산 — `EditSessionPolicyDataTest`(M-2, 4건)를 이 절이 누락하고 셌다).

## 2026-09-08T22:00Z — 수정 라운드 1 최종 S-1~S-6·S-0 한 번에 재실행(head `022629b`)
- cmd: `./gradlew --no-build-cache clean check`(S-1)
- exit: 0 — `BUILD SUCCESSFUL in 35s`, 344 actionable tasks(321 executed·23 up-to-date).
- cmd: `./gradlew --no-daemon :workflow:test`(S-2)
- exit: 0
- cmd: `./gradlew --no-daemon :workflow:moduleDependencyGate :workflow:sizeGate :workflow:cpdCheck`(S-3)
- exit: 0
- cmd: `./gradlew --no-daemon :workflow:test --tests '*EditSessionImportBoundaryTest*'`(S-3b)
- exit: 0
- cmd: `./gradlew --no-daemon :app:test --tests '*Conformance*'`(S-4)
- exit: 0 — 74 tests(기존과 동일, 이번 라운드는 fixture 무변경).
- cmd: `./gradlew --no-daemon qualityBaseline`(S-5)
- exit: 0
- cmd: `./gradlew --no-daemon :app:gateExecutionGate`(S-6)
- exit: 0
- cmd: `d=$(mktemp -d) && git clone --quiet --no-hardlinks --branch m4/2026-09-08 --single-branch /Users/harris/Development/private/bid-vector-v2-m4 "$d/repo" && (cd "$d/repo" && ./gradlew --no-build-cache clean check)`(S-0)
- exit: 0 — `BUILD SUCCESSFUL in 1m 13s`, 353 actionable tasks 전부 executed(캐시 없는 임시 clone, head `022629b`).

## 2026-09-08T22:10Z — rollback 재검증(목록 28 A·6 M 으로 갱신, `AppliedStrategy.kt`·`EditSessionPolicyDataTest.kt` 신설 반영)
- cmd: `git diff --name-status 9948c6e4056bbf71fa6683aa67d30c2a49fc6eae..HEAD`(목록 기계 산출, head `022629b`)
- exit: 0 — 28 A · 6 M(`reports/evidence/m4/4a/**` 6건 제외).
- cmd: 임시 clone 에서 `git restore --source=<base> --staged --worktree -- <경로 34개 개별 인자>`
- exit: 0 — `git status --porcelain` D 28 · M 6(목록과 일치), 신규 디렉터리 잔여 파일 0건(`find`).
- cmd: `git diff <base> -- <M 대상 6개 경로>`
- exit: 0, 출력 0줄(빈 diff).
- cmd: `./gradlew --no-daemon :workflow:compileKotlin :app:compileTestKotlin`
- exit: 0 — `BUILD SUCCESSFUL`(29 actionable tasks, 13 executed·16 from cache).
- cmd: `./gradlew --no-daemon :workflow:test :app:test --tests '*Conformance*'`
- exit: 0 — `BUILD SUCCESSFUL`(`:workflow:test NO-SOURCE`, `:app:test` conformance 정상 축소).

## 2026-09-08T22:15Z — clean-tree 게이트 재확인(수정 라운드 1 최종 경로 34개, 양성 대조 포함)
- cmd: `git status --porcelain -- <in_scope 경로 34개, 개별 인자>`
- exit: 0 — 이 시점엔 `milestone-4.md`·`checklist.md`·`rollback.md` 세 파일이 아직 미커밋이라
  `M milestone-4.md` 한 줄이 정직하게 찍혔다(장부층 커밋 전 — 아래 커밋 뒤 재확인에서 빈 값
  으로 닫힌다).
- 양성 대조: `Transition.kt`(이 시점엔 클린)에 개행 한 줄 추가 후 그 경로만으로 재실행 →
  `M workflow/.../Transition.kt` 관측(비어있지 않음) → `git checkout -- Transition.kt` 로
  되돌리고 재확인(비어있음, exit 0).

## 2026-09-08T22:20Z — secret 스캔 재확인
- cmd: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m4/4a/ --exclude=commands.md --exclude=checklist.md`
- exit: 1(매치 없음)
- 핵심 결과: `--exclude=checklist.md` 를 새로 더했다 — 이번 라운드에서 checklist.md 가 "secret 스캔"
  이라는 절 제목 문구를 담게 돼 자기참조 오탐이 그 파일로도 번졌다(육안 확인 — 실제 비밀값
  아님). commands.md 와 같은 갈래의 자기참조라 같은 방식으로 제외했다.
