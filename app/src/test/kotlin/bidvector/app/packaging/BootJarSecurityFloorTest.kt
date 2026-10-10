package bidvector.app.packaging

import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
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

    /** 판의 모양. 숫자로 시작하고, 그 뒤로는 판에 쓰이는 문자만 온다(classifier·타임스탬프 스냅숏 포함). */
    private val versionShape = Regex("""^\d[A-Za-z0-9.+_-]*$""")

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
     * **이름을 정규식으로 쪼개지 않고, 대상 접두로 고른 뒤 나머지를 판으로 읽는다.** 탐욕 정규식으로
     * 쪼개면 `jackson-core-2.21.5-1.jar` 가 좌표 `jackson-core-2.21.5`·판 `1` 로 **분해에 성공**해 버려서,
     * 대상과 좌표가 다르다는 이유로 조용히 빠진다 — 하한 미만 사본인데 초록이다. 접두로 고르면 그 이름은
     * 판 `2.21.5-1` 로 읽혀 비교 집합에 들어온다.
     *
     * 나머지가 **숫자로 시작하지 않으면** 그것은 이 좌표의 판이 아니라 **다른 좌표**다
     * (`jackson-core-utils-1.0.jar` → `utils-1.0`). 그런 이름은 대상이 아니므로 버린다 — 오탐을 만들지
     * 않는다. 숫자로 시작하는데 판 모양을 벗어나는 이름만 **판독 실패**로 끊는다(조용히 버리면 아래
     * 「하한 미만 사본 부재」가 그 이름 위에서 거짓이 된다).
     */
    private fun versionsOf(
        artifact: String,
        names: List<String>,
    ): Set<String> {
        val prefix = "$artifact-"
        val tails =
            names
                .filter { it.startsWith(prefix) }
                .map { it.removePrefix(prefix).removeSuffix(".jar") }
        val unreadable = tails.filter { it.first().isDigit() && !versionShape.matches(it) }
        withClue("$artifact: 판으로 읽히지 않는 꼬리가 있다 — 읽지 못하면 하한을 잴 수 없다: $unreadable") {
            unreadable.shouldBeEmpty()
        }
        return tails.filter { versionShape.matches(it) }.toSet()
    }

    @Test
    fun `배포물의 tomcat-embed 세 좌표가 보안 하한 판이다`() {
        val names = bundledJarNames()
        withClue("배포물의 BOOT-INF/lib 가 비어 있다 — 빈 목록 위의 '위반 0' 은 공허하다") {
            names.shouldNotBeEmpty()
        }
        val expected = setOf(floor("tomcat"))
        listOf("tomcat-embed-core", "tomcat-embed-el", "tomcat-embed-websocket").forEach { artifact ->
            withClue(artifact) { versionsOf(artifact, names) shouldBe expected }
        }
    }

    /**
     * **jackson 은 두 축으로 잰다**(D-6E2C-8).
     *
     * ⓐ **카탈로그가 하한을 선언한 좌표**(`jackson-core`·`jackson-databind`)는 **접두와 무관하게 항상
     *    대상**이다. 실린 사본은 그 major 의 하한과 같아야 한다 — `jackson-databind-2.20.3.jar` 처럼
     *    **minor 가 낮은 사본**은 `2.21.` 접두를 갖지 않으므로, 라인 접두만으로 대상을 고르면 조용히
     *    빠진다(하한 미만인데 초록). major 가 있다면 전부 하한이고, **좌표가 통째로 빠지는 것은 허용**
     *    한다(Boot 생태계가 Jackson 3 으로 수렴하는 날 그것은 바람직한 변화다).
     * ⓑ **그 밖의 가족 원소**(`jackson-dataformat-yaml` 등)는 같은 `major.minor.` 라인 위에 있으면
     *    하한과 같아야 한다. 라인 밖은 BOM 이 **독자 판**으로 핀하는 좌표라(실측: 2.21.7 BOM 이
     *    `jackson-annotations` 를 `2.21` 로 핀한다) 카탈로그 하한과 비교할 대상이 아니다.
     * ⓒ **대상 밖 원소는 통과할 때도 출력에 나열한다** — 실패 문면에만 적으면 초록인 회차에서 조용히
     *    사라지고, 「무엇을 안 쟀는가」가 보이지 않는다.
     *
     * 안 재는 것(알려진 제한): core·databind **밖** 원소의 낮은 minor 사본과 독자 라인(annotations).
     * 그 축은 취약점 게이트가 진다 — 그 jar 에 CVE 가 나면 스캔이 붉어진다.
     */
    @Test
    fun `배포물의 jackson 가족이 보안 하한 판이다`() {
        val names = bundledJarNames()
        withClue("배포물의 BOOT-INF/lib 가 비어 있다 — 빈 목록 위의 '위반 0' 은 공허하다") {
            names.shouldNotBeEmpty()
        }
        val floors = mapOf("2" to floor("jackson2"), "3" to floor("jackson3"))
        val declared = listOf("jackson-core", "jackson-databind")

        declared.forEach { artifact ->
            val offFloor =
                versionsOf(artifact, names)
                    .filter { version -> floors[version.substringBefore('.')]?.equals(version) == false }
            withClue("$artifact: 하한과 다른 사본이 실렸다(좌표가 빠지는 것은 허용, 어긋나는 것은 아니다): $offFloor") {
                offFloor.shouldBeEmpty()
            }
        }

        val offTarget = mutableListOf<String>()
        names
            .filter { it.startsWith("jackson-") }
            .mapNotNull { name -> splitArtifactVersion(name)?.let { name to it } }
            .filterNot { (_, split) -> split.first in declared }
            .forEach { (name, split) ->
                val onLine = floors.values.firstOrNull { split.second.startsWith(it.substringBeforeLast('.') + ".") }
                if (onLine == null) {
                    offTarget += name
                } else {
                    withClue("$name: 하한 $onLine 라인 위인데 하한과 다르다") { split.second shouldBe onLine }
                }
            }
        println("[BootJarSecurityFloorTest] 대상 밖 jackson 가족 원소(이 test 가 재지 않는다 — 취약점 게이트가 진다): $offTarget")
    }

    /**
     * `jackson-dataformat-yaml-2.21.7.jar` → (`jackson-dataformat-yaml`, `2.21.7`).
     * **숫자로 시작하는 첫 토큰부터가 판**이다 — 그래서 `jackson-core-2.21.5-1.jar` 는 판 `2.21.5-1`(좌표
     * `jackson-core`)로 읽히고, 탐욕 정규식처럼 좌표 `jackson-core-2.21.5`·판 `1` 로 오분해되지 않는다.
     */
    private fun splitArtifactVersion(jarName: String): Pair<String, String>? {
        val tokens = jarName.removeSuffix(".jar").split('-')
        val versionAt =
            tokens.indices.firstOrNull { it > 0 && tokens[it].firstOrNull()?.isDigit() == true }
                ?: return null
        return tokens.take(versionAt).joinToString("-") to tokens.drop(versionAt).joinToString("-")
    }
}
