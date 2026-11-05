# M9 / ops-migrations rollback

착수 base **`df4976cc`** · **복원 원천 `df4976cc`** · **실측 HEAD `50ef257a`**.

**원천이 착수 base 와 같다** — 이 브랜치는 수정 라운드에서 main 을 합치지 않았다
(`git log --merges df4976cc..HEAD` 빈 출력). 합치는 라운드가 생기면 원천을 **그때 합친 main 의 고정
SHA** 로 다시 고정해야 한다(움직이는 `origin/main` 을 쓰지 않는다 — M9/inbox-vocab 이 네 번 겪었다).

evidence 커밋은 언제나 실측 뒤에 오므로 「실측 HEAD == 판정 SHA」가 아니다 — 대조해야 하는 것은
**그 사이에 되돌림 대상이 움직였는가**이고, 그 확인 명령은 §4 다.

## 1. 되돌리는 법 — 미적용 DB(머지 전·CI)

range revert 가 아니라 **in_scope 경로 한정 복원**이고 목록은 기계가 낸다.

```
MAIN=df4976cc                                        # 착수 base = 이 브랜치가 분기한 main
EXCL=':(exclude)reports/evidence/m9/ops-migrations'   # 자기 자신은 되돌릴 수 없다

mapfile -t MINE < <(git log --no-merges --format= --name-only $MAIN..HEAD -- . "$EXCL" | sort -u)
mapfile -t MODIFIED < <(for f in "${MINE[@]}"; do git cat-file -e "$MAIN:$f" 2>/dev/null && echo "$f"; done)
mapfile -t ADDED    < <(for f in "${MINE[@]}"; do git cat-file -e "$MAIN:$f" 2>/dev/null || echo "$f"; done)

git restore --source=$MAIN --staged --worktree -- "${MODIFIED[@]}"
git rm -q -f -- "${ADDED[@]}"
```

「있음/없음」을 `--diff-filter` 가 아니라 **`git cat-file -e`** 로 가른다(병합이 끼면 필터가 남의
변경을 M 으로 센다). `git rm -f` 인 이유는 색인까지 지워야 복원 뒤 색인이 원천과 같아지기 때문이다.

`ADDED` 열 가운데 **세 개가 V 파일**(V26·V27·V28)이다. 미적용 DB 에서는 그 파일들이 사라지는 것이
되돌림의 전부다 — Flyway 이력에 없으므로 흔적이 0 이고 체크섬 충돌도 없다.

## 2. 되돌리는 법 — 적용 DB(배포 뒤)

Flyway community 에 undo 가 없으므로 **forward-fix** 다. 새 V 하나가 세 축을 거꾸로 밟는다.

```sql
-- ① DEC-08 — 판정 표를 떨군다. 행은 파생 기록이고(판정은 다시 난다) 이 표를 읽는 소비자가 없다.
DROP TRIGGER base_amount_classification_append_only ON base_amount_classification;
DROP TABLE base_amount_classification;

-- ② OPS-10 — 관문 트리거 → 함수 → 제약 → 열 순서. 트리거가 남은 채 열을 떨구면 함수가 깨진다.
DROP TRIGGER outbox_release_sha_required ON outbox;
DROP TRIGGER collection_run_release_sha_required ON collection_run;
DROP FUNCTION require_release_sha();
ALTER TABLE outbox DROP CONSTRAINT outbox_release_sha_check, DROP COLUMN release_sha;
ALTER TABLE collection_run DROP CONSTRAINT collection_run_release_sha_check, DROP COLUMN release_sha;

-- ③ OPS-05 — 같은 순서. 사유 칸이 사라지면 「왜 종단인가」는 다시 로그만 안다(그것이 되돌림의 대가다).
DROP TRIGGER outbox_terminal_reason_required ON outbox;
DROP FUNCTION require_outbox_terminal_reason();
ALTER TABLE outbox DROP CONSTRAINT outbox_terminal_reason_state_check, DROP COLUMN terminal_reason;
```

**행 처분이 필요 없다** — 세 축이 더한 것은 전부 **열·제약·트리거·표**이고, 열을 떨구면 그 열의
값도 함께 간다. V24 선례(새 어휘 행을 먼저 지워야 `ADD CONSTRAINT` 가 서는 모양)와 다른 축이다.

**이 갈래는 §1 의 파일 삭제와 배타다.** 적용 뒤 V 파일을 지우면 Flyway `validateOnMigrate` 가
「applied migration not resolved locally」로 기동을 막는다 — 적용 뒤에는 새 V 뿐이다.

**순서는 앱 되돌림이 먼저다**(V24 와 반대). 옛 앱은 이 열들을 모르므로 열이 사라져도 멀쩡하지만,
**새 앱은 열이 없으면 `INSERT` 가 깨진다**(outbox 등록·회계 기록이 `release_sha` 를 싣고, 종단
전이가 `terminal_reason` 을 싣는다). 그래서 **앱 → DB** 순서다. 설정 쪽도 같은 축이다 —
`bidvector.release.sha` 를 지우는 것은 옛 jar 로 돌아간 **뒤**라야 한다(새 jar 는 그 값 없이 못 뜬다).

