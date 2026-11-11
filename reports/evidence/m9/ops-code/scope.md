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
| `adapters/src/test/kotlin/bidvector/adapters/evaluation/**` | 그 경로의 test. **PR #127(Telegram, 미머지)이 갈라 둔 `EvaluationCommitRunE2ETest.kt`·`RelayFailureInjection.kt` 는 건드리지 않고 새 파일로** (#127 은 2026-11-08 머지 — 이 slice 가 늦은 쪽으로 맞췄다) · **정정(2026-11-11, cr r7 LOW-C)**: 이 행이 드는 두 파일은 **이 경로가 아니라 `app/src/test/kotlin/bidvector/app/evaluation/`** 에 있고, `EvaluationCommitRunE2ETest.kt` 는 이 slice 가 **실제로 편집했다** — 그 승인은 아래 「사후 등재」 문단이 그 파일을 이름으로 들어 한 것이고, in_scope 준수는 깨지지 않았다. 어긋난 것은 **읽히는 순서**뿐이다 |
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

## 계약 갱신 2026-11-06 (팀장, 2차) — 수정 라운드 1

**더하는 in_scope 경로**

| 경로 | 사유 |
| --- | --- |
| `workflow/src/main/kotlin/bidvector/workflow/collection/CollectOpeningResultsUseCase.kt` · `OpeningSampleFramer.kt` | cr HIGH · v M-1 — 개찰(6G) 경로의 `haltOf`·표본 경계가 `else` 로 `AccessBlocked` 를 삼켜 403 뒤에도 남은 후보마다 호출을 낸다. 계약이 `HALT_RUN` 의 근거로 든 바로 그 해악이므로 **등재가 아니라 코드로 닫는다**(팀장 결정). `else` 를 지워 소진 `when` 으로 바꾸고 차단은 멈춘다 — 그래서 「AccessBlocked 분기는 컴파일이 강제한다」가 이 두 자리에서도 참이 된다 |
| `workflow/src/test/kotlin/bidvector/workflow/collection/**` | 그 거동의 test(차단 뒤 남은 후보 호출 0 · 통제군 429 는 기존 거동) |
| `procurement/src/test/kotlin/bidvector/procurement/AxisLastObservedAtTest.kt` | v B-1 — `ALL_TRUNCATION_CAUSES` 가 새 사유를 모른다(`procurement/**` 는 이미 in_scope 이나 사유를 남긴다) |

**사후 등재(v L-2)** — 착수·1차 갱신 표에 없던 채 커밋된 네 경로를 여기서 승인한다:
`app/src/main/kotlin/bidvector/app/wiring/EvaluationCommitWiring.kt`(커밋 조립에 청중·알림함 포트 `@Import`) ·
`app/src/test/kotlin/bidvector/app/evaluation/{DryRunCommitParityTest,EvaluationCommitRunE2ETest,EvaluationCommitRunnerTest}.kt`
(생성자 인자 둘에 따른 조립). `app/…/EvaluationCommitRunE2ETest.kt` 는 PR #127 과 겹칠 수 있다 — 늦게 머지되는 쪽이 맞춘다.
(#127 은 2026-11-08 머지 — 이 slice 가 늦은 쪽으로 맞췄다. 머지 커밋 `d38f7115` 가 복원 원천
`22c77cbb` 의 조상이고, 이 계약이 적은 「미머지」는 그때의 사실이다.)

**H-1 처방(v)** — 출하 정책 `KONEPS_HTTP_POLICY` 를 resolve 한 그 값 위에서 ① 403 한 번이면 호출 1회 ② 연속 호출 간격 ≥ 출하 `minimumCallInterval`(주입 시계)을 잰다. 상수 표로 만든 test 정책 위의 시나리오로 갈음하지 않는다. 변이 `minimumCallInterval=ZERO` · 출하 `statusRules` 에서 403 행 제거가 각각 RED.

## 계약 갱신 2026-11-10 (팀장, 3차) — 다섯째 병합이 강제한 세 자리

