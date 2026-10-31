# M7/7C③-b 설계 검토와 종결 점검

base `9463f6e1` · 브랜치 `m7-7c3b/2026-10-31` · 계약 `reports/evidence/m7/7c3b/scope.md`(D-7C3B-1~11).

## §0 설계 검토 (Phase 2.5)

### (0) 위협 모델 경계

**방어한다** — 원천 없이 알림을 지어내는 것(가짜 원천) · 다른 계정의 알림함에 적는 것 · 같은
사실이 항목을 증식시키는 것 · 알림 기록 실패가 원천 쓰기(투찰 기록 · 결과 기록)를 깨는 것 ·
읽기 경로가 몰래 쓰기를 낳는 것 · 쓰기 능력이 HTTP 표면에 노출돼 알림을 위조하는 것 · 알림
항목으로 개인정보가 나가는 것 · 로그로 주체·행 값이 새는 것.

**방어하지 않는다** — 원천 판정 자체의 옳고 그름(마감 판정은 7B, 개찰 승격은 7C⑤-0 몫) ·
채널 발송(R-4 밖) · 운영자가 자기 알림을 많이 만드는 것 · 알림함 **화면**의 거동(7E-3a 몫).

### (1) 열거인가 구성인가

**사상은 구성이고 어휘는 파생이다.** 되비춤은 「명령 → 알림」 표가 아니라 **도착 상태 하나를
받는 소진 `when`** 이다(`reflectedNotificationFact`) — 상태를 더하면 답을 주지 않고는 컴파일되지
않고, 한 사실에 이르는 명령이 둘이어도(`RECORD_OUTCOME` · `CORRECT_OUTCOME`) 키가 하나다.

**모집단도 열거하지 않는다.** 「투찰 미기록」을 상태 목록으로 적지 않고 `DEADLINE_JUDGED_STATES`
(전이표에서 시스템의 마감 판정이 열린 출발 상태)를 쓴다 — 「아직 투찰하지 않았다」와 「마감이
지나면 마감 지남으로 간다」가 **같은 집합**이고, 전이표가 바뀌면 이 술어도 함께 바뀐다. 그 성질을
`NotificationSweepTest` 가 **집합 등식**으로 잰다(발화하는 상태 집합 == `DEADLINE_JUDGED_STATES`).

**창은 매직 넘버가 아니다.** D-1/D-0/D-2 는 호출 자리의 리터럴이 아니라 versioned 정책 데이터이고
(`NOTIFICATION_WINDOW_POLICY`), 조립이 해소하지 못하면 **기동이 실패한다**. 마감 두 창이 겹칠 수
없다는 것은 규율이 아니라 `init` 이다 — 겹치면 한 건이 같은 날 두 항목을 받는다.

**「HTTP 로 알림을 위조한다」는 술어가 아니라 구성으로 막는다.** controller 는 쓰기 포트 타입을
생성자로 받을 수 없고(`app.http.denied-types`), 그 포트를 **부르는 자리**는 (호출자, 메서드) 쌍
전수다(`app.port-call.allowed-pairs` — app 전체에서 둘). 요청 경로가 지나는 좁은 메서드
(`BidPriceRequestFactory.recordInbox`)는 **사실을 하나로 고정**하므로 종류를 고를 인자가 없다.

### (2) 우회 — 아홉을 적고 **열둘을 변이로 쟀다**

| # | 우회 | 무엇이 막는가 | 변이 |
| --- | --- | --- | --- |
| ① | 원천 없는 종류를 그럴듯하게 발화시키기(새 후보·자동 낙찰 대사) | 어느 것도 **짓는 코드가 없다** — 종류 × 원천 표가 착수 보고의 실측이고, 없는 것은 OPEN 으로 남겼다 | ─(부재의 측정) |
| ② | 되비춤 사상에서 한 상태를 조용히 빠뜨리기 | 상태 전수 ↔ 발화 집합 **등식** | M1 |
| ③ | 유찰을 패찰로 접기 | 「유찰은 패찰로 접히지 않는다」 단언 + 어휘 등식 | M2 |
| ④ | 페이퍼 건의 결과로 실투찰 항목을 덮기 | 되비춤의 `asReal()` 가드 · 질의의 `bid_kind = 'REAL'` | M3 · M9 |
| ⑤ | 전이가 저장되지 않았는데 적기(경합·남의 건) | 저장 결과 소진 `when` 의 `Applied` 갈래 하나 | M4 |
| ⑥ | 창을 넓혀 전수 공고를 알림함에 붓기 | 정책 표 + 창 밖 음성 단언 | M5 |
| ⑦ | 달력일 대신 경과 시간으로 세기(23시 마감이 「오늘」로) | 자정 직후 마감의 양성 단언 | M6 |
| ⑧ | 투찰 기록된 건에도 마감을 알리기 | 모집단 ↔ 전이표 집합 등식 | M7 |
| ⑨ | 담기 전의 개정을 「새 소식」으로 알리기 | `openedAt` 비교 | M8 |
| ⑩ | 계정 주체가 없는 경로에서도 적기(주체를 지어내기) | `?.let` 이 호출 자체를 지운다 + 자격 헤더 경로의 사유 단언 | M10 |
| ⑪ | 갱신을 새 기록으로 접어 중복 억제를 가리기 | 포트 결과 ↔ 표시 상태의 1:1 등식 | M11 |
| ⑫ | 기록 실패를 0 으로 끝내 cron 이 모르게 하기 | 종료 코드 매핑 + 대역이 받은 값 | M12 |

