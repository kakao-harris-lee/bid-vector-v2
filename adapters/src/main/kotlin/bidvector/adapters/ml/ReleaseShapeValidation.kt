package bidvector.adapters.ml

import contract.bidvector.ml.v1.ModelRelease
import contract.bidvector.ml.v1.ReleaseKind

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

/**
 * M2/2F(scope.md ⑥, D-2F-2) — `Success.release`(prediction)만 쓰는 kind 분기 검증. **이
 * slice 는 `Embedding.release`(embedding, `EmbeddingShapeValidation.kt`)를 건드리지
 * 않는다** — `embedding.proto`는 이 slice 의 in_scope 밖이고, `ModelRelease.release_kind`가
 * additive라 그 호출부는 계속 [hasNonBlankRelease](위, 무변경)를 쓴다. 두 함수가 갈라진
 * 이유는 데이터가 아니라 **범위**다 — embedding 쪽 testdata 가 아직 `release_kind`를
 * 채우지 않으므로(값은 기본 `UNSPECIFIED`) 이 함수를 그쪽에도 적용하면 기존 embedding
 * 계약 test 가 전부 거부로 깨진다.
 *
 * 규약(D-2F-2): `ARTIFACT`는 기존과 같이 다섯 전부 비공백. `DERIVED`(아티팩트 없는
 * release)는 `dataset_id`만 공백을 허용한다(D-2B-6과 충돌 없음 — 학습 dataset 자체가
 * 없다). `UNSPECIFIED`/`UNRECOGNIZED`는 거부(fail-closed, D-2F-6 — 과도기에도 값으로
 * 해석하지 않는다). **`release_id` 접두 `distribution/`는 kind 와 일치해야 한다**(설계
 * 검토 우회 (2)(9) — `dataset_id`를 비운 채 `ARTIFACT`라 주장하거나, `distribution/` 를
 * 안 쓰고 `DERIVED`라 주장하는 위장을 막는다).
 */
internal fun hasValidReleaseShape(release: ModelRelease): Boolean =
    with(release) {
        when (releaseKind) {
            ReleaseKind.RELEASE_KIND_ARTIFACT ->
                allNotBlank(releaseId, artifactChecksum, featureSchemaVersion, codeVersion, datasetId) &&
                    !releaseId.startsWith(DISTRIBUTION_RELEASE_ID_PREFIX)

            ReleaseKind.RELEASE_KIND_DERIVED ->
                allNotBlank(releaseId, artifactChecksum, featureSchemaVersion, codeVersion) &&
                    releaseId.startsWith(DISTRIBUTION_RELEASE_ID_PREFIX)

            ReleaseKind.RELEASE_KIND_UNSPECIFIED, ReleaseKind.UNRECOGNIZED -> false
        }
    }

/** D-2F-2 DERIVED 규약 — `release_id = "distribution/<inference policy version>"`. */
private const val DISTRIBUTION_RELEASE_ID_PREFIX = "distribution/"

private fun allNotBlank(vararg values: String): Boolean = values.all { it.isNotBlank() }
