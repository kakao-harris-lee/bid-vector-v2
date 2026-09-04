# commands — M1 / 1B

실행 명령과 종료 코드. 출력 전문을 붙이지 않는다 — 핵심 결과는 한 줄이다(`evidence-pack`
SKILL). 라운드 이력 절과 자기 검사 하네스는 만들지 않는다 — 그 기록은 git log 와 리뷰
verdict 가 갖는다.

## C-0 — 리뷰 range (1A 정정 문면 승계)

Codex 리뷰 요청 시점의 전체 diff 는 아래 명령이 낸다. **range 자체가 slice 산출물의 정의는
아니다** — `scope.md` 의 「하네스 레인 변경」 절이 하네스 경로를 가른다(evidence-pack SKILL
2026-09-04).

```
git diff --stat 66c1ab79af4c5a68145811a9e87008dfdb10da3c..HEAD
```

---

## Phase 3 — acceptance (`scope.md`의 `acceptance_commands`)

### 2026-09-04T00:00:00Z (UTC 시각은 근사 — 라운드 순서만 의미가 있다)

- cmd: `./gradlew :shared-kernel:check --no-daemon --no-build-cache` (커밋별 반복 실행, 4회 —
  각 커밋이 독립적으로 이 명령을 통과함을 확인)
- exit: 0 (매 회)
- 핵심 결과: 게이트 전건(`domainApiTypeGate`·`domainSourceReferenceGate`·`packageOwnershipGate`·
  `jarContentGate`·`sizeGate`·ktlint·detekt·`sourceSetLayoutGate`·`gateExecutionGate`·kover)
  통과, 0 실패 0 skip(당시 test 수는 그 시점 기록이라 낡는다 — **verifier r2 L-10 정정**:
  현재 test 수는 이 문서 하드코딩이 아니라 Phase 5 절의 명령 포인터가 정본이다)

- cmd: `git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)`  (B-0)
- exit: 0
- 핵심 결과: 격리 worktree(HEAD `2318328`)에서 전 모듈 `check` 통과, `app:compatibilitySmoke`
  포함 261 task 전건 실행(캐시 없음)

- cmd: `./gradlew --no-build-cache clean check --no-daemon`  (B-1, 작업 트리)
- exit: 0
- 핵심 결과: 전 모듈 통과. `app:test`(ArchitectureGate 포함)가 새 `shared-kernel` 도메인
  타입 위에서 위반 0을 낸다 — 1B가 처음으로 실제 도메인 코드 위에서 그 검증을 실행한다

- cmd: `./gradlew :app:test --tests '*ArchitectureGate*' --no-daemon`  (B-2)
- exit: 0
- 핵심 결과: 1A 승계 게이트 test 전건 통과(`--no-build-cache clean check`에 포함되어 별도
  독립 실행으로도 재확인)

- cmd: `./gradlew :build-logic:test --no-daemon`  (B-3)
- exit: 0
- 핵심 결과: `--no-build-cache clean check`에 포함되어 재확인됨(build-logic 판정 순수 함수
  test 전건 통과)

- cmd: `./gradlew :shared-kernel:test --no-daemon`  (B-4)
- exit: 0
- 핵심 결과: 0 실패, 0 skip. **verifier r1 L-2** — 클래스별·전체 test 수는 fixture·finding
  추가마다 밀리는 산문 하드코딩 대신 아래 명령이 정본이다:
  ```
  grep -oh 'tests="[0-9]*"' shared-kernel/build/test-results/test/TEST-bidvector.sharedkernel.*.xml
  ```

- cmd: `./gradlew qualityBaseline --no-daemon`  (B-5)
- exit: 0
- 핵심 결과: `--no-build-cache clean check`에 포함되어 재확인됨. `shared-kernel`의 실측값이
  `OPEN-ADR-06` 입력으로 처음 갱신됨(그린필드 바닥값이 아닌 실제 도메인 코드 위의 값) —
  결정 시점은 1B 종료(C12)

