package bidvector.buildlogic

internal class JarClassEntry(
    val name: String,
    val digest: String,
    val sourceFile: String?,
)

/** 한 모듈의 아카이브 전수 판정 — [violations] 와, 대조 집합이 없어 **면제한** 엔트리 수. */
internal data class JarContentVerdict(
    val violations: List<String>,
    val judged: Int,
    val exempt: Int,
)

/**
 * 아카이브의 class 엔트리가 **게이트를 통과한 산출물 그 바이트인지** 판정한다.
 *
 * 판정이 포함 관계인 것이 요점이다 — 패키지 접두만 보면 넣는 쪽이 이름을 맞출 수 있어
 * `bidvector/<module>/Sneak.class` 로 이름만 맞추면 통과한다. 경로와 내용 해시가 모두 일치해야
 * 「그 산출물」이고, 그렇지 않으면 어떤 경로로 들어왔든 위반이다.
 *
 * **원산지 층은 `SourceFile` 이 게이트를 통과한 소스 이름 집합에 드는가로 잰다.** 애노테이션은
 * 소스에 한 줄로 붙어 위조되므로 앵커가 되지 못한다.
 *
 * **[classRoots] 와 [externalClassRoots] 가 배포물 사각을 닫는 자리다**
 * (`OPEN-JAR-CONTENT-GATE-BOOTJAR-BLINDSPOT`). 배포되는 아카이브는 `bootJar` 이고 그 안의 모듈
 * 클래스는 `BOOT-INF/classes/` 아래 산다 — 접두를 벗기지 않으면 소유 판정이 전부 붉어 게이트를 끄는
 * 쪽으로 몰린다. 접두를 **벗기고 그 밑의 경로로** 판정하므로 판정이 느슨해지지 않는다.
 * [externalClassRoots] 는 그 아카이브가 품는 **다른 출처의** 클래스(Boot launcher)다 — 이 저장소의
 * 소스가 아니라 대조 집합이 없다. 면제는 **접두 집합 정책 데이터**이고 그 밖의 비-소유 엔트리는 그대로
 * 위반이다. 면제 수는 리포트에 센다(그 접두 밑은 내용 판정이 없다 — 알려진 한계).
 */
internal class JarContentPolicy(
    private val ownedPath: String,
    private val verifiedSourceNames: Set<String>,
    private val classRoots: List<String> = emptyList(),
    private val externalClassRoots: List<String> = emptyList(),
) {
    fun judge(
        entries: List<JarClassEntry>,
        verified: Map<String, String>,
    ): JarContentVerdict {
        val (exempt, judged) = entries.partition { entry -> externalClassRoots.any { entry.name.startsWith(it) } }
        return JarContentVerdict(
            violations = judged.mapNotNull { violation(it, verified) }.sorted(),
            judged = judged.size,
            exempt = exempt.size,
        )
    }

    private fun violation(
        entry: JarClassEntry,
        verified: Map<String, String>,
    ): String? {
        val path = entry.name.stripClassRoot()
        return when {
            !path.startsWith("$ownedPath/") -> "아카이브에 소유 밖 클래스 — ${entry.name}"
            path !in verified -> "게이트를 거치지 않은 클래스가 아카이브에 있다 — ${entry.name}"
            verified[path] != entry.digest -> "게이트를 통과한 산출물과 내용이 다르다 — ${entry.name}"
            else -> originViolation(entry)
        }
    }

    /** 가장 긴 접두를 벗긴다 — 접두가 서로의 접두면 짧은 쪽이 먼저 맞아 경로가 반만 벗겨질 수 있다. */
    private fun String.stripClassRoot(): String =
        classRoots
            .filter { startsWith(it) }
            .maxByOrNull { it.length }
            ?.let { removePrefix(it) }
            ?: this

    private fun originViolation(entry: JarClassEntry): String? =
        when (val source = entry.sourceFile) {
            null -> "'${entry.name}' 에 SourceFile 이 없다 — 게이트를 통과한 소스의 산출물이 아니다"
            in verifiedSourceNames -> null
            else -> "'${entry.name}' 의 SourceFile '$source' 이 게이트를 통과한 소스가 아니다"
        }
}

