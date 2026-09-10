# M4/4B-4 체크리스트 — 우회 ↔ 코드 ↔ 실측

## 사용자 승인 — 2026-09-10

**slice 4B-4 종결 승인.** 승인 셋:

1. **slice 종결** — 이 slice의 산출물(`bidvector.decision.priority` 패키지 —
   `Component`·`ScoreFact`·`PriorityInputs`·`PriorityPolicyData`+`PRIORITY_POLICY`·
   `composePriority`+`PriorityOutcome`·`UnitVector`·`SemanticMatch`+`MatchOutcome`,
   `gate.tests.decision` 등재)을 최종 형태로 승인한다.
2. **정책 값 승인(D-4B4-4, `OPEN-4B4-POLICY-VALUES` 종결)** — 재정규화 가중치 다섯
   (`Match 0.3834`·`Urgency 0.2333`·`Competitiveness 0.1333`·`BudgetCapture 0.1000`·
   `ExpectedMargin 0.1500`)·load penalty(`ratioWeight 0.18`·`workloadWeight 0.12`)·
   complexity penalty(`threshold 0.55`·`slope 0.18`·`cap 0.12`)·categoryOffset
   (`±0.20`)·`normEpsilon 0.0001`. 착수 placeholder였던 값 자체가 이제 운영 정책값이다.
   정본은 `reports/evidence/m4/4b4/policy-values.md`(이 승인과 함께 갱신).
3. **병합 진행** — 병합은 팀장이 이 등재 커밋 뒤에 실행한다(대상 `m4/2026-09-08`,
   `scope.md` 「병합 결정」 절). 이 slice(구현 레인)는 병합·push를 하지 않는다.

**승인의 근거 — verifier r1 `ready-for-review`.** blocker/high 0, medium 2(F-1·F-2)·
low 5(F-3~F-7, 장부층 3 포함)를 한 커밋(`658e693`)으로 일괄 반영하고 재검증 rollback
①~⑤·S-0을 최종 head에서 재실측했다(`658e693`·`b595f61`, `_workspace/m4-4b4/
05_verifier_fix_report.md`).

**재작업 카운터: 0/5 확정**(상한 5, 여유 5) — medium·low 일괄 반영 커밋과 이 종결
등재 커밋은 라운드로 세지 않는다(2026-09-02 운영자 채택 「장부층·low는 등재만 하고
라운드를 막지 않는다」).

**다음**: 브랜치 `m4-4b4/2026-09-10`은 `m4/2026-09-08`에 팀장이 병합한다(`scope.md`
「병합 결정」 절). 성분 산출·port·workflow 조합 배선은 4B-5, corpus 승격은
`OPEN-4B4-CORPUS`(병합 뒤 curator)로 이어진다.

## 1. 위협 모델 대응표 (scope.md 「위협 모델 — 4B-4 고유 경계」)

### 방어한다 (a)~(g)

| # | 방어 대상 | 막는 코드 | 실측 test |
| --- | --- | --- | --- |
| (a) | 부재 성분의 sentinel 0 | `ScoreFact`(`Present`/`Absent`), `composePriority`는 `Present`만 순회 | `PriorityCompositionTest` 「표본2」(≠0 대입 계산 단언) |
| (b) | 가중치 합≠1·전사상 누락 정책 | `PriorityPolicyData.init`(전사상 + scale-4 합 비교) | `PriorityPolicyDataTest` (합 0.9999/1.0001/전사상 누락 각각 생성 실패) |
| (c) | 확률 축의 몰래 유입 | `Component` enum 다섯 고정 + `factFor`의 소진 `when` | `ComponentExhaustiveTest`(값으로 재는 canary) + 컴파일 자체(소진 `when`이 값 추가 시 컴파일 실패) |
| (d) | 비정규·차원 불일치 벡터의 코사인 | `UnitVector.init`(norm·차원 불변식) + `SemanticMatch.of`(차원 비교 sealed) | `UnitVectorTest`(차원 0·norm 불변식) + `SemanticMatchTest`(`DimensionMismatch`) |
| (e) | offset 무한·조용한 clamp | `SemanticMatch.of`가 정책 범위 밖을 `OffsetOutOfRange`로(clamp 아님) | `SemanticMatchTest`(0.21 → `OffsetOutOfRange`, ±0.20 경계는 포함) |
| (f) | 조합 결과에서 어느 성분이 빠졌나가 사라짐 | `PriorityOutcome.Composed.usedComponents`/`droppedComponents` | `PriorityCompositionTest` 전 표본(각 case 의 `usedComponents`/`droppedComponents` 단언) |
| (g) | `match` 없이 priority 가 나오는 것 | `composePriority`가 `match` Absent 를 최우선 검사(D-4B4-2) | `PriorityCompositionTest`(match Absent → 즉시 `Unavailable`, 전부 Absent 케이스) |

