package bidvector.adapters.architecture

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

/**
 * **추출 층의 양성 대조**(cr M-2). 축의 dependency test 는 [isOutsideRoots] 를 손으로 쓴 문자열로
 * 시험하므로 **[SELF_COORDINATE] 를 지나간다** — 정규식이 조용히 좁아져도 그 축들은 「위반 0」으로
 * 초록이다. 실측이 그것을 보였다: 좌측 경계를 과하게 조인 변이가 축 **전부에서 위반 0 그대로**이고
 * 아무 단언도 붉히지 않았다. 그래서 「무엇을 **안** 잡나」가 아니라 **「무엇을 잡나」를 여기서 고정한다.**
 *
 * 축별 「하나도 못 뽑았는가」는 [disallowedCoordinates] 안의 비공허 단언이 든다. 이 class 는 그 술어의
 * **모양**을 잡는다 — 둘은 다른 자리다(하나는 모집단, 하나는 술어). **이제 축 열넷 전부가 이 정규식
 * 하나를 쓰므로 이 대조가 그 열넷을 함께 덮는다**(앞 판은 네 축만이었다).
 *
 * **표본이 손으로 적은 뿌리 몇이면 모자란다**(M11 verifier r1 **L-A**): 허용 밖 뿌리만 골라 빼는
 * lookahead 축소가 그런 표본을 통과한다. 그래서 아래 두 대조는 표본을 **승인된 모듈 뿌리 전부**에서
 * 구성으로 뽑는다([ArchitecturePolicyValues.moduleRoots]).
 */
class BytecodeConstantPoolTest {
    private fun extract(text: String): List<String> = SELF_COORDINATE.findAll(text).map { it.value }.toList()

    /**
     * **잡아야 하는 것 — JVM 서술자가 주 모집단이다.** 상수 풀의 좌표는 대부분 `Lbidvector/…;` 꼴로
     * 나타나고 그 앞 글자는 식별자 글자 `L` 이다. 「앞이 식별자가 아닐 것」으로 경계를 잡으면 이 행들이
     * 통째로 빠진다 — 그 판이 오탐 0 이면서 **양성을 함께 잃는** 자리이고, 이 test 가 그 변이를 붉힌다.
     */
    @Test
    fun `서술자·배열·제네릭 꼴의 자기 좌표를 뽑는다 — 양성 대조`() {
        extract("(Lbidvector/procurement/Notice;)V") shouldBe listOf("bidvector/procurement/Notice")
        extract("Ljava/util/List<Lbidvector/strategy/MatchScore;>;") shouldBe listOf("bidvector/strategy/MatchScore")
        extract("[Lbidvector/sharedkernel/Money;") shouldBe listOf("bidvector/sharedkernel/Money")
        extract("-Lbidvector/workflow/event/EventEnvelope;") shouldBe listOf("bidvector/workflow/event/EventEnvelope")
        extract("  #19 = Class  #18  // bidvector/adapters/ml/RequestMappingKt") shouldBe
            listOf("bidvector/adapters/ml/RequestMappingKt")
        extract("bidvector.procurement.Notice") shouldBe listOf("bidvector.procurement.Notice")
    }

    /**
     * **잡으면 안 되는 것 — 중첩 네임스페이스의 가운데 토막.** `ml` 축이 쓰는 생성 proto 의 JVM 패키지가
     * `contract.bidvector.ml.v1` 이다. 경계가 없으면 그 안에서 `bidvector/ml/v1/…` 를 자기 좌표로 읽어
     * **허용 밖 좌표 308**(distinct)을 낸다(실측). 가리는 기준은 「`bidvector` 가 수식 이름의 **첫
     * 세그먼트가 아닌** 자리」이고, 그래서 배제하는 선행 글자는 `/`·`.` **둘뿐**이다.
     */
    @Test
    fun `중첩 네임스페이스의 가운데 토막은 뽑지 않는다 — 음성 대조`() {
        extract("Lcontract/bidvector/ml/v1/Embedding;") shouldBe emptyList()
        extract("  #20 = Class #19  // contract/bidvector/ml/v1/ModelRelease") shouldBe emptyList()
        extract("contract.bidvector.ml.v1.Embedding") shouldBe emptyList()
        extract("Classfile /home/x/build/classes/kotlin/main/bidvector/adapters/ml/Foo.class") shouldBe emptyList()
    }

