package bidvector.buildlogic

internal class JarClassEntry(
    val name: String,
    val digest: String,
    val kotlinOrigin: Boolean,
)

/**
 * 아카이브의 class 엔트리가 **게이트를 통과한 산출물 그 바이트인지** 판정한다.
 *
 * 판정이 포함 관계인 것이 요점이다 — 패키지 접두만 보면 넣는 쪽이 이름을 맞출 수 있어
 * `bidvector/<module>/Sneak.class` 가 통과했다(Codex 7차). 경로와 내용 해시가 모두 일치해야
 * 「그 산출물」이고, 그렇지 않으면 어떤 경로로 들어왔든 위반이다.
 */
internal class JarContentPolicy(
    private val ownedPath: String,
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
            !entry.kotlinOrigin -> "'${entry.name}' 에 kotlin.Metadata 가 없다 — Kotlin 이 만든 산출물이 아니다"
            else -> null
        }
}

/**
 * 상수 풀의 `Lkotlin/Metadata;` 를 바이트로 찾는다 — Kotlin 컴파일러는 모든 산출 클래스에
 * 그 애노테이션을 단다.
 *
 * **ArchUnit 으로 재지 않는 이유**: 이 데몬 안에서 `importJar` 이 Java 산출 클래스를 **조용히
 * 건너뛰는 것**을 실측했다(같은 jar 를 독립 JVM 에서 읽으면 둘 다 나온다). 잡아야 할 바로 그
 * 클래스를 빠뜨리는 검사는 없는 것만 못하다. 바이트 탐색은 그 침묵이 없고 틀리는 방향도
 * 닫히는 쪽이다 — 그 문자열을 참조만 하는 Java 클래스는 통과하지만 포함 관계가 이미 잡는다.
 */
internal fun ByteArray.hasKotlinMetadata(): Boolean {
    val marker = "kotlin/Metadata".toByteArray(Charsets.US_ASCII)
    if (size < marker.size) return false
    return (0..size - marker.size).any { start ->
        marker.indices.all { this[start + it] == marker[it] }
    }
}
