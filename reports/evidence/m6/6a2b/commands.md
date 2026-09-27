# M6/6A-2b — 명령과 종료 코드

정본은 `scope.md` acceptance(= CI 워크플로 job 의 명령 그대로)다. 출력 전문은 싣지 않는다(핵심 결과 한 줄)
— 감사자는 명령을 다시 돌린다. **마지막 HEAD 의 게이트 결과 정본은 verifier 와 PR 조치 코멘트다**(evidence 는
자기 마지막 커밋의 post-state 를 담을 수 없다).

변이는 전부 적용 직후 **적용됐음을 확인**한 뒤 돌렸고(추적 파일은 `git diff --numstat`, 새 파일은 존재 자체),
측정 뒤 되돌려 빈 diff 를 확인했다.

## acceptance — `check` job

- cmd: `./gradlew --no-daemon clean` → `./gradlew --no-daemon check --no-build-cache`
- exit: 0
- 핵심 결과: 9 모듈 전건. `app` test **전수 37** 이 `gateExecutionGate` 에 등재돼 **실행
  자체**가 확인된다(통과 수가 아니다 — 그중 하나에 `@Disabled` 를 달면 RED 임을 실측했다)

- cmd: `./gradlew --no-daemon qualityBaseline`
- exit: 0
- 핵심 결과: 게이트가 아니라 측정 — 산출물만 갱신

- cmd: `./tools/one-command-check.sh`
- exit: 0
- 핵심 결과: Kotlin 전건 + Python 전건(`ml-engine` 무편집) 통과

## acceptance — `container` job (로컬 실측, 정본은 CI)

- cmd: 두 이미지 빌드(`ml-serving`·앱) + `./gradlew :app:bootJar`
- exit: 0
- 핵심 결과: 이미지 생성. 앱 이미지는 이 라운드의 `bootJar` 로 다시 만들었다

- cmd: `./tools/image-hygiene-check.sh` ×2(정책 파일 각각)
- exit: 0 / 0
- 핵심 결과: 「위생 게이트 통과」 — 크기·base layer 판정 포함

- cmd: 앱 이미지 거부 스모크(S-22c, 워크플로에서 추출해 그대로 실행)
- exit: 0
- 핵심 결과: 「거부 스모크 통과」 — 운반 이름공간 조기 거부 문면 확인

- cmd: `docker compose up -d` + healthy 수렴 → 컨테이너 스모크(S-23b) → `down -v`
- exit: 0
- 핵심 결과: 셋 다 healthy, 「스모크 통과」 — 쓰기 왕복(`begin → value → confirm → GET 반영`)과
  「무인증 쓰기 401 · 미선언 메서드 405」 절이 실제로 돌았다

- cmd: `./gradlew --no-daemon :adapters:test --tests '*RealServerIntegrationTest*' -PrealServer=true`
- exit: 0
- 핵심 결과: 실 Kotlin gateway ↔ 컨테이너의 실 Python 서버 통과(무편집 축)

`ml-engine` job 은 이 slice 가 Python 을 건드리지 않아 CI 초록으로 갈음한다.

## 표적 test (핵심만)

| 명령 | exit | 핵심 결과 |
|---|---|---|
| `:workflow:test --tests '…EditSessionBaseRevisionTest'` | 0 | 4 — 기준 revision 기록·교차 세션 거부·되돌아가 재확인·낡은 행 fail-closed |
| `:app:test --tests '…StrategyEditProductionE2ETest'` | 0 | 6 — 출하 조립 왕복 · **교차 세션 409 와 앞 값 보존** · 무효 값의 영속 0 · audit==actor · 무자격 401 |
| `:app:test --tests '…AppHttpDependencyGateTest'` | 0 | 14 — **면제 세 층의 배타성과 합** · 도출 포트 집합 등식 · **대상 집합(app 전체 − 면제)** · 면제가 실재 클래스만 가리키는지 · production 양성 · 위반 표본 여섯 |
| `:app:test --tests '…HttpSurfaceCensusTest'` | 0 | 6 — 두 서블릿 컨텍스트 · API·관리 각각의 handler·Filter·Servlet 집합 등식 · 문서 등식 모집단 완전성 |
| `:app:test --tests '…ProductionHttpSurfaceTest'` | 0 | 7 — 매핑 전수 405/415/400/406 · 두드림∪제외 등식 · 문서↔매핑 집합 등식 · 응답 키 집합 |
| `:app:test --tests '…StrategyEditExecutorRaceTest'` | 0 | 2 — 읽기 사이 끼어듦 거부와 끼어듦 없을 때의 양성 대조 |
| `:app:test --tests '…StrategyEditEndpointTest'` | 0 | 21 — 결과→상태 코드 표, 우회 ②③④⑦⑧, 알 수 없는 키·십진 상한·세션 필드 강제 |
| `:app:test --tests '…ManagementHealthSurfaceTest'` | 0 | 관리 포트 미디어 타입 축(200/406/노출 집합 불변) 포함 |
| `:strategy:test --tests '…StrategyExportTest'` | 0 | 2 — 왕복 등식과 정의역 등식 |
| `:adapters:test --tests '…StrategyEditTransactionAtomicityTest'` | 0 | 2 — 장애 주입(전략·세션 불변, outbox 0행)과 정상 커밋 |

