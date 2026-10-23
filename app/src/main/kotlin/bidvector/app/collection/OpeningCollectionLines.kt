package bidvector.app.collection

import bidvector.adapters.snapshot.runStateFormatCauseCode
import bidvector.workflow.collection.CollectionRange
import bidvector.workflow.collection.OpeningCollectionHalt
import bidvector.workflow.collection.OpeningCollectionReport
import bidvector.workflow.collection.OpeningCollectionSource
import java.sql.SQLException

/**
 * 개찰결과 러너가 남기는 줄들(D-6F8-4 규율 승계) — 건수·날짜·업종 이름·열거값·원인 코드만 실을 수 있다.
 * 입력 타입이 원문·공고 식별자를 나르지 않으므로 여기서 새는 값은 구조적으로 없다. **표본 키 해시도
 * 싣지 않는다** — 계수로 충분하고, 식별자를 로그로 내보낼 이유가 없다.
 */
internal fun openingStartLine(
    range: CollectionRange,
    sources: List<OpeningCollectionSource>,
): String =
    "opening-collection start noticeFrom=${range.from} noticeTo=${range.to} " +
        "sources=${sources.joinToString(",") { it.name.value }}"

internal fun openingHaltLine(halt: OpeningCollectionHalt): String =
    "opening-collection halted budgetLimit=${halt.budgetLimit ?: "none"} " +
        "truncation=${halt.truncationCause ?: "none"} notAttempted=${halt.notAttempted} " +
        "partialNotice=${halt.partialNotice}"

/**
 * **건너뜀 둘은 줄 끝에 선다**(M6/G1-C03, D-G1C03-1). 회계 열은 하나(`backoff_skipped`)라 거기서는
 * 사유가 섞이는데, 운영자가 「수집이 줄었다」를 사유와 함께 보는 자리가 이 줄이다. 끝에 더하는 이유는
 * 이 줄을 읽는 쪽이 `frame=` **이후를 통째로** 잘라 요약에 붙이기 때문이다(레인 확인) — 가운데에
 * 끼우면 그 요약의 기존 토큰 자리가 밀린다.
 */
internal fun openingFinishLine(
    report: OpeningCollectionReport,
    exitCode: CollectionExitCode,
): String =
    "opening-collection finished frame=${report.frameSize} sampled=${report.sample.selected.size} " +
        "requested=${report.sample.requested} short=${report.sample.short} " +
        "strata=${report.sample.strata.size} sampleUnseen=${report.sampleUnseen} " +
        "detailCalls=${report.detailCalls} halted=${report.halted != null} exit=${exitCode.value} " +
        "skippedAgeGate=${report.skipped.ageGate} skippedRecheckGate=${report.skipped.recheckGate}"

/** 멈춤은 미완이다 — 예산이든 쿼터든 표본 전체를 받지 못했다. */
internal fun openingExitCodeOf(report: OpeningCollectionReport): CollectionExitCode =
    if (report.halted == null) CollectionExitCode.COMPLETE else CollectionExitCode.INCOMPLETE

/**
 * 원 예외를 잇지 않는다 — SQL 메시지에 행 값이, 전송 예외에 요청 URI(서비스 키)가 실릴 수 있다(D-6F8-4).
 *
 * **형식 거부는 사유 토큰으로 낸다**(D-6G2d-48 ①). 클래스 이름으로 떨어지면 운영자가 「옛 디렉터리인가
 * 손상인가」를 출력에서 가릴 수 없고, 그 구별이 처방을 가른다(폐기해도 되는가 / 사람이 봐야 하는가).
 * 토큰은 닫힌 어휘라 로그에 실어도 새는 값이 없다.
 */
internal fun openingCauseCodeOf(failure: Exception): String =
    runStateFormatCauseCode(failure)
        ?: when (failure) {
            is SQLException -> "${failure.javaClass.name}:sqlState=${failure.sqlState}"
            else -> failure.javaClass.name
        }
