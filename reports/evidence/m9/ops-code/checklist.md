# M9/ops-code checklist

판정 SHA **`d5f8c0b6`**. 계약의 acceptance 넷에 대한 대조표다.

## 1. acceptance 대조

| 계약 acceptance | 재는 자리 | 실측 |
| --- | --- | --- |
| 403/401 비재시도 | `KonepsStatusDispositionTest` — 본문에 **재시도 가능 코드**를 싣고 상태만 차단인 응답에서 서버 카운터 1 | 충족 |
| 간격 위반 없음(주입 시계) | `KonepsCallPacerTest` — 거동 여섯 + **관문을 지난 호출의 원장 시각 간격** | 충족 |
| 평가 → 알림함 1건(실 DB) | — | **미충족** — 생산자와 포트는 섰고 test 가 잠그지만 production 배선이 in_scope 밖이라 실 DB 경로가 없다(§4) |
| 각 바꿔치우기 변이 RED | `commands.md` §4 — 변이 넷 전부 RED, 통제군 포함 | 충족 |

## 2. 왜 이 술어가 문자열이 아니라 구조인가

| 축 | 구조로 닫은 자리 |
| --- | --- |
| 403 비재시도 | 처분마다 **raw step 타입이 갈린다**. 재시도 술어·사유 변환·최종 접기 셋이 `KonepsRawStep` 을 소진하는 `when` 이라 처분을 늘리면 **컴파일이 막는다**. 상태 코드를 문자열·주석으로 분류하지 않는다 |
| 상태 → 처분 | **선언된 정책 데이터**(버전 붙은 전송 정책 안의 행 목록)이고 조회는 그 표 위에서만 돈다. 중복 상태 코드는 생성자 `require` 가 거부한다(어느 줄이 이기는지가 목록 순서에 숨지 않는다) |
| 간격 | 관문(`KonepsCallGate`)이 `HttpClient` 를 쥔 **유일한** 자리다(D-6G-47, architecture test 가 그 구조를 잰다). 간격을 그 관문 안에 두었으므로 「간격을 비껴가는 경로」가 「관문을 비껴가는 경로」와 같다 — 규율이 아니라 의존 구조가 막는다 |
| 간격의 증거 | **원장에서 읽는다.** 관문이 호출 전에 자기 시계로 `PENDING` 줄을 적으므로 그 줄들의 시각 간격이 곧 나간 호출들의 간격이다 — test 가 자기 계수를 세는 하네스가 아니라 production 이 남기는 기록을 읽는다 |
| 가치 게이트 ↔ 알림함 | 가치 게이트 통과는 커널 안에서 **한 자리로** 관측된다(`Verdict.BidNow` 일 때만 알림 포트가 불린다). 그 자리를 감싸므로 「게이트를 통과했는가」를 다시 판정하는 둘째 술어가 없다 |

## 3. 우회 — 고안하고 막은 것

| 우회 | 막는 것 |
| --- | --- |
| 403 본문에 재시도 가능 `resultCode` 를 실어 재시도를 얻는다 | 상태 처분이 **봉투보다 먼저** 돈다. 그 조합이 곧 이 slice 의 RED test 다 |
| 상태 처분을 봉투 뒤로 옮긴다 | 옮기면 `BlockedStep` 을 만들 자리가 없어지고 소진 `when` 셋이 깨진다 |
| 표에서 401·403 행을 지운다 | 「봉투를 건너뛰는 상태 전수」 집합 등식 test 가 붉어진다(부분집합 단언이 아니다) |
| 표의 429 행을 차단으로 바꿔 「전부 비재시도」로 과잉 처방한다 | 통제군 test(429 재시도 회복)가 붉어진다 — 변이로 실측했다 |
| 차단을 쿼터·비재시도 코드와 같은 사유로 접는다 | 셋이 **다른 다음 걸음**을 가진다(회계 갈래 · 재개 가능성 · 수집 실행 중단). 그 셋을 `TruncationCause` 전수 게이트와 수집 use case 의 전수 test 가 잰다 |
| 전송 정책을 주입으로 갈아 간격을 0 으로 만든다 | 간격은 **정책 표**의 값이고 주입 지점은 배선 한 자리다. legacy 가 명문화한 「주입은 '어떻게 가져오는가'만 바꾸며 '언제·몇 번·얼마나 기다려'는 못 바꾼다」를 그대로 지킨다 — 다만 **정책 표 자체를 고치는 길은 열려 있다**(그것이 §3 의 「끄는 법」이고 끔은 test 를 붉게 만든다) |
| 관문을 거치지 않는 둘째 `HttpClient` 를 만든다 | 어댑터 의존 게이트가 `HttpClient` 보유를 관문 하나로 잠근다(이 slice 가 넓히지 않았다) |
| 알림함 기록을 채널 뒤로 옮겨 채널 실패가 기록을 지우게 한다 | 순서를 고정하는 test 둘(채널 실패에도 기록 1건 · 기록 실패에도 채널 1건) |
| 설정으로 없는 계정 id 를 주어 알림을 허공에 쓴다 | 청중을 **표에서 읽는다**(설정값 아님) — 없는 id 라는 상태가 표현되지 않는다 |

