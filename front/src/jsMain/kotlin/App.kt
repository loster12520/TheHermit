import kotlinx.browser.document

/**
 * 举例
 *
 * ```kotlin
 * // 定义一个组件
 * val Counter = component {
 *     val count by signal(0)
 *     val double by computed { count * 2 }
 *
 *     onMount {
 *         println("Counter mounted")
 *     }
 *
 *     effect {
 *         println("count = $count, double = $double")
 *     }
 *
 *     div {
 *         +"Count: $count"
 *         button("+") { count++ }
 *         +"Double: $double"
 *     }
 * }
 *
 * val root = document.getElementById("root") ?: throw IllegalStateException("Root element not found")
 * root.appendChild(Counter())
 * ```
 */

fun main() {
    val root = document.getElementById("root") ?: throw IllegalStateException("Root element not found")
    root.appendChild(document.createElement("div"))
}