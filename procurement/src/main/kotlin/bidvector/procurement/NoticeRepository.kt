package bidvector.procurement

/**
 * `Notice` canonical fact의 저장 port(ADR 0005 D-10.1) — raw append → canonical fold → audit이
 * [persist] 하나의 항목 트랜잭션 안에 있다(⑤, 구현이 그 경계를 강제한다 — 이 인터페이스
 * 자체는 경계를 표현하지 않는다). `observationKey`는 [RawObservationStore.append]가 낸 값을
 * 그대로 전달받는다 — canonical fold의 입력이 raw 행을 가리켜야 한다(②).
 */
interface NoticeRepository {
    fun persist(
        command: NoticeCollected,
        observationKey: ObservationKey,
    ): PersistOutcome

    fun find(id: NoticeId): Notice?
}
