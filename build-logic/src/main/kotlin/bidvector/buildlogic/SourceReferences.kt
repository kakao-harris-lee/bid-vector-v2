package bidvector.buildlogic

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportList
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
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
     *
     * **자식을 건너뛰는 것은 부모가 실제로 후보를 만들었을 때만이다**(설계 검토 §4 S-2 단언
     * 6항, verifier r18 F-1). 부모가 같은 노드 종류라는 것만으로 건너뛰면
     * `java.net.HttpURLConnection.HTTP_OK.toString()` 처럼 바깥 노드의 selector 가
     * `KtCallExpression` 이라 후보를 못 만드는 경우, 안쪽의 완전수식 참조까지 조용히 묻힌다 —
     * 부모의 `segments()` 가 실제로 non-null 일 때만 자식을 「이미 후보에 포함됐다」로 본다.
     */
    private fun qualifiedReferences(
        ktFile: KtFile,
        fileName: String,
    ): List<SourceReference> {
        val text = ktFile.text
        val localNames = ktFile.locallyDeclaredNames()
        val found = mutableListOf<SourceReference>()
        ktFile.accept(
            object : KtTreeVisitorVoid() {
                override fun visitImportList(importList: KtImportList) = Unit

                override fun visitPackageDirective(directive: KtPackageDirective) = Unit

                override fun visitUserType(type: KtUserType) {
                    if (!type.isClaimedByParent()) {
                        found += candidatesAt(type.segments(), type.textRange.startOffset, fileName, text)
                    }
                    super.visitUserType(type)
                }

                override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
                    if (!expression.isClaimedByParent()) {
                        found += candidatesAtUnlessLocalValue(expression, localNames, fileName, text)
                    }
                    super.visitDotQualifiedExpression(expression)
                }
            },
        )
        return found
    }

    /**
     * **verifier r19 M-2.** F-1 이 `KtCallExpression` 의 callee 도 세그먼트로 받으면서
     * `tree.Node()`(`tree: Tree` 파라미터의 `inner class` 인스턴스화)가 `tree.Node` 라는 가짜
     * 후보를 냈다 — 소문자 뿌리가 **같은 파일에 선언된 이름**이면 패키지 접두가 아니라 값
     * 체인이므로 건너뛴다.
     */
    private fun candidatesAtUnlessLocalValue(
        expression: KtDotQualifiedExpression,
        localNames: Set<String>,
        fileName: String,
        text: String,
    ): List<SourceReference> {
        val segments = expression.segments()
        if (segments != null && segments.first() in localNames) return emptyList()
        return candidatesAt(segments, expression.textRange.startOffset, fileName, text)
    }

    private fun KtUserType.isClaimedByParent(): Boolean = (parent as? KtUserType)?.segments() != null

    private fun KtDotQualifiedExpression.isClaimedByParent(): Boolean =
        (parent as? KtDotQualifiedExpression)?.segments() != null

    private fun candidatesAt(
        segments: List<String>?,
        offset: Int,
        fileName: String,
        text: String,
    ): List<SourceReference> =
        candidateForms(segments.orEmpty()).map { form ->
            SourceReference(fileName, text.lineOf(offset), form)
        }

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

    /**
     * 뿌리 방향으로 재귀하며 세그먼트를 잎→뿌리 순서로 쌓는다. **호출(`KtCallExpression`)도
     * 세그먼트를 낸다** — 그 callee 가 단순 이름이면(verifier r18 F-1: `java.io.File("x")` 같은
     * 완전수식 생성자·정적 호출) 그 이름을 이어 붙인다. `candidateForms` 의 대문자 이어달리기가
     * `.toString()`·`.encode(…)` 처럼 소문자로 시작하는 일반 메서드 호출은 스스로 잘라내므로
     * (첫 소문자 세그먼트에서 멈춘다), 이 확장이 거짓 양성을 늘리지 않는다. 그 외 형태(사슬이
     * 아닌 수신자 등)는 여전히 `null`.
     */
    private fun KtExpression.collectSegments(accumulated: List<String>): List<String>? =
        when (this) {
            is KtDotQualifiedExpression -> {
                selectorName()?.let { name -> receiverExpression.collectSegments(accumulated + name) }
            }

            is KtSimpleNameExpression -> {
                accumulated + getReferencedName()
            }

            else -> {
                null
            }
        }

    private fun KtDotQualifiedExpression.selectorName(): String? =
        when (val selector = selectorExpression) {
            is KtSimpleNameExpression -> selector.getReferencedName()
            is KtCallExpression -> (selector.calleeExpression as? KtSimpleNameExpression)?.getReferencedName()
            else -> null
        }
}
