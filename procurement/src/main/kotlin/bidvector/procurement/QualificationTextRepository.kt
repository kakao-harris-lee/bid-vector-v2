package bidvector.procurement

/** `QualificationText` canonical fact의 저장 port(ADR 0005 D-10.1) — [NoticeRepository]와 같은 형태(⑤). */
interface QualificationTextRepository {
    fun persist(
        text: QualificationText,
        observationKey: ObservationKey,
    ): PersistOutcome

    fun find(id: NoticeId): QualificationText?
}
