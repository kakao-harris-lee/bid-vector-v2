# M4/4B-4 실행 명령 로그

base_sha: `20f7ad0041de5e167c49bd00d9fdc00220711b56`

## RED — 신설 test 컴파일 실패 확인 (main 부재 상태에서 test 만 작성 직후)

- cmd: `./gradlew --no-daemon :decision:test --tests 'bidvector.decision.priority.*'`
- exit: 1
- 핵심 결과: 컴파일러 진단 `Name contains illegal characters: []`(F-7 — 이전 판은
  이 줄을 `file:line`으로 인용했으나 그 좌표는 이후 편집으로 이미 낡았다) —
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

## rollback 실측 — 임시 clone

- cmd: `git clone --no-hardlinks . <tmp-dir> && cd <tmp-dir> && git checkout m4-4b4/2026-09-10 && git restore --source=20f7ad0041de5e167c49bd00d9fdc00220711b56 --staged --worktree -- config/quality/gate-tests.properties decision/src/main/kotlin/bidvector/decision/priority decision/src/test/kotlin/bidvector/decision/priority milestone-4.md reports/evidence/m4/4b4 strategy/src/main/kotlin/bidvector/strategy/Score.kt`
- exit: 0
- 핵심 결과: `git status --porcelain` — D 19(main 8·test 7·evidence 4, `scope.md` 포함) +
  M 3(`gate-tests.properties`·`milestone-4.md`·`Score.kt`). `git diff <base> -- <같은
  경로들>` 결과 0줄(base 와 완전 일치) — 신설 패키지 디렉터리 자체도 사라짐(`ls`
  `No such file or directory`).
- cmd: `./gradlew --no-daemon :decision:compileKotlin :strategy:compileKotlin :decision:test :strategy:test`(되돌린 트리)
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL` — 되돌린 트리가 컴파일되고 기존 test 전부 통과. 임시
  clone 은 실측 뒤 삭제.

## 하네스 레인 변경 확인

- cmd: `git log --oneline 20f7ad0041de5e167c49bd00d9fdc00220711b56..HEAD -- CLAUDE.md .claude/`
- exit: 0
- 핵심 결과: 출력 없음 — 하네스 레인 변경 없음(scope.md 「하네스 레인 변경」 절과 일치)

## verifier r1 finding 반영 (`_workspace/m4-4b4/04_verifier_report.md`, medium 2 · low 5)

RED — F-1~F-4 신설 test를 main 수정 전에 실행:

- cmd: `./gradlew --no-daemon :decision:test --tests 'bidvector.decision.priority.*'`
- exit: 1
- 핵심 결과: `PriorityCompositionPropertyTest.kt` `Cannot infer type for type parameter 'T'`·
  `Unresolved reference 'long'`(import 정리 중 `Arb.long` import를 실수로 뺌). import
  복구 후 재실행.

GREEN:

- cmd: `./gradlew --no-daemon :decision:test --tests 'bidvector.decision.priority.*'`
- exit: 0
- 핵심 결과: 47 tests, 0 failed(F-1~F-4 신설 5개 포함, 42→47)

M-1 재현 — verifier r1 F-1 변이(`PRIORITY_POLICY`의 `Match` 0.3834→0.5834·`Urgency`
0.2333→0.0333, 합은 1.0000 유지)를 `PriorityPolicyData.kt`에 임시 적용:

- cmd: `./gradlew --no-daemon :decision:test --tests 'bidvector.decision.priority.PriorityPolicyDataTest'`
- exit: 1
- 핵심 결과: `PRIORITY_POLICY 출하 가중치는 legacy 재정규화 산식과 값까지 일치한다(F-1)`
  실패 — `Match: 출하=0.5834 기대=0.3834`. F-1 신설 test가 이 변이를 잡는다. 변이는
  즉시 원복(`cp` 백업 복원), `git diff` 결과 없음으로 원복 확인.

M-2 재현 — verifier r1 F-2의 정확한 재현 파라미터(`match="0.50", loadRatio="-1.0"`)로
`fullInputs`를 호출하는 임시 test:

- cmd: `./gradlew --no-daemon :decision:test --tests 'bidvector.decision.priority.M2ReproTest'`
- exit: 0(test 자체는 `shouldThrow<IllegalArgumentException>`이 성공 — 즉 그 파라미터가
  이제 **생성 단계에서 예외**를 던진다는 뜻)
- 핵심 결과: F-2 이전에는 이 파라미터가 정상 생성되어 `appliedPenalties[LoadRatio]`가
  음수였다(verifier r1 재현). `loadRatio`를 `UnitScore`로 좁힌 뒤에는 `PriorityInputs`
  구성 자체가 `IllegalArgumentException`으로 실패한다 — 임시 test 파일은 확인 뒤 삭제.

S-1/S-2/S-3/S-5/S-7 재실행(수정 반영 뒤):

- cmd: `./gradlew --no-build-cache clean check`
- exit: 1(1차) → `decision:ktlintTestSourceSetCheck` — `PriorityCompositionPropertyTest.kt`
  4-인자 람다 줄바꿈 스타일 위반 5건. `Arb.bind`를 다중 줄 인자 + 별도 데이터 클래스
  (`SubsetCase`)로 재구성해 해결.
- cmd: `./gradlew --no-build-cache clean check`
- exit: 0(2차) — `BUILD SUCCESSFUL`, 345 actionable tasks(321 executed). S-1·S-7 동시 충족.
- cmd: `./gradlew --no-daemon :decision:test --tests 'bidvector.decision.priority.*'`
- exit: 0 — S-2, 47 tests
- cmd: `./gradlew --no-daemon :decision:test`
- exit: 0 — S-3, 기존 test(4B-1·4B-3) 무변경 확인
- cmd: `./gradlew --no-daemon :decision:gateExecutionGate`
- exit: 0 — S-5, `gate.tests.decision`에 `PriorityCompositionExhaustiveTest` 추가 등재 확인

F-6 — 수정 커밋(`658e693`) 뒤 최종 head에서 S-0 재실행:

- cmd: `git worktree add --detach <tmp-dir> HEAD && (cd <tmp-dir> && ./gradlew --no-build-cache clean check)`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL in 54s`, 354 actionable tasks(354 executed) — head
  `658e6932582a1175b1f697169cc1e6f1840e6d83`에서 독립 재현. worktree는
  `git worktree remove --force`로 정리.

