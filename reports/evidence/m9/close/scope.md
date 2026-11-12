# M9 / close — 종결 기록 계약

- base: main `8062dcd7` (PR #145 머지) · 브랜치 `docs/m9-close`
- 성격: 문서 slice다. 코드·정책·게이트·스키마·화면 변경은 0이고, 외부 effect·DB 질의·새 KONEPS 호출도 0이다.
- 작성: 세션 모델 단독(CLAUDE.md 운영자 지시 — milestone 문서). 리뷰는 code-reviewer(sonnet) · verifier 병렬.
- 사용자 지시 2026-11-11: **「M9 종결 문서 진행해, 끝나면 머지까지」**. 이 지시를 종결 승인으로 읽는다. 머지 조건(리뷰 통과 · head CI green)은 그대로다.

## in_scope

- `milestone-9.md`(신설)
- `README.md` 로드맵 표의 M9 행
- `docs/discovery/capability-map.md`
  - §12 머리 집계의 재계산(51 → 54)과 그 산술 한 단락
  - §12.3 (f) 등재 다섯(evidence 넷 + PR #135 코멘트 하나)과 (b) 의 CVE-APT 둘 종결 표기 — (b) 문면은 덮지 않는다
  - 머리 54 의 행 수·결정 단위(53) 공시
- `reports/evidence/m9/close/{scope,checklist}.md`

## out_scope

- 닫힌 slice 의 evidence. 덮지 않는다.
- `.claude/**`·`CLAUDE.md`(하네스 편집은 `OPEN-ROLLBACK-PREDICATE-WORDING` 으로 넘긴다).
- 코드 전부.
- §12.3 (a)~(e) 문면. 다시 판정하지 않는다.

## acceptance

CI 워크플로 job 그대로 돈다. 문서 slice 라도 Kotlin `check` 가 `leakPatternGate` 를 포함하므로 PR CI 5 job 이 정본이다.

로컬에서는 다음 기계 대조를 돌린다.

1. 머리 집계: G 절 표의 첫 칸이 `OPEN` 이고 취소선이 아닌 행의 수 + M6 잔여·메뉴 6 = 54.
2. (f) 모집단: `reports/evidence/m9/**` 의 `OPEN-` 식별자 가운데 capability map 에 0 회인 것 → 편집 뒤에는 M9 이전 식별자 인용만 남는다.
3. milestone-9 slice 표의 PR 번호 집합 = `gh pr list --state merged` 의 #122~#145.
4. `grep -rniE -f config/quality/leak-patterns.txt` 를 이 slice 산출물에 돌려 매치 0.

## rollback

머지 전에는 이 브랜치를 버린다. 머지 뒤에는 PR revert 다.

in_scope 한정 복원은 `git restore --source=8062dcd7 --staged --worktree -- README.md docs/discovery/capability-map.md` 와 신설 파일 셋 삭제다. 문서뿐이라 ④⑤⑥ 은 Kotlin `check` 한 번으로 본다(CI).

## 하네스 레인 변경

없음.
