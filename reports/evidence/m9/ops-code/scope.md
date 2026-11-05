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

## 계약 갱신 2026-11-06 (팀장)

in_scope 에 **일곱 경로**를 더한다. 셋은 구현이 요구했고(NOTI-01 의 production 배선), 둘은
타입이 강제했고(소진 `when`), 둘은 측정 상태 장부다.

| 경로 | 사유 |
| --- | --- |
| `adapters/src/main/kotlin/bidvector/adapters/evaluation/EvaluationCommitRun.kt` | 평가의 **커밋 경로**(outbox 에 실제 행을 남기는 유일한 경로)가 알림 포트를 조립하는 자리다. NOTI-01 생산자가 production 에 서려면 그 조립에 포트가 들어가야 한다 |
| `adapters/src/test/kotlin/bidvector/adapters/evaluation/**` | 그 경로의 test. **PR #127(Telegram, 미머지)이 갈라 둔 `EvaluationCommitRunE2ETest.kt`·`RelayFailureInjection.kt` 는 건드리지 않고 새 파일로** |
| `adapters/src/main/kotlin/bidvector/adapters/notificationinbox/JdbcNotificationAudience.kt` + 청중 SQL | 청중(계정 전수) 질의의 어댑터. SQL 은 **별도 상수 홀더**에 둔다 — `NotificationInboxSqlPolicyTest` 의 문장 모집단을 흔들지 않는다 |
| `app/src/main/kotlin/bidvector/app/wiring/NotificationInboxWiring.kt` | 청중 빈 하나. 쓰기 빈이 이미 한 자리로 모여 있는 그 조립에 붙인다 |
| `workflow/src/main/kotlin/bidvector/workflow/collection/CollectNoticesUseCase.kt` | `TruncationCause.AccessBlocked` 신설이 절단 사유 → 다음 걸음 표의 소진 `when` 을 깬다. 차단은 `HALT_RUN`(401·403 은 슬롯의 사실이 아니라 자격의 사실이라 다음 슬롯도 같은 답이고, 넘어가면 남은 슬롯마다 거절될 호출을 한 번씩 더 내며 승인 상한만 태운다 — 쿼터를 멈추게 한 D-6G-47 과 같은 근거) |
| `workflow/src/test/kotlin/bidvector/workflow/collection/CollectNoticesUseCaseTest.kt` | 그 표의 전수 test 가 sealed 계층에서 사유를 뽑아 돌아 새 사유를 바로 잡았다. test 를 **지우지 않고** 제외 집합에 차단을 더해 주장을 바꾸고, 멈추는 쪽 주장은 전용 test 로 세운다 |
| `reports/evidence/m6/6e1/acceptance-trace.md`(+필요 시 `docs/discovery/m7-screen-data-requirements.md`) | 측정 상태 장부. **끝에서 한 번** 갱신하고(세 행), 원래 문면은 덮지 않고 날짜 붙은 「M9/ops-code 갱신」 문단을 덧붙인다 |

### 기각한 대안 (NOTI-01 생산자 자리)

| 대안 | 기각 사유 |
| --- | --- |
| 러너(`app/evaluation/EvaluationCommitRunner`)에서 판정 결과 목록을 다시 훑어 사후 기록 | **둘째 생산 경로**가 생긴다 — 「가치 게이트를 통과했는가」를 커널 밖에서 다시 판정하게 되고, 두 술어가 갈려도 아무도 모른다. 그 경로도 in_scope 밖이었다 |
| `EvaluateCandidatesUseCase` 생성자에 포트 추가 | 조립 세 자리(dry-run · 단건 · 커밋)를 전부 건드리고 **결국 같은 파일에 닿는다** — dry-run·단건은 기록을 만들지 않아야 하므로 그 자리에 「기록하지 않는 구현」을 꽂는 일만 늘어난다 |
| 청중을 설정값(계정 id)으로 받기 | 없는 id 를 주면 저장 층의 **외래키가 거부**하고 그 거부가 `NotificationInboxOutcome.Failed` 로 조용히 접힌다. 표를 읽으면 그 상태가 애초에 표현되지 않는다 |

### 판단 승인 (팀장 2026-11-06)

- 청중 = `account` **전수 읽기 포트**(단일 운영자 전제). 계정 0 이면 기록 0 이 정상임을 test 가 든다.
  계정 수가 늘면 알림함 행이 그만큼 곱해지는 점은 알려진 제한에 등재한다.
- NOTI-01 은 **가치 게이트 통과 축만** 만든다. capability map 행을 그 축으로 좁혀 적고 「임계 미달
  기록」 축은 후속 등재한다(볼륨·운영 결정이다).
- OPS-08 은 **정책 값**으로 둔다(Spring 설정 아님) — 설정으로 옮기면 기존 배포 설정 전부가 값을
  넣어야 기동하고 그 경로는 out_scope 다. 실 컨텍스트 E2E 가 +40~60s 늘어나는 비용을 승인받았고
  측정값과 함께 알려진 제한에 적는다.

### 병행 레인과 겹치는 자리 (나중에 머지되는 쪽이 맞춘다)

- **paper-bid(V25)** 가 `NotificationInboxRecord` 에 투찰 종류 필수 인자(`NotificationBidKind`)를
  더하는 중이다. 이 slice 는 그 인자를 모르므로, 생산자가 record 를 **만드는 자리를 한 곳으로**
  모아 둔다 — 병합 때 한 줄로 `NONE` 을 싣는다.
- **PR #128(낙찰 수집)** 의 새 use case·러너가 `TruncationCause` 를 소진 `when` 으로 다룰 수 있다.
  `AccessBlocked` 분기는 **컴파일이 강제**하므로 놓치지 않는다.
