# M15 / 15Q — rollback

**실측 HEAD: `011ffcfe5`**(이 slice 의 마지막 산출물 커밋 — commands 의 unit 실측 커밋). 이 파일을 담는
커밋은 그 뒤에 온다. **앞 두 라운드의 실측을 옮기지 않고 그때마다 다시 돌렸다** — 자기 검토가
`tools/prod-notification-relay.sh` 를(pipefail·SIGPIPE 꼴), 그 뒤 unit 실측이 `commands.md` 를 또
움직였고, 둘 다 아래 복원 목록 **안**의 파일이다. 「측정 뒤 복원 목록 안 파일을 또 커밋하면
재측정이다」를 그대로 따랐다(세 번 쟀고, 트리 해시는 세 번 다 같았다).

## 전제 — 복원 원천은 착수 base 그대로다

브랜치를 `origin/main` 의 `71b1ad3a7`(PR #201 머지 커밋)에서 끊은 뒤 **main 을 들이지 않았다** — 그래서
복원 원천은 착수 base 와 같고, 「복원 원천을 바꾸면 재는 셋째 단언」은 해당이 없다. **머지를 들이면
이 문서를 다시 재야 한다**(그때 복원 원천은 병합한 main 의 **고정 SHA** 이고, 움직이는 `origin/main`
이 아니다).

- `71b1ad3a7` 에 이 slice 의 커밋이 없다 — 신설 넷은 거기에 없고, 고친 여섯은 이 slice 의 커밋에서만
  바뀐다.
- 머지 뒤의 되돌림은 이 문서가 아니라 **그 PR 의 머지 커밋 revert** 다.

**운영 host 에는 되돌릴 것이 없다** — 이 slice 는 `/etc`·systemd·공유 잠금 자리를 건드리지 않았고,
타이머를 하나도 켜지 않았다. 운영자가 §13.4 를 이미 밟았다면 되돌림은 저장소가 아니라 그 host 에서
한다: 타이머 둘을 끄고(`systemctl disable --now bidvector-evaluation-commit.timer
bidvector-notification-relay.timer`) unit·drop-in 여섯을 지운 뒤 `daemon-reload`. 타이머만 꺼도
**행은 `outbox` 에 `PENDING` 으로 남는다**(§13.5).

## 목록과 명령

`git diff --name-status 71b1ad3a7..<실측 HEAD>` — **M 여섯 · A 다섯**(목록은 기계 산출이고 라운드마다
다시 낸다. 앞 라운드의 「A 넷」은 이 문서가 아직 커밋되기 전의 수였다).

```
git restore --source=71b1ad3a7 --staged --worktree -- \
  docs/discovery/capability-map.md docs/runbook/m6-6e-operations.md \
  docs/runbook/m7-7f-production.md milestone-15.md \
  tools/prod-env.sh tools/prod-guards-check.sh
git rm -rq reports/evidence/m15/15q tools/prod-evaluation-commit.sh tools/prod-notification-relay.sh
```

신설 다섯(`tools/prod-notification-relay.sh` · `tools/prod-evaluation-commit.sh` ·
`reports/evidence/m15/15q/` 의 셋)은 base 에 **없다** — 복원이 아니라 삭제다.

## 임시 clone 실측(실측 HEAD 에서)

버릴 clone 에서 ①~⑥ 을 쟀다. 저장소 트리는 건드리지 않았다.

- ① 명령 exit: `restore` **0** · `rm` **0**
- ② 스테이징된 변경: **D 5 · M 6**(위 목록과 같은 수다)
- ③ `git diff --cached 71b1ad3a7` **빈 출력** — 복원 뒤 트리가 base 와 같다
- 트리 동일성을 해시로도 쟀다: 복원 뒤 `git write-tree` == `71b1ad3a7^{tree}` ==
  **`c78d93dd51448aee4ca928be692e5405c64e5839`**
- **양성 대조**(비파괴 절삭 — 버릴 clone 에서만, `checkout --` 를 쓰지 않았다): 복원 뒤
  `docs/discovery/capability-map.md` 에 한 줄을 더하자 등식이 깨졌다(`M docs/discovery/
  capability-map.md` · 트리 **`690a02e779f51f7c4930514ff284eda59c52cf4b`**). 빈 등식이 건전성의 증거가
  아님을 그 한 줄로 가른다.
- ④ compile · ⑤ test · ⑥ 게이트는 **트리 동일성으로 갈음**한다 — 복원 뒤 트리가 base 의 트리와 **같은
  객체**이고(위 해시 등식), `71b1ad3a7` 은 PR #201 머지 시점에 CI 가 초록이었다. 「HEAD 가 초록이다」로
  갈음하지 않았다.

## 유효성 술어

```
git diff --name-only <실측 HEAD>..<판정 SHA> -- \
  docs/discovery/capability-map.md docs/runbook/m6-6e-operations.md \
  docs/runbook/m7-7f-production.md milestone-15.md \
  tools/prod-env.sh tools/prod-guards-check.sh \
  tools/prod-evaluation-commit.sh tools/prod-notification-relay.sh \
  reports/evidence/m15/15q/
git rev-list --merges <실측 HEAD>..<판정 SHA>
```

둘 다 빈 출력이면 유효하다. 첫 술어에서 **나온 경로가 전부 원천(`71b1ad3a7`)에 없는 신설 파일이면
예외로 유효하다** — 이 파일(`reports/evidence/m15/15q/rollback.md`)이 그 경우다. 열거와 원천 부재
확인은 verifier 가 한다.
