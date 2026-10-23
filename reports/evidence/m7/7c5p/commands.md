# M7/7C⑤-0 — 실행한 명령과 결과

base **`54e2c5e2`**(main — g1c03 머지 뒤 rebase, D-7C5P-11 ⑥) · **판정 SHA(마지막 산출물 커밋)
`bf0e0f6c`** · 브랜치 `m7-7c5p/2026-10-23`. rebase 로 앞 라운드 SHA 는 모두 바뀌었다 — 그 전 이력은
로컬 브랜치 `m7-7c5p/pre-rebase-2026-10-23`(= 옛 `9eb3e225`)에 남겨 두었다.

**어느 측정이 어느 SHA 의 것인가.** 마지막 라운드(D-7C5P-13)는 주석·KDoc 만 고쳤고 **주석 밖 줄이
0** 이다(§4 의 `git diff -U0` 계수). 그래서 거동이 바뀌지 않는 측정은 다시 돌리지 않았다 —
`check`(찬 clone) · rollback ①~⑥ · 유효성 술어는 **`bf0e0f6c`** 에서, 변이 전수와 container 재생은
**`a2d4724b`**(그 직전 산출물 커밋, 두 SHA 사이의 Kotlin 변경이 전부 주석)에서다.
모든 Gradle·docker 명령은 `flock ~/.local/bid-vector-build.lock` 아래 직렬로 돌았다(호스트 공유).
종료 코드는 각 명령에서 직접 찍었다 — 파이프 `grep` 으로 판정하지 않았다.

## 1. acceptance — Kotlin `check` job

| 명령 | 자리 | 결과 |
|---|---|---|
| `./gradlew --no-daemon check` | **버릴 clone**(판정 SHA `bf0e0f6c`, build 출력 없음 = runner 와 같은 상태) | **exit 0** · `BUILD SUCCESSFUL in 10m 13s` |

**찬 트리에서 재는 이유**: 같은 명령을 작업 트리에서 돌리면 **19 초에 초록**이 나온다(431 task 중
369 가 UP-TO-DATE). 입력이 같으니 틀린 초록은 아니지만, 그 초록은 「게이트가 이 트리를 봤다」와
「게이트를 안 돌렸다」를 구별하지 못한다. 그래서 판정은 clone 에서 잰 것으로 한다.

clone 실행의 내역(443 task): 실행 **267** · FROM-CACHE 110 · NO-SOURCE 38 · UP-TO-DATE 15 ·
SKIPPED 13. 승격 test 가 사는 네 모듈의 test task 가 **모두 이 clone 에서 실행됐다** —
`:procurement:test` · `:workflow:test` · `:adapters:test`(실 DB) · `:app:test` 넷 다 캐시 적중이
아니다(마지막 라운드가 그 네 모듈의 test 입력을 건드려 캐시가 비껴갔다). 캐시 적중 110 건은 이 slice 가
건드리지 않은 모듈들이다.

`.github/workflows/ci.yml` 의 `check` job 이 드는 게이트 전부가 이 한 명령 안이다 — 컴파일 ·
ktlint · detekt · sizeGate · cpd · domainDependencyGate · architecture test · contractGate ·
jarContentGate · gateRegistrationGate · gateExecutionGate · kover · compatibilitySmoke, 그리고
`:procurement:test`·`:workflow:test`·`:adapters:test`(실 DB, Testcontainers)·`:app:test`.

같은 job 의 뒤 step 둘은 **게이트가 아니다**: `qualityBaseline` 은 측정이고(실패시키지 않는다),
`tools/one-command-check.sh` 는 Node·브라우저 전제를 요구한다 — 이 레인에서 돌리지 않았고 그
사실을 여기 적는다.

**게이트가 먼저 붉어진 실측(자연 발생 RED)** — 정책을 건드리기 **전에** 실제 코드에서 붉었고,
전부 코드·구조를 고쳐 닫았다(허용 목록을 느슨하게 한 곳 0). 이것은 **test RED 가 아니다**.

