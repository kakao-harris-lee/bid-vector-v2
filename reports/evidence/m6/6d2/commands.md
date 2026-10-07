# M6/6D-2 — 실행 명령과 실측 (구현 레인)

base `f4b4ff91` · 브랜치 `m6-6d2/2026-10-07` · 마지막 산출물 커밋 `8af675c1`.

## acceptance — CI `check` job 명령 그대로

| 명령 | 문면 | 결과 |
|---|---|---|
| `./gradlew --no-daemon check` | CI 문면 그대로 | exit 0 (1m 48s) |
| `./gradlew --no-daemon qualityBaseline` | CI 문면 그대로 | exit 0 |
| `./gradlew --no-daemon :adapters:test --tests 'bidvector.adapters.e2e.*' --rerun` | 레인이 **덧붙인** 실행 강제 | exit 0 |

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

## 축별 변이 — 바꿔치우고 표적 재실행, 저장 바이트로 복원

변이는 **더하기가 아니라 바꿔치우기**다. 각 행은 적용 직후 `git diff --numstat` 으로 적용을 확인하고,
표적 test 를 `--rerun` 으로 돌린 뒤 `git checkout -- <파일>` 로 커밋된 바이트로 복원했다(변이 전에
전부 커밋돼 있었으므로 미커밋 산출물이 함께 지워질 자리가 없다). 측정 자리는 **마지막 산출물
커밋**(`8af675c1`)이다.

| 축 | 변이 | 판정 |
|---|---|---|
| ③ 크래시 주입 | 경계 데코레이터가 hook 을 부르지 않게 한다 | RED — R-1 · R-3 · R-4 · R-5 |
| ③ 크래시 주입(R-2) | R-2 의 조립에서 `CrashAfterDispatch` 를 떼고 production 경계를 그대로 꽂는다 | RED — R-2 하나만 |
| ③ 살아 있는 홀더 | 정지 hook 의 래치 대기를 떼고 곧바로 통과시킨다 | RED — R-5 하나만 |
| ③ 협력자 출처 | `wiredCollaborators` 의 뿌리에서 relay use case 를 뺀다 | RED — 출처 단언 둘(6D-1 의 것과 이 slice 의 seam 단언) |
| ② 중복 제거(production) | relay 의 inbox 판정을 뒤집어 중복을 `Process` 로 읽게 한다 | RED — D-1 하나만 |
| ⑤ revision 음성 대조 | P-2 의 revision 올림을 같은 값으로 바꾼다 | RED — P-2 하나만 |
| ⑤ 시딩 revision | 시딩 기본값을 **1** 로 바꾼다(기본값 함정) | RED — P-1 · P-2 |
| ⑤ 정책 버전(production) | 판정이 payload 에 싣는 상수를 `copy(source = …)` 로 교체 | RED — P-1 하나만 |

**production 쪽 둘이 이 표의 핵심**이다. 「정책 버전」 변이가 RED 인 것이 B-3 (a) 가 약속한 실측이다 —
등식이 그 축을 **읽고 있다**(상수 자체를 바꾸면 양쪽이 같이 움직여 초록이므로, 바꾼 것은 판정의
**사용 자리**다). 「중복 제거」 변이가 D-1 만 붉히는 것은 그 단언이 발송 횟수가 아니라 처분 계수를
읽는다는 뜻이다.

변이를 전부 복원한 뒤 e2e 23 test 재실행 exit 0 — 잔여 0.

**이 레인이 재지 않은 변이 둘**(계약이 verifier 에 맡긴 자리): 고아 격리 호출 제거(production) → R-1 ·
claim SQL 의 `WHERE state` 제거(production) → D-2. 둘 다 구조 축이라 판정 레인이 고안·실측한다.

## 그 밖의 게이트 실측

| 항목 | 명령 | 결과 |
|---|---|---|
| clean-tree | `git status --porcelain -- <in_scope 개별 인자>` | 빈 출력 |
| clean-tree 양성 대조 | 같은 경로에 **비파괴 절삭** 1회 후 같은 명령, 복원은 메모리에 든 저장 바이트로 | `M` 한 줄 — 게이트가 잡는다. 복원 뒤 빈 출력 · SHA-256 동일 |
| 비밀값 스캔 | `grep -rniE -f config/quality/leak-patterns.txt <변경 경로 여덟>` | exit 1(일치 0) |
| 범위 혼입 | `git diff --name-only f4b4ff91..HEAD` ∖ 덮개(`comm` 양방향) | 양쪽 빈 출력 |
| production 불변 | `git diff --name-only f4b4ff91..HEAD -- '*/src/main/*' 'db/migration/*' '.github/*' 'docker/*'` | 빈 출력 |
| 새 파일 ↔ in_scope | 수정 라운드가 만든 새 파일 셋 전부 `adapters/.../e2e/` 안 — 계약 갱신 불필요 | 대조 완료 |

clean-tree 양성 대조에서 **처음에는 `git checkout HEAD --` 로 복원해 하네스 금지 사항을 어겼다**.
그 자리는 커밋된 깨끗한 파일에 내가 더한 줄 하나뿐이어서 잃은 것은 없다. 위 표의 행은 금지를 지킨
**두 번째** 측정이다.

## 실행하지 않은 것

실 KONEPS·실 LLM·실 발송·실 ML 서버 호출 0. DB 는 Testcontainers 가 만드는 일회성 컨테이너뿐이고
개발·legacy 컨테이너에는 어떤 명령도 보내지 않았다. push·merge·배포 0.
