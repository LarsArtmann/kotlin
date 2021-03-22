interface A {
    fun foo() {}
}

interface B : A {
    override fun foo() {}
}

class C : B, A
