package com.serhat.nfcreader;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Platform-independent conversion; no card commands. */
public final class Codec {
    private Codec() {}

    public static String hex(byte[] data) {
        if (data == null) return "";
        StringBuilder result = new StringBuilder(data.length * 2);
        final char[] alphabet = "0123456789ABCDEF".toCharArray();
        for (byte b : data) {
            int v = b & 255;
            result.append(alphabet[v >>> 4]).append(alphabet[v & 15]);
        }
        return result.toString();
    }

    public static byte[] parseKey(String input) {
        String value = input.replaceAll("\\s", "");
        if (!value.matches("[0-9a-fA-F]{12}"))
            throw new IllegalArgumentException("Anahtar 12 hexadecimal karakter olmalı (6 bayt).");
        byte[] key = new byte[6];
        for (int i = 0; i < 6; i++) key[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        return key;
    }

    public static String decodeText(byte[] payload) {
        if (payload.length == 0) throw new IllegalArgumentException("Boş NDEF metin kaydı.");
        int status = payload[0] & 255;
        int languageLength = status & 63;
        if ((status & 64) != 0 || 1 + languageLength > payload.length)
            throw new IllegalArgumentException("Geçersiz NDEF metin başlığı.");
        Charset charset = (status & 128) == 0 ? StandardCharsets.UTF_8 : StandardCharsets.UTF_16;
        return new String(Arrays.copyOfRange(payload, 1 + languageLength, payload.length), charset);
    }
}
