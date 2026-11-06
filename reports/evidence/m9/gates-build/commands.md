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
