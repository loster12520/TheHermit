package com.lignting.theHermit

import kotlinx.browser.document
import org.w3c.dom.Node
import kotlin.reflect.KProperty

abstract class AbstractPropertyContext<T>(
    val tag: String?,
    val attributePool: MutableList<Pair<String, T?>>
) : UniqueEntity() {
    private var function: UpdateDomain<T, T?>? = null
    private fun getTag(property: KProperty<*>) = tag ?: property.name
    
    private fun get(tag: String): T? =
        attributePool.firstOrNull { it.first == tag }?.second
    
    private fun set(tag: String, value: T?) =
        if (!(attributePool.any { it.first == tag }))
            attributePool.add(tag to value)
        else
            attributePool.forEachIndexed { index, (key, _) ->
                if (key == tag) attributePool[index] = key to value
            }
    
    operator fun getValue(thisRef: Any?, property: KProperty<*>): UpdateDomain<T, T?>? = function
    
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: UpdateDomain<T, T?>?) {
        value?.let { value ->
            getTag(property).also { tag ->
                function = value
                set(tag, get(tag)(tag, value))
            }
        }
    }
}

/**
 * 属性上下文，提供属性委托功能
 * @param tag 属性标签，如果为null，则使用属性名作为标签
 * @param attributePool 属性池，存储属性标签和属性值的键值对
 */
class AttributeContext(tag: String?, attributePool: MutableList<Pair<String, String?>>) :
    AbstractPropertyContext<String?>(tag, attributePool)

class PropertyContext<T>(tag: String?, attributePool: MutableList<Pair<String, T?>>) :
    AbstractPropertyContext<T>(tag, attributePool)

open class NodeContext() : UniqueEntity() {
    val childrenNodes: MutableList<Node> = mutableListOf()
    
    operator fun String.unaryPlus() = childrenNodes.add(document.createTextNode(this))
    operator fun plus(node: Node) = childrenNodes.add(node)
}

/**
 * 真实节点上下文，提供属性和子节点的存储功能
 * @param tag 节点标签
 */
open class RealNodeContext(val tag: String) : NodeContext() {
    val attributePool: MutableList<Pair<String, String?>> = mutableListOf()
    
    fun attribute(tag: String): AttributeContext = AttributeContext(tag, attributePool)
    fun attribute(): AttributeContext = AttributeContext(null, attributePool)
}