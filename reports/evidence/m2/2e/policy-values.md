# 2E 정책 데이터 값 표 — **사용자 승인 2026-09-10**

> **승인일: 2026-09-10.** 승인은 slice 2E 종결 승인과 같은 결정에 실렸다 — 정본은
> `reports/evidence/m2/2e/checklist.md` 「사용자 승인」 절이다. 4D-1 `policy-values.md`
> (구조 검증용 착수 placeholder → 종결 승인으로 확정된 전례)와 같은 형식.
>
> **착수 시(2026-09-10)에는 §1이 구조만 고정한 착수 placeholder였다.** 이 승인으로 값
> 자체가 운영 정책값이 됐다 — 4A `EditSessionPolicyData`·4D-1 `MlCallPolicyData`가
> placeholder에서 종결 승인으로 확정된 것과 같은 절차. 값은 착수 이후 무변경(4000자·
> 0.0005).
>
> 정본은 `config/quality/contract-policy.properties`(`policy.version=3` — 이 승인과
> 같은 커밋에서 `approved.tag`가 `contracts/v1-approved-2026-09-10`으로 함께 갱신돼
> 3으로 올랐다, §3 `change_history`) — 이 문서는 근거만 기록한다.

## 0. 층 표기와 출처

| 층 | 뜻 | 이 문서에서의 출처 |
| --- | --- | --- |
| `authoritative` | 공식 문서·명세 문면 | **해당 없음** |
| `legacy-behavior` | 기존 Python이 실제로 하는 것 | **해당 없음** — legacy 는 임베딩 텍스트 길이·정규화 오차 상한을 명시 정책값으로 두지 않았다(조사 `_workspace/m2-2e/01_scout_opportunity_scoring.md` §5) |
| 근거 차용(보수적 상한) | 다른 승인된 정책의 보수적 상한을 그대로 가져옴 | §1 전체(값은 착수 이후 무변경) |
| 운영자 승인 | 값 자체를 운영자가 정함 | **§1 전체 — 사용자 승인 2026-09-10**(slice 2E 종결 승인과 같은 결정, `checklist.md` 「사용자 승인」 절) |

## 1. `embedding.text.max-chars`·`embedding.norm.epsilon`(scope.md ④)

| 필드 | 값 | 근거 |
| --- | --- | --- |
| `embedding.text.max-chars` | 4000 | **사용자 승인 2026-09-10.** 근거 차용 — 3C `EXTRACTION_POLICY`류 chunk 예산과 달리 이 축은 legacy 에 대응값이 없다(조사 §5). 공고 원문 합성 텍스트(업종·면허·협회·기술분야·지역·예산·용량 요약)가 수천 자를 넘길 사례가 드물다는 가정으로 보수적 상한을 잡았다 — **실측 없음**, `OPEN-2E-TEXT-MAX` 종결 |
| `embedding.norm.epsilon` | 0.0005 | **사용자 승인 2026-09-10.** float32 벡터의 L2 norm은 부동소수 연산 오차로 정확히 1.0이 아닐 수 있다 — 4D-1 deadline 값들의 "보수적 상한, 실측 아님" 근거 차용과 같은 성격의 여유값 |

**착수 시 placeholder였다가 이 승인으로 확정됐다.** `reports/evidence/m2/2e/scope.md`
④는 "정책 값, 초기값은 승인 대상 `OPEN-2E-TEXT-MAX`"로 구조만 고정했고, 값 자체는 착수
이후 한 번도 바뀌지 않은 채 이 승인으로 운영 정책값이 됐다 — 4A `EditSessionPolicyData`·
4D-1 `MlCallPolicyData`가 placeholder에서 종결 승인으로 확정된 것과 같은 절차.

## 2. 되돌림 경로

이 값들이 재검토돼야 하면 이 표를 갱신하고 §3 `change_history`를 추가한다.
`contract-policy.properties`의 값·주석은 바꾸지 않고 이 문서만 먼저 바꾸면 코드와
evidence가 어긋난다는 점에 주의(정본은 `contract-policy.properties`, 이 문서는 근거).

## 3. `change_history`

- **2026-09-10 최초 등재** — 구현 레인이 두 키를 착수 placeholder로 심음(`policy.version`
  1→2). `OPEN-2E-TEXT-MAX` 신설(`docs/discovery/capability-map.md` §14).
- **2026-09-10 사용자 승인** — slice 2E 종결 승인과 같은 결정으로 §1 값 자체가
  확정됐다(`OPEN-2E-TEXT-MAX` 종결). 값은 착수 시점에서 변경 없음. 같은 커밋에서
  `approved.tag`가 `contracts/v1-approved-2026-09-10`으로 옮겨져 `policy.version`이
  2→3으로 올랐다(이 파일의 다른 키에는 값 변경 없음).
