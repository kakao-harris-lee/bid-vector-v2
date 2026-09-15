"""RED — verifier r1 H-2 게이트 보강. 설계 검토 (5)-10 이 계획한 「public 시그니처
전수 test」는 낱개 인자 부재만 본다 — 이 test 는 5C-1
`tests/training/test_no_stray_numeric_literals.py`와 같은 방식으로
`evaluation/**` 소스에 숫자 리터럴이 산포하지 않는지 AST 로 직접 센다. 수정 전에는
`evaluation/**`가 이 게이트의 스캔 대상이 아니어서 `verdict.py`의
`policy.paired_t_threshold`를 리터럴 `2.58`로 바꿔도(우회 (9)) `design_ratchet`도
`pytest`도 이 산포를 잡지 못했다(재현: 변이 심고 `pytest tests -q` 630 passed).

허용 목록은 구조적 불변식·인덱싱·문자열 절단 상수뿐이다(사유는 각 항목 옆에) — 정책
임계 값(2.58·0.70·100·5·10·seed·band edge)은 전부 `EvaluationPolicy`를 통해서만
와야 하고, 이 목록에 그 값들이 등장하면 그 자체가 회귀다."""

from __future__ import annotations

import ast
from pathlib import Path

_EVALUATION_SRC = (
    Path(__file__).resolve().parents[2] / "src" / "ml_engine" / "evaluation"
)
_EXCLUDED_FILES = frozenset({"__init__.py"})

_ALLOWED: frozenset[tuple[str, float]] = frozenset(
    {
        ("baselines.py", 0.0),  # group_mean_predictions 카운터 초기값(total, count)
        ("baselines.py", 1),  # band 인덱스 증가(range(len(edges)))
        ("diagnostics.py", 0.0),  # 빈 배열 대비 fallback(coverage_splits 등 초기값)
        ("diagnostics.py", 1),  # n<=1 하한·count+1 증가
        ("diagnostics.py", 2),  # required_row_count 제곱(** 2) — 산식 자체의 지수
        ("policy.py", 0),  # paired_t_threshold<=0·stability_seeds 인덱스 시작
        ("policy.py", 1),  # max_origins<1·agency_baseline_min_count<1
        ("policy.py", 2),  # min_evaluation_rows<2(paired_t ddof=1 하한, 설계 검토 (16))
        (
            "policy.py",
            32,
        ),  # _MAX_INDEXED_LIST_LENGTH — 평탄 인덱스 키 상한(정적 구조 상수)
        ("scoring.py", 0.0),  # baseline_rmse<=0·std<=0.0 fallback
        ("scoring.py", 1),  # residuals.size>1(ddof=1 하한)
        ("scoring.py", 2),  # 제곱(residuals**2) — 산식 자체의 지수
        ("segments.py", 0),  # improvement_ratio<0 부호 비교
        ("segments.py", 1),  # row_count>1(1행 세그먼트 제외 규칙)
        ("verdict.py", 0.0),  # improvement_ratio>=0.0(UNDERPOWERED 부호 경계)
        ("verdict.py", 2),  # targets.size<2(NO_EVALUABLE_WINDOW 하한)
        ("windows.py", 0),  # opened_count==0(IMMATURE)·train_row_count<=0
        ("windows.py", 1),  # max_origins>0 슬라이스 경계
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


def test_evaluation_modules_have_no_stray_numeric_literals_outside_allowlist() -> None:
    violations: list[str] = []
    for path in sorted(_EVALUATION_SRC.glob("*.py")):
        if path.name in _EXCLUDED_FILES:
            continue
        for lineno, value in _numeric_literals(path):
            if (path.name, value) not in _ALLOWED:
                violations.append(f"{path.name}:{lineno} = {value!r}")
    assert not violations, (
        "evaluation/** 에 허용 목록 밖 숫자 리터럴이 있다(임계는 policy/evaluation-v1.yaml "
        f"에만 있어야 한다): {violations}"
    )


def test_allowlist_entries_are_still_present() -> None:
    """허용 목록에 죽은 항목(코드에서 이미 지워진 값)이 남지 않게 — 반대 방향 확인."""
    present: set[tuple[str, float]] = set()
    for path in sorted(_EVALUATION_SRC.glob("*.py")):
        if path.name in _EXCLUDED_FILES:
            continue
        for _lineno, value in _numeric_literals(path):
            present.add((path.name, value))
    stale = _ALLOWED - present
    assert not stale, f"허용 목록에 더 이상 코드에 없는 항목이 있다: {stale}"


def test_shipped_threshold_values_never_appear_as_literals() -> None:
    """출하 임계(policy-values.md §1)가 코드 리터럴로 새지 않았는지 직접 확인 —
    ML-07 acceptance ③(정책 산출물에 존재, 코드 리터럴 아님)의 회귀 방지."""
    shipped_thresholds = {2.58, 0.70, 100, 5, 10, 20260812, 1e8, 5e8, 1e9, 5e9}
    leaked: list[str] = []
    for path in sorted(_EVALUATION_SRC.glob("*.py")):
        if path.name in _EXCLUDED_FILES:
            continue
        for lineno, value in _numeric_literals(path):
            if value in shipped_thresholds:
                leaked.append(f"{path.name}:{lineno} = {value!r}")
    assert not leaked, f"출하 임계가 코드 리터럴로 나타난다: {leaked}"