/**
 * **비-공허 단언**(`OPEN-JAR-CONTENT-GATE-BOOTJAR-BLINDSPOT` 의 사각 둘째) — 빈 입력은 통과가 아니다.
 *
 * 실측된 사각이다: `jar` 를 끄면 게이트가 **실행됐는데도** 엔트리 0 으로 exit 0 이었다(두 verifier 레인이
 * 각각 쟀다). 면제 엔트리만 남은 아카이브도 같은 공허라 **판정한 수**로 센다.
 *
 * task 가 아니라 이 자리에 있는 것은 **회귀 test 를 걸 수 있어야** 하기 때문이다 — 앞 판은 task 의
 * private 함수라 그 단언을 무력화하는 변이가 어떤 test 도 깨뜨리지 않았다(실측 — verifier M-2).
 */
internal fun archiveEmptinessFailure(
    module: String,
    archiveCount: Int,
    judged: Int,
): String? =
    when {
        archiveCount == 0 -> {
            "모듈 '$module' 의 아카이브 입력이 없다 — 게이트가 아무것도 보지 못했다(Jar task 가 꺼졌는가)"
        }

        judged == 0 -> {
            "모듈 '$module' 의 아카이브에 판정할 class 엔트리가 없다 — 빈 입력은 통과가 아니다"
        }

        else -> {
            null
        }
    }

/**
 * 아카이브 한 엔트리의 판정 입력 — **엔트리 전체 경로**와, 그 바이트가 **zip 으로 읽히는가**.
 *
 * 뒤의 것이 이름이 아니라 내용인 이유: 확장자는 넣는 쪽이 정하는 값이라 `.zip`·`.JAR`·무확장으로
 * 비껴간다(실측).
 *
 * **앞 네 바이트가 아니라 끝의 중앙 디렉터리 끝 레코드(EOCD)로 판독한다**(수정 라운드 5). zip 판독기는
 * 끝에서 EOCD 를 찾으므로 **앞에 아무 바이트나 붙인 jar 도 그대로 읽힌다** — 앞 판의 서명 네 바이트
 * 술어는 그 포장 하나로 열려 있었고(앞 판 제한 20 ⓐ 가 스스로 적은 자리), 뿌리 밖에서 미계수였다.
 * EOCD 는 **읽는 쪽이 찾는 자리**라 포장으로 옮길 수 없다.
 */
internal data class ArchiveEntry(
    val path: String,
    val archiveMagic: Boolean,
)

/**
 * 중첩 축과 배포 경로 축이 함께 보는 아카이브 한 채.
 *
 * [path] 가 키다 — 서로 다른 디렉터리의 Jar task 가 같은 `archiveFileName` 을 내면 이름 키에서 하나가
 * 조용히 사라진다. [displayName] 만 판정 문면에 들어간다 — 절대 경로는 기계마다 다른 글자이고, 그
 * 문면을 인용하면 낡는 좌표가 된다(리뷰 LOW-2).
 *
 * [descriptor] 는 **출하 경로의 도구가 읽는 자리**다([BootArchivePolicy]). 엔트리 목록만으로는 그
 * 도구가 무엇을 싣는지 알 수 없다 — 싣는 집합의 정본은 manifest 와 index 파일이고, 이 축의 뿌리
 * 술어는 그 정본이 정책 값일 때에만 배포 경로의 전제가 된다.
 */
internal data class NestedArchive(
    val path: String,
    val displayName: String,
    val entries: List<ArchiveEntry>,
    val descriptor: BootArchiveDescriptor,
)

/** 중첩 축의 판정과 그 축이 **실제로 센 수** — 수가 0 으로 내려가면 리포트에서 보인다. */
internal data class NestedArchiveVerdict(
    val violations: List<String>,
    val nestedCount: Int,
    val outsideCount: Int,
)

