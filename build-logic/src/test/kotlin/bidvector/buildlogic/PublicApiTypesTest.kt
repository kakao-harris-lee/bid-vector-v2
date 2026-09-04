package bidvector.buildlogic

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * PSI 추출의 음성 — 설계 검토 부록 §2 D-1~D-14·D-17~D-19·D-22. 정책과 무관하게 표면 자체를
 * 잰다(순수 함수, 인라인 소스 문자열).
 *
 * **추출기는 금지 목록을 모른다** — `Double` 뿐 아니라 만난 타입 참조를 전부 낸다(`Long`·
 * `String`·supertype 이름 등). 그래서 단언은 `isEmpty()`/`setOf(...)` 로 전체를 재기보다
 * `.any { it.name == "..." }` 로 **특정 이름의 출현**을 잰다 — 무관한 참조가 섞여도 깨지지 않는다.
 *
 * **`name` 은 별칭을 해석한 값이지 FQN 이 아니다** — 수식 없는 `Double` 은 소스에 `kotlin.Double`
 * 이라는 이름이 없으므로 `name == "Double"`(단순 이름) 그대로 남는다. 그 단순 이름을
 * `kotlin.Double` 로 대조하는 것은 `ApiTypePolicy.forbids` 의 일이고 `ApiTypePolicyTest` 가 잰다.
 */
