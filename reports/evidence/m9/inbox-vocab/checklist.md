# M9/inbox-vocab 점검

계약 `scope.md` · 설계 검토 `design-review.md` · 실행 기록 `commands.md`.

## 1. 설계 검토의 우회 ↔ 무엇이 막는가

| 우회 | 막는 것 | 실측 |
| --- | --- | --- |
| 1 유찰을 패찰로 접는다 | 결과 전수 distinct 등식 + 왕복 복원(`single` 이 어긋난다) + 상태→사실 전수 등식 | 변이 ①a·①b |
| 2 한쪽 어휘만 넓힌다 | DB CHECK ↔ enum 등식 · openapi ↔ enum 등식 · 생성 타입 바이트 등식 | 변이 ③a · RED 라운드 1·3 |
| 3 어휘만 넓히고 짝 CHECK 를 안 고친다 | 종류 × detail **전수 교차**(실 DB) | 변이 ③b — 어휘 등식은 초록, 교차만 붉다 |
| 4 V24 가 옛 제약을 못 지우고 새것만 더한다 | `DROP CONSTRAINT` 에 `IF EXISTS` 없음(그 자리에서 마이그레이션 실패) + 제약 수 래칫(5) + detail_code 의 닫는 정의가 둘이면 `single` 이 어긋난다 | 이름 추측이 맞았음을 V24 적용 성공이 보인다(틀렸으면 실 DB test 전건이 붉다) |
| 5 읽음 해제를 SQL 의 종류 리터럴로 적는다 | **거동 게이트**(출하 `UPSERT_ITEM` 직접 실행 · 종류 전수 × bind{T,F}) + 진단용 축어 검사(단어 경계) + 모집단 이름 단언 | 변이 ⑤(축어) · 수정 라운드의 변이 둘(`LIKE` 접두 · 리터럴 연결) — 셋 다 **60 test 중 이 게이트 하나만** 붉다 |
| 6 정책 표를 뒤집는다 | 파티션 등식 둘(양성·음성) | 변이 ④ — 전수 거동 test 는 초록(종류가 파티션을 옮겨간다) |
| 7 화면이 유찰을 패찰과 같은 문면으로 적는다 | detail 라벨 distinct(다섯) + 이름을 든 단언 + `satisfies Record<…>` | W-3·W-6 |
| 8 생성 타입을 손으로 고친다 | W-5 바이트 등식 | W-5 |
| 9 워터마크를 역행시켜 안 바뀐 개정에 배지를 다시 세운다 | `occurred_at = GREATEST(…)` + 음성 대조의 셋째 걸음·시각 단언 + 종류 전수 단조 test | 수정 라운드 RED 2(cr M-1 · v L-1) |
| 10 정책 게이트의 모집단에서 그 문장이 빠진다 | 이름을 든 모집단 단언(`shouldContainAll` 넷) | `UPSERT_ITEM` 개명 변이 — 그 단언만 붉다 |

## 2. 게이트 등재

| 게이트 | 등재 | 근거 |
| --- | --- | --- |
| `config/quality/gate-tests.properties` (`gate.tests.adapters`) | `NotificationInboxSqlPolicyTest` 하나 | 신설 class — 만드는 커밋에서 같이 등재(블록 안 사전순) |

수정 라운드 1 은 **새 test class 를 만들지 않았다**(기존 `NotificationInboxSqlPolicyTest` 를
거동 게이트로 바꿨다) — 등재는 그대로다.

스키마 스냅샷 래칫(`CleanMigration*`)은 **건드리지 않았다**: 제약을 같은 이름으로 재선언하므로
개수(이 표 5)·열·FK·권한·인덱스가 그대로다.

## 3. 재활용 조사 — 기존 어휘 셋을 보고 쓰지 않은 근거

| 기존 타입 | 왜 쓰지 않았나 |
| --- | --- |
| `bidvector.bidding.BidOutcome`(`WON`·`LOST`·`FAILED_OR_REBID`) | **값은 같다.** 그래도 알림함 detail 을 거기 묶으면 알림함이 입찰 모듈 없이는 서지 않는다(7C③ 이 이 패키지 의존을 `procurement.NoticeId`·`workflow.access.AccountId` 둘로 묶은 사유). 두 벌이 갈리는 것은 상태 사상 전수 등식이 막는다 |
| `bidvector.settlement.AwardOutcome`(4-state) | 집합이 다르다 — **유찰이 없다**(미기록·판정 안 함·낙찰·패찰). 자동 대사가 들어오면 그 집합이 같은 종류의 둘째 원천이 되지만 알림함 어휘와 같아지지는 않는다 |
| `bidvector.bidding.BidStateTag.FAILED_OR_REBID` | 상태 꼬리표(열넷)이고 detail 어휘가 아니다 |

