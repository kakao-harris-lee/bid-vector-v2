# M6/6A-2a — 우회 잠금 대응표 · 알려진 제한 · 신규 파일 대조

정본 계약은 `scope.md`. 이 문서는 그 계약의 우회 1~7 과 (2b) 표 각 행이 **무엇으로 닫혔는지**,
어디가 열려 있는지, 그리고 신규 파일이 in_scope 안인지를 잇는다. 실행 명령과 종료 코드는
`commands.md`, 되돌림은 `rollback.md`.

## 우회 1~7 → 잠금

| 우회 | 잠금 | 형태 |
|---|---|---|
| 1. API 포트에서 `/actuator/health` 를 인증 없이 부른다 | `ManagementHealthSurfaceTest` 「API 포트에는 actuator 가 없다 — 자격증명을 줘도 404 다」 + CI 스모크(무인증·인증 둘 다 401·404, **200 이면 실패**) | 구성 — actuator 가 API 포트 컨텍스트에 아예 없다. 「무인증 401」만 재면 같은 포트에 있어도 통과하므로 **자격증명을 주고도 404** 를 단언한다 |
| 2. 관리 포트에서 `env`·`configprops`·`heapdump` 로 값을 읽는다 | 같은 test 「관리 포트에는 health 말고 아무 endpoint 도 없다」 — 런타임 `WebEndpointsSupplier` 집합 == {health} + HTTP 탐침 열둘 404. **잠금 밖에서 `management` 이름공간 키를 정하려 하면 기동을 거부한다**(D-6A2a-10) — 그 축은 `ManagementSurfaceLockTest`(순수 환경)와 `ManagementSurfaceBootRefusalTest`(출하 조립을 실제로 부팅)가 함께 잰다 | 구성(노출 값 + **접두사 거부**) + 실행(런타임 집합). 링크 목록(`/actuator`)도 껐다. r1 이 고친 것: 직전 판은 잠금이 **이름으로 고정한 키**만 봐서 형제 키가 환경 한 줄로 이 우회를 다시 열었다. r2 가 고친 것: 그 접두사 거부가 **초기화자 시점 소스만** 봐서 늦게 채워지는 소스가 같은 형제 키를 운반했다 — 같은 술어를 refresh 뒤 부모·관리 child 환경 둘에서 다시 돌고(D-6A2a-14), 그 시점은 readiness 가 트래픽을 받기 전이다 |
| 3. health 세부로 구성 요소 이름·DB 주소·예외 메시지가 샌다 | 프로브 둘은 본문 키 하나(`status`). 집계 `/actuator/health` 는 **실측한 모양 그대로**(`status`+`groups`, 그룹 이름 둘) 못박는다 — 칸이 하나라도 늘면 붉다. 최상위 `show-details`·`show-components` 는 잠금이 `never` 로 고정하고, **그룹 단위·새 그룹·구성 요소 경로는 접두사 거부가 닫는다**(D-6A2a-10). 구성 요소 경로 `/actuator/health/db` 를 탐침에 넣었다 | 실행 — 응답 본문을 test 와 스모크가 둘 다 단언(집계 경로도 스모크가 잰다, D-6A2a-12). r1 이 고친 것: 그룹 수준 값이 잠긴 최상위값을 덮었다. r2 가 고친 것: 그 그룹 키가 **늦게 채워지는 소스**로 다시 들어왔다(운반 채널을 막는 대신 판정 **시점**을 고쳤다) |
| 4. 빌드 인자·`ENV` 기본값으로 값이 레이어에 박힌다 | Dockerfile 에 `ENV` 0, `ARG` 하나(jar 의 공개 경로). 이미지 `Config.Env` 는 베이스 여섯 항목뿐. `app` 에 `src/main/resources` 자체가 없어 배포물이 설정 파일을 싣지 않는다. compose 는 `${VAR:?}` 만 | 보조(산출물 검사) — 주 잠금은 「앱이 기본값 없이 환경변수만 읽는다」(6A-1·6F-8 의 기존 구조). 문자열 검사라 스타일로 열린다는 사실은 그대로다 |
| 5. root 로 기동하거나 권한을 올린다 | 위생 게이트 (1): `Config.User` + **실 ENTRYPOINT 컨테이너의 모든 프로세스 uid** ≥ 정책 하한. compose·게이트 둘 다 `no-new-privileges`. 변이 ⑤ 로 실측 | 실행 — 선언이 아니라 뜬 컨테이너를 본다(D-6C-9) |
| 6. compose 로 띄우면 수집이 켜져 실 외부 호출을 한다 | compose `app` 에 수집 변수 부재 + `CollectionWiring` 이 `@ConditionalOnProperty havingValue=once`. CI 스모크가 앱 로그에 수집 시작 줄 0 을 잰다 | 구성 + 실행 |
| 7. readiness 가 DB 없이 UP 이라 트래픽을 받는다 | readiness 그룹에 `db` 포함. `ManagementHealthSurfaceTest` 가 postgres 를 **멈춰서** readiness 가 UP 이 아니고 liveness 는 UP 임을 잰다. compose healthcheck 도 readiness 를 쓴다. **참/거짓을 뒤집는 키 넷**(`group.readiness.exclude`·`status.http-mapping.down`·`validate-group-membership`·`probes.add-additional-paths`)은 접두사 거부가 닫고 `ManagementSurfaceBootRefusalTest` 가 각각 거부를 단언한다 | 실행. 더해 `db` 기여자가 classpath 에 없으면 Boot 의 그룹 멤버십 검증이 **기동을 실패**시킨다(조용한 축 소실 없음). r1 이 고친 것: 그 넷이 잠금 밖이라 DOWN 을 200 으로, DB 부재를 UP 으로 바꿀 수 있었다. r2 가 고친 것: 같은 넷이 늦은 소스로 다시 들어왔다 — 늦은 재검사가 닫고, 출하 이미지에서 그 환경변수를 거부하는 것을 CI 스모크가 잰다 |

## (2b) 값 획득 축 → 잠금

