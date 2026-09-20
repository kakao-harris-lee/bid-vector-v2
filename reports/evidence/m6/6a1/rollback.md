# M6/6A-1 rollback.md

실측 HEAD: `440a7286`(수정 라운드 1의 마지막 산출물 커밋 — `docs(m6-6a1): rollback.md —
F-4·F-5 시정 + D-6A1-33·34 근거 + jarContentGate 신규 등재`. 이 뒤에도 같은 라운드의
평가 보고 커밋이 더 있을 수 있으나, in_scope 코드 경로를 건드리지 않는 evidence 전용
편집이라 아래 clone 실측의 유효성에 영향이 없다 — 검증자는 판정 SHA와의 사이에 코드
경로 diff가 비었는지로 이 사실을 직접 확인할 수 있다).

기준(`base`)은 **고정 SHA가 아니라 정의**다: 「이 브랜치가 분기해 나온 현재 `main`」
(`git merge-base HEAD origin/main`, D-6A1-15). 이 정의는 `origin/main`이 계속 앞으로
움직여도 안정적이다 — 실측: 이 라운드 도중 `origin/main`이 `1881c82c` → `00d49135`
(하네스 문서 정정 커밋 하나)로 다시 전진했지만, `1881c82c`가 그 뒤로도 여전히 조상이라
`git merge-base HEAD origin/main`은 그대로 **`1881c82c`**를 낸다(재확인 실측, F-5가
고치려던 「고정 SHA는 이런 전진에 낡는다」의 정반대 증거 — 정의는 낡지 않는다).
흡수 전 초기 실측값은 `128f9cd3` = scope.md `base_sha`(「출발한 지점」이라는 역사적
사실로 scope.md에는 그대로 둔다, 팀장 지시).

## 되돌리는 것

이 slice는 새 Spring Boot 기동 경로(`main()`)·HTTP 필터 둘·controller 하나·조립
(`PersistenceWiring`)·audit 저장소·게이트 두 벌·마이그레이션(V15)·OpenAPI 계약 파일을
신설했다. 운영 중인 시스템은 아직 없으므로(이 slice가 저장소 최초 진입점) 되돌림은
**코드 철회**로 충분하다 — feature flag나 별도 비활성화 스위치가 필요 없다(D-6A1-4,
실행 경로가 없는 읽기 하나뿐).

## 목록(기계 산출, 라운드마다 재산출)

**F-4 시정 — 이전 판이 in_scope `milestone-6.md`를 명령·목록 양쪽에서 빠뜨렸다**(생성
명령 범위 밖이라 아래 유효성 확인 명령도 같은 이유로 놓쳤다). 아래는 그 파일을 포함한다.

```
git diff --name-status $(git merge-base HEAD origin/main)..HEAD -- app/ adapters/ \
  gradle/libs.versions.toml openapi/ config/quality/gate-tests.properties \
  milestone-6.md | grep -v '^.\treports/evidence/'
```

신규(A, 19개) — `restore`가 아니라 삭제 대상:
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
- `app/src/test/kotlin/bidvector/app/http/ProductionAssemblyAuthAuditTest.kt` — 수정 라운드
  1 신설(D-6A1-27, production 조립 boot test)
- `app/src/test/kotlin/bidvector/app/http/RequestAuditFilterTest.kt`
- `openapi/bidvector-operator-api.yaml`

수정(M, 10개, **전부 공유 파일**):
- `adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt`
- `adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationColumnTest.kt`
- `adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationPrivilegeTest.kt`
- `adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTest.kt`
- `adapters/src/test/kotlin/bidvector/adapters/persistence/PersistenceTestSupport.kt`
- `app/build.gradle.kts`
- `app/src/test/kotlin/bidvector/app/compatibility/BootCompatibilitySmokeTest.kt`
- `config/quality/gate-tests.properties`
- `gradle/libs.versions.toml`
- `milestone-6.md` — F-4가 놓쳤던 자리(팀장 레인, 6A 분할·착수 문단)

## 공유 파일 — base를 흡수 뒤 지점으로 잡았으므로 hunk 격리가 필요 없다

