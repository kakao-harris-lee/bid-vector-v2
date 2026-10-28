# M7/7C③ — 실행 명령과 종료 코드

base `46ac0949` · 브랜치 `m7-7c3/2026-10-23` · worktree `bid-vector-v2-m7-7c3`.

호스트 규율: 모든 Gradle·npm 명령은 `flock ~/.local/bid-vector-build.lock` 아래 돌렸고 점검 셋
(`pgrep`·`free -m`·`ps`)을 **별도 호출로 먼저** 봤다. 이 라운드 동안 같은 락에 **다른 레인
10~16개가 줄 서 있어** 측정이 직렬로 느리게 났다(available 11~13GB · swap free 6.1~6.6GB
구간에서만 시작했고, 00:30~03:00 창은 피했다).

**아직 실측하지 않은 항목은 `(기록)` 이다** — 돌지 않은 명령의 결과를 적지 않는다.

## 2026-10-23T02:15Z — workflow test (순수 커널·파싱)

```
./gradlew --no-daemon :workflow:test --tests 'bidvector.workflow.notificationinbox.*'
```

`BUILD SUCCESSFUL` · exit 0. test 수는 결과 XML 에서 센다 — `NotificationFactTest` 9 ·
`NotificationPageParsingTest` 12, **실패 0 · skip 0**.

## 2026-10-23T03:30Z — 어댑터 test r1 (실 PostgreSQL · Testcontainers) — **RED, 결함 셋**

```
./gradlew --no-daemon :adapters:test \
  --tests 'bidvector.adapters.notificationinbox.*' \
  --tests 'bidvector.adapters.persistence.CleanMigration*' \
  --tests 'bidvector.adapters.retention.*'
```

`CleanMigration*`·`retention.*` 를 함께 돌리는 이유: 이 slice 가 **표를 더하므로** 스키마 스냅샷
래칫과 보존 분류의 **집합 등식 둘**(레지스트리↔카탈로그 · 심기 fixture↔분류)이 같은 커밋에서
함께 서야 한다.

`BUILD FAILED` · exit 1 · **176 tests / 3 failed**. 세 실패의 원인이 셋 다 달랐다:

| 실패 | 원인 | 처분 |
|---|---|---|
| `NotificationInboxPrivacyTest > detail 을 갖는 종류에 detail 이 없으면 DB 가 거부한다` | **내 DDL 의 결함** — 짝 CHECK 가 세 값 논리로 열려 있었다(아래) | 수정 |
| `CleanMigrationColumnTest > 축2·3·4` | 스냅샷 래칫의 **컬럼 축** 등재 누락 | 등재 |
| `CleanMigrationIndexTest > 축9` | 같은 래칫의 **인덱스 축** 등재 누락 | 등재 |

**내 test 가 내 DDL 의 결함을 잡은 자리**: `kind = 'OPENING_COMPLETED'` 인데 `detail_code` 가
NULL 이면 `detail_code IN (…)` 이 **NULL** 이 되어 첫 갈래가 `TRUE AND NULL` = NULL, 나머지
둘은 FALSE 이므로 전체가 `NULL OR FALSE OR FALSE` = **NULL** 이고, PostgreSQL 은 NULL 인 CHECK 를
**만족으로 본다**. 그래서 「detail 을 싣는 종류인데 detail 이 없는」 행이 조용히 들어갔다 —
Kotlin 쪽 sealed 구성은 그 상태를 표현하지 못하지만 이 CHECK 는 codec 이 어긋나게 쓸 때를 막는
**심층 방어**이고 그 층이 비어 있었다. 각 갈래에 `detail_code IS NOT NULL` 을 더해 닫았다.

**통과한 것(같은 run)** — 이 slice 의 핵심 주장들이 여기서 섰다:

