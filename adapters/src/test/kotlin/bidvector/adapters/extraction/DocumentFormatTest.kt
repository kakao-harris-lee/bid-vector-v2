package bidvector.adapters.extraction

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

/**
 * pdfbox 의 Standard14 `Helvetica`는 WinAnsiEncoding 뿐이라 한글을 인코딩할 수 없다
 * (임베디드 유니코드 폰트는 이 test 목적(형식 판별·텍스트 층 유무)에 과하다) — 이 helper
 * 로 만드는 PDF 는 ASCII 본문만 담는다.
 */
private fun pdfWithText(text: String): ByteArray {
    PDDocument().use { document ->
        val page = PDPage()
        document.addPage(page)
        PDPageContentStream(document, page).use { stream ->
            stream.beginText()
            stream.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
            stream.newLineAtOffset(50f, 700f)
            stream.showText(text)
            stream.endText()
        }
        val out = ByteArrayOutputStream()
        document.save(out)
        return out.toByteArray()
    }
}

private fun blankPdf(): ByteArray {
    PDDocument().use { document ->
        document.addPage(PDPage())
        val out = ByteArrayOutputStream()
        document.save(out)
        return out.toByteArray()
    }
}

/** D-3C-4 (a) — PDF 텍스트 층 + 플레인 텍스트만 지원, 그 밖은 관측(Unsupported/NoTextLayer). */
class DocumentFormatTest {
    @Test
    fun `text 미디어타입은 PlainText 로 판별된다`() {
        detectDocumentFormat("hello".toByteArray(), "text/plain") shouldBe DocumentFormat.PlainText
    }

    @Test
    fun `PDF 매직 바이트는 Pdf 로 판별된다`() {
        detectDocumentFormat(pdfWithText("license requirement"), "application/pdf") shouldBe DocumentFormat.Pdf
    }

    @Test
    fun `알 수 없는 형식은 Unsupported 다`() {
        val format = detectDocumentFormat(byteArrayOf(0x01, 0x02), "application/x-hwp")
        format.shouldBeInstanceOf<DocumentFormat.Unsupported>()
    }

    @Test
    fun `텍스트 층이 있는 PDF 는 텍스트를 추출한다`() {
        val bytes = pdfWithText("license requirement body")
        val extraction = extractDocumentText(bytes, DocumentFormat.Pdf)

        extraction.shouldBeInstanceOf<DocumentTextExtraction.Extracted>()
        extraction.text.contains("license") shouldBe true
    }

    @Test
    fun `텍스트 층이 없는 스캔 PDF 는 NoTextLayer 로 관측된다`() {
        val extraction = extractDocumentText(blankPdf(), DocumentFormat.Pdf)

        extraction shouldBe DocumentTextExtraction.NoTextLayer
    }

    @Test
    fun `Unsupported 형식은 Unreadable 로 관측된다`() {
        val extraction = extractDocumentText(byteArrayOf(1, 2, 3), DocumentFormat.Unsupported("application/x-hwp"))

        extraction.shouldBeInstanceOf<DocumentTextExtraction.Unreadable>()
    }
}
