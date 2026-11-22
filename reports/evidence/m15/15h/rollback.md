# M15/15H — rollback

실측 HEAD: `f9bd13cf915e46beb5387576c8c229139aaf8d96` (이 slice 의 **마지막 산출물 커밋**)

되돌림은 **range revert 가 아니라 in_scope 경로 한정 복원**이다 — `git revert <base>..HEAD` 는 같은
range 의 하네스 레인 커밋까지 되돌린다.

## 전제

- **이 slice 는 아직 main 에 없다.** 복원 원천은 착수 base
  `ff8e0cad42c5c114ee30b42ca4318252bbf35c5f` 이고, 이 브랜치는 그 뒤로 main 을 병합하지 않았다
  (`git rev-list --merges <base>..<실측 HEAD>` 가 **0 줄**). 움직이는 ref(`origin/main`)를 쓰지 않는다.
- 단언 둘: base 가 실측 HEAD 의 조상이다 · base 에 이 slice 의 **첫 산출물 커밋이 없다**(브랜치를
  그 SHA 에서 끊었다). 그래서 복원 원천이 다른 slice 의 줄을 떨어뜨릴 수 없고 해시 목록이 필요 없다.
- **복원 원천을 바꾸지 않았으므로 「셋째 단언」은 대상이 아니다**(착수 base == 복원 원천).
- **머지 뒤의 되돌림은 이 문서가 아니라 그 PR 의 머지 커밋 revert 다.** 머지된 뒤 같은 복원을
  돌리면 아무것도 지우지 않으면서 exit 0 을 낸다.

## 공유 파일 — hunk 격리가 필요한가

필요 없다. `git log --oneline <base>..HEAD -- config/quality/gate-tests.properties
docs/discovery/capability-map.md docs/runbook/m6-6e-operations.md` 가 **이 slice 의 커밋만** 낸다.
다른 slice 의 줄이 range 안에 없으므로 파일 단위 복원이 남의 줄을 지우지 않는다.

## 복원 목록 — 기계 산출

`git diff --name-status ff8e0cad42c5c114ee30b42ca4318252bbf35c5f..HEAD` 에서 낸다(A = 복원이 삭제 ·
M = base 내용으로 되돌림). **라운드마다 파일이 늘면 이 절차를 다시 돌린다** — 목록이 낡는 것이 이
결함의 실제 원인이다.

- `app/src/main/kotlin/bidvector/app/evaluation/EvaluationCommitRunner.kt` (M)
- `app/src/main/kotlin/bidvector/app/wiring/EvaluationWiring.kt` (M)
- `app/src/test/kotlin/bidvector/app/evaluation/EvaluationCommitRotationTest.kt` (M)
- `app/src/test/kotlin/bidvector/app/evaluation/EvaluationCommitRunnerFixtures.kt` (M)
- `app/src/test/kotlin/bidvector/app/evaluation/EvaluationCommitRunnerTest.kt` (M)
- `app/src/test/kotlin/bidvector/app/evaluation/EvaluationCommitStopLineTest.kt` (A → 삭제)
- `config/quality/gate-tests.properties` (M)
- `docs/discovery/capability-map.md` (M)
- `docs/runbook/m6-6e-operations.md` (M)

**목록 밖**: `reports/evidence/m15/15h/` — 이 slice 의 evidence 는 되돌리지 않는다(저장소 관례).
그래서 §4 술어(`git diff --name-only <실측 HEAD>..<판정 SHA> -- <위 목록>`)가 evidence 커밋이 뒤에
붙어도 빈 출력으로 선다. 하네스 경로(`CLAUDE.md`·`.claude/**`)도 목록 밖이고 range 안에 그 커밋이
**없다**.

## 명령