- cmd: `./gradlew :shared-kernel:domainApiTypeGate :shared-kernel:domainSourceReferenceGate --no-daemon --no-build-cache`  (B-6·B-7 단독 실행)
- exit: 0
- 핵심 결과: 1B가 실제 public API 표면(`Money`·`Rate`·`Measurement`·`Fact` 등) 위에서 이 둘을
  처음 실효시켰다 — 1A는 committed fixture 위에서만 쟀다(`reports/evidence/m1/1a/checklist.md`
  알려진 제한 43)

## Phase 3 후속 — 컴파일 실패 하네스 (운영자 결정 2026-09-04, 이월 항목 둘을 여기서 닫음)

### E-1 — 하네스 실행과 소요 시간

- cmd: `./gradlew :shared-kernel:test --tests "*CompileFailureHarnessTest*" --no-daemon --no-build-cache`
- exit: 0
- 핵심 결과: 5 test(음성·양성 다섯 쌍, 컴파일 10회) 0 실패 — `TEST-*.xml` `time="3.86"`초.
  `:shared-kernel:check` 전체에 더해진 시간은 무시할 만하다(B-1 재실행이 여전히 수 초대,
  아래 E-3)

### E-2 — `gateExecutionGate` 가 실제로 실행을 강제하는지(음성 대조)

- cmd: `./gradlew :shared-kernel:test --tests "*MoneyTest*" :shared-kernel:gateExecutionGate --no-daemon --no-build-cache`
- exit: 1
- 핵심 결과: 하네스 test class 를 실행 집합에서 뺀 뒤 `gateExecutionGate` 단독 실행이
  `"게이트 test class 가 실행되지 않았다 — bidvector.sharedkernel.CompileFailureHarnessTest"`
  로 실패했다 — 등재가 공허하지 않음을 실측으로 확인(이후 전건 재실행으로 정상 상태 복구,
  아래 E-3)

### E-3 — 등재 뒤 `:shared-kernel:check` 재확인

- cmd: `./gradlew :shared-kernel:check --no-daemon --no-build-cache`
- exit: 0
- 핵심 결과: `gate.tests.shared-kernel` 등재 뒤 전체 게이트(도메인·크기·ktlint·detekt·
  `gateExecutionGate`·kover) 재확인 통과

### secret 스캔

