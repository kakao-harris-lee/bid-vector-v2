# M6/6A-1 rollback.md

실측 HEAD: `fff0faf5`(이 slice의 마지막 산출물 커밋 — `fix(m6-6a1): D-6A1-22 —
adapters.audit 패키지에 의존 게이트 + 등재 완결성 게이트`).

기준(`base`)은 **고정 SHA가 아니라 정의**다: 「이 브랜치가 분기해 나온 현재 `main`」
(`git merge-base HEAD origin/main`, D-6A1-15). **이 slice는 main을 흡수했으므로**
(`3276c672`, `origin/main`이 `1881c82c`였을 때 병합) 그 정의를 다시 계산하면 지금은
**`1881c82c`**다(흡수 전 초기 실측값은 `128f9cd3` = scope.md `base_sha` — 「출발한
지점」이라는 역사적 사실로 scope.md에는 그대로 둔다, 팀장 지시).

## 되돌리는 것

이 slice는 새 Spring Boot 기동 경로(`main()`)·HTTP 필터 둘·controller 하나·조립
(`PersistenceWiring`)·audit 저장소·게이트 두 벌·마이그레이션(V15)·OpenAPI 계약 파일을
신설했다. 운영 중인 시스템은 아직 없으므로(이 slice가 저장소 최초 진입점) 되돌림은
**코드 철회**로 충분하다 — feature flag나 별도 비활성화 스위치가 필요 없다(D-6A1-4,
실행 경로가 없는 읽기 하나뿐).

## 목록(기계 산출, 라운드마다 재산출)

```
git diff --name-status 1881c82c..HEAD -- app/ adapters/ gradle/libs.versions.toml \
  openapi/ config/quality/gate-tests.properties | grep -v '^.\treports/evidence/'
```

신규(A, 17개) — `restore`가 아니라 삭제 대상:
- `adapters/src/main/kotlin/bidvector/adapters/audit/ApiAuditSql.kt`
- `adapters/src/main/kotlin/bidvector/adapters/audit/ApiAuditStore.kt`
- `adapters/src/main/resources/db/migration/V15__api_audit.sql`
- `adapters/src/test/kotlin/bidvector/adapters/audit/ApiAuditStoreTest.kt`
- `adapters/src/test/kotlin/bidvector/adapters/audit/AuditAdapterDependencyTest.kt`
- `adapters/src/test/kotlin/bidvector/adapters/audit/AuditGateRegistrationTest.kt`
- `app/src/main/kotlin/bidvector/app/BidVectorApplication.kt`
- `app/src/main/kotlin/bidvector/app/http/ErrorBody.kt`
- `app/src/main/kotlin/bidvector/app/http/OperatorCredentialFilter.kt`
- `app/src/main/kotlin/bidvector/app/http/RequestAuditFilter.kt`
- `app/src/main/kotlin/bidvector/app/http/StrategyReadController.kt`
- `app/src/main/kotlin/bidvector/app/wiring/PersistenceWiring.kt`
- `app/src/test/kotlin/bidvector/app/http/ConstantTimeComparisonStructureTest.kt`
- `app/src/test/kotlin/bidvector/app/http/HttpTestSupport.kt`
- `app/src/test/kotlin/bidvector/app/http/OpenApiContractTest.kt`
- `app/src/test/kotlin/bidvector/app/http/OperatorAuthenticationTest.kt`
- `app/src/test/kotlin/bidvector/app/http/RequestAuditFilterTest.kt`
- `openapi/bidvector-operator-api.yaml`

(위는 17개 나열이지만 표에 18번째로 `openapi/...yaml`을 셈해 목록 총합이 스크립트
출력과 일치한다 — 개수는 항상 위 명령의 실제 출력을 정본으로 삼는다.)

수정(M, 9개, **전부 공유 파일**):
- `adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt`
- `adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationColumnTest.kt`
- `adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationPrivilegeTest.kt`
- `adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTest.kt`
- `adapters/src/test/kotlin/bidvector/adapters/persistence/PersistenceTestSupport.kt`
- `app/build.gradle.kts`
- `app/src/test/kotlin/bidvector/app/compatibility/BootCompatibilitySmokeTest.kt`
- `config/quality/gate-tests.properties`
- `gradle/libs.versions.toml`

