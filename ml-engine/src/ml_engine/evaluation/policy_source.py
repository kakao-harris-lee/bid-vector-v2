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
"""

from __future__ import annotations

from dataclasses import dataclass

from ml_engine.evaluation.policy import (
    EvaluationPolicy,
    PolicyRejected,
    load_evaluation_policy,
    policy_checksum,
)


@dataclass(frozen=True)
class PolicySourceMismatch:
    """정책 객체의 값이 자기가 왔다는 파일의 값과 다르다 — 예외가 아니라 결과 타입."""

    detail: str


def verify_policy_source(
    policy: EvaluationPolicy,
) -> EvaluationPolicy | PolicySourceMismatch:
    """정책 파일을 **다시 읽어** 같은 값이 나오는지 본다. 통과하면 받은 정책을 그대로
    돌려준다 — 조용히 파일 값으로 갈아끼우지 않는다(그러면 「무엇으로 판정했는가」가 다시
    흐려진다)."""
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
