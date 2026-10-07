# M6/6D-2 — 실행 명령과 실측 (구현 레인)

base `f4b4ff91` · 브랜치 `m6-6d2/2026-10-07` · 마지막 산출물 커밋 `ea07b9a7`(승인 전 일괄).

## acceptance — CI `check` job 명령 그대로

| 명령 | 문면 | 결과 |
|---|---|---|
| `./gradlew --no-daemon check` | CI 문면 그대로 | exit 0 (1m 47s) |
| `./gradlew --no-daemon qualityBaseline` | CI 문면 그대로 | exit 0 |
| `./gradlew --no-daemon :adapters:test --tests 'bidvector.adapters.e2e.*' --rerun` | 레인이 **덧붙인** 실행 강제 | exit 0 |

위 셋은 **승인 전 일괄 뒤**(`ea07b9a7`) 재실측이다. 앞 판(`8af675c1`)에서도 같은 셋이 exit 0 이었다.

종료 코드로 읽었다 — 출력을 `grep` 해 판단하거나 커밋과 한 줄로 묶지 않았다(6D-1 교훈).
`check` 안에서 `:adapters:test` 는 FROM-CACHE·UP-TO-DATE 표시 없이 **실행**됐고(137 클래스 ·
899 test · 실패 0 · skip 4), 셋째 줄은 e2e 만 좁혀 한 번 더 강제 실행한 것이다.

**S-20(Python) 생략 사유**: Python 무변경 — Python 절반은 CI `ml-engine` job 이 정본이다(6D-1·6B-2
와 같은 처분). **container job 생략 사유**: production·docker·CI diff 0.

E2E 는 기본 `check` 안에서 돈다 — 조건 애노테이션도 `Test.filter` 제외도 쓰지 않았고, 새 test 클래스
둘은 `gate.tests.adapters` 에 등재했으며 **이 slice 가 더한 제외는 0** 이다.

**differential / golden**: N/A — test 조립 slice 라 산출 데이터가 없다(fixture 변경 0, 골든 변경 0).

## 표적 실행 — `--tests 'bidvector.adapters.e2e.*'`

| test 클래스 | tests | skipped | failures | errors |
|---|---|---|---|---|
| `PipelineRestartConvergenceE2ETest`(신설) | 6 | 0 | 0 | 0 |
| `PipelineRedeliveryE2ETest`(신설) | 2 | 0 | 0 | 0 |
| `PipelineReproducibilityE2ETest`(2 → 4) | 4 | 0 | 0 | 0 |
| `PipelineOneLineE2ETest` | 3 | 0 | 0 | 0 |
| `PipelineFailureInjectionE2ETest` | 4 | 0 | 0 | 0 |
| `PipelineContractRejectionE2ETest` | 3 | 0 | 0 | 0 |
| `PipelineLadderBoundaryE2ETest` | 1 | 0 | 0 | 0 |
| 합계 | 23 | 0 | 0 | 0 |

6D-1 의 13 에서 10 이 늘었다(신설 8 + 재현 등식 2).

## 축별 변이 — 바꿔치우고 **같은 범위**로 재실행, 저장 바이트로 복원

변이는 **더하기가 아니라 바꿔치우기**다. 측정 자리는 승인 전 일괄의 산출물 커밋 `ea07b9a7` 이고,
**아홉 모두 같은 범위**(`./gradlew --no-daemon :adapters:test --tests 'bidvector.adapters.e2e.*' --rerun`)
로 돌렸다 — 범위가 다르면 RED 목록을 서로 대조할 수 없다(verifier r1 R1-L-1). RED 목록은 JUnit XML
의 `testcase` 전수에서 뽑았고, 적용마다 `git diff --numstat` 이 한 줄 이상임을 확인하고 복원은
`git checkout -- <파일>` 로 했다(변이 전 전부 커밋 상태). 빌드 전마다 호스트 문턱을 확인하는 게이트를
구동기에 넣었다 — 미달이면 변이를 적용하지 않고 중단한다.

