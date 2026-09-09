package bidvector.workflow.notification

/** 배달 채널(scope.md ①) — 값은 이 slice가 아는 것만. 추가는 채널별 sender 어댑터가 생길 때. */
enum class Channel {
    Telegram,
    Email,
}
