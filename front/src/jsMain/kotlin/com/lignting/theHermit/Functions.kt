package com.lignting.theHermit

import kotlinx.browser.document
import org.w3c.dom.Node
import kotlin.reflect.KProperty

/**
 * 构建真实节点的函数，接受一个节点上下文作为参数，返回一个函数，该函数接受一个lambda表达式作为参数，在lambda表达式中可以设置节点的属性和子节点，最终返回构建好的节点
 * @param realNodeContext 节点上下文，用于存储节点的属性和子节点信息
 * @return 一个函数，该函数接受一个lambda表达式作为参数，在lambda表达式中可以设置节点的属性和子节点，最终返回构建好的节点
 */
fun <T : RealNodeContext> realNode(
    realNodeContext: T
): (T.() -> Unit) -> Node = { function: T.() -> Unit ->
    nodeContextStack.addLast(realNodeContext)
    
    // 执行函数，获取属性
    realNodeContext.function()
    
    nodeContextStack.removeLast()
    
    val resultNode = document.createElement(realNodeContext.tag)
    realNodeContext.attributePool.forEach { (key, value) ->
        value?.also {
            resultNode.setAttribute(key, it)
        }
    }
    realNodeContext.childrenNodes.forEach { child ->
        resultNode.appendChild(child)
    }
    
    // 如果有父节点，则将当前节点添加到父节点的子节点列表中
    if (nodeContextStack.isNotEmpty()) {
        val parentContext = nodeContextStack.last()
        parentContext.childrenNodes.add(resultNode)
    }
    
    // 返回当前节点
    resultNode
}

/**
 * 真实节点构建器，接受一个节点上下文作为参数，返回一个函数，该函数接受一个lambda表达式作为参数，在lambda表达式中可以设置节点的属性和子节点，最终返回构建好的节点
 * @receiver T 继承自RealNodeContext的节点上下文类型
 * @return 一个函数，该函数接受一个lambda表达式作为参数，在lambda表达式中可以设置节点的属性和子节点，最终返回构建好的节点
 */
fun <T : RealNodeContext> T.realNode(): (T.() -> Unit) -> Node = realNode(this)

/**
 * 信号，表示一个可变的数据，可以被观察和更新。当信号更新时，所有依赖于该信号的更新域都会被重新加载
 * @param T 信号的数据类型
 * @property data 信号的数据
 */
class Signal<T>(value: T) {
    var data: T = value
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        // TODO: 添加依赖关系
        return data
    }
    
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        data = value
        // TODO: 触发更新域的重新加载
    }
}

fun <T> signal(value: T): Signal<T> = Signal(value)

fun <T> T.signal(): Signal<T> = signal(this)