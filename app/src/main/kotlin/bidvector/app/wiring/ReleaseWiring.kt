package bidvector.app.wiring

import bidvector.adapters.persistence.ReleaseIdentifier
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 이 프로세스의 릴리스 식별자(M9 OPS-10) — **기본값이 없다.** 값이 없으면 바인딩이 실패해
 * 프로세스가 뜨지 않는다(`OpeningCollectionProperties` 와 같은 규율: 코드가 「안전한 기본값」
 * 을 지어내면 어느 릴리스가 그 행을 만들었는지 영영 모른다).
 *
 * **키가 하나다.** 앞 판은 수집 갈래 둘이 각자 `release-sha` 키를 들고 기본값 `unversioned`
 * 를 가졌고, 서버 프로세스(전략 편집 → outbox)는 자기 키가 아예 없었다 — 같은 개념이 키
 * 둘 + 부재 하나였다. 한 호스트에서 두 값이 적힐 수 있는 형태 자체를 없앤다.
 */
@ConfigurationProperties(prefix = "bidvector.release")
data class ReleaseProperties(
    val sha: String,
)

/**
 * **조건 없는 조립이다** — 어느 모드로 뜨든(HTTP 서버·수집 러너·relay) 행을 쓰는 경로가
 * 있으므로 전부 이 값을 요구한다. 조건을 달면 「이 모드에서는 식별자가 없어도 된다」가
 * 생기고, 그 모드가 쓰는 행이 식별자 없이 남는다.
 */
@Configuration
@EnableConfigurationProperties(ReleaseProperties::class)
open class ReleaseWiring {
    @Bean
    open fun releaseIdentifier(properties: ReleaseProperties): ReleaseIdentifier = ReleaseIdentifier(properties.sha)
}