| 표면 | 판정(계약) | 이 slice 가 낸 잠금 |
|---|---|---|
| 관리 포트 health·liveness·readiness | 경계로 처리, 세부 0 을 실행으로. **D-6A2a-12 가 집계 경로를 「상태 + 우리 그룹 이름」으로 정정**했다 | 프로브 둘 키 하나 · 집계는 키 둘·그룹 이름 둘로 실측 고정 · **스모크가 집계 경로까지 잰다**. 네트워크 노출 통제는 경계 밖(`OPEN-6A2A-MGMT-PORT-EXPOSURE`) |
| 관리 포트의 그 밖의 경로 | 닫는다 | 노출 = health 하나(런타임 집합) + 탐침 열둘 404(구성 요소 경로 포함). **예외 하나: `/error` 는 404 가 아니다** — D-6A2a-13 ② 가 그 사실을 등재하고 test·스모크가 키 셋을 고정한다(값은 실리지 않는다) |
| 앱 이미지 자체 | 경계로 처리 | 위생 게이트 다섯 축 + 위 우회 4 행 |
| compose `app` 서비스 | 경계로 처리, 필터 편집 0 | 인증 필터 두 파일 diff 0(`commands.md`) · API 포트는 루프백에만 publish · 관리 포트 미publish |
| 새 설정 키(`management.*`) | 환경으로 넓힐 수 없어야 한다 — **실측 요구** | **닫았다: 접두사 거부 + 늦은 재검사**(D-6A2a-10·14). 잠금 밖의 어느 속성 소스에든 `management`·`spring.jmx` 이름공간 키가 있으면 기동을 거부한다 — 허용은 `management.server.port` 하나다. r1 실측이 남긴 두 사실을 함께 적는다: ① 계약이 가정한 `PRODUCTION_DISPATCH_PROPERTIES` 자리는 `defaultProperties`(최저 우선순위)라 환경 한 줄이 이긴다 ② `addFirst` 잠금은 **자기가 이름 댄 키에 대해서만** 빈틈이 없었고 형제 키가 열려 있었다. 판정은 원시 문자열이 아니라 Boot 가 내는 **정규형 이름**에 걸어 환경변수 형태·평탄화된 JSON·대괄호 색인을 한 술어로 덮는다. r2 가 남긴 셋째 사실: 그 술어를 **한 자리에서만** 돌면 모집단이 「초기화자 시점에 열거 가능한 소스」로 고정돼, refresh 중에 실체로 채워지는 소스가 형제 키를 운반한다 — 같은 술어를 refresh 뒤 부모·관리 child 둘에서 다시 돌고, 거부는 readiness 가 `ACCEPTING_TRAFFIC` 이 되기 전에 난다(그 자리가 뒤로 밀리면 재검사 자신이 기동을 거부한다) |
| `object`/companion 주입 자리 | 새로 만들지 않는다 | 새 `object`·`companion object` 0 — 잠금은 top-level `val`·함수와 `ApplicationContextInitializer` 하나다 |
| **(r1 신설) 공개 Kotlin 표면 셋** — 거부 대상 이름공간 집합 · 배치 자유 키 집합 · 잠금 밖 키 조회 함수 | 같은 모듈의 test 가 읽는다. 런타임에 값을 **바꿀 수는 없다**(top-level `val`, 재대입 불가) | 경계로 처리 — 조회 함수는 **키 이름만** 돌려주고 값을 담지 않는다(거부 문면도 같다). 세 이름의 리터럴과 포함 관계를 test 가 단언하므로 조용한 확장이 없다 |
| **(r1 신설) 위생 정책 키 `required.executables`** | 정책 파일 편집자가 필수 도구 목록을 정한다 | 경계로 처리 — 정책 파일은 저장소 안이고 값 모양 검증을 받는다(빈 값·공백 섞인 원소·중복 키는 정책 오류로 끊는다). 부재 판정은 이미지 안 PATH 조회이고, 셸이 없으면 **판정 불가로 실패**한다 |
| **(r2 신설) 늦은 재검사 공개 표면 둘** — refresh 뒤 판정 함수 · 그것을 얹는 listener | 조립 근과 같은 모듈의 test 가 읽는다. 런타임에 끌 수 없다(스위치를 만들지 않았다) | 경계로 처리 — 함수는 `ApplicationContext` 하나를 받고 **아무 값도 돌려주지 않는다**(위반이면 던진다). 판정 대상은 **refresh 완료 시점에 서 있는** 모든 소스(잠금 제외)다(r3 정정 — 그 뒤에 서는 소스는 `local.` 이름공간뿐인 `server.ports` 하나이고 거부 대상 밖이다). 문면에는 키 이름과 **소스 표지**만 실린다 — **소스 이름은 값을 실을 수 있어서**(r3 정정, privacy-gate r3 L-6: 설정 데이터 소스 이름은 운영자가 준 location 원문을 담고 URL 이면 userinfo 가 그 안에 남는다) 상수 이름만 그대로 나가고 그 밖은 낱말 하나(`other`)다. listener 의 우선순위는 `@Order` 로 준다(인터페이스 수 래칫) |
| **(r2 확대) 배치 자유 키 집합** — 하나 → 둘(`management.server.address` 추가) | 배치가 관리 리스너의 **바인드 범위를 좁힐** 수 있다 | 경계로 처리 — 이 키는 넓힐 방향이 없다(Boot 기본값이 전 인터페이스). 포트 분리 판정은 그대로 서고, 리터럴 집합 단언 + 부팅 양성 대조가 둘을 고정한다 |
| **(r2 확대) 거부 이름공간 집합** — 둘 → 넷(`server.servlet.context-parameters` · `spring.web.error`) | 없음(거부만 늘린다) | 닫는다 — 앱이 둘 다 쓰지 않는다. 늘린 결과는 **기동 거부의 확대**이고 그 운영상 결과를 알려진 제한 15·16 이 적는다 |
| **(r2 신설) CI 컨테이너 축의 거부 스모크(S-22c)** | 없음(게이트다) | 출하 이미지에서 verifier 의 환경변수 한 줄을 거부시키고 **사유까지** 요구한다 — 종료 코드만 보면 DB 부재로도 참이 된다(음성 대조 실측). **r3 정정**: 이 입력이 재는 것은 **조기 거부(D-6A2a-10)** 다(정규형이 운반 이름공간이라 초기화자가 먼저 끊는다). 사유를 그 하나로 좁혔고 step 이름도 바꿨다 — 늦은 재검사를 재는 것은 `ManagementSurfaceLateSourceRefusalTest` 뿐이다 |
| **(r1 축소) `PersistenceProperties` 의 `data class` 합성 멤버** | — | `equals`/`hashCode`/`copy`/구조 분해/`toString(원문)` 이 **사라졌다**. 표면이 줄었고 호출 자리 전수 확인으로 쓰는 곳이 없음을 확인했다 |

## 계약이 남긴 자리 — 구현 레인의 결정

