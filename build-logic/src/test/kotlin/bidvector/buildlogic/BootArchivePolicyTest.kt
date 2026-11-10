package bidvector.buildlogic

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * **배포 경로 축** — 출하 이미지를 만드는 도구가 읽는 자리(manifest 속성과 index 파일)가 정책 값인가.
 *
 * `NestedArchivePolicyTest` 와 나눈 선은 크기가 아니라 **판정 대상**이다: 저쪽은 아카이브가 품은
 * 엔트리를, 이쪽은 **무엇이 실리는지를 정하는 선언**을 본다. 중첩 축이 「뿌리 밑인가」로 재는 동안
 * 그 뿌리를 정하는 manifest 한 줄은 아무도 보지 않았고, 그 줄로 뿌리 밖 jar 가 출하 classpath 에
 * 실렸다(verifier H-1 실측).
 *
 * 각 단언은 그 우회 하나를 고정한다. 마지막 둘은 **판독기 층까지** 잰다 — 진짜 아카이브를 하나 만들어
 * [readNestedArchive] 를 거쳐 판정한다.
 */
class BootArchivePolicyTest {
    // ---- 속성 축 ----

    @Test
    fun `정책 값 그대로면 위반이 없다 — 통제군`() {
        assertEquals(emptyList<String>(), policy().violations(bootArchive()))
    }

    @Test
    fun `적재 뿌리를 넓히면 위반이다 — 출하 이미지의 Class-Path 를 정하는 한 줄`() {
        // verifier H-1 의 재현 ⓐ: `Spring-Boot-Lib: BOOT-INF/` 로 두면 추출 도구가 `BOOT-INF/` 밑
        // **어디든** 라이브러리로 받아들인다. Boot 플러그인은 이 속성을 `putIfAbsent` 로 넣으므로
        // 빌드 스크립트의 값이 이긴다 — 게이트를 끄지 않는 한 줄이다.
        val violations =
            policy().violations(
                bootArchive(attributes = attributes(LIB_LOCATION_ATTRIBUTE to "BOOT-INF/")),
            )

        assertTrue(violations.any { it.contains("'$LIB_LOCATION_ATTRIBUTE'") }, "$violations")
        assertTrue(violations.any { it.contains(LIB_ROOT) }, "기대 값이 문면에 있어야 한다: $violations")
    }

    @Test
    fun `풀린 뒤의 진입점을 바꾸면 위반이다 — Main-Class 단언은 그 경로를 받지 않는다`() {
        // 추출 도구는 `Main-Class` 를 **지우고** `Start-Class` 를 새 런처 manifest 의 `Main-Class` 로
        // 쓴다. 그래서 출하 이미지에서 실제로 뜨는 것은 이 속성이고, `Main-Class` 만 보던 앞 판 단언은
        // 그 경로에서 공허했다.
        val violations =
            policy().violations(
                bootArchive(
                    attributes =
                        attributes(
                            START_CLASS_ATTRIBUTE to "org.springframework.boot.loader.launch.PropertiesLauncher",
                        ),
                ),
            )

        assertEquals(1, violations.size, "$violations")
        assertTrue(violations.single().contains("'$START_CLASS_ATTRIBUTE'"), "$violations")
    }

    @Test
    fun `런처를 바꾸면 위반이다 — 풀지 않고 띄우는 경로의 적재 규칙이 바뀐다`() {
        // `PropertiesLauncher` 는 `Loader-Path` 의 중첩 경로를 싣는다(수정 라운드 4, verifier M-1).
        val violations =
            policy().violations(
                bootArchive(
                    attributes =
                        attributes(MAIN_CLASS_ATTRIBUTE to "org.springframework.boot.loader.launch.PropertiesLauncher"),
                ),
            )

        assertEquals(1, violations.size, "$violations")
        assertTrue(violations.single().contains("'$MAIN_CLASS_ATTRIBUTE'"), "$violations")
    }

    @Test
    fun `속성을 지우면 위반이다 — 없는 것은 「기본값」이 아니라 재지 못한 것이다`() {
        val violations =
            policy().violations(bootArchive(attributes = attributes() - CLASSES_LOCATION_ATTRIBUTE))

        assertEquals(1, violations.size, "$violations")
        assertTrue(violations.single().contains("없다"), "$violations")
    }

