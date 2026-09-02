# commands — M1 / 1A

실행 명령과 종료 코드. **출력 전문을 붙이지 않는다** — `핵심 결과`는 한 줄이고 감사자는 명령을
다시 돌린다. 수·좌표가 필요하면 그 셈을 내는 명령을 가리킨다.

- `C-0` 이 slice 의 커밋 집합: `git log --oneline 6b03c75..HEAD`
- 재현 전제: JDK 21 이 `JAVA_HOME`. Gradle 은 wrapper 가 내려받는다(설치본 불필요).

---

## 2026-09-02T08:15:20Z — RED

- cmd: `./gradlew :app:test --tests '*ArchitectureGate*'`
- exit: 1
- 핵심 결과: 11 tests, 5 failed — 위반 fixture 가 없어 음성 단언 다섯이 전부 실패한다

## 2026-09-02T08:26Z — GREEN

- cmd: `./gradlew :app:test --tests '*ArchitectureGate*'`
- exit: 0
- 핵심 결과: 11 tests, 0 failed — 같은 규칙 값이 fixture 루트에서 다섯 위반을 전부 잡는다

## 2026-09-02T08:30Z — 게이트가 실제로 잡는가 (임시 위반을 심고 되돌림)

각 실험은 위반 파일을 만들어 명령 하나를 돌리고 즉시 지웠다. 재현은 각 행의 「심은 것」을
그대로 만들면 된다. `git status` 는 실험 뒤 비어 있다.

| # | 심은 것 | cmd | exit | 핵심 결과 |
| --- | --- | --- | --- | --- |
| `E-1` | 501 줄짜리 `.kt` | `./gradlew :settlement:sizeGate` | 1 | `파일 500 줄 한도 초과 1건` |
| `E-2` | 54 줄짜리 함수 | `./gradlew :settlement:detekt` | 1 | `LongMethod ... The maximum length is 50` — **도구 기본값은 60 이다** |
| `E-3` | 포맷 위반 한 파일 | `./gradlew :settlement:ktlintMainSourceSetCheck` | 1 | 같은 파일에 9 건 지적 |
| `E-4` | `qualification` 에 `spring-core` **의존만** 추가(사용 없음) | `./gradlew :qualification:moduleDependencyGate` | 1 | `모듈 'qualification' 의 의존이 경계를 넘는다` — 2 차 그물(arch test)은 못 보는 상태를 1 차가 잡는다 |
| `E-5` | 위에 더해 `ClassPathResource` 를 **실제 사용** | `./gradlew :app:test --tests '*ArchitectureGateTest*'` | 1 | `도메인 모듈이 프레임워크에 의존하지 않는다` FAILED — 위반 2 건 |

## 2026-09-02T08:34Z — 툴체인 실측 (scope.md 부록 B 의 미검증 항목)

