# M15 / 15M — rollback

**실측 HEAD: `62fb3569461bf937b4e1ce6c1e0fba1884c75f57`**(이 slice 의 마지막 산출물 커밋 — 수정 라운드 6
의 등재 커밋). 앞 라운드의 실측을 옮기지 않고 다시 돌렸다.

## 전제 — 복원 원천이 바뀌었다

PR 머지가 「head branch is not up to date」로 막혀 `origin/main`(PR #200 머지 커밋 `8cc202195`,
M15/15L)을 **머지 커밋 `459e2f7f2` 로** 들였다. 공유 파일을 그 뒤에 만졌으므로 **복원 원천은 착수
base 가 아니라 병합한 main SHA `8cc202195d6ad7b3678d186d6aec029859d26f0c`** 다(고정 SHA — 움직이는
`origin/main` 이 아니다).

- **그 SHA 에 이 slice 의 커밋이 없다**: `git log <착수 base>..8cc202195` 는 15L 의 커밋 열일곱 개와
  그 머지 커밋뿐이고 15M 의 것은 하나도 없다(실측).
- **셋째 단언**(복원 원천을 바꾸면 재는 것) — 복원 대상 blob 이 착수 base 와 새 원천에서 같은가:
  **같음 6 · 다름 2**. 다른 둘은 `docs/discovery/capability-map.md`(base `2a4eb87e` ≠ 새 원천
  `d4106b7e`)와 `milestone-15.md`(`078657fc` ≠ `53a277da`)이고, 둘 다 **15L 이 만진 파일**이다.
  그래서 아래 복원은 **새 원천 기준**으로 다시 셌다 — 착수 base 에서 복원하면 15L 의 두 줄이 사라진다.
- 신설 둘(`tools/prod-alert-*.sh`)은 base 와 새 원천 **양쪽 모두에 없다** — 복원이 아니라 삭제다.
- 머지 뒤의 되돌림은 이 문서가 아니라 **그 PR 의 머지 커밋 revert** 다.

운영 host 에는 되돌릴 것이 없다 — 이 slice 는 `/etc`·systemd·공유 잠금 자리를 건드리지 않았다.
운영자가 §9.5 를 이미 깔았다면 `20-alert.conf` 를 지우고 `daemon-reload` 하면 경보가 선다(그 절의
「끄는 법」). 되돌림이 러너의 `exit 4` 도 함께 지우므로 그때는 `10-defer.conf` 도 같이 걷는다 — 남겨도
해는 없다(없는 코드를 성공으로 보는 것뿐이다).

## 목록과 명령

`git diff --name-status <새 원천>..<실측 HEAD>` — M 여덟 · A 다섯.

```
git restore --source=8cc202195d6ad7b3678d186d6aec029859d26f0c --staged --worktree -- \
  .github/workflows/ci.yml docs/discovery/capability-map.md \
  docs/runbook/m7-7c3b-notification-sweep.md docs/runbook/m7-7f-production.md milestone-15.md \
  tools/prod-env.sh tools/prod-guards-check.sh tools/prod-notification-sweep.sh
git rm -rq reports/evidence/m15/15m tools/prod-alert-check.sh tools/prod-alert-telegram.sh
```

## 임시 clone 실측(실측 HEAD 에서)

- 명령 exit: restore 0 · rm 0 · 스테이징된 변경 **M 8 · D 5**
- `git diff --cached <새 원천>` **빈 출력** — 복원 뒤 트리가 새 원천과 같다.
- 트리 동일성을 해시로도 쟀다: 복원 뒤 `git write-tree` == `<새 원천>^{tree}` == `517494f721…`.
- **양성 대조**(비파괴 절삭 — 버릴 clone 에서만): 복원 뒤 `docs/discovery/capability-map.md` 에 한 줄을
  더하자 등식이 깨졌다(`M docs/discovery/capability-map.md` · 트리 `09fac3c6…`). 빈 등식이 건전성의
  증거가 아님을 그 한 줄로 가른다.
- ④ compile · ⑤ test · ⑥ 게이트는 **트리 동일성으로 갈음**한다(복원 뒤 트리가 새 원천의 트리와 같은
  객체다 — `8cc202195` 는 PR #200 머지 시점에 CI 다섯 job 초록).

## 유효성 술어

`git diff --name-only <실측 HEAD>..<판정 SHA> -- .github/workflows/ci.yml docs/discovery/capability-map.md
docs/runbook/m7-7c3b-notification-sweep.md docs/runbook/m7-7f-production.md milestone-15.md
tools/prod-env.sh tools/prod-guards-check.sh tools/prod-notification-sweep.sh
tools/prod-alert-check.sh tools/prod-alert-telegram.sh reports/evidence/m15/15m/`
가 빈 출력이고 `git rev-list --merges <실측 HEAD>..<판정 SHA>` 가 빈 출력이면 유효하다. 나온 경로가 전부
원천에 없는 신설 파일이면 예외로 유효하다.

**머지 커밋은 실측 HEAD 보다 앞에 있다**(`459e2f7f2` → `62fb35694`). 그래서 위 `--merges` 술어가 보는
구간(실측 HEAD 이후)에는 머지가 없다 — 「main 병합이 마지막 산출물 커밋 뒤에 오면 실측 자리는 그 병합
커밋」이라는 규율의 반대 경우이고, 실측 자리는 규율대로 **마지막 산출물 커밋**이다.
