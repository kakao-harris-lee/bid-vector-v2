# M7/7C⑤ — 실행한 명령과 핵심 결과

> 호스트 규율: 모든 Gradle·npm 명령은 `flock ~/.local/bid-vector-build.lock` 아래에서 돈다(레인 여덟이 호스트를 공유한다).
> 점검 셋(`pgrep` · `free -m` · `ps --sort=-rss`)은 명령과 묶지 않고 **별도 호출로 먼저** 돌려 결과를 읽은 뒤 시작했다.

## 0. 호스트 점검 (빌드 시작 전, 매 라운드)

| 시각(KST) | available | Swap free | 다른 레인 빌드 | 판정 |
|---|---:|---:|---|---|
| 11:04 | 11.2 GB | 6.5 GB | 7B 레인 Gradle test 실행 중 | 시작(문턱 6GB·2GB 충족, flock 큐) |
| 11:25 | 12.2 GB | 6.3 GB | 다른 레인 daemon 셋 | 시작 |
| 11:50 | 11.9 GB | 6.2 GB | 다른 레인 다섯 | 시작 |
| 12:15 | 10.7 GB | 6.1 GB | 다른 레인 다섯(락 대기 큐 포함) | 시작 |

## 1. 순수 판정 + 어댑터 (산출물 커밋 전 측정)

```
flock ~/.local/bid-vector-build.lock ./gradlew --no-daemon \
  :workflow:test --tests 'bidvector.workflow.opening.*' \
  :adapters:test --tests 'bidvector.adapters.opening.*'
```

- 핵심 결과: exit **0** · `OpeningResultViewTest` **15** · `JdbcOpeningResultViewTest` **17**(skip 0 · fail 0 · error 0).
- 앞선 실행 하나는 `:workflow:compileTestKotlin` 에서 깨졌다(test 의 `export` 확장 import 누락) — 고친 뒤 위 결과다.

## 2. 생성 타입 재생성 (OpenAPI 와 바이트 등식 게이트의 입력)

```
cd web && flock ~/.local/bid-vector-build.lock bash -c 'npm ci && npm run api:types'
```

- 핵심 결과: exit **0** · `openapi-typescript 7.13.0` 가 문서를 생성 타입으로 옮겼다(88.5ms). 그 산출물은
  산출물 커밋에 함께 들어간다 — 문서만 고치고 생성을 잊은 경로는 `api:types:check`(W-5)가 막는다.

## 2b. 전건 `check` 첫 실행 — style 넷에서 끊겼다

```
flock ~/.local/bid-vector-build.lock ./gradlew --no-daemon check
```

- 핵심 결과: exit **1** · `:adapters:detekt` 2건(`MagicNumber` 바인딩 자리 리터럴 · test `MaxLineLength`) +
  `:adapters:ktlintMainSourceSetCheck` 7건(`when-entry-bracing` 5 · `blank-line-between-when-conditions` 2,
  전부 잠정 1위 복원 `when` 한 자리). **test 는 한 건도 돌기 전에 끊겼다** — 이 실행은 style 축만 잰 것이다.
- 고친 뒤 재실행 결과는 §3 에 적는다(같은 명령).

## 2c. 「충돌 0」 병합의 항목 계수 (7C③ 가 찾은 함정 — 같은 자리·같은 본문은 하나로 접힌다)

`46ac0949` 병합은 git 이 충돌 0 으로 접었다. 접힘이 있었다면 **항목 하나가 본문을 잃는다** — 그래서 양쪽이
모두 만진 두 파일을 기계로 셌다(읽기만).

술어를 적는다 — 이름 없는 수는 재현되지 않는다(vr r1 L-5 가 앞 판의 어긋난 수를 짚었다). 병합 커밋 `d373224a`,
공통 조상 `1107f848`, 등재 항목은 `^[[:space:]]+bidvector\.` 로 센 **고유 줄**이다(머리말 주석에 이름이 나오는
class 는 등재가 아니라 제외된다 — 식별자를 통째로 긁는 술어와 수가 다른 이유가 이것이다).

| 파일 | 공통 조상 | 내 쪽 | main | 병합 결과 | 내 추가 | main 추가 | 잃은 항목 |
|---|---:|---:|---:|---:|---:|---:|---:|
| `config/quality/gate-tests.properties`(등재 항목) | 442 | 448 | 448 | **454** | 6 | 6 | **0** |
| `docs/discovery/capability-map.md`(조상 대비 추가 줄) | — | +15 | +1 | 둘 다 그대로 | 15 | 1 | **0** |

**양끝 확인**(한쪽만 보면 접힘이 보이지 않는다): 병합본 ∖ 내 쪽 = **6** = main 의 추가분이고, 병합본 ∖ main =
**6** = 내 추가분이며, 두 추가분의 합집합 가운데 병합본에 없는 항목이 **0** 이다. capability map 도 같은 방향으로
재어 양쪽의 추가 줄이 하나도 빠지지 않았다(15/15 · 1/1).