| 게이트 | 무엇을 가리켰나 | 어떻게 닫았나 |
|---|---|---|
| `ArchitectureGateTest` domain 순수성 | `uppercase()` 가 `java.util.Locale.ROOT` 를 끌어왔다 | 대소문자를 열거로 받는다(프로토콜 플래그에 로케일 접기를 쓰지 않는다) |
| `CollectionArchitectureGateTest` 참조 집합 | use case 가 canonical fact 를 열었다 | 승격 결과가 공고 식별자를 나르게 해 참조를 없앴다 |
| `CollectionArchitectureGateTest` 로거 집합 | 미등재 로거 사용 | 로그 출구를 만드는 배선 집합에 등재 |
| `TransportSurfaceGateTest` 쌍 등식(2건) | 실행 상태 경로를 짓는 자리 | 타입 쌍 · 숨은 송신 멤버 쌍 등재(형제 배선과 같은 모양) |
| `AppHttpDependencyGateTest` 면제 집합·크기 | 미분류 app 클래스 · 면제 수 | ① 층·③ 층 분류 + 래칫 62→65(설정 타입은 넣지 않았다) |
| `jarContentGate` | inline 비교자 클래스가 stdlib 소스로 찍혔다 | 이름 있는 `Comparator`(이미 이 저장소의 관례) |
| `detekt` 파일당 함수·return 수 · `sizeGate` 파일 500줄 | 한 파일 16 함수 · test 756줄 | **책임 셋**으로 분할(production·test 같은 경계) + 금액 조립 셋을 하나로 |
| `:workflow:sizeGate`(rebase 뒤 라운드) | use case test **501 줄**(한도 500) | 대역·고정 값·원장 줄 짓기를 support 로 **책임 분할**(test 358 · support 187) — 줄 수를 맞추려 자른 것이 아니라 이 패키지가 이미 쓰는 경계다 |

## 2. `container` job 전체 (D-7C5P-9)

손 전사를 쓰지 않는다. **기계 재생기**(COL-03 레인 산출물의 **무편집 사본** — 원본과 바이트 동일)가
`ci.yml` 의 `jobs.container` 를 파싱해 `run:` 블록을 **바이트 그대로** GitHub 기본 셸로 순서대로
돌리고, `if:` 는 앞 step 결과로 평가하며 `GITHUB_ENV`·`GITHUB_PATH`·`RUNNER_TEMP` 를 에뮬레이트한다.
그래서 측정 대상이 내 전사본이 아니라 **CI YAML 자체**다. `uses:` 넷은 runner 전용이라 실행하지 않고
`if:` 평가만 기록한다. `trivy` 는 **job 자신의 `install_trivy` step 이 설치**한다(미리 깔지 않았다).

| 재는 것 | 결과 |
|---|---|
| step 수 | **27** (run 23 · uses 4) |
| run step 결과 | **success 23 · failure 0** (각 step 종료 코드 0) |
| uses step | 4 — runner 전용, 실행하지 않고 `if:` 평가만 기록 |
| skipped | **0** — `scan_app`·`scan_web`·`scan_proxy` 의 `if:` 셋이 모두 참으로 평가돼 실행·성공 |
| JOB | **success** (`a2d4724b` 에서 재생, `REPLAY_EXIT=0`) |

이미지 태그 기록과 복원(실행 전 ID 를 적고 끝나서 되돌린다):

| 태그 | 실행 전 | 실행 후 | 조치 |
|---|---|---|---|
| app | `94382c10` | `e22ad177` | 실행 전 ID 로 복원(exit 0, 복원 뒤 ID 재확인) |
| web | `85517138` | `fc8869f6` | 실행 전 ID 로 복원(exit 0, 복원 뒤 ID 재확인) |
| proxy | `d93e25e0` | 변화 없음 | 불필요 |
| ml-serving | `326e9da3` | 변화 없음 | 불필요 |

정리 확인: compose 컨테이너 **0** · 볼륨 **0**. 6G dev DB 컨테이너는 **손대지 않았고** 실행 전후로 Up
상태 그대로다. app 이미지 ID 가 바뀐 것이 「이 slice 의 변경이 실제로 배포물에 닿는다」의 실측이다.

## 3. 변이 실측 — 바꿔치우기 21건 + 음성 대조 1건

**RED 를 별 라운드로 관측하지 못했다**(사실 선언): test 와 구현을 함께 썼고 첫 실행이 초록이었다.
그래서 test 강도는 **변이**가 진다. 변이는 전부 **바꿔치우기**다(더하기만 한 변이는 초록으로
지나간다). 산출물은 변이 전에 커밋돼 있다 — 되돌림이 `git checkout --` 이라 미커밋 산출물을 함께
지운다. 음성 대조는 `--rerun` 으로 돌린다: 적중 캐시의 초록은 **실행이 아니다**.

