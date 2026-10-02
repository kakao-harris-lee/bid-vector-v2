package bidvector.adapters.snapshot

import bidvector.procurement.BusinessDivision
import bidvector.procurement.KONEPS_COLLECTION_POLICY
import bidvector.procurement.KonepsCollectionPolicyData
import bidvector.procurement.NoticeNumber
import bidvector.procurement.SourceEndpoint
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Resolution
import java.time.Instant
import java.time.LocalDate

/**
 * 출하 조립(`assembleSnapshotRow`)을 **값으로** 부르는 두 test 클래스의 공통 하네스 — 소수 금액 축
 * (`SnapshotAmountContractTest`)과 A 묶음 축(`SnapshotAValueContractTest`)이 같은 조립·같은 정책·같은
 * 공고 하나를 쓴다. 사본을 두면 「온전한 판」의 뜻이 둘로 갈리고, 한쪽만 고친 표류가 조용해진다.
 *
 * 실 Postgres 가 필요 없는 자리다 — 재는 것이 「원문이 어떤 바이트가 되는가」이고 그 판정은 조립이 한다.
 */
internal fun assemblyPolicy(): KonepsCollectionPolicyData =
    (KONEPS_COLLECTION_POLICY.resolve(LocalDate.of(2026, 9, 7)) as Resolution.Resolved).value

internal val ASSEMBLY_KEY = NoticeKey(NoticeNumber.of("20260617001-00").value, NoticeRound.of("000"))

internal val ASSEMBLY_CANONICAL =
    CanonicalNotice(
        division = BusinessDivision.CONSTRUCTION.name,
        bidCloseAt = Instant.parse("2026-06-16T05:00:00Z"),
        floorRate = null,
    )

/** A 합산액의 구성 항목 — 술어가 참인 품질관리비까지 일곱이다(스키마 §3.3). */
internal val A_COMPONENT_KEYS =
    listOf("npnInsrprm", "mrfnHealthInsrprm", "odsnLngtrmrcprInsrprm", "rtrfundNon", "sftyMngcst", "sftyChckMngcst")

internal const val A_COMPONENT_AMOUNT = "1000000"

/** 품질관리비의 합산 대상 술어와 그 금액 — 이 둘의 짝이 D-6G2d-28 의 자리다. */
internal const val QUALITY_PREDICATE_KEY = "qltyMngcstAObjYn"

/** A 적용 술어의 원문 키 — 기초금액 축이 나른다(공사 전용). */
internal const val A_APPLICABLE_KEY = "bidPrceCalclAYn"

internal const val QUALITY_COST_KEY = "qltyMngcst"

internal const val RESERVE_PRICE_AMOUNT = "1200000000"

internal const val PLANNED_PRICE = "1250000000"

internal const val OPENING_BASE_AMOUNT = "1239999999"

/** 기초금액은 **마감 전에 공개된** 값만 싣는다(D-6G-19) — 공개일시가 없으면 그 칸은 읽히지도 않는다. */
internal const val BASE_AMOUNT_DISCLOSED_AT = "2026-06-10 09:00:00"

abstract class SnapshotAssemblyFixture {
    protected fun render(
        baseAmountFields: Map<String, String> = emptyMap(),
        formulaAFields: Map<String, String> = emptyMap(),
        reserveFields: List<Map<String, String>> = emptyList(),
        openingFields: List<Map<String, String>> = emptyList(),
        /**
         * 기초금액 축 행의 `bidPrceCalclAYn` — 스냅숏의 `bid_price_formula_a_applicable` 칸이 된다.
         * **`incompleteAValues` 를 가르지 않는다**(D-6G2e-4): 그 계수는 A 축 행 자신이 가른다.
         */
        formulaAApplicable: Boolean = true,
        /** 기초금액 축이 **걷히지 않은** 공고 — 그 축의 술어를 읽을 수 없는 자리다(D-6G2e-4). */
        baseAmountAxisCollected: Boolean = true,
    ): Rendered {
        val tally = AssemblyTally()
        val baseAmount = baseAmountFields + mapOf(A_APPLICABLE_KEY to if (formulaAApplicable) "Y" else "N")
        val axes =
            buildMap<SourceEndpoint, List<RawRow>> {
                if (baseAmountAxisCollected) put(SourceEndpoint.BASE_AMOUNT_DETAIL, listOf(rawRow(baseAmount)))
                put(SourceEndpoint.BID_PRICE_FORMULA_A, listOf(rawRow(formulaAFields)))
                put(SourceEndpoint.RESERVE_PRICE_DETAIL, reserveFields.map(::rawRow))
                put(SourceEndpoint.OPENING_COMPLETE, openingFields.map(::rawRow))
            }
        val row = assembleSnapshotRow(ASSEMBLY_KEY, axes, ASSEMBLY_CANONICAL, tally)
        return Rendered(SnapshotWriter.renderRows(listOf(row)), tally.fractionalAmounts, tally.incompleteAValues)
    }

    /** 렌더 결과와 그때의 계수 둘 — 원인이 다른 계수를 한 수로 접지 않는다. */
    protected class Rendered(
        val bytes: String,
        val fractional: Int,
        val incompleteAValues: Int,
    )

    internal fun rawRow(fields: Map<String, String>): RawRow = RawRow(fields.mapValues { it.value }, assemblyPolicy())

    /**
     * 온전한 A 묶음 — 구성 항목 여섯과 공개일시, 그리고 **품질관리비 술어**다. 술어가 `Y`/`N` 밖이면
     * A 를 낼 수 없으므로(D-6G2d-28) 온전한 판은 그 술어를 명시한다: `N` 이면 그 항목은 합산 대상이
     * 아니고, 대상이 아닌 것의 부재는 결측이 아니다.
     */
    protected fun intactFormulaA(overrides: Map<String, String> = emptyMap()): Map<String, String> =
        A_COMPONENT_KEYS.associateWith { A_COMPONENT_AMOUNT } +
            mapOf("bidPrceCalclAOpenDt" to "2026-06-10 09:00:00", QUALITY_PREDICATE_KEY to "N") + overrides

    /** 기초금액 축의 온전한 행 — 공개일시가 마감보다 앞이라 기초금액이 실린다. */
    protected fun intactBaseAmount(overrides: Map<String, String> = emptyMap()): Map<String, String> =
        mapOf(
            "bssamt" to "1234567890",
            "bssAmtPurcnstcst" to "900000000",
            "bssamtOpenDt" to BASE_AMOUNT_DISCLOSED_AT,
        ) + overrides

    /**
     * 온전한 예비가격 15행 — 위치가 순번이라는 규약을 지킨다. 예정가격과 개찰 기초금액도 이 축에서
     * 오므로 첫 행이 함께 나른다(스키마 §3.4).
     */
    protected fun intactReserveRows(
        fractionalAt: Int? = null,
        scalars: Map<String, String> = mapOf("plnprc" to PLANNED_PRICE, "bssamt" to OPENING_BASE_AMOUNT),
    ): List<Map<String, String>> =
        (1..RESERVE_PRICE_SLOTS).map { sequence ->
            mapOf(
                "compnoRsrvtnPrceSno" to sequence.toString(),
                "bsisPlnprc" to if (sequence == fractionalAt) "$RESERVE_PRICE_AMOUNT.5" else RESERVE_PRICE_AMOUNT,
            ) + if (sequence == 1) scalars else emptyMap()
        }
}
