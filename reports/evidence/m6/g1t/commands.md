# M6/G1-T commands — 실행 명령과 실측

출력 전문을 붙이지 않는다 — 핵심 결과는 한 줄이고 감사자는 명령을 다시 돌린다. 라운드 이력 절을 두지
않는다. **마지막 HEAD 의 게이트 결과 정본은 verifier 와 PR 조치 코멘트**다 — 이 표는 그 직전(마지막
산출물 커밋 `4bf953d1`)까지를 담는다.

변이 실측의 바꿔치기·판정은 `checklist.md` §2 의 표에 둔다 — 같은 사실을 두 자리에 적지 않는다.

**호스트 규율** — 레인 넷이 병렬이라 모든 Gradle·Testcontainers 명령을 호스트 전역 잠금
(`flock ~/.local/bid-vector-build.lock …`)으로 감쌌다. 점검 셋(도는 빌드 · 가용 메모리 · RSS 상위)은
별도 호출로 먼저 보고, 무거운 두 실행은 **잠금을 획득한 뒤 하한을 다시** 쟀다(대기 중에는 앞 레인의
빌드가 메모리를 쥐고 있어 시작 전 수치가 무의미하다). 00:30~03:00 창 밖에서만 돌렸다.

## acceptance — `check` job 의 Kotlin 명령 그대로 · 산출물 HEAD `4bf953d1`(수정 라운드 1)

| cmd | exit | 핵심 결과 |
|---|---|---|
| `./gradlew --no-daemon check` | **0** | BUILD SUCCESSFUL 9분 02초 · 492 task — 컴파일·ktlint·detekt·CPD·sizeGate·typeShape·testShape·게이트 등재/실행·architecture·contract·누출·compatibilitySmoke + test 전건. 신설 넷의 XML: T-1 2/0 · T-2 1/0 · T-3 1/0 · T-4 2/0(tests/failures) |
| `./gradlew --no-daemon qualityBaseline` | **0** | 측정 task(게이트가 아니다) — 리포트 생성 |
| 잠금 안 하한 재측정 | — | available 16656MB · swap free 6925MB |
| 실행 전·후 HEAD | — | `4bf953d1` == `4bf953d1`(측정 창에서 트리가 움직이지 않았다) |

착수 라운드(산출물 `40dded85`)의 같은 두 명령도 exit 0 이었다(`check` 8분 59초 · 492 task). 이 표는 **수정
라운드 1 의 실측**으로 바꿔 적는다 — 같은 자리에 두 회차를 쌓지 않는다.

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

## 수정 라운드 1 — 고친 test 둘과 그 변이

| 측정 | cmd | exit | 핵심 결과 |
|---|---|---|---|
| 통제군 | 두 모듈의 `ktlintTestSourceSetCheck`·`detekt`·`sizeGate` + T-4·T-3 표적 실행 | 0 | BUILD SUCCESSFUL **40초** · T-4 2/0 · T-3 1/0 |
| M-4 강화판 | 변이 → `:adapters:test` 임대 3 클래스 | 1 | **8 중 1 실패(T-4 뿐)** · `terminated == 1` 은 통과 → RED 가 세션 종료 축에서 난다 |
| M-3 재확인 | 변이 → `:app:test` 평가 둘 | 1 | **8 중 1 실패(T-3 뿐)** |

변이는 **잠금을 쥔 뒤** 넣었다(대기 중 워크트리가 더러운 창을 없앤다) · 사본 복원 + `cmp` 바이트 동일 ·
복원 뒤 porcelain 빈 출력. 판정 표는 `checklist.md` §2-1 이 든다.

## capability-map 역방향 좌표 — 이 라운드 실측

이 range 는 `docs/discovery/capability-map.md` 를 편집했다(팀장 레인, COL-02·COL-03 두 블록 — `13 4`,
3564 → 3573 줄). 그 파일을 `파일:줄` 로 가리키는 인용을 저장소 전수로 쟀다.

