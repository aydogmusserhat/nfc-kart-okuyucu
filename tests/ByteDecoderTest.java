package com.serhat.nfcreader;

import java.nio.charset.StandardCharsets;

public final class ByteDecoderTest {
    static void check(boolean condition) { if (!condition) throw new AssertionError(); }
    static void badInput(String input) {
        try { ByteDecoder.parseInput(input); throw new AssertionError("Invalid input accepted"); }
        catch (IllegalArgumentException expected) {}
    }
    static ByteDecoder.NumberRow row(String hex, int width) {
        for (ByteDecoder.NumberRow row : ByteDecoder.decode(Codec.parseHex(hex)).numbers)
            if (row.width == width && row.offset == 0) return row;
        throw new AssertionError("Missing row");
    }
    public static void main(String[] args) {
        ByteDecoder.Result hello = ByteDecoder.decode(ByteDecoder.parseInput("48 65:6C-6C\n6F"));
        check(hello.ascii.equals("Hello") && hello.utf8.equals("Hello") && hello.utf8Valid);
        check(hello.byteCount == 5 && hello.previewCount == 5);
        check(hello.numbers.size() == 3); // two 16-bit groups, one 32-bit; no padding
        ByteDecoder.NumberRow little = row("6400", 2);
        check(little.unsignedLE.equals("100") && little.unsignedBE.equals("25600"));
        ByteDecoder.NumberRow negative = row("0080", 2);
        check(negative.unsignedLE.equals("32768") && negative.signedLE.equals("-32768"));
        check(negative.unsignedBE.equals("128") && negative.signedBE.equals("128"));
        ByteDecoder.NumberRow all32 = row("FFFFFFFF", 4);
        check(all32.unsignedBE.equals("4294967295") && all32.signedBE.equals("-1"));
        ByteDecoder.NumberRow all64 = row("FFFFFFFFFFFFFFFF", 8);
        check(all64.unsignedLE.equals("18446744073709551615") && all64.signedBE.equals("-1"));
        ByteDecoder.NumberRow minimum64 = row("8000000000000000", 8);
        check(minimum64.signedBE.equals("-9223372036854775808"));
        check(minimum64.unsignedBE.equals("9223372036854775808"));
        check(minimum64.signedLE.equals("128"));
        ByteDecoder.Result binary = ByteDecoder.decode(Codec.parseHex("00FF805C0A"));
        check(binary.unsignedBytes.get(1) == 255 && binary.signedBytes.get(1) == -1);
        check(binary.signedBytes.get(2) == -128 && !binary.utf8Valid);
        check(binary.ascii.equals("\\x00\\xFF\\x80\\\\\\x0A"));
        ByteDecoder.Result controls = ByteDecoder.decode(Codec.parseHex("00415C0A"));
        check(controls.utf8.equals("\\u0000A\\\\\\u000A"));
        ByteDecoder.Result turkish = ByteDecoder.decode("İçerik: Türkçe 😀".getBytes(StandardCharsets.UTF_8));
        check(turkish.utf8.equals("İçerik: Türkçe 😀"));
        for (String bad : new String[]{"C3", "C080", "EDA080", "F4908080"})
            check(!ByteDecoder.decode(Codec.parseHex(bad)).utf8Valid);
        ByteDecoder.Result zeros = ByteDecoder.decode(new byte[16]);
        check(zeros.ascii.length() == 16 * 4 && zeros.unsignedBytes.size() == 16);
        check(zeros.numbers.size() == 14 && !zeros.previewTruncated);
        ByteDecoder.Result capped = ByteDecoder.decode(new byte[65]);
        check(capped.byteCount == 65 && capped.previewCount == 64 && capped.previewTruncated);
        check(capped.unsignedBytes.size() == 64 && capped.numbers.size() == 56);
        String unicode = "A".repeat(63) + "İ";
        ByteDecoder.Result boundary = ByteDecoder.decode(unicode.getBytes(StandardCharsets.UTF_8));
        check(boundary.previewTruncated && boundary.utf8Valid && boundary.utf8.equals(unicode));
        check(!boundary.utf8TextTruncated);
        ByteDecoder.Result emoji = ByteDecoder.decode("😀".repeat(65).getBytes(StandardCharsets.UTF_8));
        check(emoji.utf8.equals("😀".repeat(64)) && emoji.utf8TextTruncated);
        check(ByteDecoder.decode(new byte[0]).numbers.isEmpty());
        check(ByteDecoder.decode(new byte[1]).numbers.isEmpty());
        for (String bad : new String[]{null, "", "--", "0", "GG", "XX", "0x64", "00".repeat(257)}) badInput(bad);
        check(ByteDecoder.parseInput("FF".repeat(256)).length == 256);
        System.out.println("Byte decoder tests passed: text, UTF-8 errors, endian, signed/unsigned 8/16/32/64, limits.");
    }
}
