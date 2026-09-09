package bidvector.workflow.notification

/**
 * 배포 환경(scope.md ⑦) — `ENVIRONMENT` 문자열을 이 값으로 바꾸는 배선은 이 slice 밖
 * (app/M6). 커널은 이 값을 사실로 받는다(설계 검토 (0)) — 잘못 만들어진 값을 이 slice가
 * 검증하지 않는다.
 */
enum class RuntimeEnvironment {
    Production,
    Staging,
    Development,
    Test,
}
