package bidvector.adapters.access

import bidvector.sharedkernel.Resolution
import bidvector.workflow.access.SIGN_UP_POLICY
import bidvector.workflow.access.SignUpPolicyData
import java.time.LocalDate

/**
 * 접근 어댑터 test 가 쓰는 **가입 정책 한 자리**(M15/15B 수정 라운드 1).
 *
 * 값을 test 마다 베껴 적던 앞 판은 같은 블록이 두 파일에 있었고, 그 가운데 하나가
 * `AccessConcurrencyTest` 를 크기 한도(파일 500줄) 위로 밀었다. **출하 정책을 그대로 읽는다** —
 * 경계값을 test 에 적으면 정책을 바꾼 날 둘이 갈리고, 그때 그 test 는 바뀐 값이 아니라 옛 값을
 * 지킨다.
 *
 * 이 두 class 가 재는 축은 SQL·열·동시성이고 가입 정책 값이 아니다(그 축은
 * `bidvector.workflow.access.SignUpPolicyDataTest` 가 든다) — 여기서는 조립에만 선다.
 */
internal fun shippedSignUpPolicy(): SignUpPolicyData =
    (SIGN_UP_POLICY.resolve(LocalDate.of(2026, 11, 21)) as Resolution.Resolved<SignUpPolicyData>).value
