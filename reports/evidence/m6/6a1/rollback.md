# M6/6A-1 rollback.md

**실측 HEAD(수정 라운드 4, 최신): `561d8544`**(`OperatorCredentialProperties.toString()`
평문 노출 정정의 마지막 산출물 커밋). 이하 「임시 clone 실측(①~⑥, 라운드 4)」절이 이
HEAD에서 새로 낸 측정이고, 아래 라운드 1~3 절은 **그 시점의 유효한 기록으로 보존**한다
(앞 라운드 실측을 옮기지 않는다).

---

**실측 HEAD(수정 라운드 3): `92f8f682`**(D-6A1-44 — `OpenApiContractTest` 순회 루트를
문서 전체로 넓힌 마지막 산출물 커밋. D-6A1-43은 `01c8b005`·`69f8d466`·`06434fed` 세 커밋).

---

실측 HEAD(수정 라운드 2): `9db7da36`(같은 라운드 — evidence 커밋 `docs(m6-6a1): D-6A1-40 evidence +
S-20 CI 확인으로 상태 갱신`). **L-4 시정 계승 — 라벨을 정확히 가른다**: 이 라운드의
**마지막 코드(test 파일) 커밋**은 `88fb33e1`(D-6A1-40 — `ProductionAssemblyAuthAuditTest`가
audit 행을 직접 단언)이고, `9db7da36`는 그 뒤에 온 evidence 전용 커밋(`commands.md`만
편집)이다. 아래 clone 실측은 `9db7da36`에서 돌렸다 — `git diff --name-only
88fb33e1..9db7da36`가 `reports/evidence/m6/6a1/commands.md` **한 줄뿐**임을 확인해
in_scope 코드 경로가 둘 사이에서 전혀 움직이지 않았음을 실측으로 보장했다.

(이전 실측: `f5e39a3a`/마지막 코드 커밋 `d9708f4a` — D-6A1-37·38 반영 직후. 그 사이
verifier 레인 B가 새 HIGH(D-6A1-40)를 냈고, 팀장 지시로 `ProductionAssemblyAuthAuditTest`
에 audit 행 단언을 더한 뒤 이 라운드 안에서 실측 HEAD를 갱신했다 — 라운드를 새로 세지
않는다, 같은 verifier r2 판정 사이클의 연속이다.)

기준(`base`)은 **고정 SHA가 아니라 정의**다: 「이 브랜치가 분기해 나온 현재 `main`」
(`git merge-base HEAD origin/main`, D-6A1-15). 이 정의는 `origin/main`이 계속 앞으로
움직여도, 그 전진을 흡수한 뒤에도 **그 시점의 올바른 값**을 낸다. 이 라운드에서 두 번
실측했다 — `origin/main`이 `1881c82c` → `00d49135`로 전진한 직후(미흡수)에는 `1881c82c`를,
그 커밋을 흡수한 뒤에는 `00d49135`를 냈다. **둘 다 그 시점의 정답이다.** F-5가 고치려던
「고정 SHA는 이런 전진에 낡는다」의 정반대 증거다.

**그 옆에서 산문이 먼저 낡았다(사실 기록).** 이 절의 앞선 판은 위 두 실측 중 **앞의 것만**
보고 「merge-base는 그대로 `1881c82c`를 낸다」고 **숫자를 박아** 적었다. 흡수 직후 그
문장은 거짓이 됐다 — **실행문(`$(git merge-base …)`)은 살아남고 그것을 설명하던 산문이
죽었다.** F-5가 고친 교훈이 한 층 위에서 그대로 재현된 자리라, 지우지 않고 남긴다.
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

신규(A, 21개, 수정 라운드 3에서 `OperatorCredentialTest.kt` 추가·수정 라운드 4에서
`OperatorCredentialPropertiesTest.kt` 추가) — `restore`가 아니라 삭제 대상:
- `adapters/src/main/kotlin/bidvector/adapters/audit/ApiAuditSql.kt`
- `adapters/src/main/kotlin/bidvector/adapters/audit/ApiAuditStore.kt`
- `adapters/src/main/resources/db/migration/V15__api_audit.sql`
- `adapters/src/test/kotlin/bidvector/adapters/audit/ApiAuditStoreTest.kt`
- `adapters/src/test/kotlin/bidvector/adapters/audit/AuditAdapterDependencyTest.kt`
- `adapters/src/test/kotlin/bidvector/adapters/audit/AuditGateRegistrationTest.kt`
- `app/src/main/kotlin/bidvector/app/BidVectorApplication.kt`
- `app/src/test/kotlin/bidvector/app/OperatorCredentialPropertiesTest.kt` — 수정 라운드 4
  신설(`OperatorCredentialProperties.toString()` 평문 노출 정정의 회귀 test)
