"""RED — verifier r1 L-5. 설계 검토 (1) 표가 계획했던 「파라미터 단일 출처」 slice test —
`training/**`(`spec.py` 제외) 소스에 숫자 리터럴이 산포하지 않는지 AST 로 직접 센다.
하이퍼파라미터는 `TrainingSpec`(`spec.py`)에만 있어야 한다 — 다른 모듈에 `0.05`·`31`·
`40.0` 같은 값이 나타나면 이 test 가 그 산포를 잡는다.

허용 목록은 구조적 불변식·인덱싱·문자열 절단 상수뿐이다(사유는 각 파일 옆에)."""

from __future__ import annotations

import ast
from pathlib import Path

_TRAINING_SRC = Path(__file__).resolve().parents[2] / "src" / "ml_engine" / "training"
_EXCLUDED_FILES = frozenset({"spec.py", "__init__.py"})

# (파일, 값) — 구조적 불변식·인덱싱·문자열 절단 상수만 허용한다. 새 항목을 추가하려면
# 그 값이 왜 하이퍼파라미터가 아니라 구조 상수인지 이 표에 사유를 남긴다.
_ALLOWED: frozenset[tuple[str, float]] = frozenset(
    {
        ("corpus.py", 0.0),  # AwardRateLabel 도메인 하한 [0, 1]
        ("corpus.py", 1.0),  # AwardRateLabel 도메인 상한 [0, 1]
        ("corpus.py", 0),  # dict.get(reason, 0) 카운터 기본값
        ("corpus.py", 1),  # count + 1 증가
        ("dataset.py", 0),  # row_count 음수 거부(< 0)
        ("dataset.py", 80),  # 오류 메시지 미리보기 길이(stripped[:80])
        ("encoding_oof.py", 0),  # np.zeros((0, ...))·range 시작·dict.get 기본값
        ("encoding_oof.py", 1),  # count + 1 증가
        ("holdout.py", 0),  # 카운터 기본값(dict.get)·max(x, 0) 회계 불변식 하한
        ("holdout.py", 1),  # count + 1 증가
        ("_holdout_fit.py", 0),  # dropped_counts dict.get 기본값
        ("_holdout_fit.py", 1),  # count + 1 증가
        (
            "_holdout_window.py",
            0.0,
        ),  # 빈 배열 대비 fallback(coverage·test_mean·gate_rmse 초기값)
        (
            "_holdout_window.py",
            1,
        ),  # targets.size > 1(ddof=1 하한, residual.py 와 같은 상수)
        ("policy.py", 1),  # min_training_rows ≥ 1 불변식
        ("release.py", 16),  # release_id 를 sha256 앞 16 hex 로 자르는 길이
        ("residual.py", 1),  # residuals.size <= 1 하한
        ("train.py", 1),  # max(1, policy.min_training_rows) 코드 불변식
    }
)


def _numeric_literals(path: Path) -> list[tuple[int, float]]:
    tree = ast.parse(path.read_text(encoding="utf-8"))
    literals: list[tuple[int, float]] = []
    for node in ast.walk(tree):
        if (
            isinstance(node, ast.Constant)
            and type(node.value) in (int, float)
            and not isinstance(node.value, bool)
        ):
            literals.append((node.lineno, node.value))
    return literals


def test_training_modules_have_no_stray_numeric_literals_outside_allowlist() -> None:
    violations: list[str] = []
    for path in sorted(_TRAINING_SRC.glob("*.py")):
        if path.name in _EXCLUDED_FILES:
            continue
        for lineno, value in _numeric_literals(path):
            if (path.name, value) not in _ALLOWED:
                violations.append(f"{path.name}:{lineno} = {value!r}")
    assert not violations, (
        "training/** 에 허용 목록 밖 숫자 리터럴이 있다(파라미터는 spec.py 의 "
        f"TrainingSpec 에만 두어야 한다): {violations}"
    )


def test_allowlist_entries_are_still_present() -> None:
    """허용 목록에 죽은 항목(코드에서 이미 지워진 값)이 남지 않게 — 반대 방향 확인."""
    present: set[tuple[str, float]] = set()
    for path in sorted(_TRAINING_SRC.glob("*.py")):
        if path.name in _EXCLUDED_FILES:
            continue
        for _lineno, value in _numeric_literals(path):
            present.add((path.name, value))
    stale = _ALLOWED - present
    assert not stale, f"허용 목록에 더 이상 코드에 없는 항목이 있다: {stale}"
