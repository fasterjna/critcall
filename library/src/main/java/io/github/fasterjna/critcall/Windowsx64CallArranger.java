package io.github.fasterjna.critcall;

import java.nio.ByteBuffer;

public final class Windowsx64CallArranger extends CallArranger {

    // x86_64 calling convention (Windows):
    //     Java: rdx, r8,  r9, rdi, rsi, rcx, stack
    //   Native: rcx, rdx, r8, r9,  stack

    private static final int[] MOVE_INT_ARG = new int[] {
            0x89d1,    // mov  ecx, edx
            0x4489c2,  // mov  edx, r8d
            0x4589c8,  // mov  r8d, r9d
            0x4189f9,  // mov  r9d, edi
    };

    private static final int[] MOVE_LONG_ARG = new int[] {
            0x4889d1,  // mov  rcx, rdx
            0x4c89c2,  // mov  rdx, r8
            0x4d89c8,  // mov  r8,  r9
            0x4989f9,  // mov  r9,  rdi
    };

    @Override
    public boolean isSupported(Class<?>[] parameterTypes, long function) {
        int rx = 0;
        int xmm = 0;
        for (Class<?> parameterType : parameterTypes) {
            if (!parameterType.isPrimitive()) return false;
            if (parameterType == float.class || parameterType == double.class) {
                if (xmm ++ >= 4) return false;
            }
            else if (rx ++ >= 4) return false;
        }
        return true;
    }

    @Override
    public int capacity(Class<?>[] parameterTypes, long function) {
        int capacity = 0;

        int index = 0;
        for (Class<?> parameterType : parameterTypes) {
            if (index < 4) {
                if (parameterType == float.class || parameterType == double.class) continue;
                capacity += length((parameterType == long.class ? MOVE_LONG_ARG : MOVE_INT_ARG) [index ++]);
            }
            else break;
        }

        capacity += 10;
        capacity += 2;
        return capacity;
    }

    @Override
    public void arrange(ByteBuffer buffer, Class<?>[] parameterTypes) {
        int index = 0;
        for (Class<?> parameterType : parameterTypes) {
            if (index < 4) {
                if (parameterType == float.class || parameterType == double.class) continue;
                emit(buffer, (parameterType == long.class ? MOVE_LONG_ARG : MOVE_INT_ARG) [index ++]);
            }
            else break;
        }
    }

    @Override
    public void dispatch(ByteBuffer buffer, long function) {
        buffer.putShort((short) 0xb848).putLong(function);  // mov rax, address
        buffer.putShort((short) 0xe0ff);                    // jmp rax
    }

}
