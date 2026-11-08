package bidvector.buildlogic

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry

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

    /** 추출 도구가 읽는 index 파일의 줄 모양은 `- "<엔트리 경로>"` 다. */
    fun indexBytes(paths: List<String>): ByteArray =
        paths.joinToString(separator = "\n", postfix = "\n") { "- \"$it\"" }.toByteArray()
}
