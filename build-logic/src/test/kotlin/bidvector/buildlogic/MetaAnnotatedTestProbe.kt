package bidvector.buildlogic

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * vr r1 H-2 의 **실물 fixture** — 저자가 **그 모듈의 test 소스에** 선언한 합성 애노테이션.
 *
 * JUnit 은 메타 애노테이션을 따라가므로 이것만 단 메서드도 실제로 돌린다. 등재 등식의 모집단 판정이
 * 그 사실을 따라가지 못하면 그 클래스는 **등재 없이 조용히** 통과한다 — 그래서 가짜 이름이 아니라
 * 컴파일된 class 파일로 재는 자리를 둔다([MetaAnnotatedProbeTest] 와 `GateRegistrationCensusTest`).
 */
@Test
@Retention(AnnotationRetention.RUNTIME)
annotation class GateProbeTest

/**
 * 합성 애노테이션만 단 test 클래스. **JUnit 이 이것을 돌린다**(이 파일이 그 사실의 증거다) —
 * 그러므로 등재 등식의 모집단에도 들어야 한다.
 */
class MetaAnnotatedProbeTest {
    @GateProbeTest
    fun `합성 애노테이션만 단 메서드도 JUnit 이 돌린다`() {
        assertTrue(true)
    }
}
