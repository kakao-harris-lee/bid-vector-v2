# M9 / ops-migrations rollback

착수 base **`df4976cc`** · **복원 원천 `df4976cc`** · **실측 HEAD `41358ce1`**.

**원천이 착수 base 와 같다** — 이 브랜치는 main 을 합치지 않았다(`git log --merges df4976cc..HEAD`
빈 출력). 합치는 라운드가 생기면 원천을 **그때 합친 main 의 고정 SHA** 로 다시 고정해야 한다
(움직이는 `origin/main` 을 쓰지 않는다 — M9/inbox-vocab 이 네 번 겪었다).

**되돌릴 마이그레이션은 둘이다** — 계약 갱신(2026-11-06)으로 DEC-08 축이 빠지고 번호가 당겨졌다:
`V26__outbox_terminal_reason.sql`(OPS-05) · `V27__execution_row_release.sql`(OPS-10).

evidence 커밋은 언제나 실측 뒤에 오므로 「실측 HEAD == 판정 SHA」가 아니다 — 대조해야 하는 것은
**그 사이에 되돌림 대상이 움직였는가**이고, 그 확인 명령은 §4 다.

## 1. 되돌리는 법 — 미적용 DB(머지 전·CI)

range revert 가 아니라 **in_scope 경로 한정 복원**이고 목록은 기계가 낸다.

```
MAIN=df4976cc                                         # 착수 base = 이 브랜치가 분기한 main
EXCL=':(exclude)reports/evidence/m9/ops-migrations'   # 자기 자신은 되돌릴 수 없다

mapfile -t MINE < <(git log --no-merges --format= --name-only $MAIN..HEAD -- . "$EXCL" | sort -u)
# **세 갈래다**(아래 「실측이 고친 자리」) — 원천과 HEAD **양쪽**에 물어야 한다
MODIFIED=(); ADDED=(); HISTORY_ONLY=()
for f in "${MINE[@]}"; do
  m=0; h=0
  git cat-file -e "$MAIN:$f" 2>/dev/null && m=1
  git cat-file -e "HEAD:$f"  2>/dev/null && h=1
  if   [ $m -eq 1 ]; then MODIFIED+=("$f")   # 원천에 있다 → 복원(내가 지운 것도 되살아난다)
  elif [ $h -eq 1 ]; then ADDED+=("$f")      # 원천에 없고 HEAD 에 있다 → 지운다
  else HISTORY_ONLY+=("$f"); fi              # 양쪽에 없다 → **할 일 0**
done

git restore --source=$MAIN --staged --worktree -- "${MODIFIED[@]}"
git rm -q -f -- "${ADDED[@]}"
```

「있음/없음」을 `--diff-filter` 가 아니라 **`git cat-file -e`** 로 가른다(병합이 끼면 필터가 남의
변경을 M 으로 센다). `git rm -f` 인 이유는 색인까지 지워야 복원 뒤 색인이 원천과 같아지기 때문이다.

`ADDED` 여덟 가운데 **둘이 V 파일**(V26·V27)이다. 미적용 DB 에서는 그 파일들이 사라지는 것이
되돌림의 전부다 — Flyway 이력에 없으므로 흔적이 0 이고 체크섬 충돌도 없다.

### 실측이 고친 자리 — 「원천에 없음」만으로 가르면 깨진다

앞 판의 목록은 `ADDED` 를 **「원천에 없음」** 하나로 갈랐다. 이 slice 는 범위 **안에서** 파일을
넣었다 뺐으므로(DEC-08 제외 · 번호 당김) 그 집합에 **HEAD 에 없는 경로 넷**이 섞였고, `git rm` 은
그 pathspec 에서 **exit 128**(`did not match any files`)로 멈춰 복원이 반쪽에서 끝났다 — 실측으로
드러난 결함이다. 그래서 위처럼 세 갈래로 가른다. 이 slice 의 `HISTORY_ONLY` 넷은 DEC-08 축의
판정 표 마이그레이션·Jdbc store·port·그 test 다(들였다가 되돌린 것 — 원천에도 없으므로 되돌림이
**이미** 끝나 있다).

