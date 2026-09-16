"""`ml_engine.features.facts` — wire fact → 타입 fact 판정 진입점(신규, D-5B-3·D-5B-4).

legacy predictor는 `amount: float`·`category: str | None`·`denominator_source: str`를
검증 없이 받았다(조사 §c-3 「unit/basis validation 없음」). 이 모듈이 그 빈자리를 채운다 —
`FeatureFacts.from_proto`가 `FeatureInputs`의 fact 넷을 판정해 `Money` 단위·기준·provenance,
oneof 존재, `MissingReason.UNSPECIFIED`를 걸러낸 뒤에만 `FeatureFacts`를 낸다. 위반은 전부
`FactRejected`(결과 타입) — 예외 없음.

M5/5D-3(D-5D3-6, 계약 갱신 F-10) — 텍스트 fact 판독기(`resolve_text_fact`)를 공개
승격했다. 결측 사유 판정을 **술어**(`is_allowed_missing_reason: Callable[[int], bool]`)로
인자화해 요청 축(`FeatureFacts.from_proto`)과 표본 축(`ml_engine.inference.distribution`)이
**같은 코드**로 정규화·거부한다 — 분포 엔진 안에 두 번째 판독기를 두지 않는다.

**요청 축은 열린 집합이다** — `MISSING_REASON_UNSPECIFIED`만 거부하고 그 밖의 모든 정수
(enum 밖 값 포함)는 `Missing(raw)`로 수용한다(base·이전 동작 그대로). 열거로 닫으면
common.proto가 `MissingReason` 값을 늘릴 때마다 구버전 엔진이 신규 값을 담은 요청 전체를
거부하는 전방 호환 위반이 생긴다(F-10, verifier r1 표적 11 HIGH — 팀장이 「기존 집합
유지」 채택을 철회). **표본 축만 닫힌 집합**(`{NOT_COLLECTED_YET}` 하나)이다 — 2F 계약이
그 축의 허용 사유를 그 값 하나로 명시했다(D-5D3-2).
"""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from enum import StrEnum

from ml_engine.contracts import common_pb2, features_pb2
from ml_engine.features.normalize import normalize_feature_key
from ml_engine.features.vocabulary import denominator_source_label_name


@dataclass(frozen=True)
class Present[T]:
    """fact 가 값을 나른다."""

    value: T


@dataclass(frozen=True)
class Missing:
    """fact 가 결측 사유를 나른다(`common_pb2.MissingReason` 값, UNSPECIFIED 제외)."""

    reason: int


type FactValue[T] = Present[T] | Missing


class FactRejectionReason(StrEnum):
    MALFORMED = "MALFORMED"
    BASIS_MISMATCH = "BASIS_MISMATCH"
    NON_POSITIVE_AMOUNT = "NON_POSITIVE_AMOUNT"
    UNSPECIFIED_ENUM = "UNSPECIFIED_ENUM"
    EMPTY_KEY = "EMPTY_KEY"
    MISSING_REASON_NOT_ALLOWED = "MISSING_REASON_NOT_ALLOWED"


@dataclass(frozen=True)
class FactRejected:
    """fact 검증 실패 — `field`는 실패한 fact(또는 그 성분)의 이름."""

    reason: FactRejectionReason
    field: str


def _always_allowed(_raw: int) -> bool:
    """요청 축 기본 술어(F-10) — `UNSPECIFIED`는 `_resolve_missing_reason`이 그 사유
    확인보다 먼저 걸러내므로, 여기 도달한 값은 전부 수용한다(열린 집합)."""
    return True


def _resolve_missing_reason(
    raw: int,
    *,
    field: str,
    is_allowed_missing_reason: Callable[[int], bool] = _always_allowed,
) -> Missing | FactRejected:
    if raw == common_pb2.MISSING_REASON_UNSPECIFIED:
        return FactRejected(FactRejectionReason.MALFORMED, field)
    if not is_allowed_missing_reason(raw):
        return FactRejected(FactRejectionReason.MISSING_REASON_NOT_ALLOWED, field)
    return Missing(raw)


