package bidvector.adapters.ml

import contract.bidvector.ml.v1.Success

/**
 * verifier r1 F-2(high) — 응답 release 의 다섯 성분 중 하나라도 공백이면 이 `Success` 는
 * 형태부터 무효다(`ModelReleaseRef` 의 `init` 이 걸기 전에 여기서 먼저 막는다 — 예외가
 * 어댑터 밖으로 새지 않게 한다, scope.md ④). `releaseSatisfiesSelector`(release 대조)는
 * `releaseId`·`artifactChecksum` 둘만 보므로 `featureSchemaVersion`·`codeVersion`·
 * `datasetId` 가 공백이어도 그 대조를 통과할 수 있다 — 이 검사가 그 공백을 별도로 막는다.
 * `ParsedSuccessFields.kt`에서 갈라낸 파일이다(detekt `TooManyFunctions`).
 */
internal fun hasNonBlankRelease(success: Success): Boolean =
    with(success.release) {
        allNotBlank(releaseId, artifactChecksum, featureSchemaVersion, codeVersion, datasetId)
    }

private fun allNotBlank(vararg values: String): Boolean = values.all { it.isNotBlank() }