    @Test
    fun `index 자리를 옮기면 위반이 둘이다 — 선언이 어긋나고 기대 자리가 빈다`() {
        // 선언만 옮기고 기대 자리의 파일을 그대로 두는 변이에서 둘이 서로를 가리지 않아야 한다.
        val violations =
            policy().violations(
                bootArchive(
                    attributes = attributes(CLASSPATH_INDEX_ATTRIBUTE to "BOOT-INF/other.idx"),
                    indexes = mapOf(LAYERS_INDEX to emptyList()),
                ),
            )

        assertEquals(2, violations.size, "$violations")
        assertTrue(violations.any { it.contains("'$CLASSPATH_INDEX_ATTRIBUTE'") }, "$violations")
        assertTrue(violations.any { it.contains("classpath index '$CLASSPATH_INDEX' 가 없다") }, "$violations")
    }

    @Test
    fun `layers index 가 없으면 위반이다 — 출하 명령이 그 파일로 층을 가른다`() {
        val violations =
            policy().violations(bootArchive(indexes = mapOf(CLASSPATH_INDEX to listOf(DECLARED))))

        assertEquals(1, violations.size, "$violations")
        assertTrue(violations.single().contains("layers index"), "$violations")
    }

    // ---- index 등식 축 ----

    @Test
    fun `index 에 뿌리 밖 줄을 더하면 위반이다 — 그 줄이 그대로 Class-Path 가 된다`() {
        // verifier H-1 의 재현 ⓑ: 엔트리를 `BOOT-INF/ext/` 에 넣고 index 에 한 줄 더하면, 추출 도구가
        // 그 줄을 런처 jar 의 `Class-Path` 로 옮긴다. 중첩 축은 그 바이트를 뿌리 밖으로만 세고
        // (미계수였던 포장이면 아예 못 보고), 적재 여부는 이 등식이 받는다.
        val violations =
            policy().violations(
                bootArchive(
                    entries = listOf(DECLARED, "BOOT-INF/ext/evil.jar"),
                    indexes = indexes(listOf(DECLARED, "BOOT-INF/ext/evil.jar")),
                ),
            )

        assertEquals(1, violations.size, "$violations")
        assertTrue(violations.single().contains("BOOT-INF/ext/evil.jar"), "$violations")
        assertTrue(violations.single().contains("Class-Path"), "$violations")
    }

    @Test
    fun `index 에 하위 경로 줄을 더해도 위반이다 — 뿌리 밑이어도 바로 밑이 아니다`() {
        val violations =
            policy().violations(
                bootArchive(
                    entries = listOf(DECLARED, "${LIB_ROOT}x/evil.jar"),
                    indexes = indexes(listOf(DECLARED, "${LIB_ROOT}x/evil.jar")),
                ),
            )

        assertEquals(1, violations.size, "$violations")
        assertTrue(violations.single().contains("x/evil.jar"), "$violations")
    }

    @Test
    fun `뿌리 밑 엔트리가 index 에 없으면 위반이다 — 두 집합이 갈라진다`() {
        // 반대 방향이다. 중첩 축이 재는 집합(엔트리)과 실제로 실리는 집합(index)이 어긋나면, 어느
        // 한쪽만 보는 판정은 그 차이를 영영 보지 못한다.
        val violations = policy().violations(bootArchive(indexes = indexes(emptyList())))

        assertEquals(1, violations.size, "$violations")
        assertTrue(violations.single().contains(DECLARED), "$violations")
    }

    @Test
    fun `면제한 이름은 index 에 없어도 된다 — Boot 가 넣는 자기 도구 jar`() {
        // 실측: 도구 jar 는 `BOOT-INF/lib/` 에 있지만 `classpath.idx` 에는 없다(102 엔트리 · 101 줄).
        assertEquals(
            emptyList<String>(),
            policy().violations(
                bootArchive(
                    entries = listOf(DECLARED, "$LIB_ROOT$EXEMPT"),
                    indexes = indexes(listOf(DECLARED)),
                ),
            ),
        )
    }

    // ---- 모집단 ----

