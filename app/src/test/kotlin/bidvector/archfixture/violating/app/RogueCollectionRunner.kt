package bidvector.archfixture.violating.app

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner

/** D-6F8-3 우회 5 위반 표본 — app 안에 수집 러너 말고 프로세스를 자동 시작하는 러너를 하나 더 둔다. */
class RogueCollectionRunner : ApplicationRunner {
    override fun run(args: ApplicationArguments) = Unit
}
