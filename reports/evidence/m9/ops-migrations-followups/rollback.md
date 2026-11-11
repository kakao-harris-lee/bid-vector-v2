# M9 / ops-migrations-followups — 되돌리기

착수 base **`6e6f953c`** · **복원 원천 `6e6f953c`** · **실측 HEAD `83d97128`**(이 slice 의 마지막 산출물 커밋)

수정 라운드 1 이 복원 목록 안의 파일 넷(runbook 둘 · test 둘)을 다시 만졌으므로 **①~⑥ 을 그 HEAD
에서 다시 돌렸다** — 앞 라운드의 실측(`54211baa`)을 옮기지 않았다.

**원천과 base 가 같다.** 이 브랜치는 main 병합을 **하나도** 하지 않았으므로 「이 브랜치가 마지막으로
합친 main」이 곧 착수 base 다(움직이는 `origin/main` 을 쓰지 않는다). 전제 둘을 함께 쟀다 — 그 SHA 가
HEAD 의 조상이고, 그 SHA 에 이 slice 의 **첫 산출물 커밋이 없다**(base 가 곧 착수 전 main 이므로
구성상 성립한다). 이 절차는 **머지 전**을 전제한다 — 머지 뒤의 되돌림은 그 PR 의 머지 커밋 revert 다
(같은 복원은 머지 순간 아무것도 지우지 않으면서 exit 0 을 낸다).

## 1. 되돌리는 법

range revert 가 아니라 **in_scope 경로 한정 복원**이고 목록은 기계가 낸다.

```
MAIN=6e6f953c                                                    # 고정 SHA
EXCL=':(exclude)reports/evidence/m9/ops-migrations-followups'    # 자기 자신은 되돌릴 수 없다

changes() { git diff --name-status "$MAIN..HEAD" -- . "$EXCL"; }
mapfile -t RESTORE < <(changes | awk '$1=="M"||$1=="D"{print $2}')
mapfile -t DELETE  < <(changes | awk '$1=="A"{print $2}')

# M/D/A 밖의 상태(R·C 등)가 한 줄이라도 나오면 멈춘다 — 이 절차가 다루는 모양이 아니다.
changes | awk '$1!="M"&&$1!="D"&&$1!="A"' | command grep -q . && exit 3

git restore --source=$MAIN --staged --worktree -- "${RESTORE[@]}"
[ ${#DELETE[@]} -eq 0 ] || git rm -q -f -- "${DELETE[@]}"
```

**라운드마다 이 산출을 다시 돌린다** — 목록이 낡는 것이 이 결함의 실제 원인이다.

**hunk 격리가 필요 없다.** 복원 대상 파일을 이 브랜치에서 만진 커밋은 **전부 이 slice 의 것**이고
(`git log --oneline $MAIN..HEAD -- <파일>` 로 확인), 원천이 착수 전 main 이므로 복원된 트리에 남의
줄이 떨어질 자리가 없다. 공유 파일(`docs/runbook/**` · `tools/db-rehearsal.sh` ·
`AppHttpDependencyGateTest.kt`)은 그래서 **원천 복원 한 번**으로 끝난다.

**`DELETE` 는 비어 있을 수 있다** — 이 slice 가 만든 새 파일은 evidence 뿐이고 그것은 `EXCL` 로
빠진다. 그래서 `git rm` 을 조건부로 둔다(빈 pathspec 은 exit 128 이다).

## 2. DB 축 — N/A

이 slice 는 마이그레이션도, production 코드도, 게이트 술어도 건드리지 않는다(test·문서·셸 주석만).
스키마에 닿는 것이 **0** 이므로 적용 DB 쪽 forward-fix 갈래가 없다. 원 slice 의 스키마 되돌림은
`reports/evidence/m9/ops-migrations/rollback.md` §2 가 정본이고 이 slice 가 그것을 바꾸지 않았다.

## 3. 실측 (버릴 clone, `git clone --no-hardlinks`, detached)

