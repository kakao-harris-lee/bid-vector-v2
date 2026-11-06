"""`ml_engine.evaluation.policy_source` — 정책 출처 재검증(M9/ml-lifecycle 수정 라운드,
verifier H-1 처방 둘째 겹).

앞 판의 ④ 축은 「`EvaluationPolicy` 를 짓는 **호출 이름**이 로더 안에 하나」를 AST 로 셌고, 그
술어는 세 경로를 전부 지나갔다(verifier 실측): `dataclasses.replace(p, paired_t_threshold=…)` ·
`type(p)(**{**vars(p), …})` · `getattr(importlib.import_module(…), "Evaluation"+"Policy")(…)`.
`evaluation.policy` 의 생성 토큰이 그 셋을 형태로 막지만(실측), 토큰 타입 이름을 **동적으로
훔치는** 길(`getattr(module, "_…")`)은 어떤 AST 게이트도 보지 못한다. 그 잔여를 **값 대조**로
닫는다 — 객체가 어떻게 만들어졌든 자기가 왔다는 파일과 값이 다르면 여기서 드러난다.

**경계**: 어느 **파일**을 정책으로 쓸지는 운영자가 고른다(`ML_ENGINE_EVALUATION_POLICY`) — 이
두 겹이 막는 것은 「코드가 값을 지어내거나 비트는 것」이고, 완화된 임계는 승인 근거와 version·
effective date 를 가진 **정책 산출물**로만 존재할 수 있다(그 지문이 report 에 남는다).

두 축을 함께 본다: **값**(`policy_checksum` 등식)과 **파일 바이트**(`digest` 등식). 뒤쪽이
없으면 「로드 뒤 파일이 바뀐 실행」이 통과한다 — 값은 같을 수 있어도 그 실행이 공시한 지문이
더는 그 파일의 것이 아니다.

이 모듈을 `policy.py` 에서 분리한 이유는 설계 래칫의 파일 크기 한도다(그 파일이 500 줄을
넘었다). 의존은 한 방향(`policy_source` → `policy`)이고 그래서 순환이 없다.

**부르는 자리는 제품 경로 하나**(`app.pipeline._run_holdout`) — `training.holdout.run_holdout`
은 라이브러리 진입점이라 커널 test 가 승인 밖 조합(seed 수를 줄인 정책 등)으로도 불러야
하고, 그런 정책은 로더가 거부하므로 거기서 재검증하면 그 test 들이 재는 것을 잃는다.

**승인 축(v H-2)** — 위 두 겹은 「객체가 자기 파일과 같은가」를 묻고 **「그 파일을 누가
승인했는가」는 묻지 않는다**. 그래서 완화한 사본을 파일로 만들어 쓰면 전 게이트가 초록이었다
(verifier 실측). 그 축은 따로, 겹 둘로 닫는다:

1. **뿌리 로더**(`registry.policy.load_policy`) — 등록된 version 인데 내용 지문이 다른 파일을
   거부한다. 편집 사본이 version 을 그대로 두는 것이 공격의 전제이므로 그 전제가 깨진다.
   **미등록 version 은 받는다** — 값 불변식 test 가 승인 밖 조합으로 로더를 계속 검사해야
   한다.
2. **조립 근**(아래 `load_approved_evaluation_policy`, `app.server._preload` 가 부른다) —
   미등록 version 도 거부한다. 그래서 `ML_ENGINE_EVALUATION_POLICY` 가 하는 일은 **승인된
   산출물 사이에서 고르기**다.

**왜 재검증이 아니라 부팅에서 승인을 묻는가**: 재검증이 있는 제품 경로는 조립 근 test 들도
탄다. 그 test 들은 작은 데이터셋으로 돌려고 출하와 다른 **승인 밖** 임계(`min_evaluation_rows`
등)를 파일로 만들어 쓴다. 승인 요구를 재검증에 넣으면 그 test 들이 전부 「미승인」으로 떨어져
**재던 것을 잃는다**(실측: 조립 근 test 일곱이 붉어졌다). 승인은 운영자의 선택이 들어오는
자리에서 물어야 하고 그 자리는 env 를 읽는 부팅이다.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from ml_engine.evaluation.policy import (
    EvaluationPolicy,
    PolicyRejected,
    PolicyRejectionReason,
    load_evaluation_policy,
    policy_checksum,
)
from ml_engine.registry.policy_approvals import policy_approval


@dataclass(frozen=True)
class PolicySourceMismatch:
    """정책 객체의 값이 자기가 왔다는 파일의 값과 다르다 — 예외가 아니라 결과 타입."""

    detail: str


def verify_policy_source(
    policy: EvaluationPolicy,
) -> EvaluationPolicy | PolicySourceMismatch:
    """정책 파일을 **다시 읽어** 같은 값이 나오는지 본다. 통과하면 받은 정책을 그대로
    돌려준다 — 조용히 파일 값으로 갈아끼우지 않는다(그러면 「무엇으로 판정했는가」가 다시
    흐려진다).

    축 둘을 본다: **파일 바이트**(로드 뒤 파일이 바뀌지 않았다)와 **값**(`policy_checksum`
    등식). 「그 파일이 승인된 산출물인가」는 여기서 묻지 않는다 — 그 축은
    `load_approved_evaluation_policy` 가 조립 근에서 진다(모듈 docstring 의 사유)."""
    reloaded = load_evaluation_policy(policy.source.path)
    if isinstance(reloaded, PolicyRejected):
        return PolicySourceMismatch(
            f"정책 파일을 다시 읽을 수 없다({reloaded.reason.value}): {reloaded.detail}"
        )
    if reloaded.source.digest != policy.source.digest:
        return PolicySourceMismatch(
            "정책 파일 바이트가 로드 시점과 다르다 — "
            f"공시 {policy.source.digest} != 현재 {reloaded.source.digest}"
        )
    if policy_checksum(reloaded) != policy_checksum(policy):
        return PolicySourceMismatch(
            "정책 값이 그 파일의 값과 다르다 — 로더를 거치지 않고 비튼 객체다"
        )
    return policy


def load_approved_evaluation_policy(path: Path) -> EvaluationPolicy | PolicyRejected:
    """**조립 근이 쓰는 로더**(v H-2) — 승인 등록부에 있는 산출물만 받는다.

    뿌리 로더는 등록된 version 의 편집 사본을 이미 거부하므로, 여기서 더 막는 것은
    **이름을 바꿔 단 사본**이다(그쪽은 자기 파일과 값이 같아 출처 재검증도 지난다 —
    틀린 것은 값이 아니라 「아무도 승인하지 않았다」는 사실이다).

    거부 사유를 `INVALID_VALUE` 로 내는 이유: 거부된 것은 **`version` 값**이다(그 이름의
    승인 기록이 없다). 새 사유 어휘를 더하면 그 어휘를 읽는 모든 자리가 함께 넓어지는데,
    여기서 필요한 것은 문면 하나다."""
    loaded = load_evaluation_policy(path)
    if isinstance(loaded, PolicyRejected):
        return loaded
    approval = policy_approval(version=loaded.version, digest=loaded.source.digest)
    if approval is not None:
        return PolicyRejected(PolicyRejectionReason.INVALID_VALUE, approval.detail)
    return loaded
