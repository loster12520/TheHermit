package com.lignting.theHermit

class Signal {
    
}

fun <T> signal(value: T): Nothing = TODO()

fun <T> T.signal(): Nothing = signal(this)