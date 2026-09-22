# M6/6A-1 commands.md

acceptance_commands(scope.md) 전건 + 리뷰 요청 조건 점검. 출력 전문은 담지 않는다 —
핵심 결과 한 줄 + exit code만(evidence-pack 규격).

## 2026-09-19T10:24:30Z ~ 10:24:35Z (S-10)
- cmd: `./gradlew --no-daemon check`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL, 336 actionable tasks(31 executed·305 up-to-date) — 9개 모듈
  전건(test·lint·detekt·architecture·contract·size·leak-pattern 게이트 포함).

## 2026-09-19T10:24:42Z ~ 10:25:11Z (S-40)
- cmd: `./gradlew --no-daemon :app:test --tests '*http*' --rerun-tasks`
- exit: 0
- 핵심 결과: 16 tests, 0 failed(OperatorAuthenticationTest 4·RequestAuditFilterTest 5·
  OpenApiContractTest 5·ConstantTimeComparisonStructureTest 2).

## 2026-09-19T10:25:18Z ~ 10:25:51Z (S-41)
- cmd: `./gradlew --no-daemon :adapters:test --tests '*JdbcStrategyRepositoryTest*' --rerun-tasks`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL — 6F-1 전략 영속 왕복(이 slice가 소비만, 무편집) 유지 확인.

## 2026-09-19T10:25:56Z ~ 10:26:28Z (S-42)
- cmd: `./gradlew --no-daemon :app:test --tests '*OpenApiContractTest*' --rerun-tasks`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL — OpenAPI ↔ 구현 대조 5건 전부 통과.

## 2026-09-19T10:26:37Z ~ 10:27:16Z (S-20)
- cmd: `./tools/one-command-check.sh`
- exit: 0
- 핵심 결과: 「완료 — Kotlin 전건 + Python 전건 통과」(Kotlin `check` 재실행 + Python
  ruff·mypy·import-linter·pytest·설계 래칫·재활용/버전 대조·wheel 빌드 전건 GREEN — 이
  slice는 Kotlin만 건드려 Python 축은 회귀 확인용).

## 변이 검증 — 우회 여섯 + D-6A1-17 + D-6A1-20 ⓑ + D-6A1-21 (임시 편집 → RED 확인 → 원복)

각 항목은 production/test 파일을 임시로 mutate해 대상 test가 RED로 떨어지는지 확인한
뒤 `cp` 백업본으로 원복했다(`git diff --stat`으로 원복 후 변경 없음을 매번 확인).

**6F-4 하네스 규율(변이가 실제로 적용됐는지 먼저 잰다) 반영 — 2026-09-19 재실측.**
아래 일곱 항목 전부, mutate 직후 **`git diff --numstat`으로 변경 라인 수가 0이 아님을
먼저 확인한 뒤에만** test를 돌렸다(6F-4가 셸 heredoc의 유니코드 이스케이프가 실제
제어문자로 치환돼 변이가 적용되지 않은 채 BUILD SUCCESSFUL이 난 것을 겪었다 — 같은
함정 방지). 아래 각 항목의 `numstat`은 그 재실측에서 나온 실제 값이다.

- **우회 (1)**(기본 거부·기계 전수): `OperatorCredentialFilter` urlPatterns를 `/api/strategy`로
  좁히고 새 `@RestController`(`/api/new-unprotected-endpoint`)를 임시 추가(`numstat`:
  `9 1 HttpTestSupport.kt`) → `OperatorAuthenticationTest`의 「등록된 모든 endpoint가…
  기계 전수」 RED(expected 401 but was 200, 새 경로가 인증 밖으로 태어남을 잡음).
- **우회 (2)**(audit이 성공 경로에만): `HttpTestApplication`의 두 필터 `order`를 서로
  바꿔 인증 필터가 audit 필터보다 바깥이 되게 함(`numstat`: `2 2 HttpTestSupport.kt`) →
  `RequestAuditFilterTest`의 「인증 실패 요청도 audit 행을 남긴다」 RED(expected 1 but
  was 0).
