# M6/6E-2a — 체크리스트 · 우회 대응 · 알려진 제한 · OPEN 처분 (구현 레인)

base `2deb5f9d` · 브랜치 `m6-6e2a/2026-10-08`.

## 산출물 점검

| ID | 자리 | 상태 | 공허함을 막는 자리 |
|---|---|---|---|
| **P-1** | `PersistenceWiring` 의 풀 + 연결 초기화 | 완료 | `ProductionPoolRoleTest` — 빈 타입 · 재사용/동시/재생성 세 경로의 `current_user` · 권한 질의 + **실제 `DELETE FROM outbox` 가 42501** |
| **P-2** | migration 을 지역 소유자 연결로, `DataSource` 빈은 하나 | 완료 | 같은 test — `getBeansOfType` 크기 1 · **빈 DB 기동 그 자체** · IDENTITY 표 INSERT · 소유자 이름표 세션 0(양성 대조 포함) |
| **P-3** | 풀 크기 하한 ≥ 2 · 임대와 작업의 공존 | 완료 | `PooledLeaseFloorTest`(2 면 성립 · 1 이면 굶는다) + 임대 test 셋을 풀 위에서 |
| **P-4** | 호환 표면 등재 | 완료 | 카탈로그 alias · `expectedModules` · `BootCompatibilitySmokeTest` · `BootJarRuntimeClasspathTest` · `gate-tests.properties` · 금지 group `com.zaxxer` · **app 바깥 참조 허용 집합** |
| **P-5** | fixture 초기화를 admin 연결로 | 완료(**계약보다 넓었다 — 아래**) | 여섯 자리가 역할 전환 뒤에도 초록 · 역할 전환을 끄는 변이(M1)에서 P-1 test 가 RED |
| **P-6** | 기동 실패 의미 실측(S-22b 1초 창 · S-22c) | 완료 | `container` job 로컬 재현 전 step exit 0 · `tools/image-hygiene-check.sh` **무편집** |
| **P-7** | runbook §2.6·§3.6·§6 | 완료 | `commands.md` 「문서 등식」 E-1~E-6(문서에 박은 블록을 떼어 돌려 OK 6 실측) |

**P-5 가 계약보다 넓었다.** 계약은 `ProductionAssemblyAuthAuditTest` 하나를 지목했고, 역할 전환이
드러낸 자리는 **여섯**이었다 — `TRUNCATE` 다섯(`ProductionAssemblyAuthAuditTest` ·
`EvaluationDryRunE2ETest` · `EvaluationDryRunBidNowE2ETest` · `StrategyEditProductionE2ETest` ·
`CommitRunDbSupport.clearOutbox`)과 **실패 주입 DDL** 하나(`ALTER TABLE outbox ADD CONSTRAINT` — 표
소유자만 가능). 전부 `app/src/test/**`(in_scope)이고 공용 `adminDataSource(container)` 한 자리로 모았다.
**조립이 실제로 하는 일(HTTP 왕복·러너·조회)은 그대로 production 빈을 지난다.**

**계약에 없던 자리 하나를 더 고쳤다 — `HttpTestApplication`.** 풀 구현이 classpath 에 오면서 Boot 의
`DataSourceAutoConfiguration` 이 **`DataSource` 빈이 없는 test 조립에서** 스스로 풀을 만들려 들어(접속
정보가 없어 「드라이버를 정할 수 없다」) 컨텍스트가 깨졌다. 그 조립의 자동설정을 끈다. 출하 조립은
`PersistenceWiring` 의 빈이 있어 같은 자동설정이 물러나며, 그 사실은 빈 개수 단언이 잠근다.

`AppHttpDependencyGateTest` 의 면제 집합 크기는 **바꾸지 않았다** — 이 slice 가 만든 `bidvector.app`
production 최상위 클래스가 0 이다(상수는 `PersistenceWiring` 의 companion 이고 판정이 소유자로 접는다).
계약의 「필요 시」가 필요하지 않았던 자리다.

## 설계 검토가 지목한 우회 ↔ 무엇이 막는가

