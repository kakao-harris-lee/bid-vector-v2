# M6/6A-2a — 앱 이미지 + health: Kotlin 앱을 컨테이너로 띄우고 준비 상태를 인증 경로 밖에서 알린다 (2026-09-26)

6C 가 D-6C-1 로 6A 에 넘긴 자리다. 넘긴 이유는 「`app` 에 `main()` 이 없어 실행할 것이 없다」였고, 6A-1(`main()`·`bootJar`)과
6F-8(배포물 부팅 결함 수정·`BootJarRuntimeClasspathTest`) 뒤로 그 전제가 섰다. 앱은 지금 `java -jar` 로만 뜬다. 이미지·health·compose
서비스·CI 기동 확인이 모두 없다.

- base: `git merge-base HEAD origin/main`(고정 SHA 아님). 착수 실측 `4dc17214`(PR #46 6F-9 머지).
- worktree `bid-vector-v2-m6-6a2a`, 브랜치 `m6-6a2a/2026-09-26`.

## 운영자 지시·결정 (2026-09-26)

- 사용자: 「원래의 권장 계획 대로 진행」 — 팀장 권장안을 채택했다. 6A-2 를 **둘로 가른다**: **6A-2a 앱 이미지 + health**(이 slice)를 먼저 하고,
  **6A-2b 세션 편집 endpoint** 는 그 뒤다. endpoint 는 인증·쓰기 경로라 되돌리기 어려운 경로에 가까우므로 따로 판정받는다.
- 브리핑에서 올린 결정 넷은 사용자가 권장안을 위임했다. 팀장이 아래 D-6A2a-2·4·6·7 로 정한다.
- **Codex 없음**(팀장 판단). 이 slice 는 인증 필터·자격증명 경로를 **편집하지 않는다**(D-6A2a-4 가 health 를 별도 포트로 내서
  API 인증 체인을 건드리지 않는다). 마이그레이션도 없다. `verifier` + `code-reviewer`(sonnet) + `privacy-gate`(이미지·compose 의
  비밀값 취급, 전용 정의가 없어 범용 에이전트가 대행)로 닫는다.

## 착수 실측 (`4dc17214`)

| 자산 | 상태 |
|---|---|
| 진입점 | `BidVectorApplication.main()` · `bootJar`(`app.jar`) 켜짐 · 평범한 `jar`(`app-plain.jar`)도 켜짐(D-6A1-28, `jarContentGate` 가 보는 쪽) |
| 설정 | 전부 환경변수이고 기본값이 없다 — `BIDVECTOR_PERSISTENCE_*` · `OPERATOR_CREDENTIAL_VALUE` · 수집(`BIDVECTOR_COLLECTION_MODE=once` 일 때만 켜짐) · `BIDVECTOR_KONEPS_SERVICEKEY` |
| 필터 | `RequestAuditFilter`(HIGHEST) → `OperatorCredentialFilter`(HIGHEST+1), 둘 다 `/*` · 디스패치 전제 `PRODUCTION_DISPATCH_PROPERTIES`(D-6A1-21) |
| health | **없다** — actuator 좌표 0, health·readiness 경로 0 |
| 이미지 선례 | `docker/ml-serving.Dockerfile`: multi-stage, 다이제스트 고정, non-root |
| 위생 게이트 | `tools/image-hygiene-check.sh` + `config/quality/image-hygiene-policy.properties` — 정책 파일이 **하나**이고 금지 패키지 판정이 Python `import` 라 ml-serving 전용이다 |
| compose | `docker/compose.yaml`: ml-serving + postgres. 자격 값은 `${VAR:?}` 뿐, `no-new-privileges` |
| CI `container` job | S-21 빌드 → S-22 위생 → S-23 compose healthy → S-24 실서버 통합 test → S-25 정리. 대상은 ml-serving 하나 |
| ML | `EvaluationWiring` 이 `UnavailableMlAnalysis`(자리지킴)를 고정한다 — 앱은 ml-serving 을 부르지 않는다 |

## 재사용 조사 (Phase 2 — 새로 만들기 전에)

| 후보 | 판정 | 근거 |
|---|---|---|
| **Spring Boot Actuator** health(liveness·readiness 그룹, `management.endpoint.health.probes.enabled`) | **채택** | 이미 쓰는 Boot 4.1.1 BOM 안의 표준 모듈이다. 프로브 의미(liveness = 프로세스 살아 있음, readiness = 트래픽 받아도 됨)와 `ApplicationAvailability` 상태 전이를 직접 짜지 않는다 |
| Boot layered jar + `java -Djarmode=tools extract` | **채택** | Boot 표준 Dockerfile 형태다. 의존 layer 와 앱 layer 를 갈라 캐시가 산다. 추가 도구가 없다 |
| `bootBuildImage`(Cloud Native Buildpacks) | 기각 | 베이스·builder 가 paketo 가 고르는 이미지라 **다이제스트 고정·위생 게이트 정책**(D-6C-7 선례)과 맞추기 어렵다. 빌드가 Gradle 안에서 Docker 데몬을 부른다 |
| Jib | 기각 | 새 빌드 플러그인 좌표다. 위생 게이트가 Dockerfile 이미지 전제로 서 있고 이득(데몬 없는 빌드)이 이 저장소 CI 에 필요 없다 |
| 직접 짠 health controller | 기각 | 바퀴 재발명이다. 게다가 API 포트에 두면 인증 체인 예외가 필요해진다(D-6A2a-4) |

## 결정

| ID | 결정 | 근거 |
|---|---|---|
| **D-6A2a-1** | 6A-2 를 2a(이미지·health·compose·CI)와 2b(세션 편집 endpoint)로 가른다. 2a 는 **HTTP API 표면을 바꾸지 않는다**(OpenAPI 무변경) | 운영자 채택 권장안. 2b 가 받을 OPEN(아래 표)은 이 slice 가 건드리지 않는다 |
| **D-6A2a-2** | **빌드 = 밖에서 만든 `bootJar` 를 이미지에 넣는다.** Dockerfile 은 `app/build/libs/app.jar` 를 builder stage 에서 `jarmode=tools extract` 로 layer 로 풀고, runtime stage 는 JRE + 풀린 layer 만 갖는다. 이미지 안에서 Gradle 을 돌리지 않는다 | ① 이 호스트는 무거운 빌드를 하나만 돌린다(전역 규칙) — 이미지 안 Gradle 은 캐시 없는 전체 빌드를 한 번 더 한다 ② CI `container` job 은 이미 `setup-gradle` 을 갖췄다 ③ 재현성은 jar 를 만드는 Gradle toolchain 고정(`bidvector.jvmToolchain=21`)과 베이스 다이제스트 고정이 진다. **대가**: `docker build` 만으로는 이미지가 안 나온다 — 빌드 스크립트가 `bootJar` → `docker build` 순서를 잇고, jar 가 없으면 Dockerfile 이 분명한 메시지로 실패해야 한다 |
| **D-6A2a-3** | 베이스는 **JRE 21**(JDK 아님), 다이제스트 고정. non-root(uid ≥ 정책 하한), `no-new-privileges`. 셸·패키지 관리자 제거는 강제하지 않는다(distroless 채택은 `OPEN-6A2A-DISTROLESS`) | 위생 게이트 (1)(2)와 같은 기준. JDK 를 빼면 `javac`·`jshell` 등 컴파일·실행 도구가 사라진다. distroless 는 헬스체크 명령(아래)과 디버깅 경로를 함께 바꾸므로 따로 판단한다 |
| **D-6A2a-4** | **health 는 Actuator 를 별도 관리 포트에 낸다.** `management.server.port` 를 API 포트와 다르게 두고, 노출은 `health` **하나**만(`management.endpoints.web.exposure.include=health`), 세부는 `show-details=never`·`show-components=never`, 프로브 그룹 liveness·readiness 를 켠다. readiness 그룹에 **DB 연결**을 넣는다. **API 포트의 인증·audit 필터 체인은 편집하지 않는다** | 같은 포트에 두면 `OperatorCredentialFilter` 에 경로 예외가 필요하다 — 경로 예외는 정규화(`..`·`;`·인코딩·후행 `/`) 우회의 문이다(6A-1 D-6A1-21 이 디스패치 한 가지로 audit·인증 우회를 실측한 계열). 별도 포트는 **구성으로** 닫는다: API 포트에는 actuator 가 없고, 관리 포트에는 health 말고 아무것도 없다. 관리 포트가 받는 요청은 audit 대상이 아니다(프로브 트래픽) — 이것이 경계다(아래 위협 모델) |
| **D-6A2a-5** | compose 에 `app` 서비스를 더한다. `depends_on: postgres(service_healthy)`, 환경변수는 전부 `${VAR:?}`(기본값 없음), **수집 변수는 넣지 않는다**(수집 꺼짐 고정), 관리 포트만 healthcheck 에 쓰고 host 에 publish 하지 않는다. API 포트는 로컬 루프백(`127.0.0.1:<port>`)에만 publish 한다 | D-6C-7 과 같은 비밀값 규율. 수집·실 외부 호출은 사용자 승인 대상이라 compose 기동이 그것을 열면 안 된다 |
| **D-6A2a-6** | **ML 은 자리지킴 그대로.** app 서비스는 ml-serving 에 의존하지 않는다(`depends_on` 없음, ML 주소 환경변수 없음). `OPEN-ML-ANALYSIS-WIRING` 은 건드리지 않는다 | 실 ML 배선은 운영자 결정 대기(4B-6b → 6A 인계). 의존만 먼저 걸면 쓰지 않는 서비스가 기동 조건이 된다 |
| **D-6A2a-7** | 위생 게이트는 **이미지별 정책 파일**로 일반화한다(`image-hygiene-policy.properties` → ml-serving 전용 이름 유지 + 앱 전용 정책 파일 추가, 스크립트는 정책 파일을 인자로 받는다). 앱 이미지의 「금지」 판정은 Python `import` 가 아니라 **JVM 구조**로 건다: ① 컴파일러 부재(`javac` 실행 불가) ② 풀린 의존 layer 에 **test 전용 좌표 0**(`junit`·`testcontainers`·`archunit`·`kotest`·`mockk` 등 — 목록은 정책 파일, 판정은 이미지 안 파일 목록) ③ 크기 상한 ④ 베이스 layer 체인 = 정책 다이제스트 ⑤ 실 ENTRYPOINT 프로세스 사용자 ≥ 하한. 셸 게이트의 구조적 이식(`OPEN-6C-POLICY-GATE-STRUCTURAL`)은 **이 slice 에서 하지 않는다** | 게이트 술어는 이미지 산출물에 건다(6C 규율 그대로). 구조적 이식의 착수 조건은 D-6C-11 이 「배포 경로(레지스트리·배포)가 생길 때」로 정했고 이 slice 는 레지스트리·배포를 만들지 않는다 — 조건 미충족을 사실로 기록한다 |
| **D-6A2a-8** | CI `container` job 을 넓힌다: `bootJar` → 앱 이미지 빌드 → 두 이미지 위생 → compose 세 서비스 healthy 수렴 → **스모크**: 관리 포트 liveness·readiness 200 · API 포트 무인증 401 · 인증 `GET /api/strategy` 200 · API 포트의 `/actuator/**` 가 401 또는 404(actuator 가 API 포트에 없다) · 관리 포트의 health 외 경로 404 → 정리. 자격 값은 job 안에서 생성하고 파일에 남기지 않는다(D-6C-9 선례) | 「이미지가 뜬다」가 아니라 「뜬 이미지가 인증 경계를 지키며 준비 상태를 알린다」를 재야 한다. 스모크가 평가 dry-run 을 부르지 않는 이유: 전략 행이 비어 있는 새 DB 라 의미 있는 값이 없고, audit 쓰기만 늘린다 |
| **D-6A2a-9** | 로컬 한 명령: `tools/one-command-check.sh` 는 **바꾸지 않는다**(Docker 없는 환경의 상시 붉음을 피한다 — D-6C-4 와 같은 근거). 컨테이너 축의 로컬 재현 명령은 `commands.md` 에 적고, CI `container` job 이 정본이다 | 부분 게이트 금지 규율과 충돌하지 않는다 — 줄일 수 있는 단위는 job 이고, 이 slice 의 acceptance 는 `check` job 과 `container` job 둘 다다 |

## 계약 갱신 r1 (2026-09-26, 팀장 — verifier F-1·F-2 · code-reviewer HIGH · privacy-gate M-1 수령)

세 레인이 같은 결함을 따로 실측했다. 잠금(`MANAGEMENT_SURFACE_LOCK`)이 **이름으로 고정한 키**는 우선순위상 빈틈이 없다. 그러나 `management.*` 에는
같은 출력에 닿는 **다른 키**가 있다. 그룹별 `show-details`·`show-components`·`include`/`exclude`, 새 그룹, `status.http-mapping`,
`probes.add-additional-paths`, `validate-group-membership` 이 그 예다. 환경변수 한두 줄이 이 키들로 우회 2·3·7 을 다시 연다. 잠금 test 는 잠금 자기 목록만 돌아 이것을 볼 수 없었다.
**열거 결함**이다 — 키를 더하면 다음 Boot 판에서 같은 결함이 돌아온다.

| ID | 결정 |
|---|---|
| **D-6A2a-10** | **관리 표면은 접두사 거부로 닫는다(구성).** 기동 시 잠금 밖의 어느 속성 소스에든 `management.` 접두사 키가 있으면 기동을 거부한다. 허용 목록은 **`management.server.port` 하나**다(D-6A2a-4 의 유일한 자유). 새 Boot 키도 접두사에 걸리므로 목록을 늘리지 않아도 닫힌다. 거부 메시지에는 키 이름만 싣고 값은 싣지 않는다. 관련 접두사(`spring.jmx.*` 등)를 같은 방식으로 볼지는 구현 레인이 실측해 정한다 — 잠금이 고정한 키가 걸린 접두사는 전부 대상이다. **게이트 술어 변경이므로 verifier 표적 재검증.** |
| **D-6A2a-11** | 잠금 test 는 **잠금 밖에서 부팅**해 잰다. 적대적 명령행 인자·환경변수로 production 조립을 부팅해 기동 거부를 단언하고(그룹 세부 · 새 그룹 · 상태 매핑 · 추가 경로 · readiness exclude), 잠금 **배선 형태**(우선순위 최상위)도 잠근다. 잠금 키 집합은 리터럴로 단언한다(자기 목록 순회 금지). |
| **D-6A2a-12** | (2b) 표의 관리 포트 행 정정: 프로브 두 경로(`/liveness`·`/readiness`)는 상태 한 단어다. **집계 `/actuator/health` 는 상태 + 우리 그룹 이름(`groups`)** 을 낸다(Boot 4.1.1 형태, 값은 고정 그룹 이름뿐 — 구성 요소·주소·예외·버전 없음). 형태를 실측으로 고정하고, 키가 하나라도 늘면 RED. 스모크도 집계 경로를 잰다. |
| **D-6A2a-13** | 이 라운드에서 함께 처분할 것은 셋이다. ① `PersistenceProperties` 가 `data class` 라 바인딩 실패 분석기·로그가 자격 값을 문자열화할 수 있다(privacy-gate L-2) → `OperatorCredentialProperties`(6A-1 r4)와 같은 형태로 고친다(in_scope `app/**`). ② 관리 포트 `/error` 200·비 GET 500 은 스모크·test 로 형태를 잰다 — 500 은 `OPEN-API-WRONG-METHOD-500` 에 관리 포트 관측으로 덧붙인다. ③ 이름 기반 보조 판정(이름 바꾼 test jar · PATH 밖 `javac`)은 알려진 제한으로 둔다(계약 (1) 이 이미 보조로 선언했다, verifier F-6). |

## 위협 모델 — 6A-2a 고유 경계 (Phase 2.5 (0), 팀장)

**지키는 것**: ① 이미지에 비밀값이 들어가지 않는다(레이어·환경변수 기본값·빌드 인자 어디에도 — 값은 기동 시 환경에서만 온다)
② 이미지에 빌드·test 도구와 test 전용 의존이 없다 ③ 컨테이너가 root 로 돌지 않고 권한 상승이 구성상 막힌다 ④ health 가 **API 인증
경계를 열지 않는다** — API 포트에 인증 없이 닿는 경로가 새로 생기지 않는다 ⑤ health 응답이 내부 정보(구성 요소 이름·DB 주소·예외
메시지·버전)를 내지 않는다 ⑥ compose 기동이 수집·실 외부 호출을 켜지 않는다.

**지키지 않는 것(경계 밖)**: 레지스트리 push·서명·SBOM·CVE 차단(`OPEN-6C-IMAGE-VULN-SCAN`, 6E) · 오케스트레이터(k8s 등) 배치 구성 ·
관리 포트의 네트워크 노출 통제(**배치 환경이 진다** — 이 slice 는 compose 에서 host publish 를 하지 않는 것까지) · 멀티아키
(`OPEN-6C-MULTIARCH`) · 빌드 스크립트를 임의로 고치는 저자(6C·1A 와 같은 경계).

**요구 축소가 아닌 근거**: milestone-6 6C 절 요구는 「multi-stage build · non-root · health/readiness 분리 · 비밀값은 환경변수·비밀 저장소로만 ·
version 고정과 SBOM/취약점 검사」다(문면을 옮겨 적었다 — 원문 어휘는 스캔 어휘와 겹쳐 쓰지 않는다). SBOM·vuln 은 D-6C-5 가 이미 6E 로 보냈고, 나머지 넷은 이 slice 가 전부 받는다.

### (1) 열거인가 구성인가

- health 격리: **구성**(포트 분리 — API 포트에 actuator 가 없다). 경로 예외 목록(열거)을 쓰지 않는다.
- 비밀값 부재: 이미지 산출물 검사(환경변수 기본값·레이어 파일에서 설정 키 이름에 값이 붙은 것 0) + Dockerfile 에 `ARG`/`ENV` 로 값 자리 0.
  **보조 잠금**이다 — 문자열 검사라 스타일로 열린다. 주 잠금은 「앱이 기본값 없이 환경변수만 읽는다」(6A-1·6F-8 의 기존 구조).
- test 의존 부재: 이미지 안 파일 목록 대 정책 좌표 목록 — **열거**다. 보강: `bootJar` 는 `runtimeClasspath` 만 담는다는 Gradle 구조가 주 잠금이고
  (test 좌표가 들어가려면 `implementation` 선언을 바꿔야 한다), 이미지 검사는 그것이 이미지까지 이어졌는지 재는 보조다.

### (2) 우회 — 여섯

1. API 포트에서 `/actuator/health` 를 인증 없이 부른다. ← actuator 가 API 포트에 없다(스모크: 401 또는 404, 200 이면 실패).
2. 관리 포트에서 `env`·`configprops`·`heapdump` 같은 다른 actuator endpoint 로 비밀값을 읽는다. ← 노출 = `health` 하나(스모크: 관리 포트의 health 외 경로 404) · `OperatorCredentialProperties` 는 `data class` 가 아니다(6A-1 r4).
3. health 세부(`show-details`)로 DB 주소·예외 메시지·구성 요소 이름이 샌다. ← `never` 고정 + 스모크가 응답 본문이 `{"status":...}` 한 키임을 단언.
4. 빌드 인자·`ENV` 기본값으로 자격 값이 레이어에 박힌다. ← Dockerfile 에 값 자리 0 · 이미지 `Config.Env` 검사 · compose 는 `${VAR:?}` 만.
5. 다른 사용자(root)로 기동하거나 권한을 올린다. ← 실 ENTRYPOINT 프로세스 사용자 검사 + `no-new-privileges`(compose·위생 게이트 둘 다).
6. compose 로 띄우면 수집이 켜져 실 KONEPS 를 부른다. ← compose 에 수집 변수 부재 + 앱 기본값이 꺼짐(`@ConditionalOnProperty havingValue=once`). 스모크 로그에 `collection start` 0.
7. (보조) readiness 가 DB 없이도 UP 이라 트래픽을 받는다. ← readiness 그룹에 DB 포함 · test: DB 가 없을 때 readiness DOWN.

### (2b) 값 획득 축 — 새 public 표면 전수

| 표면 | 밖에 허락하는 것 | 판정 |
|---|---|---|
| 관리 포트 `GET /actuator/health/liveness`·`/readiness`·`/actuator/health` | 상태 한 단어(UP/DOWN/OUT_OF_SERVICE) | **경계로 처리** — 인증 없이 누구나 읽는다. 그래서 세부 0 을 **실행으로** 잰다(스모크 본문 단언 + app test). 네트워크 노출 통제는 배치 환경(경계 밖) |
| 관리 포트의 그 밖의 경로 | 없어야 한다 | 닫는다 — 노출 설정 + 스모크 404 |
| 앱 이미지 자체(`docker run` 가능한 산출물) | 이미지를 가진 주체가 레이어를 읽는다 | 경계로 처리 — 레이어에 비밀값 0 을 이미지 검사로 잰다 |
| compose `app` 서비스 | 로컬 실행자가 API 포트(루프백)에 닿는다 | 경계로 처리 — 인증은 기존 필터가 진다. 이 slice 는 필터를 편집하지 않는다 — 편집 0 을 diff 로 잰다 |
| 새 설정 키(`management.*`) | 운영자가 관리 포트·노출을 바꿀 수 있다 | 경계로 처리 — 값은 코드(조립 근)가 고정하고 환경변수로 **넓히지 못해야** 한다. 넓힐 수 있으면 우회 2 가 다시 열린다 → 구현 레인은 `management.endpoints.web.exposure.include` 를 환경으로 덮을 수 있는지 **실측**하고, 덮을 수 있으면 막는 형태(프로그램적 고정 + test)를 낸다 |
| `object`/companion 주입 자리 | — | 새로 만들지 않는다(조립 근의 `@Bean` 은 기존 형태) |

### (3) 과잉·미달

- 과잉 아님: distroless · 레지스트리 · SBOM · k8s 매니페스트를 만들지 않는다(각자 OPEN 또는 6E).
- 미달 주의: 「이미지가 빌드된다」만 재면 미달이다 — D-6A2a-8 스모크가 인증 경계와 health 격리를 **뜬 컨테이너에서** 잰다.
- `OPEN-6A1-SCAN-FILTER-SIDE-EFFECT`(명시 `@ComponentScan` 이 Boot 기본 exclude 필터 둘을 가린다): actuator 자동 구성을 들이면 영향이
  생길 수 있다. **이 slice 가 받는다** — 구현 레인이 actuator 도입 뒤 거동을 실측하고, 영향이 있으면 필터 둘을 명시로 되살린다. 영향이 없으면
  실측 결과만 적고 OPEN 은 6A-2b 로 넘긴다.

## in_scope

```yaml
in_scope:
  - docker/app.Dockerfile                                                   # 신규
  - docker/app.Dockerfile.dockerignore                                      # 신규
  - docker/compose.yaml                                                     # 공유 — app 서비스 추가만
  - tools/image-hygiene-check.sh                                            # 정책 파일 인자화 + JVM 판정 갈래
  - config/quality/image-hygiene-policy*.properties                         # 앱 정책 파일 신규(ml-serving 정책은 이름·값 유지)
  - .github/workflows/ci.yml                                                # container job 확장만
  - app/build.gradle.kts                                                    # actuator 좌표 · layered jar
  - gradle/libs.versions.toml                                               # actuator 좌표(버전은 BOM)
  - app/src/main/kotlin/bidvector/app/**                                    # 관리 포트·노출 고정(조립 근). http/ 필터 두 파일은 편집 금지(아래)
  - app/src/test/kotlin/bidvector/app/**
  - config/quality/*.properties                                             # 공유 — 게이트 등재·의존 허용 목록 추가만
  - build-logic/**                                                          # compatibilitySmoke 등 좌표 목록이 요구하면 추가만
  - reports/evidence/m6/6a2a/**
  - milestone-6.md                                                          # 착수·종결 문단(팀장) — 공유
out_of_scope:
  - app/src/main/kotlin/bidvector/app/http/OperatorCredentialFilter.kt      # 인증 체인 편집 금지(D-6A2a-4)
  - app/src/main/kotlin/bidvector/app/http/RequestAuditFilter.kt            # 같음
  - openapi/**                                                              # API 표면 무변경(D-6A2a-1)
  - 세션 편집 endpoint · 전략 쓰기(6A-2b)
  - ml-serving 이미지·서버 코드 · 실 ML 배선(`OPEN-ML-ANALYSIS-WIRING`)
  - 레지스트리·서명·SBOM·CVE(6E) · distroless · 멀티아키 · k8s 매니페스트
  - tools/one-command-check.sh(D-6A2a-9)
  - db/migration/**
```

## acceptance

- **`check` job 명령 그대로**(버릴 worktree, `--rerun-tasks` 한 번): Kotlin `check` · `qualityBaseline` · `one-command-check.sh`.
- **`container` job 명령 그대로**를 로컬에서 한 번 돌린다(Docker 있음). 기준은 CI job 이 정본이다 — PR CI 의 `container` 초록이 필요조건이다.
- test(RED 우선):
  - 관리 포트 분리 — API 포트 `/actuator/health` 가 200 이 아님.
  - 노출 = health 하나.
  - 세부 0(본문 키 하나).
  - readiness 가 DB 없이 DOWN.
  - 노출 설정을 환경으로 넓힐 수 없음(또는 넓혀지는 사실을 실측하고 막는 형태).
  - 인증 필터 두 파일 diff 0.
- 변이(구현 레인):
  - ① 관리 포트를 API 포트와 같게.
  - ② 노출에 `env` 추가.
  - ③ `show-details=always`.
  - ④ readiness 에서 DB 제거.
  - ⑤ Dockerfile `USER` 제거.
  - ⑥ 정책 test 좌표 목록에서 하나를 이미지에 실제로 넣기(위생 게이트 RED).
  - ⑦ JDK 베이스로 교체(컴파일러 부재 판정 RED).
  
  ①~④ 는 test 또는 스모크, ⑤~⑦ 은 위생 게이트가 RED 를 내야 한다.

## rollback

- 코드: in_scope 경로 한정 `git restore --source=<base> --staged --worktree`. 목록은 기계 산출한다.
- 공유 파일은 커밋 해시 hunk 격리로 되돌린다: `compose.yaml` · `ci.yml` · `config/quality/*` · `libs.versions.toml` · `build-logic/**` · `milestone-6.md`.
- 임시 worktree ①~⑥ 과 `실측 HEAD` 를 적는다.
- 비활성화(코드 되돌림 없이): compose `app` 서비스를 띄우지 않으면 기존 두 서비스는 그대로다. 앱을 `java -jar` 로 띄우는 기존 경로는 관리 포트가 하나 더 열리는 것 말고는 바뀌지 않는다.
- DB 변경 없음.

## 하네스 레인 변경 (상시 절)

착수 시점 없음.

## OPEN — 수령·신설

| ID | 방향 | 내용 |
|---|---|---|
| D-6C-1 인계(앱 이미지·entrypoint) | **수령·닫음** | 이 slice |
| `OPEN-6A1-SCAN-FILTER-SIDE-EFFECT` | **수령**(조건부) | actuator 도입 뒤 실측 — 영향 있으면 닫고, 없으면 실측을 적고 6A-2b 로 |
| `OPEN-6C-POLICY-GATE-STRUCTURAL` | 재평가 · 유지 | D-6C-11 착수 조건(배포 경로) 미충족 — 이 slice 는 레지스트리·배포를 만들지 않는다 |
| `OPEN-6A2A-DISTROLESS` | **신설** | 셸·패키지 관리자 없는 런타임 베이스. 헬스체크 명령·디버깅 경로를 함께 바꾸므로 따로 판단 |
| `OPEN-6A2A-MGMT-PORT-EXPOSURE` | **신설** | 관리 포트 네트워크 노출 통제는 배치 환경이 진다(6E 운영 runbook) |
| 6A-2b 로 넘김(무변경) | — | `OPEN-6F9-STRATEGY-WRITE-ENDPOINT` · `OPEN-6A3-MAX-ACTIVE-BIDS-EDIT` · `OPEN-6A3-APP-HTTP-DEPENDENCY-ALLOWLIST` · `OPEN-6A1-CREDENTIAL-RAW-REINTRODUCTION` · `OPEN-API-WRONG-METHOD-500` |

## 리뷰 레인

- `verifier`(opus) · `code-reviewer`(**`model: sonnet` 명시**) · `privacy-gate`(이미지·compose·CI 의 비밀값 취급 — 전용 정의가 없어 범용 에이전트 대행).
- contract-keeper 는 해당 없음 — 공개 HTTP 계약이 바뀌지 않는다. migration-reviewer 도 해당 없음 — 마이그레이션이 없다.
- **Codex 없음.** 종결은 `verifier ready-for-review` + PR CI(`check`·`container`·`ml-engine`) 초록 + 사용자 승인이다.