## 변이 — 게이트가 RED 가 되는지 실측

| 변이 | 대상 게이트 | 결과 |
|---|---|---|
| 확인의 기준 revision 축 제거 | 동시 세션 회귀 | RED 2(교차 세션 · 낡은 행 fail-closed) |
| 메서드 불일치 핸들러 삭제 | 형식 게이트 | RED 2(기계 전수 · 405 본문·`Allow`) |
| 트랜잭션 경계 `autoCommit=true` | 원자성 | RED 2(장애 주입·정상 커밋) |
| 필드 표에서 한 필드 제거 | 어휘 게이트·계약 | RED 2(하위 타입 등식 · 문서 enum 등식) |
| 읽기 컨트롤러가 전략 포트를 다시 받음 | 의존 게이트 | RED 2(의존 · 포트 호출 쌍) |
| 임계 필드의 값 칸 오배정 | 두 표의 짝 | RED 2(필드 전수 · 값 칸 태우기) |
| 확인 command 의 행위자를 `System` 으로 | 우회 ⑧ | RED 6 |
| `toDraft` 에서 필드 하나 누락 | 전수성 게이트 | RED 1(왕복 등식) |
| **MU1** HTTP 층에 JDBC 클라이언트 지름길(production 소스) | 의존 게이트 | RED 1 |
| **MU2** 경계 빈을 쥔 헬퍼를 컨트롤러가 참조(production 소스) | 의존 게이트 | RED 2(의존 · 대상 집합) |
| **MU2b** 다른 패키지의 진짜 컨트롤러 + JDBC(production 소스) | 의존 게이트 | RED 2(의존 · 대상 집합) |
| **MU4** 수집에서 경로 변수 매핑 제외 | 표면 게이트 | RED 2(매핑 앵커 · 문서↔매핑 등식) |
| **F-r2-1 ①** `@Bean RouterFunction` (production 소스) | 의존 게이트 · 표면 실측 | RED / RED |
| **F-r2-1 ②** 빈 이름 URL 매핑 `HttpRequestHandler` | 의존 게이트 · 표면 실측 | RED / RED |
| **F-r2-1 ③** 인증보다 앞선 `OncePerRequestFilter` | 의존 게이트 · 표면 실측 | RED / RED |
| **면제 클래스의 `@Bean RouterFunction`** | 의존 게이트 **통과** · 표면 실측 RED | 두 축이 함께 필요한 이유의 실측 |
| ② 층(조회기)에 `JdbcClient` UPDATE 메서드 추가 | 요청 스코프 층 허용 목록 | RED 1 |
| 컨트롤러가 ③ 층(수집 러너)을 참조 | 제한 층 허용 목록 | RED 1 |
| 위 + 허용 접두에 수집 레인을 열어 첫 규칙을 우회 | 수집 레인 의존 금지 | RED 1 — **두 잠금이 독립이다** |
| value 시점 기준 대조 제거 | 읽기 사이 끼어듦 회귀 | RED 1 |
| 등재된 게이트 test 에 `@Disabled` | `gateExecutionGate` | RED(「건너뛰어졌다」) |
| **A1** ① 층에 `@Bean WebMvcConfigurer` | ① 층 HTTP 확장 API 의존 금지 | RED 1 |
| **A2** ① 층에 `@Bean WebServerFactoryCustomizer` + Tomcat valve | ① 층 HTTP 확장 API 의존 금지 | RED 1 |
| **A6** ① 층 클래스에 `@ControllerAdvice` | ① 층 HTTP 확장 API 의존 금지 | RED 1 — 애너테이션도 의존으로 센다 |
| **A3** ② 층이 ① 층 컴패니언을 참조 | ② 층 허용 app 클래스 목록 | RED 1 |
| **A5** 어댑터 인터페이스에 메서드를 더해 ② 층이 호출 | 어댑터 호출 삼중쌍 | RED 1 |
| **A4** 어댑터 예외의 **자기 멤버** 호출 | 어댑터 예외 멤버 제한 | RED 1 |
| **A4′** 목록 **밖**의 어댑터 예외 타입 참조 + 자기 멤버 호출 | 예외 정확 목록(타입) · 멤버 fail-closed | RED 2 — r3 판정에서는 **초록**이었다(F-r4-2) |
| **오버로드** 등재된 쌍과 같은 이름의 `inTransaction(String)` 호출 | 쌍 좌표의 서술자 | RED 1 |
| **F-r4-1(a)** ② 층이 쓰기 저장소를 쥐고 메모리 포트로 편집 use case 를 조립 | ② 층 타입 목록 · 능력 보유 · use case 조립 | RED 3축 5건 |
| **F-r4-1(b)** 등재된 호출자(`EvaluationDryRunFactory`)가 **다른** use case 를 조립 | use case 조립 쌍 | RED 3축 7건 |
| 규칙 하나를 항상 공집합으로(**여덟** 각각) | 규칙별 비공허성 | **여덟 전부** 그 규칙의 줄이 RED — r4 에 일곱, 여덟째(주입 표면)는 종결 일괄에서 실측 |
| **F-r5-1** ① 층 `@Bean` 의 `() -> Int` SQL 클로저를 컨트롤러가 생성자로 받는다 | 주입 표면 | RED 3(규칙 · 목록 등식 · 운반 타입 검증) |
| **변형 ①** 같은 클로저를 `java.util.function.Supplier<Int>` 로 | 주입 표면 | RED |
| **변형 ②** `Runnable` 로 | 주입 표면 | RED |
| **변형 ③** `java.util.function.IntSupplier` 로 | 주입 표면 | RED |
| **변형 ④** `app.http` 자작 `fun interface` 로 | 주입 표면 | RED — 참조 축은 `bidvector.app.http` 허용이라 조용하다 |
| 등재 없는 게이트 test 셋(다른 클래스 이름 · `*Tests` 접미 · 루트 밖) | 등재 meta-gate | RED 3 — 앞 판의 파일명 술어는 전부 통과시켰다 |
| 등재 목록에서 게이트 test 하나 제거 | 등재 완전성 meta-gate | RED 1(그 이름을 그대로 낸다) |

