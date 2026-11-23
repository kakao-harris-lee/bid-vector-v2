# M15/15H — rollback

실측 HEAD: `7fc13c2eb642982ac11538cab4997e8e172b3077` (**main 병합 커밋** — 이 slice 의 마지막
산출물 커밋 뒤에 병합이 왔으므로 실측 자리는 그 병합 커밋이다)

되돌림은 **range revert 가 아니라 in_scope 경로 한정 복원**이다 — `git revert <base>..HEAD` 는 같은
range 의 하네스 레인 커밋과 **병합해 들어온 다른 slice** 까지 되돌린다.

## 복원 원천 — 착수 base 가 아니라 **병합한 main SHA** 다

`717a00a7f4661064b7e929d9f46c209550d82051` (PR #196 / M15/15I 의 머지 커밋). 고정 SHA 로 적는다 —
움직이는 ref(`origin/main`)를 쓰지 않는다.

**왜 옮겼는가**: 착수 base(`ff8e0cad42c5c114ee30b42ca4318252bbf35c5f`)에서 복원하면
`docs/discovery/capability-map.md` 에서 **M15/15I 가 쓴 줄(ⓑⓒⓓⓔ 닫힘)까지 함께 지워진다**.
그 파일은 병합으로 **공유 파일이 됐다**.

**단언 셋**(전부 실측):

| # | 단언 | 결과 |
|---|---|---|
| 1 | 새 원천이 실측 HEAD 의 조상 | `git merge-base --is-ancestor` **참**(병합 커밋의 부모다) |
| 2 | 새 원천에 이 slice 의 **첫 산출물 커밋**(`4ed36b3c5`)이 없다 | **참** — main 은 이 브랜치를 담은 적이 없다 |
| 3 | 복원 대상 blob 이 착수 base 와 새 원천에서 같은가 | **`docs/discovery/capability-map.md` 하나가 다르다**(15I 가 그 행을 다시 썼다). 그래서 **①~⑥ 전부를 새 원천 기준으로 다시 냈다** — 착수 base 기준의 앞 실측(`970be49d…` 트리)을 옮기지 않았다 |

목록 재산출의 **양끝도 함께 옮겼다**: `git diff --name-status <새 원천>..<새 실측 HEAD>`.

- **머지 뒤의 되돌림은 이 문서가 아니라 그 PR 의 머지 커밋 revert 다.** 이 slice 가 main 에 들어간
  뒤 같은 복원을 돌리면 아무것도 지우지 않으면서 exit 0 을 낸다(의미가 뒤집힌다).

## 공유 파일 — hunk 격리가 필요한가

필요 없다. 공유가 된 파일은 `docs/discovery/capability-map.md` **하나**이고, 새 원천이 **main 으로부터
만든 트리**라 그 파일을 원천 내용으로 덮으면 15I 의 줄이 그대로 서고 이 slice 의 줄만 사라진다 —
커밋 해시별 `git apply -R` 이 필요 없다(병합 커밋의 충돌 해소 줄은 `--no-merges` 목록 밖이라 hunk
격리로는 걷히지도 않는다). 아래 ⑦ 의 **표지 계수**가 그 결과를 실측한다.

나머지 복원 경로는 `git diff --name-status <착수 base>..<새 원천>` 에 **없다** — 15I 가 만지지 않았다.

## 복원 목록 — 기계 산출

`git diff --name-status 717a00a7f4661064b7e929d9f46c209550d82051..HEAD` 에서 낸다(A = 복원이 삭제 ·
M = 원천 내용으로 되돌림). **라운드마다 파일이 늘면 이 절차를 다시 돌린다.**

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
그래서 §4 술어가 evidence 커밋이 뒤에 붙어도 빈 출력으로 선다. 하네스 경로(`CLAUDE.md`·`.claude/**`)도
목록 밖이고 range 안에 그 커밋이 **없다**. 병합으로 들어온 15I 의 산출물
(`web/**`·`config/quality/leak-patterns*`·`build-logic/**`·`tools/db-rehearsal.sh`·
`reports/evidence/m15/15i/`)도 **이 slice 가 되돌릴 것이 아니다**.

## 명령

```
git restore --source=717a00a7f4661064b7e929d9f46c209550d82051 --staged --worktree -- \
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
경로는 삭제되므로 신규 파일에 별도 `git rm` 이 필요 없다 — `git checkout <원천> -- <경로>` 는 쓰지
않는다(원천에 없는 경로에서 pathspec 오류로 exit 1 이고 아무것도 적용되지 않는다).

## 무엇이 되돌아가는가

- 회전 보폭이 **1 에서 `n-1` 로** 돌아간다 — 주기적 결행(평일 timer)의 영구 기아가 **다시 열리고**
  매 달력일 cron 의 한 바퀴는 짧아진다. 설정 키·기본값은 양쪽 판에서 같으므로 **설정 변경도
  마이그레이션도 없다**. 영속 상태를 두지 않았으므로 되돌림에 데이터 정리가 따르지 않는다.
- 멈춤 줄 `consecutive=` 의 의미 test 둘이 사라지고, test 파일이 셋에서 둘로 합쳐진다
  (`EvaluationCommitRunnerTest` 가 499 줄 — 크기 게이트 한도의 한 줄 아래 — 로 돌아간다).
- 게이트 등록부에서 `EvaluationCommitStopLineTest` 줄이 빠진다(그 클래스가 함께 사라지므로 등식이
  다시 선다 — ⑥ 이 그것을 실측했다).
- `capability-map.md` 의 `OPEN-M15-TEST-RESIDUE` 행이 **15I 판**으로 돌아간다 — ⓐ 가 다시 열리고
  ⓑⓒⓓⓔ 닫힘과 ⓕ 는 **그대로 선다**(ⓕ 는 15I 의 리뷰가 낳은 것이라 이 slice 의 줄이 아니다…
  **다만 그 행에 ⓕ 를 적은 것은 이 slice 의 병합 해소다** — 되돌리면 ⓕ 서술이 함께 사라진다.
  그 사실을 아래 ⑦ 이 계수로 보인다. ⓕ 를 남기려면 복원 뒤 그 행에 다시 적는다).
- `runbook` 의 문면이 옛 식(`ceil((K-1)/(n-1))`)으로 돌아간다.

**예상 복구 시간**: 복원 명령 1 분 + 전건 `check` 재실행(실측 아래 ⑥).

## 임시 clone 실측 — ①~⑦

저장소 **밖** clone 에서 실측 HEAD(병합 커밋)를 뽑아 끝까지 실행했다.

| # | 무엇 | 결과 |
|---|---|---|
| ① | 복원 명령 종료 코드 | **0** |
| ② | D/M 수 | **D 1 · M 8**(복원 목록과 같다) |
| ③ | 복원 경로의 `git diff <새 원천>` | **빈 출력**(0 줄) |
| ④ | 되돌린 트리의 compile | **통과** — ⑥ 의 전건이 포함한다 |
| ⑤ | 되돌린 트리의 test | **통과** — 같은 run |
| ⑥ | 되돌린 트리의 게이트 — `check --continue` | **exit 0** · 433 task 전수 실행(`UP-TO-DATE` 0 · cache 0) |
| ⑦ | **내 표지 0 · 남의 표지 보존** | 아래 계수 |

### ⑦ 표지 계수 — 종료 코드가 아니라 **수**로 판정한다

되돌린 트리에서 센다. 공유 파일이 있는 되돌림은 「exit 0」이 완료를 뜻하지 않는다.

| 표지 | 어디 | 수 | 뜻 |
|---|---|---|---|
| `PR #195` · `M15/15H` · `m15/15h` | `capability-map.md` | **0 · 0 · 0** | 이 slice 의 줄이 남지 않았다 |
| `EvaluationCommitStopLineTest` | `config/quality/gate-tests.properties` | **0** | 등록부 줄이 걷혔다 |
| 「한 칸씩」 | `docs/runbook/m6-6e-operations.md` | **0** | 처방 문면이 옛 식으로 돌아갔다 |
| `PR #196` · `M15/15I` · `m15/15i` | `capability-map.md` | **2 · 1 · 2** | **남의 줄이 보존됐다** — 착수 base 에서 복원했다면 0 이 됐을 자리다 |

**정직하게 적는 한 가지**: `L-r1-1`·`new Set` 계수가 **0** 이다 — ⓕ 서술은 이 slice 의 **병합 해소**가
그 행에 적은 것이라 복원과 함께 사라진다. ⓕ 는 15I 의 리뷰가 낳은 사실이므로, 이 slice 를 되돌리는
사람은 **복원 뒤 그 행에 ⓕ 를 다시 적어야 한다**(사실 자체는 PR #196 verifier 판정 코멘트가 든다).
`ⓕ` 글자만으로는 세지 않는다 — 그 글자는 이 문서의 다른 행 열아홉 자리에 이미 있어 표지가 못 된다.

되돌린 트리의 `git write-tree` 는 `0ab2f1bc51fb450505795f693de3ffd07aa58bfc` 다. **앞 라운드의
트리(`970be49d…`)와 다르다** — 15I 가 들어왔기 때문이고, 그래서 트리 동일성 갈음을 쓰지 않고
④⑤⑥ 을 **다시 실행했다**.
