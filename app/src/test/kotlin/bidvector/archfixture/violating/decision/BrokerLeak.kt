package bidvector.archfixture.violating.decision

import jakarta.jms.Message

/** broker 가족 위반 — ADR 0006 이 브로커를 두지 않기로 했고 domain 은 더더욱 볼 수 없다. */
class BrokerLeak {
    fun idOf(message: Message): String = message.jmsMessageID
}