| 자리 | 결정 | 근거 |
|---|---|---|
| 베이스 이미지 | `eclipse-temurin:21-jre-noble`, 인덱스 다이제스트 `sha256:7edbe853…cccc15f` 고정 | JRE 21(JDK 아님, D-6A2a-3) · `docker buildx imagetools inspect` 가 이 다이제스트를 linux/amd64 manifest 로 풀어 layer 체인을 파생함을 실측(기존 게이트 로직 그대로 선다) · bash·`curl` 이 이미 들어 있어 healthcheck 에 도구를 더 설치하지 않는다 · 컴파일·개발 도구 아홉 전부 부재 |
| 컨테이너 HEALTHCHECK | Dockerfile 에 **넣지 않는다**. compose healthcheck(관리 포트 readiness)에만 둔다 | 도구는 막지 않았다(`curl` 있음). 막는 것은 **포트를 가리킬 방법**이다 — 관리 포트 기본값의 정본은 조립 근(Kotlin)이고, Dockerfile 에 두 번째 기본값을 구우면 드리프트 표면이 하나 늘고(매직 넘버·중복 금지), 기본값 없이 변수만 쓰면 bare `docker run` 이 멀쩡한 앱을 unhealthy 로 신고한다. ml-serving 도 healthcheck 을 compose 에만 둔다(같은 자리) |
| CI 의 자격 전달 | job 안에서 생성 → compose 는 `${VAR:?}` 로 읽는다(argv 없음) → 스모크의 curl 은 `printf` 출력을 `curl --config -` 로 흘려 헤더를 **stdin** 으로 받는다 | `-H "…$VALUE"` 는 값을 curl 의 argv 에 실어 같은 러너의 다른 프로세스가 읽을 수 있다. 실측(양성·음성 대조, `commands.md`): `-H` 형태는 프로세스 표에서 26회 관측, `--config -` 형태는 **0회**. `printf` 는 셸 내장이라 별 프로세스를 만들지 않고 값은 파이프로만 흐른다(heredoc 은 bash 가 임시 파일을 쓰므로 고르지 않았다) |
| 노출 넓히기 가능 여부 | 위 (2b) 「새 설정 키」 행 |  |

## `OPEN-6A1-SCAN-FILTER-SIDE-EFFECT` 실측 (수령 조건부 → 6A-2b 로 넘김)

명시 `@ComponentScan` 이 Boot 기본 `excludeFilters` 둘을 가리는 사실은 그대로다. actuator 도입 뒤
**거동 영향은 관측되지 않았다** — 세 축으로 쟀다:

1. **구조**: `AutoConfigurationExcludeFilter` 는 「`@Configuration` ∧ 자동 구성으로 등재된 클래스」만
   뺀다. 컴포넌트 스캔의 기준 패키지는 `bidvector.app` 이고 **`app` 에는 `src/main/resources` 가 없다**
   — 자동 구성 등재 파일이 없으므로 이 필터의 후보 집합이 공집합이다. actuator 의 자동 구성은
   `org.springframework.boot.*` 에 있어 스캔 범위 밖이고, `@EnableAutoConfiguration` 경로로 들어온다.
2. **실행**: 출하 조립이 actuator 자동 구성을 정상으로 받아 노출 집합 == {health} 가 성립한다
   (`ManagementHealthSurfaceTest`) — `@ComponentScan` 재정의가 자동 구성 적용을 방해하지 않는다.
3. **차등**: 기본 필터 둘을 명시로 되살린 트리에서 같은 test 집합을 돌려 결과가 같음을 실측
   (`commands.md` 「기본 필터 복원」 행).

그래서 계약의 「영향이 없으면 실측 결과만 적고 OPEN 은 6A-2b 로」를 따른다 — **필터를 되살리지
않는다**. 남는 위험은 6A-1 이 적은 그대로다: 뒤 slice 가 `bidvector.app` 아래에 자동 구성 등재
클래스나 `TypeExcludeFilter` 빈을 만들면 그 순간 이 가림이 거동으로 나타난다.

## actuator 좌표가 만든 부수 효과 — 전건 `check` 가 잡았다

표적 test 만 돌렸을 때는 초록이었고 **전건 `check --rerun-tasks` 가 붉었다.** actuator 좌표가
들어오자 test 전용 조립(`HttpTestApplication`)에 `RequestMappingHandlerMapping` **빈이 둘**
(우리 것 + actuator 의 controller endpoint 매핑)이 되어 `OperatorAuthenticationTest` 의 타입
주입이 깨졌다(4 test). 이름으로 한정하는 대신 그 조립에서 관리 서버를 끄는 쪽을 골랐다
(`management.server.port=-1` = Boot 의 `DISABLED`, actuator 의 web endpoint 배선 자체가 올라오지
않는다) — 그 test 들이 재는 것은 API 포트의 필터 체인이다.

**덮개 손실 없음**: 「등록된 모든 endpoint 401 기계 전수」의 모집단은 우리 `RequestMappingHandler
Mapping` 이고 actuator endpoint 는 애초에 그 매핑에 없다(별 매핑이다) — 끄든 이름으로 한정하든
모집단이 같다. 출하 조립의 actuator 표면은 포트가 갈려 이 애매성이 없고(실측: 같은 타입을
주입하는 `ProductionAssemblyAuthAuditTest` 는 무영향), `ManagementHealthSurfaceTest` 와 CI
스모크가 그 표면을 잰다.

## r1 판정 → 처분

세 레인이 낸 finding 전부의 처분이다. 계약 갱신은 `scope.md` 「계약 갱신 r1」(D-6A2a-10~13)이 정본이다.

