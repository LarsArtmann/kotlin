//FILE: GetterTest.java

import lombok.AccessLevel;
import lombok.Getter;

public class GetterTest {
    @Getter private int age = 10;

    @Getter(AccessLevel.PROTECTED) private String name;

    @Getter private boolean primitiveBoolean;
    
//    public boolean getPrimitiveBoolean() {
//        return true;
//    }

    @Getter private Boolean boxedBoolean;

//    void test() {
//        getAge();
//    }

}


//FILE: test.kt

object Test {
    fun usage() {
        val obj = GetterTest()
        val getter = obj.getAge()
        val property = obj.age

//        obj.primitiveBoolean
//        obj.isPrimitiveBoolean()

        obj.boxedBoolean
        obj.getBoxedBoolean()
    }
}
