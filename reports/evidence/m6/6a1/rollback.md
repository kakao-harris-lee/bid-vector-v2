# M6/6A-1 rollback.md

실측 HEAD: `10e53a7c`(이 slice의 마지막 산출물 커밋 — `fix(m6-6a1): 전건 check 통과`).
기준(`base`)은 **고정 SHA가 아니라 정의**다: 「이 브랜치가 분기해 나온 현재 `main`」
(`git merge-base HEAD origin/main`, 착수 시점 실측값 `128f9cd3` = scope.md `base_sha`와
일치, D-6A1-15).

## 되돌리는 것

이 slice는 새 Spring Boot 기동 경로(`main()`)·HTTP 필터 둘·controller 하나·조립
(`PersistenceWiring`)·audit 저장소·마이그레이션(V15)·OpenAPI 계약 파일을 신설했다.
운영 중인 시스템은 아직 없으므로(이 slice가 저장소 최초 진입점) 되돌림은 **코드
철회**로 충분하다 — feature flag나 별도 비활성화 스위치가 필요 없다(D-6A1-4, 실행
경로가 없는 읽기 하나뿐).

## 목록(기계 산출, `git diff --name-status <base>..HEAD` — 라운드마다 재산출)

```
git diff --name-status 128f9cd3..HEAD -- app/ adapters/ gradle/libs.versions.toml \
  openapi/ config/quality/gate-tests.properties | grep -v '^.\treports/evidence/'
```

신규(A, 15개) — `restore`가 아니라 삭제 대상:
- `adapters/src/main/kotlin/bidvector/adapters/audit/ApiAuditSql.kt`
- `adapters/src/main/kotlin/bidvector/adapters/audit/ApiAuditStore.kt`
- `adapters/src/main/resources/db/migration/V15__api_audit.sql`
- `adapters/src/test/kotlin/bidvector/adapters/audit/ApiAuditStoreTest.kt`
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

수정(M, 8개, **전부 공유 파일** — `restore`로 base 상태 복원, 다른 slice의 줄과
섞이지 않는지 아래 「공유 파일」 절 확인):
- `adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt`
- `adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationColumnTest.kt`
- `adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationPrivilegeTest.kt`
- `adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTest.kt`
- `adapters/src/test/kotlin/bidvector/adapters/persistence/PersistenceTestSupport.kt`
- `app/build.gradle.kts`
- `app/src/test/kotlin/bidvector/app/compatibility/BootCompatibilitySmokeTest.kt`
- `config/quality/gate-tests.properties`
- `gradle/libs.versions.toml`

## 공유 파일 — 이 브랜치 안에서는 겹치지 않는다

이 slice는 `m6-6a/2026-09-17` 브랜치 하나에서 단독으로 작업했다(다른 레인이 같은
브랜치에 커밋한 이력이 없다 — `git log --oneline 128f9cd3..HEAD`가 전부 이 slice의
커밋 넷뿐). 위 8개 수정 파일은 **다른 slice와 병렬로 겹칠 수 있는 파일**(6F-4가
`gate-tests.properties`를 동시에 늘리는 중, `CleanMigration*Test.kt`류는 새 표를
추가하는 모든 slice가 건드림)이지만, **이 브랜치의 `base..HEAD` 범위 안에서는** 그
겹침이 아직 일어나지 않았다(병합 전). 따라서 이 rollback은 `git restore
--source=<base>` 단순 복원으로 충분하다 — hunk 격리(커밋별 `git apply -R`)가 필요한
것은 **병합 뒤** 시점이며, 그때는 병합을 수행한 쪽(팀장 레인)이 병합 커밋 기준으로
다시 재현해야 한다(이 문서가 커버하는 범위 밖).

## 복원 명령

