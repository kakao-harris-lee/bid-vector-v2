# M9/adr07-pitest commands

base `df4976cc` · **판정 SHA `0b9ce7a0`**(둘째 병합 커밋 — 수정 라운드 3 + main `97ed49c4` 병합) · 전 실행
`~/.local/bin/bv-build.sh ./gradlew --no-daemon …`(호스트 빌드 자리 둘 규율). 아래 「결과」는
핵심 한 줄이고 출력 전문은 싣지 않는다.

**판정 SHA 문면 정정(v L-1)** — 앞 라운드에 이 머리말과 `checklist.md` 가 `f4a85991` 을 적었는데
그 뒤로 산출물 커밋이 더 왔다(capability-map 행 하나 · 수정 라운드 다섯). §1~§7 은 **첫 라운드**의 실측,
**§9 는 수정 라운드 1**(`eab07b5b`), **§10 은 첫 병합 뒤**(`d4b43248`), **§11 은 수정 라운드 2**
(처방 `07ecf5e9` · 병합 `2fad9b99`), **§12 는 수정 라운드 3**(`9fbb1caf`), **§13 은 main `97ed49c4` 병합
뒤**(`0b9ce7a0` = 현 판정 SHA)다 — 어느 수가 어느 SHA 의 것인지 절로 가른다.

## 1. 도구가 이 저장소에서 서는가 — 채택의 전제

| # | 물음 | 결과 |
| --- | --- | --- |
| T-1 | 카탈로그가 든 좌표가 실제로 해석되는가 | `gradle-pitest-plugin` 1.19.0 은 **Maven Central 에 없고** plugin portal 에만 있다(Central 의 그 디렉터리는 404) — build-logic 의 repository 둘 중 portal 이 해석한다. PIT 엔진 1.30.0 은 Central 에 있다 |
| T-2 | pitest 가 **JUnit 6** 에서 test 를 찾는가 | 찾는다. `pitest-junit5-plugin` 1.2.3 은 JUnit 의존 전부를 `provided` 로 선언해 런타임 판을 쓴다 — `engine:junit-jupiter` 로 test 를 열거하고 돌렸다 |
| T-3 | configuration cache 와 호환되는가 | 호환. 실행 끝에 `Configuration cache entry stored` |
| T-4 | 내장 Kotlin junk 필터가 이 junk 를 거르는가 | **아니다.** `KotlinFilter`(feature `FKOTLIN`, 기본 켜짐)는 `.kt` 파일의 **line 0** 변이만 거른다 — 바이트코드를 직접 읽어 확인했다. 그래서 `Intrinsics` 널 검사 제거·컬렉션 overflow 가드 제거·data class 접근자 변이가 남는다(`OPEN-ADR07-KOTLIN-JUNK`) |

## 2. 비용 실측 — 도메인 일곱 모듈, 한 모듈씩

`threads=1`, 모듈마다 `:<m>:pitest --rerun`(cache 적중을 실행으로 세지 않는다). **core** 는 PIT
자신이 적는 분석 시간이고 비교 가능한 축이다. **wall** 과 **peak** 는 같은 호스트의 다른 레인·
build-logic 재컴파일이 섞여 모듈 간 비교에 쓸 수 없다 — 상한을 가늠하는 데만 쓴다.

| 모듈 | core | wall | peak tree RSS | 변이 | 탐지 | 점수 | 변이 클래스 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| settlement | 27s | 104s | 2778MB | 73 | 61 | 83% | 8 |
| qualification | 49s | 158s | 4495MB | 109 | 84 | 77% | 18 |
| bidding | 54s | 85s | 1505MB | 146 | 117 | 80% | 20 |
| shared-kernel | 75s | 234s | 2373MB | 128 | 90 | 70% | 28 |
| strategy | 99s | 141s | 2684MB | 214 | 193 | 90% | 27 |
| decision | 154s | 244s | 5791MB | 549 | 433 | 78% | 56 |
| procurement | 226s | 275s | 1752MB | 858 | 688 | 80% | 110 |

**core 합 684s(11분 24초).** 가장 큰 모듈 하나가 3분 46초. **비용이 부분집합을 고를 이유를 내지
않았다** — 그래서 대상은 순수 도메인 모듈 전부다.

**위 「점수」는 내림이고 PIT 콘솔의 수는 반올림이다** — 같은 비율을 다르게 적는다. settlement 의
61/73 은 83.56% 이고, 표와 래칫은 **83**, PIT 콘솔은 **84** 로 적는다. 래칫 값으로 반올림을
쓰면 그 자리에서 바로 위반이 된다(61·100 = 6100 < 84·73 = 6132). 그래서 정책 값은 내림이고,
판정은 내림한 백분율끼리의 비교가 아니라 **정수 등식**(`탐지·100 ≥ 임계·전체`)으로 선다.

