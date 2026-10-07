# M6/6E 운영 runbook — 기동·설정 · 관리 표면 · incident · model rollback · dry-run/probe (초안 2026-10-07)

이 문서는 **오늘 이 저장소로 운영자가 할 수 있는 것과 할 수 없는 것**을 적는다. 없는 경로를 약속하지
않는다 — 절차의 모든 명령은 저장소에 실재하는 진입점이고, 할 수 없는 것은 「할 수 없다」로 적는다.

**선행 문서 둘을 대신하지 않는다** — 백업·복원·마이그레이션 되돌림은 `docs/runbook/m6-6b2-backup-restore.md`,
실 KONEPS 표본 수집은 `docs/runbook/m6-6g-real-collection.md` 가 정본이다. 이 문서는 그 둘이 다루지 않는
축(기동 설정 전수 · 관리 표면 · incident 처방 · model rollback · dry-run)만 쓴다.

**이 문서가 약속하지 않는 것**(6E-2 또는 결정 대기): metric/SLO 임계값 · 구조화 로그·trace·대시보드 ·
커넥션 풀 · SBOM/CVE 스캔 · 판정·투찰 기록 표 · 실 알림 발송 · RBAC·TLS·네트워크 배치.

---

## 1. 기동과 설정

### 1.1 설정이 사는 자리 — 설정 파일이 없다

이 앱에는 `application.yml`·`application.properties` 가 **없다**(저장소 전체 0개). Spring 설정은
**조립 근이 프로그램적으로 못박는다** — `app/src/main/kotlin/bidvector/app/BidVectorApplication.kt` 의
`PRODUCTION_DISPATCH_PROPERTIES`·`PRODUCTION_MANAGEMENT_PROPERTIES` 와
`app/src/main/kotlin/bidvector/app/ManagementSurface.kt` 의 `MANAGEMENT_SURFACE_LOCK` 이다.
배포가 주는 값은 **환경변수 또는 명령행 인자**뿐이고, 그 가운데 관리 표면에 닿는 것은 §2 의 두 키만
허용된다. logback·log4j2 설정 파일도 없다 — 로그 출력은 Boot 기본 패턴이다.

### 1.2 `@ConfigurationProperties` prefix 전수 — 10

| prefix | 바인딩 클래스 | 모드와 무관하게 필수인가 |
|---|---|---|
| `operator.credential` | `OperatorCredentialProperties` | **항상 필수** — 조립 근이 모드와 무관하게 바인딩한다 |
| `bidvector.persistence` | `PersistenceProperties` | **항상 필수** |
| `bidvector.evaluation` | `EvaluationProperties` | **항상 필수**(`candidate-cap`; 수집 모드에서도 부팅 요건) |
| `bidvector.evaluation.commit` | `EvaluationCommitProperties` | 평가 커밋 러너를 켤 때만 |
| `bidvector.collection` | `CollectionProperties` | 공고 목록 갈래를 켤 때만 |
| `bidvector.koneps` | `KonepsEndpointProperties` · `KonepsCredentialProperties` | 수집 갈래 둘 중 하나를 켤 때 |
| `bidvector.koneps.opening` | `KonepsOpeningEndpointProperties` | 개찰 갈래를 켤 때 |
| `bidvector.opening-collection` | `OpeningCollectionProperties` | 개찰 갈래를 켤 때만 |
| `bidvector.snapshot-extract` | `SnapshotExtractionProperties` | 스냅숏 추출을 켤 때만 |
| `bidvector.relay` | `RelayProperties` | relay 를 켤 때만 |

### 1.3 러너 스위치 — `mode=once` 다섯

러너는 **전부 기본 꺼짐**이고 `@ConditionalOnProperty(name = ["mode"], havingValue = "once")` 하나로만
켜진다. 한 프로세스에 둘 이상을 켜면 **기동하지 않는다**(`OneShotRunnerGuard` — 임의의
`ApplicationRunner` 둘이면 거부).

| 키 | 켜지는 것 |
|---|---|
| `bidvector.collection.mode=once` | 공고 목록 수집 |
| `bidvector.opening-collection.mode=once` | 개찰 수집(축 다섯) |
| `bidvector.snapshot-extract.mode=once` | 스냅숏 추출 |
| `bidvector.evaluation.mode=once` | 평가 커밋(outbox 기록) |
| `bidvector.relay.mode=once` | 알림 relay |

러너를 하나도 켜지 않으면 **HTTP 표면만 뜬다**(운영자 API). 러너를 켤 때는 6G runbook §2 의 관례대로
`SPRING_MAIN_WEB_APPLICATION_TYPE=none` 으로 HTTP 표면을 끈다.

### 1.4 설정 키 전수 (prefix 별 leaf)