MU1·MU2·MU2b 는 **위반 fixture 로도 영구 고정**했다(같은 규칙 값에 평가 루트만 바꿔 음성 대조).
production 소스 변이는 측정 뒤 지웠고 `git status` 빈 출력으로 확인했다.

production 소스에 심은 변이는 측정 뒤 되돌리고 `git status --porcelain` · `git diff --numstat` 으로
되돌림을 확인했다. ② 층·③ 층 규칙은 production 이 오늘 어기지 않아 양성만으로는 조용할 수 있어
위반 fixture(`archfixture/violating/app/tiers`)로 **영구 음성 대조**를 남겼다.

## 관리 표면 통로 실측 (OQ-2)

① 층 클래스에 `@Endpoint`/`@ReadOperation`/`@WriteOperation` 을 붙여 빈으로 올렸다. 그 애너테이션
패키지는 금지 접두 **밖**이라 의존 게이트는 **초록**이다(실측). 막는 것은 노출 잠금이다 — 관리
포트에서 `GET /actuator/rogue` **404** · `POST /actuator/rogue` **404** · 발견 경로 `/actuator`
**404**. 새 게이트를 세우지 않고 그 주장을 실측으로 확인한 것이다.

## 자체 우회 탐침 — 규칙을 세운 뒤 스스로 고안한 열

위 변이표는 남이 지목한 형태를 재현한 것이다. 규칙이 선 **뒤에** 직접 고안해 두드린 것은 아래
열(라운드 3 의 N1~N4 · 라운드 4 의 P1~P3 · 라운드 5 의 R1·R8·R9)이고, 막히지 않은 것도 그대로 적는다.

