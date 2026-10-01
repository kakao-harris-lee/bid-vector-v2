# M6/6G-2e — 실수집 전 필수 여섯: 개찰 갈래 범위 정책 · 실행 상태 셋 · 백테스트 CLI · seed 수 고정 (계약, 착수 2026-10-02)

> **지위: 착수(2026-10-02).** base `c63d0d3a`(PR #53 6G-2a 머지 뒤 `main`), worktree `bid-vector-v2-m6-6g2e`, 브랜치 `m6-6g2e/2026-10-02`. 운영자 결정: **A-1 개찰 갈래 `maxSpanDays` = 120일**(2026-10-02) · A-2 즉시 착수(2026-10-01). 초안 이력: `docs/m6-6g2-contracts`.
> **초안 시점 문면:** 운영자 결정 2026-10-01 「6G-2a 다음에 6G-2e 를 먼저, 그 뒤 2b → 2c」.
> 수령 OPEN: **`OPEN-6G-OPENING-RANGE-CAP`**(PR #52 리뷰, 2026-10-01 — 실수집 차단) · **`OPEN-6G-REVIEW-FOLLOWUPS` ★ 셋**(6G-2d D-6G2d-53) · **`OPEN-6G-BACKTEST-CLI`**(runbook 대조).
> 이 slice 가 머지되기 전에는 A-1 기간의 실 KONEPS 수집을 시작할 수 없다(6G-2d D-6G2d-55 조건에 추가).

- base: **`c63d0d3a`**.
- 레인: `kotlin-implementer`(①~④) + `ml-implementer`(⑤, 파일 하나) — 둘은 파일 집합이 겹치지 않는다. 호스트 빌드 직렬.

## 왜 이 slice 인가

실수집 runbook(PR #52)의 `/code-review` 가 6G 산출물의 실수집 전제 결함을 잡았다: **수집 범위 상한 31일**(`COLLECTION_RANGE_POLICY.maxSpanDays`, 6F-8 D-6F8-3 — 공고 목록 갈래의 호출 폭주 방지)이 개찰 갈래(`OpeningCollectionWiring.openingCollectionRange` → `resolveCollectionRange`)에도 걸려 A-1 승인 기간(업무별 하한율 변경일 이후 ~ 현재, 최대 16주)이 `SPAN_TOO_LONG` 으로 기동 거부된다. 창을 쪼개 같은 실행 상태 디렉터리로 돌릴 수도 없다 — 확정 표본이 `SampleScope(from, to, divisions)` 를 고정해 「표본틀 범위가 지금 설정과 다르다」로 거부된다. 6G 검증은 짧은 창 E2E 만 돌려 이 자리를 못 봤다. 함께, 6G-2d 가 실수집 전으로 미룬 실행 상태 결함 셋과 백테스트 job 의 CLI 부재를 한 slice 로 닫는다.

## 착수 실측 (착수 시 채운다)

| 항목 | 값 |
|---|---|
| base SHA | `c63d0d3a` |
| `resolveCollectionRange` 호출 자리 | **둘** — `CollectionWiring`(공고 목록) · `OpeningCollectionWiring`(개찰); 함수는 `CollectionWiringSupport.resolveCollectionRange`(팀장 grep). 추출 배선은 부르지 않는다(착수 실측으로 확정) |
| 개찰 갈래 호출 수가 창 길이에 의존하는가 | 개찰결과 목록 = 기간 조회 → 쪽 수가 기간에 비례(상한 20,000/80,000 이 묶음) · 상세 셋 = 공고당 1회 → 표본 크기에만 의존(6G P-2) |
| seed 로더 | `ml_engine/evaluation/policy.py` — `stability_seeds` 는 평탄 색인 키, 검사는 「비어 있지 않음」뿐(길이 미요구) — D-6b 의 자리 |
| 백테스트 CLI | 없음(`backtest_job.py` 에 `__main__`·argparse 없음, `pyproject.toml` scripts 없음) |
| Kotlin `check` test 수 · pytest | 2,588(6G-2d 종결) · 1,350(6G-2a 종결) |

## 재사용 조사 (Phase 2)

| 후보 | 판정 | 근거 |
|---|---|---|
| `EffectiveDatedPolicy<CollectionRangePolicyData>` 와 `resolveCollectionRange(from, to, clock, label)` | **채택·확장** — 정책 인스턴스를 **갈래별로** 둔다(`OPENING_COLLECTION_RANGE_POLICY`), 함수에 정책 인자를 받게 | 상한의 뜻(호출 폭주 방지)은 갈래마다 다르다: 공고 목록은 기간에 비례, 개찰 상세는 표본 크기에 비례 |
| 6G-2d verifier 가 남긴 probe(torn-only 꼬리 · Busy/heal 순서) `scratchpad/g2d-*-keep` | **참고** — 사라졌으면 `verifier-r3.md` 기술로 재작성 | |
| `ml_engine.app.backtest_job.run_backtest_job` | **그대로**, `__main__` 만 더한다 | 판정 경로 무변경 |

## 결정

| ID | 결정 | 근거 |
|---|---|---|
| **D-6G2e-1** | **개찰 갈래의 범위 정책을 분리한다.** `OPENING_COLLECTION_RANGE_POLICY`(정책 데이터, 리터럴 금지 — `maxSpanDays` 는 A-1 의 16주를 덮는 값으로 운영자 승인 A-1: 제안 **120일**) 를 `OpeningCollectionWiring` 이 쓰고, 공고 목록 갈래는 31일 그대로. 추출 갈래(`SnapshotExtraction` 관측 창)도 같은 함수를 쓰면 개찰 쪽 정책을 따른다(실측으로 확정). 개찰 갈래의 호출 수는 창 길이가 아니라 **표본 크기 × 축 + 개찰결과 목록 쪽 수**이고 상한 20,000/80,000 이 따로 묶는다. test: 120일 창 수락 · 121일 거부 · 공고 목록 갈래 32일 거부 그대로. 변이: 공고 목록 갈래에 긴 정책을 꽂음 → RED | 상한의 뜻이 갈래마다 다르다 |
| **D-6G2e-2** | **표본틀 창은 한 번에 확정**(D-6G-39·50 그대로) — 창을 쪼개는 길은 열지 않는다. 쪼갠 창으로 같은 디렉터리를 쓰려는 기동은 지금처럼 거부 | 모집단이 하나여야 표본이 하나다 |
| **D-6G2e-3** | **★① 찢어진 조각만 남은 꼬리 라운드**: `read()` 가 걸러 낸 torn 표식도 열린 라운드의 증거다 — `interruptedRounds` 가 꼬리의 torn 표식을 보고 `Failed(INTERRUPTED)` 로 닫아 상한에 하나로 센다(예산은 이미 하나로 셈 — 두 장부 일치). test: 첫 PENDING 쓰기 중 크래시 세 번 → 네 번째 0 호출. 변이 → RED | 6G-2d cr 4차 ② |
| **D-6G2e-4** | **★② `incompleteAValues` 는 A 축 행 자체의 적용 여부로 센다** — 기초금액 축 행의 `formulaAApplies` 에 묶지 않는다. test: 기초금액 축이 빈 공고의 A 결손 → 계수 1. 변이 → RED | 6G-2d cr 4차 ③ |
| **D-6G2e-5** | **★③ 복구 순서와 가드**: `verifyIntegrity`(directory_id · 모르는 파일 · 장부 유무)를 `healTornTail` **앞**에 — 거부될 디렉터리는 고쳐 쓰지 않는다(검사가 원장 줄 수를 요구하면 읽기 전용 계수로) · `LedgerDigest` 읽기를 잠금 가드 **안**으로(비UTF-8 등 예외가 잠금을 남기지 않게). test: 복사된 디렉터리(directory_id 불일치) + 찢어진 끝 줄 → 거부되고 원장 바이트 불변 · 비UTF-8 원장 → 예외 뒤 같은 프로세스에서 다시 열면 Busy 아님 | 6G-2d cr 4차 ⑦ |
| **D-6G2e-6** | **백테스트 CLI**(Python 파일 하나 `ml_engine/app/backtest_cli.py` 또는 `backtest_job.__main__`): 인자 `--snapshot-uri`(file:// 강제, 상대 경로면 절대로) · `--backtest-policy` · `--inference-policy` · `--output-dir`(스냅숏 디렉터리 **밖** 강제) · 종료 코드 0/1 · `JobFailed` 사유 출력. 판정 경로·정책 로더 무변경(`evaluation/**` diff 0), import-linter 층 계약 준수(app 층). test: 성공 경로 + 네 실패 사유 + 출력이 스냅숏 안이면 거부 | runbook 2-4 의 스크립트를 코드로 |
| **D-6G2e-6b** | **(6G-2a vr r1 M-4 이월) seed 수를 정책 스키마가 고정한다** — `stability_seeds` 색인 목록 판독기가 연속성만 요구해 하나만 있어도 로드되고 그러면 seed 안정성 레그가 항상 참이다. 로더(`ml_engine` 정책 로더, Python `src`)가 길이 == 5(승인 A-3 값, 정책 schema 에서)를 요구하고 아니면 `*_POLICY_REJECTED`. test: seed 넷·하나 → 거부, 다섯 → 수락. `OPEN-6G2A-SEED-COUNT-NOT-PINNED` 닫음. ml-implementer 몫(⑤ 와 같은 레인) | 6G-2a 가 로더를 out_scope 로 두어 넘긴 것 |
| **D-6G2e-7** | **게이트 술어 확장 0 · 실행 상태 형식 version 2 유지 · 스키마 칸·golden 무변경.** 새 정책 데이터는 등재 | |
| **D-6G2e-8** | **runbook 갱신은 이 slice 의 산출물** — `docs/runbook/m6-6g-real-collection.md` 0 절의 차단 행 둘을 닫고 2-2·2-4 를 코드대로 | 문서가 코드를 따라간다 |

## 위협 모델 — 6G-2e 고유 경계 (Phase 2.5 (0))

**지키는 것**: ① 공고 목록 갈래의 31일 상한은 그대로(호출 폭주 방지) ② 개찰 갈래의 긴 창은 표본 크기·호출 상한이 묶는다 ③ 실행 상태 회계(크래시 라운드 하나 · 거부될 디렉터리 불변 · 잠금 잔류 없음) ④ CLI 는 판정 경로를 바꾸지 않는다.
**경계 밖**: 게이트 하드닝(2a·2b) · fsync 묶기 · 이중 파싱 · 반사실.

### (2) 우회 — 다섯
1. 긴 정책을 공고 목록 갈래에도 꽂는다 ← 갈래별 정책 인스턴스, 변이 RED. 2. `maxSpanDays` 리터럴을 배선에 적는다 ← 정책 데이터 + 리터럴 게이트. 3. 창을 쪼개 표본을 두 번 뽑는다 ← D-2 거부 유지 test. 4. torn 표식을 세지 않고 `read()` 에서 버린다 ← D-3 변이. 5. CLI 가 출력 디렉터리를 스냅숏 안에 둔다 ← D-6 거부 test.

### (2b) 값 획득 축
새 public: 정책 인스턴스 하나(값 읽기만) · CLI 진입점(판정 함수 호출만). 그 밖 0 기대.

## 운영자 승인 필요 (착수 전)
- **A-1 개찰 갈래 `maxSpanDays`**: **120일 확정(운영자 2026-10-02)** — 16주 + 여유 8일; 그 이상은 기동 거부(실수 방지), 호출 수는 상한이 묶는다.
- **A-2 착수 시점**: 6G-2a 머지 직후(결정됨 2026-10-01).

## in_scope
- `workflow/src/main/kotlin/bidvector/workflow/collection/**`(범위 정책·`interruptedRounds`) · `workflow/src/test/kotlin/bidvector/workflow/collection/**`
- `app/src/main/kotlin/bidvector/app/wiring/**`(개찰·추출 배선의 정책 선택) · `app/src/main/kotlin/bidvector/app/collection/**` · `app/src/test/**`
- `adapters/src/main/kotlin/bidvector/adapters/snapshot/**`(★ 셋) · `adapters/src/test/**` · `procurement/src/main/kotlin/bidvector/procurement/**`(필요 시) · `procurement/src/test/**`
- `config/quality/**`(등재만) · `ml-engine/src/ml_engine/app/**`(CLI 파일 하나) · `ml-engine/tests/app/**` · `ml-engine/pyproject.toml`(스크립트 등재 시)
- `docs/runbook/m6-6g-real-collection.md` · `reports/evidence/m6/6g2e/**` · `milestone-6.md`(문단만)
**out_scope**: `ml-engine/src/ml_engine/evaluation/**` · 정책 YAML 값 · 스키마·golden · koneps transport · `reports/evidence/m6/6g*/**`(이전 slice).

## acceptance
CI `check` job + `ml-engine` job 명령 그대로; 실행 상태 E2E 가 container job 에 있으면 그것도. 변이표(D-1·3·4·5·6).

## rollback
in_scope 경로 한정 `git restore --source=<base> --staged --worktree --`; 공유 파일(`milestone-6.md` · `config/quality/*` · runbook)은 hunk 격리; 버릴 clone ①~⑥(Kotlin·Python 양 축). 복원 목록은 `git diff --name-only base..HEAD` 의 소스 전수 ⊆ 목록을 verifier 가 등식으로 확인(6G-2d 교훈).

## 리뷰 레인
`verifier` + `code-reviewer`(sonnet). Codex 없음. privacy-gate 불필요.

## 하네스 레인 변경
(착수 뒤)

## OPEN 수령·신설 (예상)
| OPEN | 처분 |
|---|---|
| `OPEN-6G-OPENING-RANGE-CAP` | **이 slice 가 닫는다**(D-1) |
| `OPEN-6G-REVIEW-FOLLOWUPS` ★ 셋 | **이 slice 가 닫는다**(D-3·4·5) — 나머지 항목은 6G-2c 그대로 |
| `OPEN-6G-BACKTEST-CLI` | **이 slice 가 닫는다**(D-6) |
| `OPEN-6G2A-SEED-COUNT-NOT-PINNED` | **이 slice 가 닫는다**(D-6b) |
