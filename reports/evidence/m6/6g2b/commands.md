# M6/6G-2b — 명령과 실측

> 출력 전문을 싣지 않는다(핵심 결과 한 줄). **마지막 HEAD 의 게이트 결과 정본은 verifier 와 PR 조치
> 코멘트**다. 이 slice 는 test·정책 파일만 바꾼다 — 실 KONEPS 호출 0, 실행 상태 디렉터리 접근 0.

## acceptance (CI `check` job 의 명령 그대로)

| 완료(UTC) | 명령 | exit | 핵심 결과 |
|---|---|---|---|
| 2026-10-03T12:21Z | `./gradlew --no-daemon check` | 0 | 전 모듈 **2,633** test · 실패 0 · skip 4(base 2,607 에서 +26) |
| 2026-10-03T12:21Z | `./gradlew --no-daemon qualityBaseline` | 0 | **up-to-date**(실행된 측정이 아니다 — production 입력이 바뀌지 않았다는 방증이고, 그것이 이 slice 의 기대값이다. cr L-11) |
| 2026-10-03T12:34Z | `./tools/one-command-check.sh` | 0 | Kotlin 전건 + Python 전건(pytest 1,379 · wheel 1) |

**실측 HEAD `57493a71`**(마지막 산출물 커밋). 세 줄은 그 한 스크립트가 순서대로 돈 것이다.

test 증감 +26 — 전송 표면 게이트 22(`TransportSurfaceGateTest` 14 + 음성 쪽 8) + 반사 게이트 쌍 등식 6
(음성 쪽 20 → 24, 양성 쪽은 반사 test 둘을 넷으로 바꾸고 전송 단언 둘을 지워 24 그대로) − 지운 test 2
(6G 의 두 게이트 단언).

`check` 는 라운드마다 첫 호출이 형식 게이트로 exit 1 이었다 — ktlint 합 37건(연속 KDoc·체인 연속·함수
시그니처·`}` 앞 빈 줄)과 detekt 2건(파일명 불일치·상수 반환). 전부 형식이고 술어·단언·fixture 의 의미는
바뀌지 않았다. `ktlintTestSourceSetFormat` 과 fixture 두 건 손질 뒤 exit 0(`541b5065`·`130dc4c9`).

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

### A-2 착수 실측 (반사 게이트 뿌리를 `adapters` 까지) — 0 이 아니다

| 걸리는 것 | 수 | 클래스 |
|---|---|---|
| `kotlin.reflect.KClass` 참조 | 2 | `adapters.event.OutboxPayloadCodec` · `adapters.persistence.JdbcCollectionRunStore` |
| `java.lang.Class` 의 **허용 밖** 멤버 | 7 | `getSimpleName` 여섯(extraction 넷 · koneps 둘) · `getResourceAsStream` 하나(`RequirementSchemaValidator`) |

이 실측을 받아 운영자가 2026-10-03 에 확장을 결정했다(D-6G2b-11). 집행은 아래 절이다.

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

재현 명령(vr L-3 — 앞 판은 값과 방법만 산문으로 있었다). 버릴 clone 에서:

```
# app test 소스셋에 임시 probe test 하나를 둔다. 규칙 값은 6G 의 것, 수집은 소유 타입만:
#   TransportSurfaceRules(packageRoot, surfacePackages = emptySet(),
#     surfaceTypes = (transportBypassTypes + httpClientType).toSet(), collection = OWNER_ONLY)
# 을 fixture 뿌리에 적용해 failureReport.details 를 찍는다.
./gradlew --no-daemon :app:test --tests '<probe class>'
```

probe test 는 버릴 clone 에만 두었다(저장소에 남기지 않는다 — 6G 의 두 키 계열이 이 판에 없어 영구
단언으로는 설 수 없다). **남는 영구 대조는 `ReferenceCollection.OWNER_ONLY`** 쪽이다: 같은 fixture 에
소유 타입만 보는 수집을 적용해 KA1 의 `java.net.URL` 과 자원 URL 변이를 놓치는 것을 상시 잰다.

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

### 바깥 참조·접기 축 변이 (넷)

