package de.robv.android.xposed;

import java.lang.reflect.Member;
import java.util.HashMap;

import io.github.libxposed.api.XposedInterface;

public class XC_MethodHook {
    final int priority;

    public XC_MethodHook() {
        this(XposedInterface.PRIORITY_DEFAULT);
    }

    public XC_MethodHook(int priority) {
        this.priority = priority;
    }

    public static class MethodHookParam {
        public Member method;
        public Object thisObject;
        public Object[] args;

        private Object result;
        private Throwable throwable;
        private boolean returnEarly;
        private final HashMap<String, Object> extras = new HashMap<>();

        public Object getResult() {
            return result;
        }

        public void setResult(Object result) {
            this.result = result;
            this.throwable = null;
            this.returnEarly = true;
        }

        public Throwable getThrowable() {
            return throwable;
        }

        public void setThrowable(Throwable throwable) {
            this.throwable = throwable;
            this.returnEarly = true;
        }

        public void setObjectExtra(String key, Object value) {
            extras.put(key, value);
        }

        public Object getObjectExtra(String key) {
            return extras.get(key);
        }

        boolean isReturnEarly() {
            return returnEarly;
        }

        void setResultFromOriginal(Object result) {
            this.result = result;
            this.throwable = null;
            this.returnEarly = false;
        }

        void setThrowableFromOriginal(Throwable throwable) {
            this.throwable = throwable;
            this.returnEarly = false;
        }
    }

    public static class Unhook {
        private final Member hookedMethod;
        private final XposedInterface.HookHandle handle;

        Unhook(Member hookedMethod, XposedInterface.HookHandle handle) {
            this.hookedMethod = hookedMethod;
            this.handle = handle;
        }

        public Member getHookedMethod() {
            return hookedMethod;
        }

        public void unhook() {
            handle.unhook();
        }
    }

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
    }

    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    }

    void callBeforeHookedMethod(MethodHookParam param) throws Throwable {
        beforeHookedMethod(param);
    }

    void callAfterHookedMethod(MethodHookParam param) throws Throwable {
        afterHookedMethod(param);
    }
}