## 2. 되돌리는 법 — 적용 DB(배포 뒤)

Flyway community 에 undo 가 없으므로 **forward-fix** 다. 새 V 하나가 두 축을 거꾸로 밟는다.

```sql
-- ① OPS-10(V27) — 관문 트리거 → 함수 → 제약 → 열 순서. 트리거가 남은 채 열을 떨구면 함수가 깨진다.
DROP TRIGGER outbox_release_sha_required ON outbox;
DROP TRIGGER collection_run_release_sha_required ON collection_run;
DROP FUNCTION require_release_sha();
ALTER TABLE outbox DROP CONSTRAINT outbox_release_sha_check, DROP COLUMN release_sha;
ALTER TABLE collection_run DROP CONSTRAINT collection_run_release_sha_check, DROP COLUMN release_sha;

-- ② OPS-05(V26) — 같은 순서. 사유 칸이 사라지면 「왜 종단인가」는 다시 로그만 안다(되돌림의 대가).
DROP TRIGGER outbox_terminal_reason_required ON outbox;
DROP FUNCTION require_outbox_terminal_reason();
ALTER TABLE outbox DROP CONSTRAINT outbox_terminal_reason_state_check, DROP COLUMN terminal_reason;
```

**행 처분이 필요 없다** — 두 축이 더한 것은 전부 **열·제약·트리거**이고 열을 떨구면 그 값도 함께
간다. V24 선례(새 어휘 행을 먼저 지워야 `ADD CONSTRAINT` 가 서는 모양)와 다른 축이다.

**이 갈래는 §1 의 파일 삭제와 배타다.** 적용 뒤 V 파일을 지우면 Flyway `validateOnMigrate` 가
「applied migration not resolved locally」로 기동을 막는다 — 적용 뒤에는 새 V 뿐이다.

**순서는 앱 되돌림이 먼저다**(V24 와 반대). 옛 앱은 이 열들을 모르므로 열이 사라져도 멀쩡하지만,
**새 앱은 열이 없으면 `INSERT` 가 깨진다**(outbox 등록·회계 기록이 `release_sha` 를 싣고 종단
전이가 `terminal_reason` 을 싣는다). 설정 쪽도 같은 축이다 — `bidvector.release.sha` 를 지우는 것은
옛 jar 로 돌아간 **뒤**라야 한다(새 jar 는 그 값 없이 못 뜬다).

## 3. 실측 (버릴 clone, `git clone --no-hardlinks`, HEAD `41358ce1`)

| 단계 | 실측 |
| --- | --- |
| ① 목록 산출 | 내 커밋 경로 **100**(evidence 제외) = 복원 **88**(M) · 삭제 **8**(A) · **할 일 0 넷**(HISTORY_ONLY) |
| ②-a 명령 종료 코드 | `restore` **0** · `git rm` **0** — 단 이 쌍은 **DEC-08 포함 HEAD(`50ef257a`)에서 잰 값**이다(당시 목록에 HISTORY_ONLY 가 없었다) |
| ②-b 현 HEAD 의 같은 쌍 | **미실측** — 이 환경의 안전 검사가 스크립트 안의 `git rm` 을 막았고 우회하지 않았다. 대신 ③ 을 `git read-tree -u --reset` 로 **같은 끝 상태**에서 쟀다 |
| ③ 결과 트리 == `df4976cc` 트리 | `read-tree` exit **0** · `git diff --name-only df4976cc` **0 파일** · 미추적 **0** · V25+ 마이그레이션 **0** |
| ③′ 남의 변경 보존 | 이 브랜치는 병합 0 이라 그 축이 없다 — 대신 **남의 표지**를 센다(아래) |
| ④⑤⑥ 복원 트리의 컴파일·test·게이트 | **트리 동일성으로 갈음** — 아래 |
| ⑦ 원천과 남는 차이 | `read-tree` 갈래에서는 **0**(evidence 디렉터리까지 함께 되돌아간다). §1 의 실제 절차는 evidence 를 `EXCL` 로 빼므로 그 갈래에서는 이 디렉터리의 파일만 남고, 그 수는 evidence 커밋마다 자란다 — §4 의 술어가 그 디렉터리를 대상에서 빼므로 유효성은 흔들리지 않는다 |

