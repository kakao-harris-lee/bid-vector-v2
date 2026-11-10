package bidvector.buildlogic

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.jar.JarFile
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * **중첩 아카이브 축** — 배포물이 품는 아카이브가 그 모듈이 **선언한** 의존인가.
 *
 * `JarContentPolicyTest`(class 엔트리 판정)와 나눈 선은 크기가 아니라 **판정 대상**이다: 저쪽은
 * 아카이브 안의 class 바이트를, 이쪽은 아카이브 안의 **아카이브**를 본다. 정책 타입도 둘이다
 * ([JarContentPolicy] · [NestedArchivePolicy]).
 *
 * **무엇이 실리는지를 정하는 선언**(manifest·index)은 이 축이 보지 않는다 — [BootArchivePolicy] 가
 * 받는다. 그 선이 라운드 하나를 열었다(verifier H-1): 이 축은 뿌리를 전제로 쟀는데 그 뿌리를 정하는
 * 것이 manifest 한 줄이었다.
 *
 * 각 단언은 우회 하나를 고정한다. 마지막 셋은 **판독기 층까지** 잰다 — 진짜 아카이브를 하나 만들어
 * [readNestedArchive] 를 거쳐 판정한다(리뷰 M-1: 판정만 순수 test 로 잠그면 판독이 경로를 버리는
 * 변이가 아무 test 도 깨뜨리지 않는다).
 */
class NestedArchivePolicyTest {
    // ---- 중첩 아카이브 축: 선언 · 경로 · 다중도 ----

    @Test
    fun `선언하지 않은 중첩 jar 는 위반이다 — 런타임에 실리면서 판정되지 않던 자리`() {
        val violations =
            nestedPolicy(runtime = setOf("adapters.jar"))
                .violations(archive(nested("adapters.jar"), nested("sneak.jar")))

        assertEquals(1, violations.size, "$violations")
        assertTrue(violations.single().contains("sneak.jar"), "$violations")
        assertTrue(violations.single().contains("app.jar"), "어느 아카이브인지가 판정에 들어 있어야 한다")
    }

    @Test
    fun `runtime classpath 가 해석한 jar 만 품으면 위반이 없다`() {
        assertEquals(
            emptyList<String>(),
            nestedPolicy(runtime = setOf("adapters.jar", "workflow.jar", "kotlin-reflect-2.4.10.jar"))
                .violations(archive(nested("adapters.jar"), nested("workflow.jar"))),
        )
    }

    @Test
    fun `runtime 집합이 비면 중첩 jar 전부가 위반이다 — 기준 집합이 사라지면 조용해지지 않는다`() {
        // 수정 라운드 4 의 술어 교체에서 사라졌다가 되살린 단언이다(리뷰 LOW-4). 기준 집합을 공급하는
        // 배선(`runtimeClasspath`)이 끊기면 이 축은 **모두 통과**가 아니라 **모두 위반**이어야 한다.
        val violations =
            nestedPolicy().violations(archive(nested("adapters.jar"), nested("workflow.jar")))

        assertEquals(2, violations.size, "$violations")
        assertTrue(violations.all { it.contains("선언되지 않은") }, "$violations")
    }

    @Test
    fun `중첩 jar 가 없으면 위반이 없다 — 열 모듈 중 아홉의 정상 상태`() {
        // 뿌리 밑이 비면 면제·다중도·경로 축이 전부 쉰다.
        assertEquals(
            emptyList<String>(),
            nestedPolicy(runtime = setOf("a.jar"), exempt = listOf(EXEMPT))
                .violations(archive(ArchiveEntry("bidvector/App.class", archiveMagic = false))),
        )
    }

