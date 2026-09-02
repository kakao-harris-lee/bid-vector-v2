package bidvector.archfixture.violating.decision

import com.google.protobuf.Timestamp

/** Protobuf 가족 위반 — 생성 stub 타입이 도메인에 보이면 안 된다(ADR 0006 D-6). */
class ProtobufLeak {
    fun seconds(timestamp: Timestamp): Long = timestamp.seconds
}
