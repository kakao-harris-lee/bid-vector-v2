# M15 / 15L — rollback

**실측 HEAD: `36f629cd07902aa9e80583f0b1ef59c57ce4fc4c`**(수정 라운드 1 — 이 slice 가 복원 목록 안의
파일을 건드리는 마지막 커밋. 이 뒤로는 이 문서 자신만 들어온다). 앞 라운드의 실측을 옮기지 않고
다시 돌렸다.

## 전제

복원 원천은 착수 base `a189e8c58c8a438e2685c8daae8f83b835b925ba`(main, PR #199 머지 커밋 — 고정 SHA,
움직이는 `origin/main` 이 아니다). 브랜치를 그 SHA 에서 끊었으므로 base 에 이 slice 의 첫 산출물
커밋이 없고 `git rev-list --merges <base>..<실측 HEAD>` 는 **빈 출력**이다. 공유 파일
(`capability-map.md`·`milestone-15.md`·`gate-tests.txt`·`eslint.config.js`·`check-src-tree.mjs`·
`package.json`·`package-lock.json`)의 `<base>..<실측 HEAD>` 이력은 이 slice 의 커밋뿐이라 커밋 해시
hunk 격리 자리가 아니다. **머지 뒤의 되돌림은 이 문서가 아니라 그 PR 의 머지 커밋 revert** 다.

## 목록과 명령

`git diff --name-status <base>..<실측 HEAD>` — M 여덟 · A 다섯(신설 파일 둘과 evidence 셋).

```
git restore --source=a189e8c58c8a438e2685c8daae8f83b835b925ba --staged --worktree -- \
  docs/discovery/capability-map.md milestone-15.md web/config/gate-tests.txt \
  web/e2e/kotlin-read-guard.spec.ts web/eslint.config.js web/package-lock.json \
  web/package.json web/scripts/check-src-tree.mjs
git rm -rq reports/evidence/m15/15l web/scripts/e2e-typecheck-coverage.test.mjs web/tsconfig.e2e.json
```

`package.json` 을 되돌리면 `typecheck` 가 다시 프로젝트 하나를 돌고 `@types/node` 선언이 사라진다 —
`package-lock.json` 을 같은 명령으로 함께 되돌리므로 `npm ci` 가 어긋나지 않는다. `eslint.config.js` 와
`check-src-tree.mjs` 는 수정 라운드 1 이 각각 e2e 억제 주석 금지와 `types` 술어를 더한 자리이고, 같은
복원으로 base 문면으로 돌아간다.

## 임시 clone 실측(실측 HEAD 에서)

레인 전용 디렉터리에서 받은 clone 이다(공유 scratchpad 이름을 쓰지 않는다).

- 명령 exit: restore **0** · rm **0** · 스테이징된 변경 **M 8 · D 5** · 그 밖의 줄 0
- `git diff --cached <base>` **빈 출력**이고 `git write-tree` 가 base 의 트리 SHA 와 **같다** —
  복원 뒤 트리가 base 와 동일하다. ④ compile · ⑤ test · ⑥ 게이트는 그 **트리 동일성**으로 갈음한다
  (base 는 PR #199 머지 시점에 CI 다섯 job 초록이고, 같은 트리는 같은 판정을 낸다).

## 유효성 술어

`git diff --name-only <실측 HEAD>..<판정 SHA> -- docs/discovery/capability-map.md milestone-15.md
web/config/gate-tests.txt web/e2e/kotlin-read-guard.spec.ts web/eslint.config.js web/package-lock.json
web/package.json web/scripts/check-src-tree.mjs reports/evidence/m15/15l/
web/scripts/e2e-typecheck-coverage.test.mjs web/tsconfig.e2e.json`
가 빈 출력이고 `git rev-list --merges <실측 HEAD>..<판정 SHA>` 가 빈 출력이면 유효하다. 나온 경로가
전부 원천에 없는 신설 파일이면 예외로 유효하다.
