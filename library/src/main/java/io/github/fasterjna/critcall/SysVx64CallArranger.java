package io.github.fasterjna.critcall;

import java.nio.ByteBuffer;

public final class SysVx64CallArranger extends CallArranger {

    // x86_64 calling convention (System V):
    //     Java: rsi, rdx, rcx,  r8,  r9, rdi, stack
    //   Native: rdi, rsi, rdx, rcx,  r8,  r9, stack

    private static final int SAVE_LAST_ARG =
            0x4889f8;  // mov  rax, rdi

    private static final int[] MOVE_INT_ARG = new int[] {
            0x89f7,    // mov  edi, esi
            0x89d6,    // mov  esi, edx
            0x89ca,    // mov  edx, ecx
            0x4489c1,  // mov  ecx, r8d
            0x4589c8,  // mov  r8d, r9d
            0x4189c1,  // mov  r9d, eax
    };

    private static final int[] MOVE_LONG_ARG = new int[] {
            0x4889f7,  // mov  rdi, rsi
            0x4889d6,  // mov  rsi, rdx
            0x4889ca,  // mov  rdx, rcx
            0x4c89c1,  // mov  rcx, r8
            0x4d89c8,  // mov  r8,  r9
            0x4989c1,  // mov  r9,  rax
    };

    @Override
    public int capacity(Class<?>[] parameterTypes, long function) {
        int capacity = 0;

        if (parameterTypes.length >= 6) capacity += length(SAVE_LAST_ARG);

        int index = 0;
        for (Class<?> parameterType : parameterTypes) {
            if (index < 6) {
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
        if (parameterTypes.length >= 6) {
            // 6th Java argument clashes with the 1st native arg
            emit(buffer, SAVE_LAST_ARG);
        }

        int index = 0;
        for (Class<?> parameterType : parameterTypes) {
            if (index < 6) {
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
