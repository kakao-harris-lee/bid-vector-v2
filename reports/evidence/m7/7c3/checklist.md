# M7/7C③ — 점검표

## 0. 설계 (Phase 2.5 — code 0, 팀장 수령 대상)

### 0.1 이 slice 가 세우는 것 한 문장

**알림함은 「한 계정 · 한 공고 · 한 종류」에 한 항목인 표이고, 그 항목에는 자유 문자열 칸이
없다.** 중복 억제는 파생 키 문자열이 아니라 **그 네 열의 PRIMARY KEY** 가 지고, PII 부재는
문장이 아니라 **표의 TEXT 열 전수가 닫힌 어휘이거나 공고 키라는 카탈로그 성질**이 진다.

표시 문구(공고명·기관·마감)는 **저장하지 않고 읽을 때 `notice` 표에서 합친다** — 그래서
「제목 문구가 바뀌어도 중복 억제 키가 흔들리지 않는다」가 규율이 아니라 **구조**다(저장된
항목에 제목이 아예 없으므로 흔들릴 값이 없다).

### 0.2 트리거 여섯 + 둘 — main 에 원천이 있는가 (계약이 요구한 COL-01 실측)

| # | 종류 | 원천 capability | main 의 원천 | 이 slice 가 배선하는가 |
|---|---|---|---|---|
| 1 | 새 후보(자격 통과) | STR-08 · QUAL-02 | **없음** — 감시 실행 이력·신규/지속 diff 표 0 | 아니오 |
| 2 | 마감 D-1(투찰 미기록) | BID-01 | **없음** — 입찰 건 표는 7B(미머지) | 아니오 |
| 3 | 마감 D-0 | BID-01 | 〃 | 아니오 |
| 4 | 1순위 서류 기한 D-2 | BID-01 | 〃 | 아니오 |
| 5 | 개찰 완료(잠정 1위 여부) | SET-02 | 개찰 결과 표는 있으나 **대사 판정 경로 없음**(7C⑤) | 아니오 |
| 6 | 낙찰/패찰 확정 | SET-01 · NOTI-04 | 〃 | 아니오 |
| 7 | 공고 변경(정정공고) | COL-01 | **변경 감지는 있다 · 관련성은 없다** (아래) | **아니오 — 사유 아래** |
| 8 | 투찰가 요청 결과 기록 | (7C② 의 effect, R-4) | 7C②(미머지) | 아니오 |

**COL-01 실측 — 두 겹을 갈라서 본다.**

- **변경 감지는 main 에 있다.** `notice` 표는 `revision BIGINT NOT NULL DEFAULT 1` 을 들고,
  canonical 갱신마다 AFTER UPDATE 트리거가 `notice_audit` 에 이전 행을 쌓는다. 적재 경로는
  갱신을 **값으로** 낸다 — `JdbcNoticeRepository` 의 갱신 분기가 `PersistOutcome.Updated(revision+1)`
  을 돌려주고, 그 값이 `CollectNoticesUseCase` 를 지나 `SlotTally.wrote` 에서 종류별로 세어진다.
  그 use case 는 출하 조립에 **실제로 꽂혀 있다**(`CollectionWiring` → `CollectionRunner`).
  즉 「이 공고가 바뀌었다」는 사실이 production 경로에서 **이미 흐른다**.
- **관련성(이 계정이 이 공고에 관심이 있는가)은 main 에 없다.** 그 판정의 원천은 STR-08 감시
  이력이거나 BID-01 입찰 건이고 **둘 다 main 에 없다**. 평가 경로(`EvaluateCandidatesUseCase`)는
  dry-run 전용 어댑터(`RecordingNotificationRequestPort`)만 꽂혀 있어 영속되는 판정이 없다.
- **그래서 배선하지 않는다.** 관련성 없이 배선하면 수집 run 마다 **갱신된 공고 전수**가 알림함에
  들어온다 — 이것은 「알림」이 아니라 수집 회계의 복제이고, 운영자의 안읽음 계수를 무의미하게
  만들어 **진짜 알림을 가린다**(아래 위협 모델 「방어하지 않는다」가 아니라, 이 slice 가 스스로
  만들 수 있는 피해다). 가짜 원천을 만들지 않는다는 계약 문면과 같은 방향이다.
- **대신 남기는 것**: 종류 어휘에 `NOTICE_REVISED` 를 두고(CHECK 어휘를 늘리는 둘째 마이그레이션을
  피한다 — 7B 가 `PRICE_RECEIVED` 에 쓴 것과 같은 선례), 그 종류의 중복 억제 키 규칙을 못박고,
  「관련성 원천이 서는 slice 가 이 포트를 부른다」를 알려진 제한으로 등재한다.

**결론**: 이 slice 는 트리거 **0 개**를 배선한다. 알림함 자체(표·쓰기 포트·중복 억제·읽기 API·
읽음 전이·건별 묶음)는 전부 선다. 계약의 「원천이 있으면 배선한다」는 조건절이고 **조건이
성립하지 않았다** — 성립하지 않은 사실을 실측으로 적는 것이 이 항목의 산출물이다.

### 0.3 종류 어휘와 중복 억제 키 규칙 (다른 레인이 의존 — 확정 뒤 변경은 팀장 경유)

`NotificationKind`(enum, 여덟):

| 값 | 뜻 | detail | 부를 레인 |
|---|---|---|---|
| `NEW_CANDIDATE` | 새 후보(자격 통과) | 없음 | STR-08 원천 slice |
| `BID_DEADLINE_IMMINENT` | 마감 D-1 · 투찰 미기록 | 없음 | 7B |
| `BID_DEADLINE_TODAY` | 마감 D-0 · 투찰 미기록 | 없음 | 7B |
| `DOCUMENT_DEADLINE_IMMINENT` | 1순위 서류 기한 D-2 | 없음 | 7B |
| `OPENING_COMPLETED` | 개찰 완료 | `PROVISIONAL_FIRST` \| `NOT_PROVISIONAL_FIRST` | 7C⑤ |
| `AWARD_DECIDED` | 낙찰/패찰 확정 | `AWARDED` \| `NOT_AWARDED` | 7C⑤ |
| `NOTICE_REVISED` | 공고 변경(정정공고) | 없음 | COL-01 관련성 원천 slice |
| `BID_PRICE_REQUEST_RECORDED` | 투찰가 요청 결과 기록(R-4 의 끝) | 없음 | 7C② |

**D-1 과 D-0 을 한 종류 + detail 로 접지 않는다**: 접으면 D-1 항목이 D-0 으로 갱신되며 **하나로
수렴**해 「어제 D-1 을 봤다」는 사실이 사라진다. 두 종류면 두 항목이고 각각 읽음 상태를 갖는다.
같은 이유로 `OPENING_COMPLETED` 의 잠정 1위 여부는 **detail** 이다 — 그것은 **갱신되어야 하는**
값이다(잠정 1위였다가 아니게 되면 그 항목이 바뀌는 것이 맞다).

**중복 억제 키는 종류마다 다르지 않다 — 여덟 전부 같다.**

```
dedupe key = (account_id, kind, notice_number, notice_round)
```

이것이 **PRIMARY KEY 자체**다(파생 문자열 열을 두지 않는다 — 두면 키와 그 입력이 갈릴 자리가
생긴다). 트리거 여덟 전부가 「한 공고에 대한 한 종류의 사실」이라 결이 같다.

**재기록의 거동**(한 문장씩, test 가 각각 잠근다):

1. 같은 키 재기록 → **행 1개**. `occurred_at`·`updated_at` 이 이동한다.
2. detail 이 **같으면** `read_at` 을 건드리지 않는다 — 읽은 항목이 조용히 안읽음으로 돌아가지 않는다.
3. detail 이 **다르면** `read_at = NULL` — 사실이 바뀌었으면 다시 봐야 한다.
4. `created_at` 은 움직이지 않는다(보존 cutoff 축 · 열 단위 GRANT 로 앱 역할이 못 바꾼다).

단일 문장 upsert 로 네 줄 전부를 표현한다(`ON CONFLICT … DO UPDATE SET … read_at = CASE WHEN
detail_code IS DISTINCT FROM EXCLUDED.detail_code THEN NULL ELSE … END`, `RETURNING (xmax = 0)` —
`opening_result` upsert 의 기존 관례).

### 0.4 포트 시그니처 (다른 레인이 부르는 것은 ① 하나다)

패키지 `bidvector.workflow.notificationinbox`(새 패키지 — `workflow.access`·`procurement` 로 나가는
간선만 생기고 들어오는 간선이 없어 순환이 닫히지 않는다).

```kotlin
// ① 쓰기 포트 — 7C② · 7C⑤ · 7B · STR-08 원천 slice 가 이것만 부른다.
fun interface NotificationInboxPort {
    fun record(record: NotificationInboxRecord): NotificationInboxOutcome
}

data class NotificationInboxRecord(
    val account: AccountId,          // workflow.access — 주체 없이 부를 수 없다
    val notice: NoticeId,            // procurement
    val fact: NotificationFact,
    val occurredAt: Instant,
)

/** 불법 쌍이 **표현되지 않는다** — detail 을 갖는 종류만 그 자리를 갖는다. */
sealed interface NotificationFact {
    val kind: NotificationKind

    data object NewCandidate : NotificationFact
    data object BidDeadlineImminent : NotificationFact
    data object BidDeadlineToday : NotificationFact
    data object DocumentDeadlineImminent : NotificationFact
    data object NoticeRevised : NotificationFact
    data object BidPriceRequestRecorded : NotificationFact

    data class OpeningCompleted(val provisionallyFirst: Boolean) : NotificationFact
    data class AwardDecided(val awarded: Boolean) : NotificationFact
}

sealed interface NotificationInboxOutcome {
    data object Created : NotificationInboxOutcome
    data object Updated : NotificationInboxOutcome
    data object Failed : NotificationInboxOutcome   // DB 실패를 값으로(예외를 업무 흐름에 쓰지 않는다)
}

// ② 읽기·읽음 처리 포트 — 운영자 표면만 부른다(controller 는 이것도 직접 받지 않는다).
interface NotificationInboxStore {
    fun list(account: AccountId, request: NotificationPageRequest): NotificationInboxPage
    fun markRead(account: AccountId, item: NotificationItemKey, at: Instant): MarkReadOutcome
    fun markNoticeRead(account: AccountId, notice: NoticeId, at: Instant): MarkReadOutcome
}

data class NotificationItemKey(val kind: NotificationKind, val notice: NoticeId)
data class MarkReadOutcome(val markedCount: Int, val unreadCount: Int)
```