| 탐침 | 형태 | 결과 |
|---|---|---|
| **N1** | 제한 층이 web 컨텍스트에서 **이름으로** 빈을 꺼내 리플렉션으로 SQL | RED 2 — 기존 리플렉션 봉쇄(`app`·`workflow` production 의 리플렉션 API 참조 금지)가 잡는다 |
| **N2** | **허용된 능력 타입만** 쥔 새 `Filter` 를, ① 층이 유일하게 허용된 등록 타입으로 올린다 | 의존 게이트 **전건 통과** · 표면 실측 RED — 두 축이 필요한 이유의 **역방향** 실측 |
| **N3** | 컨트롤러가 요청 시점에 `ServletContext.addServlet` 으로 서블릿을 새로 단다(능력은 캡처) | 게이트 **전건 통과**. 막은 것은 컨테이너다 — 초기화된 컨텍스트에 서블릿을 더하지 못한다(실측). 설령 붙어도 인증 필터가 `/*` 라 우회가 아니다 |
| **N4** | `ServiceLoader<Runnable>` — 어댑터 타입을 한 번도 적지 않고 능력을 꺼낸다 | 게이트 **전건 통과**. 오늘 실행 불가(자원 파일과 구현이 둘 다 없다). 알려진 제한 ⑮ · `OPEN-6A2B-LOCATOR-BAN` |

N2 의 값: 앞선 라운드는 「면제 클래스가 `@Bean` 으로 진입점을 만든다」로 표면 실측이 필요함을 보였고,
N2 는 **반대로** 「모든 타입이 허용 목록 안이어도 새 진입점이 생긴다」를 보인다. 의존 축만으로는
둘 다 조용하다.

### 라운드 5 에서 고안한 셋 — 주입 표면 축

| 탐침 | 형태 | 결과 |
|---|---|---|
| **R1** | ① 층 `@Bean` 이 **허용 목록 안의 도메인 SAM**(`Clock`)의 몸통에 SQL 을 넣고, 제한 층이 그 타입을 받는다 | **전건 초록**. 목록은 **타입**을 적고 구현은 ① 층이 고른다 |
| **R8** | ① 층이 어댑터 경계(`StrategyEditTransaction`) **구현**을 갈아끼워 위임 전에 SQL 을 실행한다 | **전건 초록**. 같은 뿌리다 |
| **R9**(대조) | 제한 층이 `DataSource` 를 직접 받는다 | RED 2(참조 축 · 주입 표면) |

R1·R8 이 보이는 잔여 축은 **구현 선택**이다 — 주입 목록이 닫는 것은 「어떤 **타입**을 받는가」이고,
그 타입의 **몸통**은 여전히 ① 층이 고른다. 오늘 이 코드베이스에서 그 몸통을 SQL 로 채우는 것은
① 층에 JDBC 가 열려 있기 때문이다(D-6A2b-32 ①). 닫는 자리는 둘 중 하나다 — ① 층의 JDBC **멤버
호출** 금지(타입을 넘기는 것은 조립이지만 `Connection`·`JdbcClient` 의 메서드를 부르는 것은 조립이
아니다), 또는 도메인 SAM 의 구현 자리를 어댑터 층으로 고정. 이 slice 의 계약에 없는 축이라 여기서
넓히지 않았고, 알려진 제한과 함께 보고한다.

### 라운드 4 에서 고안한 셋 (D-6A2b-33 경계 안쪽)

| 탐침 | 형태 | 결과 |
|---|---|---|
| **P1** | ① 층 배선이 편집 use case 를 `@Bean` 으로 조립해 내놓는다(평범한 Spring 배선 모양) | RED 1 — use case 조립 규칙이 **네 층 전부**에 서 있다. 이 규칙을 ② 층에만 걸었다면 초록이었다 |
| **P2** | ② 층이 받은 읽기 port 를 쓰기 port 로 **다운캐스트**한다 | 다운캐스트 자체는 **보이지 않는다**(`checkcast` 는 ArchUnit 의 직접 의존이 아니다 — OQ-1 과 같은 성질). 쓰기를 실제로 시도하면 인자 타입이 ② 층 목록 밖이라 RED 이고, 그 값을 만들 길도 없다(`internal constructor`). 알려진 제한 ⑰ |
| **P3** | 컨트롤러가 실행기를 건너뛰고 어댑터 트랜잭션 경계를 직접 부른다 | RED 2(타입 축 · 멤버 축) |

P1 의 값: F-r4-1 의 처방을 「② 층이 조립하지 못한다」로만 읽었다면 ① 층이 조립해 넘기는 길이
그대로 남는다. 규칙의 **대상 층**을 좁히는 것이 곧 구멍이라는 것을 이 탐침이 보인다.

P2 의 값: 이 slice 의 능력 축은 **서명**을 본다. 서명에 나타나지 않는 능력 획득(다운캐스트)은
축 밖이고, 오늘 그것을 무익하게 만드는 것은 게이트가 아니라 **타입 목록과 `internal`** 이다.