- cmd: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m1/1b/ shared-kernel/src/`
- exit: 0 — 매치는 이 절 자신(스캔 명령 문자열이 `secret`·`token`·`password`를 문자 그대로
  담는다)뿐이고, 코드·다른 evidence 문서에는 매치가 없다. **판독**: `codex-review-gate` SKILL의
  누출 검사 판독 규칙과 같은 부류 — developer가 인용한 패턴 문자열은 실제 비밀값이 아니다.
  육안 확인으로 실제 비밀값 부재를 재확인했다

## fixture-curator 레인 결과 (커밋 `93be34a`, 이 레인 밖 — 포인터만)

fixture 되돌림(C13) 실행 결과와 사유 다섯(BLOCK-1~5)의 정본은
`reports/evidence/m1/1b/fixtures.md`와 그 레인이 쓴
`reports/evidence/m1/1b/golden-manifest.json`이 갖는다 — 이 문서(구현 레인 소유)에
옮겨 적지 않는다. 요지 한 줄: 되돌린 case 0건, 술어 설계는 `fixtures/manifest.yaml`
`m1_contract_binding.predicate_design`에 있으나 실행 도구(`manifest_contract.py`)
확장은 미착수.

**`money-basis-005`(`sameKnownVat` + `ReasonCode.VAT_TREATMENT_MISMATCH`) 는 이미
example 로 고정돼 있다** — `ArithmeticTest`의
`vatTreatment 가 다르거나 Unknown 이면 율 계산이 Unmeasurable 을 낸다 (P-3c, B9)`와
`P-3c sameKnownVat 는 Unknown 곱하기 Unknown 을 통과시키지 않는다`가 "값이 같아도
`UNKNOWN`이면 막는 런타임 전건"을 구체 입력으로 실측한다. `fixtures.md` §4의 그
행이 가리키는 계약 경로(`sameKnownVat`·`ReasonCode.VAT_TREATMENT_MISMATCH`)와
일치한다 — 새 test 를 추가하지 않았다(중복 금지).

### 양성 대조 — clean-tree 게이트가 실제로 in_scope 변경을 잡는지

- cmd: `echo "// probe" >> shared-kernel/src/main/kotlin/bidvector/sharedkernel/Basis.kt && git status --porcelain -- shared-kernel/src/main/kotlin/bidvector/sharedkernel/Basis.kt reports/evidence/m1/1b/scope.md reports/evidence/m1/1b/checklist.md reports/evidence/m1/1b/commands.md reports/evidence/m1/1b/rollback.md && git checkout -- shared-kernel/src/main/kotlin/bidvector/sharedkernel/Basis.kt`
- exit: 0
- 핵심 결과: 의도적 변경 1건이 잡혔고(`M shared-kernel/.../Basis.kt`), 원복 뒤 `git status`가
  다시 비었다 — clean-tree 판정이 공허하게 통과하는 것이 아님을 확인

## Phase 4 — verifier r1 수정 라운드(H-1·H-2·M-1~M-5·low 6) 뒤 acceptance 전건 재실행

최종 HEAD `a9e0448`(커밋 아홉, `checklist.md` 「verifier r1 수정 라운드」 표)에서
`scope.md` `acceptance_commands`(B-0~B-7) 전부를 다시 돌렸다.

- cmd: `git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check --no-daemon)`  (B-0)
- exit: 0 — 262 task 전건 실행(캐시 없음), 격리 worktree
- cmd: `./gradlew --no-build-cache clean check --no-daemon`  (B-1, 작업 트리)
- exit: 0
- cmd: `./gradlew :app:test --tests '*ArchitectureGate*' --no-daemon --no-build-cache`  (B-2)
- exit: 0
- cmd: `./gradlew :build-logic:test --no-daemon --no-build-cache`  (B-3)
- exit: 0
- cmd: `./gradlew :shared-kernel:test --no-daemon --no-build-cache`  (B-4)
- exit: 0 — 클래스별·전체 test 수는 verifier r1 L-2 정정에 따라 하드코딩하지 않는다.
  명령 포인터:
  ```
  grep -oh 'tests="[0-9]*"' shared-kernel/build/test-results/test/TEST-bidvector.sharedkernel.*.xml
  ```
- cmd: `./gradlew qualityBaseline --no-daemon --no-build-cache`  (B-5)
- exit: 0
- cmd: `./gradlew :shared-kernel:domainApiTypeGate :shared-kernel:domainSourceReferenceGate --no-daemon --no-build-cache`  (B-6·B-7)
- exit: 0

**하네스 레인 변경 재확인**: `git log --oneline 66c1ab79af4c5a68145811a9e87008dfdb10da3c..HEAD -- CLAUDE.md .claude/`
— 여전히 없음(`scope.md` 「하네스 레인 변경」 절 그대로 유효).

**clean-tree 재확인**: `git status --short` — 빈 출력. 양성 대조는 위 절이 이미 실측했고
이 라운드가 그 판정을 다시 무효로 만들 변경을 하지 않았다.

**secret 스캔 재확인**: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m1/1b/ shared-kernel/src/ shared-kernel/build.gradle.kts build-logic/src/main/kotlin/bidvector.kotlin-conventions.gradle.kts config/quality/gate-tests.properties`
— 매치는 스캔 명령 문자열을 인용한 이 문서 자신뿐, 실제 비밀값 0.

**verifier r1 finding 별 재현·해소 확인**:
- H-1: `CompileFailureHarnessTest` 의 `6 파생 Money 는 모듈 밖에서 직접 생성할 수 없고 …` test — 통과(음성 「cannot access」·양성 OK)
- H-2: `rollback.md` 실측 절 — 임시 clone diff 0(SHA-256 대조 포함)
- M-1: `ArithmeticTest` 의 `M-1 UNNECESSARY 모드…`·`M-1 임의 mode·scale…` — 통과
- M-2: `CompileFailureHarnessTest` 의 `3-M2`·`5-M2` 변이 test — 통과(변이가 새 단언을 만족시키지 않음을 확인)
- M-3: `ArithmeticTest` 의 `M-3 원소 하나뿐이면…`·`M-3 원소가 하나뿐이어도…` — 통과
- M-4: `scope.md` `OPEN-1B-PROVENANCE-NAME` 행 등재 확인(문서 diff 그대로, 재수정 없음)
- M-5: `scope.md` 역방향 파급 절의 새 grep 명령이 위 명령 결과(11·12)와 일치
- low: `checklist.md` 「verifier r1 low(L-1~L-6) 처리 결과」 표 등재 확인, kotest 시드
  픽업은 이 절 B-4 재실행이 이미 간접 확인(재현 가능한 순서로 43 test 0 실패)