`CleanMigrationCheckTest.kt`·`CleanMigrationColumnTest.kt`는 6F-4(`notice_title`,
`V14`)와 실제로 겹쳤다 — 하지만 **base를 `1881c82c`(6F-4가 이미 반영된 지점)로
잡았기 때문에**, 그 파일들을 `1881c82c` 상태로 `restore`하면 6F-4의 내용은 그대로
남고 **이 slice가 그 위에 더한 hunk만** 사라진다. 커밋별 hunk 격리(`git apply -R`)가
필요한 경우는 **다른 slice가 아직 병합되지 않은 채 같은 파일을 동시에 편집 중일 때**
뿐인데, 지금은 6F-4가 이미 병합 완료 상태로 base 안에 들어와 있어 해당하지 않는다.

## 복원 명령

**F-5 시정 — 실행 블록이 `--source=1881c82c` 고정 SHA였다**(D-6A1-15 문면은 정의인데
실행이 고정이었다 — 서술과 실행이 갈리면 실행이 이긴다, 팀장 지시). 아래는 매 실행마다
정의를 재계산한다(파괴적 명령보다 아래에 경고를 두지 않는다 — 절차 자체를 고쳤다).

```bash
BASE=$(git merge-base HEAD origin/main)

git restore --source="$BASE" --staged --worktree -- \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationColumnTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationPrivilegeTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/PersistenceTestSupport.kt \
  app/build.gradle.kts \
  app/src/test/kotlin/bidvector/app/compatibility/BootCompatibilitySmokeTest.kt \
  config/quality/gate-tests.properties \
  gradle/libs.versions.toml \
  milestone-6.md

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
  app/src/test/kotlin/bidvector/app/http/ProductionAssemblyAuthAuditTest.kt \
  app/src/test/kotlin/bidvector/app/http/RequestAuditFilterTest.kt \
  openapi/bidvector-operator-api.yaml
```

`bootJar`는 다시 `enabled = false`로 돌아간다(이번 라운드에서 `jar`를 재활성했으므로
`jar`도 함께 `enabled = false`로 — D-6A1-28 이전 상태 원복). `CLAUDE.md`·`.claude/**`
(하네스 경로)는 되돌리지 않는다 — 실측: `git diff --stat "$BASE"..HEAD -- CLAUDE.md
.claude/ docs/harness/` 빈 출력.

## 마이그레이션 비대칭(적용 완료 DB가 있는 경우)

적용된 DB에는 `api_request_audit` 표(V15)가 남는다 — 되돌린 코드는 그 표를 쓰지
않을 뿐 삭제하지 않는다. 실제 `DROP TABLE`은 사용자 승인 대상이다(운영 DB에 아직
적용된 이력이 없다).

## 임시 clone 실측(①~⑥, 2026-09-20, 수정 라운드 1 — F-4·F-5·D-6A1-27 반영 뒤 재실측)

`git clone --no-hardlinks`로 격리된 clone(`440a7286` 기준)에서 위 복원 명령을 실제로
실행했다.

| 단계 | 명령 | 결과 |
| --- | --- | --- |
| ① 명령 exit | `git restore` + `git rm` | 둘 다 exit 0 |
| ② D/M 수 | `git status --porcelain` | D 19 · M 10(위 목록과 정확히 일치, F-6 시정 — 이전
  판 「D 17」·verifier 재현 「D 18」은 둘 다 이번 라운드 신설 파일 반영 전 수치였다) |
| ③ diff 빈 것 | `git diff --stat "$BASE" -- <위 29개 경로 전부>` | 빈 출력(exit 0) |
| — 하네스 무편집 | `git diff --stat "$BASE"..HEAD -- CLAUDE.md .claude/ docs/harness/` | 빈 출력 |
| ④ compile | `./gradlew --no-daemon :app:compileKotlin :adapters:compileKotlin` | BUILD SUCCESSFUL |
| ⑤ test | `./gradlew --no-daemon check`(test 포함) | BUILD SUCCESSFUL |
| ⑥ 게이트 | 위와 동일 `check`(9개 모듈 전건) | BUILD SUCCESSFUL |

