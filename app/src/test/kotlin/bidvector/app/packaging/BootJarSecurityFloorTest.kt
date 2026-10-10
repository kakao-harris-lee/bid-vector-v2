package bidvector.app.packaging

import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
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
 * **이 test 가 재는 것과 재지 않는 것**(알려진 제한 5): 재는 것은 「선언이 해석에 효과를 냈는가」 하나다.
 * 카탈로그 값을 내리면 기대값도 함께 내려가 여기는 초록이고, 그때 붉어지는 것은 **취약점 게이트**다
 * (그 CVE 가 되살아난다). 두 게이트가 짝으로만 닫힌다.
 */
class BootJarSecurityFloorTest {
    private val bootJar =
        File(System.getProperty("bidvector.bootjar") ?: error("시스템 속성 'bidvector.bootjar' 가 없다 — 빌드가 배포물 경로를 넘긴다"))

    private fun floor(name: String): String =
        System.getProperty("bidvector.security.floor.$name")
            ?: error("시스템 속성 'bidvector.security.floor.$name' 가 없다 — 빌드가 카탈로그 값을 넘긴다")

    /** `jackson-core-2.21.7.jar` → 좌표 `jackson-core`, 판 `2.21.7`. 판은 숫자로 시작하고 `-` 를 담지 않는다. */
    private val jarName = Regex("""^(?<artifact>.+)-(?<version>\d[^-]*)\.jar$""")

    private fun bundledJarNames(): List<String> =
        JarFile(bootJar).use { jar ->
            jar
                .entries()
                .asSequence()
                .map { it.name }
                .filter { it.startsWith("BOOT-INF/lib/") && it.endsWith(".jar") }
                .map { it.substringAfterLast('/') }
                .toList()
        }

    /**
     * 그 좌표로 실린 사본의 판 집합.
     *
     * **분해되지 않는 이름을 조용히 버리지 않는다**(code-review r1 L-2). `mapNotNull` 만 쓰면
     * `jackson-core-2.21.7-jdk8.jar` 같은 classifier 사본이 흔적 없이 사라져, 「하한 미만 사본이 함께
     * 실려도 잡는다」는 이 test 의 약속이 **분해 가능한 이름 위에서만** 참이 된다 — 막으려던 바로 그
     * 형태가 빠져나간다. 그래서 접두가 같은데 분해되지 않는 이름이 하나라도 있으면 그 자리에서 실패한다
     * (「분해 실패」와 「부재」가 갈린다).
     */
    private fun versionsOf(artifact: String): Set<String> {
        val names = bundledJarNames()
        val unparsed = names.filter { it.startsWith("$artifact-") && jarName.matchEntire(it) == null }
        withClue("$artifact: 분해되지 않는 jar 이름이 있다 — 판을 읽지 못하면 하한을 잴 수 없다: $unparsed") {
            unparsed.shouldBeEmpty()
        }
        return names
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

    /**
     * **major 별 술어**(code-review r1 L-3). 「jackson 2 와 3 의 판 집합이 기대와 같다」로 쓰면 **Jackson 2 가
     * 배포물에 있어야 한다**까지 잠긴다. Boot 생태계가 Jackson 3 으로 수렴해 2 가 그래프에서 빠지는 날 —
     * 그것은 바람직한 변화인데 — 이 test 가 「보안 하한 위반」 문면으로 RED 가 되고, 눈에 보이는 처방이
     * 단언을 **약화**하는 것이 된다. 그래서 「있다면 전부 하한이다」로 잰다: 하한 미만 사본은 그대로 RED,
     * 좌표가 빠지는 것은 초록. (`OPEN-6E2C-BOOT-FLOOR-SUNSET` 과는 다른 항목이다.)
     */
    @Test
    fun `배포물의 jackson 사본은 major 별로 보안 하한 판이다`() {
        val floors = mapOf("2." to floor("jackson2"), "3." to floor("jackson3"))
        listOf("jackson-core", "jackson-databind").forEach { artifact ->
            val versions = versionsOf(artifact)
            floors.forEach { (major, expected) ->
                val offFloor = versions.filter { it.startsWith(major) && it != expected }
                withClue("$artifact: major $major 사본이 하한 $expected 과 다르다(빠지는 것은 허용, 어긋나는 것은 아니다): $offFloor") {
                    offFloor.shouldBeEmpty()
                }
            }
        }
    }
}
