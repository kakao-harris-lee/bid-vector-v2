"""RED — M5/5E-3 D-5E3-1. `ml_engine.registry.policy.load_policy` 는 `yaml.safe_load`가
던지는 `yaml.YAMLError`(문법이 깨진 YAML)를 `PolicyError`로 감싼다 — 네 소비 로더
(training·evaluation·serving·inference)가 각자 사본으로 잡던 것을 뿌리 하나에서 잡는다
(`OPEN-5E-YAML-LOADER-INFERENCE` = `OPEN-5C-YAML-ERROR-5D` 종결). 정상 경로 test 는
`tests/gates/test_policy_loader.py`에 이미 있고 이 파일은 무편집이다 — 여기는 문법
오류 세 형태만 추가한다."""

from __future__ import annotations

from pathlib import Path

import pytest

from ml_engine.registry.policy import PolicyError, load_policy


def _write_yaml(tmp_path: Path, text: str) -> Path:
    path = tmp_path / "malformed.yaml"
    path.write_text(text, encoding="utf-8")
    return path


def test_unclosed_flow_sequence_is_policy_error_not_yaml_error(tmp_path: Path) -> None:
    """`yaml.parser.ParserError` 형태 — 닫히지 않은 flow sequence."""
    path = _write_yaml(tmp_path, "scenario.z: [unclosed\n")
    with pytest.raises(PolicyError):
        load_policy(path)


def test_tab_indentation_is_policy_error_not_yaml_error(tmp_path: Path) -> None:
    """`yaml.scanner.ScannerError` 형태 — 들여쓰기에 탭 사용(YAML 은 탭 들여쓰기를
    허용하지 않는다)."""
    path = _write_yaml(tmp_path, "version: v1\n\tbad: 1\n")
    with pytest.raises(PolicyError):
        load_policy(path)


def test_undefined_alias_is_policy_error_not_yaml_error(tmp_path: Path) -> None:
    """`yaml.composer.ComposerError` 형태 — 정의되지 않은 anchor 를 참조하는 alias."""
    path = _write_yaml(tmp_path, "version: v1\nfoo: *undefined\n")
    with pytest.raises(PolicyError):
        load_policy(path)


def test_malformed_yaml_error_never_raised_directly(tmp_path: Path) -> None:
    """세 형태 전부에서 raw `yaml.YAMLError`(또는 그 하위 클래스)가 새 나가지 않고
    `PolicyError`로 정규화된다는 것을 한 번에 확인한다(경계 test)."""
    import yaml

    malformed_texts = (
        "scenario.z: [unclosed\n",
        "version: v1\n\tbad: 1\n",
        "version: v1\nfoo: *undefined\n",
    )
    for index, text in enumerate(malformed_texts):
        path = tmp_path / f"malformed-{index}.yaml"
        path.write_text(text, encoding="utf-8")
        try:
            load_policy(path)
        except PolicyError:
            continue
        except yaml.YAMLError as exc:  # pragma: no cover — 회귀 발생 시에만 도달
            pytest.fail(f"yaml.YAMLError 가 PolicyError 로 정규화되지 않았다: {exc!r}")
        else:  # pragma: no cover — 회귀 발생 시에만 도달
            pytest.fail("malformed YAML 이 예외 없이 통과했다")