| finding | 처분 | 무엇이 막는가 |
|---|---|---|
| verifier F-1 (high) · code-review HIGH-1 · privacy-gate M-1 — 잠금이 키 열거라 그룹 축이 열린다 | **고쳤다(구조)** — `management`·`spring.jmx` 이름공간 접두사 거부, 허용은 관리 포트 하나 | 기동 거부. `ManagementSurfaceLockTest`(순수 환경 6 축) + `ManagementSurfaceBootRefusalTest`(출하 조립 부팅, 적대 키 7 × 명령행·환경변수 두 채널 + 평탄화 JSON). 변이: 거부 `check` 제거 → 8 FAILED |
| verifier F-2 (medium) — readiness 의 참/거짓이 환경으로 뒤집힌다 | 같은 처방이 닫는다 | 같은 거부. 그 넷(`exclude`·`status.http-mapping.down`·`validate-group-membership`·`probes.add-additional-paths`)이 적대 키 표에 각각 있다 |
| verifier F-3 (low) — env 가 health 를 API 포트에 올린다 | 같은 처방이 닫는다 | `probes.add-additional-paths` 가 적대 키 표에 있다 |
| verifier F-4 (low) · privacy-gate L-1 — 관리 포트 `/error` 200 · 구성 요소 경로 미측정 | **등재 + 모양 고정**(D-6A2a-13 ②) | `/error` 키 셋과 `/actuator/health/db` 404 를 test·스모크가 둘 다 잰다. 비 GET 500 은 본문에 우리 정보가 없음을 test 가 잰다 |
| verifier F-5 (low, 장부) — 집계 `groups` 가 계약 문면과 어긋난다 | **계약 정정**(D-6A2a-12, 팀장) + 스모크가 집계 경로를 잰다 | 키 둘·그룹 이름 둘 고정(test·스모크) |
| verifier F-6 (low) — 위생 게이트 ② 는 이름 열거다 | **알려진 제한으로 등재**(D-6A2a-13 ③) | 주 잠금은 Gradle 구조(`bootJar` = `runtimeClasspath`)와 베이스 layer 체인. 계약 (1) 이 처음부터 보조로 선언했다 |
| code-review MEDIUM-2 — 잠금 test 가 자기참조이고 배선이 잠기지 않았다 | **고쳤다** | 잠금 키 집합·이름공간 집합·배치 자유 키를 **리터럴**로 단언(키 삭제가 곧 RED). 배선은 부팅 test 가 잠근다 — 변이: 초기화자를 `.properties(...)` 로 바꾸면 3 FAILED. 순서는 stub 을 뒤에 실체로 갈아 잠근다 — 변이: `addLast` 로 바꾸면 1 FAILED |
| code-review MEDIUM-3 — `pipefail` + `grep -q` 가 위반을 숨긴다 | **고쳤다** | 로그를 변수로 받고 셸 패턴(`case`)으로 판정. 양성·음성 대조 실측: 대형 로그에서 옛 형태는 **일치를 놓치고**(MISSED) 새 형태는 잡는다(CAUGHT) |
| code-review MEDIUM-4 — 정책 목록 원소 절삭 부재 | **고쳤다(끊는 쪽)** | 공백이 섞인 원소는 정책 오류로 `exit 2`. 실측: `javac, jshell,…` → exit 2 |
| code-review LOW `EXPOSE` | **고쳤다(삭제)** | 이미지 `Config.ExposedPorts`=null 실측. `docker run -P` 가 관리 포트를 publish 하던 갈래도 닫힌다 |
| code-review LOW `--chown` | **고쳤다(삭제)** | 실측: `/application` 과 jar 가 root 소유, 앱 사용자 쓰기 시도가 거부된다. 컨테이너는 그대로 healthy 로 수렴한다 |
| code-review LOW `::add-mask::` 부재 | **고쳤다** | 두 생성 step 이 값을 먼저 마스크한다(제한 14 가 덮는 범위를 적는다) |
| code-review LOW 전송 오류가 `fail()` 을 지나친다 | **고쳤다** | 세 helper 가 전송 실패를 `transport-error` 로 옮겨 판정이 `fail()` 로 떨어진다(앱 로그를 잃지 않는다) |
| code-review LOW `_policy_value` 가 키로 정규식을 만든다 | **고쳤다(리터럴 접두 비교)** | 실측: `sizeXcapYbytes=1` 을 더해도 중복 키로 읽지 않고(0건), 실제 중복 `size.cap.bytes=1` 은 exit 2 |
| code-review LOW 난수 포트 TOCTOU | **고쳤다** | 두 부팅 test 가 `0` 을 쓰고 번호는 뜬 서버의 event 에서 읽는다. 판별은 번호 비교가 아니라 Boot 의 서버 이름공간 |
| code-review Open Question 2 — `curl` 전제 | **이름 있는 축으로 만들었다** | `required.executables=curl`. 변이 실측: 파생 이미지에서 `curl` 을 지우면 위생 게이트 exit 1(그 이름으로 실패) |
| code-review Open Question 1·3 · privacy-gate L-3 | **알려진 제한으로 등재** | 제한 12·13 |
| privacy-gate L-2 — `PersistenceProperties` 의 `data class` | **고쳤다**(D-6A2a-13 ①) | `toString` 에 자격 값 부재를 test 가 잰다. 변이: `data class` 로 되돌리면 FAILED |

## r2 판정 → 처분

세 레인 finding 전부의 처분이다. 계약 갱신은 `scope.md` 「계약 갱신 r2」(D-6A2a-14~17)가 정본이다.

| finding | 처분 | 무엇이 막는가 |
|---|---|---|
| verifier F-1r (high) · privacy-gate M-2 (medium) — 늦게 채워지는 소스가 접두사 거부를 우회한다 | **고쳤다(시점)** — 같은 술어를 refresh 뒤 **부모·관리 child 둘**에서 다시 돈다(D-6A2a-14). 채널 열거를 쓰지 않았다 | `ManagementSurfaceLateSourceRefusalTest` 가 서블릿 컨텍스트에 init-param 을 **직접** 심어 refresh 를 지나는 거부를 단언한다(「관리 child 의 refresh 를 보았다」가 refresh 통과의 표지 — child 는 부모 `finishRefresh` 의 lifecycle 단계에서 선다). 보조로 운반 이름공간을 조기 거부. 변이: 재검사 배선 제거 → 1 FAILED · 운반 이름공간 제거 → 4 FAILED · **자리 이동 둘 → 각각 1 FAILED**(r3 의 기록기 단언이 잠근다 — r2 의 「3 FAILED」는 발동할 수 없는 가드가 낸 것이어서 같은 계열의 다른 형태가 살아남았다). **컨테이너 축(S-22c)은 이 축을 재지 않는다**(r3 정정, code-review r3 LOW-1) — 그 입력은 조기 거부에 먼저 걸리고, 오늘 이미지 밖에서 늦은 재검사에 닿을 채널은 없다. 늦은 재검사를 재는 것은 저 test 뿐이고(게이트 감시 집합 등재) 방어 대상은 **미래의 늦은 소스**다 |
| verifier L-1 · code-review MEDIUM-1 — 노출 통제의 가장 싼 손잡이를 잠금이 함께 거부한다 | **고쳤다**(D-6A2a-15) — `management.server.address` 를 배치 자유 키로 | 이 키는 표면을 좁히기만 한다(Boot 기본값이 전 인터페이스). 리터럴 집합 단언 + 부팅 양성 대조(주소만 더한 환경은 잠금을 통과한다). 포트 분리 판정은 그대로 |
| code-review MEDIUM-2 — 접두사 거부가 Spring 속성이 아닌 환경변수(k8s service link)도 잡는다 | **등재**(D-6A2a-16) — 코드를 좁히지 않는다 | 알려진 제한 15·16 과 아래 「6E runbook 입력」. 바인딩 가능한 Boot 키만 열거하는 쪽으로 좁히면 이 라운드가 고친 열거 결함이 돌아온다 |
| privacy-gate L-4 — `spring.web.error.include-*` 가 관리 포트 `/error` 본문을 넓힌다 | **실측하고 고쳤다**(D-6A2a-17 ①) — `spring.web.error` 를 거부 이름공간에 | 실측: `include-message=always` 하나로 관리 포트 `/error` 본문 키가 셋에서 넷으로 늘었다(`message`). 그 이름공간이 예외 클래스·스택 포함까지 정한다(관리 child 의 오류 endpoint 가 `spring.web` 바인딩을 읽는다 — 바이트코드 실독). 두 채널 부팅 거부 test |
| code-review LOW-3 — 정책 원소 판정이 여전히 공백 **종류**를 열거한다 | **고쳤다(구성)**(D-6A2a-17 ②) — 허용 문자 집합 | 허용 밖 문자 전부가 정책 오류(`exit 2`). 실측: NBSP 원소·쉼표 뒤 공백 둘 다 exit 2, 손대지 않은 정책은 게이트 전건 통과 |
| code-review LOW-1 — 환경변수 축 거부 test 만 문면을 잠그지 않는다 | **고쳤다** | 기대 문면을 손으로 적지 않고 **파생**한다(환경변수 매퍼의 정규형 = `-` 가 사라진 형태). 이 라운드가 실제로 판정을 하나 더 붙였으므로 그 사각이 현실이었다 |
| code-review LOW-2 — 열거 불가 소스 축의 문면이 실제보다 넓게 말한다 | **고쳤다(문면 + 실체)** | 세 자리(판정 함수 주석 · 잠금 test 주석 · 알려진 제한 9)를 「`addFirst` 는 **이름 댄 키**만 지킨다」로 좁히고 그 옆에 형제 키 증인 test 를 세웠다. 축 자체는 늦은 재검사가 닫았다 — 「닫혔다」와 「이 배포물에서는 도달 불가다」를 구별해 적는다 |
| code-review LOW-4 — 전송 오류 fallback 이 curl 의 `000` 에 덧붙는다 | **고쳤다** | 세 helper 가 같은 「대입 뒤 덮어쓰기」 형태. 전송 실패에서 잡히는 값이 단독 `transport-error` 다 |
| code-review LOW-5 — HOME 이 root 소유·읽기 전용 자리를 가리킨다 | **고쳤다** | `--home-dir /nonexistent`. 이미지의 `getent passwd` 실측 + 위생 게이트 전건 통과 |
| code-review LOW-6 — 불필요한 `stream().toList()` · 두 겹 가변 누적 | **고쳤다** | 시퀀스 한 줄기. 단 `filterIsInstance` 는 쓰지 않는다 — reified inline 이라 stdlib 람다 클래스가 이 모듈 아카이브에 복사되고 `jarContentGate` 가 「게이트를 통과한 소스가 아니다」로 끊는다(전건 `check` 가 잡았다) |
| verifier L-2 — 공유 scratchpad 의 잔여 자격 파일 | 저장소 밖 위생(팀장이 삭제) | 이 라운드의 로컬 `container` 재현은 값을 **파일에 두지 않는다** — 값 생성 step 둘을 같은 명령으로 실행 셸의 메모리에만 두고(`$GITHUB_ENV` 가 없는 자리), 실행 로그에 40·48자 hex 단독 토큰이 0 임을 확인한 뒤 임시 파일을 파기했다 |

