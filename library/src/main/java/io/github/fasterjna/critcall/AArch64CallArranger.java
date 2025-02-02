package io.github.fasterjna.critcall;

import java.nio.ByteBuffer;

public final class AArch64CallArranger extends CallArranger {

    // AArch64 calling convention:
    //     Java: x1, x2, x3, x4, x5, x6, x7, x0, stack
    //   Native: x0, x1, x2, x3, x4, x5, x6, x7, stack

    @Override
    public int capacity(Class<?>[] parameterTypes, long function) {
        int count = 0;

        if (parameterTypes.length >= 8) count ++;
        for (Class<?> parameterType : parameterTypes) {
            if (count < 8) {
                if (parameterType == float.class || parameterType == double.class) continue;
                count ++;
            }
            else break;
        }

        count ++;
        if (((function >>> 16) & 0xffff) != 0) count ++;
        if (((function >>> 32) & 0xffff) != 0) count ++;
        if (((function >>> 48)) != 0) count ++;

        count ++;
        return count * 4;
    }

    @Override
    public void arrange(ByteBuffer buffer, Class<?>[] parameterTypes) {
        if (parameterTypes.length >= 8) {
            // 8th Java argument clashes with the 1st native arg
            buffer.putInt(0xaa0003e8);  // mov x8, x0
        }

        int index = 0;
        for (Class<?> parameterType : parameterTypes) {
            if (index < 8) {
                if (parameterType == float.class || parameterType == double.class) continue;
                // mov x0, x1
                buffer.putInt((parameterType == long.class ? 0xaa0003e0 : 0x2a0003e0) | index | (index + 1) << 16);
                index ++;
            }
            else break;
        }
    }

    @Override
    public void dispatch(ByteBuffer buffer, long function) {
        int a0 = (int) function & 0xffff;
        int a1 = (int) (function >>> 16) & 0xffff;
        int a2 = (int) (function >>> 32) & 0xffff;
        int a3 = (int) (function >>> 48);

        buffer.putInt(0xd2800009 | a0 << 5);               // movz x9, #0xffff
        if (a1 != 0) buffer.putInt(0xf2a00009 | a1 << 5);  // movk x9, #0xffff, lsl #16
        if (a2 != 0) buffer.putInt(0xf2c00009 | a2 << 5);  // movk x9, #0xffff, lsl #32
        if (a3 != 0) buffer.putInt(0xf2e00009 | a3 << 5);  // movk x9, #0xffff, lsl #48

        buffer.putInt(0xd61f0120);                         // br x9
    }

}
