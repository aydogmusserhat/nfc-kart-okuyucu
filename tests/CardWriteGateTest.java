package com.serhat.nfcreader;

public final class CardWriteGateTest {
    static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) {
        CardWriteGate gate = new CardWriteGate();
        byte[] message = new byte[]{1, 2};
        gate.arm("ABC", CardWriteGate.Mode.WRITE, message, 1000);
        message[0] = 9;
        CardWriteGate.Request request = gate.consume();
        check(request.matches("ABC", 2000));
        check(!request.matches("OTHER", 2000));
        check(!request.matches("ABC", 31000));
        check(request.message()[0] == 1);
        check(gate.consume() == null);
        gate.arm("ABC", CardWriteGate.Mode.FORMAT, new byte[]{0}, 0);
        gate.cancel(); check(gate.consume() == null);
        System.out.println("Write confirmation tests passed: target card, expiry, single use, cancel, immutable content.");
    }
}