측정은 `commands.md` §변이. **통제군 넷**(변이 0, 같은 명령 `--rerun`)이 전부 0 실패다 — 변이의
RED 가 「원래 붉었다」가 아님을 그 넷이 진다.

### (2b) 값 획득 축 — 새 public 표면 전수

| 새 표면 | 얻을 수 있는 값 | 무엇이 막는가(경계로 처리한 행도 실측) |
| --- | --- | --- |
| `NotificationInboxWiring.notificationInboxPort`(쓰기 빈, 신설) | 임의 계정·임의 종류의 알림 기록 | controller 는 받을 수 없다(`app.http.denied-types`) · 부르는 자리는 쌍 전수 둘(`ArchitectureGateTest` 가 app 전체를 본다) |
| `BidPriceRequestFactory.recordInbox(account, notice)` | 그 계정·그 공고의 `BID_PRICE_REQUEST_RECORDED` 한 건 | 사실이 **인자가 아니다**(하나로 고정) · 계정은 세션 속성에서만 오고 없으면 호출이 없다 · 공고는 404 를 지난 실재 행 |
| `BidCaseWorkflow(notifications = …)` | 결과 전이의 되비춤 | 저장 성공 갈래에서만 · 실투찰만 · 상태 사상이 소진 `when` |
| `NotificationSweepCandidatePort`(workflow, 신설) | **전 계정**의 건 행 | 읽기 하나뿐이라 쥐어도 바꿀 수 없다 · 빈이 아니다(배선의 지역 객체 — `NotificationSweepWiringTest` 가 잰다) · `BidCaseStorePort` 를 넓히지 않았다(그 포트의 「첫 인자가 주체」 규율이 선언으로 내려앉지 않게) |
| `SweepNotifications`(workflow, 신설) | 훑기 한 번 | 러너가 받는 단 하나의 협력자 · 창 정책을 조립이 해소해 넘긴다 |
| `NotificationSweepRunner`(app, 신설) | 프로세스 하나를 돌려 알림을 적는다 | `mode=once` 가 없으면 **빈이 없다**(기본 꺼짐) · `OneShotRunnerGuard` 가 둘째 러너를 기동 실패로 · 운영 cron 등록은 아직 없다(7F) |
| `NoticeKey.asNotificationNoticeId()`(workflow, 신설) | 건의 공고 키 → canonical 공고 식별자 | 전역 함수라 갈래가 없다 · 정규화 규칙은 `NoticeNumber.of` 하나 |
| `InboxRecordState` 값 둘 · 사유 둘(계약) | 응답 문면 | Kotlin ↔ OpenAPI 어휘 등식 · 생성 타입 ↔ 화면 표 키 등식 |

**새 빈 둘 · 새 포트 하나 · 새 러너 하나**가 이 slice 의 조립 표면 전부다. 그 밖에 app 조립에
늘어난 주입 가능 타입은 `NotificationInboxPort` 하나이고, 그 줄이 여는 것은 「② 층이 생성자로
받을 수 있다」까지다 — **무엇을 적을 수 있는가**는 위 쌍 전수가 따로 닫는다.

### (3) 과잉·미달

**걷어낸 과잉 둘(실측으로).** ⓐ 공고 키 변환의 `null` 갈래와 그것을 세는 `unkeyedCases` 계수 —
`NoticeKey` 가 빈 번호를 이미 막아 **도달하지 않는 갈래**였다(`NoticeKey(" ")` 가 애초에 만들어지지
않는다). 전역 함수로 바꾸고 계수를 지웠으며, 타입 불변식 **밖**의 행(직접 SQL 로 들어온 공백 번호)에
대한 방어는 질의의 `btrim(…) <> ''` 로 옮겼다 — 한 행이 훑기 전체를 멈추지 않게. ⓑ 훑기 러너의
임대·실행 상태 파일 — 훑기가 멱등이라(NOTI-10 의 PK) 두 run 이 겹쳐도 항목이 늘지 않는다. 보존
러너가 임대를 쥐는 이유(파기는 되돌릴 수 없다)가 여기에 없다.

