"""`ml_engine.evaluation.policy_source` — **게이트가 판정에 쓰는 정책의 출처를 승인 파일로
고정하는 자리**(M9/ml-lifecycle 수정 라운드 v H-1 → r3 H-3a → r4 H-4).

세 라운드가 이 모듈의 역할을 세 번 넓혔다. 지금 형태와 **그렇게 된 사유**를 함께 적는다.

## 무엇을 하는가

`verify_policy_source` 가 축 셋을 보고, 통과하면 **로더가 승인 파일에서 다시 만든 정책**을
돌려준다. 제품 경로(`app.pipeline._run_holdout`)는 그 **반환값**을 게이트에 넘긴다.

| 축 | 묻는 것 | 없으면 열리는 것 |
| --- | --- | --- |
| **승인** | 객체의 **자기 주장**(`version` + `source.digest`)이 승인 등록부에 있는가 | 내용이 일치하는 **미승인 사본**(r3 H-3a — 전 게이트 초록으로 t=0.1 이 게이트에 닿았다) |
| **파일 바이트** | 로드 뒤 그 파일이 바뀌지 않았는가 | 「로드 뒤 파일이 바뀐 실행」 — 값은 같아도 공시한 지문이 더는 그 파일의 것이 아니다 |
| **값** | 객체의 값이 그 파일의 값인가(`policy_checksum` 등식) | 로더를 거치지 않고 비튼 객체(`replace`·동적 생성·토큰 절취 — `evaluation.policy` 의 생성 토큰이 앞에서 막고, 토큰 이름을 **동적으로 훔치는** 길의 잔여를 이 축이 받는다) |

**승인을 「재로드한 파일」이 아니라 「자기 주장」에 거는 이유**: 재로드에 걸면 승인 축이 바이트
축이 잡던 경우를 대신 거부해 **두 검사가 한 검사로 접힌다**. 주장에 걸면 셋이 각각 다른 것을
본다(그 분리를 지키는 것은 바이트 축 test 의 **문면 단언**이라는 것도 변이로 실측했다).

**축 셋으로도 모자랐던 이유, 그리고 반환값을 바꾼 이유**(r4 H-4): 세 축은 객체의 **직렬화**를
한 시점에 비교하고 게이트는 그 값을 **거동**으로 소비한다. 둘을 갈라 놓으면 세 축이 전부
통과한다 — `policy_checksum` 프레임 안에서만 승인값을 돌려주는 property · `repr`·json·`==` 는
승인값이고 `__neg__` 는 완화값인 **float 하위 클래스** · 통과 **뒤** 제자리 변경(verifier
F6·F7·F8). 축을 더 세는 것으로는 닫히지 않는다(직렬화를 보는 축은 몇 개든 같은 틈을 공유한다).
그래서 **값의 출처를 바꾼다**: 게이트가 쓰는 객체는 로더가 승인 바이트에서 만든 것이다.

## 승인을 묻는 자리는 **둘**이다

1. **조립 근** — `load_approved_evaluation_policy` ← `app.server._preload`. 운영자의 선택
   (`ML_ENGINE_EVALUATION_POLICY`)이 들어오는 자리이고, 역할은 **빨리 실패하는 것**이다:
   미승인 정책을 가리킨 부팅은 job 을 받기 전에 `NOT_READY` 로 서야 한다.
2. **게이트 자리** — `verify_policy_source` ← `app.pipeline._run_holdout`. 부팅 승인은 **한 번**
   묻고 끝나므로 그 뒤에 값을 바꿔 끼우는 길이 있다. 여기가 **도착한 값**을 묻는다.

**앞 판에 「재검증에는 넣을 수 없다」고 적었던 사유는 해소됐다**: 그때 붉어진 조립 근 test
일곱은 작은 데이터셋에 맞춘 **파생(미승인) 정책**을 쓰고 있었고, r3 에서 **출하 승인 정책**으로
바꿨다. 그 데이터셋은 성숙한 주가 0 이라 평가 창이 선택되지 않아 임계가 애초에 물리지 않는다
(근거는 `reports/evidence/m9/ml-lifecycle/design-review.md` §7). test 전용 승인 주입 경로는
만들지 않았다 — src 가 같은 주입을 쓸 수 있는 우회 표면이 된다.

## 경계

어느 **파일**을 정책으로 쓸지는 운영자가 고르지만 **승인된 산출물 사이에서만**이다. 완화된
임계는 승인 근거와 version·effective date 를 가진 정책 산출물로만 존재할 수 있고, 그 지문이
report 에 남는다. 출하 파일 자체를 고치는 길은 런타임이 아니라 **CI**(등록부 ↔ 출하 지문 등식
test)가 막는다. 위협 모델 경계 문장은 design-review §0.

**라이브러리 진입점은 그대로다** — `training.holdout.run_holdout` 은 커널 test 가 승인 밖
조합(seed 수를 줄인 정책 등)으로도 불러야 하므로 재검증을 그 안에 두지 않는다.

이 모듈을 `policy.py` 에서 분리한 이유는 설계 래칫의 파일 크기 한도다(그 파일이 500 줄을
넘었다). 의존은 한 방향(`policy_source` → `policy`)이고 그래서 순환이 없다.
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
    """축 셋을 보고, 통과하면 **로더가 승인 파일에서 다시 만든 정책**을 돌려준다.

    부르는 자리는 그 **반환값**을 게이트에 넘겨야 한다 — 받은 객체를 다시 넘기면 직렬화와
    거동을 갈라 놓은 위조가 게이트에 닿는다(v r4 H-4).

    축 셋의 역할 분담과 「왜 받은 객체를 그대로 돌려주지 않는가」는 **모듈 docstring** 에
    있다(이 함수가 그 설계의 전부라 사유를 한 자리에만 둔다).
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
    return reloaded


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