## r3 판정 → 처분

세 레인 finding 전부의 처분이다(verifier `ready-for-review` M-1·L-1~3 · privacy-gate `pass`
L-5·L-6 · code-review `APPROVE` LOW-1~7). 승인 전 일괄 배치라 계약 갱신은 없다 — 아래 문면 정정과
test·게이트 보강뿐이다.

| finding | 처분 | 무엇이 막는가 |
|---|---|---|
| verifier M-1 — 재검사 **자리**를 옮겨도 전 test 초록(「자리 이동을 구조로 잡는다」가 성립하지 않았다) | **고쳤다(test)** — 적대 부팅이 `ApplicationStartedEvent`·`ApplicationReadyEvent`·`AvailabilityChangeEvent`(수락)를 **한 번도 내지 않았음**을 기록기로 단언한다 | 변이 C(수락 event 로 이동)·D(ready event 로 이동)가 각각 **1 FAILED**(`commands.md` 「변이 — 자리 이동(r3)」). 셋을 함께 보는 이유도 실측이다 — D 에서는 옮긴 재검사가 같은 event 에서 먼저 던져 multicast 를 끊으므로 뒤 둘은 관측되지 않는다 |
| verifier L-2 — readiness 가드와 `@Order` 를 어떤 test 도 잠그지 않는다 | **가드를 뺐다** | 가드는 발동할 수 없었다(같은 event 를 받는 가용성 bean 이 이 listener **뒤에** 상태를 기록한다). 죽은 코드가 구조적 방어로 읽히는 것을 없애고, 같은 성질을 위 기록기 단언이 실제로 잠근다. `@Order` 는 그대로 두고 잠기지 않는다는 사실을 제한 19 에 적는다 |
| privacy-gate L-6 · code-review LOW-2 — 거부 문면의 **소스 이름**이 값을 실을 수 있다 | **고쳤다(구성) + 문면 정정** — 상수 이름만 그대로, 그 밖은 낱말 하나(`other`) | RED→GREEN 실측(`commands.md` 「변이 — 거부 문면의 소스 표지」). 상수 이름 열은 test 가 Boot·Spring 상수와 대조한다. 이 열거는 **공개하는 쪽**이라 fail-closed 다 — 빠뜨린 이름은 문면 품질만 떨어뜨리고 표면을 열지 않는다. 클래스 이름 형태는 리플렉션 봉쇄 규칙이 막았다(`commands.md` acceptance 절) |
| privacy-gate L-5 · verifier L-3 — 「트래픽을 한 번도 받지 않는다」가 사실이 아니다 | **문면 정정 + 등재** | 제한 18. KDoc ③ 과 test 이름을 「readiness 가 수락을 알리기 전」으로 좁혔다. 재검사 시점의 503 자체가 connector 가 이미 bind 됐다는 증거다. 조기 거부가 **실질 방어의 일부**임을 같은 제한에 적는다. `SmartInitializingSingleton` 이동은 이 라운드에서 하지 않고 OPEN 후보로 남긴다(`scope.md` OPEN 표) |
| code-review LOW-1 — S-22c 가 받을 수 있는 둘 가운데 하나만 도달 가능하고 처분표가 이 스모크를 늦은 재검사의 잠금으로 적는다 | **고쳤다(좁힘) + 재귀속** | `case` 를 `D-6A2a-10` 하나로 좁히고 step 이름을 재는 것에 맞췄다. (2b) 표의 S-22c 행과 위 r2 F-1r 행이 이제 조기 거부 쪽을 가리킨다. **오늘 이미지 밖에서 늦은 재검사에 닿을 채널은 없다**(설정 데이터·명령행·JSON 은 초기화자보다 앞에 서고 배포물에 `web.xml`·JNDI 가 없다) — 그 재검사는 미래의 늦은 소스에 대한 방어다 |
| code-review LOW-3 — child 축의 거부는 `IllegalStateException` 이 아니다 | **KDoc 한 줄** | 부모 축은 그대로 올라오고 child 축은 `DefaultLifecycleProcessor` 가 `ApplicationContextException` 으로 감싼다 — child 축 test 에서 `shouldThrow<IllegalStateException>` 은 공허해진다 |
| code-review LOW-4 — 「판정 대상은 환경의 **모든** 소스」가 한 칸 넓다 | **문면 정정** | 「refresh 완료 시점에 서 있는 모든 소스」로 좁혔다(KDoc · `scope.md` D-6A2a-14 · 위 (2b) 행 · 제한 9). 그 뒤에 서는 유일한 소스가 `server.ports`(`local.*`, 거부 대상 밖)라는 사실을 함께 적는다 |
| code-review LOW-5 — readiness 가드가 기동 뒤의 정상 refresh 도 거부한다 | **원인을 없앴다** | 가드 제거로 이 축이 사라졌다(제한 9 의 전제에 매달 필요가 없어졌다). 두 번째 refresh 가 생기면 늦은 재검사는 같은 술어를 한 번 더 도는 것이 전부다 |
| code-review LOW-6 — S-22c 가 실패할 때 컨테이너 로그를 버린다 | **고쳤다** | 세 실패 분기가 **고정 표지 줄만** 낸다(`D-6A2a-`·Boot 의 기동 실패 표지). 전문을 찍지 않는 이유는 뒤 편집이 실 값을 붙이는 날이다 |
| code-review LOW-7 — `_mgmt` 의 군더더기 서브셸 · 「좌표」 키의 문면 | **고쳤다** | `$'…'` 형태(위 두 helper 와 같아진다) · 정책 주석에 「값은 좌표 전문이 아니라 이름 조각이고 `:` 는 허용 문자 밖」 |
| verifier L-1 — 관리 child 축은 배선으로만 덮인다(부모만 판정하는 변이가 살아남는다) | **등재** | 제한 19. 환경만으로 도달 가능한 child 전용 적대 소스를 verifier 가 **구성하지 못했다**(그 입구는 조기 거부가 막는다) — 그래서 지금 표면을 여는 편차는 아니다. child 서블릿 컨텍스트에만 init-param 을 심는 test 는 이 라운드 범위 밖이다 |

