package bidvector.buildlogic

/**
 * 배포 아카이브의 **서술자** — 출하 경로의 도구가 실제로 읽는 manifest 속성과, 그 속성이 가리킨
 * index 파일의 줄.
 *
 * [indexes] 의 키는 **아카이브 안의 엔트리 경로**다. 판독기는 manifest 가 선언한 자리와 정책이
 * 기대하는 자리를 **둘 다** 읽어 넣는다 — 선언을 다른 자리로 돌려 놓고 기대 자리의 파일을 그대로
 * 두는 변이에서, 「선언이 틀렸다」와 「기대 자리가 비었다」가 서로를 가리지 않게 하기 위해서다.
 */
internal data class BootArchiveDescriptor(
    val attributes: Map<String, String>,
    val indexes: Map<String, List<String>>,
)

internal const val MAIN_CLASS_ATTRIBUTE = "Main-Class"
internal const val START_CLASS_ATTRIBUTE = "Start-Class"
internal const val CLASSES_LOCATION_ATTRIBUTE = "Spring-Boot-Classes"
internal const val LIB_LOCATION_ATTRIBUTE = "Spring-Boot-Lib"
internal const val CLASSPATH_INDEX_ATTRIBUTE = "Spring-Boot-Classpath-Index"
internal const val LAYERS_INDEX_ATTRIBUTE = "Spring-Boot-Layers-Index"

/**
 * 배포 아카이브가 선언해야 하는 **정확 값** 표.
 *
 * 이름 다섯(`Start-Class`·`Spring-Boot-Classes`·`Spring-Boot-Lib`·`Spring-Boot-Classpath-Index`·
 * `Spring-Boot-Layers-Index`)은 **임의로 고른 것이 아니라** 추출 도구가 자기 manifest 처리에서
 * 들어내는 목록 그대로다(Boot 4.1.1 `spring-boot-jarmode-tools` 의 `IndexedJarStructure` —
 * 바이트코드 판독으로 전수 확인). 즉 「읽는 속성」과 「단언하는 속성」이 같은 집합이다.
 * `Main-Class` 는 그 밖의 여섯째다 — 풀지 않고 `java -jar` 로 띄우는 경로의 런처를 정한다.
 */
internal class BootArchiveExpectation(
    launcherMainClass: String,
    startClass: String,
    val classesLocation: String,
    val libLocation: String,
    val classpathIndex: String,
    val layersIndex: String,
) {
    val attributes: Map<String, String> =
        linkedMapOf(
            MAIN_CLASS_ATTRIBUTE to launcherMainClass,
            START_CLASS_ATTRIBUTE to startClass,
            CLASSES_LOCATION_ATTRIBUTE to classesLocation,
            LIB_LOCATION_ATTRIBUTE to libLocation,
            CLASSPATH_INDEX_ATTRIBUTE to classpathIndex,
            LAYERS_INDEX_ATTRIBUTE to layersIndex,
        )

    /** 판독기가 **선언과 무관하게** 함께 읽어 와야 하는 자리. */
    val indexLocations: Set<String> = setOf(classpathIndex, layersIndex)
}

/** 배포 경로 축의 판정과 그 축이 **배포 아카이브로 센 수** — 0 으로 내려가면 소유 모듈에서 붉는다. */
internal data class BootArchiveVerdict(
    val violations: List<String>,
    val bootArchiveCount: Int,
)

