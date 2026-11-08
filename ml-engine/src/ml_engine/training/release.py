"""`ml_engine.training.release` — 결정적 release 식별(D-5C-10). UUID 를 쓰지 않는다 —
같은 입력의 두 학습이 같은 id 를 가져야 재현성 test(⑩)가 바이트 동일을 잴 수 있다.

**`release_id` 가 ML-05 의 model_version 축이다**(M9/ml-lifecycle). 「학습 표본 정의가
바뀌면 model_version 이 올라간다」(ML-05 acceptance)의 이행이고, 그래서 선언 입력에
**학습 표본 범위(`sample_scope`)가 들어간다** — 앞 판은 다섯뿐이어서 같은 데이터셋·spec·
seed·code 로 범위만 다른 두 아티팩트가 같은 식별자를 가질 수 있었다(manifest 에는
`sample_scope` 가 있으니 내용은 달랐다). V2 의 식별자는 순서 있는 번호가 아니라 결정적
해시이므로 그 acceptance 의 「올라간다」는 「달라진다」로 읽는다(재현성 — 바로 아래 문단).

`ReleaseIdentity`는 2B `ModelRelease`(다섯 성분: release_id·artifact_checksum·
feature_schema_version·code_version·dataset_id)에서 **`artifact_checksum`을 뺀 넷만**
갖는다(D-5C-9 — 아티팩트 바이트 안에 자기 checksum 을 넣지 않는다. checksum 은
`ArtifactBytes.sha256`이 낸다, `artifact_writer.py`).

H-1 — 이 모듈은 더 이상 호출자가 넘기는 `ReleaseInputs` 를 받지 않는다.
`derive_release_id`의 여섯 입력은 전부 `TrainedArtifact`(호출부는 `artifact_writer.py`)에서만
읽힌다 — 계약 우회 후보 (12)(「`release.dataset_id` 를 요청과 다르게」)가 시그니처 차원에서
성립하지 않는다."""

from __future__ import annotations

import hashlib
from dataclasses import dataclass


@dataclass(frozen=True)
class ReleaseIdentity:
    """2B `ModelRelease`에서 `artifact_checksum`을 뺀 넷(D-5C-9)."""

    release_id: str
    feature_schema_version: str
    code_version: str
    dataset_id: str


def derive_release_id(
    *,
    dataset_id: str,
    training_spec_version: str,
    training_spec_checksum: str,
    seed: int,
    code_version: str,
    sample_scope: str,
) -> str:
    """sha256 앞 16 hex — **여섯 입력 중 하나만 바뀌어도** 다른 id(결정적, D-5C-10). 값
    자체의 유효성(비어있음 등)은 각 값의 원 출처 타입이 이미 강제한다 — `dataset_id`는
    `DatasetManifestV1.__post_init__`, `code_version`은 `CodeVersion.__post_init__`,
    `sample_scope`도 같은 manifest 가 빈 문자열을 거부하고(기본값 금지, legacy
    `artifact_contracts.py` 와 같은 근거) `training_spec_version`/`training_spec_checksum`은
    유효한 `TrainingSpec`에서만 나온다.

    `sample_scope`가 입력인 이유는 그것이 **피처 공간과 직교하는 축**이기 때문이다 —
    같은 데이터셋·spec·seed·code 로 범위만 다르게 학습한 두 아티팩트는 다른 모델이고,
    같은 식별자를 가지면 미학습 공종 가드가 조용히 열린다. 입력 전수는
    `tests/training/test_release_identity.py` 가 **시그니처에서 모집단을 만들어** 민감도를
    재므로, 입력을 더하고 아래 payload 에 넣지 않으면 그 test 가 붉어진다."""
    payload = "\0".join(
        (
            dataset_id,
            training_spec_version,
            training_spec_checksum,
            str(seed),
            code_version,
            sample_scope,
        )
    )
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()[:16]
