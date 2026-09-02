package bidvector.archfixture.violating.sharedkernel

import java.io.File

/** I/O 위반 — `milestone-1.md` 「구현 규칙」이 domain 을 "I/O가 없는 입력→출력 함수/객체"로 규정한다. */
class FileIoLeak {
    fun exists(file: File): Boolean = file.exists()
}