```
git restore --source=ff8e0cad42c5c114ee30b42ca4318252bbf35c5f --staged --worktree -- \
  app/src/main/kotlin/bidvector/app/evaluation/EvaluationCommitRunner.kt \
  app/src/main/kotlin/bidvector/app/wiring/EvaluationWiring.kt \
  app/src/test/kotlin/bidvector/app/evaluation/EvaluationCommitRotationTest.kt \
  app/src/test/kotlin/bidvector/app/evaluation/EvaluationCommitRunnerFixtures.kt \
  app/src/test/kotlin/bidvector/app/evaluation/EvaluationCommitRunnerTest.kt \
  app/src/test/kotlin/bidvector/app/evaluation/EvaluationCommitStopLineTest.kt \
  config/quality/gate-tests.properties \
  docs/discovery/capability-map.md \
  docs/runbook/m6-6e-operations.md
```

경로는 **개별 인자**다(목록을 변수 하나에 담으면 pathspec 이 하나가 된다). `--source` 에 없는
경로는 삭제되므로 신규 파일에 별도 `git rm` 이 필요 없다 — `git checkout <base> -- <경로>` 는 쓰지
않는다(base 에 없는 경로에서 pathspec 오류로 exit 1 이고 아무것도 적용되지 않는다).

## 무엇이 되돌아가는가

- 회전 보폭이 **1 에서 `n-1` 로** 돌아간다 — 주기적 결행(평일 timer)의 영구 기아가 **다시 열리고**
  매 달력일 cron 의 한 바퀴는 짧아진다. 설정 키·기본값은 양쪽 판에서 같으므로 **설정 변경도
  마이그레이션도 없다**. 영속 상태를 두지 않았으므로 되돌림에 데이터 정리가 따르지 않는다.
- 멈춤 줄 `consecutive=` 의 의미 test 둘이 사라지고, test 파일이 셋에서 둘로 합쳐진다
  (`EvaluationCommitRunnerTest` 가 499 줄 — 크기 게이트 한도의 한 줄 아래 — 로 돌아간다).
- 게이트 등록부에서 `EvaluationCommitStopLineTest` 줄이 빠진다(그 클래스가 함께 사라지므로 등식이
  다시 선다 — ⑥ 이 그것을 실측했다).
- runbook·capability-map 의 문면이 옛 식(`ceil((K-1)/(n-1))`)과 열린 ⓐ 로 돌아간다.

**예상 복구 시간**: 복원 명령 1 분 + 전건 `check` 재실행(실측 39 분).

## 임시 clone 실측 — ①~⑥

저장소 **밖** clone 에서 실측 HEAD 를 뽑아 끝까지 실행했다.

| # | 무엇 | 결과 |
|---|---|---|
| ① | 복원 명령 종료 코드 | **0** |
| ② | D/M 수 | **D 1 · M 8**(복원 목록과 같다) |
| ③ | 복원 경로의 `git diff <base>` | **빈 출력**(0 줄) |
| ④ | 되돌린 트리의 compile | **통과** — ⑥ 의 전건이 포함한다 |
| ⑤ | 되돌린 트리의 test | **통과** — 같은 run |
| ⑥ | 되돌린 트리의 게이트 — `check --continue` | **exit 0** · 433 task 전수 실행(`UP-TO-DATE` 0, cache 0) |

**되돌린 트리의 `git write-tree` 는 `970be49d7212809aece4e27a86430e4e442eaaf5`** 이고, base 트리와의
차이는 **이 slice 자신의 evidence 뿐**이다(의도적으로 되돌리지 않는 것).

④⑤⑥ 은 **먼저 실제로 돌렸다**(앞 산출물 커밋 `fd0ab770…` 의 clone, 위 수치). 그 뒤 KDoc 한 군데를
고친 커밋이 붙어 실측 자리가 `f9bd13cf…` 로 옮겼고, 그 자리에서 ①②③ 을 **다시 쟀다** — 그리고
되돌린 트리가 **같은 해시**(`970be49d…`)다. 갈음의 근거는 「HEAD 가 초록이다」가 아니라 **트리
동일성**이고, 그것이 여기서 성립한다(복원이 그 파일을 base 내용으로 덮으므로 head 쪽 편집이 되돌린
트리를 바꾸지 못한다).
