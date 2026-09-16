"""ml_engine.registry.policy — versioned 정책 YAML 로더. **값은 여기 없다**(D-M5-6, 5A 산출물
아님) — `load_policy`는 로드 형태만 만든다. 정책 값 33개의 분류는
`reports/evidence/m5/5a/policy-values.md`, 실제 YAML 은 5C·5D 가 만든다.

경계: 이 모듈은 어떤 정책 키 이름도 알지 못한다. `known_keys`가 없는 키는 조용히 무시되지
않고 `PolicyError`로 거부된다(v2-지침서.md §5 「missing/unknown feature, 지원하지 않는 schema
version 은 조용히 fallback 하지 않고 명시적 오류 계약으로 반환한다」와 같은 축).
"""

from __future__ import annotations

import dataclasses
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


def load_policy(path: Path, *, known_keys: frozenset[str] = frozenset()) -> Policy:
    """`path`의 YAML 을 읽어 `Policy`로 검증한다.

    - YAML 문법 자체가 깨졌으면(`yaml.YAMLError` 및 하위 클래스) 거부(D-5E3-1).
    - 최상위가 매핑이 아니면 거부.
    - `version`이 없거나 빈 문자열이면 거부.
    - `version` 밖의 키가 `known_keys`에 없으면 거부(미지 키 = fail-closed).

    M5/5E-3 D-5E3-1 — `yaml.safe_load`가 던지는 `yaml.YAMLError`(문법이 깨진 YAML,
    예: 닫히지 않은 flow sequence·탭 들여쓰기·미정의 anchor 참조)를 이 자리에서
    `PolicyError`로 정규화한다. 이 정규화가 뿌리(이 함수) 하나에 있으므로, 이 함수
    위에 얹힌 어떤 소비 로더(`training`·`evaluation`·`serving`·`inference`, 그리고
    앞으로 생길 로더)도 `yaml.YAMLError`를 별도로 잡을 필요가 없다 — 세 번 재발한
    사본(`OPEN-5C-YAML-ERROR-5D`)의 뿌리 처방(`tests/gates/
    test_policy_loaders_fail_closed.py`가 기계 수집으로 회귀를 지킨다)."""
    try:
        raw = yaml.safe_load(path.read_text(encoding="utf-8"))
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
    return Policy(version=version, values=values)
