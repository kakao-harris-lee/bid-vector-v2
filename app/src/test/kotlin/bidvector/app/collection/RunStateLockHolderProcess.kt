package bidvector.app.collection

import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * **별 프로세스가 자물쇠를 든다**(D-6G2c-1, cr r5 M-3). 앞 판의 잠금 E2E 는 **같은 JVM** 에서 두 번째
 * `tryLock()` 을 불렀고, 그러면 잡히는 것은 `OverlappingFileLockException` 이다 — 그 예외는 JVM 안의
 * 사실이라 「다른 **프로세스**가 들고 있다」를 재지 못했다. `tryAcquire` 를 JVM 안 맵 가드로 바꾸는
 * 변이가 그 test 들을 통과했다.
 *
 * 그래서 보유자를 **자식 JVM** 으로 둔다. 자식은 `RunStateLock` 을 쓰지 않고 **JDK 만** 쓴다 —
 * 재는 자리와 재는 도구가 같은 코드를 공유하면 그 코드를 바꾸는 변이가 둘을 함께 움직여 아무것도
 * 드러나지 않는다.
 */
internal object RunStateLockHolder {
    /** 자식이 잠금을 **실제로 든 뒤에** 돌아온다 — 들기 전에 부모가 기동하면 경쟁이 그대로 남는다. */
    fun hold(directory: Path): AutoCloseable {
        val process =
            ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp",
                System.getProperty("java.class.path"),
                RunStateLockHolderMain::class.java.name,
                directory.toString(),
            ).redirectErrorStream(true)
                .start()
        return try {
            awaitHeld(process)
            AutoCloseable { stop(process) }
        } catch (failure: Throwable) {
            // **자식을 두고 던지지 않는다**(cr r1 K-1) — 던지면 `use { }` 가 아직 없어 아무도 닫지
            // 않고, 자식은 `run.lock` 을 쥔 채 남아 같은 디렉터리를 쓰는 뒤 test 를 전부 번지게 한다.
            process.destroyForcibly().waitFor(HOLDER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            closePipes(process)
            throw failure
        }
    }

    /**
     * [HELD_LINE] **또는 EOF 까지** 기한을 두고 읽는다(cr r1 K-1). 한 줄만 읽고 비교하던 앞 판은 JVM
     * 이 먼저 내는 줄(`Picked up …`·VM 경고) 하나에 깨졌고, 기한이 없어 자식이 멈추면 test 도 멈췄다.
     * 읽기를 별 스레드에 두는 이유는 `readLine()` 자체에 기한을 걸 수 없기 때문이다 — 기한을 넘기면
     * 부모가 자식을 강제로 끝내고, 그러면 스트림이 닫혀 그 읽기도 풀린다.
     */
    private fun awaitHeld(process: Process) {
        val reader = process.inputStream.bufferedReader()
        val held =
            CompletableFuture
                .supplyAsync { generateSequence(reader::readLine).firstOrNull { it == HELD_LINE } }
                .get(HOLDER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        checkNotNull(held) { "자식 프로세스가 잠금을 들지 못했다 — $HELD_LINE 없이 끝났다" }
    }

    /** 표준 입력을 닫는 것이 「놓아라」다 — 신호를 파일로 두면 그 파일이 장부 검사에 보인다. */
    private fun stop(process: Process) {
        process.outputStream.close()
        if (!process.waitFor(HOLDER_TIMEOUT_SECONDS, TimeUnit.SECONDS)) process.destroyForcibly()
        closePipes(process)
    }

    /**
     * 부모 쪽 pipe 를 닫는다(PR #59 O) — 자식이 끝나도 부모의 읽기 기술자는 남는다. 잠금 test 가
     * 세 갈래에서 여러 번 보유자를 띄우므로 holder 마다 하나씩 쌓이고, 그 누수는 한참 뒤 다른 test 가
     * 기술자를 얻지 못할 때에야 드러난다. 읽기 스트림이 유일한 출구다(`redirectErrorStream` 이라
     * 표준 오류가 거기 합쳐진다).
     */
    private fun closePipes(process: Process) {
        runCatching { process.inputStream.close() }
    }
}

/** 자식이 잠금을 들었음을 알리는 한 줄 — 부모가 이 줄을 읽은 뒤에만 기동한다. */
internal const val HELD_LINE = "RUN-STATE-LOCK-HELD"

private const val HOLDER_TIMEOUT_SECONDS = 30L

/** 자물쇠 파일의 이름 — 출하와 같아야 한다(갈리면 보유자가 엉뚱한 파일을 들고 test 가 붉어진다). */
private const val HOLDER_LOCK_NAME = "run.lock"

/**
 * 자식 JVM 의 진입점 — 인자는 실행 상태 디렉터리 하나다. 잠금을 들면 [HELD_LINE] 을 내보내고
 * **표준 입력이 닫힐 때까지** 들고 있는다. 프로젝트 코드를 하나도 부르지 않는다(JDK 만).
 */
object RunStateLockHolderMain {
    @JvmStatic
    fun main(args: Array<String>) {
        val directory = Path.of(args.first())
        check(Files.isDirectory(directory)) { "실행 상태 디렉터리가 아니다: $directory" }
        val channel =
            FileChannel.open(
                directory.resolve(HOLDER_LOCK_NAME),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
            )
        val lock = requireNotNull(channel.tryLock()) { "이미 다른 보유자가 있다" }
        println(HELD_LINE)
        System.out.flush()
        // 표준 입력이 닫히면(부모가 놓으라고 한 것) 읽기가 -1 을 낸다.
        System.`in`.read()
        lock.release()
        channel.close()
    }
}
