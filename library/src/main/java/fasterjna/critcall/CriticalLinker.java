package fasterjna.critcall;

import io.github.fasterjna.critcall.AArch64CallArranger;
import io.github.fasterjna.critcall.CallArranger;
import io.github.fasterjna.critcall.RISCV64CallArranger;
import io.github.fasterjna.critcall.SysVx64CallArranger;
import io.github.fasterjna.critcall.Windowsx64CallArranger;
import sun.misc.Unsafe;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Locale;

/**
 * Links native methods to the provided machine code using JVMCI.
 */
public final class CriticalLinker {

    private CriticalLinker() {
        throw new AssertionError("No io.github.fasterjna.critcall.CriticalLinker instances for you!");
    }

    private static final Unsafe UNSAFE;
    private static final MethodHandles.Lookup IMPL_LOOKUP;
    static {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            UNSAFE = (Unsafe) field.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to get the sun.misc.Unsafe instance");
        }
        try {
            Class.forName("java.lang.invoke.MethodHandles$Lookup");
            Field field = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
            IMPL_LOOKUP = (MethodHandles.Lookup) UNSAFE.getObject(MethodHandles.Lookup.class, UNSAFE.staticFieldOffset(field));
        } catch (Throwable e) {
            throw new IllegalStateException("Failed to get the trusted java.lang.invoke.MethodHandles.Lookup instance");
        }
    }

    private static final int INVOCATION_ENTRY_BCI = -1; // copied from jdk.vm.ci.runtime.JVMCICompiler
    private static final Object JVMCI_BACKEND;
    private static final Object META_ACCESS;
    private static final Object CODE_CACHE;
    private static final Object BYTE_EMPTY_ARRAY = new byte[0];
    private static final Object SITE_EMPTY_ARRAY;
    private static final Object ASSUMPTION_EMPTY_ARRAY;
    private static final Object RESOLVED_JAVA_METHOD_EMPTY_ARRAY;
    private static final Object COMMENT_EMPTY_ARRAY;
    private static final Object DATA_PATCH_EMPTY_ARRAY;
    private static final MethodHandle lookupJavaMethodMethodHandle;
    private static final MethodHandle setDefaultCodeMethodHandle;
    private static final MethodHandle hotSpotCompiledNmethodConstructorMethodHandle;
    static {
        Object _jvmciBackend;
        Object _metaAccess;
        Object _codeCache;
        Object _siteEmptyArray;
        Object _assumptionEmptyArray;
        Object _resolvedJavaMethodEmptyArray;
        Object _commentEmptyArray;
        Object _dataPatchEmptyArray;
        MethodHandle _lookupJavaMethodMethodHandle;
        MethodHandle _setDefaultCodeMethodHandle;
        MethodHandle _hotSpotCompiledNmethodConstructorMethodHandle;
        try {
            System.loadLibrary("java");
            Class<?> jvmciClass = Class.forName("jdk.vm.ci.runtime.JVMCI");
            Class<?> jvmciRuntimeClass = Class.forName("jdk.vm.ci.runtime.JVMCIRuntime");
            Class<?> jvmciBackendClass = Class.forName("jdk.vm.ci.runtime.JVMCIBackend");
            Class<?> metaAccessClass = Class.forName("jdk.vm.ci.meta.MetaAccessProvider");
            Class<?> codeCacheClass = Class.forName("jdk.vm.ci.code.CodeCacheProvider");
            Class<?> resolvedJavaMethodClass = Class.forName("jdk.vm.ci.meta.ResolvedJavaMethod");
            Class<?> installedCodeClass = Class.forName("jdk.vm.ci.code.InstalledCode");
            Class<?> compiledCodeClass = Class.forName("jdk.vm.ci.code.CompiledCode");
            Class<?> compiledNMethodClass = Class.forName("jdk.vm.ci.hotspot.HotSpotCompiledNmethod");
            Class<?> siteClass = Class.forName("jdk.vm.ci.code.site.Site");
            Class<?> assumptionClass = Class.forName("jdk.vm.ci.meta.Assumptions$Assumption");
            Class<?> commentClass = Class.forName("jdk.vm.ci.hotspot.HotSpotCompiledCode$Comment");
            Class<?> dataPatchClass = Class.forName("jdk.vm.ci.code.site.DataPatch");
            Class<?> stackSlotClass = Class.forName("jdk.vm.ci.code.StackSlot");
            Class<?> hotSpotResolvedJavaMethodClass = Class.forName("jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod");
            _siteEmptyArray = Array.newInstance(siteClass, 0);
            _assumptionEmptyArray = Array.newInstance(assumptionClass, 0);
            _resolvedJavaMethodEmptyArray = Array.newInstance(resolvedJavaMethodClass, 0);
            _commentEmptyArray = Array.newInstance(commentClass, 0);
            _dataPatchEmptyArray = Array.newInstance(dataPatchClass, 0);
            Object jvmciRuntime = IMPL_LOOKUP.findStatic(jvmciClass, "getRuntime", MethodType.methodType(jvmciRuntimeClass)).invoke();
            _jvmciBackend = IMPL_LOOKUP.findVirtual(jvmciRuntimeClass, "getHostJVMCIBackend", MethodType.methodType(jvmciBackendClass)).bindTo(jvmciRuntime).invoke();
            _metaAccess = IMPL_LOOKUP.findVirtual(jvmciBackendClass, "getMetaAccess", MethodType.methodType(metaAccessClass)).bindTo(_jvmciBackend).invoke();
            _codeCache = IMPL_LOOKUP.findVirtual(jvmciBackendClass, "getCodeCache", MethodType.methodType(codeCacheClass)).bindTo(_jvmciBackend).invoke();
            _lookupJavaMethodMethodHandle = IMPL_LOOKUP.findVirtual(metaAccessClass, "lookupJavaMethod", MethodType.methodType(resolvedJavaMethodClass, Executable.class));
            _setDefaultCodeMethodHandle = IMPL_LOOKUP.findVirtual(codeCacheClass, "setDefaultCode", MethodType.methodType(installedCodeClass, resolvedJavaMethodClass, compiledCodeClass));
            _hotSpotCompiledNmethodConstructorMethodHandle = IMPL_LOOKUP.findConstructor(compiledNMethodClass, MethodType.methodType(void.class, String.class, byte[].class, int.class,
                    _siteEmptyArray.getClass(), _assumptionEmptyArray.getClass(), _resolvedJavaMethodEmptyArray.getClass(), _commentEmptyArray.getClass(),
                    byte[].class, int.class,
                    _dataPatchEmptyArray.getClass(),
                    boolean.class, int.class,
                    stackSlotClass, hotSpotResolvedJavaMethodClass,
                    int.class, int.class, long.class, boolean.class));
        } catch (Throwable e) {
            _jvmciBackend = null;
            _metaAccess = null;
            _codeCache = null;
            _siteEmptyArray = null;
            _assumptionEmptyArray = null;
            _resolvedJavaMethodEmptyArray = null;
            _commentEmptyArray = null;
            _dataPatchEmptyArray = null;
            _lookupJavaMethodMethodHandle = null;
            _setDefaultCodeMethodHandle = null;
            _hotSpotCompiledNmethodConstructorMethodHandle = null;
        }
        JVMCI_BACKEND = _jvmciBackend;
        META_ACCESS = _metaAccess;
        CODE_CACHE = _codeCache;
        SITE_EMPTY_ARRAY = _siteEmptyArray;
        ASSUMPTION_EMPTY_ARRAY = _assumptionEmptyArray;
        RESOLVED_JAVA_METHOD_EMPTY_ARRAY = _resolvedJavaMethodEmptyArray;
        COMMENT_EMPTY_ARRAY = _commentEmptyArray;
        DATA_PATCH_EMPTY_ARRAY = _dataPatchEmptyArray;
        lookupJavaMethodMethodHandle = _lookupJavaMethodMethodHandle;
        setDefaultCodeMethodHandle = _setDefaultCodeMethodHandle;
        hotSpotCompiledNmethodConstructorMethodHandle = _hotSpotCompiledNmethodConstructorMethodHandle;
    }

    private static final CallArranger CALL_ARRANGER;
    static {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ENGLISH);
        if ("x86_64".equals(arch) || "amd64".equals(arch)) CALL_ARRANGER
                = System.getProperty("os.name", "").startsWith("Windows") ? new Windowsx64CallArranger() : new SysVx64CallArranger();
        else if (arch.equals("aarch64")) CALL_ARRANGER = new AArch64CallArranger();
        else if (arch.contains("riscv64")) CALL_ARRANGER = new RISCV64CallArranger();
        else CALL_ARRANGER = null;
    }

    public static boolean isSupported() {
        return JVMCI_BACKEND != null;
    }

    public static void link(Method method, long function, CallingConvention convention) throws UnsatisfiedLinkError {
        if (CALL_ARRANGER == null) throw new UnsatisfiedLinkError("Unsupported platform");
        int modifiers = method.getModifiers();
        if (!Modifier.isStatic(modifiers) || !Modifier.isNative(modifiers)) throw new UnsatisfiedLinkError("Unsupported method");
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (!CALL_ARRANGER.isSupported(parameterTypes, function)) throw new UnsatisfiedLinkError("Unsupported method");
        ByteBuffer buffer = ByteBuffer.allocate(CALL_ARRANGER.capacity(parameterTypes, function)).order(ByteOrder.nativeOrder());
        if (convention == null || convention == CallingConvention.CDECL) CALL_ARRANGER.arrange(buffer, parameterTypes);
        CALL_ARRANGER.dispatch(buffer, function);
        link(method, buffer.array(), buffer.position());
    }

    public static void link(Method method, long function) throws UnsatisfiedLinkError {
        link(method, function, null);
    }

    public static void link(Method method, byte[] code, int offset, int length) throws UnsatisfiedLinkError {
        if (offset == 0) link(method, code, length);
        else link(method, Arrays.copyOfRange(code, offset, length));
    }

    public static void link(Method method, byte[] code) throws UnsatisfiedLinkError {
        link(method, code, code.length);
    }

    public static void link(Method method, byte[] code, int length) throws UnsatisfiedLinkError {
        if (JVMCI_BACKEND == null) throw new UnsatisfiedLinkError("JVMCI module not found. Use -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI");
        try {
            Object resolvedJavaMethod = lookupJavaMethodMethodHandle.bindTo(META_ACCESS).invoke(method);
            Object compiledNMethod = hotSpotCompiledNmethodConstructorMethodHandle.invoke(method.getName(), code, length,
                    SITE_EMPTY_ARRAY, ASSUMPTION_EMPTY_ARRAY, RESOLVED_JAVA_METHOD_EMPTY_ARRAY, COMMENT_EMPTY_ARRAY,
                    BYTE_EMPTY_ARRAY, 1,
                    DATA_PATCH_EMPTY_ARRAY,
                    true, 0, null,
                    resolvedJavaMethod, INVOCATION_ENTRY_BCI,
                    1, 0L, false);
            setDefaultCodeMethodHandle.bindTo(CODE_CACHE).invoke(resolvedJavaMethod, compiledNMethod);
        }
        catch (Throwable e) {
            throw new UnsatisfiedLinkError("Failed to link method");
        }
    }

}
