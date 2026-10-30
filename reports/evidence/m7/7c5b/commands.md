# M7/7C⑤-b 실행 기록 — 내 입찰 결과 조회

base main `003ec91d` · worktree `bid-vector-v2-m7-7c5b` · 브랜치 `m7-7c5b/2026-10-31` · push 0.

## RED 선행

| 층 | RED 확인 | GREEN |
| --- | --- | --- |
| 대사 구성·집계·차이 | `:workflow:compileTestKotlin` 미해결 참조(`reconcileBid`·`ReconciliationVerdict`·`ReconciliationBlocker`) | `:workflow:test --tests 'bidvector.workflow.result.*'` |
| 조회 조립 | 같은 자리에서 `ReadMyBidResults`·포트 셋 미해결 | 같음 |
| 읽기 어댑터 | — (실 DB test 는 어댑터와 같은 커밋) | `:adapters:test --tests 'bidvector.adapters.result.*'` |
| 계약 대조 | — | `:app:test --tests 'bidvector.app.http.OpenApiMyBidResultContractTest'` |

RED 은 **구현 전에** 각 층마다 확인했다(컴파일 실패 = 그 층의 어떤 단언도 아직 서지 않는다).

## 핵심 결과

| 명령 | 결과 |
| --- | --- |
| `:workflow:test --tests 'bidvector.workflow.result.*'` | BUILD SUCCESSFUL |
| `:adapters:test --tests 'bidvector.adapters.result.*'` | BUILD SUCCESSFUL |
| `:app:test --tests 'bidvector.app.http.OpenApiMyBidResultContractTest'` | BUILD SUCCESSFUL |
| `:app:test --tests 'bidvector.app.http.MyBidResultEndpointTest'` | 9 tests · 실패 0 · 건너뜀 0 |
| `:app:test`(전건) | 976 tests — 게이트 등재 뒤 아래 절의 여섯이 초록 |
| `web` 생성 타입 — `npm run api:types` 뒤 `npm run api:types:check` | 바이트 등식 통과 |

Gradle 은 전부 `~/.local/bin/bv-build.sh ./gradlew --no-daemon …` 경유. 호스트 점검 셋(java 대기 ·
`free -m` · RSS 상위)은 빌드와 **다른 호출**로 먼저 돌렸다.

**초록은 종료 코드로 읽는다.** 파이프로 거른 출력의 종료 코드는 거르는 쪽의 것이라 Gradle 의
판정이 아니다 — 전건 실행은 출력을 파일로 받고 종료 코드를 따로 적어 읽었다. 표적 실행의
BUILD SUCCESSFUL 도 그것만으로는 「그 test 가 돌았다」가 아니므로 결과 XML 의 `tests`·`failures`·
`skipped` 수로 **돌았음**을 따로 셌다(위 표의 9 가 그 수다).

## 게이트 등재 — 왜 필요했는가

새 표면이 **집합·계수 등식**을 지나므로 등재가 선택이 아니다. 각 등재가 없으면 무엇이 붉는지:

