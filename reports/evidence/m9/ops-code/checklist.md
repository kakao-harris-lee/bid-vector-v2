# M9/ops-code checklist

판정 SHA **`fe17d7d6`**. 계약의 acceptance 넷에 대한 대조표다.

## 1. acceptance 대조

| 계약 acceptance | 재는 자리 | 실측 |
| --- | --- | --- |
| 403/401 비재시도 | `KonepsStatusDispositionTest` — 본문에 **재시도 가능 코드**를 싣고 상태만 차단인 응답에서 서버 카운터 1 | 충족 |
| 간격 위반 없음(주입 시계) | `KonepsCallPacerTest` — 거동 여섯 + **관문을 지난 호출의 원장 시각 간격** | 충족 |
| 평가 → 알림함 1건(실 DB) | `EvaluationCommitInboxProductionTest` — 계정·공고를 심고 production 조립을 직접 세워 돌린 뒤 `notification_inbox` 행을 되읽는다 | 충족 |
| 각 바꿔치우기 변이 RED | `commands.md` §4 — 변이 여섯 전부 RED(통제군·음성 대조 포함) | 충족 |

## 2. 왜 이 술어가 문자열이 아니라 구조인가

| 축 | 구조로 닫은 자리 |
| --- | --- |
| 403 비재시도 | 처분마다 **raw step 타입이 갈린다**. 재시도 술어·사유 변환·최종 접기 셋이 `KonepsRawStep` 을 소진하는 `when` 이라 처분을 늘리면 **컴파일이 막는다**. 상태 코드를 문자열·주석으로 분류하지 않는다 |
| 상태 → 처분 | **선언된 정책 데이터**(버전 붙은 전송 정책 안의 행 목록)이고 조회는 그 표 위에서만 돈다. 중복 상태 코드는 생성자 `require` 가 거부한다(어느 줄이 이기는지가 목록 순서에 숨지 않는다) |
| 간격 | 관문이 `HttpClient` 를 쥔 **유일한** 자리다(D-6G-47, architecture test 가 그 구조를 잰다). 간격을 그 관문 안에 두었으므로 「간격을 비껴가는 경로」가 「관문을 비껴가는 경로」와 같다 — 규율이 아니라 의존 구조가 막는다 |
| 간격의 증거 | **원장에서 읽는다.** 관문이 호출 전에 자기 시계로 `PENDING` 줄을 적으므로 그 줄들의 시각 간격이 곧 나간 호출들의 간격이다 — test 가 자기 계수를 세는 하네스가 아니라 production 이 남기는 기록을 읽는다 |
| 가치 게이트 ↔ 알림함 | 가치 게이트 통과는 커널 안에서 **한 자리로** 관측된다(`Verdict.BidNow` 일 때만 알림 포트가 불린다). 그 자리를 감싸므로 「게이트를 통과했는가」를 다시 판정하는 둘째 술어가 없다 |
| 미리보기가 기록을 남기지 않음 | dry-run·단건 조립은 `RecordingNotificationRequestPort` 를 쓰고 알림함 타입을 **받지 않는다** — 그 분리는 `DryRunCommitSeparationGateTest` 가 이미 재는 축이다 |
| 조립이 구현을 고르지 않음 | 평가 어댑터 패키지의 허용 루트에 알림 **포트**만 더했다. 알림함 **어댑터** 루트는 없고, 그 금지를 양성 대조 한 줄이 잰다(바이트코드 상수 풀 — 소스 문법과 무관) |
| 청중의 정직성 | 계정을 **표에서 읽는다**(설정값 아님) — 없는 계정 id 라는 상태가 표현되지 않는다. 질의는 id 열만 읽는다 |

## 3. 우회 — 고안하고 막은 것

