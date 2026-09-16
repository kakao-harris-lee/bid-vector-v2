# 실행 명령 — M4/4D-3

## 2026-09-16T01:36:16Z
- cmd: `./gradlew --no-build-cache --no-daemon clean check` (루트, 1차)
- exit: 1 (BUILD FAILED)
- 핵심 결과: `adapters:ktlintMainSourceSetCheck`(`ParsedSuccessFields.kt` chain-method-continuation)·
  `adapters:detekt`(`ParsedSuccessFields.kt` TooManyFunctions, 14>11) 2건 실패. 도메인
  test·컴파일은 이미 초록이었다 — 스타일·크기 게이트만 걸렸다.

## (수정) 진단 파싱 헬퍼 3함수(`toDiagnostics`·`toWeightOrNull`·`ProtoSegmentSupport.toDomainOrNull`)를
`DiagnosticsShapeValidation.kt`로 이동(`CandidateShapeValidation.kt` 분리와 같은 사유) + ktlint
autoformat(`ktlintMainSourceSetFormat`)으로 chain 줄바꿈 수정.

## 2026-09-16T01:40:15Z
- cmd: `./gradlew --no-build-cache --no-daemon clean check` (루트, 2차 — scope.md acceptance)
- exit: 0
- 핵심 결과: 346 actionable tasks(321 executed), 전 모듈 `check` 통과 — `gateExecutionGate`·
  `contractGate`·`leakPatternGate`·`koverVerify` 포함.

## 2026-09-16T01:39:55Z
- cmd: `./gradlew --offline --no-daemon :workflow:test`
- exit: 0
- 핵심 결과: `PredictionValueTest` 13 tests(신설 4) · `PredictionFactsTest` 3 tests(신설 1) ·
  `PredictionBoundaryTest` 4 tests(회귀 없음, 1차 시도에서 자기 KDoc 오기로 1건 실패했다가
  수정 후 통과 — 아래 기록) — 전부 실패 0.

## 2026-09-16T01:40:14Z
- cmd: `./gradlew --offline --no-daemon :adapters:test`
- exit: 0
- 핵심 결과: `ResponseMappingTest` 20 tests(신설 7 — `git show 4b9fa21:<파일> | grep -c
  '@Test'` 13 대 head 같은 명령 20, verifier r1 F-3 시정: 이전 기록의 「신설 9」는
  오기) · `SuccessShapeFailClosedTest` 7 tests(회귀 없음, table 행 +4) ·
  `MlGateRegistrationTest` 포함 전체 실패 0. 신설 `@Test` 합계는 그대로 +12
  (PredictionValueTest +4·ResponseMappingTest +7·PredictionFactsTest +1).

## (참고) 1차 workflow:test 실패 — 자기 KDoc 이 만든 오탐
- cmd: `./gradlew --offline --no-daemon :workflow:test :adapters:test` (수정 전)
- exit: 1
- 핵심 결과: `PredictionBoundaryTest`(4A 아키텍처 경계 test) 가 `BidPredictionOutcome.kt`
  KDoc 안의 비정규화 참조(`decision.UnitScore`, `bidvector.` 접두 누락)를 잡아냈다 —
  `[bidvector.decision.UnitScore]`로 정정(KDoc 링크 문법) 뒤 통과. 게이트가 실제로
  작동한다는 증거로 남긴다.

## 2026-09-16T01:42:29Z (verifier r1 F-5 시정 — 아래로 대체)
- cmd: `git status --porcelain -- <in_scope 경로 19개 개별 인자>`(코드·test·evidence 전부)
- exit: 0, 출력 없음(clean)
- 핵심 결과: clean-tree 게이트 충족. 양성 대조는 **비파괴 절삭**으로 다시 잰다 — 원본
  줄 수(`wc -l`)와 sha256(`shasum -a 256`)을 먼저 기록 → `BidPredictionOutcome.kt`에
  한 줄 추가 → `git status --porcelain`이 `M` 1건을 낸다 확인 → `head -n <원래 줄수>`로
  덮어써 복원(`git checkout --` 금지, 2026-09-09 규격 — 같은 파일의 다른 레인 미커밋
  편집을 지울 위험이 있는 형태) → 복원 후 sha256 이 원본과 동일함을 재확인
  (`cd95d639…ef57a28` 일치) → `git status --porcelain` 다시 clean.

## 2026-09-16T01:41:57Z (verifier r1 F-4 시정 — 대상 경로를 in_scope 전부로 확장, 아래로 대체)
- cmd: `grep -rniE -f config/quality/leak-patterns.txt <in_scope 경로 19개 전부(docs·
  milestone-4.md·config/quality/gate-tests.properties 포함)>`