## Phase 5 — decision 15·16 등재·B9 구현 뒤 acceptance 전건 재실행

최종 HEAD `b656b60`(Phase 4 이후 커밋 둘 — `617b9f3` decision 15·16, `b656b60` B9,
`checklist.md` 「verifier r2 이전 후속 지시」 표)에서 `scope.md` `acceptance_commands`
(B-0~B-7) 전부를 다시 돌렸다.

- cmd: `git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check --no-daemon)`  (B-0)
- exit: 0 — 262 task 전건 실행(캐시 없음), 격리 worktree
- cmd: `./gradlew --no-build-cache clean check --no-daemon`  (B-1, 작업 트리)
- exit: 0
- cmd: `./gradlew :app:test --tests '*ArchitectureGate*' --no-daemon --no-build-cache`  (B-2)
- exit: 0
- cmd: `./gradlew :build-logic:test --no-daemon --no-build-cache`  (B-3)
- exit: 0
- cmd: `./gradlew :shared-kernel:test --no-daemon --no-build-cache`  (B-4)
- exit: 0 — 클래스별·전체 test 수는 명령 포인터가 정본(L-2 원칙 유지):
  ```
  grep -oh 'tests="[0-9]*"' shared-kernel/build/test-results/test/TEST-bidvector.sharedkernel.*.xml
  ```
  실측: `ArithmeticTest` 18 · `CompileFailureHarnessTest` 9 · `MoneyTest` 5 · `PolicyTest` 4 ·
  `RateTest` 6 · `RegressionExampleTest` 5, 합 47 — B9(fixture 7 추가, B11 이관 test 셋 순증가)
  뒤의 새 값이다
- cmd: `./gradlew qualityBaseline --no-daemon --no-build-cache`  (B-5)
- exit: 0
- cmd: `./gradlew :shared-kernel:domainApiTypeGate :shared-kernel:domainSourceReferenceGate --no-daemon --no-build-cache`  (B-6·B-7)
- exit: 0

**하네스 레인 변경 재확인**: `git log --oneline 66c1ab79af4c5a68145811a9e87008dfdb10da3c..HEAD -- CLAUDE.md .claude/`
— 여전히 없음.

**clean-tree 재확인**: `git status --short` — 이 evidence 갱신 커밋 자체를 스테이징하기
전에는 `checklist.md` 한 줄만 미스테이징 상태였고(이 명령 자체가 그 증거), 커밋 뒤에는
빈 출력이다.

