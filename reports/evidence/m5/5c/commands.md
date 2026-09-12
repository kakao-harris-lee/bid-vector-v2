# M5/5C-1 — commands.md

정본 명령은 scope.md `acceptance_commands`(S-1~S-9, CI `ml-engine` job 전건). 전부 `ml-engine/`
아래에서 실행. 라운드 이력·출력 전문은 담지 않는다(핵심 결과 한 줄만, evidence-pack 규격).

| # | 명령 | exit | 핵심 결과 |
| --- | --- | --- | --- |
| S-1 | `uv sync --frozen --all-extras` | 0 | 33 packages audited(`pyyaml`을 training extras 에 더함, `uv.lock` 갱신 2줄) |
| S-1b | `uv sync --frozen --extra serving --no-dev` + 5개 import 시도 전부 실패 확인 + `uv sync --frozen --all-extras` 복귀 | 0 | sqlalchemy·psycopg·requests·httpx·celery 전부 import 실패("no leaks") — serving extras 에 학습 의존 없음 |
| S-2 | `uv run ruff check . && uv run ruff format --check .` | 0 | "All checks passed!" · 전 파일 포맷 일치 |
| S-3 | `uv run mypy --strict src/ml_engine` | 0 | "Success: no issues found in 30 source files" — `[[tool.mypy.overrides]]` 추가 0(대신 `dataset.py`의 `google.protobuf` import 줄에 인라인 `# type: ignore[import-untyped]` 1곳, `booster.py`의 `BoosterLike.predict`는 위치 전용 매개변수(`/`)로 프로토콜 이름 불일치를 정정, `lgb.Booster` 반환을 `cast(BoosterLike, booster)` 1곳으로 좁힘) |
| S-4 | `uv run lint-imports` | 0 | "Contracts: 6 kept, 0 broken" — 신설 forbidden 계약(`features/training/evaluation/registry 는 DB·HTTP·업무 모듈·grpc 진입점을 모른다`) 포함. 계약 존재 자체는 `test_import_contracts.py::test_real_pyproject_has_features_forbidden_contract`가 tomllib 로 직접 단언(verifier r1 H-3) |
| S-5 | `uv run python -m pytest tests -q` | 0 | **325 passed**(5A+5B 233 + 5C-1 신규 92) |
| S-6 | `uv run python tools/design_ratchet.py --check` | 0 | "설계 래칫 위반 없음" |
| S-7 | `uv run python tools/reuse_provenance_check.py && ! uv run python tools/reuse_provenance_check.py --evidence tests/gates/fixtures/reuse-mismatch.md` | 0 | "재활용 출처 두 자리 일치 — 위반 0"(reuse.md 7행) · 대조 fixture 는 여전히 불일치로 실패(양성 대조 유지 확인) |
| S-9 | python-version/requires-python 대조 | 0 | `3.12` / `>=3.12,<3.13` 일치 |

## libomp(macOS 로컬 환경, 알려진 제한)

로컬(Darwin arm64) `uv sync`가 설치한 `lightgbm==4.7.0` 배포판은 `libomp.dylib`를 동적 링크하며
homebrew keg-only 로 기본 설치되지 않을 수 있다 — 없으면 최초 `import lightgbm` 시
`Library not loaded: @rpath/libomp.dylib`(`OSError`)로 실 LightGBM 학습 test 가 실패한다.
`brew install libomp`로 해소한다(이 slice 의 검증 환경에는 설치돼 있어 실 LightGBM test 9건
(재현성 포함)이 skip 없이 전건 통과했다). **CI(Linux 러너)에서는 `lightgbm` wheel 이 OpenMP 를
정적 링크하거나 러너 이미지에 `libgomp1` 이 이미 있어 이 문제가 일반적으로 재현되지 않는다** —
CI 그린 여부의 최종 확인은 실제 CI 실행(PR)으로 한다.

## verifier r1 뒤 수정 요약(장부, 라운드별 수리 서사 아님 — evidence-pack 규격)

HIGH 3(H-1 `write_artifact` 시그니처를 `trained` 하나로 좁힘·H-2 OOF 누수 test 에 legacy
「모든 행」 단언 복원·H-3 ⑫ 양성 대조가 실제 `pyproject.toml` 계약에 의존하게) · MEDIUM 1(M-1
`build_training_matrix` 삭제, M-2·M-3 은 장부/무조치) · LOW 7 일괄. 상세 근거는 `checklist.md`
「verifier r1 수정 라운드가 만든/지운 public 표면」과 각 수정 커밋 메시지.

## secret 스캔

```
grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m5/5c/
```
결과: 이 절 자신이 "secret"이라는 단어를 설명하려고 여러 번 쓰기 때문에 자기 인용으로
몇 건이 잡힌다(이 절 제목·grep 패턴 문자열·이 설명 문장 자체) — 실제 credential 형태의
매치는 0건이다(verifier r1 L-4 — 자기참조 오탐임을 명시, 값이 아니라 패턴 언급이므로
민감정보 유출이 아니다).
