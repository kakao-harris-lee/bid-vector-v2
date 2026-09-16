# M4/4B-8 정책 값 — 무변경 선언

D-4B8-5는 새 정책 슬롯을 열지 않는다. 정본은 여전히 4B-7
`reports/evidence/m4/4b7/policy-values.md`다(`SAMPLE_PROVENANCE_POLICY`·
`OpportunityPolicyData`·`SampleEligibilityPolicyData` 값 전부). 이 문서는 그 값들이
4B-8에서 바뀌지 않았음을 근거와 함께 선언한다.

## 무변경 근거(기계 확인)

```
git diff 845e29b..HEAD -- workflow/src/main/kotlin/bidvector/workflow/evaluation/SampleEligibility.kt
```

빈 출력 — `SAMPLE_PROVENANCE_POLICY`·`SAMPLE_ELIGIBILITY_POLICY`·`OPENING_DATE_ZONE`이
선언된 파일 자체를 이 slice가 건드리지 않는다. `OpportunityAnalysis.ResolvedPolicies`에
필드(`provenancePolicy`) 하나를 추가했지만, 그 필드의 **값**은 `SAMPLE_PROVENANCE_POLICY`를
`resolvedOrNull`(기존 함수, 다른 세 정책과 같은 방식)로 조회한 결과이지 새 인스턴스가
아니다 — `OpportunityAnalysis.kt`의 diff는 「같은 singleton을 하나 더 resolve해서 값으로
나른다」는 배선만 늘렸다.

## 이 slice가 만드는 유일한 새 규칙

| 규칙 | 값 | 근거 |
| --- | --- | --- |
| `normalizeCategoryKey(raw) = raw.trim().문자 단위 소문자화` | 코드 자체(정책 슬롯 아님 — 상수 규칙) | Python `ml_engine.features.normalize.normalize_feature_key`(`strip().lower()`) 미러. `docs/discovery/data-dictionary.md` §6.3.1(팀장 기록, `dfaff1a`)이 이미 "한 규칙, 두 언어"로 승인 전 확정 — 이 slice는 그 문서가 가리키는 Kotlin 정본(`procurement.CategoryCode.of(raw)`)을 구현한다 |

정책 슬롯(`EffectiveDatedPolicy` 인스턴스)이 아니라 **코드 규칙**으로 둔 이유는 D-4B8-3과
같다 — 정규화는 값의 생성 규율이지 시점에 따라 달라지는 정책이 아니다(4B-7 §4의
`OPENING_DATE_ZONE` 상수 취급과 같은 판단).

## 해소 조건

4B-7 `OPEN-4B7-POLICY-VALUES`의 §3(provenance 임계 넷 + 순서)은 이 slice로 닫히지 않는다
— 이 slice는 그 값을 소비만 하고 바꾸지 않는다. 새로 여는 것은 `OPEN-4B8-CATEGORY-BACKFILL`
(checklist.md 「알려진 제한」) 하나뿐이며 값 승인이 아니라 백필 여부의 운영 결정이다.

## 사용자 승인

무변경 선언이라 새 승인 대상이 없다. 4B-7 `OPEN-4B7-POLICY-VALUES`는 여전히 승인 대기
상태 그대로다.
