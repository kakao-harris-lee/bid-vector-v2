"""`ml_engine.evaluation.backtest.reasons` — 제외 사유와 판정 불가 축의 **어휘**.

어휘를 따로 둔 이유는 순환을 막기 위해서다: 규칙 표(`rules`)와 승인 조립(`exclusions`)이
둘 다 이 이름들을 쓰고, 한쪽이 다른 쪽을 import 하면 고리가 생긴다.

**새 사유는 코드 분기가 아니라 이 enum 한 줄**이고, 계수는 0건도 공시한다 — 제외는
침묵이 아니라 기록이다.
"""

from __future__ import annotations

from enum import StrEnum


class ExclusionReason(StrEnum):
    """제외 사유 어휘(D-6G-13 ①~⑮ + 스키마가 드러낸 입력 부재 셋)."""

    LOCAL_GOVERNMENT = "LOCAL_GOVERNMENT"
    """⑪ — **오늘 한 번도 발화하지 않는다.** 기관 코드에서 지자체를 가를 authoritative
    대응이 없고(스키마 §4), 지어내면 DEC-03 을 코드가 조용히 재정의한다. 계수 0 은
    「지자체가 없었다」가 아니라 「가르지 못했다」이며 `undecidable_counts` 가 공시한다."""

    FOREIGN_CAPITAL = "FOREIGN_CAPITAL"
    """⑫ — **구조적으로 0.** 수집 갈래가 외자 오퍼레이션을 부르지 않아 그 행이 들어오지
    않는다(스키마 §4). 「걸러서 0」이 아니라 「들어오지 않아 0」이다."""

    BID_METHOD_ABSENT = "BID_METHOD_ABSENT"
    """낙찰방법이 부재해 ①⑦⑩ 을 판정할 수 없다 — 조용히 통과시키지 않는다(fail-closed)."""

    SMALL_SUM_QUOTE = "SMALL_SUM_QUOTE"
    SME_COMPETITION_SCREENING = "SME_COMPETITION_SCREENING"
    SHIP_MANUFACTURING = "SHIP_MANUFACTURING"
    NOT_QUALIFICATION_SCREENING = "NOT_QUALIFICATION_SCREENING"
    FLOOR_RATE_EFFECTIVE_DATE_BOUNDARY = "FLOOR_RATE_EFFECTIVE_DATE_BOUNDARY"
    REBID_OR_AMENDED = "REBID_OR_AMENDED"
    SINGLE_PREARRANGED_PRICE = "SINGLE_PREARRANGED_PRICE"
    FLOOR_RATE_ABSENT_OR_OUT_OF_BAND = "FLOOR_RATE_ABSENT_OR_OUT_OF_BAND"

    BASE_AMOUNT_ABSENT_OR_LATE = "BASE_AMOUNT_ABSENT_OR_LATE"
    """D-6G-19 — 전략의 결정 변수는 `투찰금액 ÷ 기초금액` 이고 그 기초금액은 **기초금액
    조회 출처**여야 한다. 공개가 입찰 마감보다 늦으면 투찰 시점에 없던 값이라 스냅숏이
    `null` 로 두고(스키마 §3.4), 여기서 제외된다. 개찰결과 출처의 기초금액으로 메우지
    않는다 — 그게 이 칸을 둘로 가른 이유다."""

    OPENING_BASE_AMOUNT_ABSENT = "OPENING_BASE_AMOUNT_ABSENT"
    """채점 축의 기초금액(개찰결과 출처)이 없으면 사정률·순공사원가선을 낼 수 없다.
    투찰 시점 칸으로 메우지 않는다(두 칸은 서로의 대용이 아니다)."""

    RESERVE_PRICE_RANGE_ABSENT = "RESERVE_PRICE_RANGE_ABSENT"
    """D-6G-16 — 예가 범위율이 없으면 S0 의 반폭 h 가 서지 않는다. **상수 2%·3% 로
    메우지 않고**(그것이 공고별 필드라는 것이 P-3 의 발견이다) 예비가격 15개로 역산하지도
    않는다(역산은 개찰 결과를 투찰 시점 입력으로 쓰는 누출이다)."""

    RESERVE_DRAW_INCOMPLETE = "RESERVE_DRAW_INCOMPLETE"
    A_VALUE_ABSENT_OR_LATE = "A_VALUE_ABSENT_OR_LATE"
    PURE_CONSTRUCTION_COST_ABSENT = "PURE_CONSTRUCTION_COST_ABSENT"

    BIDDER_AMOUNT_ABSENT = "BIDDER_AMOUNT_ABSENT"
    """(P-1.5) 투찰금액이 없는 행이 섞이면 「적격 투찰자 중 최저」가 최저라는 보장이
    없다 — 빠진 행이 더 낮았을 수 있어 would-have-won 이 부푼다. 통째로 뺀다."""

    NOTICE_DATE_ABSENT = "NOTICE_DATE_ABSENT"
    """v3 — 공고일이 없다. **개찰일로 대체하지 않는다**: v2 는 그렇게 접혀 시행일 경계
    제외(⑬)가 개찰일 기준으로 돌았고, 하한율이 바뀐 앞뒤가 섞였다(verifier r1 H-1)."""

    BID_CLOSE_AT_ABSENT = "BID_CLOSE_AT_ABSENT"
    """v3 — 입찰 마감이 없다. A·기초금액의 공개 시점 절단이 서지 않는다."""

    OPENING_DATE_ABSENT = "OPENING_DATE_ABSENT"
    """v3 — 개찰일이 없다. 창 자르기와 누출 절단의 기준이라 대체값(EPOCH 등)을
    지어내면 그 행이 엉뚱한 사유로 계수된다(code-review r1 M-5)."""

    PLANNED_PRICE_ABSENT = "PLANNED_PRICE_ABSENT"
    """v3 — 예정가격이 없다(단수 예가·예비가격 상세 미수신). 하한가가 서지 않는다."""

    NO_ELIGIBLE_BIDDER = "NO_ELIGIBLE_BIDDER"
    TIED_LOWEST = "TIED_LOWEST"


