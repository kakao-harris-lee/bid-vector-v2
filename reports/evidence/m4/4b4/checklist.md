# M4/4B-4 체크리스트 — 우회 ↔ 코드 ↔ 실측

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
| 1 | Absent → 0 | 타입(`ScoreFact` sealed) | `PriorityCompositionTest` 「표본2」 — 재정규화 결과(0.682705)가 0 대입 계산(0.591700)과 다름을 단언, `PriorityCompositionPropertyTest` 「전부 Present 면 재정규화가 항등」 |
| 2 | 합 1.0001·0.9999 정책 | `PriorityPolicyData.init` | `PriorityPolicyDataTest`(둘 다 `shouldThrow`) |
| 3 | `Component.Probability` 추가 | 소진 `when`(`PriorityInputs.factFor`) 컴파일 실패 | 컴파일 자체(정적) + `ComponentExhaustiveTest`(값 고정) |
| 4 | 차원 다른 벡터 | `SemanticMatch.of` sealed | `SemanticMatchTest`(2차원 vs 3차원 → `DimensionMismatch`) |
| 5 | offset 0.5(범위 밖) | `SemanticMatch.of` sealed | `SemanticMatchTest`(0.21 → `OffsetOutOfRange`) |
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

## 6. 사용자 승인

**미승인 — 사용자 승인 대기.** 이 slice는 verifier ready-for-review 판정 뒤 사용자
승인으로 종결된다(CLAUDE.md 운영자 지시 2026-09-04, Codex 심판 제외).
