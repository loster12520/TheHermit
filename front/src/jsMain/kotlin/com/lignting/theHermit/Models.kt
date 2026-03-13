package com.lignting.theHermit

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
typealias UpdateDomain<Type,Context> = Context.() -> Type?