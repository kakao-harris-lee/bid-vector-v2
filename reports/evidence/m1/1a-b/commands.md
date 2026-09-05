# Commands — M1 / 1A-b

base `e0aae7f2242f3cffe8f4c32bae2ecec7a3b89b46`. **head 선언은 옮겨 적을 때마다 낡는다
(verifier r1 B-5 가 잡은 그대로) — 정확한 값은 `git log --oneline -1`을 가리킨다.** 이
문서를 쓴 시점의 최신 code 커밋은 `03349ad`(D-7, M-2 해소)이고 그 앞은 `5564b47`
(verifier r1 M-1~M-3·L-1·L-3 반영)이다.

D-6 커밋(`d0ce669`)이 최초 D-5 finding 을 해소한 뒤 acceptance H-0~H-6 을 그 head 에서
전건 재실행했다(아래 첫 블록, 중간 실패 이력은 D-5/D-6 절). verifier r1(`_workspace/
m1-1a-b/02_verifier_r1.md`, ready-for-review, high 0·medium 3·low 3·장부층 5)이 이후
독립 재현했고, 그 finding(M-1~M-3·L-1~L-3) 반영 커밋 `5564b47`에서 acceptance 를 다시
전건 재실행했다 — 「verifier r1 반영 재실행」절이 최종 상태다.

## 2026-09-05T12:16:00Z
- cmd: `git worktree add --detach <scratchpad>/h0-clean-check-2 HEAD && (cd <dir> && ./gradlew --no-daemon --no-build-cache clean check)` (H-0)
- exit: 0
- 핵심 결과: 289 task 전건 실행(캐시 없음), BUILD SUCCESSFUL

## 2026-09-05T12:20:00Z
- cmd: `./gradlew --no-daemon --no-build-cache clean check` (H-1)
- exit: 0
- 핵심 결과: 280 task, BUILD SUCCESSFUL

## 2026-09-05T12:44:00Z
- cmd: `./gradlew --no-daemon --no-build-cache :build-logic:test` (H-2)
- exit: 0
- 핵심 결과: UP-TO-DATE(직전 실행과 동일 head), BUILD SUCCESSFUL

## 2026-09-05T12:44:10Z
- cmd: `./gradlew --no-daemon --no-build-cache :app:test --tests '*ArchitectureGate*' --tests '*Conformance*'` (H-3)
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL

## 2026-09-05T12:45:00Z
- cmd: `./gradlew --no-daemon --no-build-cache qualityBaseline` (H-4)
- exit: 0
- 핵심 결과: `build/reports/quality-baseline/quality-baseline.md` — shared-kernel max depth=2·max
  interfaces=1(래칫 상한과 정확히 일치, D-3), max type members(바이트코드)=20

## 2026-09-05T12:45:20Z
- cmd: `./gradlew --no-daemon --no-build-cache :shared-kernel:cpdCheck :shared-kernel:cpdReportPresenceGate` (H-5)
- exit: 0
- 핵심 결과: `shared-kernel/build/reports/cpd/shared-kernel.xml` 3105 bytes(`test -s` 통과)

## 2026-09-05T12:45:40Z
- cmd: `./gradlew --no-daemon --no-build-cache :shared-kernel:test` (H-6)
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL

---

## D-5/D-6 실패·해소 이력 (구조 처리 대상, 재작업 상한 예외)

## 2026-09-05T12:07:00Z
- cmd: `./gradlew --no-daemon --no-build-cache --continue clean check` (①② 배선 직후, head `a355cff`)
- exit: 1
- 핵심 결과: 262 task 중 `buildLogicSizeGate` 1건 실패 — 타입 멤버 30 초과(`SourceReferencesTest` 35개).
  D-5 finding 으로 팀 리드에게 보고(임계·코드 미변경)

## 2026-09-05T12:20:00Z
- cmd: `./gradlew --no-daemon --no-build-cache --continue clean check` (④ 배선 직후, head `fc6f1fd`)
- exit: 1
- 핵심 결과: 289 task 중 동일 finding 1건만 재현(다른 회귀 없음)

