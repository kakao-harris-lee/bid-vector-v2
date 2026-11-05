# M9 / ops-code — 외부 실패 분류 · 호출 간격 · 새 후보 알림함 기록
- base: main `df4976cc` · 공통 규율: `/tmp/claude-1000/-home-deploy-project-bid-vector-v2/b843a5c1-93ae-42d9-82cf-0a5222fc9820/scratchpad/m9-lane-rules.md`
- 사용자 지시 2026-11-06: 「남은 묶음도 지금 같이 병렬로」(2026-11-05 「남은 항목이 없도록 전체 병렬」의 연속). 결정 지점은 추천안으로 진행(전역 규칙), 외부 비용·운영 결정이 필요한 축은 만들지 않고 등재.
- rollback: 공통 규율대로(원천 `df4976cc`, 내 커밋만, 결과 트리 동일성). 하네스 레인 변경: 계약이 명시한 것 외 없음.

## 대상 (M6 잔여 운영 묶음 중 지금 의미 있고 승인 불필요한 것 — 조사 2026-11-05)
| capability | 처방 |
| --- | --- |
| **OPS-09** 403 차단의 구조적 비재시도 | koneps 어댑터: 429 아닌 응답이 상태 코드와 무관하게 봉투 해석으로 가는 구멍 — 403(및 401) 은 봉투 이전에 **비재시도 분류**(fail-safe). 재시도 정책 표를 상태 코드 축으로 |
| **OPS-08** 연속 호출 간 최소 간격 | 계수 기반 상한 옆에 **간격 기반** 축(설정값, 기본값 없음 → 기동 실패 관례; 테스트는 주입 시계) |
| **NOTI-01** 평가 → 알림함 `NEW_CANDIDATE` 생산자 | 가치 게이트를 통과한 후보가 알림함 항목 1건을 남기는 production 배선(V23 표·어휘 이미 있음, 생산자 0). 채널 발송과 무관 |
| CVE 리뷰 후속 `OPEN-CVE-APT-RETRY` 는 다른 레인(cve-followups) |

## in_scope
`adapters/src/main/kotlin/bidvector/adapters/koneps/**` · `procurement/**`(호출 예산·간격 축이 거기 있으면) · `app/src/main/kotlin/bidvector/app/collection/**`·wiring(간격 설정) · `workflow/**/evaluation/**`·`notificationinbox/**` 생산자 배선 · 각 test · `config/quality/**` 등재 · `docs/discovery/capability-map.md` 해당 행 · evidence
## out_scope
마이그레이션(필요하면 멈추고 보고) · 발송 채널 · 실 API.
## acceptance
403/401 비재시도 · 간격 위반 없음(주입 시계) · 평가 → 알림함 1건(실 DB) · 각 바꿔치우기 변이 RED. 전건 PR CI.