`TIMED_OUT` 변이 **0 개**(일곱 모듈 합계). 그래서 점수가 부하와 무관하게 재현된다 —
qualification 을 두 번(probe · convention 배선) 돌려 109/84 가 같았다.

### 2.1 Kotlin junk 의 실물과 생존자 귀속

qualification 의 생존·미복제 25 개를 분류하니 절반 가까이가 컴파일러 산물이었다 —
`Intrinsics::checkNotNull` 제거, `CollectionsKt::throwCountOverflow` 제거, 값·data class 접근자의
`EmptyObjectReturnVals`. 나머지는 실제 공백이다.

`killingTest` 귀속을 세어 **컴파일 실패 하네스 test 가 변이를 하나도 죽이지 않는 것**을 확인했다
(shared-kernel·settlement). 그 test 들은 Kotlin 컴파일러를 소스에 돌리므로 바이트코드 변이가
결과를 바꾸지 못한다. 분석 시간이 분 단위 안이라 **빼지 않았다** — 빼려면 test 이름 패턴을
정책에 적어야 하고 그 열거가 낡는 쪽 대가가 더 크다.

## 3. RED → GREEN

| # | 명령 | 결과 |
| --- | --- | --- |
| R-1 | `-p build-logic test --tests 'bidvector.buildlogic.Mutation*'` (구현 전) | **FAILED** — `Unresolved reference 'MutationReport'` 외 컴파일 오류. 판정 함수가 없으니 단언이 설 자리도 없다 |
| R-2 | 같은 명령 (구현 후) | 초록. `MutationGateChecksTest` 13 · `MutationPolicyTest` 7 · `MutationReportTest` 6 = **26**, 건너뜀 0 (JUnit XML 계수) |
| R-3 | `-p build-logic test ktlint{Main,Test}SourceSetCheck detekt` | 초록 |

## 4. `check` 와 분리됐는가 — 양쪽을 센다

| # | 명령 | 결과 |
| --- | --- | --- |
| S-1 | `check --dry-run` | task **474** 중 pitest·mutation **0** |
| S-2 | `mutationCheck --dry-run` | task **87** 중 pitest·mutation **16** |

S-1 만으로는 「명령이 아무것도 세지 않았다」와 구별되지 않는다 — S-2 가 그 통제군이다.

## 5. acceptance — 판정 SHA `f4a85991`

`mutationCheck` **exit 0**, 6분 32초. `pitest` 일곱 모듈 전부 **실행**(UP-TO-DATE 가 아니다),
`mutationCoverageGate` + `mutationReportGate` 일곱이 전부 돌았다. 게이트 리포트의 등식:
`expected == registered` = 도메인 일곱.

## 6. 양성 대조 — 값을 **바꿔치운다**

정책 파일 하나를 변이 대상으로 삼고 매 변이 뒤 되돌렸다. 산출물은 변이 전에 모두 커밋돼 있다
(`git checkout --` 가 미커밋 산출물을 함께 지우지 않게).

| # | 변이 | 결과 |
| --- | --- | --- |
| C-1 | settlement 점수 래칫 83 → 100 | exit 1 — 「점수 61/73(83%) 가 래칫 100% 밑이다」 |
| C-2 | settlement 변이 수 래칫 73 → 99999 | exit 1 — 「변이 수 73 이 래칫 99999 밑이다」 |
| C-3 | settlement 변이 클래스 래칫 8 → 99999 | exit 1 — 「변이가 닿은 클래스 수 8 가 래칫 99999 밑이다」 |
| C-4 | 정책 집합에서 settlement 를 뺀다(convention 은 그대로) | exit 1 — 「정책 밖 모듈이 pitest convention 을 적용했다: [settlement]」 |
| C-5 | 리포트 파일을 옮기고 `-x :settlement:pitest` 로 게이트만 돌린다 | exit 1 — 「mutation 리포트가 존재하지 않는다」. 이 사유가 **죽은 가지가 아님**을 여기서 실측한다 |
| C-6 | settlement 에서 convention 적용만 뺀다(정책은 그대로) | exit 1 — 「정책이 대상으로 든 모듈이 pitest convention 을 적용하지 않았다: [settlement]」 |
| C-7 | 순수 도메인 밖 모듈(`workflow`)이 convention 을 적용한다 | exit 1 — **구성 단계에서** 「순수 도메인 모듈이 아니다」로 plugin 적용이 끊긴다 |