| # | 우회 | 막는 자리 | 종류 |
|---|---|---|---|
| 1 | 연결 초기화 SQL 누락·오타 | `ProductionPoolRoleTest` 의 실제 DELETE 거부와 `current_user` — **변이 M1 로 RED 실측** | test(실 DB 질의) |
| 2 | 재사용 연결이 역할을 잃는다 / 새 물리 연결만 전환된다 | 같은 test 의 **세 경로**(최대 크기 2배 순차 · 최대 크기 동시 · `softEvictConnections` 뒤) + 관측 backend pid ≥ 2 | test(실 DB 질의) |
| 3 | 소유자 `DataSource` 를 `@Bean` 으로 노출 | `getBeansOfType(DataSource).size == 1` — **변이 M4 로 RED 실측** | 구성(집합 크기) |
| 4 | 소유자 연결을 필드·클로저로 남긴다 | 소유자 이름표 세션 **0** + 양성 대조(같은 이름표를 열면 1) + 풀 이름표 ≥ 1(이름표 기계가 서버까지 닿는다) | test(실 DB 질의) |
| 5 | 런타임 코드가 `RESET ROLE` 을 한다 | **경계 밖**(악의적 저자). 보조로 `E-1` 이 역할 전환 자리가 **한 곳**임을 센다 — 게이트가 아니다 | 선언(문서 등식) |
| 6 | 풀 크기 1 이면 임대가 작업 연결을 굶는다 | `PooledLeaseFloorTest` 양방향 + 조립 위의 하한 단언 — **변이 M2 로 RED 실측** | test(실 DB 질의) |
| 7 | readiness `db` 가 다른 `DataSource` 를 본다 | 빈이 하나라 **구성상** 닫힌다(우회 3 과 같은 자리) | 구성(집합 크기) |

「풀이 migrate 보다 먼저 생긴다」(설계 검토 (3))는 **빈 DB 기동 자체**가 잡는다 — 역할은 `V2` 가 만들므로
순서가 뒤집히면 첫 연결 초기화가 그 자리에서 죽는다. **변이 M3 이 그것을 실측했다.**

## 값 획득 축 — 이번 수정이 만든 새 public 표면

| 표면 | 밖에 허락하는 것 | 판정 |
|---|---|---|
| `dataSource` 빈의 실제 타입이 풀이 됐다 | `DataSource` 로 주입받는 쪽에는 풀 제어가 보이지 않는다. 캐스팅하면 보인다 | 빈 이름·선언 타입이 같고 **권한은 오히려 줄었다**(소유자 → 최소 권한 역할). 닫는다 |
| `PersistenceWiring` companion 의 상수 일곱 | 값 읽기 | 전부 `internal` — 모듈 밖에서 보이지 않는다. 값 자체는 runbook §2.6 이 공개하는 운영 정보다 |
| `PersistenceTestSupport.pooledDataSource()`·`newPool()` | adapters **test** 하네스가 풀을 얻는다 | test source set 전용(`protected`). production 표면이 아니다 |
| `bidvector.app.adminDataSource(container)` | app **test** 하네스가 소유자 연결을 얻는다 | test source set 전용. 인자가 **test 컨테이너**라 출하 배포의 자격에 닿을 경로가 없다 |
| migration 소유자 연결 | 소유자 권한 migrate | **경계로 처리** — `private fun` 의 지역 변수이고 빈·필드가 아니다. 컨텍스트에서 얻을 경로가 없음을 빈 개수와 세션 수로 실측 |
| `PersistenceProperties` 빈(기존 표면) | **소유자 자격 값** | **경계로 처리** — 설계 검토 (2b) 의 그 행이다. 검증 레인이 실측했다: 컨텍스트에서 그 빈을 받으면 자격 값을 **얻는다**(풀 빈에서도 같은 값을 얻을 수 있고, base 의 비풀링 `DataSource` 도 같았다 — **표면 증가 0**). 이것이 `OPEN-6E2A-OWNER-CREDENTIAL-IN-APP` 가 가리키는 바로 그 자리이고, 새 결함이 아니다 |

**줄어든 표면**: 런타임 연결이 더 이상 DB 소유자 권한을 쥐지 않는다. `outbox` DELETE · `notice_audit`
INSERT · TRUNCATE · DDL 은 **앱 세션에서 DB 가 거부**한다(실측).

