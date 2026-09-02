package bidvector.buildlogic

import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
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
 * 앞선 판은 detekt 이 그 축의 정본이고 sizeGate 가 `@Suppress("LongMethod")` 텍스트를 막았다.
 * 그 방식은 **표기의 열거 게임**이 된다 — 다중 행 `@Suppress`(Codex 5차 b) · `@file:Suppress` ·
 * `@kotlin.Suppress` · 상수 경유. 직접 재면 **억제가 이 임계에 아무 영향이 없어진다.**
 *
 * `funKeyword` 부터 세므로 KDoc 과 애노테이션은 길이에 들지 않는다 — 문서를 붙였다고 함수가
 * 길어지지 않는다. **람다도 함께 잰다**: 재지 않으면 「45줄 함수 + 20줄 람다」가 한 줄짜리
 * 우회가 된다.
 *
 * lexer 로도 잴 수 있으나 상태 기계를 손으로 써야 하고, 설계 검토가 그 20줄짜리가 **식 본문
 * 함수에서 곧바로 틀리는 것**을 실측했다. PSI 는 문자열·주석·`"""`·식 본문을 이미 갈라 준다.
 */
internal fun measureFunctions(files: List<File>): List<MeasuredFunction> {
    val kotlinFiles = files.filter { it.extension == "kt" }
    if (kotlinFiles.isEmpty()) return emptyList()

    // 전역 application environment 를 잡으므로 task action 안에서 만들고 반드시 dispose 한다 —
    // 데몬이 재사용되므로 누수가 쌓인다.
    val disposable = Disposer.newDisposable("bidvector-size-gate")
    return try {
        // K1 PSI 파서다. K2 의 공개 파싱 API 가 서면 옮긴다 — 그때도 재는 대상은 같다.
        @OptIn(CompilerConfiguration.Internals::class, org.jetbrains.kotlin.K1Deprecation::class)
        val environment =
            KotlinCoreEnvironment.createForProduction(
                disposable,
                CompilerConfiguration(),
                EnvironmentConfigFiles.JVM_CONFIG_FILES,
            )
        val factory = KtPsiFactory(environment.project)
        kotlinFiles.flatMap { file -> measureFile(factory.createFile(file.name, file.readText()), file) }
    } finally {
        Disposer.dispose(disposable)
    }
}

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
            override fun visitNamedFunction(function: KtNamedFunction) {
                // `funKeyword` 부터 — KDoc·애노테이션은 길이가 아니다.
                val start = function.funKeyword?.textRange?.startOffset ?: function.textRange.startOffset
                record(function.name ?: "<익명>", start, function.textRange.endOffset)
                super.visitNamedFunction(function)
            }

            override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression) {
                val body = lambdaExpression.getChildOfType<org.jetbrains.kotlin.psi.KtFunctionLiteral>()
                val range = (body ?: lambdaExpression).textRange
                record("<람다>", range.startOffset, range.endOffset)
                super.visitLambdaExpression(lambdaExpression)
            }
        },
    )
    return measured
}

private fun String.lineOf(offset: Int): Int = substring(0, offset.coerceAtMost(length)).count { it == '\n' } + 1