| 변이 | 적용 확인 | 결과 |
|---|---|---|
| ⑦ 허용 패키지 하나 제거(`app` 의 `java.io`) | 사본 대조 1줄 삭제 | **2 failed** — 허용 밖 참조 0 · 두 방향 등식 |
| ⑧ 미관측 허용 패키지 추가(`workflow` 에 `com.example.ghost`) | 사본 대조 1줄 추가 | **1 failed** — 두 방향 등식만(참조가 없어 규칙은 조용하다 = 「허용 ⊂ 관측」 방향) |
| ⑨ 접기를 `enclosingClass` 로 되돌림 | `git diff --numstat` `1 1` | **2 failed** — 쌍 등식 · 등재 밖 참조 0(`$BodyHandler` 셋이 관측에 돌아온다) |
| ⑩ `java.util.ServiceLoader` 낱개 제거 | 사본 대조 1줄 | **1 failed** — ServiceLoader fixture 의 2층 단언만. 1층은 그대로 조용하다 |
| ⑪ `layer.application` 을 비움(모듈 하나 빠뜨림) | 사본 대조 1줄 치환 | **4 failed** — 모집단 단언 · 바깥 참조 모듈 키 등식 · 앞선 두 게이트(1급 패키지 집합 · 의존 방향) |

⑦⑧이 두 방향을 가른다 — 하나는 규칙과 등식 둘이, 다른 하나는 등식만 든다. ⑩은 두 층의 분기를 잰다:
`java.util` 이 허용 패키지라 그 확장 지점은 **1층이 아니라 2층**이 잡는다. ⑪은 판정 대상 목록을 도출로 둔
값을 잰다 — 한 키가 세 관심사를 함께 든다.

②가 가른 것: 등재 쌍 예순셋이 모두 남긴 뿌리 셋 안이라 **production 등식은 뿌리를 좁혀도 초록이다.**
뿌리가 좁아진 것을 잡는 것은 음성 fixture 표뿐이다 — 「production 이 깨끗하다」와 「새 우회를 잡는다」는
서로를 대신하지 않는다. 이것이 D-6G2b-7(상시 음성 fixture)의 값이다.

①은 거꾸로 양성·음성 양쪽이 든다. ③은 (2b) 값 획득 축의 실측이다 — 정책 키를 비우거나 조용히 바꾸는
길이 닫혀 있다.

## 바깥 참조 기본 거부 — 전환과 실측 (D-6G2b-22·25, vr H-1)

금지 뿌리 열거는 **목록 밖 패키지**를 처음부터 보지 못했다. verifier 가 production 소스셋에 넷을 심고
전체 `check` 가 exit 0 임을 실측했다 — `java.util.logging.SocketHandler`(TCP) ·
`javax.xml.parsers.DocumentBuilder.parse(String)`(HTTP GET) · `javax.management.remote.JMXConnectorFactory`(RMI) ·
`javax.swing.JEditorPane(String)`(HTTP GET). 넷 다 전송 표면 타입을 하나도 남기지 않는다.

**방향을 뒤집었다.** `app`·`workflow`·`adapters` production 이 참조하는 `bidvector..` 밖 타입의 **패키지**가
모듈 허용 집합에 있어야 한다(기본 거부). 두 층이고, 타입이 전송 표면이면 이 층을 보지 않는다.

| 축 | 값 |
|---|---|
| 모듈별 허용 패키지 | **app 55 · workflow 21 · adapters 51 = 127** |
| 판정 대상 모듈 | `layer.application`·`layer.adapters`·`layer.app` **도출** — 별도 키 없음(`collection.external.modules` 는 **삭제된 키**다, `28c789c6`) |
| 공통(합집합)으로 두면 | 82 |
| 입도 | **정확한 패키지 이름**(접두 뿌리가 아니다) |
| 판정 대상 production 클래스 | app 98 · workflow 376 · adapters 371 |
| 모듈별 바깥 참조 타입 전수 | app 161(그중 전송 표면 2) · workflow 86(0) · adapters 257(19) |

**모듈별을 고른 근거**: 양방향 등식에서 공통은 `허용 == ∪관측` 이라 한 모듈이 다른 모듈만 쓰는 패키지를
참조해도 초록이다. 모듈별은 `허용(m) == 관측(m)` 이라 그 길이 닫힌다 — 목록이 1.5배지만 조이는 쪽이다.

