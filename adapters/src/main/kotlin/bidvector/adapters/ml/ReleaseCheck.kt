package bidvector.adapters.ml

import contract.bidvector.ml.v1.ModelRelease
import contract.bidvector.ml.v1.ModelReleaseSelector

/**
 * M4/4D-1(D-4D-3·D-4D-4, `OPEN-2A-RELEASE-CHECK-4D` 종결) — M2/2B `PredictionContractTest`의
 * `releaseSatisfiesSelector`를 main 으로 승격한 실물. 제3 변환 금지(ADR 0010 D-3)의 **client
 * 집행**이다 — `GrpcBidPredictionGateway.predict`가 `latest_promoted` 요청일 때 같은 호출
 * 안에서 `GetModelMetadata`를 불러 이 함수로 대조한다(D-4D-4, 대조를 별도 port 메서드가
 * 아니라 어댑터 내부 단계로 둬 호출부가 잊을 수 없게 한다). `PredictionContractTest.kt`는
 * 이제 이 함수를 import 해서 쓴다(단언·case 무변경, scope.md S-3).
 */
internal fun releaseSatisfiesSelector(
    selector: ModelReleaseSelector,
    responseRelease: ModelRelease,
    promoted: ModelRelease?,
): Boolean =
    when (selector.selectorCase) {
        ModelReleaseSelector.SelectorCase.EXACT_RELEASE -> {
            responseRelease.releaseId == selector.exactRelease.releaseId &&
                responseRelease.artifactChecksum == selector.exactRelease.artifactChecksum
        }

        ModelReleaseSelector.SelectorCase.LATEST_PROMOTED -> {
            promoted != null &&
                responseRelease.releaseId == promoted.releaseId &&
                responseRelease.artifactChecksum == promoted.artifactChecksum
        }

        ModelReleaseSelector.SelectorCase.SELECTOR_NOT_SET, null -> {
            false
        }
    }