C-4 와 C-6 가 **서로 다른 문면**을 낸다 — 등식의 두 방향이 둘 다 살아 있다는 뜻이다.

**게이트 술어를 바꾼 커밋(퍼센트 분모 상수 이름)의 표적 재검증**: settlement 점수 래칫을
83 → **84**(실측 바로 한 칸 위)로 바꿔치우니 exit 1 과 「83% 가 래칫 84% 밑이다」. 경계가 C-1
(100)보다 촘촘한 대조다.

## 7. 비밀값·누출 어휘

evidence 와 산출물에 참조형 스캔을 걸었다 — `grep -rniE -f config/quality/leak-patterns.txt`.
baseline 밖 신규 일치 없음. 패턴 어휘를 이 파일에 축어로 적지 않는다.

## 8. 되돌림

`rollback.md` 가 든다 — 실측 HEAD·복원 목록·트리 동일성·표지 계수.

## 9. 수정 라운드 1 재실측 — `eab07b5b` 시점(병합 전)

리뷰 라운드의 지적 처리는 `checklist.md` §6 의 대응표가 들고, 여기는 **수**만 적는다.

### 9.1 H-1 — RED 선행과 처방 뒤

| # | 명령 | 결과 |
| --- | --- | --- |
| R-4 | 새 회귀 test 셋, **구판**에서 | **RED** — 「리포트 악화」와 「리포트 삭제」가 `UnexpectedBuildSuccess`(게이트가 UP-TO-DATE 로 건너뛰어 초록). 음성 대조(「리포트가 그대로면 UP-TO-DATE」)만 통과 |
| R-5 | 같은 셋, 처방 뒤 | 초록 셋. 음성 대조가 **그대로 통과**한다 — `upToDateWhen { false }` 였다면 그 단언이 깨진다 |
| R-6 | `-p build-logic test ktlint{Main,Test}SourceSetCheck detekt` | 초록. mutation 관련 test **33** — `MutationGateChecksTest` 14 · `MutationPolicyTest` 10 · `MutationReportTest` 6 · `MutationReportGateUpToDateTest` 3, 실패·건너뜀 0(앞 라운드 26 에서 +7) |

회귀 test 가 **리포트만** 바꾼다 — 정책도 class 산출물도 손대지 않는다. 그것이 앞 라운드의 양성
대조 일곱이 지나지 않았던 경로다(그 일곱은 전부 선언된 입력을 바꿨다).

**프로덕션 배선 e2e 대조**(버릴 clone, settlement — cr H-1 이 요구한 「추가할 대조」). TestKit
fixture 는 두 property 를 자기가 묶으므로 **convention 의 배선 실수**를 잡지 못한다 — 그래서
실제 모듈에서 한 번 더 쟀다.

| 단계 | 실측 |
| --- | --- |
| 손대지 않은 상태 `:settlement:mutationReportGate` | exit **0** · 리포트 **61/73(84%)** |
| test 둘 삭제(정책·main 소스 불변) 뒤 같은 명령 | exit **1** · `pitest` 가 다시 돌아 리포트 **15/73** · 게이트 **FAILED** — 「15/73(20%) 가 래칫 83% 밑이다」 |

verifier 가 **구판에서** 같은 조작으로 「리포트 15/73 · UP-TO-DATE · BUILD SUCCESSFUL」을 얻었다 —
같은 입력이 이제 RED 다. 콘솔의 21% 와 게이트의 20% 가 다른 것은 §2 의 반올림·내림 차이 그대로다.

### 9.2 표적 게이트 — 표적 test 가 돌리지 않는 등식

전건 `check` 는 팀장 판단으로 로컬에서 돌리지 않는다(PR CI 가 정본). 대신 `--tests` 표적 실행이
**건너뛰는** 등재·실행 등식 게이트를 직접 돌렸다 — 새 test 클래스가 들어온 라운드라 이 축이
바로 그 자리다.

| 명령 | 결과 |
| --- | --- |
| `buildLogicGateRegistrationGate` · `buildLogicGateExecutionGate` · `leakPatternGate` | **exit 0** |
| `tools/workflow-step-refs-check.sh .github/workflows/ci.yml` | **exit 0** — job 5 · id 15 · 참조 18 · 매달린 참조 0 |

### 9.3 acceptance — CI 와 같은 직렬