**secret 스캔 재확인**: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m1/1b/ shared-kernel/src/`
— 매치는 스캔 명령 문자열을 인용한 문서 자신들뿐, 실제 비밀값 0.

**decision 15·16·B9 재현 확인**:
- decision 15: `milestone-1.md` 「Slice 1B-c」·`scope.md` `OPEN-1B-CORPUS`(해소)·
  `capability-map.md` §14.2 두 행 확인
- decision 16: `data-dictionary.md` §5.1·§1.1 취소선, `scope.md` `OPEN-1B-PROVENANCE-NAME`
  취소선 확인
- B9: `CompileFailureHarnessTest` 의 `7 vat 고정 Money…` test 통과(음성 「too many arguments
  for」·양성 OK), `ArithmeticTest` 의 `B9 …` 넷(고정 선언 둘·Unmeasurable property 둘) +
  B11 이관 test(`같은 vat 이면 투찰율이…`) 통과, `scope.md` `OPEN-DIC-04` 행 갱신 확인

## Phase 6 — verifier r2 수정 라운드(H-3·H-4·M-6·low 4) 뒤 acceptance 전건 재실행

최종 HEAD `90eef2c`(Phase 5 이후 커밋 넷 — `6f0a507` H-3, `5290fd5` H-4, `796311e` M-6,
`90eef2c` low 4)에서 `scope.md` `acceptance_commands`(B-0~B-7) 전부를 다시 돌렸다.

- cmd: `git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check --no-daemon)`  (B-0)
- exit: 0 — 262 task 전건 실행(캐시 없음), 격리 worktree
- cmd: `./gradlew --no-build-cache clean check --no-daemon`  (B-1, 작업 트리)
- exit: 0
- cmd: `./gradlew :app:test --tests '*ArchitectureGate*' --no-daemon --no-build-cache`  (B-2)
- exit: 0
- cmd: `./gradlew :build-logic:test --no-daemon --no-build-cache`  (B-3)
- exit: 0
- cmd: `./gradlew :shared-kernel:test --no-daemon --no-build-cache`  (B-4)
- exit: 0 — 명령 포인터(하드코딩 금지, L-2·L-10 원칙):
  ```
  grep -oh 'tests="[0-9]*"' shared-kernel/build/test-results/test/TEST-bidvector.sharedkernel.*.xml
  ```
  실측: `ArithmeticTest` 18 · `CompileFailureHarnessTest` 17 · `MoneyTest` 5 · `PolicyTest` 7 ·
  `RateTest` 6 · `RegressionExampleTest` 5, 합 58(H-3 의 fixture 8·9·10 각 2 test ·
  M-6 의 `PolicyTest` 3 test · L-7 의 fixture 6·7 변이 test 2 로 47 → 58)
- cmd: `./gradlew qualityBaseline --no-daemon --no-build-cache`  (B-5)
- exit: 0
- cmd: `./gradlew :shared-kernel:domainApiTypeGate :shared-kernel:domainSourceReferenceGate --no-daemon --no-build-cache`  (B-6·B-7)
- exit: 0

**하네스 레인 변경 재확인**: `git log --oneline 66c1ab79af4c5a68145811a9e87008dfdb10da3c..HEAD -- CLAUDE.md .claude/`
— 여전히 없음.

**clean-tree 재확인**: `git status --short` — 빈 출력.

**secret 스캔 재확인**: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m1/1b/ shared-kernel/src/ shared-kernel/build.gradle.kts config/quality/gate-tests.properties`
— 매치는 스캔 명령 문자열을 인용한 문서 자신들뿐, 실제 비밀값 0.

**H-3·H-4·M-6·low 4 재현 확인**:
- H-3: `CompileFailureHarnessTest` 의 `8`·`9`·`10` 번 test(음성·양성·변이 각 쌍) 통과 —
  `Derived`·`Measurement.Measured` 양쪽 `internal constructor`+`@ConsistentCopyVisibility` 확인
- H-4: `rollback.md` 를 실제로 임시 clone(별도)에서 실행 — 파생 배열 11개 원소,
  확인 지점 1~4 전부 통과(문서의 「실측」 절이 그 결과를 갖는다)
- M-6: `PolicyTest` 의 `M-6` 셋(예제·property 둘) 통과, `RoundingPolicy(-2, …)` 가
  `IllegalArgumentException` 을 던짐을 확인
- low: `CompileFailureHarnessTest` 의 `6-M2`·`7-M2` 변이 test, `checklist.md` 의 L-9·L-10
  등재, 이 문서 Phase 3 절의 「31건」 정정 확인

## Phase 7 — Codex 1차 리뷰 수정(#1~#4) 뒤 acceptance 전건 재실행

