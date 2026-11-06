"""승인 등록부 — **승인된 정책 version 은 그 내용의 지문과 함께만 존재한다**(v H-2).

verifier 가 실측한 구멍: 완화한 정책 사본을 코드가 만들어 쓰거나 `ML_ENGINE_EVALUATION_POLICY`
로 가리키면, version 을 `evaluation-v1` 로 남겨 둔 채 전 게이트가 초록이었다. 근원은 version
문자열이 **내용에 묶여 있지 않다**는 것이다.

여기 있는 test 는 그 묶음의 네 축을 잠근다:

1. **등록부 ↔ 출하 파일** 지문 등식 — yaml 을 고치면 등록부도 고쳐야 한다.
2. **출하 version 전수의 분할** — 등록 + 미등록 == 출하 정책 전수(새 정책 파일이 조용히
   어느 쪽도 아닌 상태로 들어올 수 없다).
3. **등록부 ↔ 승인 장부** — 승인된 지문이 그 version 의 승인 기록 문서에 적혀 있다.
4. **로더 거동** — 등록된 version 의 편집 사본은 거부, 무편집 사본은 경로가 달라도 통과.

축 1~3 이 걸려 있으므로 새 version 을 승인하는 길은 **세 자리를 같은 커밋에서** 바꾸는 것
뿐이다(yaml · 등록부 한 줄 · 승인 장부).
"""

from __future__ import annotations

import hashlib
from pathlib import Path
from typing import Final

import pytest
import yaml

from ml_engine.evaluation.policy import (
    EvaluationPolicy,
    PolicyRejected,
    load_evaluation_policy,
)
from ml_engine.registry.policy import PolicyError, load_policy
from ml_engine.registry.policy_approvals import (
    APPROVED_POLICY_DIGESTS,
    PolicyApprovalRejection,
    approved_digest,
    policy_approval,
)

_ML_ENGINE_ROOT: Final[Path] = Path(__file__).resolve().parents[2]
_REPO_ROOT: Final[Path] = _ML_ENGINE_ROOT.parent
_POLICY_DIR: Final[Path] = _ML_ENGINE_ROOT / "policy"

_APPROVAL_LEDGER_BY_VERSION: Final[dict[str, str]] = {
    "evaluation-v1": "m5/5c2/policy-values.md",
    "inference-v1": "m5/5d/policy-values.md",
}
"""승인된 version → 그 값을 승인한 장부. 아래 축 3 이 집합 등식으로 잠그므로 등록부에
version 을 더하면서 장부를 적지 않으면 붉다."""

_UNREGISTERED_SHIPPED_VERSIONS: Final[frozenset[str]] = frozenset(
    {
        "serving-v1",
        "training-v1",
        "strategy-backtest-v1",
        "strategy-backtest-v2",
    }
)
"""출하 정책 중 **일부러 등록하지 않은** version 전수.

등록은 이 slice 가 값을 바꾼 두 축에서 멈춘다 — 나머지를 넓히는 것은 각자의 승인 장부와
소비 경로를 함께 봐야 하는 별건이고, 6G 판정 입력(`strategy-backtest-v2`)을 건드리지
않는다는 이 slice 의 제약과 같은 자리다. 목록으로 적는 이유는 **새 정책 파일이 조용히
어느 쪽도 아닌 상태로 들어오지 못하게** 하는 것이다(축 2)."""


def _shipped_digests() -> dict[str, str]:
    """출하 정책 전수의 `version` → 파일 내용 sha256."""
    digests: dict[str, str] = {}
    for path in sorted(_POLICY_DIR.glob("*.yaml")):
        text = path.read_text(encoding="utf-8")
        version = yaml.safe_load(text)["version"]
        assert version not in digests, f"출하 정책 version 중복: {version}"
        digests[version] = hashlib.sha256(text.encode("utf-8")).hexdigest()
    return digests


# ── 축 1 — 등록부 ↔ 출하 파일 ────────────────────────────────────────────────


def test_every_registered_digest_matches_its_shipped_policy_file() -> None:
    shipped = _shipped_digests()
    for version, digest in APPROVED_POLICY_DIGESTS.items():
        assert version in shipped, f"등록된 version 의 출하 파일이 없다: {version}"
        assert shipped[version] == digest, (
            f"{version} 의 출하 내용이 승인된 지문과 다르다 — yaml 을 고쳤으면 "
            "등록부와 승인 장부도 같은 커밋에서 고쳐야 한다"
        )


def test_registered_digests_are_lowercase_hex_of_sha256_length() -> None:
    for version, digest in APPROVED_POLICY_DIGESTS.items():
        assert len(digest) == len(hashlib.sha256(b"").hexdigest()), version
        assert digest == digest.lower(), version
        assert all(char in "0123456789abcdef" for char in digest), version


# ── 축 2 — 출하 version 전수의 분할 ──────────────────────────────────────────


def test_shipped_versions_split_into_registered_and_deliberately_unregistered() -> None:
    """등록 + 미등록 == 출하 전수, 그리고 둘은 겹치지 않는다. 정책 파일을 더하면 어느
    쪽인지 **적어야** 한다 — 「아직 아무도 생각하지 않은」 상태로 들어오지 못한다."""
    shipped = frozenset(_shipped_digests())
    registered = frozenset(APPROVED_POLICY_DIGESTS)
    assert registered & _UNREGISTERED_SHIPPED_VERSIONS == frozenset()
    assert registered | _UNREGISTERED_SHIPPED_VERSIONS == shipped


# ── 축 3 — 등록부 ↔ 승인 장부 ───────────────────────────────────────────────


