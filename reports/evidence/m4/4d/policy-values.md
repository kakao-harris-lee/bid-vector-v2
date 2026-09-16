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
| `featureSchemaVersion` | `award-rate-features-v2` | **운영자 결정 2026-09-16 ②(M5/5F-2) — 4D 승인 값(`bidvector.ml.v1`, 계약 패키지 식별자)을 대체.** M5/5B 가 feature schema 버전 축을 신설(D-5B-1, 별칭 금지)해 계약 패키지 식별자가 더는 이 필드의 근거가 아니게 됐다 — 정본은 이제 5B feature schema version, Python `ml_engine.features.schema.SUPPORTED_FEATURE_SCHEMAS`(무편집 참고, 정본은 그 모듈)와 같은 값이어야 한다. 동일성은 문서 대조로 둔다(D-5F2-2) — 실 교차 언어 대조는 `OPEN-5E2-CROSSLANG-REAL-SERVER`(6C) 몫 |

**착수 시 placeholder였다가 2026-09-10 승인으로 확정됐고, 2026-09-16 운영자 결정 ②로 값이
갱신됐다.** `reports/evidence/m4/4d/scope.md` D-4D-7은 "정책 초기값(placeholder, 승인
대상)"으로 구조만 고정했다. 값 자체는 2026-09-10 승인으로 운영 정책값이 됐으나(4A
`EditSessionPolicyData`·4E `NotificationDeliveryPolicyData`가 placeholder에서 종결
승인으로 확정된 것과 같은 절차), 5B 가 신설한 feature schema 축과 어긋나 5E-2 servicer 가
전량 `UNSUPPORTED_SCHEMA` 로 거부하는 상태였다(`OPEN-5E2-FEATURE-SCHEMA-PARITY`). M5/5F-2
가 값을 Python 이 지원하는 `award-rate-features-v2` 로 갱신해 그 OPEN 을 종결했다 — 코드
경로·타입은 무변경(D-5F2-1).

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
- **2026-09-16 운영자 결정 ②(M5/5F-2)** — §3 `featureSchemaVersion` 값을 `bidvector.ml.v1`
  에서 `award-rate-features-v2` 로 대체(4D 승인 값의 갱신, §1·§2 는 무변경). 사유는
  5B(D-5B-1)가 feature schema 버전 축을 신설해 4D 가 쓰던 계약 패키지 식별자 논리가
  낡았고, 5E-2 servicer 가 옛 값을 `UNSUPPORTED_SCHEMA` 로 거부했기 때문이다
  (`OPEN-5E2-FEATURE-SCHEMA-PARITY` 종결). 코드 쪽 정본은
  `adapters/src/main/kotlin/bidvector/adapters/ml/MlCallPolicyData.kt` 의 `ML_CALL_POLICY`.