- exit: 0, 매치 3(전부 `docs/discovery/capability-map.md` 의 기존 산문 — 4D-3 신설
  아님). `git diff 4b9fa21..HEAD -- docs/discovery/capability-map.md`에 그 세 줄이
  나타나지 않아 base 와 동일함을 확인했다(4D-3 이 새로 만든 매치 0). 루트
  `leakPatternGate`(`check`)의 scanRoot 는 `reports/evidence`뿐이라 이 파일은 그
  게이트 대상 밖 — CI 영향 없음. 이전 기록의 「exit 1」은 대상 경로가 docs 를
  빠뜨려 이 세 줄을 보지 못한 결과였다(결론은 옳았으나 근거 경로가 덜 덮었다).

## 2026-09-16T02:05Z 경 (verifier r1 F-7 시정 — 역방향 파급 검사 기록 신설, 팀장 F-1 커밋
`30ae0bf` 반영 뒤 재실행)
- cmd: `git diff -U0 4b9fa21..HEAD -- docs/discovery/data-dictionary.md \| grep '^@@'` ·
  같은 명령 `docs/discovery/capability-map.md` 대상 · `grep -rn
  'data-dictionary[^ ]*:[0-9,]\+' --include='*.md' --include='*.kt' --include='*.properties' .`
  · 같은 패턴 `capability-map`
- exit: 0(전 명령)
- 핵심 결과: 삽입 최솟점 `data-dictionary.md` 1577행·`capability-map.md` 3486행. 두
  파일을 `파일:줄`로 인용하는 곳(과거 slice evidence 전체) 의 최대 좌표는 각각
  1554·3457 — 전부 삽입 지점 위라 밀린 인용 0. `*.kt`·`*.properties`엔 이런 인용
  자체가 없다.

## 2026-09-16T02:13:20Z
- cmd: `./gradlew --no-build-cache --no-daemon clean check` (F-2·3·4·5·7 시정 커밋
  `91fb85d` HEAD 재실측 — 2026-09-16 규칙)
- exit: 0
- 핵심 결과: 346 actionable tasks(321 executed) 전건 통과.

## 2026-09-16T01:49:07Z
- cmd: `./gradlew --no-build-cache --no-daemon clean check` (evidence 커밋 `9257c60` HEAD
  재실측 — 2026-09-16 규칙: evidence 편집 커밋마다 그 HEAD 에서 재확인)
- exit: 0
- 핵심 결과: 346 actionable tasks(321 executed) 전건 통과. evidence 문서 자체가
  `leakPatternGate`(scanRoot `reports/evidence`)를 붉히지 않았다.

## 2026-09-16T01:47:09Z
- cmd: 임시 clone(`git clone --no-hardlinks`)에서 rollback.md ①~⑥ 1차 실측(당시 9개
  M 파일 목록, 문서 3파일 미포함 — verifier r1 F-2 로 목록 오류 확인 뒤 아래에서 재실측)
- exit: 0(전 단계)
- 핵심 결과: 되돌린 트리에서 355 actionable tasks(338 executed) 전건 통과.

## 2026-09-16T02:11:25Z (verifier r1 F-2 시정 — 12개 M 파일 전체로 재실측)
- cmd: 임시 clone(HEAD `30ae0bf`)에서 rollback.md ①~⑥ 재실측 — `git restore
  --source=4b9fa21 --staged --worktree -- <12개 M 파일(문서 3 포함)>` + `rm -f
  DiagnosticsShapeValidation.kt` → `git diff 4b9fa21 -- <12개 M 파일>`(출력 없음) →
  `:workflow:compileKotlin :adapters:compileKotlin :workflow:compileTestKotlin
  :adapters:compileTestKotlin` → `:workflow:test :adapters:test` → 루트
  `./gradlew --no-build-cache --no-daemon clean check`
- exit: 0(전 단계)
- 핵심 결과: 문서 3파일(`data-dictionary.md`·`capability-map.md`·`milestone-4.md`)을
  포함해 되돌려도 355 actionable tasks(338 executed) 전건 통과. rollback.md 참고.

## 2026-09-16 종결 커밋 HEAD 재실측 (팀장)
- cmd: `./gradlew --no-build-cache --no-daemon clean check` (HEAD `52fc9b3` — milestone-4 종결 문단·checklist 승인 절·scope `head_sha`)
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL, 346 tasks. `origin/main` 은 base `4b9fa21` 그대로 — 흡수 병합 없음.
