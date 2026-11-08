package bidvector.buildlogic

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 아카이브 판정. **패키지 접두를 맞춘 침입이 통과하지 않는다**는 것이 이 판정의 요점이라,
 * 각 단언은 「이름은 맞는데 그 산출물이 아닌」 형태를 하나씩 고정한다.
 *
 * 원산지 층은 **`SourceFile` 이 게이트를 통과한 소스 이름 집합에 드는가**다 — 애노테이션은
 * 소스에 한 줄로 붙어 위조되므로 앵커가 되지 못한다.
 */
class JarContentPolicyTest {
    private val policy =
        JarContentPolicy(
            "bidvector/settlement",
            setOf("Anchor.kt"),
            classRoots = listOf("BOOT-INF/classes/"),
            externalClassRoots = listOf("org/springframework/boot/loader/"),
        )
    private val verified = mapOf("bidvector/settlement/Anchor.class" to "hash-a")

    private fun entry(
        name: String,
        digest: String = "hash-a",
        sourceFile: String? = "Anchor.kt",
    ) = JarClassEntry(name, digest, sourceFile)

    private fun JarContentPolicy.violations(
        entries: List<JarClassEntry>,
        verified: Map<String, String>,
    ) = judge(entries, verified).violations

    @Test
    fun `게이트를 통과한 그 바이트면 위반이 없다`() {
        assertEquals(emptyList(), policy.violations(listOf(entry("bidvector/settlement/Anchor.class")), verified))
    }

    @Test
    fun `소유 패키지에 이름을 맞춰도 검증 집합에 없으면 잡는다`() {
        val violations = policy.violations(listOf(entry("bidvector/settlement/Sneak.class")), verified)
        assertTrue(violations.single().contains("게이트를 거치지 않은"), violations.single())
    }

    @Test
    fun `같은 이름이라도 내용이 다르면 잡는다`() {
        val violations =
            policy.violations(listOf(entry("bidvector/settlement/Anchor.class", digest = "hash-b")), verified)
        assertTrue(violations.single().contains("내용이 다르다"), violations.single())
    }

    @Test
    fun `포함 관계를 통과해도 SourceFile 이 게이트 밖이면 잡는다`() {
        val violations =
            policy.violations(listOf(entry("bidvector/settlement/Anchor.class", sourceFile = "Sneak.java")), verified)
        assertTrue(violations.single().contains("Sneak.java"), violations.single())
    }

    @Test
    fun `SourceFile 이 아예 없으면 잡는다`() {
        val violations =
            policy.violations(listOf(entry("bidvector/settlement/Anchor.class", sourceFile = null)), verified)
        assertTrue(violations.single().contains("SourceFile 이 없다"), violations.single())
    }

    @Test
    fun `소유 밖 클래스는 그 사유로 잡는다`() {
        val violations = policy.violations(listOf(entry("evil/Sneak.class")), verified)
        assertTrue(violations.single().contains("소유 밖"), violations.single())
    }

    // ---- 배포물 축(`OPEN-JAR-CONTENT-GATE-BOOTJAR-BLINDSPOT`) ----

    @Test
    fun `bootJar 접두 밑의 그 바이트는 위반이 없다 — 접두를 벗기고 판정한다`() {
        val entries = listOf(entry("BOOT-INF/classes/bidvector/settlement/Anchor.class"))

        assertEquals(emptyList(), policy.violations(entries, verified))
    }

    @Test
    fun `bootJar 접두 밑에 심은 비-검증 클래스는 잡는다 — 접두가 판정을 느슨하게 하지 않는다`() {
        val entries = listOf(entry("BOOT-INF/classes/bidvector/settlement/Sneak.class"))

        assertTrue(policy.violations(entries, verified).single().contains("게이트를 거치지 않은"))
    }

    @Test
    fun `bootJar 접두 밑의 소유 밖 클래스도 잡는다`() {
        val entries = listOf(entry("BOOT-INF/classes/evil/Sneak.class"))

        assertTrue(policy.violations(entries, verified).single().contains("소유 밖"))
    }

