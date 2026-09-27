"""복수예비가격 제도가 만드는 사정률 분포를 몬테카를로로 잰다 (2026-09-27, 표준 라이브러리만).

제도(국가 ±2%, 지방 ±3%): 범위를 균등 폭 15구간으로 나누고 구간마다 균등 난수 하나 → 예비가격 15개.
번호↔가격 배열이 무작위라 입찰자 선택은 가격과 무관하다 → 15개 중 무작위 4개의 평균 = 예정가격.
사정률 = 예정가격 / 기초금액.
"""

import random
import statistics


def draw_rate(half_width: float, rng: random.Random) -> float:
    lo = 1.0 - half_width
    step = 2 * half_width / 15
    prices = [lo + step * i + rng.random() * step for i in range(15)]
    return sum(rng.sample(prices, 4)) / 4


def main(n: int = 400_000, seed: int = 20260927) -> None:
    rng = random.Random(seed)
    for hw in (0.02, 0.03):
        rates = [draw_rate(hw, rng) for _ in range(n)]
        sd = statistics.pstdev(rates) * 100
        near = sum(abs(r - 1) <= 0.001 for r in rates) / n * 100
        mid = sum(-0.015 <= r - 1 <= -0.005 for r in rates) / n * 100
        tails = sum(abs(r - 1) > 0.015 for r in rates) / n * 100
        print(
            f"±{hw * 100:.0f}%: sd={sd:.4f}%p  P(|R-100%|<=0.1%p)={near:.1f}%  "
            f"P(-1.5..-0.5%p)={mid:.1f}%  P(|R-100%|>1.5%p)={tails:.1f}%"
        )


if __name__ == "__main__":
    main()
