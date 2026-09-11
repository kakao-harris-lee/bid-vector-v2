package bidvector.adapters.ml

import contract.bidvector.ml.v1.ModelRelease

/**
 * verifier r1 F-2(high) — 응답 release 의 다섯 성분 중 하나라도 공백이면 이 응답은
 * 형태부터 무효다(`ModelReleaseRef` 의 `init` 이 걸기 전에 여기서 먼저 막는다 — 예외가
 * 어댑터 밖으로 새지 않게 한다, scope.md ④). `releaseSatisfiesSelector`(release 대조)는
 * `releaseId`·`artifactChecksum` 둘만 보므로 `featureSchemaVersion`·`codeVersion`·
 * `datasetId` 가 공백이어도 그 대조를 통과할 수 있다 — 이 검사가 그 공백을 별도로 막는다.
 * `ParsedSuccessFields.kt`에서 갈라낸 파일이다(detekt `TooManyFunctions`).
 *
 * **리뷰 F-E(medium) 처방** — `Success.release`(prediction)와 `Embedding.release`
 * (embedding)가 **같은 `ModelRelease` 타입**이라 이전엔 `hasNonBlankRelease(Success)`·
 * `hasNonBlankRelease(Embedding)` 오버로드 둘로 나뉘어 있었다(값·규칙 완전 동일, 8줄
 * 중복 — `EmbeddingReleaseShapeValidation.kt` 파일 자체를 없앴다). 피연산자 타입을
 * `ModelRelease`로 올려 하나로 합친다 — 호출부는 `success.release`·`embedding.release`로
 * 필드를 먼저 꺼내 넘긴다(동작 변화 없음, 순수 시그니처 정리).
 */
internal fun hasNonBlankRelease(release: ModelRelease): Boolean =
    with(release) {
        allNotBlank(releaseId, artifactChecksum, featureSchemaVersion, codeVersion, datasetId)
    }

private fun allNotBlank(vararg values: String): Boolean = values.all { it.isNotBlank() }