/**
 * **중첩 아카이브 축** — 배포물 안에 들어간 아카이브가 그 모듈이 **선언한** 의존인지 본다.
 *
 * 실측된 사각이다: 최상위 `.class` 엔트리만 보던 술어 아래에서 `bootJar { classpath(files(…)) }` 로 넣은
 * jar 가 `BOOT-INF/lib` 와 `classpath.idx` 에 들어가 **런타임에 실리는데도** 리포트가 심기 전과 같았다
 * (verifier M-1). 이 저장소가 막으려던 `jar { from("prebuilt") }` 의 bootJar 판이다.
 *
 * 왜 바이트가 아니라 이름으로 견주나 — 중첩 아카이브는 다시 압축돼 있어 바이트 대조에 원본 파일이
 * 필요하다. 반면 **선언하지 않은 것은 runtime classpath 해석 결과에 아예 없다**. 다만 이름만으로는
 * 모자랐다(수정 라운드 4 — verifier H-1): 이름 **집합 소속**만 보면 같은 이름의 둘째 아카이브를 하위
 * 디렉터리에 더하는 것으로 통과했고, 로더는 그 둘을 **모두** 싣는다. 그래서 축이 셋이다.
 *
 * - **경로 축**: 뿌리 밑의 엔트리는 전체 경로가 정확히 `<뿌리><이름>` 이어야 한다. 하위 디렉터리는 위반.
 * - **다중도 축**: 한 아카이브에서 같은 이름은 **정확히 한 번**. 「선언된 이름이 둘」은 더하기 우회다.
 * - **선언 축**: 그 이름이 runtime classpath 해석 결과 또는 면제 집합에 있어야 한다.
 *
 * 뿌리 밖은 **미계수가 아니다**(수정 라운드 4 — verifier M-1, 리뷰 H-1). 앞 판은 모집단을 뿌리 접두로
 * 좁히면서 뿌리 밖의 중첩 아카이브를 「위반도 면제도 아닌」 자리에 두었고, `PropertiesLauncher` +
 * `Loader-Path` 조합이 그 자리를 실어 날랐다. 지금은 뿌리 밖의 엔트리도 **중앙 디렉터리 끝 레코드**로
 * 전수 판정한다([ArchiveEntry.archiveMagic]). 적재 자리를 정하는 manifest·index 자체는
 * [BootArchivePolicy] 가 받는다 — 그 축이 닫혀야 이 축의 「뿌리」가 배포 경로의 전제가 된다.
 *
 * 면제는 **정확 이름 집합** 하나다([exemptNames]) — Boot 플러그인이 넣는 자기 도구 jar 는 이 저장소의
 * 소스가 아니라 대조 집합이 없다(`jar.external-class-roots` 가 launcher 클래스를 면제하는 것과 같은
 * 사유). 앞 판은 접두였고, 그 이름이 **막으려는 쪽이 정하는 값**이라 접두 뒤를 바꾼 사칭이 통과했다
 * (실측). 정확 이름은 Boot 상향에 어긋나 붉는데, 그 붉음이 조용히 넓어지는 면제보다 낫다. 그리고
 * 면제한 이름이 **대상 없이 남으면** 그것도 위반이다 — 죽은 면제는 그 이름의 자리를 비워 둔다.
 *
 * **비-공허 단언은 두지 않는다** — 중첩 0 은 열 모듈 가운데 아홉에서 **정상 상태**다(배포물이 `bootJar`
 * 인 모듈이 하나뿐이다). 그래서 0 을 위반으로 만들면 아홉이 상시로 붉는다. 대신 리포트가 중첩 수와
 * runtime 수를 함께 세어 그 축이 조용해지면 수로 보인다. 뿌리 목록 자체는 비면 **거부한다**(init) —
 * 그것은 「정상 0」이 아니라 모집단을 통째로 없애는 값이다(리뷰 M-2).
 */
