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
- 핵심 결과: `ResponseMappingTest` 20 tests(신설 9) · `SuccessShapeFailClosedTest` 7 tests(회귀
  없음, table 행 +4) · `MlGateRegistrationTest` 포함 전체 실패 0.

## (참고) 1차 workflow:test 실패 — 자기 KDoc 이 만든 오탐
- cmd: `./gradlew --offline --no-daemon :workflow:test :adapters:test` (수정 전)
- exit: 1
- 핵심 결과: `PredictionBoundaryTest`(4A 아키텍처 경계 test) 가 `BidPredictionOutcome.kt`
  KDoc 안의 비정규화 참조(`decision.UnitScore`, `bidvector.` 접두 누락)를 잡아냈다 —
  `[bidvector.decision.UnitScore]`로 정정(KDoc 링크 문법) 뒤 통과. 게이트가 실제로
  작동한다는 증거로 남긴다.

## 2026-09-16T01:42:29Z
- cmd: `git status --porcelain -- <in_scope 경로 10개 개별 인자>`
- exit: 0, 출력 없음(clean)
- 핵심 결과: clean-tree 게이트 충족. 양성 대조(`BidPredictionOutcome.kt`에 줄 추가 후 같은
  명령 재실행 → `M` 1건 출력 확인 → `git checkout --`로 절삭 복원 → 재확인 clean) 완료.

## 2026-09-16T01:41:57Z
- cmd: `grep -rniE -f config/quality/leak-patterns.txt <in_scope 경로 10개> reports/evidence/m4/4d3/`
- exit: 1 (매치 없음 = 통과)
- 핵심 결과: 비밀값·개인정보 패턴 매치 0. Telegram id·사업자 정보 없음(육안 확인 병행).

## 2026-09-16T01:47:09Z
- cmd: 임시 clone(`git clone --no-hardlinks`)에서 rollback.md ①~⑥ 실측 — `git restore
  --source=4b9fa21 --staged --worktree -- <9개 M 파일>` + `rm -f
  DiagnosticsShapeValidation.kt` → `git diff 4b9fa21 -- <9개 M 파일>`(출력 없음) →
  `:workflow:compileKotlin :adapters:compileKotlin :workflow:compileTestKotlin
  :adapters:compileTestKotlin` → `:workflow:test :adapters:test` → 루트
  `./gradlew --no-build-cache --no-daemon clean check`
- exit: 0(전 단계)
- 핵심 결과: 되돌린 트리에서 355 actionable tasks(338 executed) 전건 통과(⑥ 게이트
  확인). rollback.md 참고.
