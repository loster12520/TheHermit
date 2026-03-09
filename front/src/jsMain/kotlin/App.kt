import com.lignting.theHermit.RealNodeContext
import com.lignting.theHermit.realNode
import kotlinx.browser.document
import kotlinx.browser.window

// 定义div的信息
class DivContext : RealNodeContext("div") {
    var className: String? by attribute("class")
    var `data-text`: String? by attribute()
}

// 实现一个div的真实节点
val div = DivContext().realNode()


// 创建一个真实节点
val divNode = div {
    className = "flex"
    `data-text` = "Hello, World!"
    
    div {
        className = "flex"
        `data-text` = "Hello, World!"
    }
    
    div {
        className = "flex"
        `data-text` = "Hello, World!"
        
        +"Hello, World!"
    }
}

fun main() {
    window.onload = {
        val body = document.body ?: throw IllegalStateException("Root element not found")
        body.appendChild(divNode)
    }
}