# M9 / opening-harness — 개찰 축 여정 하네스 (OPEN-7E3A-OPENING-AXIS-HARNESS)
- base: main `df4976cc` · 공통 규율: `/tmp/claude-1000/-home-deploy-project-bid-vector-v2/b843a5c1-93ae-42d9-82cf-0a5222fc9820/scratchpad/m9-lane-rules.md`
- 사용자 지시 2026-11-06: 「남은 묶음도 지금 같이 병렬로」(2026-11-05 「남은 항목이 없도록 전체 병렬」의 연속). 결정 지점은 추천안으로 진행(전역 규칙), 외부 비용·운영 결정이 필요한 축은 만들지 않고 등재.
- rollback: 공통 규율대로(원천 `df4976cc`, 내 커밋만, 결과 트리 동일성). 하네스 레인 변경: 계약이 명시한 것 외 없음.
- 근거: `reports/evidence/m7/7e3a/scope.md` D-7E3a-7 · M7 종결 §4. 하네스가 개찰 축을 수집하지 않아 `readOpeningResult` 아홉 갈래 중 셋만 낸다.

## 결정
- fake KONEPS(`web/e2e/harness/fake-koneps.mjs`)에 개찰 축 operation·fixture(낙찰정보서비스 `as/ScsbidInfoService` 쪽 목록·상세 + 입찰공고정보서비스 `02` 의 A값·기초금액 — **이름은 출하 기본값에서 가져온다**, PR #130 의 정확 일치 표·준비 표지 관례를 따른다; #130 이 아직 머지 전이면 그 브랜치와 충돌하지 않게 fake 의 기존 라우팅 구조를 확장만) + `journey-up.mjs` 에 개찰 수집→승격 두 걸음 → 아홉 갈래를 **하네스 응답 집합 등식**으로, 여정 ⑤가 확정 갈래까지 간다.

## in_scope
`web/e2e/harness/**` · `web/e2e/journey/**` · `web/config/gate-tests.txt` · 필요 시 `app` 의 test 전용 자원 없음(앱 코드 0 — 필요하면 멈추고 보고) · evidence
## acceptance
web 로컬 게이트 + PR CI `web` W-13·`check` S-20. 변이: 갈래 하나의 fixture 제거 → 등식 RED.