**정확 패키지를 고른 근거**: 「계약 대조」 9.

**판정 대상 모듈을 별도 키로 적지 않는다**(팀장 지시 2026-10-03). 한 판 동안 있었던 `collection.external.modules`
는 `28c789c6` 에서 **삭제됐다** — 지금 트리에 없는 키다. 별도 키였다면 그 키에서 모듈 하나를 빼는
것만으로 그 모듈이 판정 밖이 된다. `layer.*` 는 **모집단 단언과 다른 층 게이트가 같이 읽는 자리**라 빠뜨리면
함께 붉다 — 변이 ⑪이 그것을 잰다. 허용 키 집합(`allowed-packages.<모듈>`)이 그 도출 결과와 같아야 한다는
단언이 양쪽을 묶는다.

### 음성 fixture (열하나 추가 — 위반 31 · 과잉 대조 2)

| fixture | 변이 | 잡는 층 | 걸리는 이름 |
|---|---|---|---|
| `RogueLoggingSocketSend`(adapters) | V1 TCP 로그 전송 | 1층 | `java.util.logging` |
| `RogueWorkflowSocketSend`(workflow) | V2 같은 우회를 use case 층에 | 1층 | `java.util.logging` |
| `RogueXmlParseFetch` | V3 XML 파서가 URL 을 받는다 | 1층 | `javax.xml.parsers` |
| `RogueJmxConnect` | V4 JMX 원격 연결 | 1층 | `javax.management.remote` |
| `RogueSwingPageFetch` | V5 Swing 이 URL 을 받는다 | 1층 | `javax.swing` |
| `RogueDesktopBrowse` | 신규 — 데스크톱 브라우저 | 1층 | `java.awt` |
| `RogueScriptEval` | 신규 — 스크립트 엔진 | 1층 | `javax.script` |
| `RogueServiceLoaderExtension` | 신규 — 이름으로 실어 오는 외부 구현 | **2층** | `java.util.ServiceLoader` |
| `RogueGenericArgHolder` · `RogueSupplierArgHolder` | cr M-2 제네릭 인자 | 2층 | `java.net.http.HttpClient` |
| `RogueSamLambdaHolder` | cr M-2 SAM 타입 인자 | 2층 | 같음 |
| `RogueAnnotatedHolder` | cr M-2 어노테이션 클래스 리터럴 | 2층 | 같음 |

`ServiceLoader` 가 1층에 걸리지 않는 것은 `java.util` 이 허용 패키지이기 때문이다. 두 층의 분기를 **그
fixture 하나가 잰다** — 1층 신고 0 · 2층 신고 1.

### 접기 규칙 (cr M-1) — 세 방식 실측

| 방식 | 등재 쌍 수 | 문제 |
|---|---|---|
| ArchUnit `enclosingClass`(앞 판) | 63 | 해소 여부에 달려, 전송 표면을 늘리지 않는 편집이 철자를 바꾼다 |
| **이름의 첫 `$` 절단(채택)** | **60** | 없다. 앞 판에서 `HttpResponse$BodyHandler` 셋만 접힌다(`namecut − current = ∅`) |
| 접지 않음 | 81 | 아래 21 이 더해진다 — 무관한 편집마다 철자가 바뀐다 |

접지 않을 때 더해지는 **21 쌍의 정체**(cr r2 L-11 — 다음 라운드가 다시 재지 않게 적는다). 두 부류다.

- **보유자 쪽 합성·중첩 이름** — Kotlin 이 지어 주는 이름이다: 코루틴·람다 클래스(`…$predict$3` ·
  `…$embed$3` · `…$handleSuccess$promoted…$1` · `…$callMlRpc$1` · `…$callMlRpc$outcome$1`) ·
  `when` 분기 표(`RetryRulesKt$WhenMappings`) · `$Companion` · sealed 하위(`RawFetchOutcome$Received` ·
  `RunStateLock$Held`).
- **JDK 중첩 타입** — `HttpRequest$Builder` · `HttpResponse$BodyHandler(s)` · `HttpRequest$BodyPublisher(s)` ·
  `HttpClient$Builder` · `Status$Code`.