`a2d4724b` 에서 **22/22 기대와 일치**(어긋남 0 · 패치 실패 0 — 전수 21 건 한 번 + 아래
`MR2-1c` 한 건을 필터를 넓혀 따로). 표의 패치·붉어진 test 는 로그에서 **기계로** 뽑았다(손 전사 없음).

| 변이 | 패치 한 줄 | 기대 | 관측 | 붉어진 test |
|---|---|---|---|---|
| M0b 음성 대조 — 비교자 개명(선언 + 호출부, 거동 0) | `…LATEST_OBSERVATION_FIRST)` → `…OBSERVATION_RECENCY_ORDER)` (+1) | GREEN | GREEN | — |
| MR2-1 결말 부재를 하나로 접는다 — 상세 축도 행 시각으로 물러선다 | `conclusion == null && endpoint in OPE…` → `(줄 삭제)` | RED | RED | `OpeningPromotionWalkTest > 결말 없는 상세 축의 행은 승격하지 않는다 — 원장 줄이 없어도 마찬가지다`<br>`OpeningPromotionWalkTest > 걷기 선택은 축과 결말로 갈린다`<br>`OpeningPromotionWalkTest > 결말 없는 상세 축이 있어도 목록 축은 물러서서 승격된다` |
| MR2-1b 상세 축 거울에 목록 축을 넣는다(수집 DetailAxis 와 등식 깨기) | `SourceEndpoint.RESERVE_PRICE_DETAIL, …` → `SourceEndpoint.OPENING_RESULT_LIST, S…` | RED | RED | `OpeningPromotionWalkTest > 결말 없는 상세 축의 행은 승격하지 않는다 — 원장 줄이 없어도 마찬가지다`<br>`OpeningPromotionWalkTest > 원장 줄이 없는 목록 축은 가장 늦은 관측으로 물러선다`<br>`OpeningPromotionWalkTest > 걷기 선택은 축과 결말로 갈린다` |
| MR2-1c 거울에서 A값 축을 뺀다 — 승격 거동 불변, 등식만 깨진다 | `SourceEndpoint.BID_PRICE_FORMULA_A,` → `(줄 삭제)` | RED | RED | `PromoteOpeningAxisClassTest > 승격의 상세 축 집합은 수집 팬아웃의 DetailAxis 와 같다`<br>`OpeningPromotionAxisParityTest > 승격의 상세 축 집합은 추출의 공사 기대 축 집합과 같다`<br>`OpeningPromotionAxisParityTest > 공사가 아니면 추출의 기대 축이 더 작다 — 승격이 더 엄한 쪽이다` |
| Mleak 실패 줄에 원 예외 메시지를 싣는다 | `…causeCode))` → `…failure.message ?: causeCode))` | RED | RED | `OpeningPromotionRunnerLockTest > 읽기가 던지면 사유 코드만 로그에 남고 원 메시지는 새지 않는다` |
| Mord 비교자의 2차 키 방향을 뒤집는다 | `left.first.ordinal.compareTo(right.fi…` → `right.first.ordinal.compareTo(left.fi…` | RED | RED | `OpeningPromotionRankOneTest > 같은 시각이면 축 열거 순서가 앞선 축이 이긴다` |
| MF2 빈 선택 축을 맵에 남긴다 | `}.takeIf { it.isNotEmpty() }?.let { e…` → `}.let { endpoint to it }` | RED | RED | `OpeningPromotionWalkTest > 빈 응답으로 끝난 축은 0 행이다`<br>`OpeningPromotionWalkTest > 결말 없는 상세 축의 행은 승격하지 않는다 — 원장 줄이 없어도 마찬가지다`<br>`OpeningPromotionWalkTest > 원장이 가리킨 걷기의 행이 읽기 집합에 없으면 던지지 않는다` |
| Mtrim 문자열 슬롯의 trim 을 없앤다 | `…?.trim()` → `…` | RED | RED | `OpeningPromotionTest > 둘러싼 공백은 문자열 슬롯에 실리지 않는다` |
| Mlock 러너의 잠금 래퍼를 벗긴다 | `underRunStateLock(runState, "opening-…` → `promoteOrFail()` | RED | RED | `OpeningPromotionRunnerLockTest > 다른 프로세스가 자물쇠를 들고 있으면 승격을 거부한다 — 읽기도 쓰기도 0`<br>`OpeningPromotionRunnerLockTest > 거부한 실행은 마침 줄도 건너뜀 줄도 남기지 않는다` |
| M1 걷기를 원장이 아니라 행 시각으로 고른다 | `conclusion == null && endpoint in OPE…` → `observedAtValues.isEmpty() -> AxisWal…` | RED | RED | `OpeningPromotionWalkTest > 빈 응답으로 끝난 축은 0 행이다`<br>`OpeningPromotionWalkTest > 결말 없는 상세 축의 행은 승격하지 않는다 — 원장 줄이 없어도 마찬가지다`<br>`OpeningPromotionWalkTest > 걷기 선택은 축과 결말로 갈린다` |
| M2 부재인 셈을 0 으로 적재한다 | `….takeIf { it >= 0 }` → `…: 0` | RED | RED | `OpeningPromotionTest > 키가 없으면 슬롯이 부재다`<br>`OpeningPromotionTest > 음수 셈은 부재다`<br>`OpeningPromotionTest > 해석되지 않는 값은 부재이고 0 이 아니다` |
| M3 금액의 소수부를 깎는다 | `?.stripTrailingZeros() ?.takeIf { it.…` → `?.let { whole -> runCatching { whole.…` | RED | RED | `OpeningPromotionTest > 소수부가 섞인 금액은 깎지 않고 부재다`<br>`OpeningPromotionRankOneTest > 투찰금액의 소수부는 1위 축에서도 깎이지 않는다` |
| M4 멱등 술어를 항진식으로 | `): Boolean = stored == null || promot…` → `): Boolean = true` | RED | RED | `OpeningPromotionRankOneTest > 승격은 저장된 관측보다 늦을 때만 쓴다`<br>`PromoteOpeningResultsUseCaseTest > 같은 입력 두 번이면 둘째 실행은 쓰지 않는다`<br>`OpeningPromotionAssemblyTest > 출하 조립이 원문을 canonical 로 올리고 둘째 실행은 행을 움직이지 않는다` |
| M5 추첨번호 축을 비우지 않는다 | `…NotObserved,` → `…RangeCheckUnavailable( setOf(1), con…` | RED | RED | `OpeningPromotionTest > 추첨번호 축은 원문에 번호가 있어도 승격되지 않는다` |
| M6 1위 축의 전부-아니면-무를 없앤다 | `… || candidates.any { it == null }) {` → `…) {` | RED | RED | `OpeningPromotionRankOneTest > 상호를 얻지 못한 투찰 행이 있으면 1위 축을 비운다` |
| M7 마스킹 표지 제외를 없앤다 | `AND pii_masked_at IS NULL` → `(줄 삭제)` | RED | RED | `OpeningPromotionRepositoryTest > 마스킹된 원문은 승격 대상에서 빠지고 수가 공시된다` |
| M8 보존 창 하한을 무력화한다 | `AND inserted_at >= ?` → `AND (inserted_at >= ? OR TRUE)` | RED | RED | `OpeningPromotionRepositoryTest > 보존 창보다 오래된 원문은 승격 대상에서 빠지고 수가 공시된다` |
| M9 확정 표본 필터를 없앤다 | `if (hash !in sample.keys) {` → `if (false) {` | RED | RED | `PromoteOpeningResultsUseCaseTest > 표본 밖 공고는 승격하지 않는다`<br>`PromoteOpeningResultsUseCaseTest > 사유별 계수의 합은 읽은 묶음 수와 같다`<br>`OpeningPromotionAssemblyTest > 표본 밖 공고는 출하 조립에서도 승격되지 않는다` |
| M10 canonical 저장소 생성을 한 걸음 옮긴다 | `results = JdbcOpeningResultRepository…` → `results = canonicalOpeningResultsOf(d…` (+1) | RED | RED | `OpeningPromotionWiringTest > canonical 개찰 저장소를 만드는 production 자리는 승격 배선 하나다` |
| M11 로그 줄의 두 계수를 맞바꾼다 | `…promoted} " + "skippedUnchanged=${re…` → `…skippedUnchanged} " + "skippedUnchan…` | RED | RED | `OpeningPromotionRunnerTest > 계수마다 다른 수를 주면 줄의 칸이 그 수를 그대로 싣는다` |
| M12 축 선언에 계약에 없는 축을 넣는다 | `…SourceEndpoint.OPENING_RESULT_LIST, …` → `… SourceEndpoint.OPENING_RESULT_LIST,…` | RED | RED | `OpeningPromotionRankOneTest > 선언한 축은 그 개념의 계약 presentIn 안이다` |
| M13 domain 이 로케일을 끌어온다 | `…) { "Y", "y" -> true "N", "n" -> fal…` → `…?.uppercase()) { "Y" -> true "N" -> …` | RED | RED | `ArchitectureGateTest > 도메인 모듈이 허용 목록 밖을 보지 않는다` |