자격 값(`operator.credential.value` · `bidvector.persistence.credential` ·
`bidvector.koneps.service-key`)은 **환경변수로만** 주고 명령행 인자·로그·transcript 에 남기지 않는다
(6G runbook §1-5 의 키 취급 절차와 같은 형태). 세 자격 타입의 `toString` 은 원문을 내지 않는다.

| prefix | leaf 키 |
|---|---|
| `operator.credential` | `value` |
| `bidvector.persistence` | `jdbc-url` · `username` · `credential` |
| `bidvector.evaluation` | `candidate-cap` · `mode` |
| `bidvector.evaluation.commit` | `current-active-bids` |
| `bidvector.collection` | `mode` · `from` · `to` · `categories` · `calls-per-day` · `calls-total` · `run-state-dir` · `release-sha` |
| `bidvector.koneps` | `base-url` · `service-key` · `operations.<업종>.path` · `operations.<업종>.division` |
| `bidvector.koneps.opening` | `scsbid-base-url` · `opening-complete-path` · `bid-price-formula-a-path` · `rows-per-page` · `operations.<업종>.{opening-result-list-path,reserve-price-detail-path,base-amount-path,division}` |
| `bidvector.opening-collection` | `mode` · `from` · `to` · `categories` · `sampling-seed` · `sample-size` · `calls-per-day` · `calls-total` · `run-state-dir` · `release-sha` |
| `bidvector.snapshot-extract` | `mode` · `from` · `to` · `output-dir` · `snapshot-id` · `run-state-dir` |
| `bidvector.relay` | `mode` · `environment` · `owner` · `channel` · `claim-limit` |

### 1.5 설정이 **기동을 거부하는** 조합 — 값을 지어내지 않는다

운영자가 보게 될 거부는 거의 전부 이 목록이다. 거부는 **실행 도중이 아니라 기동 시점**에 일어난다.

| 거부 | 어디서 |
|---|---|
| 러너 둘을 함께 켰다 | `OneShotRunnerGuard` |
| 수집 모드 둘(`collection`·`opening-collection`)을 함께 켰다 | `CollectionWiring` |
| 러너는 켜졌는데 KONEPS 서비스 키가 없거나 비었다 | `CollectionWiring`·`OpeningCollectionWiring` |
| KONEPS base URL 이 평문 `http` 로 **외부** 호스트를 가리킨다(loopback mock 은 허용) | 같은 자리 — 서비스 키가 쿼리로 나간다 |
| 조회 범위가 상한 초과·미래·역전 — 공고 목록 **31일**, 개찰 **120일** | 같은 자리(`SPAN_TOO_LONG`) |
| 업종이 표에 없다 · 빈 목록 · 중복 · 대분류 누락·어휘 밖 · 빈 경로 | 같은 자리 |
| 호출 상한·표본 정책(seed·층당 목표)이 없다 | `OpeningCollectionWiring` — 기본값을 지어내지 않는다 |
| 총 상한이 일 상한보다 작다 | 같은 자리 |
| 실행 상태 디렉터리가 없거나 **저장소 안**이다 | 같은 자리 |
| 스냅숏 출력 경로가 없다 | `SnapshotExtractionWiring` |
| `bidvector.evaluation.candidate-cap` 이 없다 | `EvaluationCommitWiring` |
| `bidvector.evaluation.commit.current-active-bids` 가 없거나 음수다 | 같은 자리 — 0 을 지어내지 않는다 |
| 저장된 전략에 여력 상한(`max_active_bids`)이 없다 | 같은 자리 |
| relay `environment` 가 없거나 어휘 밖이다 | `RelayWiring` — 기본값 없음 |
| relay `owner` 가 공백이거나 `claim-limit` 이 0 이다 | 같은 자리 |
| relay 환경의 정책 모드가 `Live` 다 | `RelayBootDecision` — §5.2 |
| 관리 포트가 없다 · API 포트와 같다 · 꺼져 있다 | `ManagementSurfaceLock` — §2 |
| `MANAGEMENT_*`·`SPRING_JMX_*` 등 §2 의 거부 대상 키를 환경이 정했다 | 같은 자리 |

기동 실패 출력에 `RUN_STATE_FORMAT_MISSING|MISMATCHED|LEGACY_LINE` 이 보이면 실행 상태 디렉터리가 **옛
형식**이다 — 고치지 말고 보고한다(6G runbook §2 와 같은 처분).

### 1.6 오늘 관측할 수 있는 것 — 이것이 전부다

production metric 계측은 **0**이고 구조화 로그·trace·대시보드도 없다(6E-2). 운영자가 쓸 수 있는 관측
창구는 넷뿐이다.

1. **프로세스 종료 코드** — §3 의 표.
2. **러너 로그의 마침 줄** — 계수와 열거값만 싣는다(공고명·기관명·키·예외 메시지 없음).
3. **관리 포트의 health 셋** — §2.2.
4. **DB 직접 질의** — outbox 상태 분포·수집 회계 행·감사 행. 임계 판정은 운영자가 눈으로 한다.

---

