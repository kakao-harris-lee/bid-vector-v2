# M5/5D — commands.md

base `f5020aa982e6c5ade01f1660c0568dcd75c2fa7e`(PR #10 머지 = origin/main, 5A·5B 실물 포함)
· 계약 `2bf3bd0`(착수 계약 고정) → `619cc5a`(verifier r1 반영 지시) → `b9a4e4b`(verifier r2
반영 지시, F-1~F-4) · 코드 커밋 `2a1bae7`(최초) → `145847c`(verifier r1 M-1/M-2/M-4·L-1/
L-2/L-3 수정) → `4ad2de7`(verifier r2 F-1 — `rounding.py` 신설, `policy.py` quantize 뒤
`clamp_min>0` 불변식). 로컬: `uv`(pyenv `3.12.2`, `.python-version` 3.12 과
`requires-python` 두 자리 일치 — S-9). 전건 재실행(부분 게이트 없음) — F-1 은 게이트
술어(정책 불변식) 변경이라 표 전체를 다시 돌렸다.

## S-1 ~ S-9 (scope.md acceptance_commands 순서, F-1 뒤 재실행)

| # | 명령(요약) | exit | 핵심 한 줄 |
| --- | --- | --- | --- |
| S-1 | `uv sync --frozen --all-extras` | 0 | `Audited 33 packages`(변경 없음) |
| S-1b | `uv sync --frozen --extra serving --no-dev` + 5개 import 실패 확인 + 복구 | 0 | sqlalchemy·psycopg·requests·httpx·celery 전부 부재 확인, 복구 후 all-extras 정상 |
| S-2 | `uv run ruff check .` + `uv run ruff format --check .` | 0 | `All checks passed!` · `70 files already formatted`(`rounding.py` 신설로 +1) |
| S-3 | `uv run mypy --strict src/ml_engine` | 0 | `Success: no issues found in 27 source files`(`rounding.py` 신설로 +1, `warn_unreachable=true` 유지 — 새 unreachable 없음) |
| S-4 | `uv run lint-imports` | 0 | `Contracts: 5 kept, 0 broken`(45 files, 125 dependencies) |
| S-5(scope.md 문면, 마커 없음) | `uv run python -m pytest tests -q` | 0 | **336 passed, 1 skipped**(golden) — base 333 + F-1 신규 3(`test_clamp_min_quantizes_to_zero_is_rejected_digits_1`·`_digits_4`·`test_shipped_clamp_min_survives_quantize_check`) |
| S-5(판정 기준, verifier r1 L-4·계약 갱신 `619cc5a`) | `uv run python -m pytest tests -q -m "not legacy_parity"` | 0 | **332 passed, 1 skipped, 4 deselected** |
| S-8(관측 전용 — Kotlin `./gradlew check` 아님) | `uv run python -m pytest tests -q -m legacy_parity` | 0 | **4 passed**(legacy 산식 재현 관측 — 판정 근거 아님) |
| S-6 | `uv run python tools/design_ratchet.py --check` | 0 | 설계 래칫 위반 없음(allowlist 편집 없음 — `rounding.py`도 50줄 이내) |
| S-7 | `reuse_provenance_check.py` + 양성 대조(`reuse-mismatch.md`) | 0 / 1 | 정상: 위반 0(`rounding.py`는 신규 작성이라 `Reuse:` 포인터 없음, 대상 아님) · 양성: exit 1(회귀 없음, 10건 불일치 재확인) |
| S-9 | python 버전 두 자리 대조 | 0 | assert 통과(출력 없음) |

## verifier r2 재검증 세부

- **F-1** 재현 YAML 둘 모두 `PolicyRejected` 확인 — `scenario.bid_rate_digits=1` +
  `scenario.clamp_min=0.04` → `PolicyRejected("scenario.clamp_min(0.04)은 scenario.
  bid_rate_digits(1) 자리로 quantize 한 뒤에도 0보다 커야 한다: quantize 결과 0.0")`.
  `scenario.bid_rate_digits=4`(출하 기본) + `scenario.clamp_min=0.00001` → 같은 형태의
  `PolicyRejected`(quantize 결과 `0.0000`). 회귀 없음 확인: 출하 정책(`clamp_min=0.7`,
  `digits=4`)은 `quantize_bid_rate(Decimal("0.7"), 4) = Decimal("0.7000") > 0`이라 영향
  없음(`test_shipped_clamp_min_survives_quantize_check`). fix 제거 임시 실험(즉시
  원복, 커밋 없음) — 재현 YAML 둘 다 이전 판에서는 `InferencePolicy`를 통과했음을 확인.
- **F-2** `LoadedArtifact(manifest, _VerifiedBytes(b"anything"))`(모듈 밖 `_VerifiedBytes`
  직접 import) — 임시 프로브 파일로 `mypy --strict` 재실행: `Success, no issues found
  in 28 source files`(clean, 거부 없음) · 런타임 실행: `LoadedArtifact` 정상 생성(체크섬
  검증 0 회). checklist.md (2b) 행·알려진 제한 12 에 등재, 즉시 원복(커밋에 없음).

## verifier r1 재검증 세부(이전 라운드, 회귀 없음 재확인)

- **M-1** `LoadedArtifact(some_manifest)` — 1 인자만 주면 런타임 `TypeError`(실측,
  `test_loaded_artifact_requires_verified_bytes_argument`). mypy strict 도 같은 호출을
  **정적으로** 거부함을 임시 프로브 함수로 직접 확인(즉시 원복, 커밋에 없음) —
  `error: Missing positional argument "verified" in call to "LoadedArtifact"  [call-arg]`.
- **M-2** `scenario.clamp_min = -1.0` 로드 → `PolicyRejected("scenario.clamp_min 은
  0보다 커야 한다: -1.0")`(이전 판은 `InferencePolicy`까지 통과해 `build_scenario_
  candidates(center=-0.5, std=0.0, ...)`가 `Candidate.__post_init__`의 `ValueError`를
  던졌다 — 지금은 정책 로드 단계에서 막힌다). 음수 weight(`-0.10`, 합 1 유지)·
  `min_predictive_std=0.0` 도 각각 `PolicyRejected` 확인.
- **M-4** `warn_unreachable=true` 적용 뒤 `mypy --strict src/ml_engine` 재실행 —
  `registry/artifact.py`의 `JsonValue` 교체 후 unreachable 0. 반증 실측: 이전 판
  `JsonScalar`(중첩 컨테이너 배제)로 임시 되돌려 재실행하면 `feature_names` 목록 검사
  (`_parse_feature_fields`, verify_feature_names 호출 직전 분기)에서 unreachable **1건**
  (`Found 1 error in 1 file`)이 재현됨을 확인했다(임시 diff, 즉시 원복 — 커밋에 남지
  않음). verifier r1 보고의 "죽는 구간이 `feature_names` 목록 검사와 `verify_feature_
  names` 호출"은 이 실측과 일치한다.
- **L-1** `Decimal("0.87465").quantize(Decimal("0.0001"), rounding=ROUND_HALF_UP)` →
  `0.8747`, `ROUND_HALF_EVEN` → `0.8746`(두 값 다 test 로 직접 대조).
- **L-2** K5 `global_level=None` → `Unmeasurable(INSUFFICIENT_SAMPLES,
  NO_GLOBAL_SAMPLES)`(이전 `TOO_FEW_DRAWS`에서 분리).
- **L-3** `residual_std=float("nan")`(JSON 왕복 후에도 `NaN` 리터럴로 보존됨, Python
  `json.loads` 기본이 허용) → `ArtifactRejected("residual_std 는 유한해야 한다: nan")`.
  `inf`·`-inf` 도 동일 확인.

## verifier r1 재검증 세부

- **M-1** `LoadedArtifact(some_manifest)` — 1 인자만 주면 런타임 `TypeError`(실측,
  `test_loaded_artifact_requires_verified_bytes_argument`). mypy strict 도 같은 호출을
  **정적으로** 거부함을 임시 프로브 함수로 직접 확인(즉시 원복, 커밋에 없음) —
  `error: Missing positional argument "verified" in call to "LoadedArtifact"  [call-arg]`.
- **M-2** `scenario.clamp_min = -1.0` 로드 → `PolicyRejected("scenario.clamp_min 은
  0보다 커야 한다: -1.0")`(이전 판은 `InferencePolicy`까지 통과해 `build_scenario_
  candidates(center=-0.5, std=0.0, ...)`가 `Candidate.__post_init__`의 `ValueError`를
  던졌다 — 지금은 정책 로드 단계에서 막힌다). 음수 weight(`-0.10`, 합 1 유지)·
  `min_predictive_std=0.0` 도 각각 `PolicyRejected` 확인.
- **M-4** `warn_unreachable=true` 적용 뒤 `mypy --strict src/ml_engine` 재실행 —
  `registry/artifact.py`의 `JsonValue` 교체 후 unreachable 0. 반증 실측: 이전 판
  `JsonScalar`(중첩 컨테이너 배제)로 임시 되돌려 재실행하면 `feature_names` 목록 검사
  (`_parse_feature_fields`, verify_feature_names 호출 직전 분기)에서 unreachable **1건**
  (`Found 1 error in 1 file`)이 재현됨을 확인했다(임시 diff, 즉시 원복 — 커밋에 남지
  않음). verifier r1 보고의 "죽는 구간이 `feature_names` 목록 검사와 `verify_feature_
  names` 호출"은 이 실측과 일치한다.
- **L-1** `Decimal("0.87465").quantize(Decimal("0.0001"), rounding=ROUND_HALF_UP)` →
  `0.8747`, `ROUND_HALF_EVEN` → `0.8746`(두 값 다 test 로 직접 대조).
- **L-2** K5 `global_level=None` → `Unmeasurable(INSUFFICIENT_SAMPLES,
  NO_GLOBAL_SAMPLES)`(이전 `TOO_FEW_DRAWS`에서 분리).
- **L-3** `residual_std=float("nan")`(JSON 왕복 후에도 `NaN` 리터럴로 보존됨, Python
  `json.loads` 기본이 허용) → `ArtifactRejected("residual_std 는 유한해야 한다: nan")`.
  `inf`·`-inf` 도 동일 확인.

## rollback.md 갱신(F-1 이후 — 파일 집합 변경)

F-1 이 `rounding.py`를 신설해 in_scope 파일 집합이 이전 라운드(`145847c`)와 달라졌다
(SHA 만 바꿔 끝낼 수 없는 경우 — 「파일 집합 불변이면 SHA·재실측 한 줄」의 반대 분기).
`rollback.md`를 코드 마지막 커밋 `4ad2de7`(diff·rm 목록에 `rounding.py` 추가) 기준으로
다시 산출하고 새 임시 clone(`/tmp/5d-rollback-check3`, 정리 완료)에서 재실측했다: in_scope
diff **0** · `pytest tests -q` **233 passed** · `mypy --strict` **18 source files clean**
(`rounding.py`도 함께 걷혀 27→18) · `lint-imports` **5 kept, 0 broken**.

## 비고

- leak 스캔: `grep -rniE -f config/quality/leak-patterns.txt ml-engine/src/ml_engine/inference
  ml-engine/src/ml_engine/registry ml-engine/policy ml-engine/tests/inference
  ml-engine/tests/registry reports/evidence/m5/5d` → exit 1(매치 0).
- 하네스 레인 변경: `git log --oneline f5020aa982e6c5ade01f1660c0568dcd75c2fa7e..HEAD --
  CLAUDE.md .claude/` → 빈 목록(이번 라운드까지 하네스 레인 변경 없음).
- 설계 래칫 — 최초 라운드에서 이미 4개 함수(assessment/policy/predict/artifact)를 분해해
  위반 0 을 달성했고(2A~2E 아님, 이 slice 최초 커밋 `2a1bae7` 근거), 이번 라운드는
  `JsonScalar`→`JsonValue` 교체만으로 래칫 재확인(위반 0 유지) — allowlist 편집 없음.
- `pyproject.toml` 편집 범위(이번 라운드 추가분) — `[tool.mypy] warn_unreachable = true`
  (verifier r1 M-4 지시, 계약 갱신 `619cc5a`가 명시 승인한 5A 소유 파일 hunk). 기존
  `[tool.ruff.lint] select`의 `"BLE"`·`[tool.pytest.ini_options] markers`의
  `legacy_parity`는 최초 라운드에서 이미 반영(무변경). 게이트 계약·래칫 한도(
  `[tool.design-ratchet]`·`[tool.importlinter]`)는 계속 무편집.
- M-3(golden 통합)은 팀장 지시로 이번 라운드 제외 — `tests/inference/golden/
  test_kernel_golden.py`는 무편집, checklist.md 알려진 제한 11 에 등재.