- **우회 (3)**(error body 예외 메시지 노출): `ErrorMapping.forThrowable`의 기본 분기를
  `throwable.message`를 싣도록 수정(`numstat`: `1 1 ErrorBody.kt`) →
  `RequestAuditFilterTest`의 「컨트롤러 예외도…노출하지 않는다」 RED(expected false but
  was true, 내부 문구가 응답에 실림).
- **우회 (4)**(상수 시간 비교): `constantTimeEquals`를 `presented == expected`로 치환
  (`numstat`: `1 4 OperatorCredentialFilter.kt`) → `ConstantTimeComparisonStructureTest`
  RED(expected true but was false — `MessageDigest.isEqual` 상수 풀 항목 부재).
- **D-6A1-17**(fail-closed): `RequestAuditFilter`의 `when`에서 `auditOutcome.isFailure`
  분기를 제거(audit 실패를 무시, `numstat`: `0 4 RequestAuditFilter.kt`) →
  `RequestAuditFilterTest`의 「audit 쓰기 실패는 fail-closed다」 RED(expected 500 but was
  200, 원 조회 결과가 그대로 나감).
- **우회 (6) / D-6A1-20 ⓑ**(OpenAPI 완전 일치·깊이): `StrategyReadResponse`에 여분 필드
  `mutationExtraField`(기본값 있음, 호출부 무편집, `numstat`: `1 0
  StrategyReadController.kt`) 추가 → `OpenApiContractTest`의 「200 응답의 최상위 키
  집합이…완전히 일치한다」 RED(집합 불일치). 별도로 YAML 스키마에
  `mutationNested`(`type: object`) 속성을 추가(`numstat`: `5 0
  bidvector-operator-api.yaml`) → 「D-6A1-20 ⓑ…중첩 object 속성을 갖지 않는다」
  RED(`StrategyReadResponse.mutationNested` 검출) — 문서 축·값 축 둘 다 변이로 확인.
- **D-6A1-21**(디스패치 실측): `RequestAuditFilterTest`의 `@SpringBootTest(properties=[])`로
  두 설정(`throw-exception-if-no-handler-found`·`add-mappings`)을 제거(`numstat`: `1 1
  RequestAuditFilterTest.kt`) → 「미매핑 경로도…디스패치에서 끝나고」 RED(expected 404
  but was 500 — 두 설정이 실제로 이 슬라이스 설계의 전제임을 확인, 가정이 아니라 실측).

재실측 뒤 `git status --porcelain`·`git diff --numstat`(인자 없음, 전체) 둘 다 빈
출력으로 완전 원복을 확인했고, 그 상태에서 `./gradlew --no-daemon check`를 다시 돌려
BUILD SUCCESSFUL을 재확인했다.

## 비밀값 스캔(참조형)

```
grep -rniE -f config/quality/leak-patterns.txt <in_scope 경로 개별 인자> reports/evidence/m6/6a1/
```
- exit: 0(매치 있음 — 아래 넷은 전부 육안 확인상 비밀값이 아니다. **이 절 자신은 그
  낱말을 축어로 인용하지 않는다** — leakPatternGate의 scanRoot가 `reports/evidence/`라
  인용 자체가 새 매치를 만든다, 실측으로 한 번 걸려 아래 서술로 고쳤다)
  - `PersistenceTestSupport.kt`(공유 파일, 이 slice가 한 줄만 추가) — Testcontainers 컨테이너
    자격 설정과 `PGSimpleDataSource`의 동명 세터 호출(서드파티 라이브러리 API, D-6F5-a
    이전부터 있던 기존 코드, 이 slice가 새로 쓴 줄이 아니다).
  - `PersistenceWiring.kt`의 `dataSource()` 빈 팩토리 — `PGSimpleDataSource`의 동명 세터
    호출(서드파티 JDBC 드라이버 API, 우리 이름 선택이 아니다). 같은 파일 KDoc은
    D-6A1-19가 **쓰지 않기로 한** 설정 키 이름을 설명하는 자리라 그 자리에서도 인용을
    피해 「대상 낱말」로만 가리킨다.
  - `openapi/bidvector-operator-api.yaml`의 `securitySchemes.type` 값 — OpenAPI 3.0
    표준 스키마 타입 이름(스펙 키워드, 비밀값 아님).
  - `reports/evidence/m6/6a1/scope.md`(팀장 레인, 계약 갱신 (5)) — D-6A1-19 판정 근거
    문단이 회피 대상 낱말을 설명 목적으로 인용한다. **이 파일은 leakPatternGate의 실제
    scanRoot 안**인데 위 S-10(`./gradlew check`)이 그 게이트를 포함해 exit 0이었다 —
    기존 baseline에 이미 등재된 인용임을 실측으로 확인(신규 매치 아님).