| 측정 | 결과 |
|---|---|
| `grep -rn 'capability-map\(\.md\)\?:[0-9]'`(md·kt·kts·properties·yaml·py, build 제외) | **41 건** |
| 그중 **살아 있는 문서** | **1 좌표**(`fixtures/manifest.yaml` 의 `capability-map.md:164`, 같은 좌표가 두 줄에 걸쳐 인용된다) |
| 나머지 | **종결된 slice 의 evidence 39 건** |
| 첫 편집 줄(238) 아래를 가리키는 인용 | **34 건** — 전부 evidence 경로이고 **+9 밀렸다**(238~272 구간을 가리키는 인용은 0) |
| 살아 있는 인용의 영향 | **없음** — `:164` 는 편집 지점보다 **위**다 |

밀린 34 건은 **고치지 않는다**: 종결된 slice 의 기록을 되쓰지 않는다는 규율이고, 그 사실을 여기 선언으로
남긴다. 앞 라운드의 역방향 검사는 stem 셋(`acceptance-trace`·`gate-tests`·`6e1`)만 훑어 이 파일이 빠져
있었다(검토 r1 L-2) — 절차의 누락이었고 결과는 무결이다.

## 문서 등식 — 추적표 편집 뒤 6E-1 의 `E-1`~`E-25` 재실행

6E-1 `commands.md` 의 등식 블록을 그대로 추출해 돌렸다(종료 코드가 DIFF 수다).

| 측정 | 결과 |
|---|---|
| 편집 뒤(수정 라운드 1 의 COL-03 ⓒ 복귀·집계 재산출까지 반영) | OK **28** · DIFF **4** · exit 4 |
| COL-03 「부분 측정(강함)」 표식을 단 뒤 | OK **28** · DIFF **4** · exit 4 — `E-24` 의 표식 집합이 **19**(앞 18 + COL-03)로 늘고 머리 열거와 같다 |
| **base `77850903` 에서 같은 스크립트** | **같은 DIFF 넷** · exit 4 — 다른 slice 의 drift 이고 이 편집이 만든 것이 아니다 |
| 이 slice 가 거는 등식 | `E-1`(행 수) · `E-2`(식별자 ⊆ 등재 장부) · `E-3`(OPEN ID) · `E-23`(ⓐ 행의 production 칸) · `E-24`(「강함」 표식 == 머리 열거, 18) **전부 OK** |

DIFF 넷(`E-19`·`E-20a`·`E-20b`·`E-25`)은 runbook·`tools/`·compose 축이고 이 slice 의 in_scope 밖이다.
세 회차의 DIFF 출력은 **바이트로 같다**(`diff` 빈 출력) — 팀장 레인이 이 라운드에 더한 6E-1 `checklist.md` 의 후속 인용 블록과 `milestone-6.md` 문단은 어느 등식도 깨지 않는다. 그 까닭은 측정으로 확인했다: 등식 블록은 `checklist.md` 를 **입력으로 쓰지 않고**(그 이름의 매치 0), `milestone-6.md` 는 `E-3` 의 합집합 쪽에만 들어가 문단이 늘면 술어가 느슨해지는 방향이다.
집계는 등식이 재지 않으므로(6E-1 설계) 표 61 행의 표식을 **기계로 세어** 대조했다 — ⓐ 16 · ⓑ 2 · ⓒ 43
이고 축 표 여덟 행이 전부 맞는다.

## 표식 라운드 — evidence HEAD 의 누출 게이트

산출물이 문서뿐이라 전건 `check` 는 다시 돌리지 않았다(직전 산출물 `4bf953d1` 에서 exit 0 실측). 지시대로
그 라운드의 **evidence HEAD 에서 누출 게이트만** 돌렸다.

| cmd | exit | 핵심 결과 |
|---|---|---|
| `./gradlew --no-daemon leakPatternGate` @ evidence HEAD | **0** | 13초 |

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
이 range 가 편집한 **세 번째 파일**(`capability-map.md`, 팀장 레인)의 실측은 위 전용 절에 있다.
evidence 본문에도 이 slice 가 편집하는 파일의 줄 번호를 쓰지 않았다(FQN·절 제목·결정 ID 로 가리킨다).
