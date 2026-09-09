# 4D-1 정책 데이터 값 표 — **착수 placeholder, 승인 대기 (`OPEN-4D-POLICY-VALUES`)**

> D-4D-7(scope.md)이 구조만 고정했다 — 값 자체는 이 문서의 승인 없이 확정되지 않는다.
> 4A `policy-values.md`·4E `policy-values.md`와 같은 표 형식(층·근거·범위)을 유지한다.
> `MlCallPolicyData`(`adapters/src/main/kotlin/bidvector/adapters/ml/MlCallPolicyData.kt`)의
> `ML_CALL_POLICY.source`가 이 문서를 가리킨다.

---

## 0. 층 표기와 출처

| 층 | 뜻 | 이 문서에서의 출처 |
| --- | --- | --- |
| `authoritative` | 공식 문서·명세 문면 | **해당 없음** — ML 호출 timeout·재시도·breaker 값은 조달청 공식 문서가 정하는 값이 아니다 |
| `legacy-behavior` | 기존 Python이 실제로 하는 것 | **해당 없음** — ADR 0010 머리: 「legacy 에는 gRPC 경로가 없다」, 조사 대상이 아니다 |
| 근거 차용(보수적 상한) | 다른 승인된 정책의 보수적 상한을 그대로 가져옴, 실측 아님 | §1·§2 전체 |
| 운영자 승인 | 값 자체를 운영자가 정함 | **미승인 — 이 slice 는 승인을 받지 않는다**(D-4D-7 「착수 가정」) |

---

## 1. deadline·재시도·백오프 (scope.md ⑧, D-4D-7)

| 필드 | 값 | 근거 |
| --- | --- | --- |
| `deadlineCeiling` | 5초 | 근거 차용 — 3C `EXTRACTION_POLICY.callTimeout`(30초)·`httpRequestTimeout`(45초)보다 짧게 잡았다. ML 추론은 LLM 청크 호출보다 짧은 예산을 쓴다고 보수적으로 가정했다(실측 없음) |
| `maxAttempts` | 3 | 근거 차용 — M2/2D `contract-policy.properties`의 `retry.sample.max-attempts=3`(그 파일 주석이 "운영 재시도 정책이 아니다 — 정본은 M4 4D"로 이 값을 인계했다). 표본 상한을 그대로 운영값 착수 placeholder로 가져왔다 |
| `backoff` | `200ms`, `800ms` | 근거 차용 — 3B `KonepsResilientCall`의 지수 백오프 배열 관례를 값만 좁혀 옮겼다. 4배 성장 곡선(200→800)은 재시도 둘 사이의 간격만 두면 되는 `maxAttempts=3`(재시도는 최대 2회)에 맞춘 최소 배열이다 |

## 2. circuit breaker (scope.md ⑧, D-4D-7)

| 필드 | 값 | 근거 |
| --- | --- | --- |
| `breakerFailureRateThresholdPercent` | 50 | 근거 차용 — 3C `EXTRACTION_POLICY.breakerFailureRateThresholdPercent`와 동일값 |
| `breakerSlidingWindowSize` | 10 | 근거 차용 — 3C `EXTRACTION_POLICY.breakerSlidingWindowSize`와 동일값(`minimumNumberOfCalls`도 같은 크기로 맞춘다, 3C 실측 교훈 — 기본값 100은 창 크기보다 커서 breaker가 열리지 않는다) |
| `breakerWaitDurationInOpenState` | 30초 | 근거 차용 — 3C `EXTRACTION_POLICY.breakerWaitDurationInOpenState`와 동일값 |

## 3. `featureSchemaVersion`(scope.md ①, D-2)

| 필드 | 값 | 근거 |
| --- | --- | --- |
| `featureSchemaVersion` | `bidvector.ml.v1` | 계약 패키지 식별자(`contracts/proto/bidvector/ml/v1/*.proto` 의 `package bidvector.ml.v1`)를 그대로 썼다 — ADR 0010 D-7 "패키지가 version을 나른다"의 문면을 값으로 옮긴 것. 별도 schema 버전 축이 승인 문서에 없어 임의로 세분화하지 않았다(알려진 제한 — `docs/discovery/data-dictionary.md`가 이 축을 더 세분화하기 전까지는 패키지 식별자가 유일한 값) |

---

## 4. 되돌림 경로

이 값들이 재검토돼야 하면 이 표를 갱신하고 §5 `change_history`를 추가한다. `ML_CALL_POLICY`
(`MlCallPolicyData.kt`)의 값·주석은 바꾸지 않고 이 문서만 먼저 바꾸면 코드와 evidence가
어긋난다는 점에 주의(정본은 이 문서, 코드는 이 문서를 인용).

**실측 갱신 경로**: `OPEN-M2-DEADLINE-VALUES`(M5 5E 실측 → M4 4D 정책 version 갱신) —
이 slice의 값은 실측이 나오기 전까지 그대로 「보수적 + 관측 갱신」 문면으로 남는다
(ADR 0010 D-1).

---

## 5. `change_history`

- **2026-09-10 최초 등재** — 구현 레인이 §1~§3 값을 착수 placeholder로 심음. 사용자 승인
  대기(`OPEN-4D-POLICY-VALUES`, 종결 승인 시 확정 또는 개정).
