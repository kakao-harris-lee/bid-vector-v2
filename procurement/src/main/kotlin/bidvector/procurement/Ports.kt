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
    /**
     * **이 걷기의 이름**(D-6G-68) — 한 걷기의 모든 쪽이 다는 관측 시각이다. 걷기를 수행한 어댑터가
     * 한 번 적고, 원장의 AXIS 결말 줄이 그 값을 실어 추출이 「어느 걷기의 행인가」를 짐작하지
     * 않는다. 걷기가 아닌 자리(단건 조립·test 대역)는 `null`.
     *
     * 항목에서 읽지 않는 이유가 둘이다. 빈 응답에는 항목이 없어 걷기의 이름을 잃고, 항목의 멤버를
     * 읽는 것은 use case 가 원문을 **들여다보는** 일이라 구조 게이트가 막는다(그 금지는 옳다).
     */
    val observedAt: java.time.Instant? = null,
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
     * D-3F-1 (a) — 개찰완료(`getOpengResultListInfoOpengCompt`) 단건 조회. [fetchReservePrices]
     * 와 같은 성질(공고당 1콜·증거 값 요구, D-3F-2 — [DetailFetchDecision] 재사용)이라 새 결정
     * 타입을 만들지 않는다. 투찰자별 행은 raw_observation 감사 기록까지만 나른다(D-3F-3 해소 —
     * canonical 승격은 이 port 의 몫이 아니다).
     */
    fun fetchOpeningCompleteResults(evidence: DetailFetchDecision.Fetch): SourceBatch<RawNoticeObservation>

    /**
     * M6/6G D-6G-12 — 입찰가격산식 A 정보(`getBidPblancListBidPrceCalclAInfo`) 단건 조회.
     * 위 둘과 같은 성질(공고당 1콜 · 증거 값 요구)이라 새 결정 타입을 만들지 않는다. A 합산
     * 항목·적용 여부 술어·공개일시는 raw 관측까지만 간다 — canonical 자리는 이 slice 가 열지
     * 않는다(D-6G-1 「새 표·마이그레이션 없음」). **낙찰정보서비스가 아니라 입찰공고정보
     * 서비스**의 오퍼레이션이라 baseUri 가 다른 서비스를 가리킨다.
     */
    fun fetchBidPriceFormulaA(evidence: DetailFetchDecision.Fetch): SourceBatch<RawNoticeObservation>

    /**
     * M6/6G D-6G-19 — 기초금액 조회(`getBidPblancListInfo{Thng,Cnstwk,Servc}BsisAmount`) 단건 조회.
     * **업무 대분류마다 다른 오퍼레이션**이라 구현 인스턴스가 자기 업무의 경로를 안다(호출부가 고르지
     * 않는다). 예가 범위율·기초금액 공개일시와 공사 전용 칸을 나른다.
     */
    fun fetchBaseAmount(evidence: DetailFetchDecision.Fetch): SourceBatch<RawNoticeObservation>
}

/**
 * 자격 원문·게시 낙찰하한율 수집 port(①, COL-04). [fetchQualificationText]의 서명이
 * [QualificationFetchDecision.Fetch] 값을 요구한다 — 조회 가치 술어([decideQualificationFetch])
 * 를 거치지 않은 호출은 컴파일되지 않는다(D-3B2-5 (a), 위협 모델 방어 (a), 우회 후보 (4)).
 */
interface DocumentSourcePort {
    fun fetchQualificationText(evidence: QualificationFetchDecision.Fetch): SourceBatch<RawNoticeObservation>
}
