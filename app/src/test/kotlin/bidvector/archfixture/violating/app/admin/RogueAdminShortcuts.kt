package bidvector.archfixture.violating.app.admin

import bidvector.adapters.persistence.TransactionBoundary
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

/**
 * D-6A2b-19 위반 표본 — **HTTP 층 밖 패키지의 진짜 핸들러**. 이 형태(MU2b)를 심으면 전건
 * `check` 가 초록이다: 게이트가 대상을 패키지 이름 `app.http` 하나로 잡으면
 * 다른 패키지의 `@RestController` 를 아예 보지 못한다.
 *
 * production classpath 에는 오르지 않는다(test 소스). 실제 요청을 받지도 않는다 — 게이트가
 * **구조**(핸들러 애너테이션)로 대상을 고르는지만 잰다.
 */
@RestController
class RogueAdminBumpController(
    private val jdbc: JdbcClient,
) {
    @PostMapping("/api/strategy/admin-bump")
    fun bump(): Int = jdbc.sql("UPDATE operator_strategy SET revision = revision + 1 WHERE id = 1").update()
}

/**
 * MU2 의 헬퍼 — 트랜잭션 경계 빈을 쥐고 원시 SQL 을 돈다. **이 클래스 자체는 경계 밖**이다
 * (D-6A2b-19 ④: HTTP 로 닿지 않는 app 내부 코드의 SQL 은 빌드 저자 경계와 같은 층).
 * 아래 컨트롤러가 이것을 참조하는 순간 허용 목록 밖이라 걸린다 — 그 전이가 이 표본의 요점이다.
 */
class RogueAdminSqlHelper(
    private val transactions: TransactionBoundary,
) {
    fun bump() =
        transactions.inTransaction {
            transactions.withConnection { connection ->
                connection.prepareStatement("UPDATE operator_strategy SET revision = revision + 1").executeUpdate()
            }
        }
}

@RestController
class RogueAdminBoundaryController(
    private val helper: RogueAdminSqlHelper,
) {
    @PostMapping("/api/strategy/admin-boundary-bump")
    fun bump(): Int = helper.bump()
}
