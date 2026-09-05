package bidvector.buildlogic

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals

/**
 * 타입 멤버 수 측정의 음성·양성 — `size-policy.properties` `limit.type.members`(D-1)의
 * 정의를 고정한다. [KotlinFunctionLengthsTest]와 같은 순수 함수 패턴(Gradle 없이 PSI
 * 카운터를 직접 호출).
 */
class TypeMembersTest {
    @TempDir
    lateinit var dir: File

    private fun measure(source: String): List<MeasuredType> {
        val file = File(dir, "Probe.kt").apply { writeText(source) }
        return measureTypes(listOf(file))
    }

    private fun properties(n: Int) = (1..n).joinToString("\n") { "    val p$it: Int = $it" }

    @Test
    fun `31개 멤버는 상한 30을 넘고 30개는 넘지 않는다`() {
        val over = measure("package p\n\nclass C {\n${properties(31)}\n}\n").single { it.name == "C" }
        val exact = measure("package p\n\nclass C {\n${properties(30)}\n}\n").single { it.name == "C" }
        assertEquals(31, over.memberCount)
        assertEquals(30, exact.memberCount)
    }

    @Test
    fun `data class 의 컴파일러 합성 멤버는 세지 않는다`() {
        val type = measure("package p\n\ndata class D(val a: Int, val b: Int)\n").single { it.name == "D" }
        assertEquals(
            2,
            type.memberCount,
            "주 생성자 프로퍼티 둘만 — getter·componentN·copy·equals/hashCode/toString 은 소스에 없다",
        )
    }

    @Test
    fun `주 생성자 프로퍼티와 본문 프로퍼티 함수 보조생성자 init 을 합산한다`() {
        val source =
            "package p\n\n" +
                "class C(val a: Int) {\n" +
                "    val b: Int = 1\n" +
                "    fun run(): Int = 1\n" +
                "    constructor() : this(0)\n" +
                "    init { check(true) }\n" +
                "}\n"
        val type = measure(source).single { it.name == "C" }
        assertEquals(5, type.memberCount, "a(생성자 프로퍼티) + b(프로퍼티) + run(함수) + constructor(보조 생성자) + init")
    }

    @Test
    fun `프로퍼티 접근자는 프로퍼티에 포함되고 따로 세지 않는다`() {
        val source =
            "package p\n\n" +
                "class C {\n" +
                "    var v: Int = 0\n" +
                "        get() = field\n" +
                "        set(value) { field = value }\n" +
                "}\n"
        val type = measure(source).single { it.name == "C" }
        assertEquals(1, type.memberCount, "접근자 둘을 붙여도 프로퍼티 하나")
    }

    @Test
    fun `중첩 타입과 companion 은 자기 타입으로 따로 계수된다`() {
        val source =
            "package p\n\n" +
                "class Outer {\n" +
                "    val a: Int = 1\n\n" +
                "    class Nested {\n" +
                "        val x: Int = 1\n" +
                "        val y: Int = 2\n" +
                "    }\n\n" +
                "    companion object {\n" +
                "        val z: Int = 3\n" +
                "    }\n" +
                "}\n"
        val measured = measure(source)
        assertEquals(1, measured.single { it.name == "Outer" }.memberCount, "중첩·companion 의 멤버가 Outer 로 새면 안 된다")
        assertEquals(2, measured.single { it.name == "Nested" }.memberCount)
        assertEquals(1, measured.single { it.name == "Companion" }.memberCount)
    }

    @Test
    fun `enum entry 는 멤버가 아니다`() {
        val source =
            "package p\n\n" +
                "enum class E {\n" +
                "    A, B, C;\n\n" +
                "    fun label(): String = name\n" +
                "}\n"
        val type = measure(source).single { it.name == "E" }
        assertEquals(1, type.memberCount, "enum entry 셋은 멤버가 아니다 — 함수 하나만 세야 한다")
    }

    @Test
    fun `interface 도 같은 축으로 잰다`() {
        val source =
            "package p\n\n" +
                "interface I {\n" +
                "    val a: Int\n" +
                "    fun run(): Int\n" +
                "}\n"
        val type = measure(source).single { it.name == "I" }
        assertEquals(2, type.memberCount)
    }
}
