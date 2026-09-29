"""표본 목록(`sample-list.tsv`) — v4 의 **결과를 보기 전에 표본이 확정됐다**를
받치는 파일 하나와 그 대조들(D-6G-39 · 스키마 §2.1).

manifest 타입을 알지 않는다 — 대조에 필요한 값만 인자로 받는다. 표본 목록은
스냅숏 manifest 와 수명이 다른 개념이고, 여기에 manifest 를 끌어들이면 이 모듈이
스냅숏 판독 전체에 묶인다."""

from __future__ import annotations

import hashlib
import string
from collections.abc import Sequence
from dataclasses import dataclass
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
_NOTICE_KEY_ALPHABET: Final[frozenset[str]] = frozenset(string.hexdigits.lower())
"""소문자 hex 문자 집합(cr r3 L-6). 길이와 대소문자만 보던 앞 판은 `zzzz...` 64자를
받았다 — Kotlin `NoticeKeyHash.ofHex` 는 `[0-9a-f]{64}` 라 두 레인의 판독 강도가
달랐다. 오늘은 manifest 해시 대조가 가려 주지만, 가려 준다는 것과 검사한다는 것은
다르다."""

BUSINESS_DIVISIONS: Final[frozenset[str]] = frozenset(
    {"CONSTRUCTION", "SERVICE", "GOODS", "FOREIGN"}
)
"""스키마 §2.1 의 업무 구분 어휘(Kotlin `BusinessDivision`). **닫힌 셋이다**(D-6G-53).

세기만 하던 앞 판에는 조용한 실패가 있었다: 이 값의 distinct 수가 최소 표본 문턱을
정하는데(업무 하나당 1,739건), 서로 다른 두 업무가 **같은 문자열로 합쳐지는 오타**면
distinct 수가 줄고 문턱이 **내려간다** — 설계가 요구한 것보다 적은 데이터로 실험이
그대로 진행된다. 어휘 밖 값을 거부하면 그 방향이 닫힌다."""


@dataclass(frozen=True)
class SampleList:
    """`sample-list.tsv` 가 말하는 것 둘 — **누가** 표본인가(`keys`)와 **어떤 업무
    축들이** 표본틀에 들어갔는가(`divisions`).

    업무 축이 값을 갖는 이유는 최소 표본 결정식이 그 **수**를 쓰기 때문이다(M-6).
    코드 상수로 세면 Kotlin 의 수집 대상 업무 설정과 갈릴 수 있고, 설정이 둘인데
    셋으로 세면 영영 닿지 않는 문턱이 된다 — 두 레인이 다 읽는 이 파일이 단일
    출처다."""

    keys: tuple[str, ...]
    divisions: tuple[str, ...]


def parse_sample_list(sample_list_bytes: bytes) -> SampleList:
    """`sample-list.tsv` 를 키 순서대로 읽는다(v4, D-6G-39 · 스키마 §2.1).

    형태는 헤더 없는 TSV 다 — `notice_key_hash <TAB> business_division <TAB>
    notice_week`, **해시 오름차순**, 줄마다 `\n`(끝 줄 포함).

    `business_division` 은 **행의 `category` 와 다른 축**이지만 어휘는 같다(스키마
    §2.1 — Kotlin `BusinessDivision`). **닫힌 셋 밖이면 스냅숏 전체를 거부한다**
    (D-6G-53) — 이 값의 distinct 수가 최소 표본 문턱을 정하므로, 합쳐지는 오타가
    문턱을 조용히 낮춘다. `notice_week` 은 어휘가 없으므로 빈 값만 거부한다.

    정렬을 여기서 고쳐 주지 않는다. 파일이 표본의 정본이고 그 바이트의 sha256 이
    대조 대상이므로, 순서가 어긋난 파일은 **다른 파일**이다."""
    # 자기 파일의 디코드 실패는 **자기가** 사유를 붙인다(cr r3 L-7). 바깥에서
    # `UnicodeDecodeError` 를 한꺼번에 받으면 깨진 `rows.jsonl` 이 「표본 목록이 형태를
    # 어겼다」로 보고된다 — 어느 파일이 깨졌는지 사유가 가리키지 못한다.
    try:
        text = sample_list_bytes.decode("utf-8", errors="strict")
    except UnicodeDecodeError as exc:
        raise RowReadError(
            SnapshotRejectionReason.SAMPLE_LIST_MALFORMED, "sample-list: UTF-8 아님"
        ) from exc
    if text and not text.endswith("\n"):
        raise RowReadError(
            SnapshotRejectionReason.SAMPLE_LIST_MALFORMED, "끝 줄 개행이 없다"
        )
    keys: list[str] = []
    divisions: set[str] = set()
    for line in text.splitlines():
        key, division = _parse_line(line)
        keys.append(key)
        divisions.add(division)
    if keys != sorted(keys):
        raise RowReadError(
            SnapshotRejectionReason.SAMPLE_LIST_MALFORMED, "해시 오름차순이 아니다"
        )
    return SampleList(keys=tuple(keys), divisions=tuple(sorted(divisions)))


