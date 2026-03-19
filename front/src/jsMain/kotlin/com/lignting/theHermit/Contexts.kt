package com.lignting.theHermit

import kotlinx.browser.document
import org.w3c.dom.Element
import org.w3c.dom.Node
import kotlin.reflect.KProperty

abstract class AbstractPropertyContext<T>(
    val tag: String?,
    val get: (String) -> T?,
    val set: (String, T?) -> Unit
) : UniqueEntity() {
    private var function: UpdateDomain<T, T?>? = null
    private fun getTag(property: KProperty<*>) = tag ?: property.name
    
    operator fun getValue(thisRef: Any?, property: KProperty<*>): UpdateDomain<T, T?>? = function
    
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: UpdateDomain<T, T?>?) {
        value?.let { value ->
            function = value
            getTag(property).also { tag ->
                get(tag)() {
                    set(tag, value())
                }
            }
        }
    }
}

/**
 * 属性上下文，提供属性委托功能
 * @param tag 属性标签，如果为null，则使用属性名作为标签
 * @param get 获取属性值的函数，接受一个标签参数，返回属性值
 * @param set 设置属性值的函数，接受一个标签参数和一个属性值参数，将属性值设置到标签对应的属性上
 */
class AttributeContext(
    tag: String?,
    get: (String) -> String?,
    set: (String, String?) -> Unit
) : AbstractPropertyContext<String?>(tag, get, set)

class PropertyContext<T>(
    tag: String?,
    get: (String) -> T?,
    set: (String, T?) -> Unit
) : AbstractPropertyContext<T>(tag, get, set)

open class NodeContext() : UniqueEntity() {
    var node: Element? = null
    val childrenNodes: MutableList<Node> = mutableListOf()
    
    fun get(tag: String): String? =
        node?.getAttribute(tag)
    
    fun set(tag: String, value: String?) =
        value?.let {
            node?.setAttribute(tag, value)
        }
    
    
    operator fun plus(node: Node) = childrenNodes.add(node)
    operator fun String.unaryPlus() = childrenNodes.add(document.createTextNode(this))
    
    class TextNodeContext(var text: String) : UniqueEntity() {
        val node = document.createTextNode(text)
    }
    
    operator fun UpdateDomainContextless<String>.unaryPlus() {
        val node = TextNodeContext(this.invoke() ?: "")
        val func = this
        val function: UpdateDomain<String, TextNodeContext> = {
            text = func() ?: ""
            this.node.textContent = text
            text
        }
        node(function)
        
        this@NodeContext.childrenNodes.add(node.node)
    }
}

/**
 * 真实节点上下文，提供属性和子节点的存储功能
 * @param tag 节点标签
 */
open class RealNodeContext(val tag: String) : NodeContext() {
    fun attribute(tag: String): AttributeContext = AttributeContext(tag, this::get, this::set)
    fun attribute(): AttributeContext = AttributeContext(null, this::get, this::set)
}