| 축 | 변이 **형태**(바꿔치운 것 → 넣은 것) | RED 된 test 전수 |
|---|---|---|
| ③ 고아 격리(**production**) | relay 본문의 `isolateOrphans(guard)` 호출 → `OrphanIsolation(0, leaseHeld = true)` | R-1 · R-2 · R-3 · **R-4** |
| ③ 크래시 주입 | 경계 데코레이터 본문의 `hook()` 한 줄 제거 | R-1 · R-3 · R-4 · R-5 |
| ③ 크래시 주입(R-2) | R-2 조립의 `CrashAfterDispatch(ConsumerTransactions(b)) {…}` → `ConsumerTransactions(b)` | R-2 |
| ③ 살아 있는 홀더 | 정지 hook 의 `release.await(…)` → `true` | R-5 |
| ③ 협력자 출처 | `collaboratorGraph(listOf(useCase, dispatcher, relayUseCase))` → 뿌리 둘 | seam test · 6D-1 출처 단언 |
| ② 중복 제거(**production**) | relay 의 inbox 판정에 `if (alreadyProcessed) InboxDecision.Process` 분기를 끼워 중복을 Process 로 읽게 | D-1 · **6D-1 의 같은 공고 두 번 test** |
| ⑤ revision 음성 대조 | P-2 의 `setStrategyRevision(E2E_STRATEGY_REVISION + 1)` → 같은 값 | P-2 |
| ⑤ 시딩 revision | 시딩 SQL 의 `setInt(1, E2E_STRATEGY_REVISION)` → `setInt(1, 1)`(**사용 자리**) | P-1 · P-2 |
| ⑤ 정책 버전(**production**) | 판정의 `EVALUATION_LADDER_POLICY_VERSION` → `.copy(source = …)`(**사용 자리**) | P-1 |

아홉 행 전부 RED. 복원 뒤 `git status --porcelain` 빈 출력이고 e2e 23 test 재실행 exit 0 — 잔여 0.

**R1-M-1 이 닫혔다** — 격리 no-op 변이가 이제 R-4 도 붉힌다. 앞 판의 R-4 는 「고정점」만 보아 격리가
없어도 `CLAIMED` 1 이 좌초한 채 고정점이 되는 것을 수렴으로 받았다.

**R1-L-1 정정** — inbox 판정 변이는 D-1 **하나만** 붉히지 않는다. 6D-1 의 같은 공고 두 번 test 도
같은 production 분기를 지나므로 함께 붉어진다. 앞 판 표는 그 test 를 범위에 넣고도 목록에 적지 않았다.

**두 「사용 자리」 변이가 이 표의 핵심**이다. 정책 버전도 시딩 revision 도 **정의 자리를 바꾸면
초록으로 남는다** — 단언과 생산이 같은 상수를 참조하므로 양쪽이 함께 움직인다. 등식이 그 축을 읽고
있음은 **사용 자리**를 가른 변이로만 선다.

**이 레인이 재지 않은 변이**: claim SQL 의 `WHERE state` 제거 → D-2(구조 축). verifier r1 이 실측했고
port 의 claim guard 까지 함께 빼야 D-2 의 자기 단언이 붉어진다는 것을 보였다.

## 그 밖의 게이트 실측

| 항목 | 명령 | 결과 |
|---|---|---|
| clean-tree | `git status --porcelain -- <in_scope 개별 인자>` | 빈 출력 |
| clean-tree 양성 대조 | 같은 경로에 **비파괴 절삭** 1회 후 같은 명령, 복원은 메모리에 든 저장 바이트로 | `M` 한 줄 — 게이트가 잡는다. 복원 뒤 빈 출력 · SHA-256 동일 |
| 비밀값 스캔 | `grep -rniE -f config/quality/leak-patterns.txt <변경 경로 여덟>` | exit 1(일치 0) |
| 범위 혼입 | `git diff --name-only f4b4ff91..HEAD` ∖ 덮개(`comm` 양방향, 덮개에 ADR 0005 포함) | 양쪽 빈 출력 |
| production 불변 | `git diff --name-only f4b4ff91..HEAD -- '*/src/main/*' 'db/migration/*' '.github/*' 'docker/*'` | 빈 출력 |
| 새 파일 ↔ in_scope | 수정 라운드가 만든 새 파일 셋 전부 `adapters/.../e2e/` 안 — 계약 갱신 불필요 | 대조 완료 |

clean-tree 양성 대조에서 **처음에는 `git checkout HEAD --` 로 복원해 하네스 금지 사항을 어겼다**.
그 자리는 커밋된 깨끗한 파일에 내가 더한 줄 하나뿐이어서 잃은 것은 없다. 위 표의 행은 금지를 지킨
**두 번째** 측정이다.

## 실행하지 않은 것

실 KONEPS·실 LLM·실 발송·실 ML 서버 호출 0. DB 는 Testcontainers 가 만드는 일회성 컨테이너뿐이고
개발·legacy 컨테이너에는 어떤 명령도 보내지 않았다. push·merge·배포 0.
