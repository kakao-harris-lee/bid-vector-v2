# M4/4B-4 실행 명령 로그

base_sha: `20f7ad0041de5e167c49bd00d9fdc00220711b56`

## RED — 신설 test 컴파일 실패 확인 (main 부재 상태에서 test 만 작성 직후)

- cmd: `./gradlew --no-daemon :decision:test --tests 'bidvector.decision.priority.*'`
- exit: 1
- 핵심 결과: `PriorityCompositionPropertyTest.kt:96:9 Name contains illegal characters: []` —
  test 함수명에 대괄호를 쓴 실수. 수정 후 재실행.

## 재실행 1 — 첫 GREEN 시도, 손계산 표본 오류·norm 경계 부동소수 발견

- cmd: `./gradlew --no-daemon :decision:test --tests 'bidvector.decision.priority.*'`
- exit: 1
- 핵심 결과: 42 tests, 2 failed — `표본2`(재정규화 분모 손계산 오류, `0.7667`이 아니라
  `0.8667`) · `UnitVectorTest` norm 경계 포함 test(`1.05-1.0`의 이진 부동소수 round-off로
  `<=` 경계가 흔들림, 값을 이진 정확 표현(`1.5`/`0.5`)으로 교체).

## 재실행 2 — GREEN

- cmd: `./gradlew --no-daemon :decision:test --tests 'bidvector.decision.priority.*'`
- exit: 0
- 핵심 결과: 42 tests, 0 failed

## S-3 — decision 전체(4B-1·4B-3 test 무변경 확인)

- cmd: `./gradlew --no-daemon :decision:test`
- exit: 0
- 핵심 결과: 전체 통과, 기존 test 무변경

## S-4 — strategy(KDoc 만 변경)

- cmd: `./gradlew --no-daemon :strategy:compileKotlin :strategy:test`
- exit: 0
- 핵심 결과: 통과

## S-5 — decision gateExecutionGate

- cmd: `./gradlew --no-daemon :decision:gateExecutionGate`
- exit: 0
- 핵심 결과: 통과 — gate.tests.decision 등재 여섯 클래스 실행 확인

## S-6 — app conformance(corpus 무영향)

- cmd: `./gradlew --no-daemon ":app:test" --tests '*Conformance*'`
- exit: 0
- 핵심 결과: 통과

## S-1/S-7 — 전체 clean check (detekt ReturnCount·MaxLineLength 발견 및 수정 포함)

- cmd: `./gradlew --no-build-cache clean check`
- exit: 1 (1차)
- 핵심 결과: `decision:detekt` 6 issues — `SemanticMatch.of` ReturnCount(3>2) · test/main
  5개 파일의 MaxLineLength(120 초과, 바이트 기준 오탐 아님 — 실제 문자 수 초과 5건).
  `SemanticMatch.of`를 단일 `when` 식으로, 긴 줄 5곳을 줄바꿈으로 수정.

- cmd: `./gradlew --no-build-cache clean check`
- exit: 0 (2차, D-4B4-3 실측 포함)
- 핵심 결과: `BUILD SUCCESSFUL`, 345 actionable tasks(320 executed) — domainApiTypeGate·
  api-type-policy(D-4B4-3)·architecture·cpd·detekt·ktlint·jarContentGate·typeShapeGate·
  kover·qualityBaseline 전부 통과. `UnitVector` 의 `List<BigDecimal>` 설계(D-4B4-3 대안)가
  게이트를 통과함을 이 실행으로 확인 — `DoubleArray` 주 생성자였다면 `domainApiTypeGate`
  가 실패했을 것(설계 근거는 `PriorityPolicyData.kt`·`UnitVector.kt` KDoc, 게이트 코드
  경로는 `build-logic/src/main/kotlin/bidvector/buildlogic/PublicApiTypes.kt`
  `classOrObjectTargets`).

## S-0 — 격리 worktree 재현(HEAD `34d51440643e3af74fff80faf4b2657d08c77e91`)

- cmd: `git worktree add --detach <tmp-dir> HEAD && (cd <tmp-dir> && ./gradlew --no-build-cache clean check)`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL in 1m 15s`, 354 actionable tasks(354 executed) — 커밋된
  산출물만으로 독립 재현. worktree 는 `git worktree remove --force`로 정리.

## clean-tree 게이트 — 경로 개별 인자 + 양성 대조

- cmd: `git status --porcelain -- decision/src/main/kotlin/bidvector/decision/priority decision/src/test/kotlin/bidvector/decision/priority strategy/src/main/kotlin/bidvector/strategy/Score.kt config/quality/gate-tests.properties milestone-4.md reports/evidence/m4/4b4`
- exit: 0
- 핵심 결과: 커밋된 코드 경로 결과 없음(clean) — `milestone-4.md`(M)·
  `reports/evidence/m4/4b4/policy-values.md`(??)만 잔존(evidence 커밋 전 상태, 이후
  커밋으로 해소).
- 양성 대조: `Score.kt`에 한 줄 추가 → `git status --porcelain` 이 `M` 출력 확인 →
  `git checkout -- Score.kt`로 원복 → 재확인 결과 없음.

## 하네스 레인 변경 확인

- cmd: `git log --oneline 20f7ad0041de5e167c49bd00d9fdc00220711b56..HEAD -- CLAUDE.md .claude/`
- exit: 0
- 핵심 결과: 출력 없음 — 하네스 레인 변경 없음(scope.md 「하네스 레인 변경」 절과 일치)

## secret 스캔

- cmd: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m4/4b4/ decision/src/main/kotlin/bidvector/decision/priority decision/src/test/kotlin/bidvector/decision/priority strategy/src/main/kotlin/bidvector/strategy/Score.kt config/quality/gate-tests.properties milestone-4.md`
- exit: 1 (매치 없음 = 통과)
- 핵심 결과: 매치 0건. Telegram id·사업자 정보 육안 확인 — 해당 없음(이 slice는 순수
  도메인 커널이라 그런 값을 다루지 않는다).
