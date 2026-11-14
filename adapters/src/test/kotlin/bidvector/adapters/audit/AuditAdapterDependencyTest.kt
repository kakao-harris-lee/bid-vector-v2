package bidvector.adapters.audit

import bidvector.adapters.architecture.isOutsideRoots
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * 이 패키지가 참조해도 되는 `bidvector.*` 좌표의 루트(D-6A1-22, `ProfileAdapterDependencyTest`·
 * `EvaluationAdapterDependencyTest`와 같은 형태·같은 이유). `ApiAuditStore`·`ApiAuditSql`은
 * `java.sql`·`java.time`·`javax.sql`만 쓴다 — domain·workflow 타입을 하나도 참조하지 않는다
 * (D-6A1-7, audit 행이 도메인 값이 아니라 원시 값만 나른다). 그래서 허용 루트는 **자기
 * 패키지 하나**다 — 다른 형제(`evaluation`·`qualification`·`profile`)와 달리 이 패키지는
 * `adapters.persistence`조차 참조하지 않는다(SQL 문 자신을 `ApiAuditSql`이 직접 갖는다 —
 * `Sql.kt`를 동시에 늘리지 않기 위해서다).
 */
internal val ALLOWED_ROOTS: Set<String> = java.util.Set.copyOf(setOf("bidvector.adapters.audit"))

private fun isDisallowed(coordinate: String): Boolean = isOutsideRoots(coordinate, ALLOWED_ROOTS)

/**
 * **이 축의 판정은 이 class 에 없다** — [bidvector.adapters.architecture.ScopedBytecodeLayerGateTest] 가 표를 순회하며 한다(M12/12A ④).
 * 여기 남은 것은 이 축에만 있는 주장이고, 아래는 그 판정의 술어가 **상수 풀**인 이유다.
 *
 * D-6A1-22 — **소스 텍스트가 아니라 컴파일된 클래스의 상수 풀을 `javap -p -v`로 훑는다.**
 * import 문 없이 전체 한정 좌표로 직접 참조하는 우회를 소스 텍스트 정규식 형태는 못 본다
 * (6F-1 verifier MUT-E3 실측) — 신설 게이트는 구조로 닫는 쪽만 쓴다.
 */
class AuditAdapterDependencyTest {
    @Test
    fun `허용 루트(자기 패키지)는 이 술어에 걸리지 않는다`() {
        isDisallowed("bidvector.adapters.audit") shouldBe false
    }
}

