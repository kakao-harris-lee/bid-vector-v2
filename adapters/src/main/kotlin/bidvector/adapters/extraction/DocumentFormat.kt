package bidvector.adapters.extraction

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper

/** 판별된 문서 형식(D-3C-4 (a)) — PDF 텍스트 층·플레인 텍스트만 지원한다. */
sealed interface DocumentFormat {
    data object PlainText : DocumentFormat

    data object Pdf : DocumentFormat

    data class Unsupported(
        val mediaType: String,
    ) : DocumentFormat
}

/** 텍스트 추출 결과 — 스캔 PDF(텍스트 층 없음)는 [NoTextLayer]로 관측한다(D-3C-4 (a)). */
sealed interface DocumentTextExtraction {
    data class Extracted(
        val text: String,
    ) : DocumentTextExtraction

    data object NoTextLayer : DocumentTextExtraction

    data class Unreadable(
        val detail: String,
    ) : DocumentTextExtraction
}

private val PDF_MAGIC = byteArrayOf('%'.code.toByte(), 'P'.code.toByte(), 'D'.code.toByte(), 'F'.code.toByte())

/** 매직 바이트 + mediaType 으로 형식을 판별한다(HWP/HWPX/스캔 여부는 여기서 갈리지 않는다). */
fun detectDocumentFormat(
    bytes: ByteArray,
    mediaType: String,
): DocumentFormat =
    when {
        startsWithPdfMagic(bytes) -> DocumentFormat.Pdf
        mediaType.startsWith("text/") -> DocumentFormat.PlainText
        else -> DocumentFormat.Unsupported(mediaType)
    }

private fun startsWithPdfMagic(bytes: ByteArray): Boolean =
    bytes.size >= PDF_MAGIC.size && PDF_MAGIC.indices.all { bytes[it] == PDF_MAGIC[it] }

/**
 * 문서 원문 텍스트를 추출한다(D-3C-4 (a)) — pdfbox **한 의존**(운영자 승인 문면
 * 「PDF 텍스트 층 + 플레인 텍스트」가 채택 근거). 텍스트 층이 없는(스캔) PDF 는
 * `PDFTextStripper`가 빈 문자열을 내므로 [DocumentTextExtraction.NoTextLayer]로 접는다 —
 * 빈 문자열을 「요건 없음 추출」로 오인하지 않는다(fail-open 방어).
 */
fun extractDocumentText(
    bytes: ByteArray,
    format: DocumentFormat,
): DocumentTextExtraction =
    when (format) {
        DocumentFormat.PlainText -> {
            DocumentTextExtraction.Extracted(String(bytes, Charsets.UTF_8))
        }

        DocumentFormat.Pdf -> {
            extractPdfText(bytes)
        }

        is DocumentFormat.Unsupported -> {
            DocumentTextExtraction.Unreadable("unsupported media type: ${format.mediaType}")
        }
    }

private fun extractPdfText(bytes: ByteArray): DocumentTextExtraction =
    try {
        Loader.loadPDF(bytes).use { document ->
            val text = PDFTextStripper().getText(document)
            if (text.isBlank()) DocumentTextExtraction.NoTextLayer else DocumentTextExtraction.Extracted(text)
        }
    } catch (malformed: java.io.IOException) {
        DocumentTextExtraction.Unreadable(malformed.javaClass.simpleName)
    }
