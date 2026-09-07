package bidvector.procurement

/** `OpeningResult` canonical fact의 저장 port(ADR 0005 D-10.1) — [NoticeRepository]와 같은 형태(⑤). */
interface OpeningResultRepository {
    fun persist(
        result: OpeningResult,
        observationKey: ObservationKey,
    ): PersistOutcome

    fun find(id: NoticeId): OpeningResult?
}
