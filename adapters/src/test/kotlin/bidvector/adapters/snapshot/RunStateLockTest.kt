package bidvector.adapters.snapshot

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.nio.channels.ClosedByInterruptException
import java.nio.channels.FileLockInterruptionException
import java.nio.channels.OverlappingFileLockException
import java.nio.file.FileSystemException
import java.nio.file.Files

/**
 * M6/6G-2c D-6G2c-2·4 · D-6G2c-21 ⑦ — 실행 상태 **자물쇠의 어휘**와 그 어휘가 가르는 쓰기 권한.
 * `RunStateDirectoryTest`(무엇을 거부하는가)·`RunStateRecoveryTest`(크래시 뒤에 다시 열리는가)와
 * 재는 것이 다르다: 여기는 **잠금을 얻지 못한 실행이 무엇을 할 수 있는가**다.
 */
class RunStateLockTest : RunStateDirectoryFixture() {
    /**
     * **D-6G2c-2 (cr r5 M-3 부수) — 「잠글 수 없다」는 「다른 실행 중」이 아니다.** 앞 판은
     * `runCatching { channel.tryLock() }.getOrNull()` 하나로 둘을 접었고, 자물쇠 파일을 열 수조차
     * 없는 자리(잠금을 지원하지 않는 파일시스템 · 그 이름이 디렉터리)에서 던지거나 「이미 도는
     * 실행」으로 보고했다. 처방이 갈린다: 하나는 기다리면 풀리고 하나는 영영 풀리지 않는다.
     *
     * 둘 다 **0 호출로 멈추는 것은 그대로**다 — 갈린 것은 사유 어휘뿐이다.
     */
    @Test
    fun `자물쇠를 걸 수 없는 자리는 다른 실행 중이 아니다`() {
        Files.createDirectory(root().resolve(RUN_LOCK_NAME))

        open().lock shouldBe RunStateLock.Unlockable
    }

    /**
     * **D-6G2c-4 (cr r5 L-1 · vr r5 L-9) — 표본 원장도 잠금 없이 쓰지 못한다.** 시도 원장은
     * [LockedOutAttemptLedger] 로 감싸였는데 표본 목록은 감싸이지 않아, 잠금을 못 든 실행이 표본을
     * 확정할 수 있었다. 확정은 한 번뿐이라(`CREATE_NEW`) 그 한 번을 남이 가져가면 도는 실행의
     * 표본이 아닌 목록이 못 박힌다.
     */
    @Test
    fun `잠금을 들지 못한 실행은 표본 목록을 확정하지 못한다 — 읽기는 된다`() {
        val held = open()
        held.sampleList.confirm(runStateSample())

        val second = open()

        second.lock shouldBe RunStateLock.Busy
        second.sampleList.confirmed() shouldBe held.sampleList.confirmed()
        shouldThrow<IllegalStateException> { second.sampleList.confirm(runStateSample()) }
    }

    /** 자물쇠를 걸 수 없는 자리도 같은 거부다 — 「들지 못했다」가 하나의 답으로 묶인다. */
    @Test
    fun `자물쇠를 걸 수 없는 실행도 두 원장에 쓰지 못한다`() {
        Files.createDirectory(root().resolve(RUN_LOCK_NAME))

        val unlockable = open()

        shouldThrow<IllegalStateException> { unlockable.attempts.append(runStatePendingAttempt()) }
        shouldThrow<IllegalStateException> { unlockable.sampleList.confirm(runStateSample()) }
    }

    /**
     * **D-6G2c-21 ⑦ — 누적 해시는 잠금을 든 실행만 짓는다.** 앞 판은 Busy 에서도 원장 전체를 읽어
     * 해시를 쌓았다. 그 읽기는 던질 수 있고(비UTF-8 바이트), 그러면 조용히 물러나야 할 실행이
     * **예외로 죽는다** — 운영자는 「다른 실행이 돌고 있다」가 아니라 스택 트레이스를 본다.
     * 해시는 쓰는 실행에만 필요하다.
     */
    @Test
    fun `잠금을 못 든 실행은 판독 불가 원장에도 예외가 아니다`() {
        val held = open()
        held.sampleList.confirm(runStateSample())
        held.attempts.append(runStatePendingAttempt())
        // 열린 뒤에 깨뜨린다 — 든 실행의 판독은 이미 끝났고, 다음 기동이 그 바이트를 만난다.
        Files.write(root().resolve(ATTEMPT_LEDGER_NAME), byteArrayOf(0x7B, 0xC3.toByte(), 0x28, 0x0A))

        val second = open()

        second.lock shouldBe RunStateLock.Busy
    }

