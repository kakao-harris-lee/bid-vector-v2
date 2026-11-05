# M9/inbox-vocab 설계 검토 (구현 전)

## 0. 위협 모델 경계

운영자가 보는 **사실 어휘**와 **읽음 상태**가 저장 층·Kotlin·문서·화면 네 자리에서 갈리지 않는다 —
유찰이 패찰로 접히지 않고(한 값이 두 사실을 나르지 않는다), 새 개정이 조용히 읽음으로 남지 않는다.
경계 **밖**: 알림 발송 채널, 페이퍼 투찰 알림, 원천 판정(무엇이 바뀐 개정인가).

## 1. 열거인가 구성인가

- **구성**: `AwardOutcome` 셋 ↔ detail 셋이 **생성자 인자로 묶인다**. 두 결과가 한 detail 을 가리키면
  복원이 `single` 에서 어긋나 왕복 등식이 붉다 — 「접기」가 표현되지 않는 것이 아니라 **복원되지 않는다**.
- **구성**: 읽음 해제 규칙은 `NotificationKind` 전수 `when` 의 `ReadResetPolicy` 이고 SQL 로는 **bind 하나**로
  내려간다. SQL 에 종류 리터럴이 없다(그 부재를 종류 전수에서 생성한 모집단으로 잰다).
- **열거**: detail 어휘 다섯과 짝 CHECK 는 DDL 이라 열거다. 그 열거가 Kotlin·문서와 같은 집합이라는
  **등식**이 세 자리에서 돈다(DB CHECK ↔ enum, openapi ↔ enum, 생성 타입 ↔ 손 코드 표).

## 2. 새 public 표면 전수 (값 획득 축)

| 표면 | 값이 들어오는 자리 | 무엇이 막는가 |
| --- | --- | --- |
| `NotificationDetail.FAILED_OR_REBID` | 어댑터 codec(`valueOf`) · DB 행 | 어휘 등식 셋(DB·openapi·화면) |
| `AwardOutcome`(셋) + `AwardDecided(outcome)` | `BidCaseNotifications` 의 상태 사상 | 상태→사실 전수 등식 · 왕복 등식 |
| `ReadResetPolicy` + `NotificationKind.readResetPolicy` | upsert 의 bind | 정책 분할 등식 + 파티션 고정 등식 |
| V24 두 제약 | 모든 INSERT·UPDATE | 제약 수(5) · 어휘 등식 · 짝 CHECK 전수 |
| `UPSERT_ITEM` 의 bind 7(internal) | `JdbcNotificationInbox` 하나 | 호출자 하나뿐 · 실 DB 거동 test |
| openapi `detail` enum · 생성 타입 union · `NOTIFICATION_DETAIL_DISPLAY` | wire | `satisfies Record<…>`(W-3) · 바이트 등식(W-5) |

**「경계로 처리」 행 없음** — 위 여섯은 모두 실측 대상이다.

## 3. 우회 (≥5)

1. **유찰을 `NOT_AWARDED` 로 접는다** — 사상에서 접거나 `AwardOutcome` 의 detail 인자를 겹치게 둔다.
   막는 것: 왕복 등식(겹치면 `single` 이 어긋나 복원 실패) + 상태→사실 전수 등식 + 「유찰의 detail 이
   패찰과 다르다」 단언.
2. **DB CHECK 만 넓히고 Kotlin 은 그대로**(또는 반대) — 저장할 수 없는 어휘나 복원할 수 없는 행이
   조용히 생긴다. 막는 것: 어휘 등식 test 둘(실 DB CHECK 정의 ↔ enum 전수, openapi ↔ enum 전수).
3. **detail 어휘만 넓히고 짝 CHECK 를 안 고친다** — `AWARD_DECIDED` + `FAILED_OR_REBID` 가 DB 에서
   거부된다. 막는 것: 실 DB 전이 test(항목 1 + 새 detail) + 짝 CHECK 전수 test.
4. **V24 가 옛 제약을 못 지우고 새것만 더한다**(이름 추측 실패) — 막는 것: `DROP CONSTRAINT` 를
   `IF EXISTS` 없이 쓴다(틀린 이름이면 마이그레이션이 **실패**하고 실 DB test 전건이 붉다) + 제약
   수 등식(`notification_inbox` → 5) + detail_code 의 「닫는 정의」가 둘이면 `single` 이 어긋난다.
5. **읽음 해제를 SQL 에서 종류 문자열로 적는다**(`WHEN kind = 'NOTICE_REVISED'`) — 종류를 더할 때
   컴파일이 막지 않는다. 막는 것: 정책이 Kotlin 전수 `when` + bind 이고, `UPSERT_ITEM` 에
   **종류 이름 전수가 하나도 없음**을 `NotificationKind.entries` 에서 생성한 모집단으로 잰다
   (종류를 더하면 모집단이 함께 자란다).
6. **정책 표를 뒤집는다**(`NOTICE_REVISED` 를 기본 정책으로) — 파티션별 거동 test 둘은 각자 자기
   파티션을 돌므로 **둘 다 초록**이 된다. 막는 것: 파티션 자체를 고정하는 등식
   (`ON_NEWER_FACT` 파티션 == `{NOTICE_REVISED}`).
7. **화면이 유찰을 「낙찰 아님」과 같은 문면으로 적는다** — 두 사실이 한 얼굴이 된다. 막는 것:
   detail 표의 라벨 distinct 등식(다섯) + `satisfies Record<NotificationDetail, …>`.
8. **생성 타입을 손으로 고친다**(또는 문서만 고치고 재생성을 잊는다) — 막는 것: W-5 바이트 등식.

## 4. 과잉·미달

- **과잉 아님**: `AwardOutcome` 을 Boolean 둘(`awarded`·`failed`)로 두는 길은 `(true, true)` 를
  표현하므로 열거가 아니라 **구성**이 맞다.
- **미달 자리 하나**: 알림함 행은 개정 **번호**를 담지 않으므로 「새 개정」의 축은 사실의 시각이다.
  열 추가가 out_scope 이고, V2 트리거가 `revision` 과 `updated_at` 을 **함께** 올리므로
  (그리고 내용이 같으면 UPDATE 자체가 돌지 않는다) 그 시각의 전진은 개정과 동치다. 알려진 제한에
  등재한다.

## 5. V24 되돌림 두 갈래

- **미적용 DB**(머지 전 로컬·CI): V24 파일 삭제 + in_scope 경로 `git restore`. Flyway 이력에 없으므로
  흔적 0.
- **적용 DB**: 새 V 로 두 제약을 V23 본문으로 재선언한다. 그 전에 **기존 `FAILED_OR_REBID` 행을
  처분**해야 한다(새 짝 CHECK 밖이라 `ADD CONSTRAINT` 가 검증에서 실패한다). 처분은 **삭제**다 —
  `NOT_AWARDED` 로 접는 것이 D-1 이 거부한 바로 그 접기이고, 알림 항목은 재생성 가능한 파생
  기록이다(다음 전이·훑기가 다시 적는다). 삭제는 **소유자 연결**의 일이다(앱 역할에 DELETE 권한이
  없다). 절차와 실측은 `rollback.md`.
