package bidvector.archfixture.violating.procurement

import java.util.ResourceBundle

/** classpath 자원 I/O. 추가 의존이 0 이라 1차 게이트가 볼 것이 없다. */
class ResourceBundleLeak {
    fun label(name: String): String = ResourceBundle.getBundle(name).getString("k")
}