### 우회 후보 (1)~(9)

| # | 우회 | 차단 | 실측 test |
| --- | --- | --- | --- |
| 1 | Absent → 0 | 타입(`ScoreFact` sealed) | `PriorityCompositionTest` 「표본2」 — 재정규화 결과(0.682705)가 0 대입 계산(0.591700)과 다름을 단언, `PriorityCompositionPropertyTest` 「전부 Present 면 재정규화가 항등」, `PriorityCompositionExhaustiveTest`(F-3 — 부분집합 16 전수 손계산 대조) |
| 2 | 합 1.0001·0.9999 정책 | `PriorityPolicyData.init` | `PriorityPolicyDataTest`(둘 다 `shouldThrow`) |
| 3 | `Component.Probability` 추가 | 소진 `when`(`PriorityInputs.factFor`) 컴파일 실패 | 컴파일 자체(정적) + `ComponentExhaustiveTest`(값 고정) |
| 4 | 차원 다른 벡터 | `SemanticMatch.of` sealed | `SemanticMatchTest`(2차원 vs 3차원 → `DimensionMismatch`) |
| 5 | offset 0.5(범위 밖) | `SemanticMatch.of` sealed | `SemanticMatchTest`(0.21 → `OffsetOutOfRange`, F-4 — `-0.21` 하한도 `OffsetOutOfRange`) |
| 6 | 전부 Absent | `match` 필수 → `Unavailable`(0 아님) | `PriorityCompositionTest`(「전부 Absent 면 match 부재만으로 Unavailable」) |
| 7 | 가중치 순회 순서 의존 | property(셔플) — `Component.entries` 기반 순회, `weights` 맵 순회 아님 | `PriorityCompositionPropertyTest`(가중치 맵 셔플 1M seed, `checkAll`) |
| 8 | penalty 부재를 0 penalty 로 | `appliedPenalties`는 `Present` 인 항만 키를 갖는다 | `PriorityCompositionTest`(「loadRatio 만 Absent」·「penalty 입력 전부 Absent → 빈 맵」) |
| 9 | clamp 경계 | `[0,1]` 밖 입력 불가(`UnitScore.init`), penalty 초과 → `clamp01` | `PriorityCompositionTest`(「penalty 가 가중합을 넘으면 0」·「전부 1 이면 priority 1」), `PriorityCompositionPropertyTest`(「Composed 결과의 priority 는 항상 0 이상 1 이하」) |

## 2. 값 획득 축 (설계 검토 (2) — 전부 「연다」)

`composePriority`·`SemanticMatch.of`는 순수 함수이고 `PriorityInputs`·`UnitVector`의
생성자는 공개다(입력값이라 통로 보호 대상이 아니다 — 1D `UnitScore`·4B-1 `LadderInput`
관례). 「닫는다」축이 없다 — 이 slice에 `Verdict`류의 위조 방지 대상이 없다. 유일한
방어는 **값 타입의 생성 불변식**(`PriorityPolicyData`·`UnitVector`)과 **판정 sealed**
(`PriorityOutcome`·`MatchOutcome`)이고 둘 다 위 표에서 실측했다.

## 3. init ↔ 판정층 짝 (4D-1 G-1 교훈 — init 이 던질 수 있는 조건은 판정층이 먼저 sealed 로 낸다)

