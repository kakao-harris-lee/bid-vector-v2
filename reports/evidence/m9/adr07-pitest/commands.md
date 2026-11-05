# M9/adr07-pitest commands

base `df4976cc` · **판정 SHA `d4b43248`**(병합 커밋 — 수정 라운드 1 + main `e12c0485` 병합) · 전 실행
`~/.local/bin/bv-build.sh ./gradlew --no-daemon …`(호스트 빌드 자리 둘 규율). 아래 「결과」는
핵심 한 줄이고 출력 전문은 싣지 않는다.

**판정 SHA 문면 정정(v L-1)** — 앞 라운드에 이 머리말과 `checklist.md` 가 `f4a85991` 을 적었는데
그 뒤로 산출물 커밋이 더 왔다(capability-map 행 하나 · 수정 라운드 다섯). §1~§7 은 **첫 라운드**의 실측,
**§9 는 수정 라운드 1**(`eab07b5b`), **§10 은 병합 뒤**(현 판정 SHA)다 — 어느 수가 어느 SHA 의
것인지 절로 가른다.

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
