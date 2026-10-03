# M6/6G-2b — 명령과 실측

> 출력 전문을 싣지 않는다(핵심 결과 한 줄). **마지막 HEAD 의 게이트 결과 정본은 verifier 와 PR 조치
> 코멘트**다. 이 slice 는 test·정책 파일만 바꾼다 — 실 KONEPS 호출 0, 실행 상태 디렉터리 접근 0.

## acceptance (CI `check` job 의 명령 그대로)

| 완료(UTC) | 명령 | exit | 핵심 결과 |
|---|---|---|---|
| 2026-10-03T07:10Z | `./gradlew --no-daemon check` | 0 | 전 모듈 2,617 test · 실패 0 · skip 4(base 2,607 에서 +10) |
| 2026-10-03T07:11Z | `./gradlew --no-daemon qualityBaseline` | 0 | 33 task up-to-date(production 입력 무변경의 방증) |
| 2026-10-03T07:18Z | `./tools/one-command-check.sh` | 0 | Kotlin 전건 + Python 전건(pytest 1,379 · wheel 1) |

**실측 HEAD `3e670054`**(마지막 산출물 커밋). 세 줄 가운데 첫 줄만 test 를 실제로 실행했다 — 스크립트가
도는 `check`·`qualityBaseline` 은 같은 트리라 Gradle 입력 등식으로 up-to-date 였고(12s·6s), 그 사실을
숨기지 않고 적는다. test 수 증감은 새 test 12(`TransportSurfaceGateTest` 7 + `TransportSurfaceGateCatchesViolationsTest`
5) − 지운 test 2(6G 의 두 게이트 단언) = +10.

첫 `check` 호출은 ktlint 20건으로 exit 1 이었다(연속 KDoc 둘 · 체인 연속 9 · 함수 시그니처 7 · dangling
KDoc 2). 형식만이고 술어·fixture 의미는 바뀌지 않았다 — 파일 머리 KDoc 을 줄 주석으로 바꾸고
`ktlintTestSourceSetFormat` 을 돌린 뒤 exit 0.

## 착수 실측 (ArchUnit 전수 — scope.md 의 import 기준 표를 갈음한다)

production 클래스 1,390 기준. 수집 범위는 의존 그래프 + **호출 대상의 소유·인자·반환 타입** + 필드 접근의
필드 타입이다.

| 축 | 값 |
|---|---|
| (클래스, 전송 표면 타입) 쌍 | **63** (보유자 클래스 28 · 타입 19종) |
| 소유 타입만 보는 수집 | 49 — 깊은 수집이 **14 쌍**을 더한다 |
| 용도 분포 | koneps-gate 23 쌍/16 클래스 · ml-grpc 19/5 · attachment 8/2 · llm 7/2 · run-state-lock 6/3 |
| `java.net.URL` 참조 | **0**(초안의 「1」은 KDoc 문구였다) |
| 실제 우회 호출 | **없음** — `Socket`·`ProcessBuilder`·`Runtime`·`RestClient`·`java.beans` 전부 0 |
| 쓰이지 않는 `HttpClient` import 둘 | 이미 없다 → production diff **0**(기대값 그대로) |
| 무해 타입 목록 | **두지 않았다**(아래 「계약 대조」 2) |

깊은 수집이 더하는 14 쌍: `HttpResponse$BodyHandler` 셋 · 첨부·LLM·주소 조립의 `java.net.URI` 둘 ·
gRPC `Channel`·`CallOptions`·`Metadata`·`stub.AbstractStub` 여덟 · `WritableByteChannel` 하나.

### A-2 (반사 게이트 뿌리를 `adapters` 까지) — 0 이 아니다, 운영자 결정 대기

| 걸리는 것 | 수 | 클래스 |
|---|---|---|
| `kotlin.reflect.KClass` 참조 | 2 | `adapters.event.OutboxPayloadCodec` · `adapters.persistence.JdbcCollectionRunStore` |
| `java.lang.Class` 의 허용 밖 멤버 | 7 | `getSimpleName` 여섯(extraction 셋 · koneps 둘 · `DocumentFormatKt`) · `getResourceAsStream` 하나(`RequirementSchemaValidator`) |

