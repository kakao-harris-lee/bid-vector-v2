package bidvector.adapters.architecture

import bidvector.adapters.architecture.ScopedAllowedRoots.BYTECODE_LAYER_ROOTS
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * **바이트코드 층의 판정이 여기 하나뿐이다**(`OPEN-M11-JAVAP-ROOT-REGEX-UNANCHORED` ④).
 *
 * 앞 판은 축 test 열넷이 각자 `disallowedCoordinates(…, ALLOWED_ROOTS)` 를 적어 판정했고, 두 층 등식
 * 표는 그 옆에서 **장식일 수 있었다**. M11 verifier r1 이 그 틈으로 두 꼴을 통과시켰다 — 판정을 같은
 * facade 의 **다른 이름**(`JUDGED_ROOTS`)으로 옮기거나 **호출 자리의 식**으로 옮기는 것. 보유자
 * 단일성은 `ALLOWED_ROOTS` 라는 **이름**으로 세므로 둘 다 보유자가 하나로 보였다.
 *
 * 이 판에서는 **축 test 에 판정 호출이 하나도 없다.** 판정은 [BYTECODE_LAYER_ROOTS] 를 순회하며
 * 그 (key, value) 로 하므로 표가 **구성상 판정 입력**이다 — 다른 이름의 집합을 선언해도 그것으로
 * 판정하는 자리가 없고, 호출 자리의 식을 쓰려면 **이 파일**을 고쳐야 한다(열넷이 아니라 하나다).
 *
 * 표가 가리키는 집합이 그 축이 선언한 그 집합인지는 [ScopedAllowedRootsParityTest] 가 든다 —
 * 리플렉션 동일성 · 보유자 단일성 · 두 층 집합 등식. 둘은 다른 자리다(하나는 판정, 하나는 표의 무결).
 */
class ScopedBytecodeLayerGateTest {
    /**
     * 축의 컴파일된 class 가 그 축의 허용 뿌리 **밖**의 자기 저장소 좌표를 참조하지 않는다.
     *
     * 축마다 `shouldBe` 를 루프로 돌리지 않는다 — kotest hard assertion 은 첫 불일치에서 멈춰 나머지
     * 축의 위반을 가린다(`ScopedAllowedRootsParityTest` 의 표 단위 map 등식과 같은 꼴).
     */
    @Test
    fun `축마다 허용 뿌리 밖의 자기 저장소 좌표를 참조하지 않는다`() {
        val violations =
            BYTECODE_LAYER_ROOTS
                .mapValues { (scope, roots) ->
                    disallowedCoordinates(ScopedAllowedRoots.classesDirOf(scope), roots)
                }.filterValues(List<String>::isNotEmpty)

        violations shouldBe emptyMap()
    }

    /**
     * **술어의 모양 — 규칙을 다시 적지 않는 대조.** 허용 뿌리마다 세 표본을 **구성**으로 만든다:
     * 뿌리 자신(걸리면 안 된다) · 뿌리의 하위(`뿌리.Sub`, 걸리면 안 된다) · **이름 접두만 공유하는
     * 이웃**(`뿌리x`, 걸려야 한다). 마지막 하나가 [isOutsideRoots] 의 세그먼트 경계를 잡는다 —
     * 맨 `startsWith` 로 되돌리면 그 표본이 통과해 버린다.
     *
     * 앞 판은 이 주장을 축마다 손으로 적은 이름(`bidvector.procurementlegacy` 등)으로 열거했다.
     * 구성으로 바꾸면 축이 늘거나 허용 뿌리가 바뀔 때 표본이 **함께** 움직인다.
     */
    @Test
    fun `허용 뿌리와 그 하위는 통과하고 이름 접두만 공유하는 이웃은 걸린다 — 구성 대조`() {
        val wrong =
            BYTECODE_LAYER_ROOTS
                .mapValues { (_, roots) ->
                    val mustPass = (roots + roots.map { "$it.Sub" }).filter { isOutsideRoots(it, roots) }
                    val mustFail = roots.map { "${it}x" }.filterNot { isOutsideRoots(it, roots) }
                    (mustPass + mustFail).sorted()
                }.filterValues(List<String>::isNotEmpty)

        wrong shouldBe emptyMap()
    }

