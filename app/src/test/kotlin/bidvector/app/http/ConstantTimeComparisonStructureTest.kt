package bidvector.app.http

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

/**
 * **회귀 그물(D-6A1-43 이후 — 전칭 주장을 걷어낸다).** 1차 방어는 더 이상 이 게이트가
 * 아니라 [OperatorCredential] 타입이다 — 그 타입은 `matches()` 하나만 노출하고 `equals`/
 * `hashCode`/`toString`을 재정의하지 않아, 비교 코드가 파일·패키지 어디로 옮겨져도(MUT-A2~A4가
 * 뚫었던 세 축 전부) 빠른 비교를 **쓸 수 없다.** 이 게이트는 그 위에 남는 **회귀 그물**이다
 * — 누군가 타입을 우회해 raw `String`을 다시 직접 비교하는 코드를 이 패키지에 새로 만들면
 * 잡는다. **이 게이트 자신이 우회 (4)를 폐쇄한다는 주장은 하지 않는다** — 폐쇄는 타입이
 * 진다. 타이밍 test는 CI에서 본질적으로 flaky해 만들지 않는다.
 * 대신 **구조**를 잰다: 패키지의 모든 컴파일된 class 파일이 `MessageDigest.isEqual`은
 * 어딘가에서 쓰되(자격증명 비교가 실재함을 보장), `String.equals`는 아무도 안 쓰고,
 * `Intrinsics.areEqual`(Kotlin `==`이 컴파일되는 자리)은 **명시 허용 목록** 밖 어디에도
 * 없어야 한다.
 *
 * **이 게이트가 여전히 위치·이름 술어라는 한계(그대로 남는다).** D-6A1-37 이 판정 범위를
 * 이름 축에서 허용 목록으로 뒤집었으나(D-6A1-29 → verifier MUT-N1 재발의 시정), verifier
 * r3 는 같은 파일 안에서 `==`→`Objects.equals`로 한 글자만 바꾸는 형태(MUT-A4)까지는
 * 이 게이트의 차단 심볼(`Intrinsics.areEqual`·`String.equals` 둘뿐)이 못 본다는 것과,
 * 스캔 루트가 한 디렉터리 하드코딩(MUT-A3)·허용 목록 키가 FQN이 아닌 simple name(MUT-A2)
 * 이라는 것을 실측했다. **이 게이트를 다시 넓히지 않는다** — 넓히는 대신 [OperatorCredential]
 * 타입이 그 세 축을 전부 기능 고장(인증 항상 거부)으로 강등한다.
 *
 * 이름 목록과 허용 목록의 차이는 **기본값**이다 — 이름 목록에서 새 class는 기본이
 * 미판정이라 우회가 생길 때마다 이름을 늘려야 하고(끝이 없다), 허용 목록에서 새 class는
 * 기본이 판정이라 늘릴 필요가 없다. 그래서 여기서는 **패키지 전체 class 파일**을 판정하고,
 * `Intrinsics.areEqual`을 정당하게 쓰는 class만 [ALLOWED_INTRINSICS_AREEQUAL]에 **개별
 * 근거와 함께** 올린다 — 전부 data class 자동 생성 `equals()`이거나 sealed 싱글턴 객체
 * 참조 동일성이고, 자격증명 값을 참조하지 않는다(javap로 실측 확인, 아래 근거 참고).
 */
class ConstantTimeComparisonStructureTest {
    @Test
    fun `패키지의 어떤 class 도 허용 목록 밖에서 Intrinsics areEqual 을 쓰지 않는다`() {
        val outputs = packageClassOutputs()
        outputs.keys.shouldNotBeEmpty()

        outputs.values.any { it.contains(MESSAGE_DIGEST_IS_EQUAL) } shouldBe true
        outputs.filterValues { it.contains(STRING_EQUALS) }.keys shouldBe emptySet<String>()
        disallowedAreEqual(outputs) shouldBe emptySet<String>()
    }

