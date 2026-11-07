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
        // `present ∖ expected ∖ retired` — 앞 판은 한 방향(`expected ∖ present`)만 봤다. `expected` 가
        // `(1..max) ∖ retired` 라 **범위 밖**(실제로 열린 값은 1 미만)이 어느 가지에도 걸리지 않았다
        // (리뷰 M-1). V24 까지 적용된 DB 에 V0 을 넣으면 Flyway 가 조용히 건너뛴다.
        val outsideRange = (present - expected - retired).sorted()
        return unreadable.map { "파일 이름이 'V<번호>__<설명>.sql' 관례 밖이다 — 번호를 읽을 수 없으면 순서도 없다: $it" } +
            outsideRange.map {
                "version 이 등식의 범위 밖이다 — 적용된 최대 번호보다 낮은 자리로 들어오면 " +
                    "outOfOrder=false 아래에서 조용히 건너뛰어진다: V$it"
            } +
            duplicates(versions).map { "같은 version 을 가진 마이그레이션이 둘 이상이다 — 병합 순서가 갈렸다: V$it" } +
            (expected - present).sorted().map {
                "version 이 결번이다 — 이 번호로 나중에 들어온 마이그레이션은 outOfOrder=false 아래에서 조용히 건너뛰어진다: V$it"
            } +
            (retired intersect present).sorted().map { "은퇴한 version 을 다시 썼다 — 그 번호는 영구히 비워 둔 자리다: V$it" } +
            retired.filter { it < 1 || it > max }.sorted().map {
                "은퇴 목록에 대상이 없는 항목이 있다 — 죽은 면제는 범위를 조용히 넓힌다: V$it"
            }
    }

    /**
     * **마이그레이션은 정책이 고른 한 모듈만 갖는다** — Flyway 의 모집단은 모듈이 아니라 classpath 다
     * (`locations("classpath:db/migration")`). 비-소유 모듈에 둔 파일은 **적용되면서 재어지지 않는다**:
     * 앞 판은 모듈 이름이 다르면 디렉터리를 읽지도 않고 통과했고, 그래서 다른 모듈에 `V30` 을 두면
     * 결번 다섯이 생기는데도 초록이었다(실측 — verifier H-2 · 리뷰 M-2). 설계 검토가 막은 것은
     * 「옮기기」였고 「더하기」는 열려 있었다.
     */
    fun ownershipViolations(
        module: String,
        owner: String,
        fileNames: List<String>,
    ): List<String> =
        if (module == owner || fileNames.isEmpty()) {
            emptyList()
        } else {
            listOf(
                "모듈 '$module' 에 마이그레이션 ${fileNames.size}개가 있다 — 마이그레이션은 '$owner' 한 모듈만 갖는다. " +
                    "Flyway 의 모집단은 모듈이 아니라 classpath 이므로(locations=classpath:db/migration) " +
                    "이 파일들은 **적용되면서** 번호 등식에는 들어오지 않는다: ${fileNames.sorted().joinToString()}",
            )
        }

    /**
     * **비-공허 단언** — 소유 모듈의 디렉터리가 비면 위반이다. 「잴 것이 없어 통과」는 게이트를 옮기거나
     * 지우는 것과 구별되지 않는다(`jarContentGate` 가 빈 아카이브로 배운 것과 같은 축).
     */
    fun emptinessViolations(
        module: String,
        fileNames: List<String>,
    ): List<String> =
        if (fileNames.isNotEmpty()) {
            emptyList()
        } else {
            listOf(
                "마이그레이션을 가진 모듈 '$module' 의 디렉터리가 비어 있다 — 게이트가 아무것도 보지 못했다 " +
                    "(경로가 옮겨졌는지 'migration.resource-path' 를 본다)",
            )
        }

    private fun duplicates(versions: List<Int>): List<Int> =
        versions
            .groupBy { it }
            .filterValues { it.size > 1 }
            .keys
            .sorted()
}
