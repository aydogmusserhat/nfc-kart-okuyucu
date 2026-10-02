package com.serhat.nfcreader;

public final class CardAnalysisTest {
    static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) {
        String iso = CardAnalysis.summarize(new String[]{"android.nfc.tech.IsoDep"}, false, null, false, false, 0, 0);
        check(iso.contains("ürün modeli doğrulanmadı"));
        check(iso.contains("Kart boş kabul edilmez"));
        check(iso.contains("Bakiye alanı: belirlenmedi"));
        String classic = CardAnalysis.summarize(new String[]{"android.nfc.tech.MifareClassic"}, false, null, false, true, 0, 3);
        check(classic.contains("MIFARE Classic"));
        check(classic.contains("3 veri bloğu okundu"));
        check(classic.contains("Blokların işlevi henüz bilinmiyor"));
        String ndef = CardAnalysis.summarize(new String[]{"android.nfc.tech.NfcA", "android.nfc.tech.Ndef"}, true, false, true, false, 2, 0);
        check(ndef.contains("kayıt sayısı: 2"));
        check(ndef.contains("salt okunur"));
        String failed = CardAnalysis.summarize(new String[]{"android.nfc.tech.Ndef"}, true, null, false, false, 0, 0);
        check(failed.contains("kayıtlar bu okumada alınamadı"));
        System.out.println("Card analysis tests passed: unknown model, unreadable NDEF, read-only card, raw data without balance inference.");
    }
}
