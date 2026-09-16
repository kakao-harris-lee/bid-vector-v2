# M5/5E-3 — checklist.md

## 수정 라운드 1 (verifier r1 HIGH-1 + LOW 5, code-reviewer LOW 1)

- **HIGH-1(게이트 술어 우회, D-5E3-6)** — `ml-engine/pyproject.toml`에 import-linter
  `forbidden` 계약 한 블록 추가(`ml_engine` 전체에서 `yaml` import 금지, `registry.
  policy` + 기존 세 소비 로더만 `ignore_imports`). `tests/gates/test_policy_loaders_
  fail_closed.py`의 로더 수집을 반환 타입 주석 문자열 대신 이름 규약(정의된 모듈의
  `load_` 접두 함수, 뿌리만 이름으로 명시 제외) 하나로 재작성, 서로 다른 두 내부
  경로(inspect vs `vars()`+`callable`)로 계산한 집합이 일치하는지 대조하는 test를
  추가해 숨은 이차 필터 재도입을 구조적으로 막았다. 정규식 기반 소스 스캔 test는
  삭제(import-linter 계약이 같은 불변식을 더 튼튼하게 잡음, D-5E3-6 ③). verifier의
  정확한 재현 probe(반환 타입 별칭 + `yaml` 별칭 import)로 게이트 test·import-linter
  둘 다 독립적으로 잡음을 확인했다(아래 「우회 대응표 갱신」).
- **LOW-1(`HoldoutCancelled.completed_windows` 문면 불일치)** — docstring을 「학습까지
  끝난」에서 「처리를 끝낸(학습 실패로 건너뛴 창 포함)」으로 정정. 코드 의미는
  변경하지 않았다(현재 production 소비자 없음, `app/pipeline.py`가 값을 버리고
  `PipelineCancelled()`만 냄).
- **LOW-5(`_record_window_outcome` 분리 사유 재현 안 됨)** — 아래 D-5E3-5 행과 「알려진
  제한」에서 실측대로 정정(추출은 래칫상 필수가 아니었다 — 순수 이동으로 재분류).
- **LOW-2(acceptance 목록이 CI job 세 step 누락)** — 팀장이 scope 갱신 (3)에서
  acceptance_commands 에 S-1b·S-7 양성 대조·S-9 를 추가(commands.md 에 최종 HEAD
  실측 반영).
- **LOW-3(S-10 기록 낡음)** — 아래 「알려진 제한」에 등재.
- **LOW-4(5E-1 test docstring이 제거된 래퍼를 설명)** — 아래 「알려진 제한」에 등재
  (파일은 D-5E3-2 무편집 요구에 따라 편집하지 않는다).
- **code-reviewer LOW(세 로더의 죽은 `except yaml.YAMLError` 절·낡은 docstring)** —
  아래 「알려진 제한」에 등재(scope out_of_scope 「제거는 값 없음」 유지).

**새 파일 ↔ in_scope 대조(수정 라운드 1)** — 신규 파일 0. `git diff --name-status
cca51ab..HEAD`(계약 갱신 (3) 이후)는 `ml-engine/pyproject.toml`·`ml-engine/tests/
gates/test_policy_loaders_fail_closed.py` 둘 다 기존 파일 수정(M)이고, 둘 다
scope.md in_scope 에 이미 등재돼 있다(`pyproject.toml`은 계약 갱신 (3)으로 추가).

**(2b) 갱신** — 이번 라운드가 새 public 표면을 만들었는가: 아니다.
`pyproject.toml`의 import-linter 계약은 Python 런타임 표면이 아니라 정적 lint
설정이고, `test_policy_loaders_fail_closed.py`의 재작성은 test 파일 내부 함수
(`_load_prefixed_functions`·`_enumerate_load_functions_independently`·`_is_root`)
뿐이라 `ml_engine` 패키지 바깥에서 관측 가능한 표면이 아니다.