## 2. 관리 표면 — 잠금 · 거부 · 포트 노출

### 2.1 잠금이 고정하는 키 — 환경이 넓힐 수 없다

`MANAGEMENT_SURFACE_LOCK` 이 초기화자 단계에서 아래 키들을 못박는다. **노출되는 actuator endpoint 는
`health` 하나**다.

| 키 | 값 |
|---|---|
| `management.endpoints.web.base-path` | `/actuator` |
| `management.endpoints.web.exposure.include` | `health` |
| `management.endpoints.web.exposure.exclude` | (빈 문자열) |
| `management.endpoints.web.discovery.enabled` | `false` |
| `management.endpoint.health.show-details` | `never` |
| `management.endpoint.health.show-components` | `never` |
| `management.endpoint.health.probes.enabled` | `true` |
| `management.endpoint.health.group.liveness.include` | `livenessState` |
| `management.endpoint.health.group.readiness.include` | `readinessState,db` |
| `spring.jmx.enabled` | `false` |

위 표는 10 행이고 `MANAGEMENT_SURFACE_LOCK` 의 키 수도 **10** 이다(§7 의 등식 명령이 그 수를 센다).

### 2.2 배치가 정할 수 있는 키는 **둘**뿐

`MANAGEMENT_SURFACE_DEPLOYMENT_KEYS` = `management.server.port` · `management.server.address`.
기본 관리 포트는 `8081`(`PRODUCTION_MANAGEMENT_PROPERTIES`)이고 환경이 덮을 수 있다.
`management.server.address` 는 표면을 **좁히기만** 한다.

프로브 셋(관리 포트에서만 응답):

| 경로 | 기대 |
|---|---|
| `/actuator/health/liveness` | 200 · 본문 키 `status` 하나 · `UP` |
| `/actuator/health/readiness` | 200 · 본문 키 `status` 하나 · `UP` |
| `/actuator/health` | 200 · 본문 키 `groups,status` · 그룹 이름 `liveness,readiness` |
| `/actuator/health/db` 를 포함한 그 밖의 `/actuator/**` | **404** |
| `/error` | 200 · 본문 키 `error,status,timestamp` 셋 고정 |
| 비 GET 요청 | 405 |

**DB 가 사라지면 `readiness` 는 DOWN 이고 `liveness` 는 UP 을 유지한다** — 재기동이 답이 아니라는 신호다.

### 2.3 거부 대상 이름공간 **넷** — 키 열거가 아니다

`MANAGEMENT_SURFACE_GOVERNED_PREFIXES` = `management` · `spring.jmx` ·
`server.servlet.context-parameters` · `spring.web.error`. 이 접두사 아래의 키를 §2.2 의 두 키를 빼고
환경·명령행·`SPRING_APPLICATION_JSON`·평탄화 JSON·대괄호 색인 **어느 형태로든** 주면 기동이 거부된다.

**k8s 를 쓸 때 주의** — `management` 라는 이름의 Service 를 같은 이름공간에 두면 kubelet 이 주입하는
service link 환경변수(`MANAGEMENT_SERVICE_HOST` 계열)가 **거부 대상에 걸려 Pod 이 뜨지 않는다**. Service
이름을 바꾸거나 `enableServiceLinks: false` 로 둔다.

거부는 두 자리에서 돈다 — 초기화자(빠른 실패)와 `ContextRefreshedEvent` 뒤의 재검사(주 잠금). 늦게
실체가 채워지는 property source 도 **readiness 가 수락을 알리기 전에** 걸린다.

**거부 문면은 값을 싣지 않는다** — 키 정규형과 소스 **표지**(상수 이름이거나 `other`)만 나온다. 「무슨
값을 줬길래」는 문면에 없다. 운영자는 키 이름으로 찾는다.

### 2.4 관리 포트의 네트워크 노출은 **배치가 진다** (`OPEN-6A2A-MGMT-PORT-EXPOSURE`)

앱이 할 수 있는 것은 `management.server.address` 로 바인딩 주소를 좁히는 것까지다. 그 포트를
방화벽·NetworkPolicy·Service 로 **가두는 것은 배치 환경의 몫**이고 앱이 강제할 수단이 없다. 운영 반입
체크리스트에 그 항목을 둔다.

컨테이너 환경의 실측 형태(CI `container` job 과 같다): API 포트는 **루프백에만** publish 하고 관리
포트는 **publish 하지 않는다**. 관리 포트 검사는 컨테이너 **안에서** 묻는다.

### 2.5 audit fail-closed 의 가용성 대가 (D-6A1-17)

운영자 API 의 모든 요청은 **감사 행 insert 가 성공해야** 실제 응답을 내보낸다. 그래서
**감사 저장소 장애는 읽기 endpoint 의 가용성을 죽인다** — 고정 500 `ErrorBody`(+correlation id)만 나간다.
이것은 결함이 아니라 선택된 교환이다(증적 없는 응답을 내지 않는다).