패치 칸은 **갈리는 지점의 창**이다(공통 머리는 `…` 로 떼고, 폭을 넘으면 꼬리를 자른다). 집합 리터럴에
한 항을 끼우는 변이처럼 삽입 자리가 창 밖에 떨어지는 행도 있다 — 그 행에서 방어를 증명하는 것은
**붉어진 test 이름**이고, 패치 본문은 재현 스크립트(`mutations-r3-full.py`)가 정본이다.

**어느 test 가 새 규칙의 회귀인가(CR r3 MEDIUM)** — 표의 「붉어진 test」는 변이마다 **최대 세 줄**만
기록한다(로그가 그렇게 찍는다). `MR2-1`(축 분기 줄 삭제)의 기록된 셋은 `OpeningPromotionWalkTest` 의
것이고, use case 층에서 붉어진 이름은 그 절삭에 걸려 표에 없다 — `:workflow:test=1` 이 그 층에서도
붉었다는 사실만 남는다.

그 층의 회귀를 지는 것은 **`PROMOTE-016`**(열린 라운드 줄만 · AXIS 줄 없음)과 **`PROMOTE-018`**(원장 줄
0)이다. 둘 다 결말이 없는 상세 축이고, 축 분기를 지우면 행 시각으로 물러서서 승격 1 이 되므로 붉어진다
(분기 구조에서 따라오는 결론이고, `PROMOTE-018` 은 구판 RED 로도 측정됐다 — §3b).

