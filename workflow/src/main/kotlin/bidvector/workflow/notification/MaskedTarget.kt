package bidvector.workflow.notification

/**
 * 결과가 나르는 target 표시(scope.md ③, 설계 검토 (1)) — `internal constructor`, 유일한
 * 생성이 [mask] factory다. 원문 accessor가 없고 `toString()`도 마스킹 값이다(원문이
 * 어디에도 노출되지 않는다, NOTI-03). fragment 인식이 아니라 끝 `maskedSuffixLength`자
 * 외 전부를 덮는다(조사 (c-5) — 정규식 넷의 관행이 사각을 남겼다).
 */
@ConsistentCopyVisibility
data class MaskedTarget internal constructor(
    private val masked: String,
) {
    override fun toString(): String = masked

    companion object {
        private const val MASK_CHAR = '*'

        fun mask(
            raw: CharSequence,
            policy: NotificationDeliveryPolicyData,
        ): MaskedTarget {
            val suffixLength = policy.maskedSuffixLength
            // 길이 ≤ suffix면 전부 별표(설계 검토 (1)) — 「끝 n자만 남긴다」를 그대로 적용하면
            // 짧은 원문은 통째로 노출되므로, 그 경계에서는 노출 대신 전부 덮는다.
            if (raw.length <= suffixLength) {
                return MaskedTarget(MASK_CHAR.toString().repeat(raw.length))
            }
            val keep = raw.takeLast(suffixLength)
            val hidden = MASK_CHAR.toString().repeat(raw.length - suffixLength)
            return MaskedTarget(hidden + keep)
        }
    }
}