    @Test
    fun `배포 아카이브가 아니면 축이 쉰다 — 아홉 모듈의 평범한 jar`() {
        val plain =
            NestedArchive(
                path = "/build/libs/adapters.jar",
                displayName = "adapters.jar",
                entries = listOf(ArchiveEntry("bidvector/adapters/X.class", archiveMagic = false)),
                descriptor = BootArchiveDescriptor(emptyMap(), emptyMap()),
            )

        assertEquals(emptyList<String>(), policy().judge("adapters", listOf(plain)).violations)
        assertEquals(0, policy().judge("adapters", listOf(plain)).bootArchiveCount)
    }

    @Test
    fun `소유 모듈에 배포 아카이브가 없으면 위반이다 — bootJar 한 줄이 모집단을 0 으로 만든다`() {
        // 리뷰 M-3: `bootJar { enabled = false }` 면 `app` 의 아카이브는 평범한 jar 하나뿐이고, 중첩
        // 축·배포 경로 축의 수가 **아홉 모듈의 정상 상태와 같은 모양**으로 0 이 된다.
        val plain =
            NestedArchive(
                path = "/build/libs/app-plain.jar",
                displayName = "app-plain.jar",
                entries = listOf(ArchiveEntry("BOOT-INF/classes/bidvector/app/X.class", archiveMagic = false)),
                descriptor = BootArchiveDescriptor(emptyMap(), emptyMap()),
            )

        val violations = policy().judge(OWNER, listOf(plain)).violations

        assertEquals(1, violations.size, "$violations")
        assertTrue(violations.single().contains("배포물을 내는 모듈"), "$violations")
    }

    @Test
    fun `비-소유 모듈은 그 바닥값을 받지 않는다 — 배포물이 없는 것이 정상이다`() {
        assertEquals(emptyList<String>(), policy().judge("workflow", emptyList()).violations)
    }

    // ---- 판독기 층 — 진짜 아카이브 하나를 만들어 잰다 ----

    @Test
    fun `판독기가 manifest 속성 여섯과 index 줄을 낸다`(
        @TempDir dir: File,
    ) {
        val outer = dir.resolve("app.jar")
        ArchiveFixtures.write(
            outer,
            attributes = attributes(),
            entries =
                mapOf(
                    DECLARED to ArchiveFixtures.zipBytes(),
                    CLASSPATH_INDEX to ArchiveFixtures.indexBytes(listOf(DECLARED)),
                    LAYERS_INDEX to ArchiveFixtures.indexBytes(listOf(DECLARED)),
                ),
        )

        val read = readNestedArchive(outer, setOf(CLASSPATH_INDEX, LAYERS_INDEX))

        assertEquals(attributes(), read.descriptor.attributes)
        assertEquals(listOf(DECLARED), read.descriptor.indexes[CLASSPATH_INDEX])
        assertEquals(emptyList<String>(), policy().judge(OWNER, listOf(read)).violations)
    }

    @Test
    fun `모양이 깨진 index 줄을 버리지 않는다 — 버리면 선언과 엔트리의 어긋남이 조용해진다`(
        @TempDir dir: File,
    ) {
        // 리뷰 r5 M-1. 판독기 KDoc 이 「떼어지지 않는 줄은 그대로 둔다」고 적는데 그 성질이 어느
        // test 에도 걸려 있지 않았다 — 줄을 조용히 거르는 `mapNotNull` 변이가 초록이었다.
        //
        // 왜 버리면 안 되는가: 추출 도구는 줄 모양을 **묻지 않고** 자리로 떼어 간다. 그러니 게이트가
        // 읽지 못한 줄은 「없는 줄」이 아니라 **게이트의 index 모델이 도구와 갈라진 자리**다. 버리면
        // 그 갈라짐이 판정에서 사라지고, 남겨 두면 뿌리 밑 엔트리가 아닌 줄로 지목된다.
        val outer = dir.resolve("app.jar")
        val malformed = "BOOT-INF/ext/evil.jar"
        ArchiveFixtures.write(
            outer,
            attributes = attributes(),
            entries =
                mapOf(
                    DECLARED to ArchiveFixtures.zipBytes(),
                    CLASSPATH_INDEX to ArchiveFixtures.indexBytes(listOf(DECLARED)) + "$malformed\n".toByteArray(),
                    LAYERS_INDEX to ArchiveFixtures.indexBytes(listOf(DECLARED)),
                ),
        )

        val read = readNestedArchive(outer, INDEX_LOCATIONS)

        assertEquals(listOf(DECLARED, malformed), read.descriptor.indexes[CLASSPATH_INDEX])

        val violations = policy().judge(OWNER, listOf(read)).violations
        assertEquals(1, violations.size, "$violations")
        assertTrue(violations.single().contains(malformed), "$violations")
    }

