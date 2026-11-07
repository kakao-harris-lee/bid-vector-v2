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
 * **중첩 아카이브 축** — 아카이브가 품는 `.jar` 엔트리는 그 모듈의 runtime classpath 가 해석한 jar 여야
 * 한다(`nested ⊆ runtime`, 이름 기준).
 *
 * 실측된 사각이다: 최상위 `.class` 엔트리만 보던 술어 아래에서 `bootJar { classpath(files(…)) }` 로 넣은
 * jar 가 `BOOT-INF/lib` 와 `classpath.idx` 에 들어가 **런타임에 실리는데도** 리포트가 심기 전과 같았다
 * (verifier M-1). 이 저장소가 막으려던 `jar { from("prebuilt") }` 의 bootJar 판이다.
 *
 * **모집단은 소비자와 같은 술어로 잡는다**(수정 라운드 3 — verifier H-2). Boot 로더는 디렉터리가 아닌
 * 엔트리에 대해 `BOOT-INF/lib/` 접두만 보고 **확장자를 보지 않는다**(로더 바이트코드 판독). 앞 판은
 * `.jar` 로 끝나는 엔트리만 세어 `.zip`·`.JAR` 로 넣은 중첩 아카이브가 **위반도 면제도 아닌 미계수**가
 * 됐다. 확장자는 넣는 쪽이 정하는 값이고 접두는 로더가 정하는 값이다.
 *
 * 왜 이름으로 견주나 — 중첩 아카이브는 다시 압축돼 있어 바이트 대조에 원본 파일이 필요하다. 반면
 * **선언하지 않은 것은 runtime classpath 해석 결과에 아예 없다**: 이름만으로도 「누가 선언했는가」가
 * 갈린다.
 *
 * 면제는 **정확 이름 집합** 하나다([exemptNames]) — Boot 플러그인이 넣는 자기 도구 jar 는 이 저장소의
 * 소스가 아니라 대조 집합이 없다(`jar.external-class-roots` 가 launcher 클래스를 면제하는 것과 같은
 * 사유). 앞 판은 접두였고, 그 이름이 **막으려는 쪽이 정하는 값**이라 접두 뒤를 바꾼 사칭이 통과했다
 * (실측). 정확 이름은 Boot 상향에 어긋나 붉는데, 그 붉음이 조용히 넓어지는 면제보다 낫다.
 *
 * **비-공허 단언은 두지 않는다** — 중첩 jar 0 은 열 모듈 가운데 아홉에서 **정상 상태**다(배포물이
 * `bootJar` 인 모듈이 하나뿐이다). 그래서 0 을 위반으로 만들면 아홉이 상시로 붉는다. 대신 리포트가
 * 중첩 수와 runtime 수를 함께 세어 그 축이 조용해지면 수로 보인다.
 */
internal fun nestedArchiveViolations(
    module: String,
    nested: Map<String, List<String>>,
    runtimeJarNames: Set<String>,
    exemptNames: List<String> = emptyList(),
): List<String> =
    nested.entries
        .sortedBy { it.key }
        .flatMap { (archive, jars) ->
            jars
                .filterNot { it in runtimeJarNames || it in exemptNames }
                .sorted()
                .map { jar ->
                    "모듈 '$module' 의 아카이브 '$archive' 가 **선언되지 않은 중첩 jar** 를 품는다 — " +
                        "runtime classpath 가 해석한 jar 가 아니므로 이 바이트는 어느 게이트도 거치지 않았다. " +
                        "그런데 배포물 안에 있어 런타임에 실린다: $jar"
                }
        }
