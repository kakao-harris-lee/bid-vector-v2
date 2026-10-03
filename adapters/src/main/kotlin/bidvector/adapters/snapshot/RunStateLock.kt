package bidvector.adapters.snapshot

import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Path
import java.nio.file.StandardOpenOption

internal const val RUN_LOCK_NAME = "run.lock"

/**
 * 실행 상태 디렉터리의 **잠금**(D-6G-57) — 상태가 파일 범위이므로 잠금도 파일 범위다.
 * `RunStateDirectory` 에서 갈라낸 파일이다(sizeGate 500, `FileAttemptLedger` 와 같은 전례):
 * 디렉터리가 지는 것은 무결성 장부·복구·표본 확정이고, 여기가 지는 것은 **자물쇠의 어휘**다.
 *
 * r3 은 DB advisory lock 을 썼고 개찰 갈래에만 걸었다. 공고 목록 갈래가 같은 원장을 쓰게 된 뒤로는
 * 두 갈래가 나란히 seed 한 뒤 **남은 상한을 각자 다 쓰는** 길이 열려 있었다(vr r4 H-2, 일 상한 10 에
 * HTTP 줄 20). 범위가 다른 두 자물쇠는 같은 것을 지키지 못한다 — 상태가 있는 자리에 건다.
 *
 * `FileChannel.tryLock` 은 **프로세스 범위**다: 프로세스가 죽으면 OS 가 놓는다(잠금 행을 표에 두면
 * 죽은 실행이 그것을 들고 남는다). 얻지 못하는 것은 오류가 아니라 정상적인 답이다.
 *
 * **놓는 길은 [RunStateDirectory.close] 하나다**(D-6G2c-4) — 잠금을 민 손잡이가 밖에 있으면
 * 「잠금만 풀렸는데 원장은 쓰기 가능한」 상태를 밖에서 지을 수 있고, 그 상태에는 이름이 없다.
 */
sealed interface RunStateLock {
    /** 이 실행이 들었다 — 두 원장에 쓸 수 있는 유일한 모양이다. */
    class Held internal constructor(
        private val channel: FileChannel,
        private val lock: FileLock,
    ) : RunStateLock {
        internal fun release() {
            if (lock.isValid) lock.release()
            channel.close()
        }
    }

    /** 다른 실행이 들고 있다 — 같은 JVM 의 다른 보유자도 여기다(겹치는 잠금은 같은 뜻이다). */
    data object Busy : RunStateLock

    /**
     * 자물쇠를 **걸 수 없다**(D-6G2c-2) — 잠금을 지원하지 않는 자리이거나 자물쇠 파일을 열 수 없다.
     * [Busy] 와 가르는 이유는 처방이 다르기 때문이다: 저쪽은 기다리면 풀리고 이쪽은 영영 풀리지
     * 않는다. 막는 것은 같다(0 호출).
     */
    data object Unlockable : RunStateLock

    companion object {
        /**
         * 디렉터리를 **여는 자리가 곧 잠금 자리**다 — 잠그지 않고 원장을 얻는 길을 두지 않는다.
         * 잠금 파일은 상태가 아니라 자물쇠라 무결성 장부의 대상이 아니다(§장부 집합에서 뺀다).
         *
         * 던지는 것을 **값으로 가른다**: 겹치는 잠금은 [Busy], 입출력 실패는 [Unlockable]. 그 밖은
         * 그대로 던진다 — 모르는 실패를 「잠글 수 없다」로 접으면 그 사유가 영영 보이지 않는다.
         */
        internal fun tryAcquire(root: Path): RunStateLock =
            try {
                lockedChannelOf(root)
            } catch (_: OverlappingFileLockException) {
                Busy
            } catch (_: IOException) {
                Unlockable
            }
    }
}

private fun lockedChannelOf(root: Path): RunStateLock {
    val channel =
        FileChannel.open(
            root.resolve(RUN_LOCK_NAME),
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
        )
    // 잠금 시도가 던지면 채널을 먼저 닫고 **그대로** 올려 보낸다 — 분류는 [RunStateLock.tryAcquire]
    // 한 자리에서 한다(열기와 잠그기가 같은 입출력 실패를 낸다).
    val lock =
        runCatching { channel.tryLock() }.getOrElse {
            channel.close()
            throw it
        }
    return if (lock == null) {
        channel.close()
        RunStateLock.Busy
    } else {
        RunStateLock.Held(channel, lock)
    }
}
