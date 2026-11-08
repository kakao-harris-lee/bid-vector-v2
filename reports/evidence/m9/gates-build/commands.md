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

## 병합 해소 검산 — main 두 번 (합집합 등식으로)

이 브랜치는 main 을 두 번 병합했다. `e12c0485`(PR #122~#133) 와 `3ad4f3f6`(PR #135). 둘 다 **합집합
등식**으로 검산한다 — 충돌 0 자동 병합이 같은 본문을 접는 것은 종료 코드로 보이지 않는다.

| 검산 | 결과 |
| --- | --- |
| 둘째 병합이 내 쪽을 건드렸는가 | `git diff --name-only <병합 전>..HEAD` 와 `git diff --name-only e12c0485..3ad4f3f6` 가 **같은 14 경로**(전부 main 쪽) |
| 둘째 병합이 main 쪽을 보존했는가 | `git diff --stat 3ad4f3f6 HEAD -- web reports/evidence/m9/opening-harness` **빈 출력** |
| 충돌 표지 잔존 | 소스·정책·문서 전 트리에서 충돌 표지를 담은 파일 **0** |
| 첫 병합이 실제로 충돌한 경로 | **둘뿐** — `config/quality/gate-tests.properties` · `reports/evidence/m6/closure/open-inventory.md`. import 삭제 40 파일은 main 쪽 변경과 겹치지 않았다 |
| 등재 장부 합집합 | 블록 15 · 행 **524** = 내 쪽 521 ∪ main 쪽 521, `lost=0 extra=0`. 더한 쪽은 main 3(app) · 나 3(build-logic) |
| 같은 판독기의 **양성 대조** | `df4976cc` 에서는 어긋난 블록 셋(`app`·`decision`·`procurement`)을 그대로 지목한다 — 「위반 0」이 판독기의 침묵이 아님을 가른다 |
| `open-inventory.md` 절 계수 | `§1`~`§9` 각 **1**(중복 0). main 이 들여온 §8 과 내 §9 가 나란히 섰고, 병합이 잃은 §9 앞 빈 줄은 되돌렸다 |
| 마이그레이션 번호 | 병합 트리는 **V1~V24 중 22 개**(결번은 은퇴 둘). 들어오는 V25~V27 은 아직 main 에 없다 |

## 병합 트리에서 게이트 전수 재실측 — **`--rerun` 강제**

첫 재실행에서 전수 소스 주행 게이트 넷이 **UP-TO-DATE** 로 돌아왔다(실행 58 · UP-TO-DATE 145).
`UP-TO-DATE` 는 측정이 아니므로 **그 넷을 `--rerun` 으로 강제**해 다시 쟀다 — 42 task 전부 실행,
`BUILD SUCCESSFUL`, 리포트 mtime 이 전부 그 실행 시각이다.

| 게이트 | 병합 트리의 핵심 결과 한 줄 |
| --- | --- |
| `unusedImportGate` 외 둘 | 모듈 열 `files` 합 **1189** · build-logic **87** · 느슨한 스크립트 **4** — 전부 `unused=0` |
| `jarContentGate` | app `archives=2 entries=657 judged=558 exempt=99` · adapters `archives=1 entries=488 judged=488 exempt=0` · `violations=0` |
| `domainSourceReferenceGate` | adapters `files=154 references=1216 scopedFiles=22 memberCalls=5042 violations=0` |
| `gateRegistrationGate` | 장부 열하나 전수 exit 0(population 합 519 · adapters `excluded=2`) |
| `migrationOrderGate` | 소유 모듈 `files=22 violations=0`; 비-소유 아홉은 `files=0` |
| `cpdCheck`·`cpdReportPresenceGate` | 전 모듈 실행(캐시 아님) · 중복 0 |

**게이트가 main 의 병합분을 실제로 재판하는가**는 수로 보인다 — adapters 의 `memberCalls` 가
**4994 → 5042** 로 움직였다(main 이 들여온 adapters test 코드만큼). 리포트가 「UP-TO-DATE 라서 옛
숫자를 재인용」한 것이 아니라는 자리다.

전 모듈 `compileKotlin`·`compileTestKotlin` 과 네 모듈·build-logic 의 ktlint·detekt, `:build-logic:test`
모두 exit 0 — **import 169 줄 제거가 main 병합분과 함께서도 거동 중립임**의 근거다.

## 복원 원천의 이동 — `df4976cc` → `3ad4f3f6`

착수 때의 `df4976cc` 는 **병합 뒤에는 경계로 쓸 수 없다**. 그 경계로 세면 병합이 들여온 남의 레인
커밋까지 세어 **162 경로**가 나오고 그 중 **82 가 내 것이 아니다**(web · usecase · 다른 slice 의
evidence). 복원 원천이자 경계는 **이 브랜치가 실제로 병합한 main 의 고정 SHA** `3ad4f3f6` 이고, 그
경계에서 목록은 **80 경로**이며 등식이 양방향으로 빈 출력이다.

## 수정 라운드 1 — 실측 (verifier B-1·H-1·H-2 · 리뷰 HIGH 1 · MEDIUM 6 · LOW 7)

### B-1 — 루트 게이트를 처음으로 돌렸다

앞 라운드의 「게이트 42 task `--rerun` exit 0」은 **모듈 task 만** 셌다. 이 slice 가 루트에 task 둘을
더했는데도 루트 게이트는 한 번도 돌지 않아, 파일 500 줄 한도를 넘긴 convention plugin 이 PR CI 에서만
붉었다. 이번 라운드는 루트 게이트 **열**을 표적에 넣었다 — `buildLogicSizeGate` · `scriptSizeGate` ·
`buildLogicUnusedImportGate` · `scriptUnusedImportGate` · `buildLogicGateRegistrationGate` ·
`buildLogicTypeShapeGate` · `contractGate` · `memberEffectGate` · `leakPatternGate` ·
`conventionCoverageGate`. 전부 실행 + `BUILD SUCCESSFUL`.

크기: convention plugin **543 → 474 줄**, 새 배선 파일 129 줄, included build 스크립트 147 줄.
한도는 올리지 않았다. 함수 축도 같은 게이트가 잡아 두 번 고쳤다(배선 함수 88 줄 → 게이트마다 이름
붙은 함수, test 람다 57 줄 → 이름 붙은 함수 둘).

### H-1 — 차등 실측: 같은 probe, 두 층

`app` main 에 우회 여섯을 한 파일로 심었다(정적 import · 별칭 import · `getProperties` 경유 ·
`ProcessBuilder.environment` · Spring 활성 프로파일 · 통제군 `System.getenv`).

| 층 | 결과 |
| --- | --- |
| 새 바이트코드 축 `:app:environmentAccessGate --rerun` | **BUILD FAILED · `violations=6`** — 여섯 전부 지목(`types` 266→267 · `accesses` 9460→9475) |
| 소스 쌍 축 `:app:domainSourceReferenceGate --rerun` | **BUILD FAILED · `violations=2`** — 별칭과 통제군만(`*.getenv` 를 이 라운드에 더해서 둘이다; 앞 판 쌍 집합이면 통제군 하나뿐이다) |

**2/6 대 6/6** 이 이 축을 바이트코드로 옮긴 근거다. 심기 전 전 모듈은 `violations=0` 이고 모집단은
공허하지 않다 — adapters `types=460 accesses=16608` · app `266/9460` · workflow `682/15650` ·
procurement `254/6519` · decision `178/4498`.

### H-2 · cr M-1 · cr M-6 — 양성 대조 넷

| 심은 것 | 결과 |
| --- | --- |
| `workflow` 에 `V30` (비-소유 모듈) | `:workflow:migrationOrderGate --rerun` **FAILED** — 모듈과 파일을 지목 |
| `app` 에 `V30` (다른 비-소유 모듈) | **FAILED** — 같은 문면 |
| `adapters` 에 `V0` | **FAILED** — 「등식의 범위 밖」 |
| `adapters/…/nested/` 에 `V12` 중복 | **FAILED** — 재귀 열거가 하위 디렉터리를 본다(test 도 같은 열거로 맞췄다) |

### 변이 — 바꿔치우기 여덟, RED 15

커밋 뒤 술어 본문을 **통과 쪽으로 대체**하고 `:build-logic:test --rerun`. 되돌림은 변이 파일만
(`git restore`, 미커밋 산출물 없음을 먼저 확인).

| 변이 | RED |
| --- | --- |
| `EnvironmentAccess` 거부 필터 → 통과 | 7 |
| `MigrationOrder.ownershipViolations` → 빈 목록 | 1 |
| `MigrationOrder.emptinessViolations` → 빈 목록 | 1 |
| `MigrationOrder` 범위 밖 가지 → 빈 목록 | 1 |
| `UnusedImports.deadSyntaxFqns` → 빈 목록 | 1 |
| 면제 축을 옛 짧은 이름 의미로 되돌림 | 1 |
| `GateRegistration` 정렬 단언 → 통과 | 1 |
| `archiveEmptinessFailure` → `null` | 2 |

**첫 시도의 변이 하나는 초록이었고 그것이 변이의 결함이었다.** `archiveCount == 0` 가지만 끄면 입력 0
인 입력이 **형제 가지**(`judged == 0`)에 걸려 같은 메시지를 내므로 test 가 통과한다. 함수 전체를
`null` 로 **대체**하자 둘 다 RED 다 — 더하기·부분 변이는 「방어 있음」과 「측정 못 함」을 같은 값으로
보여 준다(등재된 교훈과 같은 축).

## 수정 라운드 2 — 중첩 jar 축 · main 둘째 병합

### 사각 재현 → 닫음 (v M-1)

| # | 실측 | 결과 |
| --- | --- | --- |
| 1 | 심기 전 사각 재현 — `bootJar { classpath(files(sneak.jar)) }` 심고 `:app:jarContentGate --rerun` | **초록**(`judged=558`, 심기 전과 동일)인데 `BOOT-INF/lib/sneak.jar` 가 아카이브와 `classpath.idx` 에 **둘 다** 있다 |
| 2 | 새 술어 stub + test | **RED 3/5**(통과한 둘은 빈 목록을 단언하는 음성 대조) |
| 3 | 구현 뒤 양성 대조(같은 심은 jar) | **FAILED** — 처음엔 **둘**을 지목했고 그중 하나가 Boot 자신의 도구 jar 였다(실측으로 나온 면제 대상) |
| 4 | 면제 접두 뒤 양성 대조 | **FAILED · `sneak.jar` 하나만** |
| 5 | 음성 대조(심은 것 제거, 전 모듈) | **BUILD SUCCESSFUL** — app `archives=2 nestedJars=102 runtimeJars=109`, 나머지 아홉 `nestedJars=0` |
| 6 | 변이(술어 전체를 통과 쪽으로 대체) | 23 중 **4 FAILED** |
| 7 | Configuration Cache | 문제 계수 **0** — 해석된 configuration 을 입력으로 받는 것이 CC 를 흔들지 않았다(팀장이 지목한 중단 조건) |

중첩 0 은 열 모듈 중 아홉의 정상 상태라 이 축에는 비-공허 단언을 두지 않고 리포트가 중첩 수와 runtime
수를 함께 센다.

### main 둘째 병합 (`97ed49c4`, PR #134) — 겹침 하나를 계수로 검산

겹친 경로는 `config/quality/gate-tests.properties` 하나다. 충돌 0 자동 병합이라 **계수로** 봤다.

| 검산 | 결과 |
| --- | --- |
| 등재 행 집합 | base **521** · 내 쪽 **525**(+4) · main 쪽 **521**(±0) · 병합 **525** — `lost=0 extra=0` |
| 기대 병합 == 실제 | 기대 525 == 실제 525, 양방향 차집합 빈 출력 |
| 블록 모양 | 블록 **15** 전부 정렬 + 중복 0 |
| main 쪽 변경 보존 | main 의 변경은 **주석 문단 하나**였고 병합 트리에 한 번만 있다(접힘 0). 비슷한 옛 문면이 남은 것은 **다른 class 의 문단**이라 접힘이 아니다 |
| 새 정렬 게이트 실행 | `gateRegistrationGate --rerun` 모듈 **열** 전부 실행 · 실패 0 |
| 내 쪽 건드림 | `git diff --name-only <병합 전>..HEAD` == main 의 9 경로 |

## 수정 라운드 3 — 두 축을 다시 닫고, main 셋째 병합

### H-1 차등 — 같은 probe, 앞 판 술어와 지금 술어

`app` main 에 여덟 형태를 한 파일로 심었다: callable reference(`System::getenv`) · 익명 object ·
suspend 람다(`withContext`) · 하위 타입 수신자(`ConfigurableEnvironment.activeProfiles`) · 같은 능력의
다른 이름 넷(`Boolean#getBoolean`·`Integer#getInteger`·`Long#getLong`·`RuntimeMXBean#getSystemProperties`).

| 레그 | 결과 |
| --- | --- |
| **앞 판 재현**(익명 필터 + Spring 정확 좌표 + 등가 멤버 없음) | **BUILD SUCCESSFUL · `violations=0`** — 여덟 전부 통과. `types=267 anonymousTypes=0` |
| **지금** | **FAILED · `violations=8`** — 여덟 전부 지목. `types=283 anonymousTypes=16 accesses=9643 denied=10` |

**0/8 → 8/8** 이 필터 제거와 `*#멤버` 의 근거다. 익명 클래스 셋의 귀속은 감싼 쪽으로 올라가 문면이
`EnvSniffProbe2 (안쪽 EnvSniffProbe2$callableRef$1)` 꼴로 **고칠 자리와 실제 자리를 둘 다** 든다.
`anonymousTypes` 를 리포트에 세어 앞 판이 **빼고 있던 모집단**이 수로 보이게 했다.

### H-2 · M-1 양성 대조

| 심은 것 | 결과 |
| --- | --- |
| `.zip`·대문자 `.JAR`·면제 이름 사칭(`…-evil.jar`) | 술어가 로더 접두 기준이 되고 면제가 정확 이름이 되어 **전부 위반**(순수 test 넷이 그 셋을 각각 고정) |
| `processResources { from("src/main/extra") { into("db/migration") } }` + `V30` | `:adapters:migrationOrderGate --rerun` **FAILED** — V26~V29 결번 지목(main 이 V25 를 들여온 뒤) |

### B-1 — 스크립트 lint 를 표적에 넣었다

PR CI 가 `:build-logic:ktlintKotlinScriptCheck` 에서 붉었다(앞 라운드 LOW-6 수정이 선언 앞 빈 줄을
빼먹었다). **앞 라운드 B-1 과 같은 꼴**이다 — 고친 파일이 `.kts` 인데 표적 lint 열에 스크립트 lint 가
없었다. 빈 줄 하나로 고치고 그 task 를 표적에 넣었다(이 라운드 실측 exit 0).

### main 셋째 병합 (`56bd33ea`, PR #137 paper-bid)

겹친 경로 일곱 가운데 **Kotlin 넷은 이 slice 의 import 정리가 만진 파일**이라, 병합 전에 **양쪽 본문을
전수로 대조**했다 — 내가 지운 이름(`AccountId`·`Instant`·`NotificationSweepCase`·`FieldConcept`·
`KONEPS_COLLECTION_POLICY`·`Resolution`·`tag`) 가운데 main 쪽 본문이 쓰는 것은 **0** 이다. 병합 뒤 그 네
파일의 중복 import 도 0 이다.

| 검산 | 결과 |
| --- | --- |
| `architecture-policy` 값쌍 | base 1278 · 내 쪽 1351 · main 1280 · 병합 1353 — 기대==실제, `lost 0 extra 0` |
| `gate-tests` 값쌍 | base 522 · 내 쪽 526 · main 525 · 병합 529 — 기대==실제, `lost 0 extra 0` |
| `capability-map` | 병합 == main + 내 32 줄, main 쪽 삭제 0 |
| 마이그레이션 | **V25 가 들어왔다** — 정책 주석이 예고한 순서 그대로이고 집합은 `1..25 ∖ {10,11}` 로 결번 0 |
| 새 정렬 게이트 | 병합된 장부에 `gateRegistrationGate --rerun` 모듈 열 실행 · 실패 0 |

### 내 실수 하나를 기록한다

`policy.version` 이 10 에 머물러 있었다 — **수정 라운드 2 의 bump 가 적용되지 않았다**(검사 없는 문자열
치환이 앵커를 못 찾고 조용히 빠졌고 그 라운드 커밋 메시지는 bump 를 적었다). 라운드 3 의 첫 커밋 제목
「정책 v12」도 그 전제 위에 서 있어 그 시점에 거짓이었다. 이력을 되쓰지 않고 한 번의 bump(→ 11)가 두
라운드를 덮게 하고 버전 주석이 경위를 적는다. 이 라운드의 문면 교체는 **전부 앵커 단언**을 붙였다.

### H-2 양성 대조 — 게이트 층(네 이름을 실제로 심어서)

순수 test 는 **술어**를 고정하고 판독기(아카이브를 훑는 쪽)는 고정하지 않는다. 그래서 네 이름을 실제
배포물에 심어 게이트를 돌렸다 — `bootJar { classpath(files(…)) }` 로 `sneak.jar` ·
`spring-boot-jarmode-tools-evil.jar` · `sneak.zip` · `sneak2.JAR`.

`:app:jarContentGate --rerun` → **FAILED, 넷 전부 지목**. `nestedJars` 는 102 → **106**(앞 판 판독기는
`.jar` 로 끝나는 둘만 세어 104 였고 지목은 하나였다). 넷 모두 `BOOT-INF/lib/` 밑에 있고 로더는 거기서
확장자를 보지 않는다.

### 변이 — 바꿔치우기 셋, RED 3 (그리고 헛변이 하나를 기록한다)

| 변이 | RED |
| --- | --- |
| `EnvironmentAccess.isDenied` 의 `*#멤버` 가지 제거 | 1 (Spring **상위 타입**(`Environment`) 수신자 test — 라벨 정정, 리뷰 LOW-4: 그 RED 는 상위 타입 입력에서 났고 하위 타입을 입력으로 둔 test 는 그때 **없었다**. 라운드 4 가 그 test 를 더했다) |
| 건너뛰기 목록을 test 에 넘기지 않음(선언 ↔ 판독 끊기) | 1 (죽은 면제 0 test 의 비-공허 단언) |
| 중첩 면제를 **느슨한 접두 일치**로 대체 | 1 (사칭 test) |

**첫 시도의 셋째 변이는 초록이었고 그것이 변이의 결함이었다.** 비교만 `in` → `startsWith` 로 바꾸면
정책 값이 **정확 이름**이라 두 비교가 같은 답을 낸다(`"…-evil.jar".startsWith("…-4.1.1.jar")` 는 거짓).
이 축의 방어는 **「정확한 값 + 동등 비교」 쌍**이고 한쪽만 흔드는 변이는 거동을 바꾸지 않는다 — 값이
접두였던 앞 판 상태를 재현(`take(25)`)해서야 RED 가 났다. 라운드 2 의 형제 가지 헛변이와 같은 계열이고,
같은 교훈을 두 번째로 현장에서 쓴 자리다.

익명 클래스 필터 제거는 **task 층**이라 순수 test 로는 잡히지 않는다 — 그 축의 변이 실측은 위 H-1
차등(앞 판 재현 레그 0/8)이 대신한다. 같은 이유로 중첩 판독기와 마이그레이션 입력 교체도 게이트 층
양성 대조가 그 자리를 진다. **라운드 4 가 그 셋 중 둘을 상시 회귀로 바꿨다**(아래) — 그 일회 측정은
세션과 함께 죽는다는 지적(리뷰 M-1)이 옳았다.

## 수정 라운드 4 — 중첩 축을 네 축으로 · main 다섯째 병합

### 양성 대조 — 우회 셋을 **실제 배포물에** 심었다 (게이트 층)

`app/build.gradle.kts` 의 `bootJar` 에 진짜 zip 셋을 심고 `:app:jarContentGate --rerun`.
심은 것은 verifier 가 쓴 U1·U2·U3 그대로다.

| 심은 것 | 로더가 싣는가 | 결과 |
| --- | --- | --- |
| `BOOT-INF/lib/x/spring-boot-jarmode-tools-4.1.1.jar` — **면제와 같은 정확 이름**, 하위 디렉터리 | 싣는다 | **FAILED** — 경로 축 + 다중도 축 |
| `BOOT-INF/lib/y/kotlin-reflect-2.4.10.jar` — **선언된 이름**, 하위 디렉터리 | 싣는다 | **FAILED** — 경로 축 + 다중도 축 |
| `BOOT-INF/ext/sneak.jar` — 뿌리 밖 | 런처를 바꾸면 싣는다 | **FAILED** — 뿌리 밖 축(zip 서명) |

판정 수: `nestedJars 102 → 104` · `nestedOutside 0 → 1` · 위반 **5 줄**(경로 2 · 다중도 2 · 뿌리 밖 1).
`unzip -l` 로 셋이 실제 아카이브 안에 있음을 따로 확인했다.

### 차등 — 같은 심은 셋을 **앞 판 술어**로 재판한다

술어만 앞 판(이름 **집합 소속** 하나)으로 **대체**하고 심은 셋을 그대로 둔 채 다시 돌렸다.

| 레그 | 결과 |
| --- | --- |
| **앞 판 술어** + 심은 셋 | **BUILD SUCCESSFUL · 리포트 위반 줄 0** (`nestedJars=104 nestedOutside=1` — 세기는 세는데 판정하지 않는다) |
| **지금** + 심은 셋 | **FAILED · 5 줄** |
| 음성 대조(심은 것 제거, 전 모듈) | **BUILD SUCCESSFUL** — app `nestedJars=102 nestedOutside=0 runtimeJars=109`, 나머지 아홉 `0` |

**0 → 5** 가 이 라운드의 근거다. 같은 레그에서 `:build-logic:test --rerun` 은 **429 중 8 FAILED** —
경로·다중도·죽은 면제·런처·뿌리 밖·뿌리 밖 음성 대조·판독기 연결 test 가 전부 RED 다. 판독기 층까지
재는 test(`@TempDir` 에 진짜 아카이브를 만들어 `readNestedArchive` 를 거쳐 판정)가 그중 둘이라,
**경로를 버리는 변이가 이제 상시로 붉는다**(리뷰 M-1 ⓑ).

### 변이 — 정책 두 자리와 모집단 한 자리

| 변이 | 결과 |
| --- | --- |
| `jar.nested-lib-roots` 를 **비움**(지우지 않고) | `:app:jarContentGate` **FAILED** — 「값이 비어 있다 — 이 축은 빈 목록으로 꺼지지 않는다」. 앞 판은 모집단 0 으로 **조용히 초록**이었다(리뷰 M-2) |
| 허용 쌍의 멤버를 대상 없는 것으로 바꿈 | `:app:environmentAccessGate` **FAILED** — 「허용한 좌표에 대상이 없다」 |
| `filterNot { it.isAnonymousClass }` **복귀**(앞 판 그대로) | `environmentAccessGate` **네 모듈 FAILED** — shared-kernel 73/71 · workflow 702/686 · adapters 520/492 · app 289/276 |

마지막 변이의 **범위가 이 축의 현재 크기**다: 익명 클래스가 0 인 여섯 모듈에서는 같은 변이가 초록이고,
그 여섯에서는 변이가 거동을 바꾸지 않는다(뺄 것이 없다). 리뷰 M-1 이 처방한 바닥값(「익명 ≥ 1」)이
**그 여섯을 상시로 붉힐 것**이라는 것도 같은 수에서 나온다 — 그래서 바닥값 대신 등식을 썼다.

### main 다섯째 병합 (`3101035f`, PR #142 CVE · #127 str12 · #136 adr07-pitest)

겹친 경로 **다섯**(`build-logic/build.gradle.kts` · `bidvector.quality-baseline.gradle.kts` ·
`architecture-policy.properties` · `gate-tests.properties` · `capability-map.md`). 손 해소는
**`gate-tests.properties` 한 파일**이고(`--cc` 로 그 한 hunk만 나온다), 내 `MigrationOrderTest` 행과
main 의 `Mutation*Test` 네 행이 나란히 섰다.

| 검산 | 결과 |
| --- | --- |
| 합집합 보존 | 다섯 경로 모두 **양쪽 추가 줄이 병합 결과에 전부 있다**(missing 0 / 0) |
| 중복 키 | `architecture-policy` 키 156 · `gate-tests` 키 16 — 둘 다 중복 **0**(이어달리기를 펴서 셌다) |
| `policy.version` | main 은 건드리지 않았다(11 그대로) → 이 라운드가 **12** 로 올린다 |

### 병합이 들여온 위반 하나 — 임계 0 축이 처음으로 붉었다

병합 직후 `:app:environmentAccessGate` 가 **FAILED** 했다:
`bidvector.app.wiring.RelayWiring → java.lang.System#getProperty`. PR #127(str12)이 더한 **기동 거부
가드**이고, JDK HttpClient 로그 속성이 켜졌으면 알림 본문이 로그로 샐 수 있어 **뜨지 않는다**.

이 축을 끄지도 좌표를 빼지도 않고, 게이트 문면이 OPS-07 (a) 로 **약속하던** 억제 경로
(`bytecode.env-access.allowed-callers`)를 구현해 정확 쌍 하나만 열었다. 임계 0 축에 허용이 생기는
것이므로 셋을 함께 걸었다 — 와일드카드 없음 · **대상 없는 허용은 위반** · 목록 전체를 test 가 값으로
고정. **이 판단은 운영자 확인 대상**이다(`checklist.md` 제한 21 · OPEN).

### 전건 `check` 를 돌려서야 보인 것 넷 — **표적만으로는 셋이 숨어 있었다**

`./gradlew --no-daemon check`(CI job 명령 그대로)를 끝까지 돌렸다. 표적 실행이 전부 초록인 상태에서
**네 자리가 붉었고**, 그중 셋은 이 라운드의 변경이 만든 것이 아니라 **병합이 들여온 것**이다. 앞 두
라운드의 차단이 전부 이 `check` 였던 이유가 여기 있다.

| # | 붉은 자리 | 원인 | 처분 |
| --- | --- | --- | --- |
| 1 | `:buildLogicSizeGate` | 새 test 가 `JarContentPolicyTest` 를 **521 줄**로 올렸다(한도 500) | **판정 대상으로 나눴다** — class 엔트리 판정은 그대로, 중첩 아카이브 축은 `NestedArchivePolicyTest`(정책 타입이 둘이니 test 도 둘). 크기 회피용 기계 분할이 아니다. 149 + 399 줄. **같은 커밋에서 `gate-tests.properties` 에 등재**했다 |
| 2 | `:build-logic:ktlintMainSourceSetCheck`·`…TestSourceSetCheck` | 새 코드의 형식 셋(함수 서명 한 줄 · 인자 줄바꿈 · 최대 길이) | 형식만 고쳤다. 긴 리터럴 둘은 이름 붙인 상수로(`PROPERTIES_LAUNCHER`·`CLASS_MAGIC`) |
| 3 | `:adapters:domainSourceReferenceGate` | **알려진 제한 8 이 실현됐다** — 범위 축 허용 뿌리가 두 자리에 살고, main(PR #127)이 `RelayAdapterDependencyTest.ALLOWED_ROOTS` 에만 `bidvector.adapters.notification` 을 더했다 | 정책 쪽에 값 복사. 그리고 **여덟 축 전부를 기계로 대조**했다 — `relay` 하나만 어긋났고(test만=`{bidvector.adapters.notification}`) 나머지 일곱은 양방향 차집합이 빈 집합이다 |
| 4 | `:app:domainSourceReferenceGate` | 바이트코드 축에만 허용을 열어 **소스 1차 그물이 같은 호출에서 붉었다** | 두 층이 **같은 키 하나**(`bytecode.env-access.allowed-callers`)를 읽게 했다. 목록을 둘로 두면 억제가 한 층에서만 들어 OPS-07 (a) 가 약속한 경로가 실제로는 못 쓰는 경로가 된다 |

3·4 는 **표적 실행으로는 영원히 보이지 않았을 자리**다(둘 다 내가 이번에 만진 task 가 아니거나,
만졌어도 그 모듈을 표적에 넣지 않았다). 전건을 끝까지 돌린 것이 그 둘을 낸 유일한 이유다.

### 전건과 clean-tree

| 명령 | 결과 |
| --- | --- |
| `:build-logic:test --rerun`(전건) | 429 tests · 0 failed(분할·새 test 전) |
| `jarContentGate environmentAccessGate migrationOrderGate --rerun`(전 모듈) | **BUILD SUCCESSFUL** — app `judged=578 exempt=99 nestedJars=102 nestedOutside=0` · `classFiles=289 types=289 anonymousTypes=13 denied=10 allowed=1 violations=0` |
| `./gradlew --no-daemon check`(CI job 명령 그대로) | **BUILD SUCCESSFUL in 33m 5s** — 435 actionable tasks(388 executed · 9 from cache · 38 up-to-date) |
| `./gradlew --no-daemon leakPatternGate --rerun` | exit 0 (라운드 내내 매 실행에서) |

전건을 **끝까지** 돌린 것은 이 slice 에서 처음이다(앞 라운드들은 로컬에서 표적만 돌리고 전건은 PR CI 를
정본으로 삼았고, 그 전건이 두 번 붉어 두 라운드를 썼다). 33 분은 CI 의 `check` job(35m14s)과 같은
자릿수다.

변이는 **전부 커밋 뒤에** 돌렸고 되돌림은 백업 사본 복사다(`checkout --` 를 쓰지 않았다 — 미커밋
산출물을 함께 지우는 자리). 각 되돌림 뒤 `git status --porcelain` 빈 출력. 양성 대조: 변이를 심은
직후 같은 명령이 그 경로를 냈다.

## 수정 라운드 5 — 배포 경로의 적재 선언을 게이트로 · main 여섯째 병합

### 왜 이 라운드가 열렸나 — 앞 판 네 축은 작동했고, 모델이 틀렸다

verifier r5 는 수정 라운드 4 의 네 축(경로·다중도·선언·뿌리 밖)이 **주장대로 작동한다**고 쟀다(우회
열둘 전부 RED). 그런데 **그 축이 기대는 로더가 배포 경로의 로더가 아니었다.** 출하 이미지는
`JarLauncher` 로 뜨지 않는다 — `docker/app.Dockerfile` 이 `jarmode=tools extract --layers` 로 풀고
평범한 `java -jar` 로 띄운다.

읽는 속성이 무엇인지는 **짐작하지 않고 셌다**: 배포물 안의 도구 jar 를 풀어 `javap -p -c -constants`
로 상수 풀을 훑었다. manifest 를 읽는 자리는 `IndexedJarStructure`·`IndexedLayers` 둘이고 이름은
`Start-Class`·`Spring-Boot-Classes`·`Spring-Boot-Lib`·`Spring-Boot-Classpath-Index`·
`Spring-Boot-Layers-Index` **다섯**이다. 게이트가 단언하는 집합을 그 다섯 + `Main-Class` 로 맞췄다.
같은 판독으로 **그 도구가 index 에 없고 클래스 뿌리 밑도 아닌 엔트리는 복사하지 않는다**는 것도
확인했다(`resolve` 의 마지막 분기가 `null`). 그래서 index 등식이 서면 실리는 집합이 닫힌다.

### 양성 대조 — verifier 의 재현을 **그대로** 심었다 (게이트 층)

`bootJar` 에 셋을 함께 심고 `:app:bootJar :app:jarContentGate --rerun`. 셋 다 **게이트를 끄지 않는**
변경이다(manifest 속성은 플러그인이 `putIfAbsent` 라 사용자 값이 이기고, `doLast` 도 게이트 밑이다).

| 심은 것 |
| --- |
| `manifest { attributes("Spring-Boot-Lib" to "BOOT-INF/") }` |
| `BOOT-INF/ext/evil.jar` — 앞에 셸 머리글을 붙인 진짜 jar |
| `doLast` 로 `classpath.idx`·`layers.idx` 에 한 줄씩 |

**BUILD FAILED · 위반 3 줄** — 뿌리 밖(EOCD 판독) · `Spring-Boot-Lib` 속성 · classpath index 등식.
셋이 **서로 다른 축**이라 하나를 되돌려도 나머지가 남는다.

같은 app.jar 에 Dockerfile 과 같은 명령을 돌려 **그 실패가 실제 적재에 대응함**을 확인했다:
`java -Djarmode=tools -jar application.jar extract --layers --destination out` exit 0 →
`out/dependencies/lib/ext/evil.jar` 가 생기고 런처 jar 의 `Class-Path` 103 번째 항목이
`lib/ext/evil.jar` 다.

### 차등 — 같은 심음을 **앞 판 술어**로 재판한다

`JarContentPolicy.kt`·`JarContentGateTask.kt` 를 `edede50b`(verifier r5 가 판정한 판)으로 바꿔치우고
`BootArchivePolicy.kt` 를 지운 뒤, 심은 셋을 그대로 두고 다시 돌렸다.

| 레그 | 결과 |
| --- | --- |
| **앞 판 술어** + 심은 셋 | **BUILD SUCCESSFUL** · `nestedJars=102 nestedOutside=0` — 통제군과 **같은 수**다. 뿌리 밖 판정이 앞 네 바이트라 그 jar 는 위반도 면제도 아닌 **미계수**였다 |
| **지금** + 심은 셋 | **FAILED · 3 줄** |
| 음성 대조(심은 것 제거) | **BUILD SUCCESSFUL** |

### 허용 축 — 같은 모양의 차등

`RelayWiring` 에 **다른 메서드의 다른 속성 읽기**를 하나 더했다(verifier r5 (2) 의 첫 변이).

| 레그 | 결과 |
| --- | --- |
| **지금** | `:app:environmentAccessGate` **FAILED** — 「허용 하나가 호출 2건을 연다」 |
| **앞 판 술어**(`EnvironmentAccess.kt` 만 `edede50b` 로) | **BUILD SUCCESSFUL** · `accesses 10141 → 10142 · violations=0` — 접근을 **세고도** 판정하지 않는다 |

오늘 그 쌍의 바이트코드 참조가 하나뿐임은 따로 확인했다
(`javap -c RelayWiring.class | grep -c 'java/lang/System.getProperty'` → 1).

### 변이 — 바꿔치우기 넷 (더하기가 아니라 대체)

| 변이 | 결과 |
| --- | --- |
| `BootArchivePolicy.judge` 가 **위반을 내지 않게** (수는 그대로 센다) | `:build-logic:test` **452 중 11 FAILED** — 속성 축 넷 · index 축 넷 · 소유 모듈 바닥값 · 판독기 연결 둘 |
| 판독기의 zip 판정을 **앞 판 술어**(앞 네 바이트)로 | **1 FAILED** — 「앞에 바이트를 붙인 zip 도 아카이브로 읽힌다」. 이 한 test 가 앞 판 제한 20 ⓐ 를 잠근다 |
| 허용 **횟수 단언 제거**(1 이상이면 통과) | **1 FAILED** — 「허용 하나는 호출 하나까지다」 |
| 정책 두 값(`jar.boot-archive-module=nosuch` · `jar.boot-start-class` 를 다른 FQN) | `:build-logic:test` **1 FAILED**(정책 값 고정 test) · `:app:jarContentGate` **FAILED**(`Start-Class` 속성 축이 실제 배포물에서 붉는다) |

마지막 줄이 **「정책 한 줄로는 꺼지지 않는다」의 실측**이다: 값을 바꾸면 test 가 붉고, 그 test 를
맞춰 고치면 이번에는 **실제 배포물의 manifest 와 어긋나** 게이트가 붉는다.

변이는 전부 **커밋 뒤에** 돌렸고 되돌림은 백업 사본 복사다(`checkout --` 금지 — 미커밋 산출물을 함께
지우는 자리). 각 되돌림 뒤 `git status --porcelain` 빈 출력.

### main 여섯째 병합 (`70406318`, PR #128 award-ingest)

자동 병합이고 **손 해소 줄 0**(`git show --cc` 가 아무 hunk 도 내지 않는다). 충돌 0 인 병합이 같은
본문을 접을 수 있으므로 계수로 검산했다 — merge-base `3101035f` 기준으로 양쪽이 **더한 줄**이 병합
결과에 전부 있는지.

| 겹친 경로 | 내 추가 | main 추가 | 유실 |
| --- | --- | --- | --- |
| `architecture-policy.properties` | 174 | 23 | 0 / 0 |
| `gate-tests.properties` | 6 | 5 | 0 / 0 |
| `capability-map.md` | 27 | 3 | 0 / 0 |

키 중복도 셌다(이어달리기를 펴서): `architecture-policy` 162 키 · `gate-tests` 16 키, 둘 다 중복 **0**.
`policy.version` 은 main 이 건드리지 않았다(8 에 머물러 있다) → 이 라운드가 **13** 으로 올린다.
main 이 `collection.usecase.type` 을 `…types`(목록)로 바꾼 것은 **대체**이고 유실이 아니다.

### 전건 `check` — **실측 HEAD `f3969ce7`**

| 명령 | 결과 |
| --- | --- |
| `./gradlew --no-daemon check`(CI job 명령 그대로) | **BUILD SUCCESSFUL in 32m 34s** — 435 actionable tasks(388 executed · 9 from cache · 38 up-to-date) |
| `:build-logic:test --rerun` | 452 tests · 0 failed |
| `jarContentGate environmentAccessGate migrationOrderGate --rerun`(전 모듈) | **BUILD SUCCESSFUL** — app `judged=594 exempt=99 nestedJars=102 nestedOutside=0 bootArchives=1 runtimeJars=109` · `classFiles=297 types=297 anonymousTypes=14 accesses=10141 denied=10 allowed=1 violations=0` · 나머지 아홉 `nestedJars=0 bootArchives=0` |
| `leakPatternGate --rerun` | exit 0 |

전건은 **이 라운드가 만든 세 자리**를 냈고(표적만으로는 보이지 않았다) 별 커밋으로 고쳤다 —
`gate()` 59 줄(한도 50) · `JarContentGateTask.kt` 함수 12 개(한도 11) · `MagicNumber` 아홉. 둘째를
**판독기 파일 분리**로 푼 것이 이 라운드의 유일한 구조 변경이고, 나눈 선은 크기가 아니라 다루는 것
(Gradle 선언 입력 ↔ zip 포맷)이다. **실측 HEAD 뒤의 커밋은 evidence 뿐**이고, 마지막 HEAD 의 전건
결과 정본은 verifier 와 PR 조치 코멘트다(evidence 에 넣으려는 시도가 커밋을 또 낳는다).

### clean-tree

`git status --porcelain -- <in_scope 개별 인자>` 빈 출력. 양성 대조 1 회(비파괴 절삭) — 변이를 심은
직후 같은 명령이 그 경로를 냈고, 백업 사본 복사로 되돌린 뒤 다시 빈 출력이다.