| 값 타입 `init` 조건 | 그 조건에 닿기 **전에** 먼저 잡는 판정층 | 짝 확인 |
| --- | --- | --- |
| `UnitScore`(1D 기존 타입, [0,1]) | `composePriority`의 `clamp01`이 항상 `[0,1]` 안 값만 `UnitScore(...)`에 넣는다 — `clamp01`을 거치지 않고 `UnitScore` 생성자에 직접 범위 밖 값이 갈 경로가 `composePriority`·`SemanticMatch.of` 안에 없다 | `PriorityCompositionTest`(클램프 경계 둘) + `SemanticMatchTest`(코사인 극값 clamp) — `UnitScore.init`의 `require`가 실제로 던지는 경로가 두 판정 함수 코드 안에 없음을 코드 리뷰로 확인(정적) |
| `UnitVector.init`(차원>0·norm 1±ε) | **짝이 없다 — 의도적.** `UnitVector`는 `PriorityInputs`·`SemanticMatch.of`의 입력이지 두 함수의 **출력**이 아니다. 호출부(4B-5·adapters)가 임베딩을 이 생성자에 그대로 넣으므로 「판정층이 먼저 거른다」구도가 성립하지 않는다 — 이 값의 유효성은 호출부 책임이고, 그 사실 자체가 D-4B4-3에서 `require`를 값 타입 init 에 남긴 근거다(스코프 「require 는 값 타입 init 에만(정책·UnitVector·UnitScore)」) | 해당 없음 — 설계상 짝이 없는 자리임을 이 표가 값으로 남긴다 |
| `PriorityPolicyData.init`(전사상·합=1 등) | 정책 인스턴스는 배선 시점에 한 번만 만들어진다(`PRIORITY_POLICY`) — `composePriority`는 이미 만들어진 정책을 받기만 하고 정책을 새로 짓지 않는다 | `PriorityPolicyDataTest`(출하 인스턴스 직접 읽기) |
| `LoadPenaltyPolicy`·`ComplexityPenaltyPolicy.init` | 위와 같음 — `PriorityPolicyData`의 구성 요소, `composePriority`가 짓지 않는다 | `PriorityPolicyDataTest` |
| `UnitScore`(`loadRatio` 경로, F-2 신설) | `loadRatio`가 `ScoreFact<BigDecimal>`(하한 없음)에서 `ScoreFact<UnitScore>`로 좁혀져, `penaltiesOf`가 읽는 시점에 이미 `[0,1]` 이 보장된다 — `composePriority`가 `loadRatio` 값을 만들지 않고 호출부(`PriorityInputs` 생성자)가 이미 `UnitScore.init`을 통과한 값만 넘긴다. 음수·>1 은 **`composePriority` 호출 전, `PriorityInputs` 구성 시점**에 거부된다(판정 함수 안이 아니라 입력 조립 시점의 짝) | `PriorityCompositionTest`(F-2 — 음수·>1 각각 `shouldThrow`) |

**결론**: 4D-1 G-1 이 방어한 패턴("판정 함수가 값 타입 init 의 예외를 그대로 새게 둔다")은
`UnitScore`·정책 값 타입 넷에서 재현 경로가 없다(정적 코드 검토 — `composePriority`·
`SemanticMatch.of` 어디에도 `try`/`catch`가 없고, `UnitScore`·정책 생성자 호출 직전 값이
전부 그 생성자의 불변식을 만족하도록 코드가 짜여 있다). `UnitVector`는 예외적으로 짝이
없는 것이 **의도**다(입력 검증 자리이지 판정 함수의 출력 경로가 아니다).

## 4. D-4B4-3 실측 (착수 가정 확정)

`api-type-policy.properties`의 `api.forbidden.types`(`kotlin.DoubleArray` 포함)가
domain 공개 API 를 막는지 `./gradlew :decision:check`(전체는 `clean check`)로 실측 —
`commands.md` 「S-1/S-7」참고. 결론: **막는다**, 그리고 생성자 자체의 `internal`/`private`
표기로는 우회되지 않는다(`build-logic/.../PublicApiTypes.kt`의
`classOrObjectTargets`가 주 생성자를 항상 클래스 자신의 가시성으로 판정 — 생성자 자신의
modifier 는 읽지 않는다). `UnitVector`는 좌표 타입을 `List<BigDecimal>`로 확정했다.

## 5. 알려진 제한

