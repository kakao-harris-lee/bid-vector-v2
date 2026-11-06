"""`ml_engine.registry.policy_approvals` — **승인된 정책 version → 그 파일의 sha256**
(M9/ml-lifecycle 수정 라운드 2, verifier H-2 · 팀장 계약 갱신 2026-11-06).

**무엇이 열려 있었나**: 앞 판의 두 겹(생성 토큰 · 출처 재검증)은 「코드가 정책 **객체**를
비트는 것」을 닫았지만, 로더와 재검증이 **어느 파일이든** 받았다. 그래서 완화한 사본을
파일로 만들어 쓰면 전 게이트가 초록이었다(verifier 실측: `paired_t_threshold` 0.1,
version 은 그대로 `evaluation-v1`, 전건 1700 passed, 재검증도 통과). 코드 변경 없이
`ML_ENGINE_EVALUATION_POLICY` 를 편집 사본으로 돌려도 같다.

**근원은 version 문자열이 내용에 묶여 있지 않다는 것**이다. 그래서 ML-07 수용 문면 둘이
동시에 깨졌다 — 「환경변수로 값을 주입해도 판정이 달라지지 않는다」와 「정책 version 이
바뀌면 그 이전 판정과 이후 판정이 구분된다」(같은 version 에서 판정이 달랐다).

**처방은 열거의 방향을 뒤집는 것이다**: 「무엇을 거부하는가」가 아니라 **「무엇이 승인됐는가」**
를 코드 상수로 적는다. 승인된 version 은 **그 내용의 지문**과 함께만 존재한다.

겹 둘로 쓴다(둘 다 이 저장소의 기존 두 층 규율대로 — 뿌리에서 거부, 제품 경로에서 거부):

1. **로더(뿌리, `registry.policy.load_policy`)** — 등록된 version 인데 내용 지문이 다르면
   `PolicyError`. 편집 사본은 version 을 그대로 두는 것이 공격의 전제이므로 이 한 줄이
   그 전제를 깬다. **미등록 version 은 로더가 받는다** — 그래야 값 불변식 test 가
   (승인 밖 조합으로) 로더를 계속 검사할 수 있다.
2. **제품 경로(`evaluation.policy_source.verify_policy_source`)** — 미등록 version 도
   거부한다. 게이트 판정은 **승인된 산출물로만** 돈다. env 가 하는 일은 승인된 산출물
   **사이에서 고르기**이고, 그것이 이 slice 의 경계 문면이다.

**새 version 을 승인하는 길은 세 자리를 같은 커밋에서 바꾸는 것뿐이다** — ⑴ `policy/*.yaml`
⑵ 아래 등록부 한 줄 ⑶ 승인 장부(`reports/evidence/m5/5c2/policy-values.md` ·
`reports/evidence/m5/5d/policy-values.md`). 셋이 어긋나면 test 가 붉다.

**범위**: 이 slice 가 **값을 바꾼 두 version** 만 등록한다. `serving-v1`·`training-v1`·
`strategy-backtest-v1/v2` 는 미등록이고, 그래서 로더가 지문을 묻지 않는다 — 그 축을
넓히는 것은 각자의 승인 장부와 소비 경로를 함께 봐야 하는 별건이다(6G 판정 입력을
건드리지 않는다는 이 slice 의 제약과도 같은 자리). 그 사실을 test 가 **목록으로** 잠근다.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import StrEnum
from types import MappingProxyType

APPROVED_POLICY_DIGESTS: MappingProxyType[str, str] = MappingProxyType(
    {
        # evaluation-v1 — 승인 장부 `reports/evidence/m5/5c2/policy-values.md` §1(값 12).
        # M9/ml-lifecycle 이 `effective_date` 칸을 더했고 같은 라운드에 그 장부에 등재했다.
        "evaluation-v1": (
            "fba628efb97b313ff511504e942a11ca5dc709c454a34ff73d84b09ae4670855"
        ),
        # inference-v1 — 승인 장부 `reports/evidence/m5/5d/policy-values.md`.
        # M9/ml-lifecycle 이 `maturity.time_axis_substitute`(닫힌 어휘)를 더했다.
        "inference-v1": (
            "1d8a8cf6d5fbfa495e9503c003029dd29f05fd3b63f924ac5e58dfe35ada3b96"
        ),
    }
)
"""승인된 정책 version → 그 파일 내용의 sha256(hex, 소문자 64자).

지문은 `registry.policy.Policy.digest` 와 **같은 규칙**이다 — 파싱에 쓴 텍스트의 UTF-8
바이트 해시. 주석·공백도 내용이다(그 자리에 승인 근거가 적히므로 의도된 성질이다)."""


class PolicyApprovalRejection(StrEnum):
    """승인 대조 실패 사유 — 닫힌 어휘."""

    UNREGISTERED_VERSION = "UNREGISTERED_VERSION"
    """등록부에 없는 version. 로더는 받고(값 불변식 test 를 위해) 제품 경로가 거부한다."""

    DIGEST_MISMATCH = "DIGEST_MISMATCH"
    """등록된 version 인데 내용이 승인된 지문과 다르다 — 편집 사본이다."""


@dataclass(frozen=True)
class PolicyNotApproved:
    """예외가 아니라 결과 타입(이 저장소의 거부 어휘와 같은 축)."""

    reason: PolicyApprovalRejection
    detail: str


def approved_digest(version: str) -> str | None:
    """등록된 version 의 승인 지문, 미등록이면 `None`."""
    return APPROVED_POLICY_DIGESTS.get(version)


def policy_approval(*, version: str, digest: str) -> PolicyNotApproved | None:
    """승인 대조 — 통과하면 `None`. 「등록돼 있고 지문이 같다」만 승인이다."""
    expected = approved_digest(version)
    if expected is None:
        return PolicyNotApproved(
            reason=PolicyApprovalRejection.UNREGISTERED_VERSION,
            detail=f"승인 등록부에 없는 정책 version: {version!r}",
        )
    if expected != digest:
        return PolicyNotApproved(
            reason=PolicyApprovalRejection.DIGEST_MISMATCH,
            detail=(
                f"정책 version {version!r} 의 내용이 승인된 지문과 다르다 — "
                f"승인 {expected} != 이 파일 {digest}"
            ),
        )
    return None