| 우회 | 막는 것 |
| --- | --- |
| 403 본문에 재시도 가능 `resultCode` 를 실어 재시도를 얻는다 | 상태 처분이 **봉투보다 먼저** 돈다. 그 조합이 곧 이 slice 의 RED test 다 |
| 상태 처분을 봉투 뒤로 옮긴다 | 옮기면 `BlockedStep` 을 만들 자리가 없어지고 소진 `when` 셋이 깨진다 |
| 표에서 401·403 행을 지운다 | 「봉투를 건너뛰는 상태 전수」 집합 등식 test 가 붉어진다(부분집합 단언이 아니다) |
| 표의 429 행을 차단으로 바꿔 「전부 비재시도」로 과잉 처방한다 | 통제군 test(429 재시도 회복)가 붉어진다 — 변이로 실측했다 |
| 차단을 쿼터·비재시도 코드와 같은 사유로 접는다 | 셋이 **다른 다음 걸음**을 가진다(회계 갈래 · 재개 가능성 · 수집 실행 중단). `TruncationCause` 전수 게이트와 수집 use case 의 전수 test 가 잰다 |
| 전송 정책을 주입으로 갈아 간격을 0 으로 만든다 | 간격은 **정책 표**의 값이고 주입 지점은 배선 한 자리다. legacy 가 명문화한 「주입은 '어떻게 가져오는가'만 바꾸며 '언제·몇 번·얼마나 기다려'는 못 바꾼다」를 지킨다 — 정책 표 자체를 고치는 길은 열려 있고(그것이 `rollback.md` §3 의 「끄는 법」) 끄면 test 가 붉어진다 |
| 관문을 거치지 않는 둘째 `HttpClient` 를 만든다 | 어댑터 의존 게이트가 `HttpClient` 보유를 관문 하나로 잠근다(이 slice 가 넓히지 않았다) |
| 커밋 조립에서 알림함 감싸기를 뺀다 | 실 DB test 둘이 붉어진다 — **변이로 실측했다**(D-6F10 R1-H-1 이 겨눈 「조립이 커널을 안 쓴다」의 재현) |
| 청중 질의를 아무 계정도 내지 않게 한다 | 같은 둘이 붉어진다 — 변이로 실측했다 |
| 알림함 기록을 채널 뒤로 옮겨 채널 실패가 기록을 지우게 한다 | 순서를 고정하는 test 둘(채널 실패에도 기록 1건 · 기록 실패에도 채널 1건) |
| 설정으로 없는 계정 id 를 주어 알림을 허공에 쓴다 | 청중을 표에서 읽으므로 그 설정 자리가 없다 |
| HTTP 로 알림을 위조한다 | 쓰기 포트를 controller 가 받지 못하고(`app.http.denied-types`) 호출 쌍 허용 목록에 이 slice 가 **한 쌍도 더하지 않았다**(생산자는 `workflow` 안에서 포트를 부른다) |

## 4. 알려진 제한

1. **알림함 기록 실패가 평가 run 의 종료 코드에 세어지지 않는다.** 기록 실패를 채널 결말 어휘에
   섞지 않기 때문이고(두 축을 한 값으로 읽지 않는다), 표지는 쓰기 포트 빈의 로그 한 줄이다
   (D-7C3B-9 가 세운 자리). 7C③-b 의 같은 제한을 그대로 잇는다.
2. **capability-map NOTI-01 의 둘째 acceptance 는 좁혀진 채다.** 그 문장은 「임계 미달이면 기록
   1건, 채널 0건」인데 이 slice 는 「가치 게이트 통과 → 기록 1건」만 만든다. 미달 후보 전수에
   기록을 남기는 것은 볼륨·운영 결정이라(팀장 승인) 후속 등재다.
3. **계정 수가 늘면 알림함 행이 그만큼 곱해진다.** 청중이 `account` 전수이고 단일 운영자를 전제
   하기 때문이다(팀장 승인). 계정이 여럿인 날 필요한 것은 이 질의의 상한이 아니라 **「이 후보가
   누구에게 관련 있는가」라는 판정**이고, 그것은 전략이 계정마다 갈리는 slice 의 몫이다.
