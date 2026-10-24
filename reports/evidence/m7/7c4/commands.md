# M7/7C④ — 명령과 결과 (Phase 2a)

전부 `flock /home/deploy/.local/bid-vector-build.lock ./gradlew --no-daemon …` 로 돌렸다(호스트 전체에서 무거운 빌드 하나).
무거운 명령 앞마다 확인 셋(pgrep · `free -m` · RSS 상위)을 **별도 호출로 먼저** 돌렸고, 다른 레인(vr5 · 7C②)의 빌드가 도는
동안에는 락에서 기다렸다. 날짜는 2026-10-24 KST, 판정 SHA 는 `1b3ade0b`(base main `fc15975c`).

## acceptance — `check` job (`.github/workflows/ci.yml` 의 명령 그대로)

- **찬 `check`**: `check --continue --rerun-tasks --no-build-cache` (HEAD `1b3ade0b`, 18:21~18:34) → **exit 0**,
  `BUILD SUCCESSFUL in 12m 5s`, **365 task 전부 실행**(up-to-date 0 — 캐시 적중으로 조용히 넘어간 게이트가 없다).
  루트 게이트 셋(`leakPatternGate` · `contractGate` · `qualityBaseline`)이 실행으로 남았고, 모듈마다 `sizeGate` ·
  `moduleDependencyGate` · `packageOwnershipGate` · `gateExecutionGate` · `gateRegistrationGate` · `typeShapeGate` ·
  `testShapeGate` · `domainApiTypeGate` · ktlint 셋 · detekt 가 같은 로그에 있다. 비밀값 스캔 어휘는 그 게이트의 정책
  파일이 들고, 여기에 축어로 옮기지 않는다.
- **`web` job 은 돌리지 않았다** — 2a 는 HTTP 표면이 없어 `web/` · OpenAPI · 생성 타입에 닿지 않는다(변경 경로에 그 셋이
  없다). 2b 에서 돈다.
- 이 slice 의 test 는 그 run 에서 전부 돌았다 — `workflow` 의 `profile` 셋(7 · 9 · 3) · `adapters` 의 `profile` 둘(실 DB:
  왕복·소유 경계·절 독립 10, 스키마·권한 7). 실패 0.

## 표적 — RED 먼저

- **RED (17:40~17:55)** `:workflow:test --tests 'bidvector.workflow.profile.*'` → exit 1. 둘이 함께 붉었다.
  - `OnboardingChecklistTest` **9 중 7** 이 `IllegalStateException`(「선행 항목이 선언되지 않은 「아직 모름」 항목」) —
    앞 세션이 남긴 미커밋 D-7C4-2 구현의 **초기화 순환**이다(checklist §3.1). 이어받기 전에 돌려서 드러났다.
  - `ProfileJudgementIsolationTest` 1 — 축 어휘를 셋으로 먼저 고정한 RED(`QUAL_10_PERFORMANCE` 가 아직 없다).
- **GREEN (18:18)** 같은 명령 → exit 0, 세 class 19 test 실패 0(결과 XML 시각 18:18:11 — 캐시가 아니라 실행).

## 변이 — 바꿔치우기, 통제군은 `--rerun`

버릴 clone(`scratchpad/7c4/mut`, HEAD `1b3ade0b`)에서 하나씩 적용했다. 적용 여부는 **원문 부재**로 확인했고(출현 1회가
아니면 중단), 라운드마다 결과 XML 시각을 함께 적었다 — 앞 라운드와 같은 시각이면 그 라운드는 돌지 않은 것이다.

| # | 바꿔치운 것 | 결과 |
|---|---|---|
| 통제군 0 | 없음 | exit 0 · 실패 0 (18:36:08.890) |
| M1 | 분야 미답의 적용 상태를 「선행 대기」 → 「해당 없음」(D-7C4-2 이전 납작화) | `OnboardingChecklistTest` **3 실패** (18:36:25.774) |
| M2 | 완료·미완 분류 술어를 「걸리는 것」 → 「해당 없음이 아닌 것」(대기를 분모에 넣는다) | 같은 class **3 실패**(전 항목 수 8↔9 · 서로소) (18:36:43.194) |
| M3 | 대기의 선행을 `NARA_READINESS` → `TRADE_NAME` | 같은 class **2 실패** (18:37:00.974) |
| M4 | 판정 대기 축 표에서 `QUAL_10_PERFORMANCE` 행 제거 | `ProfileJudgementIsolationTest` **1 실패** (18:37:19.398) |
| M5 | 그 축의 ID 를 `QUAL_10_TRACK_RECORD` 로(= wire 값 변경) | 같은 class **1 실패** (18:37:36.806) |
| M6 | **음성 대조**(더하기): `workflow.evaluation` 의 한 파일이 `profile` 열거를 참조 | 같은 class **1 실패** — 적발 대상이 `evaluation/ProfilePortsKt.class` 로 이름까지 나왔다 (18:38:46.802) |
| 통제군 1 | 복원 뒤 | exit 0 · 실패 0 (18:37:57.914) |

- M3 은 「대기의 선행은 그 스냅샷에서 걸리는 항목이다」 test 를 **통과한다**(`TRADE_NAME` 도 걸리는 항목이다) — 그 test 가
  재는 것은 죽은 선행·순환뿐이고, 어느 항목이 선행인지는 빈 계정 test 가 잡는다. 둘이 함께 있어야 M3 이 붉다.
- **M6 은 첫 시도가 돌지 않았다**: 삽입을 import 앞에 넣어 `compileKotlin` 이 깨졌고(변이 자체의 결함), 결과 XML 시각이
  앞 라운드와 **같아서** 그 미실행이 드러났다. 파일 끝에 넣어 다시 돌린 것이 위 행이다. 종료 코드만 보면 둘 다 1 이라
  구별되지 않는다 — 시각을 함께 적는 이유다.
- 격리 술어는 텍스트가 아니라 **상수 풀**을 본다 — 전체 한정 이름으로 쓴 M6 의 참조를 텍스트 정규식은 못 봤을 것이다.

## rollback

`rollback.md` 참조(되돌림 목록은 명령으로 도출하고, ①~⑦ 을 임시 clone 에서 실측했다).