- 실측 HEAD: `10e53a7c`(이 evidence 커밋 이전 마지막 산출물 커밋) — 이 evidence 파일
  자체의 편집 뒤 최종 상태는 verifier·PR 조치 코멘트가 정본(evidence-pack 규격).

## clean-tree 게이트

```
git status --porcelain -- <in_scope 경로 개별 인자>
```
- exit: 0, 출력 없음(2026-09-19T10:27:2*Z 무렵 실측).
- 양성 대조: `app/build.gradle.kts`에 주석 한 줄을 심어 같은 명령이 `M app/build.gradle.kts`를
  잡는 것을 확인한 뒤, 그 줄만 있는 유일한 변경이라 `git checkout -- app/build.gradle.kts`로
  절삭(다른 미커밋 편집이 없었음을 `git status` 직전에 확인했다 — 심은 줄 외 편집이 있는
  일반적인 경우 이 명령은 쓰지 않는다, evidence-pack 규격).

## main 흡수 (계약 갱신 (6)(7) 지시 반영, 2026-09-19)

```
git fetch origin main
git merge origin/main --no-edit
```
- exit: 0
- 핵심 결과: `자동 병합`(conflict marker 0건) — `CleanMigrationCheckTest.kt`·
  `CleanMigrationColumnTest.kt`·`milestone-6.md` 셋이 6F-4(`V14__notice_title.sql`)와
  겹쳤으나 hunk가 서로 다른 자리(내 `api_request_audit` 항목 vs 그쪽 `notice_title`
  항목)라 자동 해소됐다. `gate-tests.properties`·`Sql.kt`는 실제로는 겹치지 않았다
  (팀장 사전 경고와 달리 6F-4가 그 두 파일을 이 range에서 건드리지 않음, 병합 뒤
  `git show`로 확인). 병합 뒤 `./gradlew --no-daemon check` 재실행 — BUILD SUCCESSFUL
  (336 tasks, 126 executed·210 up-to-date).

## D-6A1-22/23/24 반영 — 신설 패키지 게이트 공백 시정

계약 갱신 (6)이 지적한 게이트 공백을 닫았다: `bidvector.adapters.audit` 패키지에
형제 셋(`evaluation`·`qualification`·`profile`)과 같은 형태의 의존 게이트 +
등재 완결성 게이트를 신설.

- 신설: `AuditAdapterDependencyTest.kt`(허용 루트 = 자기 패키지 하나 — `ApiAuditStore`/
  `ApiAuditSql`이 domain·workflow 타입을 참조하지 않는다, 실측), `AuditGateRegistrationTest.kt`
  (`ProfileGateRegistrationTest`와 동형).
- 등재: `config/quality/gate-tests.properties`의 `gate.tests.adapters`에 둘 추가.

**변이 실측(D-6A1-22가 요구한 그대로)**:
- 금지 루트 전체 한정 좌표 참조: `ApiAuditStore.kt`에 `bidvector.adapters.persistence.Sql`
  객체 참조(오브젝트 자체, `const val`이 아니다 — `const val`로 먼저 시도했더니 컴파일러
  인라인으로 상수 풀에서 사라져 게이트가 못 봤다, 실측으로 확인 뒤 오브젝트 참조로
  교체)를 삽입(`numstat`: `2 0`) → `AuditAdapterDependencyTest` RED(`["bidvector.adapters
  .persistence.Sql", "...Sql.INSTANCE"]` 검출). 원복 확인(`numstat` 0).