`./gradlew --no-daemon --max-workers=1 mutationCheck` **exit 0 · 10분 11초.** `pitest` 일곱 모듈
전부 실제 실행(UP-TO-DATE·FROM-CACHE **0 건**을 따로 셌다), PIT 분석 블록 7, 게이트 여덟 통과.
직렬 소요가 앞 라운드의 병렬 6분 32초 대비 10분 11초로 — 45분 상한에 여유가 있다.

게이트 리포트에 `policyVersion=1` 이 적히고, 집합 세 변(`expected`·`registered`·`domain`)이 같다.

**이 전건은 `8f3cb545`(판정 SHA 한 커밋 앞)에서 돌았다.** 그 뒤 커밋(`eab07b5b`)이 coverage 게이트
술어의 **문면 구성**을 바꿨으므로 — 하네스 규율대로 severity 무관 표적 재검증 대상이다 —
통과 경로(`mutationCoverageGate` 단독, 위반 0)와 실패 경로(C-9 재대조)를 판정 SHA 에서 각각 다시
쟀다. 바뀐 가지는 **위반이 있을 때의 줄 구성**뿐이고 통과 시 결과는 빈 목록으로 같다.

### 9.4 양성 대조 재실측 — 술어가 바뀌었으므로 전부 다시

| # | 변이 | 결과 |
| --- | --- | --- |
| C-1 | 점수 래칫 83 → **84**(경계 한 칸) | exit 1 — 「점수 61/73(83%) 가 래칫 84% 밑이다」 |
| C-2 | 변이 수 래칫 73 → **74** | exit 1 — 「변이 수 73 이 래칫 74 밑이다」 |
| C-3 | 변이 클래스 래칫 8 → **9** | exit 1 — 「클래스 수 8 가 래칫 9 밑이다」 |
| C-8 | 점수 래칫을 **0** 으로 무력화 | exit 1 — 「값이 1..100 밖이다: 0」(신설, cr L-4) |
| C-4 | 정책에서 모듈을 뺀다 | exit 1 — 「정책 밖 모듈이 … 적용했다: [settlement]」 |
| C-5 | 리포트를 옮기고 게이트만 | exit 1 — 「리포트가 존재하지 않는다」 |
| C-6 | 모듈에서 convention 만 뺀다 | exit 1 — 「… 적용하지 않았다: [settlement]」 |
| C-7 | 비도메인 모듈이 convention 을 적용 | exit 1 — **구성 단계**에서 「순수 도메인 모듈이 아니다」 |
| C-9 | 정책이 도메인 밖 모듈을 든다 | exit 1 — 「순수 도메인 밖 모듈을 대상으로 들었다: [workflow]」 **한 줄**(신설, cr L-3) |

**C-9 의 첫 실측이 보완을 하나 더 만들었다.** 처음에는 같은 오기가 **두 줄**로 났고 먼저 읽히는
줄이 「적용하지 않았다」였다 — L-3 이 없애려던 두 걸음이 순서만 바뀌어 남아 있었다. 도메인 밖
모듈은 convention 을 적용할 수 없으므로(C-7 이 그 증거다) 그 축에서 빼고 도메인 축을 앞에 놓았다.
지금은 **한 줄**이고 test 가 줄 수 1 을 단언한다.

## 10. 머지 직전 병합 — main `e12c0485`

**「충돌 0」을 통과로 읽지 않는다**(저장소 기억 「병합 합집합 함정」 — 충돌 0 자동 병합이 같은
본문을 접는다). 겹치는 세 파일을 **병합 전에** 양쪽에서 세고 **base + 양쪽 증분**과 산술로 댔다.

| 축 | base | 내 증분 | main 증분 | 병합 뒤 | 판정 |
| --- | --- | --- | --- | --- | --- |
| `ci.yml` 줄 | 1026 | +52 | +4 | 1082 | 일치 |
| `ci.yml` job | 4 | +`mutation` | 0 | 5 | 일치 |
| `capability-map.md` 줄 | 3858 | +6 | +113 | 3977 | 일치 |
| `gate-tests` `build-logic` | 27 | +4 | 0 | 31 | 일치 |
| `gate-tests` `app` | 121 | 0 | +3 | 124 | 일치 |
| G6 표 행 | 8 | +2 | 0 | 10 | 일치 |

형태 축도 함께 쟀다 — step id 15 **중복 0** · 매달린 `steps.<id>` **0**
(`workflow-step-refs-check.sh` exit 0: job 5 · id 15 · 참조 18) · 등록부 **중복 0**,
`build-logic` 블록 **사전순** · G6 표 파이프 수 **일정** · `### 12.3`(main)과 §14.2 의 내 해소
행이 **둘 다** 있고 취소선 없는 `OPEN-ADR-07` 행은 **0**.

