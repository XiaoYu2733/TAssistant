package de.robv.android.xposed;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

public final class XposedHelpers {
    private XposedHelpers() {
    }

    public static Object callMethod(Object obj, String methodName, Object... args) throws Throwable {
        if (obj == null) {
            throw new IllegalArgumentException("obj must not be null");
        }
        Method method = findMethodBestMatch(obj.getClass(), methodName, args);
        method.setAccessible(true);
        try {
            return method.invoke(obj, args);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }

    public static Object getObjectField(Object obj, String fieldName) throws Throwable {
        if (obj == null) {
            throw new IllegalArgumentException("obj must not be null");
        }
        Field field = findField(obj.getClass(), fieldName);
        field.setAccessible(true);
        return field.get(obj);
    }

    private static Method findMethodBestMatch(Class<?> cls, String methodName, Object[] args) {
        Method best = null;
        int bestScore = Integer.MAX_VALUE;
        for (Method method : allMethods(cls)) {
            if (!method.getName().equals(methodName)) continue;
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length != args.length) continue;
            int score = matchScore(parameterTypes, args);
            if (score < bestScore) {
                best = method;
                bestScore = score;
            }
        }
        if (best == null) {
            throw new NoSuchMethodError(cls.getName() + "#" + methodName);
        }
        return best;
    }

    private static Field findField(Class<?> cls, String fieldName) {
        Class<?> current = cls;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldError(Objects.requireNonNull(cls).getName() + "#" + fieldName);
    }

    private static ArrayList<Method> allMethods(Class<?> cls) {
        ArrayList<Method> methods = new ArrayList<>();
        Class<?> current = cls;
        while (current != null) {
            methods.addAll(Arrays.asList(current.getDeclaredMethods()));
            current = current.getSuperclass();
        }
        return methods;
    }

    private static int matchScore(Class<?>[] parameterTypes, Object[] args) {
        int score = 0;
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> type = box(parameterTypes[i]);
            Object arg = args[i];
            if (arg == null) {
                if (parameterTypes[i].isPrimitive()) return Integer.MAX_VALUE;
                score += 2;
            } else if (type == arg.getClass()) {
                score += 0;
            } else if (type.isAssignableFrom(arg.getClass())) {
                score += 1;
            } else {
                return Integer.MAX_VALUE;
            }
        }
        return score;
    }

    private static Class<?> box(Class<?> cls) {
        if (!cls.isPrimitive()) return cls;
        if (cls == Boolean.TYPE) return Boolean.class;
        if (cls == Byte.TYPE) return Byte.class;
        if (cls == Character.TYPE) return Character.class;
        if (cls == Short.TYPE) return Short.class;
        if (cls == Integer.TYPE) return Integer.class;
        if (cls == Long.TYPE) return Long.class;
        if (cls == Float.TYPE) return Float.class;
        if (cls == Double.TYPE) return Double.class;
        if (cls == Void.TYPE) return Void.class;
        return cls;
    }
}
