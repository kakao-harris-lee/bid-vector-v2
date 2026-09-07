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