/**
 * **배포 경로 축** — 출하 이미지를 만드는 도구가 읽는 자리를 정책 값으로 못 박는다.
 *
 * 실측된 사각이다(verifier H-1, 수정 라운드 5): 중첩 축은 「뿌리 밑인가 · 선언됐는가」를 `JarLauncher`
 * 의 적재 규칙으로 쟀는데, **출하 이미지는 그 런처로 뜨지 않는다**. `docker/app.Dockerfile` 은
 * `java -Djarmode=tools … extract --layers` 로 아카이브를 풀고 평범한 `java -jar` 로 띄운다. 그
 * 추출 도구는 manifest 의 [LIB_LOCATION_ATTRIBUTE] 로 「라이브러리가 사는 자리」를 정하고,
 * [CLASSPATH_INDEX_ATTRIBUTE] 가 가리킨 index 파일의 **줄 목록 그대로** 런처 jar 의 `Class-Path` 를
 * 만든다. 그래서 manifest 한 줄(`Spring-Boot-Lib: BOOT-INF/`)과 index 두 줄이면 뿌리 **밖**의 jar 가
 * 출하 이미지의 classpath 에 실렸다 — 게이트는 `Main-Class` 만 보아 초록이었다(실측).
 *
 * 축은 둘이다.
 *
 * - **속성 축**: 여섯 속성이 [BootArchiveExpectation] 의 정확 값이어야 한다. 「적재 자리를 정하는
 *   값」을 아카이브가 스스로 정하지 못하게 한다.
 * - **index 축**: `Class-Path` 가 되는 줄 집합이 **뿌리 바로 밑 엔트리 집합 ∖ 면제 이름** 과 같아야
 *   한다. 양방향이다 — 줄이 더 있으면 게이트를 거치지 않은 바이트가 실리고, 줄이 모자라면 index 가
 *   실제로 싣는 집합과 중첩 축이 재는 집합이 갈라진다.
 *
 * 이 둘이 닫히면 중첩 축의 뿌리 술어가 **배포 경로에서도** 전제다: 추출 도구는 index 에 없고 클래스
 * 뿌리 밑도 아닌 엔트리를 아예 복사하지 않고(`IndexedJarStructure.resolve` 가 `null` 을 낸다),
 * index 의 모든 줄은 [LIB_LOCATION_ATTRIBUTE] 접두로 시작해야 한다(도구 자신의 단언).
 *
 * **소유 모듈 단언**(리뷰 M-3): `bootJar { enabled = false }` 한 줄이면 이 축의 모집단이 0 이 되고,
 * 0 은 배포물이 없는 아홉 모듈의 정상 상태와 리포트가 같다. 그래서 배포물을 내는 모듈을 정책이
 * 지목하고, 그 모듈에서는 **뿌리 밑에 엔트리가 있는 배포 아카이브가 하나 이상** 있어야 한다.
 */
