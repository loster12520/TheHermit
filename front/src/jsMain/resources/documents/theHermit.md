# Kotlin/JS 响应式 UI 框架设计文档

## 1. 引言

本文档描述了一个基于 **Kotlin/JS** 的响应式 UI 框架的设计。该框架借鉴了 **SolidJS** 的细粒度响应式思想，利用 Kotlin 的语言特性（委托属性、DSL、协程）提供简洁、类型安全的声明式 UI 开发体验。核心设计原则如下：

- **信号（Signal）** 作为响应式数据的原子单元。
- **组件定义** 使用 `component` 构建器，确保每个组件实例拥有独立的状态。
- **渲染** 采用 Solid 风格的**细粒度更新**：DOM 节点通常只创建一次，后续由 effect 精确更新文本/属性/子区块。
- **副作用** 通过 `effect`、`onMount` 等生命周期函数管理，自动追踪依赖。

---

## 2. 核心概念

### 2.1 信号（Signal）
信号是响应式系统的基石。它是一个持有值的容器，当值变化时，所有依赖该信号的副作用和 UI 部分会自动更新。

> 本框架**统一使用委托（`by signal(...)`）**来读写信号值，以便像普通变量一样用 `=` 更新。

```kotlin
var count by signal(0)                 // 可写信号
val double by computed { count * 2 }   // 派生信号（只读）
```

### 2.2 组件（Component）
组件是 UI 的基本单元。通过 `component` 构建器定义，内部可以声明信号、计算属性和副作用，最后**直接返回** UI 结构（Node）。

```kotlin
val Counter = component {
    var count by signal(0)

    effect {
        println("Count changed: $count")
    }

    div {
        +"Count: $count"
        button {
            +"Increment"
            onclick = { count++ }
        }
    }
}
```

### 2.3 实例化与渲染
组件定义后，通过 `create()` 方法创建实例，然后渲染到 DOM 容器中。

```kotlin
val instance = Counter.create()
root.render(instance)
```

---

## 3. 快速开始

### 3.1 安装（假设）
通过 Gradle 或 Maven 引入框架依赖（具体方式待定）。

### 3.2 第一个应用
```kotlin
import com.example.framework.*

val App = component {
    var count by signal(0)

    div {
        h1 { +"Hello, Kotlin/JS!" }
        p { +"Count: $count" }
        button {
            +"Click me"
            onclick = { count++ }
        }
    }
}

fun main() {
    val root = document.getElementById("app")
    root.render(App.create())
}
```

---

## 4. API 参考

### 4.1 `component` 构建器（方案 A：组件块直接返回 Node）
```kotlin
fun <Props : ComponentProps> component(
    block: ComponentContext.(props: Props) -> Node
): Component<Props>
```
- `Props`：组件接收的属性类型，默认为 `ComponentProps`。
- `block`：组件定义块，在 `ComponentContext` 中执行；可以创建信号、computed、effect，并最终返回一个 `Node`。
- 返回 `Component<Props>`，可通过 `create(props)` 创建实例。

> 重要约束：**不要在返回 Node 的过程中创建新的信号**（信号应在组件实例初始化阶段创建）。

### 4.2 `ComponentContext`
组件定义块内的接收者，提供以下 API：

#### 信号（统一为委托式）
```kotlin
fun <T> signal(initialValue: T): SignalDelegate<T>
```
- 返回一个委托对象，用 `var xxx by signal(...)` 声明可写信号。

> 约定：`SignalDelegate<T>` 内部持有真实 `Signal<T>`，当需要把“信号引用”传递给其他函数/组件时，通过 `delegate.signal`（或等价命名）拿到。

示例：
```kotlin
var count by signal(0)
val countSignal = ::count.signal // 示例含义：获取底层 Signal 引用（具体命名以准）
```

#### 计算属性
```kotlin
fun <T> computed(compute: () -> T): ComputedDelegate<T>
```
- 返回委托对象，用 `val xxx by computed { ... }` 声明只读派生值。

#### 副作用
```kotlin
fun effect(block: () -> Unit)
```
- effect 首次执行时自动收集依赖；依赖变化时重新执行。
- 典型语义（Solid 风格）：effect **重跑前**会先执行上一次注册的 cleanup（若有）。

```kotlin
effect {
    console.log("count changed to $count")
}
```

#### 生命周期
```kotlin
fun onMount(callback: () -> Unit)
fun onCleanup(callback: () -> Unit)
```
- `onMount`：DOM 插入到文档后执行一次。
- `onCleanup`：组件卸载前执行，用于清理订阅、定时器、协程等。

# （不提供 render API：方案 A 中组件块直接返回 Node）

### 4.3 组件实例
调用 `Component<Props>.create(props: Props = ...)` 返回一个 `ComponentInstance` 对象，该对象包含：
- `node: Node`（或 `render(): Node`）：当前实例对应的根节点。
- 内部管理信号、computed、effect 与生命周期。
- `dispose()`：卸载/销毁实例，触发 `onCleanup`，并取消所有订阅（框架在 unmount 时自动调用）。

### 4.4 DSL 构建
框架提供一组 DSL 函数用于构建 DOM 节点，例如：
```kotlin
fun div(classes: String? = null, block: TagDiv.() -> Unit): Div
fun button(classes: String? = null, block: TagButton.() -> Unit): Button
fun text(content: String): Text
```
每个标签函数接收一个 lambda，用于设置属性和子节点。在 lambda 中，可以通过 `+` 操作符添加文本节点，或直接调用其他标签函数。

