package bidvector.adapters.qualification

import bidvector.procurement.Notice
import bidvector.qualification.LicenseEligibility
import bidvector.qualification.LicenseQualificationPolicyData
import bidvector.qualification.LicenseVerdict
import bidvector.qualification.OperatorLicenses
import bidvector.sharedkernel.Resolution
import bidvector.workflow.evaluation.LicenseGatePort
import bidvector.workflow.evaluation.OperatorProfilePort

/**
 * [LicenseGatePort]의 첫 production 구현(D-6F5-2, D-6F5-3) — **저장된 요건만 읽는다.** LLM
 * 재추출을 부르지 않는다(scope.md 착수 조사 ①②③, D-6F5-1의 분할 근거). 판정은
 * [LicenseEligibility.judge] 하나가 낸다 — 이 클래스는 `LicenseVerdict`를 직접 조립하지
 * 않는다(scope.md 우회 2, `QualificationAdapterDependencyTest`의 참조 단언이 이 호출
 * 자리를 잠근다).
 *
 * **D-6F5-3 — [OperatorLicenses]는 [OperatorProfilePort] 주입으로 받는다.** 그 port의 실
 * 구현(6F-6)에 직접 의존하지 않는다 — 진행 중 다른 slice의 미병합 브랜치에 이
 * slice가 얹히지 않는다. 프로필이 미설정(`current()`가 `null`)이면
 * [OperatorLicenses.NotDeclared]로 낸다 — 커널이 그 값을
 * `Uncertain(OperatorLicensesNotDeclared)`로 옮긴다(U-5, `Uncertain ≠ Ineligible`).
 *
 * **D-6F5-5 — 정책은 생성자 주입이다.** [policy]는 이미 해소된 [Resolution.Resolved]다 —
 * 이 클래스는 `bidvector.qualification.LICENSE_QUALIFICATION_POLICY`를 참조하지 않는다
 * (어댑터가 정책 파일의 두 번째 독자가 되지 않는다, 6F-1 D-6F1-6과 같은 규율).
 */
class StoredRequirementLicenseGate(
    private val store: JdbcRequirementStore,
    private val operatorProfilePort: OperatorProfilePort,
    private val policy: Resolution.Resolved<LicenseQualificationPolicyData>,
) : LicenseGatePort {
    /**
     * **M7/7C ② — 위임을 되돌렸다(거동은 7C ② 앞과 같다 — 남은 diff 는 이 KDoc 뿐이다).** 7C ② 가 봉투를 내는 포트를 더하면서 한때 이
     * 게이트가 그 어댑터에 위임하게 바꿨는데, `QualificationAdapterDependencyTest` 가
     * **이 클래스의 컴파일된 코드가 `LicenseEligibility` 를 참조한다**는 사실을 아키텍처 게이트로
     * 들고 있었다(실측으로 걸렸다). 그 앵커는 「어댑터가 verdict 를 스스로 조립하지 않는다」를
     * 증명하는 자리라 내 중복 제거를 위해 바꿀 것이 아니다 — 세 줄을 두 어댑터가 각자 들고,
     * 둘이 같은 판정을 내는지는 실 DB test 의 verdict 등식이 잰다(그 test 가 중복의 감시자다).
     */
    override fun verdictFor(notice: Notice): LicenseVerdict {
        val collection = store.find(notice.id)
        val operatorLicenses = operatorProfilePort.current()?.licenses ?: OperatorLicenses.NotDeclared
        return LicenseEligibility.judge(collection, operatorLicenses, policy).verdict
    }
}
