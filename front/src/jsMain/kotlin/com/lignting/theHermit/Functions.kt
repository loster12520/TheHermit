package com.lignting.theHermit

import org.w3c.dom.Element

/**
 * 所有组件通用的 props 基类。
 *
 * 说明：
 * - 这里只包含非常常见的字段（例如 id、className）。
 * - 用户可以通过继承此类来扩展自己的组件 props，例如包含回调、初始数据等。
 *
 * 使用示例：
 * ```kotlin
 * data class MyProps(
 *   override val id: String? = null,
 *   val count: Int = 0
 * ) : ComponentProps(id)
 * ```
 */
open class ComponentProps(
    open val id: String? = null,
)

/**
 * 组件运行时的上下文对象。
 *
 * ComponentContext 在组件实例化时创建并与组件实例生命周期绑定，提供：
 * - 创建局部响应式状态的能力（`signal`）
 * - 创建计算属性（`computed`）
 * - 注册生命周期回调（`onMount` / `onCleanup`）
 * - 注册副作用（`effect`），依赖收集会自动跟踪 signal/computed 的使用。
 *
 * 设计说明：
 * - 所有通过 `ComponentContext.signal` 创建的状态应当仅在当前组件内部使用；若要在外部共享，请显式导出或使用更高级的状态管理方案。
 * - `onMount` 注册的回调会在组件挂载到 DOM 后被调用，适合做 DOM 读尺寸、启动动画、订阅事件等操作。
 * - `onCleanup` 注册的回调会在组件被销毁或 `dispose` 时执行，适合移除监听器、取消定时器、停止副作用等。
 * - `effect` 可用于声明与外部系统交互的副作用（网络、LocalStorage、document.title 等）。当所依赖的 signal/computed 值发生变化时，effect 会被重新执行。
 *
 * 注意事项（边界情况）：
 * - effect 内部若创建了资源（例如定时器），请在 effect 返回的清理闭包或通过 `onCleanup` 进行清理。
 * - 多次调用 `effect` 会返回独立的副作用（当前 API 返回值被内部管理），若需要手动停止，请扩展 `effect` 的返回值以暴露 stop/cleanup API。
 */
class ComponentContext {
    /**
     * 在组件内部创建一个可变的响应式状态（signal）。
     *
     * 参数：initial - 初始值
     * 返回值：一个可作为属性委托使用的对象，写法示例：
     * ```kotlin
     * val count by signal(0)
     * ```
     */
    fun <T> signal(initial: T) = com.lignting.theHermit.signal(initial)
    
    /**
     * 创建一个基于其他 signal/computed 的计算属性（computed）。
     *
     * 参数：compute - 计算函数，会自动追踪该函数中读取的 signal/computed 作为依赖。
     * 读取 computed 时会返回缓存值，依赖发生变化时会自动刷新缓存并触发依赖它的 effect/组件更新。
     *
     * 使用示例：
     * ```kotlin
     * val double by computed { count * 2 }
     * ```
     */
    fun <T> computed(compute: () -> T) = com.lignting.theHermit.computed(compute)
    
    private val mountCallbacks: MutableList<() -> Unit> = mutableListOf()
    private val cleanupCallbacks: MutableList<() -> Unit> = mutableListOf()
    private val effects: MutableList<Effect> = mutableListOf()
    
    /**
     * 注册组件挂载时调用的回调。
     *
     * 回调将在 `ComponentInstance.mount` 成功把根节点插入到父元素之后执行。
     */
    fun onMount(cb: () -> Unit) = mountCallbacks.add(cb)
    
    /**
     * 注册组件销毁/清理时调用的回调。
     *
     * 回调将在 `ComponentInstance.dispose` 执行时触发，适合用于释放在组件内分配的外部资源。
     */
    fun onCleanup(cb: () -> Unit) = cleanupCallbacks.add(cb)
    
    /**
     * 在组件内部注册一个副作用（effect）。
     *
     * 副作用会在其依赖的响应式状态发生变化时被重新执行。通常用于与外部系统交互的逻辑。
     * 如果需要在组件销毁时停止副作用，可以在 effect 内部或通过 `onCleanup` 手动进行清理。
     */
    fun effect(block: () -> Unit) = com.lignting.theHermit.effect(block)

    internal fun runMounts() { mountCallbacks.forEach { it() } }
    internal fun runCleanups() { cleanupCallbacks.forEach { it() } }
}

/**
 * 组件实例对象，包含：
 * - `root`：该组件生成的 DOM 根节点
 * - `context`：组件的运行时上下文（用于管理生命周期与内部状态）
 *
 * API：
 * - `mount(parent)`：将组件挂载到指定父节点，并触发 `onMount` 回调
 * - `dispose()`：将组件从 DOM 中移除并触发 `onCleanup` 回调
 */
class ComponentInstance(val root: Element, private val context: ComponentContext) {
    /**
     * 将组件插入到指定父元素中并触发挂载回调。
     */
    fun mount(parent: Element) {
        parent.appendChild(root)
        context.runMounts()
    }
    
    /**
     * 清理组件：触发注册的清理回调并从 DOM 中移除根节点。
     */
    fun dispose() {
        context.runCleanups()
        root.remove()
    }
}

/**
 * 组件定义类。将组件的构建逻辑作为 `block` 参数传入，`block` 在组件实例化时以 `ComponentContext` 为接收者执行，
 * 并返回一个 DOM 根节点。
 *
 * 说明：组件本身是无状态的（只封装渲染逻辑），状态应通过 `ComponentContext.signal` 在组件运行时创建。
 */
class Component<P : ComponentProps>(private val block: ComponentContext.(P) -> Element) {
    /**
     * 创建组件实例并返回 `ComponentInstance`。
     *
     * 注意：每次调用 `create` 都会创建全新的 `ComponentContext` 和根节点，因此该实例与其他实例相互独立。
     */
    fun create(props: P): ComponentInstance {
        val ctx = ComponentContext()
        val node = ctx.block(props)
        return ComponentInstance(node, ctx)
    }
}

/**
 * 快捷构造函数：把一个接收 `ComponentContext` 和 `props` 的渲染函数包装为 `Component` 实例。
 *
 * 使用示例：
 * ```kotlin
 * val MyComp = component<MyProps> { props ->
 *   val count by signal(0)
 *   div { /* build DOM */ }
 * }
 *
 * val instance = MyComp.create(MyProps())
 * instance.mount(root)
 * ```
 */
fun <T : ComponentProps> component(callback: ComponentContext.(T) -> Element): Component<T> = Component(callback)
