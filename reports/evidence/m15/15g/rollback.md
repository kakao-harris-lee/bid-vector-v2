# M15 / 15g — 되돌리기

**실측 HEAD: `68dc7a596`**(이 slice 의 **마지막 산출물 커밋** — 라운드 1 의 `sizeGate` 수정).
그 뒤 커밋은 `reports/evidence/m15/15g/` 안의 evidence 뿐이고, 그것은 아래 복원 목록 **밖**이다.

**앞 라운드의 실측을 옮기지 않았다.** 라운드 0 은 `af1d5dfe8`(캡처 커밋)에서 쟀는데, 라운드 1 이
`EvaluationCommitRunner.kt` 를 다시 건드렸다 — 그 파일은 복원 목록 **안**이라 그 측정은 낡았다.
①~⑥ 을 새 clone 에서 전부 다시 냈다.

## 원천과 범위

- 복원 원천: **착수 base `394a23544`**(origin/main, PR #192 머지 커밋). 라운드 중에 바꾸지
  않았으므로 「셋째 단언」(복원 대상 blob 이 두 원천에서 같은가)은 해당이 없다.
- range revert 가 아니라 **in_scope 경로 한정 복원**이다:
  `git restore --source=394a23544 --staged --worktree -- <아래 22 경로>`.
- **이 slice 자신의 evidence(`reports/evidence/m15/15g/`)는 목록 밖이다** — base 에 없는 신설
  파일이라 복원할 원천이 없다. 되돌린 트리에 남는 것은 그 신설 파일들뿐이고, 그것이 원천 대비
  diff 에 남는 **유일한** 항목이다(아래 ③).

## 복원 목록 — 기계 산출 22 (A 0 · M 22 · D 0)

- cmd: `git diff --name-status 394a23544..68dc7a596 -- . ':!reports/evidence/m15/15g'`
- 22 줄 전부 `M`. 신설·삭제가 없으므로 복원은 「옛 내용으로 되쓰기」 하나다.

| 묶음 | 경로 |
| --- | --- |
| Kotlin | `app/src/main/kotlin/bidvector/app/evaluation/EvaluationCommitRunner.kt` · `app/src/test/kotlin/bidvector/app/evaluation/EvaluationCommitRunnerTest.kt` |
| 문서 | `docs/runbook/m6-6e-operations.md` |
| web | `web/e2e/journey/settings.spec.ts` · `web/src/display/profileDisplay.ts` |
| 앞 slice evidence | `reports/evidence/m15/15c/{scope,commands}.md` · `reports/evidence/m15/15f/{scope,commands}.md` |
| 캡처 | `docs/usecase/captures/` 아래 **13**(PNG 12 + `manifest.json`) |

**in_scope 와의 등식**: `scope.md` 의 in_scope 는 캡처를 **디렉터리 한 항목**(`docs/usecase/captures/`)
으로 적는다. 그 하나가 13 으로 전개되고 나머지 9 는 1:1 이라 9 + 13 = **22** 로 닫힌다. 양쪽에
덧붙은 항목도 빠진 항목도 없다.

## 실측 ①~⑥ — 버릴 clone 에서

clone 은 `/home/deploy/project/bid-vector-v2` 에서 떴고(읽기만 한다 — 공유 checkout 을 체크아웃도
수정도 하지 않았다) `68dc7a596` 을 체크아웃한 뒤 위 복원을 돌렸다. 측정이 끝난 clone 은 지웠다.

| # | 무엇을 | 결과 |
| --- | --- | --- |
| ① | `git restore …` 종료 코드 | **0** |
| ② | `git status --porcelain` 의 상태 계수 | **22 M**(복원 목록과 같은 수) |
| ③ | 원천 대비 diff — 복원 경로 한정<br>`git diff 394a23544 -- . ':!reports/evidence/m15/15g'` | **0 줄**. 전체 diff 에 남은 항목은 `reports/evidence/m15/15g/` 아래 셋(`A scope.md` · `A commands.md` · `A rollback.md`)뿐이고, **전부 원천에 없는 신설 파일**이다 |
| ④ | 컴파일 — `:app:compileTestKotlin` | **BUILD SUCCESSFUL** |
| ⑤ | test — `:app:test --tests 'bidvector.app.evaluation.*'` | **40 test · 실패 0**(base 의 수다 — 이 slice 가 더한 18번째 test 가 사라진 것이 그 사실이다) |
| ⑥ | 게이트 — `leakPatternGate`(`reports/evidence/` 트리를 읽는 CI 게이트) | **BUILD SUCCESSFUL** |

④⑤⑥ 을 **갈음하지 않고 실제로 돌린 이유**: 되돌린 트리는 base 와 트리 동일이 아니다(위 신설
evidence 가 남는다). 그 신설 파일을 **입력으로 읽는 게이트가 실제로 있다** — `leakPatternGate` 의
스캔 루트가 `reports/evidence/` 다. 그래서 ⑥ 을 그 트리에서 직접 냈다.

## 표지 계수 — 「내 것 0 · 남의 것 보존」

③ 이 0 줄이므로 복원은 집합 수준에서 이미 닫혔다. 아래는 그 사실을 사람이 읽는 축으로 한 번 더 센 것이다.

**셈의 범위가 전부다**: 이 slice 의 evidence 는 복원 목록 **밖**이고 거기에는 표지 어휘가 당연히
남는다(그 파일들이 변이와 문면을 이름으로 적는다). 그래서 계수는 **복원 목록 안**에서만 센다 —
전체로 세면 「내 표지 0」이 영원히 거짓이다.

| 축 | 표지 | 복원 목록 안 | 전체(참고) |
| --- | --- | --- | --- |
| 내 표지(이 slice 가 들인 것) | `EXEMPT_FIELD_SCANNED_ATTRIBUTES` · `teardownStep` · 「인터럽트와 연속 상한이 함께 서면」 · 「거꾸로는 읽지 않는다」 · 「크기 계수」 | **다섯 다 0** | 각 1~2 — **전부 `reports/evidence/m15/15g/` 안**(파일 이름까지 확인했다) |
| 남의 표지(앞 slice) | `clearBusinessRegistrationNumber` · `STOP_CAUSE_INTERRUPTED` · `businessDetailsBodyOf` · `BUSINESS_REGISTRATION_NUMBER_TEST_ID` | **3 · 6 · 7 · 6 — 보존** | 〃 |

## 실측 HEAD 와 판정 SHA 사이

- 술어: `git diff --name-only 68dc7a596..<판정 SHA> -- <위 22 경로>` 가 **빈 출력**이어야 유효하다.
  그 사이의 커밋은 `reports/evidence/m15/15g/` 안의 evidence 뿐이라 성립한다.
- 병합 확인: `git rev-list --merges 68dc7a596..<판정 SHA>` 도 **빈 출력**이어야 한다. 이 브랜치는
  base 이후 main 을 들이지 않았다(`git rev-list --merges 394a23544..HEAD` 가 0).
- 둘 중 하나라도 어긋나면 이 측정은 낡은 것이고, 그때는 새 마지막 산출물 커밋에서 다시 낸다.
