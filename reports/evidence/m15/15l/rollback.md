# M15 / 15L — rollback

**실측 HEAD: `3b7dcb4cf0712d883467c379889446c983029667`**(이 slice 가 복원 목록 안의 파일을 건드리는
마지막 커밋 — 이 뒤로는 이 문서 자신만 들어온다).

## 전제

복원 원천은 착수 base `a189e8c58c8a438e2685c8daae8f83b835b925ba`(main, PR #199 머지 커밋 — 고정 SHA,
움직이는 `origin/main` 이 아니다). 브랜치를 그 SHA 에서 끊었으므로 base 에 이 slice 의 첫 산출물
커밋이 없고 `git rev-list --merges <base>..<실측 HEAD>` 는 **빈 출력**이다. 공유 파일
(`capability-map.md`·`milestone-15.md`·`gate-tests.txt`·`package.json`·`package-lock.json`)의
`<base>..<실측 HEAD>` 이력은 이 slice 의 커밋 둘뿐이라 커밋 해시 hunk 격리 자리가 아니다.
**머지 뒤의 되돌림은 이 문서가 아니라 그 PR 의 머지 커밋 revert** 다.

## 목록과 명령

`git diff --name-status <base>..<실측 HEAD>` — M 여섯 · A 넷(신설 파일 둘과 evidence 디렉터리).

```
git restore --source=a189e8c58c8a438e2685c8daae8f83b835b925ba --staged --worktree -- \
  docs/discovery/capability-map.md milestone-15.md web/config/gate-tests.txt \
  web/e2e/kotlin-read-guard.spec.ts web/package-lock.json web/package.json
git rm -rq reports/evidence/m15/15l web/scripts/e2e-typecheck-coverage.test.mjs web/tsconfig.e2e.json
```

`package.json` 을 되돌리면 `typecheck` 가 다시 프로젝트 하나를 돌고 `@types/node` 선언이 사라진다 —
`package-lock.json` 을 같은 명령으로 함께 되돌리므로 `npm ci` 가 어긋나지 않는다.

## 임시 clone 실측(실측 HEAD 에서)

- 명령 exit: restore **0** · rm **0** · 스테이징된 변경 **M 6 · D 4** · 스테이징 밖·미추적 줄 0
- `git diff --cached <base>` **빈 출력**이고 `git write-tree` 가 base 의 트리 SHA 와 **같다** —
  복원 뒤 트리가 base 와 동일하다. ④ compile · ⑤ test · ⑥ 게이트는 그 **트리 동일성**으로 갈음한다
  (base 는 PR #199 머지 시점에 CI 다섯 job 초록이고, 같은 트리는 같은 판정을 낸다).

## 유효성 술어

`git diff --name-only <실측 HEAD>..<판정 SHA> -- docs/discovery/capability-map.md milestone-15.md
web/config/gate-tests.txt web/e2e/kotlin-read-guard.spec.ts web/package-lock.json web/package.json
reports/evidence/m15/15l/ web/scripts/e2e-typecheck-coverage.test.mjs web/tsconfig.e2e.json`
가 빈 출력이고 `git rev-list --merges <실측 HEAD>..<판정 SHA>` 가 빈 출력이면 유효하다. 나온 경로가
전부 원천에 없는 신설 파일이면 예외로 유효하다.
