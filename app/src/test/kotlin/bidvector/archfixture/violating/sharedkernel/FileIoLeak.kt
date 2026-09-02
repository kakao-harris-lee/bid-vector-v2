package bidvector.archfixture.violating.sharedkernel

import java.io.File

/** I/O 위반 — milestone-1.md:62 는 domain 을 "I/O 가 없는 입력→출력"으로 규정한다. */
class FileIoLeak {
    fun exists(file: File): Boolean = file.exists()
}
