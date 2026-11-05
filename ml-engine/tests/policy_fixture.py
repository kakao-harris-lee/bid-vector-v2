"""test 용 평가 정책 조립 — **파일을 거쳐서** 만든다(M9/ml-lifecycle 수정 라운드, v H-1).

`EvaluationPolicy` 는 이제 모듈 private 생성 토큰 없이 만들 수 없고, 게이트 실행
(`run_holdout`)은 정책이 자기가 왔다는 파일과 **같은 값**인지 다시 확인한다. 그래서 test 도
production 과 같은 길로 정책을 얻는다 — 임시 YAML 을 쓰고 `load_evaluation_policy` 로 읽는다.

자리는 하나다. 앞 판은 여섯 test 파일이 각자 `EvaluationPolicy(…)` 를 직접 조립했고, 정책에
칸이 하나 늘 때마다 여섯 자리를 함께 고쳐야 했다(이 라운드에 실제로 두 번 그랬다). 이제
칸이 늘면 이 파일의 표 하나만 고친다.

**임시 파일은 세션 내내 남는다**(`mkdtemp`) — `run_holdout` 이 실행 중 그 파일을 다시 읽기
때문에 `tmp_path`(함수 스코프)로는 모자란다.
"""

from __future__ import annotations

import hashlib
import tempfile
from collections.abc import Mapping, Sequence
from datetime import date
from pathlib import Path
from typing import Final

from ml_engine.evaluation.policy import (
    EvaluationPolicy,
    PolicySource,
    _ParsedPolicyFile,
    load_evaluation_policy,
)

TEST_POLICY_EFFECTIVE_DATE: Final[date] = date(2020, 1, 1)
"""test 정책의 effective date — test 가 쓰는 어떤 창보다 앞선 날짜다. 창이 효력 시작 뒤에
있어야 승격 인용이 성립하므로 여기서는 인용 축을 **상수로 비켜 두고**, 그 축 자체는
`tests/evaluation/test_epoch.py` 와 `tests/training/test_holdout.py` 의 전용 test 가 잰다."""

_DEFAULTS: Final[Mapping[str, object]] = {
    "version": "test",
    "effective_date": TEST_POLICY_EFFECTIVE_DATE,
    "paired_t_threshold": 2.58,
    "gate_baseline": "category_x_band",
    "gate_model": "gbm_all_strata",
    "gate_stratum": "clean-base",
    "maturity_threshold": 0.70,
    "min_evaluation_rows": 100,
    "max_origins": 5,
    "agency_baseline_min_count": 10,
    "stability_seeds": (20260812, 1, 7, 42, 2026),
    "amount_band_edges": (1e8, 5e8, 1e9, 5e9),
    "segment_axes": ("category", "amount_band"),
}

_INDEXED_KEYS: Final[tuple[str, ...]] = (
    "stability_seeds",
    "amount_band_edges",
    "segment_axes",
)

_DIRECTORY: Final[Path] = Path(tempfile.mkdtemp(prefix="ml-engine-test-policy-"))


def _yaml_scalar(value: object) -> str:
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, date):
        return f'"{value.isoformat()}"'
    if isinstance(value, str):
        return f'"{value}"'
    return repr(value)


def policy_yaml_text(**overrides: object) -> str:
    """출하 정책과 같은 평탄 키 꼴의 YAML 문면. 목록 값은 `stability_seeds.0` 처럼 평탄
    인덱스 키로 펴진다(로더가 접두로 모아 tuple 로 조립한다)."""
    values = dict(_DEFAULTS) | overrides
    lines: list[str] = []
    for key, value in values.items():
        if key in _INDEXED_KEYS:
            items = value if isinstance(value, Sequence) else ()
            lines.extend(
                f"{key}.{index}: {_yaml_scalar(item)}"
                for index, item in enumerate(items)
            )
            continue
        lines.append(f"{key}: {_yaml_scalar(value)}")
    return "\n".join(lines) + "\n"


def evaluation_policy(**overrides: object) -> EvaluationPolicy:
    """임시 YAML 을 쓰고 로더로 읽어 돌려준다. 로드가 거부되면 **그 자리에서** 터뜨린다 —
    test 가 「정책이 없다」를 모른 채 지나가지 않게."""
    text = policy_yaml_text(**overrides)
    path = _DIRECTORY / f"evaluation-{abs(hash(text)):016x}.yaml"
    path.write_text(text, encoding="utf-8")
    loaded = load_evaluation_policy(path)
    if not isinstance(loaded, EvaluationPolicy):
        raise AssertionError(f"test 정책 로드 실패: {loaded} / {text}")
    return loaded


def kernel_policy(**overrides: object) -> EvaluationPolicy:
    """커널 test 전용 — 모듈 private 생성 토큰으로 **직접** 조립한다.

    `evaluation_policy` 와 갈라지는 이유는 로더가 승인 전수를 요구하기 때문이다: seed 키는
    승인된 다섯이어야 하고(`APPROVED_SEED_KEYS`), 그래서 「seed 를 재배열한 정책」·「seed
    하나뿐인 정책」 같은 **커널 거동을 재는 조합**을 파일로는 만들 수 없다. 그 test 들이
    재는 것을 잃지 않도록 여기서 토큰으로 짓는다.

    출처 재검증(`policy_source.verify_policy_source`)은 **제품 경로**(`app.pipeline`)에만
    있으므로 이 정책으로 `run_holdout` 을 직접 부르는 커널 test 는 영향받지 않는다. 그
    경로를 타는 test 는 `evaluation_policy`(파일 경유)를 쓴다.

    `source` 는 실제로 쓴 파일을 가리킨다 — 지문이 바이트와 맞으므로 `__post_init__` 의
    토큰 등식이 성립하고, 승인 전수에 맞는 조합이면 재검증도 통과한다."""
    text = policy_yaml_text(**overrides)
    path = _DIRECTORY / f"kernel-{abs(hash(text)):016x}.yaml"
    path.write_text(text, encoding="utf-8")
    digest = hashlib.sha256(text.encode("utf-8")).hexdigest()
    values = dict(_DEFAULTS) | overrides
    return EvaluationPolicy(
        parsed=_ParsedPolicyFile(digest=digest),
        source=PolicySource(path=path, digest=digest),
        **values,  # type: ignore[arg-type]
    )
