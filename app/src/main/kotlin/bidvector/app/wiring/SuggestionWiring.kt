package bidvector.app.wiring

import bidvector.workflow.evaluation.CandidateSourcePort
import bidvector.workflow.evaluation.LicenseGatePort
import bidvector.workflow.evaluation.OperatorProfilePort
import bidvector.workflow.evaluation.WatchSubjectPort
import bidvector.workflow.strategy.Clock
import bidvector.workflow.strategy.StrategyReader
import bidvector.workflow.suggestion.ListSuggestionsUseCase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 제안 목록 경로의 조립(M7/7C①b) — 새 어댑터가 없다. 후보 원천·감시 subject·면허 게이트·프로필은
 * `EvaluationWiring` 이, 전략 읽기는 `PersistenceWiring` 이 이미 낸 빈을 그대로 받는다(같은 값의 두 번째
 * 빈 정의를 만들지 않는다).
 *
 * **계산 예산은 후보 원천의 스캔 상한(`bidvector.evaluation.candidate-cap`)이다.** 표시 상한(전략의
 * `candidateLimit`)은 이 조립 어디에도 들어오지 않는다 — use case 가 게이트를 다 돈 **뒤에** 전략에서 읽어
 * 자른다(STR-06, 두 값이 다른 자리에서 온다).
 *
 * **비활성화 경로**: [suggestionQuery] 빈을 빼면 제안 목록 경로 전체가 사라진다(컨트롤러가 조회기를
 * 요구하므로 기동이 실패한다) — `NoticeSearchWiring` 과 같은 끄는 자리다.
 */
@Configuration
open class SuggestionWiring {
    @Bean
    open fun listSuggestionsUseCase(
        strategyReader: StrategyReader,
        operatorProfilePort: OperatorProfilePort,
        candidateSourcePort: CandidateSourcePort,
        watchSubjectPort: WatchSubjectPort,
        licenseGatePort: LicenseGatePort,
        clock: Clock,
    ): ListSuggestionsUseCase =
        ListSuggestionsUseCase(
            strategies = strategyReader,
            profiles = operatorProfilePort,
            candidateSource = candidateSourcePort,
            watchSubjects = watchSubjectPort,
            licenseGate = licenseGatePort,
            clock = clock,
        )

    @Bean
    open fun suggestionQuery(listSuggestionsUseCase: ListSuggestionsUseCase): SuggestionQuery =
        SuggestionQuery(listSuggestionsUseCase)
}
