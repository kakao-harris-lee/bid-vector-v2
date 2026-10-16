# M6/G1-T commands — 실행 명령과 실측

출력 전문을 붙이지 않는다 — 핵심 결과는 한 줄이고 감사자는 명령을 다시 돌린다. 라운드 이력 절을 두지
않는다. **마지막 HEAD 의 게이트 결과 정본은 verifier 와 PR 조치 코멘트**다 — 이 표는 그 직전(마지막
산출물 커밋 `40dded85`)까지를 담는다.

변이 실측의 바꿔치기·판정은 `checklist.md` §2 의 표에 둔다 — 같은 사실을 두 자리에 적지 않는다.

**호스트 규율** — 레인 넷이 병렬이라 모든 Gradle·Testcontainers 명령을 호스트 전역 잠금
(`flock ~/.local/bid-vector-build.lock …`)으로 감쌌다. 점검 셋(도는 빌드 · 가용 메모리 · RSS 상위)은
별도 호출로 먼저 보고, 무거운 두 실행은 **잠금을 획득한 뒤 하한을 다시** 쟀다(대기 중에는 앞 레인의
빌드가 메모리를 쥐고 있어 시작 전 수치가 무의미하다). 00:30~03:00 창 밖에서만 돌렸다.

## acceptance — `check` job 의 Kotlin 명령 그대로 · 산출물 HEAD `40dded85`

| cmd | exit | 핵심 결과 |
|---|---|---|
| `./gradlew --no-daemon check` | **0** | BUILD SUCCESSFUL 8분 59초 · 492 task — 컴파일·ktlint·detekt·CPD·sizeGate·typeShape·testShape·게이트 등재/실행·architecture·contract·누출·compatibilitySmoke + test 전건(신설 넷 포함) |
| `./gradlew --no-daemon qualityBaseline` | **0** | 측정 task(게이트가 아니다) — 리포트 생성 |
| 잠금 안 하한 재측정 | — | available 7068MB · swap free 6196MB |
| 실행 전·후 HEAD | — | `40dded85` == `40dded85`(측정 창에서 트리가 움직이지 않았다) |

`web`·`ml-engine`·`container` job 은 생략했다(scope.md acceptance 문면) — **입력 변경 0** 을 path-limited
diff 로 보였다: `git diff --name-only 77850903..HEAD -- ml-engine web docker openapi contracts .github
ml-contract` 가 **빈 출력**이다. 그 세 job 의 명령을 순차로 묶는 `check` job 의 S-20
(`tools/one-command-check.sh`)도 같은 사유로 생략하고, 그 스크립트의 Kotlin 절이 곧 위 두 명령이다.

**앞선 전건 두 회차는 판정에 넣지 않는다.** 1회차는 **실행 도중 레인이 같은 트리의 test 파일을 고쳤다**
(동결 위반 — 한 실행이 두 트리 상태를 걸치면 그 종료 코드는 어느 트리의 것도 아니다). 2회차는 동결한
트리였지만 `:app:ktlintTestSourceSetCheck` 가 접근 사슬 줄바꿈 하나로 붉었다(exit 1). 두 회차가 잡은 것은
**신설 test 의 스타일·크기**뿐이고(ktlint 둘 · 함수 50줄 한도 하나는 커밋 전에 직접 세어 잡았다),
게이트가 실제로 작동했다는 증거다. **표적 test 실행은 ktlint·detekt·sizeGate 를 task 그래프에 넣지
않는다** — 그래서 3회차 전에 그 셋을 모듈 셋에 표적으로 돌렸다(아래).

## 신설 test 넷 — GREEN 실측 (표적 실행)

| test | cmd | exit | 핵심 결과 |
|---|---|---|---|
| T-1 | `./gradlew --no-daemon :adapters:test --tests "…BaseAmountMergeGuardTest"` | 0 | 2 tests · 0 failed |
| T-2 | `./gradlew --no-daemon :workflow:test --tests "…OpeningQuotaHaltResumptionTest"` | 0 | 1 test · 0 failed |
| T-3 | `./gradlew --no-daemon :app:test --tests "…DryRunCommitParityTest"` | 0 | 1 test · 0 failed |
| T-4 | `./gradlew --no-daemon :adapters:test --tests "…LeaseHolderTerminationTest"` | 0 | 2 tests · 0 failed |
| 스타일·크기 표적 | `… :<모듈>:ktlintTestSourceSetCheck :<모듈>:detekt :<모듈>:sizeGate`(adapters·app·workflow) + T-3 | 0 | BUILD SUCCESSFUL **33초** — 전건 9분을 쓰지 않고 같은 축을 닫는다 |

변이 아래의 RED 와 그때 함께 붉은 등재 test 는 `checklist.md` §2 가 든다.

## 문서 등식 — 추적표 편집 뒤 6E-1 의 `E-1`~`E-25` 재실행

6E-1 `commands.md` 의 등식 블록을 그대로 추출해 돌렸다(종료 코드가 DIFF 수다).

| 측정 | 결과 |
|---|---|
| 편집 뒤 | OK **28** · DIFF **4** · exit 4 |
| **base `77850903` 에서 같은 스크립트** | **같은 DIFF 넷** · exit 4 — 다른 slice 의 drift 이고 이 편집이 만든 것이 아니다 |
| 이 slice 가 거는 등식 | `E-1`(행 수) · `E-2`(식별자 ⊆ 등재 장부) · `E-3`(OPEN ID) · `E-23`(ⓐ 행의 production 칸) · `E-24`(「강함」 표식 == 머리 열거, 18) **전부 OK** |

DIFF 넷(`E-19`·`E-20a`·`E-20b`·`E-25`)은 runbook·`tools/`·compose 축이고 이 slice 의 in_scope 밖이다.

## 비밀값 스캔 — 참조형

| cmd | exit | 판정 |
|---|---|---|
| `grep -rniE -f config/quality/leak-patterns.txt <in_scope 산출물 경로들> reports/evidence/m6/g1t/` | 0 | 매치 **1** — T-3 의 **test 컨테이너 자격 리터럴**이다(기존 하네스 넷이 쓰는 같은 일회용 값). 게이트(`leakPatternGate`)의 scanRoot 는 `reports/evidence` 라 그 Kotlin 파일은 게이트 입력이 아니고, 이 수동 스캔이 더 넓다 |
| 같은 명령, evidence 디렉터리만 | 1 | 매치 **0**(exit 1 = 통과). 육안 확인 — 채널 식별자·사업자 정보·실 자격증명 없음 |

## clean-tree 게이트 (in_scope 개별 인자)

| cmd | 결과 |
|---|---|
| `git status --porcelain -- <in_scope 경로 개별 인자>` | **빈 출력** |
| 양성 대조 — 신설 test 하나에 한 줄 심기 | ` M adapters/…/LeaseHolderTerminationTest.kt` 로 잡힘 |
| 되돌림 | **사본 복원**(`checkout --` 금지) · `cmp` 바이트 동일 · 재확인 빈 출력 |

## 낡는 좌표 역방향

편집한 두 파일(등재 장부·추적표)을 가리키는 `파일:줄` 인용을 저장소 전체에서 찾았다 —
`acceptance-trace`·`gate-tests`·`6e1` 세 stem 으로 `*.md`·`*.kt`·`*.properties`·`*.kts` 를 훑어 **0 건**.
evidence 본문에도 이 slice 가 편집하는 파일의 줄 번호를 쓰지 않았다(FQN·절 제목·결정 ID 로 가리킨다).