## 알려진 제한

1. **집계 `/actuator/health` 는 그룹 이름 둘을 낸다 — 이제 계약 결정이다(D-6A2a-12).** Boot 4.1.1 실측 —
   세부 설정과 무관하다(그룹이 있으면 항상 실린다). 값은 우리가 지은 이름(liveness·readiness)이고 구성
   요소 이름·DB 주소·예외 메시지·버전은 없다. 「키 하나」로 만들 방법은 그룹을 없애는 것뿐이라 고르지
   않았다 — 모양을 고정해 칸이 늘면 붉게 하고, **스모크도 집계 경로를 잰다**(그룹 이름이 환경으로 늘어나는
   축은 접두사 거부가 닫았지만, 컨테이너에서도 한 번 더 본다).
2. **관리 포트 값은 환경이 정한다.** 잠금은 노출·세부·프로브·그룹까지고, 포트 자체는 배치의 선택이다.
   그 자유의 상한은 「API 포트와 분리돼 있어야 한다」 하나다(`ManagementPortType` 판정, 기동 거부).
   같은 포트로 몰면 health 가 인증 필터 뒤로 들어가 프로브가 막히고 audit 행이 부푼다 — **경계를 여는
   것은 아니지만** 가용성·장부 품질의 후퇴다.
3. **베이스 layer 체인 판정은 레지스트리 접근을 요구하고 linux/amd64 가 하드코딩이다**(ml-serving
   정책과 같은 제한, `OPEN-6C-MULTIARCH`). 접근이 없으면 이 축은 판정 불가로 실패한다(fail-closed).
4. **위생 게이트의 「비밀값 부재」는 문자열·메타데이터 축이다.** 레이어 파일 전수 검색을 하지 않는다 —
   주 잠금은 앱이 기본값 없이 환경변수만 읽는 구조다(계약의 (1) 이 그렇게 정했다).
5. **금지 실행 파일 판정의 PATH 조회 축은 이미지에 셸을 요구한다.** 셸이 없으면 판정 불가로 실패한다
   (거짓 통과가 아니라 실패). distroless 채택(`OPEN-6A2A-DISTROLESS`)은 이 축과 compose healthcheck 를
   함께 바꾸므로 따로 판단한다.
6. **`OPEN-6C-POLICY-GATE-STRUCTURAL` 착수 조건 미충족을 사실로 기록한다.** D-6C-11 이 그 조건을
   「배포 경로(레지스트리·배포)가 생길 때」로 정했고 이 slice 는 레지스트리·배포를 만들지 않는다.
   다만 이 slice 가 그 정책 파서를 **한 번 더 확장했다**(정책 인자화 · kind 분기 · 항목 수 하한) —
   그 확장이 6C 가 진단한 「열거 방어의 서명」을 되풀이하지 않도록 새 축마다 양성 대조를 붙였다.
7. **`ml-serving:local` 이 이 개발 호스트에서 336MB 로 측정된다**(정책 상한 400MB, 6C evidence 의
   113.7MB 와 다르다). 이 slice 는 그 이미지·정책을 건드리지 않았고 상한 안이지만 여유가 16% 다 —
   ml-serving 축의 관찰 사항으로 남긴다(out_of_scope).
8. **집계 경로 하나는 인증 없이 그룹 구성을 알린다**(제한 1 과 같은 사실). 프로브만 노출하고 집계를
   끄는 설정은 Boot 에 없다.
9. **접두사 거부는 두 자리에서 돈다 — 조기(초기화자)와 늦은 재검사(refresh 뒤).** 조기 검사의 모집단은
   그 시점에 **열거 가능한** 소스뿐이고, 그것만으로는 닫히지 않는다: 그 뒤에 실체로 채워지는 소스가
   잠금이 **이름 대지 않은** 형제 키를 환경변수보다 높은 우선순위로 들여왔다(verifier r2 F-1r 이 출하
   이미지에서 재현). `addFirst` 가 지키는 것은 잠금이 **이름 댄 키**뿐이다 — 「닫혔다」가 아니라 「그
   키에 대해서는 순서가 이긴다」가 정확한 문장이고, `ManagementSurfaceLockTest` 가 그 형태와 그 한계를
   각각 잰다(`addLast` 로 바꾸면 붉다 · 형제 키는 증인 test 가 실측한다). 늦은 재검사(D-6A2a-14)가
   같은 술어를 **부모와 관리 child 환경 둘**에서 다시 돌아 그 축을 닫고, 거부는 readiness 가
   **수락을 알리기 전**에 난다(r3 정정 — 제한 18; 실측: 재검사 시점 503, 기동 완료 뒤 200). 재검사의
   모집단은 **refresh 완료 시점에 서 있는** 모든 소스다(r3 정정 — 그 뒤에 서는 소스는 `local.`
   이름공간뿐인 `server.ports` 하나이고 거부 대상 밖이다). 남는 제한은 **그 자리 뒤에 값을 바꾸는
   경로**인데 이 조립에는 없다 — `@ConfigurationProperties` 재바인딩 경로(refresh scope · config
   client)를 쓰지 않는다. 그 경로가 생기면 재검사는 같은 술어를 한 번 더 도는 것이 전부다(r3 가
   readiness 가드를 뺐으므로 정상 refresh 를 기동 실패로 바꾸는 축은 없다).
