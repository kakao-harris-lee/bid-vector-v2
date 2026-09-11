# ml-engine

bid-vector V2 독립 Python ML engine 패키지(`v2-지침서.md` §3.2, milestone 5). LightGBM/KDE
수학과 모델 생명주기를 Kotlin 서비스와 분리된 패키지 경계로 담는다.

## 패키지 경계

`src/ml_engine/`:

| 패키지 | 담는 것 | slice |
| --- | --- | --- |
| `contracts` | M2 proto 재수출 **하나만**(`bidvector.ml.v1.*_pb2`) — 다른 패키지는 직접 못 본다 | 5A |
| `features` | training/serving 공용 feature 변환(versioned schema) | 5B |
| `training` | LightGBM 학습 파이프라인·holdout·재현 seed | 5C |
| `evaluation` | calibration·worst-segment report·promotion 측정 | 5C |
| `inference` | model predict adapter·KDE 커널 | 5D |
| `registry` | versioned 정책·artifact registry(`registry/policy.py`) | 5A(자리)·5C/5D(소비) |
| `serving` | M2 generated gRPC servicer | 5E |
| `adapters` | training 쪽 storage adapter | 5C |

경계는 `pyproject.toml` `[tool.importlinter]`가 CI 에서 강제한다(`uv run lint-imports`):
`serving`/`inference`는 `features`·`contracts`만, `training`/`evaluation`도 같은 축.
`serving`/`inference`는 SQLAlchemy·DB driver·`requests`/`httpx`·`celery`·`ml_engine.training`·
`ml_engine.adapters`를 모른다. `bidvector.*`·`ml_engine.contracts._generated.*`는 `contracts`
밖에서 import 금지(재수출 하나만 허용).

## 계약 생성 stub

`contracts/proto`(저장소 루트)가 단일 출처다. Python 쪽 생성 stub(`*_pb2.py`)은
`grpc_tools.protoc`가 `src/ml_engine/contracts/_generated/`에 만든다 — **VCS 밖**
(`.gitignore`, 디렉터리 자체는 `.gitkeep`으로 존재). `tests/conftest.py`가 pytest 세션마다
자동으로 채운다. 수동 생성:

```
uv run python tools/generate_contracts.py src/ml_engine/contracts/_generated
```

## 재활용 출처 기록 (ADR 0009)

이식한 모듈은 docstring **첫 줄**에 `Reuse: <원본 경로>@<commit>`을 적는다(원본은
`bid-vector/`, 기준 commit `ed4b06c`). 수행한 수정·튜닝 내역은
`reports/evidence/m5/<slice>/reuse.md`에 같은 모듈에 대해 같은 원본 경로·commit으로
기록한다 — `tools/reuse_provenance_check.py`가 두 자리를 대조한다(CI, S-7).

## 개발

```
uv python pin 3.12
uv sync --frozen --all-extras     # dev 포함 전체
uv run ruff check . && uv run ruff format --check .
uv run mypy --strict src/ml_engine
uv run lint-imports
uv run python -m pytest tests -q
uv run python tools/design_ratchet.py --check
uv run python tools/reuse_provenance_check.py
```

`serving`/`training` extras 분리 확인: `uv sync --frozen --extra serving --no-dev` 뒤
SQLAlchemy·`psycopg`·`requests`·`httpx`·`celery`가 설치되지 않아야 한다(끝나면
`uv sync --frozen --all-extras`로 복구).

## 설계 래칫

`tools/design_ratchet.py`(legacy `bid-vector/scripts/_design_ratchet_scan.py` 이식) —
함수 50줄·파일 500줄·`dict[str, Any]` 약한 경계를 AST 로 센다. baseline 완화가 아니라
**위반 0 또는 `pyproject.toml [tool.design-ratchet] allowlist`의 정확 경로 등재**만 허용한다.
