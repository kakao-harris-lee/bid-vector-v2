# M9 / ledger — 장부 정리: 처분 · 종결 · 신설 OPEN 등재
- base: main `df4976cc` · 공통 규율: `/tmp/claude-1000/-home-deploy-project-bid-vector-v2/b843a5c1-93ae-42d9-82cf-0a5222fc9820/scratchpad/m9-lane-rules.md`
- 사용자 지시 2026-11-06: 「남은 묶음도 지금 같이 병렬로」(2026-11-05 「남은 항목이 없도록 전체 병렬」의 연속). 결정 지점은 추천안으로 진행(전역 규칙), 외부 비용·운영 결정이 필요한 축은 만들지 않고 등재.
- rollback: 공통 규율대로(원천 `df4976cc`, 내 커밋만, 결과 트리 동일성). 하네스 레인 변경: 계약이 명시한 것 외 없음.
- 문서 전용 slice(코드 0). 닫힌 evidence 는 덮지 않고 날짜 붙은 정정·처분 문단을 덧붙인다.

## 대상
1. **M9 머지분의 OPEN 종결 등재**: `OPEN-7C3B-FAILED-BID-DETAIL`·`-REVISION-REUNREAD`(#123) · `OPEN-7E3B-TIME-DISPLAY`·`-MONEY-WORDING`(#125) · `OPEN-7E2B-BUDGET-CLEAR`(#124) · 7F 장부 v r2 L-1·L-2(#122) · 6B-3 보존 1년(#129) — capability map · M7 종결 checklist §4 · M6 open-inventory 의 받는 자리에.
2. **M9 신설 OPEN 등재**: `OPEN-M9-TIMELINE-COMMAND-WORDING` · `-DDAY-CALENDAR` · `-SCREEN-COVERAGE-SYMBOL-GRAPH` · `-CLOCK-LINT-SCOPE` · `-MONEY-VOCAB-CROSS-LANG` · `OPEN-CVE-APT-RETRY` · `OPEN-CVE-APT-RUNBOOK-BRANCH` · `OPEN-M9-PORTAL-02-SPLIT-OPERATIONS`(이미 있으면 확인) · `OPEN-STR-12-INTERACTIVE`(PR #127 이 만들면 확인) — capability map OPEN 표.
3. **운영 잔여 처분(문서만)**: OPS-03(V23 알림함·at-most-once 로 빈자리 재서술) · STR-09+OPS-11(7F 외부 cron 으로 충족) · STR-10(스냅샷 없는 설계 — STR-07 의 D-7C1B-1 Q2 술어로 재정의/폐기 판정 후보) · OPS-04(1인 운영 — 실시간 경보 전제 안 함, 보류) · STR-04(채널·단일 운영자 사유 보류) — `reports/evidence/m6/6e1/acceptance-trace.md` 그 행과 capability map 에. 분류(V2 필수→후속 등) 변경은 운영자 몫이므로 **문면은 「후보」로**.
4. **`OPEN-GATE-REGISTRATION-STALE-INPUT` 종결 기록**(6G-2g D-6G2g-12 가 구조적으로 닫음) · **6G-2c-형식 잔여 열하나**(코드 0 정리 항목 — `milestone-6.md` 6G 절 D-6G2c-42 ③) 처분 표.
5. **M6 OPEN 204 전수 재고는 하지 않는다** — 받는 자리 표로 갈음한 M6 종결 결정 유지, 그 사실만.

## in_scope
`docs/discovery/**` · `reports/evidence/m6/closure/open-inventory.md` · `reports/evidence/m6/6e1/acceptance-trace.md` · `reports/evidence/m7/close/checklist.md`(덧붙이기) · `milestone-6.md`·`milestone-7.md`(덧붙이기 한두 줄) · evidence
## out_scope
코드 · `milestone-9.md`(팀장이 쓴다) · README.
## acceptance
식별자마다 정의 자리 ↔ 처분 자리 기계 대조(`git grep`) · 「닫힘」 주장은 근거 PR·SHA 를 단다 · `leakPatternGate` 표적 exit 0. 전건 PR CI.
