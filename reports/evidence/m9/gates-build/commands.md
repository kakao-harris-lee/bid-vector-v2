# M9 / gates-build — 실행한 명령과 결과

base `df4976cc`. 모든 Gradle 호출은 `~/.local/bin/bv-build.sh ./gradlew --no-daemon --console=plain …`
이다(레인 공통 규율 — 빌드 자리 둘·획득 순간 메모리 재확인). 전건 `check`·`qualityBaseline`·container·
S-20·Playwright 는 **로컬에서 돌리지 않는다 — PR CI 가 정본**이다(같은 명령).

## 표적 test · lint (전부 exit 0)

| 명령 | 결과 |
| --- | --- |
| `:build-logic:test --tests 'bidvector.buildlogic.MigrationOrderTest' --tests '…GateRegistrationCensusTest'` | 37 tests · 0 failed |
| `:build-logic:test --tests '…JarContentPolicyTest'` | 12 tests · 0 failed |
| `:build-logic:test --tests '…DuplicatePolicyTest' --tests '…CpdReportPresenceTest' --tests '…TestShapesTest'` | 9 · 8 · 16 tests · 0 failed |
| `:build-logic:test --tests '…UnusedImportsTest'` | 0 failed |
| `:build-logic:test --tests '…SourceMemberCallsTest' --tests '…SourceReferencePolicyTest' --tests '…SourceReferencesTest' --tests '…DomainSourceReferenceFixtureTest'` | 0 failed |
| `:build-logic:ktlintMainSourceSetCheck :build-logic:ktlintTestSourceSetCheck :build-logic:detekt` | exit 0 |
| `:adapters:… :app:… :procurement:… :workflow:… {ktlintMain,ktlintTest}SourceSetCheck detekt`(import 제거가 닿은 네 모듈) | exit 0 |
| `compileKotlin compileTestKotlin`(전 모듈) | exit 0 — import 169 줄 제거가 거동 중립임의 증명 |

## 새·바뀐 게이트의 초록 실측

| 게이트 | 핵심 결과 한 줄 |
| --- | --- |
| `migrationOrderGate` | adapters `files=22 violations=0`; 비-소유 모듈은 `files=0 violations=0`(정책이 고른 한 모듈만 단언) |
| `jarContentGate` | app `archives=2 entries=657 judged=558 exempt=99 violations=0` — 앞 판은 `jar` 하나만 보아 판정 엔트리가 279 였다 |
| `gateRegistrationGate` | 장부 모양 단언 추가 뒤 전 모듈 exit 0(어긋나 있던 블록 셋을 같은 커밋에서 정렬) |
| `cpdCheck` · `cpdCheckObserved` | 정규화 두 값을 켠 뒤에도 main 전 모듈 중복 **0**(기존 위반 없음). observed(test) 축은 전과 같이 관찰만 |
| `unusedImportGate` · `buildLogicUnusedImportGate` · `scriptUnusedImportGate` | 최초 실행 **169 위반**(adapters 95 · workflow 45 · app 27 · procurement 2 · 그 밖 0) → 제거 뒤 전 트리 `unused=0` |
| `domainSourceReferenceGate`(세 축) | 전 모듈 `violations=0`; 축마다 잰 수를 센다 — 예: adapters `references=1216 scopedFiles=22 memberCalls=4994` |

## 양성 대조 (심은 위반 → 실패)

| # | 심은 것 | 결과 |
| --- | --- | --- |
| 1a | 마이그레이션 `V24` → `V26`(결번 둘) | `migrationOrderGate` FAILED — V24·V25 결번 |
| 1b | `V3` 를 둘로(중복) | FAILED — 같은 version 둘 |
| 1c | 은퇴 번호 `V10` 재사용 | FAILED |
| 3a | `bootJar` 의 `BOOT-INF/classes/` 에 심은 class(JDK `jar` 로 삽입) | `jarContentGate` FAILED — 「게이트를 거치지 않은 클래스」. 보고 `entries 657 → 658` |
| 3b | 아카이브 둘 다 치움 | FAILED — 「아카이브 입력이 없다」(비-공허) |
| 4a | `gate.tests.decision` 두 행을 맞바꿈 | `gateRegistrationGate` FAILED — 「등재 행이 사전순이 아니다」 |
| 5a | 쓰지 않는 `java.time.Instant` import | `unusedImportGate` FAILED |
| 6a | `audit` 패키지에서 `bidvector.decision` 참조 | `domainSourceReferenceGate` FAILED — 허용 뿌리 밖 |
| 8a | `System.getenv("CI")` in main | FAILED — 환경 스니핑(임계 0) |

## 차등 실측 둘 (앞 판은 어땠나)

**⑥ `const val` 사각** — `audit` 패키지에 `internal const val PROBE_DOMAIN = SAMPLE_DRAW_DOMAIN`
(`bidvector.workflow.collection`, 그 축의 허용 뿌리 밖)을 심고 두 층을 각각 돌렸다.

- 바이트코드 층 `:adapters:test --tests '*AuditAdapterDependencyTest'` → **BUILD SUCCESSFUL**.
  `javap -p -v` 로 그 클래스의 상수 풀에서 `workflow` 흔적을 센 결과 **0**.
- 새 소스 층 `:adapters:domainSourceReferenceGate` → **BUILD FAILED**, 좌표를 그대로 지목.

**② CPD 이름 치환** — 구조는 같고 식별자·리터럴이 전부 다른 블록 둘(≈200 토큰)을 main 에 심고
`normalize.*` 를 끈 레그·켠 레그로 각각 `:settlement:cpdCheck` 를 돌렸다.

- 두 레그 모두 **BUILD SUCCESSFUL** — 켜도 탐지되지 않는다.
- 통제군(같은 입력을 축어 동일로 바꿈) → **BUILD FAILED**(임계 50·Kotlin lexer 는 정상).
- 사유는 구조적이다: PMD 7.7.0 `KotlinLanguageModule.createCpdLexer(LanguagePropertyBundle)` 가 그
  번들을 쓰지 않고 인자 없는 `KotlinCpdLexer()` 를 만든다(바이트코드 확인) — anonymize 속성이
  Kotlin 토크나이저에 닿을 길이 없다. 그래서 **그 OPEN 의 이름 치환 축은 열린 채**다.

## 음성 대조 — 바꿔치우기 변이 (더하기가 아니라 대체)

커밋 뒤에 술어 본문을 **통과 쪽으로 대체**하고 돌렸다. 되돌림은 변이 파일만.

| 변이 | 결과 |
| --- | --- |
| `MigrationOrder.violations` → 빈 목록 · `GateRegistration.ledgerOrderViolations` → 빈 목록 | 37 중 **7 FAILED** |
| `UnusedImports.detectIn` → 빈 목록 · `JarContentPolicy.violation` → `null` · `SourceReferencePolicy.admitsInScope`·`isEnvSniff` → 통과 쪽 | 48 중 **16 FAILED** |

변이 뒤 `git status --porcelain` 이 매번 빈 출력임을 확인했다(산출물은 변이 전에 커밋돼 있다).

## clean-tree

각 커밋 뒤 `git status --porcelain -- <in_scope 개별 인자>` 빈 출력. 양성 대조 1회: 변이를 심은
직후 같은 명령이 그 경로를 내는 것을 확인하고(비파괴) 되돌렸다 — `checkout --` 는 쓰지 않았다.
