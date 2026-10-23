# M7/7C③ — 실행 명령과 종료 코드

base `a61ddff1` · 브랜치 `m7-7c3/2026-10-23` · worktree `bid-vector-v2-m7-7c3`.

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
전체와 함께 잰다 — (기록)

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

## (기록) — 어댑터·app test r2 (위 수정 넷 뒤 재측정)

같은 명령 둘을 한 번에 돌린다. 결과: (기록)

## (기록) — 변이 (적용 → 표적 재실행 → 사본 복원)

표적은 `checklist.md` §0.9 의 우회 표다. **더하기만 한 변이는 초록이므로 바꿔치운다**(음성
대조를 함께 남긴다). 변이 **전에 커밋**한다 — 미커밋 산출물이 복원으로 함께 지워지지 않게.

## (기록) — 비밀값 스캔

```
grep -rniE -f config/quality/leak-patterns.txt <이 slice 가 더한 경로들>
```

참조형으로만 돌린다(어휘를 이 파일에 축어로 적지 않는다). 결과: (기록)

## (기록) — clean-tree 게이트

```
git status --porcelain -- <in_scope 개별 인자>
```

빈 출력 + 양성 대조 1회(비파괴 절삭 — `checkout --` 를 쓰지 않는다). 결과: (기록)

## (기록) — acceptance: `check` job · `web` job · `container` job

`ci.yml` 의 job 명령 **그대로**다. 마이그레이션이 있어 app 이미지가 V23 을 싣으므로 `container`
job 도 잰다(생략 불가 — path-limited diff 로 입력 변경을 보인다).

| 항목 | 명령 | 결과 |
|---|---|---|
| `check` job | `./gradlew --no-daemon check` · `./gradlew --no-daemon qualityBaseline` · `./tools/one-command-check.sh` | (기록) |
| `web` job | `npm ci` · `lint` · `typecheck` · `gate:src-tree` · `api:types:check` · `test` · `gate:tests` · `build` · `gate:boundary-canary` · Playwright 스모크 | (기록) |
| `container` job | app `bootJar` + 이미지 빌드 + 위생·취약점 게이트 | (기록) |
| `ml-engine` job | 입력 변경 0 → **생략** | 해당 없음 |

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

## (기록) — rollback ①~⑥ (임시 clone, 되돌린 트리)

`rollback.md` 가 목록과 실측 HEAD 를 든다.
