package bidvector.adapters.snapshot

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.WritableByteChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 실행 상태 파일의 **내구 원시연산** — 순서가 곧 보장이라 한 자리에 모은다(D-6G2d-41).
 *
 * 지켜야 하는 순서는 하나다: **원장이 장부보다 먼저 굳는다.** 장부(`state.json`)는 원장의 누적 해시와
 * 줄 수를 싣는다 — 장부가 먼저 굳으면 정전 뒤에 「줄 수가 장부보다 적다」가 되고, 그 디렉터리는
 * 영구 거부된다(이 slice 가 닫으려던 부류 그대로). 그래서 append 는 자기 바이트를 굳힌 뒤에야 장부
 * 갱신을 부르고, 표본 확정도 두 파일을 굳힌 뒤에 장부를 부른다.
 *
 * 비용은 append 마다 fsync 셋이다(원장 · staged 장부 · 디렉터리 항목). 결말 단위로 묶는 길은
 * `OPEN-6G2D-FSYNC-BATCHING` 이다 — 묶으면 크래시 창이 그만큼 넓어지므로 값을 재고 정한다.
 */
internal val DURABLE_WRITE_OPTIONS: Array<OpenOption> =
    arrayOf(
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.TRUNCATE_EXISTING,
        StandardOpenOption.SYNC,
    )

/**
 * 덧붙이고 **굳히는** 자리 — 두 걸음을 값으로 뺀 이유는 순서를 test 가 볼 수 있어야 하기 때문이다
 * (D-6G2d-41 순서 test). `SYNC` 옵션으로 열면 쓰기마다 굳지만 그때는 「굳혔다」가 관측되지 않는다.
 */
internal interface DurableAppend : AutoCloseable {
    fun append(text: String)

    fun force()
}

/** 출하 구현 — 덧붙이기 전용 채널 하나. */
internal fun appendChannel(file: Path): DurableAppend = FileChannelAppend(file)

private class FileChannelAppend(
    file: Path,
) : DurableAppend {
    private val channel: FileChannel =
        FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)

    override fun append(text: String) {
        writeFully(channel, ByteBuffer.wrap(text.toByteArray(StandardCharsets.UTF_8)))
    }

    override fun force() = channel.force(true)

    override fun close() = channel.close()
}

/**
 * 버퍼를 **끝까지** 쓴다(D-6G2d-48 ⑤) — `write` 한 번이 전부를 쓴다는 보장은 없다. 짧은 쓰기를 무시하면
 * 원장에 **반쪽 줄**이 남고, 그 줄은 다음 기동의 복구가 「찢어진 끝 줄」로 감싸 호출 하나로 세지만 그 전에
 * 이어 붙은 다음 append 가 두 시도를 한 줄로 만든다.
 */
internal fun writeFully(
    channel: WritableByteChannel,
    bytes: ByteBuffer,
) {
    while (bytes.hasRemaining()) channel.write(bytes)
}

/** 이미 쓴 파일의 바이트를 굳힌다 — 장부가 그 파일을 가리키기 **전에** 부른다. */
internal fun forceDurable(file: Path) {
    FileChannel.open(file, StandardOpenOption.WRITE).use { it.force(true) }
}

/**
 * staged 쓰기 → 원자적 교체 → **디렉터리 fsync**. 셋이 한 함수인 이유는 순서가 곧 보장이기 때문이다.
 *
 * `ATOMIC_MOVE` 는 **이름의 교체**만 원자적으로 만든다. 바이트가 아직 페이지 캐시에 있는 동안 rename
 * 이 먼저 굳으면, 그 사이의 전원 손실 뒤에 이름은 새 파일을 가리키는데 내용이 0 바이트인 모양이
 * 남는다 — 복구가 도는 순간은 방금 죽은 기계 위라 그 창이 실제로 열린다. [DURABLE_WRITE_OPTIONS] 의
 * `SYNC` 가 바이트를 먼저 굳히고, 교체 뒤의 디렉터리 fsync 가 **그 이름 자체**를 굳힌다(디렉터리
 * 항목은 파일 fsync 로 굳지 않는다).
 */
internal fun replaceDurably(
    staged: Path,
    target: Path,
    text: String,
    directory: Path,
    directorySync: (Path) -> Unit = ::forceDirectory,
) {
    Files.writeString(staged, text, *DURABLE_WRITE_OPTIONS)
    Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    // **굳히지 못하는 마운트에서도 계속 간다**(D-6G2d-48 ③) — 이 자리에서 예외를 올리면 append 가 통째로
    // 막혀 수집이 아예 못 돈다. 잃는 것은 「이름 교체의 내구」 한 층이고 파일 데이터 fsync 는 그대로다.
    runCatching { directorySync(directory) }.onFailure(::warnDirectorySyncOnce)
}

/** 디렉터리 항목을 굳힌다 — 못 굳히는 마운트의 처분은 부르는 자리([replaceDurably])가 정한다. */
internal fun forceDirectory(directory: Path) {
    FileChannel.open(directory, StandardOpenOption.READ).use { it.force(true) }
}

/**
 * 경고는 프로세스에 **한 번**이다(교체마다 같은 줄을 내면 로그가 그 줄로 덮인다). 어댑터에는 로그
 * 포트가 없어 닫힌 토큰 한 줄을 표준 오류로 낸다 — 운영자가 그 토큰으로 마운트를 고칠 수 있다.
 */
private fun warnDirectorySyncOnce(cause: Throwable) {
    if (directorySyncWarned.compareAndSet(false, true)) {
        System.err.println("$DIRECTORY_FSYNC_UNSUPPORTED cause=${cause.javaClass.name}")
    }
}

/**
 * 마운트가 디렉터리 fsync 를 받지 않는다 — 운영자가 grep 하는 토큰이다. 실행 상태 디렉터리는 **ext4
 * (WSL 내부)** 에 두는 것이 권고이고, 그 권고가 지켜지면 이 줄은 나오지 않는다.
 */
internal const val DIRECTORY_FSYNC_UNSUPPORTED = "RUN_STATE_DIRECTORY_FSYNC_UNSUPPORTED"

private val directorySyncWarned = AtomicBoolean(false)