| 단계 | 실측 |
| --- | --- |
| ① 목록 산출 | 원천 대비 **복원 6**(전부 `M`) · **삭제 0** · **M/D/A 밖의 상태 0 줄** |
| ①′ 목록 완전성 | 원천과 다른 경로 전부가 목록 안에 있다 — `comm -23` **0 줄** |
| ② 명령 exit | `git restore --source=$MAIN --staged --worktree --` **exit 0**(`git rm` 갈래는 목록이 비어 돌지 않았다 — 그 조건부가 빈 pathspec 의 exit 128 을 막는다) |
| ③ 복원 뒤 `git diff $MAIN -- <복원 경로>` | **0 줄** |
| ③′ **내 표지 0 · 남의 표지 보존** | 표지 **열둘**(파일 여섯 × 각 축 하나)이 전부 기대값 — 내 표지 여섯 모두 **0**, 남의 표지 여섯 모두 **원천 값과 같다**. 어긋난 줄 0. 기대값은 문서에 박지 않고 **원천에서 그때 다시 센다**(`git show <원천>:<파일>`) |
| ④⑤⑥ | **`./gradlew --no-daemon check` exit 0** — 433 task 중 **255 실행 · 178 cache**. 전건이므로 compile·test·게이트 셋을 한 번에 든다. 되돌린 트리에서 게이트가 초록이라 보완 경로가 필요 없었다 |

**④⑤ 의 두 test task 는 cache 에서 왔고 ⑥ 의 게이트는 실행됐다 — 그 구별을 적는다.** 이 라운드의
복원 트리는 **앞 라운드에 실제로 실행된 복원 트리와 compile·test 입력이 바이트 동일**하다(라운드 1 이
만진 넷이 전부 복원 대상이라 복원 뒤 base 로 돌아간다). 그래서 Gradle 이 `:adapters:test`·`:app:test`
를 FROM-CACHE 로 재생했고, 그 **동일성은 내가 선언한 것이 아니라 cache 키가 기계로 증명**한다(입력
해시가 다르면 재생되지 않는다). 그 앞 실행은 같은 복원 트리에서 두 test task 를 **실제로 돌렸고**
37m10s 였다. 게이트 층은 cache 대상이 아니라 이번에도 **실행**됐다 — 누출 스캔 · 모듈별 게이트 test
실행/등재 · 크기 게이트가 실행 목록에 있다. 즉 「HEAD 초록」으로 갈음한 것이 아니라 **트리 동일성**
으로 갈음했고, 바뀐 축(evidence 문면 → 누출 스캔)은 이번 실행이 직접 쟀다.

**③′ 의 표지는 어휘 grep 이 아니라 항목 집합이다.** 파일마다 축 둘을 고르고(이 slice 가 들여온 식별자
하나 · 남의 slice 가 이미 쓰던 식별자 하나) **원천 값과 HEAD 값을 미리 양쪽에서 재** 두고, 복원 뒤의
값이 원천 값과 같은지 본다 — 「0 이 나왔다」만 보면 파일이 통째로 비어도 통과하므로 남의 표지 쪽을
함께 센다. 남의 표지 둘은 이 slice 가 사용 횟수를 바꿨고(하나는 +1, 하나는 −1 — 상수로 모았다),
그래서 **원천 값으로 돌아왔는지**가 구별력 있는 수가 된다.

**⑥ 을 전건으로 든 이유**: 이 slice 가 닿은 게이트가 한 종류가 아니다(ktlint·detekt·size·게이트 test
실행/등재·누출 스캔). 그 집합을 손으로 열거하면 빠뜨린 것이 곧 안 돌린 게이트가 되므로 job 단위로
들었다.

## 4. 실측이 유효한가 — verifier 가 보는 술어

evidence 커밋은 언제나 실측 뒤에 오므로 「실측 HEAD == 판정 SHA」가 아니다. 대조할 것은 **그 사이에
되돌림 대상이 움직였는가**다:

```
git diff --name-only <실측 HEAD>..<판정 SHA> -- <§1 의 RESTORE 목록 경로들>
```

빈 출력이면 유효하고 한 줄이라도 나오면 미검증이다(대조 경로는 이 문서 §1 의 목록 자신이다 —
되돌리지 않는 팀장 레인 문서의 편집이 멀쩡한 실측을 미검증으로 만들지 않게 한다).

| 술어 | 결과 |
| --- | --- |
| §4 대조(실측 HEAD `83d97128` 기준, 이 문서를 쓰는 시점) | **0 줄** — 되돌림 대상이 움직이지 않았다 |

이 뒤에 붙는 커밋은 **evidence 뿐**이고 그 경로는 §1 의 `EXCL` 로 복원 대상 밖이다 — 그래서 판정
SHA 가 evidence 커밋이어도 이 술어는 0 줄로 남는다. 한 줄이라도 나오면 그것은 산출물이 다시 움직인
것이므로 ①~⑥ 을 그 HEAD 에서 다시 돌려야 한다.