## 2026-09-05T12:41:00Z
- cmd: `./gradlew --no-daemon --no-build-cache buildLogicSizeGate scriptSizeGate` (D-6 배선 1차, `typeSources` 버그)
- exit: 0(그러나 `max.type.members.gated=0` — 버그 실측, `.files` 가 디렉터리를 재귀 안 함)
- 핵심 결과: 리포트 값이 0으로 나와 게이트가 공허하게 통과함을 발견 → `asFileTree` 로 수정

## 2026-09-05T12:42:00Z
- cmd: `./gradlew --no-daemon --no-build-cache buildLogicSizeGate` (수정 후)
- exit: 0
- 핵심 결과: `max.type.members.gated=21`(main, 통과) · `max.type.members.observed=35`(test 포함,
  관찰) — 리포트가 두 값을 분리해 남긴다

## 2026-09-05T12:43:00Z
- cmd: `./gradlew --no-daemon --no-build-cache --continue clean check` (D-6 커밋 전, 수정 검증)
- exit: 0
- 핵심 결과: 289 task 전건 BUILD SUCCESSFUL — D-5 finding 해소 확인 후 커밋(`d0ce669`)

---

## ③ PMD CPD 관찰/실패 모드 로컬 실측 (원복 완료)

## 2026-09-05T11:27:03Z
- cmd: 격리 없이 shared-kernel 에 `Provenance.kt` 를 `bidvector.sharedkernel.dup` 패키지로 복제 →
  `./gradlew --no-daemon --no-build-cache :shared-kernel:cpdCheck` (mode=observe)
- exit: 0
- 핵심 결과: "CPD found duplicate code" 로그 + 리포트에 79-token/34-line 중복 1건, exit 0(관찰 모드)

## 2026-09-05T11:28:00Z
- cmd: `duplicate-policy.properties` 를 `mode=fail` 로 임시 변경 →
  `./gradlew --no-daemon --no-build-cache :shared-kernel:cpdCheck`
- exit: 1
- 핵심 결과: 같은 중복으로 `CpdAction` 이 "CPD found duplicate code" 실패

## 2026-09-05T11:29:00Z
- cmd: 복제 파일 삭제 + `duplicate-policy.properties` 를 `mode=observe` 로 원복 →
  `git status --short`
- exit: 0
- 핵심 결과: 변경 없음(원복 완료, D-4 실측의 흔적 없음)

---

## ④ TestKit RED→GREEN 고정

## 2026-09-05T12:31:00Z
- cmd: `plugins.withId("java-test-fixtures") { throw ... }` 가드를 주석 처리 →
  `./gradlew --no-daemon --no-build-cache :build-logic:test --tests '*TestFixturesGateTest*'`
- exit: 1
- 핵심 결과: `TestFixturesGateTest` 1건 FAILED(`AssertionFailedError`) — 가드 없이는 RED

## 2026-09-05T12:32:00Z
- cmd: 가드 복원 → `./gradlew --no-daemon --no-build-cache :build-logic:test --tests '*TestFixturesGateTest*'`
- exit: 0
- 핵심 결과: 1 test, GREEN(가드가 원인임을 고정)

---

## 정책 점검

## 2026-09-05T12:46:18Z
- cmd: `git status --porcelain -- <in_scope 경로 24개, 개별 인자>`
- exit: 0
- 핵심 결과: 결과 없음(전부 커밋됨). 양성 대조 — `config/quality/duplicate-policy.properties`
  에 한 줄 추가 후 같은 명령 재실행 → `M config/quality/...` 로 잡힘 → `git checkout --` 로 원복.
  두 상태 다 확인(더러운 트리와 깨끗한 트리를 구별함)

## 2026-09-05T12:46:30Z
- cmd: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m1/1a-b/`
- exit: 1(매치 없음 취급 안 됨 — grep 관례상 매치 있으면 exit 0)
- 핵심 결과: 매치 3건, 전부 `scope.md`(팀 리드가 쓴 계약 문서)의 `minimumTokenCount`
  식별자 — "token" 부분 문자열 오탐. 실제 비밀값 없음(육안 확인)

## 2026-09-05T12:46:40Z
- cmd: `git diff d0db0fa~1..fc6f1fd | grep -niE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))"`
- exit: 0
- 핵심 결과: 매치 6건, 전부 `minimumTokenCount`/`toolVersion` 필드명(정책 데이터 키·테스트
  변수명) — 오탐. 실제 비밀값 없음(육안 확인)

