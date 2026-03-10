package com.lignting.theHermit

/**
 * 真实节点上下文栈，用于存储当前正在构建的节点上下文，支持嵌套节点的构建
 */
val nodeContextStack = ArrayDeque<NodeContext>()