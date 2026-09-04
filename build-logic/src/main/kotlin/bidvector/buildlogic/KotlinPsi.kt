package bidvector.buildlogic

import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.io.File

private val KOTLIN_EXTENSIONS = setOf("kt", "kts")

/**
 * Kotlin PSI 파서 환경을 **한 자리에서** 만들고 반드시 dispose 한다. 함수 길이 게이트와 소스
 * 참조 게이트가 같은 boilerplate 를 두 벌 갖지 않게 한다(v2-지침서.md §5 「중복 금지」).
 *
 * 전역 application environment 를 잡으므로 호출마다 disposable 을 새로 만들고 끝나면 반드시
 * dispose 한다 — Gradle 데몬이 재사용되므로 누수가 쌓인다.
 */
internal fun <T> withKotlinPsi(action: (KtPsiFactory) -> T): T {
    val disposable = Disposer.newDisposable("bidvector-kotlin-psi")
    return try {
        @OptIn(CompilerConfiguration.Internals::class, org.jetbrains.kotlin.K1Deprecation::class)
        val environment =
            KotlinCoreEnvironment.createForProduction(
                disposable,
                CompilerConfiguration(),
                EnvironmentConfigFiles.JVM_CONFIG_FILES,
            )
        action(KtPsiFactory(environment.project))
    } finally {
        Disposer.dispose(disposable)
    }
}

/**
 * [files] 중 Kotlin 소스만 골라 파싱하고 [parse] 로 결과를 뽑는다. 확장자가 아니면 파서를 만들지
 * 않고 빈 목록을 낸다 — 빈 domain 모듈에서도 환경 생성 비용을 물지 않는다.
 */
internal fun <T> parseKotlinFiles(
    files: List<File>,
    parse: (KtFile, File) -> List<T>,
): List<T> {
    val kotlinFiles = files.filter { it.extension in KOTLIN_EXTENSIONS }
    if (kotlinFiles.isEmpty()) return emptyList()
    return withKotlinPsi { factory -> kotlinFiles.flatMap { file -> parse(factory.createFile(file.name, file.readText()), file) } }
}

/** 오프셋을 1-based 줄 번호로. 텍스트 기준이라 PSI 노드 종류를 가리지 않는다. */
internal fun String.lineOf(offset: Int): Int = substring(0, offset.coerceAtMost(length)).count { it == '\n' } + 1