D-6G2b-6 대로 **현 뿌리를 유지**했다(이 slice 는 반사 게이트를 건드리지 않는다). 넓히려면 허용 집합
7~9줄이 생기므로 운영자 결정 뒤 별도 커밋이 맞다.

## 음성 fixture 별 결과 (열아홉 + 과잉 대조 둘)

표의 「전송 타입」은 그 변이를 성립시키는 타입이다 — 다른 이유로 잡힌 것을 통과로 세지 않는다.
전부 새 술어에서 **신고됨**(`심은 우회마다 그 전송 타입 때문에 잡힌다` GREEN).

| fixture | 변이 | 전송 타입 | 6G 술어 | 새 술어 |
|---|---|---|---|---|
| `RogueUrlChainFetch` | KA1 — 호출 사슬의 반환 타입 | `java.net.URL` | 놓침 | 잡음 |
| `RogueBeanExpressionCall` | KA12 — 대상 메서드를 문자열로 | `java.beans.Expression` | 놓침 | 잡음 |
| `RogueBeanStatementCall` | 신규 — `Expression` 의 형제 | `java.beans.Statement` | 놓침 | 잡음 |
| `RogueProcessCurl` | KA13 — 프로세스 실행 | `java.lang.ProcessBuilder` | 놓침 | 잡음 |
| `RogueRuntimeExec` | 신규 — `exec` | `java.lang.Runtime` | 놓침 | 잡음 |
| `RogueAsyncChannelFetch` | KA14 — 비동기 채널 | `java.nio.channels.AsynchronousSocketChannel` | 놓침 | 잡음 |
| `RogueRawSocket` | KA3 — 소켓 | `java.net.Socket` | 잡음 | 잡음 |
| `RogueDatagramSend` | 신규 — UDP | `java.net.DatagramSocket` | 놓침 | 잡음 |
| `RogueSocketFactoryFetch` | 신규 — `javax.net` 비 ssl | `javax.net.SocketFactory` | 놓침(`Socket` 으로만) | 잡음 |
| `RogueSpringRestClient` | KA15 — Spring 클라이언트 | `org.springframework.web.client.RestClient` | 놓침 | 잡음 |
| `RogueSpringRequestFactory` | 신규 — 저수준 요청 팩토리 | `org.springframework.http.client.JdkClientHttpRequestFactory` | 놓침 | 잡음 |
| `RogueHttpServerExposure` | 신규 — JDK 내장 서버 | `com.sun.net.httpserver.HttpServer` | 놓침 | 잡음 |
| `RogueRmiLookup` | 신규 — RMI 이름 조회 | `java.rmi.Naming` | 놓침 | 잡음 |
| `RogueJndiLookup` | 신규 — JNDI | `javax.naming.InitialContext` | 놓침 | 잡음 |
| `RogueResourceUrlRead` | 신규 — 자원 URL + Kotlin 확장 | `java.net.URL` | 놓침(**전혀**) | 잡음 |
| `RogueMethodHandleInvoke` | 신규 — 메서드 핸들 | `java.lang.invoke.MethodHandles` | 잡음 | 잡음 |
| `RogueUnlistedHttpClientHolder` | KA2 — 등재 밖 클라이언트 | `java.net.http.HttpClient` | 잡음 | 잡음 |
| `RogueTypealiasedClient` | KA4 — typealias | `java.net.http.HttpClient` | 잡음 | 잡음 |
| `RogueRegisteredHolderGainingTransport` | 신규 — 쌍 등식 축 | `java.net.Socket` | 잡음 | 잡음 |
| `CleanInboundServletHandler` | 과잉 대조 — 들어오는 서블릿 | — | 신고 0 | 신고 0 |
| `CleanLocalComputation` | 과잉 대조 — 전송 무관 | — | 신고 0 | 신고 0 |