    /**
     * 양성 대조 — 허용 목록 밖 class(예: 자격증명 비교를 추출한 형제 클래스)가
     * `Intrinsics.areEqual`을 쓰면 술어가 실제로 잡는지, 실제 바이트코드 없이 술어
     * 로직만으로 고정한다(MUT-N1 시나리오를 진 채로 판정 대상이 이름이 아니라 허용 목록
     * 전체임을 확인).
     */
    @Test
    fun `허용 목록 밖 class 의 Intrinsics areEqual 은 이 술어에 걸린다 — 양성 대조`() {
        val simulated =
            mapOf(
                "CredentialComparator" to
                    """
                    invokestatic kotlin/jvm/internal/Intrinsics.areEqual:(Ljava/lang/Object;Ljava/lang/Object;)Z
                    """.trimIndent(),
                "ErrorBody" to
                    """
                    invokestatic kotlin/jvm/internal/Intrinsics.areEqual:(Ljava/lang/Object;Ljava/lang/Object;)Z
                    """.trimIndent(),
            )

        disallowedAreEqual(simulated) shouldBe setOf("CredentialComparator")
    }

    /**
     * 양성 대조 — 허용 목록의 각 항목은 근거 문장을 갖고 임의로 늘지 않는다.
     *
     * 키 집합이 **두 자리**에 있다(아래 축어 집합 · [ALLOWED_INTRINSICS_AREEQUAL] 의 키).
     * M7 에서 레인 셋(7C③·7C④·7C⑤)이 같은 두 자리를 동시에 늘린다 — 병합은 수가 아니라
     * **항목 이름의 합집합**으로 다시 세고 두 자리를 같이 맞춘다. 7C③ 이 더한 넷은
     * `NotificationItemResponse` · `NotificationInboxRequestsKt` (+2 — 앞 판의 `NotificationTargetResult$Item`·
     * `$Notice` 는 r1 검토 CR LOW-3 으로 **타입 자체가 사라져** 등재도 함께 걷었다).
     */
    @Test
    fun `허용 목록은 등재된 것뿐이고 각각 근거 문장을 갖는다`() {
        ALLOWED_INTRINSICS_AREEQUAL.keys shouldBe
            setOf(
                "ApiAuditRecord",
                "ErrorBody",
                "StrategyReadResponse",
                "StrategyReadControllerKt",
                "EvaluationDryRunResponse",
                "ResultBuckets",
                "EditSessionResponse",
                "EditFieldTokensKt",
                "EditSessionResponseKt",
                "NoticeSummaryResponse",
                // M7/7C ② — 투찰가 요청의 응답 DTO 일곱과 사상 함수 파일 넷, 컨트롤러 하나.
                "BidPriceRequestResponse",
                "EvaluationDetailFields",
                "NotificationFields",
                "QualificationFields",
                "BaseAmountFields",
                "FloorRateFields",
                // M7/7C②-b — 표시 근거의 운반 타입 하나가 늘었다(응답 DTO 가 아니다).
                "FloorRateGrounds",
                "PriceAxesFields",
                "BidPriceVerdictLabelsKt",
                "BidPriceDropLabelsKt",
                "BidPriceQualificationLabelsKt",
                "BidPriceFactLabelsKt",
                "BidPriceRequestController",
                "BidCaseResponse",
                "BidCaseResponse\$Companion",
                "BidTimelineEntryResponse",
                "BidCaseRequestsKt",
                "BidCaseRouting",
                "DraftInput",
                "AccountScopedRoutes",
                "BidCaseHttp",
                "NotificationItemResponse",
                "NotificationInboxRequestsKt",
                "OpeningResultResponse",
                "OpeningResultControllerKt",
                "DeclaredValue",
                "DeclaredValueKt",
            )
        ALLOWED_INTRINSICS_AREEQUAL.values.all { it.isNotBlank() } shouldBe true
    }
}

