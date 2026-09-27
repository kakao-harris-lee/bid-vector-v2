"""RED — verifier r1 H-2 게이트 보강. 설계 검토 (5)-10 이 계획한 「public 시그니처
전수 test」는 낱개 인자 부재만 본다 — 이 test 는 5C-1
`tests/training/test_no_stray_numeric_literals.py`와 같은 방식으로
`evaluation/**` 소스에 숫자 리터럴이 산포하지 않는지 AST 로 직접 센다. 수정 전에는
`evaluation/**`가 이 게이트의 스캔 대상이 아니어서 `verdict.py`의
`policy.paired_t_threshold`를 리터럴 `2.58`로 바꿔도(우회 (9)) `design_ratchet`도
`pytest`도 이 산포를 잡지 못했다(재현: 변이 심고 `pytest tests -q` 630 passed).

허용 목록은 구조적 불변식·인덱싱·문자열 절단 상수뿐이다(사유는 각 항목 옆에) — 정책
임계 값(2.58·0.70·100·5·10·seed·band edge)은 전부 `EvaluationPolicy`를 통해서만
와야 하고, 이 목록에 그 값들이 등장하면 그 자체가 회귀다.

M6/6G 보강 — 스캔을 `glob("*.py")`(직계만)에서 `rglob`(하위 패키지 포함)으로 넓혔다.
`evaluation/backtest/**`(6G 전략 백테스트)가 직계가 아니라서, 고치기 전에는 그 안의
판정 임계를 리터럴로 적어도 이 게이트가 보지 못했다 — **하위 패키지 하나를 만드는 것이
게이트를 우회하는 길**이었다. 허용 목록 키도 파일명에서 `evaluation/` 상대 POSIX
경로로 바꿨다(직계 파일의 키는 그대로다 — `baselines.py` 등). 6G 승인 임계
(0.20·0.05·3·0.01·0.80·483·15·4·0.30·0.995·0.98)도
`test_shipped_threshold_values_never_appear_as_literals` 의 대상에 넣었다.

M6/6G r1 보강 둘(D-6G-33 — verifier r1 M-6·G1 변이, code-review r1 M-10):
1. **술어가 AST 숫자 상수뿐이었다.** `float("0.05")`·`Decimal("0.05")` 처럼 **문자열로**
   적으면 게이트가 초록이었다(verifier 가 변이 G1 로 실측). 이제 그 두 생성자의 문자열
   인자도 수로 읽어 같은 자리에서 센다 — 숫자를 문자열에 숨기는 길이 닫힌다.
2. **뿌리가 `evaluation/**` 뿐이었다.** D-6G-8 이 S2 주입을 두라고 한 조립 근
   (`app/backtest_job.py`·`app/backtest_distribution.py`)은 스캔 밖이라 거기 임계를
   적으면 보이지 않았다. 두 파일을 뿌리에 더했다 — `app/` 전체가 아니라 **6G 모듈
   둘**이다(기존 조립 근은 이 slice 의 것이 아니고, 넓히면 허용 목록이 그 파일들의
   구조 상수로 뒤덮여 게이트가 읽히지 않는다).

허용 목록 키의 기준 경로가 `evaluation/` 에서 **`ml_engine/`** 으로 바뀌었다(뿌리가
둘이 되어 한 기준이 필요하다) — 기존 항목은 앞에 `evaluation/` 이 붙은 것 말고는 그대로다."""

from __future__ import annotations

import ast
from decimal import Decimal
from fractions import Fraction
from pathlib import Path
from typing import Final

_ML_ENGINE_SRC = Path(__file__).resolve().parents[2] / "src" / "ml_engine"
_EVALUATION_SRC = _ML_ENGINE_SRC / "evaluation"
_BACKTEST_SRC = _EVALUATION_SRC / "backtest"
_EXCLUDED_FILES = frozenset({"__init__.py"})
# 문자열 안에 수를 숨기는 두 생성자 — `float("0.05")` 가 AST 숫자 상수가 아니라서
# 앞 판의 술어를 그대로 지나갔다(verifier r1 변이 G1).
# 문자열에 숨긴 수를 **이름 열거 없이** 잡는다(D-6G-43, verifier r2 M-c). 앞 판은
# `float(...)`·`Decimal(...)` 두 이름만 봐서 별칭(`Decimal as D`)·`json.loads("0.05")`·
# 연결(`"0." + "05"`)을 전부 지나갔다. 이제 **AST 의 모든 문자열 상수**를 수로 읽어
# 보고, 읽히면 같은 자리에서 센다 — 호출 이름이 무엇이든, 아예 호출이 아니든 상관없다.
# docstring 만 뺀다(설명 문장의 수는 값이 아니다).