## 알려진 제한

1. **소유자 자격 값이 앱 환경에 그대로 있다**(`OPEN-6E2A-OWNER-CREDENTIAL-IN-APP`). 이 slice 가 막는
   것은 **앱 결함**이고, 앱 프로세스 장악·자격 값 유출·세션의 `RESET ROLE` 은 막지 않는다. 그 축은 앱
   전용 LOGIN 역할과 migration 의 배포 단계 분리를 요구하고, 둘 다 **배치 환경 결정이 선행**한다.
2. **풀 값이 설정 키가 아니다**(코드 상수, 운영자 결정 A-2 (a)). 운영 실측으로 값을 바꾸려면 재배포가
   필요하다. **최대 크기 10 의 근거는 하한뿐**이다 — 상한의 적절성은 운영 부하가 없어 재지 못한다.
3. **접속 사용자가 `bidvector_app` 의 멤버가 아니면 기동이 실패한다.** 출하 배포 모양에서는 DB 소유
   superuser 라 성립하지만, 그 전제가 깨지는 배치는 첫 연결 초기화에서 죽는다 — **실패 방향은 안전하다**
   (조용히 소유자 권한으로 도는 일은 없다).
4. **transaction 모드 pooler(pgbouncer 등)를 지원하지 않는다**(기존 제한 그대로). 세션 범위 advisory
   lock 과 물리 연결당 1회인 역할 전환이 둘 다 「같은 서버 backend」를 전제한다.
5. **adapters 의 임대 test 가 쓰는 풀에는 역할 전환이 없다.** 그 시나리오가 `pg_terminate_backend` 를
   쓰기 때문이고, 역할 축은 app 의 실 assembly test 가 든다 — 두 축이 한 자리에서 겹치지 않는다.
6. **금지 group 게이트는 「해석된 좌표」만 본다.** 버전이 BOM 에서 오는 카탈로그 별칭을 BOM 없는 domain
   모듈에 선언하면 해석되지 않아 게이트가 조용하다(변이 M6a). 그 상태의 모듈은 **컴파일도 되지 않으므로**
   실제 경로는 열리지 않지만, 게이트 단독으로 닫힌 축은 아니다.
7. **연결 누출의 결과가 바뀌었고 탐지 수단이 없다**(`OPEN-6E2A-TX-BOUNDARY-LEAK-UNDER-POOL`, cr M-1).
   트랜잭션 경계가 연결을 돌려주지 못하는 두 자리는 **이 slice 가 만든 것이 아니고** 그 파일은 in_scope
   밖이다. 바뀐 것은 **결과**다 — 비풀링에서 소켓 하나였던 것이 상한 10 인 풀의 한 자리가 된다. 풀에
   누출 탐지 임계값을 두지 않았다(임대 연결을 본문 내내 쥐는 relay 때문에 그 임계값의 근거가 따로
   필요하고, 근거 없는 수를 지어내지 않는다).
8. **adapters 의 저장소 test 가 역할 밖에서 돈다**(`OPEN-6E2A-ADAPTER-TESTS-RUN-AS-OWNER`, cr M-3).
   production SQL 이 사는 모듈의 하네스가 소유자 자격으로 돌아, GRANT 가 빠진 **새** 경로는 app E2E 가
   지나는 자리에서만 잡힌다. 오늘은 깨끗하다 — 코드 리뷰의 GRANT 전수 대조와, 검증 레인이 그 하네스를
   역할 풀로 **재구성해 전 suite 를 돌린 실측**(902 중 실패 10, **production 경로 0**; 실패는 스키마 메타
   질의 다섯·fixture DDL 둘·변이 부산물 둘) 둘 다 어긋남 0 이다. 남는 것은 구조적 사각이다.
9. **공유 test 풀이 닫히지 않고 유휴 연결을 상시 쥔다**(cr L-2). `PersistenceTestSupport` 의 풀은
   companion 의 lazy 싱글턴이라 닫는 자리가 없다(Hikari 의 내부 스레드는 daemon 이라 JVM 종료를 막지
   않는다). `minimumIdle` 기본값이 최대 크기와 같아 **상시 8 연결**을 쥐고, 그 위에 기존 admin 연결과
   하한 test 의 일회용 풀이 얹힌다 — 공유 컨테이너의 연결 상한 여유를 그만큼 깎는다.
