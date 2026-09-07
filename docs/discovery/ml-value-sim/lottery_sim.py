"""4/15 복수예가 추첨 하에서 투찰 전략별 낙찰 확률 — `docs/discovery/ml-value-and-data-locality.md` §2.2 의 계산.

목적: (1) 「100% 근처 무작위」가 학습 기반과 같은가, (2) 정보의 가치가 어디에 있는가
      — 발주처 범위(표 조회) vs 경쟁자 밀집(학습 대상) — 를 분리해서 본다.

실행: `python3 docs/discovery/ml-value-sim/lottery_sim.py` (표준 라이브러리만, 약 1~2분).
가정은 전부 이 파일 안에 있다 — 경쟁자 분포(`competitors`)는 실측이 아니라 모형이다. 실측 대체는
같은 문서 §4 의 결정 실험(D-ML-2)이 한다. 결정 실험의 S0(밴드 내 무작위)·S1(규칙 앵커)은 아래
`rnd`·`informed` 의 정의를 따른다.
"""
import random

random.seed(11)
FLOOR = 0.87745  # 낙찰하한율 (예정가 기준), 샘플
T = 30000


def reserve_rate(lo, hi, k=15, pick=4):
    """생성범위 [lo, hi] 를 15 균등 구간으로 나눠 구간마다 1개, 그중 4개 평균 (강·류 2015 의 균등분할)."""
    step = (hi - lo) / k
    prices = [random.uniform(lo + i * step, lo + (i + 1) * step) for i in range(k)]
    return sum(random.sample(prices, pick)) / pick


def competitors(n, anchor, spread, tail=0.3):
    """70%: anchor×(1+0.002) 주변 정규 밀집(아래쪽 위험 감수자 포함, 절단 없음) · 30%: anchor −1%~+3% 균등 산포."""
    out = []
    for _ in range(n):
        if random.random() < tail:
            out.append(anchor * (1 + random.uniform(-0.01, 0.03)))
        else:
            out.append(anchor * (1 + random.gauss(0.002, spread)))
    return out


def one(my_fn, n, lo, hi, spread, exp_rate):
    rr = reserve_rate(lo, hi)
    floor = FLOOR * rr
    anchor = FLOOR * exp_rate
    comp = competitors(n, anchor, spread)
    my = my_fn(anchor)
    if my < floor:
        return 0
    valid = [b for b in comp if b >= floor]
    if not valid:
        return 1
    return 1 if my < min(valid) else 0


def pwin(my_fn, n, lo, hi, spread, exp_rate, t=T):
    return sum(one(my_fn, n, lo, hi, spread, exp_rate) for _ in range(t)) / t


def best_offset(n, lo, hi, spread, exp_rate):
    """경쟁자 분포와 하한 분포를 완전히 아는 자(=완벽한 모델)의 최적 고정 오프셋과 그 승률 — 모형 의존 상한."""
    best = (None, -1)
    for off in [x / 1000 for x in range(-10, 31)]:
        p = pwin(lambda a, o=off: a * (1 + o), n, lo, hi, spread, exp_rate, t=8000)
        if p > best[1]:
            best = (off, p)
    return best


CASES = [
    # (label, n, lo, hi, spread) — 생성범위는 강·류(2015) Table 1 (2014년 기준)
    ("조달청형 ±2%", 30, 0.98, 1.02, 0.004),
    ("조달청형 ±2%", 100, 0.98, 1.02, 0.004),
    ("지자체형 ±3%", 30, 0.97, 1.03, 0.006),
    ("지자체형 ±3%", 100, 0.97, 1.03, 0.006),
    ("LH·도로공사형 -6~0%", 30, 0.94, 1.00, 0.006),
    ("소수 경쟁 ±2% (n=8)", 8, 0.98, 1.02, 0.004),
]


def main():
    print(
        f"{'case':<22}{'n':>4} | {'100%기초':>8} {'무작위밴드':>9} {'하한×1.0+0.3%':>13} "
        f"{'하한×E[사정률]+0.3%':>18} {'완벽정보 최적':>14} (offset) | 1/(n+1)"
    )
    for label, n, lo, hi, spread in CASES:
        exp_rate = (lo + hi) / 2
        hundred = pwin(lambda a: 1.0, n, lo, hi, spread, exp_rate)
        rnd = pwin(lambda a: a * random.uniform(0.99, 1.02), n, lo, hi, spread, exp_rate)
        naive = pwin(lambda a: FLOOR * 1.003, n, lo, hi, spread, exp_rate)
        informed = pwin(lambda a: a * 1.003, n, lo, hi, spread, exp_rate)
        off, best = best_offset(n, lo, hi, spread, exp_rate)
        print(
            f"{label:<22}{n:>4} | {hundred:>8.3f} {rnd:>9.3f} {naive:>13.3f} {informed:>18.3f} "
            f"{best:>14.3f} ({off:+.3f}) | {1/(n+1):.3f}"
        )


if __name__ == "__main__":
    main()
