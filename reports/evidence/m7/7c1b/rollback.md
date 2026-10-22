# M7/7C①b — rollback

실측 HEAD: `f255f749` (이 slice 의 **마지막 산출물 커밋** — 추적표 커밋)

전제: 이 slice 는 아직 `main` 에 없다(브랜치 `m7-7c1b/2026-10-22`). 그래서 공유 파일도 **base `bee7c36f`**(이 브랜치를
만든 고정 SHA)에서 복원한다. 실행 전 단언 둘을 실측했다.
- base 가 실측 HEAD 의 조상이다(`git merge-base --is-ancestor bee7c36f f255f749` exit 0).
- base 가 이 slice 의 첫 커밋 `9ccf656f` 을 포함하지 않는다(같은 명령 역방향 exit 1).

**머지 뒤**의 되돌림은 이 절차가 아니라 PR 의 머지 커밋 revert 다.

## 되돌림 목록 (기계 산출)

`git diff --name-status bee7c36f..f255f749` 에서 `reports/evidence/m7/7c1b/` 를 뺀 것이 전부다 — **A 10 · M 10, 경로 20**.
복원 명령은 그 출력에서 바로 경로를 받는다(손으로 옮겨 적지 않는다). 수는 evidence 에서 이 자리에만 적는다.

- `A` 10(복원이 삭제한다): 아래 넷.
  - workflow 의 게이트 셋 공용 함수, 제안 use case, 그 test 둘(특성화 포함)
  - app 의 컨트롤러·조회기·wiring
  - app test 셋(endpoint · 계약 · 출하 조립 E2E)
- `M` 10(base 내용으로 되돌린다): 아래 다섯.
  - 평가 use case(추출 refactor)
  - app 의 오류 매핑·검색 컨트롤러(`NoticeCard` 구현), test 하네스, 의존 게이트 test(면제 수)
  - 게이트 계약 둘
  - 계약 문서, 생성 타입
  - 추적표

추출 refactor 와 특성화 test 도 이 목록 안이다(D-7C1B-1 Q4). `reports/evidence/m7/7c1b/**` 는 되돌리지 않는다.
하네스 경로는 이 range 에 커밋이 없다(`git log --oneline bee7c36f..HEAD -- CLAUDE.md .claude/` 빈 출력).

## 명령

```
mapfile -t P < <(git diff --name-only bee7c36f..f255f749 | grep -v '^reports/evidence/m7/7c1b/')
git restore --source=bee7c36f --staged --worktree -- "${P[@]}"
```

## 비활성화 경로 (되돌리지 않고 끄는 법)

`SuggestionWiring` 의 `suggestionQuery` 빈을 빼면 컨트롤러가 조회기를 요구해 기동이 실패한다. 그래서 끄려면 컨트롤러도 함께
빼야 한다. 검색·전략·dry-run 은 영향이 없다. 데이터 정리는 필요 없다(이 slice 는 아무것도 영속하지 않는다).

## ①~⑥ 실측 (임시 clone, HEAD `f255f749`)

| # | 확인 | 결과 |
|---|---|---|
| ① | 복원 명령 exit | 0 |
| ② | `git status --short` 의 D · M 수 | D 10 · M 10 |
| ②′ | 목록과 실제로 바뀐 경로의 `comm -3` | 0 줄(같은 집합) |
| ③ | `git diff --quiet bee7c36f -- <목록>` | 빈 diff(복원된 경로가 base 와 바이트까지 같다) |
| ④⑤⑥ | 되돌린 트리에서 `./gradlew --no-daemon --continue check` | exit 0 · BUILD SUCCESSFUL(compile · test · 게이트 전부, `leakPatternGate` 실행 포함) |

## 실측 유효성 대조 (verifier 용)

`git diff --name-only f255f749..<판정 SHA> -- <위 목록>` 이 빈 출력이면 이 실측이 유효하다. 그 뒤의 커밋은 evidence
(`reports/evidence/m7/7c1b/`)만 고쳐야 한다.

## 복구 시간

명령 하나(수 초) + 재기동. 영속 데이터·마이그레이션이 없어 데이터 쪽 복구는 없다.
