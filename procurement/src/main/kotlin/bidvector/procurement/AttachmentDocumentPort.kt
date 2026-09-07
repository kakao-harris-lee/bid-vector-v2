package bidvector.procurement

import java.time.Duration
import java.time.Instant
import java.util.Objects

/**
 * 공고 첨부문서 URL(M3/3C ①) — **3A 필드 계약 등재 키에서만** 만들어지는 값이다. 유일한
 * 생성 경로는 [from]이고, 그 서명이 [KonepsFieldContract]를 요구한다 — 그 타입의 생성자·
 * `of()` 팩토리가 이미 procurement 밖에서 `internal`로 닫혀 있으므로(`FieldContract.kt`
 * F-4/N-1), 이 함수를 호출하는 쪽(adapters)은 procurement 가 이미 발급한 계약 인스턴스를
 * **전달**할 수만 있지 스스로 지어낼 수 없다(위협 모델 우회 (7) — 계약 밖 키에서 임의
 * URL 을 읽는 경로가 막힌다).
 *
 * **알려진 제한(`OPEN-3C-ATTACHMENT-FIELD-CONTRACT`)** — 실제 첨부 URL 원문 키
 * (`ntceSpecDocUrl1` 계열)의 [KonepsFieldContract] 등재는 `CollectionPolicy.kt`(3A
 * 소유, 이 slice 의 in_scope 밖)의 몫이다. 이 slice 는 그 파일을 편집하지 않으므로
 * 프로덕션 배선(어떤 계약 인스턴스를 실제로 넘기는가)은 후속 slice(3B 확장 또는 4B)가
 * 완성한다 — 이 함수 자체는 등재된 계약이라면 어느 것이든 받아 값을 뽑아낼 뿐, concept
 * 이 attachment-url 인지는 검사하지 않는다(모듈 경계가 gate이지 concept 판별이 gate가
 * 아니다).
 */
@ConsistentCopyVisibility
data class AttachmentUrl private constructor(
    val value: String,
) {
    companion object {
        fun from(
            observation: RawNoticeObservation,
            contract: KonepsFieldContract,
        ): AttachmentUrl? = observation.valueOf(contract)?.takeIf(String::isNotBlank)?.let(::AttachmentUrl)
    }
}

/** 첨부 취득 상한(D-3C-5) — 값은 adapters 정책 데이터가 갖고, 이 타입은 형태만 정한다. */
data class AttachmentFetchLimits(
    val maxBytes: Long,
    val timeout: Duration,
) {
    init {
        require(maxBytes > 0) { "maxBytes는 0보다 커야 한다: $maxBytes" }
        require(!timeout.isNegative && !timeout.isZero) { "timeout은 0보다 커야 한다: $timeout" }
    }
}

/** 취득 실패 사유 — 전송·크기 축만(형식 판별은 취득 이후 별도 축, adapters `DocumentFormat`). */
sealed interface AttachmentFetchFailure {
    data object TooLarge : AttachmentFetchFailure

    data object Timeout : AttachmentFetchFailure

    data class TransportFailed(
        val detail: String,
    ) : AttachmentFetchFailure
}

/**
 * 취득한 첨부 바이트 + 감사 정보. `data class`가 아니다 — 합성 `equals`가 `bytes`를
 * 배열 참조로 비교해 [RawNoticeObservation]과 같은 이유로 값 의미를 왜곡한다. 내용
 * 동일성은 [sha256]로 충분하다(중복 비교, CPD).
 */
class FetchedDocument(
    val bytes: ByteArray,
    val mediaType: String,
    val sha256: String,
    val fetchedAt: Instant,
    val sourceUrl: AttachmentUrl,
) {
    override fun equals(other: Any?): Boolean =
        other is FetchedDocument &&
            sha256 == other.sha256 &&
            mediaType == other.mediaType &&
            fetchedAt == other.fetchedAt &&
            sourceUrl == other.sourceUrl

    override fun hashCode(): Int = Objects.hash(sha256, mediaType, fetchedAt, sourceUrl)

    override fun toString(): String =
        "FetchedDocument(sha256=$sha256, mediaType=$mediaType, bytes=${bytes.size}B, sourceUrl=$sourceUrl)"
}

sealed interface FetchOutcome {
    data class Fetched(
        val document: FetchedDocument,
    ) : FetchOutcome

    data class Failed(
        val reason: AttachmentFetchFailure,
    ) : FetchOutcome
}

/**
 * 첨부문서 취득 port(M3/3C ①) — **도메인 소유 인터페이스**(ADR 0005 D-10.1). 구현은
 * 3C 어댑터(`HttpAttachmentDocumentSource`)가 한다. 3A `DocumentSourcePort`
 * (`fetchQualificationText`)는 KONEPS 자격 원문 서브콜(3B-2)이라 이 port 와 다르다
 * (착수 시 정정 ①).
 */
interface AttachmentDocumentPort {
    fun fetch(
        url: AttachmentUrl,
        limits: AttachmentFetchLimits,
    ): FetchOutcome
}