def _docstring_nodes(tree: ast.AST) -> set[int]:
    """module·class·function 의 첫 문장 문자열 — 설명이지 값이 아니다."""
    marked: set[int] = set()
    for node in ast.walk(tree):
        if not isinstance(
            node, (ast.Module, ast.ClassDef, ast.FunctionDef, ast.AsyncFunctionDef)
        ):
            continue
        body = node.body
        if (
            body
            and isinstance(body[0], ast.Expr)
            and isinstance(body[0].value, ast.Constant)
            and isinstance(body[0].value.value, str)
        ):
            marked.add(id(body[0].value))
    return marked


_PARSER_INT_BASES: Final[tuple[int, ...]] = (0, 10)
"""`int` 의 진법 — 0(접두사 존중: `0x`·`0o`·`0b`)과 10 만 쓴다.

2~36 을 다 돌면 `int("abc", 36)` 이 13368 이라 **모든 알파벳 문자열이 수가 된다** —
술어가 모든 것을 잡으면 아무것도 잡지 못한다. 접두사 있는 진법 표기는 base 0 이
받으므로 「진법 포함」은 이것으로 닫힌다."""


def _parsed_number(raw: str) -> object | None:
    """문자열이 **Python 수 파서 가운데 하나라도** 받으면 그 수, 아니면 `None`.

    앞 판은 `float` 하나였다. verifier r3 M-5 가 그 열거의 사각 여섯을 실측했다 —
    `Fraction("1/60")` · `float.fromhex("0x1.1p-6")` · `complex("0.0166j")` 는
    `float()` 이 거부하지만 **전부 수로 읽힌다**. 파서 하나를 고르는 순간 나머지가
    문이 된다."""
    for parse in (
        float,
        Decimal,
        Fraction,
        complex,
        float.fromhex,
    ):
        try:
            return parse(raw)  # type: ignore[operator]
        except (ValueError, ArithmeticError, TypeError):
            continue
    for base in _PARSER_INT_BASES:
        try:
            return int(raw, base)
        except (ValueError, TypeError):
            continue
    return None


def _as_number(value: object) -> object | None:
    """`str`·`bytes` 상수가 수로 읽히면 그 수, 아니면 `None`.

    `bytes` 를 `str` 과 같이 본다 — `float(b"0.05")` 가 그냥 된다(verifier r3 M-5 의
    `G5_bytes`·`G10_shipped_bytes`). 「문자열만」은 접두사 `b` 하나로 열리는 문이었다."""
    if isinstance(value, bytes):
        try:
            value = value.decode("utf-8", errors="strict")
        except UnicodeDecodeError:
            return None
    if not isinstance(value, str):
        return None
    return _parsed_number(value)


