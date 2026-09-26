# M6/6A-2a — 우회 잠금 대응표 · 알려진 제한 · 신규 파일 대조

정본 계약은 `scope.md`. 이 문서는 그 계약의 우회 1~7 과 (2b) 표 각 행이 **무엇으로 닫혔는지**,
어디가 열려 있는지, 그리고 신규 파일이 in_scope 안인지를 잇는다. 실행 명령과 종료 코드는
`commands.md`, 되돌림은 `rollback.md`.

## 우회 1~7 → 잠금

| 우회 | 잠금 | 형태 |
|---|---|---|
| 1. API 포트에서 `/actuator/health` 를 인증 없이 부른다 | `ManagementHealthSurfaceTest` 「API 포트에는 actuator 가 없다 — 자격증명을 줘도 404 다」 + CI 스모크(무인증·인증 둘 다 401·404, **200 이면 실패**) | 구성 — actuator 가 API 포트 컨텍스트에 아예 없다. 「무인증 401」만 재면 같은 포트에 있어도 통과하므로 **자격증명을 주고도 404** 를 단언한다 |
| 2. 관리 포트에서 `env`·`configprops`·`heapdump` 로 값을 읽는다 | 같은 test 「관리 포트에는 health 말고 아무 endpoint 도 없다」 — 런타임 `WebEndpointsSupplier` 집합 == {health} + HTTP 탐침 열하나 404. `ManagementSurfaceLockTest` 가 환경변수·명령행이 노출을 넓히지 못함을 잰다 | 구성(노출 값) + 실행(런타임 집합). 링크 목록(`/actuator`)도 껐다 |
| 3. health 세부로 구성 요소 이름·DB 주소·예외 메시지가 샌다 | 프로브 둘은 본문 키 하나(`status`). 집계 `/actuator/health` 는 **실측한 모양 그대로**(`status`+`groups`) 못박는다 — 칸이 하나라도 늘면 붉다. `show-details`·`show-components` 는 잠금이 `never` 로 고정 | 실행 — 응답 본문을 test 와 스모크가 둘 다 단언 |
| 4. 빌드 인자·`ENV` 기본값으로 값이 레이어에 박힌다 | Dockerfile 에 `ENV` 0, `ARG` 하나(jar 의 공개 경로). 이미지 `Config.Env` 는 베이스 여섯 항목뿐. `app` 에 `src/main/resources` 자체가 없어 배포물이 설정 파일을 싣지 않는다. compose 는 `${VAR:?}` 만 | 보조(산출물 검사) — 주 잠금은 「앱이 기본값 없이 환경변수만 읽는다」(6A-1·6F-8 의 기존 구조). 문자열 검사라 스타일로 열린다는 사실은 그대로다 |
| 5. root 로 기동하거나 권한을 올린다 | 위생 게이트 (1): `Config.User` + **실 ENTRYPOINT 컨테이너의 모든 프로세스 uid** ≥ 정책 하한. compose·게이트 둘 다 `no-new-privileges`. 변이 ⑤ 로 실측 | 실행 — 선언이 아니라 뜬 컨테이너를 본다(D-6C-9) |
| 6. compose 로 띄우면 수집이 켜져 실 외부 호출을 한다 | compose `app` 에 수집 변수 부재 + `CollectionWiring` 이 `@ConditionalOnProperty havingValue=once`. CI 스모크가 앱 로그에 수집 시작 줄 0 을 잰다 | 구성 + 실행 |
| 7. readiness 가 DB 없이 UP 이라 트래픽을 받는다 | readiness 그룹에 `db` 포함. `ManagementHealthSurfaceTest` 가 postgres 를 **멈춰서** readiness 가 UP 이 아니고 liveness 는 UP 임을 잰다. compose healthcheck 도 readiness 를 쓴다 | 실행. 더해 `db` 기여자가 classpath 에 없으면 Boot 의 그룹 멤버십 검증이 **기동을 실패**시킨다(조용한 축 소실 없음) |

## (2b) 값 획득 축 → 잠금

| 표면 | 판정(계약) | 이 slice 가 낸 잠금 |
|---|---|---|
| 관리 포트 health·liveness·readiness | 경계로 처리, 세부 0 을 실행으로 | 프로브 둘 키 하나 · 집계는 실측 모양 고정 · 스모크 동일 단언. 네트워크 노출 통제는 경계 밖(`OPEN-6A2A-MGMT-PORT-EXPOSURE`) |
| 관리 포트의 그 밖의 경로 | 닫는다 | 노출 = health 하나(런타임 집합) + 탐침 열하나 404 |
| 앱 이미지 자체 | 경계로 처리 | 위생 게이트 다섯 축 + 위 우회 4 행 |
| compose `app` 서비스 | 경계로 처리, 필터 편집 0 | 인증 필터 두 파일 diff 0(`commands.md`) · API 포트는 루프백에만 publish · 관리 포트 미publish |
| 새 설정 키(`management.*`) | 환경으로 넓힐 수 없어야 한다 — **실측 요구** | **실측 결과: 계약이 가정한 형태(`PRODUCTION_DISPATCH_PROPERTIES` 자리)로는 넓혀진다.** 그 자리는 `defaultProperties`(가장 낮은 우선순위)라 환경변수 한 줄이 이긴다. 그래서 `MANAGEMENT_SURFACE_LOCK` 을 `addFirst` 로 심었다 — 환경변수·명령행 둘 다 못 이긴다(test 가 잠금 전/후를 함께 단언해 공허한 참을 막는다). **관리 포트 값 하나만 환경이 정한다**(배치가 포트를 고른다) — 그 자유는 `ManagementPortType` 분리 판정이 가둔다 |
| `object`/companion 주입 자리 | 새로 만들지 않는다 | 새 `object`·`companion object` 0 — 잠금은 top-level `val`·함수와 `ApplicationContextInitializer` 하나다 |

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

## 알려진 제한

1. **집계 `/actuator/health` 는 그룹 이름 둘을 낸다.** Boot 4.1.1 실측 — 세부 설정과 무관하다(그룹이
   있으면 항상 실린다). 값은 우리가 지은 이름(liveness·readiness)이고 구성 요소 이름·DB 주소·예외
   메시지·버전은 없다. 「키 하나」로 만들 방법은 그룹을 없애는 것뿐이라 고르지 않았다 — 대신 **모양을
   고정**해 칸이 늘면 붉게 했다.
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

## 신규 파일 ↔ in_scope 대조

| 신규 파일 | in_scope 항목 | 판정 |
|---|---|---|
| `docker/app.Dockerfile` | `docker/app.Dockerfile` | 안 |
| `docker/app.Dockerfile.dockerignore` | `docker/app.Dockerfile.dockerignore` | 안 |
| `config/quality/image-hygiene-policy-app.properties` | `config/quality/image-hygiene-policy*.properties` | 안 |
| `app/src/main/kotlin/bidvector/app/ManagementSurface.kt` | `app/src/main/kotlin/bidvector/app/**` | 안 |
| `app/src/test/kotlin/bidvector/app/ManagementSurfaceLockTest.kt` | `app/src/test/kotlin/bidvector/app/**` | 안 |
| `app/src/test/kotlin/bidvector/app/management/ManagementHealthSurfaceTest.kt` | `app/src/test/kotlin/bidvector/app/**` | 안 |
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