| # | 물음 | cmd | exit | 핵심 결과 |
| --- | --- | --- | --- | --- |
| `T-1` | KGP 2.4.10 × Gradle **9.6.1**(KGP 문서 상한 9.5.0 초과) | `./gradlew --no-build-cache clean check` | 0 | 통과 — **후퇴하지 않았다.** 9.5.1 + detekt alpha.5 로 내려갈 필요가 없었다 |
| `T-2` | detekt 2.0.0-alpha.6 × configuration cache (#9390 이 alpha.6 에 남았는가) | `./gradlew clean && ./gradlew detekt && ./gradlew detekt` | 0 | 2 회차가 `Configuration cache entry reused` — **남아 있지 않다** |
| `T-3` | ktlint 1.8.0 의 Kotlin 2.4 소스 파싱 | `E-3` 과 같은 명령 | 1(의도) | 파싱해서 지적을 냈다 — 파서 지연 없음 |
| `T-4` | Boot 4.1.1 BOM 이 Flyway 버전을 관리하는가 (`ADR 0004` §6 미확인) | `./gradlew :app:compatibilitySmoke` | 0 | 관리한다 — 해석된 버전은 리포트가 낸다 |
| `T-5` | 카탈로그가 BOM 을 이기는가 (BOM 은 kotlin 2.3.21 / jupiter 6.0.3) | 같은 명령 | 0 | 이긴다 — 최고 버전 승리로 카탈로그 값이 선택된다 |

**T-6 — Spotless 8.10.1 은 configuration cache 를 견디지 못한다.** `./gradlew --no-build-cache
clean check` 가 **무작위 모듈 하나**에서 `LINE_UNDEFINED ktlint(InvocationTargetException)` 로
실패했고, `--no-configuration-cache` 에서는 통과했다. ktlint **1.6.0 · 1.7.1 · 1.8.0** 에서 동일하고
`--no-parallel` 에서도 재현된다 — ktlint 버전 축이 아니다. 조사 노트가 지목한 alpha 위험은
detekt 였으나(`T-2`) **실제로 깨진 것은 Spotless 다.** 처리는 아래 「계약 갱신」.

## 2026-09-02T09:16Z — Codex 1차 라운드의 preflight

정본은 `codex-review-gate` SKILL 이고 여기는 이 라운드의 값만 든다.

| # | 축 | 값 |
| --- | --- | --- |
| `C-1` | 이 저장소 흔적 | 3 |
| `C-2` | stage1 배치 | 0 / 709 (683 → 709 로 배치 재개) |
| `C-3` | 심판 바이너리 핀 | 0.151.0 |
| `C-4` | 주입 누출 · developer 구간 매치 | 0 · 0 |
| `C-5` | hooks | 8 개 / 3 경로 stdout 0 바이트 |
| `C-6` | 방출 · 조기 종료 | 1 · 0 — **B8 이래 첫 무조기 라운드** |

**`C-7` — 이 라운드의 Codex 는 gradle 을 실행하지 못했다.** `~/.gradle` 잠금 · DNS 차단 ·
`worktree add` 거부가 겹쳤다(**격리는 버텼다**). verdict 의 `commands_run` 에 gradle 명령이
있으나 전부 실패했고, 그래서 **다섯 finding 은 전부 정적 판독**이다. 그 사실이 finding 의
타당성을 낮추지 않는다 — 다섯 중 넷은 실행 없이 서는 지적이고 이 라운드가 실측으로 닫았다.
하네스가 오프라인 캐시 탑재를 성문화 중이라 재리뷰부터는 실행 가능해질 예정이다.

## 2026-09-02T11:55Z — Codex 2차 라운드의 preflight

| # | 축 | 값 |
| --- | --- | --- |
| `C-8` | 이 저장소 흔적 | 3 |
| `C-9` | stage1 배치 | 0 / 722 (709 → 722 증가) |
| `C-10` | 심판 바이너리 핀 | 1차와 같다 (`C-3`) |
| `C-11` | 주입 누출 · developer 구간 매치 | 0 · 0 |
| `C-12` | hooks | 8 개 / stdout 0 바이트 |
| `C-13` | 방출(앵커 ∩ 형태) · 조기 종료 | 4 · **3 — 사상 최다.** 그중 9,742 행짜리 approve 가 **첫 gradle 시도 직전**이라 종말 캡처가 사고를 면했다 |

**`C-14` — 2차도 gradle 을 돌리지 못했다. 원인이 1차와 다르다.** 1차의 캐시·쓰기 문제는
하네스 §4b(오프라인 캐시 사본)가 해소했고, 이번에 막은 것은 **seatbelt 의 소켓 생성 금지**다 —
Gradle 이 프로젝트 구성 전에 `FileLockContentionHandler` 의 `DatagramSocket` 을 **무조건**
만들고 거기서 `SocketException: Operation not permitted` 로 죽는다. 플래그로 우회할 수 없다.
**그래서 2차의 두 finding 도 정적 판독이다** — verdict 의 `residual_risks` 가 같은 말을 한다.

## preflight 등재 규칙의 전환

**이 절(3차)까지가 evidence 에 preflight 를 옮겨 적는 마지막 라운드다.** 이후 코드 slice 의
preflight 정본은 심판 레인이 쓰는 **형제 `codex-review-<UTC>.preflight.json`** 이고 여기 `C-` 행을
새로 만들지 않는다(하네스 규칙 C, 2026-09-02). 같은 사실을 두 자리에 두면 한쪽이 낡는다는 규칙이
이 자리에도 적용된다 — **이 문단 자신이 그 실물이다**: 처음 쓸 때 정본을 verdict JSON 이라
적었는데 규칙이 곧바로 형제 파일로 정정됐다(`residual_risks` 는 Codex 소유라 레인이 쓰지 않는다).

## 2026-09-02T13:25Z — Codex 3차 라운드의 preflight

| # | 축 | 값 |
| --- | --- | --- |
| `C-15` | 산출물 열람 | **작동했다** — 커밋된 build report(test XML · Kover XML)를 읽고 대조했다 |
| `C-16` | 조기 방출 | 1 |
| `C-17` | 프롬프트 지시로 방출을 막을 수 있는가 | **없다** — 지시로 닫히지 않는 표면이다 |
| `C-18` | 데몬 잔재 | 사고 있었다 |

**`C-19` — 3차도 gradle 을 돌리지 않았다.** 이번엔 sandbox 제한이 지시로 주어져 시도 자체를
하지 않았고, 대신 **커밋된 산출물을 정적으로 대조**했다(`C-15`). 그래서 두 finding 도 정적
판독이나, **둘 다 실행 없이 서는 지적이고 이 라운드가 실측으로 닫았다.**

**`C-20` — verdict 의 잔여 위험 하나는 이 라운드가 답할 수 없다.** *"`clean check` 산출물에
`qualityBaseline` 보고서가 남아 있지 않아 A-3 을 재대조하지 못했다"* — 맞다. `qualityBaseline`
은 **게이트가 아니라 측정**이라 `check` 에 걸지 않았고(`scope.md` D-6), 그래서 `clean check`
산출물에 남지 않는다. CI 는 별도 단계로 돌려 아티팩트에 올린다(`.github/workflows/ci.yml`).
**설계대로이며 결함이 아니다** — 다만 리뷰어가 `check` 산출물만으로 A-3 을 확인할 수 없다는
사실은 그대로다.

## 2026-09-02T13:50Z — 접근 전환 라운드의 실측

| # | 심은 것 | cmd | exit | 핵심 결과 |
| --- | --- | --- | --- | --- |
| `E-12` | 진짜 domain 모듈에서 `readln()` | `./gradlew :app:test --tests '*ArchitectureGateTest*'` | 1 | allow-list 가 잡는다 |
| `E-12b` | 같은 상태의 1차 게이트 | `./gradlew :decision:moduleDependencyGate` | **0** | **못 잡는다** — 추가 의존이 없다. allow-list 가 필요한 이유 |
| `E-13` | `decision` 에 **선언 없는** 클래스 + JDK HTTP | `./gradlew :decision:packageOwnershipGate` | 1 | `(기본 패키지 — 선언 없음)` |
| `E-13b` | 같은 상태의 arch test | `./gradlew :app:test --tests '*ArchitectureGateTest*'` | **0** | **못 잡는다** — `importPackages("bidvector")` 의 대상이 아니다 |
| `E-14` | `decision` 에 `bidvector.app.sneaky` + JDK HTTP | `./gradlew :decision:packageOwnershipGate` | 1 | app 층으로 위장한 것을 산출물 위치가 잡는다 |
| `E-15` | 허용 목록에서 `kotlin.io` 제외를 걷음 | `./gradlew :build-logic:test` | 1 | 금지 가족이 허용으로 들어오는 것을 출처 대조가 잡는다 |
| `E-16` | 허용된 적 없는 것을 제외 목록에 | `./gradlew :build-logic:test` | 1 | 제외 항목의 뜻이 서는지 본다 |

| `E-17` | domain 모듈에 `data class Won(val amount: Long)` | `./gradlew :app:test --tests '*ArchitectureGateTest*'` | **0** | 컴파일러 삽입 `@NotNull` 을 허용하기 전에는 **exit 1** 이었다 — 게이트가 도메인이 비어 있는 동안만 초록이었다 |
| `E-18` | domain 모듈에 `ProcessBuilder`·`java.util.logging`·`Thread`·`System.getenv`·리플렉션 (각각) | 같은 명령 | **1** (다섯 전부) | 허용을 뿌리로 두었을 때는 다섯 전부 exit 0 이었다 |
| `E-19` | 허용 목록에 `kotlin.io` 를 넣음 | `./gradlew :app:test --tests '*ArchitectureGateCatchesViolationsTest*'` · `:build-logic:test` | 1 · 1 | 음성 단언과 출처 대조가 **둘 다** 죽는다. 이름만 보던 단언은 이때 죽지 않았다(masking) |

| `E-20` | domain 모듈에 `ServiceLoader`·`Timer`(별칭 import)·`StackWalker`·`TimerTask` (각각) | `./gradlew :app:test --tests '*ArchitectureGateTest*'` | **1** (넷 전부) | 정확 패키지가 닫지 못하는 자리 — 한 패키지에 순수 타입과 위험 타입이 섞여 있어 멤버 단위로 가른다. 별칭 import 도 잡힌다(판정 기준이 패키지·클래스 이름이지 import 철자가 아니다) |

`E-12b`·`E-13b`·`E-17`·`E-18` 이 이 라운드의 요점이다 — **같은 위반을 새 게이트만 잡는다.** 세 라운드 연속
같은 가족이 샌 이유가 「목록이 짧아서」가 아니라 **판정 방향과 판정 근거**에 있었다는 뜻이다.

## 2026-09-02T10:10Z — 레인 경계 (선언)

**`e733cfa` 에 하네스 레인의 변경이 섞여 있다.** 공유 working tree 에서 두 레인이 병행하는
동안 한쪽의 미커밋 편집이 남아 있었고 구현 레인이 `git add -A` 를 써서 함께 스테이징했다 —
두 조건이 모두 있어야 생기는 혼입이다.

**그 내용은 하네스 레인 소유이고 1A 의 산출물이 아니다.** 정본은 `CLAUDE.md` 변경 이력의
`codex-review-gate` 행(하네스 레인이 `cb90507` 로 등재)이며 **여기서 되풀이하지 않는다** —
파일 목록도 줄 수도 옮겨 적지 않는다. 낡을 자리를 만들지 않기 위해서다.

**이력은 되쓰지 않는다**(amend·rebase 없음). 이후 커밋은 `git add <in_scope 경로>` 만 쓰고
커밋 직전 `git diff --cached --name-status` 로 in_scope 를 대조했다.

- 검출: `git log --oneline 6b03c75..HEAD -- .claude/ CLAUDE.md`
- 판별: 그 목록에서 **scope 가 `(harness)` 가 아닌 커밋이 혼입**이다. 나머지는 하네스 레인이
  자기 몫으로 낸 커밋이라 레인 경계 위반이 아니다
- **type 이 아니라 scope 로 가른다.** 처음에는 `chore(harness)` 로 적었는데 하네스 레인이
  `docs(harness)` 도 쓰기 시작하자 그 커밋이 혼입으로 오탐됐다 — 레인을 가르는 것은 scope 이고
  type 은 그 커밋이 무엇을 했는지일 뿐이다

## 2026-09-02T10:30Z — Codex 수정 라운드의 게이트 실측

앞 절과 같은 방식이다 — 위반을 심어 명령 하나를 돌리고 즉시 지웠다.

| # | 심은 것 | cmd | exit | 핵심 결과 |
| --- | --- | --- | --- | --- |
| `E-6` | **build-logic** 에 501 줄 파일 | `./gradlew check` | 1 | `파일 500 줄 한도 초과 1건` — 게이트 구현도 게이트 안에 있다 |
| `E-7` | **build-logic** 에 54 줄 함수 | `./gradlew check` | 1 | `maximum length is 50` — 도구 기본값 60 이 아니다 |
| `E-8` | **build-logic** 에 포맷 위반 | `./gradlew check` | 1 | 같은 파일에 지적 |
| `E-9` | `qualification` 에 **쓰지 않는** `project(":decision")` | `./gradlew check` | 1 | `project 의존 ':decision' — 'qualification' 에 허용된 것은 [shared-kernel]` |
| `E-9b` | 같은 상태에서 arch test 만 | `./gradlew :app:test --tests '*ArchitectureGateTest*'` | **0** | **2 차 그물은 못 본다** — 바이트코드 참조가 없다. `E-9` 가 1 차 강제의 존재 이유다 |
| `E-10` | 정책의 `layer.domain.shareable` 에 `decision` 추가 | `./gradlew :build-logic:test` | 1 | 판정 테스트 2 건 실패 — 규칙이 느슨해지면 테스트가 죽는다 |

### `E-11` — build-logic 청소의 기각된 후보 둘

**왜 기각했는지의 정본은 `build-logic/build.gradle.kts` 의 주석 「이 빌드의 ktlint 검사는 매번 다시 돈다」다.** 여기는 그 판단을
낸 실측만 든다 — 실패 이력은 결함이 아니라 검증이 작동했다는 증거다.

| 후보 | cmd | exit | 핵심 결과 |
| --- | --- | --- | --- |
| (a) 루트 `clean` → `:build-logic:clean` 연결 | `./gradlew --no-build-cache clean check` | 1 | `:build-logic:compileTestKotlin` FAILED — 실행 중인 빌드의 산출물을 지운다 |
| (b) 루트에서 그 빌드의 검증 산출물만 `Delete` | — | — | **실측 대상이 아니다.** (a) 의 로그가 `:build-logic:clean` 을 `compileKotlin`·`jar` **뒤**에 놓는다 — included build 의 task 는 루트가 요청한 `clean check` 순서에 매이지 않으므로 루트 쪽 Delete 도 검사 task 와의 순서를 잡을 수 없다 |

재현: (a) 는 루트에 `clean` task 를 만들어 그 빌드의 `:clean` 에 `dependsOn` 을 걸고 위 명령을
돌린다. (b) 의 근거는 (a) 로그의 `> Task :build-logic:` 줄 순서다.

### 우회 경로별 대응표 (설계 검토 §2 + Codex 4차)

검토가 던진 여덟과 Codex 4차 둘. **각 행의 「막는 층」이 그 우회가 왜 닫혔는지의 정본**이다.

| # | 우회 | 막는 층 | 실측 |
| --- | --- | --- | --- |
| `U-1` | `ResourceBundle` — classpath 자원 I/O, 추가 의존 0 | **T-C** 목록 밖 | fixture |
| `U-2` | `Boolean.getBoolean` 등 시스템 프로퍼티 | **T-D** — 효과 표면 도출이 스스로 냈다 | fixture · `E-38` |
| `U-3` | 모듈이 convention plugin 을 안 씀 → 게이트 소멸 | `conventionCoverageGate` | `E-21` |
| `U-4` | `runtimeOnly` 등 다른 configuration | 혈통으로 고른 configuration 전건 + main 좌표 allow-list | `E-22` |
| `U-5` | `srcDir("gen")` — 크기 래칫만 빠짐 | sizeGate 입력을 실제 srcDirs 로 | `E-23` |
| `U-6` | `@Suppress("LongMethod")` — 승인 임계 소멸 | sizeGate 가 PSI 로 직접 잰다 — 억제가 이 임계에 닿지 않는다 | `E-24` · `E-31` |
| `U-7` | `Locale`/`TimeZone.getDefault` | **T-C** 목록 밖 | fixture |
| `U-8` | `X::class.java` 클래스 리터럴 | **막지 못한다** — 상수 풀 엔트리는 ArchUnit 의존이 아니다 | 잔여(알려진 제한) |
| Codex a | `Formatter(String)` — 허용 패키지 안의 파일 I/O | **T-C** 목록 밖 | fixture |
| Codex b | Java source set — 소유·크기·경계 셋 다 비껴감 | `java.setSrcDirs` 봉쇄 + `sourceLanguageGate` | `E-25` |

### Codex 5차 대응표 (addendum)

**두 finding 은 같은 결함이다** — 입력을 **관례**에서 뽑았다(디렉터리 관례·텍스트 관례).
해법도 같다: Gradle/Kotlin 이 이미 갖고 있는 resolved fact 를 입력으로 삼는다.

| 경로 | 막는 층 | 실측 |
| --- | --- | --- |
| `java.srcDir` 재등록 | `allSource`(살아 있는 뷰) + `src/` 병행 | `E-27` |
| `sourceSets.create("extra")` | source set 집합 고정 | `E-28` |
| 수제 `JavaCompile`·출력 리다이렉트 | **class 원산지**(`kotlin.Metadata` 부재) | `E-29` |
| `jar { from("prebuilt") }` | **포함 관계** — 아카이브 class 는 게이트를 통과한 산출물의 부분집합이어야 한다 | `E-30` · `E-47` |
| `@Suppress` 다중 행·`@file:`·`@kotlin.` | **무의미해진다** — PSI 로 직접 잰다 | `E-31` |
| 45줄 함수 + 20줄 람다 | 람다도 잰다 | `E-32` |
| 45줄 함수 여럿으로 쪼개 응집도만 저하 | **막지 못한다. 막으려 해서도 안 된다** | 알려진 제한 |

### 이 라운드의 게이트 실측

| # | 심은 것 | exit | 핵심 결과 |
| --- | --- | --- | --- |
| `E-27` | `java.srcDir("domain-java")` + `.java` | 1 | **Codex 시나리오는 현행 head 에서도 이미 잡혔다** — KGP 2.4.10 이 `java.srcDir` 를 kotlin srcDirs 에 넣기 때문(실측). 그 우연에 기대지 않으려 `allSource` 로 옮겼다 |
| `E-28` | `sourceSets.create("extra")` | 1 | `예상 밖 source set 'extra'` |
| `E-29` | 수제 `JavaCompile` → `classes/kotlin/main` | 1 | `kotlin.Metadata 가 없다` |
| `E-30` | `jar { from("prebuilt") }` 로 `evil/Sneak.class` | 1 | `아카이브에 소유 밖 클래스` |
| `E-31` | 다중 행 `@Suppress` · `@file:Suppress` · `@kotlin.Suppress` + 54줄 함수 | 1 (셋 전부) | detekt 은 exit 0 — **그 축을 더는 detekt 이 들지 않는다** |
| `E-32` | 50줄 넘는 람다 | 1 | 함수를 쪼개도 닫힌다 |

### 아카이브 = 게이트를 통과한 산출물 (Codex 7차)

**패키지 접두 검사는 넣는 쪽이 이름을 맞추면 통과한다.** `E-30` 은 소유 **밖**(`evil/Sneak.class`)만
심어 그 사각을 드러내지 못했다. 소유 패키지에 이름을 맞춘 prebuilt Java class 는 jar 게이트를
통과하면서 원산지·ktlint·detekt·크기 게이트를 전부 비껴간다.

그래서 판정을 열거가 아니라 **포함 관계**로 바꾼다 — 아카이브의 class 엔트리는 **경로와 내용
해시가 모두** 게이트를 통과한 class output 과 일치해야 한다. 엔트리마다 원산지
(`kotlin.Metadata`)도 함께 본다. 대상은 **class 엔트리뿐**이고 resource 는 컴파일 산출물이
아니라 이 판정의 대상이 아니다.

| # | 심은 것 | cmd | exit | 핵심 결과 |
| --- | --- | --- | --- | --- |
| `E-47` | 소유 패키지에 이름을 맞춘 prebuilt Java class(`bidvector/settlement/Sneak.class`)를 `jar { from(...) }` 로 | `./gradlew :settlement:jarContentGate` | 1 | `게이트를 거치지 않은 클래스가 아카이브에 있다` — **접두만 보던 판에서는 통과하던 형태** |
| `E-48` | 같은 이름·다른 내용(javac 로 만든 `ModuleBoundaryAnchor.class`) | 같은 명령 | 1 | `게이트를 통과한 산출물과 내용이 다르다` — 해시가 이름 위장을 막는다 |
| `E-49` | `from(zipTree(...))` 로 주입 | 같은 명령 | 1 | 주입 **경로**를 열거하지 않아도 닫힌다 |
| `E-50` | `src/main/resources` 에 `.class` 를 두어 `processResources` 로 | 같은 명령 | 1 | resource 경로도 같은 판정을 받는다 |
| `E-51` | `src/main/resources` 에 `.properties` | 같은 명령 | **0** | **의도된 결과** — resource 는 이 판정의 대상이 아니다 |
| `E-52` | `classes/kotlin/main` 에 심어 **포함 관계를 통과**시킴 | 같은 명령 | 1 | `kotlin.Metadata 가 없다` — **두 번째 층이 서는 자리가 이것**이다 |

**원산지 검사를 ArchUnit 으로 재지 않는 이유도 실측이다.** `ClassFileImporter().importJar` 은 이
데몬 안에서 위 Java 클래스를 **조용히 건너뛰었다**(두 엔트리 중 Kotlin 것만 임포트). 같은 jar 를
독립 JVM 에서 읽으면 둘 다 나온다. 잡아야 할 바로 그 클래스를 빠뜨리는 검사는 없는 것만 못하므로
상수 풀에서 `kotlin/Metadata` 를 바이트로 찾는다. **`importPaths` 는 영향이 없다** —
`packageOwnershipGate` 의 원산지 검사에 같은 클래스를 심어 재확인했다(exit 1).

### 효과 표면 도출 — T-D 를 열거에서 래칫으로

**허용된 클래스 안에도 효과를 내는 멤버가 있다**(`Throwable#printStackTrace` 는 `System.err` 로
쓴다). 그 자리를 손으로 열거하면 라운드마다 목록이 늘고 생각해 내지 못한 멤버는 조용히 열린다.
그래서 **금지 표면을 효과 어휘로 고정**하고(`architecture-policy.properties` 의
`effect.surface.*`) 허용 클래스의 public/protected 멤버 중 그 표면에 닿는 것을 도출한다.
**분류하지 않은 후보가 남으면 빌드가 실패**하는 것이 이 층을 래칫으로 만든다.

**여집합(허용 목록 밖에 닿으면 불순)은 쓰지 못한다** — 설계 검토가 `ArrayList#add` ·
`String#substring` · `Integer#valueOf` · `HashMap#put` · `StringBuilder#append` 가 전부 불순이
되는 것을 실측했다. `Throwable` 계열을 허용에서 빼는 길도 닫혀 있다 — owner 가 구체 예외
타입이고 그 셋(`IllegalArgumentException`·`IllegalStateException`·`NumberFormatException`)은
`require`/`check`/`toInt()` 가 컴파일러 산출로 낸다.

| # | 심은 것 | cmd | exit | 핵심 결과 |
| --- | --- | --- | --- | --- |
| `E-38` | `RuntimeException(...).printStackTrace()` · `Math.random()` · `Collections.shuffle` · `IllegalStateException(...).stackTrace` | `:app:test --tests '*ArchitectureGate*'` | 0 | 넷 다 음성 단언으로 잡힌다. **손 열거 셋(`getBoolean`·`getInteger`·`getLong`)은 도출이 스스로 재도출했다** |
| `E-39` | 판정을 **owner 기준**으로 되돌림(직전 판) | 같은 명령 | **1** | 음성 셋이 죽는다 — 구체 예외 타입 경유가 그때 통과했다는 실물 |
| `E-40` | 분류를 `forbidden` → `reviewed` 로 뒤집음 | 같은 명령 | **1** | 음성 단언이 **분류에 묶여 있다** — tautology 가 아니다 |
| `E-41` | 분류 한 줄 삭제 | `./gradlew memberEffectGate` | **1** | `분류되지 않은 후보 — java.lang.Math#random` |
| `E-42` | 후보에 없는 분류 추가 | 같은 명령 | **1** | `후보에 없는 분류(낡았다)` — 낡은 금지도 거짓말이다 |
| `E-43` | 효과 표면에서 `java.io` 제거 | 같은 명령 | **1** | `도출 결과가 커밋본과 다르다` — JDK·정책 변경이 조용히 지나가지 않는다 |
| `E-44` | 양성: `require`/`check`/`toInt()`·`toString`·`String.format` | `:app:test --tests '*ArchitectureGate*'` | 0 | 오탐 없음. 양성 corpus 가 이 통과를 고정한다 |
| `E-45` | ArchUnit 의 classpath 해석을 끄고 같은 접근을 잼 | 임시 probe | — | **해석 5/5 → 0/5 미해결**이 되고 상위 타입 훑기 fallback 이 **0 건** 잡는다(`printStackTrace` 보고 2 → 0) — 그때는 owner 의 계보도 함께 알 수 없다 |
| `E-46` | domain 모듈에 `raw.lowercase()` · `raw.uppercase()` · `a.equals(b, ignoreCase = true)` | `:app:test --tests '*ArchitectureGateTest*'` | 1 · 1 · **0** | **앞의 둘은 두 규칙이 각각 보고한다**(규칙을 따로 평가해 확인) — 멤버 층의 `String#toLowerCase` 와 클래스 층의 `java.util.Locale`. 멤버만 재분류해도 `Locale.ROOT` 필드 접근이 남는다. 비교는 되고 **정규화 키 경로가 없다**(알려진 제한 25) |

분류의 셈은 `./gradlew memberEffectGate` 가 한 줄로 낸다 — 여기 적지 않는다(후보가 바뀌면 낡는다).

**설계 검토가 미확인으로 남긴 셋을 실측으로 답한다.** ① 효과 표면 초안 — 그대로 채택하되
`java.lang.StackTraceElement` 을 더했다(실행 스택은 환경·리플렉션 축이고 `StackWalker` 와 같은
이유다. 그것이 없으면 `Throwable#getStackTrace` 가 후보에 오르지 않아 음성 fixture 하나가 서지
못한다). ② 후보 분류 — 전건 분류했고 셈은 위 명령이 낸다. ③ **`resolveMember()` fallback 은
검토자의 기대만큼 넓지 않다**(`E-45`) — 상위 타입 훑기는 「owner 의 계보는 알지만 그 멤버만 못
찾는」 좁은 경우만 덮고, 해석 자체가 꺼지면 계보도 모르므로 아무것도 잡지 못한다. 판정을
실제로 떠받치는 것은 ArchUnit 의 classpath 해석이며, **그 의존을 조용히 두지 않으려고 해석
설정을 단언하는 테스트를 넣었다.**

### 본문을 갖는 선언 — 표기별 실측

**정의의 정본은 `config/quality/size-policy.properties` 의 `limit.function.lines` 주석**이다.
그 정의가 표기마다 실제로 서는지 `settlement` 과 빌드 스크립트에 심어 재고 되돌렸다. 방문자를
골라 구현하면 구현하지 않은 표기가 곧 우회로가 되므로, 재는 대상을 노드
**타입**(`KtDeclarationWithBody` · `KtAnonymousInitializer`)으로 판정한다. **파일 축과 함수 축의
입력이 같은 집합이 아니었다** — 빌드 스크립트는 어느 source set 에도 속하지 않아 두 축 모두에서
빠져 있었고(`scriptSizeGate` 가 그 자리를 든다), 모듈 스크립트는 그 모듈 `sizeGate` 가 든다.

| # | 심은 것 | cmd | exit | 핵심 결과 |
| --- | --- | --- | --- | --- |
| `E-33` | `get() { }` · `init { }` · 보조 생성자를 각 54 줄로 한 파일에 | `./gradlew :settlement:sizeGate` | 1 | **셋 다 보고**(`v.get` · `init` · `constructor`). 같은 파일의 **본문 없는 주 생성자는 보고되지 않는다** — 정의대로다 |
| `E-34` | 위 파일 제거 | 같은 명령 | 0 | 되돌림 확인 |
| `E-35` | 모듈 `build.gradle.kts` 에 55 줄 람다 | 같은 명령 | 1 | **`.kts` 도 같은 임계** |
| `E-36` | 루트 `build.gradle.kts` 에 55 줄 람다 | `./gradlew scriptSizeGate` | 1 | 모듈에도 source set 에도 속하지 않는 스크립트를 이 게이트가 든다 |
| `E-37` | 같은 파일에 60 행짜리 top-level 문장 | 같은 명령 | **0** | **의도된 결과** — 함수 본문이 아니므로 함수 축이 아니고 파일 축(500 줄)이 든다 |

### addendum 이 미확인으로 남긴 것 — 실측으로 답한다

| 물음 | 답 |
| --- | --- |
| KGP 아래 `kotlin.srcDirs` 가 `java.srcDirs` 를 추적하는가 | **추적한다**(`KOTLIN [kotlin, domain-java, kotlin]`). 그래서 Codex 시나리오가 현행 배선에도 걸렸다 — 다만 KGP 내부 동작이라 계약으로 삼지 않는다 |
| `kotlin.Metadata` 를 전 Kotlin 산출물이 갖는가 | **이 저장소의 전 모듈 산출물에서 위반 0**(게이트가 `check` 마다 전수로 잰다). 「Kotlin 이 만든 모든 클래스」에 대한 일반 보증은 아니다 — 반례가 나오면 그 클래스가 게이트에 걸려 **시끄럽게** 드러난다 |
| jar 엔트리 검사 | **넣었다**(`E-30`) |
| TestKit | **넣지 않았다.** 알려진 제한 |

| # | 심은 것 | cmd | exit | 핵심 결과 |
| --- | --- | --- | --- | --- |
| `E-21` | `settlement` 를 `kotlin("jvm")` 로 바꿈 | `./gradlew --no-build-cache clean check` | 1 | `적용 안 됨: [settlement]` |
| `E-22` | `decision` 에 `runtimeOnly("org.springframework:spring-core")` | `./gradlew :decision:moduleDependencyGate` | 1 | 무해한 좌표(commons-lang3)도 잡힌다 — 실패 방향이 닫혀 있다 |
| `E-23` | `srcDir("gen")` 아래 500 줄 초과 파일 | `./gradlew :settlement:sizeGate` | 1 | `파일 500 줄 한도 초과` |
| `E-24` | `@Suppress("LongMethod")` + 54 줄 함수 | `./gradlew :settlement:detekt` · `:settlement:sizeGate` | **0** · 1 | **detekt 은 억제된다.** sizeGate 는 잡는다 — 억제가 그 임계에 닿지 않는다 |
| `E-25` | `decision/src/main/java/Leak.java` | `./gradlew --no-build-cache clean check` | 1 | `main/java/Leak.java` |
| `E-26` | 양성 corpus 에서 `kotlin.enums`·`kotlin.math`·`java.time.temporal`·`EnumMap` 을 각각 제거 | `./gradlew :app:test --tests '*ArchitectureGate*'` | 1 (넷 전부) | corpus 가 공허하지 않다 |

### 설계 검토가 미확인으로 남긴 넷 — 실측으로 답한다

| # | 물음 | 답 |
| --- | --- | --- |
| `M-1` | `@Suppress` 가 detekt 을 억제하는가 | **억제한다**(`E-24`). U-6 은 실재하는 우회였다 |
| `M-2` | `java.setSrcDirs(emptyList())` 의 부작용 | **없다.** 기존 빌드 그대로 exit 0, Java 산출물 미생성. **다만 봉쇄된 파일은 어느 source set 에도 안 들어가서 srcDir 만 훑는 검사에는 보이지 않는다** — 그래서 관례 트리도 함께 훑는다 |
| `M-3` | TestKit 이 이 빌드에서 도는가 | **재지 않았고 도입하지 않았다.** 사유는 알려진 제한 |
| `M-4` | `external.allowed.domain` 의 집합 | **둘**(`kotlin-stdlib`·`annotations`). `--configuration compileClasspath` 실측 |

### 금지 가족을 어느 층이 잡는가

**2차 열의 뜻이 바뀌었다** — 이제 domain 게이트는 금지 열거가 아니라 **allow-list(정확 패키지)** 다.
아래 가족은 전부 허용 밖이라 잡히고, **목록에 이름이 없는 좌표도 함께 잡힌다**(그것이 전환의
이유다). 표는 여전히 fixture 로 실측하지만 **완전성을 표가 지지 않는다** — allow-list 자신이 진다.
`E-18` 이 그 차이의 실물이다: 허용을 뿌리로 두었을 때 다섯이 새고 정확 패키지로 좁히자 닫혔다.

가족 목록의 출처는 승인 문서이고 대조는 `build-logic` 의 `ForbiddenFamilyCoverageTest` 가
**방향을 뒤집어** 든다(금지 가족이 허용 목록에 들어오지 않는가). 표를 손으로 맞추지 않는다.

| 가족 | fixture | 1차(의존 그래프) | 2차(ArchUnit) |
| --- | --- | --- | --- |
| Spring | `FrameworkLeak` | 잡는다 | 잡는다 |
| JPA | `PersistenceLeak` | 잡는다 | 잡는다 |
| JSON (Jackson **3**) | `SerializationLeak` | 잡는다 | 잡는다 |
| broker (JMS) | `BrokerLeak` | 잡는다 | 잡는다 |
| HTTP | `HttpLeak` | **못 잡는다** — JDK 타입이라 Maven group 이 없다 | 잡는다 |
| SQL | `SqlLeak` | **못 잡는다** — 같은 이유 | 잡는다 |
| file I/O | `FileIoLeak` | **못 잡는다** — 같은 이유 | 잡는다 |
| nio channels | `ChannelLeak` | **못 잡는다** — 같은 이유 | 잡는다 |
| gRPC | `GrpcLeak` | 잡는다 | 잡는다 |
| Protobuf | `ProtobufLeak` | 잡는다 | 잡는다 |
| I/O (Kotlin stdlib) | `ConsoleIoLeak` | **못 잡는다** — 추가 의존이 없다 | 잡는다 |

반대로 `E-9`·`E-9b` 가 보이듯 **선언만 되고 참조가 없는 의존은 2 차가 못 본다.** 두 층의
사각이 서로 반대라 둘 다 필요하다.

## 2026-09-02T09:20Z — acceptance (scope.md `acceptance_commands`)

아래 `A-0`·`A-2`~`A-4` 는 **커밋된 것만 있는 detached worktree** 에서 잰 값이고, `A-1` 만
working tree 에서 잰다. **`A-0` 이 나머지의 전제인 이유는 `A-0b` 절이 갖는다.**

| # | cmd | exit | 핵심 결과 |
| --- | --- | --- | --- |
| `A-0` | `git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)` | 0 | 커밋만으로 빌드된다. `build-logic/src/main/kotlin/bidvector/buildlogic/` 에 8 파일 |
| `A-1` | `./gradlew --no-build-cache clean check` | 0 | 게이트 전부 통과. 3 회 연속 재현 |
| `A-2` | `./gradlew :app:test --tests '*ArchitectureGate*'` | 0 | 0 failed. 양성 6 + 음성(위반 형태 넷 + 금지 가족 전건) — **수는 가족이 늘면 함께 는다.** 명령이 낸다 |
| `A-3` | `./gradlew qualityBaseline` | 0 | 값은 `build/reports/quality-baseline/quality-baseline.md` |
| `A-4` | `./gradlew :app:compatibilitySmoke` | 0 | 해석 + **채택 라이브러리마다 로드 테스트 하나**, 0 failed. 값은 `app/build/reports/compatibility-smoke/resolved-modules.txt` |
| `A-5` | `./gradlew :build-logic:test` | 0 | 0 failed — 경계 판정의 순수 함수 테스트 + 금지 가족의 **출처 대조** |

### `A-0b` — 커밋된 파일 집합과 디스크 실물의 대조

**clean-tree 게이트가 왜 이것만으로 부족한지의 정본이 이 절이다** — `scope.md` 와
`checklist.md` 는 여기를 가리킨다.

`git status --porcelain` 은 **ignored 파일을 보지 못한다.** 그래서 빌드에 필요한 소스가
ignore 규칙에 걸려 커밋에서 빠져도 트리는 깨끗해 보이고, 그것이 B-1 의 실물이었다.
`A-0` 이 「커밋된 것만으로 빌드되는가」를 재고, 아래 둘이 **무엇이 빠졌는가**를 이름으로
낸다 — 앞의 것은 ignore 규칙이 소스를 삼키는 경우를, 뒤의 것은 그냥 커밋을 잊은 경우를 잡는다.

```
git ls-files --others --ignored --exclude-standard -- '*/src/*'
diff <(find . -name '*.kt' -o -name '*.kts' | grep -vE '/(build|\.gradle|_workspace)/' \
        | grep -v '^\./bid-vector/' | sed 's|^\./||' | sort) \
     <(git ls-files '*.kt' '*.kts' | sort)
```

- exit: 첫 명령 출력 없음 · 둘째 exit 0(차이 없음)
- 핵심 결과: ignore 에 걸린 소스 0 건 · 디스크와 커밋의 Kotlin 소스 집합이 같다
- 첫 명령의 대상을 **`*/src/*` 로 좁히는 것이 요점**이다. `*.kt` 로 넓히면 Gradle 이 생성하는
  `build-logic/build/generated-sources/**` 수백 건이 잡히는데 그것들은 **올바르게 ignore 된
  것**이라 신호가 아니라 잡음이다. 손으로 쓴 소스는 언제나 `src/` 아래에 있고 빌드 산출물은
  거기에 없다 — 그 비대칭이 이 명령을 판별자로 만든다

## 2026-09-02T12:40Z — 인용 축어 대조

이 slice 는 `milestone-1.md` 를 편집했고 그 아래 줄 번호가 전부 밀렸다. 그래서 **그 파일을
가리킬 때 `file:line` 을 쓰지 않고 인용 축어·절 제목으로 가리킨다**(`evidence-pack` 「낡는
좌표」). 인용이 축어인지는 대조로만 안다 — **첫 시도의 표는 축어가 아니라 조사 띄어쓰기를
고친 의역이었고 이 명령이 그것을 잡았다.**

```
sed -n '/^## M1 완료 조건 대조/,/^### /p' reports/evidence/m1/1a/checklist.md \
 | awk -F'|' '/^\| / && NF>3 {gsub(/^ +| +$/,"",$2); if ($2 !~ /^-+$/ && $2 != "조건") print $2}' \
 | while IFS= read -r q; do printf '%s\t%s\n' "$(grep -cF "$q" milestone-1.md)" "$q"; done
```

- exit: 0
- 핵심 결과: **매치 0 인 줄이 없다.** 표 밖 인용과 fixture 쪽 인용도 같은 방식으로 확인했다 —
  대상은 아래 명령이 낸다(여기 열거하지 않는다)

```
grep -rhoF -f <(grep -oE '「[^」]{10,}」|\*"[^"]{10,}"\*' \
      reports/evidence/m1/1a/checklist.md app/src/test/kotlin/bidvector/archfixture/violating/README.md \
      app/src/test/kotlin/bidvector/app/architecture/ArchitectureGateCatchesViolationsTest.kt \
    | tr -d '「」*"') milestone-1.md | sort -u
```

**첫 등재는 이 대조를 fixture README 에 걸지 않았다** — 「fixture 쪽 인용」이라 적었지만 실제로
확인한 것은 `FileIoLeak.kt` 쪽이었고, README 의 인용은 조사 띄어쓰기가 원문과 달라 매치 0 이었다
(verifier r6 L-1). 축어로 되돌렸고 이제 대상이 명령에서 나오므로 같은 누락이 생기지 않는다.

## 2026-09-03 — 승인 문서 개정의 역방향 파급

편집한 세 파일 각각에 대해 `grep -rn '<파일명>:[0-9]' --include='*.md' --include='*.kt'
--include='*.properties'`(저장소 밖·`build/` 제외)를 돌렸다.

| 가리켜지는 파일 | 매치 | 처리 |
| --- | --- | --- |
| `v2-지침서.md` | 있음 — **전부 `:215`~`:223`(§4.5)** | 삽입 지점(§5 테스트 플랫폼 문면)보다 **위**라 밀린 것이 없다. 고칠 대상 0, 등재 대상 0 |
| `docs/adr/0007-test-pyramid-and-ratchet.md` | 있음 — `:2`(머리) | 편집은 §1.1 이하라 **밀리지 않는다** |
| `reports/evidence/m1/1a/scope.md` | **0** | 이 파일을 줄 번호로 가리키는 곳이 없다(같은 이름의 매치는 전부 M0 slice 가 자기 `scope.md` 를 가리킨 것) |

**밀린 것은 0 이지만 좌표의 형태는 바꿨다** — `v2-지침서.md` 가 이 slice 의 편집 대상이 되면서
그 파일로의 줄 번호가 「같은 slice 안에서 움직일 수 있는 좌표」가 됐기 때문이다(`evidence-pack`
「낡는 좌표」). 대상은 `git diff` 가 낸다. **같은 훑기가 이미 밀어 놓은 좌표 하나를 찾아 고쳤다** —
`scope.md` D-3 이 `ADR 0006` 을 가리키던 줄 번호가 이 slice 의 `D-2.1` 신설로 어긋나 있었다
(알려진 제한 16).

## 2026-09-03 — 승인 문서 개정 라운드의 clean-tree · 비밀값

| 축 | cmd | 결과 |
| --- | --- | --- |
| clean-tree | `git status --porcelain -- <scope.md 의 in_scope 경로>` | 출력 없음 |
| 비밀값(evidence) | `evidence-pack` SKILL 의 패턴 | 매치 없음(exit 1) |
| 비밀값(diff) | 같은 패턴을 `git diff acb89eb..HEAD` 에 | 매치 없음(exit 1) |

**acceptance 는 재실행하지 않는다** — 이 라운드의 변경은 승인 문서와 evidence 뿐이고
빌드 입력이 아니다. 직전 코드 라운드의 `A-0`~`A-5` 가 그대로 선다.

## 2026-09-02T08:55Z — 비밀값 스캔

**패턴을 여기 옮겨 적지 않고 이 절의 이름도 한국어로 쓴다.** 정본은 `evidence-pack` SKILL 의
해당 항목이다. 패턴을 인용하거나 절 이름에 영어 낱말을 쓰면 **스캔이 자기 문면을 잡는다** —
M0 가 「스캐너가 자기 출력을 스캔해 재귀 차단까지」 간 경로의 첫 걸음이 그것이고, 앞선
라운드가 그 자리에서 낡은 exit code 를 남겼다(verifier L-1).

**제외 패턴을 만들지 않았다.** 그쪽이 M0 가 경고한 하네스다. 여기서 바꾼 것은 낱말 선택뿐이고
「비밀값」은 SKILL 자신이 쓰는 말이다 — 스캔의 대상(커밋 diff 와 evidence 의 **값**)은 그대로다.

- cmd: SKILL 의 패턴으로 `reports/evidence/m1/1a/` 와 `git diff 6b03c75..HEAD` 를 각각 스캔
- exit: 둘 다 1 (매치 없음 = 통과)
- 육안 확인: 1A 는 사업자 정보·Telegram 식별자·자격증명을 다루지 않는다. 외부 좌표는
  Maven 아티팩트 GAV 와 공개 문서 URL 뿐이다
