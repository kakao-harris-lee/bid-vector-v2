package bidvector.buildlogic

import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.zip.ZipEntry

/**
 * **판독기 층** — 아카이브의 바이트를 [NestedArchive] 로 옮긴다. 판정은 [NestedArchivePolicy] 와
 * [BootArchivePolicy] 가 한다.
 *
 * 배선(`JarContentGateTask`)과 **다른 파일**인 것은 다루는 것이 달라서다: 저쪽은 Gradle 의 선언 입력과
 * 실패 문면을, 이쪽은 zip 포맷을 다룬다. 이 층이 조용히 좁아지면(경로를 버리거나 index 를 안 읽으면)
 * 판정은 입력을 못 받고도 초록이므로, 여기에 걸린 test 가 따로 있다.
 *
 * 최상위 함수인 것은 그래야 **test 가 진짜 아카이브를 하나 만들어 이 층을 잴 수 있기** 때문이다
 * (리뷰 M-1): 판정만 순수 test 로 잠그면 판독이 경로를 버리는 변이가 아무 test 도 깨뜨리지 않는다 —
 * 실제로 그렇게 한 라운드가 열렸다.
 *
 * 읽는 것은 셋이다 — ⓐ 엔트리 전수의 경로와 **zip 으로 읽히는가** ⓑ 배포 경로의 도구가 읽는 manifest
 * 속성 ⓒ 그 속성이 가리킨 자리와 [indexLocations] 가 가리킨 자리의 index 줄. ⓑⓒ 가 없으면 「무엇이
 * 실리는가」를 엔트리 목록만으로 짐작하게 된다(verifier H-1).
 */
internal fun readNestedArchive(
    archive: File,
    indexLocations: Set<String>,
): NestedArchive =
    JarFile(archive).use { jar ->
        val attributes =
            BOOT_ARCHIVE_ATTRIBUTES
                .mapNotNull { name ->
                    jar.manifest
                        ?.mainAttributes
                        ?.getValue(name)
                        ?.let { name to it }
                }.toMap()
        NestedArchive(
            path = archive.path,
            displayName = archive.name,
            entries =
                jar
                    .entries()
                    .asSequence()
                    .filterNot { it.isDirectory }
                    .map { ArchiveEntry(it.name, jar.holdsArchiveDirectory(it)) }
                    .toList(),
            descriptor =
                BootArchiveDescriptor(
                    attributes = attributes,
                    indexes = jar.readIndexes(indexLocations + attributes.indexLocations()),
                ),
        )
    }

/** 판독기가 읽는 manifest 속성 — [BootArchiveExpectation] 이 단언하는 것과 **같은 집합**이다. */
private val BOOT_ARCHIVE_ATTRIBUTES =
    listOf(
        MAIN_CLASS_ATTRIBUTE,
        START_CLASS_ATTRIBUTE,
        CLASSES_LOCATION_ATTRIBUTE,
        LIB_LOCATION_ATTRIBUTE,
        CLASSPATH_INDEX_ATTRIBUTE,
        LAYERS_INDEX_ATTRIBUTE,
    )

private fun Map<String, String>.indexLocations(): Set<String> =
    setOfNotNull(this[CLASSPATH_INDEX_ATTRIBUTE], this[LAYERS_INDEX_ATTRIBUTE])

/**
 * index 파일 한 줄의 모양은 `- "<엔트리 경로>"` 다 — 추출 도구가 그 자리에서 따옴표 안쪽만 떼어
 * 쓴다. 떼어지지 않는 줄은 **그대로 둔다**: 그 줄은 기대 집합에 없으므로 판정이 「뿌리 밑 엔트리가
 * 아닌 줄」로 지목하고, 판독기가 조용히 버리면 그 지목이 사라진다.
 */
private fun JarFile.readIndexes(locations: Set<String>): Map<String, List<String>> =
    locations
        .mapNotNull { location -> getEntry(location)?.let { location to readIndexLines(it) } }
        .toMap()