internal class BootArchivePolicy(
    private val expectation: BootArchiveExpectation,
    private val exemptNames: List<String>,
    private val ownerModule: String,
) {
    fun judge(
        module: String,
        archives: List<NestedArchive>,
    ): BootArchiveVerdict {
        val boot = archives.sortedBy(NestedArchive::path).filter(::isBootArchive)
        return BootArchiveVerdict(
            violations = boot.flatMap { violations(module, it) } + ownerViolations(module, boot),
            bootArchiveCount = boot.size,
        )
    }

    /**
     * 배포 아카이브인가 — **스스로 신고한 속성** 또는 **뿌리 밑 엔트리**. 둘 중 하나라도 있으면 축이
     * 돈다. 신고를 지우고 의존을 다른 자리로 옮기는 변이는 중첩 축의 「뿌리 밖」이 받는다.
     *
     * `Main-Class` 는 신고로 세지 않는다 — 평범한 실행 가능 jar 도 그 속성을 갖는다.
     */
    private fun isBootArchive(archive: NestedArchive): Boolean =
        archive.descriptor.attributes.keys
            .any { it != MAIN_CLASS_ATTRIBUTE && it in expectation.attributes } ||
            libEntries(archive).isNotEmpty()

    private fun violations(
        module: String,
        archive: NestedArchive,
    ): List<String> {
        val where = "모듈 '$module' 의 배포 아카이브 '${archive.displayName}'"
        return attributeViolations(where, archive.descriptor.attributes) + indexViolations(where, archive)
    }

    private fun attributeViolations(
        where: String,
        declared: Map<String, String>,
    ): List<String> =
        expectation.attributes.mapNotNull { (name, expected) ->
            when (val actual = declared[name]) {
                expected -> {
                    null
                }

                null -> {
                    "$where 의 manifest 에 '$name' 이 없다 — 출하 경로의 추출 도구가 **그 속성으로** " +
                        "적재 자리를 정한다. 기대 값: $expected"
                }

                else -> {
                    "$where 의 manifest '$name' 이 정책 값이 아니다 — 이 속성이 출하 이미지의 " +
                        "classpath 를 정한다. 기대 '$expected' · 실제 '$actual'"
                }
            }
        }

    private fun indexViolations(
        where: String,
        archive: NestedArchive,
    ): List<String> = classpathIndexViolations(where, archive) + layersIndexViolations(where, archive)

    /**
     * **`Class-Path` 등식** — index 의 줄 집합 == 뿌리 바로 밑 엔트리 ∖ 면제 이름.
     *
     * 추출 도구는 이 줄 목록을 그대로 런처 jar 의 `Class-Path` 로 옮긴다. 그래서 「실제로 실리는
     * 집합」의 정본이 이 파일이고, 중첩 축이 재는 집합과 같아야 한다.
     */
    private fun classpathIndexViolations(
        where: String,
        archive: NestedArchive,
    ): List<String> {
        val lines =
            archive.descriptor.indexes[expectation.classpathIndex]
                ?: return listOf(
                    "$where 에 classpath index '${expectation.classpathIndex}' 가 없다 — 추출 도구는 이 " +
                        "파일의 줄로 `Class-Path` 를 만든다. 없으면 실리는 집합을 잴 수 없다",
                )
        val expected = libEntries(archive).filterNot { it.substringAfterLast('/') in exemptNames }.toSet()
        val extra = (lines.toSet() - expected).sorted()
        val missing = (expected - lines.toSet()).sorted()
        return extra.map {
            "$where 의 classpath index 가 **뿌리 바로 밑 엔트리가 아닌 줄**을 싣는다 — 이 줄은 그대로 " +
                "출하 이미지의 `Class-Path` 가 되는데 중첩 축은 그 바이트를 재지 않았다: $it"
        } +
            missing.map {
                "$where 의 뿌리 밑 엔트리가 classpath index 에 **없다** — 중첩 축이 재는 집합과 실제로 " +
                    "실리는 집합이 갈라졌다: $it"
            }
    }

    private fun layersIndexViolations(
        where: String,
        archive: NestedArchive,
    ): List<String> =
        if (expectation.layersIndex in archive.descriptor.indexes) {
            emptyList()
        } else {
            listOf(
                "$where 에 layers index '${expectation.layersIndex}' 가 없다 — `extract --layers` 가 이 " +
                    "파일로 층을 가르고, 없으면 출하 이미지를 만드는 명령 자체가 다른 경로를 탄다",
            )
        }

    private fun ownerViolations(
        module: String,
        boot: List<NestedArchive>,
    ): List<String> =
        if (module != ownerModule || boot.any { libEntries(it).isNotEmpty() }) {
            emptyList()
        } else {
            listOf(
                "모듈 '$module' 은 배포물을 내는 모듈인데 뿌리 밑에 엔트리가 있는 배포 아카이브가 " +
                    "**없다** — `bootJar` 를 끄면 중첩 축과 이 축의 모집단이 함께 0 이 되고, 그 0 은 " +
                    "배포물이 없는 다른 모듈의 정상 상태와 리포트가 같다",
            )
        }

    /**
     * 뿌리 **바로 밑** 엔트리의 전체 경로. 하위 디렉터리는 중첩 축의 경로 술어가 따로 받고, 여기서는
     * index 등식의 기준 집합이라 「평탄한 한 겹」만 센다.
     */
    private fun libEntries(archive: NestedArchive): List<String> =
        archive.entries
            .map(ArchiveEntry::path)
            .filter { it.startsWith(expectation.libLocation) }
            .filterNot { it == expectation.libLocation }
            .filterNot { it.removePrefix(expectation.libLocation).contains('/') }
}
