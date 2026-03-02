package com.lignting.theHermit

import kotlin.reflect.KProperty

/**
 * 响应式核心（极简版）
 *
 * 提供三个基础能力：
 * - [signal]：可变状态。读取时收集依赖，写入时通知订阅者。
 * - [computed]：派生状态（带缓存）。依赖变化时自动重新计算。
 * - [effect]：副作用。会自动追踪其依赖的 signal/computed，并在依赖变化时重新执行。
 *
 * 当前实现刻意保持简单：
 * - 更新为同步立即执行（无 batch/调度器）。
 * - 无循环依赖检测；effect 内写入自己依赖的 signal 可能导致无限递归。
 */

/**
 * 当前正在执行的 [Effect]，用于依赖收集。
 *
 * 原理：
 * - 当 effect 执行时会临时把自己放到这里。
 * - signal/computed 在 get 时发现这里非空，就把该 effect 记录为订阅者。
 */
private var currentEffect: Effect? = null

/**
 * signal 的底层实现。
 *
 * 一般情况下你只需要使用 [signal] 创建可作为属性委托的 [SignalDelegate]。
 *
 * 依赖收集：
 * - 调用 [get] 时，如果当前存在正在执行的 [Effect]（见 [currentEffect]），该 effect 会订阅此 signal。
 * - 调用 [set] 且值发生变化时，会同步通知所有订阅者 effect 重新执行。
 */
class SignalImpl<T>(initial: T) {
    private var _value: T = initial
    internal val subscribers: MutableSet<Effect> = mutableSetOf()

    /**
     * 读取当前值并进行依赖收集。
     */
    fun get(): T {
        currentEffect?.let { eff ->
            // register dependency
            if (!subscribers.contains(eff)) {
                subscribers.add(eff)
                eff.deps.add(this)
            }
        }
        return _value
    }

    /**
     * 更新值并通知订阅者。
     */
    fun set(value: T) {
        val changed = if (_value is Any) {
            _value != value
        } else {
            // fallback reference compare
            _value !== value
        }
        if (!changed) return
        _value = value
        // notify subscribers (snapshot to avoid concurrent modification)
        val list = subscribers.toList()
        for (s in list) {
            s.schedule()
        }
    }

    /**
     * 清空订阅者。
     *
     * 仅供内部/调试用途：正常情况下请通过 [Effect.stop] 来解除订阅关系。
     */
    internal fun clear() {
        subscribers.clear()
    }

    fun removeSubscriber(e: Effect) {
        subscribers.remove(e)
    }
}

/**
 * signal 的属性委托包装。
 *
 * 用法：
 * ```kotlin
 * var count by signal(0)
 * count++
 * ```
 */
class SignalDelegate<T>(internal val signal: SignalImpl<T>) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = signal.get()
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) = signal.set(value)
}

/**
 * 创建一个 signal，并返回可作为属性委托的对象。
 *
 * 示例：
 * ```kotlin
 * var count by signal(0)
 * effect { println(count) }
 * count = 1
 * ```
 */
fun <T> signal(initial: T): SignalDelegate<T> = SignalDelegate(SignalImpl(initial))

/**
 * computed：派生状态（带缓存）。
 *
 * 工作方式：
 * - 内部用一个 [Effect]（[recompute]）来执行 [compute]，因此 compute() 内部读取到的 signal
 *   会自动订阅到该 effect 上。
 * - 当依赖 signal 变化时，会触发 [recompute] 重新执行，从而刷新缓存值。
 * - 为了让“读取 computed 的 effect”也能在 computed 变化时重新执行，这里用一个内部的哨兵 signal（[backing]）来转发通知：
 *   - 读取 computed 时会读一下 backing（收集依赖）
 *   - computed 值变化时 backing 自增，通知订阅者
 *
 * 约定与建议：
 * - [compute] 应当是纯函数：不要在 compute 里写 signal、做网络请求等副作用。
 * - 变更判断使用 `!=`（equals 语义）。如果你需要更细粒度的变更判定，请自行封装值类型。
 *
 * 示例：
 * ```kotlin
 * var count by signal(1)
 * val double by computed { count * 2 }
 * effect { println("double = $double") }
 * count = 2
 * ```
 */
class ComputedDelegate<T>(private val compute: () -> T) {
    private var cached: T? = null
    private var initialized = false

    /**
     * 哨兵 signal（计数器）。任何读取 computed 的 effect 都会订阅到这里。
     * computed 值变化时对该计数器 +1，以触发订阅者重新执行。
     */
    private val backing = SignalImpl(0)

    /**
     * 负责重新计算 computed 的 effect。
     *
     * 依赖收集发生在这里：compute() 读取的 signal 会把该 effect 注册为订阅者。
     */
    private val recompute = Effect {
        val value = compute()
        val changed = if (!initialized) true else (cached != value)
        cached = value
        initialized = true
        if (changed) {
            // bump backing to notify subscribers
            val current = backing.get()
            backing.set(current + 1)
        }
    }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        if (!initialized) {
            recompute.runOnce()
        }
        // 读取 backing：把“当前 effect”订阅到 backing，从而间接订阅该 computed。
        backing.get()
        @Suppress("UNCHECKED_CAST")
        return cached as T
    }
}

/**
 * 创建一个 computed，并返回可作为属性委托的对象。
 */
fun <T> computed(compute: () -> T): ComputedDelegate<T> = ComputedDelegate(compute)

/**
 * effect：声明一个副作用。
 *
 * 核心语义：
 * - 第一次注册时会立即执行一次。
 * - 执行过程中读取到的 signal/computed 会成为它的依赖；当依赖变化时，该 effect 会被同步触发重新执行。
 *
 * 限制（务必注意）：
 * - 当前实现没有批处理/去抖，短时间内多次 set 可能导致 effect 连续同步执行。
 * - 如果 effect 内部写入了它自己依赖的 signal，很容易形成无限递归。
 *
 * 生命周期：
 * - 调用 [stop] 可以停止 effect 并解除订阅（避免内存泄漏）。
 * - 目前代码中预留了 cleanup 变量，但尚未提供对外 API 来设置它；你可以在后续扩展 effect API 时使用。
 */
class Effect(internal val block: () -> Unit) {
    internal val deps: MutableSet<SignalImpl<*>> = mutableSetOf()
    private var active = true

    // 预留：未来可扩展为 effect(block: () -> (() -> Unit)?) 之类的形式
    private var cleanup: (() -> Unit)? = null

    /**
     * 立即执行一次：
     * - 先清理上一次收集到的依赖订阅
     * - 再执行 block 并重新收集依赖
     */
    fun runOnce() {
        cleanup?.invoke()
        for (d in deps) d.removeSubscriber(this)
        deps.clear()
        val prev = currentEffect
        currentEffect = this
        try {
            block()
        } finally {
            currentEffect = prev
        }
    }

    /**
     * 被 signal 通知时调用。
     *
     * 当前为同步立即执行；如果未来要做调度器，这里是最适合的切入点。
     */
    fun schedule() {
        if (!active) return
        runOnce()
    }

    /**
     * 停止 effect 并解除订阅。
     */
    fun stop() {
        active = false
        cleanup?.invoke()
        for (d in deps) d.removeSubscriber(this)
        deps.clear()
    }
}

/**
 * 注册并立即执行一个 [Effect]。
 *
 * 返回创建的 effect，必要时你可以调用 `stop()`。
 */
fun effect(block: () -> Unit): Effect {
    val e = Effect(block)
    e.runOnce()
    return e
}
