"""M5/5A 게이트 — `contracts/proto`의 파일 목록과 `ml_engine.contracts` 재수출 이름이 1:1
인지 확인(D-5A-0 (b))."""

from __future__ import annotations

from pathlib import Path

import ml_engine.contracts

_REPO_ROOT = Path(__file__).resolve().parents[3]
_PROTO_ROOT = _REPO_ROOT / "contracts" / "proto" / "bidvector" / "ml" / "v1"
# common·error·features 는 메시지만(서비스 없음), prediction·training·embedding 은 서비스가
# 있어 `_pb2_grpc`도 생성된다(2026-09-11 실측 — `grep -l '^service ' contracts/proto/**/*.proto`).
_SERVICE_STEMS = {"prediction", "training", "embedding"}


def test_reexport_names_match_proto_file_list() -> None:
    proto_stems = {path.stem for path in _PROTO_ROOT.glob("*.proto")}
    expected = {f"{stem}_pb2" for stem in proto_stems}
    expected |= {f"{stem}_pb2_grpc" for stem in proto_stems if stem in _SERVICE_STEMS}
    assert set(ml_engine.contracts.__all__) == expected


def test_every_exported_name_is_actually_importable() -> None:
    for name in ml_engine.contracts.__all__:
        assert hasattr(ml_engine.contracts, name), f"{name} 이 ml_engine.contracts 에 없다"