앞 부류가 문제다. 람다 하나를 더하거나 줄이면 그 이름이 바뀌고, 전송 표면을 하나도 늘리지 않은 편집이 정책
파일을 낡게 만든다. 뒤 부류는 안정적이지만 접으면 이미 등재된 바깥 타입으로 들어간다.

## A-2 집행 — 반사 게이트 뿌리 확장과 쌍 등식 (D-6G2b-11)

뿌리를 `bidvector.workflow,bidvector.app,bidvector.adapters` 로 넓히고 **별도 키**에 둔다 — 앞 판은 원문 값
획득 뿌리를 그대로 썼고, 겹쳐 적으면 한쪽을 넓히는 편집이 다른 쪽을 조용히 넓힌다.

허용은 (클래스, 리플렉션 타입)·(클래스, `Class` 멤버) **쌍**이다. 앞 판의 두 허용은 각각 한 칸 헐거웠다 —
멤버 이름을 전역으로 허용하면(`class-allowed-members=getName`) 어느 클래스든 그 이름을 쓸 수 있고, 클래스만
허용하면 등재된 클래스가 새 반사 멤버를 더 부를 수 있다.

| 축 | 값 |
|---|---|
| 등재 쌍 | **12** — (클래스, 타입) 2 + (클래스, 멤버) 10 |
| 그중 A-2 로 **새로 금지 범위에 든 것** | 9(`KClass` 2 + 허용 밖 `Class` 멤버 7) |
| 그중 앞 판이 **전역 `getName` 으로 덮고 있던 것** | 3(`adapters.snapshot.RunStateDurabilityKt` · `app.collection.CollectionRunnerKt` · `app.collection.OpeningCollectionLinesKt`) |
| 등장하는 멤버 이름 | 셋(`getName` 3 · `getSimpleName` 6 · `getResourceAsStream` 1) — 전부 값 획득이 아니다 |
| 깊은 수집이 더하는 타입 쌍 | **0**(소유 타입만 본 결과와 같다) — 그래도 같은 함수를 쓴다 |

**참조 수집의 정의를 하나로 합쳤다.** 전송 표면 게이트가 쓰던 깊은 수집을 공유 함수로 올려 반사 게이트도
같은 것을 쓴다 — 수집 범위를 좁히는 편집이 두 게이트의 양성 대조를 동시에 RED 로 만든다. 중첩 클래스를
접는 helper 도 한 정의로 모았다(앞 판은 두 파일에 같은 것이 있었다).

### A-2 변이 실측 (셋)

| 변이 | 적용 확인 | 결과 |
|---|---|---|
| ④ 등재 멤버 쌍 하나 제거(`RequirementSchemaValidator->getResourceAsStream`) | 사본 대조 1줄 삭제 | **2 failed** — 쌍 등식 · 등재 밖 참조 0 |
| ⑤ production 에 **새 반사 참조**(등재된 `adapters.snapshot` 파일에 `javaClass.getMethod(...).invoke(...)` 한 줄) | `git diff --numstat` `2 0` | **2 failed** — 같은 둘 |
| ⑥ ⑤를 그대로 두고 뿌리를 앞 판(`workflow`·`app`)으로 좁힘 | 사본 대조 1줄 치환 | **1 failed** — 쌍 등식만. 규칙 쪽은 **초록** |

⑥이 A-2 의 값을 잰다 — 좁힌 뿌리는 ⑤의 새 반사 참조를 **보지 못한다**. 남은 한 실패는 좁힘 때문에 등재
쌍이 관측에서 사라진 것(낡은 항목)이므로, 뿌리를 조용히 되돌리는 편집도 그 등식에 걸린다. 규칙과 등식이
서로 다른 것을 들고 있다.

변이 ⑤는 production 파일을 만졌다(out_scope). 사본 덮어쓰기로 복원하고 `git diff --quiet` 로 확인했다 —
**산출물의 production diff 는 0 그대로**다.