private fun JarFile.readIndexLines(entry: ZipEntry): List<String> =
    getInputStream(entry)
        .use { it.readBytes() }
        .decodeToString()
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { line -> line.removePrefix("- ").removeSurrounding("\"") }
        .toList()

/**
 * zip 판독기가 아카이브를 찾는 자리 — **끝의 중앙 디렉터리 끝 레코드(EOCD)** 다.
 *
 * 앞 판은 앞 네 바이트의 서명을 보았는데, 그러면 **앞에 아무 바이트나 붙인 jar** 가 빠져나갔다
 * (앞 판 제한 20 ⓐ). zip 은 앞에서가 아니라 뒤에서 읽히므로 그 포장은 적재를 막지 않는다. 서명 네
 * 바이트만으로는 우연한 일치가 많아 **주석 길이가 남은 바이트 안에 드는가**와 **중앙 디렉터리가 그
 * 앞에 놓이는가**를 함께 본다(실측: 이 저장소의 배포물에서 뿌리 밖 오탐 0).
 *
 * 끝에서 [EOCD_SCAN_WINDOW] 만큼만 보는 것은 큰 중첩 jar 를 통째로 들지 않기 위해서다. 그 창이
 * **읽는 쪽이 닿는 범위보다 넓어야** 한다는 것이 이 축의 요구다 — 좁으면 로더가 여는 바이트를 게이트가
 * 못 본다. 그 등식은 가정이 아니라 측정이다: `NestedArchivePolicyTest` 가 진짜 `JarFile` 과 같은
 * 입력으로 대조해 **「여는 것은 모두 본다」**를 잰다.
 */
private fun JarFile.holdsArchiveDirectory(entry: JarEntry): Boolean =
    getInputStream(entry).use { it.readTail(EOCD_SCAN_WINDOW) }.holdsCentralDirectoryEnd()

/** 엔트리의 **끝 [size] 바이트**와 전체 길이. 큰 중첩 jar 를 통째로 들고 있지 않기 위해서다. */
private class ArchiveTail(
    val bytes: ByteArray,
    val total: Long,
)

private fun InputStream.readTail(size: Int): ArchiveTail {
    val window = ByteArray(size)
    var filled = 0
    var total = 0L
    val chunk = ByteArray(READ_CHUNK)
    while (true) {
        val read = read(chunk)
        if (read < 0) break
        total += read
        val take = minOf(read, size)
        val keep = minOf(filled, size - take)
        if (keep > 0) System.arraycopy(window, filled - keep, window, 0, keep)
        System.arraycopy(chunk, read - take, window, keep, take)
        filled = keep + take
    }
    return ArchiveTail(window.copyOf(filled), total)
}

private fun ArchiveTail.holdsCentralDirectoryEnd(): Boolean {
    val base = total - bytes.size
    for (index in bytes.size - EOCD_LENGTH downTo 0) {
        if (bytes.readIntLe(index) == EOCD_SIGNATURE && isConsistentEnd(index, base)) return true
    }
    return false
}

/**
 * 서명 네 바이트는 우연히도 나온다 — 그래서 **주석 길이가 남은 바이트 안에 드는가**와 **중앙
 * 디렉터리가 이 레코드 앞에 놓이는가**를 함께 본다.
 *
 * **정확 일치가 아니라 부등식인 것이 요점이다**(리뷰 r5 HIGH 실측). 앞 판은 주석 길이가 남은 바이트와
 * 같아야 한다고 요구했는데, zip 판독기는 그 둘이 어긋나도 중앙 디렉터리 서명으로 되짚어 연다
 * (`ZipFile.findEND` 의 「padded at the end」 경로). 그래서 **뒤에 1 바이트만 붙여도** 게이트는
 * 「아카이브 아님」인데 로더는 그대로 적재했다 — 뿌리 밖 판정이 그 한 바이트로 공허해지는 자리였다.
 * 주석 길이가 남은 바이트를 넘으면 그 레코드는 이 파일의 EOCD 일 수 없으므로 그쪽만 거부한다.
 *
 * 뒤의 확인은 zip64 에서 성립하지 않는다 — 그 형식은 두 자리에 `0xFFFFFFFF` 표지를 넣고 실제 값을
 * 별도 레코드에 둔다. 표지가 보이면 **자리 확인을 하지 않는다**: 하면 zip64 로 포장한 아카이브가
 * 「아카이브가 아니다」로 빠져나간다(앞 판 서명 술어가 열어 두던 것과 같은 종류의 구멍이다).
 */
