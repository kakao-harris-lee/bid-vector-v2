package bidvector.adapters.contract

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * M2/2E verifier r1 F-1(high) 임시 대응 — `contracts/testdata/embedding/` 안의 JSON 원본을
 * `buf convert`로 다시 canonical 바이트로 만들어 커밋된 `.binpb`와 대조한다.
 *
 * **왜 필요한가**(F-1(a) 장부 정정) — 신설 `embedding.proto`는 승인 태그
 * `contracts/v1-approved-2026-09-07`에 없어 `buf breaking`이 이 파일 **내부**의 필드·enum
 * 값·필드 번호 변경을 못 본다(원래 없던 것의 형태 변경이라서, `contract-policy.properties`
 * D-2E 주석·`contracts/tools/breaking-mutations.sh` 머리 주석 참고 — 다음 승인 태그가
 * `embedding.proto`를 포함하기 전까지 이 사각은 구조적이다). 이 test는 **같은 JSON 텍스트가
 * 스키마 변경 뒤 다른 바이트를 낸다**는 사실을 이용한 임시 대응이다 — verifier r1이 실측:
 * enum 값 하나를 지우면 같은 JSON이 `1a02 0102` 대신 `1a01 01`을 내(미지 enum 이름이
 * 조용히 탈락), 필드 삭제·필드 번호 재사용도 같은 방식으로 바이트가 갈린다.
 *
 * **못 잡는 것** — `rpc` 삭제(메시지 wire 형식과 무관이라 이 test 로는 안 잡힘, 실측:
 * `GetEmbeddingMetadata` rpc를 지워도 이 test는 그대로 통과). `contractGate`의
 * `buf breaking` 자체를 대체하지 않는다 — **정본 방어는 다음 승인 태그 갱신**(checklist.md
 * 「종결 승인 결정 항목」)이고, 이 test는 그 전까지의 부분적 안전망이다.
 */
class EmbeddingTestdataCanonicalTest {
    private val testdataRoot: Path = contractTestdataRoot("embedding")
    private val contractsRoot: Path = contractTestdataRoot().parent

    private data class Sample(
        val protoType: String,
        val name: String,
    )

    private val samples =
        listOf(
            Sample("bidvector.ml.v1.EmbedTextRequest", "embed_text_request"),
            Sample("bidvector.ml.v1.EmbedTextResponse", "embed_text_response_success"),
            Sample("bidvector.ml.v1.EmbedTextResponse", "embed_text_response_failure_unsupported_schema"),
            Sample("bidvector.ml.v1.EmbedTextResponse", "embed_text_response_failure_invalid_request_empty_text"),
            Sample("bidvector.ml.v1.GetEmbeddingMetadataResponse", "get_embedding_metadata_response"),
        )

    @Test
    fun `JSON 원본을 buf convert 로 재생성한 바이트가 커밋된 canonical binpb 와 같다`() {
        samples.forEach { sample ->
            val jsonPath = testdataRoot.resolve("${sample.name}.json")
            val committedBytes = Files.readAllBytes(testdataRoot.resolve("${sample.name}.binpb"))
            val regeneratedBytes = bufConvertJsonToBinary(sample.protoType, jsonPath)
            regeneratedBytes.toList() shouldBe committedBytes.toList()
        }
    }

    /** `buf convert <contractsRoot> --type <type> --from <json> --to -#format=binpb`을 그대로
     * 부른다 — testdata 저작 절차(commands.md)와 같은 명령, stdout 캡처만 다르다. */
    private fun bufConvertJsonToBinary(
        protoType: String,
        jsonPath: Path,
    ): ByteArray {
        val process =
            ProcessBuilder(
                "buf",
                "convert",
                contractsRoot.toString(),
                "--type",
                protoType,
                "--from",
                jsonPath.toString(),
                "--to",
                "-#format=binpb",
            ).start()
        val stdout = ByteArrayOutputStream()
        process.inputStream.copyTo(stdout)
        val stderr = process.errorStream.readBytes()
        val exitCode = process.waitFor()
        check(exitCode == 0) {
            "buf convert 실패(exit=$exitCode, type=$protoType, json=$jsonPath): ${String(stderr, Charsets.UTF_8)}"
        }
        return stdout.toByteArray()
    }
}
