package bidvector.buildlogic

import org.jetbrains.kotlin.psi.KtAnonymousInitializer
import org.jetbrains.kotlin.psi.KtClassInitializer
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtScriptInitializer
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import java.io.File

internal class MeasuredFunction(
    val file: File,
    val name: String,
    val startLine: Int,
    val lines: Int,
)

/**
 * 함수 길이를 **Kotlin PSI 로 직접** 잰다.
 *
 * 억제 표기를 텍스트로 막으려 들면 **표기의 열거 게임**이 된다 — 다중 행 `@Suppress` ·
 * `@file:Suppress` · `@kotlin.Suppress` · 상수 경유. 직접 재면 **억제가 이 임계에 아무 영향이
 * 없어진다.**
 *
 * 재는 대상은 **본문을 갖는 선언 전부**다(`size-policy.properties` 의 정의). 방문자 메서드를
 * 골라 구현하면 구현하지 않은 표기(접근자·`init`·보조 생성자)가 그대로 임계를 지나가므로
 * 노드 **타입**으로 판정한다. `KtDeclarationWithBody` 와
 * `KtAnonymousInitializer` 는 PSI 가 「본문을 갖는다」를 표현하는 자리이므로, 새 표기가
 * 생기더라도 그 둘 아래로 들어오는 한 자동으로 재진다.
 *
 * 선언 키워드부터 세므로 KDoc 과 애노테이션은 길이에 들지 않는다 — 문서를 붙였다고 함수가
 * 길어지지 않는다. **람다도 함께 잰다**: 재지 않으면 「45줄 함수 + 20줄 람다」가 한 줄짜리
 * 우회가 된다.
 *
 * lexer 로도 잴 수 있으나 상태 기계를 손으로 써야 하고 **식 본문 함수에서 곧바로 틀린다**(실측).
 * PSI 는 문자열·주석·`"""`·식 본문을 이미 갈라 준다.
 */
internal fun measureFunctions(files: List<File>): List<MeasuredFunction> =
    // `.kts` 도 잰다 — 빌드 로직은 이 저장소에서 실제 코드이고, 빼 두면 긴 함수가 스크립트로
    // 옮겨 가는 것이 우회가 된다. `KtPsiFactory.createFile` 이 확장자로 스크립트를 판별하므로
    // 파서를 따로 두지 않는다. K1 PSI 파서다 — K2 의 공개 파싱 API 가 서면 옮긴다.
    parseKotlinFiles(files, ::measureFile)

private fun measureFile(
    ktFile: KtFile,
    source: File,
): List<MeasuredFunction> {
    val text = ktFile.text
    val measured = mutableListOf<MeasuredFunction>()

    fun record(
        name: String,
        startOffset: Int,
        endOffset: Int,
    ) {
        val startLine = text.lineOf(startOffset)
        measured += MeasuredFunction(source, name, startLine, text.lineOf(endOffset) - startLine + 1)
    }

    ktFile.accept(
        object : KtTreeVisitorVoid() {
            override fun visitKtElement(element: KtElement) {
                if (element.hasDeclarationBody()) {
                    record(element.declarationName(), element.measurementStart(), element.textRange.endOffset)
                }
                super.visitKtElement(element)
            }
        },
    )
    return measured
}

/**
 * **재는 대상의 정의.** PSI 에서 「본문을 갖는다」를 표현하는 타입은 둘뿐이므로 표기를 열거하지
 * 않는다 — 함수·람다·프로퍼티 접근자·보조 생성자는 `KtDeclarationWithBody`, `init` 블록은
 * `KtAnonymousInitializer` 아래로 들어온다. 본문이 없는 선언(주 생성자·추상 함수)은 이 축이
 * 아니다.
 */
private fun KtElement.hasDeclarationBody(): Boolean =
    when (this) {
        // 스크립트의 top-level 문장은 함수 본문이 아니다 — `KtScriptInitializer` 가
        // `KtAnonymousInitializer` 를 구현하므로 명시적으로 뺀다. **그 안의 람다는 이미 재므로
        // 새는 것이 없고**, 빼지 않으면 문장과 그 안의 람다가 같은 범위를 두 번 보고한다.
        is KtScriptInitializer -> false

        is KtAnonymousInitializer -> body != null

        is KtDeclarationWithBody -> hasBody()

        else -> false
    }

/** 위반 보고에 쓸 이름. 이름이 없는 표기는 무엇이었는지 알아볼 수 있게 표시한다. */
private fun KtElement.declarationName(): String =
    when (this) {
        is KtFunctionLiteral -> "<람다>"
        is KtAnonymousInitializer -> "init"
        is KtPropertyAccessor -> "${property.name ?: "<익명>"}.${if (isGetter) "get" else "set"}"
        is KtSecondaryConstructor -> "constructor"
        is KtNamedFunction -> name ?: "<익명>"
        else -> "<본문 선언>"
    }

/**
 * 길이의 시작점은 **선언 키워드**다 — KDoc 과 애노테이션은 그 앞에 있어 길이에 들지 않는다.
 * 키워드를 모르는 노드는 노드 시작부터 세어 **길게** 잡는다: 게이트는 닫히는 쪽으로 틀린다.
 */
private fun KtElement.measurementStart(): Int {
    val keyword =
        when (this) {
            is KtNamedFunction -> funKeyword
            is KtSecondaryConstructor -> getConstructorKeyword()
            is KtPropertyAccessor -> namePlaceholder
            is KtClassInitializer -> initKeyword
            else -> null
        }
    return (keyword ?: this).textRange.startOffset
}
