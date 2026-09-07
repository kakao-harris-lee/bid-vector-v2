# M3/3D — commands.md

base `01ecbba69e21e4b85ee8303416fe06a77b919fd6` · head `6a1fe681ec82956fc8da514ddbce42d98957da1b`.
전 명령 로컬 실측(2026-09-07, macOS, Docker Desktop 29.5.3), 출력 전문은 담지 않는다
(evidence 규격). 구현 커밋 8개(`c0dbf8d`~`6a1fe68`) — 목록은 rollback.md.

| # | 명령 | 결과 |
| --- | --- | --- |
| S-0 | `git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)` | SUCCESS — 348 tasks 전부 executed(격리 worktree, 캐시 미재사용) |
| S-1 | `./gradlew --no-build-cache clean check` | SUCCESS |
| S-2 | `./gradlew :adapters:test --tests 'bidvector.adapters.persistence.*'` | SUCCESS — test class 9종 전부 통과(아래 표) |
| S-3 | `./gradlew :adapters:test --tests '*PrecedenceMutationTest*'` | SUCCESS — 6 test(애플리케이션 역할·superuser 직접 SQL 우회 시도, 존재 가드, CHECK, Kotlin 사전 필터) |
| S-4 | `./gradlew :adapters:test --tests '*ItemAtomicityTest*'` | SUCCESS — 3 test(항목 원자성 2건 + collection_run 별도 트랜잭션 1건) |
| S-5 | `./gradlew :adapters:test --tests '*CleanMigrationTest*'` | SUCCESS — 4 test(테이블·트리거 목록 대조, flyway validate, 권한) |
| S-6 | `./gradlew :adapters:moduleDependencyGate` | SUCCESS |
| S-7 | `./gradlew qualityBaseline`(및 위 `check`에 포함된 전 게이트) | SUCCESS |

## S-2 test class 9종 전건(persistence 패키지)

| class | test 수 | 요지 |
| --- | --- | --- |
| `CleanMigrationTest` | 4 | S-5 — 테이블·트리거 목록 대조, flyway validate, 권한 |
| `PrecedenceMutationTest` | 6 | S-3 — 점유 가드·존재 가드·CHECK 우회 거부, Kotlin 사전 필터 |
| `ItemAtomicityTest` | 3 | S-4 — 항목 원자성, collection_run 별도 트랜잭션 |
| `NoticeVersioningTest` | 3 | ③ 멱등·revision+audit·OPEN-DIC-06 |
| `RawAppendOnlyTest` | 6 | ② raw·notice_audit append-only 두 겹 방어 |
| `ProvenanceAuthoritySeedTest` | 1 | DB seed ↔ `IS_AUTHORITATIVE` 완전 일치 |
| `OpeningQualificationRepositoryTest` | 2 | opening_result·qualification_text 최신 관측 우선 upsert |
| `PersistenceAdapterDependencyTest` | 1 | persistence 패키지 domain import 경계 |
| **합계** | **26** | 전건 PASS, 0 skipped |

`procurement`: `ObservationKeyTest` 5 test 추가(전건 PASS) — `AccountingTest` 등 3A 기존
test는 무변경 통과(procurement 그 밖 파일 편집 없음, S-6과 `moduleDependencyGate` 실측이
project 의존·금지 group 위반 없음을 함께 확인).

## Docker 부재 시 붉음 — 실측과 한계

**시도**: `DOCKER_HOST=tcp://127.0.0.1:1`로 무효화 + `--no-daemon --rerun-tasks`로 강제
재실행. **관측**: 이 머신의 `~/.testcontainers.properties`가
`docker.client.strategy=UnixSocketClientProviderStrategy`를 캐시해 `DOCKER_HOST`를
무시하고 실제 Docker 소켓(Docker Desktop, `~/.docker/run/docker.sock`)으로 접속에
성공했다 — 그 캐시 파일을 백업 후 삭제하고 재시도해도 Testcontainers의 전략 열거가
환경변수 실패 뒤 **동일 머신의 실제 소켓 경로를 다시 찾아** 성공한다(설계상 다중 전략
fallback). **진짜 "Docker 완전 부재"를 재현하려면 사용자의 살아 있는 Docker Desktop을
정지시키거나 소켓 파일을 옮겨야 하는데, 이는 이 실험 범위를 넘는 공유 개발 머신
훼손이라 실행하지 않았다.**

**대신 코드 검사로 "SKIP이 아니라 실패"를 확인한다**:
`PersistenceTestSupport`는 `@Testcontainers(disabledWithoutDocker = false)`(기본값 자체가
false — 명시로 재확정)이고 companion object `init` 블록이 `PostgreSQLContainer(...).start()`
를 **`assumeTrue`·`try/catch`로 감싸지 않고 직접 호출**한다 — `start()`가 예외를 던지면
그 예외가 클래스 초기화 실패(`ExceptionInInitializerError`)로 전파돼 그 클래스의 **모든**
test가 오류로 표시된다(스킵이 아니다). 이 경로는 `assumeTrue`를 어디에도 쓰지 않는다는
grep 결과(`grep -rn assumeTrue adapters/src/test/kotlin/bidvector/adapters/persistence/` →
0건)로도 뒷받침된다. **알려진 제한(evidence)**: 실제 "Docker 없음" 조건에서의 실측
실패 확인은 이번 라운드에서 완결하지 못했다 — 검증 레인이 Docker daemon을 stop한
격리 환경(별도 CI runner 또는 컨테이너 없는 sandbox)에서 재확인하는 것을 권고한다.

## 부가 검증(evidence-pack 규격)

| 검사 | 명령 요지 | 결과 |
| --- | --- | --- |
| clean-tree 게이트(개별 pathspec) | `git status --porcelain -- <in_scope 경로 9개 개별 인자>` | 빈 출력(clean) — 양성 대조: 파일 하나를 일부러 더럽힌 뒤 같은 명령으로 감지·되돌림 확인 |
| secret 스캔 | `grep -rn "PGPASSWORD\|password.*=.*['\"]" adapters/src/main/kotlin/bidvector/adapters/persistence/` | main 소스 0건. `bidvector_admin`/`bidvector_test_only`는 test 소스셋의 Testcontainers 컨테이너 기본값(`PersistenceTestSupport.kt`)뿐 — `bidvector_app` role은 NOLOGIN(비밀번호 없음, migration KDoc) |
| Docker 버전 | `docker --version` | `Docker version 29.5.3, build d1c06ef` |
| 컨테이너 이미지 태그 | `PersistenceTestSupport.POSTGRES_IMAGE = "postgres:16.4"` | 코드 상수(정책 데이터 아님), 출처 KDoc |
| 역방향 좌표 파급 | `grep -rn "V1__schema.sql:[0-9]\|JdbcNoticeRepository.kt:[0-9]\|gate-tests.properties:[0-9]" **/*.md` | 0건 — 이 slice가 편집한 파일을 `file:line`으로 가리키는 문서 없음 |
| rollback 실행 가능성 | 임시 clone에서 `rollback.md`의 명령 실행 후 `./gradlew --no-build-cache clean check` | 아래 rollback.md 「실측」 절 |

## 하네스 레인 변경 확인

`git log --oneline 01ecbba..HEAD -- CLAUDE.md .claude/` — **없음**. `884bb91`(base 직후,
3D 착수 문서 커밋)은 `milestone-3.md`·`reports/evidence/m3/3d/scope.md`만 건드리고
`CLAUDE.md`·`.claude/`는 건드리지 않아 하네스 레인 밖이다(구현 착수 이전에 이미 병합된
문서 레인 커밋 — 이 구현 레인이 만든 것이 아니다).
