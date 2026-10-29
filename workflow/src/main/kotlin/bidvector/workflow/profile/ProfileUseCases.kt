package bidvector.workflow.profile

import bidvector.qualification.LicenseName
import bidvector.workflow.access.AccountId
import bidvector.workflow.evaluation.OperatorProfilePort
import bidvector.workflow.evaluation.OperatorProfileStorePort
import bidvector.workflow.evaluation.ProfileFacts
import bidvector.workflow.evaluation.UnjudgedQualificationAxis
import java.time.Instant

/**
 * 프로필 조회가 내는 것(M7/7C④ 2b) — **V12 자격 사실 + V22 사업 정보 + 판정 대기 축**이다.
 *
 * 두 저장소가 키 체계가 다르다는 2a 의 대가(checklist §0.2)가 여기서 보인다: [qualification] 은
 * 싱글턴 V12 에서 오고(계정을 받지 않는다) 나머지는 계정 소유 V22 에서 온다. 그 비대칭을 숨기지
 * 않는다 — 별 slice 가 V12 를 계정 단위로 옮기면 이 타입은 그대로 두고 port 만 바뀐다.
 *
 * [qualification] 이 `null` 이면 「프로필 미설정」이다(V12 행 없음). [unjudgedAxes] 는 **이 use case 가
 * 정하는 목록**이고 HTTP 응답이 그것을 그대로 싣는다(순서까지) — 오늘은 선언 표 전체이고, 판정이 생긴
 * 축을 거르는 자리가 생기면 **여기만** 고치면 wire 가 따라온다(r2 v F7).
 */
data class ProfileView(
    val qualification: ProfileFacts?,
    val details: BusinessDetails,
    val capacities: List<ConstructionCapacity>,
    val unjudgedAxes: List<UnjudgedQualificationAxis>,
)

/** 조회 — 세 원천을 모아 한 번에 낸다. 계정은 **호출자가 세션에서 꺼낸 값**이다(2b 표적 2). */
class ReadProfileUseCase(
    private val qualification: OperatorProfilePort,
    private val business: BusinessProfileReader,
) {
    fun read(account: AccountId): ProfileView =
        ProfileView(
            qualification = qualification.current(),
            details = business.details(account),
            capacities = business.capacities(account),
            unjudgedAxes = UnjudgedQualificationAxis.entries,
        )
}

/**
 * 시평액 목록만 — 조회 표면이 둘인 이유는 **평탄 응답 규칙**이다(중첩 배열이 들어간 object 를 내지
 * 않는다). 프로필 전체를 읽어 그 일부만 버리면 V12 질의가 공짜로 한 번 더 돈다.
 */
class ListConstructionCapacitiesUseCase(
    private val business: BusinessProfileReader,
) {
    fun list(account: AccountId): List<ConstructionCapacity> = business.capacities(account)
}

/**
 * 자격 사실(V12 — 업종 · 면허 · 지역어) 절 교체. **계정을 받지 않는 port 에 계정을 넘기지 않는다**
 * — V12 는 아직 싱글턴이고, 받는 척하면 「이미 계정 단위다」라는 거짓 신호가 된다(2a §0.2 대가).
 * 그래서 이 use case 도 [AccountId] 를 받지 않는다. 계정 단위 전환(R-2 남은 절반)이 그 자리를 연다.
 */
class ReplaceQualificationFactsUseCase(
    private val store: OperatorProfileStorePort,
) {
    fun replace(facts: ProfileFacts) = store.save(facts)
}

/** 사업 정보(V22) 절 교체 — 절 하나를 통째로 바꾼다(부분 갱신이 다른 절을 지우지 않는다, D-7C4-1 P5). */
class ReplaceBusinessDetailsUseCase(
    private val store: BusinessProfileStorePort,
    private val clock: () -> Instant,
) {
    fun replace(
        account: AccountId,
        details: BusinessDetails,
    ): StorageWriteOutcome = store.replaceDetails(account, details, clock())
}

/**
 * 시평액 한 행 upsert. **행 수 상한을 앱에서 집행한다**(D-7C4-8 Q1) — 표에는 행 수 CHECK 가 없다.
 *
 * **알려진 제한(그 결정이 받아들인 것)**: 센 뒤 쓰는 사이는 **경합한다** — 두 요청이 상한 직전의 같은
 * 수를 읽으면 둘 다 통과해 상한을 하나 넘길 수 있다. 단일 운영자 전제 위에서 받아들인다(2a 알려진
 * 제한 1 과 같은 축). 표 층으로 닫으려면 트리거나 부분 제약이고 그것은 V23 을 끌어온다 — 하지 않는다.
 *
 * **이미 있는 면허명의 갱신은 상한에 걸리지 않는다** — 행 수가 늘지 않기 때문이다.
 */
class UpsertConstructionCapacityUseCase(
    private val store: BusinessProfileStorePort,
    private val clock: () -> Instant,
) {
    fun upsert(
        account: AccountId,
        capacity: ConstructionCapacity,
    ): ProfileWriteOutcome {
        val existing = store.capacities(account)
        val addsRow = existing.none { it.licenseName == capacity.licenseName }
        if (addsRow && existing.size >= ProfileInputPolicy.CAPACITY_ROWS_MAX) {
            return ProfileWriteOutcome.RowLimitExceeded(ProfileInputPolicy.CAPACITY_ROWS_MAX)
        }
        return store.upsertCapacity(account, capacity, clock())
    }
}

/** 시평액 한 행 삭제 — 지운 행이 없으면 `false`(404 로 가른다). 다른 계정의 같은 면허명에 닿지 않는다. */
class RemoveConstructionCapacityUseCase(
    private val store: BusinessProfileStorePort,
) {
    fun remove(
        account: AccountId,
        licenseName: LicenseName,
    ): Boolean = store.removeCapacity(account, licenseName)
}
