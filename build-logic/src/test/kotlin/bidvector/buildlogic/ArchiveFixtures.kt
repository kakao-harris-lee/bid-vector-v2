package bidvector.buildlogic

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import kotlin.random.Random

/**
 * **진짜 아카이브를 하나 만드는 자리** — 판독기 층([readNestedArchive])을 재는 test 들의 공용 입력.
 *
 * 두 test 클래스가 같은 판독기를 서로 다른 축에서 잰다(`NestedArchivePolicyTest` 는 엔트리와 zip
 * 판독, `BootArchivePolicyTest` 는 manifest 와 index). 그 둘이 각자 아카이브를 쓰면 **쓰는 쪽이
 * 갈라져** 한쪽만 고치는 날 다른 쪽이 조용해진다 — 그래서 쓰는 자리는 하나다.
 */
internal object ArchiveFixtures {
    /** class 파일의 네 바이트 서명 — zip 이 **아니라는** 것이 이 입력의 요점이다. */
    val CLASS_MAGIC = byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte())

    fun write(
        target: File,
        attributes: Map<String, String>,
        entries: Map<String, ByteArray>,
    ) {
        val manifest =
            Manifest().apply {
                mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
                attributes.forEach { (name, value) -> mainAttributes[Attributes.Name(name)] = value }
            }
        JarOutputStream(target.outputStream(), manifest).use { out ->
            entries.forEach { (name, bytes) ->
                out.putNextEntry(ZipEntry(name))
                out.write(bytes)
                out.closeEntry()
            }
        }
    }

    /** 평범한 zip 한 채. 이름이 아니라 이 바이트가 판정 입력이다. */
    fun zipBytes(): ByteArray =
        ByteArrayOutputStream()
            .also { out ->
                JarOutputStream(out).use {
                    it.putNextEntry(ZipEntry("inner.txt"))
                    it.write("inner".toByteArray())
                    it.closeEntry()
                }
            }.toByteArray()

    /**
     * **앞에 바이트를 붙인 zip** — 앞 판 술어(앞 네 바이트 서명)가 놓치던 포장이다. zip 판독기는 끝의
     * 중앙 디렉터리 끝 레코드를 찾으므로 이 바이트도 그대로 아카이브로 읽힌다.
     */
    fun prefixedZipBytes(): ByteArray = "#!/bin/sh\nexec java -jar \"$0\"\n".toByteArray() + zipBytes()

    /**
     * **뒤에 바이트를 붙인 zip** — EOCD 뒤에 [trailing] 바이트가 남는다.
     *
     * 이 포장이 fixture 인 이유는 **읽는 쪽이 관대하기 때문**이다: JDK 의 zip 판독기는 주석 길이가 남은
     * 바이트와 어긋나도 EOCD 를 받아들인다. 그래서 「주석 길이가 정확히 맞는가」로 묻던 앞 판 술어
     * 아래에서는 **단 1 바이트**를 덧붙인 jar 가 「아카이브 아님」인데 로더는 그대로 열었다.
     */
    fun suffixedZipBytes(trailing: Int): ByteArray = zipBytes() + ByteArray(trailing)

    /**
     * **끝 탐색 창보다 큰 zip** — 실제 중첩 jar 는 모두 이 경로를 탄다(가장 작은 것도 수십 KB 다).
     *
     * 내용이 난수인 것은 압축되지 않게 하기 위해서다 — 0 으로 채우면 deflate 가 창보다 작게 줄여 이
     * fixture 가 재려던 경로를 아예 타지 않는다. 씨앗을 고정해 바이트를 재현 가능하게 둔다.
     */
    fun largeZipBytes(): ByteArray =
        ByteArrayOutputStream()
            .also { out ->
                JarOutputStream(out).use {
                    it.putNextEntry(ZipEntry("big.bin"))
                    it.write(Random(LARGE_ENTRY_SEED).nextBytes(LARGE_ENTRY_BYTES))
                    it.closeEntry()
                }
            }.toByteArray()

    /** 추출 도구가 읽는 index 파일의 줄 모양은 `- "<엔트리 경로>"` 다. */
    fun indexBytes(paths: List<String>): ByteArray =
        paths.joinToString(separator = "\n", postfix = "\n") { "- \"$it\"" }.toByteArray()

    private const val LARGE_ENTRY_SEED = 20261109
    private const val LARGE_ENTRY_BYTES = 200_000
}