def _folded(node: ast.expr) -> object | None:
    """**상수만으로 이루어진 표현식**을 접어서 값을 낸다. 이름·호출·속성이 하나라도
    섞이면 `None`(실행 시점 값이므로 이 게이트의 대상이 아니다).

    이것이 있어야 `float("0.0166x"[:-1])` 같은 자리가 닫힌다(verifier r3 M-5 의
    `G9`). 상수 자체는 수로 읽히지 않지만 **코드가 상수만으로 수를 만든다** — 접어
    보지 않으면 보이지 않는다. 파서 **이름**을 열거해 호출을 찾는 방식은 별칭
    (`from builtins import float as f`) 하나로 열리므로 쓰지 않는다: 접는 쪽은
    호출이 무엇이든 상관하지 않는다."""
    if isinstance(node, ast.Constant):
        return node.value
    if isinstance(node, ast.UnaryOp) and isinstance(node.op, (ast.UAdd, ast.USub)):
        # `-1` 은 상수가 아니라 `UnaryOp(USub, Constant(1))` 이다. 이것을 접지 않으면
        # `"0.0166x"[:-1]` 의 경계가 접히지 않는다(verifier r3 G9 가 그래서 초록이었다).
        operand = _folded(node.operand)
        if not isinstance(operand, (int, float)) or isinstance(operand, bool):
            return None
        return -operand if isinstance(node.op, ast.USub) else +operand
    if isinstance(node, ast.BinOp) and isinstance(node.op, (ast.Add, ast.Mult)):
        left, right = _folded(node.left), _folded(node.right)
        if left is None or right is None:
            return None
        try:
            return left + right if isinstance(node.op, ast.Add) else left * right  # type: ignore[operator]
        except TypeError:
            return None
    if isinstance(node, ast.Subscript):
        target = _folded(node.value)
        if target is None or not isinstance(target, (str, bytes)):
            return None
        index = node.slice
        try:
            if isinstance(index, ast.Slice):
                bounds: list[int | None] = []
                for part in (index.lower, index.upper, index.step):
                    if part is None:
                        bounds.append(None)
                        continue
                    folded = _folded(part)
                    # **접지 못하면 포기한다.** 앞 판은 여기서 `None` 을 그대로 경계로
                    # 써서 「접지 못한 슬라이스」가 「경계 없는 슬라이스」가 됐다 —
                    # `[:-1]` 이 전체 문자열로 접혀 술어를 지나갔다.
                    if not isinstance(folded, int) or isinstance(folded, bool):
                        return None
                    bounds.append(folded)
                return target[slice(*bounds)]  # type: ignore[index]
            position = _folded(index)
            if not isinstance(position, int):
                return None
            return target[position]
        except (IndexError, TypeError, ValueError):
            return None
    return None


def _module_name(path: Path) -> str:
    relative = path.relative_to(_ML_ENGINE_SRC).with_suffix("")
    parts = [part for part in relative.parts if part != "__init__"]
    return ".".join(("ml_engine", *parts))


def _imported_modules(path: Path) -> set[str]:
    """그 파일이 import 하는 `ml_engine.*` 모듈 이름들(상대 import 포함)."""
    names: set[str] = set()
    tree = ast.parse(path.read_text(encoding="utf-8"))
    package = _module_name(path).rsplit(".", 1)[0]
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            names.update(alias.name for alias in node.names)
        elif isinstance(node, ast.ImportFrom):
            root = node.module or ""
            if node.level:
                base = (
                    package.rsplit(".", node.level - 1)[0]
                    if node.level > 1
                    else package
                )
                root = f"{base}.{root}" if root else base
            names.add(root)
            names.update(f"{root}.{alias.name}" for alias in node.names)
    return {name for name in names if name.startswith("ml_engine.")}


def _app_roots() -> list[Path]:
    """`evaluation/backtest/**` 를 (간접으로라도) import 하는 `app/**` 모듈 전부.

    **파일명을 적지 않는다.** 앞 판은 6G 조립 근 두 개를 이름으로 열거했다 —
    `app/` 에 파일 하나를 더 만드는 것이 게이트를 우회하는 길이었다(cr r3 L-4).
    이제 import 그래프에서 도출하므로, 백테스트를 import 하는 파일은 **만들어지는
    순간** 대상이 된다."""
    app_dir = _ML_ENGINE_SRC / "app"
    if not app_dir.is_dir():
        return []
    graph = {path: _imported_modules(path) for path in sorted(app_dir.rglob("*.py"))}
    backtest = _module_name(_BACKTEST_SRC)
    roots = {
        path
        for path, imports in graph.items()
        if any(name.startswith(backtest) for name in imports)
    }
    while True:
        names = {_module_name(path) for path in roots}
        grown = roots | {
            path
            for path, imports in graph.items()
            if any(name in names for name in imports)
        }
        if grown == roots:
            return sorted(roots)
        roots = grown


