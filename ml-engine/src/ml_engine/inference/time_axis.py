"""`ml_engine.inference.time_axis` — 정산 성숙도의 **시간축 provenance**(M9/ml-lifecycle,
SET-06). 신규 — legacy 에는 이 어휘가 없었다.

legacy 는 개찰 시각(공고별 최솟값)이 없으면 **마감 시각으로 조용히 대체**했고, 대체 근거로
두 축이 근사하다는 실측(2026-08-13: p50·p90 모두 1시간 차이, 97.8%가 같은 주)을 들었다.
대체 자체를 없애면 개찰 시각을 못 받은 공고가 분모에서 빠져 성숙도가 부풀려지므로 legacy 의
문제의식은 타당하다. 그러나 **대체 사실이 결과에 남지 않아 시간 provenance 가 소실되고,
대체도 불가한 행은 사유 없이 사라진다** — 이 형태는 채택하지 않는다(capability-map SET-06
「legacy 형태 처리 — 시간축 대체」).

V2 의 세 규율:
1. 대체는 **승인된 정책으로만** 허용한다. 승인 여부는 versioned 정책 파일의 한 칸
   (`maturity.time_axis_substitute`, 닫힌 어휘)이고 코드에는 승인 목록이 없다 —
   `OPEN-SET-10` 이 미결이므로 출하값은 미승인이다.
2. 쓰인 **시각 종류와 정책 version** 을 결과에 보존한다(`KnownTimeAxis`).
3. 대체 불가 행은 **사유 있는 측정 불가**다(`UnmeasurableTimeAxis`). 이 타입에는 시각
   필드가 **없다** — 「출처를 모르는데 값은 있다」를 적을 자리가 없으므로, 그 행을 어느
   주에 배정하려면 값을 지어내야 하고, 지어내면 성숙도 표의 모집단 항등식이 어긋난다.

**`inference.results.UnmeasurableReason` 과 다른 어휘다**(정의 자리는 `results.py` 이고
`availability.py` 는 import 만 한다 — 혼동을 막는 문단이 혼동할 좌표를 적지 않게). 그쪽은 **예측 응답**의
도메인 결과 축(wire 의 `bidvector.ml.v1.UnmeasurableReason` 으로 사상된다)이고, 여기는
**성숙도 시간축**의 측정 불가다 — 섞으면 응답 어휘에 wire 값이 없는 사유가 생기고
(`serving.wire` 의 사상 불변식이 깨진다) 두 축의 「측정 불가」가 한 이름이 된다.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from enum import StrEnum


class TimeAxisSource(StrEnum):
    """성숙도 구간을 고르는 데 쓴 시각의 **종류**. 새 종류는 코드 분기가 아니라 이 enum
    한 줄 + 정책 승인이다."""

    OPENED_AT = "OPENED_AT"
    """개찰 시각 — 정본 축."""

    BID_CLOSE_AT = "BID_CLOSE_AT"
    """마감 시각 — legacy 가 조용히 쓰던 대체 축(`OPEN-SET-10`, 승인 전에는 쓰이지 않는다)."""


class TimeAxisUnmeasurableReason(StrEnum):
    """시간축을 정할 수 없는 사유. 「분모에서 사라진 행」은 전부 이 사유 중 하나를 갖는다
    (사유 없이 사라지는 행이 0 건임을 성숙도 표의 항등식이 계수로 잰다)."""

    NO_TIME_AXIS_VALUE = "NO_TIME_AXIS_VALUE"
    """개찰 시각도 대체 후보 시각도 없다."""

    UNAPPROVED_TIME_AXIS_SOURCE = "UNAPPROVED_TIME_AXIS_SOURCE"
    """대체 후보 시각은 있으나 그 출처가 승인된 정책 밖이다(`OPEN-SET-10` 미결)."""


@dataclass(frozen=True)
class KnownTimeAxis:
    """시간축 값 + 그 값이 **어느 시각 종류에서 왔는지** + 적용된 정책 version."""

    value: datetime
    source: TimeAxisSource
    policy_version: str


@dataclass(frozen=True)
class UnmeasurableTimeAxis:
    """시간축을 정할 수 없다 — **시각 값을 나르지 않는다**(사유와 정책 version 만).

    `policy_version` 이 여기에도 있는 이유(cr M-1): 「어느 정책으로 해석해서 측정 불가로
    판정했는가」가 그 판정의 일부다. 같은 행이 대체를 승인한 정책에서는 분모에 들어간다 —
    그래서 표가 공시하는 정책과 행이 해석된 정책이 다르면, 그 표는 **자기가 측정할 수
    있었던 행을 측정 불가로** 적는다(이 모듈이 없애려던 provenance 소실과 분모 축소가 같은
    자리에서 다시 성립한다). 금지 사항은 **시각 값**이지 정책 version 이 아니다."""

    reason: TimeAxisUnmeasurableReason
    policy_version: str


type TimeAxis = KnownTimeAxis | UnmeasurableTimeAxis


class TimeAxisSubstitute(StrEnum):
    """대체 출처 승인 상태 — **닫힌 어휘**다(v L-2).

    앞 판은 정책 파일의 bool 한 칸이었다. PyYAML 은 `yes`·`Yes`·`YES`·`on`·`On` 을 **bool 로
    읽으므로**
    `maturity.time_axis_substitute_approved: yes` 가 승인으로 통과했다(실측 — 그 판의 키 이름
    이다) — 로더가 bool
    타입만 보았기 때문이다(`"true"`·`1` 은 거부됐으니 「느슨한 참값을 승인으로 읽지
    않는다」는 문면이 절반만 참이었다). 어휘를 문자열 두 값으로 닫으면 YAML 의 참값 표기가
    **전부** 미지 값이 된다 — 승인은 그 두 단어 중 하나를 적어야만 생긴다."""

    NOT_APPROVED = "NOT_APPROVED"
    APPROVED = "APPROVED"


@dataclass(frozen=True)
class TimeAxisPolicy:
    """승인된 시간축 해석 한 벌. `substitute_approved` 는 versioned 정책 파일에서만 오고
    (`inference.policy.time_axis_policy_of` 가 유일 생성 경로), `version` 은 그 정책의
    version 이라 결과에 실린 provenance 가 「어느 정책으로 해석했는가」를 가리킨다."""

    version: str
    substitute_approved: bool


def resolve_time_axis(
    *,
    opened_at: datetime | None,
    bid_close_at: datetime | None,
    policy: TimeAxisPolicy,
) -> TimeAxis:
    """두 시각 후보와 정책에서 시간축을 정한다. 개찰 시각이 있으면 **항상** 그것이다
    (대체는 결측일 때만 — 승인됐다고 정본 축을 밀어내지 않는다)."""
    if opened_at is not None:
        return KnownTimeAxis(
            value=opened_at,
            source=TimeAxisSource.OPENED_AT,
            policy_version=policy.version,
        )
    if bid_close_at is None:
        return UnmeasurableTimeAxis(
            reason=TimeAxisUnmeasurableReason.NO_TIME_AXIS_VALUE,
            policy_version=policy.version,
        )
    if not policy.substitute_approved:
        return UnmeasurableTimeAxis(
            reason=TimeAxisUnmeasurableReason.UNAPPROVED_TIME_AXIS_SOURCE,
            policy_version=policy.version,
        )
    return KnownTimeAxis(
        value=bid_close_at,
        source=TimeAxisSource.BID_CLOSE_AT,
        policy_version=policy.version,
    )