10. **이름 기반 보조 판정 둘은 그대로 우회된다**(D-6A2a-13 ③, verifier r1 F-6). 이름을 바꾼 test jar 를
   의존 layer 에 넣거나 PATH 밖 절대 경로에 컴파일러를 두면 위생 게이트의 ②·① 축이 「부재」로 읽는다.
   계약 (1) 이 이 둘을 처음부터 **보조·열거**로 선언했고 주 잠금은 각각 「`bootJar` 는 `runtimeClasspath`
   만 담는다」는 Gradle 구조와 「베이스가 JRE 다」는 layer 체인 판정이다. 공격자가 Dockerfile 저자인
   경우는 경계 밖이다(scope 위협 모델).
11. **관리 포트의 비 GET 은 500 이다**(D-6A2a-13 ②). 본문은 일반 오류뿐임을 test 가 잰다(구성 요소·주소·
    예외·그룹 이름 문자열 부재). `OPEN-API-WRONG-METHOD-500` 에 관리 포트 관측을 덧붙여 6A-2b 로 넘긴다.
12. **Dockerfile 별 `.dockerignore` 는 BuildKit 만 읽는다**(privacy-gate r1 L-3, code-review Open Question 1).
    이 저장소에 선례가 없는 형태다 — CI(`docker build`, BuildKit 기본)와 compose v2 는 읽고, 양성 대조
    (제외 대상 경로 COPY → 빌드 실패)와 `container` job 전건 0 이 그것을 실측한다. `DOCKER_BUILDKIT=0`
    레거시 빌더로 빌드하면 컨텍스트가 넓어진다(레이어에는 닿지 않는다 — runtime stage 는 builder 의
    풀린 layer 만 복사한다). 루트 `.dockerignore` 는 ml-serving 컨텍스트에 함께 영향을 주므로 따로 판단한다.
13. **위생 게이트 프로브의 표집 창은 「앱이 잠깐 살아 있다」에 기댄다**(code-review Open Question 3).
    뒤 slice 가 커넥션 풀(`OPEN-6A1-CONNECTION-POOL`)을 들여 기동 실패를 즉시로 만들면 이 축은 **판정
    불가로 실패**한다(조용한 통과가 아니라 실패이므로 안전한 방향이다).
14. **`::add-mask::` 는 러너 로그만 덮는다.** 값은 이 step 셸의 프로세스 환경과 컨테이너 환경에도 있고,
    그 축은 job 수명짜리 난수라는 사실이 든다(production 의미 없음). argv 축은 이미 닫혀 있다.
15. **관리 이름공간의 환경변수는 포트·주소 둘 말고 전부 기동을 거부한다**(D-6A2a-16 ①). 거부 대상은
    `MANAGEMENT_*`·`SPRING_JMX_*`·`SERVER_SERVLET_CONTEXTPARAMETERS_*`·`SPRING_WEB_ERROR_*` 로 시작하는
    환경변수 전부이며, 허용은 `MANAGEMENT_SERVER_PORT`·`MANAGEMENT_SERVER_ADDRESS` 둘이다. **끄는
    스위치는 없다** — 만들지 않았다. 기존 배치가 그 접두사의 변수를 갖고 있으면 그 자리에서 기동이
    거부되고, 문면이 **어느 키인지와 어느 소스에서 왔는지**를 말한다(값은 싣지 않는다). 처방은 그
    변수를 지우는 것이다(잠금과 같은 값을 주려는 경우도 거부된다 — 같은 값을 두 자리에 두지 않는다).
    코드를 「바인딩 가능한 Boot 키만」으로 좁히지 않는다: 그 순간 이 slice 가 고친 열거 결함이 돌아온다.
16. **오케스트레이터가 주입하는 환경변수도 제한 15 를 밟는다**(D-6A2a-16 ②). 가장 현실적인 형태는
    Kubernetes 의 service link 다 — 같은 namespace 에 `management` 라는 이름의 Service 가 있으면 파드에
    `MANAGEMENT_SERVICE_HOST`·`MANAGEMENT_PORT` 계열이 자동 주입되고(기본값이 켜짐), 그 전부가 관리
    이름공간으로 내려가 **기동이 거부된다**. 원인이 이 앱과 무관한 자리(Service 이름)에 있다는 것이
    이 제한의 요점이다. 거부하는 방향 자체는 안전하고 배치 구성은 경계 밖이므로 코드를 바꾸지 않는다 —
    처방은 배치 쪽이다(아래 runbook 입력).
17. **관리 포트 값에 숫자가 아닌 값을 주면 Boot·Spring 의 변환 실패 문면에 그 값이 실린다**(privacy-gate
    r2 Info). 포트·주소 둘은 타입 있는 키이고, 변환 실패 보고는 값을 절삭해 문면에 낸다 — 이 slice 가
    만든 거동이 아니라 타입 있는 모든 키에서 같다. 자격 값을 이 키에 잘못 넣는 경우가 그 노출 경로다.
18. **늦은 재검사는 관리 포트 connector 가 이미 bind 된 뒤에 돈다**(r3 — privacy-gate r3 L-5 ·
    verifier r3 L-3). 계약 D-6A2a-14 의 「트래픽을 한 번도 받지 않는다」는 **readiness 의미**로 읽는다:
    거부는 readiness 가 `ACCEPTING_TRAFFIC` 을 알리기 전에 나고, 그 전의 프로브는 503 이다. 그러나
    `finishRefresh` 의 lifecycle 단계가 이 event 보다 앞이라 관리 포트는 재검사보다 **먼저 열린다** —
    이 slice 의 test 자신이 재검사 시점에 관리 포트로 요청을 보내 503 을 받는 것이 그 증거다. 그
    밀리초 창에서 부모의 늦은 소스가 그룹 형제 키를 실었다면 `/actuator/health/readiness` 의 503
    본문에 구성 요소 이름 수준의 세부가 실릴 수 있다(창이 닫히면 프로세스가 죽는다). **지금 그 창에
    닿는 유일한 경로는 조기 거부가 막고 있다** — 그러므로 계약이 「보조」로 이름 붙인 조기 거부
    (D-6A2a-10, 운반 이름공간 포함)는 이 축에서 **실질 방어의 일부**다. 창 자체를 없애는 형태
    (connector 시작 전 판정)는 `OPEN-6A2A-PRE-CONNECTOR-CHECK` 로 남겼다.
19. **잠기지 않는 형태 둘을 사실로 적는다**(r3 — verifier r3 L-1·L-2). ① **관리 child 축**: 부모만
    판정하는 변이는 표적 test 전건 초록이다. 환경만으로 도달 가능한 child 전용 적대 소스는 verifier 가
    **구성하지 못했고**(그 입구인 서블릿 컨텍스트 이름공간은 조기 거부가 막는다) child 축 보증은
    배선 표지와 음성 대조(정상 환경은 거부되지 않는다)뿐이다 — 「부모·child **둘 다**」라는 계약
    문면을 잠그려면 child 서블릿 컨텍스트에만 init-param 을 심는 test 가 필요하다. ② **listener
    우선순위(`@Order`)**: 제거 변이가 초록이다. 이 slice 에는 같은 event 로 위반 상태에 먼저 반응하는
    listener 가 없어 거동 차이를 만들 자리가 없다.
