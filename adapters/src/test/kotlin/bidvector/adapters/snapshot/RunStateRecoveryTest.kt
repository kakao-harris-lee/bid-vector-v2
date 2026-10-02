package bidvector.adapters.snapshot

import bidvector.procurement.SourceEndpoint
import bidvector.workflow.collection.sha256Hex
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * D-6G-70 · D-6G2d-1·2 · D-6G2e-3·5·17 — 실행 상태 디렉터리의 **복구**. `RunStateDirectoryTest` 와
 * 재는 것이 다르다: 저쪽은 「무엇을 거부하는가」이고 이쪽은 **「크래시 뒤에 다시 열리는가, 그리고
 * 거부될 디렉터리를 고쳐 쓰지는 않는가」**다. 파일 500 줄 한도가 이 경계에서 갈라졌다
 * (`RunStateFormatTest`·`OpeningRetryCapTest` 와 같은 전례).
 *
 * 하네스는 `RunStateDirectoryFixture` 를 공유한다 — 여는 자리가 곧 잠금 자리라 test 마다 놓아
 * 주어야 하고, 그 규율이 클래스마다 갈리면 한쪽이 「이미 도는 실행」을 보며 조용히 붉어진다.
 */
class RunStateRecoveryTest : RunStateDirectoryFixture() {
    /**
     * **D-6G-70 (cr r5 M-1 probe C2) — 찢어진 끝 줄은 크래시 흔적이다.** append 도중에 죽으면 원장은
     * 개행 없이 끝난다. 거부하면 그 실행 상태는 사람이 손대기 전까지 막히고, 그대로 두면 다음
     * append 가 조각에 이어 붙어 두 시도가 한 줄이 된다. 기동은 **수락**하고, 그 조각은 나갔을 수
     * 있으므로 **호출 하나로 센다** — 상한이 줄지 않는 쪽이다.
     */
    @Test
    fun `찢어진 끝 줄은 기동을 막지 않고 호출 하나로 센다`() {
        val directory = open()
        directory.attempts.append(runStatePendingAttempt())
        val spentBefore =
            directory.attempts
                .read()
                .spend(RUN_STATE_AT)
                .total
        directory.close()
        val file = root().resolve(ATTEMPT_LEDGER_NAME)
        // 개행 없이 끝난 조각 — 마지막 append 가 절반만 디스크에 닿았다.
        Files.writeString(file, Files.readString(file) + "{\"at\":\"2026-09-24T01:00:00Z\",\"axis\":\"RES")

        val reopened = open()

        reopened.attempts
            .read()
            .spend(RUN_STATE_AT)
            .total shouldBe spentBefore + 1
        // 조각을 닫았으므로 다음 append 가 그 줄에 이어 붙지 않는다.
        reopened.attempts.append(runStatePendingAttempt())
        reopened.attempts
            .read()
            .spend(RUN_STATE_AT)
            .total shouldBe spentBefore + 2
    }

    /**
     * **D-6G2d-1 (vr r5-t probe C2) — 누적 해심은 복구 뒤에 짓는다.** 한 번만 여는 test 는 이
     * 결함을 보지 못한다: 복구 **전** 바이트의 해심이 장부에 굳으부 기동 A 는 성공하고 **기동 B
     * 부터** 「앞부분이 장부와 다르다」로 영구 거부된다. 벗어나는 길은 디렉터리를 므는 것밖에 없고
     * 그러면 상한이 0 에서 다시 새다 — 실제로 나간 호출 수와 상한이 어긋나는 자리다.
     */
    @Test
    fun `첢어진 끝 줄을 고친 뒤의 기동도 열린다 — 장부는 고친 바이트를 적는다`() {
        val file = tornLedger()

        val first = open()
        first.attempts.append(runStatePendingAttempt())
        val second = reopen()
        val third = reopen()

        // 기동 A 가 세운 조각 하나 + PENDING 둘 = 셋. 기동 B·C 가 같은 것을 읽으면 거부되지 않았다.
        second.attempts
            .read()
            .spend(RUN_STATE_AT)
            .total shouldBe 3
        third.attempts
            .read()
            .spend(RUN_STATE_AT)
            .total shouldBe 3
        Files.readString(root().resolve(STATE_NAME)) shouldContain
            "\"attempts_sha256\":\"${sha256Hex(Files.readString(file))}\""
    }

