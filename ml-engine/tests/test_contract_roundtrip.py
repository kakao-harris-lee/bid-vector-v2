"""M2/2A round-trip(Python) — 같은 `contracts/testdata/*.binpb` 로 Kotlin 쪽과 같은 바이트·같은
정규형 문자열을 낸다(⑦). S-6 — Gradle `check` 밖(CI 에 Python 툴체인 없음, 알려진 제한).

거부 규칙(fail-closed, ⑥)은 Kotlin 쪽 `ContractRoundTripTest`와 대칭으로 여기서도 test 가
순수 함수로 문서화한다 — Python 쪽 실제 validation 구현은 5E 몫이다(scope.md 「만들지 않는 것」).
"""

from __future__ import annotations

from pathlib import Path

_TESTDATA_ROOT = Path(__file__).resolve().parents[2] / "contracts" / "testdata"


def _read(name: str) -> bytes:
    return (_TESTDATA_ROOT / name).read_bytes()


# ---- ⑦ round-trip: 원본 testdata == parse 후 deterministic 재직렬화 ----


def test_money_round_trips_to_same_bytes(common_pb2) -> None:
    original = _read("money.binpb")
    money = common_pb2.Money()
    money.ParseFromString(original)
    assert money.SerializeToString(deterministic=True) == original


def test_rate_round_trips_and_normal_form_matches_kotlin(common_pb2) -> None:
    original = _read("rate.binpb")
    rate = common_pb2.Rate()
    rate.ParseFromString(original)
    # scale 보존 — Kotlin 쪽 ContractRoundTripTest 의 같은 단언과 대칭.
    assert rate.fraction == "0.8700"
    assert rate.SerializeToString(deterministic=True) == original


def test_request_envelope_round_trips(common_pb2) -> None:
    original = _read("request_envelope.binpb")
    envelope = common_pb2.RequestEnvelope()
    envelope.ParseFromString(original)
    assert envelope.SerializeToString(deterministic=True) == original


def test_prediction_envelope_round_trips(common_pb2) -> None:
    original = _read("prediction_envelope.binpb")
    envelope = common_pb2.PredictionEnvelope()
    envelope.ParseFromString(original)
    assert envelope.SerializeToString(deterministic=True) == original
    assert envelope.model_release_selector.WhichOneof("selector") == "exact_release"


def test_unmeasurable_round_trips(error_pb2) -> None:
    original = _read("unmeasurable.binpb")
    unmeasurable = error_pb2.Unmeasurable()
    unmeasurable.ParseFromString(original)
    assert unmeasurable.SerializeToString(deterministic=True) == original
    assert unmeasurable.reason == error_pb2.UNMEASURABLE_REASON_UNTRAINED_SEGMENT


def test_application_failure_round_trips(error_pb2) -> None:
    original = _read("application_failure.binpb")
    failure = error_pb2.ApplicationFailure()
    failure.ParseFromString(original)
    assert failure.SerializeToString(deterministic=True) == original
    assert failure.code == error_pb2.FAILURE_CODE_MODEL_NOT_READY
    assert failure.retryable is True


# ---- ⑥ fail-closed — UNSPECIFIED 는 Python 쪽에서도 계약 위반이다(거부는 test 가 문서화) ----


def test_money_default_instance_is_unspecified_and_must_be_rejected(common_pb2) -> None:
    money = common_pb2.Money()
    assert money.currency == common_pb2.CURRENCY_UNSPECIFIED
    assert money.basis == common_pb2.BASIS_UNSPECIFIED
    assert money.provenance == common_pb2.AMOUNT_PROVENANCE_KIND_UNSPECIFIED
    assert not _is_acceptable_money(common_pb2, money)


def test_valid_money_from_testdata_is_acceptable(common_pb2) -> None:
    money = common_pb2.Money()
    money.ParseFromString(_read("money.binpb"))
    assert _is_acceptable_money(common_pb2, money)


def test_unmeasurable_default_reason_is_unspecified_and_must_be_rejected(error_pb2) -> None:
    unmeasurable = error_pb2.Unmeasurable()
    assert unmeasurable.reason == error_pb2.UNMEASURABLE_REASON_UNSPECIFIED


def _is_acceptable_money(common_pb2, money) -> bool:
    """계약이 요구하는 거부 규칙 — 순수 함수. 실제 validation 구현은 5E 몫이고, 여기서는
    round-trip test 가 그 규칙을 Kotlin 쪽과 대칭으로 문서화한다."""
    return (
        money.currency != common_pb2.CURRENCY_UNSPECIFIED
        and money.basis != common_pb2.BASIS_UNSPECIFIED
        and money.vat_treatment != common_pb2.VAT_TREATMENT_UNSPECIFIED
        and money.provenance != common_pb2.AMOUNT_PROVENANCE_KIND_UNSPECIFIED
    )
