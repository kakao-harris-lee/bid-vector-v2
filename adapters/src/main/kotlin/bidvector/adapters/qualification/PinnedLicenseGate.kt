package bidvector.adapters.qualification

import bidvector.procurement.Notice
import bidvector.qualification.LicenseVerdict
import bidvector.workflow.access.AccountId
import bidvector.workflow.evaluation.LicenseGatePort

/**
 * 이미 내린 자격 판정을 고정해 돌려주는 [LicenseGatePort](M7/7C ②, `PinnedStrategyRepository`
 * 관례) — **스스로 판정하지 않는다.** 생성자가 받는 것은 판정 결과이고, 판정에 필요한 요건
 * 저장소·프로필·정책 가운데 어느 것도 이 클래스에 없다.
 *
 * 쓰는 자리: 단건 평가 조립이 `LicenseJudgementPort` 로 봉투를 **한 번** 얻고, 그 `verdict` 를
 * 이 데코레이터로 감싸 커널에 넘긴다. 그래서 응답이 싣는 근거와 커널이 판정에 쓴 verdict 가
 * **같은 값**이다 — 둘이 갈릴 자리가 없다(같은 사실을 두 자리에 두지 않는다).
 */
class PinnedLicenseGate(
    private val account: AccountId,
    private val verdict: LicenseVerdict,
) : LicenseGatePort {
    /**
     * **M15/15A — 고정한 계정이 아니면 크게 실패한다**([PinnedStrategyRepository] 와 같은 사유):
     * 계정 인자를 조용히 무시하면 남의 계정의 자격 판정을 그대로 쓰는 조립이 거동 없이 통과한다.
     * 공고는 여전히 보지 않는다 — 후보 소스가 1원소라 물어올 공고가 하나뿐이다.
     */
    override fun verdictFor(
        account: AccountId,
        notice: Notice,
    ): LicenseVerdict {
        check(account == this.account) {
            "고정 게이트는 고정한 계정의 판정만 돌려준다 — 고정=${this.account.value} 요청=${account.value}"
        }
        return verdict
    }
}

/*
 * **이 파일이 `adapters.qualification` 에 있는 이유**(M7/7C ②): `LicenseVerdict` 를 이름으로 들기
 * 때문이다. `adapters.evaluation` 의 의존 게이트는 그 패키지가 `bidvector.qualification` 좌표를
 * 참조하지 못하게 막는다(실측으로 걸렸다) — 자격 어휘를 쥐는 어댑터는 자격 패키지에 산다.
 */
