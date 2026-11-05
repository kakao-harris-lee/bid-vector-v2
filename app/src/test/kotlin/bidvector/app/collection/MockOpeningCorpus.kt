package bidvector.app.collection

/**
 * 개찰 축 E2E 의 **합성 투찰 표본** — `MockOpeningKonepsHttp` 에서 갈라낸 파일이다(sizeGate 500,
 * `NoticeListCorpus` 와 같은 근거): **표본이 무엇인가**와 **그 표본을 어느 축으로 내는가**는 따로 바뀐다.
 */
internal class SyntheticBidder(
    val rank: Int,
    val amount: String,
    val firstDraw: String,
    val secondDraw: String,
)

/** 실 응답이 상호와 함께 싣는 대표자명 — 스냅숏에 **없어야** 한다(privacy r2 INFO-4). */
internal const val MOCK_REPRESENTATIVE_NAME = "SYN-대표자"

/** 복수예가 행 수 — 한 공고의 예비가격 상세가 내는 행 전부다(하나만 내면 제외 ⑤ 가 전 행을 걷어낸다). */
internal const val RESERVE_PRICE_ROWS = 15

/** 추첨으로 뽑힌 복수예가 순번(D-6G-38) — 추출의 출처가 여기여야 golden 이 선다. */
internal val DRAWN_SEQUENCES = setOf(3, 7, 11, 14)

/**
 * 합성 투찰자 셋(D-6G-38) — 금액이 서로 달라 동가 1위(제외 ⑮)가 생기지 않는다.
 *
 * **선택(`drwtNo1`·`drwtNo2`)을 뽑힌 넷([DRAWN_SEQUENCES])과 일부러 다르게 둔다.** r1 의 mock 은 둘을
 * 같게 맞춰 두어, 추출이 **틀린 출처**(투찰자 선택)에서 읽고 있는데도 golden 이 초록이었다. 겹치지
 * 않게 두면 출처가 틀린 순간 golden 이 붉어진다.
 */
internal val BIDDERS =
    listOf(
        SyntheticBidder(rank = 1, amount = "1100000000", firstDraw = "1", secondDraw = "2"),
        SyntheticBidder(rank = 2, amount = "1150000000", firstDraw = "4", secondDraw = "5"),
        SyntheticBidder(rank = 3, amount = "1200000000", firstDraw = "6", secondDraw = "8"),
    )

/**
 * 참가가 많은 판의 투찰 행(D-6G2f-3 ⓐ) — 금액이 서로 달라 동가 1위(제외 ⑮)가 생기지 않고, 추첨번호는
 * 복수예가 행 범위를 돈다. 고정 셋([BIDDERS])과 달리 **수를 받아** 짓는다: 한 호출에 들어가지 않는
 * 참가 규모가 이 판의 입력이다.
 */
internal fun crowdedBidder(rank: Int) =
    SyntheticBidder(
        rank = rank,
        amount = (CROWDED_BID_AMOUNT + rank * CROWDED_BID_AMOUNT_STEP).toString(),
        firstDraw = (rank % RESERVE_PRICE_ROWS + 1).toString(),
        secondDraw = ((rank + 1) % RESERVE_PRICE_ROWS + 1).toString(),
    )

private const val CROWDED_BID_AMOUNT = 1_100_000_000L
private const val CROWDED_BID_AMOUNT_STEP = 1_000L
