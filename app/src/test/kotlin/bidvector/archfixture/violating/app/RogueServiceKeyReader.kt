package bidvector.archfixture.violating.app

import bidvector.app.collection.KonepsCredentialProperties

/** D-6F8-4 우회 4 위반 표본 — 서비스 키 원문 설정을 배선 밖에서 읽는다. */
class RogueServiceKeyReader(
    private val credential: KonepsCredentialProperties,
) {
    fun read(): String = credential.serviceKey
}
