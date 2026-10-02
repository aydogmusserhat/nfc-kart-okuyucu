package com.serhat.nfcreader;

import java.util.Arrays;
import java.util.List;

/** Describes observed capabilities without guessing a vendor's wallet schema. */
public final class CardAnalysis {
    private CardAnalysis() {}
    public static String summarize(String[] technologies, boolean hasNdef, Boolean writable,
            boolean ndefRead, boolean canFormat, int records, int classicBlocks) {
        List<String> tech = Arrays.asList(technologies);
        String family;
        if (tech.contains("android.nfc.tech.MifareClassic")) family = "MIFARE Classic";
        else if (tech.contains("android.nfc.tech.IsoDep")) family = "ISO-DEP (ISO 14443-4); ürün modeli doğrulanmadı";
        else if (tech.contains("android.nfc.tech.NfcV")) family = "NFC-V (ISO 15693)";
        else if (tech.contains("android.nfc.tech.NfcF")) family = "NFC-F";
        else if (tech.contains("android.nfc.tech.NfcA")) family = "NFC-A; ürün modeli doğrulanmadı";
        else if (tech.contains("android.nfc.tech.NfcB")) family = "NFC-B";
        else family = "Telefonun raporladığı teknolojiler üzerinden belirlenemedi";
        StringBuilder text = new StringBuilder("Kart ailesi: ").append(family).append("\n");
        if (!hasNdef) text.append("Veri düzeni: standart NDEF kaydı algılanmadı. Kart boş kabul edilmez.\n");
        else if (ndefRead) text.append("Veri düzeni: standart NDEF mesajı okunabildi; kayıt sayısı: ").append(records).append(".\n");
        else text.append("Veri düzeni: NDEF desteği algılandı, kayıtlar bu okumada alınamadı.\n");
        if (hasNdef && Boolean.TRUE.equals(writable)) text.append("NDEF yazma: kart yazılabilir bildirildi; kapasite ve erişim işlem sırasında kontrol edilir.\n");
        else if (hasNdef && Boolean.FALSE.equals(writable)) text.append("NDEF yazma: kart salt okunur bildirildi.\n");
        else text.append("NDEF yazma: bu okumada doğrulanamadı.\n");
        text.append("NDEF formatlama: ").append(!hasNdef && canFormat ? "telefon desteği algıladı; onay gerekir." : "uygulanabilir destek algılanmadı.").append("\n");
        if (classicBlocks > 0) text.append("Ham bellek: seçilen Classic sektörde ").append(classicBlocks).append(" veri bloğu okundu. Blokların işlevi henüz bilinmiyor.\n");
        text.append("Dolum/bakiye protokolü: bu sürümde doğrulanmış bir üretici entegrasyonu bulunmuyor.\n")
            .append("Bakiye alanı: belirlenmedi; kart kimliği, metin ve ham sayı değerleri bakiye olarak yorumlanmadı.\n")
            .append("Bakiye işlemi için gerekenler: sistem üreticisi, veri şeması, yetkili dolum protokolü ve erişim bilgileri.");
        return text.toString();
    }
}