def _parse_line(line: str) -> tuple[str, str]:
    """한 줄 -> (키, 업무 구분). 형태 위반은 여기서 사유를 붙인다."""
    columns = line.split("\t")
    if len(columns) != len(_SAMPLE_LIST_COLUMNS):
        raise RowReadError(
            SnapshotRejectionReason.SAMPLE_LIST_MALFORMED,
            f"칸 수가 {len(_SAMPLE_LIST_COLUMNS)} 이 아니다: {len(columns)}",
        )
    key, division, week = columns
    if len(key) != _NOTICE_KEY_HEX_LENGTH or not _NOTICE_KEY_ALPHABET.issuperset(key):
        raise RowReadError(
            SnapshotRejectionReason.SAMPLE_LIST_MALFORMED,
            "notice_key_hash 가 소문자 hex 64자가 아니다",
        )
    if not division.strip() or not week.strip():
        raise RowReadError(
            SnapshotRejectionReason.SAMPLE_LIST_MALFORMED,
            "층 칸이 비었다(업무 축 또는 주)",
        )
    if division not in BUSINESS_DIVISIONS:
        raise RowReadError(
            SnapshotRejectionReason.UNKNOWN_BUSINESS_DIVISION,
            "업무 구분이 닫힌 셋 밖이다",
        )
    return key, division


def check_sample_list(
    sample_list_bytes: bytes,
    *,
    declared_sha256: str,
    sample_size: int,
    row_keys: Sequence[str],
    sampled_without_detail: int,
    sampled_without_notice: int,
    incomplete_axis: int,
) -> SampleList:
    """표본 대조 셋(스키마 §2.1). v3 까지는 manifest 의 해시를 **행에서 역산**해
    맞췄으니 정의상 언제나 맞는 순환 대조였다 — 「결과를 보기 전에 표본이 확정됐다」
    (우회 ⑦)를 아무것도 검사하지 못했다. v4 는 표본을 파일로 못 박아 **실패할 수
    있는** 대조로 바꾼다.

    ⑴ 파일 바이트의 sha256 == `sample_list_sha256`
    ⑵ 모든 행의 키가 파일의 키 집합 **안**(행이 표본의 진부분집합인 것은 정상이다 —
       상세를 못 받았거나 공고 canonical 이 없는 표본이 있다)
    ⑶ 닫힌 항등식(v5, D-6G-58) — 표본 하나하나가 행이 되었거나 **되지 못한 사유로**
       계수된다. 항이 넷이다: 행 · 상세 없음 · 공고 없음 · 축 미완. 어느 쪽도 아닌
       공고는 없다 — 깨지면 계수가 행을 설명하지 못한다는 뜻이라 구조 실패다

    통과하면 읽어 둔 목록을 돌려준다 — 업무 축은 스냅숏이 실어 나르고 최소 표본
    결정식이 그 수를 쓴다(M-6). 같은 파일을 두 번 파싱하지 않기 위해서다."""
    if hashlib.sha256(sample_list_bytes).hexdigest() != declared_sha256:
        raise RowReadError(
            SnapshotRejectionReason.CHECKSUM_MISMATCH, "sample-list.tsv sha256 불일치"
        )
    listing = parse_sample_list(sample_list_bytes)
    sampled = set(listing.keys)
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
    _check_identity(
        sample_size,
        row_count=len(row_keys),
        missing=(sampled_without_detail, sampled_without_notice, incomplete_axis),
    )
    return listing


def _check_identity(
    sample_size: int, *, row_count: int, missing: tuple[int, ...]
) -> None:
    """닫힌 항등식 — 표본 하나하나가 행이 되었거나 **되지 못한 사유로** 계수된다.

    결측 항을 낱개 인자가 아니라 묶음으로 받는 이유: v4 에서 v5 로 오며 항이 하나 늘었고
    (`incomplete_axis`), 앞으로도 는다. 항을 인자로 열거하면 늘 때마다 시그니처가 바뀌고
    **더하기를 빠뜨려도 조용히 통과**한다 — 묶음이면 합이 곧 계약이다."""
    accounted = row_count + sum(missing)
    if accounted != sample_size:
        raise RowReadError(
            SnapshotRejectionReason.SAMPLE_COUNT_MISMATCH,
            f"항등식이 깨졌다: {row_count} + {'+'.join(str(n) for n in missing)}"
            f" != {sample_size}",
        )