「6G 술어」열은 **합치기 전 실측**이다 — 6G 의 값(`java.net.URL`·`URLConnection`·`HttpURLConnection`·
`Socket`·`SocketAddress`·`SSLSocket`·`SSLSocketFactory`·`SocketChannel`·`Method`·`MethodHandles` +
`HttpClient`)을 소유 타입만 보는 수집에 넣어 같은 fixture 뿌리에 돌렸다. **클래스로 신고된 것 6 · 변이를
성립시키는 타입으로 잡힌 것 5 · 놓친 것 14.** 둘을 가른 것은 `RogueSocketFactoryFetch` 다 — 6G 는 그
클래스를 `java.net.Socket`(생성 결과)으로 신고하지만 변이의 축인 `javax.net.SocketFactory` 는 목록 밖이라
보지 못한다. 「다른 이유로 잡혔다」를 통과로 세지 않는다는 것이 이 자리에서 수를 바꾼다.

「6G 변이 KA2·KA3·KA4 가 여전히 RED」는 같은 표의 세 행(`RogueUnlistedHttpClientHolder`·`RogueRawSocket`·
`RogueTypealiasedClient`)이 합친 뒤에도 「잡음」인 것이다.

`RogueMethodHandleInvoke` 가 6G 에서 잡혔다는 실측이 뿌리 목록을 바꿨다(아래 「계약 대조」 1) — 계약의
뿌리 열다섯만으로는 그 행이 **놓침**이 되어 합치기가 좁히는 것이 됐다.

## 변이 실측 (셋 — 축이 갈린다)

변이마다 `git diff --numstat` 으로 적용을 먼저 확인하고, 사본 덮어쓰기로 복원한 뒤(`git checkout --`
금지) `git diff --quiet` 로 트리를 확인했다.

| 변이 | 지운 것 | numstat | 결과 |
|---|---|---|---|
| ① 깊은 수집을 소유 타입만으로 | 호출 대상의 `rawParameterTypes`·`rawReturnType` 수집 두 줄 | `0 2` | **4 failed** — 음성 둘(변이 표 · 깊은 수집 양성 대조) + 양성 둘(쌍 등식 · 쌍이 더해짐) |
| ② 뿌리 열일곱 → 셋 | `javax.net`·`java.rmi`·`javax.naming`·`java.beans`·`java.lang.reflect`·`java.lang.invoke`·`com.sun.net`·`sun.net`·websocket 둘·Spring 셋·`io.netty` | `1 15` | **1 failed** — 음성 변이 표만. **양성 쪽은 전건 초록** |
| ③ 용도 어휘에서 한 용도 제거 | `collection.transport.purposes` 의 `run-state-lock` | `1 1` | **3 failed** — 용도 키 등식 · 쌍 등식 · 등재 밖 참조 0 |

②가 가른 것: 등재 쌍 예순셋이 모두 남긴 뿌리 셋 안이라 **production 등식은 뿌리를 좁혀도 초록이다.**
뿌리가 좁아진 것을 잡는 것은 음성 fixture 표뿐이다 — 「production 이 깨끗하다」와 「새 우회를 잡는다」는
서로를 대신하지 않는다. 이것이 D-6G2b-7(상시 음성 fixture)의 값이다.

①은 거꾸로 양성·음성 양쪽이 든다. ③은 (2b) 값 획득 축의 실측이다 — 정책 키를 비우거나 조용히 바꾸는
길이 닫혀 있다.

## 등재와 새 public 표면

- 등재 쌍 **63**, 용도 다섯. 용도 키 집합 == 닫힌 어휘(변이 ③이 그 등식을 잰다), 쌍 중복 0.
- **새 public 표면은 정책 파일 키 여섯뿐이다**(`collection.transport.roots`·`surface-packages`·
  `surface-types`·`purposes`·`holders.<용도>` 다섯). production 의 새 public 선언 **0**.
- 게이트 등재: `gate.tests.app` 에 새 test class 둘. 그 키는 「app test 전수」 양방향 등식이라 등재 없이는
  `AppGateRegistrationTest` 가 즉시 붉다.