**집계 머리말은 겹치지 않았다.** hunk 자리가 main 3518·3793·3815 대 내 것 3218·3376·3607 로 모두
떨어져 있고, main 의 §12.3 이 스스로 *"이 절의 OPEN 은 §12 머리의 활성 집계에 들어가지 않는다"* 고
선언해 머리의 수를 움직이지 않았다. 활성 수는 양쪽 다 **51**, 증감 줄은 내 **+2** 하나만 더해졌다
(규약: 각 레인은 자기 증감만, 합산은 머지하는 쪽).

**표적 게이트 셋** — `buildLogicGateRegistrationGate`·`buildLogicGateExecutionGate`·
`leakPatternGate` **exit 0**(병합 뒤). main 이 `build-logic/**`·`gradle/libs.versions.toml`·
ADR·대상 모듈 빌드 스크립트를 건드리지 않았으므로(기계 확인) 변이 분석 축은 재실행 대상이 아니다 —
겹친 자리는 `gate-tests.properties`(블록이 다르다)와 `ci.yml`(job 이 다르다) 둘뿐이다.

**rollback 원천이 `df4976cc` → `e12c0485` 로 옮겨간 이유가 실물로 있다**: main 이
`gate-tests.properties` 의 `app` 블록을 +3 했고 그 파일은 내 복원 목록 안에도 있다. base 에서
복원하면 그 셋이 사라진다. 복원 뒤 실측에서 `app` **124** 가 남고 `build-logic` 은 **27**(base)로
돌아간다 — 「내 줄 사라짐 · 남의 줄 남음」이 같은 파일 안에서 갈린다.

## 11. 수정 라운드 2 — 처방 `07ecf5e9` · 병합 `2fad9b99`

지적은 하나다(v r2 M-1): 리포트 지문이 공유 convention 의 배선 **한 줄**에 걸려 있어 그 줄을
지우면 TestKit 셋은 초록인 채로 프로덕션 증분이 거짓 초록으로 돌아간다. 처방은 지문용 property
를 없애고 **리포트 선언 자체**를 부재 허용 입력으로 둔 것이다 — 지울 수 있는 둘째 줄이 없다.

### 11.1 RED 선행 → GREEN

| # | 명령 | 결과 |
| --- | --- | --- |
| R-7 | fixture 가 리포트를 **한 줄로만** 배선하게 바꾸고 **구판**에서 회귀 셋 | **RED** — 「리포트 악화」·「리포트 삭제」가 `UnexpectedBuildSuccess`(게이트가 UP-TO-DATE 로 건너뛰어 초록). 음성 대조만 통과 |
| R-8 | 같은 fixture, 처방 뒤 | 초록 셋 — 음성 대조도 **그대로** 통과 |
| R-9 | `-p build-logic test --tests 'bidvector.buildlogic.Mutation*' validatePlugins ktlint{Main,Test}SourceSetCheck detekt` | **exit 0.** mutation test **33**(14·10·3·6), 실패·건너뜀 0 |

R-9 의 `validatePlugins` 는 **실행**됐다(UP-TO-DATE 아님) — 리포트를 단수 입력이 아니라 부재
허용 입력으로 선언한 이 조합이 Gradle 자신의 task 속성 검증을 지난다는 실측이 그 자리다.
`ktlint{Main,Test}SourceSetCheck` 는 **수집 task** 라 UP-TO-DATE 로 보고된다 — 실제 검사는
`runKtlintCheckOver{Main,Test}SourceSet` 이고 그 둘이 실행됐다. 초록을 수집 task 의 UP-TO-DATE 로
읽지 않는다.

### 11.2 프로덕션 배선에서 다시 — fixture 가 아니라 실제 convention

fixture 는 이제 프로덕션과 같은 모양으로 배선하지만(선언이 하나다) 그 동치를 fixture 안에서
증명할 수는 없다. 그래서 실제 모듈에서 세 번 쟀다. pitest 를 다시 돌리지 않고 앞 라운드가 남긴
settlement 리포트(61/73)를 입력으로 썼다 — 재는 것은 변이 분석이 아니라 up-to-date 판정이다.

| 단계 | 입력 | 결과 |
| --- | --- | --- |
| A | 손대지 않은 리포트 61/73 | 게이트 **실행** · exit 0 |
| B | 같은 입력으로 다시 | **UP-TO-DATE** · exit 0 — 음성 대조(「항상 재실행」이 아니다) |
| C | 리포트의 탐지 속성만 전부 거짓으로 **바꿔치움**(0/73) | 게이트 **FAILED** · exit 1 · 래칫 위반 문면 |