반대로 **g1c03 합성 사례는 축 분기의 회귀가 아니다**: 원장 판독(`axisConclusions`)은 갈래(`AXIS`)만 보고
어휘를 거르지 않으므로 닫는 줄은 결말을 **내고**, 그 결말이 `Failed` 라 **실패 결말 갈래**가 행을 버린다
(r1 부터 있던 분기). 그 사례의 값은 「닫힌 뒤에도 승격되지 않는다」를 고정하는 **합성**이며, test 주석을
이 기제로 고쳤다.

**측정의 함정 셋 — 이 slice 가 실측으로 배운 것**

- 음성 대조의 패치가 선언만 바꾸고 호출부를 남기면 **컴파일이 깨진다**. 그때 받은 초록은 「거동 0」이
  아니라 「빌드 0」이다 — 선언과 호출부를 함께 바꾸는 패치로 다시 쟀다.
- **변이의 필터는 파일·패키지 분할을 따라가야 한다.** 좁은 필터의 초록은 「방어가 없다」와 「재지
  않았다」를 같은 값으로 만든다. 앞 라운드에서 test 가 다른 클래스로 옮겨가 두 변이가 헛초록이었고,
  이 라운드에서는 등식 test 가 `adapters.snapshot` 패키지로 가 필터를 거기까지 넓혔다.
- **통제군은 `--rerun` 이다.** 첫 실행에서 통제군이 `:procurement:test=0` 으로 초록이었는데, 그 초록이
  실행인지 캐시 적중인지 구별되지 않았다 — 통제군만 `--rerun` 으로 다시 쟀다.

## 3b. 회귀 test 가 구판에서 RED 인가 (R2-1)

구판은 **`7c2167c7`**(R2-1 수정 직전 트리)다. 팀장이 지목한 `73f31710` 은 그 **rebase 이전 등가**이고
그 커밋은 g1c03 머지 **전**이라, 새 test 파일이 쓰는 `INTERRUPTED_ROUND` 가 없어 거기서는 컴파일되지
않는다 — 같은 트리의 rebase 등가에서 쟀다(rebase 이전 이력은 로컬 브랜치
`m7-7c5p/pre-rebase-2026-10-23` 에 남겨 두었다).