갈음 판정은 「HEAD 초록」이 아니라 **트리 동일성**(③의 빈 diff)으로 확인했다. `$BASE`는
매 실행 시점의 `git merge-base HEAD origin/main`(F-5 시정 그대로 실행).

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
  config/quality/gate-tests.properties gradle/libs.versions.toml openapi \
  milestone-6.md
```
빈 출력이면 실측이 유효하다. 실측 HEAD(`440a7286`)가 판정 SHA의 조상이 아니면 미검증.

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
- **Provenance 라벨 중복(D-6A1-33, 고치지 않는다)**: `ProvenanceCodec`(adapters.persistence,
  모듈 범위 internal)을 `app`에서 재사용하지 못해 `StrategyReadController.provenanceLabel`이
  같은 매핑을 다시 갖는다. code-reviewer가 「가시성만 넓히면 제거 가능」이라는 더 값싼
  대안을 냈고 그 지적은 옳다 — 그러나 **표현 계층의 라벨 하나를 위해 공개 표면을 영구히
  넓히는 거래**다. 양쪽 다 sealed 타입에 대한 **소진 `when`**이라 drift는 컴파일러가
  잡는다 — 이것은 **안전한 중복**이고, 그래서 가시성을 넓히지 않기로 결정했다.
- **`spring-boot-test`/`-resttestclient`/`-restclient` 세 test 전용 좌표**는 D-6A1-24로
  in_scope에 정식 편입됐다 — Boot 4.1의 `spring-boot-resttestclient` 선언 의존 그래프에
  `RestTemplateBuilder`가 빠져 있는 것(실측)을 메우는 데 필요했다. production 좌표
  아님, `compatibilitySmoke.expectedModules`에는 안 올림.
- **`OPEN-BYTECODE-GATE-CONST-VAL-BLINDSPOT`(D-6A1-25 신설, D-6A1-34로 범위 확장)**:
  `AuditAdapterDependencyTest`를 검증하며 실측 — `bidvector.adapters.persistence.Sql`의
  `const val` 필드를 참조하면 Kotlin 컴파일러가 값을 인라인해 상수 풀에서 참조 자체가
  사라진다(바이트코드 의존 게이트가 놓치는 자리). 이 slice의 게이트는 오브젝트 참조
  (비-const)로 변이해 실제로 잡히는 것만 확인했다. **D-6A1-34 — 문면을 실측보다 좁게
  적었던 것을 넓힌다**: 미검출은 `const val` 하나가 아니라 **모든 컴파일 시간 상수
  참조**다 — `object` const·top-level const·다른 모듈 companion const 셋 다 미검출임이
  실측됐다(실제 정책 상수로도 재현). 영향 범위는 `evaluation`(6F-2)·`qualification`
  (6F-5-a)·`profile`(6F-6)·`audit`(이 slice) 네 게이트 계열 전부 — 한 slice의 범위가
  아니라 **하네스 레인**이 받는다.
- **`jarContentGate`가 여전히 `jar`만 보고 배포물 `bootJar`는 안 본다(신규 등재,
  OPEN 후보)**: D-6A1-28에서 `jar`를 재활성해 게이트가 다시 뭔가를 재게(entries
  0→20) 했지만, 그 「뭔가」는 여전히 **비배포 아티팩트**(`jar` task 산출물)다.
  `jarContentGate`의 `archives.from(tasks.named("jar")...)` 배선이 build-logic에
  하드코딩돼 있어(`build-logic/src/main/kotlin/bidvector.kotlin-conventions.gradle.kts`),
  게이트가 실제 배포물(`bootJar`)의 class 바이트를 검증하지 못한다. `jar` 재활성은
  게이트가 **뭔가라도** 재게 하는 최소 조치였지 배포물을 재는 조치가 아니었다 —
  하네스 레인 OPEN 후보로 등재한다(가칭 `OPEN-JAR-CONTENT-GATE-BOOTJAR-BLINDSPOT`).
