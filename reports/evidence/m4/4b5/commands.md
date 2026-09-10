# M4/4B-5 실행 명령 로그

base_sha: `9eddf7525b74f89ce279acbb3adf4948f05c25f1`

## 팀장 계약 갱신 반영(commit `b288a1d`, 구현 중 착지)

구현 레인이 보고한 D-4B5-4 판단(⑤ 보류·④ 크기 비교·② rounding 필드)을 팀장이 그대로
계약 갱신으로 확정하고 `OPEN-4B5-COMPETITIVENESS`를 신설했다. 갱신 #1이 요구한 상수
함수(`competitivenessNotCollected` — `⑦ workloadNotCollected`와 같은 축)와
`DerivationAbsence.MarketAverageMissing`을 추가하고, 세 커밋(main+test·gate+milestone·
evidence) 각각에 `git commit --fixup` + `git rebase -i --autosquash b288a1d`로 접어
커밋 구조(3개)를 유지했다 — 별도 4번째 커밋을 만들지 않았다.

- cmd: `./gradlew --no-daemon :decision:test --tests 'bidvector.decision.priority.derive.*'`
- exit: 0 — 64 tests(`competitivenessNotCollected` test 추가로 63→64)
- cmd: `./gradlew --no-build-cache clean check`
- exit: 0 — rebase 전 반영본 확인
- cmd: `GIT_SEQUENCE_EDITOR=true git rebase -i --autosquash b288a1d`
- exit: 0 — 3 fixup 커밋이 각자의 대상 커밋에 접힘, `git status --short` clean
- cmd: `./gradlew --no-build-cache clean check`(rebase 뒤 최종 HEAD)
- exit: 0 — `BUILD SUCCESSFUL`, 345 actionable tasks(320 executed)

## 알려진 제한(TDD 순서) — 이 slice는 RED 를 문자 그대로 먼저 놓지 않았다

`bidRateAgainst`(1B)의 실제 시그니처(`origin`·`Resolution.Resolved<RoundingPolicy>`
필요, `Measurement` 래핑)와 `Money.export()` 경로가 scope.md 표의 단순 서술과 달라
main 코드로 먼저 API 를 확정한 뒤(§ 아래 「main 작성」) test 를 썼다 — 설계 검토 (5)
「RED 먼저」 지시와 다른 순서다. RED 로 고정한 것은 acceptance 기준(설계 검토 (5) 1
목록)이지 컴파일 실패 자체는 아니다 — test 작성 뒤 첫 실행에서 fixture 버그(아래 S-2
1차) 하나를 실제로 잡았다는 점에서 회귀 방지력은 확인됐다.

## main 작성 — export 미import 로 컴파일 실패 → 수정 → GREEN

- cmd: `./gradlew --no-daemon :decision:compileKotlin`
- exit: 1(1차) — `ComplexityDerivation.kt`·`Derivations.kt`의 `Money.export()` 호출에
  `bidvector.sharedkernel.export` import 누락(top-level 확장 함수는 명시 import 필요).
- cmd: `./gradlew --no-daemon :decision:compileKotlin`
- exit: 0(2차, import 추가 후)

## S-2 — 신설 test

- cmd: `./gradlew --no-daemon :decision:test --tests 'bidvector.decision.priority.derive.*'`
- exit: 1(1차) — `testBidAmount(..., provenance = Undeclared)`가 `roundedWith` 자체의
  provenance 전건에 걸려 fixture 구성 자체가 실패(`BudgetCaptureDerivationTest` 「미선언
  출처」 test). base 쪽을 `Undeclared`로 바꿔 재구성(recommended는 정상 생성).
- cmd: `./gradlew --no-daemon :decision:test --tests 'bidvector.decision.priority.derive.*'`
- exit: 0(2차) — 63 tests, 0 failed

## S-1 — 전체 clean check(detekt ReturnCount·ktlint 포맷 발견 및 수정 포함)

- cmd: `./gradlew --no-build-cache clean check`
- exit: 1(1차) — `decision:detekt` `deriveBudgetCapture` ReturnCount(3>2). `when` 단일 식 +
  `budgetCaptureFromRate` 분리로 수정.
