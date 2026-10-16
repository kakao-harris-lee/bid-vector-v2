# M6/G1-T rollback — in_scope 경로 한정 복원

**실측 HEAD: `4bf953d1`** (이 slice 의 **마지막 산출물 커밋** — 수정 라운드 1 의 추적표 재산출). 아래 ①~⑥ 은 그 커밋을
체크아웃한 **임시 clone** 에서 실제로 돌린 결과다. 뒤따르는 evidence 커밋은 되돌림 대상이 아니다
(verifier 의 대조는 「실측 HEAD == 판정 SHA」가 아니라 **그 사이에 되돌림 대상이 움직였는가**다:
`git diff --name-only 4bf953d1..<판정 SHA> -- <아래 복원 경로들>` 이 빈 출력이면 이 실측이 유효하다).

## 되돌리는 것 — 기계 산출 목록

목록은 손으로 쓰지 않는다. `git diff --name-status 77850903..HEAD` 에서 내고 **라운드마다 재산출**한다
(라운드가 파일을 더하면 이 절차를 다시 돌린다).

| 상태 | 경로 | 처분 |
|---|---|---|
| A | `adapters/src/test/kotlin/bidvector/adapters/event/LeaseHolderTerminationTest.kt` | 삭제 |
| A | `adapters/src/test/kotlin/bidvector/adapters/persistence/BaseAmountMergeGuardTest.kt` | 삭제 |
| A | `app/src/test/kotlin/bidvector/app/evaluation/DryRunCommitParityTest.kt` | 삭제 |
| A | `workflow/src/test/kotlin/bidvector/workflow/collection/OpeningQuotaHaltResumptionTest.kt` | 삭제 |
| M | `config/quality/gate-tests.properties` | base 로 복원(**공유 파일**) |
| M | `reports/evidence/m6/6e1/acceptance-trace.md` | base 로 복원(**공유 파일** — 6E-1 산출물) |

## 되돌리지 않는 것

- `reports/evidence/m6/g1t/**` — 이 slice 의 evidence. 되돌리지 않는다(관례). 그 결과 복원한 트리에는
  base 에 없는 문서가 남고, 그것이 ⑥ 의 전제다(아래).
- `docs/discovery/capability-map.md`(COL-03 절)·`reports/evidence/m6/g1t/scope.md` — **팀장 레인**이 운영자
  승인으로 쓴 계약·명세다(`git log --oneline 77850903..HEAD` 의 `91c1718c`·`3cc30d49`·`b4ca492c`). 구현
  레인의 되돌림 대상이 아니다.
- 하네스 경로(`CLAUDE.md`·`.claude/**`) — 이 range 에 변경 **없음**.

## 명령 — 머지 전 전제

이 slice 는 아직 main 에 없다. 그러므로 공유 파일 둘도 **이 브랜치의 base main 커밋에서** 복원한다
(움직이는 `origin/main` 을 쓰지 않는다). 전제 둘을 먼저 단언한다 — `77850903` 이 HEAD 의 조상이고,
그 커밋에 이 slice 의 **첫 산출물 커밋이 없다**(base 가 곧 그 main 커밋이다). 머지 **뒤**의 되돌림은
이 명령이 아니라 그 PR 의 머지 커밋 revert 다(같은 복원은 머지 순간 아무것도 지우지 않으면서 exit 0 을
낸다).

```sh
git restore --source=77850903 --staged --worktree -- \
  adapters/src/test/kotlin/bidvector/adapters/event/LeaseHolderTerminationTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/BaseAmountMergeGuardTest.kt \
  app/src/test/kotlin/bidvector/app/evaluation/DryRunCommitParityTest.kt \
  workflow/src/test/kotlin/bidvector/workflow/collection/OpeningQuotaHaltResumptionTest.kt \
  config/quality/gate-tests.properties \
  reports/evidence/m6/6e1/acceptance-trace.md
```

경로는 **개별 인자**로 넘긴다(변수 하나로 묶으면 pathspec 이 하나가 된다). hunk 역적용은 쓰지 않는다 —
공유 파일 둘을 base 트리에서 통째로 가져오므로 남의 줄을 떨어뜨릴 수 없다(그 전제를 아래 계수가 잰다).
**신규 파일에 별도 `git rm` 이 필요 없다** — `--source` 에 없는 경로는 이 명령이 지운다(실측 ②).

## 실측 — 임시 clone(`4bf953d1`)에서 ①~⑥

| # | 무엇 | 결과 |
|---|---|---|
| ① | 복원 명령 종료 코드 | **0** (실행 전 조상 단언: base 는 HEAD 의 조상) |
| ② | 그 명령이 낸 변경 수 | **D 4 · M 2** (신규 test 넷 삭제 · 공유 파일 둘 복원) |
| ③ | base 대비 in_scope diff | **0 줄**(빈 출력) |
| ③-a | **내 표지 0** — 등재 장부에서 신설 FQN 넷 | `grep -c` **0** |
| ③-b | **남의 표지 보존** — 등재 장부의 등재 줄 수 | 복원 뒤 **375** == base **375** |
| ③-c | 추적표 ⓐ 행 수 | 복원 뒤 **13** == base **13**(네 행의 판정이 되돌려졌다) |
| ③-d | 팀장 레인 보존 | `capability-map.md`·`g1t/scope.md` 가 base 와 **다른 채로 남는다**(되돌리지 않는다) |
| ④⑤⑥ | 복원한 트리에서 **전건 `check`** (컴파일 · test · 게이트 전부를 한 실행이 든다) | **exit 0** · BUILD SUCCESSFUL 32초 · 359 task(202 실행 · 157 캐시 적중 — 되돌린 트리의 **코드가 base 와 같아** 입력이 일치한 task 는 캐시가 답한다. 잠금 안 하한: available 14854MB · swap 6925MB) |
| ⑥-b | 그 트리에 **이 slice 의 evidence 를 얹고** `leakPatternGate` 만 다시 | **exit 0** · 13초 — 되돌리지 않는 evidence 가 base 의 누출 baseline 에서 새 매치를 만들지 않는다(그 결함 클래스가 ⑥ 을 규격에 넣은 사유다) |

⑥-b 를 따로 두는 이유: ④⑤⑥ 을 돌린 clone 은 **evidence 커밋 전** 트리라 이 slice 의 evidence 문서가
없다(`git clone` 은 커밋된 것만 가져온다). 실제 되돌림 뒤의 트리에는 그 문서가 남으므로, 그 차이로 입력이
바뀌는 게이트 하나를 그 상태에서 따로 쟀다.

## 예상 복구 시간

명령 하나(수초) + 확인 명령 넷. 재빌드가 필요하면 Kotlin `check` 한 번(이 호스트 실측 기준 수십 분,
잠금 대기 제외).