    @Test
    fun `launcher 접두는 면제하되 수를 센다 — 그 바이트는 이 저장소의 소스가 아니다`() {
        val verdict =
            policy.judge(
                listOf(
                    entry("org/springframework/boot/loader/launch/JarLauncher.class", sourceFile = null),
                    entry("BOOT-INF/classes/bidvector/settlement/Anchor.class"),
                ),
                verified,
            )

        assertEquals(emptyList(), verdict.violations)
        assertEquals(1, verdict.exempt)
        assertEquals(1, verdict.judged, "면제는 판정 수에 들지 않는다 — 공허 판정을 공허로 세기 위해")
    }

    @Test
    fun `면제 접두 밖의 비-소유 클래스는 면제가 있어도 그대로 위반이다`() {
        val entries = listOf(entry("org/evil/Sneak.class"))

        assertTrue(policy.violations(entries, verified).single().contains("소유 밖"))
    }

    @Test
    fun `면제만 남은 아카이브는 판정 수가 0 이다 — 비-공허 단언의 입력`() {
        val verdict =
            policy.judge(
                listOf(entry("org/springframework/boot/loader/launch/JarLauncher.class", sourceFile = null)),
                verified,
            )

        assertEquals(0, verdict.judged)
        assertEquals(emptyList(), verdict.violations)
    }

    @Test
    fun `아카이브 입력이 없으면 비-공허 단언이 붉는다`() {
        val failure = archiveEmptinessFailure("app", archiveCount = 0, judged = 0)

        assertTrue(failure != null && failure.contains("app"), "$failure")
    }

    @Test
    fun `판정할 class 엔트리가 0 이면 비-공허 단언이 붉는다 — 면제만 남은 아카이브`() {
        val failure = archiveEmptinessFailure("app", archiveCount = 2, judged = 0)

        assertTrue(failure != null, "면제 엔트리만 남은 아카이브도 같은 공허다")
    }

