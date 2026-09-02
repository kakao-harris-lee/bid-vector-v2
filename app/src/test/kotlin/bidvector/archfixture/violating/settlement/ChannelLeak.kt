package bidvector.archfixture.violating.settlement

import java.nio.channels.SocketChannel

/** I/O 가족 위반. `java.nio.file` 만 적혔을 때 이 형제 잎이 빠져나갔다 — 뿌리 `java.nio` 가 덮는다. */
class ChannelLeak {
    fun open(channel: SocketChannel): Boolean = channel.isOpen
}