- 가중치·penalty·offset·`normEpsilon` 값은 legacy-behavior 재정규화 placeholder —
  승인 대기(`OPEN-4B4-POLICY-VALUES`, `policy-values.md`).
- `similarity`는 성분이 아니다(D-4B4-5, legacy priority 가중합에 없다) — 4B-5가 필요하면
  정책 version 으로 성분을 더한다.
- 성분 값의 **산출**(마감→urgency 등)은 이 slice 밖(4B-5).
- 조합·코사인 전수 표의 corpus 승격은 병합 뒤 curator 소관(`OPEN-4B4-CORPUS`) —
  이 slice 의 test 값은 손계산 표본이지 authoritative corpus 가 아니다.
- `PriorityCompositionTest`의 나눗셈 낀 표본(표본2)은 정확한 BigDecimal 비교가 아니라
  epsilon(1e-6) 비교다(`closeTo`) — `MathContext(20)` 실제 정밀도보다 훨씬 느슨하지만
  산식 오류(가중치 누락·재정규화 생략 등)는 충분히 잡는다.

## 6. verifier r1 finding 반영 (`_workspace/m4-4b4/04_verifier_report.md`, 재작업 카운터 0/5 유지)

| finding | severity | 반영 |
| --- | --- | --- |
| F-1 | medium | `PriorityPolicyDataTest`에 출하 `PRIORITY_POLICY` 가중치를 legacy 재정규화 산식(÷(1-확률가중치) → scale4 반올림 → 잔차를 `Match`에 흡수)으로 **독립 재계산**해 대조하는 test 추가(`renormalizedLegacyWeights()`, 리터럴 복제 아님). verifier 변이(`Match` 0.3834→0.5834·`Urgency` 0.2333→0.0333)를 재현해 이제 `AssertionFailedError`로 떨어짐을 `commands.md`에 기록 |
| F-2 | medium | `PriorityInputs.loadRatio`를 `ScoreFact<BigDecimal>`(하한 없음)에서 `ScoreFact<UnitScore>`([0,1])로 좁힘. `penaltiesOf`의 `loadRatio` 항을 `it.value * ratioWeight`로 갱신. 음수·>1 `loadRatio`가 `PriorityInputs` 구성 시점에 `IllegalArgumentException`으로 거부됨을 test 로 고정(위 §3 표 신설 행) |
| F-3 | low | `PriorityCompositionExhaustiveTest` 신설 — `match` 상시 Present, 나머지 넷의 **부분집합 16개 전부**를 순회해 재정규화 결과를 독립 손계산과 대조. `PriorityCompositionPropertyTest`의 「결과 ∈[0,1]」 생성기를 `Arb.list`+`getOrNull` 접미사 패턴에서 `Arb.subsequence(optionalComponents)`로 교체해 16 부분집합을 고르게 뽑는다 |
| F-4 | low | `SemanticMatchTest`에 offset 하한(`-0.21` → `OffsetOutOfRange`) test 추가 |
| F-5 | low | `rollback.md`의 대상 파일 수를 재계산해 `A 21(main 8·test 8·evidence 5)`로 정정(F-1~F-4 반영으로 test 파일이 7→8로 늘어 — `PriorityCompositionExhaustiveTest.kt` 신설) — 목록은 `git diff --name-status`로 기계 재산출 |
| F-6 | low | `commands.md`의 S-0을 이번 라운드의 최종 head에서 재실행하고 기록 |
| F-7 | low | `commands.md`의 `PriorityCompositionPropertyTest.kt:96:9` 인용을 컴파일러 진단 문구 자체(따옴표 인용)로 교체 — 그 줄은 이미 다른 내용이라 좌표가 낡아 있었다 |

## 7. 사용자 승인

**승인 2026-09-10 — 정본은 파일 맨 위 「사용자 승인 — 2026-09-10」 절.** 이 절은
verifier ready-for-review 판정 뒤 사용자 승인으로 slice가 종결됐다는 사실만 가리킨다
(CLAUDE.md 운영자 지시 2026-09-04, Codex 심판 제외 — 코드 slice는 verifier
ready-for-review + 사용자 승인으로 완료 조건을 읽는다).
