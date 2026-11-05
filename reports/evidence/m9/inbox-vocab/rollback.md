# M9/inbox-vocab rollback

base **`79f3e5c4`**(병합된 main) · **실측 HEAD `30766939`**(이 slice 의 마지막 산출물 커밋).
evidence 커밋은 언제나 이 뒤에 오므로 「실측 HEAD == 판정 SHA」가 아니다 — 대조해야 하는 것은
**그 사이에 되돌림 대상이 움직였는가**이고, 그 확인 명령은 §4 다.

## 1. 되돌리는 법 — 미적용 DB(머지 전·CI)

**range revert 가 아니라 in_scope 경로 한정 복원**이다. 목록은 손으로 적지 않고 기계가 낸다.

```
BASE=79f3e5c4
EXCL=':(exclude)reports/evidence/m9/inbox-vocab'   # 자기 자신은 되돌릴 수 없다
mapfile -t MODIFIED < <(git diff --name-only --diff-filter=M $BASE..HEAD -- . "$EXCL")
mapfile -t ADDED    < <(git diff --name-only --diff-filter=A $BASE..HEAD -- . "$EXCL")
git restore --source=$BASE --staged --worktree -- "${MODIFIED[@]}"
git rm -q -f -- "${ADDED[@]}"
```

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

`web`·openapi 쪽은 어휘가 줄어도 **거부가 아니라 미지 값 표시**로 떨어지므로(표 조회가 폴백 없이
`unknownStateDisplay` 로 간다) 배포 순서는 DB 먼저여도 화면 먼저여도 깨지지 않는다.

## 3. 실측 (버릴 clone, `git clone --no-hardlinks`)

| 단계 | 실측 |
| --- | --- |
| ① 명령 종료 코드 | `restore` **0** · `git rm` **0** |
| ② 계수 | 복원 **14**(M) · 삭제 **2**(A) — 기계 목록과 같다 |
| ③ 되돌린 경로의 차이 | 작업 트리 ↔ base **0 파일** · 색인 ↔ base **0 파일** · 미추적 **0** |
| ④⑤⑥ 복원 트리의 컴파일·test·게이트 | **트리 동일성으로 갈음** — 아래 |
| ⑦ base 와 남는 차이 | `reports/evidence/m9/inbox-vocab/{scope,design-review}.md` 둘뿐(의도된 제외) |

### ④⑤⑥ 의 갈음 — 「HEAD 초록」이 아니라 트리 동일성

복원한 트리는 **`79f3e5c4` 의 트리와 파일 단위로 같다**(③ 의 0 파일 셋). 그 커밋은 병합된
main 이고 CI 가 초록으로 통과한 자리다. 그래서 복원 트리에서 `check` 를 다시 돌리는 것은 같은
트리를 다시 재는 일이다.

**같지 않은 입력 하나를 이름 붙인다**: 위 ⑦ 의 evidence 파일 둘이다. 그 둘은 빌드 입력이
**맞다** — `LeakPatternGateTask` 가 `reports/evidence` 를 스캔한다. 그 축은 이 slice 의 HEAD
(두 파일을 **포함한** 상위집합)에서 `check` 가 초록이었으므로 이미 실측돼 있다.

### 표지 계수 — 「내 줄 사라짐」과 「남의 줄 남음」 둘 다

복원 뒤 저장소 전체에서 센 **파일 수**다. 종료 코드가 아니라 이 계수가 완료의 증거다.

| 내 표지(0 이어야 한다) | 복원 뒤 |
| --- | --- |
| `AwardResult` · `ReadResetPolicy` · `ON_NEWER_FACT` · `readResetPolicy` · `NotificationInboxSqlPolicyTest` · 화면 표의 유찰 라벨 | **전부 0** |
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
git diff --name-only 30766939..<판정 SHA> -- \
  $(git diff --name-only 79f3e5c4..30766939 -- . ':(exclude)reports/evidence/m9/inbox-vocab')
```

빈 출력이면 이 실측이 유효하고, 한 줄이라도 나오면 **미검증**이다(그때 ①~③ 과 §4 를 다시 잰다).
동결 보고가 그 확인 결과를 적는다.

## 6. 코드를 되돌리지 않고 끄는 법

**없다 — 이 slice 는 기능 스위치를 더하지 않는다.** 어휘 하나와 읽음 해제 규칙이고 둘 다 항상
켜져 있다. 끄는 것에 가장 가까운 것은 §2 의 forward-fix 이고, 그때 화면·문서 쪽은 미지 값 표시로
떨어진다(§2 마지막 문단).