private fun ArchiveTail.isConsistentEnd(
    index: Int,
    base: Long,
): Boolean {
    val commentLength = bytes.readShortLe(index + EOCD_COMMENT_OFFSET)
    if (commentLength > bytes.size - (index + EOCD_LENGTH)) return false
    val directorySize = bytes.readIntLe(index + EOCD_SIZE_OFFSET).toUInt().toLong()
    val directoryOffset = bytes.readIntLe(index + EOCD_OFFSET_OFFSET).toUInt().toLong()
    val zip64 = directorySize == ZIP64_MARKER || directoryOffset == ZIP64_MARKER
    return zip64 || directoryOffset + directorySize <= base + index
}

/** zip 은 little-endian 이다 — 자리와 폭은 아래 상수가, 바이트 순서는 [ByteOrder] 가 든다. */
private fun ByteArray.readIntLe(at: Int): Int = ByteBuffer.wrap(this, at, Int.SIZE_BYTES).order(LITTLE_ENDIAN).int

private fun ByteArray.readShortLe(at: Int): Int =
    ByteBuffer
        .wrap(this, at, Short.SIZE_BYTES)
        .order(LITTLE_ENDIAN)
        .short
        .toInt() and UNSIGNED_SHORT_MAX

private val LITTLE_ENDIAN = ByteOrder.LITTLE_ENDIAN

/** EOCD 레코드 — 서명, 고정 길이, 그리고 그 안에서 값이 사는 자리 셋. */
private const val EOCD_SIGNATURE = 0x06054B50
private const val EOCD_LENGTH = 22
private const val EOCD_SIZE_OFFSET = 12
private const val EOCD_OFFSET_OFFSET = 16
private const val EOCD_COMMENT_OFFSET = 20

/** zip64 가 「실제 값은 다른 레코드에 있다」고 적는 표지. */
private const val ZIP64_MARKER = 0xFFFFFFFFL

private const val UNSIGNED_SHORT_MAX = 0xFFFF

/**
 * **읽는 쪽이 닿는 범위보다 넓은 여유**. EOCD 의 명목 최대 밀림은 주석 길이(최대 [UNSIGNED_SHORT_MAX])
 * 지만, JDK 의 zip 판독기는 끝에서 **블록 단위로 되짚어** 읽어서 마지막 블록이 그 명목 경계를 걸치면
 * 너머의 EOCD 도 집는다. 실측(JDK 21): 평범한 zip 뒤에 65_536~65_614 바이트를 덧붙인 판을 `JarFile` 이
 * 열었다 — 명목 창만 보던 판에서 그 79 바이트 띠는 **게이트는 「아카이브 아님」인데 로더는 적재한다**
 * 였고, 이 축이 닫으려는 바로 그 비대칭이다.
 *
 * 여유를 블록 하나보다 넉넉히 두는 쪽을 고른 것은 **틀리는 방향이 다르기** 때문이다: 창이 넓어서
 * 생기는 대가는 오탐(게이트가 가짜로 붉음)이고 그것은 시끄럽게 실패한다. 좁아서 생기는 대가는 미탐이고
 * 그것은 조용하다.
 */
private const val READER_SCAN_SLACK = 4096

/** 끝에서 EOCD 를 찾는 창 — 명목 최대 밀림 + [READER_SCAN_SLACK]. */
internal const val EOCD_SCAN_WINDOW = EOCD_LENGTH + UNSIGNED_SHORT_MAX + READER_SCAN_SLACK
private const val READ_CHUNK = 8192
