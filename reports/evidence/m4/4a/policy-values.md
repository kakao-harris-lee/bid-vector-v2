# 4A 정책 데이터 값 표 — **사용자 승인 2026-09-09**

> **승인일: 2026-09-09.** 승인은 slice 4A 종결 승인과 같은 결정에 실렸다 — 정본은
> `reports/evidence/m4/4a/checklist.md` 「사용자 승인」 절이다. 이 문서는 3A 관례
> (`reports/evidence/m3/3a/policy-values.md`)를 따라 그 값의 근거와 범위를 별도 정본으로
> 고정한다 — `EditSessionPolicyData` 를 Kotlin `EffectiveDatedPolicy` 인스턴스(`EDIT_SESSION_
> POLICY`, `EditSessionPolicyData.kt`)로 옮긴 자리가 이 문서를 `source` 로 가리킨다.
>
> **3A 와 다른 점 — 이 slice 는 값이 하나뿐이다.** 3A 의 표는 여러 축(필드 계약·resultCode
> 범주·basis 순서 등)을 묶었지만, 4A 는 D-4A-3 하나(편집 세션 timeout)만 승인 대상이다.
> 표 형식(층·근거·범위)은 유지하되 항목 수만큼만 적는다.

---

## 0. 층 표기와 출처

| 층 | 뜻 | 이 문서에서의 출처 |
| --- | --- | --- |
| `authoritative` | 공식 문서·명세 문면 | **해당 없음** — timeout 은 조달청 공식 문서가 정하는 값이 아니라 운영 정책값이다 |
| `legacy-behavior` | 기존 Python 이 실제로 하는 것 | **0건**(조사 결과 — legacy 에 TTL 슬롯 자체가 없다, `_workspace/m4-prep/01_scout_workflow.md`) |
| 운영자 승인 | 값 자체를 운영자가 정함 | **이 문서 §1이 그 정본이다** |

---

## 1. D-4A-3 — 편집 세션 timeout

| 필드 | 값 | 근거 |
| --- | --- | --- |
| `EditSessionPolicyData.timeoutWindow` | **`Duration.ofMinutes(15)`(15분)** | 사용자 승인 2026-09-09 |

**착수 시 placeholder였다.** `reports/evidence/m4/4a/scope.md` D-4A-3 은 "timeout 값·재확인
창은 정책 데이터 슬롯(값은 승인 대상)"으로 구조만 고정했고, 구현 레인(2026-09-08)이
`EditSessionPolicyData(Duration.ofMinutes(15))` 를 **구조 검증용 placeholder**로 심었다
(`EDIT_SESSION_POLICY` 의 `source` 문면 "timeout 값 미확정, 구조만"). legacy 에 대응 TTL
슬롯이 0건이라(조사 노트) 재활용할 값이 없었다.

**승인이 확정한 것은 두 가지다.** ① 값 15분 자체 ② 그 값이 이제 placeholder 가 아니라
**운영 정책값**이라는 지위 — `EditSessionPolicyData.init` 의 생성 불변식(양수·상한 24시간,
`EditSessionPolicyDataTest` 가 회귀 보호)은 승인 전후로 바뀌지 않는다. 상한 24시간 자체는
이번 승인 대상이 아니다(우회 (4) 차단용 안전판 — 4A 구현 레인이 임의로 정한 생성 불변식이고
운영자가 재검토하지 않았다. 필요해지면 별도 승인 대상).

**되돌림 경로.** 이 값이 재검토돼야 하면 이 표를 갱신하고 `change_history` 절을 추가한다 —
`EditSessionPolicyData.kt` 의 값·주석은 바꾸지 않고 이 문서만 먼저 바꾸면 코드와 evidence가
어긋난다는 점에 주의(정본은 이 문서, 코드는 이 문서를 인용).

---

## 2. `change_history`

최초 등재. 아직 변경 없음.
