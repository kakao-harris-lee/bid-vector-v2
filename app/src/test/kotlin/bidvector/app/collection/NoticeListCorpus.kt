package bidvector.app.collection

import java.time.LocalDate

/**
 * 공고 목록 E2E 의 **합성 표본**(D-6F8-11 · D-6F9-2) — mock 서버가 슬롯마다 내는 항목들이다.
 * 정상 셋에 더해 **일부러 어긋난 것**을 섞는다: 빈 문자열 옵션 값 · 번호 없는 항목 · 중복 · 날짜가
 * 아닌 마감 · 세 자리가 아닌 차수. 그 어긋남이 회계의 탈락 사유를 실제로 밟게 한다.
 *
 * `CollectionRunnerE2ETest` 에서 갈라낸 파일이다(sizeGate 500) — **표본이 무엇인가**와 **그것으로
 * 무엇을 단언하는가**는 따로 바뀐다.
 */
internal const val NORMAL_PER_SLOT = 3

internal const val BAD_DATE_ITEM_NUMBER = "BAD-DATE-1"

internal const val BAD_ROUND_ITEM_NUMBER = "BAD-ROUND-1"

private fun itemOf(
    category: String,
    number: String,
    closing: String = "2026-12-31 10:00:00",
    classification: Map<String, String> = emptyMap(),
) = mapOf(
    "bidNtceNo" to number,
    "bidNtceOrd" to "000",
    "bidNtceNm" to "공고명 $category $number",
    // 실 응답에는 이 키가 없다(6F-8 실측) — 있어도 대분류는 오퍼레이션이 정한다(D-6F9-1): 용역 응답에도 「공사」를 싣는다.
    "bsnsDivNm" to "공사",
    "bidClseDt" to closing,
) + classification

internal fun noticeListItemsFor(
    operation: String,
    day: String,
    firstDay: LocalDate,
): List<Map<String, String>> {
    val category = operation.removePrefix("getBidPblancListInfo")

    fun item(
        number: String,
        closing: String = "2026-12-31 10:00:00",
        classification: Map<String, String> = emptyMap(),
    ) = itemOf(category, number, closing, classification)
    val normal =
        (1..NORMAL_PER_SLOT).map {
            item(
                "E2E-$category-$day-$it",
                classification = classificationFor(category, it),
            )
        }
    // D-6F8-11 — KONEPS 는 옵션 일시·금액을 빈 문자열로 내기도 한다(실수집 실측). 합성 표본이며 정규화되고 마감은 null 이다.
    val blankOptionals =
        item("E2E-$category-$day-BLANK", closing = "", classification = blankClassificationFor(category)) +
            mapOf("opengDt" to "", "bssamt" to "", "presmptPrce" to "", "chgDt" to "", "tpEvalApplClseDt" to "")
    val missingNumber = mapOf("bidNtceNm" to "번호 없는 공고명")
    val duplicate = normal.first()
    val firstConstructionDay = category == "Cnstwk" && day == firstDay.toString().replace("-", "")
    val badDate =
        if (firstConstructionDay) {
            listOf(
                item(BAD_DATE_ITEM_NUMBER, closing = "not-a-date"),
            )
        } else {
            emptyList()
        }
    // 차수가 비어 있지 않지만 세 자리 숫자가 아니다 — 어댑터는 통과시키고 정규화가 IDENTIFIER 탈락으로 접는다.
    val badRound =
        if (firstConstructionDay) {
            listOf(
                item(BAD_ROUND_ITEM_NUMBER) + mapOf("bidNtceOrd" to "1"),
            )
        } else {
            emptyList()
        }
    return normal + listOf(blankOptionals) + missingNumber + duplicate + badDate + badRound
}