class PublicApiTypesTest {
    @Test
    fun `D-1 반환 타입과 nullable 프로퍼티 타입을 잡는다`() {
        val surface =
            PublicApiTypes.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    fun rate(): Double = 0.0\n    val ratio: Double? = null\n}\n",
            )
        assertEquals(2, surface.uses.count { it.name == "Double" })
    }

    @Test
    fun `D-2 제네릭 타입 인자를 재귀로 잡는다`() {
        val surface =
            PublicApiTypes.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    fun f(xs: List<Double>): Long = xs.size.toLong()\n" +
                    "    fun g(): Map<String, Double> = emptyMap()\n}\n",
            )
        assertTrue(surface.uses.any { it.name == "Double" })
    }

    @Test
    fun `D-3 D-4 배열형과 배열의 배열을 잡는다`() {
        val surface =
            PublicApiTypes.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    fun a(): DoubleArray = doubleArrayOf()\n" +
                    "    fun b(): List<DoubleArray> = emptyList()\n}\n",
            )
        assertEquals(2, surface.uses.count { it.name == "DoubleArray" })
    }

    @Test
    fun `D-5 typealias 우변은 가시성과 무관하게 잡는다`() {
        val surface =
            PublicApiTypes.extract("Probe.kt", "package p\n\nprivate typealias Amount = Double\n")
        assertTrue(surface.uses.any { it.name == "Double" && it.declaration.contains("typealias Amount") })
    }

    @Test
    fun `D-6 Number 를 잡는다`() {
        val surface = PublicApiTypes.extract("Probe.kt", "package p\n\nclass C {\n    fun rate(): Number = 0\n}\n")
        assertTrue(surface.uses.any { it.name == "Number" })
    }

    @Test
    fun `D-7 식 본문 함수와 초기화식 프로퍼티의 타입 미명시를 잡는다`() {
        val surface =
            PublicApiTypes.extract("Probe.kt", "package p\n\nclass C {\n    fun f() = 1.0\n    val rate = 0.5\n}\n")
        assertEquals(2, surface.untyped.size)
    }

    /**
     * **verifier r18 F-2.** 접근자 본문만 있는 프로퍼티(초기화식도 위임도 없다)는 기존 조건
     * (`hasInitializer() || hasDelegate()`)이 false 라 타입 미명시로 잡히지 않았다 — 넓힘 ④가
     * 막으려던 바로 그 우회로(`val rate = 0.5` 는 막히는데 `val rate get() = 0.5` 는 연다).
     */
    @Test
    fun `F-2 getter 본문만 있고 타입이 없는 프로퍼티는 타입 미명시다`() {
        val surface = PublicApiTypes.extract("Probe.kt", "package p\n\nclass C {\n    val rate get() = 0.5\n}\n")
        assertEquals(1, surface.untyped.size, "${surface.untyped}")
        assertTrue(
            surface.untyped
                .single()
                .declaration
                .contains("rate"),
        )
        assertTrue(surface.uses.none { it.name == "Double" }, "${surface.uses}")
    }

    @Test
    fun `F-2 var 의 getter 본문만 있어도 타입 미명시다`() {
        val surface =
            PublicApiTypes.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    var margin\n        get() = 0.5\n        set(value) {}\n}\n",
            )
        assertTrue(surface.untyped.any { it.declaration.contains("margin") }, "${surface.untyped}")
    }

    /** getter 가 반환 타입을 명시하면 그것을 프로퍼티의 타입 표면으로 본다 — 금지 타입 단언이 잡아야 한다. */
    @Test
    fun `F-2 getter 가 반환 타입을 명시하면 타입 미명시가 아니라 금지 타입 단언이 잡는다`() {
        val surface =
            PublicApiTypes.extract("Probe.kt", "package p\n\nclass C {\n    val rate\n        get(): Double = 0.5\n}\n")
        assertTrue(surface.untyped.isEmpty(), "${surface.untyped}")
        assertTrue(surface.uses.any { it.name == "Double" && it.declaration.contains("rate") }, "${surface.uses}")
    }

    @Test
    fun `F-2 private 프로퍼티의 getter 전용 타입 미명시는 대상이 아니다`() {
        val surface = PublicApiTypes.extract("Probe.kt", "package p\n\nclass C {\n    private val x get() = 0.5\n}\n")
        assertTrue(surface.untyped.isEmpty(), "${surface.untyped}")
    }

    @Test
    fun `블록 본문 함수와 타입 명시 프로퍼티는 타입 미명시가 아니다`() {
        val surface =
            PublicApiTypes.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    fun f(): Long {\n        return 1\n    }\n    val n: Long = 1\n}\n",
            )
        assertTrue(surface.untyped.isEmpty())
    }

    @Test
    fun `D-8 완전수식 java-lang-Double 을 FQN 으로 잡는다`() {
        val surface =
            PublicApiTypes.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    fun f(): java.lang.Double? = null\n}\n",
            )
        assertTrue(surface.uses.any { it.name == "java.lang.Double" })
    }

    @Test
    fun `D-9 별칭 import 는 원 FQN 으로 해석한다`() {
        val surface =
            PublicApiTypes.extract(
                "Probe.kt",
                "package p\n\nimport kotlin.Double as Scalar\n\nclass C {\n    fun f(): Scalar = 0.0\n}\n",
            )
        val use = surface.uses.single { it.written == "Scalar" }
        assertEquals("kotlin.Double", use.name)
    }

    @Test
    fun `D-10 확장 수신자 타입을 잡는다`() {
        val surface = PublicApiTypes.extract("Probe.kt", "package p\n\nfun Double.won(): Long = toLong()\n")
        assertTrue(surface.uses.any { it.name == "Double" })
    }

    @Test
    fun `D-11 함수 타입의 수신자 파라미터 반환을 재귀로 잡는다`() {
        val surface =
            PublicApiTypes.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    fun f(g: (Double) -> Long): Long = g(0.0)\n    val h: Double.() -> Unit = {}\n}\n",
            )
        assertEquals(2, surface.uses.count { it.name == "Double" })
    }

    @Test
    fun `D-12 주 생성자 파라미터는 private 라도 잡는다`() {
        val surface = PublicApiTypes.extract("Probe.kt", "package p\n\nclass C(private val d: Double)\n")
        assertTrue(surface.uses.any { it.name == "Double" })
    }

    @Test
    fun `D-17 override 는 modifier 없으면 public 이고 supertype 타입 인자도 잡는다`() {
        val surface =
            PublicApiTypes.extract(
                "Probe.kt",
                "package p\n\nclass C : Comparator<Double> {\n    override fun compare(a: Double, b: Double): Int = 0\n}\n",
            )
        assertTrue(surface.uses.any { it.name == "Double" })
    }

    /**
     * **오탐이며 방향이 닫히는 쪽이다.** Kotlin 은 override 가 modifier 없으면 base 가시성을
     * 상속하지만(여기서는 `internal`), 게이트는 해석 없이 그것을 모른다 — modifier 부재를
     * public 으로 본다(닫히는 쪽 근사). 벗어나려면 실제 가시성을 명시해야 한다.
     */
    @Test
    fun `override 는 modifier 없으면 실제 가시성이 좁아도 public 으로 본다`() {
        val surface =
            PublicApiTypes.extract(
                "Probe.kt",
                "package p\n\nopen class B {\n    internal open fun compare(a: Double): Int = 0\n}\n" +
                    "class C : B() {\n    override fun compare(a: Double): Int = 0\n}\n",
            )
        // B.compare 는 internal 이라 안 잡히고 C.compare 만 (오탐으로) 잡힌다.
        assertEquals(1, surface.uses.count { it.name == "Double" })
    }

    @Test
    fun `override 에 실제 가시성을 명시하면 오탐에서 벗어난다`() {
        val surface =
            PublicApiTypes.extract(
                "Probe.kt",
                "package p\n\nopen class B {\n    internal open fun compare(a: Double): Int = 0\n}\n" +
                    "class C : B() {\n    internal override fun compare(a: Double): Int = 0\n}\n",
            )
        assertTrue(surface.uses.none { it.name == "Double" }, "명시한 internal override 는 대상 밖이어야 한다: ${surface.uses}")
    }

    @Test
    fun `D-18 타입 파라미터 bound 를 잡는다`() {
        val surface = PublicApiTypes.extract("Probe.kt", "package p\n\nclass Box<T : Number>(val value: T)\n")
        assertTrue(surface.uses.any { it.name == "Number" })
    }

    /**
     * **Codex 14차 #2.** `where` 절(`typeConstraints`)은 `extendsBound` 와 다른 PSI 자리라
     * 놓쳤다 — `class Numeric<T> where T : Number` 가 `uses=[]` 였다.
     */
    @Test
    fun `Codex-2 class 의 where 절 bound 를 잡는다`() {
        val surface =
            PublicApiTypes.extract("Probe.kt", "package p\n\nclass Numeric<T>(val value: T) where T : Number\n")
        assertTrue(surface.uses.any { it.name == "Number" }, "${surface.uses}")
    }

    @Test
    fun `Codex-2 함수의 where 절 bound 는 중첩 타입 인자도 잡는다`() {
        val surface =
            PublicApiTypes.extract("Probe.kt", "package p\n\nfun <T> f(x: T): T where T : Comparable<Double> = x\n")
        assertTrue(surface.uses.any { it.name == "Double" }, "${surface.uses}")
    }

    @Test
    fun `Codex-2 where 절 bound 가 허용 타입이면 통과한다`() {
        val surface =
            PublicApiTypes.extract("Probe.kt", "package p\n\nclass Box<T>(val value: T) where T : CharSequence\n")
        assertTrue(surface.uses.none { it.name == "Number" || it.name == "Double" }, "${surface.uses}")
        assertTrue(surface.uses.any { it.name == "CharSequence" }, "${surface.uses}")
    }

    @Test
    fun `D-19 함수 본문의 지역 선언은 대상이 아니다`() {
        val surface =
            PublicApiTypes.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    fun f(): Long {\n        class Local(val d: Double)\n        return 1\n    }\n}\n",
            )
        assertTrue(surface.uses.none { it.name == "Double" }, "지역 선언의 타입이 새어 나왔다: ${surface.uses}")
    }

    @Test
    fun `D-22 백틱 식별자를 원 이름으로 읽는다`() {
        val surface = PublicApiTypes.extract("Probe.kt", "package p\n\nclass C {\n    fun f(): `Double` = 0.0\n}\n")
        assertTrue(surface.uses.any { it.name == "Double" })
    }

    @Test
    fun `private internal 선언 안의 Double 은 잡지 않는다`() {
        val surface =
            PublicApiTypes.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    private fun a(): Double = 0.0\n    internal fun b(): Double = 0.0\n}\n",
            )
        assertTrue(surface.uses.none { it.name == "Double" }, "${surface.uses}")
    }

    @Test
    fun `internal 클래스 안의 public 멤버는 잡지 않는다 — 사슬 최소값`() {
        val surface =
            PublicApiTypes.extract("Probe.kt", "package p\n\ninternal class C {\n    fun a(): Double = 0.0\n}\n")
        assertTrue(surface.uses.none { it.name == "Double" }, "${surface.uses}")
    }
}
