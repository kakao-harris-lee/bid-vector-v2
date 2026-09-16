# 재활용 조사 — M4/4D-3

N/A — 이 slice 는 Python ML 코드나 외부 라이브러리로 해결되는 문제를 다루지 않는다
(순수 Kotlin 도메인 타입·계약 파싱). 재활용 우선 방침은 이 slice 밖(Python 이식은
마일스톤 5 `ml-implementer` 소관).

참고로 기존 Kotlin 관례를 새로 만들지 않고 그대로 따랐다(중복 금지 규율의 이행,
「재활용」과는 다른 축): `Weight` [0,1] 값 타입은 기존 `Rate`·`Uncertainty` 값 타입의
`init` 방어 패턴을, `toWeightOrNull()`은 `toRateOrNull()`의 범위 사전 검사 패턴을,
`DiagnosticsShapeValidation.kt`는 `ReleaseShapeValidation.kt`·`CandidateShapeValidation.kt`
의 파일 분리·검증층 패턴을, `ProtoSegmentSupport.toDomainOrNull()`은 `ProtoIntervalSource.
toDomainOrNull()`의 fail-closed enum 매핑 패턴을 그대로 재사용했다.
