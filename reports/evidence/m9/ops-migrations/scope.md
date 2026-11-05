# M9 / ops-migrations — 정책 version 저장 · outbox 실패 사유 · 실행 행 릴리스 식별자
- base: main `df4976cc` · 공통 규율: `/tmp/claude-1000/-home-deploy-project-bid-vector-v2/b843a5c1-93ae-42d9-82cf-0a5222fc9820/scratchpad/m9-lane-rules.md`
- 사용자 지시 2026-11-06: 「남은 묶음도 지금 같이 병렬로」(2026-11-05 「남은 항목이 없도록 전체 병렬」의 연속). 결정 지점은 추천안으로 진행(전역 규칙), 외부 비용·운영 결정이 필요한 축은 만들지 않고 등재.
- rollback: 공통 규율대로(원천 `df4976cc`, 내 커밋만, 결과 트리 동일성). 하네스 레인 변경: 계약이 명시한 것 외 없음.
- **DB 마이그레이션 slice** — 번호는 **V26·V27·V28** 고정(V25 는 paper-bid 레인 몫). 리뷰는 verifier + code-reviewer + migration 관점. Codex 는 운영자 요청 시에만.

## 대상
| capability | 처방 |
| --- | --- |
| **DEC-08** 분류 정책 version 저장 | `ProvenanceEvidence.policyVersion` 이 메모리에만 — 결과 행에 정책 version 칸(V26) + production 호출부가 근거를 버리지 않게 |
| **OPS-05** outbox 실패 사유 | outbox 에 종단 사유 칸(닫힌 어휘, V27) — 「왜 ISOLATED/FAILED 인가」를 DB 로 답한다. 시간 기반 janitor 는 등재만(임대 기반 회수가 현행 설계) |
| **OPS-10** 실행 행 릴리스 식별자 | `collection_run`·`notice`·`opening_result`·`outbox` 에 릴리스 칸(V28) + 수집 설정의 기본값 `unversioned` 제거(기본값 없음 → 기동 실패 관례) |

## in_scope
`adapters/src/main/resources/db/migration/V26__*.sql`·`V27__*.sql`·`V28__*.sql` · 관련 adapters·workflow·app 코드와 test · GRANT·privacy 등재 test · `config/quality/**` · `docs/discovery/data-dictionary.md` 해당 절 · capability map 행 · runbook(릴리스 식별자 주입 — 운영 compose 가 이미 넘기는지 확인) · evidence
## acceptance
각 마이그레이션 clean-migrate · CHECK·GRANT 등식 · 되돌림 리허설 두 갈래(미적용=파일 삭제 / 적용=새 V) · 기본값 제거로 깨지는 배선(운영 compose·dev compose·test) 전수 갱신 · 변이 RED. 전건 PR CI.