정책·class 산출물·빌드 스크립트는 C 에서 손대지 않았다. 측정 뒤 리포트를 원복했다(빌드
산출물이라 커밋 대상이 아니다). 앞 라운드의 e2e(test 둘을 지워 15/73 을 만든 대조)와 달리 이
셋은 **같은 리포트 파일만** 움직여 up-to-date 축 하나를 고립시킨다.

### 11.3 변이 — 처방을 바꿔치운다

산출물을 커밋한 뒤 리포트 선언의 지문 annotation 을 `@Internal` 로 **바꿔치우니** 회귀 둘이
**RED**(3 중 2 실패)이고 음성 대조는 통과. 원복 뒤 `git status --porcelain` 빈 출력.
표지로 그 annotation 이름 자체를 쓸 수 없다 — build-logic 의 다른 선언 열넷이 같은 것을 쓴다
(남의 줄까지 센다).

### 11.4 병합 — main `3ad4f3f6`(#135)

겹침을 계수로 먼저 확인했다: 내 경로 **30** · main 증분 **14** · **교집합 0**. 「충돌 0」을 통과로
읽지 않고(저장소 기억 「병합 합집합 함정」) 합집합을 등식 셋으로 댔다.

| 축 | 결과 |
| --- | --- |
| `HEAD ↔ 3ad4f3f6` 차이 집합 == 내 경로 집합 | 양방향 차집합 **0** |
| main 증분 14 파일 ↔ `3ad4f3f6` | 차이 **0**(접히거나 덮인 본문 없음) |
| 내 파일 30 ↔ 병합 전 `07ecf5e9` | 차이 **0** |

형태 축 — `ci.yml` 줄 1082 · job **5** · step id 15 · 매달린 참조 **0**
(`tools/workflow-step-refs-check.sh` exit 0) · 등록부 `build-logic` **31** · `app` **124** ·
`web/config/gate-tests.txt` 108(main 과 동일, 내가 만지지 않는 파일) · 등재 중복 **0**.

`#135` 는 `web/**` 와 자기 evidence 만 바꿨다 — `build-logic/**`·카탈로그·ADR·대상 모듈 빌드
스크립트를 건드리지 않았으므로(기계 확인) 변이 분석 축은 재실행 대상이 아니다.

### 11.5 표적 게이트 — 병합 뒤

| 명령 | 결과 |
| --- | --- |
| `buildLogicGateRegistrationGate`·`buildLogicGateExecutionGate`·`buildLogicSizeGate`·`buildLogicTypeShapeGate`·`buildLogicTestShapeGate`·`leakPatternGate` | **exit 0** — 여섯 전부 **실행**(UP-TO-DATE 아님). 실행 등식은 `:build-logic:test` **전건**에서 `required` 31 == 결과 XML 31, 등재 등식은 `population` 31 == 장부 31 |
| `tools/workflow-step-refs-check.sh .github/workflows/ci.yml` | exit 0 — job 5 · id 15 · 참조 18 · 매달린 참조 0 |

등재·실행 등식은 `:build-logic:test` **전건**을 물고 돈다 — 표적 `--tests` 가 건너뛰는 자리가
거기다. 이 라운드는 **import 하나도 지웠고**(지문용 property 와 함께 쓰이던 annotation) 그 축은
별도 게이트가 아니라 ktlint 의 미사용 import 규칙이 진다(R-9 에서 실행됐다).

**첫 시도는 없는 task 이름으로 죽었다** — 다른 레인이 자기 브랜치에서 만든 import 게이트 이름을
이 브랜치에서 불렀고 Gradle 이 `Task 'buildLogicUnusedImportGate' not found` 로 끊었다(exit 1,
게이트 하나도 돌지 않음). 그 실패를 「게이트가 붉었다」로 읽지 않는다 — **선택 단계**의 실패다.
이 브랜치에 있는 이름으로 다시 돌린 것이 위 줄이다.

`leakPatternGate` 는 참조형 스캔이고 패턴 어휘를 이 파일에 축어로 적지 않는다. 그 측정은 이
evidence 를 **담은** 작업 트리에서 돌았다 — 그 뒤 이 표의 한 줄이 다시 자라므로, 마지막 HEAD 의
정본은 verifier 와 PR 조치 코멘트다(하네스 규율).

