package bidvector.workflow.opening

/**
 * 진행구분 라벨 → [OpeningProgress] 사상(versioned policy data, M7/7C⑤-a).
 *
 * **왜 데이터인가**: 라벨은 상류(조달청)의 어휘다. 코드 분기로 적으면 매직 문자열이 되고, 어휘가
 * 늘 때 고칠 자리가 분기마다 흩어진다. 등재 라벨 셋의 정본은 조달청 참고자료의 진행구분코드명
 * 항목이고(*"진행구분이 유찰, 개찰완료, 재입찰로 구분 됨"*, provenance `authoritative` — M3/3A
 * 정책 값 문서의 그 행), 이 데이터는 그 셋을 그대로 옮긴다.
 *
 * **등재 밖은 통과가 아니다** — [progressOf] 가 [OpeningProgress.UNRECOGNIZED] 를 돌려준다.
 * 모르는 라벨을 「개찰완료」로 접으면 상류 어휘 변화가 조용히 정상으로 보이고, 그 조용함은
 * 유찰을 낙찰로 보이게 할 수 있다.
 *
 * **앞뒤 공백을 걷지 않는다**(리터럴 등식). canonical 값을 정규화해 넣는 것은 승격 경로의 몫
 * 이다(7C⑤-0) — 조회가 관대해지면 저장된 값이 무엇인지 아무도 모르게 된다.
 */
data class OpeningProgressPolicyData(
    val version: String,
    val labels: Map<String, OpeningProgress>,
) {
    init {
        require(version.isNotBlank()) { "정책 version 은 비어 있을 수 없다" }
        require(labels.isNotEmpty()) { "등재 라벨이 하나도 없는 정책은 성립하지 않는다" }
        require(labels.keys.none(String::isBlank)) { "빈 라벨은 등재할 수 없다" }
        // 「부재」와 「등재 밖」은 라벨이 만드는 값이 아니라 라벨의 **부재·미등재**가 만드는 값이다.
        // 그 둘을 라벨에 배정하면 등재된 라벨이 곧 판정 불가가 되어 열거가 자기를 지운다.
        require(labels.values.none { it == OpeningProgress.UNRECOGNIZED || it == OpeningProgress.ABSENT }) {
            "UNRECOGNIZED·ABSENT 는 라벨에 배정할 수 없다"
        }
    }

    /** `null`(열이 비었다) → [OpeningProgress.ABSENT], 등재 밖 → [OpeningProgress.UNRECOGNIZED]. */
    fun progressOf(label: String?): OpeningProgress =
        if (label == null) {
            OpeningProgress.ABSENT
        } else {
            labels[label] ?: OpeningProgress.UNRECOGNIZED
        }
}

/** 운영 정책 값 — 등재 라벨 셋(위 KDoc 의 정본 문면). */
val OPENING_PROGRESS_POLICY =
    OpeningProgressPolicyData(
        version = "opening-progress-v1",
        labels =
            mapOf(
                "개찰완료" to OpeningProgress.OPENED,
                "유찰" to OpeningProgress.FAILED_BID,
                "재입찰" to OpeningProgress.REBID,
            ),
    )