20. **거부 문면은 상수 이름이 아닌 소스를 낱말 하나(`other`)로 말한다**(r3). 운영자가 「어느 채널로
    들어왔는가」를 문면에서 바로 읽을 수 있는 것은 그 소스가 Boot·Spring 의 상수 이름을 갖는 경우다
    (환경변수·시스템 속성·명령행·`SPRING_APPLICATION_JSON`·서블릿 init-param 둘·JNDI·`random`·
    기본값·`server.ports` — 오늘 이 앱이 실제로 쓰는 채널 전부가 여기 있다). 설정 데이터·설정 트리
    채널은 앱이 쓰지 않고, 그 이름에는 운영자가 정한 위치 문자열이 들어가므로 **일부러 싣지 않는다**.
    소스 클래스로 분류를 쪼개는 형태는 `bidvector.app` 의 리플렉션 봉쇄 규칙이 막는다(D-6F8-13 F2-2).

## 6E runbook 입력 (D-6A2a-16 — 배치 쪽 처방)

운영 runbook 이 세워지는 slice(6E)로 넘기는 문장이다. 여기 적는 이유는 **운영자가 보는 자리가
되돌림 문서의 비활성화 절이 아니기 때문**이다(code-review r2 MEDIUM-2).

1. 앱 환경에서 **관리 이름공간 변수를 지운다** — 남길 수 있는 것은 `MANAGEMENT_SERVER_PORT` 와
   `MANAGEMENT_SERVER_ADDRESS` 둘뿐이다. 그 밖의 `MANAGEMENT_*`·`SPRING_JMX_*`·
   `SERVER_SERVLET_CONTEXTPARAMETERS_*`·`SPRING_WEB_ERROR_*` 는 기동을 거부한다.
2. **k8s**: `enableServiceLinks: false` 를 파드 spec 에 둔다. 또는 그 접두사와 겹치는 이름의 Service
   (특히 `management`)를 만들지 않는다. 이 둘 중 하나가 없으면 파드가 기동하지 못한다.
3. 관리 포트의 **네트워크 노출 통제는 배치가 진다**(`OPEN-6A2A-MGMT-PORT-EXPOSURE`). 앱 쪽 수단은
   `MANAGEMENT_SERVER_ADDRESS` 로 바인드 범위를 좁히는 것 하나이고, 그 밖은 NetworkPolicy·방화벽이다.
   compose 는 관리 포트를 host 에 publish 하지 않는다.
4. 기동이 거부되면 **문면이 키 이름과 소스 표지를 말한다** — 같은 키가 환경변수·명령행·서블릿
   init-param 어디로 들어왔는지가 그 줄에 있다(Boot 상수 이름을 갖는 소스는 그 이름으로, 그 밖은
   `other` 로 — 제한 20). 속성 값도, 소스 이름이 담을 수 있는 설정 위치 문자열도 실리지 않는다.
5. **설정 위치 경로·URL 에 비밀값을 담지 않는다.** `spring.config.location`·`additional-location`·
   `import` 의 문자열은 Boot 의 실패 문면 여럿에 실린다(이 slice 의 거부 문면은 싣지 않지만 Boot
   자신의 설정 데이터 실패 보고는 싣는다). URL 형태의 import 라면 userinfo·쿼리 토큰을 쓰지 않는다.

## 신규 파일 ↔ in_scope 대조

| 신규 파일 | in_scope 항목 | 판정 |
|---|---|---|
| `docker/app.Dockerfile` | `docker/app.Dockerfile` | 안 |
| `docker/app.Dockerfile.dockerignore` | `docker/app.Dockerfile.dockerignore` | 안 |
| `config/quality/image-hygiene-policy-app.properties` | `config/quality/image-hygiene-policy*.properties` | 안 |
| `app/src/main/kotlin/bidvector/app/ManagementSurface.kt` | `app/src/main/kotlin/bidvector/app/**` | 안 |
| `app/src/test/kotlin/bidvector/app/ManagementSurfaceLockTest.kt` | `app/src/test/kotlin/bidvector/app/**` | 안 |
| `app/src/test/kotlin/bidvector/app/management/ManagementHealthSurfaceTest.kt` | `app/src/test/kotlin/bidvector/app/**` | 안 |
| `app/src/test/kotlin/bidvector/app/management/ManagementSurfaceBootRefusalTest.kt`(r1) | `app/src/test/kotlin/bidvector/app/**` | 안 |
| `app/src/test/kotlin/bidvector/app/wiring/PersistencePropertiesTest.kt`(r1) | `app/src/test/kotlin/bidvector/app/**` | 안 |
| `app/src/test/kotlin/bidvector/app/management/ManagementSurfaceLateSourceRefusalTest.kt`(r2) | `app/src/test/kotlin/bidvector/app/**` | 안 — 게이트 감시 집합(`config/quality/gate-tests.properties`)에도 등재했다 |
| `reports/evidence/m6/6a2a/{checklist,commands,rollback}.md` | `reports/evidence/m6/6a2a/**` | 안 |

**r3 은 신규 파일을 만들지 않았다** — 만진 경로 다섯(`ci.yml` · `ManagementSurface.kt` ·
`ManagementSurfaceLockTest.kt` · `ManagementSurfaceLateSourceRefusalTest.kt` ·
`image-hygiene-policy-app.properties`)이 전부 위 표와 in_scope 안에 이미 있다(`git diff
--name-status` 로 대조했다 — 신규 0).

**in_scope 밖으로 나가지 않았다** — 계약이 예비로 열어 둔 `build-logic/**` 은 **건드리지 않았다**
(좌표 목록은 `app/build.gradle.kts` 의 `compatibilitySmoke.expectedModules` 에만 더했다).
`out_of_scope` 넷의 diff 0 은 `commands.md` 가 든다: 인증 필터 두 파일 · `openapi/**` ·
마이그레이션 · `tools/one-command-check.sh`.

## 6A-2b 로 넘기는 OPEN

`OPEN-6A1-SCAN-FILTER-SIDE-EFFECT`(위 실측대로 영향 0) · `OPEN-6F9-STRATEGY-WRITE-ENDPOINT` ·
`OPEN-6A3-MAX-ACTIVE-BIDS-EDIT` · `OPEN-6A3-APP-HTTP-DEPENDENCY-ALLOWLIST` ·
`OPEN-6A1-CREDENTIAL-RAW-REINTRODUCTION` · `OPEN-API-WRONG-METHOD-500` — 전부 무변경.
신설 둘(`OPEN-6A2A-DISTROLESS` · `OPEN-6A2A-MGMT-PORT-EXPOSURE`)은 `scope.md` OPEN 표에 있다.