| class | 결과 | 선 성질 |
|---|---|---|
| `NotificationInboxConcurrencyTest` | **2/2** | **경합에서 항목이 증식하지 않는다** — 두 연결 동시 기록이 행 1 · 결과가 `{Created, Updated}`(거부 0) · 음성 대조로 키가 다르면 둘 |
| `NotificationInboxPersistenceTest` | **27/27** | 중복 억제 · 재기록의 시각 거동 · detail 변화의 읽음 해제 · 계정 경계(남의 항목 읽기·읽음 처리 0) · 묶음 계수(안읽음 필터에도 전체 크기 유지) · 공고 합침 셋(있음/없음/제목만 없음) · 정렬·페이지 |
| `NotificationInboxPrivilegeTest` | **7/7** | **열 단위 GRANT** — 적재 시각·소유 계정·종류·공고 키를 못 바꾸고 DELETE·TRUNCATE 가 막히며, 네 열 권한으로 **upsert 문장이 그대로 돈다** |
| `NotificationInboxPrivacyTest` | 8/9 | 카탈로그 등식(TEXT 열 전수가 닫힌 어휘이거나 공고 키) · 어휘 enum↔CHECK 둘 · 양성/음성 대조 |

## 2026-10-23T03:40Z — 생성 타입 재생성 (W-5)

```
npm ci && npm run api:types     # web/
```

exit 0 · `npm ci` 265 packages · `operator-api.ts` **+314 줄 · 삭제 0**. 추가만이라는 것이
「기존 operation 의 타입이 바뀌지 않았다」는 뜻이다. `api:types:check`(바이트 등식)는 `web` job
전체와 함께 잰다 — **W-5 가 그 명령이고 exit 0** 이다(§4 의 web W-1~W-9 아홉 전부 0).

## 2026-10-23T03:35Z — app test r1 — **RED, 컴파일 실패**

```
./gradlew --no-daemon :app:test \
  --tests 'bidvector.app.http.SessionAccountTest' \
  --tests 'bidvector.app.http.OpenApiNotificationInboxContractTest'
```

`BUILD FAILED` · exit 1 · `:app:compileKotlin FAILED`. 원인 하나: `requireKnownKeys` 가 같은
패키지의 기존 top-level 확장과 시그니처가 같아 **Conflicting overloads** 이고 호출 자리 둘이
**Overload resolution ambiguity** 였다(`private` 는 파일 범위지만 같은 패키지의 top-level
확장끼리는 그 검사를 지나지 못한다). 세 helper 에 접두를 붙여 고쳤다 — 예외 타입을 갈라 둔
판단은 유지한다(어느 표면이 거부했는가가 진단이고, 이름 충돌은 이름으로 끝난다).

## 2026-10-23T04:05Z — 어댑터·app test r2 (위 수정 넷 뒤 재측정) — **전부 통과**

```
./gradlew --no-daemon \
  :adapters:test --tests 'bidvector.adapters.notificationinbox.*' \
                 --tests 'bidvector.adapters.persistence.CleanMigration*' \
                 --tests 'bidvector.adapters.retention.*' \
  :app:test --tests 'bidvector.app.http.SessionAccountTest' \
            --tests 'bidvector.app.http.OpenApiNotificationInboxContractTest'
```

`BUILD SUCCESSFUL` · exit 0. **종료 코드만 믿지 않고 class 별 계수를 결과 XML 에서 센다**:

| class | tests / skipped / failures / errors |
|---|---|
| `NotificationInboxConcurrencyTest` | 2 / 0 / **0** / 0 |
| `NotificationInboxPersistenceTest` | 27 / 0 / **0** / 0 |
| `NotificationInboxPrivacyTest` | 9 / 0 / **0** / 0 (r1 의 DDL 결함이 닫혔다) |
| `NotificationInboxPrivilegeTest` | 7 / 0 / **0** / 0 |
| `CleanMigrationTest` | 5 / 0 / 0 / 0 |
| `CleanMigrationColumnTest` | 3 / 0 / **0** / 0 (r1 RED → 등재 뒤 통과) |
| `CleanMigrationIndexTest` | 2 / 0 / **0** / 0 (r1 RED → 등재 뒤 통과) |
| `CleanMigrationCheckTest` | 10 / 0 / 0 / 0 |
| `CleanMigrationPrivilegeTest` | 1 / 0 / 0 / 0 |
| `CleanMigrationTriggerTest` | 4 / 0 / 0 / 0 |
| `RetentionRegistrySchemaEqualityTest` | 12 / 0 / 0 / 0 |
| `SessionAccountTest` | 5 / 0 / **0** / 0 (r1 컴파일 실패 → 이름 수정 뒤 통과) |
| `OpenApiNotificationInboxContractTest` | 14 / 0 / **0** / 0 |

**skip 0** 이다 — 통과를 비활성으로 산 자리가 없다.

## 2026-10-23T04:10Z — 빌드 없이 미리 잰 정적 확인 넷 (빌드 슬롯 절약)