    @Test
    fun `판독한 아카이브를 그대로 판정하면 재현이 붉는다 — 적재 뿌리와 index 를 함께 바꾼 판`(
        @TempDir dir: File,
    ) {
        // verifier H-1 의 재현 그대로: 뿌리를 넓히고, 뿌리 밖에 **앞에 바이트를 붙인** jar 를 넣고,
        // index 두 파일에 한 줄씩 더한다. 셋 모두 게이트를 끄지 않는 변경이다.
        val outer = dir.resolve("app.jar")
        val planted = "BOOT-INF/ext/evil.jar"
        ArchiveFixtures.write(
            outer,
            attributes = attributes(LIB_LOCATION_ATTRIBUTE to "BOOT-INF/"),
            entries =
                mapOf(
                    DECLARED to ArchiveFixtures.zipBytes(),
                    planted to ArchiveFixtures.prefixedZipBytes(),
                    CLASSPATH_INDEX to ArchiveFixtures.indexBytes(listOf(DECLARED, planted)),
                    LAYERS_INDEX to ArchiveFixtures.indexBytes(listOf(DECLARED, planted)),
                ),
        )

        val violations = policy().judge(OWNER, listOf(readNestedArchive(outer, INDEX_LOCATIONS))).violations

        assertEquals(2, violations.size, "$violations")
        assertTrue(violations.any { it.contains("'$LIB_LOCATION_ATTRIBUTE'") }, "$violations")
        assertTrue(violations.any { it.contains(planted) && it.contains("Class-Path") }, "$violations")
    }

    // ---- 정책 데이터 자체 ----

    @Test
    fun `배포 경로의 네 값과 소유 모듈이 정책에 있다 — 비우면 축이 조용해진다`() {
        val values = readPolicy(policyFile())

        assertEquals(LAUNCHER, values.requireValue("jar.boot-launcher-main-class"))
        assertEquals(START_CLASS, values.requireValue("jar.boot-start-class"))
        assertEquals(CLASSPATH_INDEX, values.requireValue("jar.boot-classpath-index"))
        assertEquals(LAYERS_INDEX, values.requireValue("jar.boot-layers-index"))
        assertEquals(OWNER, values.requireValue("jar.boot-archive-module"))
    }

    @Test
    fun `적재 자리 둘은 복사하지 않고 뿌리 목록에서 온다 — 복사하면 갈라지는 날 전제를 잃는다`() {
        // `Spring-Boot-Lib`·`Spring-Boot-Classes` 의 기대 값은 **다른 키의 값**이다. 그 등식이
        // 깨지면(목록이 둘이 되거나 비면) 값을 읽는 자리가 거부한다.
        val values = readPolicy(policyFile())

        assertEquals(LIB_ROOT, values.requireSingle("jar.nested-lib-roots"))
        assertEquals(CLASSES_ROOT, values.requireSingle("jar.class-roots"))
    }

    @Test
    fun `소유 모듈 값이 settings 의 include 목록에 실재한다 — 개명하면 이 축이 조용히 쉰다`() {
        // 리뷰 r5 M-2: `jar.boot-archive-module` 은 **모듈 이름 글자**다. 모듈을 개명하면 그 값은
        // 어느 모듈에도 걸리지 않고, 「배포물을 내는 모듈인데 배포 아카이브가 없다」는 바닥값이
        // 영영 돌지 않는다 — 축이 죽은 것이 리포트에서 **아홉 모듈의 정상 상태와 같은 모양**이다.
        val owner = readPolicy(policyFile()).requireValue("jar.boot-archive-module")
        val included = includedModules()

        assertTrue(included.isNotEmpty(), "include 목록을 읽지 못했다 — 비-공허 단언")
        assertTrue(owner in included, "소유 모듈 '$owner' 이 include 목록에 없다: $included")
    }