## 2d. 낡은 `.class` 대조 (지운·옮긴 타입이 있는 라운드의 로컬 실측 전제)

증분 빌드의 `build/classes` 에는 **지운 타입의 낡은 class** 가 남는다(CI 는 매번 새 checkout 이라 없다).
이 라운드는 파일 넷을 개명·이동했으므로 측정 전에 대조했다.

- **발화했다**: `app/test` 의 `OpeningResultTestWiring.class`(소스에서 지운 `@Configuration`). 그 class 가
  test classpath 에 남아 있는 한 component scan 이 집어 들어 **빈 충돌이 소스를 고쳐도 재현**된다.
- 그래서 그 실행 결과를 버리고 **`clean` 부터 다시 쟀다**(`CLEAN_EXIT=0`).
- 대조 술어는 **두 축을 함께** 본다 — ① class 가 가리키는 `SourceFile` 이 모듈 소스에 있는가(파일을 지우거나
  개명한 경우·중첩 class 포함) ② `$` 로 나뉜 마디가 **그 파일 자신의 선언**에 있는가(파일은 남고 안의 선언만
  지운 경우). 한 축만 보면 각각을 놓친다. 범위를 그 파일 하나로 좁혀 람다 오탐을 없앴다(873 → 0).
- **재측정 뒤 결과: 훑은 class 4,027 · 소스 근거 없는 class 0**(스캐너 종료 코드 0).

## 3. acceptance — `ci.yml` 의 `check` job 과 `web` job 명령 그대로

**`ml-engine`·`container` job 생략 근거(계약 문면)** — 그 두 job 의 입력 변경이 **0** 이다:

```
git diff --name-only 46ac0949..HEAD -- ml-engine/ container/ .github/   # 0 파일
git status --porcelain -- ml-engine/ container/ .github/                # 0 파일
```

`check` job 과 `web` job 은 **명령 그대로** 돈다.

```
flock ~/.local/bid-vector-build.lock ./gradlew --no-daemon clean      # CLEAN_EXIT=0
cd web && npm run api:types                                           # TYPES_EXIT=0 (문서 무변경 → 커밋 0)
flock … ./gradlew --no-daemon ktlintCheck detekt                      # STYLE_EXIT=0
flock … ./gradlew --no-daemon check                                   # CHECK_EXIT=0
```

- 핵심 결과: **`check` exit 0**(BUILD SUCCESSFUL, 23m59s · task 줄 598). 이 실행이 **판정의 근거**다 —
  앞선 실행들은 style(ktlint `when`·import·`function-signature`·KDoc 자리)과 detekt(`MatchingDeclarationName`·
  `ReturnCount`)과 `sizeGate`(50줄)에서 끊겼고, 그 전부를 고친 뒤의 실행이다.
- 모듈별 test 계수는 이 실행 **직후 변이 실행이 같은 자리의 XML 을 덮었다** — 그래서 이 절은 계수를 옮겨
  적지 않는다(게이트의 판정은 종료 코드다). 계수가 필요하면 `check` 를 다시 돌려 그 자리에서 읽는다.
- `web` job 명령 셋은 §5 에 따로 적는다.

**`check` job 의 나머지 두 명령**(vr r1 L-5 — 앞 판은 이 절을 비워 두었다). CI 의 `check` job 은 명령이 셋이다:
`./gradlew --no-daemon check` · `./gradlew --no-daemon qualityBaseline` · `./tools/one-command-check.sh`(S-20).

- `qualityBaseline` 은 **이 레인이 직접 돌린다**(§3b).
- **S-20 은 돌리지 않았고, 그 커버리지를 스크립트 전수로 보인다**(`tools/one-command-check.sh` 의 `_step` 을 전부
  읽었다) — S-20 이 부르는 것은 ⓐ `check` ⓑ `qualityBaseline`
  ⓒ Python(S-1~S-11, `ml-engine` job 과 **같은 입력**이고 그 입력 변경이 0 이라 계약이 생략을 허용한다)
  ⓓ `web` 전건(W-1~W-11, §5 에서 열한 단계 전부 실행). 즉 S-20 의 네 갈래가 모두 다른 자리에서 돌았고, S-20
  자체는 그 넷을 부르는 **상위 스크립트**다. 이 논증이 깨지는 조건은 하나다 — S-20 이 그 넷 밖의 검사를 더
  가지는 것. 그래서 다음 라운드에서 스크립트가 바뀌면 이 인용을 다시 봐야 한다.

## 4. 변이 — 바꿔치우기 (여덟, 전부 적용 실측)