## 4. 알려진 제한

1. **NOTI-01 이 production 에 서지 않았다.** 생산자·청중 포트·test 는 있으나 주입(평가 커밋
   조립)·JDBC 청중 어댑터·청중 빈이 in_scope 밖이라 만들지 않았다. 계약 갱신 요청을 팀장에게
   올렸다. 오늘 `NEW_CANDIDATE` 생산자 수는 여전히 0 이다.
2. **알림함 기록 실패가 평가 run 의 종료 코드에 세어지지 않는다.** 기록 실패를 채널 결말 어휘에
   섞지 않기 때문이고(두 축을 한 값으로 읽지 않는다), 표지는 쓰기 포트 빈의 로그 한 줄이다
   (D-7C3B-9 가 세운 자리). 7C③-b 의 같은 제한을 그대로 잇는다.
3. **capability-map NOTI-01 의 둘째 acceptance 는 좁혀진 채다.** 그 문장은 「임계 미달이면 기록
   1건, 채널 0건」인데 이 slice 의 계약은 「가치 게이트 통과 → 기록 1건」이다. 미달 후보 전수에
   기록을 남기는 것은 볼륨·운영 결정이라 만들지 않았다.
4. **PR CI 가 +40~60s 늘어난다**(`commands.md` §3 실측). 간격이 정책 값이라 실 컨텍스트 E2E 가
   운영값을 쓴다. Spring 설정으로 옮기면 E2E 에서 덮을 수 있지만 기존 배포 설정 전부가 값을
   넣어야 기동하고 그 경로는 out_scope 다.
5. **`KonepsCallPacer` 는 스레드 안전을 주장하지 않는다.** 수집이 직렬이라는 계약(legacy 의 직렬
   self-chaining — 동시 청크가 과거 429 를 유발했다) 위에 선다. 동시 호출이 생기는 날 필요한 것은
   이 칸의 잠금이 아니라 **그 계약의 재검토**다.
6. **상태 처분 표에 5xx 가 없다.** 5xx 는 오늘도 봉투 경로로 가 `StructureFailure` 로 접히고
   걷기 안에서 재시도되지 않는다(회계상 일시 실패라 다음 실행이 다시 본다). 그 축을 바꾸는 것은
   이 계약 밖이라 거동을 보존했다.
7. **장부 갱신이 남았다.** 측정이 늘어난 사실을 적는 자리는 `reports/evidence/m6/6e1/acceptance-trace.md`
   의 OPS-08·OPS-09·NOTI-01 행과 `docs/discovery/m7-screen-data-requirements.md` 의 ⓒ 계수인데
   둘 다 in_scope 밖이라 손대지 않았다. `docs/discovery/capability-map.md` 는 측정 상태 칸이 없고
   이 변경으로 낡는 문장도 없다(OPS-09 의 조건부 acceptance 는 `OPEN-OPS-01` 에 달려 있고 그
   fail-safe 를 그대로 뒀다) — 그래서 계약이 가리킨 「해당 행」에 해당하는 편집이 없다.

## 5. 수정 라운드가 만든 새 파일 ↔ in_scope 대조

라운드 0(첫 제출). 새 파일 **다섯** — 전부 in_scope 안이다.

| 새 파일 | in_scope 항목 |
| --- | --- |
| `adapters/…/koneps/KonepsStatusPolicy.kt` | `adapters/…/koneps/**` |
| `adapters/…/koneps/KonepsCallPacer.kt` | 같음 |
| `adapters/…/koneps/KonepsStatusDispositionTest.kt` | 「각 test」 |
| `adapters/…/koneps/KonepsCallPacerTest.kt` | 같음 |
| `workflow/…/evaluation/InboxRecordingNotifications.kt` + test | `workflow/**/evaluation/**` |

**in_scope 밖에 닿은 파일 둘**(타입이 강제했고 팀장에게 보고했다) —
`workflow/…/collection/CollectNoticesUseCase.kt`(절단 사유 → 다음 걸음 표에 한 분기) ·
`workflow/…/collection/CollectNoticesUseCaseTest.kt`(sealed 전수 test 의 제외 집합과 전용 test).
`TruncationCause` 에 값을 더하면 소진 `when` 이 깨지는 구조라 선택지가 없다.