    /**
     * **D-6G2d-2 (cr r5-t M-3) — 복구 쓰기도 원자적이다.** 복구가 도는 새간은 방금 죽은 기계
     * 위다. 제자리 truncate+rewrite 는 그 중간에 또 죽으면 원장을 **짧게** 만들고, 다음 기동은
     * 「줄 수가 장부보다 적다」로 영구 거부한다. 중간 파일 자리를 막아 쓰기를 실패시키면, 원자
     * 교안는 원장을 **손대지 않은 채** 거부하고 제자리 쓰기는 그 자리를 아예 지나지 않는다.
     */
    @Test
    fun `복구 쓰기가 실패하면 원장이 짧아지지 않는다`() {
        val file = tornLedger()
        val torn = Files.readString(file)
        // 중간 파일 이름을 디렉터리로 막는다 — 그 자리로 쓰는 구현만 여기서 멈울다.
        Files.createDirectory(root().resolve(STAGED_ATTEMPT_NAME))

        shouldThrow<IOException> { open() }

        Files.readString(file) shouldBe torn
    }

    /**
     * D-6G2d-2 — 중간 파일이 남은 디렉터리는 기동을 막지 않는다. 교체가 원자적이라 **원장이 성한**
     * 채로 중간 파일만 남는 모양이 있다(중간 파일은 썼는데 교체가 실패해 사람이 끝 줄을 손으로
     * 닫은 뒤). 그 이름은 장부 대상 집합에서 빠진다 — 빠지지 않으면 복구가 남긴 흔적이 「모르는
     * 파일」로 그 디렉터리를 영구히 막고, 그 사고를 만든 것은 복구 자신이다.
     */
    @Test
    fun `복구가 남긴 중간 파일은 장부 대상이 아니다`() {
        val state = open()
        state.sampleList.confirm(runStateSample())
        state.attempts.append(runStatePendingAttempt())
        val file = root().resolve(ATTEMPT_LEDGER_NAME)
        val intact = Files.readString(file)
        Files.writeString(root().resolve(STAGED_ATTEMPT_NAME), "{\"at\":\"2026-09-2")

        val reopened = reopen()

        reopened.attempts.read().size shouldBe 1
        Files.readString(file) shouldBe intact
    }

    /**
     * **D-6G2e-3 (6G-2d cr 4차 ②) — (공고, 축)까지 닿은 조각은 열린 라운드다.** 관문은 호출 **전에**
     * 의도 줄을 적고 예산은 이미 그 조각을 호출 하나로 세는데(D-6G-61 ①), 앞 판은 조각을 **세기만**
     * 해서 그 라운드가 열린 것으로 보이지 않았다 — 같은 축의 반복 크래시가 재호출 상한을 올리지
     * 못하고 매 실행 새 호출을 태웠다(예산 상한만이 막았다). 조각에서 (공고, 축, 시각)이 읽히면
     * 그 라운드를 의도 줄로 되살려, 두 장부가 같은 가정 위에 선다.
     *
     * 걷기 식별자는 **조각 자신의 시각**이다 — 그 호출이 나간 시각이고, 앞 줄에서 빌리지 않는다.
     */
    @Test
    fun `축과 공고까지 닿은 조각은 열린 라운드로 읽힌다 — 호출 수는 그대로 하나`() {
        val file = fragmentOnlyLedger(AXIS_READABLE_FRAGMENT)

        val history = reopen().attempts.read()

        // 조각이 의도 줄로 되살아나도 호출 수는 **하나**다(예산이 두 번 세지 않는다).
        history.spend(RUN_STATE_AT).total shouldBe 1
        history.interruptedRounds()[RUN_STATE_KEY.value]?.get(SourceEndpoint.RESERVE_PRICE_DETAIL) shouldBe
            TORN_CALL_AT
        // 되살린 줄을 원장에 **쓰지 않는다** — 표식 그대로다(판독이 매번 같은 답을 낸다).
        Files.readString(file) shouldContain TORN_KEY
    }

