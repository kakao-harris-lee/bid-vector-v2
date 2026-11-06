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
(verifier 실측). 그 축을 닫는 자리는 아래 `load_approved_evaluation_policy` 하나다 —
`app.server._preload` 가 부르고, 거기가 **운영자의 선택(env)이 들어오는 자리**다. 사유와
등록부는 `registry.policy_approvals` 모듈 docstring.

**왜 재검증(`verify_policy_source`)이 아닌가**: 재검증이 있는 제품 경로는 조립 근 test 들도
탄다. 그 test 들은 작은 데이터셋으로 돌려고 출하와 다른 **승인 밖** 임계(`min_evaluation_rows`
등)를 파일로 만들어 쓴다. 승인 요구를 재검증에 넣으면 그 test 들이 전부 「미승인」으로 떨어져
**재던 것을 잃는다**(실측: 조립 근 test 일곱이 붉어졌다).
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
    """게이트에 **닿은 값**이 승인된 산출물의 것인지 본다. 통과하면 받은 정책을 그대로
    돌려준다 — 조용히 파일 값으로 갈아끼우지 않는다(그러면 「무엇으로 판정했는가」가 다시
    흐려진다).

    축 **셋**을 본다. 셋이 함께 말하는 것은 「이 객체의 값은 **승인된 산출물**의 값이고,
    그 산출물은 지금도 디스크에 그대로 있다」다.

    1. **승인**(v r3 H-3a) — 객체가 **자기 주장**(`version` + `source.digest`)으로 승인
       등록부에 있는가. 재로드한 파일이 아니라 **주장**에 거는 이유는 아래 2 와 역할을
       가르기 위해서다: 2 는 「로드 뒤 파일이 바뀌었나」를 묻고, 1 은 「애초에 승인된
       것을 로드했다고 주장하나」를 묻는다.
    2. **파일 바이트** — 로드 뒤 그 파일이 바뀌지 않았다.
    3. **값**(`policy_checksum` 등식) — 객체의 값이 그 파일의 값이다.

    **1 이 없으면 무엇이 열리는가**(verifier MX1b 실측): `copy.copy` + `object.__setattr__`
    로 승인 정책을 비틀고, 그 객체가 가리킬 **내용이 일치하는 미승인 사본**을 디스크에 써
    두면 2·3 이 **둘 다 성립한다**. 조립 근의 승인 대조는 부팅에서 한 번뿐이라 그 뒤에
    값을 바꿔 끼우는 길을 보지 못하고, 호출 자리 집합 등식도 보지 못한다 — 그 변이는 호출
    자리를 하나도 늘리지 않는다. 그래서 승인은 **게이트에 닿는 자리**에서도 물어야 한다.
    """
    approval = policy_approval(version=policy.version, digest=policy.source.digest)
    if approval is not None:
        return PolicySourceMismatch(
            f"승인된 정책 산출물이 아니다({approval.reason.value}): {approval.detail}"
        )
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

    **이 자리가 두 갈래를 함께 거부한다**(cr r3 M-1 정정) — 이름을 유지한 편집 사본
    (`DIGEST_MISMATCH`)과 이름을 바꿔 단 사본(`UNREGISTERED_VERSION`). 뿌리 로더
    (`registry.policy.load_policy`)는 승인을 **묻지 않는다**(사유는
    `registry.policy_approvals` docstring). 앞 판 문면이 지문 갈래를 뿌리에 귀속했는데,
    그 검사는 같은 라운드에 이 자리로 옮겨졌다.

    **부팅에서 묻는 것이 충분하지 않다**(v r3 H-3a) — 부팅 뒤에 객체를 바꿔 끼우는 길이
    있다. 그래서 `verify_policy_source` 가 게이트 자리에서 다시 묻는다. 이 자리가 남아
    있는 이유는 **빨리 실패하는 것**이다: 미승인 정책을 가리킨 부팅은 job 을 받기 전에
    `NOT_READY` 로 서야 한다(게이트까지 가서 실패하면 그 사실이 job 실패로만 보인다).

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
