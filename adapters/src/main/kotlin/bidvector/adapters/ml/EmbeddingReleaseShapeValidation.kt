package bidvector.adapters.ml

import contract.bidvector.ml.v1.Embedding

/**
 * 4D-1 `hasNonBlankRelease(Success)`(`ReleaseShapeValidation.kt`)와 같은 규칙을 `Embedding`
 * (embedding.proto) 위에 다시 문서화한다 — 값·규칙은 동일하나 시그니처가 다른 계약
 * message 라 오버로드로 둔다(2E `EmbeddingContractTest`가 이미 같은 규칙을 test 전용 순수
 * 함수로 고정해 뒀다 — 그 test 는 「main 코드는 4D-2 몫」이라고 명시했다). `ModelRelease`
 * (prediction.proto)가 두 메시지 모두의 실제 release 필드 타입이므로, 다섯 성분 비공백
 * 규칙 자체는 한 곳(`hasNonBlankRelease(Success)`)과 동형이다.
 */
internal fun hasNonBlankRelease(embedding: Embedding): Boolean =
    with(embedding.release) {
        releaseId.isNotBlank() &&
            artifactChecksum.isNotBlank() &&
            featureSchemaVersion.isNotBlank() &&
            codeVersion.isNotBlank() &&
            datasetId.isNotBlank()
    }
