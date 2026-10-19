# M7/7C① — rollback

실측 HEAD: `2705d8287acc99e9753299a605561fd005fe07c2` (이 slice 의 **마지막 산출물 커밋** — r3 후속 라운드 + R3-M-1 재설계 뒤)

전제: 이 slice 는 아직 `main` 에 없다(브랜치 `m7-7c1/2026-10-16`). 그래서 공유 파일도 **base
`77850903`**(이 브랜치를 만든 고정 SHA — 움직이는 `origin/main` 이 아니다)에서 복원한다. 실행 전
단언 둘을 **이 판에서 다시** 실측했다: base 가 실측 HEAD 의 조상(`git merge-base --is-ancestor`
exit 0) · base 가 이 slice 의 **첫** 산출물 커밋(`cdb2f37f`)을 포함하지 않음(같은 명령 exit 1).
**머지 뒤**의 되돌림은 이 절차가 아니라 그 PR 의 머지 커밋 revert 다 — 머지 순간 같은 복원은
아무것도 지우지 않으면서 exit 0 을 낸다.

## 되돌림 목록 (기계 산출)

`git diff --name-status 77850903..2705d828` 에서 `reports/evidence/m7/7c1/` 를 뺀 것이 전부다 —
**A 16 · M 11, 경로 27**. 목록은 요약이 아니라 그 명령의 출력 자체이고, 복원 명령은 그 출력에서
바로 경로를 받는다(손으로 옮겨 적는 자리가 없다). 복원 뒤의 상태 계수(아래 ②)를 그 목록과
다시 대조한다.

**라운드마다 다시 산출한다.** r2 판 목록은 A 15 였고 `ReadOnlyWitness.kt` 가 빠져 있었다
(verifier r3 L-3) — 목록이 낡는 것이 그 결함의 실제 원인이다.

- `A`(base 에 없음 → 복원이 삭제한다) **16**: `workflow` 의 조건 타입·파싱·포트와 그 test ·
  `adapters` 의 질의 조립·JDBC 어댑터와 그 test 넷(의존 게이트 · **읽기 전용 증인** 포함) ·
  `app` 의 컨트롤러·조회기·wiring 과 그 test 셋.
- `M`(base 내용으로 되돌린다) **11**: `adapters` 의 SQL 상수 파일, `app` 의 오류 매핑·전략 응답·
  test 하네스와 게이트 test 둘(면제 수 · 비교 허용 목록), 게이트 계약 둘(`architecture-policy`·
  `gate-tests`), 계약 문서, 생성 타입, 추적표.

`reports/evidence/m7/7c1/**` 는 되돌리지 않는다(이 slice 의 기록). 하네스 경로(`CLAUDE.md`·
`.claude/**`)는 이 range 에 커밋이 **없다**(`git log --oneline 77850903..HEAD -- CLAUDE.md .claude/`
빈 출력) — 되돌릴 것도 없다.

## 명령

```
git restore --source=77850903 --staged --worktree -- <위 27 경로를 개별 인자로>
```

`--source` 에 없는 경로는 삭제되므로 신규 파일에 `git rm` 이 따로 필요 없다. 신규 파일마다
pathspec 오류를 내는 `git checkout <base> -- <경로>` 는 쓰지 않는다. hunk 격리(커밋 해시별
역적용)도 쓰지 않는다 — 공유 파일 열하나 모두 **base 가 이 slice 를 포함하지 않는 트리**라 base
복원이 다른 slice 의 줄을 떨어뜨릴 수 없다.

## 비활성화 경로 (되돌리지 않고 끄는 법)

`NoticeSearchWiring` 의 `noticeSearchQuery` 빈을 빼면 검색 경로 전체가 사라진다 — 컨트롤러가
조회기를 요구하므로 기동이 **실패**하고(조용히 열린 채 남지 않는다) 전략 조회·편집·dry-run 은
그대로 남는다. 계약 문서에서 operation 하나를 지우고 생성 타입을 재생성하면 web 쪽 표면도 닫힌다.

잘못된 퍼센트 인코딩의 400 갈래만 끄려면 컨트롤러가 질의를 읽는 자리에서 `try`/`catch` 를
걷어낸다 — 그 입력이 다시 전역 폴백(500)으로 간다. 그때는 계약 문서의 400 문면과 생성 타입도
함께 되돌려야 문서와 거동이 갈라지지 않는다. 이 갈래는 **이 operation 하나**에만 닿으므로 끄더라도
다른 경로의 거동은 그대로다.

## ①~⑥ 실측 (임시 clone, HEAD `2705d828`)

| # | 무엇 | 결과 |
|---|---|---|
| ① | 복원 명령 종료 코드 | exit 0 (경로 27) |
| ② | 상태 계수 | `D 16 · M 11` — 기계 목록의 A 16 · M 11 과 일치 |
| ③ | base 대비 diff(되돌린 경로들) | 빈 출력 |
| ④ | 되돌린 트리의 compile | `./gradlew --no-daemon check` 안에서 모듈 **11** 의 `compileKotlin` 이 돌았다 |
| ⑤ | 되돌린 트리의 test | 같은 명령 안에서 모듈 **10** 의 `test` 가 돌았다 |
| ⑥ | 되돌린 트리의 게이트 | 같은 명령이 게이트 전건을 든다(의존·크기·계약·등재·누출·detekt·ktlint …) — 태스크 줄 443 |

**한 명령 결과**: `flock … ./gradlew --no-daemon check` **exit 0**(BUILD SUCCESSFUL in 9m 24s,
실패·오류 0). 게이트가 초록이라 보완 경로를 실행할 일이 없었다.

**남의 줄 보존 / 내 표지 0 계수**(종료 코드가 아니라 계수로 판정한다):
- 되돌린 트리에서 이 slice 의 표지(`NoticeSearch` · `NoticeSummary`)를
  담은 파일이 **0 개**다(`adapters/src`·`app/src`·`workflow/src`·`config/quality`·`openapi`·
  `web/src/api` 전수 grep).
- 의존 게이트의 면제 수 고정값이 base 값(**51**)으로 돌아갔다 — 이 slice 가 올린 53 이 사라졌다.
- 추적표의 능력 행 **61** 이 그대로다(이 slice 는 STR-16 행의 본문만 고쳤고 행을 더하지 않았다).
- ③ 이 빈 출력이라는 사실이 「남의 줄 보존」의 정확한 증명이다 — 되돌린 트리가 base 와 **같다**.

## 실측 유효성 대조 (verifier 용)

```
git diff --name-only 2705d828..<판정 SHA> -- <위 27 경로>
```

빈 출력이면 이 실측이 유효하다(evidence 커밋은 언제나 마지막 산출물 커밋 뒤에 오므로 「실측
HEAD == 판정 SHA」는 구조적으로 성립할 수 없다 — 볼 것은 **그 사이에 되돌림 대상이
움직였는가**다). 이 배치의 장부 커밋은 `reports/evidence/m7/7c1/**` 만 만지고 그 경로는 위 27
안에 없다 — 그래서 술어가 빈 출력으로 남는다.

## 복구 시간

명령 하나(수초) + 재빌드. 마이그레이션·데이터 변경이 없어 DB 되돌림이 없다.