10. **`ProductionPoolRoleTest` 가 감사 표에 행을 남기고 그 class 에 초기화가 없다**(cr L-7). 전용
    컨테이너를 쓰고 행 수를 세는 칸이 없어 **오늘은 무해**하다. 뒤에 「감사 행 수」를 재는 칸이 이
    class 에 생기면 JUnit 의 메서드 순서에 조용히 의존하게 된다 — 그 칸을 더하는 쪽이 초기화를 함께
    더해야 한다.

## OPEN 처분

| OPEN | 처분 |
|---|---|
| `OPEN-6A1-CONNECTION-POOL` | **닫는다** — 런타임 `DataSource` 가 HikariCP 풀이다(runbook §2.6 상수표). 남은 축은 「값이 설정 키가 아니다」이고 제한 2 로 등재 |
| `OPEN-6E1-APP-ROLE-NOT-ASSUMED` | **닫는다** — 풀의 물리 연결마다 역할 전환이 돌고 앱 세션의 `DELETE FROM outbox` 가 42501 로 거부된다(출하 조립 위 실측). 남은 축은 자격 값이고 아래로 넘긴다 |
| **`OPEN-6E2A-OWNER-CREDENTIAL-IN-APP`**(신설) | 소유자 자격 값이 앱 환경에 남고 세션이 `RESET ROLE` 로 그 권한을 되찾을 수 있다. 앱 전용 LOGIN 역할 · migration 의 배포 단계 분리 — **M7 배치 환경 결정 동반** |
| **`OPEN-6E2A-TX-BOUNDARY-LEAK-UNDER-POOL`**(신설, cr M-1) | 풀이 트랜잭션 경계의 기존 누출 경로를 「소켓 하나」에서 **「풀 용량 소진」**으로 승격시켰고 탐지 수단이 없다. 처분 후보 둘 — ⓐ 누출 탐지 임계값(그 수의 근거가 relay 임대 최대 수행 시간에서 와야 한다) ⓑ 경계 코드의 자원 반납 수정(`adapters/src/main`, 이 slice 의 in_scope 밖). **둘 다 이 slice 의 범위 밖이라 등재로 닫는다**(runbook §2.6·§6 제한 17) |
| **`OPEN-6E2A-ADAPTER-TESTS-RUN-AS-OWNER`**(신설, cr M-3) | production SQL 이 사는 `adapters` 의 test 하네스가 소유자 자격으로 돈다 — GRANT 누락은 app E2E 가 지나는 경로에서만 잡힌다. 처분 후보: lease·relay 가 아닌 저장소 test 를 **역할을 건 풀**로 옮긴다(하네스 교체라 그 자체가 slice 하나). 오늘의 근거는 **검증 레인의 역할 풀 재구성 실측**(902 중 실패 10, production 경로 0, runbook §6 제한 18) |

## 틀릴 수 있는 자리

- **「소유자 세션 0」의 비공허성.** 질의가 세는 것은 production 상수와 같은 문자열의 이름표다. 이름표를
  다는 배선이 통째로 죽으면 0 은 공허하게 참이 된다 — 그래서 **풀 이름표 ≥ 1**(같은 기계가 서버까지
  닿는다)과 **양성 대조**를 함께 뒀다. 그래도 「migration 쪽만 이름표를 잃는」 변이는 이 셋을 통과한다.
- **`expectedModules` 등재는 「등재 ⊆ 해석」이다**(변이 M5a, cr L-4). 등재를 빼면 그 좌표를 아무도 재지
  않고 게이트는 초록이다 — 등재가 의무인 이유가 그것이고, 그 성질은 게이트가 아니라 리뷰가 든다.
  **그 좌표를 실제로 잠그는 것은 둘이다**: `PersistenceWiring` 의 컴파일 의존(좌표가 없으면 컴파일이
  깨진다)과 `BootJarRuntimeClasspathTest` 의 새 칸(`compileOnly` 로 바꿔 배포물에서만 빼는 형태도
  붉어진다). 등재는 **해석 축의 리포트**를 더할 뿐이고, 그 둘로 충분하다.
