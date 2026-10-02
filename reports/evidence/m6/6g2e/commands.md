# M6/6G-2e — 실행 명령과 종료 코드

> 레인별로 **자기 절만** 추가한다(Kotlin: D-6G2e-1~5·7·8 · Python: D-6G2e-6·6b·8 Python 몫·10).
> 출력 전문을 붙이지 않는다 — 핵심 결과는 한 줄이고, 감사자는 명령을 다시 돌린다.
> **마지막 HEAD 의 게이트 결과 정본은 이 문서가 아니라 verifier 리포트와 PR 조치 코멘트다**
> (evidence 는 자기 마지막 커밋의 post-state 를 담을 수 없다).

## Python 레인 (2026-10-02T01:10Z, D-6G2e-6 · 6b · 10 · 8 Python 몫)

산출물 커밋 셋 — 백테스트 CLI · seed 수 고정(로더 둘) · runbook 2-4. base `c63d0d3a`.

### 계약 문면 ↔ 기호

| 계약 문면 | 기호 | 잠그는 test |
|---|---|---|
| D-6 「Python 파일 하나 … `python -m`」 | `ml_engine.app.backtest_cli` (`main(argv)`) | `test_the_public_surface_is_one_entry_point` |
| D-6 「`--snapshot-uri`(file:// 강제, 상대 경로면 절대로)」 | `_snapshot_uri` | `…relative_bare_path_becomes_an_absolute_file_uri` · `…file_uri_is_passed_through_without_a_conversion_notice` · `…non_file_scheme_is_refused` |
| D-6 「`--output-dir`(스냅숏 디렉터리 **밖** 강제)」 | `_verdict_path` | `test_output_dir_inside_the_snapshot_is_refused`(자신·하위 둘) |
| **D-16** 「포함 검사는 해석된 `Path` 로 · 이미 URI 면 **디코딩** · `file:<상대>` 도 공시」 | `_snapshot_dir`(`(URI, 디렉터리)`) · `_verdict_path(…, snapshot_dir)` | `…bare_path_with_a_space_converts_to_a_percent_encoded_uri` · `…output_inside_a_spaced_snapshot_is_refused_before_any_read` · `…uri_input_for_a_spaced_snapshot_also_refuses_output_inside`(인코딩·공백 그대로 둘) · `…relative_file_scheme_input_prints_the_conversion_notice` |
| D-6 「종료 코드 0/1 · `JobFailed` 사유 출력」 | `main` 의 `SystemExit(<문면>)` / 정상 복귀 | `…module_run_writes_the_verdict_and_exits_zero` · `…each_failure_reason_is_reported_and_exits_one`(사유 다섯) |
| **D-23 ①** 「app 층에 HTTP 모듈을 들이지 않는다」 | `from urllib.parse import unquote` (`urllib.request` 0) | `test_the_app_package_does_not_import_urllib_request`(`ml_engine/app/**` AST 전수) |
| **D-23 ②** 「guard 는 디코딩 경로와 리터럴 경로 둘 다 본다」 | `_snapshot_dirs` 가 후보 tuple 을 내고 `_verdict_path` 가 `any(...)` | `test_output_inside_a_percent_named_snapshot_is_refused` |
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

### acceptance — CI `ml-engine` job 명령 그대로, 한 HEAD 에서

워크플로의 step 을 **무편집**으로 돌렸다(줄일 수 있는 단위는 job 이고, 이 slice 의 Python
변경은 `ml-engine` job 하나가 전부 진다 — 워크플로가 두 job 의 소스 비겹침을 스스로 선언한다).

아래 수는 **r3(승인 전 일괄)** 의 것이고, Python 마지막 산출물 커밋 `8f30025a` 의 `ml-engine`
트리에서 쟀다 — `git diff --name-only 8f30025a..c1f8b02f -- ml-engine` 이 빈 출력이라 실행 HEAD 와
트리가 같다(그 사이 커밋은 Kotlin 축 것이다). 앞 라운드 수는 옮기지 않았다. r2 의 수(1,377)도 같은
방식으로 확인했고 그 트리 동일성은 **`9c274fc0` 까지** 유효했다(D-6G2e-23 ⑥).

| step | 명령 | exit | 핵심 결과 |
|---|---|---|---|
| S-1 | `uv sync --frozen --all-extras` | 0 | lock 그대로 설치 |
| S-1b | serving extras 분리(금지 다섯 import 불가 → 복구) | 0 | 분리 확인 통과 |
| S-2 | `ruff check .` · `ruff format --check .` | 0 | 218 files already formatted |
| S-3 | `mypy --strict src/ml_engine` | 0 | 97 source files, 오류 0 |
| S-4 | `lint-imports` | 0 | Contracts 8 kept, 0 broken |
| S-5 | `python -m pytest tests -q` | 0 | **1,379 passed**(6G-2a 종결 1,350 → +29) |
| S-6 | `tools/design_ratchet.py --check` | 0 | allowlist 밖 위반 0 |
| S-7 | `tools/reuse_provenance_check.py`(+양성 대조) | 0 | 위반 0 · 어긋난 evidence 는 거부됨 |
| S-9 | Python 버전 두 자리 대조 | 0 | 3.12 일치 |
| S-11 | `uv build --wheel` + 재수출 게이트 | 0 | 1 passed |

test 수 증감의 내역: CLI 13 · seed 수 8 · 평가 정책 양성 대조 1 · 민감도 전수 거부에
`stability_seeds.4` 1 추가 · 6G-2a 양성 대조 1 삭제 = +22, r2 의 D-16 test 5(공백·한글 변환 ·
읽기 전 거부 · URI 입력 둘 · 상대 `file:` 공시) = +27, **r3 의 D-23 test 2**(`%XX` 이름 판 거부 ·
app 층 `urllib.request` 전수) = **+29**.

**S-11 의 `/tmp/ml-engine-wheel` 산출물은 저장소 밖**이다(커밋 0).

### 비밀값 누출 대조

`grep -rniE -f config/quality/leak-patterns.txt <in_scope 경로들> reports/evidence/m6/6g2e/`
→ **exit 1(매치 0 = 통과)**. 첫 실행에서 test 파일의 **지역 변수 이름 하나**가 패턴 어휘와
겹쳐 매치가 났고(값이 아니라 이름), baseline 등재 대신 이름을 바꿨다 — CI 게이트의 scanRoot 는
`reports/evidence` 뿐이라 CI 는 붉지 않았지만 스캔 술어가 계속 「매치 0」으로 읽히는 쪽이 낫다.

**마지막 HEAD 의 결과 정본은 이 문서가 아니라 verifier 리포트와 PR 조치 코멘트다** — evidence 는
자기 마지막 커밋의 post-state 를 담을 수 없다.

### 변이 실측 (셋 다 RED)

| 변이 | 지운 것 | 결과 |
|---|---|---|
| CLI 가 맨 경로를 변환 없이 넘긴다 | `_snapshot_uri` 의 변환·공시 두 줄 → `return raw` | 2 failed(성공 판 · 상대 경로 판) |
| 출력이 스냅숏 안인데 받아들인다 | `_verdict_path` 의 거부 블록 | 2 failed(디렉터리 자신 · 하위) |
| 로더가 seed 개수를 보지 않는다 | 로더 둘의 개수 검사 블록 | `test_seed_count_pinned.py` 6 failed + 민감도 전수 거부 `[stability_seeds.4]` 1 failed |
| 포함 검사를 **URI 문자열 재유도**로 되돌린다(D-16) | `main` 이 건네는 `Path` 대신 `Path(urlparse(uri).path).resolve()` | 2 failed(공백·한글 경로 판 · 인코딩된 URI 판) |
| guard 가 **한쪽만** 본다(D-23 ②) | 리터럴 경로 후보를 뺀다 | 1 failed(`%XX` 이름 판). 거부가 설 때 0.8초 · 서지 못할 때 14초 — 뒤쪽에서는 판정 전체가 돌고 `verdict.json` 이 스냅숏 안에 생긴다 |

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
| **`OPEN-6G2E-SNAPSHOT-READER-URI-DECODE`** | **신설**(D-16, 6G-2c 로) — 판독기가 `file://` URI 의 퍼센트 인코딩을 풀지 않는다. 이 slice 이전부터 있던 결함이고 in_scope 밖이다. 닫히기 전까지의 **대가**는 알려진 제한 2b 둘이고, 그중 (ㄴ)은 guard 가 후보 둘을 보는 것으로 막아 둔다(D-23 ②) |
| `OPEN-6G2A-SEED-COUNT-NOT-PINNED` | **닫는다**(D-6b·10) — 로더 둘이 개수를 요구. 6G-2a 의 등재(`_REMOVAL_ACCEPTED`)와 양성 대조 test 는 설계대로 RED 가 되어 삭제, 그 키는 전수 거부 test 의 모수로 들어갔다 |

### 알려진 제한

1. **첫 구간에 콜론이 있는 상대 경로**(`a:b/c`)는 scheme 으로 읽혀 거부된다 — 조용히 다른
   뜻으로 읽는 것보다 낫다고 보고 fail-closed 로 두었다(문서화: CLI 모듈 docstring).
2. **`file://<host>/…` 거부는 CLI 가 아니라 판독기가 진다** — 같은 뜻을 두 자리에서 재지
   않기 위해서다. CLI 는 scheme 만 본다.
2b. **판독기가 URI 를 문자 그대로 읽는 데서 오는 것 둘.** (ㄱ) **공백·한글이 든 스냅숏 경로는
   아직 끝까지 돌지 못한다.** 변환(올바른 퍼센트 인코딩)과
   출력 자리 거부는 r1 수정(D-16)으로 맞지만, 판독기(`adapters/snapshot_files.py`, 이 slice
   의 범위 밖)가 URI 의 퍼센트 인코딩을 풀지 않아 읽기가 `SNAPSHOT_UNREADABLE NOT_FOUND`
   로 선다 — **`OPEN-6G2E-SNAPSHOT-READER-URI-DECODE`**(6G-2c). 그래서 그 경로의 test 는
   성공을 요구하지 않고 변환과 거부만 잰다. 수정 전에는 그 거부조차 비활성이었고 막혀 보인
   유일한 이유가 판독기의 같은 결함이었다 — fail-closed 가 설계가 아니라 우연이었다. 이제
   거부는 판독기와 **무관하게** 성립한다. (ㄴ) 같은 갈림이 **반대 방향**으로도 난다: 이름에
   유효한 `%XX` 가 문자 그대로 든 디렉터리(`a%41b`)를 URI 로 주면 판독기는 **읽어 내고** guard 가
   디코딩 경로만 보면 스냅숏 안 출력이 통과한다(vr r2 L-r2-1 — exit 0 으로 판정이 입력 안에
   쓰였다). r3 에서 guard 가 **두 경로를 다** 보게 해 닫았다(어느 쪽이든 안이면 거부). 그 OPEN 이
   닫히면 후보 둘을 하나로 줄일 수 있고, 그때까지는 보수적인 쪽으로 둔다.
3. **`verdict.json` 덮어쓰기를 막지 않는다** — 같은 `--output-dir` 로 두 번 돌리면 앞
   판정이 사라진다. runbook 이 스냅숏별 디렉터리를 쓰라고 적는 것으로 둔다(계약 밖).
4. **곁딸린 수정 둘**(D-10 「잘못된 이유로 통과하지 않음을 확인」의 실측 결과):
   `test_evaluation_policy.py` 의 거부 test 여섯이 helper 의 seed 둘 탓에 개수 고정 뒤
   전부 seed 개수로 거부됐다 → 기준 한 벌을 하나로 모으고 양성 대조를 세웠다. 같은 확인에서
   `…rejects_gapped_indexed_list` 가 **구멍을 만들지 못하고** 「엄격 오름차순」으로 거부되고
   있던 것을 발견해 가운데 키를 지우는 형태로 고쳤다(6G-2e 이전부터 있던 공허).
5. **`[project.scripts]` 등재 0** — 저장소가 console script 를 쓰지 않아 `-m` 만 둔다.

### 레인 경계 사실 (혼입)

이 두 evidence 파일은 공유 워킹트리에서 두 레인이 같이 쓴다. Python 절의 **미커밋 편집이 두 번
Kotlin 레인 커밋에 함께 실렸다** — r1 의 `caccd807`(그 커밋 메시지가 같은 사실을 선언한다)과
r2 의 `0f7e3e16`(선언 없음 — 이 줄이 그 자리다). **이력은 되쓰지 않는다.** 두 커밋의 메시지는
Python 절 내용을 기술하지 않으므로, Python 절의 저작 이력은 커밋 메시지가 아니라 이 절과
`git log -- <이 파일>` 의 Python 커밋(`4e8cfc76` · `7463ad35`)에서 읽는다. r2 의 Python 절 수치는
전부 이 레인이 실측해 쓴 것이고, 그것을 실은 커밋이 다른 레인의 것이라는 사실만 여기 남긴다.

## Kotlin 레인 (2026-10-02, D-6G2e-1~5 · 7 · 8 · 15 · 17 · 18 Kotlin 몫)

산출물 커밋 열하나(마지막 `5fddfd1a`) — 갈래별 범위 정책 · 쪼갠 창 거부 못 박기 · 조각 꼬리 라운드 ·
A 결손 계수(정본은 기초금액 축 술어) · 복구 순서와 잠금 가드 · 읽기 전용 대조를 모두 복구 앞으로 ·
runbook 0 절·2-2·5 절 · test 파일 둘을 각자의 물음으로. base `c63d0d3a`.

### acceptance — CI `check` job 명령 그대로, 마지막 산출물 커밋 `5fddfd1a` 에서

| 명령 | exit |
|---|---|
| `./gradlew --no-daemon clean` | 0 |
| `./gradlew --no-daemon check --no-build-cache` | 0 |
| `./gradlew --no-daemon qualityBaseline` | 0 |
| `./tools/one-command-check.sh` | 0 (Kotlin 전건 + Python 전건) |

test 수(`*/build/test-results/test/TEST-*.xml` 합): **2,592** · 실패 0 · 오류 0 · 건너뜀 4.
모듈별: adapters 838 · app 451 · build-logic 251 · decision 184 · procurement 263 ·
qualification 31 · shared-kernel 89 · strategy 84 · workflow 401.

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
| D-4 「기초금액 축이 걷히지 않아도 A 결손을 센다」 | `aValueTotalOf` → `carriesAValueInput` 갈래 | `기초금액 축이 걷히지 않은 공고의 A 결손도 센다` |
| D-15 「기초금액 술어가 정본 · 부재·미지일 때만 A 행 입력 · 품질관리비는 술어 무관 입력」 | `aValueTotalOf(row, appliesByBaseAmount)` 의 `?:` · `RawRow.carriesAValueInput` | `A 결손 계수는 기초금액 축 술어가 정본이고 그 축이 없을 때만 A 행이 가른다`(판 일곱) |
| D-5 「`verifyIntegrity` 를 `healTornTail` 앞으로」 | `verifyPlacement`(자리 대조) → 되돌림 → `healTornTail` | `복사된 디렉터리는 찢어진 끝 줄을 고치지 않고 거부한다 — 원장 바이트 불변` |
| D-17 「읽기 전용 대조를 모두 복구 앞으로 · 찢어진 끝 줄은 접두 대조에서 제외하고 센다」 | `verifyThenHeal` 의 넷 · `requireLedgerPrefix`(읽기 전용) · `resyncLedgerIfAhead`(복구 뒤 하나) | `원장 앞부분이 장부와 다르면 … 바이트 동일` · `표본 목록이 바뀌면 … 바이트 동일` · 기존 크래시 test 넷(답 불변) |
| D-5 「`LedgerDigest` 읽기를 잠금 가드 안으로」 | `ledger` 초기화식 전체가 `heldOrRelease` 안 | `원장이 UTF-8 이 아니면 던지고 잠금을 놓는다 — 다음 기동이 Busy 가 아니다` |
| D-7 「형식 version 2 유지 · 스키마 칸·golden 무변경」 | `RUN_STATE_FORMAT_VERSION` 무변경 | `git diff c63d0d3a..HEAD` 에 정책 YAML·스키마·golden 0 |

### 변이 실측 (아홉, 전부 RED)

| 변이 | 지운 것 | 결과 |
|---|---|---|
| 공고 목록 갈래에 개찰 정책을 꽂음 | `CollectionWiring` 의 정책 인자 | `CollectionWiringTest` 2 failed(새 단언 + 기존 32일 거부) |
| 개찰 정책을 31일로 좁힘 | `maxSpanDays = 120` → `31` | 배선 경계 1 + 정책 비교 1 failed |
| 표본틀 범위 대조를 `to` 만 비교 | `require(confirmed.scope == scope)` | 새 쪼갠 창 test 1 failed(기존 「창이 달라지면」 test 는 **통과** — 사각이 실재했다) |
| 조각을 세기만 하는 앞 판 | 조각 되살림 | 열린 라운드 test 1 failed |
| 조각을 결말 줄로 되살림 | `kind = PENDING` → `AXIS` | 같은 test 1 failed(열린 라운드가 아니게 된다) |
| 계수를 A 행 입력 유무로만 가름(r1 판) | `appliesByBaseAmount ?:` | 판 표 1 failed, 먼저 걸린 판 「기초 Y + A 행 빔」 |
| 계수를 기초금액 술어로만 가름(D-4 앞 판) | `?: row.carriesAValueInput()` | 판 표 1 **+ D-4 test 1** failed, 먼저 걸린 판 「기초 축 부재 + 품질관리비만」 |
| 품질관리비를 입력에서 뺌 | `A_VALUE_INPUT_CONCEPTS` 의 한 줄 | 판 표 1 failed, 먼저 걸린 판 「기초 축 부재 + 품질관리비만」 |
| A 입력 존재를 파싱된 금액으로 가름(r2 판) | `textOf` → `amountOf` | 판 표 1 failed, 먼저 걸린 판 「기초 축 부재 + A 행 해석 불가」 |
| 조각의 뜻을 두 가지로 다시 섞음 | 「개행 뒤 바이트」 정의 | 공백 꼬리 test 1 failed |
| 기동 첫 걸음을 잠금 가드 밖으로 | `heldOrRelease` → `run` | 2 failed(비UTF-8 · 형식 거부의 잠금 잔류) |
| 복구를 읽기 전용 대조보다 앞으로(r1 순서) | `verifyThenHeal` 의 걸음 순서 | 2 failed(원장 앞부분 · 표본 목록, 둘 다 바이트가 바뀐다) |

**세 계수 변이가 가른 것은 「서로 다른 판」이 아니라 「서로 다른 실패 집합」이다**(vr r2 L-r2-2 정정).
판을 한 표로 두었으므로 그 표의 test 는 **먼저 걸린 판에서 멈춘다** — 「판 표 1 failed」는 「판 하나만
틀렸다」가 아니다(둘째 변이는 판 둘을 동시에 틀리게 하고 표는 앞의 하나만 말한다). 집합이 갈리는
자리는 D-4 test 의 포함 여부와 먼저 걸린 판의 이름이고, 그 둘로 세 변이가 서로 구별된다.

변이 뒤 복원은 **사본 덮어쓰기**로 했다(`git checkout --` 금지 — 같은 워킹트리의 다른 레인
미커밋 편집을 지운다). 복원 뒤 영향 범위 test 재실행으로 초록 확인.

### 새 public 표면 (설계 검토 (2b) 값 획득 축)

| 표면 | 값을 받는 자리 | 실측 |
|---|---|---|
| `OPENING_COLLECTION_RANGE_POLICY`(`val`, workflow) | 없다 — 읽기 전용 정책 인스턴스이고 `CollectionRangePolicyData` 생성자는 `internal` 이라 모듈 밖에서 상한을 지은 값을 넣을 수 없다 | 배선·test 가 `resolve(today)` 로 **읽기만** 한다 |
| `resolveCollectionRange` 의 새 인자(`internal`, app) | 정책 인스턴스 — **기본값 없음**(새 갈래가 남의 상한을 조용히 물려받지 못한다) | 호출 자리 둘, 각자 자기 갈래 정책 |

test 하네스에 `SnapshotAssemblyFixture`(`abstract class`, test 소스 전용)가 생겼다 — 출하 표면이
아니고 `protected`/`internal` 이라 test 소스 밖으로 나가지 않는다.

그 밖 0. `FileAttemptLedger`·`RunStateDirectory`·`SnapshotRowAssembly` 의 새 함수는 전부
`private` 이고(r2 가 더한 `carriesAValueInput`·`requireLedgerPrefix`·`resyncLedgerIfAhead`·
`ledgerTextOf` 넷도 같다), `AttemptHistory.tornLines` 의 뜻만 좁아졌다(생성자 서명 무변경).
**r2 가 더한 public 표면 0.**

### config/quality 등재

**1 건, 등재만** — `gate-tests.properties` 에 `RunStateRecoveryTest` 를 더했다. 게이트 **술어는
바꾸지 않았다**: `RunStateDirectoryTest` 가 이미 그 목록에 있고 복구 판들이 새 클래스로 갔으므로,
등재하지 않으면 `gateExecutionGate` 가 드는 범위에서 그 판들이 조용히 빠진다(목록에 **이름만**
더한다 — 임계·baseline·allowlist 무변경). 그 밖의 `config/quality` 파일 0.

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
4. **(r2 에서 정정) `incompleteAValues` 의 뜻.** r1 이 적은 「두 뜻이 갈리는 모양은 하나뿐」은
   **사실이 아니었다** — verifier r1 H-1 이 base 와 r1 판정 SHA 를 판 여섯으로 대조해 네 모양에서
   1 → 0 을 실측했다(기초 `Y` + A 행 빔 · 기초 `Y` + A 행 해석 불가 · 품질관리비만 + 술어 부재 ·
   같음 + 술어 빈 값). D-6G2e-15 가 그 넷을 되돌리고 D-4 가 고친 자리는 남겼다. 지금의 뜻은
   「기초금액 축 술어가 정본이고, 그 축이 없거나 모를 때만 A 행 입력이 가른다」이고 base 와 갈리는
   모양은 **하나**다: 기초금액 축이 **걷히지 않은** 공고의 A 결손(base 0 → 지금 1, D-4 의 목적).
   판 일곱이 그 경계를 든다. 계수는 공시이지 판정 입력이 아니다(`a_value` 값은 어느 판에서도 같다).
5. **착수 실측의 test 수(2,588)와 이 slice 의 측정이 맞지 않는다** — base `c63d0d3a` 의
   Kotlin 소스로 되돌린 트리에서 **2,580** 이 나왔다(되돌림 ⑤ 실측, 같은 집계 방법 — r1·r2 두
   라운드에서 같은 수). 이 slice 가 더한 test 는 12 이고 2,580 + 12 = 2,592 로 맞는다. 착수 표의
   2,588 은 다른 방법으로 센 값이거나 낡은 값이다 — 계약 표의 숫자를 고치지 않고 사실만 적는다.

### 이탈

1. **D-3 의 처방 자리가 `interruptedRounds` 가 아니다.** 계약은 「`interruptedRounds` 가
   꼬리의 torn 표식을 보고 닫는다」로 적었는데, 표식의 (공고, 축) 귀속은 **줄 형태**를 아는
   자리(원장 어댑터)만 답할 수 있는 물음이라 되살림을 그 자리에 두었다. `interruptedRounds`
   는 무변경이고, 되살아난 의도 줄을 **이미 있는 규칙으로** 열린 라운드로 본다 — 도메인에
   조각이라는 개념을 들이지 않는다(판독의 일이다).
2. **(r2 에서 추가 선언 — vr r1 M-1) D-5 가 복구 앞으로 옮긴 것은 자리 대조 셋뿐이었다.**
   계약 D-5 의 「거부될 디렉터리는 고쳐 쓰지 않는다」 중 r1 이 세운 것은 괄호 속 셋(directory_id ·
   모르는 파일 · 장부 유무)이고, 표본 해시 대조와 원장 앞부분 대조는 복구 **뒤**에 남아 그 둘로
   거부되는 디렉터리의 원장 바이트가 바뀌었다. r1 evidence 에 선언하지 않은 것이 이 항목의 지적이다.
   **r2 에서 D-6G2e-17 로 해소**했다 — 지금 복구 앞의 넷은 전부 읽기만 하고, 복구 뒤에 남는 것은
   재동기 하나다. 현재 순서를 문면으로 적는다: 형식 판별 → 자리 대조 → 끝나지 않은 확정 되돌림 →
   표본 해시 대조 → 원장 앞부분 대조 → 찢어진 꼬리 복구 → 누적 해시 → (init) 재동기.
3. **`container` job 을 돌리지 않았다 — 그 job 은 D-17 코드에 닿지 않는다.** D-18 은 「container 는
   D-17 이 실행 상태 코드라 한 번 더」로 적었는데, 그 전제가 서지 않는다는 것을 실측했다:
   `docker/compose.yaml` 은 **수집 변수를 하나도 두지 않고**(그 파일 자신의 주석이 D-6A2a-5 우회 6
   으로 그렇게 적는다), 실행 상태 디렉터리를 만드는 배선 셋은 전부 `mode=once` 조건부다. 그래서
   compose 로 뜬 앱에서는 `RunStateDirectory` 가 **적재되지 않는다** — 그 job 이 D-17 의 순서를 밟을
   경로가 없다. D-17 을 드는 자리는 `check` job 안의 adapters 판 넷과 app Testcontainers E2E 다.
   한편 그 job 의 「compose 기동이 수집을 켜지 않는다」 단언은 r1 이 만진 배선 둘과 관련이 있는데,
   그 축은 `check` 의 배선 test(`mode 가 없으면 이 갈래는 아예 올라오지 않는다`)가 든다.
   돌리는 편이 낫다고 보면 요청만 주면 된다 — 사람이 쓴 재현이 아니라 job 그대로 돌릴 수 있다.
4. **「세 번 크래시 → 네 번째 0 호출」을 한 test 로 재지 않았다.** 사슬을 둘로 나눠 쟀다:
   조각 → 열린 라운드(어댑터, 실 파일 원장) · 꼬리의 의도 줄 → 상한 → 다음 기동 0 호출
   (`OpeningRetryCapTest`, 이미 있다). 한 test 로 재려면 실 `RunStateDirectory` 와 수집
   use case 를 한 자리에 세워야 하고, 그 조립은 `workflow` test 지원(약 250 줄)을 adapters
   쪽에 한 벌 더 두는 일이라 중복 금지에 걸린다. 두 토막의 **접합부**는 「되살린 줄이 쓰인
   의도 줄과 같은 값」이고 그것을 변이 둘이 든다.
