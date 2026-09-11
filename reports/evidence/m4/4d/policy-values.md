# 4D-1 정책 데이터 값 표 — **사용자 승인 2026-09-10**

> **승인일: 2026-09-10.** 승인은 slice 4D-1 종결 승인과 같은 결정에 실렸다 — 정본은
> `reports/evidence/m4/4d/checklist.md` 「사용자 승인」 절이다. 4A `policy-values.md`·4E
> `policy-values.md`와 같은 표 형식(층·근거·범위)을 유지한다.
> `MlCallPolicyData`(`adapters/src/main/kotlin/bidvector/adapters/ml/MlCallPolicyData.kt`)의
> `ML_CALL_POLICY.source`가 이 문서를 가리킨다.
>
> **착수 시(2026-09-10)에는 D-4D-7(scope.md)이 구조만 고정한 착수 placeholder였다.** 이
> 승인으로 값 자체가 운영 정책값이 됐다 — 4A `EditSessionPolicyData`·4E
> `NotificationDeliveryPolicyData`가 착수 placeholder에서 종결 승인으로 확정된 것과 같은
> 절차. `OPEN-M2-DEADLINE-VALUES`(M5 5E 실측)는 이 승인과 별개로 **활성 유지** — 5E가
> 실측을 내면 그 결과가 이 문서의 다음 정책 version(§5 `change_history`)이 된다(값의
> 존재 승인과 값의 옳음 실측은 다른 축).

---

## 0. 층 표기와 출처

| 층 | 뜻 | 이 문서에서의 출처 |
| --- | --- | --- |
| `authoritative` | 공식 문서·명세 문면 | **해당 없음** — ML 호출 timeout·재시도·breaker 값은 조달청 공식 문서가 정하는 값이 아니다 |
| `legacy-behavior` | 기존 Python이 실제로 하는 것 | **해당 없음** — ADR 0010 머리: 「legacy 에는 gRPC 경로가 없다」, 조사 대상이 아니다 |
| 근거 차용(보수적 상한) | 다른 승인된 정책의 보수적 상한을 그대로 가져옴, 실측 아님 | §1·§2 전체(값은 착수 이후 무변경) |
| 운영자 승인 | 값 자체를 운영자가 정함 | **§1·§2·§3 전체 — 사용자 승인 2026-09-10**(slice 4D-1 종결 승인과 같은 결정, `checklist.md` 「사용자 승인」 절) |

---

## 1. deadline·재시도·백오프 (scope.md ⑧, D-4D-7)

| 필드 | 값 | 근거 |
| --- | --- | --- |
| `deadlineCeiling` | 5초 | **사용자 승인 2026-09-10.** 근거 차용 — 3C `EXTRACTION_POLICY.callTimeout`(30초)·`httpRequestTimeout`(45초)보다 짧게 잡았다. ML 추론은 LLM 청크 호출보다 짧은 예산을 쓴다고 보수적으로 가정했다(실측 없음, `OPEN-M2-DEADLINE-VALUES`가 실측 갱신 경로) |
| `maxAttempts` | 3 | **사용자 승인 2026-09-10.** 근거 차용 — M2/2D `contract-policy.properties`의 `retry.sample.max-attempts=3`(그 파일 주석이 "운영 재시도 정책이 아니다 — 정본은 M4 4D"로 이 값을 인계했다) |
| `backoff` | `200ms`, `800ms` | **사용자 승인 2026-09-10.** 근거 차용 — 3B `KonepsResilientCall`의 지수 백오프 배열 관례를 값만 좁혀 옮겼다. 4배 성장 곡선(200→800)은 재시도 둘 사이의 간격만 두면 되는 `maxAttempts=3`(재시도는 최대 2회)에 맞춘 최소 배열이다 |

## 2. circuit breaker (scope.md ⑧, D-4D-7)

| 필드 | 값 | 근거 |
| --- | --- | --- |
| `breakerFailureRateThresholdPercent` | 50 | **사용자 승인 2026-09-10.** 근거 차용 — 3C `EXTRACTION_POLICY.breakerFailureRateThresholdPercent`와 동일값 |
| `breakerSlidingWindowSize` | 10 | **사용자 승인 2026-09-10.** 근거 차용 — 3C `EXTRACTION_POLICY.breakerSlidingWindowSize`와 동일값(`minimumNumberOfCalls`도 같은 크기로 맞춘다, 3C 실측 교훈 — 기본값 100은 창 크기보다 커서 breaker가 열리지 않는다) |
| `breakerWaitDurationInOpenState` | 30초 | **사용자 승인 2026-09-10.** 근거 차용 — 3C `EXTRACTION_POLICY.breakerWaitDurationInOpenState`와 동일값 |

## 3. `featureSchemaVersion`(scope.md ①, D-2)

| 필드 | 값 | 근거 |
| --- | --- | --- |
| `featureSchemaVersion` | `bidvector.ml.v1` | **사용자 승인 2026-09-10.** 계약 패키지 식별자(`contracts/proto/bidvector/ml/v1/*.proto` 의 `package bidvector.ml.v1`)를 그대로 썼다 — ADR 0010 D-7 "패키지가 version을 나른다"의 문면을 값으로 옮긴 것. 별도 schema 버전 축이 승인 문서에 없어 임의로 세분화하지 않았다(알려진 제한 — `docs/discovery/data-dictionary.md`가 이 축을 더 세분화하기 전까지는 패키지 식별자가 유일한 값) |

**착수 시 placeholder였다가 이 승인으로 확정됐다.** `reports/evidence/m4/4d/scope.md`
D-4D-7은 "정책 초기값(placeholder, 승인 대상)"으로 구조만 고정했고, 값 자체는 착수
이후 한 번도 바뀌지 않은 채 이 승인으로 운영 정책값이 됐다 — 4A `EditSessionPolicyData`·
4E `NotificationDeliveryPolicyData`가 placeholder에서 종결 승인으로 확정된 것과 같은
절차.

---

## 4. 되돌림 경로

이 값들이 재검토돼야 하면 이 표를 갱신하고 §5 `change_history`를 추가한다. `ML_CALL_POLICY`
(`MlCallPolicyData.kt`)의 값·주석은 바꾸지 않고 이 문서만 먼저 바꾸면 코드와 evidence가
어긋난다는 점에 주의(정본은 이 문서, 코드는 이 문서를 인용).

**실측 갱신 경로 — `OPEN-M2-DEADLINE-VALUES`는 이 승인과 별개로 활성 유지된다.** M5 5E가
실측을 내면 그 결과로 §1·§2 값을 갱신하고 이 문서의 정책 version을 올린다(ADR 0010 D-1
「보수적 상한 + 측정 의무」) — 이 승인은 「값이 존재해도 되는가」를 확정한 것이지 「값이
옳은가」를 실측한 것이 아니다.

---

## 5. `change_history`

- **2026-09-10 최초 등재** — 구현 레인이 §1~§3 값을 구조 검증용 착수 placeholder로 심음.
- **2026-09-10 사용자 승인** — slice 4D-1 종결 승인과 같은 결정으로 §1~§3 값 자체가
  확정됐다(`OPEN-4D-POLICY-VALUES` 종결). 값은 착수 시점에서 변경 없음.
  `OPEN-M2-DEADLINE-VALUES`는 5E 실측 뒤 갱신 경로로 활성 유지.