---

## verifier r1 반영 재실행 (M-1~M-3·L-1·L-3, 커밋 `5564b47`) — 최종 acceptance 상태

## 2026-09-05T13:05:00Z
- cmd: `./gradlew --no-daemon --no-build-cache --continue clean check` (직접 실행, 커밋 직후)
- exit: 1
- 핵심 결과: 282 task 중 `buildLogicTypeShapeGate` 1건만 실패(아래 M-2 finding). 다른 회귀 없음

## 2026-09-05T13:12:00Z
- cmd: `git worktree add --detach <scratchpad>/h0-r1 HEAD && (cd <dir> && ./gradlew --no-daemon --no-build-cache --continue clean check)` (H-0)
- exit: 1
- 핵심 결과: 291 task 전건 콜드 실행, `buildLogicTypeShapeGate` 1건만 실패(동일). worktree
  `git worktree remove --force` 후 잔여 없음 확인

## 2026-09-05T13:15:00Z
- cmd: `./gradlew --no-daemon --no-build-cache :build-logic:test` (H-2) · `:app:test --tests '*ArchitectureGate*' --tests '*Conformance*'` (H-3) · `qualityBaseline` (H-4) · `:shared-kernel:test` (H-6) · `:shared-kernel:cpdCheck :shared-kernel:cpdReportPresenceGate` + `test -s`(H-5)
- exit: 0 (다섯 전부)
- 핵심 결과: 전부 BUILD SUCCESSFUL. H-5 리포트 3106 bytes

### M-1 재현(수정 확인)

## 2026-09-05T13:00:00Z
- cmd: `shared-kernel/build.gradle.kts` 끝에 31개 멤버 클래스 추가 → `./gradlew --no-daemon --no-build-cache :shared-kernel:sizeGate`
- exit: 1
- 핵심 결과: `타입 멤버 30 개 한도 초과 1건 ... VProbeBigInScript` — verifier r1 재현(exit 0)과
  달리 이제 잡힌다. 파일 원복 후 `git status --short`로 clean 확인

### M-3 재현(수정 확인)

## 2026-09-05T13:18:00Z
- cmd: `TestFixturesGateTest.kt`를 컴파일 대상 밖으로 이동 → `./gradlew --no-daemon --no-build-cache :build-logic:test buildLogicGateExecutionGate --rerun-tasks`
- exit: 1
- 핵심 결과: `게이트 test class 가 실행되지 않았다 — bidvector.buildlogic.TestFixturesGateTest`.
  파일 복원 후 `buildLogicGateExecutionGate` 재실행 → exit 0

### L-3 재현(수정 확인)

## 2026-09-05T13:10:00Z
- cmd: `rm shared-kernel/build/reports/cpd/shared-kernel.xml` → `./gradlew --no-daemon --no-build-cache :shared-kernel:cpdReportPresenceGate -x cpdCheck`
- exit: 1
- 핵심 결과: `PMD CPD 리포트가 존재하지 않는다(D-4, OPEN-ADR-16 (a))` — verifier r1 이 잡은
  "Gradle 일반 메시지로 가려짐"이 해소됨. `cpdCheck` 재실행으로 리포트 복구

### M-2 — 새 finding(수정하지 않음, D-5 원칙)

## 2026-09-05T12:55:00Z
- cmd: `./gradlew --no-daemon --no-build-cache buildLogicTypeShapeGate`
- exit: 1
- 핵심 결과: 상속 깊이 위반 17건, 전부 depth 3(`bidvector.buildlogic.*GateTask`류 +
  정밀 스크립트 컴파일 클래스 2개). interfaces 는 위반 없음(`max.interfaces=1`).
  원인: Gradle `DefaultTask`가 이미 2단 상속이라 `abstract class X : DefaultTask()` 형태는
  구조적으로 depth 3 이다 — mixin 팽창이 아니다. 임계·코드 미변경, 팀 리드에게 보고
  (evidence checklist.md 「M-2 가 드러낸 새 finding」)

