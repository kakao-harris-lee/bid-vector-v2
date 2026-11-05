# M9/inbox-vocab rollback

착수 base **`79f3e5c4`** · **복원 원천 `4953d743`**(이 브랜치가 **마지막으로** 합친 main 의
**고정 SHA** = 병합 커밋 `5c1f0c45` 의 둘째 부모) · **실측 HEAD `f4a97373`**.

**원천이 착수 base 가 아니다.** 이 브랜치는 수정 라운드에서 `origin/main` 을 **두 번** 합쳤고(라운드 2: #122 운영 도구 ·
#126 CVE apt 하한 / 라운드 3: #125 화면 표시), base 에서 복원하면 **그 남의 변경까지 지운다**(PR #124 verifier 실측). 그래서
ⓐ 복원 목록은 **내 커밋만**(병합 제외)에서 산출하고 ⓑ 복원 원천은 합친 main 의 고정 SHA 다 —
움직이는 `origin/main` 을 쓰지 않는다(다음 머지가 그 ref 를 옮기면 실측이 다른 트리를 가리킨다 —
라운드 3 에서 실제로 옮겨져 원천을 `6c38fb32` 에서 `4953d743` 로 **다시** 고정했다).

evidence 커밋은 언제나 실측 뒤에 오므로 「실측 HEAD == 판정 SHA」가 아니다 — 대조해야 하는 것은
**그 사이에 되돌림 대상이 움직였는가**이고, 그 확인 명령은 §5 다.

## 1. 되돌리는 법 — 미적용 DB(머지 전·CI)

**range revert 가 아니라 in_scope 경로 한정 복원**이다. 목록은 손으로 적지 않고 기계가 낸다.

```
MAIN=4953d743                                      # 마지막으로 합친 main 의 고정 SHA(= HEAD^2)
EXCL=':(exclude)reports/evidence/m9/inbox-vocab'   # 자기 자신은 되돌릴 수 없다

# 목록은 **내 커밋만** — 병합으로 들어온 남의 커밋을 제외한다
mapfile -t MINE < <(git log --no-merges --format= --name-only $MAIN..HEAD -- . "$EXCL" | sort -u)
mapfile -t MODIFIED < <(for f in "${MINE[@]}"; do git cat-file -e "$MAIN:$f" 2>/dev/null && echo "$f"; done)
mapfile -t ADDED    < <(for f in "${MINE[@]}"; do git cat-file -e "$MAIN:$f" 2>/dev/null || echo "$f"; done)

git restore --source=$MAIN --staged --worktree -- "${MODIFIED[@]}"
git rm -q -f -- "${ADDED[@]}"
```

「있음/없음」을 `--diff-filter` 가 아니라 **`git cat-file -e`** 로 가른다: 필터는 범위의 양 끝을
비교하므로 병합이 끼면 남의 변경을 M 으로 센다. 원천에 그 경로가 있는지만 물으면 병합과 무관하다.

`ADDED` 둘 가운데 하나가 **V24 파일**이다. 미적용 DB 에서는 그 파일이 사라지는 것이 되돌림의
전부다 — Flyway 이력에 없으므로 흔적이 0 이고, 체크섬 충돌도 생기지 않는다.

`git rm -f`(단순 `rm` 이 아니라)인 이유: 색인까지 지워야 복원 뒤 색인이 base 와 같아진다.
단순 `rm` 은 작업 트리만 비우고 색인에 HEAD 의 추가분을 남긴다(실측으로 갈린 자리).

## 2. 되돌리는 법 — 적용 DB(배포 뒤)

마이그레이션은 **forward-fix** 로 되돌린다(Flyway community 에 undo 가 없다). 새 V 하나가
① 새 어휘로 적힌 행을 처분하고 ② 제약 둘을 V23 본문으로 재선언한다.

**행 처분은 삭제다.** `NOT_AWARDED` 로 접는 길은 D-M9-1 이 거부한 바로 그 접기이고, 알림 항목은
재생성 가능한 파생 기록이다(다음 전이·훑기가 다시 적는다). 남기면 `ADD CONSTRAINT` 가 기존 행
검증에서 **실패한다** — 처분이 선택이 아니라 순서상 필수다. 삭제는 **소유자 연결**의 일이다(앱
역할에 `DELETE` 권한이 없다).

**이 갈래는 §1 의 파일 삭제와 배타다**(v L-3 ⓐ). 적용 DB 에서 V24 파일을 지우면 Flyway 의
`validateOnMigrate` 가 「applied migration not resolved locally」로 **기동을 막는다** — 이력에 남은
적용 기록을 로컬에서 해소할 수 없기 때문이다. 즉 적용 뒤에는 파일을 지우는 길이 없고 새 V 뿐이다.

**순서는 forward-fix V 가 앱 되돌림보다 먼저다**(v L-3 ⓑ). 옛 코드는 `FAILED_OR_REBID` 를 모르므로
그 행을 **복원하지도 처분하지도 못한다**(`notificationFactOf` 의 복원이 그 어휘에서 던지고, 앱
역할에는 `DELETE` 권한이 없다). 앱을 먼저 되돌리면 그 행을 읽는 목록 질의가 복원 단계에서 깨진다.
web·openapi 쪽은 어휘가 줄어도 미지 값 표시로 떨어져 순서에 무관하지만(아래), **Kotlin 쪽은
무관하지 않다.**

`web`·openapi 쪽은 어휘가 줄어도 **거부가 아니라 미지 값 표시**로 떨어지므로(표 조회가 폴백 없이
`unknownStateDisplay` 로 간다) 화면 배포 순서는 DB 먼저여도 화면 먼저여도 깨지지 않는다 — 위
Kotlin 쪽 순서 제약과 **다른 축**이다.

## 3. 실측 (버릴 clone, `git clone --no-hardlinks`)

| 단계 | 실측 |
| --- | --- |
| ① 명령 종료 코드 | `restore` **0** · `git rm` **0** |
| ② 계수 | 내 커밋 경로 **18**(evidence 제외) = 복원 **16**(M) · 삭제 **2**(A) — 기계 목록과 같다 |
| ③ 결과 트리 == `4953d743` 트리 | 작업 트리 **0 파일** · 색인 **0 파일** · 미추적 **0** |
| ③′ main 의 남의 변경 보존 | #122·#126 — Dockerfile 둘 · `prod-guards-check.sh` · `prod-rollback.sh` **있음** · apt 하한 줄 **7** · ci.yml 의 S-27d **1**. #125 — `InstantText.tsx` · `timeDisplay.ts` · `screen-card-coverage.mjs` · 캡처 `manifest.json` **있음** |
| ④⑤⑥ 복원 트리의 컴파일·test·게이트 | **트리 동일성으로 갈음** — 아래 |
| ⑦ 원천과 남는 차이 | **실측 시점(`f4a97373`)에** `reports/evidence/m9/inbox-vocab/**` **다섯**뿐(의도된 제외 — `EXCL` 이 디렉터리 전체를 제외한다). 그 수는 evidence 커밋마다 자라고, 유효성은 §5 의 술어가 되돌림 대상에서 evidence 를 빼므로 흔들리지 않는다 |

### ④⑤⑥ 의 갈음 — 「HEAD 초록」이 아니라 트리 동일성

복원한 트리는 **`4953d743` 의 트리와 파일 단위로 같다**(③ 의 0 파일 셋). 그 커밋은 병합된
main(#125 머지 커밋)이고 CI 가 초록으로 통과한 자리다. 그래서 복원 트리에서 `check` 를 다시
돌리는 것은 같은 트리를 다시 재는 일이다.

**같지 않은 입력을 이름 붙인다**: 위 ⑦ 의 evidence 파일들이다. 그것들은 빌드 입력이
**맞다** — `LeakPatternGateTask` 가 `reports/evidence` 를 스캔한다. 그 축은 이 slice 의 HEAD
(그 파일들을 **포함한** 상위집합)에서 `check` 가 초록이었으므로 이미 실측돼 있다.

### 표지 계수 — 「내 줄 사라짐」과 「남의 줄 남음」 둘 다

복원 뒤 저장소 전체에서 센 **파일 수**다. 종료 코드가 아니라 이 계수가 완료의 증거다.

| 내 표지(0 이어야 한다) | 복원 뒤 |
| --- | --- |
| `AwardResult` · `ReadResetPolicy` · `ON_NEWER_FACT` · `readResetPolicy` · `NotificationInboxSqlPolicyTest` · 화면 표의 유찰 라벨 · `GREATEST(notification_inbox.occurred_at` | **전부 0** |
| V24 파일 | **0** |

| 남의 표지(남아 있어야 한다) | 복원 뒤 |
| --- | --- |
| `NotificationInboxStore`(7C③) | 11 |
| `reflectedNotificationFact`(7C③-b) | 3 |
| `BidState.FailedOrRebid`(7B) | 8 |
| `NOTIFICATION_DETAIL_DISPLAY`(7E-3a) | 4 |
| `V23__notification_inbox` | 1 |
| `settlement.AwardOutcome`(SET-01 — **이름이 겹치던 그 타입**) | 2 |

내 표지를 고를 때 `FAILED_OR_REBID` 는 쓸 수 없다 — 7B 의 `BidStateTag`·`BidOutcome` 이 그 값을
이미 들고 있어 **남의 줄까지 세게 된다**. 그래서 이 slice 가 처음 들여온 이름들로 골랐다.

## 4. 적용 DB 되돌림 리허설 — 실측

버릴 clone 에 임시 V25(§2 의 모양)와 임시 test 하나를 넣고 **버릴 클러스터**에서 돌렸다
(공유 컨테이너를 오염시키지 않는다). 둘 다 **커밋하지 않았다**.

| 단계 | 실측 |
| --- | --- |
| V24 까지 migrate → 유찰 행 1 삽입 | 영향 행 **1** · 어휘 **다섯** |
| V25 migrate | exit **0** |
| 유찰 행 | **0** (처분됐다) |
| detail 어휘 | **넷** (V23 본문으로 돌아왔다) |
| 유찰 재삽입 | **거부** (짝 CHECK 도 돌아왔다) |
| `NOT_AWARDED` 삽입 | **영향 행 1** (음성 대조 — 제약이 전부를 막는 모양이 아니다) |

test 실행 계수: `tests="1" skipped="0" failures="0" errors="0"`.

## 5. 실측 HEAD 와 판정 SHA 사이가 움직였는가

evidence 커밋은 실측 뒤에 오므로 둘은 다르다. 유효성은 **되돌림 대상이 그 사이에 움직였는가**로
본다:

```
git diff --name-only f4a97373..<판정 SHA> -- \
  $(git log --no-merges --format= --name-only 4953d743..f4a97373 \
      -- . ':(exclude)reports/evidence/m9/inbox-vocab' | sort -u)
```

빈 출력이면 이 실측이 유효하고, 한 줄이라도 나오면 **미검증**이다(그때 ①~③ 과 §4 를 다시 잰다).
동결 보고가 그 확인 결과를 적는다.

## 6. 코드를 되돌리지 않고 끄는 법

**없다 — 이 slice 는 기능 스위치를 더하지 않는다.** 어휘 하나와 읽음 해제 규칙이고 둘 다 항상
켜져 있다. 끄는 것에 가장 가까운 것은 §2 의 forward-fix 이고, 그때 화면·문서 쪽은 미지 값 표시로
떨어진다(§2 마지막 문단).