## D-5E3-1~6 이행

| ID | 이행 |
| --- | --- |
| D-5E3-1 | `registry/policy.py::load_policy`가 `yaml.safe_load` 호출을 `try/except yaml.YAMLError`로 감싸 `PolicyError`를 던진다(시그니처·반환 타입 무변경) — `tests/registry/test_policy.py`(문법 오류 세 형태: 미닫힌 flow sequence·탭 들여쓰기·미정의 anchor) |
| D-5E3-2 | `app/server.py::_load_inference_policy_safe` 래퍼 제거, `_preload`가 `load_inference_policy`를 직접 호출. `inference/policy.py`는 docstring만 갱신(코드 무변경) — `tests/app/test_server.py`의 malformed 4 case parametrize test 는 수정 없이 그대로 통과(diff 로 확인, 무편집) |
| D-5E3-3 | `training/holdout.py::run_holdout(..., should_stop: Callable[[], bool] = 항상 거짓)` — `_evaluate_windows`가 `enumerate(plan_selected)`의 인덱스를 완료 창 수로 삼아 창마다 학습(`evaluate_one_window`) 전에 확인, 참이면 `HoldoutCancelled(completed_windows=인덱스)`를 즉시 반환(부분 outcome 조립 없음) — `tests/training/test_holdout.py` 3 case(기본 인자 하위 호환·2창 뒤 취소·0창째 취소) |
| D-5E3-4 | `app/pipeline.py::_run_holdout`가 job 취소 신호 객체(5E-1 도입, `is_cancelled()`를 갖는 기존 타입)의 확인 메서드를 `should_stop=`으로 넘기고, `HoldoutCancelled`를 `PipelineFailed`가 아니라 `PipelineCancelled`로만 옮긴다 — `tests/app/test_pipeline.py` 2 case(배선 확인·매핑 확인) |
| D-5E3-5 | `holdout.py` 편집은 추가만(새 키워드 인자·새 결과 타입·루프 앞 확인 한 줄) — 산식·창 계획·보고서 조립 함수(`_assemble_report`·`_unaccounted_row_count`·`_unaccounted_or_reject`) 무변경, 5C-2 기존 test 22 case 전부 무편집 통과(`git diff` 로 확인 — 새 test 3 개만 추가, 기존 함수 시그니처 diff 없음). **verifier r1 LOW-5 정정** — `_record_window_outcome` 분리는 design ratchet(함수 50줄) 통과에 **필수가 아니었다**: 이 헬퍼를 인라인으로 되돌린 사본을 실측하면 정확히 50줄이고 판정식이 `> 50`이라 그 사본도 래칫을 통과한다(수정 라운드 1 실측, 원래 착수 커밋의 긴 docstring을 짧게 줄인 것만으로 50줄에 닿았다). 분리는 산식·분기·순서·호출 인자가 원본과 동일한 **예방적 순수 이동**(창 결과 분류 블록을 그대로 옮긴 것 — 앞으로 이 함수에 줄이 더 붙어도 여유를 유지하려는 목적, 필수 처방은 아니었다) |
| D-5E3-6(계약 갱신 (3), 수정 라운드 1) | `ml-engine/pyproject.toml` import-linter forbidden 계약 — `ml_engine` 전체에서 `yaml` import 금지. **계약 (4) 정정(verifier r2 LOW-1)** — `ignore_imports`는 `ignore_imports = ["ml_engine.registry.policy -> yaml"]` 한 줄이 아니라 **넷**이다: 뿌리(`registry.policy`, 실제 파싱) + 자기 `except yaml.YAMLError` 절을 가진 로더 셋(`training`·`evaluation`·`serving`의 `policy.py`, out_of_scope 「제거는 값 없음」이 그 절과 그 절이 요구하는 import 를 강제한다). scope.md 도 같은 문구로 계약 (4)에서 정정됐다(두 문서가 같은 말을 한다). 게이트 test 수집을 이름 규약(모듈에 정의된 `load_` 접두, 뿌리만 이름 제외)으로 재작성 — 반환 타입 주석을 보지 않는다. 텍스트 스캔 test는 삭제(import-linter가 대체) |
| D-5E3-6 ④(계약 갱신 (4), verifier r2 MEDIUM-1) | 예외 셋 ∩ `load_` 아닌 이름의 교집합 — 예외 모듈에 `load_` 접두가 아닌 이름(예: `read_training_policy_v2`)으로 `yaml.safe_load`를 직접 부르면 게이트도 import 계약도 못 잡는다(수정 라운드 1 실측). `tests/gates/test_policy_loaders_fail_closed.py::test_exception_modules_reference_only_yaml_yamlerror` 신설 — `pyproject.toml`을 **직접 읽어**(손 목록 아님) `ignore_imports`의 `-> yaml` 소스 모듈(뿌리 제외)을 뽑고, 그 각각을 AST 로 전수 스캔해 `yaml` 참조가 `YAMLError` 속성 하나뿐(호출 0·from-import 0)임을 이름 규약과 무관하게 단언한다 |

