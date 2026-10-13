package bidvector.workflow.retention

import bidvector.sharedkernel.EffectiveDatedPolicy
import bidvector.sharedkernel.EffectiveFrom
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 보존 정책 값(M6/6B-3 P-5) — 「90일」과 일괄 크기가 코드 리터럴로 흩어지지 않게 한 자리에
 * 모은다. 값의 정본은 **문서**다(`KONEPS_COLLECTION_POLICY`·`NOTIFICATION_DELIVERY_POLICY`
 * 관례) — 아래 [RETENTION_POLICY]의 `source` 가 그 자리를 가리킨다.
 *
 * [MINIMUM_RETENTION_DAYS] 는 **정책 값이 아니라 불변식**이다(설계 검토 우회 4): 보존 기간이
 * 0·음수면 cutoff 가 현재나 미래가 되어 「기한 안쪽 행은 한 행도 사라지지 않는다」가 즉시
 * 거짓이 된다. 그 입력은 받지 않는다 — 생성이 실패한다.
 */
data class RetentionPolicyData(
    val retentionDays: Int,
    val batchSize: Int,
) {
    init {
        require(retentionDays >= MINIMUM_RETENTION_DAYS) {
            "retentionDays 는 $MINIMUM_RETENTION_DAYS 이상이어야 한다: $retentionDays"
        }
        require(batchSize >= MINIMUM_BATCH_SIZE) {
            "batchSize 는 $MINIMUM_BATCH_SIZE 이상이어야 한다: $batchSize"
        }
    }

    /**
     * 기준 시각 — [RetentionCutoff] 를 만드는 **유일한 경로**다. 호출부가 `now` 를 cutoff 로
     * 쓰는 길(= 전부 파기)이 타입으로 막힌다(설계 검토 (2b) 「보존 저장소 포트」 행).
     */
    fun cutoffFrom(now: Instant): RetentionCutoff =
        RetentionCutoff(now.minus(retentionDays.toLong(), ChronoUnit.DAYS))

    companion object {
        /** 하루보다 짧은 보존은 받지 않는다 — 위 KDoc 의 불변식. */
        const val MINIMUM_RETENTION_DAYS = 1

        /** 일괄 0 은 영원히 끝나지 않는 루프다(한 번도 줄지 않는다). */
        const val MINIMUM_BATCH_SIZE = 1
    }
}

/**
 * 파기·마스킹 술어가 쓰는 기준 시각 — **생성자가 `internal`**이다(설계 검토 (2b)). `adapters`
 * 는 이 값을 **읽을 수만** 있고 만들 수 없으므로, 어댑터나 배선이 `Instant.now()` 를 cutoff
 * 로 넘겨 「전부 파기」를 부르는 경로가 컴파일되지 않는다. 만드는 자리는
 * [RetentionPolicyData.cutoffFrom] 하나이고 그 자리는 정책 불변식을 이미 지난 뒤다.
 */
@ConsistentCopyVisibility
data class RetentionCutoff internal constructor(
    val instant: Instant,
)

/**
 * **승인된 값이다.** 90일은 운영자 결정 2026-09-26 ②(「보존 기간 90일 — 공고·평가 이력·
 * audit」)이고 그 해석(어느 표가 파기·마스킹·제외인가)은 6B-3 운영자 결정 A-1~A-4 다.
 *
 * `batchSize` 는 운영자 결정이 아니라 **한 트랜잭션이 잠그는 행 수의 상한**이다 — 90일 치
 * 백로그를 한 문장으로 지우면 그 트랜잭션이 도는 동안 같은 표의 쓰기가 막힌다. 값을 바꾸는
 * 것은 거동이 아니라 배치 크기만 바꾼다(파기 집합은 cutoff 가 정한다).
 */
val RETENTION_POLICY: EffectiveDatedPolicy<RetentionPolicyData> =
    EffectiveDatedPolicy(
        source = "milestone-6.md 운영자 결정 2026-09-26 ② · reports/evidence/m6/6b3/scope.md A-1~A-4",
        entries =
            listOf(
                EffectiveFrom.Initial to
                    RetentionPolicyData(
                        retentionDays = 90,
                        batchSize = 1_000,
                    ),
            ),
    )
