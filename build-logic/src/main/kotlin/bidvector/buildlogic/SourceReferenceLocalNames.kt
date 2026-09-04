package bidvector.buildlogic

import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtSecondaryConstructor

/**
 * [this] 자리에서 실제로 보이는(lexical scope) 이름 — Codex 14차 #1. verifier r19 M-2 수정이
 * **파일 전체** 이름 집합을 썼다가, 다른 함수의 동명 지역 변수(`val java = 1`)가 이 함수의
 * 완전수식 참조(`java.net.…`)까지 지우는 **미탐**을 냈다 — 게이트 술어의 원칙(오탐이 미탐보다
 * 낫다)에 어긋난다.
 *
 * 판별을 **조상 사슬**로 좁힌다 — ① 감싸는 블록의 지역 선언(같은 블록의 형제 문장 전부, 위치를
 * 가리지 않는다: 선언보다 앞서 쓰면 Kotlin 자체가 컴파일을 거부하므로 안전하다) ② 감싸는
 * 함수·람다·보조 생성자·접근자의 파라미터 ③ 감싸는 클래스(중첩 포함)의 프로퍼티·주 생성자
 * 파라미터 ④ 파일의 top-level 프로퍼티·import 별칭. **다른 파일의 top-level 선언은 이 조상
 * 사슬에 없어 여전히 값 체인으로 보지 않는다** — 알려진 제한 47(그 잔여는 그대로 남는다).
 */
internal fun KtElement.visibleLocalNames(): Set<String> {
    val names = mutableSetOf<String>()
    var node: PsiElement? = parent
    while (node != null) {
        node.collectNamesInto(names)
        node = node.parent
    }
    return names
}

private fun PsiElement.collectNamesInto(names: MutableSet<String>) {
    when (this) {
        is KtBlockExpression -> statements.filterIsInstance<KtProperty>().forEach { it.name?.let(names::add) }
        is KtNamedFunction -> valueParameters.forEach { it.name?.let(names::add) }
        is KtFunctionLiteral -> valueParameters.forEach { it.name?.let(names::add) }
        is KtSecondaryConstructor -> valueParameters.forEach { it.name?.let(names::add) }
        is KtPropertyAccessor -> parameter?.name?.let(names::add)
        is KtClassOrObject -> collectClassNamesInto(names)
        is KtFile -> collectFileNamesInto(names)
        else -> Unit
    }
}

private fun KtClassOrObject.collectClassNamesInto(names: MutableSet<String>) {
    declarations.filterIsInstance<KtProperty>().forEach { it.name?.let(names::add) }
    (this as? KtClass)?.primaryConstructor?.valueParameters?.forEach { it.name?.let(names::add) }
}

private fun KtFile.collectFileNamesInto(names: MutableSet<String>) {
    declarations.filterIsInstance<KtProperty>().forEach { it.name?.let(names::add) }
    names += importDirectives.mapNotNull { it.aliasName }
}
