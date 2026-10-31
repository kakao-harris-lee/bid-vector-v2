package bidvector.workflow.evaluation

/**
 * **아직 판정하지 않는 자격 축**(QUAL-08 지역 · QUAL-10 수행실적 · QUAL-11 시공능력평가금액) —
 * 입력 칸이 있든 없든 V2 에 판정 로직이 없는 축이다. 「판정했고 통과했다」와 「판정하지 않았다」를
 * 운영자가 구분할 수 있게 응답이 이 목록을 그대로 싣는다(자격 카드가 「면허만 보면 끝」으로
 * 읽히지 않게 한다).
 *
 * **이 enum 이 세 표면의 단일 출처다**(`D-7C2-1` Q5 · `D-7C2-6` · `D-7C2-11`). 7C ④ 의 프로필 표면과
 * 7C ①b 의 제안 카드가 각자 같은 이름의 사본을 들고 있었고, 그 둘이 먼저 머지된 뒤 이 slice 가
 * 하나로 합쳤다. 참조 방향은 **`profile`·`suggestion` → 이 패키지 한쪽뿐**이다 — 반대로 이 패키지가
 * `profile` 을 이름으로 참조하면 `ProfileJudgementIsolationTest` 의 이름 참조 단언이 붉어진다(그
 * test 가 막는 것이 정확히 그 참조다).
 *
 * 그 표면이 지키는 것은 「입력이 생겼다고 자격 판정이 그 값을 쓰는 것처럼 보이는」 표시다 —
 * 입력(프로필)과 판정(자격)이 다른 축임을 `ProfileJudgementIsolationTest` 가 네 겹으로 잠근다.
 *
 * 판정이 생기는 날 그 slice 가 해당 값을 이 enum 에서 **지워야** 한다 — 지우면 응답 계약 test 의
 * 집합 등식과 문서 enum 등식이 함께 붉어지므로 「판정이 생겼는데 아직 판정하지 않았다고 말하는」
 * 상태가 조용히 남지 않는다.
 */
enum class UnjudgedQualificationAxis(
    /** 추적표의 capability ID — 표시 어휘(`name`)와 장부 어휘를 한 값에 묶지 않는다. */
    val capability: String,
) {
    /** 지역 자격 — 구조화 지역제한 필드가 수집되지만 판정에 쓰이지 않는다. */
    QUAL_08_REGION("QUAL-08"),

    /** 수행실적 자격 — V2 에 판정이 없다(입력도 없다). */
    QUAL_10_PERFORMANCE("QUAL-10"),

    /** 시공능력평가금액 요건 — 입력만 서고 미달 처리가 미결(`OPEN-QUAL-09`·`OPEN-QUAL-10`). */
    QUAL_11_CONSTRUCTION_CAPACITY("QUAL-11"),
}
