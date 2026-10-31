package bidvector.app.http

import bidvector.workflow.profile.ProfileWriteOutcome
import bidvector.workflow.profile.StorageWriteOutcome
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

/**
 * 프로필 쓰기 결과 → 응답. **두 controller 가 공유하는 유일한 매핑표**다(`BidCaseHttp` 와 같은 규율) —
 * 각자 매핑을 들면 같은 사유가 다른 코드로 나가는 길이 열린다.
 *
 * 규율:
 *  · 저장 층의 제약 거부는 **400** 이다 — 요청 값이 표의 불변식을 어겼다는 뜻이고, 어느 값이 틀렸는지는
 *    **싣지 않는다**(고정 문구 하나). 드라이버 문면은 여기까지 오지도 않는다(어댑터가 값으로 바꾼다).
 *    **외래 키 거부(`23503`)도 여기로 온다** — 그 사유는 「요청 값이 틀렸다」가 아니라 **세션이 사는 동안
 *    계정 행이 사라졌다**이다(계정 삭제). 400 이 의미상 경계인 자리이고, 운영자에게는 다시 로그인하라는
 *    401 이 더 맞지만 그 판단은 세션 층의 몫이라 여기서 사유를 나누지 않는다(r2 CR L-7).
 *  · 행 수 상한은 **409** 다 — 요청 자체는 올바르고 **계정의 상태가 받지 않는** 것이다(7B 의 전이 거부와
 *    같은 축). 상한 값은 본문에 싣지 않는다(`ErrorBody` 는 세 필드다).
 *  · 쓰기가 성공하면 **다시 읽은 자원**을 낸다 — 화면이 서버 판정을 그 자리에서 받는다.
 */
internal object ProfileHttp {
    /** 전수 `when` 이라 갈래가 늘면 컴파일이 누락을 잡는다. */
    fun toResponse(
        outcome: ProfileWriteOutcome,
        request: HttpServletRequest,
        written: () -> ResponseEntity<Any>,
    ): ResponseEntity<Any> =
        when (outcome) {
            StorageWriteOutcome.Written -> {
                written()
            }

            is StorageWriteOutcome.RejectedByStorage -> {
                ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ErrorBody(ErrorCode.INVALID_REQUEST, "요청 값이 유효하지 않다", request.correlationIdOrUnknown()))
            }

            is ProfileWriteOutcome.RowLimitExceeded -> {
                ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(
                        ErrorBody(
                            ErrorCode.CAPACITY_ROWS_EXCEEDED,
                            "시평액 행 수 상한을 넘었다",
                            request.correlationIdOrUnknown(),
                        ),
                    )
            }
        }

    /** 지울 행이 없었다 — 404. 다른 계정의 같은 면허명은 **없는 것과 같다**(존재 여부를 흘리지 않는다). */
    fun capacityNotFound(request: HttpServletRequest): ResponseEntity<Any> =
        ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorBody(ErrorCode.NOT_FOUND, "대상을 찾을 수 없다", request.correlationIdOrUnknown()))
}