- 등재 삭제: `gate-tests.properties`에서 `AuditGateRegistrationTest` 자신의 등재 줄을
  제거(`numstat`: `1 0`) → `AuditGateRegistrationTest` RED(자기 자신이 discovered에는
  있지만 등재에는 없음을 검출). 원복 확인.

재실측 뒤 `./gradlew --no-daemon check` — BUILD SUCCESSFUL(336 tasks, 60 executed·276
up-to-date). 비밀값 스캔(참조형, 신설 파일 셋) — exit 1(매치 없음).

## D-6A1-22 추가 실측(팀장 요청) — 형제 파일 우회·6F-4 줄 보존

**ⓑ 형제 파일 우회 — 클래스 하나만 보면 안 된다(6F-5-a r2 HIGH 선례).** 앞서 ⓐ는
`ApiAuditStore.kt`에 금지 참조를 심었다. 이번엔 **같은 패키지의 다른 파일**
(`ApiAuditSql.kt`, 처음 변이와 무관한 파일)에 같은 금지 참조(`bidvector.adapters
.persistence.Sql` 오브젝트, `const val` 아님)를 삽입(`numstat`: `2 0`, 원복 전 확인) →
`AuditAdapterDependencyTest` RED(동일하게 `Sql`·`Sql.INSTANCE` 검출) — 게이트가
`classesDir.walkTopDown()`으로 **패키지 전체**를 훑어 특정 파일에 고정되지 않음을
확인했다. 원복 확인(`numstat` 0).

**6F-4 줄 보존 확인 — 개수로 잰다.**
```
grep -c "notice_title\|notice_notice_title_check" \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationColumnTest.kt
```
결과: `CheckTest.kt` 4건 · `ColumnTest.kt` 1건 — `origin/main`(`1881c82c`, 병합 전
6F-4 원본)의 같은 명령 결과(각각 4·1)와 **정확히 일치**. 병합이 6F-4의 항목을 지우지
않았다.

병합 뒤 재실측: `./gradlew --no-daemon check`(2026-09-19T10:57:44Z~50Z) — BUILD
SUCCESSFUL(336 tasks, 33 executed·3 from cache·300 up-to-date).

## 수정 라운드 2 — D-6A1-37·38 (verifier r2 재판정 HIGH 2) 반영

verifier r2가 지정한 표적은 test 파일 둘뿐이고 production 코드는 무편집(HEAD `d9708f4a`
까지의 세 커밋 — `672f002c` D-6A1-37 · `4ba6082a` D-6A1-38 · `d9708f4a` detekt
ReturnCount 시정, MUT-N1 실측 중 버릴 clone의 전건 `check`에서 발견).

### 표적 재검증 — acceptance 전건 재실행 (HEAD `d9708f4a`)

| ID | 명령 | 시각(UTC) | exit | 핵심 결과 |
| --- | --- | --- | --- | --- |
| S-10 | `./gradlew --no-daemon check` | 15:48:43~49:02 | 0 | BUILD SUCCESSFUL, 337 tasks(43 executed) |
| S-40 | `:app:test --tests '*http*' --rerun-tasks` | 15:49:06~38 | 0 | 22 tests, 0 failed(Constant… 3·OpenApiContract 8·OperatorAuthentication 4·ProductionAssemblyAuthAudit 2·RequestAuditFilter 5) |
| S-41 | `:adapters:test --tests '*JdbcStrategyRepositoryTest*' --rerun-tasks` | 15:49:51~50:12 | 0 | BUILD SUCCESSFUL |
| S-42 | `:app:test --tests '*OpenApiContractTest*' --rerun-tasks` | 15:50:16~34 | 0 | BUILD SUCCESSFUL, 8 tests(신설 양성 대조 셋 포함) |
| S-20 | `./tools/one-command-check.sh` | 15:50:39~53:02 | **1** | Kotlin 전건 통과 · Python **1 failed / 957 passed** — 로컬 샌드박스 환경 문제(아래 참조), **CI에서는 확인됨** |

