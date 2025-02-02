package io.github.fasterjna.critcall;

import java.nio.ByteBuffer;

public abstract class CallArranger {

    public boolean isSupported(Class<?>[] parameterTypes, long function) {
        for (Class<?> parameterType : parameterTypes) {
            if (!parameterType.isPrimitive()) return false;
        }
        return true;
    }
    public abstract int capacity(Class<?>[] parameterTypes, long function);
    public abstract void arrange(ByteBuffer buffer, Class<?>[] parameterTypes);
    public abstract void dispatch(ByteBuffer buffer, long function);

    static void emit(ByteBuffer buf, int code) {
        byte ch;
        ch = (byte) (code >>> 24);
        if (ch != 0) buf.put(ch);
        ch = (byte) (code >>> 16);
        if (ch != 0) buf.put(ch);
        ch = (byte) (code >>> 8);
        if (ch != 0) buf.put(ch);
        ch = (byte) code;
        if (ch != 0) buf.put(ch);
    }

    static int length(int code) {
        int length = 0;
        if ((byte) (code >>> 24) != 0) length ++;
        if ((byte) (code >>> 16) != 0) length ++;
        if ((byte) (code >>> 8) != 0) length ++;
        if ((byte) code != 0) length ++;
        return length;
    }

}