| 증상 | 읽는 법 | 처방 |
|---|---|---|
| 모든 API 요청이 500 인데 `readiness` 는 UP | DB 는 살아 있고 감사 쓰기만 실패 | 감사 표의 쓰기 권한·공간·제약을 본다. 앱 재기동은 답이 아니다 |
| `readiness` 가 DOWN | DB 연결 자체가 끊김 | DB 를 먼저 살린다 |

### 2.6 커넥션 풀이 없다 (`OPEN-6A1-CONNECTION-POOL`)

`PGSimpleDataSource`(**비풀링**)로 배선돼 있다. 단일 운영자·저동시성에서는 오늘 문제가 없으나
**이 상태로 운영에 나가지 않는다** — 풀 도입은 6E-2 이고, `milestone-6.md` 가 「여기서 닫혀야 운영
반입이 가능하다」로 지목한 항목이다. 그때까지의 운영 가정: 동시 요청은 1, 배치 러너는 한 프로세스에
하나(§1.3).

---

## 3. incident 절차 — 종료 코드로 읽는다

### 3.1 먼저 보는 것

1. **종료 코드**(§3.2~§3.4). 0 이 아니면 그 값이 처방을 정한다.
2. **마침 줄** — 러너는 끝에 계수를 실은 한 줄을 남긴다. 그 줄의 계수가 무엇이 일어났는지 말한다.
3. **사유 토큰**(§3.5). 실패 줄에는 예외 메시지·SQL 상세·접속 문자열이 **없다** — 클래스 이름과
   SQLSTATE 5자리까지다.
4. **DB 질의** — outbox 상태 분포, 수집 회계 행.

### 3.2 알림 relay — `RelayExitCode` 다섯

| 코드 | 이름 | 뜻 | 처방 |
|---|---|---|---|
| 0 | `COMPLETE` | 집은 행 전부가 종단에 닿았다(일부 전달 뒤 개별 거부·격리는 정상 처분이라 0 이다) | 없음 |
| 1 | `FAILED` | 실행 자체가 실패했다. **임대를 도중에 잃은 run 도 이 값**이다 | 사유 토큰을 읽는다. 임대 상실이면 배타성이 깨진 것이니 **같은 owner 로 두 프로세스가 도는지** 본다 |
| 2 | `INCOMPLETE` | 셋 중 하나 — (a) 사유 불명으로 격리된 행이 있다(미지 payload) (b) **고아를 하나라도 태웠다** (c) 발송이 한 번도 성공하지 않았고 종단 실패가 있다 | (b) 가 가장 센 신호다 — §3.6 |
| 3 | `LEASE_BUSY` | 임대를 못 쥐었다. claim 0·격리 0 | **기다리면 풀린다.** cron 이면 다음 tick 에 재시도. 고치지 않는다 |
| 4 | `ENV_SUPPRESSED` | 정책표가 이 환경에 붙인 모드가 `Live` 가 아니라 억제됐다. claim 0, 행은 `PENDING` 보존 | **설정을 고쳐야 풀린다** — §5.1 의 사상표를 본다. 기다려도 바뀌지 않는다 |

`LEASE_BUSY`(3)와 `ENV_SUPPRESSED`(4)를 **한 값으로 접지 않는다** — 하나는 기다리면 풀리고 하나는
영영 풀리지 않는다.

### 3.3 수집(공고 목록·개찰·스냅숏 추출) — `CollectionExitCode` 다섯

| 코드 | 이름 | 뜻 | 처방 |
|---|---|---|---|
| 0 | `COMPLETE` | 전 슬롯이 끝까지 읽혔다 | 없음 |
| 1 | `FAILED` | 실행이 실패했다 | 사유 토큰을 읽는다 |
| 2 | `INCOMPLETE` | 상한·쿼터·일시 실패로 멈췄다 — **다음 실행이 이어 돈다**(멱등, 같은 실행 상태 디렉터리) | 일 상한이면 **KST 자정** 뒤 재실행. 쿼터(`resultCode 22`·HTTP 429)도 다음 날 |
| 3 | `ALREADY_RUNNING` | 같은 디렉터리를 다른 프로세스가 쥐고 있다. 호출 0 | **기다리면 풀린다** |
| 4 | `UNLOCKABLE` | 자물쇠를 **걸 수 없다**(잠금 미지원 파일 시스템이거나 자물쇠 파일을 못 엶). 호출 0 | **기다려도 풀리지 않는다** — 실행 상태 디렉터리 경로·권한을 고친 뒤 재기동 |

**6G runbook §2 는 이 다섯 가운데 네 개(0·2·3·4)만 적는다** — `FAILED`(1) 를 적지 않는다. 그 문서는 실
수집 캠페인 전용이고 이 표가 종료 코드의 전수다.

### 3.4 평가 커밋 — `EvaluationCommitExitCode` 셋

