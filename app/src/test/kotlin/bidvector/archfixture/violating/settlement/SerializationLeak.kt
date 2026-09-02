package bidvector.archfixture.violating.settlement

import tools.jackson.databind.ObjectMapper

/** JSON 가족 위반. Jackson 3 은 좌표가 `tools.jackson` 이라 옛 목록으로는 잡히지 않는다. */
class SerializationLeak {
    fun mapper(): ObjectMapper = ObjectMapper()
}