**S-20 — 로컬은 샌드박스 네트워크로 exit 1, CI 러너에서 3/3 통과 확인(`ml-engine` 포함,
확인하지 않은 것이 아니라 확인된 것으로 옮긴다).** 로컬 실패 원인은
`tests/gates/test_wheel_reexport.py::test_wheel_install_outside_repo_exposes_contracts_and_servicers`
가 `uv build --wheel`로 `build-system.requires`를 해소하려다 PyPI 요청이
`operation timed out`으로 죽은 것 — 샌드박스 egress 부재다(verifier r2 E-1과 재현 일치).
`git diff --name-only $(git merge-base HEAD origin/main)..HEAD -- ml-engine` **빈 출력**
(재확인, 이 slice는 `ml-engine` 무접촉) — 실패한 test 파일도 미편집. 프록시 설정을
바꾸거나 네트워크를 우회하지 않았다.

**PR #41의 CI에서 독립 확인**(`~/.internal-bin/gh` — 운영자가 준 프록시 우회 경로,
`HTTP_PROXY`/`HTTPS_PROXY`/`NO_PROXY`는 무편집, 바이너리만 교체):

```
~/.internal-bin/gh pr checks 41
```
- `check` pass(8m10s) · `container` pass(3m6s) · `ml-engine` **pass**(56s) — **3/3**.
- `~/.internal-bin/gh pr view 41 --json headRefOid` → `1a45dfb0...`(팀장 보고와 일치).

**판정**: 로컬 acceptance는 **Kotlin 전건(`check`, S-10)**으로 잡고, Python 축은 위
CI 실측(head `1a45dfb0`, `ml-engine` 포함 3/3 통과)을 근거로 든다 — 이 slice가
`ml-engine`을 그 head 이후로도 여전히 무접촉이므로 이 결과가 현재 HEAD에도 적용된다.

### 변이 실측 여섯 — 버릴 clone(`git clone --no-hardlinks`)에서만, `numstat`으로 적용 먼저 확인

verifier r2가 지정한 닫힘 판정 그대로 재현했다. 각 clone은 실측 직후 폐기했다.

- **MUT-N1**(D-6A1-37 닫힘) — clone(`d9708f4a` 기준)에서 `OperatorCredentialFilter.kt`의
  `constantTimeEquals`(MessageDigest 위임)를 삭제하고 같은 패키지의 새 오브젝트
  `CredentialComparator.matches`(`presented == expected`, 단락 비교)로 교체
  (`numstat`: `8 12`, 적용 확인). `javap`로 `CredentialComparator.class` 상수 풀에
  `Intrinsics.areEqual` **1건** 확인(허용 목록 밖). 결과: `ConstantTimeComparisonStructureTest`
  **FAILED**(`패키지의 어떤 class 도 허용 목록 밖에서 Intrinsics areEqual 을 쓰지
  않는다` — `expected:<true> but was:<false>`, MessageDigest.isEqual 부재로도 동시에
  검출) · 전건 **`./gradlew check` BUILD FAILED**(152 tests, 1 failed). **닫힘 확인**.
- **MUT-D2~D5**(D-6A1-38 정적 닫힘) — 별도 clone의 `openapi/bidvector-operator-api.yaml`에
  **어디에도 참조되지 않는** `MutationProbe` 스키마를 추가(`numstat`: `20 0`, 적용 확인)해
  네 형태를 한 번에 심었다: `arrayOfRefItems`(배열의 `$ref`, D2) · `directRef`(속성 직접
  `$ref`, D3) · `arrayOfArrayOfObject`(배열의 배열의 object, D4) · `freeFormMap`
  (`additionalProperties`, `type` 키 없이, D5). 결과: `OpenApiContractTest`의
  `D-6A1-20 ⓑ` **FAILED** — `collectNonFlatProperties()`가 넷 전부 정확히 개별
  식별(`MutationProbe.arrayOfRefItems`·`.directRef`·`.arrayOfArrayOfObject`·
  `.freeFormMap`). **닫힘 확인**(네 형태 전부).