internal class NestedArchivePolicy(
    private val roots: List<String>,
    private val runtimeJarNames: Set<String>,
    private val exemptNames: List<String>,
) {
    init {
        require(roots.isNotEmpty()) {
            "중첩 축의 뿌리 목록이 비었다 — 빈 목록은 모집단을 0 으로 만들어 그 축을 **경고 없이** 끈다"
        }
    }

    fun judge(
        module: String,
        archives: List<NestedArchive>,
    ): NestedArchiveVerdict {
        val judged = archives.sortedBy { it.path }.map(::split)
        return NestedArchiveVerdict(
            violations = judged.flatMap { violations(module, it) },
            nestedCount = judged.sumOf { it.under.size },
            outsideCount = judged.sumOf { it.outside.size },
        )
    }

    private fun split(archive: NestedArchive): SplitArchive =
        SplitArchive(
            archive = archive,
            under = archive.entries.mapNotNull { entry -> rootOf(entry)?.let { NestedEntry(entry.path, it) } },
            outside = archive.entries.filter { it.archiveMagic && rootOf(it) == null }.map(ArchiveEntry::path),
        )

    /**
     * **가장 긴 접두를 고른다** — [JarContentPolicy.stripClassRoot] 와 같은 규칙이다(리뷰 LOW-1).
     * 뿌리가 서로의 접두면(`BOOT-INF/` 와 `BOOT-INF/lib/`) 선언 순서가 「바로 밑인가」의 답을 바꾸므로,
     * 목록의 순서가 판정에 들어가지 않게 한다.
     */
    private fun rootOf(entry: ArchiveEntry): String? =
        roots.filter { entry.path.startsWith(it) }.maxByOrNull(String::length)

    private fun violations(
        module: String,
        split: SplitArchive,
    ): List<String> {
        val where = "모듈 '$module' 의 아카이브 '${split.archive.displayName}'"
        val nested =
            if (split.under.isEmpty()) {
                emptyList()
            } else {
                pathViolations(where, split.under) +
                    multiplicityViolations(where, split.under) +
                    undeclaredViolations(where, split.under) +
                    deadExemptViolations(where, split.under)
            }
        return nested + split.outside.sorted().map { outsideViolation(where, it) }
    }

    /** **경로 축** — 뿌리 **바로 밑**이 아닌 자리. 로더는 그것도 싣지만 이름 비교는 둘을 구별하지 못한다. */
    private fun pathViolations(
        where: String,
        under: List<NestedEntry>,
    ): List<String> =
        under
            .filterNot(NestedEntry::directlyUnderRoot)
            .map(NestedEntry::path)
            .sorted()
            .map {
                "$where 가 로더 뿌리의 **하위 경로**에 중첩 아카이브를 품는다 — 로더는 뿌리 밑을 평탄하게 " +
                    "훑어 이 엔트리도 classpath 에 싣는데, 이름만 견주면 같은 이름의 선언된 jar 가 이것을 " +
                    "덮어 준다(그 둘이 **모두** 실린다): $it"
            }

    /** **다중도 축** — 같은 이름이 두 번이면 하나는 선언 밖이다. 「더하기」 우회는 이 축이 받는다. */
    private fun multiplicityViolations(
        where: String,
        under: List<NestedEntry>,
    ): List<String> =
        under
            .groupingBy(NestedEntry::name)
            .eachCount()
            .filterValues { it > 1 }
            .keys
            .sorted()
            .map {
                "$where 가 같은 이름의 중첩 아카이브를 ${under.count { e -> e.name == it }}번 품는다 — " +
                    "선언된 이름은 해석 결과에 **한 번** 나오므로 둘째부터는 누가 넣었는지가 없다: $it"
            }

    private fun undeclaredViolations(
        where: String,
        under: List<NestedEntry>,
    ): List<String> =
        under
            .map(NestedEntry::name)
            .distinct()
            .filterNot { it in runtimeJarNames || it in exemptNames }
            .sorted()
            .map {
                "$where 가 **선언되지 않은 중첩 아카이브** 를 품는다 — runtime classpath 가 해석한 것이 " +
                    "아니므로 이 바이트는 어느 게이트도 거치지 않았다. 그런데 배포물 안에 있어 런타임에 " +
                    "실린다: $it"
            }

    /** 죽은 면제는 범위를 조용히 넓힌다 — 그 이름의 자리를 비워 두기 때문이다(verifier L-1). */
    private fun deadExemptViolations(
        where: String,
        under: List<NestedEntry>,
    ): List<String> =
        exemptNames
            .filterNot { name -> under.any { it.name == name } }
            .sorted()
            .map {
                "$where 에 면제된 이름이 **없다** — 대상 없는 면제는 그 이름의 자리를 비워 두어, 누가 그 " +
                    "이름으로 넣어도 통과시킨다: $it"
            }

    private fun outsideViolation(
        where: String,
        path: String,
    ): String =
        "$where 가 로더 뿌리 **밖에** 중첩 아카이브를 품는다(중앙 디렉터리 끝 레코드로 판독) — 지금의 " +
            "적재 자리 선언으로는 실리지 않지만 그 바이트는 어느 게이트도 거치지 않았고, 선언 한 줄이면 " +
            "실린다: $path"

    private class SplitArchive(
        val archive: NestedArchive,
        val under: List<NestedEntry>,
        val outside: List<String>,
    )

    private class NestedEntry(
        val path: String,
        root: String,
    ) {
        private val tail = path.removePrefix(root)

        val name: String = tail.substringAfterLast('/')

        val directlyUnderRoot: Boolean = !tail.contains('/')
    }
}
