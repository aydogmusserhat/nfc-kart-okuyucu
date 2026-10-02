package com.serhat.nfcreader;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class CodecTest {
    static void check(boolean condition) { if (!condition) throw new AssertionError(); }
    static void invalidKey(String key) {
        try { Codec.parseKey(key); throw new AssertionError("Invalid key accepted"); }
        catch (IllegalArgumentException expected) {}
    }
    public static void main(String[] args) {
        check(Codec.hex(new byte[]{0, (byte) 255, (byte) 128, 15}).equals("00FF800F"));
        check(Codec.hex(null).isEmpty());
        check(Codec.hex(Codec.parseHex("0080Ff")).equals("0080FF"));
        for (String invalid : new String[]{"0", "ZZ"}) {
            try { Codec.parseHex(invalid); throw new AssertionError("Invalid hex accepted"); }
            catch (IllegalArgumentException expected) {}
        }
        check(Codec.hex(Codec.parseKey("01 23 45 67 89 ab")).equals("0123456789AB"));
        invalidKey(""); invalidKey("123"); invalidKey("GG23456789AB");
        byte[] text = "İçerik: asfalt".getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[text.length + 3];
        payload[0] = 2; payload[1] = 't'; payload[2] = 'r';
        System.arraycopy(text, 0, payload, 3, text.length);
        check(Codec.decodeText(payload).equals("İçerik: asfalt"));
        byte[] utf16 = "Türkçe".getBytes(StandardCharsets.UTF_16);
        payload = new byte[utf16.length + 1]; payload[0] = (byte) 128;
        System.arraycopy(utf16, 0, payload, 1, utf16.length);
        check(Codec.decodeText(payload).equals("Türkçe"));
        for (byte[] invalid : Arrays.asList(new byte[0], new byte[]{3, 't'}, new byte[]{64})) {
            try { Codec.decodeText(invalid); throw new AssertionError("Invalid NDEF accepted"); }
            catch (IllegalArgumentException expected) {}
        }
        System.out.println("Codec tests passed: hex, keys, UTF-8/16, malformed payloads.");
    }
}
