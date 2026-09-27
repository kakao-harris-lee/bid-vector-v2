package bidvector.adapters.koneps

import java.io.StringReader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

/**
 * 게이트웨이 오류 봉투가 코드를 싣는 원소 이름 — `returnReasonCode`는 data.go.kr 게이트웨이
 * (서비스 앞단)의 `cmmMsgHeader` 형태, `resultCode`는 서비스 자신이 `type=json`을 못 지키고
 * XML 로 답할 때의 형태다. **코드 어휘는 둘 다 같은 표**(`KONEPS_COLLECTION_POLICY
 * .resultCodeCategories`)를 쓴다 — 여기서 범주를 다시 정하지 않는다.
 */
private val CODE_ELEMENT_NAMES = setOf("returnReasonCode", "resultCode")

/**
 * XML 오류 봉투에서 코드 하나를 읽는다(`OPEN-6F8-QUOTA-XML-ENVELOPE`, M6/6G D-6G-11) —
 * 읽지 못하면 `null`이다(지어내지 않는다).
 *
 * **문자열 탐색이 아니라 문서 구조로 읽는다** — 한도 초과 메시지(`LIMITED_NUMBER_…`)의 문면을
 * 맞춰 보면 게이트웨이가 문구를 바꾸는 날 조용히 열린다. 코드 원소의 텍스트만 본다.
 *
 * DTD·외부 엔티티를 끈다 — 오류 본문은 신뢰 경계 밖에서 온 바이트라 XXE 로 로컬 파일을 읽게
 * 하는 경로를 열지 않는다. 끈 상태에서 `<!DOCTYPE …>`는 해석 실패이고, 실패는 호출부가
 * 구조 실패로 접는다.
 */
internal fun readGatewayErrorCode(body: String): String? {
    val reader = newSecureReader(body)
    return try {
        reader.firstCodeElementText()
    } finally {
        reader.close()
    }
}

private fun newSecureReader(body: String): XMLStreamReader {
    val factory = XMLInputFactory.newInstance()
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false)
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
    factory.setProperty(XMLInputFactory.IS_COALESCING, true)
    return factory.createXMLStreamReader(StringReader(body))
}

private fun XMLStreamReader.firstCodeElementText(): String? {
    while (hasNext()) {
        if (next() == XMLStreamConstants.START_ELEMENT && localName in CODE_ELEMENT_NAMES) {
            return elementText.trim().takeIf { it.isNotEmpty() }
        }
    }
    return null
}