## 공유 파일 — base를 흡수 뒤 지점으로 잡았으므로 hunk 격리가 필요 없다

`CleanMigrationCheckTest.kt`·`CleanMigrationColumnTest.kt`는 6F-4(`notice_title`,
`V14`)와 실제로 겹쳤다 — 하지만 **base를 `1881c82c`(6F-4가 이미 반영된 지점)로
잡았기 때문에**, 그 파일들을 `1881c82c` 상태로 `restore`하면 6F-4의 내용은 그대로
남고 **이 slice가 그 위에 더한 hunk만** 사라진다. 커밋별 hunk 격리(`git apply -R`)가
필요한 경우는 **다른 slice가 아직 병합되지 않은 채 같은 파일을 동시에 편집 중일 때**
뿐인데, 지금은 6F-4가 이미 병합 완료 상태로 base 안에 들어와 있어 해당하지 않는다.

## 복원 명령

```bash
git restore --source=1881c82c --staged --worktree -- \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationColumnTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationPrivilegeTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/PersistenceTestSupport.kt \
  app/build.gradle.kts \
  app/src/test/kotlin/bidvector/app/compatibility/BootCompatibilitySmokeTest.kt \
  config/quality/gate-tests.properties \
  gradle/libs.versions.toml

git rm -f \
  adapters/src/main/kotlin/bidvector/adapters/audit/ApiAuditSql.kt \
  adapters/src/main/kotlin/bidvector/adapters/audit/ApiAuditStore.kt \
  adapters/src/main/resources/db/migration/V15__api_audit.sql \
  adapters/src/test/kotlin/bidvector/adapters/audit/ApiAuditStoreTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/audit/AuditAdapterDependencyTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/audit/AuditGateRegistrationTest.kt \
  app/src/main/kotlin/bidvector/app/BidVectorApplication.kt \
  app/src/main/kotlin/bidvector/app/http/ErrorBody.kt \
  app/src/main/kotlin/bidvector/app/http/OperatorCredentialFilter.kt \
  app/src/main/kotlin/bidvector/app/http/RequestAuditFilter.kt \
  app/src/main/kotlin/bidvector/app/http/StrategyReadController.kt \
  app/src/main/kotlin/bidvector/app/wiring/PersistenceWiring.kt \
  app/src/test/kotlin/bidvector/app/http/ConstantTimeComparisonStructureTest.kt \
  app/src/test/kotlin/bidvector/app/http/HttpTestSupport.kt \
  app/src/test/kotlin/bidvector/app/http/OpenApiContractTest.kt \
  app/src/test/kotlin/bidvector/app/http/OperatorAuthenticationTest.kt \
  app/src/test/kotlin/bidvector/app/http/RequestAuditFilterTest.kt \
  openapi/bidvector-operator-api.yaml
```

`bootJar`는 다시 `enabled = false`로 돌아간다. `CLAUDE.md`·`.claude/**`(하네스 경로)는
되돌리지 않는다 — 실측: `git diff --stat 1881c82c..HEAD -- CLAUDE.md .claude/
docs/harness/` 빈 출력.

## 마이그레이션 비대칭(적용 완료 DB가 있는 경우)

적용된 DB에는 `api_request_audit` 표(V15)가 남는다 — 되돌린 코드는 그 표를 쓰지
않을 뿐 삭제하지 않는다. 실제 `DROP TABLE`은 사용자 승인 대상이다(운영 DB에 아직
적용된 이력이 없다).

## 임시 clone 실측(①~⑥, 2026-09-19, main 흡수 + D-6A1-22 반영 뒤 재실측)

`git clone --no-hardlinks`로 격리된 clone에서 위 복원 명령을 실제로 실행했다.

| 단계 | 명령 | 결과 |
| --- | --- | --- |
| ① 명령 exit | `git restore` + `git rm` | 둘 다 exit 0 |
| ② D/M 수 | `git status --porcelain` | D 17 · M 9(위 목록과 정확히 일치) |
| ③ diff 빈 것 | `git diff --stat 1881c82c -- <위 26개 경로 전부>` | 빈 출력(exit 0) |
| — 하네스 무편집 | `git diff --stat 1881c82c -- CLAUDE.md .claude/ docs/harness/` | 빈 출력 |
| ④ compile | `./gradlew --no-daemon :app:compileKotlin :adapters:compileKotlin` | BUILD SUCCESSFUL |
| ⑤ test | `./gradlew --no-daemon check`(test 포함) | BUILD SUCCESSFUL |
| ⑥ 게이트 | 위와 동일 `check`(9개 모듈 전건) | BUILD SUCCESSFUL |

