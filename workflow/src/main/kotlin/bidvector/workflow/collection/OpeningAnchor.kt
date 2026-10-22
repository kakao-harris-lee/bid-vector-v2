package bidvector.workflow.collection

import java.time.Instant

/**
 * 표본 공고의 **개찰 일시** — 조회 가치 술어의 age-gate 앵커다(COL-03 둘째 bullet).
 *
 * 값이 아니라 **선언**인 이유(D-G1C03-1, 운영자 결정 (나)): 지금 6G 표본틀은 이 값을 **싣지 못한다.**
 * 개찰결과 목록의 개찰 일시 키는 계약 행이 그 끝점을 선언하지 않아 어댑터의 allow-list 가 지우고,
 * 실개찰 일시는 6G 가 걷지 않는 갈래와 **게이트가 막으려는 상세 호출 자신**에만 있다. 그 사실을 조용한
 * `null` 로 두면 「이 경로는 아직 싣지 않는다」와 「이 공고만 값이 없다」와 「호출부가 상수를 넘겼다」가
 * 모두 같은 값이 되고, 그 구별이 사라진 자리가 바로 G1-T 가 거짓으로 판정한 자리다.
 *
 * 별 slice 「개찰 축 끝점 범위 필드 행」이 값을 세우면 **표본틀의 한 줄**만 [Observed] 로 바뀐다. 읽을 수
 * 없는 값의 처분(건너뜀 + 별도 계수)도 그 slice 가 셋째 갈래로 더한다 — 실을 수 없는 지금 그 갈래를
 * 미리 만들면 **아무 공고도 지나지 않는 분기**가 된다.
 */
internal sealed interface OpeningAnchor {
    data class Observed(
        val at: Instant,
    ) : OpeningAnchor

    data object NotCarriedByFrame : OpeningAnchor
}

/**
 * 술어에 넘길 값 — 싣지 않는 경로는 **age 판정을 하지 않는다**(건너뛰는 것은 판정이지 호출이 아니다).
 * 소진 `when` 이라 갈래가 하나 늘면 컴파일이 이 자리를 가리킨다.
 */
internal fun OpeningAnchor.observedAt(): Instant? =
    when (this) {
        is OpeningAnchor.Observed -> at
        OpeningAnchor.NotCarriedByFrame -> null
    }
