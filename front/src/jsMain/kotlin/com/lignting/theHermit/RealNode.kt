package com.lignting.theHermit

import kotlinx.browser.document
import org.w3c.dom.Node
import kotlin.reflect.KProperty

/**
 * 属性上下文，提供属性委托功能
 * @param tag 属性标签，如果为null，则使用属性名作为标签
 * @param attributePool 属性池，存储属性标签和属性值的键值对
 */
class AttributeContext(val tag: String?, val attributePool: MutableList<Pair<String, String?>>) {
    private fun getTag(property: KProperty<*>) = tag ?: property.name
    
    operator fun getValue(thisRef: Any?, property: KProperty<*>): String? =
        attributePool.firstOrNull { it.first == getTag(property) }?.second
    
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: String?) {
        val tag = getTag(property)
        if (!(attributePool.any { it.first == tag }))
            attributePool.add(tag to value)
        else
            attributePool.forEachIndexed { index, (key, _) ->
                if (key == tag) attributePool[index] = key to value
            }
    }
}

/**
 * 真实节点上下文栈，用于存储当前正在构建的节点上下文，支持嵌套节点的构建
 */
val realNodeContextStack = ArrayDeque<RealNodeContext>()

/**
 * 真实节点上下文，提供属性和子节点的存储功能
 * @param tag 节点标签
 */
open class RealNodeContext(val tag: String) {
    val attributePool: MutableList<Pair<String, String?>> = mutableListOf()
    val childrenNodes: MutableList<Node> = mutableListOf()
    
    fun attribute(tag: String): AttributeContext = AttributeContext(tag, attributePool)
    fun attribute(): AttributeContext = AttributeContext(null, attributePool)
    
    operator fun String.unaryPlus() = childrenNodes.add(document.createTextNode(this))
    operator fun plus(node: Node) = childrenNodes.add(node)
}


/**
 * 构建真实节点的函数，接受一个节点上下文作为参数，返回一个函数，该函数接受一个lambda表达式作为参数，在lambda表达式中可以设置节点的属性和子节点，最终返回构建好的节点
 * @param realNodeContext 节点上下文，用于存储节点的属性和子节点信息
 * @return 一个函数，该函数接受一个lambda表达式作为参数，在lambda表达式中可以设置节点的属性和子节点，最终返回构建好的节点
 */
fun <T : RealNodeContext> realNode(
    realNodeContext: T
): (T.() -> Unit) -> Node = { function: T.() -> Unit ->
    // 执行函数，获取属性
    realNodeContextStack.addLast(realNodeContext)
    realNodeContext.function()
    realNodeContextStack.removeLast()
    
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
    if (realNodeContextStack.isNotEmpty()) {
        val parentContext = realNodeContextStack.last()
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