package bidvector.buildlogic

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtUserType

// [SourceReferences] 의 **세그먼트 판독** — 같은 detekt `TooManyFunctions` 예산을 나눠 쓴다
// (v2-지침서.md §5 「크기와 결합도」, `SourceReferenceCandidates` 분리와 같은 이유).

/** 세그먼트 이름을 뿌리→잎 순서로. 백틱은 `getReferencedName()` 이 이미 벗긴다(S-16). */
internal fun KtUserType.segments(): List<String>? {
    val names = mutableListOf<String>()
    var current: KtUserType? = this
    while (current != null) {
        names += current.referenceExpression?.getReferencedName() ?: return null
        current = current.qualifier
    }
    return names.asReversed()
}

internal fun KtDotQualifiedExpression.segments(): List<String>? = collectSegments(emptyList())?.asReversed()

/**
 * 뿌리 방향으로 재귀하며 세그먼트를 잎→뿌리 순서로 쌓는다. **호출(`KtCallExpression`)도
 * 세그먼트를 낸다** — 그 callee 가 단순 이름이면(`java.io.File("x")` 같은
 * 완전수식 생성자·정적 호출) 그 이름을 이어 붙인다. `candidateForms` 의 대문자 이어달리기가
 * `.toString()`·`.encode(…)` 처럼 소문자로 시작하는 일반 메서드 호출은 스스로 잘라내므로
 * (첫 소문자 세그먼트에서 멈춘다), 이 확장이 거짓 양성을 늘리지 않는다. 그 외 형태(사슬이
 * 아닌 수신자 등)는 여전히 `null`.
 */
internal fun KtExpression.collectSegments(accumulated: List<String>): List<String>? =
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

internal fun KtDotQualifiedExpression.selectorName(): String? =
    when (val selector = selectorExpression) {
        is KtSimpleNameExpression -> selector.getReferencedName()
        is KtCallExpression -> (selector.calleeExpression as? KtSimpleNameExpression)?.getReferencedName()
        else -> null
    }
