# M4/4B-6a 실행 명령 로그

base_sha: `786febe39469835a45e9a1c6d6e8081226777364`

## RED — main 없이 test 컴파일 실패 확인

- cmd: `./gradlew --no-daemon :workflow:compileTestKotlin`
- exit: 1 — `TextSynthesisTest`·`KeywordHitsCounterTest`·`ProfilePortsTest`·
  `OpportunityPolicyDataTest` 전부 `Unresolved reference`(`synthesizeNoticeText`·
  `SynthesisOutcome`·`ProfileFacts`·`WorkloadPort`·`OpportunityPolicyData`·
  `SynthesisVersion` 등) — 신설 symbol 넷 전부가 아직 없어 실패함을 확인.

## GREEN — main 작성 뒤 S-2

- cmd: `./gradlew --no-daemon :workflow:test --tests 'bidvector.workflow.evaluation.*'`
- 1차 exit: 1 — `CompositionBoundaryTest`가 `TextSynthesis.kt` KDoc의
  `contract.bidvector.ml.v1.TextKind` 문자열(완전정규화 참조 패턴)을 allow-list 위반으로
  잡음. KDoc을 패키지 경로 없이 재서술해 수정.
- 2차 exit: 0 — 52 tests(기존 evaluation 패키지 test 27 + 신설 25), 0 failed.
  (`TextSynthesisTest` 7 · `KeywordHitsCounterTest` 5 · `ProfilePortsTest` 4 ·
  `OpportunityPolicyDataTest` 9 — 각 파일 test-results XML `testcase` 수로 실측).

## S-3 — CompositionBoundaryTest 단독

- cmd: `./gradlew --no-daemon :workflow:test --tests '*CompositionBoundaryTest*'`
- exit: 0 — 신설 파일 넷이 이 패키지 스캔(`walkTopDown`) 대상에 자동 포함됨을 확인
  (allow-list 재구성 불필요).

## S-5 — 비밀값 단어 누출 검사

- cmd: `grep -rniE -f config/quality/leak-patterns.txt workflow/src/main/kotlin/bidvector/workflow/evaluation`
- exit: 1(매치 0건 — grep 관례상 「매치 없음」이 exit 1, scope.md acceptance 그대로) —
  합성 코드에 `config/quality/leak-patterns.txt` 의 여섯 패턴 매치 없음(어휘는 패턴 파일 참조 — 산문에 적지 않는다).

## S-4 — gateExecutionGate

- cmd: `./gradlew --no-daemon :workflow:gateExecutionGate`
- exit: 0 — `gate-tests.properties`에 등재한 신설 test 넷이 실제로 실행됐음을 확인
  (등재만 하고 실행에서 빠지는 결함 없음).

## ktlint — 서식 위반 자동 수정

- cmd: `./gradlew --no-build-cache clean check` (1차)
- exit: 1 — `ktlintMainSourceSetCheck`(`OpportunityPolicyData.kt` 리스트 인자 줄바꿈,
  `TextSynthesis.kt` 메서드 체인 줄바꿈)·`detekt`(`ProfilePortsTest.kt` 68행
  `MaxLineLength`).
- cmd: `./gradlew --no-daemon :workflow:ktlintFormat`
- exit: 0 — 세 파일 자동 정렬(수기 수정 없음, 골든 텍스트·로직 무변경 — 재실행한 S-2가
  그대로 GREEN임을 재확인).

## S-1 — 전체 clean check

- cmd: `./gradlew --no-build-cache clean check` (2차, 서식 수정 뒤)
- exit: 0 — `BUILD SUCCESSFUL`, 345 actionable tasks(321 executed). `qualityBaseline`
  (S-6)도 이 체인 안에서 함께 실행됨(`check`가 그 task를 의존).

## S-0 — 격리 worktree clean check(커밋 뒤, head `40997a15b7f5d3e05c497c4c11c4f0785cf7aa27`)

- cmd: `git worktree add --detach <scratchpad>/s0-worktree HEAD`
- cmd: `(cd <scratchpad>/s0-worktree && ./gradlew --no-build-cache clean check)`
- exit: 0 — `BUILD SUCCESSFUL in 1m 2s`, 354 actionable tasks(354 executed, 캐시 없이
  전건 재실행 — S-1과 다른 clean 상태에서도 통과).
- cmd: `git worktree remove --force <scratchpad>/s0-worktree`
- exit: 0 — worktree 제거 확인, `git worktree list`에 잔여 없음.

## verifier r1 반영(F-1 medium·F-3 low) — 커밋 `8c0a9df` 뒤 재검증

- cmd: `./gradlew --no-daemon :workflow:test --tests 'bidvector.workflow.evaluation.*'`
- exit: 0 — 신설 F-1 test 둘(NOTICE·PROFILE) + F-3 test 둘(단어 내부 매치·공백 삽입
  불일치) 포함 전건 GREEN.
- cmd: `./gradlew --no-build-cache --no-daemon clean check` (1차, F-1/F-3 반영 직후)
- exit: 1 — `detekt`가 `synthesize`의 `return` 3개(`ReturnCount` 한도 2 초과)를 잡음.
  `truncated: String?` 중간값 + `isNullOrBlank()` 단일 `return`으로 재구성(로직 동일).
- cmd: `./gradlew --no-build-cache --no-daemon clean check` (2차, 재구성 뒤)
- exit: 0 — `BUILD SUCCESSFUL in 59s`, 345 actionable tasks(321 executed).
- cmd: `grep -rniE -f config/quality/leak-patterns.txt workflow/src/main/kotlin/bidvector/workflow/evaluation; test $? -eq 1`
- exit: 0(매치 0건) — F-1/F-3 반영분에도 비밀값 단어 없음.

## rollback 재실측(`8c0a9df` 기준, 임시 clone)

- cmd: `git clone --no-hardlinks . <tmp> && cd <tmp> && git checkout m4-4b6a/2026-09-11`
- exit: 0 — HEAD `8c0a9df8f29c79664278cf54aab37824ef3237f4` 확인.
- cmd: rollback.md의 `git restore --source=786febe3… --staged --worktree -- <14경로>`
- exit: 0 — `git status --porcelain` `D` 12 / `M` 2(F-1/F-3 반영 전과 파일 수 동일).
- cmd: `git diff 786febe39469835a45e9a1c6d6e8081226777364 -- <같은 경로들>`
- exit: 0, 출력 0줄(base와 완전 일치).
- cmd: `./gradlew --no-daemon :workflow:compileKotlin :workflow:compileTestKotlin :workflow:test :workflow:gateExecutionGate`
- exit: 0 — `BUILD SUCCESSFUL`(되돌린 트리에서 기존 evaluation test 전부 통과, 신설
  29 test는 파일 소멸로 대상에서 빠짐). 임시 clone 삭제 확인.

## 커밋

- `f8177fd` — main 넷 + test 넷(feat).
- `40997a1` — `gate-tests.properties`(4줄)·`milestone-4.md`(4B-6a 착수 문단, docs).
- `3fb22e4` — evidence 넷(checklist·commands·policy-values·rollback).
- `8c0a9df` — verifier r1 F-1(medium)/F-3(low) 반영(fix, 코드+test).
- (이 커밋) — evidence 갱신(checklist head_sha·F-1/F-2/F-3 등재, rollback 재산출).

경로 명시 `git add`/`git commit -- <경로들>`로 커밋(전체 add 금지) — 각 커밋 직후
`git diff --cached --name-status`로 스테이징 대조.
