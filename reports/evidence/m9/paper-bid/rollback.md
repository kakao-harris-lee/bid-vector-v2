# M9/paper-bid rollback

착수 base **`df4976cc`** · **복원 원천 `3ad4f3f6`**(이 브랜치가 **마지막으로** 합친 main 의
**고정 SHA** = 병합 커밋 `1f34e3fc` 의 둘째 부모) · **실측 HEAD `b5da4a2d`**(판정 SHA =
승인 전 일괄 라운드의 마지막 산출물 커밋).

**그 라운드에서 다시 쟀다.** 복원 목록 안 경로 **넷**이 움직였기 때문이다 — §5 의 술어가 네 줄을
냈다(`notificationDisplay.ts` · `results.spec.ts` · `NotificationInboxWiringTest.kt` ·
`capability-map.md`). 앞 실측을 옮기지 않고 판정 SHA 에서 ①~③ 과 표지 계수를 **전부 다시** 냈다.
**원천은 그대로다** — 그 라운드에 main 병합이 없었다.

**원천이 착수 base 가 아니다.** 이 브랜치는 머지 직전에 `origin/main` 을 **두 번** 합쳤다
(`e12c0485` = #130~#133 · `3ad4f3f6` = #135). base 에서 복원하면 **그 남의 변경까지 지운다**.
그래서 ⓐ 복원 목록은 **내 커밋만**(병합 제외)에서 산출하고 ⓑ 복원 원천은 **마지막으로** 합친
main 의 고정 SHA 다 — 움직이는 `origin/main` 을 쓰지 않는다(다음 머지가 그 ref 를 옮기면 실측이
다른 트리를 가리킨다. inbox-vocab 이 네 번 다시 고정한 자리이고 이 slice 도 `df4976cc` →
`e12c0485` → `3ad4f3f6` 으로 두 번 옮겼다). **머지 전에 main 이 또 움직이면** 원천을 그 SHA 로
다시 고정하고 아래 ①~③ 과 §4 를 다시 잰다.

evidence 커밋은 실측 뒤에 오므로 이 slice 에서는 「실측 HEAD == 판정 SHA」가 **성립한다**(아직
evidence 를 커밋하지 않은 자리에서 쟀다). 그래도 대조해야 하는 것은 **그 사이에 되돌림 대상이
움직였는가**이고, 그 확인 명령은 §5 다.

## 1. 되돌리는 법 — 미적용 DB(머지 전·CI)

**range revert 가 아니라 in_scope 경로 한정 복원**이다. 목록은 손으로 적지 않고 기계가 낸다.

```
MAIN=3ad4f3f6                                     # 이 브랜치가 **마지막으로** 합친 main 의 고정 SHA
EXCL=':(exclude)reports/evidence/m9/paper-bid'    # 자기 자신은 되돌릴 수 없다

mapfile -t MINE < <(git log --no-merges --format= --name-only $MAIN..HEAD -- . "$EXCL" | sort -u)
mapfile -t MODIFIED < <(for f in "${MINE[@]}"; do git cat-file -e "$MAIN:$f" 2>/dev/null && echo "$f"; done)
mapfile -t ADDED    < <(for f in "${MINE[@]}"; do git cat-file -e "$MAIN:$f" 2>/dev/null || echo "$f"; done)

git restore --source=$MAIN --staged --worktree -- "${MODIFIED[@]}"
git rm -q -f -- "${ADDED[@]}"
```

「있음/없음」을 `--diff-filter` 가 아니라 **`git cat-file -e`** 로 가른다: 필터는 범위의 양 끝을
비교하므로 병합이 끼면 남의 변경을 M 으로 센다. 원천에 그 경로가 있는지만 물으면 병합과 무관하다.

`ADDED` 넷 가운데 하나가 **V25 파일**이고 셋은 이 slice 가 만든 test class 다. 미적용 DB 에서는
그 파일이 사라지는 것이 마이그레이션 되돌림의 전부다 — Flyway 이력에 없으므로 흔적이 0 이고
체크섬 충돌도 생기지 않는다.

`git rm -f`(단순 `rm` 이 아니라)인 이유: 색인까지 지워야 복원 뒤 색인이 원천과 같아진다.

## 2. 되돌리는 법 — 적용 DB(배포 뒤)

마이그레이션은 **forward-fix** 로 되돌린다(Flyway community 에 undo 가 없다). 새 V 하나가 네
걸음을 **이 순서로** 한다.

```
① DELETE FROM notification_inbox WHERE bid_kind = 'PAPER';
② ALTER TABLE notification_inbox DROP CONSTRAINT notification_inbox_bid_kind_pair_check;
   ALTER TABLE notification_inbox DROP CONSTRAINT notification_inbox_bid_kind_check;
③ ALTER TABLE notification_inbox DROP CONSTRAINT notification_inbox_pkey;
   ALTER TABLE notification_inbox ADD PRIMARY KEY (account_id, kind, notice_number, notice_round);
④ ALTER TABLE notification_inbox DROP COLUMN bid_kind;
```

**① 이 선택이 아니라 순서상 필수다.** 옛 PK 는 네 열이고, 같은 공고·같은 알림 종류의 서면 행과
실투찰 행은 그 넷에서 **같은 키**다 — 남겨 두면 ③ 의 `ADD PRIMARY KEY` 가 중복 키로 **실패한다**.
§4 가 그 실패를 실측한다(처분 전 복원 시도 → 거부).

**처분은 삭제이고 서면 행만이다.** `NONE` 행은 **남긴다** — 열이 사라지면 그 축도 사라지고, 그
행들(투찰가 요청 기록)은 옛 PK 의 넷에서 서면·실투찰 행과 **키가 겹치지 않는다**(짝 CHECK 가 두
축의 종류 집합을 서로 배타로 두었기 때문이다. 이것이 ① 에서 `bid_kind = 'PAPER'` 만 지우면 되는
근거다). 삭제는 **소유자 연결**의 일이다 — 앱 역할에 `DELETE` 권한이 없다.

**이 갈래는 §1 의 파일 삭제와 배타다.** 적용 DB 에서 V25 파일을 지우면 Flyway 의
`validateOnMigrate` 가 「applied migration not resolved locally」로 **기동을 막는다** — 이력에
남은 적용 기록을 로컬에서 해소할 수 없다. 즉 적용 뒤에는 파일을 지우는 길이 없고 새 V 뿐이다.

**그래서 적용 DB 에서 앱을 되돌릴 때 §1 을 그대로 쓰면 안 된다**(v L-1). §1 의 복원 목록에는
`adapters/src/main/resources/db/migration/V25__*.sql` 가 **들어 있고**(`ADDED` 셋 중 하나) 그
경로를 지우는 것이 곧 위의 기동 정지다. 적용 DB 의 앱 되돌림은 **마이그레이션 디렉터리를 §1 의
복원 대상에서 제외하고**(V25 와 forward-fix V 를 **둘 다 남긴다**) 나머지 경로만 복원한다 —
Flyway 이력의 두 줄에 로컬 파일 두 개가 그대로 대응해야 validate 가 지난다.

```
# 적용 DB 갈래의 §1 변형 — 마이그레이션은 건드리지 않는다
EXCL2=':(exclude)adapters/src/main/resources/db/migration'
mapfile -t MINE < <(git log --no-merges --format= --name-only $MAIN..HEAD -- . "$EXCL" "$EXCL2" | sort -u)
```

**순서는 forward-fix V 가 앱 되돌림보다 먼저다.** 옛 코드는 투찰 종류 축을 모르므로 그 열이 있는
표에 `INSERT` 를 쏘면 **NOT NULL 로 거부된다**(그 열은 기본값이 없다 — V25 가 backfill 뒤
떨어뜨렸다). 즉 앱을 먼저 되돌리면 알림 기록 경로 전체가 멈춘다. 읽기 경로는 그 열을 안 읽으므로
깨지지 않지만, 쓰기 쪽 제약이 순서를 정한다.

`web`·openapi 쪽은 어휘가 줄어도 **거부가 아니라 미지 값 표시**로 떨어지므로(표 조회가 폴백 없이
`unknownStateDisplay` 로 간다) 화면 배포 순서는 어느 쪽이어도 깨지지 않는다 — 위 Kotlin 쪽 순서
제약과 **다른 축**이다.

## 3. 실측 (버릴 clone, `git clone --no-hardlinks`)

| 단계 | 실측 |
| --- | --- |
| ① 명령 종료 코드 | `restore` **0** · `git rm` **0** |
| ② 계수 | 내 커밋 경로 **49**(evidence 제외) = 복원 **45**(M) · 삭제 **4**(A) — 기계 목록과 같다 |
| ③ 결과 트리 == `3ad4f3f6` 트리 | 작업 트리 **0 파일** · 색인 **0 파일** · 미추적 **0** |
| ③′ main 의 남의 변경 보존 | **추가 파일 전수 존재**(이름으로 세지 않고 목록을 돌며 `-e` 로 물었다 — 없는 것 **0**) · `KonepsEndpointDefaultsTest` 7 · `NotificationInboxStore` 11 · `AwardResult` 8 · `RetentionPolicyData` 26 · `APPROVED_OPERATIONS` 4 · **#135**: `provisional-state-` 3 · `opening-axis` 8 |
| ③″ 내 표지 **0** | 열 표지 전부 0(이 라운드가 더한 `NotificationTestIdParts` 까지) · V25 파일 0(마이그레이션 수 **22** 로 복귀) · capability map 다섯 열 문장 0 · §14.3 종결 취소선 0 |
| ④⑤⑥ 복원 트리의 컴파일·test·게이트 | **트리 동일성으로 갈음** — 아래 |
| ⑦ 원천과 남는 차이 | `reports/evidence/m9/paper-bid/**` **다섯**뿐(의도된 제외 — `EXCL` 이 디렉터리 전체를 제외한다). 그 수는 evidence 커밋마다 자라고, 유효성은 §5 의 술어가 되돌림 대상에서 evidence 를 빼므로 흔들리지 않는다 |

### ④⑤⑥ 의 갈음 — 「HEAD 초록」이 아니라 트리 동일성

복원한 트리는 **`3ad4f3f6` 의 트리와 파일 단위로 같다**(③ 의 0 파일 셋). 그 커밋은 병합된
main(#135 머지 커밋)이고 CI 가 초록으로 통과한 자리다. 그래서 복원 트리에서 `check` 를 다시
돌리는 것은 같은 트리를 다시 재는 일이다.

**같지 않은 입력을 이름 붙인다**: ⑦ 의 evidence 파일들이다. 그것들은 빌드 입력이 **맞다** —
누출 패턴 게이트가 `reports/evidence` 를 스캔한다. 그 축은 이 slice 의 HEAD(그 파일들을
**포함한** 상위집합)에서 그 게이트가 초록이었으므로 이미 실측돼 있다.

### 표지 계수 — 「내 줄 사라짐」과 「남의 줄 남음」 둘 다

복원 뒤 저장소 전체에서 센 **파일 수**다(`reports/` 제외 — 내 evidence 가 자기 이름을 들고 있다).
종료 코드가 아니라 이 계수가 완료의 증거다.

| 내 표지(0 이어야 한다) | 복원 뒤 |
| --- | --- |
| `NotificationBidKind` · `NotificationGroupKey` · `BidKindAxis` · `bidKindAxis` · `asNotificationBidKind` · `groupNotifications` · `NOTIFICATION_BID_KIND_DISPLAY` · `notificationItemTestId` · 새 test class **셋**의 이름 | **전부 0** |
| V25 파일 | **0**(마이그레이션 수가 22 로 돌아왔다) |
| capability map 의 다섯 열 문장 | **0**(네 열 문면으로 돌아왔다) |
| §14.3 행의 종결 취소선 | **0**(「진행 중」으로 돌아왔다) |

| 남의 표지(남아 있어야 한다) | 복원 뒤 |
| --- | --- |
| `NotificationInboxStore`(7C③) | 11 |
| `reflectedNotificationFact`(7C③-b) | 3 |
| `AwardResult`(M9/inbox-vocab) | 8 |
| `ReadResetPolicy`(같음) | 4 |
| `NotificationInboxSqlPolicyTest`(같음) | 4 |
| `GREATEST(notification_inbox.occurred_at`(같음) | 1 |
| `BidState.FailedOrRebid`(7B) | 8 |
| `RetentionPolicyData`(#129) | 26 |
| V23·V24 마이그레이션 파일 | **둘 다 있음** |
| `APPROVED_OPERATIONS`(여정 e2e 가 `src` 를 import 하는 기존 선례) | 4 |
| 여정 e2e 의 **옛** 네 조각 testId 꼴 | **1**(복귀했다 — 내가 바꾼 자리가 원천 모양으로 돌아왔다는 뜻) |

내 표지를 고를 때 `bid_kind`·`PAPER`·`REAL` 은 쓸 수 없다 — V21 의 `bid_case` 와 `BidKind` 가 그
어휘를 이미 들고 있어 **남의 줄까지 세게 된다**. 그래서 이 slice 가 처음 들여온 이름들로 골랐다.

**세는 도구가 한 번 거짓 양성을 냈다.** 재측정의 첫 판은 내 표지가 0 이 아니라 다섯·셋 따위로
나왔는데, `grep` 호출에서 `--exclude-dir` 을 `--` **뒤**에 둬서 그것들이 옵션이 아니라 **파일
이름**으로 먹혔고 `.git` 과 `reports/`(내 evidence 가 자기 이름을 들고 있다)까지 훑은 탓이다.
옵션을 `--` 앞으로 옮기고 패턴을 `-e` 로 줘 다시 셌다. 그리고 **0 이 공허하지 않다는 양성 대조**를
붙였다 — 같은 계수기를 복원하지 않은 실 worktree 에 돌리면 같은 표지가 **26·14·…·V25 파일 1 ·
마이그레이션 23** 으로 나온다. 앞 라운드의 `MockKonepsRoutes` 거짓 0 과 **같은 계열**이다(세는
방법이 틀리면 되돌림의 성공·실패 양쪽이 조용하다).

## 4. 적용 DB 되돌림 리허설 — 실측

버릴 clone 에 §2 의 네 걸음을 쏘는 임시 test 하나를 넣고 **버릴 클러스터**에서 돌렸다(공유
컨테이너를 오염시키지 않는다). 그 test 는 **커밋하지 않았고** 측정 뒤 지웠다.

**이 실측은 `5ca4f561` 에서 돌았고 판정 SHA 는 `b5da4a2d` 다.** 그 사이 V25 가 바뀐 것은
**주석뿐이고**(`5ca4f561..635f05a6` 의 비주석·비공백 줄 **0**), main 병합 **둘 다** V25 를 **아예
만지지 않았다**(`635f05a6..HEAD` 의 그 파일 diff **0 줄** — main 은 두 범위 모두에서
마이그레이션을 더하지 않았다). **승인 전 일괄 라운드도 마이그레이션 디렉터리를 만지지 않았다**
(`1f34e3fc..b5da4a2d` 의 그 디렉터리 변경 **0 파일** — 그 라운드는 산문·타입·test 술어뿐이다).
그래서 그 파일이 만드는 스키마가 같고 §2 의 네 걸음이 쏘는 DDL 도 같다. 그 밖에 이 리허설의
입력(표 정의·제약 이름·GRANT)을 바꾼 커밋은 없다.

| 단계 | 실측 |
| --- | --- |
| 같은 공고에 서면·실투찰·공고축 행 셋 | 행 **3** |
| **처분 전** 옛 PK 복원 시도 | **거부** — 중복 키(① 이 순서상 필수라는 주장의 실측) |
| 서면 행 처분 | 행 **2** |
| 제약 둘 제거 → 옛 PK 복원 | **성공**(exit 0) |
| 열 제거 뒤 제약 수 | **5**(V23 본문으로 돌아왔다) |
| `bid_kind` 열 | **0**(없다) |
| PK 열 | `account_id, kind, notice_number, notice_round` |
| 남은 행 | **2**(실투찰이던 것과 `NONE` 이던 것 — 둘 다 보존) |
| 음성 대조 | 같은 네 열로 재삽입 → **거부**(옛 중복 억제가 돌아왔다) |

test 실행 계수: `tests="1" skipped="0" failures="0" errors="0"`.

**이 리허설이 재지 **않는** 축 하나 — verifier 가 쟀다**(v L-2): V25 의 backfill(**기본값 +
UPDATE 하나**, 「두 UPDATE」가 아니다)이다. 깨끗한 스키마에 migrate 하면 기존 행이 없어 그
UPDATE 가 무연산이고, 전 CI 가 그 줄을 돌리지만 **답을 묻지 않는다**.

verifier 가 Flyway `target=24` 로 V24 까지 migrate → 두 계정 × 종류 여덟 행(읽음 절반) → V25
적용으로 **직접 측정했다**: 16행에서 공고 축 둘이 `NONE`·나머지 여섯이 `REAL` · 계정과 `read_at`
보존 · `column_default` NULL · NOT NULL · PK 다섯 열. **음성 대조** — backfill UPDATE 를 지운
V25 사본은 migrate 가 **23514 로 실패**하고 `bid_kind` 열이 0 으로 롤백된다. 「잊으면 조용하지
않다」는 주장이 그 자리에서 성립한다.

이 slice 의 CI 안에 그 하네스를 세우지는 않았다 — 회귀 방어는 짝 CHECK 전수 교차가 진다(공고 축
종류에 `'REAL'` 을 실은 행이 거부되므로, backfill 을 빠뜨린 행이 남으면 `ADD CONSTRAINT` 가
검증에서 실패한다). 알려진 제한 4 에 그대로 등재한다.

## 5. 실측 HEAD 와 판정 SHA 사이가 움직였는가

evidence 커밋은 실측 뒤에 오므로 둘은 갈릴 수 있다. 유효성은 **되돌림 대상이 그 사이에
움직였는가**로 본다:

```
git diff --name-only b5da4a2d..<HEAD> -- \
  $(git log --no-merges --format= --name-only 3ad4f3f6..b5da4a2d \
      -- . ':(exclude)reports/evidence/m9/paper-bid' | sort -u)
```

빈 출력이면 이 실측이 유효하고, 한 줄이라도 나오면 **미검증**이다(그때 ①~③ 과 §4 를 다시 잰다).
동결 보고가 그 확인 결과를 적는다.

## 6. 코드를 되돌리지 않고 끄는 법

**없다 — 이 slice 는 기능 스위치를 더하지 않는다.** 키의 열 하나이고 항상 켜져 있다. 끄는 것에
가장 가까운 것은 §2 의 forward-fix 이고, 그러면 서면 건의 알림이 **다시 버려지는** 상태로
돌아간다(원천 양쪽의 가드를 함께 되살려야 하므로 앱 되돌림이 짝이다 — 그 둘을 따로 되돌리면
서면 기록이 NOT NULL 로 거부되며 멈춘다, §2 마지막 문단).
