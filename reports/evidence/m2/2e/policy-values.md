# 2E 정책 데이터 값 표 — **착수 placeholder, 종결 승인 대기 (`OPEN-2E-TEXT-MAX`)**

> 4D-1 `policy-values.md`(구조 검증용 착수 placeholder → 종결 승인으로 확정된 전례)와 같은
> 형식. 정본은 `config/quality/contract-policy.properties`(`policy.version=2`) — 이 문서는
> 근거만 기록한다.

## 0. 층 표기와 출처

| 층 | 뜻 | 이 문서에서의 출처 |
| --- | --- | --- |
| `authoritative` | 공식 문서·명세 문면 | **해당 없음** |
| `legacy-behavior` | 기존 Python이 실제로 하는 것 | **해당 없음** — legacy 는 임베딩 텍스트 길이·정규화 오차 상한을 명시 정책값으로 두지 않았다(조사 `_workspace/m2-2e/01_scout_opportunity_scoring.md` §5) |
| 근거 차용(보수적 상한) | 다른 승인된 정책의 보수적 상한을 그대로 가져옴 | §1 전체 |
| 운영자 승인 | 값 자체를 운영자가 정함 | **착수 시 미승인 — `OPEN-2E-TEXT-MAX`, 이 slice 종결 승인 때 확정 대상** |

## 1. `embedding.text.max-chars`·`embedding.norm.epsilon`(scope.md ④)

| 필드 | 값 | 근거 |
| --- | --- | --- |
| `embedding.text.max-chars` | 4000 | **착수 placeholder.** 근거 차용 — 3C `EXTRACTION_POLICY`류 chunk 예산과 달리 이 축은 legacy 에 대응값이 없다(조사 §5). 공고 원문 합성 텍스트(업종·면허·협회·기술분야·지역·예산·용량 요약)가 수천 자를 넘길 사례가 드물다는 가정으로 보수적 상한을 잡았다 — **실측 없음**, `OPEN-2E-TEXT-MAX`가 확정 경로 |
| `embedding.norm.epsilon` | 0.0005 | **착수 placeholder.** float32 벡터의 L2 norm은 부동소수 연산 오차로 정확히 1.0이 아닐 수 있다 — 4D-1 deadline 값들의 "보수적 상한, 실측 아님" 근거 차용과 같은 성격의 여유값 |

## 2. 되돌림 경로

값이 재검토돼야 하면 이 표를 갱신하고 `contract-policy.properties`의 `policy.version`을
올린다. 코드(test)의 값·주석은 바꾸지 않고 이 문서만 먼저 바꾸면 코드와 evidence가 어긋난다.

## 3. `change_history`

- **2026-09-10 최초 등재** — 구현 레인이 두 키를 착수 placeholder로 심음(`policy.version`
  1→2). `OPEN-2E-TEXT-MAX` 신설(`docs/discovery/capability-map.md` §14).