최종 HEAD `f6363b1`(Codex 1차 finding 넷 — `0ffd32c` #1, `480eb57` #2, `aa64f02` #3,
`f6363b1` #4(등재만))에서 `scope.md` `acceptance_commands`(B-0~B-7) 전부를 다시 돌렸다.

- cmd: `git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check --no-daemon)`  (B-0)
- exit: 0 — 262 task 전건 실행(캐시 없음), 격리 worktree
- cmd: `./gradlew --no-build-cache clean check --no-daemon`  (B-1, 작업 트리)
- exit: 0
- cmd: `./gradlew :app:test --tests '*ArchitectureGate*' --no-daemon --no-build-cache`  (B-2)
- exit: 0
- cmd: `./gradlew :build-logic:test --no-daemon --no-build-cache`  (B-3)
- exit: 0
- cmd: `./gradlew :shared-kernel:test --no-daemon --no-build-cache`  (B-4)
- exit: 0 — 명령 포인터(하드코딩 금지, L-2·L-10 원칙):
  ```
  grep -oh 'tests="[0-9]*"' shared-kernel/build/test-results/test/TEST-bidvector.sharedkernel.*.xml
  ```
  실측: `ArithmeticTest` 20 · `CompileFailureHarnessTest` 17 · `MoneyTest` 9 · `PolicyTest` 7 ·
  `RateTest` 6 · `RegressionExampleTest` 5 · `UndeclaredProvenanceTest`(신설) 8, 합 72
  (Codex #1 이 새 class 로 분리한 8 + #2 의 MoneyTest 신규 4 + #3 의 ArithmeticTest 신규 2 로
  58 → 72)
- cmd: `./gradlew qualityBaseline --no-daemon --no-build-cache`  (B-5)
- exit: 0
- cmd: `./gradlew :shared-kernel:domainApiTypeGate :shared-kernel:domainSourceReferenceGate --no-daemon --no-build-cache`  (B-6·B-7)
- exit: 0

**하네스 레인 변경 재확인**: `git log --oneline 66c1ab79af4c5a68145811a9e87008dfdb10da3c..HEAD -- CLAUDE.md .claude/`
— 여전히 없음.

**clean-tree 재확인**: `git status --short` — 빈 출력.

**fixtures/** 미접촉 재확인**: `git diff --stat fd9f621..HEAD -- fixtures/ reports/evidence/m1/1b/golden-manifest.json reports/evidence/m1/1b/fixtures.md`
— 빈 출력(이번 라운드가 그 경로들을 하나도 건드리지 않았다).

**secret 스캔 재확인**: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m1/1b/ shared-kernel/src/`
— 매치는 스캔 명령 문자열을 인용한 문서 자신들뿐, 실제 비밀값 0.

**Codex 1차 finding 별 재현 확인**:
- #1: `UndeclaredProvenanceTest` 전 8 test 통과(times→roundedWith·assessmentRateAgainst·
  awardRateAgainst·bidRateAgainst·sumOfBaseAmounts 전 경로에서 `Undeclared` 거부 확인)
- #2: `MoneyTest` 의 `Codex2` 넷(같은 vat 비교·다른 vat 거부·UNKNOWN 대 UNKNOWN 거부·property)
  통과, 여섯 타입에 `Comparable` 미구현 확인(컴파일 자체가 증거)
- #3: `ArithmeticTest` 의 `Codex3` 둘 + 정정된 `P-2b` property 통과, `RoundedBidAmount`
  가 소수 하한 미달을 `Unmeasurable(ROUNDED_BELOW_FLOOR)` 로 냄을 확인
- #4: `scope.md` `OPEN-1B-STABLE-FACT-REF` 신설 등재 확인, 코드 변경 없음(diff 그대로)

## Phase 8 — Codex 1차 finding #4 수정 라운드(decision 17 구현) 뒤 acceptance 전건 재실행

최종 HEAD `50e77d9`(decision 17 구현)에서 `scope.md` `acceptance_commands`(B-0~B-7) 전부를
다시 돌렸다.

- cmd: `./gradlew :shared-kernel:compileKotlin --no-daemon --no-build-cache` (RED 확인 —
  `Derivation.kt`의 `inputs` 필드만 먼저 제거한 중간 상태)
- exit: 1 — `MoneyArithmetic.kt`의 `measured`(`DerivationRecord(inputs = …)` 호출 지점,
  `No parameter with name 'inputs' found`)와 `asRate`(`DerivationRecord(inputs, …)` 호출
  지점, `Too many arguments for 'constructor(policyVersion: PolicyVersion): DerivationRecord'`)
  **(verifier r4 L-5 정정 — 이전 문면의 `:114`·`:175` 줄 번호 인용은 이후 커밋으로 낡아
  인용문 형태로 바꿨다)**
- cmd: `git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check --no-daemon)`  (B-0)
- exit: 0 — 262 task 전건 실행(캐시 없음), 격리 worktree, `git worktree remove` 로 정리 완료
- cmd: `./gradlew --no-build-cache clean check --no-daemon`  (B-1, 작업 트리)
- exit: 0
- cmd: `./gradlew :app:test --tests '*ArchitectureGate*' --no-daemon --no-build-cache`  (B-2)
- exit: 0
- cmd: `./gradlew :build-logic:test --no-daemon --no-build-cache`  (B-3)
- exit: 0
- cmd: `./gradlew :shared-kernel:test --no-daemon --no-build-cache`  (B-4)
- exit: 0 — 명령 포인터(하드코딩 금지, L-2·L-10 원칙):
  ```
  grep -oh 'tests="[0-9]*"' shared-kernel/build/test-results/test/TEST-bidvector.sharedkernel.*.xml
  ```
  실측: `ArithmeticTest` 20 · `CompileFailureHarnessTest` 17 · `MoneyTest` 9 · `PolicyTest` 7 ·
  `RateTest` 6 · `RegressionExampleTest` 5 · `UndeclaredProvenanceTest` 8, 합 72 — Phase 7의
  72와 **동일**(이 라운드는 test를 하나 고쳐 썼을 뿐 추가·삭제하지 않았다 — B11 test 하나가
  policyVersion 대조로 바뀌었다)
- cmd: `./gradlew qualityBaseline --no-daemon --no-build-cache`  (B-5)
- exit: 0
- cmd: `./gradlew :shared-kernel:domainApiTypeGate :shared-kernel:domainSourceReferenceGate --no-daemon --no-build-cache`  (B-6·B-7)
- exit: 0

**하네스 레인 변경 재확인**: `git log --oneline 66c1ab79af4c5a68145811a9e87008dfdb10da3c..HEAD -- CLAUDE.md .claude/`
— `551b603`·`f95feac` 둘(이번 세션 이전 커밋) 외 신규 없음. **verifier r4 L-4 정정**: 이
문서가 이전에 「`scope.md` 절에 이미 등재됨」이라 적었던 시점에는 그 절이 실제로는
「없음」으로 낡아 있어 거짓 참조였다 — `scope.md` 「하네스 레인 변경」 절이 이제 이 둘을
SHA·경로·목적 표로 정식 등재했다(이번 라운드 수정 사항).

**clean-tree 재확인**: `git status --short` — 빈 출력.

**fixtures/** 미접촉 재확인**: `git diff --stat fd9f621..HEAD -- fixtures/ reports/evidence/m1/1b/golden-manifest.json reports/evidence/m1/1b/fixtures.md`
— 빈 출력.

**secret 스캔 재확인**: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m1/1b/ shared-kernel/src/`
— 매치는 스캔 명령 문자열을 인용한 문서 자신들뿐, 실제 비밀값 0.

**Codex 1차 #4 재현 확인**: `grep -rn "\.inputs\b" shared-kernel/src` — 0건(`DerivationRecord.inputs`
참조가 코드 어디에도 남지 않음). `AmountRecord` 는 `Money.export()`·`MoneyTest` 소비처가
남아 제거하지 않음(`grep -rn "AmountRecord" shared-kernel/src` — main 2건·test 1건).
`ArithmeticTest`의 개정된 B11 test(`같은 vat 이면 투찰율이 정상 산출되고 파생값은 계산
정책 version 을 되짚는다`) 통과 — `derivedFrom.policyVersion`이 `roundedWith`·
`bidRateAgainst` 양쪽에서 입력 정책의 version 과 같음을 확인.

## Phase 9 — verifier r4 H-1·M-1 수정 라운드 뒤 acceptance 전건 재실행

최종 HEAD `f750c60`(H-1·M-1 구현)에서 `scope.md` `acceptance_commands`(B-0~B-7) 전부를
다시 돌렸다.

- cmd: `git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check --no-daemon)`  (B-0)
- exit: 0 — 262 task 전건 실행(캐시 없음), 격리 worktree, `git worktree remove` 로 정리 완료
- cmd: `./gradlew --no-build-cache clean check --no-daemon`  (B-1, 작업 트리)
- exit: 0
- cmd: `./gradlew :app:test --tests '*ArchitectureGate*' --no-daemon --no-build-cache`  (B-2)
- exit: 0
- cmd: `./gradlew :build-logic:test --no-daemon --no-build-cache`  (B-3)
- exit: 0
- cmd: `./gradlew :shared-kernel:test --no-daemon --no-build-cache`  (B-4)
- exit: 0 — 명령 포인터(하드코딩 금지, L-2·L-10 원칙):
  ```
  grep -oh 'tests="[0-9]*"' shared-kernel/build/test-results/test/TEST-bidvector.sharedkernel.*.xml
  ```
  실측: `ArithmeticTest` 20 · `CompileFailureHarnessTest` 19 · `MoneyTest` 12 · `PolicyTest` 7 ·
  `RateTest` 6 · `RegressionExampleTest` 5 · `UndeclaredProvenanceTest` 8, 합 77 — Phase 8의
  72에서 **+5**: `CompileFailureHarnessTest` +2(fixture 11 negative/positive 쌍 test 1 +
  mutant test 1) · `MoneyTest` +3(옛 공허 test 1 을 H-1 test 1 로 대체해 순증감 0, M-1 test
  3 신설로 +3)
- cmd: `./gradlew qualityBaseline --no-daemon --no-build-cache`  (B-5)
- exit: 0
- cmd: `./gradlew :shared-kernel:domainApiTypeGate :shared-kernel:domainSourceReferenceGate --no-daemon --no-build-cache`  (B-6·B-7)
- exit: 0

**하네스 레인 변경 재확인**: `git log --oneline 66c1ab79af4c5a68145811a9e87008dfdb10da3c..HEAD -- CLAUDE.md .claude/`
— `551b603`·`f95feac` 둘, 신규 없음(`scope.md` 「하네스 레인 변경」 절이 이번 라운드에
SHA·경로·목적 표로 정식 등재됐다 — verifier r4 L-4).

**clean-tree 재확인**: `git status --short` — 빈 출력. **양성 대조**: `reports/evidence/m1/1b/.verifier-r5-probe`
를 만들어 `git status --porcelain -- shared-kernel reports/evidence/m1/1b` 가 `??` 항목을
잡는 것을 확인한 뒤 삭제, 원복 확인(경로를 변수 하나로 넘기지 않았다).

**fixtures/** 미접촉 재확인**: `git diff --stat fd9f621..HEAD -- fixtures/ reports/evidence/m1/1b/golden-manifest.json reports/evidence/m1/1b/fixtures.md`
— 빈 출력.

**secret 스캔 재확인**: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m1/1b/ shared-kernel/src/`
— 매치는 스캔 명령 문자열을 인용한 문서 자신들뿐, 실제 비밀값 0.

**verifier r4 H-1 재현 확인**: 회귀 재현(구현 전 상태로 되돌려 fixture 11 을 돌림) —
`assertNegativeFails` 가 `expected:<COMPILATION_ERROR> but was:<OK>` 로 실패해 회귀가 실제로
있었음을 실측(로컬 되돌림, `git stash`/`git checkout` 으로 임시 재현 후 즉시 원복 — 최종
`git diff` 빈 출력 확인). 수정 뒤 `CompileFailureHarnessTest` fixture 11(negative·positive·
mutant) 3 test 전부 GREEN.

**verifier r4 M-1 mutant 실측**: `compareSameType` 의 provenance 분기(`!hasDeclaredProvenance(...)
-> ...`)를 `false ->` 로 죽인 로컬 mutant 로 `:shared-kernel:test --tests '*MoneyTest*'` 를
돌려 M-1 test 셋(양쪽 Undeclared·한쪽만 Undeclared·순서 확인) 전부 실패(`Known`/
`VAT_TREATMENT_MISMATCH` 를 냄)를 확인한 뒤 원복 — 원복 뒤 GREEN 재확인, `git diff` 빈 출력.