| 코드 | 이름 | 뜻 | 처방 |
|---|---|---|---|
| 0 | `COMPLETE` | 요청이 전부 접수됐다(승격이 0 건이어도 0) | 없음 |
| 1 | `FAILED` | 협력자가 던졌다 | 사유 토큰을 읽는다 |
| 2 | `INCOMPLETE` | 요청 하나 이상이 실패했다 — **판정은 남고 outbox 쓰기가 실패**했다 | outbox 쓰기 실패 사유(SQLSTATE)를 본다. 판정은 보존돼 있으므로 재실행이 안전하다 |

### 3.5 cause code 를 읽는 법

실패 줄의 사유 토큰은 두 형태뿐이다.

| 형태 | 뜻 |
|---|---|
| `<예외 클래스 FQN>:sqlState=<5자리>` | SQL 예외. SQLSTATE 가 진짜 정보다(`23505` 유일성 위반, `53300` 연결 한도, `57P01` 관리자 종료 등) |
| `<예외 클래스 FQN>` | 그 밖의 예외 |

**메시지는 싣지 않는다** — 접속 문자열·호스트·공고 내용이 거기로 샌다. 「메시지가 없어서 못 고친다」면
SQLSTATE 와 클래스 이름으로 좁힌 뒤 DB 쪽 로그를 본다. 수집 레인의 사유 코드는 아직 자기 사본을
쓴다(`OPEN-6F10-CAUSE-CODE-DEDUP`) — 형태는 같다.

### 3.6 `ISOLATED` 는 **알림을 영구히 잃는 사건**이다

outbox 의 상태 어휘는 `PENDING` · `CLAIMED` · `DELIVERED` · `FAILED` · `ISOLATED` 다섯이고 종단 셋
(`DELIVERED`·`FAILED`·`ISOLATED`)에서 **나가는 전이가 하나도 없다**. 전이표 밖의 (상태, 명령) 쌍은
전부 거부된다.

- **되돌릴 간선이 없다.** 격리된 행을 `PENDING` 으로 되돌리는 명령이 전이표에 없다.
- **DELETE 권한도 없다.** 애플리케이션 역할은 outbox 행을 지울 수 없다(축9 권한 행렬).
- 그래서 격리는 **운영자가 손으로 수습할 수 없는 손실**이다. 할 수 있는 것은 ① 그 사건이
  일어났음을 아는 것(`INCOMPLETE` 2 + 마침 줄의 `orphansIsolated`) ② 같은 일이 다시 일어날 조건을
  없애는 것이다.

격리가 생기는 두 경로:

| 경로 | 무엇이 일어났나 | 다시 일어나지 않게 하는 법 |
|---|---|---|
| **고아 격리** | 앞 run 이 `CLAIMED` 상태로 죽었다. 새 임대를 쥔 relay 가 claim 전에 보이는 `CLAIMED` 를 태운다 | relay 프로세스를 강제 종료하지 않는다. 종료가 필요하면 run 사이에 한다 |
| **모호한 배달** | sender 가 `Unknown` 을 냈다 — 보냈는지 못 보냈는지 모른다 | at-most-once 확정(운영자 결정 2026-08-26)의 결과다. 중복을 만들지 않기로 한 대가이므로 **되돌리지 않는다** |

**놓친 알림의 회수 경로가 오늘 없다.** capability 명세는 앱 알림함(DB)이 내구성을 진다고 하지만 그 표가
V2 에 없다(`OPEN-6F3-BID-RECORD`). 그래서 격리된 알림의 내용은 outbox 행의 `payload` 를 DB 로 직접
읽는 것이 유일한 확인 수단이다.

```
select state, count(*) from outbox group by state order by state;
select entry_id, correlation_id, occurred_at, payload_type from outbox where state = 'ISOLATED' order by occurred_at desc limit 20;
```

### 3.7 임대 Busy 는 장애가 아니다

`LEASE_BUSY`(relay 3) · `ALREADY_RUNNING`(수집 3) 은 **중복 실행 억제가 제대로 돈 것**이다. 이 값을 0
으로 접지 않는 이유는 cron 이 성공으로 읽어 「왜 아무것도 안 돌았나」가 안 보이게 되는 것을 막기
위해서다. 경보를 걸 대상이 아니고, **연속으로 계속 Busy** 이면 그때 홀더 프로세스를 찾는다.

### 3.8 멈춤 조건 — 이때는 고치지 말고 보고한다

- 기동 실패 출력에 `RUN_STATE_FORMAT_*` 이 보인다(실행 상태 디렉터리가 옛 형식).
- `ISOLATED` 행이 한 run 에 **여러 건** 생겼다 — 원인을 모른 채 다음 run 을 돌리면 더 태운다.
- 같은 owner 로 relay 가 둘 돌았다(`FAILED` 1 + 임대 상실).
- 감사 쓰기 실패로 API 가 전부 500 인데 DB 는 살아 있다(§2.5).
- 로그·표준 출력에 자격 값이나 공고명 원문이 보인다 — **즉시 멈추고 보고**한다(설계상 0 이어야 한다).