**남긴 미달 다섯(전부 알려진 제한).** ① 새 후보(STR-08 원천 부재) ② 유찰(detail 어휘 부재,
`OPEN-7C3B-FAILED-BID-DETAIL`) ③ 개찰·낙찰의 **자동 대사**(상호가 canonical 에 없다) ④ 페이퍼
투찰(키에 종류 축이 없다) ⑤ 운영 cron 등록(7F). 다섯 모두 「지어내지 않는다」의 결과이고 §2 가
각각의 자리를 적는다.

## §1 in_scope 대조 — 계약 문면 **밖** 열일곱

기계 산출: `git diff --name-only 9463f6e1..HEAD` 50 경로 ↔ 계약 문면(D-7C3B-10 추가 포함) → 문면 밖 **17**.
**고른 것이 아니라 강제된 것**이다(각각 무엇이 강제했는가):

| 경로 묶음 | 수 | 무엇이 강제했나 |
|---|---|---|
| `app/src/main/.../http/BidPriceRequest{Controller,Response}.kt` · `BidPriceResponseStates.kt` | 3 | D-7C3B-9 가 정한 어휘 셋의 **Kotlin 쪽**이다. 계약 문면은 OpenAPI 만 들었지만 어휘 등식 test 가 두 자리를 같이 잰다 — 한쪽만 고치면 붉다 |
| `app/src/test/.../AppHttpDependencyGateTest.kt` · `AccountCarrierStructureTest.kt` · `ConstantTimeComparisonStructureTest.kt` | 3 | **공유 래칫 셋**. 각각 면제 계수(75→80) · 주체 접근자 호출자 집합 · `areEqual` 허용 목록이고 전부 **집합 등식**이라 새 자리가 생기면 등재해야 한다 |
| `app/src/test/.../{HttpTestApplication,InMemoryBidCaseStore,BidPriceTestWiring,BidPriceRequest*Test}.kt` | 7 | 생성자 시그니처와 응답 어휘가 바뀌어 **컴파일·단언이 강제**했다. 둘(`InMemoryNotificationInbox` · `NotificationTriggerEndpointTest`)은 새 파일이고 전자는 중복 억제 키를 지는 이중체다 |
| `docs/runbook/m7-7c3b-notification-sweep.md` | 1 | **D-7C3B-8 의 문면**(「runbook 에 항목만 적는다」)이 지시했다. 계약 in_scope 목록에는 `docs/runbook/**` 이 없다 |
| `web/src/screens/WorksheetScreen.test.tsx` | 1 | 생성 타입 어휘가 늘어 그 화면 test 의 fixture 가 **`tsc` 에서 멈췄다**. D-7C3B-10 은 `worksheetDisplay` 둘만 들었는데 같은 강제가 셋째 파일에 닿았다 |
| `app/src/test/.../InMemoryNotificationInbox.kt` · `NotificationTriggerEndpointTest.kt` | (위 7 에 포함) | — |
| `workflow/src/test/.../FakeBidCaseStore.kt` | (문면 안) | 파일 private 이라 새 test 가 쓸 수 없었다 — `workflow/**` 의 「해당 test」 안이다 |

**보고 항목**: 위 다섯 묶음 가운데 **runbook 하나와 `WorksheetScreen.test.tsx` 하나**가 계약 갱신이
필요한 자리다(나머지 열다섯은 공유 래칫과 컴파일 강제라 7C③ 의 선례와 같은 분류).

## §2 알려진 제한

1. **새 후보(`NEW_CANDIDATE`)는 발화하지 않는다.** 감시 실행 이력(STR-08)이 없고, 출하 평가 커밋
   러너는 **싱글턴 전략**을 읽어 「누구의 후보인가」를 모른다. 어휘는 두고 원천은 OPEN 이다.
2. **유찰(`FAILED_OR_REBID`)은 알림을 남기지 않는다.** detail 어휘에 자리가 없고 `NOT_AWARDED` 로
   접으면 「졌다」와 「아무도 못 땄다」가 한 값이 된다 — `OPEN-7C3B-FAILED-BID-DETAIL`.
3. **개찰 완료·낙찰 확정은 자동 대사가 아니다.** 운영자가 기록한 전이의 되비춤이다. 그래서
   NOTI-04 · SET-02 의 capability 판정을 올리지 않았다(D-7C3B-7 ③).
4. **페이퍼 투찰은 알림을 내지 않는다.** 중복 억제 키에 투찰 종류 축이 없어 같은 공고의 페이퍼 건이
   실투찰 건의 항목을 덮는다 — 가상 연습이 실제 결과를 가리지 않는 쪽을 골랐다.
