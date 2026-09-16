# M5/5E-2 — reuse.md

**이식 없음.** `tools/reuse_provenance_check.py`가 대상으로 삼는 `Reuse: <원본 경로>@
<commit>` docstring 을 가진 신규 파일이 이 slice 에 없다(`serving/wire.py`·
`serving/runtime.py` 둘 다 새로 작성 — 아래 재작성 판정 근거).

legacy bid-vector 저장소(FastAPI+Celery)에는 gRPC wire 매핑 층 자체가 없다 — 응답을
FastAPI response model(pydantic)로 직접 구성했고, `ModelRelease`·`Weight`·`Diagnostics`
같은 구조화 메시지 개념이 없었다(조사 01 (b-3)(b-4), 자유 형식 dict·문자열 하나로
접었다). 따라서 `KernelResult → CalculateOptimalBidResponse` 매핑(`wire.py`)과
`ModelRelease` 런타임 조립(`runtime.py`)은 대체할 원본이 legacy 에 없다(a 형 — 재작성
목록 (a) 「기존 코드가 새 경계를 위반」이 아니라 애초에 대상 부재, 5E-1 reuse.md 와
같은 판정 계보).

## 재사용한 것 (이 저장소 자신의 기존 산출물 — 이식 표 대상 아님)

- `evaluation/policy.py::policy_checksum`(5C-2)의 canonical JSON 규칙(키 정렬·구분자
  `(",", ":")`·`allow_nan=False`) — `runtime.py::derived_release_checksum`이 같은
  규칙을 적용한다. **손 목록 대신 `dataclasses.fields` 기계 열거**로 한 단계 더
  나아갔다(D-5E2-4, `InferencePolicy` 필드가 늘어나도 이 함수가 낡지 않는다) — 5C-2 의
  것은 `EvaluationPolicy` 필드를 손으로 나열한다. 공용 헬퍼로 뽑지는 않았다(팀장 지시
  — layers 규칙 안에서 `features`·`contracts` 밖 공용 자리가 없으면 serving 에 두고
  보고: `derived_release_checksum`은 `serving` 층 전용이고 `evaluation`·`serving`
  사이에 공유 가능한 layer 가 없다 — `evaluation`은 `serving`보다 아래 층이 아니라
  `features`·`contracts`와만 의존하는 별도 층이라 `serving`이 `evaluation`을 import 할
  수 없다(import-linter forbidden 계약). 중복 규칙이지만 계층 경계가 그 중복을
  요구한다).
- 5E-1 `serving/status.py::envelope_violation`·`fill_application_failure`·
  `serving/embedding.py::EmbeddingServicer._validate`의 「검증 함수가 순서를 소유,
  servicer 는 결과만 본다」 관례 — `prediction.py::_validate`가 같은 형태를 따른다
  (새 코드지만 기존 패턴 재사용, 표 대상 아님).
- `adapters/ml/{ReleaseShapeValidation,ReleaseCheck,ParsedSuccessFields,FractionRules,
  CandidateShapeValidation}.kt`(Kotlin, 다른 언어라 이식 대상이 아니다) — 규칙을
  **미러**한다(D-5E2-7, `tests/serving/test_kotlin_rules_parity.py`). 정본은 그
  Kotlin 코드이고, Python 은 그것을 옮겨 적을 뿐 소유하지 않는다.

## 재작성 판정 근거(재활용 우선 원칙 (a)/(b) 중 어느 쪽인가)

(a)형 — 대체할 원본이 legacy 에 없다. legacy 는 gRPC 서버도, `ModelRelease`/
`Weight`/`Diagnostics` 구조화 계약도, decimal 정규형 왕복 규칙도 갖지 않았다(pydantic
response model 하나로 직렬화를 맡겼다). (b)형(기존 코드 결함으로 인한 재작성)에
해당하는 부분은 이 slice 에 없다 — 5D-2/5D-3 의 `ml_engine.inference.*`(엔진 자체)는
소비만 하고 편집하지 않았다(out_of_scope).