## 우회 (1)~(5) ↔ test 대응표

| # | 우회 | 대응 test |
| --- | --- | --- |
| (1) | 새 로더가 `load_policy`를 안 쓰고 `yaml.safe_load`/`yaml.load`를 직접 호출 | **수정 라운드 1(D-5E3-6) — `ml-engine/pyproject.toml`의 import-linter forbidden 계약**(`ml_engine` 전체에서 `yaml` import 금지, `registry.policy`만 예외) — S-4(`lint-imports`)가 CI 에서 강제한다. 정규식 소스 스캔(수정 전 대응 test)은 별칭 import·`from yaml import`·`full_load`류 형제 진입점·`getattr` 간접 호출을 전부 통과시켰다(verifier r1 실측) — import-linter 는 이 넷을 AST 의 import 문 자체로 잡아 호출부 스타일과 무관하게 닫는다. 정규식 test 는 삭제(두 겹 유지는 값 없음, D-5E3-6 ③) |
| (2) | `should_stop`을 안 넘기는 호출자가 있어 취소가 전달되지 않음 | `tests/app/test_pipeline.py`의 배선 확인 test(파일명 첫 절, commands.md 참고 — 같은 취소 신호 객체의 `is_cancelled`가 `should_stop`으로 전달됨을 직접 확인) + `tests/training/test_holdout.py::test_run_holdout_default_should_stop_never_cancels`(기본값 하위 호환) |
| (3) | 창 루프의 취소 확인을 학습 **뒤**로 옮기는 변이 | `tests/training/test_holdout.py::test_run_holdout_should_stop_halts_before_training_next_window` — mutation 대조 실측(commands.md 「RED 확인」): 확인을 학습 뒤로 옮긴 사본에서 `completed_windows`가 기대보다 1 많아져 이 test 가 실제로 붉어짐을 확인했다 |
| (4) | `HoldoutCancelled`를 `PipelineFailed`로 매핑해 FAILED 로 위장 | `tests/app/test_pipeline.py::test_holdout_cancelled_mid_windows_maps_to_pipeline_cancelled_with_no_artifact_files`(`PipelineCancelled` 타입 고정 단언 + artifact 파일 0) |
| (5) | 게이트 test 의 로더 열거가 손 목록이라 새 로더 누락 | **수정 라운드 1(D-5E3-6) 재작성** — `_iter_fail_closed_loaders`가 `pkgutil.walk_packages`로 `ml_engine` 아래 `policy` 서브모듈을 전수 순회하고, **이름 규약 하나만**(모듈에 정의된 `load_` 접두 함수, 뿌리만 이름으로 명시 제외)으로 대상을 고른다 — 반환 타입 주석은 더는 보지 않는다(verifier r1 HIGH-1 이 반환 주석을 타입 별칭으로 적은 다섯째 로더로 그 필터를 우회했다). `test_collected_loader_set_equals_independent_enumeration`이 다른 내부 경로(`inspect` vs `vars()`+`callable`)로 다시 계산한 집합과 일치하는지 대조해 숨은 이차 필터 재도입을 구조적으로 막는다. `test_every_non_root_policy_module_has_at_least_one_loader`가 하한 `>= 4`(로더가 늘어도 안 보이던 값) 대신 「정책 모듈마다 로더 ≥1」을 고정하고, `test_root_loader_itself_is_not_collected_as_fail_closed`가 뿌리 제외를 확인한다. verifier의 재현 probe(반환 타입 별칭 + `yaml` 별칭 import)로 새 test 들이 실제로 그 로더를 수집해 예외 누출을 잡는 것을 확인했다(commands.md 「우회 대응표 갱신 실측」) |
| (1)∩(5) | **승인 전 일괄(verifier r2 MEDIUM-1)** — 예외 셋(로더 (1))과 `load_` 이름 규약(로더 (5)) 의 교집합: 예외 모듈에 `load_` 아닌 이름으로 `yaml.safe_load`를 직접 부르면 둘 다 통과한다 | `test_exception_modules_reference_only_yaml_yamlerror`(D-5E3-6 ④) — 예외 세 모듈을 AST 전수 스캔해 `yaml` 참조가 `YAMLError` 뿐임을 이름 규약과 무관하게 단언. 변이 둘(`safe_load` 호출 추가·verifier의 `read_training_policy_v2` probe 그대로) 모두 실제로 실패시킴을 확인했다(commands.md). **잔여: MEDIUM-2**(verifier r3, 아래 「알려진 제한」) |
| (경계) | **동적 import**(`importlib.import_module("yaml").safe_load(...)`) — import 문이 아니라 호출이라 import-linter 계약이 보지 못한다(verifier r2 LOW-2) | 방어 대상 밖으로 scope.md 위협 모델 「방어하지 않는다」에 등재(계약 (4)) — 정책 로더로는 평범한 스타일이 아니고, `policy` 모듈 + `load_` 접두라면 이름 규약 게이트가 여전히 잡는다 |

