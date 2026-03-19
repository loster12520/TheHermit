package com.lignting.theHermit

import kotlinx.browser.document
import org.w3c.dom.Node

operator fun <T : RealNodeContext> T.invoke(function: T.() -> Unit): Node {
    node = document.createElement(this.tag)
    
    nodeContextStack.addLast(this)
    // 执行函数，获取属性
    this.function()
    nodeContextStack.removeLast()
    
    this.childrenNodes.forEach { child ->
        node?.appendChild(child)
    }
    
    // 如果有父节点，则将当前节点添加到父节点的子节点列表中
    if (nodeContextStack.isNotEmpty()) {
        val parentContext = nodeContextStack.last()
        parentContext.childrenNodes.add(node ?: throw RuntimeException("An impossible exception"))
    }
    
    // 返回当前节点
    return node ?: throw RuntimeException("An impossible exception")
}

typealias Signal<T> = Update<T>

fun <T> signal(value: T): Signal<T> = object : Update<T>() {
    override var value: T? = value
}

fun <T> T.signal(): Signal<T> = signal(this)