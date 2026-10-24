# M7/7C①b — rollback

실측 HEAD: `c5a35921` (이 slice 의 **마지막 산출물 커밋** — 승인 전 일괄 test·KDoc, 셋째 rebase 뒤)

전제: 이 slice 는 아직 `main` 에 없다(브랜치 `m7-7c1b/2026-10-22`). 공유 파일은 **병합된 main 의 고정 SHA `998b59dd`**(셋째
rebase 의 base)에서 복원한다. 실행 전 단언 둘을 실측했다.
- base 가 실측 HEAD 의 조상이다(`git merge-base --is-ancestor 998b59dd c5a35921` exit 0).
- base 가 이 slice 의 첫 커밋 `a3af4095` 을 포함하지 않는다(같은 명령 역방향 exit 1).

**머지 뒤**의 되돌림은 이 절차가 아니라 PR 의 머지 커밋 revert 다. PR 직전 rebase 로 base 가 바뀌면 이 실측은 다시 한다.

## 되돌림 목록 (기계 산출)

`git diff --name-status 998b59dd..c5a35921` 에서 `reports/evidence/m7/7c1b/` 를 뺀 것이 전부다 — **A 11 · M 10, 경로 21**.
복원 명령은 그 출력에서 바로 경로를 받는다(손으로 옮겨 적지 않는다). 수는 evidence 에서 이 자리에만 적는다.

- `A` 11(복원이 삭제한다): workflow 의 게이트 셋 공용 함수 · 제안 use case · 그 test 둘(특성화 포함) · app 의 컨트롤러 · 조회기 ·
  wiring · app test 넷(endpoint · 계약 · 출하 조립 E2E · 그 관측 장치 `SuggestionE2EProbes`).
- `M` 10(base 내용으로 되돌린다): 평가 use case(추출 refactor) · app 의 오류 매핑 · 검색 컨트롤러 · test 하네스 · 의존 게이트
  test(면제 수) · 게이트 계약 둘 · 계약 문서 · 생성 타입 · 추적표.

공유 파일(의존 게이트 test · 게이트 계약 둘 · 추적표)은 rebase 로 다른 레인의 줄이 섞인 파일이다. base 가 그 줄을 이미 담고
있으므로 복원은 **이 slice 의 줄만** 지운다 — 아래 ⑦ 이 계수로 확인한다.
하네스 경로는 이 range 에 커밋이 없다(`git log --oneline 998b59dd..c5a35921 -- CLAUDE.md .claude/` 빈 출력).

## 명령

```
mapfile -t P < <(git diff --name-only 998b59dd..c5a35921 | grep -v '^reports/evidence/m7/7c1b/')
git restore --source=998b59dd --staged --worktree -- "${P[@]}"
```

## 비활성화 경로 (되돌리지 않고 끄는 법)

`SuggestionWiring` 의 `suggestionQuery` 빈을 빼면 컨트롤러가 조회기를 요구해 기동이 실패한다. 그래서 끄려면 컨트롤러도 함께
빼야 한다. 검색·전략·dry-run 은 영향이 없다. 데이터 정리는 필요 없다(이 slice 는 아무것도 영속하지 않는다).

## ①~⑥ 실측 (임시 clone, HEAD `c5a35921`, 2026-10-24 05:20 ~ 05:44 KST)

| # | 확인 | 결과 |
|---|---|---|
| ① | 복원 명령 exit | 0 |
| ② | `git status --short` 의 D · M 수 | D 11 · M 10 |
| ②′ | 목록과 실제로 바뀐 경로의 `comm -3` | 0 줄(같은 집합) |
| ③ | `git diff --quiet 998b59dd -- <목록>` · 같은 명령 `--cached` | 둘 다 exit 0(복원된 경로가 base 와 바이트까지 같다) |
| ④⑤⑥ | 되돌린 트리에서 `./gradlew --no-daemon check --continue --no-build-cache` | exit 0 · BUILD SUCCESSFUL(compile · test · 게이트 전부, `leakPatternGate` 실행 포함) |
| ⑦ | 공유 파일 표지 계수 | 이 slice 표지(`suggestion`, 의존 게이트 test · 게이트 계약 둘) 0 · 남의 표지(의존 게이트 test 의 7C⑤-0 문단 1 · 추적표의 G1-C03 문단 1 · G1-SET01 문단 1) 보존 · 면제 수 상수는 main 의 65 |

## 실측 유효성 대조 (verifier 용)

`git diff --name-only c5a35921..<판정 SHA> -- <위 목록>` 이 빈 출력이면 이 실측이 유효하다. 그 뒤의 커밋은 evidence
(`reports/evidence/m7/7c1b/`)만 고쳐야 한다.

**PR 때 main 이 앞서 있으면**(예: #86, 팀장 update-branch 로 병합 커밋을 만든다) 이 실측을 다시 하지 않고 술어 하나로 확인한다:
`comm -12 <(git diff --name-only 998b59dd..<그 main SHA> | sort) <(git diff --name-only 998b59dd..c5a35921 | grep -v '^reports/evidence/m7/7c1b/' | sort)`
가 빈 출력이면(「main 이 바꾼 경로 ∩ 복원 경로 = ∅」) 이 목록과 복원 결과가 그대로다. 한 줄이라도 나오면 그 base 에서 다시 잰다.

## 복구 시간

명령 하나(수 초) + 재기동. 영속 데이터·마이그레이션이 없어 데이터 쪽 복구는 없다.
