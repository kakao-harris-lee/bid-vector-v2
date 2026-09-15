# M5/5C-1 — commands.md

정본 명령은 scope.md `acceptance_commands`(S-1~S-9, CI `ml-engine` job 전건). 전부 `ml-engine/`
아래에서 실행. 라운드 이력·출력 전문은 담지 않는다(핵심 결과 한 줄만, evidence-pack 규격).

| # | 명령 | exit | 핵심 결과 |
| --- | --- | --- | --- |
| S-1 | `uv sync --frozen --all-extras` | 0 | 2번째 rebase(`main`=`d4727fc`) 뒤 재실측 — 빌드+재설치 1건("Uninstalled 1 package / Installed 1 package")뿐, 새 패키지 없음(5D PR #12 가 `pyyaml`을 serving/training extras 에 이미 넣어 5C-1 의 동일 추가와 병합 시 단일화됨, 아래 「2차 rebase」 절) |
| S-1b | `uv sync --frozen --extra serving --no-dev` + 5개 import 시도 전부 실패 확인 + `uv sync --frozen --all-extras` 복귀 | 0 | sqlalchemy·psycopg·requests·httpx·celery 전부 import 실패("no leaks") — serving extras 에 학습 의존 없음 |
| S-2 | `uv run ruff check . && uv run ruff format --check .` | 0 | "All checks passed!" · 전 파일 포맷 일치 |
| S-3 | `uv run mypy --strict src/ml_engine` | 0 | "Success: no issues found in 39 source files"(rebase 뒤 재실측 — 5D 소스가 합류해 30 → 39. `[[tool.mypy.overrides]]` 추가 0(대신 `dataset.py`의 `google.protobuf` import 줄에 인라인 `# type: ignore[import-untyped]` 1곳, `booster.py`의 `BoosterLike.predict`는 위치 전용 매개변수(`/`)로 프로토콜 이름 불일치를 정정, `lgb.Booster` 반환을 `cast(BoosterLike, booster)` 1곳으로 좁힘) |
| S-4 | `uv run lint-imports` | 0 | "Contracts: 6 kept, 0 broken" — 신설 forbidden 계약(`features/training/evaluation/registry 는 DB·HTTP·업무 모듈·grpc 진입점을 모른다`) 포함. 계약 존재 자체는 `test_import_contracts.py::test_real_pyproject_has_features_forbidden_contract`가 tomllib 로 직접 단언(verifier r1 H-3) |
| S-5 | `uv run python -m pytest tests -q` | 0 | **452 passed, 1 skipped**(rebase 뒤 재실측 — 5D 합류 + D-5C-9b test 4건 + `test_artifact_roundtrip.py` 6건이 verifier r1 시점 325 위에 더해짐. skip 1건은 5D 소유, 이 slice 무관) |
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

## rebase 뒤(2026-09-13 계약 갱신 이력) 수정 요약

1. **baseline 정합** — 5D rebase 로 `tests/inference/test_policy.py`가 새로 유입되며
   `tests/training/test_policy.py`와 basename 충돌(둘 다 rootless 패키지) → pytest
   `import file mismatch`로 S-5 수집 자체 실패. `test_training_policy.py`로 개명해 해소
   (함수명·내용 무변경, `policy-values.md` 인용 갱신). BLE(ruff)·`warn_unreachable`(mypy)·
   `legacy_parity` 마커 중복은 재확인 결과 이미 정합(추가 수정 불필요). **팀장 지시로 전수
   확인** — `tests/{training,inference,registry,adapters,features}/*.py`의 basename 을
   `comm -12`로 교차 대조, `test_*.py` 계열 겹침은 `test_policy.py`(training↔inference,
   이미 해소) 하나뿐임을 확인(`conftest.py`는 세 디렉터리에 공존하지만 pytest 가 경로
   기준 importlib 로 개별 로드해 basename 충돌 대상이 아님 — 452 passed 전건 통과가 실증).
2. **D-5C-9b** — `write_artifact`가 바이트 안 `release.artifact_checksum`을 블랭크
   canonical bytes 의 sha256 으로 채우도록 변경. payload 조립을 `write_artifact` 밖으로
   뽑으며 설계 래칫 함수 길이 위반을 해소했고, 그 과정에서 새로 생긴 함수 경계에
   `dict[str, object]`/`object`(설계 래칫 약한 경계)가 나타나지 않도록 `_ArtifactPayload`·
   `_ReleasePayload` TypedDict 와 로컬 `_JsonValue` 재귀 별칭을 도입했다(전부 모듈
   밑줄 접두, 외부 참조 0 — checklist.md 「rebase 뒤 수정이 만든 public 표면」).
3. **`test_artifact_roundtrip.py`(신설)** — `OPEN-5C-ARTIFACT-ROUNDTRIP` 종결.

S-1~S-9 rebase 뒤 재실측(D-5C-9b 구현 + 왕복 test 신설 직후, 이 절을 처음 쓴 시점) — 전건
exit 0, 표는 위 최신 수치로 갱신 완료. **verifier r3 L-3 정정** — 이 절이 처음엔 그 시점의
head SHA(`07f6024`)를 박아 뒀으나 그 뒤 두 차례 rebase 로 조상에서 떨어져 낡는 좌표가 됐다
(`git merge-base --is-ancestor 07f6024 HEAD` 실패) — 이후로는 SHA 대신 **작업 내용**으로
가리킨다(다음 rebase 에도 낡지 않는다).

## 2차 rebase — `main` 이 `d4727fc`(PR #12 병합)로 이동

팀장 지시(2026-09-15) — 5D 가 PR #12 로 origin `main`에 병합돼 `main`=`d4727fc`(`cefb19c` +
`0310b1a` 「pyyaml 을 serving/training extras 로」 + 머지 커밋). `git rebase d4727fc` 실행,
28개 커밋 중 **`ml-engine/uv.lock` 한 파일에서만 충돌**(`ml-engine/pyproject.toml`은
git 3-way 병합이 자동으로 정리 — 양쪽이 `training` extras 에 같은 줄 `"pyyaml==6.0.3",`을
동일 위치에 추가해 identical-content 병합으로 충돌 없이 단일화됐다).

`uv.lock`의 `requires-dist` 배열에서 5D 쪽(`extra == 'serving'`)과 5C-1 쪽(`extra ==
'training'`) 두 `pyyaml` 항목이 서로 다른 줄이라 conflict marker 로 남았다 — 둘 다 유지(삭제
대상 아님, 서로 다른 extra 를 가리키므로 중복이 아니다)하는 방향으로 수동 해소한 뒤
`uv lock`으로 전체 재생성 — **재생성 결과가 수동 해소와 바이트 동일**(diff 0)임을 확인했다.

재실측(base `d4727fc`, HEAD `44f0950`) — S-1~S-9 전건 exit 0, S-5 **452 passed, 1 skipped**
(rebase 전과 동일 — 이 rebase 는 dependency 선언만 건드렸고 코드는 무변경).

## verifier r3 H-1 — artifact 파생 값 셋 독립 재계산 test + 변이 실측

3개 파생 값(`feature_manifest_checksum`·`training_spec_checksum`·`residual_std` 하한)이
재계산 대조 test 0건이라 변이가 452 test 전건을 조용히 통과하던 문제(verifier r3 §「H-1」).
독립 재계산 test 넷 신설(`test_train_artifact.py` ①②③+대조군) + 왕복 test 의
`_expected_ref` 동어반복 제거(`test_artifact_roundtrip.py`) 뒤, 세 변이를 실제로 심어
FAILED 를 확인하고 원복했다(임시 `sed` 변이, 검증 뒤 `git diff --stat` 0 확인):

| 변이 | 대상 | 원복 전 결과 |
| --- | --- | --- |
| 1b | `artifact_writer.py` — `feature_manifest_checksum` 뒤에 `.upper()` | **5 FAILED**(전용 test 1 + 왕복 test 4 — `_expected_ref` 정정 효과로 여기서도 검출됨) |
| 4 | `train.py` — `training_spec_checksum=spec_checksum(spec)` 뒤에 `.upper()` | **1 FAILED** |
| 6 | `train.py` — `floor=spec.min_residual_std` → `floor=float()` | **1 FAILED**(`0.0 == 0.002` 단언 실패로 바닥 우회가 값으로 드러남) |

세 변이 모두 원복 뒤 **456 passed, 1 skipped**(452 + 신규 4)로 복귀. S-1~S-9 전건 재실측
exit 0.

## verifier r3 L-4·L-5 — scope.md 정정 필요(인계, 팀장 몫)

**이 slice 의 `reports/evidence/m5/5c/scope.md`는 구현 레인 커밋이 0건**(전 5커밋이
`docs(m5-5c…)` 문서 레인 — rollback.md 「`scope.md` — 유일한 rollback 제외 대상」 절의
근거와 같다). 구현 레인이 그 파일을 직접 고치면 이 근거가 깨지므로, 정정할 사실만 여기
적고 실제 편집은 팀장 몫으로 남긴다.

- **L-4**: `scope.md` 「하네스 레인 변경 (상시 절)」이 「착수 시 없음. 리뷰 요청 시점
  재실행」에 머물러 있다. 실측(`git log --oneline main..HEAD -- CLAUDE.md .claude/`) —
  **0행**(하네스 레인 변경 없음, 이번 rebase 두 차례 동안도 계속 0).
- **L-5**: `scope.md` in_scope 목록이 `ml-engine/uv.lock`을 「(b) 의 결과 — lock 이
  바뀐다」로 선언하지만, 2차 rebase(`main`=`d4727fc`) 뒤로는 **`main`과 바이트 동일**이다
  (본 문서 「2차 rebase」절 · `rollback.md` 「파일 목록」절이 이미 정확히 적어 뒀다).

## secret 스캔

```
grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m5/5c/
```
결과: 이 절 자신이 "secret"이라는 단어를 설명하려고 여러 번 쓰기 때문에 자기 인용으로
몇 건이 잡힌다(이 절 제목·grep 패턴 문자열·이 설명 문장 자체) — 실제 credential 형태의
매치는 0건이다(verifier r1 L-4 — 자기참조 오탐임을 명시, 값이 아니라 패턴 언급이므로
민감정보 유출이 아니다).
