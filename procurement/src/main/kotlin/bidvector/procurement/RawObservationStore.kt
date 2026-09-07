package bidvector.procurement

/**
 * raw 관측의 결정적 식별자(③, M3/3D 신설, ADR 0005 D-10.1 「domain이 의존하는 port는 domain
 * 안에 선다」) — 재수집 재시도가 같은 [RawNoticeObservation]을 다시 보내면 같은 키를 낸다
 * (append 멱등의 유일한 근거). [RawNoticeObservation]은 계약 없는 값 열람을 막으므로(위협
 * 모델 우회 (1)(10), `RawObservation.kt` 헤더 KDoc) 이 키는 그 클래스가 이미 공개한 표면만
 * 으로 짓는다 — [RawNoticeObservation.sourceEndpoint]·[RawNoticeObservation.observedAt]·
 * [RawNoticeObservation.keys](필드 **이름** 집합이지 값이 아니다) · `hashCode()`.
 *
 * `hashCode()`는 `Objects.hash(fields, sourceEndpoint, observedAt)`이고, `String.hashCode()`는
 * JDK 문서가 다항식 공식을 고정하며 `Map.hashCode()`는 항목별 `key.hashCode() xor
 * value.hashCode()`의 합으로 정의된 인터페이스 계약이다 — `fields`(내용, 값 포함)·
 * `sourceEndpoint`·`observedAt`이 같으면 실행·JVM 세션이 달라도 **항상 같은 값**이 나온다.
 * 이것은 오버라이드하지 않은 `Object.hashCode()`(세션마다 달라짐)와는 다른 성질이다.
 *
 * **알려진 제한**: `hashCode()`는 32비트라 이론상 서로 다른 내용이 같은 지문으로 충돌할 수
 * 있다. sourceEndpoint·observedAt·필드 이름 집합을 함께 섞어 실무 위험을 낮추지만 완전히
 * 배제하지는 않는다 — 완전한 배제는 [RawNoticeObservation]에 전체 내용을 노출하는 API를
 * 더해야 하는데, 그 파일은 이 slice의 편집 대상이 아니다(scope.md out_of_scope, 「그 밖의
 * procurement 편집 금지」).
 */
data class ObservationKey(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "ObservationKey는 빈 문자열일 수 없다" }
    }

    companion object {
        private const val SEPARATOR = "|"

        /** [RawNoticeObservation] 하나에서 결정적으로 유도한다 — 같은 내용은 항상 같은 키. */
        fun of(observation: RawNoticeObservation): ObservationKey {
            val sortedKeyNames =
                observation.keys
                    .map { it.name }
                    .sorted()
                    .joinToString(separator = SEPARATOR)
            val parts =
                listOf(
                    observation.sourceEndpoint.name,
                    observation.observedAt.toString(),
                    sortedKeyNames,
                    observation.hashCode().toString(),
                )
            return ObservationKey(parts.joinToString(separator = SEPARATOR))
        }
    }
}

/** canonical write가 거부된 사유(⑥) — DB 트리거·Kotlin 규칙이 같은 어휘를 공유한다(3D 설계 검토). */
enum class RejectionReason {
    /** 권위 없는 유입이 이미 권위 있는 값을 덮으려 했다(`data-dictionary.md` §5.1 규율 1). */
    NON_AUTHORITATIVE_OVERWRITE,

    /** 이미 값이 있는 자리를 빈 값으로 덮으려 했다(존재 가드). */
    EMPTY_OVERWRITE_OF_EXISTING,
}

/** 저장 write 하나의 결과(⑤) — 항목 단위 트랜잭션 하나의 산출. */
sealed interface PersistOutcome {
    /** 새 canonical 행이 처음 생겼다. */
    data object Inserted : PersistOutcome

    /** 기존 canonical 행이 갱신됐다(revision 증가) — 갱신 뒤 revision 값. */
    data class Updated(
        val revision: Long,
    ) : PersistOutcome

    /** 재수집 값이 기존과 같아 갱신이 필요 없었다(멱등, ③). */
    data object Unchanged : PersistOutcome

    /** 점유 가드가 write를 거부했다. */
    data class Rejected(
        val reason: RejectionReason,
    ) : PersistOutcome
}

/**
 * raw 관측 저장 port(①·②, ADR 0005 D-10.1) — append-only. 구현(3D adapters)은 **먼저** raw
 * 행을 추가하고, 이후 단계가 실패해도 이미 커밋된 raw 행은 남는다(⑤). 같은 내용의 재시도는
 * 같은 [ObservationKey]를 내고 새 행을 만들지 않는다(멱등, ③).
 */
interface RawObservationStore {
    fun append(observation: RawNoticeObservation): ObservationKey
}