락 대기가 길어 **다음 라운드를 태우기 전에** 게이트 술어를 손으로 재현했다. 초록을 미리 주장한
것이 아니라 **붉을 자리를 먼저 찾으려** 돌린 것이고, 넷 다 통과라 재측정이 한 번에 끝났다.

| 확인 | 방법 | 결과 |
|---|---|---|
| 평탄 속성 게이트 | 계약 test 의 허용 목록 술어를 그대로 재현해 **property 정의 전수** 17 스키마에 적용 | 위반 0 |
| 계정 전용 선언 | operation 셋이 **자기 `security` 를 선언**하는지(전역 기본을 물려받으면 문서가 거짓) | GET=`{browserSession}` · POST 둘=`{browserSession, operatorCsrf}` · 자격 헤더 대안 0 |
| 응답·요청 키 집합 | 계약 test 의 **리터럴 기대 집합을 test 소스에서 읽어** YAML 과 대조 | 넷 다 일치 · `properties == required` |
| 어휘 등식 | YAML enum ↔ **Kotlin enum 소스**에서 읽은 집합 | 종류·detail 둘 다 일치 · 요청 kind == 항목 kind |

**약속한 grep 도 함께 돌렸다**(D-7C3-2 전건) — 주체 문자열을 **파싱하는** 자리는
`SessionAccount.kt` **하나**다. 나머지 일치는 KDoc 언급이거나 **짓는** 쪽(세션 인증기의
`subjectOf`)이다. 그래서 7B 접근자로의 교체는 **파일 삭제 + 호출 한 줄 치환**으로 끝난다.

## 변이 (적용 → 표적 재실행 → 사본 복원)

표적은 `checklist.md` §0.9 의 우회 표다. **더하기만 한 변이는 초록이므로 바꿔치운다**(음성
대조를 함께 남긴다). 변이 **전에 커밋**한다 — 미커밋 산출물이 복원으로 함께 지워지지 않게.

## 비밀값 스캔

```
grep -rniE -f config/quality/leak-patterns.txt <이 slice 가 더한 경로들>
```

참조형으로만 돌린다(어휘를 이 파일에 축어로 적지 않는다).

**실측(`e09168cd`, 42 경로 전수)**: 적중 **21 줄**(r4·승인 전 일괄 두 라운드가 더한 적중 각각 **0** — 라운드마다 재측정한다). 각 줄을 `git blame` 으로 귀속해 이 slice 가
**더한 줄은 넷**이고(나머지 열일곱은 이 slice 가 만지기 전부터 있던 줄), 넷 전부 요청 판독의
**공개 어휘 토큰**을 담는 지역 변수 이름과 그것을 인용한 허용 목록 사유 문단이다 — **값 0**.
귀속을 같이 세는 이유: 적중 수만 적으면 남이 만든 줄과 내가 만든 줄이 한 숫자에 섞여, 다음
라운드에 그 수가 움직여도 누구 것이 움직였는지 알 수 없다.

## clean-tree 게이트

```
git status --porcelain -- <in_scope 개별 인자>
```

빈 출력 + 양성 대조 1회(비파괴 절삭 — `checkout --` 를 쓰지 않는다).

**실측(`e09168cd`)**: 42 경로를 **개별 인자**로 넘겨 음성 쪽 `PORCELAIN_EXIT=0` · **0 줄**.
양성 대조는 V23 파일을 비파괴로 바꾼 뒤(사본을 떠 두고 주석 한 줄 덧댐 → **사본으로** 되돌림)
같은 명령이 **그 한 파일만** 들고 오는지 보았다 — 출력 **정확히 1 줄**(` M …V23__notification_inbox.sql`),
되돌린 뒤 다시 **0 줄** 이고 `git diff --stat` 도 0 줄이다. `checkout --` 는 쓰지 않는다 —
미커밋 산출물을 함께 지우기 때문이다.

## acceptance: `check` job · `web` job · `container` job

