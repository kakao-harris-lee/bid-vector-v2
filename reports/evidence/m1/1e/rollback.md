# M1/1E — rollback

정본. **range revert 가 아니라 in_scope 경로 한정 restore**(evidence-pack 규격,
2026-09-04 결정) — 하네스 레인 변경이 이번 range 에 없으므로(`commands.md` 상단) 이
slice 는 `git log 2af32f6..HEAD -- CLAUDE.md .claude/` 결과가 비어 하네스 경로를 가릴
필요가 없지만, 명령 형태는 다른 slice 와 동일하게 경로 한정으로 통일한다.

## 되돌리는 것

`strategy/**`의 1E 신규 구현(값 타입 전부·`WatchRules.evaluate`·`validate`·컴파일 실패
하네스)과 `config/quality/gate-tests.properties`의 `gate.tests.strategy` 등재. `strategy`
모듈은 자리표시자(`ModuleBoundaryAnchor.kt`)로 돌아간다.

## 명령 (임시 clone 에서 실측 — `commands.md` 「rollback 명령 실측」)

```sh
git restore --source=2af32f6 --staged --worktree -- \
  strategy/build.gradle.kts \
  strategy/src/main/kotlin/bidvector/strategy/ActionThresholds.kt \
  strategy/src/main/kotlin/bidvector/strategy/AxisOutcome.kt \
  strategy/src/main/kotlin/bidvector/strategy/EvaluationPath.kt \
  strategy/src/main/kotlin/bidvector/strategy/Score.kt \
  strategy/src/main/kotlin/bidvector/strategy/StrategyEvent.kt \
  strategy/src/main/kotlin/bidvector/strategy/StrategyPolicyData.kt \
  strategy/src/main/kotlin/bidvector/strategy/StrategyTypes.kt \
  strategy/src/main/kotlin/bidvector/strategy/StrategyValidation.kt \
  strategy/src/main/kotlin/bidvector/strategy/Text.kt \
  strategy/src/main/kotlin/bidvector/strategy/WatchBudgetAxis.kt \
  strategy/src/main/kotlin/bidvector/strategy/WatchRules.kt \
  strategy/src/main/kotlin/bidvector/strategy/WatchTypes.kt \
  strategy/src/test/kotlin/bidvector/strategy/CompileFailureHarnessTest.kt \
  strategy/src/test/kotlin/bidvector/strategy/StrategyValidationTest.kt \
  strategy/src/test/kotlin/bidvector/strategy/WatchRulesTest.kt \
  strategy/src/test/resources/compile-fixtures \
  config/quality/gate-tests.properties
```

`--source`에 없는 경로(전부 신규 파일)는 삭제로 스테이징된다 — 별도 `git rm`이 필요
없다(evidence-pack 규격). `strategy/build.gradle.kts`·`config/quality/gate-tests.properties`는
base 내용으로 되돌아간다.

## 확인 지점

`git diff 2af32f6 -- strategy/ config/quality/gate-tests.properties`가 빈 결과 —
임시 clone에서 실측 완료(`commands.md`). `git status`에서 위 파일들이 삭제/수정으로
스테이징됨을 확인한 뒤 `git commit`으로 되돌림을 확정한다(이 문서는 명령만 제공하고
실제 커밋은 운영자 승인 하에 별도로 한다).

## 예상 복구 시간

되돌림 자체는 즉시(명령 1회). `./gradlew :strategy:check`(자리표시자 anchor만 남은
상태)로 재확인하는 데 수 초.

## 알려진 제한

- `OperatorStrategy`의 `internal constructor`는 **모듈 밖** 조립만 막는다 — 같은
  `strategy` 모듈 test는 직접 호출할 수 있다(1B·1C와 같은 한계, `StrategyValidationTest`의
  `OperatorStrategy 는 validate 를 거쳐서만 생긴다` test가 이 한계를 명시적으로 등재한다).
- `KeywordScopeText`·`FullScopeText`의 타입 분리는 **어댑터가 어느 텍스트를 어느 자리에
  넣을지 선택했음을 강제**할 뿐, **그 선택이 정직한지**(예: `FullScopeText`의 내용물로
  `KeywordScopeText`를 만들어 실제로는 같은 문자열을 두 자리에 넣는 것)는 막지 않는다
  (scope.md 위협 모델 「방어하지 않는다」, 컴파일 fixture 2의 한계).
- `Score.of`의 `Fact.Absent` 분기는 `ReasonCode.POLICY_NOT_APPLICABLE`을 재사용한다 —
  D-16이 `StrategyViolation`·`WatchUndeterminableReason` 두 사유 어휘에 `ReasonCode`를
  더하지 않는다는 규율과 `Fact<Score>`가 구조적으로 요구하는 `ReasonCode` 필드 사이의
  판단이 갈린 지점이다(`Score.kt` KDoc에 근거 기록). 이 값은 `validate` 안에서만 소비되고
  `StrategyViolation.ScoreOutOfRange`로 번역돼 밖으로 나가므로 사용자 대면 어휘 오염은
  없다.
- D-9 결합에서 제외 규칙 둘(지역·키워드)이 동시에 매치해도 `WatchVerdict.Rejected.failed`는
  우선순위상 먼저인 규칙 하나만 싣는다(exclude-region 우선) — 모든 동시 매치 규칙을
  나열하지 않는다. D-7 문면(「부분집합」)과 충돌하지 않으나 legacy의 「첫 실패에서 즉시
  종료」 관찰과 더 가깝다(`WatchRules.kt` KDoc에 근거 기록).
- `WatchRules.evaluate`의 예산 축은 감시 규칙의 `BudgetBound`가 이미 `OperatorDeclared`·
  `INCLUSIVE`로 구성됐다고 가정하지 않는다 — `validate`를 거치지 않고 `WatchRules`를 직접
  조립하면(같은 모듈 test처럼) 임의 과세 처리의 `BudgetBound`를 넣을 수 있고, 그 경우
  `compareKnownVat`가 `Absent`를 내 `Undeterminable(BudgetNotComparable)`로 자연스럽게
  이어진다(오작동이 아니라 D-8 문면대로의 동작).
