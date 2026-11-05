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

| 명령 | exit | 어디서 |
| --- | --- | --- |
| `./gradlew --no-daemon check` | 0 | 로컬(착수 라운드 `cfe23214` · 수정 라운드 1 `01ea5da0`) + **CI**(아래) |
| `./gradlew --no-daemon qualityBaseline` | 0 | 같음 |
| web job W-1~W-13(`npm ci` · lint · typecheck · gate:src-tree · api:types:check · vitest · gate:tests · build · gate:boundary-canary · playwright · 스모크 · `:app:bootJar` · 여정) | 0 | 로컬(착수 라운드) + **CI** |

수정 라운드 1 뒤 **같은 명령을 다시 돌렸다**(§5). 표적은 `:adapters:test --tests "*NotificationInbox*"`
로 좁혀 반복하고 전건은 마지막에 한 번이다. web 절은 이 라운드가 `web/` 를 건드리지 않아 그대로다
(`git diff --name-only 26ff9fe3..01ea5da0 -- web openapi` 빈 출력).

**수정 라운드 2(main 병합)의 전건은 CI 가 정본이다**(팀장 공지 2026-11-05 — 호스트 빌드 큐 포화).
로컬은 표적 `--tests` 와 변이만 돌렸다. 같은 명령이고 job 정의도 그대로다 — 병합이 들여온
`ci.yml` 변경은 **`container` job 의 S-27d 한 step** 뿐이고 `check`·`web` job 의 명령은 바뀌지
않았다(`git diff 79f3e5c4..origin/main -- .github/workflows/ci.yml` 의 추가분 전체가 그 step).

| CI run | head | job 결과 |
| --- | --- | --- |
| `42014237148` | `01ea5da0`(라운드 1 head) | check **pass** 45m45s · web **pass** 3m45s · container **pass** 7m17s · ml-engine **pass** 9m28s — **네 job 전부 초록** |
| `42029858342` | `457f6114`(라운드 4 head · **판정 SHA**) | check **pass** 50m39s · web **pass** 4m0s · container **pass** 5m56s · ml-engine **pass** 9m25s — **네 job 전부 초록** |

`container` 가 초록인 것이 #126(CVE apt 하한)의 효과다 — 그 앞 라운드의 같은 job 은 그 CVE 로 붉었다.
라운드 2·3 의 head(`9a913033`·`60c9d1c1`)는 push 가 팀장 몫이어서 레인이 run 을 띄우지 않았고,
라운드 4 는 push 허가를 받아 그 head 의 run 을 직접 띄워 위 표에 적었다.

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

## §6 수정 라운드 2 — main 병합 (팀장 공지 2026-11-05)