示例：
```kotlin
div("container") {
    h1 { +"Title" }
    p { +"Paragraph" }
    button {
        +"Click"
        onclick = { /* ... */ }
    }
}
```

### 4.5 信号读写（委托式）
通过委托属性声明的信号，可以直接读写：
```kotlin
var count by signal(0)
count = 5          // 写
println(count)     // 读
```

当你需要传递“信号本身”（而不是当前值）时，使用委托暴露的底层 `Signal<T>` 引用（命名以实现为准，例如：`delegate.signal` / `SignalDelegate.signal`）。

---

## 5. 高级用法

### 5.1 带属性的组件
组件可以接收外部属性，通过 `component<Props>` 指定类型，并在组件块里使用 `props`。

```kotlin
data class GreetingProps(val name: String)

val Greeting = component<GreetingProps> { props ->
    div {
        +"Hello, ${props.name}!"
    }
}

// 使用
Greeting.create(GreetingProps("Alice"))
```

### 5.2 动态列表（Solid 风格建议：For/Index）
如果列表不会增删重排，直接 `forEach`/`map` 生成即可：

```kotlin
val items by signal(listOf("Apple", "Banana"))

ul {
    items.forEach { item ->
        li { +item }
    }
}
```

如果列表会**添加/删除/重排**，推荐使用专门的 `forList`（类似 Solid 的 `<For>`），以最小化 DOM 变更：

```kotlin
forList(items, key = { it }) { item ->
    li { +item }
}
```

> 建议：`forList` 支持 `key`，用于稳定复用/移动 DOM 节点（而不是整段重建）。

### 5.3 条件渲染（Solid 风格：Show）
可以使用 `if/when`，但为了更明确的区块管理与卸载 cleanup，建议提供 `show`：

```kotlin
show(condition) {
    div { +"Condition is true" }
} fallback {
    div { +"Condition is false" }
}
```

### 5.4 组合组件
组件可以嵌套使用：

```kotlin
val App = component {
    var user by signal(User("Alice"))

    div {
        Header()
        UserProfile(user)
        Footer()
    }
}
```

---

## 6. 响应式原理（简要，细粒度 DOM effect：不使用 diff）

- **信号**：内部维护订阅者集合。当值变化时，调度所有订阅者重新执行。
- **计算属性（computed）**：首次读取时计算并收集依赖；依赖变化后标记为脏（lazy），下次读取时再重新计算。
- **副作用（effect）**：执行时开启依赖追踪，收集读取到的信号并订阅；依赖变化时重跑，并在重跑前执行 cleanup。
- **渲染/DOM 更新**（Solid 风格）：
  - 初次渲染时构建 DSL 并创建 DOM 节点；
  - 当信号变化时，**不会重新构建整棵树，也不会 diff**；而是由框架在“文本节点 / 属性 / 子区块插槽”上建立 effect，精准改写对应的 DOM。
  - `Show` / `For` 这类控制流原语负责管理“DOM 区块”的创建、插入、移动与销毁，并在销毁时触发对应的 cleanup。

---

## 7. 最佳实践

### 7.1 将信号定义在 `component` 块内
确保每个组件实例拥有独立的状态，避免意外共享。

### 7.2 使用 `computed` 代替重复计算
对于从信号派生的值，使用 `computed` 缓存结果，避免在 UI/绑定中重复计算。

### 7.3 避免在渲染过程中创建新信号
信号应在组件实例初始化时创建；UI 构建只负责读取信号并声明绑定。

### 7.4 清理副作用
在 `onCleanup` 中取消订阅、清除定时器等，防止内存泄漏。

### 7.5 使用 `show` / `forList` 等控制流函数
用明确的控制流原语管理区块的创建/销毁，避免“条件切换后 effect 没销毁”这类问题。

---

## 8. 与 React/Solid 对比

| 特性 | React | Solid | 本框架 |
|:---|:---|:---|:---|
| 响应式基础 | 虚拟 DOM + Diff | 细粒度信号 | 细粒度信号（Kotlin 委托） |
| DOM 更新 | 重渲染 + Diff | 精确更新 DOM | 精确更新 DOM（无 diff） |
| 组件定义 | 函数/类 | 函数 | `component` 构建器 |
| 状态声明 | `useState` | `createSignal` | `var x by signal(...)` |
| 派生状态 | `useMemo` | `createMemo` | `val x by computed { ... }` |
| 副作用 | `useEffect` | `createEffect` | `effect { ... }` |
| 组件复用 | 自动（函数每次执行） | 自动（实例隔离） | 显式 `.create()` |
| 类型安全 | 有限（TypeScript） | 有限 | Kotlin 强类型 |

---

## 9. 未来展望

- **编译器插件**：将 DSL 编译为精确的 DOM 操作，消除运行时开销。
- **Store 机制**：针对嵌套对象的响应式 Proxy 方案。
- **路由集成**：官方路由库。
- **服务端渲染（SSR）**：支持 Kotlin/JS 在 Node.js 环境渲染为 HTML。
- **开发工具**：DevTools 扩展，用于查看信号依赖关系、组件更新等。

---

## 10. 结语

本框架旨在结合 Kotlin 的语言优势和 Solid 的响应式思想，提供一个高效、易用、类型安全的 UI 开发方案。欢迎社区贡献与反馈！