# M10 / notice-resume-total — 되돌리기

착수 base `8062dcd7` · **복원 원천 `ff867b5c`**(이 브랜치가 병합한 main 의 고정 SHA — 움직이는
`origin/main` 을 쓰지 않는다) · **실측 HEAD `fee57b5c`**(이 slice 의 마지막 산출물 커밋 — 그 뒤는 evidence 뿐이다)

**원천이 착수 base 에서 옮겨갔다.** 병합이 마지막 산출물 커밋 뒤에 왔으므로 되돌림이 적용될 트리는
병합 커밋의 트리이고, 실측 자리도 그 커밋이다. 전제 **셋**을 측정했다 —
① 원천이 HEAD 의 조상(`merge-base --is-ancestor` exit 0) ·
② 원천에 이 slice 의 **첫 산출물 커밋이 없다**(같은 술어 exit 1) ·
③ **셋째 단언**: 복원 목록의 M·D 경로가 착수 base 와 새 원천에서 같은 blob 인가 —
`docs/discovery/capability-map.md` **하나가 다르다**(main 이 §12.3 에 (f)·(h)·(i)·(j) 를 더했다).
그래서 **①~⑥ 전부를 새 원천 기준으로 다시 냈다**. 착수 base 기준의 앞 실측은 옮기지 않았다.

이 절차는 **이 slice 가 main 에 없는 동안**을 전제한다. 머지 뒤의 되돌림은 그 PR 의 **머지 커밋
revert** 다 — 같은 복원은 머지 순간 아무것도 지우지 않으면서 exit 0 을 낸다.

배선 변경이 없어 비활성화 스위치는 없다. 되돌리면 공유 누산기의 「마지막 배치 값」 거동으로 돌아간다.

## 1. 되돌리는 법

range revert 가 아니라 **in_scope 경로 한정 복원**이고 목록은 기계가 낸다. 양끝을 **함께** 새 원천으로
옮겼다 — 옛 base 를 한쪽 끝에 남기면 명령은 exit 0 인데 목록이 틀린다.

```
BASE=ff867b5c64b428c4fac5f675be0a186603234135      # 병합한 main 의 고정 SHA
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

**hunk 격리가 필요 없다 — 병합 뒤에도 그렇다.** 공유 파일은 `capability-map.md` 하나인데, 병합 해소를
**삽입 전용**으로 두었기 때문이다: 그 파일의 `git diff ff867b5c` 가 **추가 11 줄 · 삭제 0 줄**이다.
즉 원천 복원이 내 (g) 만 걷고 남의 (f)·(h)·(i)·(j) 는 손대지 않는다 — 아래 ③′ 가 그것을 계수로 센다.
나머지 여섯 경로는 셋째 단언에서 두 원천의 blob 이 같았으므로 어느 원천으로 복원해도 결과가 같다.

**하네스 경로는 되돌리지 않는다** — `git log --oneline <원천>..HEAD -- CLAUDE.md .claude/` 가 **빈 출력**이라
이 range 에 하네스 레인 커밋이 없다.

## 2. DB 축 — N/A

마이그레이션도 스키마도 건드리지 않는다. 바뀌는 것은 `collection_run.source_total` 에 **적히는 값**뿐이고,
이미 0 으로 적힌 과거 행은 고치지 않는다(소급 없음).

## 3. 실측 (버릴 clone, `git clone --no-hardlinks`, detached `fee57b5c`)

| 단계 | 실측 |
| --- | --- |
| 전제 ①②③ | 조상 **exit 0** · 첫 산출물 커밋 미포함 **exit 1** · 셋째 단언에서 공유 파일 **하나**가 다름(위 머리말) |
| ① 목록 산출 | **복원 6**(전부 `M`) · **삭제 1**(`ClaimedSourceTotal.kt`) · **M/D/A 밖 0 줄** |
| ①′ 목록 완전성 | 원천과 다른 경로 전부가 목록 안이다 — `comm -23` **0 줄** |
| ② 명령 exit | `git restore` **exit 0** · `git rm` **exit 0** |
| ③ 복원 뒤 diff | `git diff $BASE -- <복원 경로>` **0 줄** · **evidence 뺀 전수 diff 0 줄**(결과 트리 == 새 원천 트리) · 신설 파일 **없음** |
| ③′ 내 표지 0 · 남의 표지 보존 | 표지 **열**이 전부 기대값 — 아래 |
| ④⑤⑥ | **`./gradlew --no-daemon check` exit 0** · 36m58s — 433 task 중 **278 실행 · 155 cache**. 전건이라 compile·test·게이트를 한 번에 든다. 되돌린 트리에서 게이트가 초록이라 보완 경로가 필요 없었다 |

**기대값을 문서에 박지 않는다** — 원천 값을 그때 `git show <원천>:<파일>` 로 다시 세고 복원 뒤 값과
맞춘다. 「0 이 나왔다」만 보면 파일이 통째로 비어도 통과하므로 **남의 표지**를 함께 센다.

| 파일 | 내 표지 (원천 → HEAD → 복원 뒤) | 남의 표지 (원천 → HEAD → 복원 뒤) |
| --- | --- | --- |
| capability map | `#### (g)` 0 → 1 → **0** | `#### (f)`·`(h)`·`(i)`·`(j)` 각 1 → 1 → **각 1** |
| `CollectNoticesUseCase` | `widerTotalOf` 0 → 2 → **0** | `SlotTally` 2 → 3 → **2** |
| `CollectAwardListUseCase` | `widerTotalOf` 2 → 1 → **2**(이동이 걷어간 선언이 돌아온다) | — |
| `CollectionTestSupport` | `claimedTotalAccounting` 0 → 1 → **0** | `sourceAccounting` 2 → 3 → **2** |

