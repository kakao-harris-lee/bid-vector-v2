# M5/5E-3 — reuse.md

**이식 없음.** `tools/reuse_provenance_check.py`가 대상으로 삼는 `Reuse: <원본 경로>@
<commit>` docstring 을 가진 신규 파일이 이 slice 에 없다(S-7 exit 0, 「재활용 출처
두 자리 일치 — 위반 0」).

이 slice 는 두 결정 불요 미결(`OPEN-5E-YAML-LOADER-INFERENCE`·`OPEN-5C-YAML-ERROR-5D`,
`OPEN-5E-CANCEL-GRANULARITY`)을 닫는 뿌리 처방·정밀도 보강이고, 두 항목 모두 legacy
bid-vector 저장소에 대응하는 코드가 없다(legacy 는 FastAPI+Celery 구조로 gRPC 서버·
정책 YAML 로더·창 단위 홀드아웃 취소 개념 자체가 없었다 — 5C-2/5E-1 reuse.md 와 같은
판정 계보, (a)형: 대체할 원본이 legacy 에 없다).

## 재사용한 것(이 저장소 자신의 기존 산출물 — 이식 표 대상 아님)

- `registry/policy.py::load_policy`(5A) 자체 — 시그니처·반환 타입 무변경, `yaml.
  safe_load` 호출을 `try/except`로 감싸기만 했다(D-5E3-1).
- `training/holdout.py::_evaluate_windows`의 기존 창 순회·`_exclusion_from_skip`
  호출 로직 — `should_stop` 확인 한 줄을 추가하고, design ratchet(함수 50줄) 준수를
  위해 창 결과 분류 블록을 `_record_window_outcome`으로 옮겼다(로직 자체는 그대로
  이동, 신규 판단 없음 — 5C-2 golden·mutation test 무편집 통과가 동작 불변의 증거).
- `training.jobs.pipeline.PipelineCancelled`(5E-1 도입) — `app/pipeline.py`가 이미
  갖고 있던 취소 결과 타입을 그대로 재사용해 `HoldoutCancelled`를 옮긴다(신규 결과
  타입을 만들지 않았다).
- `evaluation/windows.py::WeekMaturity`·`training/_holdout_fit.py::WindowSkip`
  등 5C-2 산출물의 형태는 무편집 — `should_stop`은 이 타입들 밖에서 순회 제어만
  한다.

## 재작성 판정 근거(재활용 우선 원칙 (a)/(b) 중 어느 쪽인가)

(a)형 — 대체할 원본이 legacy 에 없다. legacy 에는 정책 YAML 로더도, 주 단위 홀드아웃
평가도, 취소 가능한 job 실행기도 없었다(FastAPI 동기 엔드포인트 + Celery task 로
계산을 던지고 끝까지 돌렸다 — 조사 01, 5E-1 reuse.md 승계). 이 slice 가 만지는 다섯
파일은 전부 M5 자체 산출물(5A·5C-2·5E-1)의 뿌리 처방·정밀도 보강이다.
