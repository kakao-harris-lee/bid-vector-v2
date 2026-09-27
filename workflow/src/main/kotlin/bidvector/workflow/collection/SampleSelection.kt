package bidvector.workflow.collection

import bidvector.procurement.BusinessDivision
import java.security.MessageDigest
import java.time.LocalDate
import java.time.temporal.IsoFields

private const val HEX_MASK = 0xff

private fun sha256Hex(text: String): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and HEX_MASK) }

/**
 * 공고 하나의 익명 키(D-6G-2) — `sha256("<공고번호>/<차수>")`. **salt 가 없다**: 표본 선택
 * 술어가 같은 값을 다시 계산할 수 있어야 하고(재현), 공고번호 자체는 공개값이다. 이 해시가
 * 지우는 것은 비밀이 아니라 **조인 키**다 — 비식별의 대상은 공고가 아니라 투찰자다.
 */
@ConsistentCopyVisibility
data class NoticeKeyHash private constructor(
    val value: String,
) {
    companion object {
        fun of(
            noticeNumber: String,
            noticeRound: String,
        ): NoticeKeyHash = NoticeKeyHash(sha256Hex("$noticeNumber/$noticeRound"))

        /** 이미 계산된 해시를 되읽는다(표본 목록 파일) — 형태만 검사하고 값을 지어내지 않는다. */
        fun ofHex(raw: String): NoticeKeyHash {
            require(HEX_SHAPE.matches(raw)) { "공고 키 해시 형태가 아니다" }
            return NoticeKeyHash(raw)
        }

        private val HEX_SHAPE = Regex("[0-9a-f]{64}")
    }
}

/** 표본 seed — 정책 파일이 나르는 값이고 코드 리터럴이 아니다(사후에 바꾸면 새 version 이다). */
data class SamplingSeed(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "표본 seed 는 빈 문자열일 수 없다" }
    }
}

/** 층(D-6G-11) — 업무 대분류 × 공고 주. 공고 주는 ISO 주(`YYYY-Www`)다. */
data class SampleStratum(
    val division: BusinessDivision,
    val noticeWeek: String,
)

/**
 * 표본 후보 하나 — **결과를 나르는 칸이 없다**(우회 ⑦ 표본 쇼핑). 개찰 결과·투찰가·낙찰
 * 여부를 이 타입에 실을 수 없으므로 「결과를 보고 표본을 고르는」 경로가 구조적으로 닫힌다.
 * 주석이 아니라 타입이 그 닫힘이다.
 */
data class SampleCandidate(
    val key: NoticeKeyHash,
    val division: BusinessDivision,
    val noticeDate: LocalDate,
) {
    val stratum: SampleStratum
        get() =
            SampleStratum(
                division,
                "%d-W%02d".format(
                    noticeDate.get(IsoFields.WEEK_BASED_YEAR),
                    noticeDate.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),
                ),
            )
}

/**
 * 층 하나의 표집 결과 — [short] 는 「후보가 목표에 모자랐다」는 사실이다. 모자란 층을 조용히
 * 다른 층에서 채우지 않는다(층화 표본이 아니게 된다) — 사실로 나르고 계수는 보고가 한다.
 */
data class StratumOutcome(
    val target: Int,
    val available: Int,
    val taken: Int,
) {
    val short: Boolean get() = available < target
}

/**
 * 표집 전체 결과 — [sampleListSha256] 은 뽑힌 키의 **정렬 목록**에서만 나온다(후보 순서와
 * 무관). 판정 JSON 이 이 값을 실어 「결과를 보기 전에 확정된 목록」임을 고정한다.
 */
data class SampleOutcome(
    val selected: List<NoticeKeyHash>,
    val strata: Map<SampleStratum, StratumOutcome>,
    /** 뽑힌 키가 어느 층에서 왔는가 — 표본 목록 파일이 층을 함께 싣기 위해 필요하다(D-6G-39). */
    val strataByKey: Map<NoticeKeyHash, SampleStratum> = emptyMap(),
) {
    val sampleListSha256: String = sha256Hex(selected.map { it.value }.sorted().joinToString("\n"))
}

/**
 * 층화 무작위 표본(D-6G-11) — 무작위성의 출처는 난수 발생기가 아니라 **`sha256(seed | 공고 키
 * 해시)`** 다. 그래서 같은 seed·같은 후보 집합이면 언제 돌려도 같은 표본이 나오고, 뽑는 값이
 * 수집 결과와 무관하다는 것이 계산 그 자체로 선다.
 *
 * 난수 발생기를 쓰지 않는 이유: 상태가 있는 발생기는 후보를 **주는 순서**가 표본을 바꾼다 —
 * 수집 순서(응답이 온 순서)가 표본의 입력이 되면 「결과와 무관」이 깨진다.
 */
class StratifiedSampler(
    private val seed: SamplingSeed,
    private val targetPerStratum: Int,
) {
    init {
        require(targetPerStratum > 0) { "층당 목표 표본 수는 양수여야 한다: $targetPerStratum" }
    }

    fun select(candidates: List<SampleCandidate>): SampleOutcome {
        val byStratum = candidates.distinctBy { it.key }.groupBy { it.stratum }
        val selected = mutableListOf<NoticeKeyHash>()
        val outcomes = mutableMapOf<SampleStratum, StratumOutcome>()
        val strataByKey = mutableMapOf<NoticeKeyHash, SampleStratum>()
        for ((stratum, pool) in byStratum) {
            val tickets = pool.map { DrawTicket(drawOrderOf(it.key), it.key) }.sortedWith(DRAW_ORDER)
            val taken = tickets.take(targetPerStratum).map { it.key }
            selected += taken
            taken.forEach { strataByKey[it] = stratum }
            outcomes[stratum] = StratumOutcome(targetPerStratum, pool.size, taken.size)
        }
        return SampleOutcome(selected.sortedWith(NOTICE_KEY_ORDER), outcomes, strataByKey)
    }

    private fun drawOrderOf(key: NoticeKeyHash): String = sha256Hex("${seed.value}|${key.value}")
}

/** 뽑기 순서와 그 키 — 순서를 미리 계산해 두면 비교마다 해시를 다시 돌리지 않는다. */
private class DrawTicket(
    val order: String,
    val key: NoticeKeyHash,
)

// 명시 Comparator 다 — `sortedBy` 는 stdlib 출처의 합성 비교자 클래스를 산출물에 남겨
// jarContentGate(게이트를 통과한 소스만 아카이브에 든다)가 거부한다.
private val DRAW_ORDER = Comparator<DrawTicket> { left, right -> left.order.compareTo(right.order) }
val NOTICE_KEY_ORDER = Comparator<NoticeKeyHash> { left, right -> left.value.compareTo(right.value) }
