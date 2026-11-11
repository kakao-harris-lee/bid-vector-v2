package bidvector.buildlogic

import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportList
import org.jetbrains.kotlin.psi.KtPackageDirective
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
    ): List<SourceReference> = withKotlinPsi { factory -> extractFrom(factory.createFile(fileName, text), fileName) }

    /** 이미 파싱된 파일에서 뽑는다 — 파일마다 PSI 환경을 새로 만들지 않는 호출자를 위해 갈라 둔다. */
    fun extractFrom(
        ktFile: KtFile,
        fileName: String,
    ): List<SourceReference> = importReferences(ktFile, fileName) + qualifiedReferences(ktFile, fileName)

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
     * 6항). 부모가 같은 노드 종류라는 것만으로 건너뛰면
     * `java.net.HttpURLConnection.HTTP_OK.toString()` 처럼 바깥 노드의 selector 가
     * `KtCallExpression` 이라 후보를 못 만드는 경우, 안쪽의 완전수식 참조까지 조용히 묻힌다 —
     * 부모의 `segments()` 가 실제로 non-null 일 때만 자식을 「이미 후보에 포함됐다」로 본다.
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
                    if (!type.isClaimedByParent()) {
                        found += candidatesAt(type.segments(), type.textRange.startOffset, fileName, text)
                    }
                    super.visitUserType(type)
                }

                override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
                    if (!expression.isClaimedByParent()) {
                        found += candidatesAtUnlessLocalValue(expression, fileName, text)
                    }
                    super.visitDotQualifiedExpression(expression)
                }
            },
        )
        return found
    }

    /**
     * 파일 전체 이름 집합을 쓰면, 다른 함수의
     * 동명 지역 변수(`val java = 1`)가 이 함수의 완전수식 참조까지 지우는 미탐을 낸다 —
     * 판별을 [visibleLocalNames]로 좁혀 **이 표현식에서 실제로 보이는** 조상 사슬만 본다.
     */
    private fun candidatesAtUnlessLocalValue(
        expression: KtDotQualifiedExpression,
        fileName: String,
        text: String,
    ): List<SourceReference> {
        val segments = expression.segments()
        if (segments != null && segments.first() in expression.visibleLocalNames()) return emptyList()
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
}
