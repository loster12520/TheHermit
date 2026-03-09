package com.lignting.theHermit

//import kotlinx.browser.document
//import org.w3c.dom.events.Event
//
//data class DivProps(
//    override val id: String? = null,
//    val className: String? = null,
//    val onClick: ((Event) -> Unit)? = null
//) : ComponentProps(id)
//
//val div = component<DivProps> { props ->
//    document.createElement("div").apply {
//        props.id?.let { id = it }
//        props.className?.let { className = it }
//        props.onClick?.let {
//            addEventListener("click", it, false)
//        }
//    }
//}