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
