# M6/6A-2a — 우회 잠금 대응표 · 알려진 제한 · 신규 파일 대조

정본 계약은 `scope.md`. 이 문서는 그 계약의 우회 1~7 과 (2b) 표 각 행이 **무엇으로 닫혔는지**,
어디가 열려 있는지, 그리고 신규 파일이 in_scope 안인지를 잇는다. 실행 명령과 종료 코드는
`commands.md`, 되돌림은 `rollback.md`.

## 우회 1~7 → 잠금

| 우회 | 잠금 | 형태 |
|---|---|---|
| 1. API 포트에서 `/actuator/health` 를 인증 없이 부른다 | `ManagementHealthSurfaceTest` 「API 포트에는 actuator 가 없다 — 자격증명을 줘도 404 다」 + CI 스모크(무인증·인증 둘 다 401·404, **200 이면 실패**) | 구성 — actuator 가 API 포트 컨텍스트에 아예 없다. 「무인증 401」만 재면 같은 포트에 있어도 통과하므로 **자격증명을 주고도 404** 를 단언한다 |
| 2. 관리 포트에서 `env`·`configprops`·`heapdump` 로 값을 읽는다 | 같은 test 「관리 포트에는 health 말고 아무 endpoint 도 없다」 — 런타임 `WebEndpointsSupplier` 집합 == {health} + HTTP 탐침 열둘 404. **잠금 밖에서 `management` 이름공간 키를 정하려 하면 기동을 거부한다**(D-6A2a-10) — 그 축은 `ManagementSurfaceLockTest`(순수 환경)와 `ManagementSurfaceBootRefusalTest`(출하 조립을 실제로 부팅)가 함께 잰다 | 구성(노출 값 + **접두사 거부**) + 실행(런타임 집합). 링크 목록(`/actuator`)도 껐다. r1 이 고친 것: 직전 판은 잠금이 **이름으로 고정한 키**만 봐서 형제 키가 환경 한 줄로 이 우회를 다시 열었다 |
| 3. health 세부로 구성 요소 이름·DB 주소·예외 메시지가 샌다 | 프로브 둘은 본문 키 하나(`status`). 집계 `/actuator/health` 는 **실측한 모양 그대로**(`status`+`groups`, 그룹 이름 둘) 못박는다 — 칸이 하나라도 늘면 붉다. 최상위 `show-details`·`show-components` 는 잠금이 `never` 로 고정하고, **그룹 단위·새 그룹·구성 요소 경로는 접두사 거부가 닫는다**(D-6A2a-10). 구성 요소 경로 `/actuator/health/db` 를 탐침에 넣었다 | 실행 — 응답 본문을 test 와 스모크가 둘 다 단언(집계 경로도 스모크가 잰다, D-6A2a-12). r1 이 고친 것: 그룹 수준 값이 잠긴 최상위값을 덮었다 |
| 4. 빌드 인자·`ENV` 기본값으로 값이 레이어에 박힌다 | Dockerfile 에 `ENV` 0, `ARG` 하나(jar 의 공개 경로). 이미지 `Config.Env` 는 베이스 여섯 항목뿐. `app` 에 `src/main/resources` 자체가 없어 배포물이 설정 파일을 싣지 않는다. compose 는 `${VAR:?}` 만 | 보조(산출물 검사) — 주 잠금은 「앱이 기본값 없이 환경변수만 읽는다」(6A-1·6F-8 의 기존 구조). 문자열 검사라 스타일로 열린다는 사실은 그대로다 |
| 5. root 로 기동하거나 권한을 올린다 | 위생 게이트 (1): `Config.User` + **실 ENTRYPOINT 컨테이너의 모든 프로세스 uid** ≥ 정책 하한. compose·게이트 둘 다 `no-new-privileges`. 변이 ⑤ 로 실측 | 실행 — 선언이 아니라 뜬 컨테이너를 본다(D-6C-9) |
| 6. compose 로 띄우면 수집이 켜져 실 외부 호출을 한다 | compose `app` 에 수집 변수 부재 + `CollectionWiring` 이 `@ConditionalOnProperty havingValue=once`. CI 스모크가 앱 로그에 수집 시작 줄 0 을 잰다 | 구성 + 실행 |
| 7. readiness 가 DB 없이 UP 이라 트래픽을 받는다 | readiness 그룹에 `db` 포함. `ManagementHealthSurfaceTest` 가 postgres 를 **멈춰서** readiness 가 UP 이 아니고 liveness 는 UP 임을 잰다. compose healthcheck 도 readiness 를 쓴다. **참/거짓을 뒤집는 키 넷**(`group.readiness.exclude`·`status.http-mapping.down`·`validate-group-membership`·`probes.add-additional-paths`)은 접두사 거부가 닫고 `ManagementSurfaceBootRefusalTest` 가 각각 거부를 단언한다 | 실행. 더해 `db` 기여자가 classpath 에 없으면 Boot 의 그룹 멤버십 검증이 **기동을 실패**시킨다(조용한 축 소실 없음). r1 이 고친 것: 그 넷이 잠금 밖이라 DOWN 을 200 으로, DB 부재를 UP 으로 바꿀 수 있었다 |

