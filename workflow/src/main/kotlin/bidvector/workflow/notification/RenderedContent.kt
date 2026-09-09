package bidvector.workflow.notification

/** 렌더러 port 산출(scope.md ①) — 사람이 읽는 문장은 여기에만 산다. 렌더러 구현(내용의 옳음)은 이 slice 밖. */
data class RenderedContent(
    val channel: Channel,
    val body: String,
)