N3·N4 는 같은 뿌리다 — **일반 타입으로 능력을 세탁하면 의존 방향이 보이지 않는다**. 앞 세 라운드의
병(대상 종류 열거)과는 다른 계열이라 종류를 더 세는 처방으로는 닫히지 않는다. 닫는 자리는 허용
목록의 입도(패키지 → 타입)이거나 locator 금지이고, 후자가 이미 있는 게이트라 그쪽으로 넘겼다.

## 새 public 표면 전수 (`javap`)

- 실행기·트랜잭션 경계·use case: **포트를 꺼낼 공개 경로 0**. 경계 구현은 `inTransaction` 하나이고
  getter 가 없으며, use case 는 필드가 전부 `private final` 이라 빌려준 블록 안에서도 포트를 꺼낼 수 없다.
- **크기 분할이 만든 JVM 공개 표면 둘**(Kotlin `internal` 최상위 함수는 JVM 에서 `public static` 이다):
  판정 순서 술어 넷과 JSON 원시 읽기 여섯. 새 권한은 아니다 — 전자는 이미 공개 생성자를 가진 결과
  타입만 내고(`Applied`·`AppliedStrategy` 는 내지 못한다) 인자로 `EditSession`(internal constructor)을
  요구하며, 후자는 `JsonNode` 만 읽는다.
- `EditSessionState.WaitingForConfirmation` 에 `baseRevision` 이, `EditCommand.ProvideValue` 에
  `baseRevision` 이 붙었다 — 값 하나씩 늘었을 뿐 생성 경로는 그대로다. command 쪽은 **선택 값**이다
  (필드 행이 아직 없는 최초 value 에는 기준이 없다). command 는 원래 public 타입이라 밖에서 만들 수
  있었고, 그 값을 지어내면 value 시점 대조가 `StaleRevision` 으로 막는다(알려진 제한 ⑥).
- **`StrategyReader` 가 `workflow` 의 공개 표면에 늘었다**(D-6A2b-41 ①). 밖에 허락하는 것은
  「현재 전략을 읽는다」 하나이고, **app 안에서는 제한 층이 그것을 직접 받지 못한다**(r5 정정 —
  도입 직후에는 허용 접두 안이라 컨트롤러가 조회기를 건너뛸 수 있었다) — 이 port 를 구현해도 쓰기 경로는 생기지 않는다(`save` 는
  `StrategyRepository` 에 남고 인자 `AppliedStrategy` 는 `internal constructor` 다). 기존
  `StrategyRepository` 의 표면은 그대로다(상위 타입이 하나 생겼을 뿐이다).
- **`StrategyEditExecutor` 의 생성자에서 `sessionIds: () -> String` 이 사라졌다.** 기본값만 쓰이던
  죽은 seam 이고, 없애면서 밖에서 세션 id 생성을 바꿀 자리도 함께 사라졌다(쓰던 곳은 0 이었다).
- 판정 술어가 하나 늘었다(`internal` 최상위, JVM 공개) — `EditSession`(internal constructor)과
  `OperatorStrategy` 를 요구하고 `Rejected?` 만 낸다. 지문 함수는 `private` 이라 표면이 아니다.
- 406 핸들러가 `ResponseEntity<Void>` 를 낸다 — 본문 없음이 계약이라 `ErrorBody` 를 만들지 않는다.
- 커널의 `apply`·`beginSession`·`expireIfDue` 는 이 slice 이전부터 JVM public 이다(Kotlin `internal`) —
  이번에 나뉜 판정 술어 넷도 같은 형태이고 새 권한을 주지 않는다.

## privacy·누출 축

- cmd: `./gradlew --no-daemon check`(그 안의 누출 게이트 — evidence 를 스캔한다)
- exit: 0
- 핵심 결과: 게이트 통과. 방어 메시지에서 제출 값을 뺐다(요청 본문을 문자열에 넣던 유일한 자리)

- cmd: 참조형 수동 스캔 `grep -rniE -f config/quality/leak-patterns.txt <이 slice 가 만진 경로>`
- exit: 0(일치 있음 — **전부 어휘 수준**)
- 핵심 결과: 일치는 필드 이름 어휘와 Testcontainers 가 만든 test 고정값뿐이다(기존 boot test 넷이
  이미 같은 형태). 실제 비밀값 리터럴 0

## clean-tree 게이트

- cmd: `git status --porcelain -- <in_scope 개별 인자>`
- exit: 0
- 핵심 결과: 빈 출력. **양성 대조**: 계약 파일 끝에 개행 하나를 더하니 ` M` 이 뜨고, 절삭을 되돌리니
  다시 빈 출력이다(비파괴 — `checkout --` 를 쓰지 않았다)
