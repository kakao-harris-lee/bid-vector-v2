package bidvector.archfixture.violating.app

import org.slf4j.LoggerFactory

/** D-6F8-4 우회 4 위반 표본 — 수집 러너의 한 줄 출구 밖에서 로거를 쓴다. */
class RogueLoggerUser {
    private val logger = LoggerFactory.getLogger(RogueLoggerUser::class.java)

    fun say(line: String) = logger.info(line)
}