- `app/src/main/kotlin/bidvector/app/http/ErrorBody.kt`
- `app/src/main/kotlin/bidvector/app/http/OperatorCredentialFilter.kt`
- `app/src/main/kotlin/bidvector/app/http/RequestAuditFilter.kt`
- `app/src/main/kotlin/bidvector/app/http/StrategyReadController.kt`
- `app/src/main/kotlin/bidvector/app/wiring/PersistenceWiring.kt`
- `app/src/test/kotlin/bidvector/app/http/ConstantTimeComparisonStructureTest.kt`
- `app/src/test/kotlin/bidvector/app/http/HttpTestSupport.kt`
- `app/src/test/kotlin/bidvector/app/http/OpenApiContractTest.kt`
- `app/src/test/kotlin/bidvector/app/http/OperatorAuthenticationTest.kt`
- `app/src/test/kotlin/bidvector/app/http/OperatorCredentialTest.kt` — 수정 라운드 3 신설
  (D-6A1-43, `OperatorCredential`의 (2b) 값 획득 축 실측)
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
  app/src/test/kotlin/bidvector/app/OperatorCredentialPropertiesTest.kt \
  app/src/main/kotlin/bidvector/app/http/ErrorBody.kt \
  app/src/main/kotlin/bidvector/app/http/OperatorCredentialFilter.kt \
  app/src/main/kotlin/bidvector/app/http/RequestAuditFilter.kt \
  app/src/main/kotlin/bidvector/app/http/StrategyReadController.kt \
  app/src/main/kotlin/bidvector/app/wiring/PersistenceWiring.kt \
  app/src/test/kotlin/bidvector/app/http/ConstantTimeComparisonStructureTest.kt \
  app/src/test/kotlin/bidvector/app/http/HttpTestSupport.kt \
  app/src/test/kotlin/bidvector/app/http/OpenApiContractTest.kt \
  app/src/test/kotlin/bidvector/app/http/OperatorAuthenticationTest.kt \
  app/src/test/kotlin/bidvector/app/http/OperatorCredentialTest.kt \
  app/src/test/kotlin/bidvector/app/http/ProductionAssemblyAuthAuditTest.kt \
  app/src/test/kotlin/bidvector/app/http/RequestAuditFilterTest.kt \
  openapi/bidvector-operator-api.yaml