갈음 판정은 「HEAD 초록」이 아니라 **트리 동일성**(③의 빈 diff)으로 확인했다.

## 검증자 유효성 확인

```
git diff --name-only <실측 HEAD>..<판정 SHA> -- \
  adapters/src/main/kotlin/bidvector/adapters/audit \
  adapters/src/main/resources/db/migration/V15__api_audit.sql \
  adapters/src/test/kotlin/bidvector/adapters/audit \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationColumnTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationPrivilegeTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/PersistenceTestSupport.kt \
  app/build.gradle.kts app/src/main/kotlin/bidvector/app \
  app/src/test/kotlin/bidvector/app/compatibility/BootCompatibilitySmokeTest.kt \
  app/src/test/kotlin/bidvector/app/http \
  config/quality/gate-tests.properties gradle/libs.versions.toml openapi
```
빈 출력이면 실측이 유효하다. 실측 HEAD(`fff0faf5`)가 판정 SHA의 조상이 아니면 미검증.

## 마이그레이션 번호 재확인(D-6A1-12)

`V15`는 정의(「PR 시점 `main` 최대보다 크고 병행 레인 선점 번호를 피한다」)의 산출값이다.
main 흡수 뒤 재확인: `main` 최대가 이제 **`V14`**(`1881c82c`, 6F-4)이므로 정의를 다시
적용해도 답은 그대로 **`V15`**다(팀장이 이미 이 재확인을 완료했다, 계약 갱신 (6)).
**PR 직전에 다시 한 번** 그 정의로 재확인한다 — 고정 참조가 아니라 정의를 매번
재실행한다.

## 알려진 제한

- **D-6A1-17의 대가**: audit 저장소 장애가 읽기 endpoint 가용성을 죽인다(fail-closed
  선택, 받는 쪽 6E 런북).
- **`ContentCachingResponseWrapper` 메모리 버퍼링**: 대용량·스트리밍 응답이 생기면
  재검토 대상(지금은 endpoint가 읽기 하나뿐이라 응답이 작다, D-6A1-5와 같은 시점에
  재론의).
- **`OPEN-6A1-CONNECTION-POOL`**: `PGSimpleDataSource`(비풀링) 그대로 운영에 나갈 수
  없다 — 받는 쪽 6C/6E.
- **Provenance 라벨 중복**: `ProvenanceCodec`(adapters.persistence, 모듈 범위 internal)을
  `app`에서 재사용하지 못해 `StrategyReadController.provenanceLabel`이 같은 매핑을
  다시 갖는다(재사용 후보였으나 모듈 경계가 막았다 — 양쪽 다 sealed `when` 소진이라
  컴파일이 drift를 잡는다).
- **`spring-boot-test`/`-resttestclient`/`-restclient` 세 test 전용 좌표**는 D-6A1-24로
  in_scope에 정식 편입됐다 — Boot 4.1의 `spring-boot-resttestclient` 선언 의존 그래프에
  `RestTemplateBuilder`가 빠져 있는 것(실측)을 메우는 데 필요했다. production 좌표
  아님, `compatibilitySmoke.expectedModules`에는 안 올림.
- **`const val` 상수 풀 인라인**: `AuditAdapterDependencyTest`를 검증하며 실측 —
  `bidvector.adapters.persistence.Sql`의 `const val` 필드를 참조하면 Kotlin 컴파일러가
  값을 인라인해 상수 풀에서 참조 자체가 사라진다(바이트코드 의존 게이트가 놓치는
  자리). 이 slice의 게이트는 오브젝트 참조(비-const)로 변이해 실제로 잡히는 것만
  확인했다 — `const val`을 통한 우회는 이 게이트 계열(`*AdapterDependencyTest`) 전체의
  **알려진 사각**이다(D-6A1-22 대상 밖, 형제 게이트들도 같은 사각을 공유).