- **MUT-R2**(D-6A1-38 런타임 닫힘) — 별도 clone에서 `containsNestedObject`의 재귀 호출을
  D-6A1-30 이전 형태(`value.any { it is Map<*, *> }`, 비재귀)로 원복(`numstat`: `1 1`,
  적용 확인). 결과: `평탄 응답 값 술어는 임의 깊이의 object 를 재귀로 잡는다`
  **FAILED**(깊이 2, `List<List<Map>>` 지점에서 `expected:<true> but was:<false>`) —
  재귀가 실제로 깊이 2 이상을 잡는 유일한 이유임을 확인. **닫힘 확인**.

세 clone 모두 실측 직후 삭제(`rm -rf`), 실제 lane worktree는 무접촉으로 유지했다
(`git status --porcelain` 매 mutation 전후 실측 worktree에서 빈 출력 확인).

### 허용 목록 근거(D-6A1-37) — 개별 class와 근거

| class(nameWithoutExtension) | areEqual 건수(HEAD `d9708f4a`, javap 실측) | 근거 |
| --- | --- | --- |
| `ApiAuditRecord` | 6 | data class 자동 `equals()` — 필드는 시각·주체·경로·상태·correlation id, 자격증명 값 없음 |
| `ErrorBody` | 4 | data class 자동 `equals()` — 필드는 code·message·correlationId, 자격증명 값 없음 |
| `StrategyReadResponse` | 19 | data class 자동 `equals()` — 전략 필드뿐, 자격증명 값 없음 |
| `StrategyReadControllerKt` | 5 | `provenanceLabel()`의 `Provenance` sealed 싱글턴 객체 참조 동일성(javap로 `Provenance$DerivedFromOpening.INSTANCE` 등 확인, `when` 소진) — 자격증명과 무관 |

허용 목록 밖 나머지 열 class(`OperatorCredentialFilter`·`$Companion`·`…Kt`·
`ErrorBodyKt`·`ErrorCode`·`ErrorMapping`·`GlobalErrorHandler`·`RequestAuditFilter`·
`StrategyReadController`·`StrategyReadResponse$Companion`)는 HEAD 시점 `areEqual` 0건
(javap 실측) — 허용 목록 확장 없이 기본 판정 상태 그대로다.

## D-6A1-40(레인 B HIGH) 반영 — production 조립이 audit 행을 직접 단언한다

verifier r2가 스폰 사고로 **둘** 떴고, 레인 B(`16_verifier_report_r2_laneB.md`)가 레인 A와
독립으로 잡은 HIGH — `ProductionAssemblyAuthAuditTest`가 상태 코드·body만 재고
`api_request_audit` 행을 한 줄도 단언하지 않아 production audit 필터 bean을 삭제해도
전건 `check`가 초록이었다. 커밋 `88fb33e1`(`ProductionAssemblyAuthAuditTest.kt`에 새
test `D-6A1-40 — production 조립에서 성공·인증실패·예외 각각 api_request_audit 행이
정확히 하나다` 추가 — `@BeforeEach`로 표를 비우고, production `PersistenceWiring`이
실제로 만든 `DataSource` bean을 그대로 써서 성공(200)·인증실패(401)·미매핑(404) 각각
행 개수를 개별+합계로 단언). `ApiAuditStore`의 (2b) 불변식(추가 전용, 읽기 메서드
없음)은 건드리지 않았다 — 표를 직접 조회하는 것은 test 코드뿐이다.

### acceptance 재검증(HEAD `88fb33e1`)

| ID | 명령 | 시각(UTC) | exit | 핵심 결과 |
| --- | --- | --- | --- | --- |
| S-10 | `./gradlew --no-daemon check` | 16:15:02~09 | 0 | BUILD SUCCESSFUL, 337 tasks |
| S-40 | `:app:test --tests '*http*' --rerun-tasks` | 16:15:14~38 | 0 | **23** tests, 0 failed(`ProductionAssemblyAuthAuditTest` **3**건으로 증가) |
| S-41 | `:adapters:test --tests '*JdbcStrategyRepositoryTest*' --rerun-tasks` | 16:15:42~16:03 | 0 | BUILD SUCCESSFUL |
| S-42 | `:app:test --tests '*OpenApiContractTest*' --rerun-tasks` | 16:16:07~25 | 0 | BUILD SUCCESSFUL |