    @Test
    fun `같은 이름을 하위 디렉터리에 하나 더 넣으면 위반이다 — 경로 축`() {
        // verifier H-1 의 U1·U2: 원본을 그대로 두고 **더한다**. 로더는 뿌리 밑을 평탄하게 훑으므로
        // 둘 다 실리는데, 앞 판은 이름 **집합 소속**만 보아 둘째가 첫째의 선언에 업혀 통과했다.
        val violations =
            nestedPolicy(runtime = setOf("kotlin-reflect-2.4.10.jar"))
                .violations(
                    archive(
                        nested("kotlin-reflect-2.4.10.jar"),
                        nested("y/kotlin-reflect-2.4.10.jar"),
                    ),
                )

        assertTrue(violations.any { it.contains("하위 경로") && it.contains("y/kotlin-reflect") }, "$violations")
    }

    @Test
    fun `면제와 같은 정확 이름도 하위 디렉터리에 더하면 위반이다 — 면제는 자리까지 면제하지 않는다`() {
        val violations =
            nestedPolicy(exempt = listOf(EXEMPT)).violations(archive(nested(EXEMPT), nested("x/$EXEMPT")))

        assertTrue(violations.any { it.contains("하위 경로") }, "$violations")
        assertTrue(violations.any { it.contains("2번 품는다") }, "다중도 축도 같은 더하기를 받는다: $violations")
    }

    @Test
    fun `선언된 이름이라도 한 아카이브에 두 번이면 위반이다 — 다중도 축`() {
        // 해석된 runtime classpath 에 같은 이름은 한 번 나온다. 둘째부터는 누가 넣었는지가 없다.
        val violations =
            nestedPolicy(runtime = setOf("adapters.jar"))
                .violations(archive(nested("adapters.jar"), nested("adapters.jar")))

        assertEquals(1, violations.size, "$violations")
        assertTrue(violations.single().contains("2번 품는다"), "$violations")
    }

    @Test
    fun `아카이브마다 따로 센다 — 어디에 심었는지가 보여야 한다`() {
        val violations =
            nestedPolicy().violations(
                archive(nested("sneak.jar")),
                archive(nested("sneak.jar"), name = "app-plain.jar"),
            )

        assertEquals(2, violations.size, "$violations")
        assertTrue(violations.any { it.contains("app-plain.jar") }, "$violations")
    }

    @Test
    fun `판정 문면은 아카이브 이름만 든다 — 절대 경로는 기계마다 다른 글자다`() {
        val violations = nestedPolicy().violations(archive(nested("sneak.jar")))

        assertTrue(violations.single().contains("'app.jar'"), "$violations")
        assertTrue(!violations.single().contains(ARCHIVE_DIRECTORY), "$violations")
    }

    // ---- 중첩 아카이브 축: 면제 ----

    @Test
    fun `면제된 정확 이름은 위반이 아니다 — Boot 가 넣는 자기 도구 jar`() {
        // 축을 켜자마자 실측으로 나온 자리다: Boot 플러그인이 `spring-boot-jarmode-tools-<버전>.jar` 를
        // BOOT-INF/lib 에 넣는데 그 jar 는 모듈의 runtime classpath 에 없다.
        assertEquals(
            emptyList<String>(),
            nestedPolicy(exempt = listOf(EXEMPT)).violations(archive(nested(EXEMPT))),
        )
    }

    @Test
    fun `면제 이름을 사칭한 접두는 위반이다 — 접두 면제가 열어 두던 자리`() {
        // 앞 판은 접두 비교였고 `…-evil.jar` 가 통과했다(verifier H-2 실측). 이름은 **막으려는 쪽이
        // 정하는 값**이라 접두로는 닫히지 않는다.
        val violations =
            nestedPolicy(exempt = listOf(EXEMPT))
                .violations(archive(nested("spring-boot-jarmode-tools-evil.jar"), nested(EXEMPT)))

        assertEquals(1, violations.size, "$violations")
        assertTrue(violations.single().contains("evil"), "$violations")
    }

    @Test
    fun `면제가 다른 이름을 덮지 않는다`() {
        val violations =
            nestedPolicy(exempt = listOf(EXEMPT)).violations(archive(nested("sneak.jar"), nested(EXEMPT)))

        assertEquals(1, violations.size, "$violations")
        assertTrue(violations.single().contains("sneak.jar"), "$violations")
    }

