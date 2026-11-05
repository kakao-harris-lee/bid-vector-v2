# M9/inbox-vocab 실행 기록

base `79f3e5c4` · 브랜치 `feature/m9-inbox-vocab` · 계약 `scope.md`(D-1~D-4) · 설계 검토
`design-review.md`.

재현은 worktree 뿌리에서. 무거운 Gradle 명령은 `~/.local/bin/bv-build.sh <명령>` 으로 감싼다
(호스트 빌드 자리 둘 · 획득 순간 메모리 재확인).

## §1 착수 실측 — D-3 의 술어를 뒤집은 자리

계약 D-3 은 「`NOTICE_REVISED` 는 **같은 키 재기록이면** `read_at` 을 NULL 로(새 개정 = 새
소식)」이었다. 착수 조사에서 그 술어가 **의도를 배반한다**는 것이 나왔고, 팀장에게 계약 갱신을
요청한 뒤 괄호 안 의도(「새 개정」)를 구현했다. 근거 셋:

| 사실 | 자리 |
| --- | --- |
| 개정 알림의 **유일한 원천**은 훑기이고, 조건 넷이 유지되는 **동안 매 run 마다** 같은 사실을 다시 적는다 | `NotificationSweep.kt` 의 `noticeRevisedAt`(in_scope 밖) |
| 그 기록의 사실 시각은 훑은 시각이 아니라 **`notice.updated_at`** 이다 — 같은 개정이면 run 마다 같은 값 | 같은 함수 |
| `revision` 과 `updated_at` 은 **같은 트리거**가 함께 올리고, 재수집이 내용을 안 바꾸면 UPDATE 자체가 돌지 않는다(`merged == existing` → `Unchanged`) | V2 의 `notice_revision_bump` · `JdbcNoticeRepository.updateNotice` |

그래서 **`occurred_at` 전진 ⟺ 새 개정**이고, 문면 그대로의 「매 재기록」은 읽음 처리가 영영
유지되지 않게 만든다(배지를 끌 수 없다 — 지금의 알려진 제한 13 보다 나쁘다).

## §2 RED 실측

| 라운드 | 명령(요지) | 무엇이 붉었나 |
| --- | --- | --- |
| 어휘(V24 전) | `:adapters:test --tests "*NotificationInbox{Privacy,Persistence}Test*"` | **3** — detail 어휘 등식 · 짝 전수 교차 · 유찰 기록(`Updated` 기대, `Failed`) |
| 사상 | `:workflow:test --tests "*BidCaseNotificationTest*"` | **2** — 되비추는 상태 집합(확정 둘 → 셋) · 유찰의 detail |
| 생성 타입 | `npm run typecheck` | **3** — `satisfies Record<NotificationDetail, …>` 와 그 소비자 둘 |

D-3 의 RED 는 **변이로 세웠다**(§4 ②) — SQL 의 `CASE` 와 bind 개수가 한 묶음이라, 구현 전에
test 만 넣으면 「바인딩 수가 안 맞는다」가 붉어 **다른 것을 재게 된다**.

## §3 acceptance

| 명령 | exit |
| --- | --- |
| `./gradlew --no-daemon check` | 0 |
| `./gradlew --no-daemon qualityBaseline` | 0 |
| web job W-1~W-13(`npm ci` · lint · typecheck · gate:src-tree · api:types:check · vitest · gate:tests · build · gate:boundary-canary · playwright · 스모크 · `:app:bootJar` · 여정) | 0 |

수정 라운드 1 뒤 **같은 명령을 다시 돌렸다**(§5). 표적은 `:adapters:test --tests "*NotificationInbox*"`
로 좁혀 반복하고 전건은 마지막에 한 번이다. web 절은 이 라운드가 `web/` 를 건드리지 않아 그대로다
(`git diff --name-only 26ff9fe3..HEAD -- web openapi` 빈 출력).

`tools/one-command-check.sh`(check job 의 넷째 step)는 **안 돌렸다.** 그 안의 Kotlin 절과 web
절은 위와 **같은 명령**이고, ml-engine 절은 acceptance 밖이다(이 slice 의 Python 변경 0 —
`git diff --name-only 79f3e5c4..HEAD -- ml-engine` 빈 출력). CI 는 전문을 돌린다.

## §4 변이 다섯 — 각각 RED

변이는 **바꿔치우기**이고 커밋 뒤에 돌렸다(원복은 변이 파일만 `git checkout --`).