main `07140729`(PR #128 낙찰 수집을 담은 자리) 병합 `1d53dd6e` 뒤 **셋이 깨졌다.** 셋 다 이 slice 의
새 절단 사유 `AccessBlocked` 와 main 의 새 낙찰 갈래가 만난 자리이고, 가리킨 것은 사람이 아니라
**타입과 게이트**다.

**더하는 in_scope 경로 둘**

| 경로 | 사유 |
| --- | --- |
| `workflow/src/main/kotlin/bidvector/workflow/collection/CollectAwardListUseCase.kt` | main `07140729` 이 만든 파일. 그 `haltOf` 가 `TruncationCause` 소진 `when` 이고 이 slice 가 `AccessBlocked` 를 들여와 **컴파일이 멈췄다**(`'when' expression must be exhaustive`). 처분은 새 결정이 아니다 — 이 계약의 「#128 의 소진 `when` 은 컴파일이 강제한다」와 main 저자가 그 `when` KDoc 에 적어 둔 답(403 계열 차단은 멈춤)이 둘 다 미리 정해 두었다 |
| `workflow/src/test/kotlin/bidvector/workflow/collection/CollectAwardListUseCaseTest.kt` | 그 처분 표의 전수 test. 모수를 sealed 계층에서 도출하므로 새 사유가 병합과 **동시에** 표에 들어왔고, 기대 행이 없어 등식이 붉었다 |

**셋째 자리는 이미 in_scope 다** — `app/src/test/kotlin/bidvector/app/wiring/CollectionWiringSleeperTest.kt`.
main 의 `AwardCollectionWiring` 이 `konepsTransportFor` 를 부르는데 탐침표에 그 클래스가 없어 집합
대조가 `Some elements were unexpected: ["wiring/AwardCollectionWiring"]` 로 먼저 붉어졌다. 그 test 의
KDoc 이 「셋째 클래스가 전송을 만들면 거동을 재지 않은 채 지나갈 수 없다」를 **주장**했고 병합이 그
주장을 **실측**으로 바꿨다. 탐침은 키만 더하지 않고 그 클래스의 production 조립 메서드로 거동까지
잰다(cr r6 M-2 가 지목한 손 불변식을 피한다).

**이 라운드의 범위 문면을 고친다.** 앞 라운드의 「production 거동 변경 0」은 더 이상 참이 아니다 —
**병합이 강제한 한 줄**이 있다: `haltOf` 의 `TruncationCause.AccessBlocked -> AwardCollectionHalt(null, cause)`.
그 한 줄이 없으면 컴파일되지 않으므로 「등재하고 넘긴다」가 선택지가 아니었다.

**복원 목록과 공유 파일은 기계가 낸다**

- 복원 목록 **44**(앞 판 42) — `git log --no-merges --format= --name-only <원천>..HEAD -- . ':(exclude)reports/evidence/m9/ops-code' | sort -u`.
  `git diff --name-only` 산출과 `comm` **양방향 빈 출력**으로 대조했다.
- 공유 파일 **10** — `comm -12 <복원 목록> <git diff --name-only df4976cc 07140729>`. 문서가 말하던
  「여섯」은 그때도 적었고(v r6 L-3 가 잡은 자리의 재발), 그 사유는 **열거를 손으로 적고 수를 따로
  적은** 데 있다. 이제 열거와 계수가 **같은 산출**에서 나온다.

**표지 축을 바꾼다 — `AccessBlocked` 는 더 이상 내 표지가 아니다.** 원천 `07140729` 에 그 어휘가
**4 파일**(award-ingest 가 쓴 KDoc 둘 · 그 slice 의 evidence 둘)에 있다. 상류가 내 이름을 **예고로
채택한** 경우이고, 그대로 두면 「복원 뒤 0」이 영원히 거짓이 된다. 그 이름을 **보존 쪽(4/4)** 으로
옮기고, 같은 축의 내 표지는 `ACCESS_BLOCKED`(원천 **0** · HEAD **5**)로 바꾼다.

**복원 원천도 옮긴다** — `bd2f450e` → `07140729`(이 브랜치가 마지막으로 병합한 main 의 고정 SHA).
`bd2f450e` 는 `07140729` 의 조상이라 그것으로 복원하면 그 사이 main 의 줄을 함께 되돌린다.
