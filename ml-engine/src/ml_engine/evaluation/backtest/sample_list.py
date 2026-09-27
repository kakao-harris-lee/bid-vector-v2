"""표본 목록(`sample-list.tsv`) — v4 의 **결과를 보기 전에 표본이 확정됐다**를
받치는 파일 하나와 그 대조들(D-6G-39 · 스키마 §2.1).

manifest 타입을 알지 않는다 — 대조에 필요한 값만 인자로 받는다. 표본 목록은
스냅숏 manifest 와 수명이 다른 개념이고, 여기에 manifest 를 끌어들이면 이 모듈이
스냅숏 판독 전체에 묶인다."""

from __future__ import annotations

import hashlib
from collections.abc import Sequence
from typing import Final

from ml_engine.evaluation.backtest.jsonrow import (
    RowReadError,
    SnapshotRejectionReason,
)

# 칸 이름을 적어 두면 개수가 파생된다 — 수를 따로 적지 않아도 되고, 무엇이 오는지도
# 문면에 남는다(스키마 §2.1).
_SAMPLE_LIST_COLUMNS: Final[tuple[str, ...]] = (
    "notice_key_hash",
    "business_division",
    "notice_week",
)
# 해시 길이는 해시 함수가 정한다 — 상수로 적으면 둘이 갈릴 수 있다.
_NOTICE_KEY_HEX_LENGTH: Final[int] = len(hashlib.sha256(b"").hexdigest())


def parse_sample_list(sample_list_bytes: bytes) -> tuple[str, ...]:
    """`sample-list.tsv` 를 키 순서대로 읽는다(v4, D-6G-39 · 스키마 §2.1).

    형태는 헤더 없는 TSV 다 — `notice_key_hash <TAB> business_division <TAB>
    notice_week`, **해시 오름차순**, 줄마다 `\n`(끝 줄 포함). 여기서 뒤 두 칸은 층
    정보라 판독이 쓰지 않지만 **칸 수는 센다** — 형태가 다르면 두 레인이 다른 파일을
    보고 있다는 뜻이다.

    정렬을 여기서 고쳐 주지 않는다. 파일이 표본의 정본이고 그 바이트의 sha256 이
    대조 대상이므로, 순서가 어긋난 파일은 **다른 파일**이다."""
    text = sample_list_bytes.decode("utf-8", errors="strict")
    if text and not text.endswith("\n"):
        raise RowReadError(
            SnapshotRejectionReason.SAMPLE_LIST_MALFORMED, "끝 줄 개행이 없다"
        )
    keys: list[str] = []
    for line in text.splitlines():
        columns = line.split("\t")
        if len(columns) != len(_SAMPLE_LIST_COLUMNS):
            raise RowReadError(
                SnapshotRejectionReason.SAMPLE_LIST_MALFORMED,
                f"칸 수가 {len(_SAMPLE_LIST_COLUMNS)} 이 아니다: {len(columns)}",
            )
        key = columns[0]
        if len(key) != _NOTICE_KEY_HEX_LENGTH or key != key.lower():
            raise RowReadError(
                SnapshotRejectionReason.SAMPLE_LIST_MALFORMED,
                "notice_key_hash 가 소문자 hex 64자가 아니다",
            )
        keys.append(key)
    if keys != sorted(keys):
        raise RowReadError(
            SnapshotRejectionReason.SAMPLE_LIST_MALFORMED, "해시 오름차순이 아니다"
        )
    return tuple(keys)


def check_sample_list(
    sample_list_bytes: bytes,
    *,
    declared_sha256: str,
    sample_size: int,
    row_keys: Sequence[str],
    sampled_without_detail: int,
    sampled_without_notice: int,
) -> None:
    """표본 대조 셋(스키마 §2.1). v3 까지는 manifest 의 해시를 **행에서 역산**해
    맞췄으니 정의상 언제나 맞는 순환 대조였다 — 「결과를 보기 전에 표본이 확정됐다」
    (우회 ⑦)를 아무것도 검사하지 못했다. v4 는 표본을 파일로 못 박아 **실패할 수
    있는** 대조로 바꾼다.

    ⑴ 파일 바이트의 sha256 == `sample_list_sha256`
    ⑵ 모든 행의 키가 파일의 키 집합 **안**(행이 표본의 진부분집합인 것은 정상이다 —
       상세를 못 받았거나 공고 canonical 이 없는 표본이 있다)
    ⑶ 닫힌 항등식 — 표본 하나하나가 행이 되었거나 되지 못한 사유로 계수된다"""
    if hashlib.sha256(sample_list_bytes).hexdigest() != declared_sha256:
        raise RowReadError(
            SnapshotRejectionReason.CHECKSUM_MISMATCH, "sample-list.tsv sha256 불일치"
        )
    sampled = set(parse_sample_list(sample_list_bytes))
    if len(sampled) != sample_size:
        raise RowReadError(
            SnapshotRejectionReason.SAMPLE_COUNT_MISMATCH,
            f"manifest sample_size {sample_size} != 파일 {len(sampled)}",
        )
    stray = [key for key in row_keys if key not in sampled]
    if stray:
        raise RowReadError(
            SnapshotRejectionReason.SAMPLE_LIST_MISMATCH,
            f"표본 목록 밖의 행 {len(stray)}건",
        )
    accounted = len(row_keys) + sampled_without_detail + sampled_without_notice
    if accounted != sample_size:
        raise RowReadError(
            SnapshotRejectionReason.SAMPLE_COUNT_MISMATCH,
            f"항등식이 깨졌다: {len(row_keys)} + {sampled_without_detail} + "
            f"{sampled_without_notice} != {sample_size}",
        )
