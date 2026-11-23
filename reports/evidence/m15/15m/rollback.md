# M15 / 15M — rollback

**실측 HEAD: `010502e2e93eaa2a47a56a3bf1ae398c86bcdc60`**(이 slice 의 마지막 산출물 커밋 — scope·commands).

## 전제

복원 원천은 착수 base `a189e8c58c8a438e2685c8daae8f83b835b925ba`(main, PR #199 머지 커밋 — 고정 SHA).
브랜치를 그 SHA 에서 끊었으므로 base 에 이 slice 의 첫 산출물 커밋이 없고
`git rev-list --merges <base>..<실측 HEAD>` 는 빈 출력이다(실측 0). 공유 파일
(`.github/workflows/ci.yml`·`docs/runbook/m7-7f-production.md`·`milestone-15.md`)의 `<base>..HEAD` 이력은
이 slice 의 커밋뿐이라 hunk 격리 자리가 아니다. **머지 뒤의 되돌림은 이 문서가 아니라 그 PR 의 머지 커밋
revert** 다.

운영 host 에는 되돌릴 것이 없다 — 이 slice 는 `/etc`·systemd 를 건드리지 않았다. 운영자가 §9.5 를 이미
깔았다면 drop-in 한 줄을 지우고 `daemon-reload` 하면 경보가 선다(그 절의 「끄는 법」).

## 목록과 명령

`git diff --name-status <base>..<실측 HEAD>` — M 셋 · A 넷. 기계 산출 목록과 아래 복원 목록의
`comm -23`(기계 산출 ∖ 복원 목록)은 **빈 출력**이다(실측).

```
git restore --source=a189e8c58c8a438e2685c8daae8f83b835b925ba --staged --worktree -- \
  .github/workflows/ci.yml docs/runbook/m7-7f-production.md milestone-15.md
git rm -rq reports/evidence/m15/15m tools/prod-alert-check.sh tools/prod-alert-telegram.sh
```

## 임시 clone 실측(실측 HEAD 에서)

- 명령 exit: restore 0 · rm 0 · 스테이징된 변경 **M 3 · D 4**
- `git diff --cached <base>` **빈 출력** — 복원 뒤 트리가 base 와 같다.
- 트리 동일성을 해시로도 쟀다: 복원 뒤 `git write-tree` == `<base>^{tree}` == `ef782ade29c8…`.
- **양성 대조**(비파괴 절삭 — 버릴 clone 에서만): 복원 뒤 `milestone-15.md` 에 한 줄을 더하자 등식이
  깨졌다(`M milestone-15.md` · 트리 `d92d1c8e…`). 빈 등식이 건전성의 증거가 아님을 그 한 줄로 가른다.
- ④ compile · ⑤ test · ⑥ 게이트는 **트리 동일성으로 갈음**한다(복원 뒤 트리가 base 의 트리와 같은 객체다 —
  base 는 PR #199 머지 시점에 CI 다섯 job 초록).

## 유효성 술어

`git diff --name-only <실측 HEAD>..<판정 SHA> -- .github/workflows/ci.yml docs/runbook/m7-7f-production.md
milestone-15.md tools/prod-alert-check.sh tools/prod-alert-telegram.sh reports/evidence/m15/15m/`
가 빈 출력이고 `git rev-list --merges <실측 HEAD>..<판정 SHA>` 가 빈 출력이면 유효하다. 나온 경로가 전부
원천에 없는 신설 파일이면 예외로 유효하다.
