# M7/7F-3 — rollback

**전제**: 이 slice 는 아직 main 에 **없다**(머지 전). 머지 뒤의 되돌림은 이 문서가 아니라 그 PR 의 revert 다.
복원원은 base **`78b7f384`** 고정. 이 범위에 **병합 0** · 이 slice 가 만진 여섯 파일을 만진 커밋은 **전부 이
slice 의 것**이다(`git log --oneline 78b7f384..HEAD -- <파일>`) — 남의 줄이 섞인 공유 파일이 없으므로
hunk 격리가 필요 없다.

**새 파일 0** — 산출물 여섯은 전부 수정(`M`)이다. 그래서 별도 `git rm` 도 없다.

## 복원 명령 (in_scope 경로 한정 · 경로는 개별 인자)

```bash
git restore --source=78b7f384 --staged --worktree -- \
  docs/runbook/m7-7f-production.md \
  tools/prod-deploy.sh \
  tools/prod-env.sh \
  tools/prod-notification-sweep.sh \
  tools/prod-release.sh \
  tools/prod-rollback.sh
```

목록은 기계 산출이다(`git diff --name-only 78b7f384..<실측 HEAD>` 에서 `reports/evidence/m7/7f3/` 를 뺀 것).
`comm -23` · `comm -13` **양방향 빈 출력**으로 위 목록과 같음을 확인했다. evidence 디렉터리는 장부층이라
이 목록 밖이다 — 함께 지우려면 `git rm -r reports/evidence/m7/7f3`(목록이 자기를 담은 커밋을 가리키지
않게 하는 분리이기도 하다).

**하네스 경로**(`CLAUDE.md` · `.claude/**`)는 되돌리지 않는다 — 이 범위에 하네스 레인 커밋 **0**.

## 되돌린 트리에서 무엇이 꺼지는가

고침 여섯이 함께 base 상태로 간다: 프로젝트 잠금이 다시 `${TMPDIR:-/tmp}` 로 · 주기 러너가 manifest 이미지
ID 대조와 `--pull never` 와 잠금을 잃고 · 반입의 「app DB == 가드 DB」가 다시 URL 마지막 조각 비교로 ·
되돌림의 `--expect-running` 이 사라진다. 운영 반입을 아직 하지 않았으므로 **운영 상태는 바뀌지 않는다** —
되돌림은 저장소 파일만 움직인다. 예상 복구 시간은 명령 하나(초 단위).

## 실측 (버릴 clone, `rb-clone`)

**실측 HEAD: `b5913b3d`** — 이 slice 의 **마지막 산출물 커밋**(그 뒤 커밋은 evidence 뿐이고, evidence 는 위
목록 밖이다).

| # | 확인 | 결과 |
|---|---|---|
| ① | 복원 명령 | exit **0** |
| ② | `git status --porcelain -- <여섯>` | **`M` 6**(`D`·`A` 0 — 새 파일이 없다) |
| ③ | `git diff 78b7f384 -- <여섯>` | **0 줄** |
| ④ | 컴파일 축 | `78b7f384..b5913b3d` 의 컴파일 대상 변경 **0 파일**(`*.kt`·`*.kts`·`*.ts`·`*.tsx`·`*.py`) — 되돌린 트리의 컴파일 입력이 base 와 **같다** |
| ⑤ | test 축 | 같은 이유로 base 와 같다 |
| ⑥ | 이 slice 가 닿은 게이트 | S-27 **0** · S-27b **0** · S-27c **0** · `leakPatternGate` **0** |
| — | 게이트 실행 뒤에도 트리가 되돌린 상태 그대로 | `M` 6 (변하지 않았다) |
| — | 복원원에 이 slice 가 없는가 | 첫 산출물 커밋 `ce7dd9f7` 이 `78b7f384` 의 조상이 **아니다** |

## 유효성 확인 (감사자용)

```bash
git diff --name-only b5913b3d..<판정 SHA> -- \
  docs/runbook/m7-7f-production.md tools/prod-deploy.sh tools/prod-env.sh \
  tools/prod-notification-sweep.sh tools/prod-release.sh tools/prod-rollback.sh
```

빈 출력이면 실측 뒤 되돌림 대상이 움직이지 않았다는 뜻이고 이 실측이 유효하다.
