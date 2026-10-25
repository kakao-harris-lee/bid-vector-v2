package bidvector.app.http

import bidvector.app.wiring.OpeningResultQuery
import bidvector.procurement.NoticeId
import bidvector.workflow.opening.NoticeOpeningFacts
import bidvector.workflow.opening.OPENING_PROGRESS_POLICY
import bidvector.workflow.opening.OpeningResultFactsPort
import bidvector.workflow.opening.ReadOpeningResult
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 7C⑤-a — 개찰 결과 조회 포트의 이중체. HTTP 층이 **사실을 어떤 응답으로 옮기는가**만 재고,
 * 실 질의 거동(열 집합·읽기 전용·원문 존재)은 `bidvector.adapters.opening
 * .JdbcOpeningResultViewTest`(실 PostgreSQL)가 잰다.
 */
class FakeOpeningResultFactsPort : OpeningResultFactsPort {
    @Volatile var facts: NoticeOpeningFacts = NoticeOpeningFacts.NoticeAbsent

    @Volatile var lastLookup: NoticeId? = null

    override fun lookup(id: NoticeId): NoticeOpeningFacts {
        lastLookup = id
        return facts
    }
}

/**
 * 개찰 결과 조회 경로의 **test 조립** — `HttpTestApplication` 과 같은 패키지라 그 스캔이 이 설정을
 * 집어 든다(그 클래스에 빈을 얹지 않는다).
 *
 * **자기 파일인 이유**: `HttpTestSupport.kt` 는 파일 크기 한도(500줄)에 닿아 있다. 레인마다 자기
 * 조립을 그 파일에 얹으면 한도가 레인 사이의 경합이 되고, 한 레인의 추가가 다른 레인의 병합을
 * 깨뜨린다 — 조립을 자기 파일로 두면 그 경합 자체가 없어진다.
 *
 * 출하 조립과 **같은 형태**로 꽂는다 — 조회기가 포트·시계·정책을 받은 판정 하나를 위임한다.
 * 정책은 운영 값 그대로다(test 전용 라벨 집합을 쓰면 문서 ↔ 구현 등식이 가짜가 된다).
 */
@Configuration
open class OpeningResultTestWiring {
    @Bean
    open fun fakeOpeningResultFactsPort(): FakeOpeningResultFactsPort = FakeOpeningResultFactsPort()

    @Bean
    open fun openingResultQuery(
        fakeOpeningResultFactsPort: FakeOpeningResultFactsPort,
        clock: FixedClock,
    ): OpeningResultQuery =
        OpeningResultQuery(ReadOpeningResult(fakeOpeningResultFactsPort, clock, OPENING_PROGRESS_POLICY))
}
