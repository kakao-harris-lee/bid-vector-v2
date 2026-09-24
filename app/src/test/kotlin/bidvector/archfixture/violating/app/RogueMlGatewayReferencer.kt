package bidvector.archfixture.violating.app

import bidvector.adapters.ml.GrpcBidPredictionGateway

/**
 * D-6A3-17(b) 위반 표본(verifier M4 재현) — `app` 이 허용 목록 밖의 `adapters.ml` 타입
 * (`GrpcBidPredictionGateway`, 실 production 클래스)을 참조한다 — 루트 패키지에 실 ML
 * gateway 를 끌어오는 `@Primary` 빈 우회를 재현한다. production classpath 에는 오르지
 * 않는다(test 소스).
 */
class RogueMlGatewayReferencer(
    private val gateway: GrpcBidPredictionGateway,
)
