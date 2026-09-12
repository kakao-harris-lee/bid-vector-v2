# M5/5C-1 — commands.md

정본 명령은 scope.md `acceptance_commands`(S-1~S-9, CI `ml-engine` job 전건). 전부 `ml-engine/`
아래에서 실행. 라운드 이력·출력 전문은 담지 않는다(핵심 결과 한 줄만, evidence-pack 규격).

| # | 명령 | exit | 핵심 결과 |
| --- | --- | --- | --- |
| S-1 | `uv sync --frozen --all-extras` | 0 | 33 packages audited(`pyyaml`을 training extras 에 더한 뒤 `uv lock` 갱신, 락 diff 2줄: `training` 의존 목록 + `requires-dist` 한 줄) |
| S-1b | `uv sync --frozen --extra serving --no-dev` + 5개 import 시도 전부 실패 확인 + `uv sync --frozen --all-extras` 복귀 | 0 | sqlalchemy·psycopg·requests·httpx·celery 전부 import 실패("no leaks") — serving extras 에 학습 의존 없음 |
| S-2 | `uv run ruff check . && uv run ruff format --check .` | 0 | "All checks passed!" · "78 files already formatted"(1차 라운드에서 import 정렬 3건 자동수정 — `--fix` 뒤 재검) |
| S-3 | `uv run mypy --strict src/ml_engine` | 0 | "Success: no issues found in 31 source files" — `[[tool.mypy.overrides]]` 추가 0. 실패 이력: (a) `google.protobuf` 미설치 스텁 → `dataset.py` import 줄에 인라인 `# type: ignore[import-untyped]`(overrides 절 편집 아님) (b) `BoosterLike.predict` 프로토콜 이름 불일치(`matrix` vs lgb 실제 `data`) → 위치 전용 매개변수(`/`)로 정정 (c) `lgb.Booster` 실제 stub 반환형이 프로토콜보다 넓어 `cast(BoosterLike, booster)` 한 자리 |
| S-4 | `uv run lint-imports` | 0 | "Contracts: 6 kept, 0 broken" — 신설 forbidden 계약(`features/training/evaluation/registry 는 DB·HTTP·업무 모듈·grpc 진입점을 모른다`) 포함 |
| S-5 | `uv run python -m pytest tests -q` | 0 | 330 passed(5A+5B 233 + 5C-1 신규 97). 실패 이력: (a) `test_serving_purity.py`(5A 전역 `sys.modules` 관측) — pytest collection 단계가 전체 스위트 test 모듈을 미리 import 해 `ml_engine.training`/`ml_engine.adapters` 가 실행 순서와 무관하게 캐시에 남음 → `tests/adapters/conftest.py`·`tests/training/conftest.py` 에 `pytest_collection_finish` 훅 + per-test teardown 추가(5A 파일 무편집, 알려진 제한 참조) (b) 설계 래칫 초과 3건 → 함수 분해로 해소(아래 S-6) (c) `reuse_provenance_check` 8건 불일치 → `reuse.md` 8행 작성(경로 오기 1회: 최초 `ml-engine/reports/evidence/...`에 잘못 생성, `reports/evidence/m5/5c/`로 정정) |
| S-6 | `uv run python tools/design_ratchet.py --check` | 0 | "설계 래칫 위반 없음" — `encoding_oof.py`(`out_of_fold_matrix_and_residuals`·`_fit_fold` 2건)·`train.py`(`train_award_rate_gbm`)·`artifact_writer.py`(`write_artifact`) 4개 함수를 각각 2~3개 헬퍼로 분해해 해소(`_FoldContext`·`_Accumulators`·`_admit_and_gate`·`_build_oof_and_space`·`_serialize_rejected_rows` 등) |
| S-7 | `uv run python tools/reuse_provenance_check.py && ! uv run python tools/reuse_provenance_check.py --evidence tests/gates/fixtures/reuse-mismatch.md` | 0 | "재활용 출처 두 자리 일치 — 위반 0" · 대조 fixture 는 여전히 13건 불일치로 실패(양성 대조 유지 확인) |
| S-9 | python-version/requires-python 대조 | 0 | `3.12` / `>=3.12,<3.13` 일치 |

## libomp(macOS 로컬 환경, 알려진 제한)

로컬(Darwin arm64) `uv sync`가 설치한 `lightgbm==4.7.0` 배포판은 `libomp.dylib`를 동적 링크하며
homebrew keg-only 로 기본 설치되지 않는다 — 최초 `import lightgbm` 시
`Library not loaded: @rpath/libomp.dylib`(exit 아님, `OSError`)로 실 LightGBM 학습 test 2개
(`test_booster.py` 2건)와 재현성 test(`test_train_artifact.py`)가 실패했다. `brew install libomp`
(`/opt/homebrew/opt/libomp`)로 해소 후 전건 통과 확인. **CI(Linux 러너)에서는 `lightgbm` wheel 이
OpenMP 를 정적 링크하거나 러너 이미지에 `libgomp1` 이 이미 있어 이 문제가 일반적으로 재현되지
않는다** — 이 slice 는 로컬 실측만 남기고, CI 그린 여부는 verifier 가 실제 CI 실행으로 확인한다.

## secret 스캔

```
grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m5/5c/
```
결과: 매치 0건.
