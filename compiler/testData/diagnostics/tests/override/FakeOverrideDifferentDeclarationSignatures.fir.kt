interface A {
    fun f(): String = "string"
}

open class B {
    open fun f(): CharSequence = "charSequence"
}

<!MANY_IMPL_MEMBER_NOT_IMPLEMENTED!>class C<!> : B(), A

val obj: A = object : B(), A {}