- **두 포트의 전 메서드 첫 인자가 `AccountId`** 다(7B 가 세운 「주체 없이 부르는 포트 0」 규율).
  예외 없다 — 알림함은 전부 사용자 소유 데이터다.
- `NotificationFact` 의 하위 타입 생성자는 **공개**다(다른 레인이 지어야 한다). 대신 그 값으로
  **저장에 닿지 못한다** — 저장은 `NotificationInboxPort` 구현만 하고, app production 의 어느
  클래스도 그 포트의 메서드를 부르지 못한다(0.8 ①).
- `Failed` 를 값으로 내는 것은 `OutboxNotificationRequestPort` 의 선례와 같다. **호출부가 그것을
  무시하면 dual-write 가 된다**는 같은 성질을 알려진 제한으로 잇는다(호출부는 다른 레인이다).

### 0.5 V23 마이그레이션 (초안)

번호는 7B(V21) · 7C④(V22) 와 겹치므로 **PR 직전 재확인**하고 뒤에 머지되는 쪽이 다시 매긴다
(D-6A1-12 의 정의).

```sql
SET LOCAL lock_timeout = '5s';   -- V19 가 세운 관례(기동 정지보다 실패가 싸다)

CREATE TABLE notification_inbox (
    account_id    BIGINT NOT NULL REFERENCES account (id),     -- R-2
    kind          TEXT   NOT NULL CHECK (kind IN (여덟 어휘)),
    notice_number TEXT   NOT NULL CHECK (notice_number <> ''),
    notice_round  TEXT   NOT NULL CHECK (notice_round ~ '^[0-9]{3}$'),

    -- 닫힌 어휘 넷 또는 NULL. 종류별 짝을 CHECK 가 진다(아래).
    detail_code   TEXT   CHECK (detail_code IN (넷 어휘)),

    occurred_at   TIMESTAMPTZ NOT NULL,                 -- 사실의 시각(호출자 시계)
    read_at       TIMESTAMPTZ,                          -- NULL = 안읽음
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),   -- 보존 cutoff 축(A-2: DB 가 찍는다)
    updated_at    TIMESTAMPTZ NOT NULL,                 -- 재기록 증거

    PRIMARY KEY (account_id, kind, notice_number, notice_round),

    -- 종류 ↔ detail 짝. 「detail 을 갖는 종류만 갖는다」를 표 층에도 싣는다(심층 방어 —
    -- Kotlin 쪽은 sealed 로 애초에 표현되지 않는다).
    CHECK (
        (kind = 'OPENING_COMPLETED' AND detail_code IN ('PROVISIONAL_FIRST','NOT_PROVISIONAL_FIRST'))
     OR (kind = 'AWARD_DECIDED'     AND detail_code IN ('AWARDED','NOT_AWARDED'))
     OR (kind NOT IN ('OPENING_COMPLETED','AWARD_DECIDED') AND detail_code IS NULL)
    ),
    CHECK (read_at IS NULL OR read_at >= created_at)
);

-- 목록 질의(계정 안에서 최신 순) · 보존 cutoff(V19 관례와 같은 축)
CREATE INDEX notification_inbox_account_recent_idx ON notification_inbox (account_id, occurred_at DESC);
CREATE INDEX notification_inbox_retention_cutoff_idx ON notification_inbox (created_at);

-- GRANT — **열 단위**. 앱 역할이 소유 계정·종류·공고 키·적재 시각을 바꿀 수 없다.
GRANT SELECT, INSERT ON notification_inbox TO bidvector_app;
GRANT UPDATE (detail_code, occurred_at, read_at, updated_at) ON notification_inbox TO bidvector_app;
-- DELETE·TRUNCATE 는 주지 않는다(파기는 소유자 연결의 일이다 — V8·V12·V15·V20 관례).
```

**`notice` 에 FK 를 걸지 않는다 — 사유를 DDL 주석에 적는다.** `notice` 는 **파기 대상**이다
(90일, `Purge`). FK 를 CASCADE 없이 걸면 공고 파기가 FK 위반으로 **멈추고**, CASCADE 로 걸면
운영자의 알림함이 90일째에 **조용히 비워진다**. 알림함은 「그 사실이 있었다」의 기록이므로 공고의
수명보다 오래 살아야 한다. 공고 키 두 열은 **주제 참조**일 뿐이고, 읽기 경로는 `notice` 를
LEFT JOIN 해 제목·기관·마감을 얻는다 — 공고가 파기됐으면 그 칸이 `null` 이고 항목은 종류와 공고
번호로 여전히 읽힌다. (사유를 적지 않으면 「빠뜨린 FK」와 구별되지 않는다 — 7B 가 포트 KDoc 에
세운 같은 규율.)

**PK 밖의 UNIQUE 를 만들지 않는다.** 중복 억제가 PK 자체이므로 스키마 스냅샷 래칫의 UNIQUE 축
(`expectedUniqueConstraints`, 오늘 한 원소)은 **건드리지 않는다**. 시퀀스도 만들지 않는다
(IDENTITY PK 를 두지 않으므로 — V6 가 적은 `GRANT … ALL SEQUENCES` 스냅샷 함정을 피한다).

### 0.6 보존·파기 판정 (6B-3 대조 — 계약이 요구한 항목)

`RETENTION_TABLE_RULES` 는 **DB 카탈로그와 집합 등식**이다(`RetentionRegistrySchemaEqualityTest`).
그래서 새 표는 **분류를 적지 않으면 초록이 되지 않는다** — 선택이 아니라 강제다.

**판정: `Purge("notification_inbox", "created_at", TimeOnly)`.**

- `Exempt` 의 여덟 사유 어느 것도 맞지 않는다 — 싱글턴 설정도, 멱등 원장도, 개정 이력도, 수집
  회계도, 참조 데이터도, 계정 신원도 아니다. 90일 지난 알림을 지워서 잃는 것은 「그 알림이
  있었다」뿐이고, 그 사실의 원천(공고·개찰·입찰 건)은 각자의 표가 든다.
- 기준 시각은 `created_at`(A-2 의 적재 시각 축 · DB 가 찍고 앱 역할이 못 바꾼다 · NOT NULL
  TIMESTAMPTZ — 등식 test 의 네 요구를 전부 만족).
- 종단 상태 조건이 필요 없다(`TimeOnly`) — `outbox` 와 달리 「미발송」 같은 상태 축이 없다.
  읽음/안읽음은 **파기 조건이 아니다**: 안읽음 항목을 영구 보존하면 90일 약속이 거짓이 된다.
  (안읽음 항목도 90일에 사라진다는 것을 알려진 제한으로 등재한다.)
- `RETENTION_SEEDS` 에 심기 항목을 더한다(그 fixture 는 분류된 표 **전수**와 등식이다 — 새 표가
  분류만 되고 재지 않는 일이 없다).
- CASCADE 간선을 만들지 않으므로 「CASCADE 손자 0 · 마스킹 분류 CASCADE 자식 0」 등식은 그대로다.

### 0.7 API 모양

**응답 스키마는 평탄해야 한다**(D-6A1-20 ⓑ — 속성은 스칼라이거나 스칼라 배열. `$ref`·`oneOf`·
`allOf`·`anyOf`·`additionalProperties`·`type: object` 는 전부 거부). 그래서 **「건별 묶음」을
중첩으로 표현할 수 없다** — 항목마다 묶음 계수를 실어 클라이언트가 묶는다.

```
GET  /api/notifications?page&size&unreadOnly
     → 200 [NotificationItem]  (평탄 항목의 배열)
     헤더: X-Total-Count · X-Unread-Count   (7C① 의 X-Total-Count 관례를 잇는다)
     정렬: 공고 묶음의 최신 occurred_at DESC → 묶음 안 occurred_at DESC
           (묶음이 연속되게 — 클라이언트가 재정렬하지 않는다)

POST /api/notifications/read        body {kind, noticeNumber, noticeRound}   → 단건
POST /api/notifications/read-notice body {noticeNumber, noticeRound}         → 묶음 전체
     → 200 {markedCount, unreadCount}
```

`NotificationItem`(전부 스칼라):
`kind` · `detail`(nullable) · `noticeNumber` · `noticeRound` · `occurredAt` · `read`(boolean) ·
`readAt`(nullable) · `noticeItemCount` · `noticeUnreadCount` · 그리고 **합쳐 온 공고 칸**
`noticeTitle`·`noticeStatus`·`deadlineAt`(전부 nullable — 공고가 파기됐으면 null).

- **읽음 처리를 두 operation 으로 나눈다**(한 operation 의 선택적 `kind` 가 뜻을 바꾸는 모양을
  고르지 않는다 — 그런 칸은 조용히 잘못 맞는다). 알려진 키 밖은 400(D-6A2b-25 관례).
- **합성 id 를 노출하지 않는다** — 항목의 신원이 `(kind, 공고 쌍)` 이고 그 셋은 호출자가 이미
  자기 계정의 목록에서 본 값이다. 열거할 불투명 id 가 없으므로 IDOR 표면이 그만큼 작다.
- `markRead` 는 멱등이다 — 이미 읽은 항목에 다시 쏘면 `markedCount = 0` 이고 `read_at` 은
  움직이지 않는다(처음 읽은 시각이 정본).
