package bidvector.buildlogic

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * pitest 의 `mutations.xml` 해석.
 *
 * **탐지의 분자는 pitest 자신이 쓴 `detected` 속성**이다 — `KILLED` 밖에도 탐지로 세는 상태
 * (`TIMED_OUT`)가 있고, 그 분류를 우리가 다시 열거하면 도구가 분류를 늘리는 날 두 자리가
 * 어긋난다. 속성이 사라지면 읽기를 **거부**한다: 없는 것을 「탐지 0」으로 읽으면 도구 출력
 * 변경이 조용한 전수 실패가 되고, 「탐지 전부」로 읽으면 조용한 전수 통과가 된다.
 */
internal class MutationReport(
    private val outcomes: List<Outcome>,
) {
    internal data class Outcome(
        val mutatedClass: String,
        val detected: Boolean,
    )

    val total: Int get() = outcomes.size

    val detected: Int get() = outcomes.count { it.detected }

    val mutatedClasses: Set<String> get() = outcomes.map { it.mutatedClass }.toSet()

    /** 사람이 읽는 백분율. **판정은 이 값으로 하지 않는다** — 내림이 1점 미만의 하락을 가린다. */
    val scorePercent: Int get() = if (total == 0) 0 else detected * PERCENT_SCALE / total

    companion object {
        fun parse(file: File): MutationReport {
            val document =
                DocumentBuilderFactory
                    .newInstance()
                    .apply {
                        // 리포트는 도구가 만든 로컬 파일이지만 외부 엔티티 해석을 열어 둘 이유가 없다.
                        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                        isXIncludeAware = false
                        isExpandEntityReferences = false
                    }.newDocumentBuilder()
                    .parse(file)
            val nodes = document.getElementsByTagName("mutation")
            val outcomes =
                (0 until nodes.length).map { index ->
                    val mutation = nodes.item(index) as Element
                    Outcome(
                        mutatedClass = mutation.requireChildText("mutatedClass"),
                        detected = mutation.requireDetected(),
                    )
                }
            return MutationReport(outcomes)
        }

        private fun Element.requireDetected(): Boolean {
            val raw = getAttribute("detected")
            return raw.toBooleanStrictOrNull()
                ?: error("mutations.xml 의 mutation 에 detected 속성이 없거나 참·거짓이 아니다: '$raw'")
        }

        private fun Element.requireChildText(tag: String): String {
            val children = getElementsByTagName(tag)
            if (children.length == 0) {
                error("mutations.xml 의 mutation 에 $tag 가 없다")
            }
            return children.item(0).textContent.trim()
        }
    }
}