- cmd: `./gradlew --no-build-cache clean check`
- exit: 1(2차) — `decision:ktlintMainSourceSetCheck`(`Band.kt` 한 줄 `init{}`·`Derivations.kt`
  `when` 분기 혼합 줄바꿈) — 여러 줄로 재포맷.
- cmd: `./gradlew --no-build-cache clean check`
- exit: 1(3차) — `decision:ktlintTestSourceSetCheck`(`DerivationPolicyDataTest.kt` 메서드
  체이닝 줄바꿈 위치) — 중간 변수로 재구성.
- cmd: `./gradlew --no-build-cache clean check`
- exit: 0(4차) — `BUILD SUCCESSFUL`, 345 actionable tasks(321 executed).

## S-3 — decision 전체(4B-1·4B-3·4B-4 test 무변경)

- cmd: `./gradlew --no-daemon :decision:test`
- exit: 0

## S-4 — decision gateExecutionGate

- cmd: `./gradlew --no-daemon :decision:gateExecutionGate`
- exit: 0 — `gate.tests.decision`에 등재한 여덟 클래스 실행 확인

## S-5 — app conformance(corpus 무영향)

- cmd: `./gradlew --no-daemon :app:test --tests '*Conformance*'`
- exit: 0

## S-6 — qualityBaseline

- cmd: `./gradlew qualityBaseline`
- exit: 0

## gate·milestone 커밋 뒤 — S-1 재실행

- cmd: `./gradlew --no-build-cache clean check`
- exit: 0 — gate-tests 등재·milestone 문단 추가 뒤에도 `BUILD SUCCESSFUL`

## S-0 — 격리 worktree 재현(1차 HEAD `efeff5f`, 계약 갱신 반영 뒤 2차 HEAD)

**verifier r1 F-5 정정** — 이 절이 이전에 2차 head 로 적었던 `c4f1871` 은 그 뒤
evidence 커밋 amend 로 궤도를 벗어나 **HEAD 의 조상이 아니다**(`git merge-base
--is-ancestor c4f1871 HEAD` 실패, 「낡는 좌표」 클래스). 2차 실행은 실제로는 rebase
직후의 head(당시 `173230a`, 이후 amend·capability-map 커밋으로 대체됨)에서였다 —
아래는 그 사실만 남기고 도달 불가능한 SHA 를 지운다.