`ci.yml` 의 job 명령 **그대로**다. 명령·종료 코드·실행 계수는 `checklist.md` §4 가 든다(여기서
다시 적지 않는다). 요지: **실측 HEAD `db991f25`** 에서 `clean`·`check`·`qualityBaseline`·
web(W-1~W-9)·S-20 **열셋 전부 0**, FAILED task 0. 이 창에서는 `:adapters:test`·`:app:test` 가
**표식 없이 실행**됐고 `:workflow:test` 만 FROM-CACHE 라, 그 하나를 `--rerun` 으로 강제해 세
모듈 전부를 실행 계수로 남겼다(§4). 라운드마다 이 구분을 다시 보는 이유는 r3 에서 같은 명령이
test task 아홉을 **복원**으로 처리했기 때문이다 — 캐시 적중은 창마다 다르다.

`container` job 은 **CI 의 PR head 커밋이 정본**이다(팀장 2026-10-27 — 부분 게이트는 안 돌린
것과 같고 줄일 수 있는 단위는 job 이다). 로컬에서 `bootJar`·앱 이미지·위생만 돌린 것은
**참고이고 판정 근거가 아니다.** `ml-engine` job 은 입력 변경 0 으로 해당 없음.

`check` 의 exit 0 은 「전부 실행됐다」가 아니었다 — `:adapters:test`·`:workflow:test` 가
UP-TO-DATE 로 건너뛰어, 판정 SHA 에서 `--rerun` 으로 강제 실행해 계수를 §4 에 남겼다.

### 7B 머지 전에는 공유 test 둘이 붉다 (D-7C3-1 ① 이 예상한 것 · 실측으로 확인한 원인)

오늘 main 의 두 test 가 **계정 전용 endpoint** 를 전제하지 않는다:

- 본문 거부 전수 test 는 **자격 헤더 하나로 등록된 POST 매핑 전수**를 두드린다. 알림함의 POST
  둘은 그 헤더로 401 이라 전수 집합에 `(401, …)` 이 섞인다.
- 문서 계약 test 는 「인증을 요구하는 operation 은 **전부** 자격 헤더 대안을 남긴다」를 단언한다.
  알림함 operation 셋은 실제로 자격 헤더로 401 이므로 그 대안을 적으면 **문서가 구현보다
  넓어지고**, 적지 않으면 그 test 가 붉다.

**둘은 7B 가 같은 설계 사실(계정 전용 경로)로 이미 고친 축**이고(모집단을 「그 자격으로 실제로
닿는 매핑」으로 가름 + 문서 대안을 계정 전용에 대해 양방향 정확 일치로 조임), 운영자 결정
D-7C3-1 ① 로 **7B → 7C③ 머지 순서**가 정해졌다. 그래서 이 slice 는 그 고침을 **중복 구현하지
않고**, 7B 머지 뒤 rebase 에서 `AccountScopedRoutes.ROOTS` 를 둘로 만든 다음 전체 acceptance 를
잰다. 동결과 판정 SHA 고정은 그 rebase 뒤다.

## 7B 머지 뒤 rebase 에서 **지우고 교체할 것** (운영자 결정 D-7C3-2 · 동결 전건)

이 셋은 **7B 가 main 에 없어서** 임시로 둔 것이고, 동결 SHA 에 남아 있으면 `not-ready` 다.
각 파일 KDoc **첫 줄**에 같은 문장을 적어 두었다(파일만 보고도 임시임을 알 수 있게).

| 임시 산출물 | 교체 대상 | rebase 에서 할 일 |
|---|---|---|
| `app/.../http/SessionAccount.kt` | 7B 의 **typed `AccountId` 요청 속성 + 접근자**(D-7B-2) | **파일 삭제.** controller 의 `SessionAccount.of(request)` 한 줄을 7B 접근자 호출로 바꾼다 |
| `app/.../http/SessionAccountTest.kt` | 7B 접근자의 test | **파일 삭제.** 이 class 가 재던 성질(라벨 왕복 · 형태 거부)은 문자열 파싱이 사라지면 함께 없어진다 |
| `app/.../http/AccountScopedRoutes.kt` | 7B 가 세운 같은 이름의 선언 | **내 판 파일 삭제** 후 7B 판 `ROOTS` 에 알림함 뿌리 한 줄을 더한다(선언은 끝까지 하나) |

**왜 지금 문자열 파싱을 두었나**: 7B 의 접근자가 main 에 없고 알림함 controller 가 계정 주체
없이는 컴파일되지 않는다. 그래서 파싱을 **한 자리에 모아** 교체가 파일 삭제 하나로 끝나게 했다
— controller 는 `SessionAccount.of(request)` 한 줄만 부른다. 주체 문자열을 파싱하는 코드가
**그 한 자리 밖에 없다**는 것을 rebase 전에 `grep` 으로 재확인한다.

