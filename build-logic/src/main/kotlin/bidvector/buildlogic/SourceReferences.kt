package bidvector.buildlogic

import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportList
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtUserType

/** 소스가 이름으로 부른 좌표 하나. [wildcard] 는 `import x.y.*` 의 패키지 FQN 을 표시한다. */
internal data class SourceReference(
    val fileName: String,
    val line: Int,
    val fqn: String,
    val wildcard: Boolean = false,
)

/**
 * domain 소스가 **이름으로 부르는** 좌표를 PSI 에서 뽑는다 — 설계 검토 §4. import
 * 지시자(S-1 단언)와 완전수식 참조(S-2 단언) 둘 다 본다. **순수 함수다** — 정책을 모르고
 * 판정하지 않는다. 판정은 [SourceReferencePolicy] 가 갖는다.
 */
internal object SourceReferences {
    fun extract(
        fileName: String,
        text: String,
    ): List<SourceReference> =
        withKotlinPsi { factory ->
            val ktFile = factory.createFile(fileName, text)
            importReferences(ktFile, fileName) + qualifiedReferences(ktFile, fileName)
        }

    /** S-1 단언 — 모든 `KtImportDirective.importedFqName` 이 대상이다. 별칭은 원 FQN 을 낸다. */
    private fun importReferences(
        ktFile: KtFile,
        fileName: String,
    ): List<SourceReference> {
        val text = ktFile.text
        return ktFile.importDirectives.mapNotNull { import ->
            val fqName = import.importedFqName?.asString() ?: return@mapNotNull null
            SourceReference(fileName, text.lineOf(import.textRange.startOffset), fqName, import.isAllUnder)
        }
    }

    /**
     * S-2 단언 — 수식된 `KtUserType` 과 `KtDotQualifiedExpression`. package 선언·import
     * 지시자는 뺀다(그 안도 같은 노드 모양을 쓴다 — 겹쳐 세지 않도록 방문을 아예 막는다).
     * 바깥쪽에서 후보를 만든 노드의 자식으로는 내려가지 않는다 — 부모가 같은 종류가 아닐 때만
     * 처리해 중복 보고를 막는다.
     */
    private fun qualifiedReferences(
        ktFile: KtFile,
        fileName: String,
    ): List<SourceReference> {
        val text = ktFile.text
        val found = mutableListOf<SourceReference>()
        ktFile.accept(
            object : KtTreeVisitorVoid() {
                override fun visitImportList(importList: KtImportList) = Unit

                override fun visitPackageDirective(directive: KtPackageDirective) = Unit

                override fun visitUserType(type: KtUserType) {
                    if (type.parent !is KtUserType) {
                        found += candidatesAt(type.segments(), type.textRange.startOffset, fileName, text)
                    }
                    super.visitUserType(type)
                }

                override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
                    if (expression.parent !is KtDotQualifiedExpression) {
                        found += candidatesAt(expression.segments(), expression.textRange.startOffset, fileName, text)
                    }
                    super.visitDotQualifiedExpression(expression)
                }
            },
        )
        return found
    }

    private fun candidatesAt(
        segments: List<String>?,
        offset: Int,
        fileName: String,
        text: String,
    ): List<SourceReference> =
        candidateForms(segments.orEmpty()).map { form ->
            SourceReference(fileName, text.lineOf(offset), form)
        }

    /**
     * 소문자 세그먼트를 접두로 모으고 첫 대문자 세그먼트까지가 후보다(설계 검토 §4 단계 2·3).
     * 대문자 세그먼트가 이어지면 `$` 로 이어 붙인 형태도 함께 낸다 — **전부** 허용이어야
     * 한다(`java.util.Map`·`java.util.Map$Entry` 를 둘 다 재는 이유, 단계 4).
     *
     * 첫 세그먼트가 대문자면 단순 이름 참조라 건너뛴다 — import 나 같은 파일 선언이 이미 푼다.
     * 대문자 세그먼트가 아예 없으면 값 체인이라 건너뛴다.
     */
    private fun candidateForms(segments: List<String>): List<String> {
        val typeIndex = segments.indexOfFirst { it.startsWithUpper() }
        val isSimpleNameOrValueChain = segments.isEmpty() || segments.first().startsWithUpper() || typeIndex < 0
        if (isSimpleNameOrValueChain) return emptyList()

        val prefix = segments.subList(0, typeIndex).joinToString(".")
        val typeSegments = segments.subList(typeIndex, segments.size).takeWhile { it.startsWithUpper() }
        return typeSegments.indices.map { i -> "$prefix." + typeSegments.subList(0, i + 1).joinToString("$") }
    }

    private fun String.startsWithUpper(): Boolean = isNotEmpty() && first().isUpperCase()

    /** 세그먼트 이름을 뿌리→잎 순서로. 백틱은 `getReferencedName()` 이 이미 벗긴다(S-16). */
    private fun KtUserType.segments(): List<String>? {
        val names = mutableListOf<String>()
        var current: KtUserType? = this
        while (current != null) {
            names += current.referenceExpression?.getReferencedName() ?: return null
            current = current.qualifier
        }
        return names.asReversed()
    }

    private fun KtDotQualifiedExpression.segments(): List<String>? = collectSegments(emptyList())?.asReversed()

    /** 뿌리 방향으로 재귀하며 세그먼트를 잎→뿌리 순서로 쌓는다. 사슬이 끊기면(호출 등) `null`. */
    private fun KtExpression.collectSegments(accumulated: List<String>): List<String>? =
        when (this) {
            is KtDotQualifiedExpression -> {
                (selectorExpression as? KtSimpleNameExpression)?.let { selector ->
                    receiverExpression.collectSegments(accumulated + selector.getReferencedName())
                }
            }

            is KtSimpleNameExpression -> {
                accumulated + getReferencedName()
            }

            else -> {
                null
            }
        }
}
