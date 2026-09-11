# M5/5A — checklist.md

## D-5A-0~4 충족 근거

| ID | 결정 | 충족 근거 |
| --- | --- | --- |
| D-5A-0 (b) | 생성 stub `ml_engine/contracts/_generated/`(VCS 밖) + `contracts/__init__.py` 재수출 하나 | `src/ml_engine/contracts/__init__.py`(구현 노트 — import 문 형태는 protoc 제약으로 절대 import·`sys.path` 삽입 관례로 조정, 생성 위치·gitignore·재수출 하나 허용은 그대로) · `.gitignore`(`_generated/*` 무시 + `.gitkeep` 예외) · `tests/gates/test_generated_not_tracked.py`(`git ls-files` 빈 것 단언, S-5 안) |
| D-5A-1 | Python 3.12, `requires-python`+`.python-version` 두 자리 대조 | `ml-engine/.python-version`="3.12" · `pyproject.toml` `requires-python = ">=3.12,<3.13"` · S-9 exit 0(commands.md) |
| D-5A-2 | mypy allowlist 는 이식 모듈 단위·사유·해소 계획 명시, 신규 모듈은 strict 예외 없음 | `[[tool.mypy.overrides]]`(`bidvector.*` 하나 — 구조적 예외, `# reason:`/`# resolve:` 주석) · `tests/gates/test_mypy_allowlist.py`(주석 없는 override 는 검사 함수가 걸러냄을 양성 대조로 확인) |
| D-5A-3 | 위반 0 또는 명시 allowlist(baseline 완화 불채택) | `pyproject.toml [tool.design-ratchet] allowlist = []` · `tools/design_ratchet.py`(baseline 비교 로직 자체가 없다 — legacy 의 `compare_reports`/`count_improvements` 미이식) · `tests/gates/test_design_ratchet.py::test_allowlist_is_empty_at_5a` |
| D-5A-4 | 도구 버전은 lock 이 정본, dev extras `==` 핀 | `pyproject.toml [project.optional-dependencies] dev`(전부 `==`) · `ml-engine/uv.lock`(커밋) · `uv sync --frozen`이 lock 불일치 시 실패(S-1) |

## (2b) 값 획득 축 갱신 — 5A 가 새로 여는 public 표면

| 표면 | 연다 것 | 판정 |
| --- | --- | --- |
| `ml_engine.contracts.*`(재수출) | `common_pb2`~`training_pb2_grpc` 9개 이름 | **의도된 유일 통로** — forbidden 계약이 그 밖 접근을 막는다(`test_import_contracts.py`) |
| `ml_engine.registry.policy.load_policy`/`Policy` | YAML 로드(값 없음, `known_keys` 인자로 호출부가 범위를 준다) | 결과가 **쓴 값을 나르지 않는다** — `Policy.values`는 호출부가 준 `known_keys`만 담고, 그 키 집합 자체가 5A 산출물이 아니다(설계 검토 (2b)) |
| 일곱 `__init__.py`(비어 있음) + 최상위 `ml_engine/__init__.py` | 패키지 자리만 | 로직 없음 |
| `tools/*.py` 셋(CLI) | exit 코드만 | `main()`이 값을 반환하지 않는다 |

**새 표면 없음**: 커널·변환·정책 값 실물 — 전부 out_of_scope 그대로.

## 알려진 제한

1. **`serving.grpc` 예외 미등재** — pyproject.toml 의 forbidden 계약이 `grpc`를
   `serving`/`inference`에 전면 금지한다. `ignore_imports`로 `serving.grpc`만 열 계획이었으나
   5A 시점 그 모듈이 없어(5E 가 만든다) import-linter 가 "No matches for ignored import"로
   설정 오류 처리한다(exit 1, 실측). **5E 가 `serving/grpc.py`를 만들 때
   `pyproject.toml`에 `ignore_imports = ["ml_engine.serving.grpc -> grpc"]` 한 줄을
   추가해야 한다** — `OPEN-5A-SERVING-GRPC-EXCEPTION`.
2. **`registry/policy.py`가 정책 값을 모른다** — 의도된 설계(D-M5-6, 값은 5C·5D). `Policy`는
   `known_keys`를 호출부가 주지 않으면 어떤 본문 키도 통과시키지 않는다.
3. **CI 러너 미실행** — `.github/workflows/ci.yml`의 `ml-engine` job 은 로컬에서 같은 명령
   순서로 초록 확인했다(commands.md). 실제 GitHub Actions 러너 실행은 push 뒤에만
   확인 가능하다(push 는 이 slice 범위 밖) — `OPEN-5A-PY-CI`.
4. **`tools/design_ratchet.py`가 legacy 의 일부만 이식** — clone 탐지·`json` 직접 호출·
   `ENVIRONMENT` sniff·Celery 미검증 payload 지표는 드롭했다(`reuse.md` 사유). ml-engine 에
   해당 정책·의존이 아직 없어 5B~5E 가 그 축을 만나면 재검토가 필요할 수 있다.

## OPEN

| OPEN | 상태 | 처리 |
| --- | --- | --- |
| `OPEN-5A-MYPY-ALLOWLIST` | **초기 — 0건**. 5A 시점 `[[tool.mypy.overrides]]`는 `bidvector.*`(생성 stub 재수출 구조적 예외) 하나뿐, 이식 모듈 strict 예외는 없다 | prep 조사(01_scout_ml_package §a-5)가 `settlement_maturity`(K7)를 1건 후보로 지목했다 — **5D 가 K7 을 이식할 때 갱신**(사유·해소 slice 명시, `test_mypy_allowlist.py`가 주석 강제) |
| `OPEN-5A-PY-CI` | CI job 신설(이 slice) | 러너 실행 확인은 push 뒤(범위 밖) |
| `OPEN-5A-SERVING-GRPC-EXCEPTION` | 신설 | 5E 가 `serving/grpc.py` 를 만들 때 `ignore_imports` 한 줄 추가 |
