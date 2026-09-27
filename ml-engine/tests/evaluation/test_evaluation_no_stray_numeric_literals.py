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
from pathlib import Path

_ML_ENGINE_SRC = Path(__file__).resolve().parents[2] / "src" / "ml_engine"
_EVALUATION_SRC = _ML_ENGINE_SRC / "evaluation"
# 6G 조립 근 둘 — D-6G-8 이 S2 주입을 두라고 한 자리다. `app/` 전체가 아니다.
_APP_BACKTEST_SOURCES = ("backtest_job.py", "backtest_distribution.py")
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


def _as_number(raw: str) -> float | None:
    """문자열이 수로 읽히면 그 수, 아니면 `None`."""
    try:
        return float(raw)
    except ValueError:
        return None


def _scanned_paths() -> list[Path]:
    """`evaluation/**` 전부(하위 패키지 포함) + 6G 조립 근 둘. 정렬은 상대 경로
    기준이라 플랫폼과 무관하게 같은 순서가 나온다."""
    paths = list(_EVALUATION_SRC.rglob("*.py"))
    paths.extend(_ML_ENGINE_SRC / "app" / name for name in _APP_BACKTEST_SOURCES)
    return sorted(paths, key=lambda path: path.relative_to(_ML_ENGINE_SRC).as_posix())


def _key(path: Path) -> str:
    return path.relative_to(_ML_ENGINE_SRC).as_posix()


_ALLOWED: frozenset[tuple[str, float]] = frozenset(
    {
        (
            "evaluation/baselines.py",
            0.0,
        ),  # group_mean_predictions 카운터 초기값(total, count)
        ("evaluation/baselines.py", 1),  # band 인덱스 증가(range(len(edges)))
        (
            "evaluation/diagnostics.py",
            0.0,
        ),  # 빈 배열 대비 fallback(coverage_splits 등 초기값)
        ("evaluation/diagnostics.py", 1),  # n<=1 하한·count+1 증가
        (
            "evaluation/diagnostics.py",
            2,
        ),  # required_row_count 제곱(** 2) — 산식 자체의 지수
        (
            "evaluation/policy.py",
            0,
        ),  # paired_t_threshold<=0·stability_seeds 인덱스 시작
        ("evaluation/policy.py", 1),  # max_origins<1·agency_baseline_min_count<1
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
        ("evaluation/backtest/sample_list.py", 0),  # TSV 첫 칸(키) 인덱스
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
    }
)


def _numeric_literals(path: Path) -> list[tuple[int, float]]:
    """AST 숫자 상수 **와** 수로 읽히는 모든 문자열 상수(docstring 제외)를 함께 센다.

    문자열 축을 호출 이름으로 좁히면(`float`·`Decimal`) 별칭·다른 파서(`json.loads`)·
    연결 형태가 전부 빠져나간다(verifier r2 M-c). 술어를 **값의 성질**(수로 읽히는가)로
    옮겨서 이름 열거를 없앴다."""
    tree = ast.parse(path.read_text(encoding="utf-8"))
    docstrings = _docstring_nodes(tree)
    literals: list[tuple[int, float]] = []
    for node in ast.walk(tree):
        if not isinstance(node, ast.Constant):
            continue
        if type(node.value) in (int, float) and not isinstance(node.value, bool):
            literals.append((node.lineno, node.value))
        elif isinstance(node.value, str) and id(node) not in docstrings:
            hidden = _as_number(node.value)
            if hidden is not None:
                literals.append((node.lineno, hidden))
    return literals


def test_evaluation_modules_have_no_stray_numeric_literals_outside_allowlist() -> None:
    violations: list[str] = []
    for path in _scanned_paths():
        if path.name in _EXCLUDED_FILES:
            continue
        for lineno, value in _numeric_literals(path):
            if (_key(path), value) not in _ALLOWED:
                violations.append(f"{_key(path)}:{lineno} = {value!r}")
    assert not violations, (
        "evaluation/** 에 허용 목록 밖 숫자 리터럴이 있다(임계는 policy/evaluation-v1.yaml "
        f"에만 있어야 한다): {violations}"
    )


def test_allowlist_entries_are_still_present() -> None:
    """허용 목록에 죽은 항목(코드에서 이미 지워진 값)이 남지 않게 — 반대 방향 확인."""
    present: set[tuple[str, float]] = set()
    for path in _scanned_paths():
        if path.name in _EXCLUDED_FILES:
            continue
        for _lineno, value in _numeric_literals(path):
            present.add((_key(path), value))
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
