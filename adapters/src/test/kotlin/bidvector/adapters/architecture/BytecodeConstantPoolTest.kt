package bidvector.adapters.architecture

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * **추출 층의 양성 대조**(cr M-2). 여섯 축의 dependency test 는 [isOutsideRoots] 를 손으로 쓴 문자열로
 * 시험하므로 **[SELF_COORDINATE] 를 지나간다** — 정규식이 조용히 좁아져도 그 축들은 「위반 0」으로
 * 초록이다. 실측이 그것을 보였다: 좌측 경계를 과하게 조인 변이가 여섯 축 **전부에서 위반 0 그대로**이고
 * 아무 단언도 붉히지 않았다. 그래서 「무엇을 **안** 잡나」가 아니라 **「무엇을 잡나」를 여기서 고정한다.**
 *
 * 축별 「하나도 못 뽑았는가」는 [disallowedCoordinates] 안의 비공허 단언이 든다. 이 class 는 그 술어의
 * **모양**을 잡는다 — 둘은 다른 자리다(하나는 모집단, 하나는 술어).
 *
 * 형제 열 축의 파일 `private` 정규식에는 이 대조가 **없다** — 같은 구멍이고 이 PR 의 회귀가 아니다.
 * 받는 자리는 `OPEN-M11-JAVAP-ROOT-REGEX-UNANCHORED` 이고 그 문면이 「경계·호출 꼴·추출 층 대조 부재」
 * 셋을 함께 든다.
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
}