use case 층은 생성자가 바뀌지 않아 **출하 test 파일을 그대로** 얹었다(support 파일과 함께). 순수 층은
이 수정으로 `promoteOpeningResult` 서명이 바뀌어 출하 test 가 구판에 얹히지 않으므로, **구판 서명으로
쓴 탐침**으로 같은 사실을 쟀다.

| 재는 것(층) | 구판 결과 |
|---|---|
| `원장 줄이 하나도 없는 상세 축의 행은 승격하지 않는다`(use case, 출하 test 그대로) | **FAILED** |
| `결말 없는 상세 축의 행은 승격하지 않는다`(순수, 구판 서명 탐침) | **FAILED** |

CR MEDIUM-2(비교자 주석)·LOW-2(러너 실패 경로)는 **결함이 아니라 문면·미측정**이었으므로 구판 RED 가
없다 — 그 강도는 변이 `Mord`·`Mleak` 가 진다(§3).

## 3c. 변이 재현 명령

변이는 스크립트 하나가 돌린다 — **적용 → 좁은 task 실행 → `git checkout --` 로 되돌림**을 변이마다
반복하고, 패치는 「정확히 한 번 출현」을 요구해 빗나간 패치가 조용히 지나가지 않는다. 산출물이 먼저
커밋돼 있어야 한다. 결과표(§3)는 그 로그에서 기계로 뽑는다.

필터는 **분할 뒤 클래스·패키지 전수**다:

```
:procurement:test --tests 'bidvector.procurement.OpeningPromotion*'
:workflow:test    --tests 'bidvector.workflow.collection.PromoteOpening*'
:adapters:test    --tests 'bidvector.adapters.persistence.OpeningPromotion*' \
                  --tests 'bidvector.adapters.snapshot.OpeningPromotionAxisParityTest'
:app:test         --tests 'bidvector.app.collection.OpeningPromotion*' \
                  --tests 'bidvector.app.wiring.OpeningPromotionWiringTest'
```

통제군에만 `--rerun` 을 붙인다: RED 를 기대하는 변이는 캐시 적중이 와도 초록이 되어 **어긋남으로
드러나지만**, 통제군의 초록은 그 구별이 없다.

## 4. rollback ①~⑥ 실측

`rollback.md` 가 목록과 판정을 담는다. 실측은 **버릴 clone** 에서 돌았고 작업 트리를 건드리지 않았다.

## 4b. 잔존 상한 180일의 유도 — 빠진 두 항 (L4)

「보존 창의 두 배」는 **승격이 더하는 지연**만 센 값이다. 실제 잔존에는 두 항이 더 붙는다.
ⓐ **파기 실행 주기** — 파기는 즉시가 아니라 배치라 그 주기가 더해진다. ⓑ **정책 해석의 기준일** —
보존 정책이 실행일이 아니라 운영자 입력 `reference-date` 로 해소되므로, 정책 판이 바뀌면 승격 창과
마스킹 창이 갈릴 수 있다. 유도 자체(적재 시각 하한 = `now − 창` → 승격 지연 ≤ 창 → canonical 행
`created_at` + 창 파기)는 코드와 일치한다. 두 항은 점검표 제한 15 에 등재했다.

## 5. 호스트 규율

모든 라운드 전에 점검 셋을 **별도 호출로 먼저** 돌렸다 — `pgrep -af java`(java 만 센다: 남의 bash
대기 루프를 세면 오탐이다) · `free -m` · `ps -eo pid,rss,args --sort=-rss | head`. available 6GB 이상 ·
swap free 2GB 이상에서만 시작했고, 00:30~03:00 KST 에는 시작하지 않았다.

이 라운드의 무거운 실행 여섯(찬 acceptance · 변이 전수 · 변이 하나 · 구판 RED · rollback ①~⑥ ·
container 재생)은 **한 자물쇠에 줄 세워** 돌았다(`flock ~/.local/bid-vector-build.lock`). 다른 레인의
빌드가 자물쇠를 들고 있던 동안에는 큐에서 기다렸고, 기다리는 사이에 자리를 비우지 않고 로그 꼬리를
직접 읽었다. 중간에 한 번 **낡은 트리를 돌던 내 빌드를 내가 멈췄다**(자물쇠를 오래 쥐고 있었고 결과가
쓸모없었다) — 멈춘 뒤 적용돼 있던 변이 패치 한 줄을 `git checkout --` 로 되돌리고 트리가 깨끗한지
확인한 다음 다시 세웠다.