- 페이지 상한을 선언한다. **묶음이 페이지 경계에 걸칠 수 있다**(알려진 제한) — `noticeItemCount`
  가 묶음의 전체 크기라 클라이언트가 「더 있다」를 알 수 있다.
- 쓰기이므로 세션 경로 + CSRF 가 묶인다. 읽기는 CSRF 면제(쿠키 대안 선언) —
  `OpenApiSecurityContractTest` 가 양방향으로 잠근다.

### 0.8 설계 검토 (0)~(3)

**(0) 위협 모델 경계.**
*방어한다* — ① 다른 계정 항목 읽기·읽음 처리(IDOR) ② 중복 억제 경합으로 같은 사실이 두 항목이
되는 것 ③ 쓰기 포트가 HTTP 표면에 올라 알림이 위조되는 것 ④ 항목에 PII(사업자번호·담당자 이름·
연락처)가 담기는 것 ⑤ CSRF ⑥ 어휘가 Kotlin 과 DB 한쪽에만 늘어 조용히 갈리는 것.
*방어하지 않는다* — ⓐ 단일 운영자가 자기 알림을 많이 읽는 것 ⓑ **원천을 쥔 코드가 의미 없는
사실을 많이 기록해 안읽음 계수를 흐리는 것**(중복 억제가 공고×종류로 접으므로 무한 축은 「서로
다른 공고의 수」뿐이다 — 그 수를 제한하는 것은 원천 slice 의 관련성 판정이고 이 slice 밖이다)
ⓒ 채널 발송 경로(R-4 — `OPEN-STR-12` 뒤).

**(1) 열거인가 구성인가.**
- 종류 ↔ detail 짝은 **sealed 구성**이다(열거 + nullable 필드로 두면 불법 쌍이 표현된다).
- 계정 범위는 **포트 시그니처의 구성**이다(첫 인자가 `AccountId` — 「필터를 빠뜨린 메서드」가
  시그니처상 존재하지 않는다).
- PII 부재는 **카탈로그 성질**이다(0.9 ④) — 문자열 금지 목록이 아니라 「TEXT 열 전수가 닫힌
  어휘이거나 공고 키」라는 등식.
- 어휘 등식은 **CHECK 정의에서 읽는다**(`outbox` 상태 다섯을 재는 기존 선례와 같은 술어).

**(2) 우회 — 열하나.** 0.9 의 표가 정본이다.

**(3) 과잉·미달.**
- *과잉으로 하지 않은 것*: 알림 본문 템플릿·문구 테이블(문구는 클라이언트가 종류로 만든다) ·
  채널 어댑터 · 합성 id 시퀀스 · 파생 dedupe 문자열 열 · 종류별 detail 전용 열 셋 · 묶음 전용
  둘째 endpoint · 안읽음 항목 보존 예외.
- *미달 위험*: 트리거 0 개 배선이라 **production 에서 항목이 한 건도 태어나지 않는다.** 이것이
  이 slice 의 가장 큰 알려진 제한이고, 완화는 test 쪽이다 — 쓰기 포트를 **출하 조립과 같은
  어댑터·같은 실 DB**로 호출하는 test 가 「배선되면 돈다」를 잰다(가짜 원천을 만들지 않고 포트의
  production 구현을 직접 두드린다).

### 0.9 우회 ↔ 무엇이 막는가 (설계 검토 (2), ≥5 → 열하나)

| # | 우회 | 막는 층 | 변이로 재는 것 |
|---|---|---|---|
| ① | HTTP 에서 쓰기 포트를 불러 알림을 위조 | `app.port-call.ports` 에 두 포트 등재 + **허용 쌍 0** · `app.http.denied-types` · 주입 게이트(controller 는 `app.wiring` 의 조회기 하나만 받는다) | 허용 쌍을 하나 심으면 RED |
| ② | 다른 계정 항목 읽기 | 두 포트 전 메서드 첫 인자 `AccountId` + SQL WHERE 에 항상 `account_id` · 주체는 **세션에서** 오고 본문에서 오지 않는다 | WHERE 에서 `account_id` 를 빼면 RED(다른 계정 항목이 보인다) |
| ③ | 다른 계정 항목 읽음 처리 | UPDATE WHERE 에 `account_id` → 영향 행 0 → **200 + `markedCount` 0**(r1 검토 F4 정정 — 404 가 아니다. 「없음·이미 읽음·남의 것」 셋을 구분하지 않는 것이 목적이고 문서 응답 코드에도 404 가 없다) | 〃 |
| ④ | 항목에 PII 를 담는다 | **카탈로그 등식**: 이 표의 TEXT 열 전수가 ⓐ 닫힌 어휘 CHECK 이거나 ⓑ 공고 키 두 열. 자유 문자열 열을 더하면 등식이 깨진다 | 자유 TEXT 열을 더하면 RED |
| ⑤ | 응답에 PII 를 담는다 | 응답 키 집합 **완전 일치** test + 평탄 술어 + 저장된 항목에 담을 PII 가 없음(④) | 키를 더하면 RED |
| ⑥ | 중복 억제 경합으로 두 항목 | PK + 단일 문장 upsert. **실 DB 동시성 test**(두 연결 동시 기록 → 행 1) | upsert 를 select-then-insert 로 바꾸면 RED |
| ⑦ | 제목 문구가 바뀌어 항목이 증식 | **제목을 저장하지 않는다** — 키 입력에 문구가 들어올 자리가 없다 | 제목을 키에 넣는 변이가 애초에 컴파일되지 않음(열 부재) |
| ⑧ | CSRF 없이 읽음 처리 | 세션 인증기의 CSRF 기본-요구(면제 메서드 열거는 GET·HEAD·OPTIONS) · 문서 선언을 `OpenApiSecurityContractTest` 가 양방향 일치로 잠금 | 문서에서 CSRF 스킴을 빼면 RED |
| ⑨ | 종류 어휘를 Kotlin 에만(또는 CHECK 에만) 늘린다 | **어휘 등식 test** — enum 전수 == CHECK 가 정한 여덟. detail 도 같은 등식 | 한쪽만 늘리면 RED |
| ⑩ | 불법 (종류, detail) 쌍을 저장 | Kotlin: sealed 로 표현 불가 · DB: 짝 CHECK(심층 방어) | 어댑터 codec 에서 쌍을 어긋나게 쓰면 DB 가 거부 → RED |
| ⑪ | 보존 분류를 적지 않고 표를 더한다 | 레지스트리 ↔ 카탈로그 집합 등식 + `RETENTION_SEEDS` 전수 등식 | 분류를 지우면 RED |
| ⑫ | 적재 시각을 밀어 파기를 피한다 | 열 단위 GRANT(앱 역할에 `created_at` UPDATE 없음) + 실 권한 test(7D `AccessPrivilegeTest` 선례) | GRANT 를 넓히면 그 test 가 RED |

### 0.10 (2b) 값 획득 축 — 새 public 표면 전수

| 표면 | 밖에서 할 수 있는 것 | 닫은 방법 |
|---|---|---|
| `NotificationInboxPort` | 알림 항목을 **만들 수 있다** | app production 의 어느 클래스도 이 메서드를 부르지 못한다(허용 쌍 0) · controller 는 이 타입의 빈을 받지 못한다(`denied-types` + 주입 게이트) · 호출자는 **계정 주체를 들고 있어야** 한다 |
| `NotificationInboxStore` | 목록·읽음 처리 | 〃 (controller 는 `app.wiring` 조회기 경유) · 전 메서드 `AccountId` 선행 |
| `NotificationKind` · `NotificationDetail`(enum) | 어휘를 **읽을 수 있다** | 읽기는 해가 없다(비밀이 아니다). 늘리려면 CHECK 와 **같이** 늘려야 한다(⑨) |
| `NotificationFact` 하위 타입(공개 생성자) | 사실 값을 **지을 수 있다** | 지어도 **저장에 닿지 못한다** — 저장은 포트 구현이 하고 그 포트는 ① 로 닫혀 있다. 불법 쌍은 타입상 지어지지 않는다 |
| `NotificationInboxRecord` · `NotificationItemKey` | 계정·공고·시각을 **넘길 수 있다** | `AccountId` 는 세션 판정의 산출물이다(밖에서 지어도 그것을 받는 포트에 닿는 자리가 없다). provenance·금액·PII 칸이 **없다** |
| `MarkReadOutcome` · `NotificationInboxPage` · `NotificationPageRequest` | 계수·페이지 요청 | 값 타입. 상한은 요청이 아니라 **서버가** 정한다(상한 넘기면 400) |
| HTTP: `GET /api/notifications` | 자기 계정 목록 | 세션 전용(0.11) · `account_id` WHERE · 응답에 다른 계정 축이 없다 |
| HTTP: `POST …/read` · `…/read-notice` | 자기 항목 읽음 | 세션 + CSRF · `account_id` WHERE · **읽음만** 바꾼다(종류·공고 키·detail·시각 칸이 요청에 없다) |
| 어댑터 `JdbcNotificationInbox` | — | `app` 은 이 타입을 이름으로 들지 않는다(`NotificationInboxWiring` 이 포트 타입으로 꽂는다 — `NoticeSearchWiring` 선례) |

「주체 없이 부르는 포트」 **0**. 「읽음 전이 use case 밖에서 `read_at` 을 바꾸는 경로」 **0**
(UPDATE 문장 둘 다 어댑터의 읽음 처리 메서드에만 있고, 열 단위 GRANT 가 한 겹 더 진다).

### 0.11 7B · 7D 결정 중 무엇을 따르는가

**7D(main) 의 세션 주체를 따른다.** 기본 거부 필터 하나가 모든 경로를 덮고, 인증 경로 둘 중
**계정을 내는 것은 세션뿐**이다(자격 헤더의 주체는 `operator` 이고 계정 행이 없다). 알림함은
사용자 소유 데이터이므로 **세션 경로 전용**이다.

