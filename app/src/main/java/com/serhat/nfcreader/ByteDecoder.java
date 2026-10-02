package com.serhat.nfcreader;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Read-only interpretations of bytes, without assigning application semantics. */
public final class ByteDecoder {
    public static final int PREVIEW_BYTES = 64;
    public static final int MANUAL_MAX_BYTES = 256;
    private ByteDecoder() {}

    public static byte[] parseInput(String text) {
        if (text == null || text.length() > 4096)
            throw new IllegalArgumentException("En fazla 256 bayt HEX gir.");
        String hex = text.replaceAll("[\\s:-]", "");
        if (hex.isEmpty()) throw new IllegalArgumentException("Önce HEX baytlarını gir: ör. 48 65 6C 6C 6F.");
        if (hex.length() > MANUAL_MAX_BYTES * 2)
            throw new IllegalArgumentException("En fazla 256 bayt HEX gir.");
        return Codec.parseHex(hex);
    }

    public static final class NumberRow {
        public final int offset, width;
        public final String hex, unsignedLE, signedLE, unsignedBE, signedBE;
        NumberRow(byte[] bytes, int offset, int width) {
            this.offset = offset; this.width = width;
            byte[] group = Arrays.copyOfRange(bytes, offset, offset + width);
            hex = spacedHex(group);
            unsignedBE = new BigInteger(1, group).toString();
            signedBE = new BigInteger(group).toString();
            for (int i = 0; i < group.length / 2; i++) {
                byte temp = group[i]; group[i] = group[group.length - 1 - i];
                group[group.length - 1 - i] = temp;
            }
            unsignedLE = new BigInteger(1, group).toString();
            signedLE = new BigInteger(group).toString();
        }
    }

    public static final class Result {
        public final int byteCount, previewCount;
        public final boolean previewTruncated, utf8Valid, utf8TextTruncated;
        public final String hex, ascii, utf8;
        public final List<Integer> unsignedBytes = new ArrayList<>();
        public final List<Integer> signedBytes = new ArrayList<>();
        public final List<NumberRow> numbers = new ArrayList<>();

        Result(byte[] data) {
            byteCount = data.length;
            previewCount = Math.min(data.length, PREVIEW_BYTES);
            previewTruncated = data.length > previewCount;
            byte[] preview = Arrays.copyOf(data, previewCount);
            hex = spacedHex(preview);
            StringBuilder asciiText = new StringBuilder();
            for (byte b : preview) {
                int u = b & 255;
                unsignedBytes.add(u); signedBytes.add((int) b);
                if (u >= 32 && u <= 126) {
                    if (u == '\\') asciiText.append("\\\\"); else asciiText.append((char) u);
                } else asciiText.append(String.format(Locale.ROOT, "\\x%02X", u));
            }
            ascii = asciiText.toString();
            String decoded = null;
            try {
                decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data)).toString();
            } catch (CharacterCodingException ignored) {}
            utf8Valid = decoded != null;
            utf8TextTruncated = decoded != null && decoded.codePointCount(0, decoded.length()) > PREVIEW_BYTES;
            utf8 = decoded == null ? "Geçerli UTF-8 değil." : escapedText(decoded, PREVIEW_BYTES);
            for (int width : new int[]{2, 4, 8})
                for (int offset = 0; offset + width <= preview.length; offset += width)
                    numbers.add(new NumberRow(preview, offset, width));
        }

        public String display() {
            StringBuilder out = new StringBuilder();
            out.append("Bayt: ").append(byteCount).append("\nHEX: ").append(hex.isEmpty() ? "(boş)" : hex)
                .append("\nASCII: ").append(ascii.isEmpty() ? "(boş)" : ascii)
                .append("\nUTF-8: ").append(utf8.isEmpty() ? "(boş)" : utf8);
            if (utf8TextTruncated) out.append(" … (ilk 64 karakter)");
            out.append("\n8 bit işaretsiz: ").append(unsignedBytes)
                .append("\n8 bit işaretli: ").append(signedBytes)
                .append("\nSayı grupları 0. bayttan başlar; @ değeri bayt konumudur.\nLE: düşük bayt önce; BE: yüksek bayt önce.\nSıra: işaretsiz / işaretli\n");
            for (NumberRow row : numbers)
                out.append(row.width * 8).append(" bit @").append(row.offset).append(" [").append(row.hex)
                    .append("]\n LE: ").append(row.unsignedLE).append(" / ").append(row.signedLE)
                    .append("\n BE: ").append(row.unsignedBE).append(" / ").append(row.signedBE).append('\n');
            if (previewTruncated) out.append("HEX/ASCII/sayı görünümü ilk 64 baytla sınırlı. Tam ham veri kart raporunda korunur.\n");
            out.append("Bu dönüşümler alanın bakiye, tarih veya sayaç olduğunu doğrulamaz.");
            return out.toString();
        }
    }

    public static Result decode(byte[] data) {
        if (data == null) throw new IllegalArgumentException("Bayt verisi yok.");
        return new Result(data);
    }

    private static String spacedHex(byte[] data) {
        String hex = Codec.hex(data);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < hex.length(); i += 2) {
            if (i > 0) out.append(' ');
            out.append(hex, i, i + 2);
        }
        return out.toString();
    }

    private static String escapedText(String text, int limit) {
        StringBuilder out = new StringBuilder();
        int count = 0;
        for (int i = 0; i < text.length() && count < limit; count++) {
            int cp = text.codePointAt(i); i += Character.charCount(cp);
            if (cp == '\\') out.append("\\\\");
            else if (Character.isISOControl(cp)) out.append(String.format(Locale.ROOT, "\\u%04X", cp));
            else out.appendCodePoint(cp);
        }
        return out.toString();
    }
}
