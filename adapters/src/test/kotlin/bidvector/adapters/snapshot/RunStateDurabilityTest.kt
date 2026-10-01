package bidvector.adapters.snapshot

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.ByteBuffer
import java.nio.channels.WritableByteChannel
import java.nio.file.Files
import java.nio.file.Path

/**
 * **실행 상태의 내구 원시연산**(D-6G2d-41 · 48 ③⑤) — 재는 것이 디렉터리의 잠금·복구와 다르다: 여기는
 * 「쓰기가 끝까지 가는가」와 「굳히지 못하는 마운트에서 계속 도는가」다.
 */
class RunStateDurabilityTest {
    @TempDir
    lateinit var temp: Path

    /**
     * **D-6G2d-48 ⑤ — 짧은 쓰기를 끝까지 쓴다.** `write` 한 번이 전부를 쓴다는 보장은 없다(파이프·
     * 네트워크 파일시스템·시그널). 무시하면 원장에 **반쪽 줄**이 남고, 그 뒤의 append 가 이어 붙어 두
     * 시도가 한 줄이 된다 — 그 줄은 영영 읽히지 않는다.
     */
    @Test
    fun `짧은 쓰기를 끝까지 쓴다`() {
        val sink = TwoPartChannel()

        writeFully(sink, ByteBuffer.wrap("원장 한 줄".toByteArray()))

        sink.written shouldBe "원장 한 줄"
        // 한 번에 다 쓰지 않았다는 것까지 잰다 — 대역이 전부를 한 번에 쓰면 이 판은 아무것도 잠그지 않는다.
        (sink.calls > 1) shouldBe true
    }

    /**
     * **D-6G2d-48 ③ — 디렉터리를 굳히지 못해도 계속 간다.** 디렉터리 fsync 를 받지 않는 마운트(WSL 의
     * DrvFs·9p, 일부 네트워크 파일시스템)가 있고, 거기서 예외를 올리면 append 가 통째로 막혀 **수집이
     * 아예 못 돈다** — 이름 교체가 늦게 굳는 것보다 나쁘다. 파일 데이터 fsync 는 유지된다.
     */
    @Test
    fun `디렉터리를 굳히지 못해도 교체는 끝난다`() {
        val root = Files.createDirectories(temp.resolve("run-state"))
        val target = root.resolve("state.json")

        replaceDurably(root.resolve("state.json.staged"), target, "굳지 않는 마운트\n", root) {
            error("이 마운트는 디렉터리 fsync 를 받지 않는다")
        }

        Files.readString(target) shouldBe "굳지 않는 마운트\n"
        Files.exists(root.resolve("state.json.staged")) shouldBe false
    }

    /** 기본 인자는 **실제** 디렉터리 fsync 다 — 굳는 마운트에서는 그 경로로 교체가 끝난다. */
    @Test
    fun `굳는 마운트에서는 기본 경로로 교체가 끝난다`() {
        val root = Files.createDirectories(temp.resolve("run-state-green"))
        val target = root.resolve("state.json")

        replaceDurably(root.resolve("state.json.staged"), target, "굳는 마운트\n", root)

        Files.readString(target) shouldBe "굳는 마운트\n"
    }

    /** 두 조각으로 나눠 쓰는 대역 — 짧은 쓰기의 최소 재현이다. */
    private class TwoPartChannel : WritableByteChannel {
        private val sink = java.io.ByteArrayOutputStream()
        var calls: Int = 0
            private set

        val written: String get() = sink.toString(Charsets.UTF_8)

        override fun write(src: ByteBuffer): Int {
            calls++
            // 바이트로 모은다 — 반쪽 문자를 조각마다 해독하면 대역 자신이 값을 망친다.
            val half = if (src.remaining() > 1) src.remaining() / 2 else src.remaining()
            val bytes = ByteArray(half)
            src.get(bytes)
            sink.write(bytes)
            return half
        }

        override fun isOpen(): Boolean = true

        override fun close() = Unit
    }
}