**7B 의 계정 범위 경로 결정을 따른다 — 선언은 `AccountScopedRoutes` 하나다.** 7B 는 그 선언을
세우고 공유 test 둘을 그 축으로 고쳤다(본문 거부 전수를 「그 자격으로 실제로 닿는 매핑」으로
가르고, 문서의 자격 헤더 대안을 계정 전용 operation 에 대해 **양방향 정확 일치**로 조였다).
나는 **같은 축에 올라탄다** — 내 뿌리를 그 `ROOTS` 에 더하고 `@RequestMapping` 이 그 상수를
받는다. 선언을 두 개 만들지 않는다.

**그래서 머지 순서가 전제다(열린 질문 ①).** 오늘 main 의 두 test 는 7B 의 고침 **전**이고, 내
endpoint 는 그 전제를 깬다 — 실측:
- 본문 거부 전수 test 는 자격 헤더 하나로 **등록된 POST 매핑 전수**를 두드린다. 내 POST 둘은
  그 헤더로 401 이라 전수 집합에 `(401, …)` 가 섞여 붉어진다.
- 문서 계약 test 는 「인증을 요구하는 operation 은 **전부** 자격 헤더 대안을 남긴다」를 단언한다.
  내 operation 셋은 실제로 자격 헤더로 401 이므로, 그 대안을 적으면 **문서가 구현보다 넓어지고**
  적지 않으면 그 test 가 붉어진다.

**7B 가 먼저 머지되면** 두 축을 그대로 물려받아 내 변경은 「뿌리 하나 추가 + 계정 전용 집합에
경로 하나 추가」다. **내가 먼저 머지되면** 7B 의 공유 test 고침(모집단 가르기 + 양방향 일치)을
내가 다시 해야 한다 — 같은 일을 두 레인이 두 번 하고 머지에서 충돌한다.

### 0.12 손댈 공유 게이트·계약 자리 (머지 충돌 예상 포함)

| 자리 | 변경 | 사유 |
|---|---|---|
| 스키마 스냅샷 래칫 — 표 목록 | `+notification_inbox` | 추가만(기존 관례) |
| 〃 — PK 열 집합 | `+` 네 열 복합 | 자연 키가 PK |
| 〃 — UNIQUE 축 | **변경 없음** | PK 밖 UNIQUE 를 만들지 않는다 |
| 〃 — FK 목록 | `+ (notification_inbox.account_id → account.id)` | 단일 열, CASCADE 없음 |
| 〃 — CHECK 축 test | `+` 종류·detail·짝 CHECK | 어휘 등식의 상대 |
| 보존 분류 레지스트리 · 심기 fixture | `+Purge` 한 줄 · `+TableSeed` 한 항목 | 카탈로그 등식이 강제 |
| `architecture-policy.properties` | `app.port-call.ports +2`(허용 쌍 **0**) · `app.http.denied-types +2` · `app.http.allowed-packages +bidvector.workflow.notificationinbox`(문면 정정 — r1 검토 CR LOW-5. `…notification` 은 채널 발송 패키지이고 이 slice 는 그 경계를 넓히지 않는다) · `app.injection.allowed-types +`(조회기·요청·응답 값 타입) · `app.surface.api.handlers +controller` · `app.assembly.tier*` 해당 줄 | 넓힌 줄마다 사유를 커밋 메시지에 남긴다 |
| `gate-tests.properties` | `gate.tests.app` · `gate.tests.adapters` · `gate.tests.workflow` 에 새 class 등재 | 그 키는 전수 등식 |
| `app` 의존 게이트 면제 계수 | **62 → ?** | **충돌 예상**: 7B 가 63, 7C①b 가 64 로 올린다. 뒤에 rebase 하는 레인이 합집합으로 재계산(팀장 지시 2026-10-22) — 사유 문단은 세 레인 것을 전부 남기고 수만 다시 센다 |
| `AccountScopedRoutes` | 뿌리 `+/api/notifications` | **충돌 예상** — 0.11 |
| OpenAPI 단일 출처 + 생성 타입 | operation 셋 · 스키마 셋 추가 후 재생성(W-5 바이트 등식) | |
| 추적표 · capability map | NOTI-10 행 · §10 집계 | **충돌 예상** — 여러 레인이 같은 블록을 만진다 |

### 0.13 열린 질문 (팀장 결정 요청 — 각각 권고안 포함)

**① 머지 순서: 7B 를 7C③ 보다 먼저 머지한다.** (권고)
근거는 0.11 의 실측 — 두 공유 test 의 전제를 깨는 것은 7B 와 **같은 설계 사실**(계정 전용 경로)
하나이고, 그 고침은 한 번만 하면 된다. 7B 가 먼저면 나는 뿌리 하나를 더한다. 내가 먼저면 같은
고침을 중복 수행하고 머지에서 충돌한다. **7B 가 아직 멀면** 대안은 내가 그 고침을 들고 가고 7B
가 rebase 에서 합치는 것 — 어느 쪽이든 결정이 필요하고 기본 권고는 **7B 선행**이다.

**② COL-01 트리거를 배선하지 않는다.** (권고 — 0.2)
변경 감지는 main 에 있지만 관련성 원천이 없어, 배선하면 수집 run 마다 갱신 공고 전수가 들어와
진짜 알림을 가린다. 어휘 `NOTICE_REVISED` 와 키 규칙만 세우고 배선은 관련성 원천 slice 몫으로
홀더를 남긴다. → 이 slice 는 트리거 **0 개** 배선이고, 그것이 가장 큰 알려진 제한이다.

**③ 종류 어휘를 여덟로 둔다(오늘 부를 레인이 없는 것 포함).** (권고)
7B 가 `PRICE_RECEIVED` 에 쓴 선례와 같다 — CHECK 어휘를 늘리는 둘째 마이그레이션을 피한다.
반대 의견이 있으면 「오늘 부를 수 있는 셋만」으로 줄일 수 있으나, 그러면 7C②·7C⑤·7B 각자가
마이그레이션을 하나씩 더 들고 와야 한다.

**④ 재기록이 읽음을 지우는 조건: detail 이 바뀔 때만.** (권고 — 0.3)
같은 사실의 재기록이 읽은 항목을 안읽음으로 되돌리면 운영자가 같은 것을 반복해 읽는다. detail
이 바뀐 것은 **다른 사실**이므로 되돌린다. 계약의 「같은 키 두 번 → 항목 1, 갱신 시각만 이동」
과 맞다 — 다만 그때 `occurred_at` 도 함께 움직인다(최신 관측 시각). 「`updated_at` 만」으로
읽어야 하면 `occurred_at` 을 최초 기록 시각으로 고정한다.

**⑤ 읽음 처리를 두 operation 으로 나눈다.** (권고 — 0.7)
한 operation 의 선택적 `kind` 가 뜻을 바꾸는 모양(있으면 단건·없으면 묶음)은 조용히 잘못
맞는다. 두 경로면 각자의 요청 스키마가 닫힌다.

**⑥ 보존: 90일 파기(`Purge`), 안읽음 예외 없음.** (권고 — 0.6)
`Exempt` 의 여덟 사유 어느 것도 맞지 않고, 안읽음을 예외로 두면 「90일이면 사라진다」가 거짓이
된다. 안읽음 항목도 사라진다는 것을 알려진 제한으로 등재한다.

**⑦ 저장 항목에 공고 제목을 담지 않고 읽을 때 합친다.** (권고 — 0.1, 0.5)
그 결과 ⓐ 중복 억제 키가 문구에 흔들릴 자리가 구조상 없고 ⓑ PII 가 담길 TEXT 열이 없고
ⓒ 공고가 파기되면 제목 칸이 `null` 이 된다(항목은 남는다). ⓒ 를 받아들일 수 없으면 제목을
스냅샷으로 저장해야 하고, 그러면 ⓐ·ⓑ 둘 다 포기한다.

### 0.14 이 slice 가 남기는 알려진 제한 (초안 — 구현 뒤 확정)

1. **트리거 0 개 배선** — production 에서 항목이 태어나지 않는다(0.2). 완화: 출하 조립과 같은
   어댑터·실 DB 로 쓰기 포트를 직접 두드리는 test.
2. **`Failed` 를 무시하는 호출부가 생기면 dual-write** — 호출부는 다른 레인이다(기존
   `OPEN-6F-ASSEMBLY` 와 같은 축).
3. **묶음이 페이지 경계에 걸친다** — `noticeItemCount` 가 전체 크기를 들어 클라이언트가 안다.
4. **안읽음 항목도 90일에 파기된다**.
5. **공고가 파기되면 제목·기관·마감 칸이 `null`** — 항목은 종류와 공고 번호로 읽힌다.
6. **화면 없음** — 7E 소관. 클릭 → 워크시트는 항목이 공고 쌍을 실어 클라이언트에서 닫힌다.
7. **자격 헤더 경로는 알림함에 닿지 못한다**(7B 와 같은 사실 · R-2 남은 절반).
8. **채널 발송 0** — R-4 경계, `OPEN-STR-12` 뒤.
9. **마감 종류의 꼬리표는 굳은 사실이고 표시 마감 시각은 현재 값이다** — 둘의 출처가 달라서
   생기는 틈이다(7B 의 지연 마감 판정 통보를 받고 자기 설계에서 찾은 것). 정정공고로 마감이
   **연장되면** 항목의 종류는 `BID_DEADLINE_IMMINENT` 로 남은 채 합쳐 온 `noticeDeadlineAt` 은
   먼 날짜를 가리킬 수 있다. 완화는 둘이고 **둘 다 이 slice 밖**이다: ⓐ 원천 slice 가 다시
   기록하면 중복 억제가 **그 항목을 갱신**한다(행이 늘지 않고 사실의 시각이 움직인다) ⓑ 화면이
   `noticeDeadlineAt` 을 지금과 비교한다. 이 slice 가 스스로 고칠 수는 없다 — 종류를 읽을 때
   다시 판정하면 **알림함이 판정자가 되고** 그것은 원천의 일이다(가짜 원천 금지와 같은 선).
   **처분과 홀더**(팀장 2026-10-24): 고치지 않고 **등재로 둔다**. 완화 둘은 **7E 화면 slice 의
   입력**으로 넘긴다 — ⓐ 는 원천 slice 가 부를 때 성립하고 ⓑ 는 7E 가 든다. 즉 이 제한은
   주인이 없는 것이 아니라 **다른 slice 에 홀더가 있다**(그 사실을 적지 않으면 나중에
   「아무도 안 맡은 결함」으로 읽힌다).