## (2b) 값 획득 축 → 잠금

| 표면 | 판정(계약) | 이 slice 가 낸 잠금 |
|---|---|---|
| 관리 포트 health·liveness·readiness | 경계로 처리, 세부 0 을 실행으로. **D-6A2a-12 가 집계 경로를 「상태 + 우리 그룹 이름」으로 정정**했다 | 프로브 둘 키 하나 · 집계는 키 둘·그룹 이름 둘로 실측 고정 · **스모크가 집계 경로까지 잰다**. 네트워크 노출 통제는 경계 밖(`OPEN-6A2A-MGMT-PORT-EXPOSURE`) |
| 관리 포트의 그 밖의 경로 | 닫는다 | 노출 = health 하나(런타임 집합) + 탐침 열둘 404(구성 요소 경로 포함). **예외 하나: `/error` 는 404 가 아니다** — D-6A2a-13 ② 가 그 사실을 등재하고 test·스모크가 키 셋을 고정한다(값은 실리지 않는다) |
| 앱 이미지 자체 | 경계로 처리 | 위생 게이트 다섯 축 + 위 우회 4 행 |
| compose `app` 서비스 | 경계로 처리, 필터 편집 0 | 인증 필터 두 파일 diff 0(`commands.md`) · API 포트는 루프백에만 publish · 관리 포트 미publish |
| 새 설정 키(`management.*`) | 환경으로 넓힐 수 없어야 한다 — **실측 요구** | **닫았다: 접두사 거부**(D-6A2a-10). 잠금 밖의 어느 속성 소스에든 `management`·`spring.jmx` 이름공간 키가 있으면 기동을 거부한다 — 허용은 `management.server.port` 하나다. r1 실측이 남긴 두 사실을 함께 적는다: ① 계약이 가정한 `PRODUCTION_DISPATCH_PROPERTIES` 자리는 `defaultProperties`(최저 우선순위)라 환경 한 줄이 이긴다 ② `addFirst` 잠금은 **자기가 이름 댄 키에 대해서만** 빈틈이 없었고 형제 키가 열려 있었다. 판정은 원시 문자열이 아니라 Boot 가 내는 **정규형 이름**에 걸어 환경변수 형태·평탄화된 JSON·대괄호 색인을 한 술어로 덮는다 |
| `object`/companion 주입 자리 | 새로 만들지 않는다 | 새 `object`·`companion object` 0 — 잠금은 top-level `val`·함수와 `ApplicationContextInitializer` 하나다 |
| **(r1 신설) 공개 Kotlin 표면 셋** — 거부 대상 이름공간 집합 · 배치 자유 키 집합 · 잠금 밖 키 조회 함수 | 같은 모듈의 test 가 읽는다. 런타임에 값을 **바꿀 수는 없다**(top-level `val`, 재대입 불가) | 경계로 처리 — 조회 함수는 **키 이름만** 돌려주고 값을 담지 않는다(거부 문면도 같다). 세 이름의 리터럴과 포함 관계를 test 가 단언하므로 조용한 확장이 없다 |
| **(r1 신설) 위생 정책 키 `required.executables`** | 정책 파일 편집자가 필수 도구 목록을 정한다 | 경계로 처리 — 정책 파일은 저장소 안이고 값 모양 검증을 받는다(빈 값·공백 섞인 원소·중복 키는 정책 오류로 끊는다). 부재 판정은 이미지 안 PATH 조회이고, 셸이 없으면 **판정 불가로 실패**한다 |
| **(r1 축소) `PersistenceProperties` 의 `data class` 합성 멤버** | — | `equals`/`hashCode`/`copy`/구조 분해/`toString(원문)` 이 **사라졌다**. 표면이 줄었고 호출 자리 전수 확인으로 쓰는 곳이 없음을 확인했다 |