**7B 의 구조 게이트가 `getAttribute` 호출자 집합 등식으로 강화된다**(팀장 2026-10-24) —
접근자를 거치지 않고 요청 속성을 직접 읽는 자리를 만들면 그 등식이 붉다. 읽기 전용 실측:

| 질문 | 답 |
|---|---|
| 이 slice 가 더한 직접 `getAttribute` 호출 | **하나** — `SessionAccount.kt` 뿐이고 그 파일이 rebase 에서 **삭제**된다 |
| 삭제 뒤 이 slice 가 더하는 호출자 | **0** — 등식의 모수를 늘리지 않는다(구성상 준수) |
| controller 가 요청 속성을 읽는 방법 | 직접 부르지 않는다 — 계정은 `SessionAccount.of`(→ 7B 접근자로 교체), correlation id 는 기존 공유 helper |

그래서 rebase 의 확인 항목이 **둘**이다: 주체 문자열 파싱 0 · 직접 `getAttribute` 호출 0.
둘 다 `SessionAccount.kt` 삭제로 **동시에** 성립하므로 새로 고칠 코드는 없다.

**7B 의 마감 판정은 지연 평가다**(팀장 2026-10-24) — use case 가 건을 읽거나 명령할 때 마감
사본이 지났으면 시스템 주체로 전이를 먼저 적용한다. 그래서 `bid_case.state` 를 **DB 에서 직접
읽는** 소비자는 마감이 지났는데도 마감 전 상태를 본다. 읽기 전용 실측:

| 질문 | 답 |
|---|---|
| 이 slice 의 SQL 이 건드리는 표 전수 | `notification_inbox`(자기 표) · `notice`(표시용 LEFT JOIN) **둘뿐** |
| `bid_case` 를 읽는 자리 | **0** — 이 slice 의 어느 파일도 그 표도 그 타입도 이름 부르지 않는다 |
| 마감 판정을 누가 하는가 | **호출자(7B)** 다. 마감 종류 셋은 어휘 선언뿐이고 이 slice 는 트리거를 **배선하지 않는다**(D-7C3-1 ②) |
| 표시용 마감 시각의 출처 | `notice.deadline_at` — **공고의 현재 값**이다(팀장이 권한 둘째 방법) |

즉 경고가 지목한 함정(저장된 state 직접 읽기)은 이 slice 의 구조에 **없다**. rebase 에서는
위 네 줄을 **다시 실측해** 그 부재가 유지되는지만 확인한다(7B 머지로 `bid_case` 가 main 에
들어오므로, 「읽을 수 있게 됐는데도 읽지 않는다」가 그때 비로소 의미 있는 측정이 된다).

## 낡은 `.class` 대조 (팀장 2026-10-26 · 7C②·DEC-05 실측 유래)

이 slice 는 타입을 **지웠고**(`SessionAccount`·그 test) 패키지를 **옮겼다**. 증분
`build/classes` 에 옛 정의가 남으면 로컬 실측이 **가짜 RED** 를 낸다(CI 는 새 checkout 이라
이 일이 없다).

**첫 판정은 술어가 약했다.** `$` 가 든 이름을 **통째로 건너뛰어** 중첩 class 를 아예 보지
않았다 — DEC-05 가 겪은 「바깥 마디만 맞추면 지운 안쪽이 통과한다」보다도 약하다. 이름을
맞추는 방식은 Kotlin 에서 두 번 더 틀렸다: 람다·지역 class 의 마디가 **함수 이름**이라 선언이
없고(거짓 831), 한 파일이 최상위 타입을 여럿 담을 수 있어 **class 이름 ≠ 파일 이름**이다(거짓 1377).

**이름 추측을 버리고 두 축으로 바꿨다**(class 3575 전수):

| 축 | 무엇을 잡는가 | 결과 |
|---|---|---|
| (A) 소스 부재 | class 가 기록한 원본 `.kt`(상수 풀)가 그 패키지에 실재하는가. 없으면 바깥 마디의 선언 파일로 되짚는다(인라인 라이브러리 람다 213 이 이 경로다) | **0** |
| (B) 소스보다 오래됨 | 소스는 있는데 `.class` 가 그 소스보다 **오래됐는가**. 증분 컴파일은 바뀐 소스의 class 만 다시 쓰므로, **바깥은 살아 있고 안쪽만 지운** 경우가 여기서 잡힌다 — (A) 가 못 보는 자리다 | **0** |

