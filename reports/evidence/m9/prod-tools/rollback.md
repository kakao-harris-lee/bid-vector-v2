# M9 / prod-tools — rollback

## 전제

- **이 slice 는 main 에 없다**(머지 전). 그래서 공유 파일(`tools/prod-*.sh` · 그 runbook · `ci.yml`)을
  **base `79f3e5c4`**(이 브랜치가 분기한 main 커밋 — 고정 기재, 움직이는 `origin/main` 을 쓰지 않는다)에서
  복원해도 남의 줄을 떨어뜨릴 수 없다. 이 브랜치는 main 을 병합하지 않았고, base 가 담은 내용이 곧
  그 시점 main 의 내용이다.
- 머지 **뒤** 되돌림은 이 절차가 아니라 **그 PR 의 머지 커밋 revert** 다(같은 복원은 머지 순간 아무것도
  지우지 않으면서 exit 0 이다).
- 하네스 경로(`CLAUDE.md`·`.claude/**`)는 되돌리지 않는다 — 이 range 에 그 경로의 커밋은 없다
  (`git log --no-merges --format= --name-only 79f3e5c4..HEAD | sort -u` = in_scope 와 같다).

## 복원 목록 (기계 산출 — 라운드마다 재산출)

```bash
git diff --name-status 79f3e5c4..HEAD -- \
  tools/prod-env.sh tools/prod-deploy.sh tools/prod-rollback.sh \
  tools/prod-notification-sweep.sh tools/prod-guards-check.sh \
  docs/runbook/m7-7f-production.md .github/workflows/ci.yml
```

→ `M` 여섯(`ci.yml` · runbook · `prod-deploy.sh` · `prod-env.sh` · `prod-notification-sweep.sh` ·
`prod-rollback.sh`) · `A` 하나(`tools/prod-guards-check.sh`).

## 명령

```bash
git restore --source=79f3e5c4 --staged --worktree -- \
  tools/prod-env.sh tools/prod-deploy.sh tools/prod-rollback.sh \
  tools/prod-notification-sweep.sh tools/prod-guards-check.sh \
  docs/runbook/m7-7f-production.md .github/workflows/ci.yml
rm -rf reports/evidence/m9
```

- `--source` 에 없는 `tools/prod-guards-check.sh` 는 이 복원이 **삭제**한다(별도 `git rm` 없음).
- evidence 디렉터리는 **복원 목록 밖**이고 `rm -rf` 로 지운다 — 목록 안에 두면 evidence 커밋마다
  실측이 낡는다.
- 운영 자리에는 아무것도 깔리지 않은 상태다(이 slice 는 운영 host 에서 실행하지 않았다). 복원 뒤 운영자가
  할 일은 없다 — 단, 이 release 를 이미 반입했다면 §「반입 뒤 운영 자리 사본 갱신」과 같은 이유로 `<저장소>`
  의 트리가 `current` 의 커밋인지만 확인한다.

## 실측 (임시 clone, 2026-11-05 — 수정 라운드마다 **재실측**, 앞 라운드 실측을 옮기지 않았다)

- **실측 HEAD: `d0a92bf84afef2487ae441f4d24ca2e6d8e9bfc6`**(이 slice 의 마지막 산출물 커밋).
  뒤따르는 evidence 커밋은 위 복원 목록의 경로를 건드리지 않는다 —
  `git diff --name-only d0a92bf8..<판정 SHA> -- <복원 목록>` 이 빈 출력이면 이 실측이 유효하다.
- 복원 목록은 라운드 둘을 거쳐도 같은 일곱 경로다(기계 산출 재실행 — 수정 라운드가 만든 새 파일 0).
- ① 명령 exit: `git restore …` 0 · `rm -rf reports/evidence/m9` 0
- ② D/M 수: `D 1 · M 6`(목록과 같다)
- ③ 트리 동일성: `git diff 79f3e5c4 -- <복원 목록 + reports/evidence/m9>` = **0 줄**(복원된 트리의 그 경로가
  base 와 같다 — 「HEAD 초록」이 아니라 이 등식으로 갈음한다)
- ④ compile 갈음: 되돌린 네 셸 스크립트 `bash -n` exit 0(이 slice 에 컴파일 대상 코드가 없다)
- ⑤ 신규 파일 부재: `tools/prod-guards-check.sh` 없음
- ⑥ 게이트: 되돌린 트리에서 `./tools/prod-env-keys-check.sh` exit 0(`키 18개 등식`) ·
  `grep -c 'S-27d' .github/workflows/ci.yml` = 0(없어진 게이트를 CI 가 더 부르지 않는다). S-27b·S-27c 는
  실제 docker 가 필요해 이 레인에서 돌리지 않았다(`commands.md` 의 같은 사유).

예상 복구 시간: 1 분 미만(파일 복원뿐 — 재빌드·재기동 없음).