10. **읽음 시각의 순서를 저장 층이 막지 않는다**(D-7C3-3) — 앱 쪽 시계가 잘못 배선돼 과거를
    싣더라도 DB 가 거부하지 않는다. 적재 시각을 미는 길은 열 단위 GRANT 가 여전히 막는다.

## 0.15 결정 D-7C3-3 — 교차 시계 CHECK 를 걷어낸다 (2026-10-26, 운영자)

**무엇을 지웠나**: V23 의 `CHECK (read_at IS NULL OR read_at >= created_at)`. CHECK 개수 래칫이
6 → 5 로 내려갔고, 그 제약을 재던 privacy test 하나(`읽은 시각이 적재보다 앞서면 DB 가
거부한다`)를 지웠다.

**왜**: 그 제약은 **다른 두 시계**를 비교한다 — `created_at` 은 보존 cutoff 축이라 **DB 가**
찍고(V20·V21 과 같은 관례), `read_at` 은 **앱 시계**가 싣는다. 하나의 CHECK 로 묶으면 시계가
틀어질 때마다 **쓰기가 실패한다**: 앱 컨테이너가 DB 보다 1초 뒤처지면 정상적인 읽음 처리가
DB 오류가 된다.

**어떻게 드러났나**: 고정 시각 상수를 쓰는 test 가 **쓴 날은 통과하고 사흘 뒤 붉어졌다**
(`created_at 2026-10-26` vs `read_at 2026-10-23`). 코드가 아니라 달력이 바뀐 것이고, 증상은
test 의 시각 의존이지만 **뿌리는 제약의 설계**였다.

**무엇을 잃었나 — 약화가 맞다.** 「읽은 시각이 적재보다 앞설 수 없다」는 불변식이 **DB 층에서
사라졌다**. 잃은 것은 구체적으로 **앱 쪽 순서 버그의 검출**이다: 읽음 처리 시각을 내는 시계가
잘못 배선돼 과거를 싣더라도 저장 층이 더는 막지 않는다.

**무엇이 남았나**: ⓐ **열 단위 GRANT** — 앱 역할에 `created_at` UPDATE 권한이 없어 **적재 시각을
밀어 순서를 위조하는** 길은 그대로 막혀 있다. ⓑ `NotificationInboxPrivilegeTest` — 그 권한을
DDL 문면이 아니라 **앱 역할로 직접 쏘아** 잰다. ⓒ 읽음 시각을 내는 자리가 조회기의 `Clock`
하나뿐이라는 구조(새 방어를 만든 것은 아니고, 이미 그렇다는 사실이다).

**앱 층에서 되살리지 않는다**(운영자 ①): `created_at` 이 DB 시계이므로 앱에서 비교하면 같은
시계 어긋남을 **앱으로 옮길 뿐**이다. 앱이 지킬 수 있는 것은 같은 시계끼리의 순서뿐이고 이
범위에서 새로 만들지 않는다.

**회귀 방어**(운영자 ②): `호출자의 시각이 실행 시각보다 멀어도 기록과 읽음 처리가 성공한다 —
시계 비의존`. 변이 **⑬**(걷어낸 CHECK 복원)이 그 test 를 RED 로 만든다 — 그 짝이 없으면 이
test 는 「늘 통과하는 test」와 구별되지 않는다.

**verifier 표적**: 이 행 전체. 「줄어든 제약 + 지워진 test」가 **의도된 거래**인지, 남은 방어
셋이 실제로 서는지(특히 ⓑ 가 권한을 문면이 아니라 실행으로 재는지)를 본다.

## 1. 계약이 요구한 선언

계약이 Phase 1 에 요구한 선언은 전부 §0 에 있고 `D-7C3-1`·`D-7C3-2` 로 수령됐다 — 포트
시그니처와 종류 어휘·종류별 중복 억제 키(§0.3·§0.4, 7C②·7C⑤ 에 전달) · COL-01 실측(§0.2,
**트리거 0 배선**) · 보존·파기 판정(§0.6, `Purge` 90일) · API 모양(§0.7) · 설계 검토 (0)~(3)
(§0.8 위협 모델 · §0.9 우회 **열둘** · §0.10 값 획득 축 **아홉 표면**) · 7B·7D 추종(§0.11) ·
손댈 공유 자리(§0.12). 구현 중에 선 결정 하나는 §0.15 `D-7C3-3`(교차 시계 CHECK 철회)다.

## 2. 우회 ↔ test ↔ 변이 실측

§0.9 의 우회 열둘에 변이를 하나씩 붙이고, 구현 중 선 축 셋(⑬⑭⑮)을 더해 **열다섯**을 쟀다.
변이는 전부 **바꿔치우기**다(더하기만 한 변이는 초록이라 아무것도 증명하지 못한다). 통제군은
`--rerun` 으로 돌려 FROM-CACHE 가 아님을 보장했다.

| 변이 | 우회 | 결과 |
|---|---|---|
| ① HTTP 에서 쓰기 포트 호출 | ① 알림 위조 | **RED** |
| ② 목록에서 계정 술어 제거 | ② 타계정 읽기 | **RED** |
| ③ 단건 읽음에서 계정 술어 제거 | ③ 타계정 읽음 처리 | **RED** |
| ④ 자유 문자열 열 추가 | ④ 항목에 PII | **RED** |
| ⑤ 응답에 키 추가 | ⑤ 응답에 PII(필드 축) | **첫 실측 GREEN → 아래 참조 → 수정 뒤 RED**(`ba19fa1e`). r2 에서 ⑯ 이 그 **wire 축**을 따로 잠갔다 |
| ⑥ upsert 를 `DO NOTHING` 으로 | ⑥ 경합으로 두 항목 | **RED** |
| ⑦ 중복 억제 키에서 종류 축 제거 | (키 규칙) | **RED** |
| ⑧ 문서에서 CSRF 요구 제거 | ⑧ CSRF 없는 쓰기 | **RED** |
| ⑨ Kotlin enum 에만 종류 추가 | ⑨ 어휘를 한쪽만 늘림 | **RED** |
| ⑩ codec 이 어긋난 짝을 씀 | ⑩ 불법 (종류, detail) | **RED** |
| ⑪ 보존 분류 삭제 | ⑪ 분류 없이 표 추가 | **RED** |
| ⑫ GRANT 를 적재 시각까지 확대 | ⑫ 파기 회피 | **RED** |
| ⑬ 걷어낸 교차 시계 CHECK 복원 | `D-7C3-3` 의 거래 | **RED** — 시계 비의존 회귀 test 가 문다 |
| ⑭ 묶음 크기가 안읽음 필터를 따라감 | 배지·묶음 계수 | **RED** |
| ⑮ 표시 칸 조인을 내부 조인으로 | 파기 공고 항목 소실 | **RED** |
| ⑯ 응답 DTO 에 **getter 전용** 속성 추가 | ⑤ 응답에 PII(wire 축) | **RED** — 반사가 못 보는 꼴을 wire 등식이 문다 |
| ⑰ 알려진 키 검사 제거 | (2b) 요청 표면 | **RED** — 표면 test 의 400 들이 문다 |
| ⑱ F3 모집단을 `text` 전용으로 완화 | ④ (모집단 축) | **RED** — 순수 양성 대조가 문다 |
| ⑲ 실 스키마에 자유 문자열 열 둘 | ④ (실 DB) | **RED** |
| ⑳ 이름이 겹치는 열 하나(술어 그대로) | ④ (이름 겹침 축) | **RED** |
| ㉑ INSERT GRANT 를 표 단위로 | ⑫ · F5 | **RED** — 적재 시각 지정 INSERT 가 통과해 새 test 가 문다 |
| ㉒ 같은 열 + **어휘 축만** 완화 | ④ 의 옛 구멍 재현 | **GREEN(기대대로)** — 아래 비교 쌍 |
| 통제군(변이 없음, `--rerun`) | — | **GREEN** — 표적이 「늘 붉은 것」이 아니다 |

**F3 의 비교 쌍이 이 라운드의 핵심 측정이다.** ⑳(이름 겹치는 열 + **새** 술어) **RED** ↔
㉒(같은 열 + **옛 부분 문자열** 술어) **GREEN**. 한쪽만으로는 아무것도 말하지 못한다 — RED 만
보면 「술어가 선다」까지고, GREEN 만 보면 「방어가 없다」로 읽힌다. 둘을 나란히 두어야
**어느 축이 그 열을 막는가**가 나온다.

**그 쌍을 세우는 데 두 번 실패했고 둘 다 측정 장치의 결함이었다**(산출물 결함이 아니다):
① 처음 ⑳ 은 **모집단 축까지** 완화해서, 그 축을 직접 재는 순수 양성 대조가 **반드시** 붉었다 —
GREEN 이 구조적으로 도달 불가였다. 한 변이가 두 축을 건드리면 어느 축이 막았는지 알 수 없다.
② 그 뒤 「둘만」 돌리려고 넣은 부분 실행 필터를 **적용 뒤**에 두고 `return` 해서, 건너뛴 변이가
트리에 **남아 쌓였다** — ⑦ 이 앞 변이가 지운 문자열을 찾다 APPLY-FAIL 하고, ⑳ 은 ⑧~⑲ 위에서
돌았다(⑨ 는 그 표적을 **그 자체로** 붉힌다). 그 실행의 ⑦·⑳·㉒ 셋을 **무효로 선언**하고 다시
쟀다. 하네스를 셋 고쳤다: 건너뛰기를 적용 **앞**으로 · 적용 **직전** clean-tree 단언
(`DIRTY-BEFORE-APPLY`) · 실패(APPLY-FAIL·NO-OP·기대 불일치·끝 트리 더러움) 시 **비영 종료**
(앞 판은 적용 실패에도 `exit 0` 이었다 — 결과 파일을 읽지 않았다면 「통과」로 읽혔다).

