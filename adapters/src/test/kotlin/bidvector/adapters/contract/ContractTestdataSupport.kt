package bidvector.adapters.contract

import com.google.protobuf.CodedOutputStream
import com.google.protobuf.Message
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * M2/2C verifier r1 F-3 — testdata 로더·canonicalization 공유 함수. `ContractRoundTripTest`
 * (2A)·`PredictionContractTest`(2B)·`TrainingContractTest`(2C) 세 파일에 복제돼 있던 블록을
 * 여기 하나로 모은다(CPD observed 72·80 토큰 중복, 2A `ContractFractionRules.kt`와 같은
 * 관례). `canonicalBytes`는 `OPEN-2A-CANONICAL-FORM`(protobuf deterministic
 * serialization을 canonical form으로 삼는 근거)의 유일한 구현이다.
 */
internal fun contractTestdataRoot(subdirectory: String? = null): Path {
    val root =
        Path.of(
            System.getProperty("bidvector.contracts.testdata")
                ?: error("시스템 속성 'bidvector.contracts.testdata' 가 없다 — 빌드가 넘긴다"),
        )
    return if (subdirectory == null) root else root.resolve(subdirectory)
}

internal fun readTestdataBytes(
    root: Path,
    name: String,
): ByteArray = Files.readAllBytes(root.resolve(name))

internal fun canonicalBytes(message: Message): ByteArray {
    val buffer = ByteArrayOutputStream()
    val coded = CodedOutputStream.newInstance(buffer)
    coded.useDeterministicSerialization()
    message.writeTo(coded)
    coded.flush()
    return buffer.toByteArray()
}