---

## 4. model rollback

### 4.1 정의

**model rollback = 릴리스 선택자 `EXACT` 로 직전 release 를 지정하는 것**이다. 타입은
`workflow/src/main/kotlin/bidvector/workflow/prediction/BidPredictionRequest.kt` 의
`sealed interface ModelReleaseSelector` 이고 두 값뿐이다.

| 값 | 뜻 |
|---|---|
| `LatestPromoted` | 승격된 최신 release 를 쓴다 |
| `Exact(releaseId, artifactChecksum)` | 그 release 를 쓴다. 두 인자 모두 blank 를 거부한다 |

### 4.2 **오늘 production 에 운영자 경로가 없다**

파이프라인이 쓰는 선택자는 정책 데이터에 **`LatestPromoted` 로 고정**돼 있다 —
`workflow/src/main/kotlin/bidvector/workflow/evaluation/OpportunityPolicyData.kt` 의 출하 인스턴스가
`releaseSelector = ModelReleaseSelector.LatestPromoted` 다. 그 값을 바꾸는 설정 키도, HTTP 표면도,
명령행 인자도 **없다**.

그래서 운영자가 **지금** 할 수 있는 것과 할 수 없는 것은 이렇게 갈린다.

| 할 수 있다 | 할 수 없다 |
|---|---|
| 승격을 **멈춘다** — 나쁜 release 를 승격하지 않으면 `LatestPromoted` 가 그것을 고르지 않는다 | 파이프라인에게 「직전 release 로 돌아가라」고 **말한다** |
| 레지스트리 쪽에서 **승격을 되돌린다**(그 release 를 승격 상태에서 내린다). 그러면 `LatestPromoted` 가 그 앞 release 를 고른다 | 설정·요청·플래그로 `EXACT` 를 주입한다 |
| `EXACT` 가 실제로 직전 release 를 돌려준다는 것을 **gateway 수준에서 확인**한다 — `bidvector.adapters.e2e.PipelineContractRejectionE2ETest` 의 「`EXACT` 선택자는 직전 release 를 돌려주고 `LATEST` 와 다른 `releaseId` 를 낸다」 | 파이프라인 전체를 `EXACT` 로 돌린다 |

### 4.3 그래서 오늘의 rollback 절차는 「승격을 되돌리기」다

1. 어느 release 가 문제인지 고른다 — 판정 payload 가 release 식별자를 나르므로 그 값으로 좁힌다.
2. 레지스트리에서 그 release 의 **승격을 내린다**. 승격은 예약 학습 실행이 만들 수 없고
   (`아티팩트 무결성` 계약) 체크섬 없는 아티팩트는 「통과」가 아니라 별도 verdict 로 보고된다.
3. 파이프라인을 다시 돌린다. `LatestPromoted` 가 이제 앞 release 를 고른다.
4. **확인**은 판정 payload 의 release 식별자로 한다 — 그 값이 바뀌지 않았다면 되돌아가지 않은 것이다.
   `bidvector.adapters.e2e.PipelineReproducibilityE2ETest` 의 「release 를 바꾸면 payload 등식이
   깨진다」가 그 축을 test 로 잠근 것이다.

**운영자 경로(선택자 주입)를 만드는 것은 이 slice 의 범위 밖**이고, 그것 없이 2번 항목이 유일한 수단이다.

---

## 5. notification dry-run 과 live read probe

### 5.1 환경 → 모드 → claim → 종료 코드 사상표

출하 정책은 `NOTIFICATION_DELIVERY_POLICY`(`EffectiveDatedPolicy`, 정본은
`reports/evidence/m4/4e/policy-values.md §1·§2` — 사용자 승인 2026-09-09)이고
`environmentModes` 는 `RuntimeEnvironment` **전 값을 덮을 것**을 생성 시점에 요구한다.

| `bidvector.relay.environment` | `DeliveryMode` | relay 가 claim 하는가 | 행의 운명 | 기동 | run 종료 코드 |
|---|---|---|---|---|---|
| `Production` | `Live` | — | — | **거부** (§5.2) | 러너가 뜨지 않는다 |
| `Staging` | `DryRun` | **아니오** | `PENDING` 보존 | 뜬다 | `4` `ENV_SUPPRESSED` |
| `Development` | `DryRun` | **아니오** | `PENDING` 보존 | 뜬다 | `4` `ENV_SUPPRESSED` |
| `Test` | `Blocked` | **아니오** | `PENDING` 보존 | 뜬다 | `4` `ENV_SUPPRESSED` |

억제 판정은 **환경 이름을 보지 않는다** — 정책표가 그 환경에 붙인 모드를 본다. `maskedSuffixLength` 는
`4` 이고 배달 대상 라벨은 끝 4자만 남기고 나머지를 별표로 덮는다.