영구 대조 둘도 남겼다. 뿌리를 앞 판으로 좁히면 ⓐ `adapters` 쌍이 관측에서 사라지고(양성 쪽) ⓑ `adapters`
층 음성 fixture 가 신고되지 않는다(음성 쪽). 새 음성 fixture 둘 — `adapters` 층 반사(`getMethod`·`forName`)와
**등재된 클래스가 새 반사 멤버를 더 부르는** 쌍 축이다. 쌍 축은 합성 등재 집합으로 「등재한 `getName` 은
조용 · 더 부른 `getDeclaredMethod` 만 신고」를 잰다.

## 등재와 새 public 표면

- 등재 쌍 **60**(이름 기준 접기로 `$BodyHandler` 셋이 접혔다), 용도 다섯. 용도 키 집합 == 닫힌 어휘(변이 ③이 그 등식을 잰다), 쌍 중복 0.
- **새 public 표면은 정책 파일 키뿐이다 — 더한 것 15 · 지운 것 8**(기계로 센 차집합, 내역은 `checklist.md`).
  판정 대상 모듈 목록은 키가 아니라 `layer.*` 도출이라 표면이 아니다.
  production 의 새 public 선언 **0**.
- 게이트 등재: `gate.tests.app` 에 새 test class 둘. 그 키는 「app test 전수」 양방향 등식이라 등재 없이는
  `AppGateRegistrationTest` 가 즉시 붉다.
- 새 파일은 **열**이고 전부 in_scope 안이다 — 게이트 셋(`app/.../architecture/**`) · fixture 여섯
  (`violating/transport/**` 셋 · `violating/adapters/**` 둘 · `violating/workflow/external/**` 하나) ·
  `app/src/test/resources/archunit.properties`. 경로를 넣은 것은 계약 갱신 r1 D-10 · r2 D-18 · r5 D-31 이다.

## 계약 대조 (scope.md 문면과 다르게 한 것)

아래 열하나 전부 계약 갱신 r1·r2·r4·r5(D-6G2b-10~18·28~31)가 결정으로 받았다 — 그 갱신들 **전에** 레인이
한 판단이므로 근거를 여기 남긴다. **계약 문면과 다른 것은 남지 않았다.**

1. **뿌리를 열다섯에서 열일곱으로 늘렸다**(→ D-6G2b-12). 6G 의 금지 목록에 `java.lang.reflect.Method`·
   `java.lang.invoke.MethodHandles` 가 있어 계약의 뿌리만으로 합치면 그 둘이 빠진다(실측 — `RogueMethodHandleInvoke`
   가 6G 에서 잡히고 계약 뿌리에서 놓쳐진다). 두 패키지를 뿌리로 올려 잃지 않았다(production 참조 0).
   클래스패스 실측으로 더한 것: `jakarta.websocket`·`org.apache.tomcat.websocket`(tomcat-embed-websocket
   이 실제로 있다) · `com.sun.net`·`sun.net`. `kotlin.reflect` 는 **넣지 않았다** — 반사 게이트 소관이고
   그 게이트의 뿌리 범위는 A-2 결정이다(겹쳐 적으면 한쪽이 낡는다).
2. **무해 타입 목록을 두지 않았다**(초안 D-6G2b-3 의 「무해 타입은 쌍 등식에서 뺀다」와 다르다 → D-6G2b-13). 관측 19종 중
   「바이트를 밖으로 내지 못하는」 값·예외 타입이 14종으로 계약 (3) 의 열 개 문턱을 넘었고, 그 문턱의
   지시대로 뿌리를 다시 보니 무해 목록 **자체**가 게이트를 헐겁게 하는 쪽이었다 — 목록에 오른 타입은 모든
   클래스에서 자유로워진다. 보유자 28 이 닫힌 어휘 다섯으로 빠짐없이 분류되므로 예외를 없애고 관측 쌍
   전수를 등재했다. 쌍 등식의 취지는 그대로이고 결과는 더 조인다(무해 타입 수 0).
3. **낱개 타입 넷의 구성이 다르다**(→ D-6G2b-14). 초안은 `ProcessBuilder`·`Runtime`·`java.beans.Expression`·
   `Statement` 넷이었다. `java.beans` 는 뿌리로 둘 수 있어((1) 의 열거 사유 「`java.lang` 을 뿌리로 금지할
   수 없어서」가 해당하지 않는다) 뿌리로 올렸고, 낱개는 `java.lang` 넷(`Process`·`ProcessBuilder`·
   `ProcessHandle`·`Runtime`)으로 바꿨다. 전부 production 참조 0.
