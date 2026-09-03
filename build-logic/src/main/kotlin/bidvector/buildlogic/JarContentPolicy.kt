package bidvector.buildlogic

internal class JarClassEntry(
    val name: String,
    val digest: String,
    val sourceFile: String?,
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
 */
internal class JarContentPolicy(
    private val ownedPath: String,
    private val verifiedSourceNames: Set<String>,
) {
    fun violations(
        entries: List<JarClassEntry>,
        verified: Map<String, String>,
    ): List<String> = entries.mapNotNull { violation(it, verified) }.sorted()

    private fun violation(
        entry: JarClassEntry,
        verified: Map<String, String>,
    ): String? =
        when {
            !entry.name.startsWith("$ownedPath/") -> "아카이브에 소유 밖 클래스 — ${entry.name}"
            entry.name !in verified -> "게이트를 거치지 않은 클래스가 아카이브에 있다 — ${entry.name}"
            verified[entry.name] != entry.digest -> "게이트를 통과한 산출물과 내용이 다르다 — ${entry.name}"
            else -> originViolation(entry)
        }

    private fun originViolation(entry: JarClassEntry): String? =
        when (val source = entry.sourceFile) {
            null -> "'${entry.name}' 에 SourceFile 이 없다 — 게이트를 통과한 소스의 산출물이 아니다"
            in verifiedSourceNames -> null
            else -> "'${entry.name}' 의 SourceFile '$source' 이 게이트를 통과한 소스가 아니다"
        }
}