- 이 라운드가 만든 새 파일 다섯은 전부 in_scope 안이다(`app/src/test/kotlin/bidvector/app/architecture/**`
  셋 · 새 fixture 디렉터리 둘 — fixture 경로는 in_scope 의 `app/src/test/kotlin/bidvector/app/architecture/**`
  밖이라 「계약 대조」 4 로 선언한다).

## 계약 대조 (scope.md 문면과 다르게 한 것)

1. **뿌리를 열다섯에서 열일곱으로 늘렸다.** 6G 의 금지 목록에 `java.lang.reflect.Method`·
   `java.lang.invoke.MethodHandles` 가 있어 계약의 뿌리만으로 합치면 그 둘이 빠진다(실측 — `RogueMethodHandleInvoke`
   가 6G 에서 잡히고 계약 뿌리에서 놓쳐진다). 두 패키지를 뿌리로 올려 잃지 않았다(production 참조 0).
   클래스패스 실측으로 더한 것: `jakarta.websocket`·`org.apache.tomcat.websocket`(tomcat-embed-websocket
   이 실제로 있다) · `com.sun.net`·`sun.net`. `kotlin.reflect` 는 **넣지 않았다** — 반사 게이트 소관이고
   그 게이트의 뿌리 범위는 A-2 결정이다(겹쳐 적으면 한쪽이 낡는다).
2. **무해 타입 목록을 두지 않았다**(D-6G2b-3 의 「무해 타입은 쌍 등식에서 뺀다」와 다르다). 관측 19종 중
   「바이트를 밖으로 내지 못하는」 값·예외 타입이 14종으로 계약 (3) 의 열 개 문턱을 넘었고, 그 문턱의
   지시대로 뿌리를 다시 보니 무해 목록 **자체**가 게이트를 헐겁게 하는 쪽이었다 — 목록에 오른 타입은 모든
   클래스에서 자유로워진다. 보유자 28 이 닫힌 어휘 다섯으로 빠짐없이 분류되므로 예외를 없애고 관측 쌍
   전수를 등재했다. 쌍 등식의 취지는 그대로이고 결과는 더 조인다(무해 타입 수 0).
3. **낱개 타입 넷의 구성이 다르다.** 계약은 `ProcessBuilder`·`Runtime`·`java.beans.Expression`·
   `Statement` 넷이었다. `java.beans` 는 뿌리로 둘 수 있어((1) 의 열거 사유 「`java.lang` 을 뿌리로 금지할
   수 없어서」가 해당하지 않는다) 뿌리로 올렸고, 낱개는 `java.lang` 넷(`Process`·`ProcessBuilder`·
   `ProcessHandle`·`Runtime`)으로 바꿨다. 전부 production 참조 0.
4. **fixture 경로가 in_scope 문면 밖이다.** 계약의 in_scope 는 `app/src/test/kotlin/bidvector/app/architecture/**`
   이고 음성 fixture 는 기존 선례대로 `app/src/test/kotlin/bidvector/archfixture/violating/transport/**`
   에 두었다(6G·6F-8 의 fixture 가 사는 자리). scope.md 는 고치지 않았다 — 계약 갱신이 필요한 항목이다.
5. **acceptance 의 `clean`·`--no-build-cache` 는 쓰지 않았다**(계약 정정 문면대로 현 CI `check` job 명령 셋).
6. **`policy.version` 을 7 → 8 로 올렸다.** 계약에 없던 편집이다 — 같은 파일의 v6·v7 주석 관례(판이 오르는
   것은 임계가 아니라 **대상의 모양**이 바뀔 때)에 맞췄다. 두 키 계열이 사라지고 허용의 모양이 집합에서
   쌍으로 바뀌었으므로 판을 올리지 않으면 앞 판과 구별되지 않는다.
7. **`OPEN-6G-GATE-REGISTRY-KONEPS` 는 닫지 않았다.** D-6G2b-9 는 「새 게이트 test 를 등재 목록에 넣는다」를
   요구하고 그것은 했지만, 그 OPEN 의 내용은 게이트 장부 전반이라 이 slice 의 쌍 등식이 갈음하지 못한다 —
   판단은 종결 보고로 넘긴다.