def _scanned_paths() -> list[Path]:
    """`evaluation/**` 전부(하위 패키지 포함) + **백테스트를 import 하는 `app/**`**.

    `evaluation/**` 는 `evaluation/backtest/**` 의 상위집합이라 계약이 요구한 뿌리를
    덮는다(좁히면 5C-2 모듈들의 기존 보호가 사라진다). `app/` 쪽만 그래프에서
    도출한다 — 거기가 열거였던 자리다."""
    paths = list(_EVALUATION_SRC.rglob("*.py"))
    paths.extend(_app_roots())
    return sorted(
        set(paths), key=lambda path: path.relative_to(_ML_ENGINE_SRC).as_posix()
    )


def _key(path: Path) -> str:
    return path.relative_to(_ML_ENGINE_SRC).as_posix()


_ALLOWED_ENTRIES: tuple[tuple[str, float], ...] = (
    # **튜플이지 집합이 아니다**(cr r3 L-5). Python 은 `0 == 0.0` 이라 집합 리터럴이
    # 두 항목을 하나로 접었고, 주석 둘이 한 원소를 설명하며
    # `test_allowlist_entries_are_still_present` 가 둘을 가르지 못했다. 아래
    # `_ALLOWED` 가 `repr` 로 타입을 보존해 키를 만든다.
    (
        "evaluation/baselines.py",
        0.0,
    ),  # group_mean_predictions 카운터 초기값(total, count)
    ("evaluation/baselines.py", 1),  # band 인덱스 증가(range(len(edges)))
    ("evaluation/baselines.py", 0),  # 같은 초기값 쌍의 정수 쪽(count)
    (
        "evaluation/diagnostics.py",
        0.0,
    ),  # 빈 배열 대비 fallback(coverage_splits 등 초기값)
    ("evaluation/diagnostics.py", 1),  # n<=1 하한·count+1 증가
    ("evaluation/diagnostics.py", 0),  # row_count<=0·분모 0 경계
    (
        "evaluation/diagnostics.py",
        2,
    ),  # required_row_count 제곱(** 2) — 산식 자체의 지수
    (
        "evaluation/policy.py",
        0,
    ),  # paired_t_threshold<=0·stability_seeds 인덱스 시작
    ("evaluation/policy.py", 1),  # max_origins<1·agency_baseline_min_count<1
    ("evaluation/policy.py", 0.0),  # maturity_threshold 하한(0.0<x)
    ("evaluation/policy.py", 1.0),  # maturity_threshold 상한(x<=1.0)
    (
        "evaluation/policy.py",
        2,
    ),  # min_evaluation_rows<2(paired_t ddof=1 하한, 설계 검토 (16))
    (
        "evaluation/policy.py",
        32,
    ),  # _MAX_INDEXED_LIST_LENGTH — 평탄 인덱스 키 상한(정적 구조 상수)
    ("evaluation/scoring.py", 0.0),  # baseline_rmse<=0·std<=0.0 fallback
    ("evaluation/scoring.py", 1),  # residuals.size>1(ddof=1 하한)
    ("evaluation/scoring.py", 0),  # baseline_rmse<=0 경계(정수 쪽)
    ("evaluation/scoring.py", 2),  # 제곱(residuals**2) — 산식 자체의 지수
    ("evaluation/segments.py", 0),  # improvement_ratio<0 부호 비교
    ("evaluation/segments.py", 1),  # row_count>1(1행 세그먼트 제외 규칙)
    (
        "evaluation/verdict.py",
        0.0,
    ),  # improvement_ratio>=0.0(UNDERPOWERED 부호 경계)
    ("evaluation/verdict.py", 2),  # targets.size<2(NO_EVALUABLE_WINDOW 하한)
    ("evaluation/windows.py", 0),  # opened_count==0(IMMATURE)·train_row_count<=0
    ("evaluation/windows.py", 1),  # max_origins>0 슬라이스 경계
    # ── M6/6G `evaluation/backtest/**` ────────────────────────────────────
    # 아래 여섯은 전부 **구조적 불변식·산식 자체의 눈금**이고, 판정 임계는 하나도
    # 없다(임계는 policy/strategy-backtest-v1.yaml 에만 있다).
    ("evaluation/backtest/exclusions.py", 0),  # 계수 초기값
    ("evaluation/backtest/exclusions.py", 0.0),  # 빈 표본의 채움률
    ("evaluation/backtest/exclusions.py", 1),  # 동가 1건 초과 비교
    (
        "evaluation/backtest/rules.py",
        0.0,
    ),  # 공사가 아닌/A값 아닌 공고의 A · 금액 양수 검사
    ("evaluation/backtest/floor.py", 1.0),  # 비율에서 1 을 빼 증감으로 바꾸는 자리
    (
        "evaluation/backtest/floor.py",
        10000.0,
    ),  # bp 의 정의(10^4) — 단위이지 임계가 아니다
    (
        "evaluation/backtest/policy.py",
        32,
    ),  # _MAX_INDEXED_LIST_LENGTH — 평탄 인덱스 키 상한(정적 구조 상수)
    ("evaluation/backtest/policy_values.py", 0),  # 양수·음이 아님 검사 경계
    ("evaluation/backtest/policy_values.py", 1),  # (0,1) 열린 구간 상한·개수 하한
    (
        "evaluation/backtest/policy_values.py",
        1.0,
    ),  # (1 + 여유) 비율 — 여유의 정의
    (
        "evaluation/backtest/policy_values.py",
        2,
    ),  # min_window_rows>=2(쌍대 검정 하한)·격자 하한
    ("evaluation/backtest/snapshot.py", 0),  # 개찰일 범위 tuple 인덱스
    ("evaluation/backtest/snapshot.py", 1),  # 같음
    ("evaluation/backtest/institution.py", 0),  # digitize 구간 인덱스 하한(clip)
    (
        "evaluation/backtest/institution.py",
        1,
    ),  # 구간 경계 슬라이스·bin_count-1 상한
    (
        "evaluation/backtest/institution.py",
        1.0,
    ),  # 사정률 1 기준(예가 범위는 1 둘레의 비율)
    ("evaluation/backtest/strategies.py", 0),  # 표본 없음·경쟁자 0 비교
    (
        "evaluation/backtest/strategies.py",
        1,
    ),  # 자신을 뺀 경쟁자 수(n-1)·중앙값 인덱스
    (
        "evaluation/backtest/strategies.py",
        1.0,
    ),  # 비율 1 기준(밴드 상·하단, E[R] 중점)
    ("evaluation/backtest/strategies.py", 2),  # 중앙값 인덱스 나눗셈
    (
        "evaluation/backtest/strategies.py",
        2.0,
    ),  # _HALF — 반폭·중점의 정의(구조 상수)
    ("evaluation/backtest/fit.py", 0.0),  # 확률·통계량의 하한 클램프
    ("evaluation/backtest/fit.py", 1),  # 급수 항 시작·구간 경계 슬라이스
    ("evaluation/backtest/fit.py", 1.0),  # 확률 상한 클램프·정규화 축 1 기준
    ("evaluation/backtest/fit.py", 2),  # 급수의 홀짝 부호 판정(k % 2)
    ("evaluation/backtest/fit.py", 2.0),  # Kolmogorov 급수의 계수 2 — 분포의 정의
    ("evaluation/backtest/fit.py", 64),  # _KOLMOGOROV_TERMS — 급수 절단(수치 상수)
    (
        "evaluation/backtest/fit.py",
        200000,
    ),  # _REFERENCE_SAMPLE_COUNT — 기준 표본 분해능
    ("evaluation/backtest/mcnemar.py", 0),  # k<=0 꼬리 경계
    ("evaluation/backtest/mcnemar.py", 0.0),  # 검정력 하한(기각 불가)
    (
        "evaluation/backtest/mcnemar.py",
        0.5,
    ),  # 귀무가설 성공 확률 — McNemar 의 정의 그 자체
    ("evaluation/backtest/mcnemar.py", 1),  # 팩토리얼 누적합 시작·탐색 시작
    ("evaluation/backtest/mcnemar.py", 1.0),  # 확률 상한·여확률(1-p)
    ("evaluation/backtest/mcnemar.py", 2),  # 이분 탐색 중점·상계 배증
    (
        "evaluation/backtest/mcnemar.py",
        2.0,
    ),  # 승수 차이를 불일치 쌍 비율로 옮기는 반분
    (
        "evaluation/backtest/mcnemar.py",
        200000,
    ),  # _MAX_SEARCH_PAIRS — 탐색 상한(구조 상수)
    (
        "evaluation/backtest/mcnemar.py",
        64,
    ),  # _BACK_SCAN_PAIRS — 계단 역주행 폭(구조 상수)
    ("evaluation/backtest/run.py", 2.0),  # 반폭 = (end - begin) / 2 — 반폭의 정의
    ("evaluation/backtest/metrics.py", 0),  # 빈 표본 비율
    ("evaluation/backtest/metrics.py", 0.0),  # 빈 표본 비율 fallback
    ("evaluation/backtest/metrics.py", 0.25),  # 사분위 하 — 지표 정의
    ("evaluation/backtest/metrics.py", 0.5),  # 중앙값 — 지표 정의
    ("evaluation/backtest/metrics.py", 0.75),  # 사분위 상 — 지표 정의
    ("evaluation/backtest/metrics.py", 1),  # 계수 증가
    ("evaluation/backtest/metrics.py", 2),  # 사분위 tuple 인덱스
    ("evaluation/backtest/run.py", 0),  # 사분위/인덱스 시작
    ("evaluation/backtest/run.py", 0.0),  # 상대 개선 부호 비교
    ("evaluation/backtest/run.py", 1),  # 서명 집합 크기 비교
    ("evaluation/backtest/verdict.py", 0.0),  # 개선률 부호 경계(UNDERPOWERED)
    ("evaluation/backtest/verdict.py", 1),  # 창 통과 계수
    ("evaluation/backtest/verdict.py", 2),  # 과반 판정(계수 * 2 > 전체)
    ("evaluation/backtest/windows.py", 1),  # 창 인덱스 증가
)

