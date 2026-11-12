# M10 / notice-resume-total — 되돌리기

착수 base **`8062dcd7`** · **복원 원천 = 착수 base**(이 브랜치는 main 을 병합하지 않았다) ·
**실측 HEAD `4ec1d655`**(이 slice 의 마지막 산출물 커밋 — 그 뒤는 evidence 뿐이다)

전제 둘을 측정했다 — 원천이 HEAD 의 조상이고(`merge-base --is-ancestor` exit 0), `<원천>..HEAD` 의
**병합 커밋이 0**(`rev-list --merges`)이다. 그래서 원천 교체도, 병합 커밋의 손 해소 줄도 없다.
이 절차는 **이 slice 가 main 에 없는 동안**을 전제한다. 머지 뒤의 되돌림은 그 PR 의 **머지 커밋 revert** 다 —
같은 복원은 머지 순간 아무것도 지우지 않으면서 exit 0 을 낸다.

배선 변경이 없어 비활성화 스위치는 없다. 되돌리면 공유 누산기의 「마지막 배치 값」 거동으로 돌아간다.

## 1. 되돌리는 법

range revert 가 아니라 **in_scope 경로 한정 복원**이고 목록은 기계가 낸다.

```
BASE=8062dcd784cf81905c50a2c8706d8bbd3ea5263e
EXCL=':(exclude)reports/evidence/m10/notice-resume-total'   # 자기 자신은 되돌릴 수 없다

changes() { git diff --name-status "$BASE..HEAD" -- . "$EXCL"; }
mapfile -t RESTORE < <(changes | awk '$1=="M"||$1=="D"{print $2}')
mapfile -t DELETE  < <(changes | awk '$1=="A"{print $2}')

# M/D/A 밖의 상태(R·C 등)가 한 줄이라도 나오면 멈춘다 — 이 절차가 다루는 모양이 아니다.
changes | awk '$1!="M"&&$1!="D"&&$1!="A"' | command grep -q . && exit 3

git restore --source=$BASE --staged --worktree -- "${RESTORE[@]}"
[ ${#DELETE[@]} -eq 0 ] || git rm -q -f -- "${DELETE[@]}"
```

**라운드마다 이 산출을 다시 돌린다** — 목록이 낡는 것이 이 결함의 실제 원인이다.

**hunk 격리가 필요 없다.** 이 slice 의 in_scope 파일을 만진 커밋은 **이 slice 의 것뿐이고**(`git log
--oneline $BASE..HEAD -- <복원 경로>` 가 이 slice 의 다섯 커밋) 병합도 없다. 그래서 원천 복원 한 번이
**이 slice 의 줄만** 걷는다 — 아래 ③′ 가 그것을 남의 표지 쪽에서도 센다.

**하네스 경로는 되돌리지 않는다** — `git log --oneline $BASE..HEAD -- CLAUDE.md .claude/` 가 **빈 출력**이라
이 range 에 하네스 레인 커밋이 없다(scope.md 「하네스 레인 변경」 절과 같은 값).

## 2. DB 축 — N/A

마이그레이션도 스키마도 건드리지 않는다. 바뀌는 것은 `collection_run.source_total` 에 **적히는 값**뿐이고,
이미 0 으로 적힌 과거 행은 고치지 않는다(소급 없음).

## 3. 실측 (버릴 clone, `git clone --no-hardlinks`, detached `4ec1d655`)

| 단계 | 실측 |
| --- | --- |
| ① 목록 산출 | **복원 6**(전부 `M`) · **삭제 1**(`ClaimedSourceTotal.kt`) · **M/D/A 밖 0 줄** |
| ①′ 목록 완전성 | 원천과 다른 경로 전부가 목록 안이다 — `comm -23` **0 줄** |
| ② 명령 exit | `git restore` **exit 0** · `git rm` **exit 0** |
| ③ 복원 뒤 diff | `git diff $BASE -- <복원 경로>` **0 줄** · **evidence 뺀 전수 diff 0 줄**(결과 트리 == base 트리) · 신설 파일 **없음** · 되돌리지 않은 자기 evidence 그대로 |
| ③′ 내 표지 0 · 남의 표지 보존 | 표지 **열**(파일 여섯 × 내 축/남의 축)이 전부 기대값 — 아래 |
| ④⑤⑥ | **`./gradlew --no-daemon check` exit 0** · 36m51s — 433 task 중 **278 실행 · 155 cache**. 전건이라 compile·test·게이트를 한 번에 든다. 되돌린 트리에서 게이트가 초록이라 보완 경로가 필요 없었다 |

