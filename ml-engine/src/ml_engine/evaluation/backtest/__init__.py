"""`ml_engine.evaluation.backtest` — M6/6G 전략 백테스트(D-6G-8). 분포 엔진이 밴드 내
균등 난수보다 나은가를 재는 **실험** 커널이다. 제품 경로가 아니다 — 판정 하나를 낸다.

층 경계: `evaluation` 안이므로 `features`·`contracts` 아래로만 의존한다(import-linter
layers). **`ml_engine.inference` 를 import 하지 않는다**(forbidden 계약) — S2(분포 엔진)
전략은 `StrategyLike` Protocol 뒤에 두고 조립 근(`ml_engine.app`)이 주입한다. 그래서
이 패키지는 「무엇을 재는가」만 알고 「무엇으로 재는가」는 주입받는다.

누출 금지는 이 패키지의 구조로 닫힌다 — 전략에 넘기는 입력(`StrategyInput`)에는 그 공고의
개찰 결과(예정가격·추첨 번호·투찰자 행)가 **타입 자체에 없다**. 채점에 쓰는 개찰 결과는
`metrics` 가 전략을 부른 **뒤에** 따로 읽는다."""

from __future__ import annotations
