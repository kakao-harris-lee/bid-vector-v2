# M15 / 15M — rollback

**실측 HEAD: `d1f49e3704e76c0edb7cbd26810da06c7d9d8d14`**(이 slice 의 마지막 산출물 커밋 — 수정 라운드 3
evidence). 앞 라운드의 실측을 옮기지 않고 다시 돌렸다.

## 전제

복원 원천은 착수 base `a189e8c58c8a438e2685c8daae8f83b835b925ba`(main, PR #199 머지 커밋 — 고정 SHA).
브랜치를 그 SHA 에서 끊었으므로 base 에 이 slice 의 첫 산출물 커밋이 없고
`git rev-list --merges <base>..<실측 HEAD>` 는 빈 출력이다(실측 0). 공유 파일 일곱
(`.github/workflows/ci.yml`·`docs/runbook/m7-7c3b-notification-sweep.md`·`docs/runbook/m7-7f-production.md`·
`milestone-15.md`·`tools/prod-env.sh`·`tools/prod-guards-check.sh`·`tools/prod-notification-sweep.sh`)의
`<base>..HEAD` 이력은 이 slice 의 커밋뿐이라 hunk 격리 자리가 아니다. **머지 뒤의 되돌림은 이 문서가
아니라 그 PR 의 머지 커밋 revert** 다.

운영 host 에는 되돌릴 것이 없다 — 이 slice 는 `/etc`·systemd·공유 잠금 자리를 건드리지 않았다.
운영자가 §9.5 를 이미 깔았다면 `20-alert.conf` 를 지우고 `daemon-reload` 하면 경보가 선다(그 절의
「끄는 법」). 되돌림이 러너의 `exit 4` 도 함께 지우므로 그때는 `10-defer.conf` 도 같이 걷는다 — 남겨도
해는 없다(없는 코드를 성공으로 보는 것뿐이다).

## 목록과 명령

`git diff --name-status <base>..<실측 HEAD>` — M 일곱 · A 다섯. 기계 산출 목록(12 줄)과 아래 복원
목록(12 줄)의 `comm -23`(기계 산출 ∖ 복원 목록)은 **빈 출력**이다(실측).

```
git restore --source=a189e8c58c8a438e2685c8daae8f83b835b925ba --staged --worktree -- \
  .github/workflows/ci.yml docs/runbook/m7-7c3b-notification-sweep.md \
  docs/runbook/m7-7f-production.md milestone-15.md \
  tools/prod-env.sh tools/prod-guards-check.sh tools/prod-notification-sweep.sh
git rm -rq reports/evidence/m15/15m tools/prod-alert-check.sh tools/prod-alert-telegram.sh
```

## 임시 clone 실측(실측 HEAD 에서)

- 명령 exit: restore 0 · rm 0 · 스테이징된 변경 **M 7 · D 5**
- `git diff --cached <base>` **빈 출력** — 복원 뒤 트리가 base 와 같다.
- 트리 동일성을 해시로도 쟀다: 복원 뒤 `git write-tree` == `<base>^{tree}` == `ef782ade29c8…`.
- **양성 대조**(비파괴 절삭 — 버릴 clone 에서만): 복원 뒤 `tools/prod-env.sh` 에 한 줄을 더하자
  등식이 깨졌다(`M tools/prod-env.sh` · 트리 `347adeb1…`). 빈 등식이 건전성의 증거가 아님을
  그 한 줄로 가른다.
- ④ compile · ⑤ test · ⑥ 게이트는 **트리 동일성으로 갈음**한다(복원 뒤 트리가 base 의 트리와 같은
  객체다 — base 는 PR #199 머지 시점에 CI 다섯 job 초록).

## 유효성 술어

`git diff --name-only <실측 HEAD>..<판정 SHA> -- .github/workflows/ci.yml
docs/runbook/m7-7c3b-notification-sweep.md docs/runbook/m7-7f-production.md milestone-15.md
tools/prod-env.sh tools/prod-guards-check.sh tools/prod-notification-sweep.sh
tools/prod-alert-check.sh tools/prod-alert-telegram.sh reports/evidence/m15/15m/`
가 빈 출력이고 `git rev-list --merges <실측 HEAD>..<판정 SHA>` 가 빈 출력이면 유효하다. 나온 경로가 전부
원천에 없는 신설 파일이면 예외로 유효하다.