rollback ①~⑤ — 수정 커밋 뒤 임시 clone에서 재실측(팀장 지시 「마지막 코드 커밋 뒤」):

- cmd: `git diff --name-status 20f7ad0041de5e167c49bd00d9fdc00220711b56..HEAD`
- exit: 0
- 핵심 결과: 24행 — `rollback.md` 「대상 파일 목록」과 정확히 일치(① 목록 재산출).
- cmd: `git restore --source=20f7ad0041de5e167c49bd00d9fdc00220711b56 --staged --worktree -- config/quality/gate-tests.properties decision/src/main/kotlin/bidvector/decision/priority decision/src/test/kotlin/bidvector/decision/priority milestone-4.md reports/evidence/m4/4b4 strategy/src/main/kotlin/bidvector/strategy/Score.kt`
- exit: 0 — ②
- 핵심 결과: `git status --porcelain` D 21 / M 3(③, `rollback.md` F-5 정정값과 일치).
- cmd: `git diff 20f7ad0041de5e167c49bd00d9fdc00220711b56 -- <같은 경로들>`
- exit: 0
- 핵심 결과: 0줄(④) — base 와 완전 일치, 신설 패키지 디렉터리 자체 소멸(`ls`
  `No such file or directory`).
- cmd: `./gradlew --no-daemon :decision:compileKotlin :strategy:compileKotlin :decision:test :strategy:test :decision:gateExecutionGate`(되돌린 트리)
- exit: 0(⑤) — `BUILD SUCCESSFUL`, 23 actionable tasks. 임시 clone은 실측 뒤 삭제.

## secret 스캔

- cmd: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m4/4b4/ decision/src/main/kotlin/bidvector/decision/priority decision/src/test/kotlin/bidvector/decision/priority strategy/src/main/kotlin/bidvector/strategy/Score.kt config/quality/gate-tests.properties milestone-4.md`
- exit: 1 (매치 없음 = 통과)
- 핵심 결과: 매치 0건. Telegram id·사업자 정보 육안 확인 — 해당 없음(이 slice는 순수
  도메인 커널이라 그런 값을 다루지 않는다).
- verifier r1 finding 반영 뒤 재실행: exit 0, 매치 2건 — 둘 다 이 절의 `cmd:` 줄과
  `- cmd:` 인용 자신(commands.md가 스캔 명령 문자열을 담고 있어 스캔이 자기 자신을
  잡는 상시 false-positive 바닥, verifier r1 「누출 스캔」 항목과 같은 판독). developer
  구간(실제 코드·정책 값) 매치 0.
