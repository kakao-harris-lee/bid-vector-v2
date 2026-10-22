package bidvector.workflow.profile

import bidvector.strategy.WatchRules
import bidvector.workflow.evaluation.ProfileFacts
import java.time.LocalDate

/**
 * 시작하기 체크리스트 판정의 입력 — 저장된 원천 넷과 오늘 날짜(KST)다. 진행 상태 자체는 저장하지
 * 않는다(D-7C4-1 P1): 저장하면 원천과 어긋날 수 있는 둘째 사본이 생긴다.
 *
 * [qualification] 이 `null` 이면 V12 행이 없다(「프로필 미설정」 — `OperatorProfilePort.current()`
 * 와 같은 뜻). [today] 를 받는 이유는 인증서 만료 판정이 시각에 달려 있어서다 — 시계는 호출자가
 * 쥐고, 이 판정은 순수 함수로 남는다.
 */
data class OnboardingSnapshot(
    val qualification: ProfileFacts?,
    val details: BusinessDetails,
    val capacities: List<ConstructionCapacity>,
    val watchRules: WatchRules,
    val today: LocalDate,
)

/**
 * 체크리스트 항목 — **이 열거가 선언 표 자체다**(항목 · 적용 조건 · 완료 조건). 항목을 더하면
 * 조건 둘을 함께 적어야 컴파일되고, 판정 함수는 이 표를 읽기만 한다(CLAUDE.md §4.5-2·3).
 *
 * 넣지 않은 것(checklist §0.8): 사업자번호(슬롯 금지) · 알림 채널(R-4 · `OPEN-STR-12`) ·
 * 첫 공고 미리보기(7C① 검색 소관).
 *
 * [HEADQUARTERS_REGION] · [CONSTRUCTION_CAPACITY] 의 「완료」는 **입력이 있다**는 뜻일 뿐이다 —
 * 그 값으로 자격을 판정하는 경로는 아직 없다([UnjudgedQualificationAxis]).
 */
enum class OnboardingItem(
    internal val applies: (OnboardingSnapshot) -> Boolean,
    internal val completed: (OnboardingSnapshot) -> Boolean,
) {
    /** SET-01 대사의 운영자 쪽 입력. */
    TRADE_NAME(ALWAYS, { it.details.tradeName != null }),

    /**
     * 등록했고, 분야가 하나 이상이고, 사업자용 인증서가 오늘(KST)까지 유효하다. 「미등록」이라고
     * 답한 것도 미완이다 — 그 상태로는 투찰(③)이 안 된다.
     */
    NARA_READINESS(ALWAYS, ::naraReady),

    /** V12 행이 있고 업종이 하나 이상이다. */
    BUSINESS_TYPES(ALWAYS, { snapshot -> snapshot.qualification?.businessTypes?.isNotEmpty() == true }),

    /**
     * 면허 상태가 「미설정」이 아니다. `NotDeclared`(미신고)도 **완료**다 — 운영자가 명시적으로
     * 고른 상태이고, 이것을 미완으로 두면 「신고하라」고 몰아 세 상태가 납작해지는 쪽으로 기운다.
     * V12 행이 있으면 면허 상태가 있다(V12 의 `licenses_declared NOT NULL`) — 「미설정」은 행 없음뿐이다.
     */
    LICENSES(ALWAYS, { it.qualification != null }),

    /** QUAL-08 입력 — 판정 없음. */
    HEADQUARTERS_REGION(ALWAYS, { it.details.headquartersRegion != null }),

    /**
     * QUAL-11 입력 — 판정 없음. **공사로 입찰하는 계정에만 낸다**(D-7C4-1 Q1). 「공사로 입찰하는가」
     * 는 나라장터 등록분야의 공사로 잰다 — V12 업종은 자유 텍스트라 「공사인가」를 기계로 가를 수
     * 없다(checklist §1 해석 기록).
     */
    CONSTRUCTION_CAPACITY(::biddingOnConstruction, { it.capacities.isNotEmpty() }),

    /** 감시 금액대의 최소 · 최대 중 하나 이상(STR-01). */
    BUDGET_RANGE(ALWAYS, { it.watchRules.budget.min != null || it.watchRules.budget.max != null }),

    /** 감시 관심 업종 하나 이상(STR-01). */
    WATCH_CONDITIONS(ALWAYS, { it.watchRules.focusCategories.isNotEmpty() }),
}

/** 판정 결과 — 적용되는 항목만 두 목록 중 하나에 정확히 한 번 든다(선언 순서 유지). */
data class OnboardingChecklist(
    val completed: List<OnboardingItem>,
    val pending: List<OnboardingItem>,
)

/** 체크리스트 판정 — [OnboardingItem] 표를 읽기만 한다. 서버가 판정한다(요구표 :37, 클라이언트 추론 금지). */
fun judgeOnboardingChecklist(snapshot: OnboardingSnapshot): OnboardingChecklist {
    val (completed, pending) =
        OnboardingItem.entries
            .filter { it.applies(snapshot) }
            .partition { it.completed(snapshot) }
    return OnboardingChecklist(completed = completed, pending = pending)
}

private val ALWAYS: (OnboardingSnapshot) -> Boolean = { true }

private fun naraReady(snapshot: OnboardingSnapshot): Boolean {
    val readiness = snapshot.details.naraReadiness as? NaraReadiness.Registered ?: return false
    val expiresOn = readiness.businessCertificateExpiresOn
    return readiness.fields.isNotEmpty() && expiresOn != null && !expiresOn.isBefore(snapshot.today)
}

private fun biddingOnConstruction(snapshot: OnboardingSnapshot): Boolean =
    (snapshot.details.naraReadiness as? NaraReadiness.Registered)
        ?.fields
        ?.contains(NaraRegistrationField.CONSTRUCTION) == true
