# M5/5A — 재활용 출처 (ADR 0009 D-5 오른쪽 자리)

> 왼쪽 자리(모듈 옆 최소 포인터)는 각 모듈 docstring 첫 줄 `Reuse: <원본 경로>@<commit>`.
> 이 표는 그 이식이 **수행한 수정·튜닝 내역**을 담는다(D-6.1 #3). 조인 키는 `module`
> (저장소 루트 기준 POSIX 경로) — `ml-engine/tools/reuse_provenance_check.py`(S-7)가
> 이 표와 docstring 포인터를 대조한다.

| module | original_path | commit | 수행한 수정·튜닝 |
| --- | --- | --- | --- |
| ml-engine/tools/design_ratchet.py | bid-vector/scripts/_design_ratchet_scan.py | ed4b06c | 측정 알고리즘(함수 길이·파일 LOC 밴드·`dict`/`Any` 약한 경계 판정, `is_weak_annotation`)만 그대로 가져왔다. `bid-vector/scripts/design_ratchet.py`(CLI·baseline 영속화)·`_design_ratchet_contracts.py`(pydantic `StrictModel` 데이터 계약)·`_design_ratchet_clones.py`(클론 탐지)는 가져오지 않았다 — (1) **baseline allowance 모델(증가만 차단)을 버리고 위반 0 + 명시 allowlist(D-5A-3)로 교체** — ml-engine 시점엔 감축할 부채가 없어 baseline 자체가 불필요, (2) `json_direct_calls`·`env_test_sniff`·`unvalidated_dict_tasks`(Celery)·클론 탐지 지표는 ml-engine 에 해당 정책·의존이 없어 드롭(함수 50/파일 500/`dict[str,Any]` 경계 0 만 승인 대상, ML-11.2), (3) `pydantic.StrictModel` 대신 `dataclasses.dataclass(frozen=True)`(ml-engine 은 pydantic 을 의존하지 않는다), (4) 스캔 대상을 `app`/`scripts`에서 `src/ml_engine`/`tools`로 교체. |
