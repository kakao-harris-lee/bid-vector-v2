package bidvector.app.collection

/**
 * mock KONEPS 의 응답 축(M9/portal-02 D-3) — 오퍼레이션 **이름**이 아니라 이 축이 응답 형태를 정한다.
 * [detail] 은 공고번호로 부르는 단건 조회 축이다(목록 축은 표본틀을 만든다).
 */
internal enum class MockKonepsAxis(
    val detail: Boolean,
) {
    NOTICE_LIST(detail = false),
    OPENING_LIST(detail = false),
    RESERVE_PRICE(detail = true),
    OPENING_COMPLETE(detail = true),
    BASE_AMOUNT(detail = true),
    FORMULA_A(detail = true),
}

/**
 * 합성 공고번호의 업종 표식 — 공고 목록 축과 개찰 목록 축이 **같은 표식**을 써야 두 출처가 엮인다
 * (번호가 갈리면 추출이 아무것도 잇지 못한다). 업종 하나가 표식을 한 자리에서 가지므로 두 축이
 * 어긋날 수 없다. [category] 는 설정 표의 업종 키이고, [token] 은 공고번호와 합성 분류 fixture 가
 * 쓰는 짧은 이름이다(오퍼레이션 경로에서 잘라내지 않는다 — 그 잘라내기가 D-3 의 오라우팅이었다).
 */
internal enum class MockKonepsDivision(
    val category: String,
    val token: String,
) {
    CONSTRUCTION(category = "construction", token = "Cnstwk"),
    SERVICE(category = "service", token = "Servc"),
}

/** 오퍼레이션 하나의 라우팅 — 업종 공통 축(개찰완료·산식 A)은 [division] 이 `null` 이다. */
internal class MockKonepsRoute(
    val axis: MockKonepsAxis,
    val division: MockKonepsDivision?,
)

/**
 * **정확 일치** 라우팅(M9/portal-02 D-3) — 표에 없는 이름은 `null` 이고 호출자가 404 로 돌려준다.
 *
 * 앞 판은 접두·접미 술어(`startsWith`·`endsWith`·`removePrefix`)로 갈랐다. 조달청이 오퍼레이션명
 * 뒤에 `02` 를 붙이자 그 술어들이 **조용히** 빗나갔다: `…CnstwkBsisAmount02` 는 접미 `BsisAmount`
 * 에 더는 맞지 않지만 접두 `getBidPblancListInfo` 에는 여전히 맞아, 기초금액 요청이 공고 목록 응답을
 * 200 으로 받는다. 오라우팅은 호출 계수를 통째로 옮겨 E2E 가 재려던 것을 지운다.
 */
internal fun mockKonepsRouteOf(operation: String): MockKonepsRoute? = ROUTES[operation]

/** 공고 목록 축 한 오퍼레이션의 업종 — 합성 표본이 쓰는 자리(그 축이 아니면 기동 실패다). */
internal fun mockNoticeListDivisionOf(operation: String): MockKonepsDivision {
    val route = mockKonepsRouteOf(operation)
    require(route != null && route.axis == MockKonepsAxis.NOTICE_LIST && route.division != null) {
        "공고 목록 축이 아닌 오퍼레이션으로 합성 표본을 요청했다: $operation"
    }
    return route.division
}

/**
 * 표는 **production 기본값에서 만든다** — 오퍼레이션 이름을 여기에 다시 적으면 그 자리가 다음 개편에서
 * 한쪽만 바뀌는 자리가 된다(그래서 이 표는 이름의 문면을 잠그지 않는다 — 그것은
 * `KonepsEndpointDefaultsTest` 의 몫이다). 기본 표에 업종이 더해지면 [MockKonepsDivision] 에 표식이
 * 없어 **즉시 실패**한다: 모르는 업종이 조용히 404 로 미뤄지지 않는다.
 */
private val ROUTES: Map<String, MockKonepsRoute> = buildRoutes()

private fun buildRoutes(): Map<String, MockKonepsRoute> {
    val notice = KonepsEndpointProperties()
    val opening = KonepsOpeningEndpointProperties()
    val rows =
        notice.operations.map { (category, operation) ->
            operation.path to MockKonepsRoute(MockKonepsAxis.NOTICE_LIST, divisionOf(category))
        } +
            opening.operations.flatMap { (category, operation) ->
                val division = divisionOf(category)
                listOf(
                    operation.openingResultListPath to MockKonepsRoute(MockKonepsAxis.OPENING_LIST, division),
                    operation.reservePriceDetailPath to MockKonepsRoute(MockKonepsAxis.RESERVE_PRICE, division),
                    operation.baseAmountPath to MockKonepsRoute(MockKonepsAxis.BASE_AMOUNT, division),
                )
            } +
            listOf(
                opening.openingCompletePath to MockKonepsRoute(MockKonepsAxis.OPENING_COMPLETE, null),
                opening.bidPriceFormulaAPath to MockKonepsRoute(MockKonepsAxis.FORMULA_A, null),
            )
    // 겹친 이름은 한 축을 조용히 덮는다 — `toMap()` 의 마지막 승리를 거부로 바꾼다.
    require(rows.distinctBy { it.first }.size == rows.size) {
        "기본 표의 오퍼레이션 이름이 겹친다: ${rows.map { it.first }.sorted()}"
    }
    return rows.toMap()
}

private fun divisionOf(category: String): MockKonepsDivision =
    MockKonepsDivision.entries.firstOrNull { it.category == category }
        ?: error("mock 이 모르는 업종이다 — 기본 표에 업종을 더하면 표식도 더한다: $category")