_ALLOWED: frozenset[tuple[str, str]] = frozenset(
    (path, repr(value)) for path, value in _ALLOWED_ENTRIES
)


def _numeric_literals(path: Path) -> list[tuple[int, object]]:
    """AST 숫자 상수 **와** 수로 읽히는 모든 `str`·`bytes` 상수(docstring 제외), 그리고
    **상수만으로 접히는 표현식**을 함께 센다.

    축이 셋인 이유는 각각이 앞 판을 지나간 자리이기 때문이다:
    - 숫자 상수 — 원래 축
    - 문자열 상수 — `float("0.05")`(verifier r1 G1). 이름이 아니라 **값의 성질**로
      본다(별칭·`json.loads`·연결이 한꺼번에 닫힌다)
    - `bytes` 상수와 접히는 표현식 — `float(b"0.05")` · `float("0.0166x"[:-1])`
      (verifier r3 M-5). 접두사 하나·슬라이스 하나가 문이었다"""
    tree = ast.parse(path.read_text(encoding="utf-8"))
    docstrings = _docstring_nodes(tree)
    literals: list[tuple[int, object]] = []
    for node in ast.walk(tree):
        if isinstance(node, ast.Constant):
            if type(node.value) in (int, float) and not isinstance(node.value, bool):
                literals.append((node.lineno, node.value))
            elif isinstance(node.value, (str, bytes)) and id(node) not in docstrings:
                hidden = _as_number(node.value)
                if hidden is not None:
                    literals.append((node.lineno, hidden))
        elif isinstance(node, (ast.BinOp, ast.Subscript)):
            folded = _folded(node)
            if isinstance(folded, (str, bytes)):
                hidden = _as_number(folded)
                if hidden is not None:
                    literals.append((node.lineno, hidden))
    return literals


