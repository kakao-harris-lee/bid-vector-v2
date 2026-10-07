package bidvector.app.wiring

import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * **일회 러너는 한 프로세스에 하나뿐이다**(PR #63 finding 1) — 둘 이상이면 **기동 실패**다.
 *
 * 무엇이 문제였나: 일회 러너는 끝나면 `CollectionTermination` 으로 종료 코드를 내고 JVM 을
 * 끝낸다. 그래서 `bidvector.relay.mode=once` 와 `bidvector.evaluation.mode=once` 를 함께
 * 켜면 **먼저 끝난 러너가 JVM 을 끝내고 나머지는 조용히 안 돈다** — 운영자는 cron 이 둘을
 * 돌렸다고 믿는데 실제로는 하나만 돈다. 수집 러너들과 섞어도 같다.
 *
 * **세는 쪽만 둔다** — 수집 레인 코드(`app/collection` 아래)는 건드리지 않는다(in_scope 밖).
 * 그래서 이 guard 는 러너의 **종류를 모른다**: `ApplicationRunner` 빈 정의가 둘 이상이라는
 * 사실만 본다. 그 무지가 장점이다 — 뒤에 어느 레인이 러너를 더해도 같은 자리에서 걸린다.
 *
 * **왜 `BeanFactoryPostProcessor` 인가**: 빈 **정의**를 세므로 러너를 하나도 **만들지 않고**
 * 실패한다. `List<ApplicationRunner>` 를 받는 빈으로 세면 세기 위해 전부 생성해야 하고, 생성
 * 자체가 효과를 갖는 러너(조립 중 DB 연결·설정 검증)가 있으면 그 효과가 실패 전에 일어난다.
 * `allowEagerInit = false` 로 묻는 것이 같은 이유다.
 *
 * 이 설정은 **조건이 없다** — 늘 올라온다. 러너가 0 개나 1 개면 아무것도 하지 않으므로
 * 평가 endpoint 만 쓰는 배포도 영향이 없다.
 */
@Configuration
open class OneShotRunnerGuard {
    /**
     * **`@JvmStatic` 을 쓰지 않는다.** static `@Bean` 이 Spring 의 권장이지만, Kotlin 에서
     * 그것은 `companion object` + `@JvmStatic` 이고 그러면 ⓐ 클래스 본문이 비어 detekt 가
     * `object` 로 바꾸라고 하는데 Spring `@Configuration` 은 no-arg 생성자를 요구하고
     * ⓑ 바깥 참조가 `kotlin.jvm` 으로 하나 늘고 ⓒ `ApplicationRunner` 참조가 중첩 클래스
     * 이름으로 잡혀 러너 참조 장부가 중첩 이름을 등재하게 된다. 비-static 의 대가는 이
     * 설정 클래스가 **먼저 생성되는 것**뿐이고, 이 클래스는 상태가 없다.
     */
    @Bean
    open fun oneShotRunnerLimit(): BeanFactoryPostProcessor =
        BeanFactoryPostProcessor { beanFactory ->
            val runners =
                beanFactory.getBeanNamesForType(ApplicationRunner::class.java, true, false).toList()
            require(runners.size <= 1) {
                "일회 러너는 한 프로세스에 하나만 켠다 — 먼저 끝난 러너가 JVM 을 끝내 나머지는 " +
                    "돌지 않는다. 켜진 러너 ${runners.size}개: ${runners.sorted()}"
            }
        }
}
