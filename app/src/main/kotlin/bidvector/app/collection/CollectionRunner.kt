package bidvector.app.collection

import bidvector.workflow.collection.CollectNoticesUseCase
import bidvector.workflow.collection.CollectionRange
import bidvector.workflow.collection.CollectionReport
import bidvector.workflow.collection.CollectionSource
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import java.sql.SQLException

/** 로그 한 줄의 출구 — 러너는 이 한 자리로만 쓴다(테스트가 줄 전부를 잡을 수 있다). */
fun interface CollectionLog {
    fun write(line: String)
}

/** 프로세스 종료 자리 — 웹 서버가 함께 떠 있어도 일회성 실행이 끝나면 프로세스가 끝난다. */
fun interface CollectionTermination {
    fun terminate(exitCode: Int)
}

/**
 * 실행 실패의 정제된 표현 — 원인 코드만 싣고 원 예외는 **잇지 않는다**. 저장소 예외의 메시지(SQL 상세)에는
 * 행 값(공고명 등)이 실릴 수 있고, 전송 계층 예외에는 요청 URI(서비스 키)가 실릴 수 있어서, 원 예외가
 * 스택에 붙어 로그로 나가는 경로를 만들지 않는다(D-6F8-4).
 */
class CollectionRunFailedException(
    val causeCode: String,
) : RuntimeException("collection run failed cause=$causeCode")

/** 예외에서 로그에 실어도 되는 원인 코드만 뽑는다 — 클래스 이름, SQL 예외는 SQLSTATE 5자리만 더한다(메시지 없음). */
private fun causeCodeOf(failure: Exception): String =
    when (failure) {
        is SQLException -> "${failure.javaClass.name}:sqlState=${failure.sqlState}"
        else -> failure.javaClass.name
    }

/**
 * 일회성 수집 러너(D-6F8-3) — `bidvector.collection.mode=once` 일 때만 빈으로 등록된다(배선이 조건을 건다).
 * use case 를 한 번 돌려 슬롯마다 회계 요약을 로그로 남기고 프로세스를 끝낸다. HTTP 로 수집을 여는
 * endpoint·스케줄러는 없다. 이 클래스는 포트·원문·정규화에 닿지 않는다 — use case 만 부른다(구조 게이트).
 */
class CollectionRunner(
    private val useCase: CollectNoticesUseCase,
    private val range: CollectionRange,
    private val sources: List<CollectionSource>,
    private val log: CollectionLog,
    private val termination: CollectionTermination,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        log.write(startLine(range, sources))
        val report = collectOrFail()
        report.halted?.let { log.write(haltLine(it)) }
        val exitCode = exitCodeOf(report)
        log.write(finishLine(report, exitCode))
        termination.terminate(exitCode.value)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun collectOrFail(): CollectionReport =
        try {
            useCase.collect(range, sources) { log.write(slotLine(it)) }
        } catch (failure: Exception) {
            val causeCode = causeCodeOf(failure)
            log.write(failureLine(causeCode))
            throw CollectionRunFailedException(causeCode)
        }
}