## 알려진 제한

1. **목적지를 모른다**(계약의 경계 밖 선언 그대로). 등재된 `attachment`·`llm`·`ml-grpc` 보유자가 KONEPS
   주소로 호출하는지는 정적 분석이 재지 못한다. 통제는 「서비스 키 원문 설정은 배선 한 곳만 참조한다」다.
2. **이름을 문자열로 짓는 반사 가운데 반사 게이트의 뿌리 밖.** 전송 표면 뿌리가 `java.lang.reflect`·
   `java.lang.invoke` 를 덮어 6G 보다 넓어졌지만, 반사 타입이 **전혀 남지 않는** 형태(서드파티 반사
   도구 경유)는 여전히 밖이다 — 그것을 타입 이름 목록으로 막으면 열거로 돌아간다(6G 의 같은 제한 승계).
3. **게이트 test·정책 파일·빌드 스크립트를 고치는 저자**는 경계 밖이다(M1/1A 승계).
4. **JNI·네이티브·JDBC 경유 DB 쪽 네트워크·새 빌드 의존**은 빌드 의존 게이트 소관이다.
5. **A-2 미결** — 반사 게이트 뿌리는 `workflow`·`app` 그대로다. `adapters` 의 반사 9건은 위 표에 있다.
6. **`io.netty` 는 클래스패스에 없다.** 뿌리로 남겼으나 오늘 그 뿌리는 아무것도 재지 않는다(의존이 들어오면
   그때부터 닫힌다).

## 누출 어휘 스캔 (패턴을 축어로 적지 않고 파일 참조로)

- 이 slice 가 더한 줄만: `git diff 1745a3e2..HEAD -- <in_scope> | grep '^+' | grep -niE -f config/quality/leak-patterns.txt`
  → **exit 1**(매치 없음).
- in_scope 전체 파일: **10 매치**, 전부 기존 줄이다(위 줄 단위 스캔이 0 인 것이 그 방증) — `ArchitecturePolicy.kt`
  4 · `EditableFieldVocabularyGateTest.kt` 2 · `architecture-policy.properties` 4. 건드리지 않았다.
- `reports/evidence/m6/6g2b/`(이 셋 + `scope.md`): `grep -rniE -f config/quality/leak-patterns.txt` →
  **exit 1**(매치 없음). evidence 편집 커밋 뒤의 재확인과 마지막 HEAD 의 정본은 verifier 와 PR 조치
  코멘트 몫이다.
- 육안: 새 fixture·게이트·정책에 실 식별자·기관명·사업자 정보 없음. fixture 는 호출 형태만 담고 실행되지
  않는다(게이트는 바이트코드만 읽는다).

## 낡는 좌표 점검

편집한 파일을 `file:line` 으로 가리키지 않았다(인용문·절 제목·결정 ID·커밋 해시·클래스 이름만). 역방향
파급도 쟀다 — 편집한 아홉 파일의 stem 으로 `grep -rn '<stem>:[0-9]'` → **전부 0건**.

## clean-tree 게이트

`git status --porcelain -- <in_scope 개별 인자 아홉>` → **빈 출력**(0줄). 양성 대조 1회 — 공유 파일
하나를 **비파괴 절삭**(마지막 한 줄 제거)해 porcelain 이 그 파일 한 줄을 내는 것을 확인하고 사본으로
복원, 다시 0줄. `git checkout --` 는 쓰지 않았다.

## 크기 게이트 (evidence ≤ 산출물)

표는 `checklist.md` 「크기 게이트」가 정본이다. 산출물 쪽 값: base..실측 HEAD 의 in_scope 추가 **705줄 /
37,345 B**(삭제 66줄). 구성은 게이트·fixture Kotlin 다섯 531줄 · 정책 둘 149줄 · 기존 test 둘 25줄.
`milestone-6.md` 착수 문단과 `scope.md` 는 팀장 커밋이라 산출물에 세지 않는다(바이트는 diff 의 `+`
접두를 포함한 값이라 줄 수만큼 부풀어 있다 — 두 축을 같은 방법으로 쟀다).
