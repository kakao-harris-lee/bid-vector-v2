# M6/6E-2a — 커넥션 풀 · 최소 권한 접속 역할 (2026-10-08, 착수 계약, 팀장)

6E-1 이 6E-2 로 넘긴 운영자 결정 가운데 **사용자 결정(2026-10-08, 「추천대로 6E-2 진행해」)** 으로 착수하는 셋 — G-2 커넥션 풀 ·
G-1 SBOM/CVE · 접속 역할 전환 — 을 **두 slice 로 가른다**. 풀과 접속 역할은 같은 DataSource 배선을 바꾸는 한 축이라 묶고(**6E-2a, 이 slice**),
SBOM/CVE 는 CI·도구 축이라 따로 둔다(**6E-2b**, `reports/evidence/m6/6e2b/scope.md`). G-3 metric · G-7 투찰 기록은 결정 뒤(미착수).

- base: `2deb5f9d`(PR #65 6E-1 머지 뒤 `main`). worktree `bid-vector-v2-m6-6e2a`, 브랜치 `m6-6e2a/2026-10-08`.
- 착수 조사 `_workspace/m6-6e2a/00_scout.md`(gitignored). 설계 검토 `_workspace/m6-6e2a/01_design-review.md`(세션 모델 직접).
- 결정 ID `D-6E2A-n`. evidence `reports/evidence/m6/6e2a/`.
- 받는 OPEN: **`OPEN-6A1-CONNECTION-POOL`**(D-6A1-18) · **`OPEN-6E1-APP-ROLE-NOT-ASSUMED`**(6E-1 신설).
- Codex: 접속 역할은 인가 경로라 **Codex 대상 자격이 있다** — 유료 호출이므로 Phase 4 통과 뒤 **운영자에게 범위·비용을 묻는다**(6B-1 선례). 그때까지 검토 레인은 verifier(opus) + code-reviewer(sonnet).

## 착수 실측 (요지)

| 축 | 오늘 | 함의 |
|---|---|---|
| DataSource | `PersistenceWiring` 의 `@Bean dataSource` = `PGSimpleDataSource`(비풀링) 하나. 설정 `bidvector.persistence.{jdbcUrl,username,credential}`(기본값 없음) | 풀 교체 자리는 한 곳 |
| migration | **같은 빈**을 `strategyRepository` 빈이 받아 `Flyway.migrate()` — 빈 생성의 부수효과(알려진 제한) | 최소 권한으로 바꾸면 migrate 가 권한 부족으로 죽는다(`flyway_schema_history` 권한 0) → **migration 연결 분리가 선행** |
| 역할 | `V2` 의 `CREATE ROLE bidvector_app NOLOGIN` 하나. GRANT 전부 그 역할 대상(`outbox` SELECT/INSERT/UPDATE, DELETE 없음 등). production `SET ROLE` 0. compose·CI 의 앱 접속 사용자 == `POSTGRES_USER`(소유 superuser) | 권한 경계가 문서상으로만 선다(runbook §3.6) |
| 세션 상태 | `PostgresAdvisoryLockLease` 가 본문 내내 전용 연결 하나를 쥔다(세션 잠금) · `TransactionBoundary` autoCommit=false · 커서 스트리밍 · `FOR UPDATE SKIP LOCKED`. production 에 `SET`·`LISTEN`·임시 표·런타임 DDL **0** | 소유자 권한에 기대는 production 경로는 migrate 하나. 풀 크기 1 이면 relay 가 자기 자신과 교착 |
| 호환 표면 | HikariCP 좌표 0(카탈로그가 `spring-boot-starter-jdbc` 를 일부러 피함) · `compatibilitySmoke.expectedModules`(등재형) · `BootCompatibilitySmokeTest` · `gate-tests.properties`(test 전수 등식) · `AppHttpDependencyGateTest` 면제 집합 크기 고정 | 좌표만 더하면 게이트가 초록인 채 아무것도 안 잰다 → 등재 셋을 같이 |
| 기동 실패 | 오늘도 기동 중 migrate 로 DB 에 붙는다. 이미지 위생 S-22b 는 TEST-NET-1 주소로 **pid 1 이 1초 살아 있음**에 기댄다(6A-2a 제한 13 이 풀 도입 시 「판정 불가로 실패」 예고) | **실측**해야 한다(추정 금지) |
| test 하네스 | Testcontainers 는 전부 컨테이너 superuser. `ProductionAssemblyAuthAuditTest` 가 **production DataSource 빈으로 `TRUNCATE`** | 역할 전환 뒤 그 test 는 admin 연결을 따로 써야 한다 |

## 운영자 결정 — 추천안으로 착수(사용자 「추천대로」, 정정 시 계약 갱신)

- **A-1 풀 구현** — (a) **HikariCP**(Boot BOM 관리 좌표, `com.zaxxer:HikariCP` 단독 — starter 아님) · (b) 다른 풀. **추천 (a)**.
- **A-2 풀 설정 값** — (a) **코드 상수**(최대 크기·연결 타임아웃·초기화 실패 동작; runbook 에 표로) — 새 필수 환경변수 0, compose·CI 무변경 · (b) `bidvector.persistence.pool.*` 설정 키. **추천 (a)** — 설정 키는 운영 실측(M7)이 값을 요구할 때. 최대 크기 하한 **2**(임대 전용 1 + 작업) 를 구조로 잠근다.
- **A-3 접속 역할 방식** — (a) **풀 연결 초기화에서 `SET ROLE bidvector_app`**(Hikari `connectionInitSql`) + **migration 은 별도의 비풀링 소유자 연결**(빈으로 노출하지 않는 지역 객체, migrate 뒤 폐기) — 배포 환경·자격 값·마이그레이션 무변경 · (b) `bidvector_app` 에 LOGIN+비밀번호를 주고 앱이 그 계정으로 접속, migration 은 별도 자격(새 환경변수 둘·compose·CI·백업/복원·역할 비밀번호 배포 절차) · (c) migration 을 앱 밖 배포 단계로. **추천 (a)**. **방어 경계**: (a) 는 **앱 결함**(정상 DML 경로가 권한 밖 쓰기를 하는 것)을 DB 가 거부하게 한다. **앱 프로세스 장악·자격 값 유출은 방어하지 않는다** — 소유자 자격 값이 여전히 앱 환경에 있고 세션은 `RESET ROLE` 할 수 있다. 그 축은 신설 `OPEN-6E2A-OWNER-CREDENTIAL-IN-APP`(M7 배포 환경 결정 — (b)/(c) 로 이행)로 등재.
- **A-4 Codex** — Phase 4 통과 뒤 운영자에게 묻는다(위).

## 산출물

| ID | 무엇 | 공허함을 막는 술어 |
|---|---|---|
| **P-1** | production `DataSource` 빈 = `HikariDataSource`(A-1·A-2), `connectionInitSql` 로 역할 전환(A-3) | **실 assembly + Testcontainers** 로: 빈 타입 · `current_user = bidvector_app` · `has_table_privilege(current_user,'outbox','DELETE') = false` · **실제 `DELETE FROM outbox` 가 권한 거부** · 풀에서 연결을 N>최대크기 번 돌려 받아도 매번 같은 역할(재사용 연결 포함) |
| **P-2** | migration 연결 분리 — 소유자 비풀링 연결을 **migrate 지역에서만** 만들고 닫는다. 컨텍스트의 `DataSource` 빈은 **정확히 하나**(풀) | 컨텍스트 `getBeansOfType(DataSource)` 크기 == 1 · 소유자 연결 객체가 빈·필드로 남지 않음(값 획득 축, 설계 검토 (2b)) · clean DB 에서 기동 → migrate 성공 + 런타임은 `bidvector_app` |
| **P-3** | 풀 크기 하한 ≥ 2 · relay 임대(세션 advisory lock) + 작업 연결 공존 | 상수가 하한 아래면 RED 인 test · `PostgresAdvisoryLockLeaseTest`·`PoolerLeaseProbeTest`·`RelayLeaseLossDatabaseTest` 를 **풀 DataSource 위에서** 초록 |
| **P-4** | 호환 표면 등재 — 카탈로그 alias(사유 주석) · `compatibilitySmoke.expectedModules` · `BootCompatibilitySmokeTest` 대표 API 로드 · `BootJarRuntimeClasspathTest` 에 HikariCP jar 존재 · `gate-tests.properties` 신규 test 등재 · 필요 시 `AppHttpDependencyGateTest` 면제 크기 | 등재 각각을 지우는 변이 → RED |
| **P-5** | test 하네스 정리 — production DataSource 빈으로 `TRUNCATE` 하던 자리를 admin 연결로 | 그 test 가 역할 전환 뒤에도 초록 · 역할 전환을 끄는 변이에서 P-1 test 가 RED |
| **P-6** | 기동 실패 의미 실측 — 이미지 위생 S-22b(TEST-NET-1 1초 창) · S-22c 거부 스모크 · compose S-23 | `container` job 로컬 재현 exit 0. 창이 깨지면 `tools/image-hygiene-check.sh` 를 고치되 **판정 축을 약화하지 않는다**(uid 표집을 유지) |
| **P-7** | 문서 — runbook `docs/runbook/m6-6e-operations.md` §2.6(풀 부재 경고 → 풀 상수 표) · §3.6(DELETE 보호가 **작동함** + 경계: 자격 유출은 방어 안 함) · §6 제한 갱신 | 6E-1 의 E-25 를 **이 slice 의 등식으로 갱신**해 commands.md 에 둔다(기대값: `SET ROLE` 상수 자리 1 · `NOLOGIN` 유지 · 소유자 변수 동일 유지 · 문면). 6E-1 evidence 파일은 그 SHA 의 기록이므로 **편집하지 않는다** |

## in_scope / out_scope

- in: `app/src/main/kotlin/bidvector/app/wiring/PersistenceWiring.kt`(및 같은 패키지 신설 파일) · `app/build.gradle.kts` · `adapters/build.gradle.kts`(필요 시) · `gradle/libs.versions.toml` · `app/src/test/**` · `adapters/src/test/**`(풀 위 재실행이 필요한 test 와 하네스) · `config/quality/gate-tests.properties` · `config/quality/architecture-policy.properties`(필요 시, 술어 변경이면 표적 재검증) · `tools/image-hygiene-check.sh`(P-6 이 요구할 때만) · `docs/runbook/m6-6e-operations.md` · `reports/evidence/m6/6e2a/**` · `milestone-6.md`(착수·종결 문단).
- out: 마이그레이션 파일 전부(역할·GRANT 무변경 — 필요해지면 slice 를 멈추고 계약 갱신, migration-reviewer·Codex 축) · `docker/**` · `.github/workflows/ci.yml` · `reports/evidence/m6/6e1/**` · 6E-2b(SBOM/CVE) · G-3·G-7 · 실 DB·운영 credential.

## acceptance

CI job 명령 그대로: `./gradlew --no-daemon check` · `./gradlew --no-daemon qualityBaseline` · **`container` job 로컬 재현**(S-21~S-25 — 앱 이미지가 바뀌므로; 호스트 규율: pgrep·free·ps 별도 호출 선행, available ≥ 6GB·swap free ≥ 2GB, 호스트 전체 무거운 빌드 1개). S-20 생략(Python 무변경). 변이(바꿔치우기): ① `connectionInitSql` 제거 → P-1 RED ② 최대 크기 1 → P-3 RED ③ migration 을 풀 빈으로 → 기동 실패 또는 P-2 RED ④ 소유자 DataSource 를 `@Bean` 으로 노출 → P-2 RED ⑤ `expectedModules` 에서 HikariCP 제거 → RED.

## rollback

in_scope 경로 한정 `git restore --source=2deb5f9d --staged --worktree -- <목록>`(목록은 `git diff --name-status 2deb5f9d..HEAD` 기계 산출); 신설 파일 제거; 공유 파일(`milestone-6.md`·runbook·`gate-tests.properties`)은 커밋 해시 hunk 역적용(`git log --no-merges --format=%h 2deb5f9d..HEAD -- <파일>` 실행 시 산출, 최신부터). 임시 clone 에서 ①~⑥ 실측, `실측 HEAD` 기록.

## 하네스 레인 변경 (상시)

- (착수 시점 없음)
- 2026-10-09 `075c03b1` — `.claude/skills/codex-review-gate/SKILL.md` 바이너리·버전 핀 갱신(WSL 경로 · 0.160.1, 운영자 결정) + `docs/harness/change-history.md` 한 행. slice 산출물 아님 — rollback 은 되돌리지 않는다.

## 계약 갱신 r1 (2026-10-08, 팀장 — Codex 승인)

| ID | 결정 | 출처 |
|---|---|---|
| **D-6E2A-1** | **A-4 Codex 심판 허용**(사용자 「codex 심판 허용. 계속 진행해」 2026-10-08). 시점은 Phase 2.5 설계 검토 · Phase 4 verifier `ready-for-review` **뒤** 한 번(의미 있는 단위). 범위 = 이 slice 의 in_scope diff(`2deb5f9d..<판정 SHA>`). 종결 조건은 「verifier ready-for-review + Codex approve + 사용자 승인」. `request_changes` 두 수정 라운드 뒤 blocker 잔존 시 자동 반복 중단·보고 | 사용자 |

## 계약 갱신 r2 (2026-10-09, 팀장 — 구현 수령·동결 · 판정 착수)

| ID | 결정 | 출처 |
|---|---|---|
| **D-6E2A-2** | 구현 레인 완료 보고 수령(`_workspace/m6-6e2a/02_implementer_report.md`, 레인 HEAD `bb12794a`) → **레인 동결**, 판정 대상 SHA = 이 계약 갱신 커밋. 레인 실측: `check`·`qualityBaseline`·container job S-21a~S-25·rollback ⓪~⑥ exit 0, P-6 위생 1초 창 유지(스크립트 무편집). **계약 대비 어긋남 셋을 수령**: ① P-5 범위가 여섯 자리(TRUNCATE 다섯 + 실패 주입 DDL 하나)로 넓었다 — 전부 in_scope ② test 조립 `HttpTestApplication` 에서만 `DataSourceAutoConfiguration` 제외(풀 classpath 진입이 낳은 것; 출하 조립은 빈 개수 단언으로 잠금) — verifier 표적 ③ acceptance 변이 ⑤ 문면 정정: `expectedModules` 는 「등재 ⊆ 해석」이라 **등재 삭제는 초록**(M5a), 등재 좌표 바꿔치우기로 RED(M5b) — 등재형 게이트의 한계를 사실로. 코드 주석의 결정 ID 충돌(`D-6E2A-1`)은 `P-n`·`A-n` 인용으로 정정됨. 판정 레인: verifier(opus) + code-reviewer(sonnet) 병렬, 그 뒤 Codex(D-6E2A-1) | 팀장 |

## 계약 갱신 r3 (2026-10-09, 팀장 — 판정 r1 수령 · 승인 전 일괄)

| ID | 결정 | 출처 |
|---|---|---|
| **D-6E2A-3** | **verifier r1 `ready-for-review` @`32138c6f`**(`_workspace/m6-6e2a/03_verifier_r1.md`: acceptance 셋 exit 0 · 위생 1초 창 유지 · 바꿔치우기 변이 다섯 RED · adapters 전 suite 를 SET ROLE 풀로 재구성 실행 902 중 실패 10, production 경로 0) + code-review r1(high 0 · medium 3 · low 7). 재작업 0/5 | 팀장 |
| **D-6E2A-4** | **승인 전 일괄 커밋(장부·low, 재검증 없음 — Codex 가 본다)**: cr M-1 `TransactionBoundary` 누출 경로(adapters main, in_scope 밖) → 신설 **`OPEN-6E2A-TX-BOUNDARY-LEAK-UNDER-POOL`** + runbook §2.6 제한 · cr M-2 `INITIALIZATION_FAIL_TIMEOUT_MS` 는 라이브러리 기본값과 같아 삭제 변이가 초록 → 변이표에 사실로 + runbook 의 「즉시 기동 실패」 약속을 그 조건으로 한정 · cr M-3 adapters 하네스가 소유자로 돈다 → 신설 **`OPEN-6E2A-ADAPTER-TESTS-RUN-AS-OWNER`**, 근거로 verifier 재구성 실측(902 중 10 실패 · production 경로 0) · low: cr L-1(adapters build 주석 모순) · L-3(finally 안 단언) · L-5(runbook 「빈 DB」 조건) · L-6(빈 부수효과 알려진 제한 문구) · vr L-1(§2.6 — `hikaricp.configurationFile` JVM 옵션으로 표 밖 값 변경 가능, 경계 밖) · vr L-2(값 획득 표에 `PersistenceProperties` 행 복원 + 실측) · vr L-3(§2.6 minimumIdle = 최대 크기, 유휴 10). cr L-2·L-4·L-7 은 checklist 알려진 제한 | 팀장 |
| **D-6E2A-5** | 일괄 뒤 **Codex 심판 1회**(D-6E2A-1) — 범위 `2deb5f9d..<일괄 뒤 판정 SHA>` in_scope | 팀장 |

## 계약 갱신 r4 (2026-10-09, 팀장 — 일괄 수령·동결 · Codex 요청)

| ID | 결정 | 출처 |
|---|---|---|
| **D-6E2A-6** | 승인 전 일괄 수령(`_workspace/m6-6e2a/04_implementer_batch.md`, 레인 HEAD `8ad42495`; `check` @`72f27528` exit 0 · rollback ⓪~⑥ 재실측 exit 0, 실측 HEAD `72f27528`). 일괄이 연 것: rollback 의 「하네스 레인 커밋 0」 문면을 사실로(`075c03b1` 두 파일은 되돌리지 않는 경로) · cr M-2 는 사실 등재로만 닫힘(역할 멤버 아닌 사용자 기동 실패 칸은 **신설 `OPEN-6E2A-INIT-FAIL-PREDICATE`** 로 다음 slice). **레인 동결, 판정 SHA = 이 갱신 커밋 → Codex 심판 1회**(D-6E2A-1·5, 범위 `2deb5f9d..<이 커밋>` 의 in_scope; 하네스 커밋 `075c03b1` 은 위 절에 선언) | 팀장 |
