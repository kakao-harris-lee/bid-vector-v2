# M9/adr07-pitest commands

base `df4976cc` · 판정 SHA `f4a85991` · 전 실행 `~/.local/bin/bv-build.sh ./gradlew --no-daemon …`
(호스트 빌드 자리 둘 규율). 아래 「결과」는 핵심 한 줄이고 출력 전문은 싣지 않는다.

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
