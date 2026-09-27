package bidvector.app.collection

import java.nio.file.Files
import java.nio.file.Path

/**
 * 저장소 루트의 표식 — **둘 다** 본다. 계약(D-6G-43)은 `settings.gradle.kts` 를 적었고, `.git` 은
 * 그 빌드 파일이 없는 배치(소스 export, 다른 빌드 도구)에서도 경계를 세운다. 어느 하나라도 있는
 * 첫 조상이 루트다 — 표식을 좁게 잡으면 경계가 사라져 저장소 안이 「밖」으로 통과한다.
 */
private val ROOT_MARKERS = listOf("settings.gradle.kts", ".git")

/**
 * 실험 입력(스냅숏·표본 목록)은 **저장소 밖**이다(data-extract §7 · ADR 0010 D-8) — 커밋되지 않아야
 * 한다. 「커밋하지 마라」를 규율이 아니라 기동 실패로 둔다.
 *
 * 경계는 **저장소 루트**다(D-6G-43). 기준을 cwd 로 잡으면 하위 디렉터리에서 기동하는 것만으로 경계가
 * 그 아래로 내려가, 저장소 안의 형제 디렉터리가 「밖」으로 통과한다 — 경로를 바꾸지 않고 작업 디렉터리만
 * 바꿔 여는 문이다.
 */
fun requireOutsideRepository(
    target: Path,
    root: Path? = repositoryRoot(),
): Path {
    val absolute = realPathOf(target)
    require(root == null || !absolute.startsWith(root)) {
        "경로는 저장소 밖이어야 한다 — 실험 입력은 커밋되지 않는다"
    }
    return absolute
}

/**
 * 표식을 가진 첫 조상 — 디렉터리든 파일이든(linked worktree 의 `.git` 은 파일이다). 찾지 못하면
 * `null` 이고, 그때는 경계가 없다(저장소가 아닌 곳에서 돌고 있다).
 */
internal fun repositoryRoot(from: Path = Path.of("")): Path? {
    var probe: Path? = realPathOf(from)
    while (probe != null) {
        if (ROOT_MARKERS.any { Files.exists(probe.resolve(it)) }) return probe
        probe = probe.parent
    }
    return null
}

/**
 * 심링크까지 따라간 절대 경로 — 이름만 대조하면 저장소 밖을 가리키는 이름이 안을 가리켜도 통과한다.
 * 아직 없는 경로는 **존재하는 조상**까지 실해석한 뒤 나머지를 잇는다(출력 디렉터리는 기동 시점에
 * 아직 없다).
 */
private fun realPathOf(target: Path): Path {
    val absolute = target.toAbsolutePath().normalize()
    val missing = ArrayDeque<Path>()
    var probe: Path? = absolute
    while (probe != null && !Files.exists(probe)) {
        probe.fileName?.let(missing::addFirst)
        probe = probe.parent
    }
    val existing = probe ?: return absolute
    return missing.fold(existing.toRealPath()) { resolved, name -> resolved.resolve(name) }
}