5. **시간 기반 셋은 지금 배포에서 발화하지 않는다.** 러너는 코드로 서 있고 기본값이 꺼짐이며 운영
   host 의 cron 등록이 아직 없다(7F). 하네스에서도 발화하지 않는다(별 프로세스다).
6. **알림 기록 실패는 원천을 되돌리지 않는다.** 전이·평가는 남고 알림만 잃는다. 표지는 조립의 로그
   한 줄(요청 경로)과 러너 보고의 `failed` 계수·종료 코드 2(훑기)다. 요청 경로의 실패는 응답의
   `INBOX_WRITE_FAILED` 로도 보인다.
7. **마감 창은 하루씩이라 훑기를 하루 빠뜨리면 그날 몫을 잃는다.** 서류 기한 창만 폭이 있어 회수된다.
   마감 두 창에 같은 폭을 줄 수 없는 이유는 두 종류가 **다른 항목**이기 때문이다(창 정책 `init`).
8. **공고 개정은 「무엇이 바뀌었는지」를 말하지 않는다.** canonical 행의 개정 번호가 올랐다는 사실
   뿐이고 이전 값은 `notice_audit` 에 남는다. 정정으로 **차수가 바뀌는** 공고는 키가 달라 다른 건이다.
9. **같은 키의 재기록은 두 번째 개정을 안읽음으로 되돌리지 않는다.** `NOTICE_REVISED` 는 detail 이
   없어 7C③ 의 `read_at` 규칙상 읽음이 유지된다 — 「이미 본 공고가 또 바뀌었다」가 배지에 서지 않는다.
10. **되돌려진 마감 건은 훑기가 「읽기 뒤의 상태」를 본다**(CR M-1). 「마감 지남」의 되돌림
    (`REVOKE_DEADLINE_PASSED`)은 **읽기 시 정산**이 돌리므로, 운영자가 그 건을 한 번도 열지 않은
    채 정정 공고로 마감이 미뤄지면 저장된 상태는 아직 `DEADLINE_PASSED_UNRECORDED` 다 — 훑기의
    모집단(`DEADLINE_JUDGED_STATES`)에 들지 않아 마감 알림이 서지 않는다. **술어를 바꾸지 않았다**:
    훑기를 읽기 경로에 얹지 않는다는 D-7C3B-8 결정의 귀결이고, 훑기가 정산까지 하면 「읽기가 쓰기를
    낳는다」를 피한 자리가 러너로 옮겨 올 뿐이다. 그 건은 **공고 개정 축**이 집으므로 알림 자체가
    0 은 아니다(개정 알림이 선다).
11. **알림함 목록 질의에 `LIMIT` 상한만 있고 전체 상한이 없다**(CR L-3, 등재). 페이지 크기는 100 으로
    닫혀 있으나 한 계정의 누적 항목 수를 제한하는 자리는 보존 파기(90일)뿐이다.
12. **여정이 자기 알림함 행 수를 절대값으로 세지 않는다**(CR L-4, 등재). 공유 하네스라 다른 여정의
    항목이 같은 계정에 섞이고, 그래서 종류는 포함으로·개수는 DOM ↔ 응답 등식으로만 잰다.
13. **`NOTICE_REVISED` 의 둘째 개정은 안읽음으로 돌아오지 않는다**(CR OQ →
    `OPEN-7C3B-REVISION-REUNREAD`). 그 종류는 detail 이 없어 7C③ 의 `read_at` 규칙상 재기록이 읽음을
    유지한다 — 「이미 본 공고가 **또** 바뀌었다」가 배지에 서지 않는다. 고치려면 detail 축을 쓰거나
    읽음 해제 규칙을 종류별로 가르는 결정이 필요하다.
14. **알림함 읽기 이중체와 쓰기 이중체가 HTTP test 조립에서 이어져 있지 않다.** 그래서 「적힌 항목이
    `GET /api/notifications` 로 돌아 나온다」는 **실 DB 축**(`NotificationSweepCandidatesTest`)과
    **여정**이 잰다. 표면 test 는 「포트를 어떤 주체·어떤 사실로 불렀는가」까지다.

## §3 rollback 과 끄는 법

- **코드를 되돌리지 않고 끄는 법**: ⓐ 훑기는 `bidvector.notification-sweep.mode` 를 주지 않는다
  (기본값이 그것이다) ⓑ 요청 경로 둘은 `NotificationInboxWiring.notificationInboxPort` 빈을 빼면
  사라진다 — 세 자리가 모두 그 타입을 요구하므로 기동이 실패하고, 그것이 「반만 켜진 상태」를
  만들지 않는 자리다.
- **마이그레이션 0** — V23 이 이미 어휘 여덟과 열 단위 GRANT 를 들고 있고 `RetentionTableRegistry` 에
  파기 행도 있다. 되돌림에 DB 작업이 없다.
- 경로 한정 복원과 실측은 `rollback.md`.