def test_evaluation_modules_have_no_stray_numeric_literals_outside_allowlist() -> None:
    violations: list[str] = []
    for path in _scanned_paths():
        if path.name in _EXCLUDED_FILES:
            continue
        for lineno, value in _numeric_literals(path):
            if (_key(path), repr(value)) not in _ALLOWED:
                violations.append(f"{_key(path)}:{lineno} = {value!r}")
    assert not violations, (
        "evaluation/** 에 허용 목록 밖 숫자 리터럴이 있다(임계는 policy/evaluation-v1.yaml "
        f"에만 있어야 한다): {violations}"
    )


def test_allowlist_entries_are_still_present() -> None:
    """허용 목록에 죽은 항목(코드에서 이미 지워진 값)이 남지 않게 — 반대 방향 확인."""
    present: set[tuple[str, str]] = set()
    for path in _scanned_paths():
        if path.name in _EXCLUDED_FILES:
            continue
        for _lineno, value in _numeric_literals(path):
            present.add((_key(path), repr(value)))
    stale = _ALLOWED - present
    assert not stale, f"허용 목록에 더 이상 코드에 없는 항목이 있다: {stale}"


def test_shipped_threshold_values_never_appear_as_literals() -> None:
    """출하 임계(policy-values.md §1)가 코드 리터럴로 새지 않았는지 직접 확인 —
    ML-07 acceptance ③(정책 산출물에 존재, 코드 리터럴 아님)의 회귀 방지."""
    shipped_thresholds = {
        # 5C-2 evaluation-v1.yaml
        2.58,
        0.70,
        100,
        5,
        10,
        20260812,
        1e8,
        5e8,
        1e9,
        5e9,
        # M6/6G strategy-backtest-v1.yaml — A-3 승인값과 제도·밴드 상수
        0.20,
        0.05,
        3,
        0.01,
        0.80,
        483,
        7,
        15,
        4,
        0.30,
        0.995,
        0.98,
        4000,
        41,
        400.0,
        30,
        200,
        0.02,
    }
    leaked: list[str] = []
    for path in _scanned_paths():
        if path.name in _EXCLUDED_FILES:
            continue
        for lineno, value in _numeric_literals(path):
            if value in shipped_thresholds:
                leaked.append(f"{_key(path)}:{lineno} = {value!r}")
    assert not leaked, f"출하 임계가 코드 리터럴로 나타난다: {leaked}"


