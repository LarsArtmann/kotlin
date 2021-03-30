// MODULE: m1
// FILE: x.kt
package p

public interface Base<T> {
    public fun foo(t: T) {}
}

public interface A<T> : Base<T>

// MODULE: m2
// FILE: x.kt
package p

public interface Base<T> {
    public fun foo(t: T) {}
}

public interface B : Base<String>

// MODULE: m3(m1, m2)
// FILE: x.kt

import p.*

<!MANY_IMPL_MEMBER_NOT_IMPLEMENTED!>class Foo<!>: A<String>, B
