package bidvector.app.packaging

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File
import java.util.jar.JarFile

/**
 * **보안 하한이 배포물에서 실제 효과를 냈는가**(M6/6E-2c D-6E2C-3, `OPEN-6E2B-DEPENDENCY-BUMP`).
 *
 * Boot 4.1.1 BOM 이 관리하는 tomcat·jackson 판에 수정판 있는 HIGH/CRITICAL 이 걸려 있고 Boot 4.1.x 는
 * 4.1.1 이 최신이라, 하한을 `platform()`·`constraints` 로 BOM 위에 얹었다. 그 선언이 **효과를 냈는지**는
 * 선언 자체가 말해 주지 않는다 — 이 모듈은 `io.spring.dependency-management` 를 적용하지 않아 Boot 3 의
 * `extra["tomcat.version"]` 관례가 무효인 것이 그 예다(형태는 멀쩡한데 아무것도 바뀌지 않는다).
 *
 * 그래서 **출하되는 바이트**(`bootJar` 의 `BOOT-INF/lib`)에서 판을 읽는다. 기대값은 카탈로그가 빌드
 * 스크립트를 통해 넘기므로 값이 두 자리에 살지 않는다.
 *
 * 판정은 「그 판이 있다」가 아니라 **「그 좌표의 판 집합이 기대와 같다」**다 — 존재만 보면 하한 미만의
 * 사본이 함께 실려도 초록이고, 그 사본이 곧 스캐너가 잡는 것이다.
 */
class BootJarSecurityFloorTest {
    private val bootJar =
        File(System.getProperty("bidvector.bootjar") ?: error("시스템 속성 'bidvector.bootjar' 가 없다 — 빌드가 배포물 경로를 넘긴다"))

    private fun floor(name: String): String =
        System.getProperty("bidvector.security.floor.$name")
            ?: error("시스템 속성 'bidvector.security.floor.$name' 가 없다 — 빌드가 카탈로그 값을 넘긴다")

    /** `jackson-core-2.21.7.jar` → 좌표 `jackson-core`, 판 `2.21.7`. 판은 숫자로 시작하고 `-` 를 담지 않는다. */
    private val jarName = Regex("""^(?<artifact>.+)-(?<version>\d[^-]*)\.jar$""")

    private fun versionsOf(artifact: String): Set<String> =
        JarFile(bootJar).use { jar ->
            jar
                .entries()
                .asSequence()
                .map { it.name }
                .filter { it.startsWith("BOOT-INF/lib/") && it.endsWith(".jar") }
                .map { it.substringAfterLast('/') }
                .mapNotNull(jarName::matchEntire)
                .filter { it.groups["artifact"]!!.value == artifact }
                .map { it.groups["version"]!!.value }
                .toSet()
        }

    @Test
    fun `배포물의 tomcat-embed 세 좌표가 보안 하한 판이다`() {
        val expected = setOf(floor("tomcat"))
        listOf("tomcat-embed-core", "tomcat-embed-el", "tomcat-embed-websocket").forEach { artifact ->
            withClue(artifact) { versionsOf(artifact) shouldBe expected }
        }
    }

    @Test
    fun `배포물의 jackson 2 와 3 이 각자 보안 하한 판이고 그 둘뿐이다`() {
        val expected = setOf(floor("jackson2"), floor("jackson3"))
        listOf("jackson-core", "jackson-databind").forEach { artifact ->
            withClue(artifact) { versionsOf(artifact) shouldBe expected }
        }
    }
}