## (2b) 값 획득 축 — 실측(commands.md 「(2b)」 절)

| 표면 | 허락하는 것 | 판정 |
| --- | --- | --- |
| `run_holdout(should_stop=)` 키워드 인자 | 호출자가 중단 시점을 정한다 | 닫는다 — production 호출자 `app/pipeline.py::_run_holdout` 하나(`grep -rn "run_holdout(" ml-engine/src/ml_engine/` 실측) |
| `training.HoldoutCancelled` | 읽기 | 닫는다 — `app/pipeline.py`(isinstance 분기)·`training/__init__.py`(재수출) 둘뿐, 판정 로직을 소비하는 다른 모듈 없음(`grep -rln` 실측) |
| `registry.load_policy` 예외 타입 변경 | 호출자가 잡을 예외가 하나로 준다 | 닫는다 — 소비 로더 넷(`serving`·`training`·`inference`·`evaluation`)이 이미 `PolicyError`를 잡고 있었고(변경 전부터), 이 slice 가 그 잡는 대상의 실제 발생 조건(YAML 문법 오류)을 넓혔을 뿐 새 예외 타입을 노출하지 않았다. `tests/gates/test_policy_loaders_fail_closed.py`가 이 집합을 손 목록 없이 매 실행마다 재확인(우회 (5)와 같은 게이트) |

## 새 public 표면과 그것이 밖에 허락하는 것

