package bidvector.adapters.snapshot

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
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