## 12. 수정 라운드 3 — 처방 `9fbb1caf`

지적은 하나다(v r3 L-1, 비차단): 게이트가 **읽는** 자리와 pitest 가 **쓰는** 자리를 convention 의
지역 값 하나가 묶고 있었다. 한쪽만 옮기고 옛 리포트를 남기면 pitest 가 새 자리에 나빠진 수를
쓰는데도 게이트는 좋은 낡은 수를 읽어 로컬 증분 빌드가 통과한다(verifier B5). 처방은 자리를
**도구에서 받아오는** 것이다 — `pitestMutationXmlReport()` 가 pitest task 의 `reportDir` 에서
파생하고 convention 은 그 한 줄만 쓴다. M-1 과 같은 원칙의 둘째 적용이다: 쓰이는 값과 그 값의
출처가 **같은 것**이어야 한다.

### 12.1 RED 선행 → GREEN

| # | 명령 | 결과 |
| --- | --- | --- |
| R-10 | fixture(**실제 pitest plugin 적용** · `reportDir` 은 옮긴 자리 · 옛 자리에 좋은 리포트)에서 게이트를 **옛 자리로 고정**한 구판 꼴 | **RED** — 「옮긴 자리를 읽는다」가 `UnexpectedBuildSuccess`(좋은 낡은 리포트로 통과), 양방향 통제군이 `UnexpectedBuildFailure`. 기존 셋은 그대로 초록(5 중 2 실패) |
| R-11 | 같은 fixture, 처방 뒤 | 다섯 전부 초록 |
| R-12 | `-p build-logic test --tests 'bidvector.buildlogic.Mutation*' validatePlugins ktlint{Main,Test}SourceSetCheck detekt` | **exit 0.** mutation test **35**(14·10·5·6), 실패·건너뜀 0. `validatePlugins`·`runKtlintCheckOver{Main,Test}SourceSet`·`detekt` 전부 실행 |

RED 가 **거동**이다 — 구판 꼴 fixture 는 헬퍼를 참조하지 않으므로 컴파일을 지나고, 게이트가
실제로 옛 파일을 읽어 판정이 뒤집힌다(빌드 실패로 얻은 RED 가 아니다).

### 12.2 프로덕션 배선 — `--rerun` 으로 실제 분석까지

| 단계 | 명령 | 결과 |
| --- | --- | --- |
| A | `:settlement:pitest --rerun` | pitest **실제 실행**(1분 11초) · finalizer 게이트 실행 · **exit 0** · 리포트 61/73 — 파생된 자리에서 읽었다 |
| B | `:settlement:mutationReportGate -x :settlement:pitest`(입력 불변) | **UP-TO-DATE** · exit 0 |
| C | 리포트만 0/73 로 바꿔치움 | **FAILED** · exit 1 |

앞 라운드의 A/B/C 는 남아 있던 리포트를 입력으로 썼고 이번 A 는 `--rerun` 으로 **분석부터** 돌려,
파생 배선에서 pitest 와 게이트가 같은 파일을 보는지까지 실측했다.

### 12.3 변이 — 파생을 고정 경로로 바꿔치운다

커밋 뒤 헬퍼의 파생 한 줄을 고정 경로로 바꿔치우니 새 회귀 둘이 **RED**(35 중 2 실패)이고 나머지
서른셋은 통과. 원복 뒤 `git status --porcelain` 빈 출력.

### 12.4 표적 게이트

| 명령 | 결과 |
| --- | --- |
| `buildLogicGateRegistrationGate`·`buildLogicGateExecutionGate`·`buildLogicSizeGate`·`buildLogicTypeShapeGate`·`buildLogicTestShapeGate` | **exit 0**, 다섯 전부 실행. 실행 등식은 `:build-logic:test` 전건에서 `required` 31 == 결과 XML 31, 등재 등식은 `population` 31 · `excluded` 0 |
| `tools/workflow-step-refs-check.sh .github/workflows/ci.yml` | exit 0 — job 5 · id 15 · 참조 18 · 매달린 0 |
| `leakPatternGate` | **exit 0** — 실행(UP-TO-DATE 아님). 이 라운드의 evidence 를 담은 트리에서 쟀고, 그 뒤 이 표의 한 줄만 자란다(§11.5 의 같은 단서) |

새 파일 하나(`MutationReportLocation.kt`)는 **main 소스**라 test 등록부와 무관하다 — 새 test
클래스가 없고(기존 클래스에 둘을 더했다) 등재 등식 31 이 그 사실과 같다.