**읽는 법**: 변이 결과는 **그 측정이 깨끗한 트리에서 났는가**와 함께 읽어야 한다. 두 사건 모두
결과만 보면 그럴듯했다(첫 판은 RED 라 「방어가 선다」로, 둘째 판은 RED/GREEN 이 기대와 맞아
「쌍이 섰다」로). 그래서 지금 하네스는 그 질문을 **스스로** 묻는다.

**우회 ⑦ 은 변이로 표현되지 않는다.** 제목 문구로 항목이 증식하는 경로는 **열이 없어서**
닫혔다 — 키에 제목을 넣는 변이는 애초에 컴파일되지 않는다. 그래서 그 자리는 변이가 아니라
test(제목이 바뀐 뒤 재기록해도 항목 하나)와 ⑦(키에서 종류 축 제거)로 둘러쌌다.

**⑤ 가 드러낸 것 — 「막는다」고 적힌 층이 실제로는 막지 않았다.** §0.9 ⑤ 행은 「응답 키 집합
**완전 일치** test」를 방어로 적었는데, 그 test 는 제목이 「Kotlin DTO 와 완전히 일치한다」면서
문서의 키를 **같은 파일의 축어 집합과만** 비교했다 — 문서를 자기 자신과 비교한 셈이다. DTO 에
칸이 늘어도 아무것도 붉지 않았고, 즉 **문서에 없는 칸이 wire 에 닿는 경로가 열려 있었다.**
설계 문서의 방어 주장이 거짓이었던 자리이고, 그것을 찾은 것은 리뷰가 아니라 변이다.
수정(`ba19fa1e`)은 등식을 두 방향으로 묶는다 — 문서 ↔ 축어(문서가 조용히 줄어드는 쪽) ·
문서 ↔ **DTO 필드 전수**(DTO 가 조용히 늘어나는 쪽). RED 선행을 따로 쟀다: 같은 변이에서
`WITH_MUTATION_EXIT=1`(실패 단언이 바로 새 등식) · 되돌린 뒤 `WITHOUT_MUTATION_EXIT=0`.

## 3. 약속 ↔ 출하 대조

| 산출물(scope.md) | 출하 | 비고 |
|---|---|---|
| 1. V23 알림함 표 — 계정 범위 중복 억제 키 · 읽음 시각 · PII 없는 구조 필드 | ✅ | 자유 문자열 열 **0**(카탈로그 등식이 잠근다) · CHECK 다섯 · 열 단위 GRANT · 보존 `Purge` 90일 |
| 2. 쓰기 포트 + 읽기 포트 + 어댑터 + controller, 단일 문장 upsert | ✅ | 어댑터는 포트별 **두 class**(`typeShapeGate` 가 한 class 의 두 인터페이스를 막는다 — 래칫을 올리지 않고 갈랐다) · 실 DB 동시성 test 가 경합에서 행 1 을 잰다 |
| 3. OpenAPI 목록·읽음 처리 둘 + 생성 타입 재생성(W-5) | ✅ | operation 3 · 스키마 4 · 생성 타입 바이트 등식 |
| 4. test — 중복 억제 · 제목 변경에도 1 · 읽음 전이 · 타계정 접근 0 · 401 · CSRF 거부 · 응답 PII 0 | ✅ | 어댑터 45 · 순수 커널 21 · app 17. **응답 PII 0 은 이 라운드에 비로소 실제로 잠겼다**(§2 ⑤) |
| 5. 추적표·capability map NOTI-10 행 | ✅ | **이 대조를 쓰다가 비어 있는 것을 발견했다.** `2f0c29a7` 에서 2:2 줄 치환으로 등재(줄 수 불변 — 이 파일 아래 좌표를 인용하는 evidence 가 있다). `OPEN-NOTI-05` 의 답이 `D-7C3-1` 에 있다는 사실도 그 자리에 적었다(레지스트리 종결 표시는 팀장 소관) |

**약속하지 않았는데 더한 것**: 없다. **약속했는데 못 한 것**: 없다 — 다만 산출물 1 의 「표시용
본문」은 **저장하지 않는 쪽**으로 섰고(읽을 때 `notice` 를 합친다) 그 판단은 `D-7C3-1` 이 받았다.

## 4. acceptance 실측