def test_every_registered_version_names_its_approval_ledger() -> None:
    assert frozenset(_APPROVAL_LEDGER_BY_VERSION) == frozenset(APPROVED_POLICY_DIGESTS)


@pytest.mark.parametrize("version", sorted(_APPROVAL_LEDGER_BY_VERSION))
def test_the_approved_digest_is_written_in_its_approval_ledger(version: str) -> None:
    """승인된 지문이 장부 문서에 **그 문자열로** 적혀 있다. 이것이 세 자리를 잇는 마지막
    고리다 — yaml 을 고치면 지문이 바뀌고, 지문이 바뀌면 등록부와 이 문서를 함께 고쳐야
    한다. 장부를 파싱하지 않고 **존재만** 보는 이유는 표 서식이 바뀌어도 이 고리가 끊기지
    않게 하는 것이다(장부 값 ↔ yaml 값 대조는 각 로더의 전용 test 가 이미 진다)."""
    ledger = _REPO_ROOT / "reports" / "evidence" / _APPROVAL_LEDGER_BY_VERSION[version]
    digest = APPROVED_POLICY_DIGESTS[version]
    assert digest in ledger.read_text(encoding="utf-8"), (
        f"{version} 의 승인 지문이 {_APPROVAL_LEDGER_BY_VERSION[version]} 에 없다"
    )


# ── 축 4 — 로더 거동 ────────────────────────────────────────────────────────


def _relaxed_copy(destination: Path) -> Path:
    """출하 평가 정책의 **임계만 완화한** 사본 — version 은 그대로 둔다(그것이 공격의
    전제다)."""
    original = (_POLICY_DIR / "evaluation-v1.yaml").read_text(encoding="utf-8")
    relaxed = original.replace("paired_t_threshold: 2.58", "paired_t_threshold: 0.1")
    assert relaxed != original, "완화 치환이 아무것도 바꾸지 못했다"
    destination.write_text(relaxed, encoding="utf-8")
    return destination


def test_an_edited_copy_of_an_approved_version_is_rejected(tmp_path: Path) -> None:
    rejected = load_evaluation_policy(_relaxed_copy(tmp_path / "evaluation-v1.yaml"))
    assert isinstance(rejected, PolicyRejected), rejected
    assert "승인된 지문과 다르다" in rejected.detail
    assert APPROVED_POLICY_DIGESTS["evaluation-v1"] in rejected.detail


def test_an_unedited_copy_at_another_path_still_loads(tmp_path: Path) -> None:
    """**음성 대조** — 막는 것은 「다른 경로」가 아니라 「다른 내용」이다. 운영자가
    승인된 산출물을 어디에 두고 가리키든 통과한다."""
    copy = tmp_path / "somewhere-else.yaml"
    copy.write_text(
        (_POLICY_DIR / "evaluation-v1.yaml").read_text(encoding="utf-8"),
        encoding="utf-8",
    )
    loaded = load_evaluation_policy(copy)
    assert isinstance(loaded, EvaluationPolicy), loaded
    assert loaded.source.digest == APPROVED_POLICY_DIGESTS["evaluation-v1"]


def test_the_root_loader_rejects_an_edited_copy_whatever_the_known_keys(
    tmp_path: Path,
) -> None:
    """거부는 **뿌리 로더**에 있다 — 소비 로더의 `known_keys` 와 무관하게, 등록된
    version 을 내건 다른 내용이면 거기서 끊긴다."""
    with pytest.raises(PolicyError, match="승인된 지문과 다르다"):
        load_policy(_relaxed_copy(tmp_path / "evaluation-v1.yaml"))


def test_an_unregistered_version_is_not_blocked_by_the_loader(tmp_path: Path) -> None:
    """**일부러 남긴 절반** — 미등록 version 은 뿌리 로더가 받는다. 값 불변식 test 가
    승인 밖 조합(seed 를 줄인 정책 등)으로 로더를 계속 검사해야 하기 때문이다. 미등록
    version 으로 **제품 게이트를 돌리는** 길은 조립 근이 막는다
    (`tests/app/test_server.py` 의 승인 산출물 축)."""
    path = tmp_path / "not-approved.yaml"
    path.write_text('version: "made-up-v9"\n', encoding="utf-8")
    loaded = load_policy(path)
    assert loaded.version == "made-up-v9"
    assert approved_digest("made-up-v9") is None


# ── 승인 대조 자체 ──────────────────────────────────────────────────────────


def test_policy_approval_reports_the_two_rejection_reasons() -> None:
    approved = "evaluation-v1"
    digest = APPROVED_POLICY_DIGESTS[approved]
    assert policy_approval(version=approved, digest=digest) is None

    mismatch = policy_approval(version=approved, digest="0" * len(digest))
    assert mismatch is not None
    assert mismatch.reason is PolicyApprovalRejection.DIGEST_MISMATCH

    unregistered = policy_approval(version="made-up-v9", digest=digest)
    assert unregistered is not None
    assert unregistered.reason is PolicyApprovalRejection.UNREGISTERED_VERSION


def test_the_registry_cannot_be_extended_through_the_mapping() -> None:
    """등록부는 읽기 전용 사상이다 — 코드가 항목을 끼워 넣어 자기 사본을 승인할 수
    없다(그 길은 **모듈 소스를 고쳐야** 열리고, 그러면 축 1~3 이 붉다)."""
    with pytest.raises(TypeError):
        APPROVED_POLICY_DIGESTS["made-up-v9"] = "0" * 64  # type: ignore[index]