(S-20은 위 절에서 CI 확인으로 갱신 — 이 코드 변경은 `ml-engine` 무접촉이라 그 결과가
그대로 적용된다.)

### 변이 실측 — 팀장이 지정한 닫힘 판정 둘, 버릴 clone에서

- **MUT-AUDIT-BEAN** — clone에서 `BidVectorApplication.kt`의 `requestAuditFilterRegistration`
  bean 전체(및 이제 안 쓰는 import 셋)를 삭제(`numstat`: `0 30`, 적용 확인. 삭제 뒤
  ktlint가 잡은 빈 줄 하나는 변이와 무관한 서식 문제라 별도로 지워 실제 신호를 가리지
  않게 했다). 결과: 새 test **FAILED**(`expected:<1> but was:<0>`) · 전건
  **`./gradlew check` BUILD FAILED**(153 tests, 1 failed). **닫힘 확인**.
- **MUT-URLPATTERNS** — 별도 clone에서 같은 bean의 `urlPatterns`를
  `listOf("/mut-d40-nonexistent-path")`로 좁힘(`numstat`: `1 1`, 적용 확인). 결과: 같은
  test **FAILED**(`expected:<1> but was:<0>`) · 전건 **BUILD FAILED**. **닫힘 확인**.

두 clone 모두 실측 직후 삭제. 팀장이 지정한 두 닫힘 판정("audit 필터 등록 bean을
삭제 → RED" · "urlPatterns 무매칭으로 좁혀도 RED") 둘 다 실측으로 확인했다.

## 수정 라운드 3 — D-6A1-43·44(운영자 결정 2026-09-23 「타입으로 끝낸다」)

verifier r3 HIGH 둘(위치·이름 술어가 세 번째로 뚫린 상수 시간 게이트, `components.schemas`
고정 순회) 모두 **타입/구조로 종결**했다 — 게이트를 넓히지 않았다.

- **D-6A1-43** `OperatorCredentialFilter`가 raw `String`을 직접 비교하던 것을
  `OperatorCredential`(`matches()` 하나만 노출, `equals`/`hashCode`/`toString` 미재정의)로
  대체. 필터 생성자도 `String` 대신 `OperatorCredential`만 받도록 강화해, raw 자격증명이
  필터 class 안에 순간적으로도 존재하지 않게 했다(감싸는 지점을 조립 근으로 이동).
- **D-6A1-44** `OpenApiContractTest`의 평탄성 순회 루트를 `components.schemas` 고정에서
  **문서 트리 전체 재귀**(`properties` 키를 가진 모든 Map)로 넓혔다 — 위치를 손으로
  늘리지 않는다.

### acceptance 재검증(HEAD `92f8f682`)

| ID | 명령 | exit | 핵심 결과 |
| --- | --- | --- | --- |
| S-10 | `./gradlew --no-daemon check` | 0 | BUILD SUCCESSFUL, 337 tasks |
| S-40 | `:app:test --tests '*http*' --rerun-tasks` | 0 | BUILD SUCCESSFUL(`OperatorCredentialTest` 7건 신규 포함) |
| S-41 | `:adapters:test --tests '*JdbcStrategyRepositoryTest*' --rerun-tasks` | 0 | BUILD SUCCESSFUL |
| S-42 | `:app:test --tests '*OpenApiContractTest*' --rerun-tasks` | 0 | BUILD SUCCESSFUL(9 tests — 신규 `paths 의 인라인 응답 스키마…` 포함) |
| S-20 | `./tools/one-command-check.sh` | 1 | **환경**(E-1, PyPI 프록시 timeout) — 이 라운드는 Kotlin만 편집, `ml-engine` 무접촉. 우회하지 않았다 |

### 변이 실측 — D-6A1-43(버릴 clone `git clone --no-hardlinks`, `numstat`로 적용 먼저 확인)

닫힘 판정 넷 중 형태별로 **막힌 방식을 구분**해 실측했다:

- **MUT-1**(`==`) — `expected.matches(...)` → `expected != OperatorCredential.wrap(presented)`
  (numstat `1 1`). 결과: **컴파일 성공, 전건 `check` BUILD FAILED**(11 tests failed, 전부
  「올바른 자격증명인데 401」류 — 기능 고장으로 강등, 시간이 새는 방향이 아니다).
- **MUT-2**(`Objects.equals`) — `java.util.Objects.equals(expected, ...)`로 치환(numstat
  `1 1`). 결과: **전건 `check` BUILD FAILED**(10 tests failed, 같은 형태).
- **MUT-3**(이웃 패키지 이동, verifier r3 MUT-A3 재현) — `bidvector.app.security` 패키지에
  raw `String ==` 비교 함수를 신설하고 필터가 그것을 부르게 함(numstat `2 1` + 신규 파일).
  **1차 구현**(필터 생성자가 `String`을 받아 내부에서 감싸던 판)에서는 raw 문자열을 새
  필드로 재저장해 넘기면 **BUILD SUCCESSFUL**(닫히지 않음, 착수 실측). **강화 구현**(현재
  HEAD — 필터 생성자가 `OperatorCredential`만 받음)에서는 같은 형태를 재현하려면 필터
  생성자 시그니처와 두 조립 지점(`BidVectorApplication`·`HttpTestSupport`) 셋을 모두
  고쳐야 했다 — 3파일 변경(numstat `1 1` + `2 1` + `1 1` + 신규 파일) 뒤 서식을 바로잡고
  실측한 결과 **BUILD SUCCESSFUL**(잔존 — 아래 「알려진 제한」에 등재).
- MUT-4(허용 목록 simple name 재사용) — 실행하지 않음. 1차 방어가 타입으로 옮겨간 뒤에는
  `Intrinsics.areEqual`을 이름 충돌로 숨겨도 **숨길 만한 exploitable raw 비교가 애초에
  없다**(회귀 그물 게이트 자체의 맹점은 r3 F-1 그대로 남지만, 보안 성질은 그 게이트에
  의존하지 않는다) — 이유를 `ConstantTimeComparisonStructureTest` KDoc에 적었다.

### 변이 실측 — D-6A1-44(버릴 clone, 실 `openapi.yaml` 대상)

verifier r3 닫힘 판정 문면(「paths 의 인라인 응답 스키마에 ① 중첩 object ② object 배열
③ `$ref` 를 각각 심어 전부 RED」) 그대로, `/api/strategy` 200 응답 스키마를 `$ref` 대신
inline object로 바꿔 속성을 하나씩 심었다(각 clone에서 개별 실행):

- **MUT-D**(중첩 object 속성) — numstat `7 1`. `:app:test --tests '*OpenApiContractTest*'
  --rerun-tasks` **FAILED**(`D-6A1-20 ⓑ` — `expected:<[]> but was:<[".../probeNestedObject"]>`,
  나머지 8건은 그대로 통과).
- **MUT-E**(object 배열 속성) — numstat `9 1`. 같은 test **FAILED**(같은 형태).
- **MUT-F**(`$ref` 속성) — numstat `4 1`. 같은 test **FAILED**(같은 형태).

세 형태 모두 실 spec 파일에서 **정확한 위치**(`spec.paths./api/strategy.get.responses.200
.content.application/json.schema.properties.<probe>`)를 assertion 메시지로 식별했다 —
합성 spec 단위 test(`paths 의 인라인 응답 스키마에 심은 위반도 순회가 잡는다`)뿐 아니라
실 문서 대상으로도 닫힘을 재확인했다.

## 참고 — 하네스 레인 변경

`git log --oneline $(git merge-base HEAD origin/main)..HEAD -- CLAUDE.md .claude/ docs/harness/`
— 없음(scope.md와 동일).
