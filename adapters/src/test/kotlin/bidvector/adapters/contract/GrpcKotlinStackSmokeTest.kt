package bidvector.adapters.contract

import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.kotlin.AbstractCoroutineStub
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

/**
 * §4b 스모크(런타임) — `_workspace/m2-prep/02_grpc_stack_compat.md` 위험 ① — grpc-kotlin-stub
 * 1.5.0 의 POM 은 `grpc-stub:1.62.2`(22 마이너 하위)를 선언하고, `ml-contract` 의 `grpc-bom`
 * 이 전 `io.grpc:*` 좌표를 1.84.0 으로 강제 정렬한다(scope.md D-2A-0). 컴파일+protoc 플러그인
 * 해석·실행 스모크는 `ml-contract/build.gradle.kts` 커밋이 이미 냈다 — 여기서는 그 정렬이
 * **런타임**에서도 깨지지 않는지(`NoSuchMethodError`·`AbstractMethodError` 없음) 잰다.
 *
 * 2A 의 `.proto` 는 아직 `service` 가 없어(2B 몫) 생성 코루틴 stub 이 없다 — 대신
 * `AbstractCoroutineStub`(grpc-kotlin) 을 최소 서브클래싱해 grpc-java 1.84.0 의
 * `Channel`/`CallOptions`/`AbstractStub`(1.84.0) 위에서 실제로 인스턴스화·재구성되는지 확인한다.
 */
class GrpcKotlinStackSmokeTest {
    private class SmokeStub(
        channel: Channel,
        callOptions: CallOptions = CallOptions.DEFAULT,
    ) : AbstractCoroutineStub<SmokeStub>(channel, callOptions) {
        override fun build(
            channel: Channel,
            callOptions: CallOptions,
        ): SmokeStub = SmokeStub(channel, callOptions)
    }

    @Test
    fun `grpc-kotlin-stub 이 grpc-java 1_84_0 채널 위에서 인스턴스화·재구성된다`() {
        val serverName = "bidvector-2a-smoke-${System.nanoTime()}"
        val server =
            InProcessServerBuilder
                .forName(serverName)
                .directExecutor()
                .build()
                .start()
        val channel = InProcessChannelBuilder.forName(serverName).directExecutor().build()
        try {
            val stub = SmokeStub(channel)
            stub.channel shouldBe channel

            // AbstractStub#withDeadlineAfter 가 build(Channel, CallOptions) 를 통해 새 인스턴스를
            // 낸다 — grpc-kotlin 서브클래스의 build() 가 grpc-java 1.84.0 의 추상 메서드 시그니처와
            // 실제로 맞물리는지(리플렉션이 아니라 실제 호출로) 확인한다.
            val withDeadline = stub.withDeadlineAfter(5, TimeUnit.SECONDS)
            (withDeadline !== stub) shouldBe true
            withDeadline.channel shouldBe channel
        } finally {
            channel.shutdownNow()
            server.shutdownNow()
            channel.awaitTermination(5, TimeUnit.SECONDS)
            server.awaitTermination(5, TimeUnit.SECONDS)
        }
    }
}
