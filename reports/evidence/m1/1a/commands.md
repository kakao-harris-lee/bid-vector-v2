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
- 판별: 그 목록에서 **`chore(harness)` 가 아닌 커밋이 혼입**이다. 나머지는 하네스 레인이
  자기 몫으로 낸 커밋이라 레인 경계 위반이 아니다

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

**왜 기각했는지의 정본은 `build-logic/build.gradle.kts:48`~`:52` 주석이다.** 여기는 그 판단을
낸 실측만 든다 — 실패 이력은 결함이 아니라 검증이 작동했다는 증거다.

| 후보 | cmd | exit | 핵심 결과 |
| --- | --- | --- | --- |
| (a) 루트 `clean` → `:build-logic:clean` 연결 | `./gradlew --no-build-cache clean check` | 1 | `:build-logic:compileTestKotlin` FAILED — 실행 중인 빌드의 산출물을 지운다 |
| (b) 루트에서 그 빌드의 검증 산출물만 `Delete` | — | — | **실측 대상이 아니다.** (a) 의 로그가 `:build-logic:clean` 을 `compileKotlin`·`jar` **뒤**에 놓는다 — included build 의 task 는 루트가 요청한 `clean check` 순서에 매이지 않으므로 루트 쪽 Delete 도 검사 task 와의 순서를 잡을 수 없다 |

재현: (a) 는 루트에 `clean` task 를 만들어 그 빌드의 `:clean` 에 `dependsOn` 을 걸고 위 명령을
돌린다. (b) 의 근거는 (a) 로그의 `> Task :build-logic:` 줄 순서다.

### 금지 가족을 어느 층이 잡는가

fixture 일곱이 이 표를 실측으로 만든다. **어느 한 층도 혼자로는 충분하지 않다.**

| 가족 | fixture | 1차(의존 그래프) | 2차(ArchUnit) |
| --- | --- | --- | --- |
| Spring | `FrameworkLeak` | 잡는다 | 잡는다 |
| JPA | `PersistenceLeak` | 잡는다 | 잡는다 |
| JSON (Jackson **3**) | `SerializationLeak` | 잡는다 | 잡는다 |
| broker (JMS) | `BrokerLeak` | 잡는다 | 잡는다 |
| HTTP | `HttpLeak` | **못 잡는다** — JDK 타입이라 Maven group 이 없다 | 잡는다 |
| SQL | `SqlLeak` | **못 잡는다** — 같은 이유 | 잡는다 |
| file I/O | `FileIoLeak` | **못 잡는다** — 같은 이유 | 잡는다 |

반대로 `E-9`·`E-9b` 가 보이듯 **선언만 되고 참조가 없는 의존은 2 차가 못 본다.** 두 층의
사각이 서로 반대라 둘 다 필요하다.

## 2026-09-02T09:20Z — acceptance (scope.md `acceptance_commands`)

아래 `A-0`·`A-2`~`A-4` 는 **커밋된 것만 있는 detached worktree** 에서 잰 값이고, `A-1` 만
working tree 에서 잰다. **`A-0` 이 나머지의 전제인 이유는 `A-0b` 절이 갖는다.**

| # | cmd | exit | 핵심 결과 |
| --- | --- | --- | --- |
| `A-0` | `git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)` | 0 | 커밋만으로 빌드된다. `build-logic/src/main/kotlin/bidvector/buildlogic/` 에 8 파일 |
| `A-1` | `./gradlew --no-build-cache clean check` | 0 | 게이트 전부 통과. 3 회 연속 재현 |
| `A-2` | `./gradlew :app:test --tests '*ArchitectureGate*'` | 0 | 17 tests, 0 failed (양성 6 · 음성 11 — 금지 가족 일곱 포함) |
| `A-3` | `./gradlew qualityBaseline` | 0 | 값은 `build/reports/quality-baseline/quality-baseline.md` |
| `A-4` | `./gradlew :app:compatibilitySmoke` | 0 | 해석 아홉 + **로드 테스트 7 tests, 0 failed**. 값은 `app/build/reports/compatibility-smoke/resolved-modules.txt` |
| `A-5` | `./gradlew :build-logic:test` | 0 | 7 tests, 0 failed — 경계 판정의 순수 함수 테스트 |

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
