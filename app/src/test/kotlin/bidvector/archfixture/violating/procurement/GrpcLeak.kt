package bidvector.archfixture.violating.procurement

import io.grpc.Status

/** gRPC 가족 위반 — ADR 0006 D-6: "도메인은 gRPC 타입도 Protobuf 타입도 보지 못한다". */
class GrpcLeak {
    fun code(status: Status): String = status.code.name
}
