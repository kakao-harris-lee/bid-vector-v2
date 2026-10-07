package bidvector.app.wiring

import bidvector.app.collection.CollectionTermination

/**
 * 종료 코드를 모아 두는 [CollectionTermination] 대역 — 러너가 **부른 값 그대로** 보관한다.
 * 러너의 종료 코드 매핑이 `terminate` 로 실제로 가는지는 이 목록이 유일한 증거다(cr L-2).
 *
 * production 종료 자리는 `exitProcess` 를 부른다 — 러너 본문을 돌리는 test 가 그것을 그대로
 * 쓰면 **test JVM 이 죽고** Gradle 은 그것을 「초록 + 남은 test 건너뜀」으로 보고한다(실측,
 * 초록으로 보이는 실패다). 그래서 러너를 돌리는 test 는 이 대역을 쓴다.
 *
 * Spring 빈 재정의로 바꿔치우지 않는다 — `@TestConfiguration` 은 컴포넌트 스캔에 걸리지 않아
 * 명시 등록이 필요하고(`BidNowFakeMlAnalysisTestConfiguration` 선례), 등록해도 `@Import` 된
 * production 정의와의 우선순위가 보장되지 않는다(실측). 러너를 쓰는 test 는 조립을 직접
 * 세워 이 대역을 생성자로 넘긴다.
 */
class RecordedExitCodes : CollectionTermination {
    private val codes = mutableListOf<Int>()

    override fun terminate(exitCode: Int) {
        codes += exitCode
    }

    fun recorded(): List<Int> = codes.toList()
}
