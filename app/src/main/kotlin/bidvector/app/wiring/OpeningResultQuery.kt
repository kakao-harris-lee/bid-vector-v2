package bidvector.app.wiring

import bidvector.procurement.NoticeId
import bidvector.workflow.opening.OpeningResultView
import bidvector.workflow.opening.ReadOpeningResult

/**
 * 개찰 결과 조회기(D-6A2b-8 — `app.http` 는 포트가 아니라 조회기를 받는다). 포트에 읽기
 * 하나뿐이어도 조회기를 끼우는 이유는 7C① 과 같다: HTTP 로 닿는 층이 포트 타입을 **쥐지 않게**
 * 한다(새 메서드가 포트에 생기면 그것이 곧 HTTP 지름길이 된다).
 *
 * 하는 일은 위임 하나다 — 시계·정책을 더해 상태를 내는 것은 [ReadOpeningResult] 이고 이
 * 클래스는 그 판정을 복제하지 않는다.
 */
class OpeningResultQuery(
    private val openingResults: ReadOpeningResult,
) {
    fun read(id: NoticeId): OpeningResultView = openingResults.read(id)
}
