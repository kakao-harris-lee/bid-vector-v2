package bidvector.buildlogic

/**
 * 마이그레이션 번호의 **집합 등식** — `읽어낸 version 다중집합 == (1..max ∖ 은퇴)`
 * (`OPEN-MIGRATION-ORDER-GATE`).
 *
 * 왜 결번이 위반인가: Flyway `outOfOrder=false` 아래에서 적용된 최대 version 보다 낮은 번호로 나중에
 * 들어온 마이그레이션은 **조용히 적용되지 않는다**. 결번을 허용하면 그 번호가 「나중에 쓸 수 있는
 * 자리」로 남고, 쓰는 순간이 바로 그 침묵이다. 결번 0 은 그 자리를 아예 없앤다.
 *
 * 왜 산문 규율로는 모자랐나: 병합 순서 규율(`6f5a/scope.md` D-6F5-8)은 세 slice 가 연쇄로 기댔는데
 * 자동 게이트가 없어 **어긴 것이 보이지 않았다** — 실제로 한 번 뒤집혔다.
 *
 * **순수 함수다** — 파일 시스템도 Gradle 도 모른다. 판독은 파일 **이름**만 본다.
 */
internal object MigrationOrder {
    /** Flyway 관례 `V<번호>__<설명>.sql`. 점 버전·repeatable(`R__`)은 이 저장소의 관례가 아니라 판독 불가다. */
    private val VERSIONED_NAME = Regex("""^V(\d+)__[^/]+\.sql$""")

    fun versionOf(fileName: String): Int? =
        VERSIONED_NAME
            .matchEntire(fileName)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()

    /**
     * [fileNames] 는 마이그레이션 디렉터리의 **전수**이고 [retired] 는 「영구히 비워 둔 번호」다.
     *
     * 은퇴 목록은 결번을 **고정**한다 — 그 번호로 새 파일이 들어오면 위반이고(재사용 금지), 목록에만
     * 있고 범위 밖인 항목도 위반이다(OPS-13 원리 4 「죽은 allowlist 검출」 — 회수한 부채를 잠그지
     * 않으면 나중의 재악화를 무료로 허용한다).
     */
    fun violations(
        fileNames: List<String>,
        retired: Set<Int>,
    ): List<String> {
        val unreadable = fileNames.filter { versionOf(it) == null }.sorted()
        val versions = fileNames.mapNotNull(::versionOf)
        val present = versions.toSet()
        val max = present.maxOrNull() ?: 0
        val expected = (1..max).toSet() - retired
        return unreadable.map { "파일 이름이 'V<번호>__<설명>.sql' 관례 밖이다 — 번호를 읽을 수 없으면 순서도 없다: $it" } +
            duplicates(versions).map { "같은 version 을 가진 마이그레이션이 둘 이상이다 — 병합 순서가 갈렸다: V$it" } +
            (expected - present).sorted().map {
                "version 이 결번이다 — 이 번호로 나중에 들어온 마이그레이션은 outOfOrder=false 아래에서 조용히 건너뛰어진다: V$it"
            } +
            (retired intersect present).sorted().map { "은퇴한 version 을 다시 썼다 — 그 번호는 영구히 비워 둔 자리다: V$it" } +
            retired.filter { it < 1 || it > max }.sorted().map {
                "은퇴 목록에 대상이 없는 항목이 있다 — 죽은 면제는 범위를 조용히 넓힌다: V$it"
            }
    }

    private fun duplicates(versions: List<Int>): List<Int> =
        versions
            .groupBy { it }
            .filterValues { it.size > 1 }
            .keys
            .sorted()
}