    /**
     * 경계가 **첫 세그먼트 판정**이지 「식별자 아님」이 아님을 한 자리에서 고정한다 — 두 요구가 갈리는
     * 입력 쌍이다. 왼쪽은 뽑고(앞 글자 `L` 은 서술자 표지일 뿐이다) 오른쪽은 안 뽑는다(앞 글자가 `/`).
     */
    @Test
    fun `선행 글자가 식별자여도 첫 세그먼트면 뽑는다 — 두 경계안이 갈리는 자리`() {
        extract("Lbidvector/procurement/Notice;").size shouldBe 1
        extract("Lcontract/bidvector/ml/v1/Embedding;").size shouldBe 0
    }

    /**
     * **승인된 모듈 뿌리 전부를 뽑는다 — 구성 표본**(`OPEN-M11-JAVAP-ROOT-REGEX-UNANCHORED` ⑤).
     *
     * 위 세 대조의 표본은 손으로 적은 뿌리 다섯이었고, 그래서 **허용 밖 뿌리만 골라 빼는** 축소
     * (`bidvector[/.](?!decision|bidding|…)`)가 통과했다 — 빠진 다섯(`qualification`·`bidding`·
     * `decision`·`settlement`·`app`)을 아무도 묻지 않았기 때문이다(실측: 그 변이가 exit 0). 표본을
     * 모듈 목록에서 구성으로 뽑으면 **어느 뿌리를 빼도** 그 뿌리의 행이 붉는다.
     */
    @Test
    fun `승인된 모듈 뿌리 전부를 서술자와 점 표기 양쪽에서 뽑는다 — 구성 표본`() {
        val roots = ArchitecturePolicyValues.moduleRoots
        roots.shouldNotBeEmpty()

        val missed =
            roots.filterNot { root ->
                val internal = root.replace('.', '/')
                extract("(L$internal/Sample;)V") == listOf("$internal/Sample") &&
                    extract("$root.Sample") == listOf("$root.Sample")
            }.sorted()

        missed shouldBe emptyList()
    }

    /**
     * **모듈 뿌리마다 중첩 이름공간의 가운데 토막은 뽑지 않는다 — 구성 음성 대조.** 위 음성 대조는
     * `contract.bidvector.ml.v1` 하나만 들었다. 경계를 무엇으로 잡든 **모든 뿌리에서** 같은 답이
     * 나와야 하고, 그 전수가 「경계가 특정 뿌리에만 걸려 있다」를 배제한다.
     */
    @Test
    fun `모듈 뿌리마다 수식 이름 안의 토막은 뽑지 않는다 — 구성 음성 대조`() {
        val caught =
            ArchitecturePolicyValues.moduleRoots
                .filter { root ->
                    val internal = root.replace('.', '/')
                    extract("Lcontract/$internal/v1/Sample;").isNotEmpty() ||
                        extract("contract.$root.v1.Sample").isNotEmpty()
                }.sorted()

        caught shouldBe emptyList()
    }

    /**
     * **토막은 `Classfile` 머리글 줄을 담지 않는다** — 그 줄은 상수 풀이 아니라 **파일 경로**이고,
     * 경로에는 `…/bidvector/adapters/…` 가 늘 들어 있어 담아 두면 **추출 비공허가 정규식과 무관하게
     * 늘 만족된다**(앞 판의 형제 열 축이 그 상태였다). 그래서 두 가지를 함께 잰다: 머리글이 빠졌고,
     * **그래도 실제 좌표가 남는다**. 뒤의 것이 없으면 「머리글만 뺐더니 아무것도 안 남았다」와 구별되지
     * 않는다.
     */
    @Test
    fun `토막은 머리글 경로를 담지 않고 그래도 실제 좌표가 남는다`() {
        val classesDir = ScopedAllowedRoots.classesDirOf(ScopedAllowedRoots.OWN_MODULE_ROOT)
        val classFile = BytecodeConstantPool.classFilesUnder(classesDir).first()

        val body = BytecodeConstantPool.bodyOf(classFile)

        body shouldNotContain "Classfile "
        body shouldNotContain classFile.absolutePath
        extract(body).shouldNotBeEmpty()
    }
}