    @Test
    fun `대상 없는 면제는 위반이다 — 도구 jar 를 빼면 그 이름의 자리가 빈 채로 남는다`() {
        // verifier L-1 의 U5: `bootJar { includeTools = false }` 로 도구 jar 를 빼고 면제는 그대로 둔
        // 상태. 그 상태에서는 누가 그 정확 이름으로 넣어도 통과한다.
        val violations =
            nestedPolicy(runtime = setOf("adapters.jar"), exempt = listOf(EXEMPT))
                .violations(archive(nested("adapters.jar")))

        assertEquals(1, violations.size, "$violations")
        assertTrue(violations.single().contains("면제된 이름이 **없다**"), "$violations")
    }

    @Test
    fun `확장자를 가리지 않는다 — zip 과 대문자 JAR 도 같은 판정을 받는다`() {
        // 로더는 `BOOT-INF/lib/` 접두만 보고 확장자를 보지 않는다. 앞 판 술어는 `.jar` 로 끝나는
        // 엔트리만 세어 이 둘이 **위반도 면제도 아닌 미계수**였다(verifier H-2).
        val violations = nestedPolicy().violations(archive(nested("sneak.zip"), nested("sneak2.JAR")))

        assertEquals(2, violations.size, "$violations")
    }

    // ---- 중첩 아카이브 축: 뿌리 밖과 런처 ----

    @Test
    fun `뿌리 밖의 중첩 아카이브는 zip 서명으로 잡는다 — 미계수가 아니다`() {
        // verifier M-1 의 U3: `PropertiesLauncher` + `Loader-Path: BOOT-INF/ext/` 가 싣는 자리다.
        // 앞 판은 모집단을 뿌리 접두로 좁혀 이 엔트리가 **위반도 면제도 아니었다**.
        val violations =
            nestedPolicy().violations(archive(ArchiveEntry("BOOT-INF/ext/sneak.jar", archiveMagic = true)))

        assertEquals(1, violations.size, "$violations")
        assertTrue(violations.single().contains("뿌리 **밖에**"), "$violations")
    }

    @Test
    fun `뿌리 밖의 보통 엔트리는 위반이 아니다 — 이름이 아니라 바이트를 본다`() {
        // `.jar` 로 **끝나는 이름의 class 리소스**는 아카이브가 아니다. 반대로 확장자 없는 zip 은
        // 아카이브다. 그래서 술어가 확장자가 아니라 서명이다.
        val violations =
            nestedPolicy().violations(
                archive(
                    ArchiveEntry("BOOT-INF/classes/bidvector/app/not-an-archive.jar", archiveMagic = false),
                    ArchiveEntry("BOOT-INF/ext/noextension", archiveMagic = true),
                ),
            )

        assertEquals(1, violations.size, "$violations")
        assertTrue(violations.single().contains("noextension"), "$violations")
    }

    @Test
    fun `뿌리 목록이 비면 생성에서 거부한다 — 비우면 모집단이 0 이라 축이 조용해진다`() {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                NestedArchivePolicy(emptyList(), emptySet(), emptyList())
            }