4. **PR CI 가 +40~60s 늘어난다**(`commands.md` §3 실측, 팀장 승인). 간격이 정책 값이라 실 컨텍스트
   E2E 가 운영값을 쓴다.
5. **`KonepsCallPacer` 는 스레드 안전을 주장하지 않는다.** 수집이 직렬이라는 계약(legacy 의 직렬
   self-chaining — 동시 청크가 과거 429 를 유발했다) 위에 선다. 동시 호출이 생기는 날 필요한 것은
   이 칸의 잠금이 아니라 **그 계약의 재검토**다.
6. **상태 처분 표에 5xx 가 없다.** 5xx 는 오늘도 봉투 경로로 가 `StructureFailure` 로 접히고 걷기
   안에서 재시도되지 않는다(회계상 일시 실패라 다음 실행이 다시 본다). 그 축을 바꾸는 것은 이
   계약 밖이라 거동을 보존했다.
7. **커밋 E2E 의 「변한 표 == {outbox}」는 이 fixture 가 계정을 심지 않기 때문에 유효하다.**
   평가 커밋 경로에는 이제 `notification_inbox` write 가 있다 — `OPEN-6F10-EVALUATION-DOMAIN-WRITE`
   가 예고한 자리이고, 그 단언을 **옮기는 대신** 전제를 문면으로 명시하고 기록 축의 측정을
   `EvaluationCommitInboxProductionTest` 로 세웠다. 그 fixture 를 계정까지 심는 쪽으로 늘리면 그
   class 의 표별 전후 등식들이 함께 움직여야 한다(그 작업은 그 slice 의 몫으로 남는다).
8. **`NotificationInboxRecord` 를 만드는 자리가 한 곳이다**(`InboxRecordingNotifications`) —
   paper-bid(V25)가 그 타입에 투찰 종류 필수 인자를 더하는 중이라 병합 때 한 줄로 `NONE` 을 싣게
   의도한 모양이다. 나중에 머지되는 쪽이 맞춘다.

## 5. 수정 라운드가 만든 새 파일 ↔ in_scope 대조

라운드 0(첫 제출, 계약 갱신 한 번 포함). 새 파일 **일곱** — 전부 in_scope 안이다.

| 새 파일 | in_scope 항목 |
| --- | --- |
| `adapters/…/koneps/KonepsStatusPolicy.kt` | `adapters/…/koneps/**` |
| `adapters/…/koneps/KonepsCallPacer.kt` | 같음 |
| `adapters/…/koneps/KonepsStatusDispositionTest.kt` | 「각 test」 |
| `adapters/…/koneps/KonepsCallPacerTest.kt` | 같음 |
| `workflow/…/evaluation/InboxRecordingNotifications.kt` + test | `workflow/**/evaluation/**` |
| `adapters/…/notificationinbox/JdbcNotificationAudience.kt` | 계약 갱신 — `notificationinbox` 청중 어댑터(별도 SQL 홀더) |
| `adapters/…/evaluation/EvaluationCommitInboxProductionTest.kt` | 계약 갱신 — `adapters/src/test/…/evaluation/**` |

**계약 갱신 뒤에도 in_scope 밖인 파일 셋**(전부 **필수 인자가 생겨 컴파일이 강제**했고 팀장에게
보고했다):

| 파일 | 왜 |
| --- | --- |
| `app/…/evaluation/EvaluationCommitRunE2ETest.kt` | 커밋 조립 생성자 호출(PR #127 이 가른 파일 — 인자 둘과 전제 문면만 더했다) |
| `app/…/evaluation/EvaluationCommitRunnerTest.kt` | 같은 생성자 호출 |
| `app/…/evaluation/DryRunCommitParityTest.kt` | 커밋 **배선** 메서드 호출 |

`app/…/wiring/EvaluationCommitWiring.kt` 도 그 배선이 포트를 받는 자리라 함께 고쳤다(계약이 승인한
주입의 짝 — 청중 빈을 명시 import 해 이 배선을 혼자 켰을 때의 숨은 의존을 없앴다).
