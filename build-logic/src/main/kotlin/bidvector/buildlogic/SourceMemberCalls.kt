package bidvector.buildlogic

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportList
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

/** 소스가 부른 멤버 하나 — [receiver] 는 수신자의 **마지막 세그먼트**, [member] 는 selector 이름이다. */
internal data class SourceMemberCall(
    val fileName: String,
    val line: Int,
    val receiver: String,
    val member: String,
)

/**
 * **수신자·멤버 쌍**을 PSI 에서 뽑는다 — OPS-13 「실행 환경 스니핑 0」의 관측 자리다.
 *
 * 왜 좌표(FQN) 축으로는 안 되나: `System.getenv("CI")` 의 `System` 은 `java.lang` 기본 import 로
 * 해석되므로 소스에 패키지 접두가 없고, [SourceReferences] 의 후보 조립은 첫 세그먼트가 대문자면
 * 단순 이름 참조로 보아 건너뛴다. 그래서 그 호출은 좌표 축에 **아예 나타나지 않는다**.
 *
 * 수신자는 마지막 세그먼트로 센다 — `java.lang.System.getenv` 와 `System.getenv` 가 같은 쌍이 된다.
 * 별칭 import 로 수신자 이름을 바꾸는 경로는 그 **import 가 원 FQN 을 내므로** 좌표 축에 걸린다
 * (두 축이 겹쳐 닫는다). 리플렉션으로 부르는 경로는 이 축이 닫지 못한다(알려진 한계 —
 * `collection.reflection.*` 가 그 축을 따로 든다).
 */
internal object SourceMemberCalls {
    fun extractFrom(
        ktFile: KtFile,
        fileName: String,
    ): List<SourceMemberCall> {
        val text = ktFile.text
        val found = mutableListOf<SourceMemberCall>()
        ktFile.accept(
            object : KtTreeVisitorVoid() {
                override fun visitImportList(importList: KtImportList) = Unit

                override fun visitPackageDirective(directive: KtPackageDirective) = Unit

                override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
                    record(
                        expression.receiverExpression.lastSegment(),
                        expression.selectorExpression?.nameOf(),
                        expression,
                    )
                    super.visitDotQualifiedExpression(expression)
                }

                /**
                 * `System::getenv` 로 넘겨 나중에 부르는 형태도 같은 쌍이다 — 호출 자리만 보면
                 * 참조를 변수에 담아 비껴가는 길이 열린다.
                 */
                override fun visitCallableReferenceExpression(expression: KtCallableReferenceExpression) {
                    record(
                        expression.receiverExpression?.lastSegment(),
                        expression.callableReference.getReferencedName(),
                        expression,
                    )
                    super.visitCallableReferenceExpression(expression)
                }

                fun record(
                    receiver: String?,
                    member: String?,
                    at: KtExpression,
                ) {
                    if (receiver == null || member == null) return
                    found += SourceMemberCall(fileName, text.lineOf(at.textRange.startOffset), receiver, member)
                }
            },
        )
        return found
    }

    /** 수신자 사슬의 마지막 이름. 사슬이 아니면(호출 결과 등) `null` — 쌍을 만들 수 없다. */
    private fun KtExpression.lastSegment(): String? =
        when (this) {
            is KtSimpleNameExpression -> getReferencedName()
            is KtDotQualifiedExpression -> selectorExpression?.nameOf()
            is KtCallExpression -> (calleeExpression as? KtSimpleNameExpression)?.getReferencedName()
            else -> null
        }

    private fun KtExpression.nameOf(): String? =
        when (this) {
            is KtSimpleNameExpression -> getReferencedName()
            is KtCallExpression -> (calleeExpression as? KtSimpleNameExpression)?.getReferencedName()
            else -> null
        }
}