## 3. 실측 (버릴 clone, `git clone --no-hardlinks`, HEAD `50ef257a`)

| 단계 | 실측 |
| --- | --- |
| ① 명령 종료 코드 | `restore` **0** · `git rm` **0** |
| ② 계수 | 내 커밋 경로 **98**(evidence 제외) = 복원 **88**(M) · 삭제 **10**(A) — 기계 목록과 같다 |
| ③ 결과 트리 == `df4976cc` 트리 | 작업 트리 **0 파일** · 색인 **0 파일** · 미추적 **0** |
| ③′ 남의 변경 보존 | 이 브랜치는 병합 0 이라 「남의 변경」 축이 없다. 대신 **남의 표지**를 센다(아래) |
| ④⑤⑥ 복원 트리의 컴파일·test·게이트 | **트리 동일성으로 갈음** — 아래 |
| ⑦ 원천과 남는 차이 | 실측 시점에 `reports/evidence/m9/ops-migrations/**` **둘**뿐(의도된 제외). 그 수는 evidence 커밋마다 자라고 §4 의 술어가 그 디렉터리를 되돌림 대상에서 빼므로 유효성은 흔들리지 않는다 |

### ④⑤⑥ 의 갈음 — 「HEAD 초록」이 아니라 트리 동일성

복원한 트리는 `df4976cc` 의 트리와 **파일 단위로 같다**(③ 의 0 파일 셋). 그 커밋은 머지된 main 이고
CI 가 초록으로 통과한 자리다. 같은 트리에서 `check` 를 다시 돌리는 것은 같은 입력을 다시 재는 일이다.

**같지 않은 입력을 이름 붙인다**: ⑦ 의 evidence 파일 둘이다. 그것들은 빌드 입력이 **맞다**
(누출 패턴 게이트가 `reports/evidence` 를 스캔한다). 그 축은 이 slice 의 HEAD(그 둘을 **포함한**
상위집합)에서 로컬 lint·detekt 가 초록이었고, 전건은 PR CI 가 잰다.

### 표지 계수 — 「내 줄 사라짐」과 「남의 줄 남음」 둘 다

복원 뒤 저장소 전체에서 센 **파일 수**다(evidence 디렉터리 제외). 종료 코드가 아니라 이 계수가
완료의 증거다.

| 내 표지(0 이어야 한다) | 복원 뒤 |
| --- | --- |
| `OutboxTerminalReason` · `terminal_reason` · `ReleaseIdentifier` · `bidvector.release.sha` · `base_amount_classification` · `BaseAmountClassificationRecordPort` · `provenanceJudgementFor` · `release_sha_required` | **전부 0** |
| `V26`·`V27`·`V28` 파일 | **0** |

| 남의 표지(남아 있어야 한다) | 복원 뒤 |
| --- | --- |
| `raw_observation.release_sha`(V1 — 이 slice 가 기댄 조인의 원천) | 3 |
| `V24__notification_inbox_failed_bid_detail`(M9/inbox-vocab) | 2 |
| `OutboxEntryState`(M4/4C-1) | 19 |
| `JdbcCollectionRunStore`(M3/3D) | 12 |
| `BIDVECTOR_RELEASE_TAG`(M7/7F) | 12 |
| `FAILED_OR_REBID`(M9/inbox-vocab) | 36 |

내 표지를 고를 때 `release_sha` 단독은 쓸 수 없다 — V1 의 `raw_observation.release_sha` 가 그
이름을 이미 들고 있어 **남의 줄까지 센다**. 그래서 이 slice 가 처음 들여온 이름들로 골랐다.

## 4. 실측 HEAD 와 판정 SHA 사이가 움직였는가

```
git diff --name-only 50ef257a..<판정 SHA> -- \
  $(git log --no-merges --format= --name-only df4976cc..50ef257a \
      -- . ':(exclude)reports/evidence/m9/ops-migrations' | sort -u)
```

빈 출력이면 이 실측이 유효하고, 한 줄이라도 나오면 **미검증**이다(그때 ①~③ 을 다시 잰다).
동결 보고가 그 확인 결과를 적는다.

## 5. 적용 DB 리허설의 **미실측** 자리

§2 의 forward-fix 는 **모양만** 적혀 있고 실행 실측이 없다 — 공통 규율이 container test 를
로컬에서 금지하고, 그 리허설에는 버릴 Postgres 클러스터가 필요하다. 그래서 두 갈래 가운데
**미적용 갈래만** 실측했다(§3). 적용 갈래의 실행 실측은 verifier·CI 몫으로 남는다(알려진 제한 2).

## 6. 코드를 되돌리지 않고 끄는 법

**세 축이 다르다.**
- **OPS-05·OPS-10 은 끌 수 없다** — 스위치를 더하지 않았다. 사유·릴리스는 쓰기 경로가 늘 싣는다.
- **DEC-08 은 끌 수 있다** — 조립에 끼우는 기록 port 를
  `DiscardingBaseAmountClassificationRecordPort` 로 바꾸면 판정 기록이 0 이 된다(표와 열은 남는다).
  오늘 출하 조립에는 이 분석 자체가 없어 **이미 그 상태**다(알려진 제한 1).
