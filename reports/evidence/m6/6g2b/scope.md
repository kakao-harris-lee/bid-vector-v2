# M6/6G-2b — 전송 표면 게이트 보강: 관문 밖으로 바이트를 내는 길 (계약, 초안 2026-09-30 · 착수 2026-10-03)

> **지위: 착수(2026-10-03).** base **`1745a3e2`**(PR #57 6G-2f 머지 뒤 `main`), worktree `bid-vector-v2-m6-6g2b`, 브랜치 `m6-6g2b/2026-10-03`.
> 운영자 결정 2026-10-01 순서(6G-2a → 6G-2e → **6G-2b** → 6G-2c)대로. 실수집(1,000/operation/일 안에서 진행 중)과 병행 — 이 slice 는 test·정책 파일만 바꾼다.
> 수령하는 OPEN: **`OPEN-6G-TRANSPORT-GATE-HARDENING`**(6G 계약 D-6G-75, verifier r4 M-1 · r5 H-3, code-review r5 L-7).
> 초안 시점 문면은 아래에 그대로 두고, 착수 시 바뀐 것은 「착수 실측(2026-10-03 채움)」·「acceptance 정정」·OPEN 표의 이관 행으로 적는다.

- base: **`1745a3e2`**.
- 레인: `kotlin-implementer` 하나. Python 변경 없음(`OPEN-6G2E-APP-HTTP-IMPORT-CONTRACT` 는 6G-2c 로 이관 — Python 레인·`ml-engine` job 소관).
- Phase 2.5 설계 검토: 아래 「위협 모델」·(1)·(2)·(2b)·(3) 절이 그것이다(세션 모델 직접 수행, 2026-09-30 초안 · 2026-10-03 재확인 — 착수 실측으로 (2) 를 갱신한다).

## 왜 이 slice 인가

6G 의 호출 상한은 **관문 하나**(`KonepsCallGate`)가 센다(D-6G-47). 관문을 지나지 않는 호출은 상한에도 원장에도 들어가지 않으므로,
「관문 밖으로 나가는 길이 없다」가 구조로 서야 한다. 6G 는 두 게이트를 세웠다.

- `java.net.http.HttpClient` 를 쥔 클래스 집합 == 등재 집합(정확 집합, production 전체).
- 우회 전송·반사 타입(`java.net.URL` 등 열 개)을 쥔 클래스 집합 == 빈 집합.

verifier r5 가 둘째 게이트의 구멍을 실측했다 — 변이 다섯이 초록이다.

| 변이 | 형태 |
|---|---|
| KA1 | `uri.toURL().readText()` — `java.net.URL` 을 **타입으로 쥐지 않고** 호출 사슬로만 지난다 |
| KA12 | `java.beans.Expression` 으로 메서드를 부른다 |
| KA13 | `ProcessBuilder("curl", …)` |
| KA14 | `java.nio.channels.AsynchronousSocketChannel` |
| KA15 | Spring `RestClient` |

원인은 둘이다. ⓐ 술어가 ArchUnit 의존의 **호출 대상 소유 타입**만 본다 — 인자·반환 타입은 보지 않는다. ⓑ 금지 집합이 **타입 이름 열거**라
목록 밖 전송 API 는 처음부터 대상이 아니다.

**출하 코드에 우회 호출은 없다** — 6G 실수집의 호출 수는 맞다. 비어 있는 것은 회귀를 잡을 자물쇠다.

## 실수집과의 관계

- 이 slice 는 **실수집을 막지 않는다**(D-6G-65 게이트 하드닝 분류).
- 이 slice 는 **test 와 정책 파일만** 바꾼다. 수집 코드·실행 상태 형식·원장 형식을 바꾸지 않으므로, 실수집이 진행 중이어도 머지할 수 있다.
- 착수 실측에서 출하 코드의 **실제 우회 호출**이 나오면 성격이 바뀐다 — 멈추고 보고한다(D-6G2b-8).

## 착수 실측 (2026-10-03 채움, 팀장 — 레인이 ArchUnit 로 재실측해 commands.md 「계약 대조」에 적는다)

| 항목 | 값 |
|---|---|
| base SHA | `1745a3e2` |
| production import 기준 전송 표면 참조(app·adapters·workflow·domain·procurement `src/main`) | `java.net.URI` 14 · `java.net.http.HttpClient` 5 · `HttpTimeoutException` 4 · `HttpResponse` 3 · `HttpRequest` 3 · `io.grpc.StatusRuntimeException` 3 · `StatusException` 3 · `ManagedChannel` 2 · `io.grpc.stub` 1 · `io.grpc.Status` 1 · `java.nio.channels.FileChannel` 2 · `WritableByteChannel` 1 · `FileLock` 1 · **`java.net.URL` 0**(초안의 「1」은 KDoc 문구) — import 기준이라 호출 사슬·인자·반환 타입은 레인이 ArchUnit 로 전수 |
| D-6G2b-8 의 「쓰이지 않는 `HttpClient` import 둘」 | **이미 없다**(`CollectionWiring`·`OpeningCollectionWiring` 모두 0) → production diff **0** 이 기대값 |
| 기존 게이트 자리 | `app/src/test/kotlin/bidvector/app/architecture/CollectionArchitectureGateTest.kt`(`referencersOf`, 반사 게이트 `moduleMustNotUseReflection` + 관측 집합 등식) · 정책 `config/quality/architecture-policy.properties` |
| `./gradlew check` test 수 | 2,607(6G-2f 종결) |
| A-2(반사 게이트 뿌리를 `adapters` 까지) | 레인이 넓혔을 때 걸리는 기존 참조 수를 **먼저 실측해 보고**하고, 구현은 그 보고 뒤 운영자 결정으로 — 그 전까지 D-6 은 현 뿌리 유지 |

### 착수 실측 (초안 시점 표)

| 항목 | 값 |
|---|---|
| base SHA | |
| production 이 참조하는 전송 표면 타입 전수(호출 대상의 소유·인자·반환 타입 포함) | (초안 시점 import 기준: `java.net.URI` 13 · `java.net.http.*` 12 · `io.grpc.*` 10 · `java.nio.channels.FileChannel`·`FileLock` 각 1 · `java.net.URLEncoder` 1) |
| 전송 표면을 쥔 production 클래스 집합 | (초안 시점: KONEPS 관문 계열 · 첨부 내려받기 · LLM 클라이언트 · ML gRPC 어댑터 · 실행 상태 잠금) |
| `./gradlew check` test 수 | (6G 판정 SHA 기준 2498) |

## 재사용 조사 (Phase 2)

| 후보 | 판정 | 근거 |
|---|---|---|
| `CollectionArchitectureRules.kt` 의 `referencersOf` 와 기존 두 게이트 | **확장** | 집합 등식 구조는 맞다. 의존을 모으는 범위와 금지 집합의 모양을 바꾼다 |
| ArchUnit `JavaCall.getTarget()` 의 `getRawParameterTypes()` · `getRawReturnType()` | **채택 후보** | 호출 대상의 인자·반환 타입을 의존으로 모은다. KA1 이 이것으로 잡히는지 착수 첫 실측 |
| 클래스 파일 상수 풀·서술자 전수(기존 「원시 키 리터럴」 게이트가 쓰는 방식) | **대안** | ArchUnit 이 놓치는 참조가 있으면 이쪽으로. 둘 다 쓰지 않는다 — 하나로 정한다 |
| `CollectionArchitectureGateCatchesViolationsTest` 의 음성 fixture 방식 | **채택** | 변이를 손으로 넣지 않고 fixture 클래스로 상시 잰다 |
| 기존 금지 의존 목록(`okhttp`·`retrofit`·`httpcomponents`·`ktor`) | **유지** | 빌드 의존 단계의 금지. 이 slice 는 클래스패스에 **이미 있는** 표면을 다룬다 |
| 네트워크 sandbox·SecurityManager·에이전트 | 기각 | 런타임 장치는 CI 게이트가 아니고 JDK 21 에서 SecurityManager 는 폐기 예정이다 |

## 결정

| ID | 결정 | 근거 |
|---|---|---|
| **D-6G2b-1** | **금지 집합을 타입 이름이 아니라 패키지 뿌리로 둔다.** 전송 표면 = `java.net..` · `javax.net..` · `java.nio.channels..` · `java.rmi..` · `javax.naming..` · `org.springframework.web.client..` · `org.springframework.web.reactive..` · `org.springframework.http.client..` · `io.grpc..` · `io.netty..` 와 낱개 타입 `java.lang.ProcessBuilder` · `java.lang.Runtime`(`exec`) · `java.beans.Expression` · `java.beans.Statement`. 뿌리 목록은 정책 파일에 두고, **착수 실측에서 클래스패스의 전송 가능 패키지를 전수**해 빠진 뿌리를 더한다 | 열거는 새 API 하나마다 열린다. 뿌리는 그 패키지에 새 타입이 생겨도 닫혀 있다 |
| **D-6G2b-2** | **의존을 모으는 범위를 넓힌다.** production 클래스가 참조하는 타입 = 필드·매개변수·반환·지역·상속 타입 + **호출 대상의 소유 타입 · 인자 타입 · 반환 타입** + 생성자 호출 + 클래스 객체 참조. KA1(`URI.toURL()` 의 반환 타입이 `URL`)이 이것으로 잡혀야 한다 | 타입을 쥐지 않고 호출 사슬로만 지나는 길이 r4·r5 두 번 열렸다 |
| **D-6G2b-3** | **허용은 (클래스, 타입) 쌍의 정확 집합이다.** 관측한 (production 클래스, 전송 표면 타입) 쌍 집합 == 정책 파일의 등재 쌍 집합. 클래스만 등재하면 그 클래스가 새 전송 타입을 더 쥐어도 초록이다. 값 타입(`java.net.URI` · `java.net.URLEncoder` · `java.nio.channels.FileChannel` · `FileLock` · `OverlappingFileLockException`)처럼 **바이트를 밖으로 내지 못하는** 타입은 「무해 타입」으로 따로 등재하고 쌍 등식에서 뺀다 — 무해 타입 목록도 정확 집합(관측과 같아야 한다) | 6G 의 보유자 게이트는 클래스 단위 등식이라 한 칸 헐겁다 |
| **D-6G2b-4** | **등재 쌍마다 용도를 적는다**(정책 파일 주석이 아니라 키 구조로 — 예: `collection.transport.holders.<용도>=`). 용도 어휘는 닫힌 셋: `koneps-gate` · `attachment` · `llm` · `ml-grpc` · `run-state-lock`. `koneps-gate` 밖의 용도가 KONEPS 주소로 호출하지 않는다는 것은 이 게이트가 재지 못한다 — **경계 밖**으로 선언한다(아래 위협 모델) | 정적 분석은 목적지를 모른다. 모른다고 적는 편이 막는다고 적는 것보다 낫다 |
| **D-6G2b-5** | **6G 의 두 게이트를 이 게이트 하나로 합친다.** `collection.http-client.*` 와 `collection.transport-bypass.*` 는 새 쌍 등식의 부분집합이 되므로 없앤다. 합치기 전후에 6G 의 변이(KA2·KA3·KA4)가 여전히 RED 임을 잰다 | 같은 것을 재는 게이트 셋은 하나가 낡는다 |
| **D-6G2b-6** | **반사 봉쇄는 기존 게이트를 그대로 쓴다**(`workflow`·`app` production 의 리플렉션 API 참조 금지). 뿌리를 `adapters` 까지 넓힐지는 착수 실측으로 정한다 — 넓혔을 때 걸리는 기존 참조가 0 이면 넓히고, 있으면 그 목록과 함께 운영자에게 묻는다 | `Class.forName` 으로 이름을 문자열로 지으면 타입 참조가 없다 |
| **D-6G2b-7** | **음성 fixture 로 상시 잰다.** KA1·KA12·KA13·KA14·KA15 와 착수 시 고안한 새 우회 ≥5 를 test 소스의 fixture 클래스로 두고, 게이트 술어가 각각을 **걸러 냄**을 단언한다. 술어를 고치지 않고 fixture 만 빼면 그 단언이 RED | verifier 가 매 라운드 손으로 심던 것을 저장소에 둔다 |
| **D-6G2b-8** | **출하 코드 변경은 import 정리뿐이다.** 쓰이지 않는 `import java.net.http.HttpClient` 둘(code-review r5 L-6 — 6G 표적 수정이 이미 지웠으면 해당 없음) 밖의 production diff 는 없어야 한다. 착수 실측에서 **등재되지 않은 실제 전송 호출**이 나오면 멈추고 보고한다 | 게이트 slice 가 제품 거동을 바꾸면 범위가 샌다 |
| **D-6G2b-9** | **게이트 등재 meta-gate 에 올린다.** 새 게이트 test 를 `AppGateRegistrationTest` 계열의 등재 목록에 넣는다 — 빠뜨리면 그 test 가 RED | 6G r5 에서 아키텍처 게이트가 한 커밋 동안 붉은 채 지나갔다(`check` 미실행). 등재는 게이트가 돌지 않는 사고를 막는다 |

## 계약 갱신 r1 (2026-10-03, 팀장 — 착수 실측·구현 보고 수령)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6G2b-10** | **in_scope 에 음성 fixture 경로 `app/src/test/kotlin/bidvector/archfixture/violating/transport/**` 와 `config/quality/gate-tests.properties` 를 더한다.** 초안 in_scope 가 빠뜨린 자리 — fixture 는 기존 관례대로 `archfixture/violating/` 아래가 맞다 | 레인 계약 대조 ④ |
| **D-6G2b-11** | **A-2 운영자 결정 2026-10-03: 반사 게이트 뿌리를 `adapters` 까지 넓히고, 기존 참조 9건(`kotlin.reflect.KClass` 2 · `java.lang.Class` 멤버 7 — `getSimpleName` 6, `getResourceAsStream` 1)을 (클래스, 멤버) 쌍 정확 집합으로 허용 등재한다.** 별도 커밋. 변이: 쌍 하나 제거 → RED, 새 반사 참조 → RED | 전송 게이트가 정적 분석이라 문자열 이름 반사가 유일한 구멍이고 adapters 가 전송 코드가 사는 모듈 |
| **D-6G2b-12** | **금지 뿌리 15 → 17.** 6G 의 `transport-bypass.types` 에 있던 `java.lang.reflect.Method`·`java.lang.invoke.MethodHandles` 가 계약 뿌리 목록만으로는 빠진다(실측: `RogueMethodHandleInvoke` 를 6G 는 잡고 계약 뿌리는 못 잡음) → `java.lang.reflect`·`java.lang.invoke` 를 뿌리로. 클래스패스 실측으로 `jakarta.websocket`·`org.apache.tomcat.websocket`·`com.sun.net`·`sun.net` 추가(관측 0). `io.netty` 는 classpath 에 없으나 뿌리로 유지 | 6G 를 잃지 않는다(D-5 의 「합치기 전후 변이 유지」) |
| **D-6G2b-13** | **무해 타입 목록을 두지 않는다 — 관측 쌍 전수(63)를 용도와 함께 등재.** 값·예외 타입이 14종으로 (3) 의 열 개 문턱을 넘었고, 문턱의 지시대로 다시 보니 무해 목록은 「그 타입을 모든 클래스에서 자유롭게」 해 게이트를 헐겁게 하는 쪽이었다. 쌍 등식의 취지는 그대로이고 더 조인다(무해 타입 0) | 레인 ⓓ · (3) 과잉 판단 |
| **D-6G2b-14** | **낱개 타입은 `java.beans` 뿌리 + `java.lang` 넷(`ProcessBuilder`·`Runtime`·`Process`·`ProcessHandle`).** (1) 의 「`java.lang` 을 뿌리로 금지할 수 없어서」는 `java.beans` 에 해당하지 않는다 | 레인 ③ |
| **D-6G2b-15** | `policy.version` 7 → 8(정책 파일 구조 변경: 두 게이트 키 삭제 + `collection.transport.*` 여섯). `OPEN-6G-GATE-REGISTRY-KONEPS` 는 **닫지 않는다** — 등재는 했으나 그 OPEN 은 게이트 장부 전반이라 쌍 등식이 갈음하지 못한다(6G-2c 후보) | 레인 ⑤·⑦ |
| **D-6G2b-16** | **RED 의 형태**: 「컴파일 안 되는 RED 커밋」 대신 **같은 fixture 뿌리를 6G 술어 형태(소유 타입만 + 타입 이름 열거)로 돌린 실측**(19 중 놓침 14 · 변이 타입으로만 5)을 RED 로 받는다 — 게이트 slice 에서 「옛 술어가 못 잡는다」가 곧 RED 다. 변이 셋(깊은 수집 → 소유 타입만 4 failed · 뿌리 17 → 3 **1 failed, 양성 쪽 전건 초록** · 용도 하나 제거 3 failed) — 둘째가 D-7 음성 fixture 의 존재 이유를 실측으로 보인다 | 레인 보고 |

## 계약 갱신 r2 (2026-10-03, 팀장 — A-2 집행 수령)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6G2b-17** | **반사 허용은 (클래스, 멤버/타입) 쌍 12 다 — 운영자 결정의 「9건」 + 앞 판이 전역 `getName` 허용으로 덮던 3**(`RunStateDurabilityKt` · `CollectionRunnerKt` · `OpeningCollectionLinesKt`). 전역 허용을 없앤 결과이고 등재하지 않으면 그 셋이 RED. 멤버 이름은 `getName` 3 · `getSimpleName` 6 · `getResourceAsStream` 1 + `KClass` 2 — 전부 값 획득 아님 | 쌍 정확 집합의 귀결 |
| **D-6G2b-18** | **반사 뿌리는 별도 키 `collection.reflection.roots`**(`bidvector.workflow,bidvector.app,bidvector.adapters`) — 원문 값 획득 뿌리와 겹쳐 적으면 한쪽을 넓히는 편집이 다른 쪽을 조용히 넓힌다. 앞 판 키 둘(`allowed-referencers`·`class-allowed-members`) 삭제. in_scope 에 `archfixture/violating/adapters/**` 추가 | 레인 ② |
| **D-6G2b-19** | **참조 수집 정의를 하나로**(`referencedTypeNames` 공유, `outermostClass` 공유) — 수집 범위를 좁히는 편집이 두 게이트의 양성 대조를 동시에 RED 로. 변이 셋: 쌍 제거 2 failed · 새 반사 참조 2 failed · **뿌리를 앞 판으로 좁힘 1 failed(규칙 초록, 쌍 등식만 RED)** — 규칙과 등식이 서로 다른 것을 든다 | 레인 실측 |
| **D-6G2b-20** | **반사 뿌리는 production 전체가 아니다** — `procurement`·`decision`·`qualification`·`settlement`·`shared-kernel` 은 밖(domain 계열, 다른 게이트가 프레임워크·반사 참조를 금지하나 이 쌍 등식으로는 재지 않음). 운영자 결정 문면(`adapters` 까지)을 넘지 않는다 → **`OPEN-6G2B-REFLECTION-ROOT-DOMAIN`** 신설(6G-2c 후보: 그 모듈들의 기존 참조 실측 뒤 넓힐지) | 레인 알려진 제한 |
| **D-6G2b-21** | **판정 SHA 는 이 갱신 커밋**(레인 산출물 `cc3fd1c7` · evidence `79091d02` 뒤). 게이트 술어를 바꾸는 slice 이므로 **수정 라운드의 모든 커밋이 표적 재검증 대상**. verifier 표적: 음성 fixture 21+2 전수 재실행 · 새 우회 고안 ≥3(fixture 밖 — 예: Kotlin `kotlin.io.path` 경유 네트워크 아님 대조 · `java.net.URI.toURL` 외 체인 · 인터페이스 타입으로 받은 전송 객체 · 람다/SAM 경유 · 제네릭 인자 안의 전송 타입 `List<HttpClient>`) · 6G 변이 KA2~4 유지 · 뿌리 축소 변이 · 쌍 등식 양방향 · 반사 12쌍 변이 · acceptance `check` 1회 · rollback 두 술어(실측 HEAD `cc3fd1c7`, 복원 13 경로, 공유 hunk 넷 — A-2 쪽 먼저) · evidence 위생 · 새 public 표면(정책 키 순증 7, production 0) | |

## 계약 갱신 r3 (2026-10-03, 팀장 — verifier r1 not-ready(H-1) · code-reviewer medium 5 수령, 재작업 1/5)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6G2b-22** | **운영자 결정 2026-10-03(vr H-1): 전송 표면 게이트를 「금지 뿌리 열거」에서 「허용 뿌리 목록 + 기본 거부」로 전환한다.** `bidvector.app`·`bidvector.workflow`·`bidvector.adapters` production 클래스가 참조하는 **`bidvector..` 밖 모든 타입**의 패키지 뿌리는 정책 파일의 닫힌 허용 집합(`collection.external.allowed-roots`, 모듈별 또는 공통)에 있어야 하고, 밖이면 신고. 허용 집합은 **관측 전수와의 등식**(관측 뿌리 ⊂ 허용 · 허용 ⊂ 관측 — 쓰이지 않는 허용 뿌리도 RED)으로 잠근다. 전송 뿌리 열일곱은 허용 집합에 **들어가지 않고** 그 안의 (클래스, 타입) 쌍 등식(D-3·13)으로만 지난다 — 즉 두 층: 허용 뿌리(구성) + 전송 쌍(구성). 선택지 셋(허용 목록 · 거부 뿌리 넷 추가+OPEN · 경계 밖 선언) 중 첫째 | vr H-1: `java.util.logging.SocketHandler`·`DocumentBuilder.parse(String)`·`JMXConnectorFactory`·`JEditorPane(String)` 변이 초록 — 열거는 목록 밖을 못 잡는다(CLAUDE.md 「열거인가 구성인가」) |
| **D-6G2b-23** | **vr M-1(등재 보유자 안 String 시그니처 raw send + 등재 밖 호출자가 초록)** 은 (클래스, 타입) 해상도 밖이다 → **알려진 제한 + `OPEN-6G2B-HOLDER-INTERNAL-SURFACE`**(6G-2c 후보: 등재 보유자의 public 멤버 중 전송 타입을 받지 않는 send 류를 세는 (클래스, 멤버) 층). 이 slice 에서는 해상도를 올리지 않는다 — 보유자 28 의 멤버 전수가 또 한 라운드다 | 분류 게이트 하드닝 |
| **D-6G2b-24** | **cr M-1(중첩 이름 접기 비대칭)**: `referencedTypeNames` 가 ArchUnit 해소 여부에 따라 `HttpResponse$BodyHandler` 는 남기고 `$BodyHandlers`·`HttpClient$Builder` 는 접는다 → **접기를 한 규칙으로**(중첩은 항상 바깥 클래스로 접거나, 항상 그대로 두거나 — 레인이 등재 쌍 수 변화와 함께 고른다), KDoc 과 일치시킨다. **cr M-2**: 제네릭 인자(`List<HttpClient>`·`Supplier<HttpClient>`) · SAM 람다 · 인터페이스/`Any` 로 받은 전송 객체 · 어노테이션 인자를 **상시 음성 fixture** 로(verifier 가 임시 변이로 RED 를 실측한 것을 고정). **cr M-4**: 수입된 production 모집단 고정 단언 한 줄(아홉 모듈 전부 — 모듈 하나가 classpath 에서 빠지면 RED). **cr M-3**(뿌리 전수가 CI 에서 재측정 안 됨)은 D-22 허용 목록 전환으로 **소멸**(새 패키지는 기본 거부) | code-reviewer |
| **D-6G2b-25** | **H-1 변이 넷 + 레인 고안 ≥2 를 상시 음성 fixture 로**(`java.util.logging.SocketHandler` · `javax.xml.parsers.DocumentBuilder.parse(String)` · `javax.management.remote.JMXConnectorFactory` · `javax.swing.JEditorPane(String)` · 예: `java.awt.Desktop.browse` · `java.util.ServiceLoader` 경유 외부 구현 · `javax.script`). 허용 목록 전환 뒤 이들이 전부 신고되고, 허용 뿌리 하나를 빼면 양성 등식 RED, 하나를 더하면(미관측) RED 임을 변이로 | D-22 의 민감도 |
| **D-6G2b-26** | **장부 일괄(판정 뒤)**: checklist 마지막 산출물 `3e670054` → 재측정 HEAD · 제한 8 해소 · 키 수(실제 +12/−8) · commands.md 계약 대조 머리글·4·8 · D-16 RED 실측 명령 추가(vr L-3) · `RogueMethodHandleInvoke` KDoc(vr L-4) · `mustReport` 부분 문자열 → 정확 매치(vr L-5, cr ④⑤) · cr low ①③⑥⑦⑧⑨⑪. 크기 게이트 바이트(21% 초과)는 D-6G2f-17 과 같은 사실 등재 | 장부층 |
| **D-6G2b-27** | **r2 판정**: 게이트 술어를 바꾸는 slice 라 **수정 라운드의 모든 커밋이 표적 재검증**. verifier r2 표적: H-1 변이 넷 + 새 fixture 전수 RED · 허용 뿌리 등식 양방향 변이 · 전송 쌍 등식 유지(S1~S7) · 6G 변이 KA2~4 · 반사 변이 셋 · M-4 모집단 단언 변이(모듈 제외) · 접기 규칙 변이 · acceptance `check` 1회 · rollback 두 술어(복원 목록 재산출) · evidence 위생. 재작업 **1/5** | |

## 계약 갱신 r4 (2026-10-03, 팀장 — 수정 라운드 착수 실측 수령)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6G2b-28** | **허용 집합의 입도는 「정확 패키지」, 등식은 「모듈별」.** 실측(base, production 클래스 app 98 · workflow 376 · adapters 371): 정확 패키지 app 55 · workflow 21 · adapters 51 = **127**(공통 합집합 82) · 2세그먼트 뿌리 76 · 3세그먼트 102. 접두 뿌리를 쓰면 `java.util`·`javax.xml` 이 허용돼 H-1 변이 둘(`java.util.logging.SocketHandler`·`javax.xml.parsers.DocumentBuilder`)이 그대로 들어오고, 막으려면 거부 하위를 열거해야 한다 — H-1 이 벌한 방향. 정확 패키지는 거부 목록 없이 기본 거부가 잡는다(`java.util.logging` 관측 0 · `javax.xml` 은 `.stream` 둘뿐). 모듈별 등식 `허용(m) == 관측(m)` 이 공통 `허용 == ∪관측` 보다 엄격(다른 모듈만 쓰는 패키지 참조가 공통에서는 초록). 정책 키 `collection.external.allowed-packages.<module>=`, 모듈 목록은 M-4 모집단 단언과 같은 자리에서 읽는다. **두 층**: 전송 표면 타입이면 허용 패키지를 보지 않고 (클래스,타입) 쌍 등식만 · 아니면 그 타입의 패키지 ∈ 허용(m). `java.lang` 허용 아래의 `ProcessBuilder`·`Runtime` 처럼 **`java.util.ServiceLoader` 를 전송 낱개 타입에 추가**(production 관측 0) | 레인 실측 2026-10-03 18:49 |
| **D-6G2b-29** | **중첩 이름 접기 = 이름 기준 첫 `$` 절단(NAMECUT).** 쌍 수: 현(ArchUnit `enclosingClass`) 63 · NAMECUT **60** · 접지 않음 81. 접지 않으면 Kotlin 합성 람다 클래스 이름이 정책 파일에 들어와 무관한 편집마다 철자가 바뀐다. NAMECUT 은 현 등재에서 `HttpResponse$BodyHandler` 쌍 셋만 `HttpResponse` 로 접히고 나머지 60 은 글자 그대로(`namecut − current = ∅`) — 결정적, KDoc 과 일치 | cr M-1 |
| **D-6G2b-30** | **수입된 production 모집단 = 아홉 모듈**(adapters · app · decision · procurement · qualification · settlement · sharedkernel · strategy · workflow) 고정 단언 — 모듈 하나가 classpath 에서 빠지면 RED | cr M-4 |

## 계약 갱신 r5 (2026-10-03, 팀장 — 수정 라운드 1 수령, 판정 r2)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6G2b-31** | in_scope 에 `archfixture/violating/workflow/external/**` 와 `app/src/test/resources/archunit.properties` 추가(레인 계약 대조 11). 수정 라운드 결과: 허용 집합 모듈별 127 · fixture 위반 19 → 31(1층 7 + 2층 5) · 변이 열(⑦ 허용 패키지 제거 2 failed · ⑧ 미관측 허용 추가 **등식만 1 failed** · ⑨ 접기 되돌림 2 failed · ⑩ `ServiceLoader` 낱개 제거 **2층만 1 failed**) · test 2,633 · production diff 0. 수집 쪽 정확 매치는 「이름 경계 일치」(`$` 경계) — 기존 단언 하나가 느슨한 비교에 의존했음이 드러나(`RawObservationStore.append` 가 `append$default` 로만 잡힘) 경계 규칙으로 통과. `qualityBaseline` 은 up-to-date 로 실행된 측정이 아님(사실 등재) | 레인 보고 |
| **D-6G2b-32** | **판정 SHA r2 = 이 갱신 커밋**. verifier r2 표적(D-27 그대로 + 수정 라운드 커밋 전수 `0f11117a`·`f77c319a`·`0c6f36b2`·`de86738d`·`0fb24e8d`·`541b5065`·`130dc4c9` 표적 재검증): H-1 변이 넷 + 새 fixture 12 전수 · 허용 등식 양방향(⑦⑧) · 두 층 분기(⑩) · 접기(⑨) · 전송 쌍 S1~S7 · KA2~4 · 반사 셋 · 모집단 단언(모듈 제외 변이) · **새 우회 ≥2**(허용 패키지 안에서 바이트를 내보내는 길 — 예: `java.io` 로 `/dev/tcp` 류·`java.nio.file.Files` 의 원격 FS·`javax.sql.DataSource` 의 JDBC URL(경계 밖 선언 확인)) · acceptance `check` 1회 · rollback(실측 HEAD `130dc4c9`, 복원 14 + 공유 hunk 5) · evidence 위생. 재작업 1/5 | |

## 계약 갱신 r6 (2026-10-03, 팀장 — 판정 SHA 재고정 · code-reviewer r2 수령)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6G2b-33** | **사실 선언(레인 경계)**: r5 가 판정 SHA 를 `d1b2b7f0` 로 고정한 시점에 팀장의 앞선 지시(「모듈 목록을 M-4 단언과 같은 자리에서 읽게」)가 레인에서 집행 중이었다 → 동결 뒤 커밋 다섯(`28c789c6` 정책 · `9431ead0` 술어·단언 · `dca888aa` ktlint · `137d5615`·`a3dc58e1` evidence). 이력은 되쓰지 않는다. **판정 SHA r2 = 이 갱신 커밋**(a3dc58e1 뒤). 표적 커밋 목록에 `28c789c6`·`9431ead0`·`dca888aa` 추가(앞 둘은 술어/정책). 교훈은 하네스 메모리로: 동결은 「미완 항목 없음」 보고 뒤에 건다 | 팀장 책임의 경합 |
| **D-6G2b-34** | **판정 대상 모듈은 `layer.*` 선언에서 도출**(`collection.external.modules` 키 삭제, 모집단 기대값도 `policy.allModules`) — 손 목록 셋이 하나로. 변이 ⑪: `layer.application` 을 비우면 **넷 RED**(모집단 · 모듈 키 등식 · 1급 패키지 집합 · 의존 방향). 새 public 표면 16 → 15 | cr r2 M-1 · 팀장 지시 |
| **D-6G2b-35** | **cr r2 M-2**: `outermostClass()` KDoc 의 「두 접기 규칙의 결과가 같다」는 다른 게이트의 등재(`collection.key-hash.holders` 의 `NoticeKeyHash$Companion`, `app.injection.allowed-types` 의 `Resolution$Resolved`)로 반증된다 → KDoc 을 「**이 게이트의 관측(전송·바깥 참조·반사)에서** 같다; 다른 게이트는 `enclosingClass` 접기를 그대로 쓰며 옮기려면 그 등재를 재관측해야 한다」로 정정(장부 일괄). 다른 게이트의 접기 통일은 **`OPEN-6G2B-FOLDING-UNIFICATION`**(6G-2c 후보) | 뒤 slice 가 문장을 믿고 옮기면 등식이 깨진다 |
| **D-6G2b-36** | **cr r2 M-3**: 허용 패키지 **안**의 바이트 출구(`java.io` 의 `/dev/tcp`·FIFO, `java.nio.file` 원격 FS)는 1층·2층 어느 쪽도 못 잡고 낱개 열거(`ServiceLoader` 선례)로만 닫힌다 → **`OPEN-6G2B-ALLOWED-PACKAGE-EGRESS`** 등재(위협 모델 「방어하지 않는 것」에 「허용 패키지 안의 파일 시스템 경유 출구」 추가). 낱개 목록을 지금 늘리지 않는다 | 열거 축의 잔여 — 경계로 처리하고 OPEN |
| **D-6G2b-37** | **verifier r2-b 표적**(r2 는 `d1b2b7f0` clone 에서 완료): `28c789c6`·`9431ead0` 델타 — 변이 ⑪ 재현 · `collection.external.modules` 잔존 참조 0 · 허용 키 집합 == 도출 모듈 집합 단언 · rollback 실측 HEAD `dca888aa`, `dca888aa..<판정 SHA>` 복원 경로 diff 빈 출력 · 공유 hunk 넷 · clean-tree. cr r2 low 열하나는 장부 일괄 | |

## 위협 모델 — 6G-2b 고유 경계 (Phase 2.5 (0))

**방어하는 것**: 저자가 `bidvector..` production 코드에 **관문을 지나지 않는 바깥 호출**을 더하는 것 — JDK · Kotlin 표준 라이브러리 · 클래스패스에
이미 있는 라이브러리(Spring · gRPC · Netty)의 API 를 **직접 참조**해서. 타입을 쥐든 호출 사슬로만 지나든 같다.

**방어하지 않는 것(경계 밖)**:
- 게이트 test · 정책 파일 · 빌드 스크립트를 고치는 저자(M1/1A 의 경계 승계).
- 등재된 보유자(첨부 · LLM · ML gRPC)가 **KONEPS 주소로** 호출하는 것 — 정적 분석은 목적지를 모른다. 통제는 그 보유자들의 주소가 설정에서만 오고
  KONEPS 주소 설정을 읽는 자리가 관문 배선 하나라는 기존 게이트(「서비스 키 원문 설정은 배선 한 곳만 참조한다」)다.
- 이름을 문자열로 짓는 반사 가운데 기존 반사 게이트의 뿌리 밖(D-6G2b-6 에서 정한다).
- JNI · 네이티브 · JDBC 를 거친 DB 쪽 네트워크 기능 · 새 빌드 의존을 더하는 것(빌드 의존 게이트 소관).

이 경계는 6G 의 D-6G-62 문면(「관문 밖에서 … 금지. 변이 KA1~KA3 RED」)을 줄이지 않는다 — KA1 을 포함해 그 요구를 실제로 채운다.

### (1) 열거인가 구성인가

금지는 **패키지 뿌리**(구성)다. 허용은 **관측과의 등식**(구성)이다. 낱개 타입 넷(`ProcessBuilder` 등)만 열거이고, 그것은 `java.lang` 을 뿌리로
금지할 수 없어서다 — 이 넷의 근거를 계약에 적고 새 낱개 타입을 더할 때는 계약을 갱신한다.

### (2) 우회 — 다섯 이상 (착수 시 실측으로 갱신)

1. KA1 — 호출 사슬의 반환 타입으로만 지난다. ← D-6G2b-2.
2. KA12 — `java.beans.Expression`. ← 낱개 타입 금지.
3. KA13 — 프로세스 실행. ← 낱개 타입 금지.
4. KA14 — 비동기 채널. ← `java.nio.channels..` 뿌리.
5. KA15 — Spring 클라이언트. ← `org.springframework.web.client..` 뿌리.
6. Kotlin 확장 함수(`java.net.URL.readText()` — 소유 타입은 `kotlin.io.TextStreamsKt`). ← 인자 타입이 `URL` 이므로 D-6G2b-2.
7. 등재된 보유자 클래스 안에 새 전송 타입을 더 쥔다. ← D-6G2b-3 쌍 등식.
8. typealias 로 이름을 가린다(6G KA4 — 이미 RED). ← 바이트코드에는 원 타입이 남는다. 회귀로 유지.
9. 전송 표면을 쥔 **test 지원 코드**를 production 소스셋에 둔다. ← 뿌리가 production 전체다.
10. `com.sun.net.httpserver` · `sun.net..` 내부 API. ← 착수 실측에서 뿌리에 더할지 정한다(수신 전용이면 무해 타입).

### (2b) 값 획득 축

새 public 표면은 정책 파일의 키뿐이다. 키를 비우면 등식이 RED 가 되므로 「비워서 통과」는 없다. production 의 새 public 선언은 0 이어야 한다.

### (3) 과잉·미달

- 과잉 위험: 뿌리가 넓어 무해한 참조가 많이 걸릴 수 있다. 무해 타입 목록이 열 개를 넘으면 뿌리 설계를 다시 본다(착수 실측 뒤 판단).
- 미달 경계: 목적지 구분은 하지 않는다(경계 밖 선언).

## 운영자 승인 필요 (착수 전)

- **A-1 착수 시점**: 6G 머지 뒤 아무 때나. 실수집과 병행 가능하다(수집 코드 무변경). 다만 Gradle `check` 가 실수집 실행과 겹치지 않게 한다.
- **A-2 D-6G2b-6**: 반사 게이트의 뿌리를 `adapters` 까지 넓힐지 — 착수 실측 결과와 함께 묻는다.

## in_scope

- `app/src/test/kotlin/bidvector/app/architecture/**`
- `app/src/test/kotlin/bidvector/archfixture/violating/transport/**`(음성 fixture — 6G·6F-8 fixture 가 사는 자리, 계약 갱신 r1 D-6G2b-10 으로 추가)
- `app/src/test/kotlin/bidvector/archfixture/violating/adapters/**`(A-2 음성 fixture — fixture 의 패키지가 게이트 뿌리를 정하므로 `adapters` 층 반사는 이 자리여야 한다, r2 D-6G2b-18)
- `app/src/test/kotlin/bidvector/archfixture/violating/workflow/external/**`(모듈별 허용 집합을 모듈마다 재는 fixture, r5 D-6G2b-31)
- `app/src/test/resources/archunit.properties`(ArchUnit `failOnEmptyShould` 핀 — cr L-3, r5 D-6G2b-31)
- `config/quality/architecture-policy.properties`
- `config/quality/gate-tests.properties`(게이트 test 등재, D-6G2b-9)
- `app/src/main/kotlin/bidvector/app/wiring/CollectionWiring.kt` · `OpeningCollectionWiring.kt`(쓰이지 않는 import 제거만)
- `reports/evidence/m6/6g2b/**` · `milestone-6.md`(착수·종결 문단만)

**out_scope**: `adapters/src/main/**` · `workflow/src/main/**` · `procurement/src/main/**`(수집·관문·실행 상태 코드 무변경) · `ml-engine/**` ·
`db/migration/**` · `contracts/**` · `docker/**` · `reports/evidence/m6/6g/**`.

## acceptance

**정정(2026-10-03)**: 현 CI `check` job 의 명령은 `./gradlew --no-daemon check` · `./gradlew --no-daemon qualityBaseline` · `./tools/one-command-check.sh`(`.github/workflows/ci.yml`)다 — 아래 초안의 `clean`·`--no-build-cache` 는 쓰지 않는다. 게이트 술어를 바꾸는 slice 이므로 **변이가 실제 적용됐는지 `git diff --numstat` 로 먼저 재고** 돌린다(CLAUDE.md 2026-09-19).

CI `check` job 명령 그대로(`clean` → `check --no-build-cache` · `qualityBaseline` · `one-command-check.sh`). container job 은 production 코드가
바뀌지 않으면 트리 동일성으로 갈음한다(import 제거만 있으면 실행). `commands.md` 에 음성 fixture 별 결과와 등재 쌍 수를 적는다.

## rollback

in_scope 경로 한정 `git restore --source=<base> --staged --worktree --`. 공유 파일 `architecture-policy.properties` 는 **커밋 해시 hunk 격리** —
다른 slice 가 같은 파일을 만지므로 수동 절차를 미리 적는다. `milestone-6.md` 는 문단 블록 삭제. 버릴 clone 에서 ①~⑥ 실측, ⑥ 은 게이트 test 다.

## 리뷰 레인

`verifier`(음성 fixture 밖의 새 우회 고안 · 등재 쌍이 관측과 같은지 · 합친 게이트가 6G 변이를 여전히 잡는지) + `code-reviewer`(sonnet).
**게이트 술어를 바꾸는 slice 이므로 수정 라운드의 모든 커밋이 severity 와 무관하게 표적 재검증 대상이다.** Codex 없음.

## 하네스 레인 변경

(착수 뒤 리뷰 요청 시점마다 등재)

- **팀장(2026-10-03, 착수)**: `milestone-6.md` 착수 문단(공유 파일 — rollback 은 hunk 역적용). 계약 파일 이 커밋.

## OPEN 수령·신설 (예상)

| OPEN | 처분 |
|---|---|
| `OPEN-6G-TRANSPORT-GATE-HARDENING` | **이 slice 가 닫는다** |
| `OPEN-6G-GATE-REGISTRY-KONEPS` | D-6G2b-9 로 함께 닫는지 착수 실측에서 판단 |
| (신설 가능) `OPEN-6G2B-DESTINATION-BINDING` | 등재 보유자의 목적지 구분 — 경계 밖으로 둔 것을 등재만 |
| `OPEN-6G2E-APP-HTTP-IMPORT-CONTRACT` | **6G-2c 로 이관(2026-10-03)** — Python `import-linter` 계약(`ml-engine/pyproject.toml`)이라 Python 레인·`ml-engine` job 소관이고 이 slice 는 Kotlin `check` job 하나로 닫는다. 초안 처분 「이 slice 가 닫는다」의 내용: `ml-engine/pyproject.toml` import-linter 「app 은 DB·HTTP·업무 모듈을 모른다」 계약의 `forbidden_modules` 가 서드파티 다섯만 열거해 `urllib.request`(표준 HTTP)가 지나간다(6G-2e cr r2 M). 금지 목록을 패키지 뿌리·표준 HTTP 모듈까지, test 쪽 AST sweep(6G-2e 임시 자물쇠)과 등식 |
