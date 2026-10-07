package bidvector.archfixture.violating.app

import bidvector.adapters.evaluation.EvaluationCommitRun

/**
 * D-6F10-15 위반 표본 — dry-run 조립이 **커밋 조립 타입**(`EvaluationCommitRun`, 실
 * production 클래스)을 참조한다. 그 참조가 생기면 요청 스코프 dry-run 이 outbox 에 쓰는
 * 경로를 손에 넣는다(effect 0 이 깨진다). production classpath 에는 오르지 않는다(test 소스).
 */
class RogueDryRunCommitReferencer(
    private val commit: EvaluationCommitRun,
)