둘 다 0 이라 `clean` 없이 쟀다. `SessionAccount*.class` 패턴 검색도 0 이다(그 검색은 중첩까지
덮는다). 옛 패키지 경로에 남은 `NotificationSender` 등은 **채널 발송 패키지의 정당한 class**
이고 이 slice 의 것이 아니다.

## rollback ①~⑥ (임시 clone, 되돌린 트리)

`rollback.md` 가 목록·실측 HEAD·①~⑥ 표를 든다(여기서 다시 적지 않는다). 요지: base
`0768bda1` · 실측 HEAD `e09168cd` · 복원 19 · 삭제 23 · 되돌린 트리가 base 와 **바이트까지 같은
트리**(`924ccdb8…`) · 내 표지 0 · 남의 표지 보존.

## 조립 분할과 허용 목록 등재 (2026-10-27 — `HttpTestApplication` 를 자기 파일로)

`HttpTestSupport.kt` 는 7B 머지 시점에 **정확히 500 줄**(크기 게이트 상한)이었다. 이 slice 가
fake 조회기 bean 한 개를 더해야 해서, test 전용 조립 class 를 자기 파일로 **옮겼다** — 상한을
올리지 않고 책임으로 갈랐다(조립 ↔ 이중체·도우미).

**옮긴 것이 무엇을 잃지 않았는가(전수 대조).** 이름 추측으로 import 를 채우지 않고, 옮기기 전
파일의 import 목록과 옮긴 본문을 기계로 대조해 **본문이 실제로 쓰는 것만** 넣었다(원본 59 중 27).

| 축 | 결과 |
|---|---|
| 선언 이름 계수(옮기기 전 한 파일 ↔ 지금 두 파일 합) | 66 → 67 · **유실 0** · 새로 생긴 이름은 `notificationInboxQuery` 하나 |
| `@Bean` 계수 | 22 → 23 (이 slice 가 더한 하나) |
| 이중체 둘(`Unreachable*Port`) | **선언은 제자리** · `private` → `internal` 만 (7 번 절) |

**두 게이트가 이 라운드에서 각각 다른 것을 잡았다**(둘 다 실측):

| 실측 | 결과 | 무엇을 잡았나 |
|---|---|---|
| verify5 — `:app:test`(표적 다섯) + 정적 다섯, `--continue` | `VERIFY_EXIT=1` | `compileTestKotlin` 통과(미해결 참조 0) · `detekt`·`sizeGate`·`testShapeGate` 초록 · `ktlintTestSourceSetCheck` **FAILED**(import 순서 1건 — `java.*` 는 사전순이 아니라 **끝**) · `:app:test` 30 중 **1 실패** |
| verify6 — 위 둘만 재측정 | `VERIFY_EXIT=0` | — |

실패한 그 하나가 이 라운드의 핵심이다. `Intrinsics.areEqual` 허용 목록은 **키 집합을 두 자리**
에 든다(map 의 키 · 같은 test 가 적는 축어 집합). 앞 라운드에서 map 에만 넷을 올렸더니 **축어
집합 쪽이 붉었다** — 「허용 목록이 조용히 늘지 않는다」를 지키는 자리가 따로 있다. 네 항목은
등재 전에 자리마다 판정했다(앞 절의 javap 실측 — data class 자동 `equals` 와 enum 이름 조회,
자격증명 좌표 **0**). 등재는 그 판정 뒤의 **의도 선언**이고, 게이트를 초록으로 만들려는 수단이
아니다.

**변이 둘을 더했다(⑭·⑮ — 파일 분할을 필터가 따라가게).** 읽기 투영 test 는 분할로
`NotificationInboxQueryTest` 로 갔는데 그 반을 재는 변이가 없었다. 변이 없는 반의 초록은
「방어됨」과 「미측정」이 같은 값이다. 둘 다 **바꿔치우기**다 — ⑭ 묶음 전체 크기를 안읽음
필터에 끌려가게(`count(*)` → `count(*) FILTER (WHERE read_at IS NULL)`), ⑮ 표시 칸 조인을
`LEFT JOIN` → `JOIN`(공고가 파기된 항목이 목록에서 사라진다).