| 표면 | 허락하는 것 |
| --- | --- |
| `ml_engine.training.holdout.HoldoutCancelled`(+ `training.HoldoutCancelled` 재수출) | 결과 타입(읽기 전용 데이터, `completed_windows: int`) — 생성은 `_evaluate_windows` 내부뿐 |
| `run_holdout(..., *, should_stop: Callable[[], bool] = 항상 거짓)` | 새 키워드 전용 인자 — 기본값이 항상 거짓이라 기존 호출자·test 의 동작을 바꾸지 않는다(하위 호환, `test_run_holdout_default_should_stop_never_cancels`가 고정) |
| `_ConcreteTrainingPipeline._run_holdout(dataset, ...)` | 비공개(모듈 내부, leading underscore) — 시그니처에 취소 신호 객체 인자 추가는 이 클래스 밖에서 호출되지 않는다(`run()`·`_evaluate_and_finalize`만 호출) |

`registry.policy.load_policy`·`app/server.py::_preload`·`inference/policy.py::load_
inference_policy`는 시그니처·반환 타입 무변경(공개 표면 증가 0) — `load_policy`가
던지는 예외의 **발생 조건**만 넓어졌다(문법 오류도 이제 `PolicyError`).

## 알려진 제한

1. **창 하나 안의 LightGBM 학습 자체는 중단하지 않는다**(scope.md 위협 모델 「방어하지
   않는다」절, D-5E3-3 문서화) — `should_stop`은 창 경계에서만 확인한다. 취소된 job 이
   진행 중이던 창의 학습을 끝까지 마친 뒤에야 멈춘다(1 창 분의 계산 시간 지연).
2. **`training.jobs.runner`/`servicer` 레벨의 CANCELLED 상태 전이는 이 slice 가
   새로 테스트하지 않았다** — `tests/training/test_jobs_runner.py`·`tests/training/
   test_jobs_servicer.py`는 가짜 파이프라인이 직접 `PipelineCancelled()`를 반환하는
   방식으로 이미 이 전이를 검증하고 있고(`_CancelledPipeline`류), 이 slice 의 변경은
   `_ConcreteTrainingPipeline`이 **언제** `PipelineCancelled`를 내는지에만 있다 —
   그 값을 받은 뒤의 job 상태 전이 로직(`training/jobs/state.py` 전이표)은 무편집이라
   기존 test 가 그대로 보증한다. 별도 servicer 통합 test 를 추가하지 않았다(중복
   검증 방지 — evidence-pack 「출력을 두 번 재는 하네스를 만들지 않는다」와 같은 결).
3. **`_evaluate_windows`가 `_record_window_outcome` 헬퍼로 분리됐다** — 순수 이동이라
   동작 변화는 없지만, `holdout.py`가 D-5E3-5 문면(「추가만」)과 문자 그대로 일치하지는
   않는다. 5C-2 golden·mutation test 무편집 통과(commands.md S-5·checklist D-5E3-5)가
   동작 불변의 증거다. **verifier r1 LOW-5 정정** — 분리는 design ratchet 통과에
   필수가 아니었다(D-5E3-5 행 참고, 인라인 사본이 정확히 50줄로 래칫을 통과한다) —
   예방적 순수 이동으로 재분류.
4. **`training.jobs.runner`/`servicer` 레벨 CANCELLED 전이 재확인은 이 slice 가
   새로 테스트하지 않는다**(위 2 항목 참고) — verifier r1 표적 6 이 별도로
   `_training_pipeline_factory`가 `Protocol`을 만족시키는 것(`mypy --strict`)과
   `tests/training/test_jobs_runner.py`가 같은 `Protocol`을 만족하는 가짜
   파이프라인으로 `PipelineCancelled` → `JobState.CANCELLED`를 이미 검증하고
   있음을 확인해, 이 이음매가 타입으로 닫혀 있다고 판정했다(verifier r1 표적 6).