```bash
git restore --source=128f9cd3 --staged --worktree -- \
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

`bootJar`는 다시 `enabled = false`로 돌아간다(`app/build.gradle.kts` base 상태).
`CLAUDE.md`·`.claude/**`(하네스 경로)는 되돌리지 않는다 — 이 range에 해당 경로 편집이
없다(실측: `git diff --stat 128f9cd3..HEAD -- CLAUDE.md .claude/ docs/harness/` 빈 출력).

## 마이그레이션 비대칭(적용 완료 DB가 있는 경우)

적용된 DB에는 `api_request_audit` 표(V15)가 남는다 — 되돌린 코드는 그 표를 쓰지
않을 뿐 삭제하지 않는다. 실제 `DROP TABLE`은 사용자 승인 대상이다(운영 DB에 아직
적용된 이력이 없다 — 이 slice는 로컬 테스트 컨테이너에서만 migrate를 실행했다).

## 임시 clone 실측(①~⑥, 2026-09-19)

`git clone --no-hardlinks`로 격리된 clone에서 위 복원 명령을 실제로 실행했다(dry-run
아님):

| 단계 | 명령 | 결과 |
| --- | --- | --- |
| ① 명령 exit | `git restore` + `git rm` | 둘 다 exit 0 |
| ② D/M 수 | `git status --porcelain` | D 15 · M 8(위 목록과 정확히 일치) |
| ③ diff 빈 것 | `git diff --stat 128f9cd3 -- <위 23개 경로 전부>` | 빈 출력(exit 0) |
| — 하네스 무편집 | `git diff --stat 128f9cd3 -- CLAUDE.md .claude/ docs/harness/` | 빈 출력 |
| ④ compile | `./gradlew --no-daemon :app:compileKotlin :adapters:compileKotlin` | BUILD SUCCESSFUL |
| ⑤ test | `./gradlew --no-daemon check`(test 포함) | BUILD SUCCESSFUL |
| ⑥ 게이트 | 위와 동일 `check`(9개 모듈 전건 — lint·detekt·architecture·contract·size·leak-pattern) | BUILD SUCCESSFUL, 346 tasks(197 executed·117 cached·32 up-to-date) |

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
빈 출력이면 실측이 유효하다. 실측 HEAD(`10e53a7c`)가 판정 SHA의 조상이 아니면 미검증.

## 마이그레이션 번호 재확인(D-6A1-12)

`V15`는 착수 시점 정의(「PR 시점 `main` 최대보다 크고 병행 레인 선점 번호를 피한다」,
`main` 최대 `V13` + 6F-4 선점 `V14`)의 산출값이다. **PR 직전에 그 정의로 재확인**한다
— 이 evidence 시점 이후 `main`이 더 전진했거나 다른 병행 레인이 새 번호를 선점했으면
값이 달라질 수 있다(고정 참조가 아니라 정의를 재실행한다).

## 알려진 제한

- **D-6A1-17의 대가**: audit 저장소 장애가 읽기 endpoint 가용성을 죽인다(fail-closed
  선택, 받는 쪽 6E 런북).
- **`ContentCachingResponseWrapper` 메모리 버퍼링**: 대용량·스트리밍 응답이 생기면
  재검토 대상(지금은 endpoint가 읽기 하나뿐이라 응답이 작다, D-6A1-5와 같은 시점에
  재론의).
- **`OPEN-6A1-CONNECTION-POOL`**: `PGSimpleDataSource`(비풀링) 그대로 운영에 나갈 수
  없다 — 받는 쪽 6C/6E.
- **커넥션 풀 부재로 인한 동시성 상한**: 이 slice는 단일 운영자·저동시성 전제만 검증했다.
- **Provenance 라벨 중복**: `ProvenanceCodec`(adapters.persistence, 모듈 범위 internal)을
  `app`에서 재사용하지 못해 `StrategyReadController.provenanceLabel`이 같은 매핑을
  다시 갖는다(재사용 후보였으나 모듈 경계가 막았다 — 사소한 중복, 두 곳 다 sealed
  `when` 소진이라 컴파일이 drift를 잡는다).
- **`spring-boot-test`/`-resttestclient`/`-restclient` 세 test 전용 좌표**는
  scope.md 문면(`gradle/libs.versions.toml # spring-boot-starter-web 좌표 추가만`)
  밖의 추가다 — TestRestTemplate 기본 생성자의 전이 의존 그래프 공백(Boot 4.1
  `spring-boot-resttestclient`의 선언 의존에 `RestTemplateBuilder`가 없음, 실측)을
  메우는 데 필요했다. production 의존이 아니라 test 전용이며, `compatibilitySmoke`의
  `expectedModules`에는 올리지 않았다(그 task는 production 채택 좌표만 잰다).
