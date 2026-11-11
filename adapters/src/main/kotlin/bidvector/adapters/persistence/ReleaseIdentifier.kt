package bidvector.adapters.persistence

/**
 * 이 프로세스가 쓰는 행에 찍히는 **릴리스 식별자**(M9 OPS-10) — 배포 식별자(git SHA 등)이고
 * 구성 근이 설정에서 받아 주입한다. 값을 모르면 **지어내지 않는다**: 설정 키에 기본값이 없어
 * 기동이 실패하고(`bidvector.release.sha`), 빈 문자열은 이 타입이 거부한다.
 *
 * **문자열이 아니라 타입인 이유**: 이 값을 받는 저장소가 셋이고 전부 생성자 인자가 문자열
 * 여럿이다(`releaseSha`·`jdbcUrl`·`username`…). 같은 타입이면 자리를 바꿔 넣어도 컴파일된다.
 *
 * **`value class` 가 아니다** — 인라인 클래스는 JVM 시그니처에서 `String` 으로 지워져 Spring
 * 의 타입 주입이 「`java.lang.String` 빈이 없다」로 실패한다(실측: `CollectionWiringTest` 가
 * 붉었다). 이 값은 구성 근에서 **빈으로** 흐르므로 지워지지 않는 타입이어야 한다.
 *
 * **라벨↔빌드 동일성은 이 타입이 보장하지 않는다**(`OPEN-6G-RELEASE-SHA-LABEL`) — 운영자가
 * 댄 라벨이 실제로 그 jar 를 가리키는지는 배포 절차(runbook)의 몫이다.
 */
data class ReleaseIdentifier(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "릴리스 식별자는 빈 문자열일 수 없다" }
        require(value.length <= MAX_LENGTH) { "릴리스 식별자가 너무 길다(${value.length} > $MAX_LENGTH)" }
    }

    override fun toString(): String = value

    companion object {
        /** 저장 열이 TEXT 라 DB 상한은 없다 — 설정 오타(파일 내용·로그 한 줄 붙여넣기)를 자르는 값이다. */
        const val MAX_LENGTH = 128
    }
}
