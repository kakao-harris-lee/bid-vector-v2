# M9 / ml-lifecycle — 승격 게이트 effective date · 정산 성숙도 시간축 · model_version 축 (ml-engine 오프라인)
- base: main `df4976cc` · 공통 규율: `/tmp/claude-1000/-home-deploy-project-bid-vector-v2/b843a5c1-93ae-42d9-82cf-0a5222fc9820/scratchpad/m9-lane-rules.md`
- 사용자 지시 2026-11-06: 「남은 묶음도 지금 같이 병렬로」(2026-11-05 「남은 항목이 없도록 전체 병렬」의 연속). 결정 지점은 추천안으로 진행(전역 규칙), 외부 비용·운영 결정이 필요한 축은 만들지 않고 등재.
- rollback: 공통 규율대로(원천 `df4976cc`, 내 커밋만, 결과 트리 동일성). 하네스 레인 변경: 계약이 명시한 것 외 없음.
- 사용자 결정 유지: ML 앱 배선·수집 연장 **보류**. 이 slice 는 앱 배선 없이 서는 **ml-engine 오프라인 축**만.

## 대상
| capability | 처방 |
| --- | --- |
| **ML-07** 승격 게이트 | `ml-engine/policy/evaluation-v1.yaml` 에 **effective date** 칸 + 정책 version 전후로 판정이 갈리는 측정 자리; 임계 완화가 CLI·env·요청으로 불가함을 test |
| **SET-06** 정산 성숙도 | 평가 창에 **시간축 provenance** 와 결측 `Unmeasurable` 어휘(0/0 금지·분자·분모 동반·제외 사유); 대체 출처 허용(`OPEN-SET-10`)은 등재만 |
| **ML-05** model_version | release/checksum 축을 model_version 축으로 **선언**(spec 변경 → 새 식별자 test) |
| ML-08 서명 | 키 보관·회전이 운영자 결정 — **등재만** |

## in_scope
`ml-engine/**`(src·tests·policy) · capability map 해당 행 · evidence
## acceptance
CI `ml-engine` job 명령 그대로(로컬 pytest·mypy·import-linter OK — 가볍다) · 각 바꿔치우기 변이 RED. 6G 판정(`reports/evidence/m6/6g/verdict.md`) 결과를 바꾸지 않는다(재판정 0).