    /**
     * 반대 방향 — 축에 닿지 못한 조각은 **라운드를 짓지 않는다**(D-6G-70 그대로). 어느 (공고, 축)의
     * 호출이었는지 모르는 채로 라운드를 세면 엉뚱한 축이 상한을 쓴다. 호출 하나로만 센다.
     */
    @Test
    fun `축에 닿지 못한 조각은 라운드를 짓지 않고 호출 하나로만 센다`() {
        fragmentOnlyLedger(AXIS_UNREADABLE_FRAGMENT)

        val history = reopen().attempts.read()

        history.spend(RUN_STATE_AT).total shouldBe 1
        history.interruptedRounds() shouldBe emptyMap()
    }

    /**
     * **D-6G2e-5 (6G-2d cr 4차 ⑦) — 거부될 디렉터리를 먼저 고쳐 쓰지 않는다.** 앞 판은 복구가 자리
     * 대조보다 앞서서, 복사된 디렉터리(directory_id 불일치)의 원장이 기동 거부 **전에** 이미 교체됐다.
     * 사람이 보려던 증거가 거부 메시지와 함께 달라져 있으면, 무엇이 복사됐는지 판독할 자리가 사라진다.
     * 복구는 그 디렉터리를 쓰기로 한 뒤의 일이다.
     */
    @Test
    fun `복사된 디렉터리는 찢어진 끝 줄을 고치지 않고 거부한다 — 원장 바이트 불변`() {
        val source = open()
        source.sampleList.confirm(runStateSample())
        source.attempts.append(runStatePendingAttempt())
        val copy = Files.createDirectories(temp.resolve("torn-copy"))
        listOf(SAMPLE_LIST_NAME, SAMPLE_SCOPE_NAME, ATTEMPT_LEDGER_NAME, STATE_NAME).forEach {
            Files.copy(root().resolve(it), copy.resolve(it))
        }
        val ledger = copy.resolve(ATTEMPT_LEDGER_NAME)
        Files.writeString(ledger, Files.readString(ledger) + AXIS_UNREADABLE_FRAGMENT)
        val received = Files.readString(ledger)

        shouldThrow<IllegalArgumentException> { open(copy) }

        Files.readString(ledger) shouldBe received
        Files.exists(copy.resolve(STAGED_ATTEMPT_NAME)) shouldBe false
    }

    /**
     * **D-6G2e-5 — 원장 **읽기**가 던져도 잠금은 남지 않는다.** 기동의 첫 걸음은 전부 가드 안이고
     * 누적 해시를 짓는 읽기도 그 안이다. 잠금이 남으면 다음 실행은 「다른 실행이 돌고 있다」로 조용히
     * 끝나고(exit 3), 운영자는 거부 사유를 영영 보지 못한다 — 사고 하나가 둘이 된다.
     */
    @Test
    fun `원장이 UTF-8 이 아니면 던지고 잠금을 놓는다 — 다음 기동이 Busy 가 아니다`() {
        val state = open()
        state.sampleList.confirm(runStateSample())
        state.attempts.append(runStatePendingAttempt())
        state.close()
        opened.clear()
        Files.write(root().resolve(ATTEMPT_LEDGER_NAME), byteArrayOf(0x7B, 0xC3.toByte(), 0x28, 0x0A))

        shouldThrow<Exception> { open() }

        // 같은 프로세스에서 다시 열면 잠금을 **얻는다**(Busy 가 아니다) — 거부 사유가 다시 보인다.
        shouldThrow<Exception> { open() }.message.toString().isNotEmpty() shouldBe true
        RunStateLock.tryAcquire(root()).let {
            (it is RunStateLock.Held) shouldBe true
            it.release()
        }
    }

