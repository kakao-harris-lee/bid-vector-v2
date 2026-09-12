"""`ml_engine.features.vocabulary` — 정렬 어휘 + 위치 부호화(신규, D-5B-2·D-5B-3).

`Vocabulary`는 legacy `AwardRateFeatureSpace.categories`/`denominator_sources`(정렬 tuple)와
`_vocabulary_code`(어휘 위치 조회, 실패 시 미지 코드)의 계약을 그대로 잇되, 미지 코드
`sentinel -1.0`을 없애고 결과 타입 `OutOfVocabulary`로 바꿨다(D-5B-3 — sentinel 폐기).

`denominator_source_vocabulary`는 신규다 — legacy `ReliableBaseSource`(4값, 손으로 나열한
Python enum)를 이식하지 않고, wire `BaseAmountProvenanceLabel`(5값)에서 어휘를 **직접
도출**한다(D-5B-2). 리터럴로 나열하지 않는 이유: 어휘를 손으로 베끼면 계약(wire enum)이
바뀌어도 어휘가 조용히 낡을 수 있다 — enum에서 읽으면 어긋날 수 없다.
"""

from __future__ import annotations

from dataclasses import dataclass

from ml_engine.contracts import common_pb2

_DENOMINATOR_SOURCE_PREFIX = "BASE_AMOUNT_PROVENANCE_LABEL_"
_DENOMINATOR_SOURCE_UNSPECIFIED_NAME = "BASE_AMOUNT_PROVENANCE_LABEL_UNSPECIFIED"


@dataclass(frozen=True)
class OutOfVocabulary:
    """어휘 밖 키 — sentinel 코드(legacy `-1.0`)를 대신하는 결과 타입."""

    key: str


@dataclass(frozen=True)
class Vocabulary:
    """정렬·중복 없음 불변식을 갖는 범주 어휘. 코드는 어휘 안의 **위치**다."""

    values: tuple[str, ...]

    def __post_init__(self) -> None:
        if list(self.values) != sorted(self.values):
            raise ValueError(f"Vocabulary 는 정렬돼야 합니다: {self.values!r}")
        if len(set(self.values)) != len(self.values):
            raise ValueError(f"Vocabulary 에 중복이 있습니다: {self.values!r}")

    def code_of(self, key: str) -> int | OutOfVocabulary:
        """어휘 위치를 코드로 — 없으면 `OutOfVocabulary`(sentinel 아님)."""
        try:
            return self.values.index(key)
        except ValueError:
            return OutOfVocabulary(key)


def denominator_source_label_name(value: int) -> str:
    """wire `BaseAmountProvenanceLabel` 정수값 → 짧은 라벨 이름(예: `CLEAN`, prefix 제거)."""
    name: str = common_pb2.BaseAmountProvenanceLabel.Name(value)
    return name[len(_DENOMINATOR_SOURCE_PREFIX) :]


def denominator_source_vocabulary() -> Vocabulary:
    """wire `BaseAmountProvenanceLabel` 5값(UNSPECIFIED 제외)에서 어휘를 도출한다(D-5B-2)."""
    # `EnumTypeWrapper`는 dict 가 아니라 `__iter__`가 없다(실측) — `.keys()`를 지우면
    # `TypeError: 'EnumTypeWrapper' object is not iterable`(SIM118 오탐, 아래 noqa 근거).
    names = sorted(
        name[len(_DENOMINATOR_SOURCE_PREFIX) :]
        for name in common_pb2.BaseAmountProvenanceLabel.keys()  # noqa: SIM118
        if name != _DENOMINATOR_SOURCE_UNSPECIFIED_NAME
    )
    return Vocabulary(tuple(names))
