package bidvector.app.packaging

import io.kotest.matchers.collections.shouldNotBeEmpty
import org.junit.jupiter.api.Test
import java.io.File
import java.util.jar.JarFile

/**
 * M6/6F-8 — **배포물이 부팅에 필요한 라이브러리를 실제로 싣는가**. test 런타임 classpath 는 production 과 다르다
 * (ArchUnit·kotest 등이 kotlin-reflect 를 끌어온다) — 그래서 Testcontainers 로 production 조립을 부팅하는 test 가
 * 전부 초록인 채로 `java -jar app.jar` 는 `@ConfigurationProperties` 의 Kotlin 생성자 바인딩에서
 * `NoClassDefFoundError: kotlin/reflect/jvm/ReflectJvmMapping` 으로 죽었다(6F-8 스모크 실측, 6A-1 부터의 결함).
 * 이 test 는 `bootJar` 산출물을 열어 그 라이브러리가 들어 있음을 잰다 — 의존 선언을 지우면 RED 다.
 */
class BootJarRuntimeClasspathTest {
    private val bootJar =
        File(System.getProperty("bidvector.bootjar") ?: error("시스템 속성 'bidvector.bootjar' 가 없다 — 빌드가 배포물 경로를 넘긴다"))

    private fun bundledLibraries(): List<String> =
        JarFile(bootJar).use { jar ->
            jar
                .entries()
                .asSequence()
                .map { it.name }
                .filter { it.startsWith("BOOT-INF/lib/") && it.endsWith(".jar") }
                .map { it.substringAfterLast('/') }
                .toList()
        }

    @Test
    fun `배포물에 kotlin-reflect 가 들어 있다 — Kotlin 설정 클래스 바인딩이 부팅에 필요하다`() {
        bundledLibraries().filter { it.startsWith("kotlin-reflect-") }.shouldNotBeEmpty()
    }

    @Test
    fun `배포물이 실제로 열렸다 — 빈 목록을 통과로 오판하지 않는다`() {
        bundledLibraries().filter { it.startsWith("kotlin-stdlib-") }.shouldNotBeEmpty()
    }
}
