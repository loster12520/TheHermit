import com.lignting.theHermit.RealNodeContext
import com.lignting.theHermit.signal
import com.lignting.theHermit.invoke
import kotlinx.browser.document
import kotlinx.browser.window

// 定义div的信息
class DivContext : RealNodeContext("div") {
    var className by attribute("class")
    var `data-text` by attribute()
}

// 实现一个div的真实节点
val div = { function: DivContext.() -> Unit ->
    DivContext()(function)
}

var text by "Hello, World!".signal()

// 创建一个真实节点
val divNode = div {
    className = { "flex" }
    `data-text` = { text }
    
    div {
        className = { "flex" }
        `data-text` = { text }
        
        +{ text }
    }
}

fun main() {
    window.onload = {
        val body = document.body ?: throw IllegalStateException("Root element not found")
        body.appendChild(divNode)
    }
    
    window.setInterval({
        text += "111"
    }, 1000)
}