| 등재 | 없으면 붉는 것 | 왜 그 등식이 있는가 |
| --- | --- | --- |
| `app.surface.api.handlers` 에 `MyBidResultController` | 표면 census · 계정 바인딩 구조 **둘** | 「HTTP 로 무엇이 닿는가」의 단일 출처. 새 handler 가 조용히 늘지 않게 |
| 배선 ① 층에 `MyBidResultWiring` | 의존 허용 목록 ⊆ | 조립은 어댑터 구체 클래스를 쥐어도 되는 자리라 **이름을 정확히** 적는다(접두·패턴이면 그 아래 새 클래스가 조용히 면제된다) |
| **면제 수 래칫 75 → 76** | 면제 크기 고정 단언 | 면제가 **눈에 띄지 않게 늘지 않게** 하는 자리다. 이 slice 가 늘린 면제는 **배선 하나뿐**이고 그 사실이 이 수의 +1 로 선언된다. 조회기(② 층)는 늘지 않았다 — 컨트롤러가 받는 것이 포트가 아니라 use case 하나라서다 |
| 주입 허용 표면에 `ReadMyBidResults` · 응답 DTO 둘 | 주입 표면 집합 등식 | 컨트롤러가 무엇을 받는지가 한 자리에 선언된다. 중첩 응답 DTO 도 그 집합에 든다(`AwaitingItemResponse` 선례) |
| **상수 시간 비교 허용 목록에 넷** | `Intrinsics.areEqual` 금지 등식 | 비밀값을 **짧은 비교**로 다루는 길을 막는 게이트다. 등재한 넷은 `data class` 자동 `equals`(응답 값 객체 셋)와 sealed 싱글턴 `when` 소진(파일 하나)이고, **어느 것도 자격증명 값을 참조하지 않는다** — 항목마다 그 사유를 붙였고 그 사유가 비면 등재 자체가 거부된다 |
| 계정 범위 핸들러 목록에 `MyBidResultController` | typed 접근자 등식 | 계정 주체를 얻는 길이 접근자 하나임을 집합으로 잠근다. 주체 **라벨**을 읽는 자리는 늘지 않았다 |
| 상호 포트를 `ProfileWiring` 이 낸다 | 조립 경계 · V22 어댑터 격리 **둘** | 7C④ 가 「판정 쪽으로 건너가는 길」을 막으려고 세운 경계다. 소비자가 늘 때 허용 자리를 늘리는 대신 **소유자가 좁은 읽기를 publish** 한다 |
| `app.http.allowed-packages` 에 `workflow.result` | app 층 참조 허용 목록 | 응답 조립이 판정 어휘와 수를 칸으로 옮기려면 그 어휘를 이름 불러야 한다(7C⑤-a 의 `workflow.opening`·7C④ 의 `workflow.profile` 과 같은 자리) |
| **`app.http.denied-types` 에 읽기 포트 넷** | — (등재가 **좁히는** 쪽이다) | 패키지를 열면 포트도 함께 열린다. 핸들러가 받는 것이 use case 하나라는 사실을 여기서 다시 세운다 — 포트가 HTTP 층에 보이면 그것이 곧 지름길이다 |
| 주입 허용 표면에 `sharedkernel.AmountRecord` | 주입 표면 집합 등식 | `ConfirmedAwardFields` 가 받는 타입이고, 금액 다섯 성분을 함께 나르는 **유일한 공개 경로**다(R-BASIS-06) |
| `gate-tests.properties` 에 새 test class 넷 | 등재 등식(모집단 ∖ 제외 == 등재) | 등재를 잊으면 그 test 를 수집에서 빼는 것이 곧 게이트를 끄는 것이 된다 |

## 무엇이 어디서 잠기는가

| 방어 | 잠그는 자리 |
| --- | --- |
| 판정 불가 사유 **열**이 각각 도달 가능 | `ReconcileBidTest` — `ReconciliationBlocker.entries` 를 돌며 소진 `when` 이 준 입력이 그 사유를 내는지 확인(손으로 든 목록이 아니다) |
| 유찰·재입찰이 판정 불가로 접히지 않음 | 같은 class |
| 개찰 축이 커널 축보다 먼저 | 같은 class |
| 분모 0 이 0% 가 아님 · 「셀 것 없음」↔「전부 제외」 | `MyBidResultTallyTest` |
| 「분자 + 제외 = 표본」 | `AxisTally` 의 파생 + `MyBidResultAggregate` 생성자 단언, `MyBidResultTallyTest` |
| 차이가 잠정으로 채워지지 않음 | `awardGapOf` 시그니처(잠정 자리 없음) + `MyBidResultTallyTest` |
| 통화 축 전제 | `MyBidResultTallyTest` 의 어휘 래칫 |
| 다른 계정 혼입 | 포트 서명 + `JdbcMyBidResultViewTest`(실 DB) + `MyBidResultEndpointTest`(401) |
| 페이퍼 혼입 | `recordedRealBidOrNull` 타입 + 실 DB test |
| 메모·증빙·건너뜀 사유 미노출 | 열 목록 등식 + **메모를 심어** 산출 전문에 없음을 값으로(실 DB test) |
| 낙찰자 상호 미노출 | 개찰 사실 경로의 표지 글자 부재(실 DB) · 응답 전문의 표지 글자 부재(wire) · 계약 칸 이름 금지어 |
| 쓰기 0 | 읽기 전용 증인(조회마다 따로) + 네 표 행 수 불변 + **음성 대조**(쓰기 뒤 계수 변화) |
| 계약 ↔ 타입 키 등식 | `OpenApiMyBidResultContractTest`(최상위 2 · 건 23 · 집계 16, 전부 required) |

## 구현 중 바뀐 설계 — 상호를 어디서 읽는가

처음에는 `BusinessProfileReader` 를 use case 가 그대로 받았다. 게이트 **둘**이 차례로 그것을 막았고,
둘 다 7C④ 가 일부러 세운 경계라 허용 자리를 늘리지 않고 **설계를 옮겼다**.

