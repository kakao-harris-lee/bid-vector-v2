package bidvector.procurement

/**
 * 추출된 요건이 온 서식 필드 — qualification `RequirementSourceField`와 **의도적으로
 * 별도**다(ADR 0006 D-4, 업무 모듈은 서로를 직접 참조하지 않는다). 1C 로의 변환은
 * adapters `ExtractionToQualification` 한 함수가 한다(D-3C-3).
 */
sealed interface ExtractedSourceField {
    data object LicenseLimitName : ExtractedSourceField

    data object PermittedIndustryList : ExtractedSourceField
}

/** 추출된 그룹 번호 원문 — qualification `LmtGrpNo`와 같은 이유로 문자열(제로패딩 보존). */
data class ExtractedGroupNo(
    val value: String,
)

/** 추출된 일련번호 원문 — qualification `LmtSno`와 같은 이유로 문자열. */
data class ExtractedSerialNo(
    val value: String,
)

/** 원문 안 문자 구간(0-based, 반개구간) — provenance 가 이 구간을 가리키고 원문을 복사하지 않는다. */
data class ExtractionEvidenceSpan(
    val start: Int,
    val end: Int,
) {
    init {
        require(start >= 0) { "start는 음수일 수 없다: $start" }
        require(end >= start) { "end는 start 이상이어야 한다: start=$start end=$end" }
    }
}

/**
 * 추출된 요건 행 하나 — `licenseNames`는 비어 있을 수 없다(qualification
 * `RequirementRow.Parsed`와 같은 이유, D-3 「이름을 못 읽었으면 이 행을 만들지 않는다」).
 * 스키마 검증(networknt, adapters 소관)을 통과한 JSON 이 이 형태로 파싱된 뒤에만
 * 만들어진다 — 검증 자체는 이 타입의 `init`이 재검사하지 않는다(외부 라이브러리 의존은
 * adapters 층의 일이다, ADR 0006 D-4·§4b). `init`이 재는 것은 이 타입 **자신의** 구조
 * 불변식(빈 이름 목록·공백 이름 금지)뿐이다.
 */
data class ExtractedRequirementItem(
    val groupNo: ExtractedGroupNo?,
    val serialNo: ExtractedSerialNo,
    val sourceField: ExtractedSourceField,
    val licenseNames: List<String>,
    val evidence: ExtractionEvidenceSpan,
) {
    init {
        require(licenseNames.isNotEmpty()) {
            "licenseNames는 비어 있을 수 없다(serialNo=$serialNo) — 이름을 못 읽었으면 이 행을 만들지 않는다"
        }
        require(licenseNames.all(String::isNotBlank)) { "licenseNames는 공백 문자열을 담을 수 없다(serialNo=$serialNo)" }
    }
}

/**
 * 문서 하나의 추출 결과(④, ⑦) — `items`가 비어 있고 [assertedAbsent]가 `false`인 조합은
 * 만들 수 없다. 모델이 "요건 없음"이라고 **명시적으로** 답한 경우만 `assertedAbsent=true`
 * (빈 목록, `items=emptyList()`)로 표현한다 — 「값을 못 읽었다」와 「없다고 확인했다」를
 * 구분한다(scope.md ⑦, `data-dictionary.md` §1.6). `Eligible`류 값이 없다 — 이 타입으로는
 * 자격 통과를 표현할 수 없다(위협 모델 (a) fail-open 방어).
 */
data class ExtractedRequirements(
    val items: List<ExtractedRequirementItem>,
    val assertedAbsent: Boolean,
) {
    init {
        require(assertedAbsent || items.isNotEmpty()) {
            "assertedAbsent=false 이면 items는 비어 있을 수 없다 — 빈 목록은 「요건 없음 주장」으로만 낸다"
        }
        require(!(assertedAbsent && items.isNotEmpty())) {
            "assertedAbsent=true 이면 items는 비어 있어야 한다 — 두 신호를 동시에 세우지 않는다"
        }
    }
}

/**
 * 이 port 자신의 판정 결과(②) — **거친 이분법**이다. 실패 세부 사유(schema 위반·timeout·
 * breaker open·예산 초과)는 도메인이 구분할 개념이 아니라 adapters 운영 관심사라 이
 * port 밖(adapters `ExtractionFailure`, D-3C-3 (a))에 둔다 — 1C 변환(`ExtractionToQualification`)
 * 도 그 adapters 타입을 직접 읽어 `RequirementCollection`을 만들고, 이 port 의
 * [Uncertain]은 「domain 이 이 port 를 마주칠 때의 최소 진술」일 뿐이다.
 */
sealed interface ExtractionOutcome {
    data class Extracted(
        val requirements: ExtractedRequirements,
    ) : ExtractionOutcome

    data object Uncertain : ExtractionOutcome
}

/**
 * 문서 → 구조화 요건 추출 port(M3/3C ①, ADR 0005 D-10.1 「domain이 의존하는 port는
 * domain 안에 선다」) — 구현은 adapters(`HttpLlmRequirementExtractor`)가 한다. 이 port
 * 를 감시 통과 여부와 무관하게 부르는 것은 domain 의 일이 아니다 — 그 게이트(D-3C-6)는
 * adapters 층에 선다(같은 도메인 모듈끼리 참조 불가, ADR 0006 D-4 — `strategy`
 * `WatchVerdict`를 이 port 의 인자로 받을 수 없다).
 */
interface RequirementExtractionPort {
    fun extract(document: FetchedDocument): ExtractionOutcome
}
