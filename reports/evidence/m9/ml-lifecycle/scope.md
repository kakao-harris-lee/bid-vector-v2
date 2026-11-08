# M9 / ml-lifecycle — 승격 게이트 effective date · 정산 성숙도 시간축 · model_version 축 (ml-engine 오프라인)
- base: main `df4976cc` · 공통 규율: `/tmp/claude-1000/-home-deploy-project-bid-vector-v2/b843a5c1-93ae-42d9-82cf-0a5222fc9820/scratchpad/m9-lane-rules.md`
- 사용자 지시 2026-11-06: 「남은 묶음도 지금 같이 병렬로」(2026-11-05 「남은 항목이 없도록 전체 병렬」의 연속). 결정 지점은 추천안으로 진행(전역 규칙), 외부 비용·운영 결정이 필요한 축은 만들지 않고 등재.
- rollback: 공통 규율대로(내 커밋만, 결과 트리 동일성). **원천·목록·실측은 `rollback.md` 가 정본**이다 — 병합이 있을 때마다 원천이 「마지막으로 병합한 main 커밋」으로 옮겨가므로 여기에 SHA 를 적지 않는다(착수 base `df4976cc` 는 더 이상 원천이 아니다). 하네스 레인 변경: 계약이 명시한 것 외 없음.
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

## 계약 갱신 2026-11-06 (팀장) — 수정 라운드 2

**사후 등재(v r2 L-2)** — `reports/evidence/m5/5c2/policy-values.md` · `reports/evidence/m5/5d/policy-values.md`
(정책 값 승인 장부, 합 +4 −1). 이 slice 가 `evaluation-v1` 에 effective date 칸을 더하고 성숙도 키를 바꿨으므로
그 version 의 **승인 장부가 같은 값을 적어야** 한다 — 팀장이 이 확장을 승인한다(원래 문면은 덮지 않고 덧붙이는
형태 유지).

**v r2 H-2 처방 — 구조로 닫는다(팀장 결정, 「운영자가 아무 파일이나 고를 수 있다」 경계는 채택하지 않는다).**
version 문자열이 내용에 묶이지 않아 env 로 가리킨 편집 사본·코드가 쓴 완화 사본이 같은 `evaluation-v1` 로
통과했다 — ML-07 수용 문면 둘(env 주입이 판정을 바꾸지 않는다 · version 변경이 전후 판정을 가른다)을 깬다.
- 승인된 version → 정책 파일 sha256 을 **코드 상수 등록부**(`ml-engine/src/**`)로 둔다. 로더와 재검증은 등록부에
  없는 version, 등록된 version 인데 내용 digest 가 다른 파일을 **거부**한다.
- 등록부 ↔ 출하 `ml-engine/policy/*.yaml` digest 등식 test, 등록부의 각 version 의 값 ↔ 위 승인 장부 표 대조 test.
- 새 version 을 승인하는 길은 「yaml + 등록부 한 줄 + 승인 장부」 세 자리를 같은 커밋에서 바꾸는 것뿐 — runbook·
  design-review 에 적는다.
