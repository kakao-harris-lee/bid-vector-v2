package bidvector.workflow.bidding

import bidvector.sharedkernel.EffectiveDatedPolicy
import bidvector.sharedkernel.EffectiveFrom
import java.time.Period
import java.time.ZoneId

/**
 * 제출확인서 **참조**의 보관 기간(Q-12 · 운영자 결정 R-5) — 값은 **3년**이다.
 *
 * **「파일을 언제 지우는가」가 아니다.** PDF 업로드 · 저장소 · 파기 실행은 이 slice 밖이고
 * (계약 산출물 3), 그래서 이 값이 정하는 것은 「이 **참조**를 언제까지 보관하는가」다. 저장소
 * slice 가 열리면 같은 날짜를 파일 파기 기한으로 승계한다(알려진 제한 ③).
 *
 * `EffectiveDatedPolicy` 관례를 따르는 이유: Q-12 가 *"기간 값은 정책 데이터로 두고 여기서
 * 숫자를 정하지 않는다"* 라 적는다 — 값이 바뀌면 **옛 기록의 기한이 소급해 움직이지 않아야**
 * 하고, 그것을 효력일이 진다.
 */
data class EvidenceRetentionPolicyData(
    val retainFor: Period,
) {
    init {
        require(!retainFor.isNegative && !retainFor.isZero) { "보관 기간은 양수여야 한다: $retainFor" }
    }
}

/** 보관 기한을 세는 달력 구역 — 날짜 경계가 운영자의 하루와 같아야 한다(KST). */
val EVIDENCE_RETENTION_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

/**
 * **정본은 코드가 아니라 문서다**(`BROWSER_ACCESS_POLICY`·`EDIT_SESSION_POLICY` 관례) —
 * 값을 바꾸려면 `source` 의 문서를 먼저 갱신한다.
 */
val EVIDENCE_RETENTION_POLICY: EffectiveDatedPolicy<EvidenceRetentionPolicyData> =
    EffectiveDatedPolicy(
        source = "milestone-7.md R-5 — 제출확인서 보관 3년(운영자 결정 2026-10-21, 증빙 가치)",
        entries =
            listOf(
                EffectiveFrom.Initial to EvidenceRetentionPolicyData(retainFor = Period.ofYears(3)),
            ),
    )
