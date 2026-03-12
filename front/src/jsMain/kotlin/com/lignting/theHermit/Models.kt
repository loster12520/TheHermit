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
 * @param T 更新域的类型
 * @return 更新域
 */
typealias UpdateDomain<T> = () -> T?