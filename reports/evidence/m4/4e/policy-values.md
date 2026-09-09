# 4E 정책 데이터 값 표 — **착수 placeholder, 승인 대기(`OPEN-4E-POLICY-VALUES`)**

> **지위: 미승인.** 이 문서의 값은 구현 레인(2026-09-09)이 심은 착수 placeholder다 — 4A
> 관례(`reports/evidence/m4/4a/policy-values.md`)를 따라 표 형식(층·근거·범위)을 유지하되
> slice 종결 승인 시점에 운영자가 확정한다. `NOTIFICATION_DELIVERY_POLICY.source`
> (`NotificationDeliveryPolicyData.kt`)도 이 문서를 가리키며 같은 미승인 지위를 명시한다.

---

## 0. 층 표기와 출처

| 층 | 뜻 | 이 문서에서의 출처 |
| --- | --- | --- |
| `authoritative` | 공식 문서·명세 문면 | **해당 없음** — 배달 경로 모드·마스킹 길이는 조달청 공식 문서가 정하는 값이 아니라 운영 정책값이다 |
| `legacy-behavior` | 기존 Python이 실제로 하는 것 | §1 `maskedSuffixLength`(legacy 마스킹 함수의 끝 4자 관행) |
| 운영자 승인 | 값 자체를 운영자가 정함 | **미확정** — 이 문서 §1·§2가 착수 placeholder를 등재하고, 확정은 slice 4E 종결 승인 시점 |

---

## 1. 환경 → 배달 모드 매핑 (scope.md ⑦, D-4E-8)

| `RuntimeEnvironment` | `DeliveryMode` | 근거 |
| --- | --- | --- |
| `Production` | `Live` | 착수 placeholder — 운영 환경은 실제로 배달한다는 상식적 기본값. legacy `NON_DELIVERING_ENVIRONMENTS = {"test"}`가 `test` 하나만 막고 나머지 전부(운영 포함)를 배달 허용으로 두던 것과 같은 방향(조사 (c-6)) |
| `Staging` | `DryRun` | 착수 placeholder — 실 배달 전 최종 검증 환경은 dry-run이 안전측 기본값 |
| `Development` | `DryRun` | 착수 placeholder — 개발 환경에서 실 채널 호출은 사고 위험(설정 오류로 실 사용자에게 발송) |
| `Test` | `Blocked` | 착수 placeholder — legacy `NON_DELIVERING_ENVIRONMENTS = {"test"}`와 값 자체가 일치(테스트 스위트가 어떤 경로로도 배달을 시도하지 않음). 「배달 모드가 아니라 완전 차단」인 이유는 test 실행 중 dry-run조차 판정 로그를 남기지 않는 편이 안전하다는 판단(placeholder) |

**착수 시 placeholder였다.** `reports/evidence/m4/4e/scope.md` ⑦은 "값은 착수 시
placeholder(정본 `policy-values.md`, 승인 대기 `OPEN-4E-POLICY-VALUES`)"로 구조만
고정했다 — 4A의 `EditSessionPolicyData` timeout이 처음에 placeholder였다가 종결 승인으로
확정된 것과 같은 절차(`reports/evidence/m4/4a/policy-values.md` §1 참고).

**legacy와의 차이**: legacy는 `NON_DELIVERING_ENVIRONMENTS` 집합 하나(값 밖은 전부
배달 허용)였다. 이 slice의 정책 데이터는 [RuntimeEnvironment] 전 값을 강제로 덮는
전사상(exhaustive map)이라 "모르는 환경은 보낸다" 경로가 애초에 없다(`NotificationDeliveryPolicyData.init`
생성 불변식, 우회 (6) 차단).

---

## 2. 마스킹 suffix 길이 (scope.md ③)

| 필드 | 값 | 근거 |
| --- | --- | --- |
| `NotificationDeliveryPolicyData.maskedSuffixLength` | **4** | `legacy-behavior` 층 — legacy 마스킹 함수의 끝 4자 관행을 계승(조사 결과, `_workspace/m4-prep/01_scout_workflow.md` (c-5)). 착수 placeholder이나 legacy 실측값과 일치한다는 점에서 다른 값보다 재검토 우선순위가 낮다 |

---

## 3. 되돌림 경로

이 값들이 재검토돼야 하면 이 표를 갱신하고 §4 `change_history`를 추가한다 —
`NOTIFICATION_DELIVERY_POLICY`(`NotificationDeliveryPolicyData.kt`)의 값·주석은 바꾸지
않고 이 문서만 먼저 바꾸면 코드와 evidence가 어긋난다는 점에 주의(정본은 이 문서, 코드는
이 문서를 인용).

---

## 4. `change_history`

최초 등재(착수 placeholder). 아직 승인·변경 없음.