4. **A-2 fixture 경로**(→ D-6G2b-18 이 in_scope 에 넣었다). 전송 fixture 경로
   (`archfixture/violating/transport/**`)는 D-6G2b-10 이 in_scope 에 넣었지만, A-2 가 더한
   `archfixture/violating/adapters/RogueAdapterReflectionPeek.kt` 는 그 문면에 없다. 그 자리에 둔 이유는
   fixture 의 패키지가 **게이트 뿌리를 정하기 때문**이다 — `adapters` 층 반사를 재려면 fixture 가
   `archfixture/violating/adapters` 아래 있어야 한다. scope.md 는 고치지 않았다.
5. **acceptance 의 `clean`·`--no-build-cache` 는 쓰지 않았다**(계약 정정 문면대로 현 CI `check` job 명령 셋).
6. **`policy.version` 을 7 → 8 로 올렸다**(→ D-6G2b-15). 초안에 없던 편집이다 — 같은 파일의 v6·v7 주석 관례(판이 오르는
   것은 임계가 아니라 **대상의 모양**이 바뀔 때)에 맞췄다. 두 키 계열이 사라지고 허용의 모양이 집합에서
   쌍으로 바뀌었으므로 판을 올리지 않으면 앞 판과 구별되지 않는다. A-2 가 반사 키 계열도 같은 모양으로
   바꾸지만 **판은 8 그대로** 두고 그 주석에 둘째 축을 더했다 — 한 slice 가 판 하나다.
7. **`OPEN-6G-GATE-REGISTRY-KONEPS` 는 닫지 않았다**(→ D-6G2b-15). D-6G2b-9 는 「새 게이트 test 를 등재 목록에 넣는다」를
   요구하고 그것은 했지만, 그 OPEN 의 내용은 게이트 장부 전반이라 이 slice 의 쌍 등식이 갈음하지 못한다 —
   판단은 종결 보고로 넘긴다.
8. **A-2 등재 쌍이 열둘이다 — 운영자 결정 문면의 「9건」보다 셋 많다**(→ D-6G2b-17). D-6G2b-11 이 센 9 는 앞 판에서
   **금지였던** 참조다(`KClass` 2 + 허용 밖 `Class` 멤버 7). 허용을 쌍으로 바꾸면 앞 판이 **전역 `getName`
   으로 덮고 있던** 셋(`adapters.snapshot.RunStateDurabilityKt` · `app.collection.CollectionRunnerKt` ·
   `app.collection.OpeningCollectionLinesKt`)도 등재해야 한다 — 전역 허용을 없앤 결과이고, 등재하지 않으면
   그 셋이 RED 다. 9 + 3 = 12.
9. **허용 집합의 입도가 「정확 패키지」다 — 지시 문면의 「가장 넓은 안전 단위 + 거부 하위 명시」와 다르다**
   (→ D-6G2b-28). 접두 뿌리로 묶으면 `java.util` 과 `javax.xml` 이 허용 뿌리가 되고 **H-1 변이 둘이 그 아래로
   들어온다**(`java.util.logging.SocketHandler` · `javax.xml.parsers.DocumentBuilder`). 막으려면 거부 하위를
   열거해야 하는데 그 열거가 H-1 이 벌한 방향이다. 정확 패키지는 거부 목록이 **필요 없다** — 관측에 없으면
   거부다. 관측도 그 입도를 뒷받침한다: `javax.xml` 아래 관측은 `javax.xml.stream` 뿐이고
   `java.util.logging` 은 0 이다.
10. **`java.util.ServiceLoader` 를 낱개 전송 타입에 더했다**(→ D-6G2b-28). `java.util` 이 허용 패키지라 1층을
   지나므로 `java.lang` 의 `ProcessBuilder` 와 같은 자리로 두었다 — 지시가 든 변이 후보 하나를 **2층**이
   잡게 하는 선택이고 production 관측은 0 이다.
