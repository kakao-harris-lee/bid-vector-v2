package bidvector.procurement

import java.time.LocalDate

/**
 * 조회 기준일 — **KST 캘린더 일자**(COL-01, `data-dictionary.md` §1.5). `LocalDate`는
 * 그 자체로 타임존을 갖지 않는 순수 캘린더 일자라 이 타입은 그 값이 KST 업무 구간의
 * 일자로 해석됨을 이름으로 진술한다(타임존 리터럴을 코드에 두지 않는다).
 */
data class CollectionReferenceDate(
    val date: LocalDate,
)

/** 페이지네이션 커서 — 불투명 토큰. 다음 페이지가 없으면 `next = null`(COL-06 종료 조건). */
data class PageCursor(
    val token: String,
)

/**
 * 수집 port 공통 반환 형태(①) — 원문 항목 + 회계를 함께 낸다. 회계 없이 항목만 내면
 * COL-06(수집이 무엇을 놓쳤는가)이 무너진다.
 */
data class SourceBatch<T>(
    val items: List<T>,
    val accounting: CollectionAccounting,
    val next: PageCursor?,
)

/**
 * 공고 목록 수집 port(①, COL-01) — **도메인 소유 인터페이스**(ADR 0006 D-2, ADR 0005
 * D-10.1 「domain이 의존하는 port는 domain 안에 선다」). 구현은 3B(어댑터)가 한다.
 *
 * **`suspend`가 아니다**(설계 검토 예상과의 판단 차이, 착수 시 실측) — `suspend fun`은
 * 컴파일된 시그니처에 `kotlin.coroutines.Continuation` 매개변수를 남기는데, 그 패키지는
 * `architecture-policy.properties`의 domain 허용 목록(T-B/T-C)에 없어 `ArchitectureGateTest`가
 * 실제로 거부한다(실측: 8건 위반). 코루틴 취소 전파는 어댑터(3B)가 구현 시 자기 시그니처를
 * suspend로 감싸 처리할 수 있다 — port 인터페이스 자체가 그 표면을 domain에 끌어들일
 * 필요는 없다. 게이트를 여는 대신 인터페이스를 좁힌다.
 */
interface NoticeSourcePort {
    fun fetchNotices(
        referenceDate: CollectionReferenceDate,
        cursor: PageCursor?,
    ): SourceBatch<RawNoticeObservation>
}

/**
 * 개찰 결과 수집 port(①, COL-02·COL-03). [fetchReservePrices]·[fetchOpeningCompleteResults]의
 * 서명이 [DetailFetchDecision.Fetch] 값을 요구한다 — 조회 가치 술어([decideDetailFetch])를
 * 거치지 않은 호출은 컴파일되지 않는다(위협 모델 방어 (h), 우회 후보 (7)(8)).
 */
interface OpeningResultSourcePort {
    fun fetchOpeningResults(
        referenceDate: CollectionReferenceDate,
        cursor: PageCursor?,
    ): SourceBatch<RawNoticeObservation>

    fun fetchReservePrices(evidence: DetailFetchDecision.Fetch): SourceBatch<RawNoticeObservation>

    /**
     * M3/3F D-3F-1 (a) — 개찰완료(`getOpengResultListInfoOpengCompt`) 단건 조회. [fetchReservePrices]
     * 와 같은 성질(공고당 1콜·증거 값 요구, D-3F-2 — [DetailFetchDecision] 재사용)이라 새 결정
     * 타입을 만들지 않는다. 투찰자별 행은 raw_observation 감사 기록까지만 나른다(D-3F-3 해소 —
     * canonical 승격은 이 port 의 몫이 아니다).
     */
    fun fetchOpeningCompleteResults(evidence: DetailFetchDecision.Fetch): SourceBatch<RawNoticeObservation>
}

/**
 * 자격 원문·게시 낙찰하한율 수집 port(①, COL-04). [fetchQualificationText]의 서명이
 * [QualificationFetchDecision.Fetch] 값을 요구한다 — 조회 가치 술어([decideQualificationFetch])
 * 를 거치지 않은 호출은 컴파일되지 않는다(D-3B2-5 (a), 위협 모델 방어 (a), 우회 후보 (4)).
 */
interface DocumentSourcePort {
    fun fetchQualificationText(evidence: QualificationFetchDecision.Fetch): SourceBatch<RawNoticeObservation>
}