## rollback 재실측 (verifier r1 B-2·B-3 정정)

## 2026-09-05T13:20:00Z
- cmd: 임시 clone(`git clone` from HEAD `5564b47`) + rollback.md 의 `git restore` 명령(경로
  27개, `config/quality/gate-tests.properties` 추가됨) 그대로 실행
- exit: 0
- 핵심 결과: 신규 파일 삭제 + 기존 파일 복원, `git diff <base> -- <in_scope 경로>` 빈 출력
  (완전 복원 확인). **정확한 D/M 개수는 여기 적지 않는다**(evidence-pack "명령이 내는 셈을
  산문에 옮겨 적기" 금지 — B-3 이 지적한 드리프트의 재발을 막는다) — 재확인하려면 이 명령을
  그대로 돌린다

## 2026-09-05T13:21:00Z
- cmd: `git status --porcelain -- <in_scope 경로 27개, 개별 인자>` (경로에 `config/quality/gate-tests.properties` 추가)
- exit: 0
- 핵심 결과: 결과 없음(전부 커밋됨). 양성 대조 — `gate-tests.properties`에 한 줄 추가 후
  재실행 → `M ...`로 잡힘 → `git checkout --`로 원복, 두 상태 다 확인

## 2026-09-05T13:21:30Z
- cmd: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m1/1a-b/checklist.md reports/evidence/m1/1a-b/rollback.md`
- exit: 1(매치 없음)
- 핵심 결과: 매치 0건

---

## D-7 반영(M-2 해소, 상속 깊이 정의 정제) — 최종 acceptance 상태, 커밋 `03349ad`

## 2026-09-05T13:31:00Z
- cmd: `./gradlew --no-daemon --no-build-cache :build-logic:check`(정의 정제 직후, detekt 1건 확인)
- exit: 1 → `LoopWithTooManyJumpStatements`(명령형 루프의 break 2개) → `generateSequence.
  drop(1).takeWhile.count()` 함수형으로 재작성 → 재실행 exit 0

## 2026-09-05T13:33:00Z
- cmd: `./gradlew --no-daemon --no-build-cache --continue clean check`(재작성 후, 커밋 전)
- exit: 0
- 핵심 결과: 291 task 전건 GREEN — `buildLogicTypeShapeGate` 포함 회귀 없음

## 2026-09-05T13:40:00Z
- cmd: `git worktree add --detach <scratchpad>/h0-d7 HEAD(03349ad) && (cd <dir> && ./gradlew --no-daemon --no-build-cache clean check)` (H-0)
- exit: 0
- 핵심 결과: 291/291 task 콜드 실행 전건 GREEN. worktree 제거 후 잔여 없음 확인

## 2026-09-05T13:42:00Z
- cmd: `./gradlew --no-daemon --no-build-cache clean check`(H-1) · `:build-logic:test`(H-2) ·
  `:app:test --tests '*ArchitectureGate*' --tests '*Conformance*'`(H-3) · `qualityBaseline`(H-4) ·
  `:shared-kernel:test`(H-6) · `:shared-kernel:cpdCheck :shared-kernel:cpdReportPresenceGate` + `test -s`(H-5)
- exit: 0 (여섯 전부)
- 핵심 결과: 전부 BUILD SUCCESSFUL. H-5 리포트 3106 bytes. `quality-baseline.md` 값은
  checklist.md 「M-2 가 드러낸 finding → D-7 로 해소」절의 표 참고(모듈별 depth/interfaces,
  낡는 좌표 방지를 위해 여기 다시 옮기지 않는다)

## 2026-09-05T13:45:00Z
- cmd: 임시 clone(HEAD `03349ad`) + rollback.md 명령 그대로 실행(경로 27개, 이번 라운드는
  신규 경로 없이 기존 6개 파일만 수정 — 목록 변경 없음)
- exit: 0
- 핵심 결과: `git diff <base> -- <in_scope 경로>` 빈 출력(완전 복원 확인)
