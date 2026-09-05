# Commands — M1 / 1A-b

base `e0aae7f2242f3cffe8f4c32bae2ecec7a3b89b46`, head `d0ce6694e12b58820f4f578df9019c147e91d7a1`.
D-6 커밋(`d0ce669`)이 D-5 finding 을 해소한 뒤 acceptance H-0~H-6 을 이 head 에서 전건
재실행했다 — 아래는 그 최종 재실행 기록이다(중간 실패 이력은 D-5/D-6 절 참고).

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