- cmd: `git worktree add --detach <tmp-dir> HEAD && (cd <tmp-dir> && ./gradlew --no-build-cache clean check)`
- exit: 0(1차, `efeff5f`) — `BUILD SUCCESSFUL in 1m 1s`, 354 actionable tasks(354 executed).
- exit: 0(2차, 팀장 계약 갱신 #1~#4 반영 뒤 rebase 직후) — `BUILD SUCCESSFUL in 56s`, 354
  actionable tasks(354 executed). worktree는 매번 `git worktree remove --force`로
  정리, `git worktree prune`로 잔여 확인.
- exit: 0(3차, verifier r1 반영 뒤 최종 head — 아래 「verifier r1 반영」절) — S-0 재실행 결과 동일.

## clean-tree 게이트 — 경로 개별 인자 + 양성 대조

- cmd: `git status --porcelain -- decision/src/main/kotlin/bidvector/decision/priority/derive decision/src/test/kotlin/bidvector/decision/priority/derive config/quality/gate-tests.properties milestone-4.md reports/evidence/m4/4b5`
- exit: 0 — `reports/evidence/m4/4b5/{commands,checklist,policy-values,rollback}.md`(??, 이
  커밋 전 상태)만 잔존, 코드 경로는 clean.
- 양성 대조: `Band.kt`에 한 줄 추가 → `git status --porcelain` `M` 출력 확인 →
  `git checkout -- Band.kt`로 원복 → 재확인 결과 없음.

## 역방향 파급 검사(scope.md·milestone-4.md 에 줄을 추가했으므로)

- cmd: `grep -rn "4b5/scope\.md:[0-9]\|milestone-4\.md:[0-9]\|4b5/checklist\.md:[0-9]\|4b5/policy-values\.md:[0-9]\|4b5/commands\.md:[0-9]" --include='*.md' --include='*.kt' .`
- exit: 1(매치 없음) — 이 slice 문서를 `file:line`으로 인용하는 다른 문서 0건.

## 하네스 레인 변경 확인

- cmd: `git log --oneline 9eddf7525b74f89ce279acbb3adf4948f05c25f1..HEAD -- CLAUDE.md .claude/`
- exit: 0(출력 없음) — 하네스 레인 변경 없음.

## secret 스캔

- cmd: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" decision/src/main/kotlin/bidvector/decision/priority/derive decision/src/test/kotlin/bidvector/decision/priority/derive config/quality/gate-tests.properties milestone-4.md`
- exit: 1(매치 없음 = 통과)
- evidence 커밋(commands.md 자신 포함) 뒤 재실행: exit 0, 매치 2건 — 둘 다 이 절의
  스캔 명령 문자열 자기 인용(같은 판독 규칙, developer 구간 매치 0).

## rollback 실측 — 임시 clone(evidence 커밋 뒤)

- cmd: `git clone --no-hardlinks . <tmp-dir> && cd <tmp-dir> && git checkout m4-4b5/2026-09-10 && git restore --source=9eddf7525b74f89ce279acbb3adf4948f05c25f1 --staged --worktree -- config/quality/gate-tests.properties decision/src/main/kotlin/bidvector/decision/priority/derive decision/src/test/kotlin/bidvector/decision/priority/derive milestone-4.md reports/evidence/m4/4b5`
- exit: 0 — `git status --porcelain` D 17(main 7·test 9·evidence 1 `scope.md`)/M 2, `git
  diff <base> -- <같은 경로들>` 0줄(완전 일치, `derive` 디렉터리 자체 소멸).
- cmd: `./gradlew --no-daemon :decision:compileKotlin :decision:test :decision:gateExecutionGate`(되돌린 트리)
- exit: 0 — `BUILD SUCCESSFUL`. 임시 clone은 실측 뒤 삭제.

## clean-tree 게이트 재확인(evidence 커밋 뒤) + 양성 대조

- cmd: `git status --porcelain -- decision/src/main/kotlin/bidvector/decision/priority/derive decision/src/test/kotlin/bidvector/decision/priority/derive config/quality/gate-tests.properties milestone-4.md reports/evidence/m4/4b5`
- exit: 0, 출력 없음 — clean.
- 양성 대조: `Band.kt`에 한 줄 추가 → `git status --porcelain` 이 `M` 출력 확인 →
  `git checkout --` 로 원복 → 재확인 결과 없음.

## verifier r1 반영(not-ready → 수정 라운드 1, base head `e47370e`)

F-1(high)·F-2·F-3(medium, 같은 함수·정책 파일)을 코드+test로, F-4~F-8(장부·low·계약)을
evidence로 반영했다(`_workspace/m4-4b5/04_verifier_report.md`).

- cmd: `./gradlew --no-build-cache clean check`
- exit: 0 — `BUILD SUCCESSFUL`, 345 actionable tasks(321 executed). F-1 `MarginInputs.init`
  추가·F-3 KDoc 정정 뒤에도 GREEN.
- cmd: `./gradlew --no-daemon :decision:test --tests 'bidvector.decision.priority.derive.*'`
- exit: 0 — 71 tests(F-1 다섯·F-2 둘 추가로 64→71).
- cmd: `./gradlew --no-daemon :decision:test`
- exit: 0 — S-3, 4B-1·4B-3·4B-4 무변경.
- cmd: `./gradlew --no-daemon :decision:gateExecutionGate`
- exit: 0 — S-4.

**F-1 경계 probe(우회 재현 → 차단 확인)** — `MarginInputs.init`(F-1)을 일시 제거하고
`ExpectedMarginDerivationTest`만 재실행:
- exit: 1 — F-1 신설 test 셋(`recommendedRate`·`floorRate`·`predictedRate` 상한) 전부
  `Expected exception ... but no exception was thrown`로 실패(11 중 3 failed) — 우회가
  다시 열리면 test가 잡는다는 것을 실측. `init` 복원 후 재실행 exit 0(원복 확인,
  `git diff --stat` 원본과 동일).

**F-2 변이 probe(scaleDigits 6→2)** — 출하 `DERIVATION_POLICY.budgetCaptureRounding`
의 `scaleDigits`를 `6`→`2`로 바꾸고 `DerivationPolicyDataTest`만 재실행:
- exit: 1 — 신설 test `expected:<6> but was:<2>`로 실패(16 중 1 failed, F-2 재현 그대로
  잡힘). 값 복원 후 재실행 exit 0(원복 확인).

**최종 head(`17d517b`) 재확인** — F-4~F-8 커밋(evidence 장부, D-4B5-3 정정 커밋
`9817dda` 반영 포함) 뒤:
- cmd: `git worktree add --detach <tmp-dir> HEAD && (cd <tmp-dir> && ./gradlew --no-build-cache clean check)`
- exit: 0 — S-0, `BUILD SUCCESSFUL in 1m 6s`, 354 actionable tasks(354 executed). worktree
  는 `git worktree remove --force` + `git worktree prune`로 정리.
- cmd: `git clone --no-hardlinks . <tmp-dir> && cd <tmp-dir> && git checkout m4-4b5/2026-09-10 && git restore --source=9eddf7525b74f89ce279acbb3adf4948f05c25f1 --staged --worktree -- config/quality/gate-tests.properties decision/src/main/kotlin/bidvector/decision/priority/derive decision/src/test/kotlin/bidvector/decision/priority/derive docs/discovery/capability-map.md milestone-4.md reports/evidence/m4/4b5`
- exit: 0 — `git status --porcelain` D 21/M 3(rollback.md 선언과 정확히 일치), `git diff
  <base> -- <같은 경로들>` 0줄, `derive` 디렉터리 소멸.
- cmd: `./gradlew --no-daemon :decision:compileKotlin :decision:test :decision:gateExecutionGate`(되돌린 트리)
- exit: 0 — `BUILD SUCCESSFUL`. 임시 clone·worktree 전부 실측 뒤 삭제.
- cmd: `git status --porcelain -- <in_scope 6경로>`
- exit: 0, 출력 없음 — clean-tree 최종 확인.

## 사용자 승인 2026-09-10 — 종결 등재(verifier r2 G-1 반영, D-4B6-4 인계)

- cmd: `./gradlew --no-daemon :decision:compileKotlin :decision:test --tests 'bidvector.decision.priority.derive.*'`
- exit: 0 — `DerivationAbsence.FloorRateOutOfRange` 신설·`DerivationInputs.kt` KDoc 축별
  정정·`DerivationPolicyData.kt` 승인 문면 갱신 뒤에도 71 tests 무변경 GREEN(코드 로직
  변경 없음, 값·분기 동일).
- cmd: `git log --oneline 9eddf7525b74f89ce279acbb3adf4948f05c25f1..HEAD -- CLAUDE.md .claude/`
- exit: 0(출력 없음) — 하네스 레인 변경 재확인.
- cmd: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m4/4b5/ decision/src/main/kotlin/bidvector/decision/priority/derive decision/src/test/kotlin/bidvector/decision/priority/derive milestone-4.md`
- exit 1(매치 없음, 이 절 자체를 아직 커밋하기 전 실행이라 자기 인용도 없음) — secret 스캔.
- cmd: `grep -rn "4b5/scope\.md:[0-9]\|milestone-4\.md:[0-9]\|4b5/checklist\.md:[0-9]\|4b5/policy-values\.md:[0-9]\|4b5/commands\.md:[0-9]\|4b5/rollback\.md:[0-9]" --include='*.md' --include='*.kt' .`
- exit: 1(매치 없음) — 좌표 역방향 파급 없음(scope.md·checklist.md·policy-values.md·
  milestone-4.md 전부 편집했으나 `file:line` 인용으로 이 문서들을 가리키는 곳은 0건).