**정책 판정과 환경 판정은 서로 다른 필드로 남는다** — 「정책상 차단」과 「환경상 불가」가 한 값으로
합쳐지지 않는다.

### 5.2 `Live` 기동 거부의 의미

정책표가 그 환경에 `Live` 를 붙이면 `RelayBootDecision` 이 `RefusedLiveWithoutSender` 를 내고
**러너가 기동하지 않는다**(`OPEN-STR-12` — 실 발송 채널이 없다). 이유:

> 이대로 뜨면 relay 가 claim 한 뒤 자리지킴 sender 에서 터지고, 그 행은 다음 run 의 고아 격리가 태운다.
> **매 run 이 행을 영구히 잃는다.**

즉 기동 거부는 보수적인 과잉이 아니라 §3.6 의 손실을 막는 유일한 수단이다. **`Live` 가 뜨게 하려면
설정이 아니라 실 sender 가 먼저 있어야 한다.** 귀결: 오늘 relay 가 실제로 claim 하는 배포는 **없다**.

### 5.3 평가 dry-run — `POST /api/evaluation-dry-runs`

판정을 **outbox 에 쓰지 않고** 끝까지 돌린다(`RecordingNotificationRequestPort` 로 조립된다). 커밋
경로(`bidvector.evaluation.mode=once`)와 조립이 분리돼 있고 그 분리는 architecture gate 가 잠근다.

호출 형태 — **자격 값을 argv 에 싣지 않는다**(`-H "…$VALUE"` 금지; 같은 호스트의 다른 프로세스가
`/proc/<pid>/cmdline` 으로 읽는다). `printf` 는 셸 내장이라 별 프로세스를 만들지 않는다.

```sh
body="$(mktemp)"; printf '{"currentActiveBids": 0}' > "$body"
printf 'header = "X-Operator-Credential: %s"\nheader = "Content-Type: application/json"\nrequest = "POST"\ndata = "@%s"\n' \
  "$OPERATOR_CREDENTIAL_VALUE" "$body" \
  | curl --config - -sS -w '\n%{http_code}' "http://127.0.0.1:${API_PORT}/api/evaluation-dry-runs"
rm -f "$body"
```

본문은 비밀이 아니지만 `--config` 의 `data = "@파일"` 로 넘겨 인용 규칙을 단순하게 둔다(CI 스모크와
같은 형태). 자격 값만 `header` 줄로 파이프를 지난다.

응답 본문의 칸은 아홉이다 — `strategyRevision` · `candidateCount` · `currentActiveBids` ·
`maxActiveBids` · `bidNowNoticeIds` · `reviewNoticeIds` · `skipNoticeIds` · `notReachedNoticeIds` ·
`wouldNotifyNoticeIds`. **공고명·기관명 원문은 없다**(평탄하고 ID 와 건수만 싣는다). 후보별 사유
상세는 아직 없다(`OPEN-6A3-EVALUATION-DETAIL`).

거부 코드:

| 코드 | 언제 | 처방 |
|---|---|---|
| 400 `INVALID_REQUEST` | `currentActiveBids` 가 유효한 JSON 정수(0..2147483647)가 아니다 — 음수·누락·null·문자열·소수·범위 초과·본문이 object 아님·비JSON·빈 본문 | 본문을 고친다 |
| 401 `UNAUTHENTICATED` | 자격증명 없음 또는 틀림(사유를 구분하지 않는다) | 자격 값을 확인한다 |
| 409 `MAX_ACTIVE_BIDS_NOT_CONFIGURED` | 저장된 전략에 여력 상한이 없다 | 전략 편집으로 `MAX_ACTIVE_BIDS` 를 먼저 정한다 — **0 으로도 무한으로도 지어내지 않는다** |
| 409 `CANDIDATE_CAP_EXCEEDED` | 후보 스캔이 상한을 넘었다 | 조용히 자르지 않고 크게 실패한다. 범위를 좁히거나 상한을 올린다 |
| 415 `UNSUPPORTED_MEDIA_TYPE` | 본문이 `application/json` 이 아니다 | 헤더를 고친다 |
| 500 `INVALID_STORED_STRATEGY` \| `INTERNAL_ERROR` | 저장된 전략이 유효하지 않거나 **감사 기록이 실패**했다 | 후자는 §2.5 |

**dry-run 이 outbox 를 건드리지 않는다는 것은 CI 가 매 PR 에 잰다** — `container` job 의 S-23b 스모크가
왕복 뒤 outbox 행 수 전후 등식을 단언한다.

### 5.4 일반 운영의 live read probe — 절차만, 실행은 사용자 승인

6G runbook 은 **M6/6G 백테스트 표본 수집 전용** 절차다. 운영 반입 후 「외부 읽기가 살아 있는가」를
확인하는 probe 는 아래와 같이 **가장 좁은 1건 호출**로 한다.

