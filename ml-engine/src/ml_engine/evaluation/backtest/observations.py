"""스냅숏이 실어 나르는 **값 타입들**(v4, 스키마 §3).

판독 절차(`snapshot.py`)와 나눠 둔다 — 타입은 여러 모듈이 읽고, 절차는 한 곳만
쓴다. 같은 파일에 두면 절차가 커질 때마다 타입을 읽는 모듈들이 함께 무거워진다.

**누출 방지가 타입으로 서 있다**: 한 행이 `NoticeObservation`(투찰 시점에 알 수 있는
것)과 `OpeningOutcome`(개찰로 드러나는 것)으로 갈린다. 전략은 앞의 반쪽만 받는다 —
개찰 결과 이름이 `notice` 쪽에 없다는 것을 test 가 전수로 잠근다."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, datetime
from enum import StrEnum

from ml_engine.evaluation.backtest.jsonrow import SnapshotRejectionReason


class BusinessCategory(StrEnum):
    """업무 대분류 — 닫힌 셋. 하한가 산식이 이 축으로 갈린다(D-6G-12)."""

    CONSTRUCTION = "CONSTRUCTION"
    SERVICE = "SERVICE"
    GOODS = "GOODS"


@dataclass(frozen=True)
class SnapshotRejected:
    """판독 거부 — `detail` 은 필드 이름·개수만 나른다(식별자 금지)."""

    reason: SnapshotRejectionReason
    detail: str


@dataclass(frozen=True)
class AValue:
    """입찰가격산식 A — 합산액과 **그 자신의 공개일시**. 공개가 입찰 마감 뒤인 공고는
    투찰 시점에 알 수 없는 값이라 제외된다(D-6G-12·D-6G-13 ⑥).

    `standard_market_price_applicable`(`snapshot-v2`, D-6G-23)는 표준시장단가금액
    (`smkpAmt`)의 적용 여부 술어다. **`total` 은 그 금액을 더하지 않는다**(스키마 §3.3 —
    예규의 A 일곱 항목 열거에 없고 근거 문면을 확보하지 못했다). 이 술어가 있어야
    그 배제의 **영향 범위**를 셀 수 있다 — 없으면 판정문이 「몇 건이 걸리는가」를 말하지
    못한다. `Y`/`N` 밖의 값이나 부재는 `None`(판정 불가)."""

    total: float
    open_at: datetime
    standard_market_price_applicable: bool | None


@dataclass(frozen=True)
class NoticeObservation:
    """투찰 시점에 알 수 있는 것만. 개찰 결과 이름은 여기 없다(누출 금지의 타입 경계).

    **기초금액이 두 칸으로 갈린다**(스키마 §3.4, D-6G-19). 여기 `base_amount` 는 **기초금액
    조회 출처**이고 `base_amount_disclosed_at < bid_close_at` 인 행만 값을 갖는다 — 공개가
    마감보다 늦으면 투찰 시점에 없던 값이라 `null` 이다. 개찰결과 출처의 기초금액은
    `OpeningOutcome.opening_base_amount` 로 따로 있고 **채점만** 쓴다. 한 칸에 접으면
    전략이 투찰 시점에 몰랐던 값을 입력으로 쓰게 된다.

    **없는 값을 상수로 메우지 않는다** — 없으면 제외 사유가 되고 계수된다(D-6G-16).
    예가 범위율은 기초금액 조회에서 오고 원문이 percent 라 추출이 fraction 으로 넘긴다.
    시작률이 종료율의 반대수라는 보장은 없다(비대칭 범위 지원)."""

    notice_key_hash: str
    category: BusinessCategory
    noticed_on: date | None
    bid_close_at: datetime | None
    base_amount: float | None
    base_amount_disclosed_at: datetime | None
    floor_rate: float | None
    reserve_range_begin_rate: float | None
    reserve_range_end_rate: float | None
    a_value: AValue | None
    successful_bid_method_code: str | None
    successful_bid_method_name: str | None
    prearranged_price_decision_method: str | None
    notice_ordinal: int
    procurement_class_code: str | None
    demand_agency_code: str | None
    bid_price_formula_a_applicable: bool | None
    pure_construction_cost: float | None
    has_award_method_application_standard: bool
    has_application_basis_content: bool
    """자유텍스트 두 칸의 **존재 여부만**(v3, D-6G-33). 원문은 무엇이 실릴지 모르는
    자유텍스트라 담당자명이 들어올 수 있는 유일한 비통제 경로였다(privacy r1) — 스냅숏이
    원문을 나르지 않으므로 그 경로가 사라진다. D-6G-22 채움률은 이 불리언으로 낸다."""


@dataclass(frozen=True)
class BidderRow:
    """투찰자 한 행 — 공고 안 순번과 금액뿐. 상호·사업자번호를 싣지 않는다(D-6G-10).

    `rank`(원문 `opengRank`)는 **결측·중복이 흔하다**(스키마 §3.2 실측: 표본 15건 중
    전 행 유일은 4건뿐) — 경쟁자 분포는 `rank` 가 아니라 `amount` 로 만든다. `ordinal`
    은 금액 오름차순으로 추출이 붙인 순번이고 항상 있다."""

    ordinal: int
    rank: int | None
    amount: float | None


@dataclass(frozen=True)
class OpeningOutcome:
    """개찰로 드러나는 것 — 채점만 읽는다. `opening_base_amount` 는 개찰결과 출처의
    기초금액이고 `NoticeObservation.base_amount` 와 **다른 칸**이다(스키마 §3.4)."""

    opened_on: date | None
    planned_price: float | None
    progress_division: str | None
    """진행구분 — **개찰로 드러나는 값**이라 이쪽이다(v3, verifier r1 M-5). v2 는 투찰
    시점 타입에 있었고, 그러면 「타입으로 닫았다」가 이 칸에서 깨진다."""

    opening_base_amount: float | None
    reserve_prices: tuple[float, ...] | None
    drawn_serial_numbers: tuple[int, ...] | None
    participant_count: int | None
    bidder_rows: tuple[BidderRow, ...]


@dataclass(frozen=True)
class SnapshotRow:
    notice: NoticeObservation
    outcome: OpeningOutcome


@dataclass(frozen=True)
class LoadedSnapshot:
    """판독된 스냅숏 — 행은 `notice_key_hash` 오름차순으로 고정한다(입력 파일의 줄
    순서가 판정에 새지 않게)."""

    snapshot_id: str
    period_start: date
    period_end: date
    rows_sha256: str
    sample_list_sha256: str
    sample_size: int
    sampled_without_detail: int
    sampled_without_notice: int
    """표본인데 행이 되지 못한 공고 수(v4, D-6G-39). 「상세 축 관측이 없다」와 「공고
    목록 canonical 이 없다」를 가른다 — 판정 JSON 이 둘을 따로 싣는다. 행이 표본의
    **진부분집합인 것은 정상**이고, 그 차이가 이 두 수로 설명되어야 한다."""

    sample_divisions: tuple[str, ...]
    """표본틀에 들어간 업무 축들(오름차순 distinct). 최소 표본 결정식이 이 **수**를
    쓴다(M-6) — 코드 상수가 아니라 두 레인이 다 읽는 파일이 출처다."""

    rows: tuple[SnapshotRow, ...]

    @property
    def notice_observed_count(self) -> int:
        """`has_*` 채움률의 분모(M-8) — 표본에서 **공고 canonical 이 없던 것만** 뺀다.

        상세(개찰 결과)를 못 받아 행이 되지 못한 표본은 남는다: 공고 축은 관측됐다.
        행 수로 세면 「상세를 받은 공고만」의 비율이 되어 실제보다 높게 나온다."""
        return self.sample_size - self.sampled_without_notice