## 계약이 남긴 자리 — 구현 레인의 결정

| 자리 | 결정 | 근거 |
|---|---|---|
| 베이스 이미지 | `eclipse-temurin:21-jre-noble`, 인덱스 다이제스트 `sha256:7edbe853…cccc15f` 고정 | JRE 21(JDK 아님, D-6A2a-3) · `docker buildx imagetools inspect` 가 이 다이제스트를 linux/amd64 manifest 로 풀어 layer 체인을 파생함을 실측(기존 게이트 로직 그대로 선다) · bash·`curl` 이 이미 들어 있어 healthcheck 에 도구를 더 설치하지 않는다 · 컴파일·개발 도구 아홉 전부 부재 |
| 컨테이너 HEALTHCHECK | Dockerfile 에 **넣지 않는다**. compose healthcheck(관리 포트 readiness)에만 둔다 | 도구는 막지 않았다(`curl` 있음). 막는 것은 **포트를 가리킬 방법**이다 — 관리 포트 기본값의 정본은 조립 근(Kotlin)이고, Dockerfile 에 두 번째 기본값을 구우면 드리프트 표면이 하나 늘고(매직 넘버·중복 금지), 기본값 없이 변수만 쓰면 bare `docker run` 이 멀쩡한 앱을 unhealthy 로 신고한다. ml-serving 도 healthcheck 을 compose 에만 둔다(같은 자리) |
| CI 의 자격 전달 | job 안에서 생성 → compose 는 `${VAR:?}` 로 읽는다(argv 없음) → 스모크의 curl 은 `printf | curl --config -` 로 헤더를 **stdin** 으로 받는다 | `-H "…$VALUE"` 는 값을 curl 의 argv 에 실어 같은 러너의 다른 프로세스가 읽을 수 있다. 실측(양성·음성 대조, `commands.md`): `-H` 형태는 프로세스 표에서 26회 관측, `--config -` 형태는 **0회**. `printf` 는 셸 내장이라 별 프로세스를 만들지 않고 값은 파이프로만 흐른다(heredoc 은 bash 가 임시 파일을 쓰므로 고르지 않았다) |
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
9. **접두사 거부는 초기화자가 도는 그 시점의 소스만 본다.** 열거할 수 없는 소스(서블릿 컨텍스트 stub 등)와
   그 뒤에 실체로 채워지는 소스는 판정 모집단 밖이다 — 그 축을 막는 것은 잠금을 **맨 앞**에 심는다는
   사실이고, `ManagementSurfaceLockTest` 가 stub 을 뒤에 실체로 갈아 그 우선순위를 잠근다(`addLast` 로
   바꾸면 붉다, 변이 실측). 값을 실행 중에 바꾸는 경로는 없다(`@ConfigurationProperties` 바인딩은
   초기화자 뒤 한 번이다).
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
| `reports/evidence/m6/6a2a/{checklist,commands,rollback}.md` | `reports/evidence/m6/6a2a/**` | 안 |

**in_scope 밖으로 나가지 않았다** — 계약이 예비로 열어 둔 `build-logic/**` 은 **건드리지 않았다**
(좌표 목록은 `app/build.gradle.kts` 의 `compatibilitySmoke.expectedModules` 에만 더했다).
`out_of_scope` 넷의 diff 0 은 `commands.md` 가 든다: 인증 필터 두 파일 · `openapi/**` ·
마이그레이션 · `tools/one-command-check.sh`.

## 6A-2b 로 넘기는 OPEN

`OPEN-6A1-SCAN-FILTER-SIDE-EFFECT`(위 실측대로 영향 0) · `OPEN-6F9-STRATEGY-WRITE-ENDPOINT` ·
`OPEN-6A3-MAX-ACTIVE-BIDS-EDIT` · `OPEN-6A3-APP-HTTP-DEPENDENCY-ALLOWLIST` ·
`OPEN-6A1-CREDENTIAL-RAW-REINTRODUCTION` · `OPEN-API-WRONG-METHOD-500` — 전부 무변경.
신설 둘(`OPEN-6A2A-DISTROLESS` · `OPEN-6A2A-MGMT-PORT-EXPOSURE`)은 `scope.md` OPEN 표에 있다.
