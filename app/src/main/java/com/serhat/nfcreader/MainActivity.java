package com.serhat.nfcreader;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.Ndef;
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcV;
import android.nfc.tech.TagTechnology;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity implements NfcAdapter.ReaderCallback {
    private NfcAdapter adapter;
    private TextView status, output;
    private Button save;
    private volatile boolean foreground;
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final Object connectionLock = new Object();
    private TagTechnology active;
    private volatile long generation;
    private volatile ReadOptions options;
    private String lastReport, pendingExport;
    private static final int EXPORT = 10;

    private static final class ReadOptions {
        final int sector;
        final byte[] key;
        final boolean keyB;
        ReadOptions(int sector, byte[] key, boolean keyB) {
            this.sector = sector; this.key = key.clone(); this.keyB = keyB;
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        adapter = NfcAdapter.getDefaultAdapter(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);
        layout.setBackgroundColor(Color.rgb(242, 246, 248));
        scroll.addView(layout); setContentView(scroll);
        TextView title = label(layout, "NFC Kart Okuyucu", 26);
        title.setTypeface(null, Typeface.BOLD);
        label(layout, "Kartı telefonun arkasına tut. Okuma tamamlanana kadar sabit beklet.", 16);
        status = label(layout, "Hazırlanıyor…", 16);
        Button settings = button(layout, "NFC ayarlarını aç");
        settings.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_NFC_SETTINGS)); }
            catch (RuntimeException e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
        });
        label(layout, "İsteğe bağlı: MIFARE Classic sektör okuma", 18);
        label(layout, "Bildiğin sektör anahtarını gir. Anahtar olmadan yalnızca kart bilgileri ve erişilebilir NDEF kayıtları okunur.", 14);
        EditText sector = new EditText(this);
        sector.setHint("Sektör numarası, ör. 0");
        sector.setInputType(InputType.TYPE_CLASS_NUMBER);
        sector.setSaveEnabled(false); layout.addView(sector);
        EditText key = new EditText(this);
        key.setHint("6 bayt anahtar: 12 hex karakter");
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setSingleLine(true); key.setSaveEnabled(false); layout.addView(key);
        CheckBox keyB = new CheckBox(this);
        keyB.setText("Anahtar B kullan (varsayılan: A)"); layout.addView(keyB);
        Button enable = button(layout, "Anahtarı uygula, ardından kartı okut");
        enable.setOnClickListener(v -> {
            try {
                int index = Integer.parseInt(sector.getText().toString().trim());
                if (index < 0 || index > 39) throw new IllegalArgumentException("Sektör 0–39 arasında olmalı.");
                byte[] bytes = Codec.parseKey(key.getText().toString());
                options = new ReadOptions(index, bytes, keyB.isChecked());
                Arrays.fill(bytes, (byte) 0);
                status.setText("Sektör " + index + " okuma etkin. Kartı yeniden yaklaştır.");
            } catch (IllegalArgumentException e) { toast(e.getMessage()); }
        });
        Button reset = button(layout, "Anahtarlı okumayı kapat");
        reset.setOnClickListener(v -> { options = null; key.setText(""); status.setText("Genel okuma etkin. Kartı yaklaştır."); });
        save = button(layout, "Sonucu JSON olarak kaydet"); save.setEnabled(false);
        save.setOnClickListener(v -> {
            if (lastReport == null) return;
            pendingExport = lastReport;
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json"); intent.putExtra(Intent.EXTRA_TITLE, "nfc-kart-raporu.json");
            try { startActivityForResult(intent, EXPORT); }
            catch (RuntimeException e) { pendingExport = null; toast("Dosya seçici açılamadı."); }
        });
        output = label(layout, "Henüz kart okunmadı.", 14);
        output.setTypeface(Typeface.MONOSPACE); output.setTextIsSelectable(true);
        if (state != null) {
            lastReport = state.getString("report"); pendingExport = state.getString("export");
            if (lastReport != null) { output.setText(lastReport); save.setEnabled(true); }
        }
    }

    private TextView label(LinearLayout layout, String text, int size) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size);
        view.setTextColor(Color.rgb(25, 43, 55)); view.setPadding(0, 12, 0, 12);
        layout.addView(view); return view;
    }
    private Button button(LinearLayout layout, String text) {
        Button view = new Button(this); view.setText(text); view.setAllCaps(false);
        layout.addView(view); return view;
    }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }

    @Override protected void onResume() {
        super.onResume(); foreground = true; generation++;
        if (adapter == null) { status.setText("Bu telefonda NFC donanımı bulunamadı."); return; }
        if (!adapter.isEnabled()) { status.setText("NFC kapalı. Ayarlardan aç."); return; }
        int flags = NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B
                | NfcAdapter.FLAG_READER_NFC_F | NfcAdapter.FLAG_READER_NFC_V
                | NfcAdapter.FLAG_READER_NFC_BARCODE;
        try { adapter.enableReaderMode(this, this, flags, null); status.setText("Hazır. Kartı yaklaştır."); }
        catch (RuntimeException e) { status.setText("NFC okuyucu başlatılamadı. NFC ayarlarını kontrol et."); }
    }
    @Override protected void onPause() {
        foreground = false; generation++;
        if (adapter != null) adapter.disableReaderMode(this);
        synchronized (connectionLock) {
            if (active != null) { try { active.close(); } catch (Exception ignored) {} active = null; }
        }
        super.onPause();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("report", lastReport); state.putString("export", pendingExport);
        super.onSaveInstanceState(state);
    }
    private void open(TagTechnology technology) throws Exception {
        synchronized (connectionLock) {
            if (!foreground) throw new java.io.IOException("Okuma durduruldu.");
            active = technology;
        }
        technology.connect();
        if (!foreground) { close(technology); throw new java.io.IOException("Okuma durduruldu."); }
    }
    private void close(TagTechnology technology) {
        try { technology.close(); } catch (Exception ignored) {}
        synchronized (connectionLock) { if (active == technology) active = null; }
    }

    @Override public void onTagDiscovered(Tag tag) {
        if (!foreground || !busy.compareAndSet(false, true)) return;
        long scanGeneration = generation;
        ReadOptions scanOptions = options;
        runOnUiThread(() -> { if (foreground && generation == scanGeneration) {
            status.setText("Okunuyor… Kartı sabit tut."); lastReport = null; save.setEnabled(false);
        }});
        try {
            JSONObject report = new JSONObject();
            report.put("uid_hex", Codec.hex(tag.getId()));
            report.put("okuma_zamani_utc", new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.ROOT) {{ setTimeZone(java.util.TimeZone.getTimeZone("UTC")); }}.format(new java.util.Date()));
            report.put("teknolojiler", new JSONArray(Arrays.asList(tag.getTechList())));
            report.put("not", "UID bakiye değildir. Ham verinin anlamı üreticinin veri düzenine bağlıdır.");
            NfcA a = NfcA.get(tag);
            if (a != null) report.put("nfc_a", new JSONObject().put("atqa_hex", Codec.hex(a.getAtqa())).put("sak", a.getSak() & 65535));
            NfcV v = NfcV.get(tag);
            if (v != null) report.put("nfc_v", new JSONObject().put("dsfid", v.getDsfId() & 255).put("response_flags", v.getResponseFlags() & 255));
            IsoDep dep = IsoDep.get(tag);
            if (dep != null) report.put("iso_dep", new JSONObject().put("historical_bytes_hex", Codec.hex(dep.getHistoricalBytes())).put("hi_layer_response_hex", Codec.hex(dep.getHiLayerResponse())));
            readNdef(tag, report);
            readClassic(tag, report, scanOptions);
            String json = report.toString(2);
            runOnUiThread(() -> { if (foreground && generation == scanGeneration) {
                lastReport = json; output.setText(json); save.setEnabled(true);
                status.setText("Okuma tamamlandı. Ayrıntılar aşağıda.");
            }});
        } catch (Exception e) {
            runOnUiThread(() -> { if (foreground && generation == scanGeneration) {
                status.setText("Okuma tamamlanamadı. Kartı uzaklaştırıp yeniden yaklaştır.");
                output.setText("Yeni kartın raporu oluşturulamadı."); lastReport = null; save.setEnabled(false);
            }});
        } finally { busy.set(false); }
    }

    private void readNdef(Tag tag, JSONObject report) throws Exception {
        Ndef ndef = Ndef.get(tag);
        if (ndef == null) { report.put("ndef_durum", "NDEF algılanmadı; bu, kartın boş olduğu anlamına gelmez."); return; }
        try {
            open(ndef);
            report.put("ndef_turu", ndef.getType()); report.put("ndef_kapasite_bayt", ndef.getMaxSize());
            NdefMessage message = ndef.getNdefMessage();
            JSONArray records = new JSONArray();
            if (message != null) for (NdefRecord record : message.getRecords()) {
                JSONObject item = new JSONObject().put("tnf", record.getTnf())
                    .put("type_hex", Codec.hex(record.getType())).put("id_hex", Codec.hex(record.getId()))
                    .put("payload_hex", Codec.hex(record.getPayload()));
                try {
                    if (record.getTnf() == NdefRecord.TNF_WELL_KNOWN && Arrays.equals(record.getType(), NdefRecord.RTD_TEXT))
                        item.put("metin", Codec.decodeText(record.getPayload()));
                    else if (record.toUri() != null) item.put("uri", record.toUri().toString());
                } catch (RuntimeException e) { item.put("cozumleme", "Kayıt metin/URI olarak çözümlenemedi; ham veri korunmuştur."); }
                records.put(item);
            }
            report.put("ndef_kayitlar", records);
            report.put("ndef_durum", message == null ? "NDEF mesajı yok." : "NDEF okundu.");
        } catch (Exception e) { report.put("ndef_durum", "NDEF okunamadı: kart uzaklaşmış veya erişim engellenmiş olabilir."); }
        finally { close(ndef); }
    }

    private void readClassic(Tag tag, JSONObject report, ReadOptions request) throws Exception {
        MifareClassic classic = MifareClassic.get(tag);
        if (classic == null) {
            if (request != null) report.put("classic_durum", "MIFARE Classic bu kart/telefon birleşiminde desteklenmiyor.");
            return;
        }
        report.put("classic_bellek_bayt", classic.getSize());
        report.put("classic_sektor_sayisi", classic.getSectorCount());
        if (request == null) { report.put("classic_durum", "Sektör okuma için bilinen anahtar girilmedi."); return; }
        if (request.sector >= classic.getSectorCount()) { report.put("classic_durum", "Seçilen sektör bu kartta bulunmuyor."); return; }
        JSONArray blocks = new JSONArray();
        report.put("classic_bloklar", blocks); report.put("classic_sektor", request.sector);
        try {
            open(classic);
            boolean authenticated = request.keyB ? classic.authenticateSectorWithKeyB(request.sector, request.key)
                : classic.authenticateSectorWithKeyA(request.sector, request.key);
            if (!authenticated) { report.put("classic_durum", "Anahtar doğrulanmadı; sektör okunmadı."); return; }
            int start = classic.sectorToBlock(request.sector);
            int count = classic.getBlockCountInSector(request.sector);
            // Sector trailer contains keys/access bits; omit it from exported reports.
            for (int i = 0; i < count - 1; i++) {
                if (!foreground) throw new java.io.IOException("Okuma durduruldu.");
                blocks.put(new JSONObject().put("blok", start + i).put("hex", Codec.hex(classic.readBlock(start + i))));
            }
            report.put("classic_durum", "Seçilen sektörün veri blokları okundu; anahtar bloğu rapora alınmadı.");
        } catch (Exception e) { report.put("classic_durum", "Okuma kesildi veya blok okuma izni yok. Okunabilen bloklar raporda korunmuştur."); }
        finally { close(classic); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != EXPORT) return;
        String snapshot = pendingExport; pendingExport = null;
        if (resultCode != RESULT_OK || data == null || data.getData() == null || snapshot == null) return;
        try (OutputStream stream = getContentResolver().openOutputStream(data.getData(), "wt")) {
            if (stream == null) throw new java.io.IOException("Dosya açılamadı.");
            stream.write(snapshot.getBytes(StandardCharsets.UTF_8)); toast("JSON raporu kaydedildi.");
        } catch (Exception e) { toast("Rapor kaydedilemedi."); }
    }
}