class UndecidableAxis(StrEnum):
    """제외 규칙이 **발화하지 못한** 축 — 「해당 없음」과 구별해 센다(D-6G-21)."""

    LOCAL_GOVERNMENT = "LOCAL_GOVERNMENT"
    SHIP_MANUFACTURING_CLASS = "SHIP_MANUFACTURING_CLASS"
    PREARRANGED_PRICE_METHOD = "PREARRANGED_PRICE_METHOD"
    """D-6G-36 — 예정가격 결정방법이 부재해 ④ 를 직접 판정하지 못하고 ⑤ 에 맡긴 공고
    수. 「복수예가로 확인했다」와 「⑤ 가 보증한다」는 다른 사실이라 따로 센다."""


class ProducerExclusionReason(StrEnum):
    """생산 쪽이 귀속하는 결측 사유(스키마 §6, v5 · D-6G-58).

    **행이 아예 오지 않는 표본들**이다 — 판독은 행을 볼 수 없으므로 manifest 의 계수로만
    본다. 그래도 어휘를 닫는 이유는 하나다: 이름이 닫혀 있어야 「왜 빠졌는지 모르는
    공고」가 생기지 않는다. 판정문은 이 셋으로 결측을 귀속하고, 닫힌 항등식이 세 수의
    합이 표본을 설명함을 보증한다."""

    SAMPLED_WITHOUT_DETAIL = "SAMPLED_WITHOUT_DETAIL"
    SAMPLED_WITHOUT_NOTICE = "SAMPLED_WITHOUT_NOTICE"
    INCOMPLETE_AXIS = "INCOMPLETE_AXIS"


class DivisionCoverage(StrEnum):
    """확정 범위의 업무 하나가 표본에서 어떻게 대표됐는가(v5 D-6G-66 · **셋으로**
    M6/6G-2c D-6G2c-17).

    앞 판은 **둘**이었고 가르는 기준이 「행이 하나라도 있는가」였다 — 행 **하나**뿐인
    업무도 `COVERED` 로 읽혔다(6G verifier r5 L-8). 그 표지를 「이 업무는 대표됐다」로
    읽으면 판정문이 거짓말을 한다. 그래서 셋으로 가른다:

    - `COVERED` — 행 수가 **업무별 필요 표본 이상**이다.
    - `UNDERPOWERED` — 행은 있는데 그 수에 못 미친다.
    - `ABSENT` — 행이 **하나도** 오지 않았다(앞 판이 `UNDERPOWERED` 라 부른 자리).

    필요 표본 수는 **백테스트 정책의 창당 표본 하한**(`verdict.min_window_rows`)이다 —
    새 정책 키를 만들지 않는다(스키마 §2 가 그 출처를 적는다). 어느 표지든 **문턱은
    내려가지 않는다**: 빠진 업무가 목록에서 사라지는 대신 이름으로 남아야 결측이 스스로
    검사를 낮추지 못한다.

    **창 단위 `UNDERPOWERED`(D-6G-31)와 이름이 같을 뿐 다른 축이다.** 저쪽은 창의 불일치
    쌍이 필요 수에 못 미치는 것을 재고 이쪽은 업무가 표본에서 얼마나 대표됐는지를 잰다 —
    업무 표지는 창 판정을 바꾸지 않는다."""

    COVERED = "COVERED"
    UNDERPOWERED = "UNDERPOWERED"
    ABSENT = "ABSENT"
