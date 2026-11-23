# M15 / 15K — rollback

**실측 HEAD: `8b869f84e37d0aa8cd3066a9a0a2a7c66073ebb7`**(이 slice 의 마지막 산출물 커밋 — 수정 라운드 1). 앞
실측을 옮기지 않고 다시 돌렸다.

## 전제

복원 원천은 착수 base `ebcd3e493a197d13a1014fca65e668529fd7bcb9`(main, PR #198 머지 커밋 — 고정
SHA). 브랜치를 그 SHA 에서 끊었으므로 base 에 이 slice 의 첫 산출물 커밋이 없고
`git rev-list --merges <base>..<실측 HEAD>` 는 빈 출력이다. 공유 파일(`capability-map.md`·`milestone-15.md`)의 `<base>..HEAD` 이력은 이 slice 의 커밋뿐이라 hunk 격리 자리가 아니다. **머지 뒤의 되돌림은
이 문서가 아니라 그 PR 의 머지 커밋 revert** 다.

## 목록과 명령

`git diff --name-status <base>..<실측 HEAD>` — M 셋 · A 셋(A 는 evidence 디렉터리).

```
git restore --source=ebcd3e493a197d13a1014fca65e668529fd7bcb9 --staged --worktree -- \
  docs/discovery/capability-map.md milestone-15.md web/e2e/journey/touchMeasure.ts
git rm -rq reports/evidence/m15/15k
```

## 임시 clone 실측(실측 HEAD 에서)

- 명령 exit: restore 0 · rm 0 · 스테이징된 변경 M 3 · D 3
- `git diff --cached <base>` **빈 출력** — 복원 뒤 트리가 base 와 같다. ④⑤⑥ 은 트리 동일성으로
  갈음(base 는 PR #198 머지 시점에 CI 다섯 job 초록).

## 유효성 술어

`git diff --name-only <실측 HEAD>..<판정 SHA> -- docs/discovery/capability-map.md milestone-15.md web/e2e/journey/touchMeasure.ts reports/evidence/m15/15k/`
가 빈 출력이고 `git rev-list --merges <실측 HEAD>..<판정 SHA>` 가 빈 출력이면 유효하다. 나온 경로가
전부 원천에 없는 신설 파일이면 예외로 유효하다.
