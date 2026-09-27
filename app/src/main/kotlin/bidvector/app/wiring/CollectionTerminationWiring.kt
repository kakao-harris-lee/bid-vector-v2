package bidvector.app.wiring

import bidvector.app.collection.CollectionTermination
import org.springframework.boot.ExitCodeGenerator
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import kotlin.system.exitProcess

/**
 * 일회성 러너의 **종료 자리 하나** — 웹 서버가 함께 떠 있어도 실행이 끝나면 프로세스가 끝난다.
 *
 * 갈래마다 두지 않고 한 자리에 둔다. 처음에는 공고 목록 배선 안에 있었는데, 그러면 개찰 축이나 추출
 * 갈래를 **혼자 켰을 때** 이 빈이 없어 기동이 실패한다(배선 test 가 그것을 잡았다) — 갈래 사이에 숨은
 * 의존이 생긴 자리였다. 세 배선이 이 설정을 `@Import` 해 같은 빈 하나를 공유한다.
 */
@Configuration
open class CollectionTerminationWiring {
    @Bean
    open fun collectionTermination(context: ConfigurableApplicationContext): CollectionTermination =
        CollectionTermination { exitCode ->
            exitProcess(SpringApplication.exit(context, ExitCodeGenerator { exitCode }))
        }
}