```

`bootJar`는 다시 `enabled = false`로 돌아간다. **사실 정정(라운드 2 rollback 드릴에서
발견)** — `jar`는 `enabled = false`로 돌아가지 **않는다**: `base`(`1A`가 남긴 상태)가
이미 `tasks.named<Jar>("jar") { enabled = true }`를 갖고 있어(실측:
`git show "$BASE":app/build.gradle.kts`), D-6A1-28이 되살린 `jar` 활성은 **base와
같은 값으로 돌아가는 것**이지 base를 벗어나는 값이 아니다. `restore`가 만드는 순변화는
`bootJar`(`true`→`false`) **하나**다 — 이전 판의 「jar도 함께 enabled = false로」는
base를 직접 확인하지 않고 「이번 라운드에 잠깐 껐다 켰다」는 이력만 보고 쓴 추측이었다.
`CLAUDE.md`·`.claude/**`(하네스 경로)는 되돌리지 않는다 — 실측: `git diff --stat
"$BASE"..HEAD -- CLAUDE.md .claude/ docs/harness/` 빈 출력.

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

## 임시 clone 실측(①~⑥, 2026-09-23, 수정 라운드 2 — D-6A1-37·38 반영 뒤 재실측)

`git clone --no-hardlinks`로 격리된 clone(`f5e39a3a` 기준, 이 라운드의 마지막 산출물
커밋은 `d9708f4a` — 위 L-4 정정 참고)에서 위 복원 명령을 다시 실제로 실행했다. 목록
자체는 라운드 1과 **파일 집합이 동일**하다(이번 라운드는 기존 test 파일 둘의 내용만
바꿨을 뿐 신규·삭제 파일이 없다 — D 19·M 10 그대로).

| 단계 | 명령 | 결과 |
| --- | --- | --- |
| ① 명령 exit | `git restore` + `git rm` | 둘 다 exit 0 |
| ② D/M 수 | `git status --porcelain \| awk '{print $1}' \| sort \| uniq -c` | D 19 · M 10(라운드 1과 동일, 목록과 일치) |
| ③ diff 빈 것 | `git diff --stat "$BASE" -- <위 29개 경로 전부>` | 빈 출력(exit 0) |
| — 하네스 무편집 | `git diff --stat "$BASE"..HEAD -- CLAUDE.md .claude/ docs/harness/` | 빈 출력 |
| ④ compile | `./gradlew --no-daemon :app:compileKotlin :adapters:compileKotlin` | BUILD SUCCESSFUL(FROM-CACHE 다수) |
| ⑤⑥ test·게이트 | `./gradlew --no-daemon check`(9개 모듈 전건) | BUILD SUCCESSFUL(346 tasks, 197 executed·117 from cache·32 up-to-date) |
| 부가 확인 | `grep -c notice_title CleanMigrationCheckTest.kt CleanMigrationColumnTest.kt` | `4`·`1`(6F-4 줄 보존, 라운드 1과 동일) |
| 부가 확인 | `grep -c api_request_audit` 같은 두 파일 | `0`·`0`(이 slice의 줄이 실제로 사라짐) |
| 부가 확인 | `app/build.gradle.kts`의 `bootJar`/`jar` `enabled` 값 | `bootJar=false`(원복) · `jar=true`(base와 동일 — 위 사실 정정 참고) |

갈음 판정은 「HEAD 초록」이 아니라 **트리 동일성**(③의 빈 diff)으로 확인했다. `$BASE`는
실행 시점의 `git merge-base HEAD origin/main` = `00d49135`(라운드 1과 동일 — `main`이
이 라운드 사이 전진하지 않았다). clone은 실측 직후 삭제했다.

## 임시 clone 실측(①~⑥, 2026-09-23, D-6A1-40 반영 뒤 최종 재실측)

verifier r2 레인 B가 낸 새 HIGH(D-6A1-40, `ProductionAssemblyAuthAuditTest`가 audit
행을 직접 단언하도록)를 반영한 뒤 `git clone --no-hardlinks`로 격리된 clone(`9db7da36`
기준, 마지막 산출물 커밋 `88fb33e1`)에서 위 복원 명령을 다시 실행했다. 목록은
**변화 없음**(이번에도 기존 test 파일 하나의 내용만 늘었을 뿐 신규·삭제 파일 없음 —
D 19·M 10 그대로).

| 단계 | 명령 | 결과 |
| --- | --- | --- |
| ① 명령 exit | `git restore` + `git rm` | 둘 다 exit 0 |
| ② D/M 수 | `git status --porcelain \| awk '{print $1}' \| sort \| uniq -c` | D 19 · M 10 |
| ③ diff 빈 것 | `git diff --stat "$BASE" -- <위 29개 경로 전부>` | 빈 출력(exit 0) |
| — 하네스 무편집 | `git diff --stat "$BASE"..HEAD -- CLAUDE.md .claude/ docs/harness/` | 빈 출력 |
| ④ compile | `./gradlew --no-daemon :app:compileKotlin :adapters:compileKotlin` | BUILD SUCCESSFUL(FROM-CACHE 다수) |
| ⑤⑥ test·게이트 | `./gradlew --no-daemon check`(9개 모듈 전건) | BUILD SUCCESSFUL(346 tasks, 197 executed·117 from cache·32 up-to-date) |
| 부가 확인 | `grep -c notice_title` 두 CleanMigration test | `4`·`1`(6F-4 줄 보존) |
| 부가 확인 | `grep -c api_request_audit` 같은 두 파일 | `0`·`0`(이 slice 줄 소거) |

`$BASE` = `00d49135`(불변 — `main`이 이 세 라운드 사이 전진하지 않았다). clone은
실측 직후 삭제했다.

## 임시 clone 실측(①~⑥, 2026-09-23, 수정 라운드 3 — D-6A1-43·44 반영 뒤 최종 재실측)

`git clone --no-hardlinks`로 격리된 clone(`92f8f682` 기준)에서 위 복원 명령을 다시
실행했다. **목록이 이번에는 바뀌었다** — `OperatorCredentialTest.kt`(D-6A1-43의 (2b) 값
획득 축 실측 test) 신설로 삭제 대상이 19 → **20개**, 전체 경로가 29 → **30개**.

| 단계 | 명령 | 결과 |
| --- | --- | --- |
| ① 명령 exit | `git restore` + `git rm` | 둘 다 exit 0 |
| ② D/M 수 | `git status --porcelain \| awk '{print $1}' \| sort \| uniq -c` | **D 20 · M 10**(목록 갱신과 정확히 일치) |
| ③ diff 빈 것 | `git diff --stat "$BASE" -- <위 30개 경로 전부>` | 빈 출력(exit 0) |
| — 하네스 무편집 | `git diff --stat "$BASE"..HEAD -- CLAUDE.md .claude/ docs/harness/` | 빈 출력 |
| ④ compile | `./gradlew --no-daemon :app:compileKotlin :adapters:compileKotlin` | BUILD SUCCESSFUL |
| ⑤⑥ test·게이트 | `./gradlew --no-daemon check`(9개 모듈 전건) | BUILD SUCCESSFUL(346 tasks, 197 executed·117 from cache·32 up-to-date) |
| 부가 확인 | `grep -c notice_title` 두 CleanMigration test | `4`·`1`(6F-4 줄 보존, 변화 없음) |
| 부가 확인 | `grep -c api_request_audit` 같은 두 파일 | `0`·`0`(이 slice 줄 소거) |
| 부가 확인 | `app/build.gradle.kts`의 `bootJar`/`jar` `enabled` 값 | `bootJar=false` · `jar=true`(base와 동일, 변화 없음) |

`$BASE` = `00d49135`(불변 — `main`이 라운드 2 이후 전진하지 않았다). clone은 실측 직후
삭제했다. **(수정 라운드 4가 더 붙어 「최종」 라벨은 아래 절로 넘어간다 — 이 절 자체는
그 시점의 유효한 기록으로 보존한다.)**

## 임시 clone 실측(①~⑥, 2026-09-23, 수정 라운드 4 — verifier r4 finding 넷 반영 뒤 최종 재실측)

`git clone --no-hardlinks`로 격리된 clone(`561d8544` 기준)에서 위 복원 명령을 다시
실행했다. **목록이 이번에도 바뀌었다** — `OperatorCredentialPropertiesTest.kt`
(①의 회귀 test) 신설로 삭제 대상이 20 → **21개**, 전체 경로가 30 → **31개**.

| 단계 | 명령 | 결과 |
| --- | --- | --- |
| ① 명령 exit | `git restore` + `git rm` | 둘 다 exit 0 |
| ② D/M 수 | `git status --porcelain \| awk '{print $1}' \| sort \| uniq -c` | **D 21 · M 10**(목록 갱신과 정확히 일치) |
| ③ diff 빈 것 | `git diff --stat "$BASE" -- <위 31개 경로 전부>` | 빈 출력(exit 0) |
| — 하네스 무편집 | `git diff --stat "$BASE"..HEAD -- CLAUDE.md .claude/ docs/harness/` | 빈 출력 |
| ④ compile | `./gradlew --no-daemon :app:compileKotlin :adapters:compileKotlin` | BUILD SUCCESSFUL |
| ⑤⑥ test·게이트 | `./gradlew --no-daemon check`(9개 모듈 전건) | BUILD SUCCESSFUL(346 tasks, 197 executed·117 from cache·32 up-to-date) |
| 부가 확인 | `grep -c notice_title` 두 CleanMigration test | `4`·`1`(6F-4 줄 보존, 변화 없음) |
| 부가 확인 | `grep -c api_request_audit` 같은 두 파일 | `0`·`0`(이 slice 줄 소거) |
| 부가 확인 | `app/build.gradle.kts`의 `bootJar`/`jar` `enabled` 값 | `bootJar=false` · `jar=true`(base와 동일, 변화 없음) |

`$BASE` = `00d49135`(불변 — `main`이 라운드 3 이후 전진하지 않았다). clone은 실측 직후
삭제했다. **이 실측이 이 slice의 최종 rollback 검증이다** — 이 시점 이후 in_scope 코드
변경은 없다.

## 알려진 제한 추가 — D-6A1-43 잔존(수정 라운드 4에서 정정 — 이전 판 서술이 사실보다 강했다)

**정정 배경(사실 기록).** 수정 라운드 3의 이 절은 잔존 경로를 「raw 문자열 저장을
의도적으로 되살리는 다중 파일 재작성」이라고 적었다. **verifier r4가 단일 파일 4줄로
반증했다** — 그 서술은 **비용을 부풀린 거짓**이었다. 아래가 정정본이다(같은 병 두 축).

**ⓐ 실제 최소 비용 — 단일 파일 4줄, 리플렉션으로 `private` 우회(verifier r4 실측,
이 세션이 독립 재현).** `OperatorCredentialFilter.doFilter` 안에서
```kotlin
val f = OperatorCredential::class.java.getDeclaredField("bytes")
f.isAccessible = true
val expectedRaw = String(f.get(expected) as ByteArray, StandardCharsets.UTF_8)
```
로 `expected`(같은 파일 안의 매개변수)의 `private val bytes`를 꺼내 `String`으로 복원한
뒤 `presented`와 `Objects.equals`(내부적으로 `String.equals`, 단락 비교)로 비교하면
(numstat `4 1`, 신규 파일 없음) **전건 `check`가 BUILD SUCCESSFUL**이다. **타입이
`private`으로 막은 것은 컴파일 시점 접근뿐이고, 런타임 리플렉션(`isAccessible = true`)은
막지 않는다** — 이것이 D-6A1-43의 **진짜 잔존 경로**다. 다중 파일 재작성(3파일 변경으로
raw 문자열을 생성자에 재도입하는 형태, 수정 라운드 3에서 실측)도 여전히 가능하지만
**그것이 최소 비용이 아니다** — 이 절이 이전에 「그것이 유일한 경로」인 것처럼 적어
비용을 실제보다 부풀렸다.

**ⓑ MUT-4(허용 목록 simple name 재사용)도 여전히 열려 있다(정정 — 이전 판이 실행하지
않고 단정했다).** 수정 라운드 3의 이 절 자리는 「1차 방어가 타입으로 옮겨간 뒤엔 숨길
만한 exploitable raw 비교가 없다」고 적고 MUT-4를 실행하지 않았다. **그 단정이 틀렸다.**
verifier r4 대조 실험(단일 변수 = 이름): 이웃 패키지에 `internal.CredentialCheck`라는
이름으로 raw 비교를 심으면 게이트가 **FAILED**(잡음)이지만, **`internal.ErrorBody`(허용
목록의 simple name을 다른 패키지에서 재사용, 별칭 import로 호출)로 심으면 게이트가
**exit 0**(못 잡음). 이 세션이 독립 재현: `bidvector.app.http.internal.ErrorBody`에
`object ErrorBody { fun rawEquals(a: Any?, b: Any?) = a == b }`를 신설(신규 파일) →
**전건 `check` BUILD SUCCESSFUL**. **허용 목록 simple name 재사용은 여전히 열려 있다**
— r3 F-1이 지적한 세 축(이름·위치·허용 목록) 중 이 축은 이번 라운드에도 안 닫혔다.
회귀 그물 게이트(`ConstantTimeComparisonStructureTest`)의 맹점이지, 1차 방어(타입)와는
별개다.

**남는 사실.** D-6A1-43이 실제로 닫은 것은 **verbatim 4형태**(같은 파일 안의 `==`·
`Objects.equals` 치환 — MUT-1·MUT-2로 전건 `check` BUILD FAILED 실측 확인,
`commands.md` 참고)뿐이다. ⓐ(리플렉션)·ⓑ(허용 목록 이름 재사용)는 **둘 다 열려 있고**,
둘 다 하네스 레인 소관 게이트의 맹점이거나 JVM 리플렉션의 근본적 한계라 **이 slice의
코드 수정으로 닫을 수 있는 범위가 아니다**(private을 아무리 강하게 걸어도 리플렉션은
JVM 표준 기능이고, 이름 기반 허용 목록은 구조가 아니라 문자열 비교다). 이 잔존은
`OPEN-6A1-CREDENTIAL-RAW-REINTRODUCTION`(scope.md D-6A1-45)에 담기며, 그 문면도 ⓐⓑ
실측(리플렉션 4줄·허용 목록 simple name 재사용)을 반영해 **팀장 레인이 사실대로
갱신**한다(요청만 남긴다) — 받는 쪽은 이 slice를 이어받는 인증 관련 후속 slice(현재
계획된 것 없음, 필요 시 신설) 또는 하네스 레인(게이트 맹점 자체).

## (2b) 값 획득 축 추가 — `OperatorCredentialProperties`(수정 라운드 4, verifier r4 발견 반영)

D-6A1-43 설계 당시 (2b) 표는 `OperatorCredentialFilter`·`OperatorCredential` 둘만 전수
했고, **자격증명 원문을 들고 있는 두 번째 public 타입**(`OperatorCredentialProperties`,
`@ConfigurationProperties` 바인딩 객체, `BidVectorApplication.kt`)이 누락됐다. 이번
라운드에 전수한다:

| 표면 | 노출 경로 | 값을 꺼낼 수 있는가 | 처분 |
| --- | --- | --- | --- |
| `value: String`(public `val`) | 직접 필드 접근(`properties.value`) — **의도된 경로**, `OperatorCredentialFilter` 조립 지점 한 곳만 읽는다 | 예 — 그러나 이 필드는 애초에 **원문을 전달하는 것이 존재 목적**(환경변수 → 타입 래핑)이라 이것 자체는 위협이 아니다 | 닫는다 — 읽는 지점이 조립 근 하나뿐임을 실측(grep, `commands.md`) |
| `toString()`(수정 전: `data class` 합성, 원문 노출 / 수정 후: `Any.toString()`) | Spring이 기동 실패·바인딩 오류·actuator 환경 노출 등에서 **암묵적으로** 이 객체를 문자열화할 수 있는 모든 경로(로그 포함) | **수정 전 — 예(결함, 이번 라운드에 닫음)**. 수정 후 — 아니오(`Any.toString()`은 필드를 담지 않는다) | 닫는다 — `data class` → `class`로 바꾸고 `OperatorCredentialPropertiesTest`(신규)가 `toString()`이 원문을 담지 않음을 실측으로 잠근다 |
| `copy()`·구조 분해·`equals`/`hashCode`(수정 전: `data class` 자동 생성) | `data class`가 합성하는 전부 — 이 slice 안에서는 실제로 쓰이는 자리가 없었다(grep 확인, 단일 사용처) | 수정 전에는 **잠재적으로 가능**했으나 실제 사용처가 없어 오늘 위협은 아니었다. 수정 후 — 애초에 존재하지 않는다 | 닫는다 — `data class` 제거로 그 표면 자체를 없앴다(사용처 없는 표면은 「경계로 처리」가 아니라 삭제가 맞다) |

**verifier r4가 지적한 누락의 원인(사실 기록)** — D-6A1-43 설계 당시 (2b) 표는
`OperatorCredentialFilter`(경계로 처리, 자격증명 주입 자리)만 새 public 표면으로
꼽았다. `OperatorCredentialProperties`는 **그 라운드 이전부터 이미 존재**하던 타입이라
「이번 라운드가 새로 낸 표면」의 정의(수정 라운드마다 갱신 규율의 대상)에 형식적으로는
안 들지만, **자격증명 원문을 들고 있다는 실질**은 `OperatorCredential`과 같은 층이었다
— (2b) 전수가 「새 표면」이 아니라 「자격증명 원문에 닿는 모든 표면」을 기준으로
갔어야 했다는 것이 이 누락의 교훈이다.

## D-6A1-46 미완 — 문면 정정이 소스 KDoc에만 미쳤다(사실 기록, 수정 라운드 4)

**D-6A1-46**(scope.md, 팀장 결정)은 「`ConstantTimeComparisonStructureTest`의 전칭
주장을 걷고, **위협 모델의 해당 줄도 같이 맞춘다**」고 적었다. 수정 라운드 3에서
구현 레인은 `ConstantTimeComparisonStructureTest.kt`·`OperatorCredentialFilter.kt`의
**소스 KDoc만** 고쳤다 — `scope.md`의 위협 모델 (4)·(2b) 표는 **무편집**으로 남았다
(구현 레인은 `scope.md`를 편집할 수 없는 레인 경계이기도 하다). **이 문서(rollback.md)를
사실에 맞춘다** — 위 「알려진 제한 추가 — D-6A1-43 잔존」·「(2b) 값 획득 축 추가」
두 절이 이번 라운드에 사실대로 정정된 내용이고, `scope.md`의 위협 모델·(2b) 표
동기화는 **팀장 레인이 직접** 한다(요청만 남긴다 — D-6A1-42가 세운 「OPEN도 세 문서가
같은 라운드에 움직여야」와 같은 축).

## 알려진 제한 추가 — D-6A1-41(등재만, MEDIUM)

**명시 `@ComponentScan`이 Boot 기본 `excludeFilters` 둘(`TypeExcludeFilter`·
`AutoConfigurationExcludeFilter`)을 가린다**(verifier r2 레인 B 실측 — Spring이 실제
쓰는 메타데이터 경로로 확인). **오늘 거동 영향은 0**이고 D-6A1-35의 「production
공집합」 주장도 사실로 재확인됐다(배포물에 우리 클래스 24개·test 클래스 0개). 그러나
부수 효과가 있다는 사실 자체가 D-6A1-35(「예산 때문에 유지」)가 남긴 부채의 **크기를
키운다**. 받는 쪽은 **6A-2**(앱 이미지·entrypoint를 확정하는 slice, D-6A1-35와 같은
자리) — `OPEN-6A1-SCAN-FILTER-SIDE-EFFECT`(scope.md OPEN 표 등재분과 같은 ID). scope.md의
OPEN 표·알려진 제한에 정식 등재하는 것은 팀장 레인 소관이라 이 rollback.md 항목이 이
slice가 낼 수 있는 등재다(수정 라운드 4 — 이전 판이 이 절에서 OPEN ID 문자열을 빠뜨린
것을 정정, ④ OPEN ID 정합 점검).

**같은 점검에서 확인한 것(④)** — `OPEN-6A1-CREDENTIAL-RAW-REINTRODUCTION`(scope.md
D-6A1-45가 신설)은 위 「알려진 제한 추가 — D-6A1-43 잔존」절이 다루는 것과 같은
사실(raw 자격증명 재도입 잔존)이지만 이 절 작성 시점에 ID 문자열을 인용하지 않았다 —
여기서 명시한다: **`OPEN-6A1-CREDENTIAL-RAW-REINTRODUCTION`**. 나머지 세 OPEN ID
(`OPEN-6A1-CONNECTION-POOL`·`OPEN-BYTECODE-GATE-CONST-VAL-BLINDSPOT`·
`OPEN-JAR-CONTENT-GATE-BOOTJAR-BLINDSPOT`)는 `grep -oE 'OPEN-[A-Z0-9-]+'`로 대조해
`scope.md`와 문자열이 정확히 일치함을 확인했다(differs 0).

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
  app/src/test/kotlin/bidvector/app/OperatorCredentialPropertiesTest.kt \
  config/quality/gate-tests.properties gradle/libs.versions.toml openapi \
  milestone-6.md
```

(수정 라운드 4 — `OperatorCredentialPropertiesTest.kt`가 `bidvector.app` 최상위 test
패키지에 있어 기존 `app/src/test/kotlin/bidvector/app/http` 디렉터리 경로로는 덮이지
않는다. 개별 경로로 추가했다.)

**유효성 술어 정정(CLAUDE.md 2026-09-19, `origin/main`에 이미 반영·이 branch는 흡수 전)** —
verifier가 대조하는 것은 「실측 HEAD == 판정 SHA」가 아니다(evidence 커밋은 언제나 뒤에
오므로 둘은 영원히 다르다). **그 사이에 되돌림 대상이 움직였는가**를 본다 — 위 명령이
`<판정 SHA>` 자리에 실제 판정 대상 SHA를 넣었을 때 **빈 출력이면 유효**, 한 줄이라도
나오면 미검증이다. 「실측 HEAD가 판정 SHA의 조상」은 이 확인의 전제일 뿐 충분조건이
아니다(조상이어도 그 사이 되돌림 경로가 움직였으면 미검증).

**자기 검증(2026-09-20, 라운드 1)** — 실측 HEAD `440a7286`부터 이 문서의 최신 내용
커밋까지 위 경로 집합에 대해 직접 실행: `git diff --name-only 440a7286..<이 편집을
포함한 커밋> -- <위 경로 전부>` → **빈 출력**(exit 0). 즉 `440a7286` 이후 rollback.md
자체 말고는 되돌림 대상 경로가 전혀 움직이지 않았다 — `440a7286`을 실측 HEAD로 계속
써도 된다.

**자기 검증(2026-09-23, 라운드 2)** — 새 실측 HEAD `f5e39a3a`부터 이 rollback.md 편집을
포함한 커밋까지 같은 경로 집합에 대해 직접 실행: **빈 출력**(exit 0, `commands.md`·
`rollback.md` 자신 말고는 아무 것도 움직이지 않았다). 또한 `f5e39a3a`의 부모 쪽으로
`d9708f4a..f5e39a3a`도 확인 — `reports/evidence/m6/6a1/commands.md` **한 줄뿐**(L-4가
가른 「마지막 코드 커밋」과 「실측에 쓴 HEAD」 사이 유일한 차이가 evidence 파일 하나임을
재확인).

**자기 검증(2026-09-23, 최종 — D-6A1-40 반영 뒤)** — 실측 HEAD `9db7da36`부터 이
rollback.md 편집을 포함한 커밋까지: 그 커밋은 `git add`·`git commit --` 둘 다
`reports/evidence/m6/6a1/rollback.md` 하나만 개별 인자로 받으므로(공유 working tree
add+commit 한 명령 규율) **구조적으로 이 파일 하나만** 담는다 — 되돌림 대상 경로는
움직일 수 없다. 커밋 뒤 `git diff --name-only 9db7da36..<이 커밋>`으로 실측 재확인
예정(다음 절차가 그 결과를 담는다).

**자기 검증(2026-09-23, 수정 라운드 3)** — 실측 HEAD `92f8f682`부터 이 commands.md·
rollback.md 편집을 담는 evidence 커밋(`6ea7002f`)까지: 그 커밋도 `git add`·`git commit --`
둘 다 `reports/evidence/m6/6a1/commands.md`·`reports/evidence/m6/6a1/rollback.md` 개별
인자만 받으므로 되돌림 대상 30경로는 그 커밋 안에서 움직일 수 없다. **실행으로 재확인**:
`git diff --name-only 92f8f682..6ea7002f -- <위 30경로>` → **빈 출력**(exit 0) — 구조적
guarantee와 실측이 일치한다.

**자기 검증(2026-09-23, 수정 라운드 4)** — 실측 HEAD `561d8544`부터 이 commands.md·
rollback.md 편집을 담는 evidence 커밋(`739a4368`)까지: 같은 구조(evidence 경로만 개별
인자로 커밋)이므로 되돌림 대상 31경로는 움직일 수 없다. **실행으로 재확인**:
`git diff --name-only 561d8544..739a4368 -- <위 31경로>` → **빈 출력**(exit 0).

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
  아니라 **하네스 레인**이 받는다. **L-3 — 이 문단은 이미 형태 셋을 전부 열거한다**
  (verifier r2가 짚은 것은 scope.md의 「OPEN — 수령·신설」**표 항목**이 아직 좁은
  문면이라는 점이다 — 그 표는 팀장 레인 소관이라 이 slice가 직접 넓히지 않는다. 위
  문단이 이 slice가 낼 수 있는 완전한 등재이고, 팀장 레인에 scope.md 표 동기화를
  요청한다).
- **`OPEN-JAR-CONTENT-GATE-BOOTJAR-BLINDSPOT`(L-1 시정 — 가칭에서 정식 등재로).**
  verifier r2가 지적한 대로 이 OPEN이 「가칭」 산문으로만 있고 정식 등재가 안 됐던
  것을 여기서 정식화한다(scope.md의 「OPEN — 수령·신설」표는 팀장 레인 소관이라 이
  slice가 직접 편집하지 않는다 — **이 rollback.md 항목이 이 slice가 낼 수 있는
  등재의 정본이고, 팀장 레인에 scope.md 표 반영을 요청한다**). 두 사실을 하나의
  OPEN에 함께 담는다:
  1. **배포물 사각** — `jarContentGate`의 `archives.from(tasks.named("jar")...)` 배선이
     build-logic에 하드코딩돼(`build-logic/src/main/kotlin/bidvector.kotlin-conventions.gradle.kts`)
     배포물(`bootJar`)의 class 바이트를 검증하지 못한다.
     D-6A1-28에서 `jar`를 재활성해 게이트가 다시 뭔가를 재게(entries 0→20) 했지만,
     그 「뭔가」는 여전히 **비배포 아티팩트**(`jar` task 산출물)다.
  2. **빈 입력 통과 사각(verifier r2 MUT-J2 실측)** — `jar`를 다시 끄고 clean
     빌드하면 `entries=0`인데 게이트 **exit 0**(통과)이다. D-6A1-28은 **값**(20)을
     되돌렸을 뿐 **공허해질 수 있는 기제 자체**는 그대로라, build 파일 한 줄이면
     이 사각이 재발한다.
  받는 쪽은 **하네스 레인**(게이트 술어 자체를 구조로 닫아야 하는 층) — 이 slice의
  범위가 아니다.