def test_app_roots_are_derived_from_imports_not_a_file_list() -> None:
    """D-6G-52 — `app/**` 뿌리는 **import 그래프에서 나온다**. 앞 판은 파일 이름 둘을
    적어 뒀고, `app/` 에 파일 하나를 더 만드는 것이 게이트를 우회하는 길이었다
    (cr r3 L-4). 여기서는 「백테스트를 import 하는 app 모듈은 **전부** 뿌리에 있다」를
    직접 잰다 — 이름 목록으로 되돌리면 새 파일이 생기는 순간 이 단언이 깨진다."""
    roots = set(_app_roots())
    backtest = _module_name(_BACKTEST_SRC)
    for path in sorted((_ML_ENGINE_SRC / "app").rglob("*.py")):
        imports = _imported_modules(path)
        if any(name.startswith(backtest) for name in imports):
            assert path in roots, f"백테스트를 import 하는데 뿌리에 없다: {path.name}"
    # 헛돌지 않는 확인 — 실제로 뿌리가 비어 있지 않다.
    assert roots


def test_allowlist_keys_keep_int_and_float_apart() -> None:
    """cr r3 L-5 — `0 == 0.0` 이라 **집합 리터럴이 두 항목을 하나로 접는다**. 그래서
    주석 둘이 한 원소를 설명했고, 「죽은 항목」 검사가 둘을 가르지 못했다.

    두 가지를 잰다: ⑴ 등재 튜플이 집합으로 접히지 않는다(원소 수가 보존된다)
    ⑵ 같은 파일의 `0` 과 `0.0` 이 **다른 키**가 된다."""
    # `set(_ALLOWED_ENTRIES)` 로 중복을 세면 안 된다 — 그 집합이 바로 `0`/`0.0` 을
    # 접는다. 타입을 보존한 키(`_ALLOWED`)로 세야 「진짜 중복」이 보인다.
    assert len(_ALLOWED_ENTRIES) == len(_ALLOWED), (
        "등재 목록에 진짜 중복이 있다(같은 파일·같은 타입·같은 값)"
    )
    collapsed = len({(path, value) for path, value in _ALLOWED_ENTRIES})
    assert len(_ALLOWED) > collapsed, (
        "타입을 보존한 키 수가 값 비교로 접은 수보다 많아야 한다 — "
        "접히는 쌍이 실제로 등재돼 있고, 키가 그것을 가른다"
    )
    sample = ("evaluation/backtest/mcnemar.py", repr(0))
    twin = ("evaluation/backtest/mcnemar.py", repr(0.0))
    assert sample in _ALLOWED and twin in _ALLOWED and sample != twin
