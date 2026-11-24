# M15 / 15P — rollback

**실측 HEAD: `ff33bf4074889429791d486769b13a507cad0027`**(이 slice 의 마지막 산출물 커밋 — 수정 라운드 1
의 evidence 커밋). 앞 라운드의 실측을 옮기지 않고 다시 돌렸다. 이 문서를 담는 커밋은 그 뒤에 온다.

## 복원 원천

착수 base `71b1ad3a7aca2f1e6d6b2eba3384a9d1622ee3dc`(PR #201 머지 커밋, M15/15M) **그대로**다.
이 브랜치는 그 커밋에서 곧장 끊었고 **main 을 들인 적이 없다**(`git rev-list --merges <base>..<실측 HEAD>`
빈 출력) — 그래서 「복원 원천을 바꾸면 재는 셋째 단언」은 해당이 없다. 그 SHA 에 이 slice 의 커밋은
하나도 없다(브랜치의 첫 커밋이 그 자식이다). 머지 뒤의 되돌림은 이 문서가 아니라 **그 PR 의 머지 커밋
revert** 다.

운영 host 에는 되돌릴 것이 없다 — 이 slice 는 `/etc`·systemd·공유 잠금 자리를 건드리지 않고,
`tools/prod-alert-telegram.sh` 의 **거동**도 바꾸지 않았다(머리 주석 한 문단만 바뀐다). 되돌리면
15M 판의 `shlex` 술어가 그대로 돌아오고 CI 의 `install shfmt` step 이 사라진다.

## 목록과 명령

`git diff --name-status <원천>..<실측 HEAD>` — M 다섯 · A 넷(기계 산출, 이 라운드에 재산출).

```
git restore --source=71b1ad3a7aca2f1e6d6b2eba3384a9d1622ee3dc --staged --worktree -- \
  .github/workflows/ci.yml docs/discovery/capability-map.md milestone-15.md \
  tools/prod-alert-check.sh tools/prod-alert-telegram.sh
git rm -rq config/quality/shell-guard-policy.properties reports/evidence/m15/15p
```

## 임시 clone 실측(실측 HEAD 에서)

버릴 clone 에서만 돌았다.

- ① 명령 exit: `restore` **0** · `rm` **0**.
- ② 스테이징된 변경 **M 5 · D 4** — 목록과 같다.
- ③ `git diff --cached <원천>` **빈 출력**.
- 트리 동일성을 해시로도 쟀다: 복원 뒤 `git write-tree` == `<원천>^{tree}` == `c78d93dd51…`.
- **양성 대조**(비파괴 — 더하기만 한다. `checkout --` 를 쓰지 않는다): 복원 뒤
  `tools/prod-alert-check.sh` 에 한 줄을 더하자 등식이 깨졌다(`M tools/prod-alert-check.sh` ·
  트리 `82c86ba3…`). 빈 등식이 건전성의 증거가 아님을 그 한 줄로 가른다.
- ④ compile · ⑤ test · ⑥ 게이트는 **트리 동일성으로 갈음**한다 — 복원 뒤 트리가 원천의 트리와 **같은
  객체**이고, `71b1ad3a7` 은 PR #201 머지 시점에 CI 전건 초록이었다. 「HEAD 초록」이 아니라 트리로 갈음한다.

## 유효성 술어

`git diff --name-only <실측 HEAD>..<판정 SHA> -- .github/workflows/ci.yml
docs/discovery/capability-map.md milestone-15.md tools/prod-alert-check.sh
tools/prod-alert-telegram.sh config/quality/shell-guard-policy.properties
reports/evidence/m15/15p/` 가 빈 출력이고 `git rev-list --merges <실측 HEAD>..<판정 SHA>` 가 빈
출력이면 유효하다. 나온 경로가 **전부 원천에 없는 신설 파일**이면 예외로 유효하다 — 열거와 원천 부재
확인은 verifier 가 한다.
