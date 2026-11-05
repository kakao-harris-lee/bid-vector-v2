"""`ml_engine.evaluation.epoch` — 정책 epoch 과 승격 인용(M9/ml-lifecycle, ML-07
acceptance ③·⑥). 신규 — legacy 에는 정책 effective date 가 없었다(임계가 코드 상수였으므로
「언제부터의 규칙인가」를 적을 자리 자체가 없다).

**이 모듈이 막는 것 하나**: 승격 근거가 **자기를 만든 데이터**가 되는 것. ML-07 이 문면으로
남긴 함정이다 — 관측 효과에서 역산한 수를 상수로 굳히면 그 정책은 이 데이터의 사후 산물이
된다(legacy commit `5d38ac1`). 정책이 효력을 갖기 **전에** 끝난 창은 그 정책이 참고할 수
있었던 창이므로, 그 창의 통과를 승격 근거로 인용하지 않는다.

술어는 하나다: ``window_start >= effective date 의 KST 일 시작``. 임계도 손잡이도 없고,
통과를 만드는 길은 **정책을 새 effective date 로 다시 승인**하는 것뿐이다(그러면 정책
version·checksum·effective date 가 report 에 그대로 남아 「어느 정책으로 판정했는가」가
드러난다).

**KST 로 재는 이유**: effective date 는 운영자의 승인 **날짜**(KST)다. UTC 자정으로 재면
월요일 오전 KST 에 시작한 창이 전날로 밀린다 — SET-06 이 실측한 바로 그 함정
(`docs/discovery/capability-map.md` SET-06 「UTC 자정 경계를 쓰면 월요일 오전 개찰이 전주로
밀린다」). 성숙도 커널(`ml_engine.inference.maturity`)과 **같은 경계**를 써야 하지만 그
모듈은 import 할 수 없다(evaluation → inference 는 forbidden 계약) — 그래서 상수를
공유하지 않고 **등식을 test 로** 잠근다(`tests/evaluation/test_epoch.py` 의 cross-layer 등식).
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import UTC, datetime, time
from enum import StrEnum
from typing import Final
from zoneinfo import ZoneInfo

from ml_engine.evaluation.policy import EvaluationPolicy, policy_checksum

POLICY_TIME_ZONE: Final[ZoneInfo] = ZoneInfo("Asia/Seoul")
"""effective date 를 해석하는 달력 — 승인 날짜는 KST 다."""


@dataclass(frozen=True)
class PolicyEpoch:
    """「어느 정책이 언제부터」 한 벌. `policy_epoch`가 유일 생성 경로이므로 낱개 값으로
    epoch 을 지어 인용을 통과시키는 자리가 없다."""

    version: str
    checksum: str
    effective_at: datetime
    """effective date 의 KST 일 시작(tz-aware). 날짜가 아니라 시각이라 창 시작과 직접
    비교된다 — 비교 쪽에서 다시 달력을 해석하지 않는다."""


def policy_epoch(policy: EvaluationPolicy) -> PolicyEpoch:
    """정책 한 벌에서 epoch 를 파생한다 — 유일 생성 경로."""
    return PolicyEpoch(
        version=policy.version,
        checksum=policy_checksum(policy),
        effective_at=datetime.combine(
            policy.effective_date, time.min, tzinfo=POLICY_TIME_ZONE
        ),
    )


class CitationRejectionReason(StrEnum):
    """인용 거부 사유 어휘. 새 사유는 코드 분기가 아니라 이 enum 한 줄이다."""

    MEASURED_BEFORE_EFFECTIVE_DATE = "MEASURED_BEFORE_EFFECTIVE_DATE"


@dataclass(frozen=True)
class Citable:
    """이 창의 판정을 현재 정책의 근거로 인용할 수 있다."""

    epoch: PolicyEpoch


@dataclass(frozen=True)
class NotCitable:
    """인용 불가 — 사유와 두 시각을 적은 문면을 함께 나른다(침묵 아님)."""

    reason: CitationRejectionReason
    detail: str


type PromotionCitation = Citable | NotCitable


def cite_promotion_window(
    *, policy: EvaluationPolicy, window_start: datetime
) -> PromotionCitation:
    """창 시작이 정책 effective date(KST 일 시작) 이후인가. naive datetime 은 UTC 로
    간주한다(`ml_engine.inference.maturity._ensure_utc`와 같은 계약)."""
    epoch = policy_epoch(policy)
    start = (
        window_start
        if window_start.tzinfo is not None
        else window_start.replace(tzinfo=UTC)
    )
    if start < epoch.effective_at:
        return NotCitable(
            reason=CitationRejectionReason.MEASURED_BEFORE_EFFECTIVE_DATE,
            detail=(
                f"창 시작 {start.isoformat()} 이 정책 "
                f"{policy.version} 의 효력 시작 {epoch.effective_at.isoformat()} 보다 앞선다"
            ),
        )
    return Citable(epoch=epoch)
