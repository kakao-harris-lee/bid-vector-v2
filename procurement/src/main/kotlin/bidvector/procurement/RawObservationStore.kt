package bidvector.procurement

/**
 * raw 관측의 결정적 식별자(③, M3/3D 신설, ADR 0005 D-10.1 「domain이 의존하는 port는 domain
 * 안에 선다」) — 재수집 재시도가 같은 [RawNoticeObservation]을 다시 보내면 같은 키를 낸다
 * (append 멱등의 유일한 근거). 이 타입 자체는 **값만** 나른다 — 유도(어떤 문자열을
 * `ObservationKey`로 만들지 결정하는 규칙)는 **어댑터(3D adapters) 소유**다
 * (verifier r1 F-2 뒤 정정).
 *
 * **왜 procurement가 유도 규칙을 갖지 않는가**: 충돌에 강한 유도(암호학적 해시)는
 * `java.security.MessageDigest`가 필요한데 domain 모듈은 그 패키지를 볼 수 없다
 * (`architecture-policy.properties` T-C 허용 목록 밖). [RawNoticeObservation]이 계약 없는
 * 값 열람도 막으므로(위협 모델 우회 (1)(10)) procurement 안에서 만들 수 있는 유일한
 * 내용-기반 지문은 `Object.hashCode()`류(32비트, 충돌 가능 — 실측: `"Aa"`·`"BB"`가
 * `String.hashCode()`에서 같은 값을 낸다)뿐이었고, 그것이 F-2의 원인이었다. 어댑터는
 * `KonepsFieldContractRegistry`(계약 등재 필드 전체)에 접근할 수 있어 완전한 정본
 * 직렬화(`ObservationPayloadCodec.encode`)를 얻고, `MessageDigest`도 domain 제약 밖이라
 * SHA-256을 쓸 수 있다 — 실제 유도는 `bidvector.adapters.persistence
 * .ObservationKeyDerivation.of(observation, fieldContracts)`.
 */
data class ObservationKey(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "ObservationKey는 빈 문자열일 수 없다" }
    }
}

/**
 * raw 키 재료의 행 구별 축(M3/3E 신설, D-3E-1a (a)) — 한 응답(페이지) 안에 같은 raw 관측 재료
 * (`sourceEndpoint`·`observedAt`·등재분 투영·`sourceText`)를 내는 복수 행이 있을 때(예: 복수예비가격
 * 15행 중 순번이 부재하는 행들) [ObservationKeyDerivation]이 키 충돌로 조용히 접는 것을 막는다
 * (`OPEN-3B2-STORAGE-ROW-KEY-COLLISION`). **값이 있으면 값이 우선이다** — [of]가 그 규칙을 강제한다.
 * 유도(어느 raw 키를 식별자로 볼지, 언제 이 축을 재료에 넣을지)는 어댑터 소유다 — 이 타입 자체는
 * 값만 나른다([RawObservationStore.kt] 파일 KDoc과 같은 경계).
 */
sealed interface RowDiscriminator {
    /** 오퍼레이션이 선언한 행 식별자 값이 있다 — 그 값이 키 재료다(응답 안 위치보다 우선). */
    data class Identified(
        val value: String,
    ) : RowDiscriminator {
        init {
            require(value.isNotBlank()) { "Identified 값은 빈 문자열일 수 없다" }
        }
    }

    /**
     * 행 식별자 값이 부재·공백이다 — 그 응답(페이지) 안에서의 위치(0-based)가 키 재료다.
     * 부재를 `0`·`""` 같은 값으로 지어내지 않는다(3B-2 G-1과 같은 「모름을 빈 문자열로
     * 표현하지 않는다」 규율) — `Identified`와 `Positional`을 타입으로 갈라 그 구분이 항상
     * 명시적이다.
     */
    data class Positional(
        val ordinal: Int,
    ) : RowDiscriminator {
        init {
            require(ordinal >= 0) { "Positional ordinal은 음수일 수 없다" }
        }
    }

    companion object {
        /**
         * 값 우선 규칙(D-3E-1a (a) — 「값이 있으면 값 우선, 부재·공백이면 응답 안 위치」)의
         * 유일한 조립 지점이다. 호출부가 이 규칙을 각자 다시 구현하면(예: 값이 있어도
         * ordinal을 쓰는 경로) 응답 순서가 바뀔 때 같은 행이 다른 키를 얻는다(설계 검토
         * (2) 층 A 「하지 말 것」) — 이 factory 하나로 그 실수를 막는다.
         */
        fun of(
            value: String?,
            ordinalIfAbsent: Int,
        ): RowDiscriminator = if (value.isNullOrBlank()) Positional(ordinalIfAbsent) else Identified(value)
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
    /**
     * `rowDiscriminator`(M3/3E 신설, 기본값 `null`) — 한 응답 안에 행이 복수인 오퍼레이션의
     * 호출부만 넘긴다(예: 복수예비가격 상세). 목록 오퍼레이션(한 행=한 공고)의 기존 호출부는
     * 이 인자를 몰라도 되고, 유도 재료가 그대로라 키도 그대로다(하위호환).
     */
    fun append(
        observation: RawNoticeObservation,
        rowDiscriminator: RowDiscriminator? = null,
    ): ObservationKey
}
