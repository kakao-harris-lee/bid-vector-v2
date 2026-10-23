# M6/G1-SET01 rollback — in_scope 경로 한정 복원

**실측 HEAD: `61c156c3`**(이 slice 의 **마지막 산출물 커밋**, 검토 r1 · 이력 재작성 · rebase 뒤). base 는
`54e2c5e2`(계약 D-G1SET01-1). 아래 ①~⑥ 은 그 커밋을 체크아웃한 **임시 clone** 에서 2026-10-23 에 실제로 돌린
결과다. 뒤따르는 evidence 커밋은 되돌림 대상이 아니다 — verifier 의 대조 술어는
`git diff --name-only 61c156c3..<판정 SHA> -- <아래 복원 경로들>` 이 **빈 출력**인가다.

## 되돌리는 것 — 기계 산출 목록

목록은 `git diff --name-status 54e2c5e2..HEAD` 에서 내고 라운드마다 재산출한다.

| 상태 | 경로 | 처분 |
|---|---|---|
| A ×25 · D ×1 · M ×1 | `settlement/**` | **디렉터리 통째로** base 복원(신규 25 삭제(main 4 · test 6 · fixture 15) · `ModuleBoundaryAnchor.kt` 되살림 · `build.gradle.kts` 되돌림) |
| M | `config/quality/gate-tests.properties` | base 로 복원(**공유 파일** — 새 키 `gate.tests.settlement` 만 사라진다) |
| M | `docs/discovery/capability-map.md` | base 로 복원(**공유 파일** — SET-01 절 bullet · 신설 OPEN 행 둘 · 집계 줄) |
| M | `reports/evidence/m6/6e1/acceptance-trace.md` | base 로 복원(**공유 파일** — 6E-1 산출물의 SET-01 행·문단) |

**`settlement` 을 파일이 아니라 디렉터리로 든다.** 파일 목록은 자기 누락을 ③ 으로 볼 수 없다 — 비교 대상
집합이 복원 대상 집합과 같은 목록이기 때문이다. 디렉터리 pathspec 이 그 구멍을 닫고, ③-t 가 **index 트리
전체**를 base 트리와 대조해 목록 밖 누락까지 본다.

## 되돌리지 않는 것

- `reports/evidence/m6/g1set01/**` — 이 slice 의 evidence 와 팀장이 쓴 계약(`scope.md`). 그래서 복원한
  트리에 base 에 없는 문서가 남는다.
- 하네스 경로(`CLAUDE.md`·`.claude/**`) — 이 range 에 변경 **없음**.

## 명령 — 머지 전 전제

머지 **전**이므로 공유 파일 셋도 이 브랜치의 base main 커밋(고정 SHA)에서 복원한다. 머지 **뒤**의
되돌림은 이 명령이 아니라 그 PR 의 머지 커밋 revert 다(머지 뒤에 이 명령은 아무것도 지우지 않고 exit 0
이다).

```sh
git restore --source=54e2c5e2 --staged --worktree -- \
  settlement \
  config/quality/gate-tests.properties \
  docs/discovery/capability-map.md \
  reports/evidence/m6/6e1/acceptance-trace.md
```

경로는 개별 인자로 넘긴다. hunk 역적용은 쓰지 않는다 — 공유 파일을 base 트리에서 통째로 가져오므로
남의 줄을 떨어뜨릴 수 없다(③-b 가 잰다). `--source` 에 없는 신규 파일은 이 명령이 지운다(②).

## 실측 — 임시 clone(`61c156c3`)에서 ①~⑥

| # | 무엇 | 결과 |
|---|---|---|
| 전제 | base 가 HEAD 의 조상 · 이 slice 의 첫 산출물 커밋(`e7537f7d`)이 base 에 없음 | 실행 전 단언 — 둘 다 **OK** |
| ① | 복원 명령 종료 코드 | **0** |
| ② | 그 명령이 낸 변경 수 | **A 1 · D 25 · M 4** |
| ③ | base 대비 복원 경로 diff | **0 줄** |
| ③-t | **트리 동일성** — evidence 디렉터리를 index 에서 뺀 트리 == base 트리 | `a5e62fa38b28` == `a5e62fa38b28` |
| ③-a | 내 표지 0 — 새 키 · 추적표·capability map 의 `M6/G1-SET01` | **0 · 0 · 0** |
| ③-a | `settlement` main `.kt` 수 · `src/test` | **1**(`ModuleBoundaryAnchor.kt`) · **없음** |
| ③-b | 남의 표지 보존 — 공유 파일 줄 수 == base | 907 == 907 · 3585 == 3585 · 181 == 181 |
| ③-c | 되돌리지 않는 것이 남는가 | evidence 넷 — 남는다 |
| ④⑤⑥ | 복원한 트리에서 전건 `check`(컴파일 · test · 게이트, `leakPatternGate` 포함) | **exit 0** · BUILD SUCCESSFUL **10분 3초** · 359 task(224 실행 · 135 캐시) · HEAD 끝 `61c156c3` |

**③-b 가 줄 수인 이유**: 「내 줄이 사라졌다」(③-a)만 재면 남의 줄까지 떨어뜨린 복원도 통과한다. 두 방향을
다 재야 「내 것만 되돌렸다」가 선다. ③-t 는 그 둘을 트리 해시 하나로 함께 잰다.

## 예상 복구 시간

명령 하나(수초) + 확인 명령 몇 개. 재빌드는 Kotlin `check` 한 번(이 호스트 실측 약 10분, 잠금 대기 제외).
