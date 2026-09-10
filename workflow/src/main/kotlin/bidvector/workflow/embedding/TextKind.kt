package bidvector.workflow.embedding

/**
 * 임베딩 대상 텍스트의 출처 축(scope.md ①, `embedding.proto` `TextKind` 미러) — 미지정을
 * 표현할 수 없다(2B `OptimizationObjective` 관례와 같은 이유, enum 에 UNSPECIFIED 가지가
 * 없다). 합성 규약 자체(어느 fact 를 어떤 순서로 문장에 넣는가)는 이 slice 가 닫지 않는다
 * (`OPEN-2E-TEXT-SYNTHESIS`, 4B-6 소관) — 이 타입은 어느 대상의 텍스트인지만 나른다.
 */
enum class TextKind {
    NOTICE,
    OPERATOR_PROFILE,
}