| 막은 게이트 | 무엇이 걸렸나 | 옮긴 결과 |
| --- | --- | --- |
| 조립 경계 — 「V22 타입을 조립하는 자리는 `ProfileWiring` 하나」 | 결과 조회의 조립이 프로필 저장 포트를 받았다 | 좁은 포트(`OperatorTradeNamePort`)로 받는다 — 조립은 상호 하나만 안다 |
| 어댑터 격리 — 「V22 표 이름은 허용된 어댑터 자리에서만」 | 그 포트를 채우려고 `account_business_profile` 을 직접 읽는 뷰를 새로 두었다 | **V22 를 아는 슬라이스가 그 포트를 publish** 한다. 새 뷰와 질의는 지웠다 |

남은 모양이 더 낫다: 상호의 소유자가 프로필이고, 결과 조회는 「상호 하나」만 안다. 프로필 포트가
절 전체(본사 소재지 · 나라장터 준비)를 내는데 대사에 필요한 것은 상호 하나이므로, 「담을 자리가
없는 값을 읽을 이유가 없다」가 입력 쪽에도 선다.

## 크기 한도 — 넘긴 셋과 되찾은 자리

셋 다 이 slice 가 넘긴 것이고, 남의 근거 문장은 건드리지 않고 닫았다.

- 두 모듈의 `dependencies` 람다가 **이미 정확히 50 줄**이라 의존 한 줄만 더해도 한도를 넘었다.
  주석을 한 줄로 줄이고 블록 안 **빈 줄**을 둘씩 회수했다.
- 상수 시간 허용 목록 test 가 54 줄 — 이름 집합을 **함수 밖 선언**으로 옮겼다. 사유 표와의 키
  등식은 그대로라 「사유 없는 등재」도 「등재 없는 사유」도 여전히 붉는다.
- `openingViewOf` 의 반환 셋을 하나로.

## 변이(바꿔치우기) — 버릴 clone 에서

**더하기가 아니라 바꿔치우기**다(각 변이는 기존 문장을 다른 것으로 바꾼다). 「붉다」는 종료 코드가
아니라 **결과 XML 의 실패 수**로 셌다 — 종료 코드만 보면 test 가 돌지 않은 초록과 구별되지 않는다.

| # | 바꿔치운 것 | 결과 |
| --- | --- | --- |
| 1 | 커널의 「낙찰자 상호 미기록」 갈래를 **패찰**로 | 붉음 2 — 3-state 가 접히지 않는다 |
| 2 | 분모에 **판정 불가를 더한다** | 붉음 4 — 분모는 낙찰+패찰뿐 |
| 3 | 확정 낙찰가가 없을 때 **차이를 내 금액으로 채운다** | 붉음 1 — 부재는 부재다 |
| 4 | 투찰 기록 질의에 **`memo` 열을 더한다** | 붉음 10 — 열 목록 등식과 표지 글자 |
| 5 | canonical 질의에 **낙찰자 상호 열을 더한다** | 붉음 1 — 개찰 사실의 표지 부재 |
| 6 | 질의에서 **종류 필터를 걷는다** | **살아남음** — 아래 |
| 7 | **읽기 전용 트랜잭션을 열지 않는다** | 붉음 13 — 증인과 무쓰기 지문 |
| 8 | 분모 0 일 때 비율에 **`"0"` 을 싣는다** | 붉음 2 — 부재는 0% 가 아니다 |

### 6 이 살아남은 이유 — 측정해서 답했다

페이퍼 배제는 **두 층**이 진다(질의 필터 · 포트 값의 타입 가드). 한 층을 걷어도 다른 층이 같은
답을 내므로 **거동이 바뀌지 않고**, 그래서 test 가 붉지 않는 것이 맞다. 어느 층이 지고 있는지
추측하지 않고 변이를 하나 더 세워 갈랐다.

| 걷어낸 것 | 결과 |
| --- | --- |
| 질의 필터만 | 살아남음 (타입 가드가 진다) |
| 타입 가드만 | 살아남음 (질의 필터가 진다) |
| **둘 다** | **붉음 1** — `페이퍼 건의 투찰 기록은 목록에 없다` |

즉 둘은 서로의 **대체**이지 장식이 아니고, test 는 「둘 다 사라지는 것」을 잡는다. 한 층만 남기면
그 층이 유일한 실패점이 되므로 둘을 유지한다.

## acceptance

미실행 — `ci.yml` 의 Kotlin `check` + `qualityBaseline`, `web` job 전체.

## 알려진 제한

`checklist.md` 의 「알려진 제한」 여섯.
