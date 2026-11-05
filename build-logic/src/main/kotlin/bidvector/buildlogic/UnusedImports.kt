package bidvector.buildlogic

import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportList
import org.jetbrains.kotlin.psi.KtPackageDirective

/** 쓰이지 않는 import 하나 — [name] 은 그 파일이 **들여온 이름**(별칭이 있으면 별칭)이다. */
internal data class UnusedImport(
    val fileName: String,
    val line: Int,
    val fqn: String,
    val name: String,
)

/**
 * **들여온 이름이 본문에서 쓰이는가** — 등식은 `도입 이름 ∖ (본문 식별자 ∪ 문법이 쓰는 이름) == ∅`
 * (`OPEN-KTLINT-UNUSED-IMPORTS-SILENT`).
 *
 * 왜 자체 게이트인가: ktlint 1.8.0 `ktlint_official` 의 `no-unused-imports` 가 **켜져 있는데도** 쓰지
 * 않는 import 두 줄을 보고하지 않았다(그 태스크 단독 재실행으로 exit 0 실측, 운영자 등재 결정
 * 2026-09-26). 도구가 조용한 축은 도구로 덮지 않는다.
 *
 * **본문 참조는 PSI 식별자 토큰으로 센다** — 텍스트 grep 이 아니다. 식별자 leaf 를 세므로 수식 참조·
 * 애노테이션·타입 인자·문자열 템플릿 안 식·KDoc 링크(`[Foo]`)가 모두 같은 한 가지 방법으로 들어온다.
 * 선언 이름(`val foo`·`fun bar`)도 함께 들어오는데, 그것은 **느슨한 쪽**으로만 작용한다(같은 짧은
 * 이름을 그 파일이 스스로 선언하면 import 가 쓰인 것으로 보인다 — 알려진 한계, 미탐 방향).
 *
 * wildcard import(`import a.b.*`)는 대상이 아니다 — 들여온 이름이 열거되지 않아 쓰임을 셀 수 없다
 * (알려진 한계; 그 경로는 `domainSourceReferenceGate` 의 wildcard 규칙이 따로 좁힌다).
 */
internal object UnusedImports {
    fun detect(
        fileName: String,
        text: String,
        syntaxNames: Set<String>,
    ): List<UnusedImport> =
        withKotlinPsi { factory ->
            detectIn(factory.createFile(fileName, text), fileName, syntaxNames)
        }

    fun detectIn(
        ktFile: KtFile,
        fileName: String,
        syntaxNames: Set<String>,
    ): List<UnusedImport> {
        val referenced = identifiersOutsideImports(ktFile)
        val text = ktFile.text
        return ktFile.importDirectives.mapNotNull { directive ->
            val fqn = directive.importedFqName?.asString()
            val name = directive.aliasName ?: directive.importedFqName?.shortName()?.asString()
            when {
                directive.isAllUnder || fqn == null || name == null -> null
                name in referenced || name in syntaxNames -> null
                else -> UnusedImport(fileName, text.lineOf(directive.textRange.startOffset), fqn, name)
            }
        }
    }

    /**
     * import 목록과 package 선언 **밖**의 식별자 토큰 전수. 그 두 노드 안도 같은 토큰을 쓰므로 방문을
     * 아예 막는다 — 막지 않으면 모든 import 가 자기 자신을 「쓰임」으로 세어 게이트가 공허해진다.
     *
     * 자식 순회는 `firstChild`/`nextSibling` 으로 한다 — 주석·KDoc·공백까지 빠짐없이 지난다.
     */
    private fun identifiersOutsideImports(ktFile: KtFile): Set<String> {
        val names = mutableSetOf<String>()
        collectInto(ktFile, names)
        return names
    }

    private fun collectInto(
        element: PsiElement,
        names: MutableSet<String>,
    ) {
        if (element is KtImportList || element is KtPackageDirective) return
        if (element is LeafPsiElement && element.elementType == KtTokens.IDENTIFIER) {
            names += element.text.removeSurrounding("`")
        }
        var child = element.firstChild
        while (child != null) {
            collectInto(child, names)
            child = child.nextSibling
        }
    }
}