**기대값을 문서에 박지 않는다** — 원천 값을 그때 `git show <원천>:<파일>` 로 다시 세고 복원 뒤 값과 맞춘다.
「0 이 나왔다」만 보면 파일이 통째로 비어도 통과하므로 **남의 표지**를 함께 센다.

| 파일 | 내 표지 (원천 → HEAD → 복원 뒤) | 남의 표지 (원천 → HEAD → 복원 뒤) |
| --- | --- | --- |
| capability map | `OPEN-M9-NOTICE-LIST-RESUME-TOTAL` 1 → 3 → **1** | `M6 범위` 1 → 1 → **1** |
| `CollectNoticesUseCase` | `widerTotalOf` 0 → 2 → **0** | `SlotTally` 2 → 3 → **2** |
| `CollectAwardListUseCase` | `widerTotalOf` 2 → 1 → **2**(이동이 걷어간 선언이 돌아온다) | `attemptOutcomeOf` 4 → 4 → **4** |
| `CollectionTestSupport` | `claimedTotalAccounting` 0 → 1 → **0** | `sourceAccounting` 2 → 3 → **2** |
| 낙찰 test | `claimedTotalAccounting` 0 → 3 → **0** | — |
| 공고 목록 test | `claimedTotalAccounting` 0 → 2 → **0** | — |

어긋난 줄 **0**. 낙찰 갈래의 `widerTotalOf` 가 복원 뒤 **늘어나는**(1 → 2) 유일한 축인데, 이 slice 가 한 것이
**이동**이라 그렇다 — 되돌림이 선언을 제자리에 돌려놓는다.

**④⑤⑥ 은 갈음이 아니라 실행이다.** 되돌린 트리는 base 트리와 **evidence 디렉터리 하나만큼** 다르고 그
경로는 누출 스캔의 scanRoot 안이라 「HEAD 가 초록」으로 갈음할 수 없다. 전건으로 든 이유는 이 slice 가 닿은
게이트가 한 종류가 아니기 때문이다(ktlint · 크기 · CPD · test).

**`:workflow:test` 는 `FROM-CACHE` 였다 — 그것을 실행의 증거로 쓰지 않는다.** 대신 ③ 이 잰 **트리 동일성**이
근거다: 되돌린 트리가 base 와 다른 곳은 **자기 evidence 디렉터리 하나뿐**이고(전수 diff 0 줄) 그 경로는
`:workflow:test` 의 입력이 아니다. 접미 없이 **실행된** test task 는 `:adapters:test`·`:app:test`·
`:build-logic:test` 셋이고, 누출 스캔·모듈별 게이트 층은 실행 목록에 있다.

## 4. 실측이 유효한가 — verifier 가 보는 술어

evidence 커밋은 언제나 실측 뒤에 오므로 「실측 HEAD == 판정 SHA」가 아니다. 대조할 것은 **그 사이에
되돌림 대상이 움직였는가**다:

```
git diff --name-only <실측 HEAD>..<판정 SHA> -- <§1 의 RESTORE·DELETE 목록 경로들>
git rev-list --merges <실측 HEAD>..<판정 SHA>
```

둘 다 빈 출력이면 유효하다. 이 뒤에 붙는 커밋은 **evidence 뿐**이고 그 경로는 §1 의 `EXCL` 로 복원 대상
밖이라 첫 술어가 빈 출력으로 선다. 한 줄이라도 나오면 산출물이 다시 움직인 것이므로 ①~⑥ 을 그 HEAD 에서
다시 돌려야 한다.