| 변이 | 무엇을 바꿨나 | 붉은 자리 |
| --- | --- | --- |
| ①a 사상 접기 | `FailedOrRebid -> AwardDecided(NOT_AWARDED)` | `유찰은 패찰로 접히지 않고 자기 detail 로 선다` |
| ①b 어휘 접기 | `AwardResult.FAILED_OR_REBID(NOT_AWARDED)` | `NotificationFactTest` **4**(왕복 · 고아 없음 · 결과 셋 · distinct) |
| ② 읽음 해제 원판 복귀 | upsert 의 `CASE` 에서 시각 갈래 + bind 제거 | `읽은 개정 항목은 더 늦은 개정에 안읽음으로 돌아온다` **하나만** — 같은·옛 개정 유지 test 는 초록(두 축이 갈려 있다) |
| ③a V24 어휘 축소 | detail CHECK 에서 새 값 제거 | **3** — 어휘 등식 · 교차 · 유찰 기록 |
| ③b 짝만 옛 어휘 | 어휘는 넓히고 **짝 CHECK** 만 V23 본문 | **2** — 교차 · 유찰 기록. **어휘 등식은 초록**(교차가 없으면 못 보는 자리다) |
| ④ 정책 표 뒤집기 | `NOTICE_REVISED -> ON_DETAIL_CHANGE` | 파티션 등식 **2** + 실 DB 재안읽음 **1**. 전수 유지 test 는 초록 — 그 종류가 자기 파티션으로 **옮겨가** 통과한다(파티션 등식이 없으면 둘 다 초록이다) |
| ⑤ 정책을 SQL 로 | `CASE` 에 `kind = 'NOTICE_REVISED'` 리터럴(거동 동일) | `NotificationInboxSqlPolicyTest` **하나만** — 실 DB 거동 test 20 은 **전부 초록** |

⑤ 가 신설 게이트의 사유다: 거동으로는 보이지 않고, 잃는 것은 「종류를 더할 때 컴파일이 막아
준다」는 성질이다.

## §5 수정 라운드 1 (cr request_changes · v ready-for-review @`26ff9fe3`)

finding 별 별도 커밋이고 각각 RED 선행이다.

| finding | 처방 | RED 실측 | 커밋 |
| --- | --- | --- | --- |
| cr M-1 = v L-1 워터마크 역행 | `occurred_at = GREATEST(기존, EXCLUDED)` | **2** — 음성 대조의 ② 시각 단언 · 종류 전수 단조 test(둘 다 `2026-10-23` 기대, `2026-10-22` 관측) | `5b2ee387` |
| cr M-2 + v M-1 게이트 술어 | 문자열 → **거동**(종류 전수 × bind{T,F}) | v 의 변이 둘을 재현: `kind LIKE 'NOTICE_REV%'` · `kind = 'NOTICE_' \|\| 'REVISED'` → **각각 60 test 중 이 하나만 RED**. 모집단 단언은 상수 이름이 홀더를 떠나는 변이에서 RED | `bb45e82a` |
| cr L-1 V24 주석 | `AwardOutcome` → `AwardResult` | — (문면) | `0df93d4c` |
| cr L-2·L-3 · v L-2 · cr L-4 · v L-3 | evidence 정정 | — (장부) | 이 커밋 |

**cr M-2 의 기술 근거 하나는 사실이 아니다 — 처방은 그래도 채택했다.** 「`Modifier.isStatic` 은
`const val` 만 본다」가 전제였는데, Kotlin `object` 의 평범한 `val` 도 백킹 필드가 **static** 이다.
바이트코드로 확인했다(`javap -p -c`): `const val` 넷은 `public static final`, `val` 로 바꾼
`UPSERT_ITEM` 은 `private static final` + getter 다. 그래서 `const val` → `val` 변이는 모집단을
떠나지 **않고**(실측: 그 변이에서 게이트가 초록) cr 이 그린 구멍은 그 경로로 열리지 않는다.
처방(이름을 든 모집단 단언)은 **다른 경로**를 막으므로 그대로 넣었다 — 상수가 홀더를 떠나거나
이름이 바뀌는 꼴이다. 그 경로로 변이를 세워 RED 를 실측했다(`UPSERT_ITEM` 개명 → 모집단 단언만 붉다).

**바뀐 거동 하나를 이름 붙인다**: `GREATEST` 는 `ON_DETAIL_CHANGE` 종류의 `occurred_at` 의미도
바꾼다(어느 종류든 후퇴 없음). 그 값은 목록 정렬 축이기도 해서 단조가 더 맞고, 종류 전수 test 가
그 성질을 잠근다.

## §6 재활용 조사가 찾은 이름 충돌

새 enum 의 첫 이름은 `AwardOutcome` 이었고, 그 이름은 `bidvector.settlement.AwardOutcome` 이
**다른 개념**으로 이미 쓰고 있다(개찰 대사의 4-state). `workflow` 모듈은 그 타입을 실제로 든다
(`workflow.result.ReconciliationVerdict`) — 같은 모듈에서 같은 이름 둘이 import 로만 갈리는
상태였다. `check` 는 초록이었다(패키지가 달라 컴파일 충돌이 없다).

**찾은 자리는 rollback 표지 계수다**: 복원 뒤 0 이어야 할 `AwardOutcome` 이 **20 파일**로
나왔다. `AwardResult` 로 바꾸고 재활용 조사의 결론(기존 어휘 셋을 보고 쓰지 않은 근거)을
그 타입의 KDoc 과 `checklist.md` §3 에 남겼다.

## §7 되돌림 리허설

`rollback.md` — 두 갈래(미적용 DB = 파일 삭제 / 적용 DB = 새 V 로 원복 + 유찰 행 처분)를 버릴
clone 에서 실측했다.
