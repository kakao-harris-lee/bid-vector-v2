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
 * 판별을 **조상 사슬**로 좁힌다 — ① 감싸는 블록의 지역 선언 중 **이 참조보다 먼저 끝난 것만**
 * (Kotlin 의 지역 변수 스코프는 **선언이 끝난 지점부터** 시작한다 — verifier r20 H-1: 위치를
 * 가리지 않고 형제 문장 전부를 모았더니 참조 **뒤쪽**에 둔 동명 선언도 가리는 것으로 오판했다.
 * verifier r21 H-1': 선언의 **시작** 오프셋만 비교했더니 참조가 **그 선언 자신의 초기화식
 * 안**에 있는 경우(`val java = java.net.…`)도 가려지는 것으로 오판했다 — 그 자리에서 `java`
 * 는 아직 스코프에 없어 패키지로 해석되고 컴파일도 된다. 선언의 **끝** 오프셋(초기화식을
 * 포함한 전체 서브트리의 끝)과 비교하면 자기 초기화식 안의 참조는 항상 그 선언보다 앞서
 * 끝나므로 자동으로 걸러진다) ② 감싸는 함수·람다·보조 생성자·접근자의 파라미터(위치 무관 —
 * Kotlin 도 그렇다: 함수 본문 전체에서 파라미터는 처음부터 보인다) ③ 감싸는 클래스(중첩 포함)의
 * 프로퍼티·주 생성자 파라미터(위치 무관) ④ 파일의 top-level 프로퍼티·import 별칭(위치 무관).
 * **다른 파일의 top-level 선언, 같은 파일의 무관한 형제 함수·클래스 선언은 이 조상 사슬에
 * 없어 여전히 값 체인으로 보지 않는다** — 알려진 제한 47(그 잔여는 그대로 남는다).
 */
internal fun KtElement.visibleLocalNames(): Set<String> {
    val names = mutableSetOf<String>()
    val referenceOffset = textRange.startOffset
    var node: PsiElement? = parent
    while (node != null) {
        node.collectNamesInto(names, referenceOffset)
        node = node.parent
    }
    return names
}

private fun PsiElement.collectNamesInto(
    names: MutableSet<String>,
    referenceOffset: Int,
) {
    when (this) {
        is KtBlockExpression -> {
            statements
                .filterIsInstance<KtProperty>()
                .filter { it.textRange.endOffset <= referenceOffset }
                .forEach { it.name?.let(names::add) }
        }

        is KtNamedFunction -> {
            valueParameters.forEach { it.name?.let(names::add) }
        }

        is KtFunctionLiteral -> {
            valueParameters.forEach { it.name?.let(names::add) }
        }

        is KtSecondaryConstructor -> {
            valueParameters.forEach { it.name?.let(names::add) }
        }

        is KtPropertyAccessor -> {
            parameter?.name?.let(names::add)
        }

        is KtClassOrObject -> {
            collectClassNamesInto(names)
        }

        is KtFile -> {
            collectFileNamesInto(names)
        }

        else -> {
            Unit
        }
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
