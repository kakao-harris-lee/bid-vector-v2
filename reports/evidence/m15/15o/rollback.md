# M15 / 15O — rollback

**실측 HEAD: `fdb3d5d4170f0ef9054f27f4702ade32de9f054b`**(재검증 뒤 등재 라운드 — 이 slice 가 복원
목록 안의 파일을 건드리는 마지막 커밋). 이 뒤로는 **이 문서 자신만** 들어온다. 앞 라운드의 실측을
옮기지 않고 다시 돌렸다 — **복원 목록은 그대로**이지만(등재 라운드가 만진 것은 이미 목록 안인
evidence 하나다) 규율은 「목록 안 파일을 또 커밋하면 재측정」이다. 수정 라운드 1 에서는 목록 자체가
늘었다(`web/eslint.config.js`).

## 전제

복원 원천은 착수 base `71b1ad3a7aca2f1e6d6b2eba3384a9d1622ee3dc`(main, PR #201 머지 커밋 — 고정 SHA,
움직이는 `origin/main` 이 아니다). 브랜치를 그 SHA 에서 끊었으므로 base 에 이 slice 의 첫 산출물
커밋이 없고 `git rev-list --merges <base>..<실측 HEAD>` 는 **빈 출력**이다. 공유 파일 여섯의
`<base>..<실측 HEAD>` 이력도 이 slice 의 커밋뿐이라 커밋 해시 hunk 격리 자리가 아니다.
**머지 뒤의 되돌림은 이 문서가 아니라 그 PR 의 머지 커밋 revert** 다.

## 목록과 명령

`git diff --name-status <base>..<실측 HEAD>` 기계 산출 — M 여섯 · A 넷.

```
git restore --source=71b1ad3a7aca2f1e6d6b2eba3384a9d1622ee3dc --staged --worktree -- \
  docs/discovery/capability-map.md milestone-15.md web/config/gate-tests.txt \
  web/eslint.config.js web/scripts/check-src-tree.mjs web/scripts/e2e-typecheck-coverage.test.mjs
git rm -rq reports/evidence/m15/15o web/scripts/ban-ts-comment-reach.test.mjs
```

`gate-tests.txt` 를 되돌리면 새 범위 test 의 등재가 사라지고 그 파일도 같은 명령에서 지워지므로
W-7 등록부가 어긋나지 않는다. `eslint.config.js` 는 수정 라운드 1 이 화면 블록의 glob 을 네 확장자로
맞춘 자리이고(금지의 **내용**은 무편집), 같은 복원으로 base 문면으로 돌아간다. 두 `tsconfig` 와
`package.json` 은 이 slice 가 만지지 않아 목록에 없다.

## 임시 clone 실측(실측 HEAD 에서)

레인 전용 디렉터리에서 받은 clone 이다(공유 scratchpad 이름을 쓰지 않는다).

- 명령 exit: restore **0** · rm **0** · 스테이징된 변경 **M 6 · D 4** · 그 밖의 줄 0
- `git diff --cached <base>` **빈 출력**이고 `git write-tree` 가 base 의 트리 SHA 와 **같다**
  (`c78d93dd5…`). ④ compile · ⑤ test · ⑥ 게이트는 그 **트리 동일성**으로 갈음한다 — base 는 PR #201
  머지 시점에 CI 초록이고 같은 트리는 같은 판정을 낸다.
- **양성 대조**: `milestone-15.md` 에 한 줄을 더하면 트리가 `8d9ece79f…` 로 갈린다(등식이 항진 아님).

## 유효성 술어

`git diff --name-only <실측 HEAD>..<판정 SHA> -- docs/discovery/capability-map.md milestone-15.md
web/config/gate-tests.txt web/eslint.config.js web/scripts/check-src-tree.mjs
web/scripts/e2e-typecheck-coverage.test.mjs reports/evidence/m15/15o/
web/scripts/ban-ts-comment-reach.test.mjs` 가 빈 출력이고
`git rev-list --merges <실측 HEAD>..<판정 SHA>` 가 빈 출력이면 유효하다. 나온 경로가 **전부 원천에
없는 신설 파일**이면 예외로 유효하다(열거와 원천 부재 확인은 verifier 가 한다).