11. **in_scope 문면 밖이던 자리 둘**(→ D-6G2b-31 이 넣었다) — `archfixture/violating/workflow/external/**` 와
   `app/src/test/resources/archunit.properties`(cr L-3 핀). `adapters/external/**` 는 D-6G2b-18 이 넣은
   `adapters/**` 아래라 처음부터 문면 안이었다. workflow fixture 는 **모듈별 허용 집합을 모듈마다 재려면**
   그 모듈 뿌리 아래여야 해서 다른 자리에 둘 수 없고, ArchUnit 핀은 test 리소스라 Kotlin 경로 밖이다.

## 검토 처분 (D-6G-65 분류)

| finding | 분류 | 처분 |
|---|---|---|
| vr H-1(전송 뿌리 밖 JDK API 가 초록) | 게이트 하드닝 | 정책 `0f11117a` + 술어 `f77c319a` + fixture `0c6f36b2` + 단언 `de86738d` — 바깥 참조 기본 거부로 전환(D-22) |
| vr M-1(등재 보유자 안의 새 사용처) | 게이트 하드닝 | 코드 0 — 알려진 제한 + `OPEN-6G2B-HOLDER-INTERNAL-SURFACE`(D-23, scope OPEN 표는 팀장) |
| cr M-1(접기가 해소 여부에 달린다) | 게이트 하드닝 | `f77c319a` 이름 기준 절단 + 정책 쌍 셋 제거 |
| cr M-2(간접 시그니처 상시 fixture 부재) | 게이트 하드닝 | `0c6f36b2` fixture 넷 + `de86738d` 변이 표 네 행 |
| cr M-3(뿌리 전수를 다시 재지 않는다) | 게이트 하드닝 | **소멸** — D-22 기본 거부에서 새 패키지는 등식이 잡는다 |
| cr M-4(모집단 미고정) | 게이트 하드닝 | `de86738d` 모듈 아홉 단언 |
| cr M-5 · vr L-1 · L-2 · cr L-1 · L-2 · L-10 | 장부층 | checklist·commands 정정(마지막 산출물 커밋 · 경로 수 · 키 수 · 계약 대조 머리글·4·8 · 크기) |
| vr L-3(RED 실측 명령 부재) | 장부층 | 위 「6G 술어 형태」 절에 재현 명령 |
| vr L-4 · cr L-6(fixture KDoc·KA 번호) | 해당 없음 | `0c6f36b2` · `de86738d` |
| vr L-5 · cr ④⑤(부분 문자열 비교) | 게이트 하드닝 | `de86738d` — 전송 쪽 전체 일치 · 수집 쪽 이름 경계 일치 |
| cr L-3(`failOnEmptyShould` 핀 부재) | 게이트 하드닝 | `0fb24e8d` `archunit.properties` |
| cr L-4(죽은 가지) · cr L-9(중복 키) | 게이트 하드닝 | `f77c319a` + `de86738d` 중복 키 단언 |
| cr L-7(주석이 두 게이트 겹침을 숨긴다) · cr L-8(멤버 이름만 비교) | 장부층 | 정책 주석·checklist 제한 |
| cr L-11(`qualityBaseline` up-to-date) | 장부층 | acceptance 절에 사실로 적는다 |

## 알려진 제한

1. **목적지를 모른다**(계약의 경계 밖 선언 그대로). 등재된 `attachment`·`llm`·`ml-grpc` 보유자가 KONEPS
   주소로 호출하는지는 정적 분석이 재지 못한다. 통제는 「서비스 키 원문 설정은 배선 한 곳만 참조한다」다.
2. **반사 타입이 전혀 남지 않는 형태.** 전송 표면 뿌리가 `java.lang.reflect`·
   `java.lang.invoke` 를 덮어 6G 보다 넓어졌지만, 반사 타입이 **전혀 남지 않는** 형태(서드파티 반사
   도구 경유)는 여전히 밖이다 — 그것을 타입 이름 목록으로 막으면 열거로 돌아간다(6G 의 같은 제한 승계).