**r2 판정 SHA `acade492` 의 job 단위 실측(정본)** — verifier r2 가 직접 돌렸다:
`check` **exit 0**(` FAILED` 0 · `:adapters:test` 186 class/**1244** · `:app:test` 102/**884** ·
`:workflow:test` FROM-CACHE(입력 불변) 72/606 · 실패 0 · skip 4) · `qualityBaseline` **exit 0**.

**이 줄이 CR HIGH-1 을 닫는다.** 그 지적은 산출물 결함이 아니라 **머지 조건 미성립**이었다 —
`ErrorBody` 접기가 출하 main 소스를 바꿨는데 그 뒤 job 단위 `check` 가 한 번도 초록으로 돌지
않았고, 아래 r2 기록은 「바뀐 두 파일이 먹이는 task 만 바뀌었다」는 **논증으로 갈음**했다.
하네스가 금지한 갈음이다(부분 게이트는 안 돌린 것과 같다 · 줄일 수 있는 단위는 job 뿐).
논증이 결과적으로 맞았다는 것은 지금 알지만, **맞았다는 사실은 갈음을 정당화하지 않는다.**

**r2 실측(병합 뒤)**: HEAD `2eed6a0c` 에서 `clean` → `check --continue` → `qualityBaseline` →
web(W-1~W-9) → `one-command-check.sh` 를 돌렸다. **web 아홉 전부 0**, `CLEAN_EXIT=0`,
`BASELINE_EXIT=0` 이고 Kotlin `check` 는 **붉었다**(`CHECK_EXIT=1`) — FAILED 둘:

| FAILED | 원인 | 처분 |
|---|---|---|
| `:app:sizeGate` | `ErrorBody.readPathCode` **55줄**(한도 50). 이 slice 하나가 아니라 **병합 합집합**이 넘겼다 — 네 표면이 각각 `when` 갈래를 더했고 넷의 결과가 **완전히 같다** | 같은 결과 넷을 한 갈래로 접었다(중복 제거). 사유를 함수 **안**에 적었더니 52줄로 여전히 넘어, 게이트가 세는 범위 밖인 **KDoc 으로** 옮겨 45줄 |
| `:app:ktlintTestSourceSetCheck` | `HttpTestSupport.kt` 의 빈 줄 하나 — 7C② 가 옮긴 이중체 셋을 지우며 남았다 | 1줄 |

**고친 뒤 표적 재실행(`0f0a0514`)**: `sizeGate`·`detekt`·`ktlintMainSourceSetCheck`·
`ktlintTestSourceSetCheck`·`ktlintCheck`·`test` **여섯 task 전부 표식 없이 실행**(FROM-CACHE·
UP-TO-DATE 아님) · BUILD SUCCESSFUL 8m 3s. 갈래 접기가 **순서를 바꾸므로** 컴파일로 갈음하지
않고 네 표면의 400 거동을 쟀다 — class **29** / test **351** / 실패 **0**
(`EvaluationDryRunControllerTest` 12 · `NoticeSearchEndpointTest` 15 · `OpeningResultEndpointTest` 12 ·
`NotificationInboxEndpointTest` 10 · `StrategyEditEndpointTest` 19 · `BidPriceRequestEndpointTest` 23 ·
`ProductionHttpSurfaceTest` 7 · `OpenApi*` 아홉). 상속 관계도 먼저 읽었다(옮긴 타입과 그 사이
타입 사이에 상위-하위 관계 0).

전체 acceptance 의 나머지(web 아홉 · 그 둘 밖 Kotlin task)는 **그 둘이 든 task 만** 바뀌었으므로
유효하다 — 바뀐 두 파일이 먹이는 task 넷이 위 표적 실행에서 전부 초록이다.

아래 표는 **r1** 판정 SHA `e1ffeb3a` 의 acceptance-v8 기록이다(15 단계 전부 0 · FAILED task 0).

| job · step | 명령(ci.yml 그대로) | 결과 |
|---|---|---|
| `check` | `./gradlew --no-daemon check` | `CHECK_EXIT=0` |
| 〃 | `./gradlew --no-daemon qualityBaseline` | `BASELINE_EXIT=0` |
| 〃 (S-20) | `./tools/one-command-check.sh` | `WRAPPER_EXIT=0` |
| `web` W-1~W-9 | `npm ci` · `lint` · `typecheck` · `gate:src-tree` · `api:types:check` · `test` · `gate:tests` · `build` · `gate:boundary-canary` | 아홉 전부 0 (생성 타입은 OpenAPI 문서와 바이트까지 같다) |
| `web` W-10·W-11 | playwright 의존·브라우저 설치 · `test:e2e` | **안 돌렸다** — apt·브라우저 CDN 이 필요한 CI 전용 step. CI 에서 |
| `container` | job 전체 | **안 돌렸다**. **정본은 CI `container` job — PR head 커밋**(머지 조건). 부분만 돌린 것을 이 job 의 실측으로 적지 않는다(하네스: 줄일 수 있는 단위는 job) |
| (참고 — 판정 근거 아님) | `:app:bootJar` · 앱 이미지 build · 앱 이미지 위생 게이트 | 0 · 0 · 0. V23 이 app 배포물 안으로 들어가므로 입력이 바뀌는 쪽을 미리 두드린 것이고, container job 의 갈음이 아니다 |

**`check` 의 exit 0 이 「전부 실행됐다」를 뜻하지 않았다.** test 결과 XML 의 mtime 을 보니
`:adapters:test`·`:workflow:test` 는 **2026-10-26 17:29·17:27**(HEAD `207ae4f5`) 것이고 이번
창에서 쓰인 것은 app 쪽뿐이었다 — Gradle 이 UP-TO-DATE 로 건너뛴 것이다. Gradle 의 판단
자체는 맞다(그 뒤 바뀐 것은 app test 소스 다섯 · `architecture-policy.properties` · evidence 뿐이고,
그 정책 파일을 읽는 코드는 `app` 하나다). 그래도 **추론으로 갈음하지 않고** 판정 SHA 에서
강제 실행했다:

| 보강 실측 | 결과 |
|---|---|
| `./gradlew --no-daemon :adapters:test --rerun :workflow:test --rerun` (HEAD `2ad0e6b9`, TREE=clean) | `RERUN_EXIT=0` · adapters class 183 / test **1213** · workflow class 70 / test **587** · 실패 0 · 오류 0 |
| 그 실행 안의 이 slice test | 어댑터 **45**(쓰기·전이 17 · 읽기 투영 11 · 카탈로그 8 · 권한 7 · 동시성 2) · 순수 커널 **21**(사실 9 · 파싱 12) · app **17**(문서 계약 14 · areEqual 구조 3) |
| 건너뜀 4 | `RealServerIntegrationTest` — 이 slice 와 무관한 기존 건너뜀 |

## 5. 알려진 제한 (확정)

§0.14 의 열 항목을 **그대로 확정**한다(문면 변경 없음). 가장 큰 것은 첫째 — **트리거 0 배선**
이라 production 에서 항목이 태어나지 않는다. 완화는 출하 조립과 같은 어댑터·실 DB 로 쓰기
포트를 두드리는 test 다.

구현 라운드가 더한 **둘**:

11. **공유 test 조립의 병합 취약** — 이 slice 가 `HttpTestApplication` 을 자기 파일로 옮겼고,
    7C②·7C④-2b 는 **옛 자리**에 `@Import` 를 붙였다. §7 이 그 해소 규칙을 든다. 네 자리 중
    셋은 어긋나면 CI 가 붉고, 조용히 유실될 수 있는 것은 `@Import` 를 한 줄만 남기는 해소
    하나다(무너지는 것이 **다른 레인의** test context 라 원인을 자기 변경에서 찾게 된다).

12. **계수와 본문이 다른 snapshot 일 수 있다**(r1 검토 CR LOW-4) — 목록은 계수 질의와 본문
    질의를 **두 문장**으로 돌고 그 둘이 READ COMMITTED 의 서로 다른 snapshot 을 볼 수 있다
    (7C① 의 공유 읽기 전용 트랜잭션 helper 를 쓴다). 그래서 「전체 건수 7, 본문 6 줄」 같은
    순간이 이론상 가능하다. 단일 운영자 규모에서 그 창은 사실상 닫혀 있고, 닫으려면 두 질의를
    한 문장으로 합치거나 REPEATABLE READ 로 올려야 한다 — 둘 다 이 slice 밖의 거래다.

## 6. 하네스 레인 변경

- 없음.

## 7. 병합 메모 — 다음 머지에서 반드시 할 일 (레인 넷이 같은 네 자리를 만진다)

이 slice 는 **공유 test 조립**을 건드린다. 네 자리 모두 「수를 맞추면 통과하지만 뜻이 사라지는」
모양이라, 병합은 수가 아니라 **항목 이름의 합집합**으로 다시 센다.

| 자리 | 다른 레인이 하는 것 | 이 slice 가 한 것 | 병합 규칙 |
|---|---|---|---|
| 조립 class `HttpTestApplication` | 7C② 가 `@Import(BidPriceTestWiring::class)`, 7C④-2b 가 `@Import(ProfileTestWiring::class)` 를 붙인다 — **둘 다 옛 자리**(`HttpTestSupport.kt` 안) | class 를 `HttpTestApplication.kt` 로 **옮겼다**(`@Import` 없음) | git 은 「옮김 + 옛 자리 편집」을 따라가지 못한다. 두 `@Import` 를 **새 파일로 옮겨 한 줄로 접는다** — `@Import(BidPriceTestWiring::class, ProfileTestWiring::class)`. `@Import` 는 `@Repeatable` 이 **아니다**(spring-context 7.0.8 의 그 annotation 을 javap 으로 확인 — `Target` 만 있고 `Repeatable` 이 없다). 그래서 위험이 갈라진다 — 두 줄로 남으면 **컴파일이 거부**되고, 충돌 해소가 **한 줄만 남기면** 다른 레인의 배선이 조용히 사라져 그 레인의 test context 가 무너진다. 접으면 둘 다 없다 |
| 이중체 `UnreachableWatchSubjectPort` · `UnreachableLicenseGatePort` | 7C⑤ 가 같은 두 이름을 `EvaluationTestDoubles.kt` 로 `internal` 로 **옮겼다**(그 worktree 에서 실측) | **선언은 제자리에 두고 `private` → `internal` 만** 바꿨다 | 내가 선언을 옮기지 않은 이유가 이것이다 — 둘 다 옮기면 같은 패키지에 같은 이름이 두 벌이 된다. 7C⑤ 가 먼저 머지되면 `HttpTestSupport.kt` 의 **내 두 선언을 지운다**(7C⑤ 판이 이미 `internal`이라 새 파일에서 보인다) |
| `Intrinsics.areEqual` 허용 목록 | 7C④·7C⑤ 도 늘린다 | **+4** — `NotificationItemResponse` · `NotificationTargetResult$Item` · `NotificationTargetResult$Notice` · `NotificationInboxRequestsKt` | 자리가 **둘**이다(map 의 키 · 같은 test 가 적는 축어 집합). 한 자리만 맞추면 다른 자리가 붉다(이 라운드 실측). 두 자리를 **항목 이름 합집합**으로 같이 맞춘다 |
| `app` 의존 게이트 면제 계수 | 7C⑤ 68→70 · 7C④-2b 68→69(`ProfileWiring`) | 68 **+2** = 70 — tier1 `NotificationInboxWiring` · tier2 `NotificationInboxQuery` | 수를 맞추지 말고 목록을 base 와 diff 해 **이름**을 센다. 계수와 `tier1-bootstrap-classes` 목록은 **자리가 다르다** — 수만 올리면 계수 단언이 아니라 참조 단언이 붉는다(이 slice 에서 두 번 틀렸다) |

**알려진 제한(병합 취약).** 네 자리 중 셋은 어긋나면 CI 가 붉는다. 조용히 유실될 수 있는 것은
**`@Import` 를 한 줄만 남기는 해소**뿐이다 — 그것은 컴파일되고, 무너지는 것은 **다른 레인의**
test context 라 머지하는 사람이 이 행을 읽지 않으면 원인을 조립이 아니라 자기 변경에서 찾는다.

## 8. r1 검토 처분 (verifier `ready-for-review` · code-review `request_changes`)

| finding | 처분 | 무엇으로 닫았나 |
|---|---|---|
| CR HIGH-1 측정 HEAD 낡음 | **닫음** | §4 를 판정 SHA `e1ffeb3a` 의 acceptance-v8 로 갱신. 실측은 있었고 장부가 낡았다 |
| CR HIGH-2 병합 메모가 main 의 조립 본문을 세지 않음 | **닫음** | 병합 커밋 — main 의 class 본문을 새 파일로 옮기고 `open fun` 합집합 **25** 로 계수. 메모에 없던 이중체 셋째(`EmptyCandidateSource`)도 발견해 함께 처리 |
| v F1 · CR M-3 응답 키 등식이 wire 를 재지 않음 | **닫음** | `NotificationInboxEndpointTest` — fake 에 한 페이지를 꽂아 직렬화를 돌리고 **wire 키 집합 == 문서 키 집합**. 헤더 둘도 같은 자리에서. 반사(`declaredFields`)로는 getter 전용 속성이 보이지 않는다는 것이 F1 의 요점이다 |
| v F2 · CR M-2 controller·판독 표면 test 0 | **닫음** | 같은 class — 미지 키 · `kind` 비문자열 · 차수 형식 위반 · 알 수 없는 토큰 · 경로 교차 · `?page=1&page=2` · 상한 초과(포트 미호출까지) 가 전부 400 |
| v F3 카탈로그 술어가 문자열·자료형 하나에 걸림 | **닫음** | 모집단을 **자료형 무관 전수**로, 어휘 판정을 `pg_constraint.conkey` 로. 통과 자료형은 **열거**(기본 거부). 부분 문자열 술어는 파일에서 삭제. r1 이 뚫은 두 꼴(`code`·`memo`)을 순수 양성 대조로 고정 |
| CR M-1 · v F6 권한 test 가 SQL 사본 | **닫음** | `NotificationInboxSql.UPSERT_ITEM`·`MARK_ITEM_READ`·`MARK_NOTICE_READ` 를 매개변수까지 묶어 앱 역할로 직접 쏜다 |
| v F5 INSERT GRANT 가 표 단위 | **닫음(고치는 쪽)** | V23 이 **열 단위 INSERT** 일곱(코드가 실제로 넣는 전부). `created_at`·`read_at` 지정 INSERT 가 앱 역할로 거부되는 것을 test 가 실측. 부작용으로 `CleanMigrationPrivilegeTest` 의 `insert` 가 `false` 로(열 단위는 `has_table_privilege` 에 보이지 않는다) |
| v F4 · CR LOW-1·5 문면 낡음 | **닫음** | §0.9 ③ 404 → 200 `markedCount 0` · §0.12 패키지 이름 · `NotificationInboxQuery` KDoc 과 OpenAPI 주석의 걷어낸 CHECK 인용 제거 |
| CR LOW-2 `>=` 단언 | **닫음** | `shouldBeAfter` — 등호가 참이라 「움직이지 않아도」 초록이던 자리 |
| CR LOW-3 도달하지 않는 갈래 둘 | **닫음(구조로)** | 중간 sealed 타입 `NotificationTargetResult` 를 걷고 판독기가 **정확한 타입**을 돌려준다 — 갈래가 없어서 도달 불가 갈래도 없다. 타입이 사라졌으므로 areEqual 등재 둘도 함께 걷었다(죽은 등재는 「이미 판정된 자리」로 읽힌다) |
| CR LOW-6 `rawRow` 가 차수를 뺌 | **닫음** | 술어에 `notice_round` 추가(기본값으로 기존 호출 열하나 유지) |
| CR LOW-4 READ COMMITTED snapshot | **등재** | §5 알려진 제한 12 |
| v 범위 밖 참고 — port-call 게이트가 리플렉션을 못 봄 | **그대로** | 공유 게이트의 기존 성질이고 이 slice 가 만든 것이 아니다(verifier 도 finding 으로 올리지 않았다) |

## 9. 병합 기록 (r2 — main 을 두 번 받았다)

`46ac0949`(착수) → **`23837b19`**(7C⑤·DEC-05 등) → **`1632242e`**(7C②(PR #94)·guava CVE(PR #95)).
두 번 다 `git merge --no-ff` 이고, 합집합은 **숫자가 아니라 항목 이름**으로 다시 셌다.

**같은 함정이 두 번 나왔다 — 두 쪽이 같은 수를 들고 온다.** 면제 래칫이 첫 병합에서 `70`/`70`
(기준 68 + 각자 둘 → 실제 **72**), 둘째 병합에서 `72`/`72`(기준 70 + 각자 둘 → 실제 **74**)였다.
숫자를 따라가면 git 이 그 수를 그대로 남기고 **면제 둘이 조용히 사라진다**. 세 레인의 사유
문단은 어느 것도 지우지 않고 함께 남겼다 — 그 문단들이 셈의 근거다.

**메모에 없던 이동 선언을 두 번 찾았다.** 첫 병합에서 7C⑤ 가 `EvaluationTestDoubles.kt` 로 옮긴
셋(메모에는 둘만 적혀 있었고 `EmptyCandidateSource` 가 빠져 있었다), 둘째 병합에서 7C② 가
`StrategyEditTestDoubles.kt` 로 옮긴 셋(`InMemoryEditSessionRepository` ·
`InMemoryStrategyEditTransaction` · `RecordingStrategyEventSink`). 충돌 해소에서 내 쪽을 고르면
**같은 패키지에 같은 선언이 두 벌** 남는다. 탐지법은 하나뿐이다 — **선언이 어디 있는지 양쪽에서
세는 것**(메모를 믿으면 둘 다 놓쳤다).

**조립 class 는 줄 단위로 대조했다.** 둘째 병합에서 main 본문(123줄) ↔ 내 것(129줄, 주석 제외)
의 차이가 `@Import(BidPriceTestWiring::class)` **한 줄**뿐임을 확인한 뒤 그것만 옮겼다. 빈 이름은
합집합 **26** 과 정확히 일치. `@Import` 는 `@Repeatable` 이 아니므로 다음 레인이 오면 **한 줄로
접는다**(두 줄은 컴파일 거부, 한 줄만 남기는 해소는 다른 레인 배선을 조용히 지운다).

**죽은 등재가 합집합으로 되살아나는 것도 막았다.** r2 에서 의도적으로 뺀 둘(`NoticeId` ·
`NotificationItemKey`)이 기계 합집합으로 돌아오지 않게 제외하고, **main 이 그 둘을 쓰지 않음을
먼저 확인**했다(base·main 양쪽에 없다 — 원래 내 것이었다). 이 확인을 건너뛰면 남의 게이트를
깨뜨린다.

**충돌 0 자동 병합이 가장 위험한 자리다** — 둘째 병합에서 다섯이 그렇게 들어왔고 전부 계수로
봤다: areEqual 두 자리 36↔36 동일 · `ErrorBody` 예외 네 종 각각 class 1·`when` 1·핸들러 1 ·
`capability-map` 줄 수 **3795 불변**(내 2:2 치환이 그대로) · OpenAPI path 30 · schema 33 ·
operationId 33 중복 0 · 중복 키 거부 로더 적재 성공. 생성 타입은 손으로 병합하지 않고 **재생성**
(바이트 등식 통과).

**병합이 산출물을 하나 깨뜨렸고 그것은 합집합의 당연한 결과였다** — `ErrorBody.readPathCode` 가
50줄 한도를 넘었다(§4). 「합집합이 맞다」와 「합집합이 게이트를 통과한다」는 다른 문제이고, 그
둘을 가르는 것은 병합 뒤 acceptance 다.

**V23 은 여전히 유일하다**: main 최대 V22, 열린 worktree 넷 중 V23 을 가진 곳 0.

## 10. r2 검토 처분 (verifier `ready-for-review` · code-review `request_changes`)

| finding | 처분 | 무엇으로 닫았나 |
|---|---|---|
| CR HIGH-1 접기 뒤 job 단위 `check` 미실행 | **닫음** | verifier r2 가 판정 SHA 에서 `check` exit 0 · `qualityBaseline` 0 을 직접 돌렸다(§4 머리). 지적의 성격은 산출물 결함이 아니라 **갈음 금지** 위반이었고, 그 점을 §4 에 사실로 적었다 |
| v M1 · CR MEDIUM-1 「어휘가 닫혔다」가 아직 문자열 판정 | **닫음(구조로)** | `closesVocabularyOf(column)` 가 **정의 전문을 한 형태에 일치**시킨다 — 좌변이 그 열(또는 text 캐스트) 자체이고 우변이 리터럴 배열이며 **그 밖의 절이 없는** 정의만 닫힘이다. `OR` 꼬리와 함수 감싼 꼴을 **순수 양성 대조**로 RED 고정하고, 진짜 어휘 CHECK 두 꼴(캐스트 유/무)을 **음성 대조**로 함께 묶었다(음성 대조가 없으면 「전부 열림으로 센다」는 퇴화가 통과한다) |
| v L1 공고 키 두 열이 이름만으로 면제 | **닫음** | 두 열의 CHECK 정의를 **각각 축어로 고정**했다 — 그 제약을 풀면 그 test 가 붉는다. 함께 KDoc 을 사실대로 고쳤다: 앞 판은 두 열 모두 「형태 CHECK」라고 적었으나 `notice_round` 만 세 자리 정규식이고 `notice_number` 는 **비어 있지 않음뿐**이다 |
| CR MEDIUM-2 · v L2 scope.md 가 rollback.md 와 어긋남 | **닫음** | scope.md 의 base 머리말과 rollback 줄을 `734e11b9` 로, rollback.md 등식 블록의 `46ac0949..HEAD` 도 현재 base 로. 「옛 base 복원은 다른 레인 줄까지 되돌린다」를 그 자리에 적었다. 계약 갱신 이력에 **r2 절**을 세워 새 base 둘·계수 44→42·새 파일 하나의 in_scope 귀속·죽은 등재 둘의 삭제를 등재했다 |
| v L3 §4 에 job 단위 초록 없음 | **닫음** | 위 CR HIGH-1 과 같은 자리 |
| CR LOW-1 §0.4 패키지 이름 | **닫음** | `…notification` → `…notificationinbox`. r1 에서 §0.12 만 고쳤고 **§0.4 는 다른 레인이 포트 시그니처를 읽는 절**이라 더 중요한 자리였다 |
| CR LOW-2 죽은 import 25 | **닫음** | 기계로 찾아 지웠다 — `HttpTestSupport.kt` 20(조립 이전·병합 해소 잔재) · `HttpTestApplication.kt` 5. ktlint `no-unused-imports` 가 이 설정에서 발화하지 않는다(레포 전체 같은 꼴 176) — **게이트가 아니라 사람이 지울 자리**라는 CR 의 판단에 동의한다 |
| CR LOW-3 EndpointTest KDoc 이 구현과 반대 | **닫음** | 문면을 구현에 맞췄다(`findAndAddModules()` 아님). r2 에서 그 설정을 「근거가 틀린 설정」으로 걷어냈는데 **문면만 남아 있었다** |
| CR LOW-4 읽히지 않는 fake 상태 둘 | **닫음(단언을 붙여)** | 지우는 대신 **판독한 대상이 포트에 그대로 가는지**를 재는 test 를 세웠다 — 400 들은 「거부가 된다」만 재고, 통과한 요청이 어떤 값으로 포트에 닿는지는 말하지 않는다(종류 토큰과 공고 쌍이 뒤바뀌어도 상태 코드는 같다). 공유 싱글턴이라 포착 상태 셋을 매 test 앞에서 전부 되돌린다 |
| CR LOW-5 §5 번호·순서 | **닫음** | 「열한째」(단수) → 「둘」, 항목을 11 → 12 순서로. (고치는 중에 블록을 파일 꼬리로 밀어 §6~§9 뒤에 놓는 실수를 했고 구조 계수로 잡아 되돌렸다) |

**r1·r2 를 통틀어 이 slice 의 게이트 술어는 세 번 고쳐졌다** — ⓐ 문자열 스캔(착수) → ⓑ 모집단
전수 + `conkey` 열 귀속(r1 F3) → ⓒ 정의 전문 일치(r2 M1). 매번 「구조로 닫았다」고 적었고 두 번은
**절반만 참이었다.** 남아 있던 축은 각각 달랐다(r1: 모집단·열 귀속 / r2: 제약의 모양). 교훈은
「구조로 닫았다」를 적을 때 **무엇을 구조로 닫았고 무엇이 아직 텍스트인가**를 축별로 세는 것이고,
그것을 세어 준 것은 설계 문서가 아니라 **남의 변이**였다.