    /**
     * **모집단 대조 — 표본이 「그때 손으로 적은 뿌리 몇」이 아니다**
     * (`OPEN-M11-JAVAP-ROOT-REGEX-UNANCHORED` ⑤ 의 같은 처방을 축 판정 쪽에 세운다).
     *
     * 표본은 **승인된 모듈의 패키지 뿌리 전부**(`layer.*` 도출)와 **adapters 의 1급 패키지 전부**
     * (디렉터리 전수)다. 축의 허용 뿌리가 덮지 않는 것은 전부 걸려야 한다 — 축마다 적어도 하나는
     * 그런 표본이 있어야 하고(비공허), 그렇지 않으면 그 축에서 이 대조가 아무것도 묻지 않는다.
     *
     * **이것은 차분 대조다** — 기대값을 「덮이지 않음」이라는 **같은 규칙의 둘째 구현**으로 계산한다.
     * 그래서 규칙 자체가 틀리는 것은 못 잡고, 그 자리는 위 이웃 표본이 든다. 이 대조가 잡는 것은
     * **모집단의 구멍**이다(어떤 모듈·형제 패키지가 어느 축의 눈에서 빠지는가).
     */
    @Test
    fun `모듈 뿌리와 형제 adapter 패키지 가운데 허용 밖인 것은 모두 걸린다 — 구성 표본`() {
        val population = ArchitecturePolicyValues.moduleRoots + ScopedAllowedRoots.adapterPackages()

        // 표본이 조용히 비면 아래 등식이 공허해진다. 앵커는 자기 모듈 뿌리이고 리터럴이 아니다 —
        // 리터럴로 적으면 그 앵커가 또 하나의 열거가 된다(D-12A-5).
        population.shouldNotBeEmpty()
        population shouldContain ScopedAllowedRoots.OWN_MODULE_ROOT

        val outsideByScope =
            BYTECODE_LAYER_ROOTS.mapValues { (_, roots) ->
                population.filterNot { candidate -> roots.any { candidate == it || candidate.startsWith("$it.") } }
            }
        val vacuous = outsideByScope.filterValues(List<String>::isEmpty).keys.sorted()
        vacuous shouldBe emptyList()

        val missed =
            BYTECODE_LAYER_ROOTS
                .mapValues { (scope, roots) ->
                    outsideByScope.getValue(scope).filterNot { isOutsideRoots(it, roots) }.sorted()
                }.filterValues(List<String>::isNotEmpty)

        missed shouldBe emptyMap()
    }

    /**
     * **축의 허용 뿌리를 제자리에서 넓힐 수 없다**(M11 verifier r1 M-1 의 처방).
     *
     * 리플렉션 동일성(`===`)은 표의 값이 **그 소유 getter 가 돌려주는 인스턴스**임을 잠그지만 **그
     * 인스턴스의 내용**은 잠그지 않는다. `(X as MutableSet).apply { add(…) }` 는 같은 인스턴스라
     * 동일성이 성립한다 — 그러면 축의 `ALLOWED_ROOTS` **선언은 뿌리 셋을 보여 주고 게이트는 넷으로
     * 판정**한다. 이 slice 가 그 꼴을 실측했다: 표만 바꾼 판은 두 층 집합 등식이 잡지만(exit 1),
     * **이름 층 정책을 한 줄 함께 넓히고 축의 선언은 그대로 둔 판**은 통과했다(exit 0 · 13/0).
     * 「양쪽을 함께 넓힌다」(위협 모델 경계 밖)와 갈리는 지점은 **축의 선언이 거짓이 되는가**다.
     *
     * 그래서 단언은 클래스 **이름**이 아니라 **거동**이다 — cast 뒤 `add` 가 거부해야 한다. 이름
     * 열거는 JDK 구현 클래스에 매이고, 막으려는 것은 제자리 변경 **가능성**이기 때문이다. 축의 선언이
     * `java.util.Set.copyOf` 를 쓰면 거부가 `UnsupportedOperationException` 으로 온다.
     */
    @Test
    fun `축의 허용 뿌리 집합은 제자리 변경을 거부한다 — cast 뒤 add`() {
        val mutable =
            BYTECODE_LAYER_ROOTS.keys
                .filter { scope ->
                    @Suppress("UNCHECKED_CAST")
                    val asMutable = BYTECODE_LAYER_ROOTS.getValue(scope) as MutableSet<String>
                    try {
                        asMutable.add(MUTATION_PROBE)
                        // 받아들여졌다 = 가변. 다음 test 에 오염을 남기지 않고 돌려놓는다.
                        asMutable.remove(MUTATION_PROBE)
                        true
                    } catch (_: UnsupportedOperationException) {
                        false
                    }
                }.sorted()

        mutable shouldBe emptyList()
    }

    private companion object {
        /**
         * 제자리 변경이 받아들여지는지 보려고 넣는 값. 샜을 때 **조용하지 않은** 이름을 쓴다 —
         * 두 층 집합 등식이 이름 층 정책에 없는 뿌리를 바로 붉힌다.
         */
        const val MUTATION_PROBE = "bidvector.inplace-mutation-probe"
    }
}