    /**
     * **D-6G2e-17 (vr r1 M-1 = cr r1 M-3) — 읽기 전용 대조는 전부 복구 앞이다.** D-6G2e-5 는 자리
     * 대조 셋만 앞으로 옮겼고, 표본 해시 대조와 원장 **앞부분** 대조는 복구 뒤에 남았다 — 그 둘로
     * 거부될 디렉터리도 원장을 고쳐 쓴 뒤 거부됐다. 조각은 표식 안에 원문 그대로 남으니 내용 손실은
     * 없지만, 「거부될 디렉터리는 고쳐 쓰지 않는다」가 절반만 선 것이고 사람이 받은 바이트가 달라진다.
     *
     * 찢어진 끝 줄은 **접두 대조에서 빼고 줄 수에는 센다** — 아직 줄이 아니지만 그 호출은 나갔을 수
     * 있다. 그래서 정직한 크래시의 답은 바뀌지 않는다(복구 뒤 재동기 그대로).
     */
    @Test
    fun `원장 앞부분이 장부와 다르면 찢어진 끝 줄을 고치지 않고 거부한다 — 바이트 동일`() {
        val file = tamperedLedgerWithTornTail { it.replaceFirst("2026-09-24T01:00:00Z", "2026-09-24T01:00:01Z") }
        val received = Files.readString(file)

        shouldThrow<IllegalArgumentException> { reopen() }

        Files.readString(file) shouldBe received
        Files.exists(root().resolve(STAGED_ATTEMPT_NAME)) shouldBe false
    }

    /** 같은 원칙의 다른 축 — 확정 표본이 바뀐 디렉터리도 원장을 고쳐 쓰지 않는다. */
    @Test
    fun `표본 목록이 바뀌면 찢어진 끝 줄을 고치지 않고 거부한다 — 바이트 동일`() {
        val file = tamperedLedgerWithTornTail { it }
        Files.writeString(root().resolve(SAMPLE_LIST_NAME), "SYN-6G-9999\tSERVICE\t2026-W23\n")
        val received = Files.readString(file)

        shouldThrow<IllegalArgumentException> { reopen() }

        Files.readString(file) shouldBe received
        Files.exists(root().resolve(STAGED_ATTEMPT_NAME)) shouldBe false
    }

    /**
     * 표본 확정 + 줄 하나 + 찢어진 끝 줄까지 쓴 원장 — [tamper] 가 **앞부분**을 바꾼다(그대로 돌려
     * 주면 앞부분은 성하고 다른 축만 어긋난 판이 된다).
     */
    private fun tamperedLedgerWithTornTail(tamper: (String) -> String): Path {
        val directory = open()
        directory.sampleList.confirm(runStateSample())
        directory.attempts.append(runStatePendingAttempt())
        directory.close()
        val file = root().resolve(ATTEMPT_LEDGER_NAME)
        Files.writeString(file, tamper(Files.readString(file)) + AXIS_UNREADABLE_FRAGMENT)
        return file
    }

    /**
     * 조각 **하나만** 남은 원장 — 그 라운드의 첫 의도 줄을 쓰다 죽은 모양이라 앞 줄이 없다. 장부는
     * 표본 확정으로 세워 둔다(장부 없이 파일만 있으면 그 자체로 기동 거부다).
     */
    private fun fragmentOnlyLedger(fragment: String): Path {
        val directory = open()
        directory.sampleList.confirm(runStateSample())
        directory.close()
        val file = root().resolve(ATTEMPT_LEDGER_NAME)
        Files.writeString(file, fragment)
        return file
    }

    /** 개행 없이 끝난 원장 — 마지막 append 가 절반만 디스크에 닿은 모양이다. */
    private fun tornLedger(): Path {
        val directory = open()
        directory.attempts.append(runStatePendingAttempt())
        directory.close()
        val file = root().resolve(ATTEMPT_LEDGER_NAME)
        Files.writeString(file, Files.readString(file) + "{\"at\":\"2026-09-24T01:00:00Z\",\"axis\":\"RES")
        return file
    }
}

/** 되살린 라운드의 걷기 식별자 — 조각 자신의 시각이고 [RUN_STATE_AT] 과 다른 값으로 둔다. */
private val TORN_CALL_AT: Instant = Instant.parse("2026-09-24T02:00:00Z")

/** (시각, 축, 공고)까지 닿은 조각 — 의도 줄을 쓰다 `outcome` 칸에서 끊겼다. */
private val AXIS_READABLE_FRAGMENT: String =
    "{\"at\":\"$TORN_CALL_AT\",\"axis\":\"${SourceEndpoint.RESERVE_PRICE_DETAIL.name}\"," +
        "\"notice_key_hash\":\"${RUN_STATE_KEY.value}\",\"outc"

/** 축 값에서 끊긴 조각 — 어느 라운드의 호출이었는지 알 수 없다. */
private const val AXIS_UNREADABLE_FRAGMENT = "{\"at\":\"2026-09-24T01:00:00Z\",\"axis\":\"RES"
