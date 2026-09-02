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
 * **클래스 파서를 들이지 않는 이유**: 이 층은 바이트 탐색만으로 서고 그것이 실제로 잡는 것이
 * 실측돼 있다(검증 집합에 심어 포함 관계를 통과시킨 class 를 이 층이 잡는다). 틀리는 방향도
 * 닫히는 쪽이다 — 그 문자열을 참조만 하는 Java 클래스는 통과하지만 포함 관계가 이미 잡는다.
 * (`importJar` 이 Java 산출 클래스를 빠뜨리는 것을 한 번 보았으나 **재현되지 않았다** — 그
 * 관측은 이 선택의 근거가 아니다. 알려진 제한 26.)
 */
internal fun ByteArray.hasKotlinMetadata(): Boolean {
    val marker = "kotlin/Metadata".toByteArray(Charsets.US_ASCII)
    if (size < marker.size) return false
    return (0..size - marker.size).any { start ->
        marker.indices.all { this[start + it] == marker[it] }
    }
}
