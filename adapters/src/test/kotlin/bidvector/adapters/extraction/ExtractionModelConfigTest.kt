package bidvector.adapters.extraction

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

private val FORBIDDEN_LITERALS =
    Regex("""gpt-|claude-|gemini|openai\.com|anthropic\.com""", RegexOption.IGNORE_CASE)

/**
 * M3/3C ③ — provider/model 은 생성자 주입만 가능하고 코드 상수가 없다(위협 모델 방어
 * (d), 우회 (4)). `LlmEndpoint`·`ModelId`는 기본 인자가 없는 `data class`라 호출부가
 * 반드시 값을 넘겨야 한다는 사실은 컴파일 시점 증거이고, 이 test 는 main 소스에 provider
 * 이름·도메인 리터럴이 전혀 없다는 실행 증거를 더한다.
 */
class ExtractionModelConfigTest {
    @Test
    fun `main 소스에 provider 이름 리터럴이 없다`() {
        val sourceRoot = File("src/main/kotlin/bidvector/adapters/extraction")
        check(sourceRoot.isDirectory) { "소스 루트를 찾지 못했다: ${sourceRoot.absolutePath}" }

        val violations =
            sourceRoot
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { file -> violationsIn(file) }
                .toList()

        violations shouldBe emptyList()
    }

    private fun violationsIn(file: File): List<String> =
        file.readLines().mapIndexedNotNull { index, line ->
            if (FORBIDDEN_LITERALS.containsMatchIn(line)) "${file.name}:${index + 1}" else null
        }
}