def _resolve_base_amount(
    fact: features_pb2.BaseAmountFact,
) -> FactValue[float] | FactRejected:
    which = fact.WhichOneof("fact")
    if which is None:
        return FactRejected(FactRejectionReason.MALFORMED, "base_amount")
    if which == "missing":
        return _resolve_missing_reason(fact.missing, field="base_amount")
    money = fact.value
    if money.basis != common_pb2.BASIS_BASE_AMOUNT:
        return FactRejected(FactRejectionReason.BASIS_MISMATCH, "base_amount.basis")
    if money.currency != common_pb2.CURRENCY_KRW:
        return FactRejected(
            FactRejectionReason.UNSPECIFIED_ENUM, "base_amount.currency"
        )
    if money.provenance == common_pb2.AMOUNT_PROVENANCE_KIND_UNSPECIFIED:
        return FactRejected(
            FactRejectionReason.UNSPECIFIED_ENUM, "base_amount.provenance"
        )
    if money.amount_won <= 0:
        return FactRejected(
            FactRejectionReason.NON_POSITIVE_AMOUNT, "base_amount.amount_won"
        )
    return Present(float(money.amount_won))


def resolve_text_fact(
    fact: features_pb2.CategoryCodeFact | features_pb2.AgencyIdFact,
    *,
    field: str,
    is_allowed_missing_reason: Callable[[int], bool] = _always_allowed,
) -> FactValue[str] | FactRejected:
    """텍스트 fact(공종·발주기관) 판독기(D-5D3-6, 공개 승격) — oneof 미설정·거부된
    결측 사유·정규화 뒤 빈 키는 전부 `FactRejected`다. `is_allowed_missing_reason`을
    좁히면 같은 코드로 더 엄격한 축(표본별 기관·공종)을 판정할 수 있다 — 기본값(F-10)은
    요청 축(`FeatureFacts.from_proto`)의 열린 집합과 같다(`UNSPECIFIED`만 거부)."""
    which = fact.WhichOneof("fact")
    if which is None:
        return FactRejected(FactRejectionReason.MALFORMED, field)
    if which == "missing":
        return _resolve_missing_reason(
            fact.missing,
            field=field,
            is_allowed_missing_reason=is_allowed_missing_reason,
        )
    normalized = normalize_feature_key(fact.value)
    if not normalized:
        return FactRejected(FactRejectionReason.EMPTY_KEY, field)
    return Present(normalized)


def _resolve_denominator_source(
    fact: features_pb2.BaseAmountProvenanceLabelFact,
) -> FactValue[str] | FactRejected:
    which = fact.WhichOneof("fact")
    if which is None:
        return FactRejected(FactRejectionReason.MALFORMED, "denominator_source")
    if which == "missing":
        return _resolve_missing_reason(fact.missing, field="denominator_source")
    if fact.value == common_pb2.BASE_AMOUNT_PROVENANCE_LABEL_UNSPECIFIED:
        return FactRejected(FactRejectionReason.UNSPECIFIED_ENUM, "denominator_source")
    return Present(denominator_source_label_name(fact.value))


@dataclass(frozen=True)
class FeatureFacts:
    """`FeatureInputs`가 검증을 통과한 뒤의 타입 fact 넷. 직접 생성은 test 전용 컨벤션 —
    이 타입의 의미는 「`from_proto`를 통과했다」다(설계 검토 (2b), Python은 가시성을
    강제하지 못한다)."""

    base_amount: FactValue[float]
    category_code: FactValue[str]
    agency_id: FactValue[str]
    denominator_source: FactValue[str]

    @staticmethod
    def from_proto(
        inputs: features_pb2.FeatureInputs,
    ) -> FeatureFacts | FactRejected:
        base_amount = _resolve_base_amount(inputs.base_amount)
        if isinstance(base_amount, FactRejected):
            return base_amount
        category_code = resolve_text_fact(inputs.category_code, field="category_code")
        if isinstance(category_code, FactRejected):
            return category_code
        agency_id = resolve_text_fact(inputs.agency_id, field="agency_id")
        if isinstance(agency_id, FactRejected):
            return agency_id
        denominator_source = _resolve_denominator_source(
            inputs.base_amount_provenance_label
        )
        if isinstance(denominator_source, FactRejected):
            return denominator_source
        return FeatureFacts(
            base_amount=base_amount,
            category_code=category_code,
            agency_id=agency_id,
            denominator_source=denominator_source,
        )
