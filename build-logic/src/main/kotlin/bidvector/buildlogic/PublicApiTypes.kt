package bidvector.buildlogic

import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import java.io.File

/** 타입 표면에서 만난 좌표 하나. [name] 은 별칭을 해석한 뒤(D-9), [written] 은 소스 표기 그대로. */
internal data class ApiTypeUse(
    val name: String,
    val written: String,
    val declaration: String,
    val line: Int,
)

/** public 선언인데 타입이 없어(추론 자리, D-7) 이 게이트가 잴 수 없는 자리. */
internal data class UntypedDeclaration(
    val declaration: String,
    val line: Int,
)

internal data class PublicApiSurface(
    val publicDeclarations: Int,
    val uses: List<ApiTypeUse>,
    val untyped: List<UntypedDeclaration>,
)

/**
 * domain main 의 **public(+protected) 선언**이 부르는 타입 표면을 PSI 에서 뽑는다 — 설계 검토
 * 부록 §0·§4. `SourceReferences` 와 같은 분리다: 순수 함수라 금지 목록을 모른다. 정책은
 * [ApiTypePolicy] 가 갖는다. 타입 표면 순회(`surfaceReferences`·`userTypes` 등)는
 * `PublicApiTypeSurface.kt` — 파일 하나에 몰면 detekt `TooManyFunctions` 를 넘는다.
 */
internal object PublicApiTypes {
    fun extract(
        fileName: String,
        text: String,
    ): PublicApiSurface = withKotlinPsi { factory -> surfaceOf(factory.createFile(fileName, text)) }

    /** task 용 — 환경을 파일마다 만들지 않는다(`KotlinPsi.parseKotlinFiles`). */
    fun extractAll(files: List<File>): List<PublicApiSurface> =
        parseKotlinFiles(files) { ktFile, _ -> listOf(surfaceOf(ktFile)) }

    private fun surfaceOf(ktFile: KtFile): PublicApiSurface {
        val text = ktFile.text
        val aliases = ktFile.importAliases()
        val declarations = ktFile.declarations.flatMap { it.inScopeDeclarations() }

        val uses = mutableListOf<ApiTypeUse>()
        val untyped = mutableListOf<UntypedDeclaration>()
        declarations.forEach { entry ->
            entry.surfaceReferences().forEach { (slot, ref) ->
                uses += ref.uses(aliases, "${entry.label}$slot", text)
            }
            entry.untypedReason()?.let { reason ->
                val line = text.lineOf(entry.declaration.textOffset)
                untyped += UntypedDeclaration("${entry.label} — $reason", line)
            }
        }
        return PublicApiSurface(declarations.size, uses, untyped)
    }
}

/** 순회 대상 하나 — 선언과 사유 메시지용 이름. */
internal class Target(
    val declaration: KtNamedDeclaration,
    val label: String,
    /** 주 생성자 파라미터처럼 자기 가시성이 아니라 바깥(클래스)의 가시성으로 판정하는 경우. */
    val ownVisibilityOverride: Visibility? = null,
)

internal enum class Visibility { PRIVATE, INTERNAL, PROTECTED, PUBLIC }

/** 효과적으로 public API 표면에 있는지 — 사슬에 private·internal 이 있으면 대상 밖. */
internal fun Target.isInScope(): Boolean = effectiveVisibility() >= Visibility.PROTECTED

private fun Target.effectiveVisibility(): Visibility {
    var visibility = ownVisibilityOverride ?: declaration.ownVisibility()
    var container = declaration.getParentOfType<KtClassOrObject>(strict = true)
    while (container != null) {
        visibility = minOf(visibility, container.ownVisibility())
        container = container.getParentOfType<KtClassOrObject>(strict = true)
    }
    return visibility
}

private fun KtModifierListOwner.ownVisibility(): Visibility =
    when {
        hasModifier(KtTokens.PRIVATE_KEYWORD) -> Visibility.PRIVATE
        hasModifier(KtTokens.INTERNAL_KEYWORD) -> Visibility.INTERNAL
        hasModifier(KtTokens.PROTECTED_KEYWORD) -> Visibility.PROTECTED
        else -> Visibility.PUBLIC
    }

/**
 * 이 선언 아래에서 표면을 재는 대상 목록을 낸다. 함수·프로퍼티·보조 생성자는 자기 자신 하나,
 * 클래스/오브젝트는 자신 + 주 생성자(가시성은 클래스 기준, D-12·D-14) + 본문 선언 재귀,
 * typealias 는 가시성과 무관하게 대상이다(D-5). 본문·초기화식으로는 내려가지 않는다(D-19).
 */
private fun KtDeclaration.inScopeDeclarations(): List<Target> =
    when (this) {
        is KtTypeAlias -> listOf(Target(this, "typealias $name", ownVisibilityOverride = Visibility.PUBLIC))
        is KtNamedFunction -> listOf(Target(this, fqNameLabel)).filterInScope()
        is KtProperty -> listOf(Target(this, fqNameLabel)).filterInScope()
        is KtSecondaryConstructor -> listOf(Target(this, "$fqNameLabel(보조 생성자)")).filterInScope()
        is KtClassOrObject -> classOrObjectTargets()
        else -> emptyList()
    }

private fun List<Target>.filterInScope(): List<Target> = filter { it.isInScope() }

private fun KtClassOrObject.classOrObjectTargets(): List<Target> {
    val self = Target(this, fqNameLabel)
    if (!self.isInScope()) return emptyList()
    val primaryConstructor =
        (this as? KtClass)?.primaryConstructor?.let { ctor ->
            Target(ctor, "$fqNameLabel(주 생성자)", ownVisibilityOverride = Visibility.PUBLIC)
        }
    val body = declarations.flatMap { it.inScopeDeclarations() }
    return listOfNotNull(self, primaryConstructor) + body
}

internal val KtNamedDeclaration.fqNameLabel: String
    get() = name ?: "<익명>"