/**
 * `Intrinsics.areEqual`을 정당하게 남기는 class 파일 이름(`nameWithoutExtension`) → 근거.
 * **자격증명 비교를 담지 않는 class만** 올린다 — 새 항목을 더할 때마다 그 class가
 * [OperatorCredentialAuthenticator]의 자격증명 값(`expectedCredential`·`presented`)을 참조하지
 * 않는지 javap으로 확인한다. 이름 접두사가 아니라 **종류**(data class 자동 equals 또는
 * sealed 싱글턴 참조 동일성)로 가른 목록이다.
 */
private val ALLOWED_INTRINSICS_AREEQUAL: Map<String, String> =
    mapOf(
        "ApiAuditRecord" to
            "감사 행 값 객체(data class) 자동 생성 equals — 필드는 시각·주체·경로·상태·correlation id, 자격증명 값 없음",
        "ErrorBody" to
            "오류 응답 값 객체(data class) 자동 생성 equals — 필드는 code·message·correlationId, 자격증명 값 없음",
        "StrategyReadResponse" to
            "전략 조회 응답 값 객체(data class) 자동 생성 equals — 전략 필드뿐, 자격증명 값 없음",
        "StrategyReadControllerKt" to
            "provenanceLabel()의 Provenance sealed 싱글턴 객체 참조 동일성(when 소진, javap 확인 완료) — 자격증명과 무관",
        "EvaluationDryRunResponse" to
            "평가 dry-run 응답 값 객체(data class) 자동 생성 equals — 판정 결과 공고 ID 배열·건수뿐, 자격증명 값 없음",
        "ResultBuckets" to
            "CandidateEvaluation 분류 중간값(data class, private) 자동 생성 equals — 공고 ID 배열 넷뿐, 자격증명 값 없음",
        "EditSessionResponse" to
            "편집 세션 응답 값 객체(data class) 자동 생성 equals — 세션 id·상태·필드·버전·만료 시각뿐, 자격증명 값 없음",
        "EditFieldTokensKt" to
            "필드 토큰 표의 sealed 싱글턴 when 소진과 토큰→필드 Map 조회(javap 확인: 자격증명 좌표 0)",
        "EditSessionResponseKt" to
            "상태·거부 사유 sealed 싱글턴 when 소진(javap 확인: 자격증명 좌표 0) — 값 비교가 아니라 객체 동일성",
        "NoticeSummaryResponse" to
            "공고 요약 응답 값 객체(data class) 자동 생성 equals — 공고번호·차수·상태·공고명·기관명·기초금액·마감뿐, 자격증명 값 없음",
        // M7/7C ② (D-7C2-5 ④) — 열둘. 근거는 **클래스마다** 적는다(그 class 가 무엇을 == 로 비교하는가).
        // 열둘 전부 javap 으로 자격증명·CSRF·서명 어휘 참조 0 을 실측했다. 패키지를 옮겨 이 게이트를
        // 비껴가는 선택은 하지 않았다 — 위치로 보안 게이트를 피하는 형태다.
        "BidPriceRequestResponse" to
            "투찰가 요청 응답 값 객체(data class) 자동 생성 equals — 공고 식별자·상관 id·개정·여력 수·고지 코드와 축별 묶음 여섯, 자격증명 값 없음",
        "EvaluationDetailFields" to
            "평가 상세 묶음(data class) 자동 생성 equals — 판정·사유 코드·단계·공고 상태·점수 문자열뿐, 자격증명 값 없음",
        "NotificationFields" to
            "알림 처분 묶음(data class) 자동 생성 equals — 알림 요청 여부와 알림함 기록 상태·사유 코드뿐, 자격증명 값 없음",
        "QualificationFields" to
            "자격 근거 묶음(data class) 자동 생성 equals — 판정·사유 코드·면허명·그룹 라벨·출처 필드·정책 출처뿐, 자격증명 값 없음",
        "BaseAmountFields" to
            "기초금액 묶음(data class) 자동 생성 equals — 금액·통화·basis·과세·provenance·해석 갈래뿐, 자격증명 값 없음",
        "FloorRateFields" to
            "게시 하한율 묶음(data class) 자동 생성 equals — 율 문자열·출처·차수·근거 상태뿐, 자격증명 값 없음",
        "FloorRateGrounds" to
            "근거 상태+근거 값 운반(data class, internal) 자동 생성 equals — 필드는 표시 어휘 enum 과 율 문자열 둘뿐, 자격증명 값 없음(javap: 비밀값 어휘 참조 0)",
        "PriceAxesFields" to
            "가격 세 축 묶음(data class) 자동 생성 equals — 상태와 사유 코드뿐(값 칸이 없다), 자격증명 값 없음",
        "BidPriceVerdictLabelsKt" to
            "판정 축 사상 함수 파일 — sealed 싱글턴(Verdict·사유)을 소진 when 으로 가르는 참조 동일성, 자격증명 값 없음",
        "BidPriceDropLabelsKt" to
            "탈락 축 사상 함수 파일 — sealed 싱글턴(단계·드롭 사유·감시 불가 사유) 참조 동일성, 자격증명 값 없음",
        "BidPriceQualificationLabelsKt" to
            "자격 축 사상 함수 파일 — sealed 싱글턴(면허 판정·판정 불가 사유·그룹 id·유효기간·정책 시행일) 참조 동일성, 자격증명 값 없음",
        "BidPriceFactLabelsKt" to
            "공고 fact·ML 축 사상 함수 파일 — sealed 싱글턴(금액 해석 갈래·하한율 출처·ML 상태·미가용 사유) 참조 동일성, 자격증명 값 없음",
        "BidPriceRequestController" to
            "단건 평가 컨트롤러 — 요청 결과 sealed 갈래(공고 없음/준비됨)를 소진 when 으로 가르는 참조 동일성. " +
            "인증은 앞단 필터가 지고 이 class 는 자격증명 타입을 받지도 참조하지도 않는다",
        // M7/7B — 입찰 건의 HTTP 층. 전부 data class 자동 equals 이거나 sealed 싱글턴
        // when 소진이고, `OperatorCredentialAuthenticator` 의 자격증명 좌표를 참조하지 않는다.
        "BidCaseResponse" to
            "입찰 건 응답 값 객체(data class) 자동 생성 equals — 건 id·공고 키·상태·다음 확인 시점뿐, 자격증명 값 없음",
        "BidCaseResponse\$Companion" to
            "응답 변환 companion 의 NextCheckpoint sealed 싱글턴 when 소진 — 값 비교가 아니라 객체 동일성, 자격증명 좌표 0",
        "BidTimelineEntryResponse" to
            "타임라인 줄 응답 값 객체(data class) 자동 생성 equals — 상태·명령·주체 종류·시각뿐, 본문도 자격증명도 없음",
        "BidCaseRequestsKt" to
            "요청 읽기의 enum 어휘 조회(`values.firstOrNull { it.name == raw }`) — 공개 어휘 비교이고 자격증명 값 없음",
        "BidCaseRouting" to
            "경로·질의 읽기의 어휘 비교(bidKind·state) — 공개 enum 이름 비교이고 자격증명 값 없음 " +
            "(주체는 읽지 않는다 — 계정은 typed 속성, 계약 D-7B-2)",
        "DraftInput" to
            "초안 요청 값 객체(data class) 자동 생성 equals — 금액·시각뿐, 자격증명 값 없음",
        "AccountScopedRoutes" to
            "계정 전용 뿌리 선언의 경로 문자열 비교 — 공개 URL 경로이고 비밀이 아니다",
        "BidCaseHttp" to
            "결과 → 상태 코드 매핑의 sealed 싱글턴 when 소진(BidCommandResult·TransitionOutcome·RejectionReason) — 객체 동일성, 자격증명 좌표 0",
        // M7/7C③ — 알림함의 HTTP 층 **둘**. **자리마다 javap 으로 확인했다**: `areEqual` 은
        // 각각 9·1 이고 **자격증명 좌표는 둘 다 0** 이다(`credential`·`passphrase`·`csrf`·
        // `presented` 어느 것도 참조하지 않는다). 종류는 data class 자동 equals 와 enum 어휘
        // 조회 둘뿐이고, `BidCaseResponse`·`BidCaseRequestsKt` 와 같은 형태다.
        //
        // 앞 판은 넷이었다 — `NotificationTargetResult$Item`·`$Notice` 는 r1 검토(CR LOW-3)에서
        // **중간 sealed 타입을 걷어내며 사라졌다**(판독기가 정확한 타입을 돌려준다). 타입이
        // 없어지면 등재도 함께 걷는다 — 죽은 등재는 다음 사람에게 「이미 판정된 자리」로 읽힌다.
        "NotificationItemResponse" to
            "알림 항목 응답 값 객체(data class) 자동 생성 equals — 종류·detail·공고 키·시각·읽음·묶음 계수·" +
            "합쳐 온 공고 칸뿐이고, 이 표는 자유 문자열을 저장하지 않으므로 운반할 비밀이 애초에 없다",
        "NotificationInboxRequestsKt" to
            "요청 읽기의 enum 어휘 조회(`entries.firstOrNull { it.name == token }`) — 공개 어휘 비교이고 " +
            "자격증명 값 없음(주체는 읽지 않는다 — 계정은 typed 속성, 계약 D-7B-2)",
        "OpeningResultResponse" to
            "개찰 결과 응답 값 객체(data class) 자동 생성 equals — 공고 키·상태·관측값·금액 칸뿐, 자격증명 값 없음",
        "OpeningResultControllerKt" to
            "상태·잠정 1위 토큰의 sealed 싱글턴 when 소진과 계약 매개변수 **이름** 비교 — 비교 대상이 질의 칸 이름이지 자격증명 값이 아니다",
        "DeclaredValue" to
            "질의 매개변수 하나의 값 객체(data class) 자동 생성 equals — 칸 이름과 그 원문 값 목록뿐, 자격증명 값 없음",
        "DeclaredValueKt" to
            "계약이 든 **이름**으로 질의 값을 찾는 비교(`firstValue`) — 비교 대상이 칸 이름이지 자격증명 값이 아니다",
    )

