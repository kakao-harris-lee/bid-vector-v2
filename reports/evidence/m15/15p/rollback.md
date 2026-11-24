# M15 / 15P — rollback

**실측 HEAD: `36c78be6ae763cde97a990d601cc32df27323e5c`**(이 slice 의 마지막 산출물 커밋 — evidence
압축). 앞 라운드의 실측을 옮기지 않고 다시 돌렸다. main 병합 커밋(`58172b62e`)은 그보다 **앞**에 있어
실측 자리는 규율대로 마지막 산출물 커밋이다. 이 문서를 담는 커밋은 그 뒤에 온다.

## 복원 원천 — 바뀌었다

PR 머지를 앞두고 `origin/main`(PR #203 머지 커밋 `6a6962049`, M15/15O)을 **머지 커밋으로** 들였다
(rebase 하지 않는다 — 판정 SHA 들의 이력을 그대로 둔다). 공유 파일을 그 뒤에 만지지는 않았지만 **main
쪽이 공유 파일 둘을 만졌으므로**, 복원 원천은 착수 base 가 아니라 **병합한 main SHA
`6a69620491729e79544ffcda4d7cc7b23d662a93`** 다(고정 SHA — 움직이는 `origin/main` 이 아니다).

- **그 SHA 에 이 slice 의 커밋이 없다**: `git log <착수 base>..6a6962049` 는 커밋 열둘이고 그중 15P 의
  것은 **0** 이다(실측).
- **셋째 단언**(복원 원천을 바꾸면 재는 것) — 복원 대상 blob 이 착수 base 와 새 원천에서 같은가:
  **같음 3 · 다름 2**. 다른 둘은 `docs/discovery/capability-map.md`(base `ad103901` ≠ 새 원천
  `6051c16a`)와 `milestone-15.md`(`ac7a16f7` ≠ `65ce45d2`)이고, 둘 다 **15O 가 만진 파일**이다.
  그래서 아래 복원은 **새 원천 기준**으로 다시 셌다 — 착수 base 에서 복원하면 15O 의 두 줄이 사라진다.
- 신설 둘(`config/quality/shell-guard-policy.properties` · `reports/evidence/m15/15p/`)은 base 와 새
  원천 **양쪽 모두에 없다** — 복원이 아니라 삭제다.
- 머지 뒤의 되돌림은 이 문서가 아니라 **그 PR 의 머지 커밋 revert** 다.

운영 host 에는 되돌릴 것이 없다 — 이 slice 는 `/etc`·systemd·공유 잠금 자리를 건드리지 않고,
`tools/prod-alert-telegram.sh` 의 **거동**도 바꾸지 않았다(머리 주석 한 문단만 바뀐다). 되돌리면
15M 판의 `shlex` 술어가 그대로 돌아오고 CI 의 `install shfmt` step 이 사라진다.

## 목록과 명령

`git diff --name-status <새 원천>..<실측 HEAD>` — M 다섯 · A 넷(기계 산출, 이 라운드에 재산출).

```
git restore --source=6a69620491729e79544ffcda4d7cc7b23d662a93 --staged --worktree -- \
  .github/workflows/ci.yml docs/discovery/capability-map.md milestone-15.md \
  tools/prod-alert-check.sh tools/prod-alert-telegram.sh
git rm -rq config/quality/shell-guard-policy.properties reports/evidence/m15/15p
```

## 임시 clone 실측(실측 HEAD 에서)

버릴 clone 에서만 돌았다.

- ① 명령 exit: `restore` **0** · `rm` **0**.
- ② 스테이징된 변경 **M 5 · D 4** — 목록과 같다.
- ③ `git diff --cached <새 원천>` **빈 출력**.
- 트리 동일성을 해시로도 쟀다: 복원 뒤 `git write-tree` == `<새 원천>^{tree}` == `67d2194256…`.
- **양성 대조**(비파괴 — 더하기만 한다. `checkout --` 를 쓰지 않는다): 복원 뒤 `milestone-15.md` 에
  한 줄을 더하자 등식이 깨졌다(`M milestone-15.md` · 트리 `111383d6…`). 빈 등식이 건전성의 증거가
  아님을 그 한 줄로 가른다.
- ④ compile · ⑤ test · ⑥ 게이트는 **트리 동일성으로 갈음**한다 — 복원 뒤 트리가 새 원천의 트리와
  **같은 객체**이고, `6a6962049` 는 PR #203 머지 시점의 main 이다. 「HEAD 초록」이 아니라 트리로 갈음한다.

## 유효성 술어

`git diff --name-only <실측 HEAD>..<판정 SHA> -- .github/workflows/ci.yml
docs/discovery/capability-map.md milestone-15.md tools/prod-alert-check.sh
tools/prod-alert-telegram.sh config/quality/shell-guard-policy.properties
reports/evidence/m15/15p/` 가 빈 출력이고 `git rev-list --merges <실측 HEAD>..<판정 SHA>` 가 빈
출력이면 유효하다. 나온 경로가 **전부 원천에 없는 신설 파일**이면 예외로 유효하다 — 열거와 원천 부재
확인은 verifier 가 한다. **main 병합 커밋은 실측 HEAD 보다 앞에 있으므로**(`58172b62e` → `36c78be6a`)
그 뒤 구간에는 머지가 없다.