    /**
     * **vr r1 F-1 — 닫힌 뒤에는 같은 인스턴스도 쓰지 못한다.** 앞 판은 잠금만 보았고 그 판정이 생성
     * 시점에 굳었다: `close()` 로 잠금을 놓은 뒤에도 같은 인스턴스의 두 원장이 그대로 썼고, 같은 JVM
     * 의 둘째 인스턴스가 `Held` 를 얻어 **둘 다** 썼다 — 없애려던 「잠금만 풀린 원장」이 `release`
     * 대신 `close` 로 다시 지어진 것이다.
     */
    @Test
    fun `닫힌 뒤에는 같은 인스턴스가 두 원장에 쓰지 못한다`() {
        val directory = open()
        directory.sampleList.confirm(runStateSample())
        directory.attempts.append(runStatePendingAttempt())
        directory.close()

        shouldThrow<IllegalStateException> { directory.attempts.append(runStatePendingAttempt()) }
        shouldThrow<IllegalStateException> { directory.sampleList.confirm(runStateSample()) }
        // 읽기는 그대로다 — 막는 것은 쓰기뿐이고, 닫힌 인스턴스로도 바이트를 볼 수 있다.
        directory.attempts.read().size shouldBe 1
    }

    /**
     * 양성 대조 — 닫힌 뒤의 **다음 실행**은 정상이다. 표지가 디렉터리가 아니라 인스턴스에 붙는다는
     * 것을 고정한다(디렉터리에 붙으면 한 번 닫은 자리가 영영 막힌다).
     */
    @Test
    fun `닫힌 뒤에 다시 열면 그 인스턴스는 쓸 수 있다`() {
        val first = open()
        first.attempts.append(runStatePendingAttempt())
        first.close()
        opened.clear()

        val second = open()

        second.lock.shouldBeInstanceOf<RunStateLock.Held>()
        second.attempts.append(runStatePendingAttempt())
        second.attempts.read().size shouldBe 2
    }

    /**
     * **cr r1 K-3 — 인터럽트는 「자물쇠를 걸 수 없다」가 아니다.** 둘 다 `IOException` 하위라 전부
     * 접으면 이 스레드에 내려진 지시가 종료 코드 4 와 「영영 풀리지 않는다」는 진단으로 나가고
     * 인터럽트는 삼켜진다. 분류가 순수 함수라 실제 인터럽트를 일으키지 않고 값으로 잰다.
     */
    @Test
    fun `잠금 실패의 분류 — 겹침은 Busy, 입출력은 Unlockable, 인터럽트는 올려 보낸다`() {
        runStateLockFailure(OverlappingFileLockException()) shouldBe RunStateLock.Busy
        runStateLockFailure(FileSystemException("자물쇠 파일")) shouldBe RunStateLock.Unlockable

        shouldThrow<ClosedByInterruptException> { runStateLockFailure(ClosedByInterruptException()) }
        shouldThrow<FileLockInterruptionException> { runStateLockFailure(FileLockInterruptionException()) }
        // 모르는 실패도 접지 않는다 — 접으면 그 사유가 영영 보이지 않는다.
        shouldThrow<IllegalStateException> { runStateLockFailure(IllegalStateException("모르는 실패")) }
    }

    /** 든 실행은 그대로 해시를 짓는다 — ⑦ 이 Held 쪽 판독까지 끄지 않았다는 양성 대조다. */
    @Test
    fun `잠금을 든 실행은 판독 불가 원장을 그대로 거부한다`() {
        val held = open()
        held.attempts.append(runStatePendingAttempt())
        held.close()
        opened.clear()
        Files.write(root().resolve(ATTEMPT_LEDGER_NAME), byteArrayOf(0x7B, 0xC3.toByte(), 0x28, 0x0A))

        shouldThrow<Exception> { open() }

        // 거부로 죽어도 잠금은 남지 않는다 — 다음 기동이 Busy 가 아니다(D-6G2e-5).
        shouldThrow<Exception> { open() }
        RunStateLock.tryAcquire(root()).shouldBeInstanceOf<RunStateLock.Held>().release()
    }
}
