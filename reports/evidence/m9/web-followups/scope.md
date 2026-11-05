# M9 / web-followups — 화면 표시 후속 (PR #125 가 등재한 것)
- base: main `df4976cc` · 공통 규율: `/tmp/claude-1000/-home-deploy-project-bid-vector-v2/b843a5c1-93ae-42d9-82cf-0a5222fc9820/scratchpad/m9-lane-rules.md`
- 사용자 지시 2026-11-06: 「남은 묶음도 지금 같이 병렬로」(2026-11-05 「남은 항목이 없도록 전체 병렬」의 연속). 결정 지점은 추천안으로 진행(전역 규칙), 외부 비용·운영 결정이 필요한 축은 만들지 않고 등재.
- rollback: 공통 규율대로(원천 `df4976cc`, 내 커밋만, 결과 트리 동일성). 하네스 레인 변경: 계약이 명시한 것 외 없음.

## 대상
| OPEN | 처방 |
| --- | --- |
| `OPEN-M9-TIMELINE-COMMAND-WORDING` | 워크시트 타임라인이 명령 토큰(`ADD_TO_BID_BOX` 꼴)을 원문으로 — 전수 표(`satisfies Record`)로 한국어 문면, 표 밖은 `displayOrUnknown` |
| `OPEN-M9-DDAY-CALENDAR` | `daysUntil` 이 24시간 단위 — **KST 달력일** 기준으로(추천안; 23:00 KST 에 이튿날 01:00 마감 = D-1). 경계 test |
| `OPEN-M9-SCREEN-COVERAGE-SYMBOL-GRAPH` | 화면 커버리지 모집단을 호출 식별자 이름 대신 **TS 심볼 import 그래프**로 |
| `OPEN-M9-CLOCK-LINT-SCOPE` | 시계 lint 를 `Intl`(인자 없는 format)·`Temporal.Now`·제외 디렉터리 경유까지 |
| `OPEN-M9-MONEY-VOCAB-CROSS-LANG` | 금액 basis/provenance 어휘(web 세 자리 손복제)를 openapi enum 에서 유도하거나 등식 test 로 묶기 |

## in_scope
`web/src/**` · `web/scripts/**` · `web/eslint.config.*` · `web/config/gate-tests.txt` · `web/e2e/journey/**`(문면 단언) · 캡처(`docs/usecase/**` — 재촬영은 app.jar 필요, bv-build 한 번) · evidence
## acceptance
web 로컬 게이트 · 각 게이트 양성·음성 대조 · 변이 RED. W-13·S-20 은 PR CI.
