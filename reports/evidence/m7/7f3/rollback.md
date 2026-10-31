# M7/7F-3 — rollback

**전제**: 이 slice 는 아직 main 에 **없다**(머지 전). 머지 뒤의 되돌림은 이 문서가 아니라 그 PR 의 revert 다.
복원원은 base **`78b7f384`** 고정. 이 범위에 **병합 0** · 이 slice 가 만진 일곱 파일을 만진 커밋은 **전부 이
slice 의 것**이다(`git log --oneline 78b7f384..HEAD -- <파일>`) — 남의 줄이 섞인 공유 파일이 없으므로
hunk 격리가 필요 없다.

**새 파일 0** — 산출물 일곱은 전부 수정(`M`)이다. 그래서 별도 `git rm` 도 없다. r1 수정 라운드가 새로 만진
경로는 `docker/prod.env.template` 하나이고 계약 in_scope 안이라, 목록이 여섯에서 일곱으로 늘었다.

## 복원 명령 (in_scope 경로 한정 · 경로는 개별 인자)

```bash
git restore --source=78b7f384 --staged --worktree -- \
  docker/prod.env.template \
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

고침 전부가 함께 base 상태로 간다: 프로젝트 잠금이 다시 `${TMPDIR:-/tmp}` 로 가고 자리의 소유자·권한 단언이
사라지며 · 주기 러너가 manifest 이미지 ID 대조와 `--pull never` 와 잠금과 compose 문턱을 잃고 · 반입의
「app DB == 가드 DB」가 다시 URL 마지막 조각 비교로 돌아가며 · app 설정 통로의 키 집합 등식과 JVM 옵션
허용 형태가 꺼지고 · 되돌림의 `--expect-running` 이 사라진다. runbook 쪽은 주기 unit 의 시간 상한과 잠금
문제해결 절이 함께 사라진다.

운영 반입을 아직 하지 않았으므로 **운영 상태는 바뀌지 않는다** — 되돌림은 저장소 파일만 움직인다.
예상 복구 시간은 명령 하나(초 단위). 이미 운영 host 에 배치한 뒤라면 `/etc/tmpfiles.d/bidvector-v2.conf`
와 unit 의 `TimeoutStartSec=` 은 **저장소 밖**이므로 이 명령이 걷지 않는다 — 운영자가 따로 되돌린다
(둘 다 더 안전한 쪽이라 남겨 두어도 해가 없다).

## 실측 (버릴 clone, `rb-clone`)

**실측 HEAD: `32f3257e`** — 이 slice 의 **마지막 산출물 커밋**(그 뒤 커밋은 evidence 뿐이고, evidence 는 위
목록 밖이다).

| # | 확인 | 결과 |
|---|---|---|
| — | 목록 등식 | 기계 산출 **7** · 문서 목록 **7** · `comm` 양방향 빈 출력 |
| ① | 복원 명령 | exit **0** |
| ② | `git status --porcelain -- <일곱>` | **`M` 7**(`D`·`A` 0 — 새 파일이 없다) |
| ③ | `git diff 78b7f384 -- <일곱>` | **0 줄** |
| ④ | 컴파일 축 | `78b7f384..32f3257e` 의 컴파일 대상 변경 **0 파일**(`*.kt`·`*.kts`·`*.ts`·`*.tsx`·`*.py`) — 되돌린 트리의 컴파일 입력이 base 와 **같다** |
| ⑤ | test 축 | 같은 이유로 base 와 같다 |
| ⑥ | 이 slice 가 닿은 게이트 | S-27 **0** · S-27b **0** · S-27c **0** · `leakPatternGate` **0** |
| — | 게이트 실행 뒤에도 트리가 되돌린 상태 그대로 | `M` 7 (변하지 않았다) |
| — | 복원원에 이 slice 가 없는가 | 첫 산출물 커밋 `ce7dd9f7` 이 `78b7f384` 의 조상이 **아니다** |

## 유효성 확인 (감사자용)

```bash
git diff --name-only 32f3257e..<판정 SHA> -- \
  docker/prod.env.template docs/runbook/m7-7f-production.md tools/prod-deploy.sh \
  tools/prod-env.sh tools/prod-notification-sweep.sh tools/prod-release.sh tools/prod-rollback.sh
```

빈 출력이면 실측 뒤 되돌림 대상이 움직이지 않았다는 뜻이고 이 실측이 유효하다.
