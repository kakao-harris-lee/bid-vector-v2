package bidvector.buildlogic

import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtTypeElement
import org.jetbrains.kotlin.psi.KtTypeParameterListOwner
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType

// [PublicApiTypes] 의 타입 표면 순회 — 슬롯 나열·재귀 추출·별칭 해석. `PublicApiTypes.kt` 와 같은
// detekt `TooManyFunctions` 예산을 나눠 쓴다(v2-지침서.md §5 「크기와 결합도」).

/** 슬롯 이름(사유 메시지에 붙일 접미)과 그 자리의 타입 참조 — 설계 검토 부록 §0 의 여섯 표면. */
internal fun Target.surfaceReferences(): List<Pair<String, KtTypeReference?>> =
    when (val d = declaration) {
        is KtNamedFunction -> {
            listOf(" 의 반환 타입" to d.typeReference, " 의 확장 수신자" to d.receiverTypeReference) +
                d.valueParameters.map { " 의 파라미터 '${it.name}'" to it.typeReference } +
                d.typeParameterBounds()
        }

        is KtProperty -> {
            listOf(
                " 의 타입" to d.typeReference,
                " 의 확장 수신자" to d.receiverTypeReference,
                // `val rate get(): Double = …` 처럼 프로퍼티 자신이 아니라 getter 가 타입을
                // 명시하는 자리(verifier r18 F-2) — 명시됐으면 타입 미명시가 아니라 이 축(금지
                // 타입 단언)이 잡아야 한다.
                " 의 접근자 반환 타입" to d.getter?.returnTypeReference,
            )
        }

        is KtSecondaryConstructor -> {
            d.valueParameters.map { " 의 파라미터 '${it.name}'" to it.typeReference }
        }

        is KtPrimaryConstructor -> {
            d.valueParameters.map { " 의 파라미터 '${it.name}'" to it.typeReference }
        }

        is KtClassOrObject -> {
            d.superTypeListEntries.map { " 의 상위 타입" to it.typeReference } + d.typeParameterBounds()
        }

        is KtTypeAlias -> {
            listOf(" 의 우변" to d.getTypeReference())
        }

        else -> {
            emptyList()
        }
    }

private fun KtTypeParameterListOwner.typeParameterBounds(): List<Pair<String, KtTypeReference?>> =
    typeParameters.mapNotNull { param ->
        param.extendsBound?.let { bound -> " 의 타입 파라미터 '${param.name}' bound" to bound }
    }

/** D-7 — 식 본문 함수와 초기화식·위임을 가진 프로퍼티가 타입을 명시하지 않으면 잴 수 없다. */
internal fun Target.untypedReason(): String? =
    when (val d = declaration) {
        is KtNamedFunction -> functionUntypedReason(d)
        is KtProperty -> propertyUntypedReason(d)
        else -> null
    }

private fun Target.functionUntypedReason(function: KtNamedFunction): String? =
    "함수 본문에서 반환 타입이 추론된다"
        .takeIf { isInScope() && !function.hasBlockBody() && function.typeReference == null }

/**
 * **verifier r18 F-2.** 초기화식·위임뿐 아니라 **접근자 본문만 있는 프로퍼티**도 타입 미명시다
 * (`val rate get() = 0.5`) — 이전 조건은 `hasInitializer() || hasDelegate()` 만 봐서 접근자
 * 전용 형태를 놓쳤다. 다만 **getter 가 스스로 반환 타입을 명시하면**(`get(): Double = …`)
 * 타입 미명시가 아니다 — 그 타입은 `surfaceReferences` 가 별도 슬롯으로 잡아 금지 타입
 * 단언으로 넘긴다.
 */
private fun Target.propertyUntypedReason(property: KtProperty): String? {
    val getter = property.getter
    val isUntyped = property.typeReference == null && getter?.returnTypeReference == null
    val hasInferredSource = property.hasInitializer() || property.hasDelegate() || getter?.hasBody() == true
    return "초기화식/위임/접근자에서 타입이 추론된다".takeIf { isInScope() && isUntyped && hasInferredSource }
}

internal fun KtFile.importAliases(): Map<String, String> =
    importDirectives
        .mapNotNull { directive ->
            val alias = directive.aliasName ?: return@mapNotNull null
            val fqn = directive.importedFqName?.asString() ?: return@mapNotNull null
            alias to fqn
        }.toMap()

/** [KtTypeReference] 를 끝까지 재귀해 만난 [KtUserType] 마다 하나씩 [ApiTypeUse] 후보를 낸다. */
internal fun KtTypeReference?.uses(
    aliases: Map<String, String>,
    fileName: String,
    declaration: String,
    text: String,
): List<ApiTypeUse> = this?.typeElement.userTypes().map { it.toUse(aliases, fileName, declaration, text) }

private fun KtTypeElement?.userTypes(): List<KtUserType> =
    when (val element = this) {
        null -> {
            emptyList()
        }

        is KtUserType -> {
            listOf(element) +
                element.typeArgumentList
                    ?.arguments
                    .orEmpty()
                    .flatMap { it.typeReference?.typeElement.userTypes() }
        }

        is KtNullableType -> {
            element.innerType.userTypes()
        }

        is KtFunctionType -> {
            element.receiverTypeReference?.typeElement.userTypes() +
                element.parameters.flatMap { it.typeReference?.typeElement.userTypes() } +
                element.returnTypeReference?.typeElement.userTypes()
        }

        else -> {
            emptyList()
        }
    }

private fun KtUserType.toUse(
    aliases: Map<String, String>,
    fileName: String,
    declaration: String,
    text: String,
): ApiTypeUse {
    val written = qualifiedName()
    val resolved = if ('.' !in written) aliases[written] ?: written else written
    return ApiTypeUse(fileName, resolved, written, declaration, text.lineOf(textRange.startOffset))
}

private fun KtUserType.qualifiedName(): String =
    generateSequence(this) { it.qualifier }
        .toList()
        .asReversed()
        .joinToString(".") { it.referenceExpression?.getReferencedName().orEmpty() }