        assertTrue(failure.message!!.contains("뿌리 목록이 비었다"), "${failure.message}")
    }

    @Test
    fun `센 수가 리포트에 남는다 — 뿌리 안과 밖을 따로 센다`() {
        val verdict =
            nestedPolicy(runtime = setOf("adapters.jar")).judge(
                "app",
                listOf(archive(nested("adapters.jar"), ArchiveEntry("BOOT-INF/ext/x", archiveMagic = true))),
            )

        assertEquals(1, verdict.nestedCount)
        assertEquals(1, verdict.outsideCount)
    }

    // ---- 판독기 층(리뷰 M-1) — 진짜 아카이브 하나를 만들어 잰다 ----

    @Test
    fun `판독기가 엔트리 전체 경로와 zip 판독 여부를 낸다 — 경로를 버리는 변이가 여기서 붉는다`(
        @TempDir dir: File,
    ) {
        val outer = dir.resolve("outer.jar")
        writeArchive(
            outer,
            "BOOT-INF/lib/adapters.jar" to ArchiveFixtures.zipBytes(),
            "BOOT-INF/lib/y/adapters.jar" to ArchiveFixtures.zipBytes(),
            "BOOT-INF/classes/bidvector/App.class" to ArchiveFixtures.CLASS_MAGIC,
            "BOOT-INF/ext/sneak.zip" to ArchiveFixtures.zipBytes(),
        )

        val read = readNestedArchive(outer, emptySet())

        assertEquals("outer.jar", read.displayName)

        assertEquals(
            setOf("BOOT-INF/ext/sneak.zip", "BOOT-INF/lib/adapters.jar", "BOOT-INF/lib/y/adapters.jar"),
            read.archiveEntryPaths(),
            "class 리소스는 zip 이 아니고, 중첩 아카이브 셋은 자리와 무관하게 내용으로 보인다",
        )
    }

    @Test
    fun `앞에 바이트를 붙인 zip 도 아카이브로 읽힌다 — 포장으로 비껴가던 자리`(
        @TempDir dir: File,
    ) {
        // 앞 판은 **앞 네 바이트**의 서명을 보았고, 그래서 셸 머리글을 붙인 jar 가 뿌리 밖에서
        // 미계수였다(앞 판 제한 20 ⓐ — verifier H-1 이 그 포장을 재료로 썼다). zip 판독기는 끝의
        // 중앙 디렉터리 끝 레코드를 찾으므로 그 포장은 적재를 막지 않는다.
        val outer = dir.resolve("outer.jar")
        writeArchive(
            outer,
            "BOOT-INF/ext/evil.jar" to ArchiveFixtures.prefixedZipBytes(),
            "BOOT-INF/classes/bidvector/App.class" to ArchiveFixtures.CLASS_MAGIC,
            "BOOT-INF/classes/bidvector/notes.txt" to "PK\u0005\u0006 라고 적힌 평범한 글".toByteArray(),
        )

        val read = readNestedArchive(outer, emptySet())

        assertEquals(
            setOf("BOOT-INF/ext/evil.jar"),
            read.archiveEntryPaths(),
            "서명 글자를 품은 평범한 파일은 아카이브가 아니다 — 주석 길이와 중앙 디렉터리 자리가 맞아야 한다",
        )
        assertEquals(1, nestedPolicy().judge("app", listOf(read)).outsideCount)
    }

    @Test
    fun `zip 판독기가 여는 포장은 판독기도 본다 — 두 판정을 같은 바이트로 대조한다`(
        @TempDir dir: File,
    ) {
        // 리뷰 r5 HIGH 의 자리다. 앞 판은 **주석 길이 == 남은 바이트**를 요구했는데 JDK 의 zip
        // 판독기는 그 어긋남을 용인하고 되짚어 연다. 그래서 뒤에 1 바이트만 붙여도 게이트는
        // 「아카이브 아님」이고 로더는 그대로 열었다 — 뿌리 밖 판정이 그 한 바이트로 공허해진다.
        //
        // 단언이 **표가 아니라 함의**인 것이 요점이다: 어느 덧붙임에서 `JarFile` 이 열고 닫는지는
        // JDK 판에 달렸지만, 「여는 것은 모두 본다」는 그 판과 무관하게 성립해야 한다. 표로 적으면
        // JDK 가 바뀌는 날 **맞는 쪽으로도** 붉는다.
        val missed =
            SUFFIX_PADS.filter { pad ->
                val bytes = ArchiveFixtures.suffixedZipBytes(pad)
                val outer = dir.resolve("outer-$pad.jar")
                writeArchive(outer, "$EXT_ROOT$pad.jar" to bytes)

                opensAsZip(dir.resolve("standalone-$pad.jar").apply { writeBytes(bytes) }) &&
                    readNestedArchive(outer, emptySet()).archiveEntryPaths().isEmpty()
            }

        assertEquals(emptyList(), missed, "zip 판독기가 여는데 게이트가 못 보는 덧붙임 바이트 수")
    }

    @Test
    fun `대조가 공허하지 않다 — 여는 판과 열지 못하는 판이 둘 다 표본에 있다`(
        @TempDir dir: File,
    ) {
        // 위 함의는 **아무것도 열리지 않으면** 저절로 성립한다. 표본이 그렇게 되지 않았음을 따로 센다.
        val opened =
            SUFFIX_PADS.filter {
                opensAsZip(dir.resolve("p-$it.jar").apply { writeBytes(ArchiveFixtures.suffixedZipBytes(it)) })
            }

        assertTrue(opened.isNotEmpty(), "여는 판이 없으면 함의가 공허하다: $opened")
        assertTrue(opened.size < SUFFIX_PADS.size, "전부 열리면 창 밖을 재지 못한 표본이다: $opened")
    }

    @Test
    fun `창보다 큰 아카이브도 뿌리 밖에서 잡힌다 — 실제 중첩 jar 는 모두 이 경로를 탄다`(
        @TempDir dir: File,
    ) {
        // verifier r6 M-2: 끝 창을 넘는 엔트리를 「아카이브 아님」으로 만드는 변이가 test 두 클래스와
        // 실제 게이트를 모두 통과했다 — 이 저장소의 배포물에는 뿌리 밖 엔트리가 0 이라 실측이 그
        // 변이를 보지 못한다. 그 사각을 **창보다 큰 fixture 하나**로 닫는다.
        val big = ArchiveFixtures.largeZipBytes()
        assertTrue(big.size > EOCD_SCAN_WINDOW, "창(${EOCD_SCAN_WINDOW})보다 커야 이 경로를 탄다: ${big.size}")

        val outer = dir.resolve("outer.jar")
        writeArchive(outer, "${EXT_ROOT}big.jar" to big)

        val read = readNestedArchive(outer, emptySet())

        assertEquals(setOf("${EXT_ROOT}big.jar"), read.archiveEntryPaths())
        assertEquals(1, nestedPolicy().judge("app", listOf(read)).outsideCount)
    }

    @Test
    fun `판독한 아카이브를 그대로 판정하면 세 축이 모두 붉는다 — 판독기와 판정이 이어진다`(
        @TempDir dir: File,
    ) {
        val outer = dir.resolve("outer.jar")
        writeArchive(
            outer,
            "BOOT-INF/lib/adapters.jar" to ArchiveFixtures.zipBytes(),
            "BOOT-INF/lib/y/adapters.jar" to ArchiveFixtures.zipBytes(),
            "BOOT-INF/ext/sneak.zip" to ArchiveFixtures.zipBytes(),
        )

        val violations =
            nestedPolicy(runtime = setOf("adapters.jar"))
                .judge("app", listOf(readNestedArchive(outer, emptySet())))
                .violations

        assertEquals(3, violations.size, "$violations")
        assertTrue(violations.any { it.contains("하위 경로") }, "$violations")
        assertTrue(violations.any { it.contains("2번 품는다") }, "$violations")
        assertTrue(violations.any { it.contains("뿌리 **밖에**") }, "$violations")
    }

    // ---- 정책 데이터 자체 ----

    @Test
    fun `면제 이름의 버전이 카탈로그의 Spring Boot 버전과 같다 — 상향하면 이 단언이 붉는다`() {
        // 정확 이름 면제의 대가는 Boot 상향 때 어긋나는 것이고, 그 어긋남을 **여기서** 보이게 한다.
        // 정책만 고치고 카탈로그를 안 올리는(또는 그 반대의) 표류를 이 등식이 받는다.
        val catalogVersion = springBootVersionInCatalog()
        val exemptInPolicy = exemptNamesInPolicy()
        assertTrue(exemptInPolicy.isNotEmpty(), "면제 목록이 비었다 — 비-공허 단언")

        assertEquals(
            listOf("spring-boot-jarmode-tools-$catalogVersion.jar"),
            exemptInPolicy,
            "카탈로그 spring-boot=$catalogVersion",
        )
    }

    @Test
    fun `중첩 축의 뿌리가 정책에 있고 비어 있지 않다 — 비우면 축이 조용해진다`() {
        // 리뷰 M-2: 키를 **지우면** `requireValue` 가 죽지만 **비우면** 앞 판은 조용했다. 값을 읽는
        // 자리가 빈 목록을 거부하고, 그 거부가 실제 정책 파일에 대해 성립하는지를 여기서 잰다.
        val values = readPolicy(policyFile())

        assertEquals(listOf(ROOT), values.requireNonEmptyList("jar.nested-lib-roots"))
    }

    private fun nestedPolicy(
        runtime: Set<String> = emptySet(),
        exempt: List<String> = emptyList(),
    ) = NestedArchivePolicy(listOf(ROOT), runtime, exempt)

    private fun NestedArchivePolicy.violations(vararg archives: NestedArchive): List<String> =
        judge("app", archives.toList()).violations

    /** 뿌리 밑 엔트리. 진짜 아카이브라 서명도 참이다 — 판정은 뿌리 밑에서 서명을 묻지 않는다. */
    private fun nested(tail: String) = ArchiveEntry("$ROOT$tail", archiveMagic = true)

    private fun archive(
        vararg entries: ArchiveEntry,
        name: String = "app.jar",
    ) = NestedArchive(
        "$ARCHIVE_DIRECTORY/$name",
        name,
        entries.toList(),
        BootArchiveDescriptor(emptyMap(), emptyMap()),
    )

    private fun NestedArchive.archiveEntryPaths(): Set<String> =
        entries.filter(ArchiveEntry::archiveMagic).map(ArchiveEntry::path).toSet()

    private fun writeArchive(
        target: File,
        vararg entries: Pair<String, ByteArray>,
    ) = ArchiveFixtures.write(target, attributes = emptyMap(), entries = entries.toMap())

    /** 로더가 실제로 여는가 — 게이트 술어가 아니라 **읽는 쪽**에 묻는다. */
    private fun opensAsZip(file: File): Boolean =
        runCatching { JarFile(file).use { it.entries().hasMoreElements() } }.isSuccess

    private companion object {
        const val EXEMPT = "spring-boot-jarmode-tools-4.1.1.jar"
        const val ROOT = "BOOT-INF/lib/"
        const val EXT_ROOT = "BOOT-INF/ext/"
        const val ARCHIVE_DIRECTORY = "/build/libs"

        /**
         * 덧붙인 바이트 수의 표본 — 작은 쪽(리뷰 r5 가 든 +1·+16)과 **명목 창 경계의 양쪽**이다.
         * 경계 값들은 실측에서 갈린 자리라, 창을 명목으로 되돌리는 변이가 여기서 붉는다.
         */
        val SUFFIX_PADS = listOf(0, 1, 16, 1_024, 65_535, 65_536, 65_614, 70_000)

        fun policyFile(): File = File(System.getProperty("bidvector.architecture.policy"))

        fun exemptNamesInPolicy(): List<String> = readPolicy(policyFile()).requireList("jar.external-nested-jars")

        /**
         * 카탈로그는 TOML 이고 이 한 값은 `spring-boot = "x.y.z"` 한 줄이다. **`[versions]` 블록 안으로
         * 범위를 좁힌다**(리뷰 LOW-3) — `[plugins]` 의 `spring-boot = { id = … }` 도 같은 접두라, 이
         * 키가 `[versions]` 에서 사라지면 앞 판은 플러그인 id 를 집었다. 파일 자리는 좌표 세 겹이 아니라
         * `:build-logic:test` 가 넘기는 절대 경로다(그 파일은 이미 선언 입력이다).
         */
        fun springBootVersionInCatalog(): String {
            val lines = File(System.getProperty("bidvector.version.catalog")).readLines()
            val start = lines.indexOfFirst { it.trim() == "[versions]" }
            require(start >= 0) { "카탈로그에 [versions] 블록이 없다" }
            return lines
                .asSequence()
                .drop(start + 1)
                .takeWhile { !it.trimStart().startsWith("[") }
                .first { it.trimStart().startsWith("spring-boot ") }
                .substringAfter('"')
                .substringBefore('"')
        }
    }
}
