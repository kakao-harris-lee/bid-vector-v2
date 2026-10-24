package bidvector.workflow.profile

/**
 * **판정이 아직 없는 자격 축** — 입력 칸이 있든 없든 V2 에 판정 로직이 없는 축이다(M7 완료 조건 G2
 * 는 QUAL-08 · QUAL-11 을 「7C ④ 입력이 서고 『아직 판정하지 않음』 표시가 test 로 잠긴다」로 적는다).
 * 프로필 조회 응답이 이 표를 그대로 싣는다.
 *
 * 이 표가 지키는 것은 「입력이 생겼다고 자격 판정이 그 값을 쓰는 것처럼 보이는」 표시다.
 * `ProfileJudgementIsolationTest` 가 세 겹으로 잠근다 — 이 표의 축과 ID · 판정 쪽의 **이름 참조**
 * (빗금 · 점 두 표기) · 이 패키지가 판정 쪽 타입을 **구현 · 상속하지 않음**(의존 역전). `adapters`
 * 쪽의 같은 위협(V12 판정 입력 port 의 구현이 새 입력을 접는다)은 `ProfileAdapterDependencyTest` 가
 * class 단위로 막는다. 그 두 test 의 KDoc 이 **재는 범위와 재지 않는 범위**를 적는다. 판정을 세우는
 * slice 가 이 표에서 그 축을 빼고 그 test 들을 함께 고친다.
 *
 * **ID 는 7C ② 의 자격 카드와 바이트 동일하다**(팀장 결정 `D-7C2-1` Q5 — 두 표면이 한 어휘를 쓴다).
 * 7C ② 는 같은 이름의 열거를 `workflow.evaluation` 에 두었고 그쪽이 단일 출처다. **뒤에 머지되는
 * 쪽이 자기 리터럴을 지우고 상대를 참조하는데, 그 방향은 이 패키지 → `evaluation` 뿐이다** — 반대로
 * `evaluation` 이 이 패키지를 참조하면 위 (2) 의 격리 단언이 붉어진다(그 test 가 막는 것이 바로 그
 * 참조다).
 *
 * QUAL-10 은 이 slice 가 **입력을 받지 않는다**(D-7C4-1 Q2 — 수행실적은 capability 근거 부족).
 * 그래도 드는 이유는 이 표가 「입력이 선 축」이 아니라 「판정이 없는 축」이기 때문이다. 판정 없는
 * 축을 빼면 운영자에게 「면허만 보면 끝」으로 읽힌다.
 */
enum class UnjudgedQualificationAxis(
    val capability: String,
) {
    /** 본사 소재지 시·도 — 구조화 지역제한 필드로 판정하는 축이 아직 없다. */
    QUAL_08_REGION("QUAL-08"),

    /** 수행실적 — 입력도 판정도 없다. */
    QUAL_10_PERFORMANCE("QUAL-10"),

    /** 면허별 시평액 — 공고 요건과의 대조는 `OPEN-QUAL-09` · `OPEN-QUAL-10` 이 열려 있다. */
    QUAL_11_CONSTRUCTION_CAPACITY("QUAL-11"),
}
