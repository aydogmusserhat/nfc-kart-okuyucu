package com.serhat.nfcreader;

/** A confirmation authorizes exactly one scan of one card for 30 seconds. */
public final class CardWriteGate {
    public enum Mode { WRITE, CLEAR, FORMAT }
    public static final class Request {
        public final String uid;
        public final Mode mode;
        private final byte[] message;
        private final long deadline;
        private Request(String uid, Mode mode, byte[] message, long now) {
            this.uid = uid; this.mode = mode; this.message = message.clone(); this.deadline = now + 30000;
        }
        public byte[] message() { return message.clone(); }
        public boolean matches(String uid, long now) { return this.uid.equals(uid) && now < deadline; }
    }
    private Request pending;
    public synchronized void arm(String uid, Mode mode, byte[] message, long now) {
        if (uid == null || uid.isEmpty()) throw new IllegalArgumentException("Kart kimliği gerekli.");
        pending = new Request(uid, mode, message, now);
    }
    public synchronized Request consume() { Request result = pending; pending = null; return result; }
    public synchronized void cancel() { pending = null; }
}