## 13. 셋째 병합 — main `97ed49c4`(#134) · 판정 SHA `0b9ce7a0`

겹침이 **한 파일**이고 그것이 등록부다(`config/quality/gate-tests.properties` — 내가
`build-logic` 블록을 +4 했고 main 이 같은 파일의 **주석 문단**을 고쳤다). 등록부가 겹치면
「충돌 0」으로는 아무것도 알 수 없으므로 블록별 등식·중복·정렬을 기계로 셌다.

### 13.1 등록부 합집합 — 블록별 등식

열여섯 블록 전부 **base + 양쪽 증분**과 일치했다. 움직인 블록은 `build-logic` 하나(원천 27 ·
내 31 · main 27 → 병합 **31**)이고 나머지 열다섯은 세 판 모두 같은 수다. **중복 등재 0**(세 판과
병합 뒤 모두). 사전순이 아닌 블록은 **세 판 모두 같은 셋**(`app`·`decision`·`procurement`)이라
이 병합이 만든 것이 아니다 — 내가 더한 `build-logic` 블록은 사전순이다.

### 13.2 같은 파일의 본문이 접히지 않았는가

| 축 | 결과 |
| --- | --- |
| 줄 수 | 원천 1069 + 내 **+4** + main **+6** = **1079**(병합 뒤 실측) |
| main 이 새로 쓴 주석 문면 | 병합 뒤 **1**(main 과 같다) |
| main 이 **지운** 옛 문면 | 병합 뒤 **1** — 그 문면은 원천에 **둘** 있었고(`Persistence`·`Extraction` 두 문단) main 이 앞의 하나만 고쳤다. main 자신도 1 이다. 「0 이어야 한다」는 내 첫 기대가 틀렸고 수가 맞다 |
| 내 `build-logic` 블록 +4 | 병합 뒤에도 **31** |

### 13.3 집합 등식

| 축 | 결과 |
| --- | --- |
| 내 경로 31 ∩ main 증분 9 | **등록부 한 파일** |
| `HEAD ↔ 97ed49c4` 차이 집합 == 내 경로 집합 | 양방향 차집합 **0** |
| main 의 나머지 8 파일 ↔ `97ed49c4` | 차이 **0** |
| 내 파일(등록부 제외) ↔ 병합 전 `4768c2d9` | 차이 **0** |

### 13.4 재실측 — 모든 task 에 `--rerun`

| # | 명령 | 결과 |
| --- | --- | --- |
| R-13 | `-p build-logic test --rerun validatePlugins --rerun ktlint{Main,Test}SourceSetCheck detekt --rerun` | **exit 0.** `test`·`validatePlugins`·`runKtlintCheckOver{Main,Test}SourceSet`·`detekt` 전부 **실행**. 결과 XML **31** 클래스 · 실패 클래스 **0** · mutation test **35**(14·10·5·6), 건너뜀 0 |
| R-14 | 게이트 여섯에 각각 `--rerun`(등재·실행·크기·타입 꼴·test 꼴·leak) | **exit 0**, 여섯 전부 **실행**. 실행 등식 `required` 31 == 결과 XML 31 · 등재 `population` 31 · `excluded` 0 |
| R-15 | 이 라운드의 evidence 를 담은 트리에서 `leakPatternGate --rerun` | **exit 0** · 실행. 그 뒤 이 표의 한 줄만 자란다(§11.5 의 같은 단서) |

R-14 의 `:build-logic:test` 는 **FROM-CACHE** 였다 — R-13 이 바로 앞에서 같은 입력으로 돌려 캐시가
받은 것이다. 실행의 근거는 R-13(`--rerun` 으로 실제 실행)이고 실행 등식이 읽는 결과 XML 서른한
개가 그 실행의 산출물이다. **FROM-CACHE 를 실행으로 세지 않는다**(저장소 기억).

**pitest 자체는 이 라운드에 다시 돌리지 않았다** — main 증분이 `adapters/**`·등록부 주석·자기
evidence 뿐이고 `build-logic/**`·카탈로그·ADR·대상 모듈 빌드 스크립트를 건드리지 않았다(기계
확인). 변이 분석 축과 프로덕션 배선 A/B/C 는 §12(처방 커밋)에서 `--rerun` 으로 측정된 그대로다.
전건 `check` 는 PR CI 가 정본이다.

### 13.5 되돌림

원천을 `97ed49c4` 로 옮기고 다시 쟀다 — `rollback.md`. 겹친 등록부에서 **내 +4 는 사라지고
main 의 주석 문단은 남는** 것이 그 파일 한 장에서 갈린다.
