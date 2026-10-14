"""`tools/generate_contracts.py`(conftest 와 CLI 가 공유하는 생성 함수, D-5A-0 (b))로
두 가지를 한다.

(1) module 레벨에서 **즉시**(pytest collection 이전) `ml-engine/.contracts-generated/`
(패키지 트리 **밖**, VCS 밖 — F-2, gitignore)에 `contracts/proto`
전체(6개)를 생성한다 — `ml_engine.contracts` 재수출(`src/ml_engine/contracts/__init__.py`)이
이 자리를 `sys.path`에 얹고 절대 import하므로, 어느 test 파일이든 module 최상단에서
`ml_engine.contracts`를 import할 수 있고 pytest 는 conftest.py 를 그 형제 test 파일들보다
항상 먼저 로드하므로 여기서 미리 채운다.

(2) 기존 session fixture(`common_pb2`·`error_pb2`, 임시 디렉터리 + `sys.path` 삽입)는
그대로 남긴다 — 생성 로직만 공유 함수로 바꿨을 뿐 동작은 같다. 2B~2E 는 각자 독립 module-scope
fixture로 생성하므로(이 파일을 건드리지 않는다) 회귀 없음.

(3) 인수 — hypothesis `ci` 프로파일을 루트로 승격(`policy-values.md` §「hypothesis
`ci` 프로파일은 프로세스 전역이다」 인수). `tests/features/conftest.py`는 삭제한다 — 이제
`training/**`의 hypothesis test 도 같은 프로파일을 물려받아야 하고, 하위 디렉터리 conftest
는 그 필요를 덮지 못한다(스위트 전체 스코프가 필요). `deadline=None`은 CI 러너의 비결정적
처리 시간 변동이 `DeadlineExceeded`로 test 를 실패시키는 것을 막는다.
"""

from __future__ import annotations

import os
import sys
import tempfile
from collections.abc import Iterator
from pathlib import Path

import pytest
from hypothesis import settings

from tools.generate_contracts import generate

settings.register_profile("ci", deadline=None, max_examples=50)
settings.load_profile(os.environ.get("HYPOTHESIS_PROFILE", "ci"))

_ML_ENGINE_ROOT = Path(__file__).resolve().parents[1]
# 패키지 트리 밖(D-5A-0 (b) 정정, F-2) — `src/ml_engine/contracts/` 안이 아니라
# `ml-engine/` 바로 아래라 `ml_engine.contracts.<하위>` 라는 Python import 경로 자체가 없다.
_CONTRACTS_GENERATED_DIR = _ML_ENGINE_ROOT / ".contracts-generated"
_PROTO_FILES_2A = (
    "bidvector/ml/v1/common.proto",
    "bidvector/ml/v1/error.proto",
)

_CONTRACTS_GENERATED_DIR.mkdir(parents=True, exist_ok=True)
generate(_CONTRACTS_GENERATED_DIR)


@pytest.fixture(scope="session")
def generated_stub_path() -> Iterator[Path]:
    """`common.proto`·`error.proto`만 임시 디렉터리에 생성하고 `sys.path`에 얹는다(2A 관례
    그대로 — 이 fixture 를 쓰는 round-trip test 는 `.contracts-generated`가 아니라 이 임시
    사본을 쓴다). 생성물은 세션 종료 시 디렉터리째 삭제된다."""
    with tempfile.TemporaryDirectory(prefix="bidvector-ml-contract-py-") as tmp:
        out_dir = Path(tmp)
        generate(out_dir, proto_files=_PROTO_FILES_2A)
        sys.path.insert(0, str(out_dir))
        try:
            yield out_dir
        finally:
            sys.path.remove(str(out_dir))


@pytest.fixture(scope="session")
def common_pb2(generated_stub_path: Path):
    from bidvector.ml.v1 import common_pb2 as module

    return module


@pytest.fixture(scope="session")
def error_pb2(generated_stub_path: Path):
    from bidvector.ml.v1 import error_pb2 as module

    return module


# ---- 저널 쓰기 감사 훅(M6/6B-4 vr H-2 · cr M-1) ----
#
# 술어와 기록은 `tests/journal_audit.py` 가 가진다(이름이 유일해야 한다 — `import conftest`
# 는 `tests/training/conftest.py` 로도 해석돼 모호하다, 실측). 이 파일은 **설치**와 **수집
# 순서**만 진다: 감사 훅은 제거할 수 없으므로 세션 시작에 한 번 설치하고, 세션 전역 단언
# test 를 맨 뒤로 돌린다.

from journal_audit import (  # noqa: E402 — 위 생성 블록 뒤여야 한다
    JOURNAL_WRITE_AUDIT,
    WHOLE_SESSION_AUDIT_TEST,
    JournalWriteAudit,
)

sys.addaudithook(JOURNAL_WRITE_AUDIT.hook)


@pytest.fixture(scope="session")
def journal_write_audit() -> JournalWriteAudit:
    """세션 내내 켜져 있는 감사 훅의 기록(`tests/gates/test_journal_write_audit.py`)."""
    return JOURNAL_WRITE_AUDIT


def pytest_collection_modifyitems(items: list[pytest.Item]) -> None:
    """세션 전역 감사 단언을 **맨 뒤로** 돌린다.

    그 test 가 제 자리(`tests/gates/`)에서 돌면 **그 시점까지**의 위반만 본다 — 뒤에 도는
    `tests/serving`·`tests/training` 에서 생긴 위반은 이미 지나간 test 를 붉힐 수 없다.
    `pytest_sessionfinish` 는 대안이 아니다: 그 자리에서 `session.exitstatus` 를 바꿔도
    **종료 코드가 0 으로 남는다**(실측 — 메시지는 찍히고 exit 0). 「게이트 결과는 종료
    코드로」 규율대로, 판정은 수집 순서를 바꿔 **보통의 test 실패**로 만든다."""
    tail = [item for item in items if item.name == WHOLE_SESSION_AUDIT_TEST]
    for item in tail:
        items.remove(item)
    items.extend(tail)
