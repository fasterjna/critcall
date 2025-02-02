package io.github.fasterjna.critcall;

import java.nio.ByteBuffer;

public final class RISCV64CallArranger extends CallArranger {

    // RISCV64 calling convention:
    //     Java: x10, x11, x12, x13, x14, x15, x16, x17, stack
    //   Native: x10, x11, x12, x13, x14, x15, x16, x17, stack

    @Override
    public int capacity(Class<?>[] parameterTypes, long function) {
        return 24;
    }

    @Override
    public void arrange(ByteBuffer buffer, Class<?>[] parameterTypes) {
        // nothing to be done, the Java and Native calling convention are the same
    }

    @Override
    public void dispatch(ByteBuffer buffer, long function) {
        long imm = function >> 17;
        long upper = imm, lower = imm;
        lower = (lower << 52) >> 52;
        upper -= lower;

        int a0 = (int) (upper);
        int a1 = (int) (lower);
        int a2 = (int) ((function >> 6) & 0x7ff);
        int a3 = (int) ((function) & 0x3f);

        int zr = 0; // x0
        int t0 = 5; // x5

        buffer.putInt(0b0110111 | (t0 << 7) | (a0 << 12));                              // lui  t0, a0
        buffer.putInt(0b0010011 | (t0 << 7) | (0b000 << 12) | (t0 << 15) | (a1 << 20)); // addi t0, t0, a1
        buffer.putInt(0b0010011 | (t0 << 7) | (0b001 << 12) | (t0 << 15) | (11 << 20)); // slli t0, t0, 11
        buffer.putInt(0b0010011 | (t0 << 7) | (0b000 << 12) | (t0 << 15) | (a2 << 20)); // addi t0, t0, a2
        buffer.putInt(0b0010011 | (t0 << 7) | (0b001 << 12) | (t0 << 15) | ( 6 << 20)); // slli t0, t0, 6
        buffer.putInt(0b1100111 | (zr << 7) | (0b000 << 12) | (t0 << 15) | (a3 << 20)); // jalr a3(t0)
    }

}