private const val MESSAGE_DIGEST_IS_EQUAL = "java/security/MessageDigest.isEqual"
private const val STRING_EQUALS = "java/lang/String.equals"
private const val INTRINSICS_AREEQUAL = "kotlin/jvm/internal/Intrinsics.areEqual"

private fun disallowedAreEqual(outputs: Map<String, String>): Set<String> =
    outputs
        .filterKeys { it !in ALLOWED_INTRINSICS_AREEQUAL }
        .filterValues { it.contains(INTRINSICS_AREEQUAL) }
        .keys

private fun packageClassOutputs(): Map<String, String> {
    val packageDir = File("build/classes/kotlin/main/bidvector/app/http")
    check(packageDir.isDirectory) {
        "빌드 산출물을 찾지 못했다: ${packageDir.absolutePath} — :app:compileKotlin 선행 필요"
    }
    return packageDir
        .walkTopDown()
        .filter { it.isFile && it.extension == "class" }
        .associate { it.nameWithoutExtension to javap(it) }
}

private fun javap(classFile: File): String {
    val javapExecutable =
        File(System.getProperty("java.home"), "bin/javap").let {
            if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) File(it.path + ".exe") else it
        }
    val process =
        ProcessBuilder(javapExecutable.absolutePath, "-p", "-v", classFile.absolutePath)
            .redirectErrorStream(true)
            .start()
    val output = process.inputStream.bufferedReader().readText()
    val exitCode = process.waitFor()
    check(exitCode == 0) { "javap 실행 실패(exit=$exitCode): ${classFile.name}\n$output" }
    return output
}