    @Test
    fun `적재 자리 둘은 디렉터리 접두다 — 끝의 구분자가 빠지면 이웃 이름이 뿌리 밑으로 샌다`() {
        // 리뷰 r5 LOW: `BOOT-INF/lib` 로 적으면 `BOOT-INF/libextra.jar` 가 접두 비교를 통과해 **뿌리
        // 바로 밑 엔트리**로 세어진다. 정책 오타 하나가 모집단을 바꾸는 자리라 값을 읽는 쪽이 거부한다.
        val failure =
            assertFailsWith<IllegalArgumentException> {
                BootArchiveExpectation(
                    LAUNCHER,
                    START_CLASS,
                    CLASSES_ROOT,
                    "BOOT-INF/lib",
                    CLASSPATH_INDEX,
                    LAYERS_INDEX,
                )
            }

        assertTrue(failure.message!!.contains("'/' 로 끝나야"), "${failure.message}")
    }

    private fun policy() = BootArchivePolicy(expectation(), listOf(EXEMPT), OWNER)

    private fun BootArchivePolicy.violations(archive: NestedArchive): List<String> =
        judge(OWNER, listOf(archive)).violations

    private fun bootArchive(
        attributes: Map<String, String> = attributes(),
        entries: List<String> = listOf(DECLARED),
        indexes: Map<String, List<String>> = indexes(listOf(DECLARED)),
    ) = NestedArchive(
        path = "/build/libs/app.jar",
        displayName = "app.jar",
        entries = entries.map { ArchiveEntry(it, archiveMagic = true) },
        descriptor = BootArchiveDescriptor(attributes, indexes),
    )

    private fun indexes(lines: List<String>) = mapOf(CLASSPATH_INDEX to lines, LAYERS_INDEX to lines)

    private companion object {
        const val OWNER = "app"
        const val LIB_ROOT = "BOOT-INF/lib/"
        const val CLASSES_ROOT = "BOOT-INF/classes/"
        const val CLASSPATH_INDEX = "BOOT-INF/classpath.idx"
        const val LAYERS_INDEX = "BOOT-INF/layers.idx"
        const val LAUNCHER = "org.springframework.boot.loader.launch.JarLauncher"
        const val START_CLASS = "bidvector.app.BidVectorApplicationKt"
        const val EXEMPT = "spring-boot-jarmode-tools-4.1.1.jar"
        const val DECLARED = "${LIB_ROOT}adapters.jar"

        val INDEX_LOCATIONS = setOf(CLASSPATH_INDEX, LAYERS_INDEX)

        fun expectation() =
            BootArchiveExpectation(LAUNCHER, START_CLASS, CLASSES_ROOT, LIB_ROOT, CLASSPATH_INDEX, LAYERS_INDEX)

        /** 정책 값 그대로의 여섯. [overrides] 로 한 자리만 바꿔 **그 한 변경**이 재는 것을 분리한다. */
        fun attributes(vararg overrides: Pair<String, String>): Map<String, String> =
            expectation().attributes + overrides

        fun policyFile(): File = File(System.getProperty("bidvector.architecture.policy"))

        /**
         * `settings.gradle.kts` 의 `include(...)` 블록이 드는 모듈 이름.
         *
         * `includeBuild(...)` 를 함께 집지 않도록 **여는 줄이 정확히 `include(`** 인 블록만 본다 —
         * 접두로 맞추면 included build 둘이 모듈로 세어진다. 블록을 못 찾으면 빈 목록이고, 그 빈
         * 목록은 부르는 쪽의 비-공허 단언이 받는다(조용한 통과가 되지 않게).
         */
        fun includedModules(): List<String> {
            val lines = File(System.getProperty("bidvector.settings.file")).readLines()
            val start = lines.indexOfFirst { it.trim() == "include(" }
            if (start < 0) return emptyList()
            return lines
                .asSequence()
                .drop(start + 1)
                .takeWhile { it.trim() != ")" }
                .mapNotNull { line ->
                    line.trim().removeSuffix(",").takeIf { it.startsWith("\"") && it.endsWith("\"") }
                }.map { it.removeSurrounding("\"") }
                .toList()
        }
    }
}