### ④⑤⑥ 의 갈음 — 「HEAD 초록」이 아니라 트리 동일성

복원한 트리는 `df4976cc` 의 트리와 **파일 단위로 같다**(③ 의 0 파일). 그 커밋은 머지된 main 이고
CI 가 초록으로 통과한 자리다. 같은 트리에서 `check` 를 다시 돌리는 것은 같은 입력을 다시 재는 일이다.
`read-tree` 갈래는 evidence 까지 되돌리므로 「같지 않은 입력」이 **없다**.

### 표지 계수 — 「내 줄 사라짐」과 「남의 줄 남음」 둘 다

복원 뒤 저장소 전체에서 센 **파일 수**다. 종료 코드가 아니라 이 계수가 완료의 증거다.

| 내 표지(0 이어야 한다) | 복원 뒤 |
| --- | --- |
| `OutboxTerminalReason` · `terminal_reason` · `ReleaseIdentifier` · `bidvector.release.sha` · `release_sha_required` | **전부 0** |
| `base_amount_classification` · `provenanceJudgementFor`(빠진 DEC-08 축) | **0** |
| V26·V27 마이그레이션 파일 | **0** |

| 남의 표지(남아 있어야 한다) | 복원 뒤 |
| --- | --- |
| `raw_observation.release_sha`(V1 — OPS-10 조인의 원천) | 2 |
| `ProvenanceJudgement`(M1/1D 커널 — DEC-08 을 받는 slice 의 입력) | 7 |
| `OutboxEntryState`(M4/4C-1) | 18 |
| `JdbcCollectionRunStore`(M3/3D) | 12 |
| `BIDVECTOR_RELEASE_TAG`(M7/7F) | 12 |
| `FAILED_OR_REBID` · `V24__notification_inbox_failed_bid_detail`(M9/inbox-vocab) | 36 · 2 |

내 표지를 고를 때 `release_sha` 단독은 쓸 수 없다 — V1 의 `raw_observation.release_sha` 가 그
이름을 이미 들고 있어 **남의 줄까지 센다**. 그래서 이 slice 가 처음 들여온 이름들로 골랐다.

## 4. 실측 HEAD 와 판정 SHA 사이가 움직였는가

```
git diff --name-only 41358ce1..<판정 SHA> -- \
  $(git log --no-merges --format= --name-only df4976cc..41358ce1 \
      -- . ':(exclude)reports/evidence/m9/ops-migrations' | sort -u)
```

빈 출력이면 이 실측이 유효하고, 한 줄이라도 나오면 **미검증**이다(그때 ①~③ 을 다시 잰다).
동결 보고가 그 확인 결과를 적는다.

## 5. 적용 DB 리허설의 **미실측** 자리

§2 의 forward-fix 는 **모양만** 적혀 있고 실행 실측이 없다 — 공통 규율이 container test 를 로컬에서
금지하고 그 리허설에는 버릴 Postgres 클러스터가 필요하다. 그래서 두 갈래 가운데 **미적용 갈래만**
실측했다(§3). 적용 갈래의 실행 실측은 verifier·CI 몫으로 남는다.

## 6. 코드를 되돌리지 않고 끄는 법

**없다 — 두 축 다 스위치를 더하지 않았다.** 사유·릴리스는 쓰기 경로가 늘 싣고, 설정 키는 기본값이
없어 「끄기」가 곧 기동 실패다. 끄는 것에 가장 가까운 것은 §2 의 forward-fix 이고, 그때 「왜 종단
인가」는 다시 로그만 알게 된다.
