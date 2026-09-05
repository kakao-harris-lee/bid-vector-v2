package bidvector.buildlogic

import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import java.io.File

internal class MeasuredType(
    val file: File,
    val name: String,
    val startLine: Int,
    val memberCount: Int,
)

/**
 * 타입(클래스·객체·인터페이스) 하나의 멤버 수를 **Kotlin PSI 소스**에서 잰다 —
 * `size-policy.properties` `limit.type.members`(D-1)의 입력이며 `sizeGate`의 셋째 축이다.
 *
 * 「멤버」는 본문의 선언 수다: 주 생성자 `val`/`var` 파라미터 + 본문 프로퍼티(접근자는
 * 프로퍼티에 포함되며 따로 세지 않는다 — PSI 접근자는 프로퍼티의 자식이지 형제 선언이
 * 아니다) + 함수 + 보조 생성자 + `init` 블록. **중첩·companion 타입은 자기 타입으로 따로
 * 계수한다**(멤버가 아니다) — `KtClassBody.properties`/`functions`는 직계 자식만 내므로
 * 중첩 타입 안의 선언은 자동으로 빠지고, 그 중첩 타입 자신은 이 방문자가 별도 노드로
 * 다시 방문해 잰다. **enum entry 는 멤버가 아니다** — `KtClassBody.enumEntries`가
 * `properties`/`functions`와 분리된 API라 자동으로 빠진다.
 *
 * data class 의 컴파일러 합성 멤버(getter·`componentN`·`copy`·`equals`/`hashCode`/
 * `toString`)는 소스에 없으므로 PSI 로는 절대 잡히지 않는다 — 바이트코드 계수
 * (`QualityBaselineTask.maxTypeMembers`)보다 이 축이 작거나 같은 이유가 그것이다.
 */
internal fun measureTypes(files: List<File>): List<MeasuredType> = parseKotlinFiles(files, ::measureFileTypes)

private fun measureFileTypes(
    ktFile: KtFile,
    source: File,
): List<MeasuredType> {
    val text = ktFile.text
    val measured = mutableListOf<MeasuredType>()
    ktFile.accept(
        object : KtTreeVisitorVoid() {
            override fun visitKtElement(element: KtElement) {
                if (element is KtClassOrObject) {
                    measured +=
                        MeasuredType(
                            source,
                            element.typeName(),
                            text.lineOf(element.textRange.startOffset),
                            element.countDirectMembers(),
                        )
                }
                super.visitKtElement(element)
            }
        },
    )
    return measured
}

/** 익명 객체·이름 없는 companion(`companion object { }`)도 위반 보고에서 알아볼 수 있게 표시한다. */
private fun KtClassOrObject.typeName(): String =
    name ?: if (this is KtObjectDeclaration && isCompanion()) "Companion" else "<익명 객체>"

private fun KtClassOrObject.countDirectMembers(): Int {
    val constructorProperties = primaryConstructorParameters.count { it.hasValOrVar() }
    val body = body
    val bodyProperties = body?.properties?.size ?: 0
    val bodyFunctions = body?.functions?.size ?: 0
    val initializers = body?.anonymousInitializers?.size ?: 0
    return constructorProperties + bodyProperties + bodyFunctions + secondaryConstructors.size + initializers
}