    @Test
    fun `아카이브와 판정 엔트리가 있으면 비-공허 단언은 조용하다`() {
        assertEquals(null, archiveEmptinessFailure("app", archiveCount = 2, judged = 558))
    }

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
    fun `중첩 jar 가 없으면 위반이 없다 — 열 모듈 중 아홉의 정상 상태`() {
        // 뿌리 밑이 비면 런처·면제·다중도 축이 전부 쉰다. 그 아홉 모듈은 Main-Class 도 없다.
        assertEquals(
            emptyList<String>(),
            nestedPolicy(runtime = setOf("a.jar"), exempt = listOf(EXEMPT))
                .violations(archive(mainClass = null, entries = arrayOf(ArchiveEntry("bidvector/App.class", false)))),
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
    fun `런처를 바꾸면 위반이다 — 뿌리 접두 술어의 전제가 그 런처다`() {
        val violations =
            nestedPolicy(runtime = setOf("adapters.jar"), exempt = emptyList())
                .violations(
                    archive(nested("adapters.jar"), mainClass = "org.springframework.boot.loader.launch.PropertiesLauncher"),
                )

        assertEquals(1, violations.size, "$violations")
        assertTrue(violations.single().contains("Main-Class"), "$violations")
    }

    @Test
    fun `뿌리 목록이 비면 생성에서 거부한다 — 비우면 모집단이 0 이라 축이 조용해진다`() {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                NestedArchivePolicy(emptyList(), emptySet(), emptyList(), LAUNCHER)
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
    fun `판독기가 엔트리 전체 경로와 zip 서명을 낸다 — 경로를 버리는 변이가 여기서 붉는다`(
        @TempDir dir: File,
    ) {
        val outer = dir.resolve("outer.jar")
        writeArchive(
            outer,
            mainClass = LAUNCHER,
            entries =
                mapOf(
                    "BOOT-INF/lib/adapters.jar" to zipBytes(),
                    "BOOT-INF/lib/y/adapters.jar" to zipBytes(),
                    "BOOT-INF/classes/bidvector/App.class" to byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte()),
                    "BOOT-INF/ext/sneak.zip" to zipBytes(),
                ),
        )

        val read = readNestedArchive(outer)

        assertEquals(LAUNCHER, read.mainClass)
        assertEquals("outer.jar", read.displayName)
        assertEquals(
            setOf("BOOT-INF/ext/sneak.zip", "BOOT-INF/lib/adapters.jar", "BOOT-INF/lib/y/adapters.jar"),
            read.entries.filter(ArchiveEntry::archiveMagic).map(ArchiveEntry::path).toSet(),
            "class 리소스는 zip 이 아니고, 중첩 아카이브 셋은 자리와 무관하게 서명으로 보인다",
        )
    }

    @Test
    fun `판독한 아카이브를 그대로 판정하면 세 축이 모두 붉는다 — 판독기와 판정이 이어진다`(
        @TempDir dir: File,
    ) {
        val outer = dir.resolve("outer.jar")
        writeArchive(
            outer,
            mainClass = LAUNCHER,
            entries =
                mapOf(
                    "BOOT-INF/lib/adapters.jar" to zipBytes(),
                    "BOOT-INF/lib/y/adapters.jar" to zipBytes(),
                    "BOOT-INF/ext/sneak.zip" to zipBytes(),
                ),
        )

        val violations =
            nestedPolicy(runtime = setOf("adapters.jar")).judge("app", listOf(readNestedArchive(outer))).violations

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
    fun `중첩 축의 뿌리와 런처 값이 정책에 있고 비어 있지 않다 — 비우면 축이 조용해진다`() {
        // 리뷰 M-2: 키를 **지우면** `requireValue` 가 죽지만 **비우면** 앞 판은 조용했다. 값을 읽는
        // 자리가 빈 목록을 거부하고, 그 거부가 실제 정책 파일에 대해 성립하는지를 여기서 잰다.
        val values = readPolicy(policyFile())

        assertEquals(listOf("BOOT-INF/lib/"), values.requireNonEmptyList("jar.nested-lib-roots"))
        assertEquals(LAUNCHER, values.requireValue("jar.boot-launcher-main-class"))
    }

    private fun nestedPolicy(
        runtime: Set<String> = emptySet(),
        exempt: List<String> = emptyList(),
    ) = NestedArchivePolicy(listOf(ROOT), runtime, exempt, LAUNCHER)

    private fun NestedArchivePolicy.violations(vararg archives: NestedArchive): List<String> =
        judge("app", archives.toList()).violations

    /** 뿌리 밑 엔트리. 진짜 아카이브라 서명도 참이다 — 판정은 뿌리 밑에서 서명을 묻지 않는다. */
    private fun nested(tail: String) = ArchiveEntry("$ROOT$tail", archiveMagic = true)

    private fun archive(
        vararg entries: ArchiveEntry,
        name: String = "app.jar",
        mainClass: String? = LAUNCHER,
    ) = NestedArchive("$ARCHIVE_DIRECTORY/$name", name, entries.toList(), mainClass)

    private companion object {
        const val EXEMPT = "spring-boot-jarmode-tools-4.1.1.jar"
        const val ROOT = "BOOT-INF/lib/"
        const val LAUNCHER = "org.springframework.boot.loader.launch.JarLauncher"
        const val ARCHIVE_DIRECTORY = "/build/libs"

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

        /** zip 서명으로 시작하는 진짜 아카이브 한 채. 이름이 아니라 이 바이트가 판정 입력이다. */
        fun zipBytes(): ByteArray =
            ByteArrayOutputStream()
                .also { out ->
                    JarOutputStream(out).use {
                        it.putNextEntry(ZipEntry("inner.txt"))
                        it.write("inner".toByteArray())
                        it.closeEntry()
                    }
                }.toByteArray()

        fun writeArchive(
            target: File,
            mainClass: String?,
            entries: Map<String, ByteArray>,
        ) {
            val manifest =
                Manifest().apply {
                    mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
                    mainClass?.let { mainAttributes[Attributes.Name.MAIN_CLASS] = it }
                }
            JarOutputStream(target.outputStream(), manifest).use { out ->
                entries.forEach { (name, bytes) ->
                    out.putNextEntry(ZipEntry(name))
                    out.write(bytes)
                    out.closeEntry()
                }
            }
        }
    }
}
