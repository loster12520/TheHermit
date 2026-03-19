package com.lignting.theHermit

import kotlin.reflect.KProperty

abstract class UniqueEntity {
    companion object {
        private var idCounter = 0
    }
    
    val id: Int by lazy { idCounter++ }
    val uuid: String by lazy { id.toString(36) }
}

/**
 * 更新域，表示一个作用范围。在这个作用范围里的所有信号，都会被自动捕获，并在信号更新时，更新域会被重新加载
 * @param Type 更新域的类型
 * @param Context 更新域的上下文类型，表示更新域所在的上下文环境，可以是一个节点上下文或者一个属性上下文等
 * @return 更新域
 */
typealias UpdateDomain<Type, Context> = Context.() -> Type?


operator fun <Type, Context> Context.invoke(tag: String, function: UpdateDomain<Type, Context>): Type? {
    effectContextStack.addLast(mutableListOf())
    val result = function()
    val list = effectContextStack.removeLast()
    list.forEach { updateUuid ->
        effectPool.getOrPut(updateUuid) { mutableListOf() }.add {
            function()
        }
    }
    return result
}

abstract class Update<T>() : UniqueEntity() {
    abstract var value: T?
    
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T? {
        effectContextStack.last().add(uuid)
        return this.value
    }
    
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        this.value = value
        // TODO: 触发更新域的重新加载
    }
}