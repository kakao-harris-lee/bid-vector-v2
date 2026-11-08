"""ml_engine.registry.policy — versioned 정책 YAML 로더. **값은 여기 없다**(D-M5-6, 5A 산출물
아님) — `load_policy`는 로드 형태만 만든다. 정책 값 33개의 분류는
`reports/evidence/m5/5a/policy-values.md`, 실제 YAML 은 5C·5D 가 만든다.

경계: 이 모듈은 어떤 정책 키 이름도 알지 못한다. `known_keys`가 없는 키는 조용히 무시되지
않고 `PolicyError`로 거부된다(v2-지침서.md §5 「missing/unknown feature, 지원하지 않는 schema
version 은 조용히 fallback 하지 않고 명시적 오류 계약으로 반환한다」와 같은 축).
"""

from __future__ import annotations

import dataclasses
import hashlib
from pathlib import Path

import yaml

PolicyScalar = str | int | float | bool


class PolicyError(Exception):
    """정책 로드 실패 — 조용한 fallback 없음(명시 오류 계약)."""


@dataclasses.dataclass(frozen=True)
class Policy:
    """로드된 정책 — `version` 필수, 나머지는 `known_keys`에 등재된 키만 담는다."""

    version: str
    values: dict[str, PolicyScalar]
    digest: str
    """읽은 정책 파일의 sha256 — **같은 읽기에서** 나온다(M9/ml-lifecycle, 수정 라운드
    v H-1). 호출부가 파일을 다시 읽어 지문을 내면 그 사이에 파일이 바뀔 수 있는 창이
    생기므로, 지문은 파싱에 쓴 바로 그 텍스트에서 만든다.

    **정확히는 「줄끝 정규화된 텍스트」의 UTF-8 바이트 해시다**(cr r3 L-1) —
    `read_text` 가 universal newline 변환을 하므로 **CRLF 사본은 바이트가 달라도 같은
    지문**이 된다(실측). LF 저장소의 출하 파일에서는 파일 바이트의 해시와 같다. 보안
    영향 0: 파싱도 **같은** 정규화 텍스트에서 하므로 지문과 값이 어긋날 자리가 없고,
    줄끝만 다른 YAML 은 같은 값으로 파싱된다.

    쓰는 쪽: `evaluation.policy` 가 이 지문을 `PolicySource` 에 실어 **자기 출처를
    재검증**한다(「임계 완화 불가」를 이름이 아니라 구조로 닫는 자리). 다른 세 로더는
    아직 쓰지 않는다 — 필드가 있어도 비용이 0 이고, 쓸 때 같은 자리에서 가져간다."""


def load_policy(path: Path, *, known_keys: frozenset[str] = frozenset()) -> Policy:
    """`path`의 YAML 을 읽어 `Policy`로 검증한다.

    - YAML 문법 자체가 깨졌으면(`yaml.YAMLError` 및 하위 클래스) 거부(D-5E3-1).
    - 최상위가 매핑이 아니면 거부.
    - `version`이 없거나 빈 문자열이면 거부.
    - `version` 밖의 키가 `known_keys`에 없으면 거부(미지 키 = fail-closed).

    **승인 대조는 여기 없다**(M9/ml-lifecycle 수정 라운드 2, v H-2). 「이 version 을 누가
    승인했는가」는 `registry.policy_approvals` 가 들고, 그것을 **묻는 자리**는 운영자의
    선택이 들어오는 조립 근이다(`evaluation.policy_source.load_approved_evaluation_policy`
    ← `app.server._preload`). 이 뿌리에서 물으면 승인 밖 조합으로 로더를 검사하는 값
    불변식 test 들이 재던 것을 잃는다(실측: 32 failed · 79 errors — 그 test 들은 현실적인
    version 이름으로 파생 정책을 쓰고, 하나는 그 이름이 인용 문면에 나오는 것을 단언한다).

    D-5E3-1 — `yaml.safe_load`가 던지는 `yaml.YAMLError`(문법이 깨진 YAML,
    예: 닫히지 않은 flow sequence·탭 들여쓰기·미정의 anchor 참조)를 이 자리에서
    `PolicyError`로 정규화한다. 이 정규화가 뿌리(이 함수) 하나에 있으므로, 이 함수
    위에 얹힌 어떤 소비 로더(`training`·`evaluation`·`serving`·`inference`, 그리고
    앞으로 생길 로더)도 `yaml.YAMLError`를 별도로 잡을 필요가 없다 — 세 번 재발한
    사본(`OPEN-5C-YAML-ERROR-5D`)의 뿌리 처방(`tests/gates/
    test_policy_loaders_fail_closed.py`가 기계 수집으로 회귀를 지킨다)."""
    raw_text = path.read_text(encoding="utf-8")
    digest = hashlib.sha256(raw_text.encode("utf-8")).hexdigest()
    try:
        raw = yaml.safe_load(raw_text)
    except yaml.YAMLError as exc:
        raise PolicyError(f"정책 파일 문법 오류: {path}: {exc}") from exc
    if not isinstance(raw, dict):
        raise PolicyError(f"정책 파일의 최상위는 매핑이어야 합니다: {path}")

    version = raw.get("version")
    if not isinstance(version, str) or not version:
        raise PolicyError(f"정책 파일에 'version' 문자열이 필요합니다: {path}")

    body_keys = set(raw) - {"version"}
    unknown_keys = body_keys - known_keys
    if unknown_keys:
        raise PolicyError(f"미지 정책 키 {sorted(unknown_keys)}: {path}")

    values: dict[str, PolicyScalar] = {key: raw[key] for key in body_keys}
    return Policy(version=version, values=values, digest=digest)
