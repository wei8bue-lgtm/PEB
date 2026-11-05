import java.lang.reflect.InvocationTargetException;

public class PlanningTestRunner {
    public static void main(String[] arguments) throws Exception {
        int count = 0;
        int skipped = 0;
        for (String name : arguments) {
            Class<?> type = Class.forName(name);
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object instance = constructor.newInstance();
            for (var method : type.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(org.junit.jupiter.api.Test.class)) continue;
                method.setAccessible(true);
                try { method.invoke(instance); }
                catch (InvocationTargetException exception) {
                    if (exception.getCause() instanceof org.opentest4j.TestAbortedException) {
                        System.out.println("SKIP " + name + "." + method.getName()); skipped++; continue;
                    }
                    throw new AssertionError(name + "." + method.getName(), exception.getCause());
                }
                System.out.println("PASS " + name + "." + method.getName());
                count++;
            }
        }
        System.out.println("Passed " + count + " focused tests; skipped " + skipped + ".");
    }
}