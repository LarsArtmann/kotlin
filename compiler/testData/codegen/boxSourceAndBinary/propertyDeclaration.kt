// FILE: 1.kt
val d get() = c

fun box(): String = d

// FILE: 2.kt
val a get() = "OK"
val b get() = a

// FILE: 3.kt
val c get() = b
