import java.lang.reflect.*;
public class TestRegistrationParse {
    public static void main(String[] args) throws Exception {
        Object handler = Class.forName("RegistrationHandler").getDeclaredConstructor().newInstance();
        Method m = Class.forName("RegistrationHandler").getDeclaredMethod("parseLong", Object.class);
        m.setAccessible(true);
        System.out.println(m.invoke(handler, new Object[] { null }));
        System.out.println(m.invoke(handler, new Object[] { "" }));
        System.out.println(m.invoke(handler, new Object[] { "abc" }));
        System.out.println(m.invoke(handler, new Object[] { 42L }));
    }
}
