package bidvector.app.collection

import bidvector.adapters.snapshot.RunStateDirectory
import bidvector.workflow.collection.OpeningPromotionReport
import bidvector.workflow.collection.OpeningPromotionRun
import bidvector.workflow.collection.PromoteOpeningResultsUseCase
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.LocalDate

/**
 * 승격 실행의 입력(M7/7C⑤-0) — `bidvector.opening-promote.mode=once` 일 때만 바인딩된다.
 *
 * [runStateDir] 는 수집 갈래가 남긴 **저장소 밖** 실행 상태 디렉터리다(D-6G-45). 확정 표본과 시도
 * 원장이 거기 있고, 둘 다 승격의 입력이다 — 표본은 범위를 정하고(D-7C5P-6) 원장은 어느 걷기의 행을
 * 쓸지 정한다(D-6G-68). 기본값을 두지 않는다: 어느 실행의 상태를 쓰는지 매번 실행자가 정한다.
 *
 * [referenceDate] 는 계약 개정 해석의 기준일이다 — 수집 갈래가 같은 결정을 배선에서 한다.
 */
@ConfigurationProperties(prefix = "bidvector.opening-promote")
data class OpeningPromotionProperties(
    val runStateDir: String,
    val referenceDate: LocalDate,
)

/**
 * 승격 러너(M7/7C⑤-0) — 이미 적재된 개찰 축 원문을 읽어 canonical `opening_result` 를 쓴다.
 * **외부 호출 0**(KONEPS 를 부르지 않아 서비스 키 설정을 요구하지 않는다) · 원문 쓰기 0 · 시도 원장
 * 쓰기 0. 로그에는 계수만 남긴다 — 공고 식별자도 상호도 싣지 않는다.
 *
 * **잠금 안에서만 돈다**(D-7C5P-5). 수집이 도는 중에 읽으면 표본·원장이 검증되지 않은 채로 오고, 그
 * 순간의 원장은 절반만 쓰인 걷기를 가리킬 수 있다 — 승격은 그 걷기를 canonical 로 굳히는 쓰기라
 * 추출보다 되돌리기가 어렵다. 잠금을 못 잡으면 **거부**한다.
 */
@Suppress("TooGenericExceptionCaught")
class OpeningPromotionRunner(
    private val useCase: PromoteOpeningResultsUseCase,
    private val runState: RunStateDirectory,
    private val log: CollectionLog,
    private val termination: CollectionTermination,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) =
        underRunStateLock(runState, "opening-promote", log, termination) { promoteOrFail() }

    private fun promoteOrFail() {
        try {
            promote()
        } catch (failure: Exception) {
            // 형제 러너와 같은 형태(D-6F8-4) — 원 예외를 잇지 않는다. 저장소 예외 메시지에 행 값이
            // 실릴 수 있어 그 채널을 만들지 않는다.
            val causeCode = openingCauseCodeOf(failure)
            log.write(failureLine(causeCode))
            throw CollectionRunFailedException(causeCode)
        }
    }

    private fun promote() {
        when (val run = useCase.promote()) {
            OpeningPromotionRun.SampleNotConfirmed -> {
                // 표본이 없는 것은 오류가 아니라 **순서**다 — 수집이 먼저다. 아무것도 쓰지 않았다.
                log.write(PROMOTION_SKIPPED_LINE)
                termination.terminate(CollectionExitCode.INCOMPLETE.value)
            }

            is OpeningPromotionRun.Completed -> {
                log.write(promotionFinishedLine(run.report))
                termination.terminate(CollectionExitCode.COMPLETE.value)
            }
        }
    }
}

/**
 * 승격이 끝난 줄 — 계수들의 **유일한 공시 자리**다. 줄을 짓는 일을 값 함수로 두는 이유는 칸 하나가
 * 빠지거나 **두 계수가 서로 바뀌어도** test 가 붉어지게 하기 위해서다(줄 안에 값을 박아 두면 그
 * 배선은 어느 test 도 보지 못한다, 형제 러너의 같은 판단).
 */
internal fun promotionFinishedLine(report: OpeningPromotionReport): String =
    "opening-promote finished groupsRead=${report.groupsRead} " +
        "promoted=${report.promoted} " +
        "skippedUnchanged=${report.skippedUnchanged} " +
        "outsideSample=${report.outsideSample} " +
        "noUsableAxis=${report.noUsableAxis} " +
        "rejected=${report.rejected} " +
        "unkeyedRows=${report.unkeyedRows} " +
        "maskedRows=${report.maskedRows} " +
        "expiredRows=${report.expiredRows}"

/** 확정 표본이 없어 아무것도 쓰지 않았다 — 실패와 구별되는 한 줄이다(처방이 「수집을 먼저 돌려라」다). */
internal const val PROMOTION_SKIPPED_LINE = "opening-promote skipped reason=SAMPLE_NOT_CONFIRMED promoted=0"
