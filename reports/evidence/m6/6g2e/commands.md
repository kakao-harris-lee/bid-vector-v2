# M6/6G-2e — 실행 명령과 종료 코드

> 레인별로 **자기 절만** 추가한다(Kotlin: D-6G2e-1~5·7·8 · Python: D-6G2e-6·6b·8 Python 몫·10).
> 출력 전문을 붙이지 않는다 — 핵심 결과는 한 줄이고, 감사자는 명령을 다시 돌린다.
> **마지막 HEAD 의 게이트 결과 정본은 이 문서가 아니라 verifier 리포트와 PR 조치 코멘트다**
> (evidence 는 자기 마지막 커밋의 post-state 를 담을 수 없다).

## Python 레인 (2026-10-01T23:5xZ, D-6G2e-6 · 6b · 10 · 8 Python 몫)

산출물 커밋 셋 — 백테스트 CLI · seed 수 고정(로더 둘) · runbook 2-4. base `c63d0d3a`.

### 계약 문면 ↔ 기호

| 계약 문면 | 기호 | 잠그는 test |
|---|---|---|
| D-6 「Python 파일 하나 … `python -m`」 | `ml_engine.app.backtest_cli` (`main(argv)`) | `test_the_public_surface_is_one_entry_point` |
| D-6 「`--snapshot-uri`(file:// 강제, 상대 경로면 절대로)」 | `_snapshot_uri` | `…relative_bare_path_becomes_an_absolute_file_uri` · `…file_uri_is_passed_through_without_a_conversion_notice` · `…non_file_scheme_is_refused` |
| D-6 「`--output-dir`(스냅숏 디렉터리 **밖** 강제)」 | `_verdict_path` | `test_output_dir_inside_the_snapshot_is_refused`(자신·하위 둘) |
| D-6 「종료 코드 0/1 · `JobFailed` 사유 출력」 | `main` 의 `SystemExit(<문면>)` / 정상 복귀 | `…module_run_writes_the_verdict_and_exits_zero` · `…each_failure_reason_is_reported_and_exits_one`(사유 다섯) |
| D-6 「판정 경로·정책 로더 무변경」 | `run_backtest_job` 호출 한 자리 | `git diff c63d0d3a..HEAD -- ml-engine/src/ml_engine/evaluation ml-engine/policy` = D-6b·10 의 로더 둘뿐(아래 「무변경 실측」) |
| D-6b·10 「로더가 길이 == 5 를 요구, 아니면 `*_POLICY_REJECTED`」 | `_APPROVED_SEED_KEYS` + `load_strategy_backtest_policy` · `load_evaluation_policy` | `test_seed_count_pinned.py` 8개 · `…loader_refuses_a_policy_with_a_key_removed[stability_seeds.4]` |
| D-6b 「승인 A-3 값은 정책 schema 에서(리터럴 금지)」 | 승인 키 열거 + `len` | 숫자 리터럴 게이트(`5` 는 출하 임계 `max_origins` — 소스에 적으면 붉어진다) |
| D-7 「스키마 칸·golden 무변경」 | — | `git diff` 에 `.../policy/*.yaml`·golden 0 |

### 새 public 표면 (설계 검토 (2b) 값 획득 축)

| 표면 | 값을 받는 자리 | 실측 |
|---|---|---|
| `ml_engine.app.backtest_cli.main(argv)` | argv 넷(스냅숏 URI·정책 둘·출력 디렉터리) — **임계·seed 를 받는 인자 0** | `test_the_public_surface_is_one_entry_point` 가 모듈 정의 이름 집합이 `{main}` 임을 단언 |
| `_APPROVED_SEED_KEYS`(비공개) | 값 읽기 없음 — 길이만 쓴다 | 모듈 밖 노출 0(`_` 접두) |

그 밖 0. 정책은 **파일로만** 온다(ML-07 acceptance ④ 와 같은 축).

### 변이 실측 (셋 다 RED)

| 변이 | 지운 것 | 결과 |
|---|---|---|
| CLI 가 맨 경로를 변환 없이 넘긴다 | `_snapshot_uri` 의 변환·공시 두 줄 → `return raw` | 2 failed(성공 판 · 상대 경로 판) |
| 출력이 스냅숏 안인데 받아들인다 | `_verdict_path` 의 거부 블록 | 2 failed(디렉터리 자신 · 하위) |
| 로더가 seed 개수를 보지 않는다 | 로더 둘의 개수 검사 블록 | `test_seed_count_pinned.py` 6 failed + 민감도 전수 거부 `[stability_seeds.4]` 1 failed |

변이 뒤 복원은 사본 덮어쓰기로 했다(`git checkout --` 금지 — 같은 워킹트리의 다른 레인
미커밋 편집을 지운다). 복원 뒤 `git diff --quiet` 로 확인.

### 무변경 실측 (D-6 「판정 경로 무변경」)

- `git diff --name-only c63d0d3a..HEAD -- ml-engine/src/ml_engine/evaluation ml-engine/policy`
  → `evaluation/policy.py` · `evaluation/backtest/policy.py` **둘**(D-6G2e-9 carve-out 그대로).
  `evaluation/backtest/run.py`·`verdict.py`·`report.py`·정책 YAML·golden 0.
- 개수 검사는 **로더에만** 있고 dataclass `__post_init__` 에는 없다 — seed 하나로 직접
  생성하는 기존 test 일곱 자리(`tests/evaluation/**`, in_scope 밖)가 그대로 선다.

### OPEN 처분

| OPEN | 처분 |
|---|---|
| `OPEN-6G-BACKTEST-CLI` | **닫는다**(D-6) — runbook 2-4 가 CLI 를 부르고 0 절 행이 ✓ |
| `OPEN-6G2A-SEED-COUNT-NOT-PINNED` | **닫는다**(D-6b·10) — 로더 둘이 개수를 요구. 6G-2a 의 등재(`_REMOVAL_ACCEPTED`)와 양성 대조 test 는 설계대로 RED 가 되어 삭제, 그 키는 전수 거부 test 의 모수로 들어갔다 |

### 알려진 제한

1. **첫 구간에 콜론이 있는 상대 경로**(`a:b/c`)는 scheme 으로 읽혀 거부된다 — 조용히 다른
   뜻으로 읽는 것보다 낫다고 보고 fail-closed 로 두었다(문서화: CLI 모듈 docstring).
2. **`file://<host>/…` 거부는 CLI 가 아니라 판독기가 진다** — 같은 뜻을 두 자리에서 재지
   않기 위해서다. CLI 는 scheme 만 본다.
3. **`verdict.json` 덮어쓰기를 막지 않는다** — 같은 `--output-dir` 로 두 번 돌리면 앞
   판정이 사라진다. runbook 이 스냅숏별 디렉터리를 쓰라고 적는 것으로 둔다(계약 밖).
4. **곁딸린 수정 둘**(D-10 「잘못된 이유로 통과하지 않음을 확인」의 실측 결과):
   `test_evaluation_policy.py` 의 거부 test 여섯이 helper 의 seed 둘 탓에 개수 고정 뒤
   전부 seed 개수로 거부됐다 → 기준 한 벌을 하나로 모으고 양성 대조를 세웠다. 같은 확인에서
   `…rejects_gapped_indexed_list` 가 **구멍을 만들지 못하고** 「엄격 오름차순」으로 거부되고
   있던 것을 발견해 가운데 키를 지우는 형태로 고쳤다(6G-2e 이전부터 있던 공허).
5. **`[project.scripts]` 등재 0** — 저장소가 console script 를 쓰지 않아 `-m` 만 둔다.

## Kotlin 레인 (2026-10-02, D-6G2e-1~5 · 7 · 8 Kotlin 몫)

산출물 커밋 일곱(마지막 `29c6c39d`) — 갈래별 범위 정책 · 쪼갠 창 거부 못 박기 · 조각 꼬리 라운드 ·
A 결손 계수 · 복구 순서와 잠금 가드 · runbook 0 절·2-2. base `c63d0d3a`.

### acceptance — CI `check` job 명령 그대로, 마지막 산출물 커밋에서

| 명령 | exit |
|---|---|
| `./gradlew --no-daemon clean` | 0 |
| `./gradlew --no-daemon check --no-build-cache` | 0 |
| `./gradlew --no-daemon qualityBaseline` | 0 |
| `./tools/one-command-check.sh` | 0 (Kotlin 전건 + Python 전건) |

test 수(`*/build/test-results/test/TEST-*.xml` 합): **2,590** · 실패 0 · 오류 0 · 건너뜀 4.
모듈별: adapters 836 · app 451 · build-logic 251 · decision 184 · procurement 263 ·
qualification 31 · shared-kernel 89 · strategy 84 · workflow 401.
`container` job 은 돌리지 않았다 — 이 slice 가 만진 E2E 가 없다(`check` 안의 app test 가
Testcontainers 로 같은 조립을 이미 든다).

### 계약 문면 ↔ 기호

| 계약 문면 | 기호 | 잠그는 test |
|---|---|---|
| D-1 「개찰 갈래의 범위 정책을 분리, 리터럴 금지」 | `OPENING_COLLECTION_RANGE_POLICY`(`EffectiveDatedPolicy<CollectionRangePolicyData>`) | `개찰 갈래의 범위 정책은 공고 목록 갈래보다 길다 — 같은 값이 아니다` |
| D-1 「120일 창 수락 · 121일 거부」 | `OpeningCollectionWiring.openingCollectionRange` | `범위 상한 정확히(120일)는 뜨고 하루 더 넓으면 기동하지 않는다 — 경계 양쪽` |
| D-1 「공고 목록 갈래는 31일 그대로」 | `CollectionWiring.collectionRange` | `개찰 갈래의 긴 창은 이 갈래에서 거부된다 — 범위 정책을 공유하지 않는다` + 기존 32일 거부 test |
| D-1 「추출 배선은 범위를 부르지 않는다(착수 실측 확정)」 | `SnapshotExtractionWiring` — `resolveCollectionRange` 호출 0 | `추출 갈래는 관측 창의 길이를 재지 않는다 — 수집 범위 상한 밖이다`(2025-01-01~2026-06-30 창이 뜬다) |
| D-2 「쪼갠 창으로 같은 디렉터리를 쓰려는 기동은 거부」 | `SampleResolution` 의 표본틀 범위 `require` | `확정 뒤 창을 쪼개 뒷조각으로 돌리면 거부한다` |
| D-3 「꼬리의 torn 표식도 열린 라운드」 | `FileAttemptLedger.read` 의 조각 되살림(의도 줄) | `축과 공고까지 닿은 조각은 열린 라운드로 읽힌다 — 호출 수는 그대로 하나` |
| D-3 「예산은 이미 하나로 셈 — 두 장부 일치」 | `AttemptHistory.spend` 의 `tornLines`(되살린 조각은 빠진다) | 같은 test 의 `spend().total == 1` |
| D-3 「상한에 하나로 센다」 | `interruptedRounds` → `AXIS Failed(INTERRUPTED)` → `axisResumptions` | `OpeningRetryCapTest.의도 줄만 남기고 죽은 라운드도 상한에 센다`(상한 뒤 0 호출) |
| D-4 「계수는 A 축 행 자체의 적용 여부로」 | `aValueTotalOf`(인자 `applies` 제거, 합산 입력 유무로) | `기초금액 축이 걷히지 않은 공고의 A 결손도 센다` · `A 입력을 하나도 싣지 않은 A 행은 세지 않는다` |
| D-5 「`verifyIntegrity` 를 `healTornTail` 앞으로」 | `verifyPlacement`(자리 대조) → 되돌림 → `healTornTail` | `복사된 디렉터리는 찢어진 끝 줄을 고치지 않고 거부한다 — 원장 바이트 불변` |
| D-5 「`LedgerDigest` 읽기를 잠금 가드 안으로」 | `ledger` 초기화식 전체가 `heldOrRelease` 안 | `원장이 UTF-8 이 아니면 던지고 잠금을 놓는다 — 다음 기동이 Busy 가 아니다` |
| D-7 「형식 version 2 유지 · 스키마 칸·golden 무변경」 | `RUN_STATE_FORMAT_VERSION` 무변경 | `git diff c63d0d3a..HEAD` 에 정책 YAML·스키마·golden 0 |

### 변이 실측 (일곱, 전부 RED)

| 변이 | 지운 것 | 결과 |
|---|---|---|
| 공고 목록 갈래에 개찰 정책을 꽂음 | `CollectionWiring` 의 정책 인자 | `CollectionWiringTest` 2 failed(새 단언 + 기존 32일 거부) |
| 개찰 정책을 31일로 좁힘 | `maxSpanDays = 120` → `31` | 배선 경계 1 + 정책 비교 1 failed |
| 표본틀 범위 대조를 `to` 만 비교 | `require(confirmed.scope == scope)` | 새 쪼갠 창 test 1 failed(기존 「창이 달라지면」 test 는 **통과** — 사각이 실재했다) |
| 조각을 세기만 하는 앞 판 | 조각 되살림 | 열린 라운드 test 1 failed |
| 조각을 결말 줄로 되살림 | `kind = PENDING` → `AXIS` | 같은 test 1 failed(열린 라운드가 아니게 된다) |
| 계수를 기초금액 축 술어에 다시 묶음 | 합산 입력 유무 → `applies` | A 계수 test 2 failed(양방향) |
| 기동 첫 걸음을 잠금 가드 밖으로 | `heldOrRelease` → `run` | 2 failed(비UTF-8 · 형식 거부의 잠금 잔류) |

변이 뒤 복원은 **사본 덮어쓰기**로 했다(`git checkout --` 금지 — 같은 워킹트리의 다른 레인
미커밋 편집을 지운다). 복원 뒤 영향 범위 test 재실행으로 초록 확인.

### 새 public 표면 (설계 검토 (2b) 값 획득 축)

| 표면 | 값을 받는 자리 | 실측 |
|---|---|---|
| `OPENING_COLLECTION_RANGE_POLICY`(`val`, workflow) | 없다 — 읽기 전용 정책 인스턴스이고 `CollectionRangePolicyData` 생성자는 `internal` 이라 모듈 밖에서 상한을 지은 값을 넣을 수 없다 | 배선·test 가 `resolve(today)` 로 **읽기만** 한다 |
| `resolveCollectionRange` 의 새 인자(`internal`, app) | 정책 인스턴스 — **기본값 없음**(새 갈래가 남의 상한을 조용히 물려받지 못한다) | 호출 자리 둘, 각자 자기 갈래 정책 |

그 밖 0. `FileAttemptLedger`·`RunStateDirectory` 의 새 함수는 전부 `private` 이고,
`AttemptHistory.tornLines` 의 뜻만 좁아졌다(생성자 서명 무변경).

### config/quality 등재

**0 건.** 이 slice 는 게이트 술어를 바꾸지 않았고 baseline·allowlist 를 손대지 않았다
(`git diff --name-only c63d0d3a..HEAD -- config/quality` 빈 출력).

### 비밀값 누출 대조

`grep -rniE -f config/quality/leak-patterns.txt` 기준의 `check` 내 게이트가 초록이다.
새 test fixture 는 합성 공고 키 해시와 ISO 시각뿐이고 자격 값 어휘를 싣지 않는다.

### OPEN 처분

| OPEN | 처분 |
|---|---|
| `OPEN-6G-OPENING-RANGE-CAP` | **닫는다**(D-1) — 개찰 갈래 120일, 공고 목록 갈래 31일. runbook 0 절 행 ✓ |
| `OPEN-6G-REVIEW-FOLLOWUPS` ★ 셋 | **닫는다**(D-3·4·5). 그 OPEN 의 ★ 아닌 항목들(①⑥⑧⑨)은 6G-2c 그대로 |

### 알려진 제한

1. **조각에서 축·공고를 못 읽으면 라운드를 짓지 못한다**(D-3). 조각이 `axis` 칸에 닿기 전에
   끊기면 어느 라운드의 호출인지 알 수 없고, 그 경우는 앞 판 그대로 **호출 하나로만** 센다 —
   재호출 상한은 그 조각을 세지 못한다(예산 상한만이 막는다). 축을 지어내는 것보다 낫다고
   보았다. 줄 형태가 `at`·`axis`·`notice_key_hash` 를 앞에 싣기 때문에 실제로 그 자리에서
   끊기는 창은 한 줄의 앞 70 바이트 남짓이다.
2. **조각을 결말로 되살리지 않는다**(같은 자리). 온전히 쓰였으나 개행만 빠진 결말 줄도 의도
   줄로 읽혀 그 라운드가 열린 채 남는다 — 그 축은 상한 안에서 한 번 더 불린다. 굳지 않은
   결말을 「끝났다」로 읽는 쪽의 손해(받지 못한 축이 완료가 된다)가 더 크다.
3. **원장 읽기만을 잠금 가드 밖으로 옮기는 변이는 RED 가 아니다**(D-5). `healTornTail` 이
   같은 바이트를 먼저 읽어 같은 예외를 가드 안에서 던지기 때문이다 — 실측으로 확인했다
   (그 변이에서 `check` 의 해당 test 둘 통과). 그 자리의 가드는 **방어 심도**이고, 거동으로
   갈리는 것은 `LedgerDigest` 가 「없는 파일」과 「읽히지 않는 파일」을 가르게 된 쪽이다.
4. **`AssemblyTally.incompleteAValues` 의 뜻이 좁아졌다**(D-4) — 「A 가 적용되는 공고의 결손」
   에서 「A 입력이 있는데 합산을 낼 수 없는 행」으로. 두 뜻이 갈리는 모양은 하나뿐이다:
   기초금액 축의 술어가 `N` 인데 A 행에 입력이 있는 공고. 그 행은 이제 세어진다(앞 판은 0).
   원천이 그런 응답을 내는지는 실수집 전에 알 수 없고, 계수는 공시이지 판정 입력이 아니다.
5. **착수 실측의 test 수(2,588)와 이 slice 의 측정이 맞지 않는다** — base `c63d0d3a` 의
   Kotlin 소스로 되돌린 트리에서 **2,580** 이 나왔다(되돌림 ⑤ 실측, 같은 집계 방법). 이
   slice 가 더한 test 는 10 이고 2,580 + 10 = 2,590 으로 맞는다. 착수 표의 2,588 은 다른
   방법으로 센 값이거나 낡은 값이다 — 계약 표의 숫자를 고치지 않고 사실만 적는다.

### 이탈

1. **D-3 의 처방 자리가 `interruptedRounds` 가 아니다.** 계약은 「`interruptedRounds` 가
   꼬리의 torn 표식을 보고 닫는다」로 적었는데, 표식의 (공고, 축) 귀속은 **줄 형태**를 아는
   자리(원장 어댑터)만 답할 수 있는 물음이라 되살림을 그 자리에 두었다. `interruptedRounds`
   는 무변경이고, 되살아난 의도 줄을 **이미 있는 규칙으로** 열린 라운드로 본다 — 도메인에
   조각이라는 개념을 들이지 않는다(판독의 일이다).
2. **「세 번 크래시 → 네 번째 0 호출」을 한 test 로 재지 않았다.** 사슬을 둘로 나눠 쟀다:
   조각 → 열린 라운드(어댑터, 실 파일 원장) · 꼬리의 의도 줄 → 상한 → 다음 기동 0 호출
   (`OpeningRetryCapTest`, 이미 있다). 한 test 로 재려면 실 `RunStateDirectory` 와 수집
   use case 를 한 자리에 세워야 하고, 그 조립은 `workflow` test 지원(약 250 줄)을 adapters
   쪽에 한 벌 더 두는 일이라 중복 금지에 걸린다. 두 토막의 **접합부**는 「되살린 줄이 쓰인
   의도 줄과 같은 값」이고 그것을 변이 둘이 든다.
