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

- **우회 (1)**(기본 거부·기계 전수): `OperatorCredentialFilter` urlPatterns를 `/api/strategy`로
  좁히고 새 `@RestController`(`/api/new-unprotected-endpoint`)를 임시 추가 →
  `OperatorAuthenticationTest`의 「등록된 모든 endpoint가…기계 전수」 RED(expected 401
  but was 500, 새 경로가 인증 밖으로 태어남을 잡음).
- **우회 (2)**(audit이 성공 경로에만): `HttpTestApplication`의 두 필터 `order`를 서로
  바꿔 인증 필터가 audit 필터보다 바깥이 되게 함 → `RequestAuditFilterTest`의 「인증
  실패 요청도 audit 행을 남긴다」 RED(expected 1 but was 0).
- **우회 (3)**(error body 예외 메시지 노출): `ErrorMapping.forThrowable`의 기본 분기를
  `throwable.message`를 싣도록 수정 → `RequestAuditFilterTest`의 「컨트롤러 예외도…노출하지
  않는다」 RED(expected false but was true, 내부 문구가 응답에 실림).
- **우회 (4)**(상수 시간 비교): `constantTimeEquals`를 `presented == expected`로 치환 →
  `ConstantTimeComparisonStructureTest` RED(`MessageDigest.isEqual` 상수 풀 항목 부재).
- **D-6A1-17**(fail-closed): `RequestAuditFilter`의 `when`에서 `auditOutcome.isFailure`
  분기를 제거(audit 실패를 무시) → `RequestAuditFilterTest`의 「audit 쓰기 실패는
  fail-closed다」 RED(expected 500 but was 200, 원 조회 결과가 그대로 나감).
- **우회 (6) / D-6A1-20 ⓑ**(OpenAPI 완전 일치·깊이): `StrategyReadResponse`에 여분 필드
  `mutationExtraField`(기본값 있음, 호출부 무편집) 추가 → `OpenApiContractTest`의 「200
  응답의 최상위 키 집합이…완전히 일치한다」 RED(집합 불일치). 별도로 YAML 스키마에
  `mutationNested`(`type: object`) 속성을 추가 → 「D-6A1-20 ⓑ…중첩 object 속성을 갖지
  않는다」 RED(`StrategyReadResponse.mutationNested` 검출) — 문서 축·값 축 둘 다 변이로
  확인.
- **D-6A1-21**(디스패치 실측): `RequestAuditFilterTest`의 `@SpringBootTest(properties=[])`로
  두 설정(`throw-exception-if-no-handler-found`·`add-mappings`)을 제거 → 「미매핑
  경로도…디스패치에서 끝나고」 RED(expected 404 but was 500 — 두 설정이 실제로 이 슬라이스
  설계의 전제임을 확인, 가정이 아니라 실측).

## 비밀값 스캔(참조형)

```
grep -rniE -f config/quality/leak-patterns.txt <in_scope 경로 개별 인자> reports/evidence/m6/6a1/
```
- exit: 0(매치 있음 — 아래 넷은 전부 육안 확인상 비밀값이 아니다)
  - `PersistenceTestSupport.kt`(공유 파일, 이 slice가 한 줄만 추가) — Testcontainers/
    PGSimpleDataSource의 `withPassword`/`password` API 이름(서드파티 라이브러리 호출,
    D-6F5-a 이전부터 있던 기존 코드).
  - `PersistenceWiring.kt:36` — `PGSimpleDataSource.password` 세터(서드파티 JDBC 드라이버
    API 이름, 우리 이름 선택이 아니다). 같은 파일 KDoc은 D-6A1-19가 **쓰지 않기로 한**
    설정 키 이름을 인용해 설명하는 자리다(회피 사유를 적는 것 자체가 인용을 요구한다).
  - `openapi/bidvector-operator-api.yaml:51` — OpenAPI 3.0 표준 어휘 `securitySchemes.type:
    apiKey`(스펙 키워드, 비밀값 아님).
  - `reports/evidence/m6/6a1/scope.md`(팀장 레인, 계약 갱신 (5)) — D-6A1-19 판정 근거
    문단이 회피 대상 낱말을 인용해 설명한다. **이 파일은 `leakPatternGate`의 실제
    scanRoot(`reports/evidence/`) 안**인데 위 S-10(`./gradlew check`)이 그 게이트를
    포함해 exit 0이었다 — 기존 baseline에 이미 등재된 인용임을 실측으로 확인(신규 매치
    아님).
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

## 참고 — 하네스 레인 변경

`git log --oneline c4d09cc..HEAD -- CLAUDE.md .claude/ docs/harness/` — 없음(scope.md와 동일).