5. **evidence 커밋이 늘 때마다 `commands.md`의 S-10 기록이 그 시점만큼 낡는다**
   (verifier r1 LOW-3) — 마지막 evidence 편집 커밋 자신은 그 뒤의 자기 상태를
   기록할 수 없는 구조적 한계다(evidence 편집이 스스로를 검증하지 못하는 축).
   이 라운드도 verifier가 직접 HEAD `51b70a7`에서 재실행해 exit 0 을 확인했다
   (verifier r1 판정문 LOW-3) — 다음 라운드가 있으면 그 라운드의 verifier/reviewer
   가 최종 evidence 커밋의 HEAD 를 다시 확인하는 것으로 이 축을 닫는다.
6. **`tests/app/test_server.py::test_preload_malformed_yaml_in_any_of_four_policies_
   does_not_raise`의 docstring이 이미 제거된 호출부 정규화(`_load_inference_policy_
   safe`)를 아직 설명한다**(verifier r1 LOW-4) — D-5E3-2 가 이 파일의 **무편집**을
   증거로 요구하므로(수정 없이 그대로 초록이어야 검증이 성립) 계약의 결과이지
   구현의 실수가 아니다. 이 파일을 다음에 만질 기회에 문구를 갱신한다.
7. **세 소비 로더(`training`·`evaluation`·`serving`의 `policy.py`)의
   `except (PolicyError, OSError, yaml.YAMLError)` 절이 이제 도달 불가능한 죽은
   분기다**(code-reviewer LOW) — 뿌리(`registry.policy.load_policy`)가 `yaml.
   YAMLError`를 절대 통과시키지 않으므로 이 절의 `yaml.YAMLError` 브랜치는 실행되지
   않는다. 관련 docstring(예: `training/policy.py`의 「5A `load_policy`는 ... 잡지
   않고 그대로 전파한다」)도 이제 사실과 어긋난다. scope.md out_of_scope(「기존 절을
   남겨 둔다 — 제거는 값 없음」)에 따라 이번 라운드에서 고치지 않는다 — 이 세 파일을
   다음에 만질 기회에 죽은 분기와 docstring 을 함께 정리하는 것을 권한다.
8. **예외 모듈 안 `yaml` 이중 별칭 import 는 신설 AST test 를 우회한다**(verifier r3
   MEDIUM-2, 미차단) — `test_exception_modules_reference_only_yaml_yamlerror`가
   `node.value.id == "yaml"`로 판별하므로, 이미 `import yaml`이 있는 예외 모듈에
   `import yaml as _y`를 한 번 더 두고 `load_` 아닌 이름으로 `_y.safe_load(...)`를
   부르면 이름이 다른 별칭이라 검사가 놓치고 raw 파서 예외가 샌다(ruff·mypy·
   `lint-imports`·design ratchet 전부 통과, verifier r3 실측). 미차단 사유 —
   평범한 저자는 그 파일에 이미 있는 `yaml.safe_load`를 쓰고 그러면 잡힌다(표적
   1(b)(c)). 형제 형태 `getattr(yaml, "safe_load")(...)`는 ruff `B009`가 별도로
   잡는다. **구조적 해소는 세 로더의 죽은 `except yaml.YAMLError` 절 제거**다 —
   그러면 `ignore_imports`가 뿌리 한 줄로 수렴하고 이 AST 검사의 대상·MEDIUM-2 의
   도달 경로가 함께 사라진다(out_of_scope 유지 — 다음에 그 세 파일을 만지는
   slice 의 후속 소폭으로 남긴다). 대증 처방(`_yaml_reference_summary`가 이름
   `yaml` 대신 `ast.Import`의 바인딩 이름 집합 전체를 재는 것)은 운영자가 이번
   PR에서 원하면 한 줄로 가능하다.

## OPEN 처분

| OPEN | 처리 |
| --- | --- |
| `OPEN-5E-YAML-LOADER-INFERENCE`·`OPEN-5C-YAML-ERROR-5D` | **종결**(D-5E3-1) |
| `OPEN-5E-CANCEL-GRANULARITY` | **종결**(창 단위, D-5E3-3·4) — 창 안 중단은 위 알려진 제한 1 |
