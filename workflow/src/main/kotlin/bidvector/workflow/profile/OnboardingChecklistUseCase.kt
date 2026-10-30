package bidvector.workflow.profile

import bidvector.workflow.access.AccountId
import bidvector.workflow.evaluation.OperatorProfilePort
import bidvector.workflow.strategy.Clock
import bidvector.workflow.strategy.StrategyReader
import java.time.ZoneId

/**
 * 체크리스트가 세는 **하루의 경계** — 운영자의 달력일(KST)이다.
 *
 * 커널이 `today` 를 인자로 받는 이유가 이 값이다(시계는 호출자가 쥔다, [OnboardingSnapshot]):
 * 인증서 만료 판정이 「오늘」에 달려 있고 그 오늘은 **운영자가 보는 날짜**여야 한다 — UTC 로 세면
 * KST 00~09 시에 어제 날짜를 쓰게 되고, 만료 당일 밤의 계정이 하루 먼저 「만료」로 보인다.
 * 보관 기한(`EVIDENCE_RETENTION_ZONE`)이 같은 이유로 같은 구역을 든다.
 */
val ONBOARDING_CALENDAR_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

/**
 * 시작하기 체크리스트 조회(M7/7C④-3) — 저장된 원천 넷을 모아 [judgeOnboardingChecklist] 에 넘긴다.
 *
 * **판정 의미는 여기에 없다**: 항목 표도 적용 조건도 완료 조건도 커널의 것이고, 이 use case 가 하는
 * 일은 원천을 모으고 시계를 달력일로 접는 것뿐이다. 진행 상태를 저장하지 않는다(D-7C4-1 P1) —
 * 매 요청이 원천에서 다시 판정한다.
 *
 * **원천의 키 체계가 둘**이라는 2a 의 대가가 여기서도 보인다(`ReadProfileUseCase` 와 같은 자리):
 * [qualification] 과 [strategies] 는 싱글턴에서 오고(계정을 받지 않는다) 사업 정보만 계정 소유다.
 * 그 비대칭을 숨기지 않는다 — V12 · 전략이 계정 단위로 옮겨 가면(R-2 남은 절반) port 만 바뀌고 이
 * 타입은 그대로다.
 */
class ReadOnboardingChecklistUseCase(
    private val qualification: OperatorProfilePort,
    private val business: BusinessProfileReader,
    private val strategies: StrategyReader,
    private val clock: Clock,
) {
    fun read(account: AccountId): OnboardingChecklist =
        judgeOnboardingChecklist(
            OnboardingSnapshot(
                qualification = qualification.current(),
                details = business.details(account),
                capacities = business.capacities(account),
                watchRules = strategies.load().watchRules,
                today = clock.now().atZone(ONBOARDING_CALENDAR_ZONE).toLocalDate(),
            ),
        )
}
