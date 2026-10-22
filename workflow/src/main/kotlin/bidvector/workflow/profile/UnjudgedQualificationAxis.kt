package bidvector.workflow.profile

/**
 * **입력은 섰지만 판정은 아직 없는 자격 축**(M7 완료 조건 G2 — QUAL-08 · QUAL-11 은 「7C ④ 입력이
 * 서고 『아직 판정하지 않음』 표시가 test 로 잠긴다」). 프로필 조회 응답이 이 표를 그대로 싣는다.
 *
 * 이 표가 지키는 것은 「입력이 생겼다고 자격 판정이 그 값을 쓰는 것처럼 보이는」 표시다. 그래서
 * 두 겹으로 잠근다: (1) 이 표에 두 축이 있다는 것, (2) `workflow` 의 이 패키지 밖 어느 클래스도
 * 이 패키지를 참조하지 않는다는 것(`ProfileJudgementIsolationTest` — 판정 경로가 몰래 입력을 읽기
 * 시작하면 붉어진다). 판정을 세우는 slice 가 이 표에서 그 축을 빼고 그 test 를 함께 고친다.
 *
 * QUAL-11 의 입력 축은 **시평액만**이다 — 수행실적(QUAL-10)은 받지 않는다(D-7C4-1 Q2).
 */
enum class UnjudgedQualificationAxis(
    val capability: String,
) {
    /** 본사 소재지 시·도 — 구조화 지역제한 필드로 판정하는 축이 아직 없다. */
    QUAL_08_REGION("QUAL-08"),

    /** 면허별 시평액 — 공고 요건과의 대조는 `OPEN-QUAL-09` · `OPEN-QUAL-10` 이 열려 있다. */
    QUAL_11_CONSTRUCTION_CAPACITY("QUAL-11"),
}