`origin/main` **`6c38fb32`**(#122 운영 도구 · #126 CVE apt 하한)을 합쳤다 — 병합 커밋 `606d764c`.

**합집합 함정을 계수로 확인했다**(충돌 0 이 「접히지 않았다」를 뜻하지 않는다):

| 확인 | 결과 |
| --- | --- |
| 내 경로 ∩ main 변경 경로 | **빈 집합** — 같은 파일을 양쪽이 만지지 않았다 |
| 내 23 경로가 병합 전(`01ea5da0`)과 같은가 | 차이 **0 파일** |
| main 의 15 경로가 `origin/main` 과 같은가 | 차이 **0 파일** |
| `HEAD` vs `origin/main` 의 차이가 내 경로뿐인가 | 그 밖 **0 파일** |
| 표적 test(`*NotificationInbox*` · `NotificationFactTest` · `BidCaseNotificationTest`) | exit **0** |

rollback 의 **복원 원천이 바뀌었다** — `rollback.md` §1(착수 base 가 아니라 합친 main 의 고정
SHA, 목록은 내 커밋만). 그러지 않으면 복원이 #122·#126 까지 지운다.

## §7 수정 라운드 3 — main 재병합(#125)과 v-123-r2 두 건

main 이 다시 움직였다 — `origin/main` **`4953d743`**(#125 화면 표시). 병합 커밋 `5c1f0c45`.

| 확인 | 결과 |
| --- | --- |
| 내 경로 ∩ `6c38fb32..4953d743` 변경(46 경로) | **빈 집합**. `web/src/display/**` 가 겹칠 수 있었으나 #125 가 만진 것은 `editSession`·`money`·`notice`·`time` 이고 `notificationDisplay` 는 아니다 |
| 의미 겹침 | `web/src/screens/InboxScreen.tsx`(+test)가 내 표의 **소비자**다. main 의 test 는 `NOTIFICATION_DETAIL_DISPLAY.PROVISIONAL_FIRST.label` 하나만 읽고 표 전수를 단언하지 않아 다섯 키와 호환 |
| 내 18 경로가 병합 전(`9a913033`)과 같은가 | 차이 **0 파일** |
| main 의 46 경로가 `origin/main` 과 같은가 | 차이 **0 파일** |
| 양쪽 표지 계수 | 내 쪽 — 유찰 라벨 **2**(내 표 + `caseDisplay` 의 다른 문면) · `GREATEST(…` **1** · `AwardResult` **8**. #125 쪽 — `InstantText` **7** · `screenVocabulary` **4** · `timeDisplay` **16** · `screen-card-coverage` **3** |
| 로컬 web 게이트 | lint **0** · typecheck **0** · `api:types:check` **바이트 등식**(189122) · vitest **38 파일 / 758 test** · `gate:tests` 통과 |

`web/package.json`·`package-lock.json` 은 안 바뀌어 `npm ci` 를 다시 돌리지 않았다.

**v-123-r2 L-1** 은 라운드 2 에서 이미 처리했고(복원 원천을 합친 main 의 고정 SHA 로), 라운드 3 은
그 원천을 `4953d743` 로 **다시** 옮긴 것이다 — 움직이는 ref 를 쓰지 않는다는 규율이 왜 필요한지
그 자체가 보인 자리다. **L-2** 는 알려진 제한 3 의 한 줄(두 축의 쓰기 규칙이 다르다).

## §8 수정 라운드 4 — #124 재병합(**겹친 파일 셋**)

`origin/main` **`319fe212`**(#124 예산 비우기) → 병합 커밋 `cddf2c30`. 앞 두 병합과 달리 이번에는
내 경로와 **겹쳤다**: `openapi/bidvector-operator-api.yaml` · `web/src/api/generated/operator-api.ts` ·
`config/quality/gate-tests.properties`. 충돌 0 이지만 그것이 「접히지 않았다」를 뜻하지 않으므로
축마다 따로 쟀다.

**openapi — 병합 함정 검사**

| 축 | 결과 |
| --- | --- |
| YAML 파싱 + **중복 키 검출**(`SafeLoader` 에 중복 감지 constructor 를 끼운 로더 — 기본 로더는 중복을 **조용히 마지막 값으로** 접는다) | 파싱 OK · 중복 키 **0** · 스키마 **41** |
| `NotificationItem.properties` 키 계수 | **13**(접힘 없음) |
| 내 쪽 생존 | `detail.enum` = 다섯(`FAILED_OR_REBID` 포함) · `description` 있음 · 축어 enum 항 **1** |
| #124 쪽 생존 | `EditSessionValueRequest.amountWon` 의 `nullable: true` + 「명시적 비움」 `description` 있음 · 축어 **1** |
| 두 절이 **다른 자리**인가 | 내 절 ~2469 · #124 절 ~3021 — 같은 본문을 공유하지 않는다 |

**생성 타입 — 손 병합하지 않고 재생**: `npm run api:types` 를 돌린 뒤 **diff 0 파일**이었다(병합
결과가 이미 정확했다는 뜻이고, 그것을 재생으로 **확인**했다). `api:types:check` 바이트 등식 통과
(**189564 bytes** — #124 의 description 이 늘린 만큼 앞 라운드의 189122 에서 자랐다).

**`gate-tests.properties`**: 병합 결과가 `origin/main` 과 **한 줄** 차이(내 등재)다. 내 등재와
#124 등재 각각 **1**. 블록 안 사전순은 `adapters`(188 항, 내 등재 포함) **정렬 OK** — 어긋난 블록
셋(`app`·`decision`·`procurement`)은 **main 에서도 어긋나 있던 기존 상태**이고 이 병합이 만든 것이
아니다(같은 검사를 `origin/main` 본문에 돌려 확인).

**로컬 게이트**: web lint **0** · typecheck **0** · vitest **38 파일 / 762 test** · `gate:tests` 통과 ·
`api:types:check` 통과. Kotlin 표적 — `:adapters:test *NotificationInbox*` · `:workflow:test`
사실·사상 · **`:app:test *OpenApi*`**(계약 게이트 전수) + `NotificationInboxEndpointTest` ·
세 모듈 `ktlintCheck` → **exit 0**.

## §9 수정 라운드 5 — #129 재병합(**내 test 파일 둘과 겹침**)

`origin/main` **`31551579`**(#129 보존 1년) → 병합 커밋 `c4354840`. 이번 겹침은 **내 in_scope
test 파일 둘**이다 — `NotificationInboxPersistenceTest` · `NotificationInboxPrivilegeTest`.

| 축 | 결과 |
| --- | --- |
| #129 가 그 둘에서 바꾼 것 | **주석 문면만** — 「90일 약속」→「보존 기간 약속」 · 「90일이면 사라진다」→「기한이 지나면 사라진다」(보존 기간을 1년으로 일반화하며 문면을 넓힌 것) |
| 병합 뒤 양쪽 생존 | #129 문면 **각 1** · 내 쪽 test 표지 **3** · `setBoolean(7, true)` **1** · 낡은 「90일 약속」 **0** |
| 겹친 둘의 변화량 | `2 files changed, 2 insertions(+), 2 deletions(-)` — 정확히 #129 의 두 줄 |
| 내 나머지 16 경로 | 병합 전과 차이 **0 파일** |
| main 의 32 경로 | `origin/main` 과 차이 **2** — 그 둘이 겹친 파일이고 내 변경을 담으므로 달라야 한다 |
| `HEAD` vs `origin/main` | 차이가 내 경로뿐 |
| 마이그레이션 번호 | #129 는 마이그레이션을 더하지 않았다 — V24 와 충돌 0 |
| 내 산출물·evidence 의 「90일」 주장 | **0** — 보존 기간을 숫자로 적은 자리가 없어 #129 와 어긋날 문면이 없다 |

**로컬 게이트**: Kotlin 표적(`*NotificationInbox*` · 사실·사상 · `:app:test *OpenApi*` ·
`NotificationInboxEndpointTest`) + 세 모듈 `ktlintCheck` **exit 0**. web lint **0** · typecheck **0** ·
vitest **38 파일 / 762 test** · `gate:tests` 통과 · `api:types:check` **189564 bytes**.

**복원이 겹친 파일에서도 남의 줄을 지키는가** — 가장 날카로운 자리다. 복원 뒤 그 두 파일에 #129 의
문면이 **각 1** 로 남고 내 표지는 0 이다(§3 의 ③′). 즉 「내 것만 걷어내고 남의 것은 그 파일 안에
그대로」가 계수로 선다.

## §10 재활용 조사가 찾은 이름 충돌

새 enum 의 첫 이름은 `AwardOutcome` 이었고, 그 이름은 `bidvector.settlement.AwardOutcome` 이
**다른 개념**으로 이미 쓰고 있다(개찰 대사의 4-state). `workflow` 모듈은 그 타입을 실제로 든다
(`workflow.result.ReconciliationVerdict`) — 같은 모듈에서 같은 이름 둘이 import 로만 갈리는
상태였다. `check` 는 초록이었다(패키지가 달라 컴파일 충돌이 없다).

**찾은 자리는 rollback 표지 계수다**: 복원 뒤 0 이어야 할 `AwardOutcome` 이 **20 파일**로
나왔다. `AwardResult` 로 바꾸고 재활용 조사의 결론(기존 어휘 셋을 보고 쓰지 않은 근거)을
그 타입의 KDoc 과 `checklist.md` §3 에 남겼다.

## §11 되돌림 리허설

`rollback.md` — 두 갈래(미적용 DB = 파일 삭제 / 적용 DB = 새 V 로 원복 + 유찰 행 처분)를 버릴
clone 에서 실측했다.