**이 slice 는 이 절차를 실행하지 않았다. 실행은 사용자 승인 대상이다.**

전제(6G runbook §0·§1 과 같다):
1. 호스트 점검 셋을 **별도 호출로** 먼저 돈다 — `pgrep` · `free -m`(available ≥ 6 GB **그리고** Swap
   free ≥ 2 GB) · `ps -eo pid,rss,args --sort=-rss | head`.
2. 서비스 키는 **원문형**을 서브셸에서 읽어 **그 프로세스 환경에만** 넘긴다(인코딩형은 이중 인코딩이라
   쓰지 않는다). 명령 문자열·argv·history·transcript 어디에도 값이 나타나지 않는 형태여야 한다.
3. 호출 상한을 **1일 1건으로 묶는다** — `calls-per-day=1`·`calls-total=1`.

절차:
1. 조회 범위를 **하루**로 둔다(`from` == `to`, 어제).
2. 업종을 **하나만** 준다.
3. `SPRING_MAIN_WEB_APPLICATION_TYPE=none` + `bidvector.collection.mode=once` 로 공고 목록 갈래를 띄운다.
4. 종료 코드와 마침 줄을 읽는다 — §3.3. `0` 이면 외부 읽기가 살아 있다. `2` 이면 마침 줄의 절단 사유를
   본다. 전송 실패면 사유 토큰이 클래스 이름으로 남는다.
5. 실행 상태 디렉터리는 **운영 캠페인과 다른 경로**를 쓴다 — probe 가 캠페인의 예산 원장을 소비하지
   않게 한다.
6. 기록은 **건수·비율만** 남긴다(원문·키 없음).

probe 가 **하지 않는 것**: 개찰 갈래를 켜지 않는다(축 다섯이 돌아 호출이 늘어난다) · 스냅숏을 뽑지
않는다 · 알림 relay 를 켜지 않는다.

---

## 6. 알려진 제한 — 이 문서가 약속하지 않는 것

1. **SLO·임계값이 없다.** 큐 깊이·검출 지연·drift 임계는 `OPEN-OPS-03`·`OPEN-OPS-04` 가 미결로 들고
   있다. 이 문서는 숫자를 발명하지 않는다.
2. **metric 계측이 0 이다.** Micrometer 좌표는 들어와 있으나 production 사용이 없고, 노출된 actuator
   endpoint 는 `health` 하나다. `metrics` 를 여는 변경은 §2 의 잠금 술어를 바꾸는 일이라 6E-2 에 있다.
3. **구조화 로그·trace·대시보드가 없다.** correlation id 는 이벤트·감사 층에 있고 **로그 줄에는 실리지
   않는다**(계수와 열거값만).
4. **큐 깊이를 재는 자리가 없다.** §3.6 의 질의가 유일한 수단이고 임계 판정은 사람이 한다
   (`OPEN-OPS-10`).
5. **커넥션 풀이 없다**(§2.6, `OPEN-6A1-CONNECTION-POOL`).
6. **판정·투찰 기록 표가 없다**(`OPEN-6F3-BID-RECORD`). outbox 행이 판정의 유일한 영속 흔적이다.
7. **model rollback 의 운영자 경로가 없다**(§4.2).
8. **실 알림 발송 채널이 없다**(`OPEN-STR-12`). `Live` 는 기동이 거부된다.
9. **주기 실행(스케줄러)이 없다**(`OPEN-6F10-SCHEDULER`). 모든 러너는 `mode=once` 1회성이고 주기는
   외부 cron 이 진다.
10. **SBOM·CVE 스캔·서명·레지스트리 push 가 없다**(`OPEN-6C-IMAGE-VULN-SCAN`). 이미지 위생(고정 태그·
    non-root·금지 패키지·크기·base digest 핀)은 CI 가 잰다.
11. **멀티아키 빌드가 없다**(`OPEN-6C-MULTIARCH`) — 배포 대상이 정해질 때.
12. **RBAC·다중 사용자·TLS·네트워크 배치를 다루지 않는다**(`OPEN-6A-RBAC`). 단일 운영자 전용이다.
13. **자격증명 원문 경계가 완전히 닫히지 않았다**(`OPEN-6A1-CREDENTIAL-RAW-REINTRODUCTION`) — 환경변수
    원문은 어딘가에 `String` 으로 존재해야 한다는 구조적 뿌리가 남아 있다.

## 7. 이 문서의 표가 코드와 어긋나지 않는지 재는 법

§1.2·§1.3·§2.1·§2.2·§2.3·§3.2·§3.3·§3.4·§5.1 의 표는 **코드에서 기계 수집한 집합과 등식**이 서야
한다. 그 등식을 재는 명령 전부는 `reports/evidence/m6/6e1/commands.md` 「문서 등식」 절에 있고,
표를 고칠 때 그 명령을 다시 돌린다.