각 행은 **더하기가 아니라 바꿔치우기**다(변이 전에 산출물을 커밋해 두고, 변이 뒤 `git checkout --` 로 되돌린다).
`--rerun-tasks` 로 돌려 **cache 적중을 실행으로 읽지 않는다** — 아래 「cache」 열이 그 계수다(여덟 모두 0).

| 변이 | 무엇을 무엇으로 | exit | RED | cache | RED 로 선 test |
|---|---|---:|---:|---:|---|
| M1 | 상호 열을 SELECT 에 더하고 사실·DTO 에 칸을 만든다(세 자리 동시) | 1 | **3** | 0 | 어댑터 표지 부재 · 계약 키 집합 등식 · E2E 본문 표지 |
| M2 | 읽기 전용 **문장**을 `autoCommit=false` 로 | 1 | **2** | 0 | 「모든 문장이 읽기 전용 안」 + 세 표 지문 |
| M3 | 「승격 전」 갈래를 「수집 전」으로 | 1 | **3** | 0 | 순수 판정 우회 ④ · endpoint 상태 아홉 · E2E 승격 전/수집 전 |
| M4 | 등재 밖 라벨 → `OPENED` | 1 | **1** | 0 | `우회 ⑥ — 등재 밖 라벨은 개찰완료로 접히지 않고 판정 불가다` |
| M5 | **공용 자리**의 반복 판정을 빈 목록으로 | 1 | **2** | 0 | **두 endpoint 가 함께** — 7C① 「같은 매개변수를 두 번 …」 + 내 「우회 ⑨ …」(`D-7C5-8` 조건 ②) |
| M5b | **공용 자리**의 읽기 실패를 빈 값으로 삼킴 | 1 | **2** | 0 | 7C① 둘(이스케이프 문법 · 잘못된 UTF-8). **내 endpoint 는 초록** — 아래 비대칭 |
| M6 | 확정 금액 읽기에 잠정 축 폴백 | 1 | **1** | 0 | 어댑터 우회 ③ |
| M7 | 마감 경계 `<` → `<=` | 1 | **1** | 0 | 순수 판정 마감 경계 |

**M5b 의 비대칭은 숨기지 않고 적는다**(예측하고 통제군으로 둔 자리다). 공용 「읽는 자리」가 해석 실패를 삼키면
7C① 의 두 test 는 붉어지지만 **내 endpoint 는 초록**이다 — 내 계약은 두 칸이 **필수**라, 읽기가 삼켜져 값이 비면
`NOTICE_NUMBER_MISSING` 으로 **같은 400** 이 나간다. 상태 코드로는 두 경로가 구별되지 않는다. 지키려는 약속
(「500 이 아니다」)은 두 경우 모두 성립하므로 이 자리는 **결함이 아니라 측정의 한계**이고, 그 한계를 공용 자리의
7C① 쪽 test 가 메운다(같은 함수를 두 endpoint 가 쓰므로 그 RED 가 곧 이 자리의 경보다).

**앞 실행의 가짜 초록을 버린 사실**: 첫 변이 실행에서 M4·M5·M5b 가 `FileNotFoundError` 로 **적용되지 않은 채**
초록이었다(이 라운드에 그 세 파일을 개명했다 — 좁은 필터·낡은 경로의 초록은 「방어 있음」과 구별되지 않는다).
그 셋을 버리고 경로를 고쳐 다시 쟀고, 스크립트는 이제 **적용 실패 시 `MUTATION_NOT_APPLIED` 로 멈춘다**.

## 5. `web` job — 명령 그대로, 열한 단계 전부 exit 0

CI 전용 step 둘(체크아웃·툴체인 설치 · 브라우저 **시스템 패키지**(apt))만 빼고 `ci.yml` 의 `web` job 명령을
순서까지 그대로 돌렸다(`flock` 경유, HEAD `7cc9c5b2`).

| step | 명령 | exit |
|---|---|---:|
| — | `npm ci` | 0 |
| W-2 | `npm run lint` | 0 |
| W-3 | `npm run typecheck` | 0 |
| W-4 | `npm run gate:src-tree` | 0 |
| W-5 | `npm run api:types:check` | 0 — **생성 타입이 문서와 바이트까지 같다**(113,104 bytes) |
| W-6 | `npm run test` | 0 — test file 5 · test 28 통과 |
| W-7 | `npm run gate:tests` | 0 |
| W-8 | `npm run build` | 0 |
| W-9 | `npm run gate:boundary-canary` | 0 |
| W-10 | `npx playwright install chromium` | 0 |
| W-11 | `npm run test:e2e` | 0 — 스모크 2 통과 |

W-5 가 이 slice 에서 특히 중요한 자리다: OpenAPI 를 병합으로 다시 세운 뒤(main 전문 + 내 operation·schema
재삽입) 생성 타입을 재생성했고, 그 둘이 **바이트까지 같다**는 것이 문서와 구현이 갈리지 않았다는 실측이다.
