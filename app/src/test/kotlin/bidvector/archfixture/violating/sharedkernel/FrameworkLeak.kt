package bidvector.archfixture.violating.sharedkernel

import org.springframework.core.io.ClassPathResource

/** domain 모듈이 Spring 타입을 참조한다 — v2-지침서.md §3.1 위반. */
class FrameworkLeak {
    fun leak(): ClassPathResource = ClassPathResource("게이트가 잡으므로 읽히지 않는다")
}