어긋난 줄 **0**. 남의 표지 쪽에서 결정적인 것은 **capability map 의 (f)·(h)·(i)·(j) 가 복원 뒤에도 각 1**
이라는 것이다 — 공유 문서를 병합한 main 으로 복원하면서 다른 세 레인의 절을 떨어뜨리지 않았다는 뜻이고,
그것이 이 라운드에서 원천을 바꾼 유일한 위험이었다.

**④⑤⑥ 은 갈음이 아니라 실행이다.** 되돌린 트리는 원천 트리와 **evidence 디렉터리 하나만큼** 다르고 그
경로는 누출 스캔의 scanRoot 안이라 「HEAD 가 초록」으로 갈음할 수 없다.

**`:workflow:test` 는 `FROM-CACHE` 였다 — 그것을 실행의 증거로 쓰지 않는다.** 근거는 ③ 이 잰 **트리
동일성**이다: 되돌린 트리가 원천과 다른 곳은 **자기 evidence 디렉터리 하나뿐**이고(전수 diff 0 줄) 그
경로는 그 task 의 입력이 아니다. 접미 없이 **실행된** test task 는 `:adapters:test`·`:app:test`·
`:build-logic:test` 셋이다.

## 4. 실측이 유효한가 — verifier 가 보는 술어

evidence 커밋은 언제나 실측 뒤에 오므로 「실측 HEAD == 판정 SHA」가 아니다. 대조할 것은 **그 사이에
되돌림 대상이 움직였는가**이고, 병합이 또 끼지 않았는지도 함께 본다:

```
git diff --name-only <실측 HEAD>..<판정 SHA> -- <§1 의 RESTORE·DELETE 목록 경로들>
git rev-list --merges <실측 HEAD>..<판정 SHA>
```

둘 다 빈 출력이면 유효하다. 이 뒤에 붙는 커밋은 **evidence 뿐**이고 그 경로는 §1 의 `EXCL` 로 복원 대상
밖이라 첫 술어가 빈 출력으로 선다. 한 줄이라도 나오면 ①~⑥ 을 그 HEAD 에서 다시 돌려야 한다.