**그 조사가 이름 충돌을 찾았다**: 처음 판은 새 enum 을 `AwardOutcome` 으로 지었는데 그 이름은
`settlement` 이 **다른 개념**으로 쓰고 있고, `workflow` 모듈이 그 타입을 실제로 든다
(`workflow.result.ReconciliationVerdict` 의 import). 같은 모듈에서 같은 이름 둘이 import 로만
갈리는 상태였다 — `check` 는 초록이었다(패키지가 달라 컴파일 충돌이 없다). `AwardResult` 로
바꿨다. 찾은 자리는 rollback 표지 계수다(복원 뒤 0 이어야 할 표지가 20 으로 나왔다).

## 4. 수정 라운드가 만든 새 파일 ↔ in_scope

새 파일 **둘**이고 둘 다 in_scope 안이다.

| 새 파일 | in_scope 항 |
| --- | --- |
| `adapters/src/main/resources/db/migration/V24__notification_inbox_failed_bid_detail.sql` | `…/db/migration/V24__*.sql` |
| `adapters/src/test/kotlin/bidvector/adapters/notificationinbox/NotificationInboxSqlPolicyTest.kt` | `adapters/…/notificationinbox/** 와 그 test` |

## 5. 알려진 제한

1. **D-3 문면이 아니라 D-3′ 다**(계약 갱신 2026-11-05 로 **승인**됨 — `scope.md` 의 「계약 갱신」
   절). 「매 재기록」이 아니라 「사실의 시각이 전진한 재기록」이다: 문면 그대로면 개정 알림의
   원천인 훑기가 같은 개정을 run 마다 다시 적어 배지를 영영 끌 수 없다(`commands.md` §1 의 근거
   셋). 제한으로 남는 것은 **그 전진의 축**이고 아래 2 가 그것이다.
2. **「새 개정」의 축이 개정 번호가 아니라 사실의 시각이다.** 알림함 행은 개정 번호를 담지
   않고(열 추가는 out_scope) 그 동치는 V2 트리거가 `revision` 과 `updated_at` 을 **함께**
   올린다는 사실에 기댄다. 그 트리거를 가르는 변경이 오면 이 동치가 조용히 깨진다 — 그때의
   게이트는 V2 쪽에 서야 하고 이 slice 는 세우지 않았다.
   **저장된 쪽의 단조성은 닫았다**(수정 라운드 1 · `GREATEST`): 옛 사실이 늦게 도착해도 워터마크가
   후퇴하지 않으므로 「같은 개정이 다시 배지를 세우는」 꼴은 더 이상 표현되지 않는다.
3. **`occurred_at` 은 이제 종류 전수에서 단조다.** 늦게 도착한 옛 사실의 시각은 **기록되지 않고
   버려진다**(행에는 더 큰 값이 남는다). 「그 사실이 실제로 언제 일어났는지」를 뒤늦게 교정하는
   경로가 이 표에 없다는 뜻이고, 그 대가로 읽음 상태와 목록 순서가 흔들리지 않는다. 교정이
   필요해지면 축을 늘리는 결정(개정 번호 열)이 먼저다.
4. **건 상태 표 하나가 다른 문면을 쓴다.** 알림함 detail 은 「유찰·재입찰」(계약 D-4 문면)이고
   이 문면이 저장소의 **지배적 표기**다(`MyBidResultTally` · `myBidResultDisplay` · openapi ·
   backtest 규칙 등 열 자리). 다른 문면은 **하나** — `web/src/display/caseDisplay.ts` 의 건 상태
   라벨 「유찰·재공고」다. 그 파일은 out_scope(화면 소유 레인)이고, 통일 여부는 그 레인·운영자
   결정이다. 이 slice 는 계약 문면 = 지배적 표기를 따랐다.
5. **유찰 알림의 원천은 운영자 기록뿐이다.** 자동 대사는 여전히 서지 않으므로 NOTI-04 ·
   SET-02 의 capability 판정은 올라가지 않는다(D-7C3B-7 ③ 그대로).
6. **새 개정은 목록 맨 위로 온다** — `occurred_at` 이 정렬 축이라 전진이 순서도 올린다. 7C③
   의 기존 거동이고 이 slice 가 바꾼 것이 아니다.
7. **페이퍼 투찰 알림은 없다**(out_scope · `OPEN-7C3B-PAPER-BID-NOTIFICATION`).
8. **`docs/discovery/data-dictionary.md` 를 고치지 않았다.** 그 문서에 **알림함 어휘 절이
   없다**(`notification_inbox` 0건 — 알림함은 ADR 0005 맥락의 한 줄뿐). 없는 절을 이 slice 가
   새로 세우는 것은 범위에 비해 크다고 보고 손대지 않았다. in_scope 는 허용하지만 acceptance 가
   요구하지 않는다.
9. **알림 기록 실패는 여전히 원천을 되돌리지 않는다**(D-7C3B-9 그대로) — 유찰 전이가 알림을
   못 남겨도 전이는 남는다.