3. **게이트 test·정책 파일·빌드 스크립트를 고치는 저자**는 경계 밖이다(M1/1A 승계).
4. **JNI·네이티브·JDBC 경유 DB 쪽 네트워크·새 빌드 의존**은 빌드 의존 게이트 소관이다.
5. **반사 게이트 뿌리는 production 전체가 아니다.** A-2 로 `adapters` 가 들어왔지만 `procurement`·
   `decision`·`qualification`·`settlement`·`shared-kernel` 은 밖이다(domain 계열이고 프레임워크·반사 참조는
   다른 게이트가 금지하지만, 이 게이트의 쌍 등식으로는 재지 않는다). 전송 표면 게이트처럼 production
   전체로 올리지 않은 것은 운영자 결정 문면(`adapters` 까지)을 넘지 않기 위해서다.
6. **등재 보유자 안의 새 사용처는 쌍의 해상도 밖이다**(vr M-1 → D-6G2b-23,
   `OPEN-6G2B-HOLDER-INTERNAL-SURFACE`). 등재 보유자에 **String 시그니처의 public 함수**를 더해 그 안에서
   이미 등재된 전송 타입으로 호출을 내면, 어느 클래스든 그 함수를 문자열로 부를 수 있고 관문을 지나지
   않는다. (클래스, 타입) 쌍은 「새 **타입**」만 재고 「같은 타입의 새 **사용처**」는 보지 못한다. 해상도를
   올리는 길은 (호출 메서드, 송신 멤버) 쌍인데 보유자 스물여덟의 멤버 전수가 또 한 라운드다.
7. **`io.netty` 는 클래스패스에 없다.** 뿌리로 남겼으나 오늘 그 뿌리는 아무것도 재지 않는다(의존이 들어오면
   그때부터 닫힌다).

## 누출 어휘 스캔 (패턴을 축어로 적지 않고 파일 참조로)

- 이 slice 가 더한 줄만: `git diff 1745a3e2..<실측 HEAD> -- <in_scope> | grep '^+' | grep -niE -f config/quality/leak-patterns.txt`
  → **exit 1**(매치 없음).
- in_scope 전체 파일: **10 매치**, 전부 기존 줄이다(위 줄 단위 스캔이 0 인 것이 그 방증) — `ArchitecturePolicy.kt` ·
  `EditableFieldVocabularyGateTest.kt` · `architecture-policy.properties` 세 파일에 흩어져 있고 건드리지 않았다.
- `reports/evidence/m6/6g2b/`(이 셋 + `scope.md`): `grep -rniE -f config/quality/leak-patterns.txt` →
  **exit 1**(매치 없음). evidence 편집 커밋 뒤의 재확인과 마지막 HEAD 의 정본은 verifier 와 PR 조치
  코멘트 몫이다.
- 육안: 새 fixture·게이트·정책에 실 식별자·기관명·사업자 정보 없음. fixture 는 호출 형태만 담고 실행되지
  않는다(게이트는 바이트코드만 읽는다).

## 낡는 좌표 점검

편집한 파일을 `file:line` 으로 가리키지 않았다(인용문·절 제목·결정 ID·커밋 해시·클래스 이름만). 역방향
파급도 쟀다 — 편집한 **열여섯** 파일의 stem 으로 `grep -rn '<stem>:[0-9]'` → **전부 0건**.

## clean-tree 게이트

`git status --porcelain -- <in_scope 개별 인자 열일곱>` → **빈 출력**(0줄). 양성 대조 1회 — 공유 파일
하나를 **비파괴 절삭**(마지막 한 줄 제거)해 porcelain 이 그 파일 한 줄을 내는 것을 확인하고 사본으로
복원, 다시 0줄. `git checkout --` 는 쓰지 않았다.

## 크기 게이트 (evidence ≤ 산출물)

표는 `checklist.md` 「크기 게이트」가 정본이다. 산출물 쪽 값: base..마지막 산출물 커밋(`57493a71`)의 in_scope 추가 **1,660줄 /
87,820 B**(삭제 168줄). 구성은 게이트·fixture Kotlin 열 · 정책 둘 · ArchUnit 핀 하나 · 기존 test 넷.
`milestone-6.md` 착수 문단과 `scope.md` 는 팀장 커밋이라 산출물에 세지 않는다(바이트는 diff 의 `+`
접두를 포함한 값이라 줄 수만큼 부풀어 있다 — 두 축을 같은 방법으로 쟀다).
