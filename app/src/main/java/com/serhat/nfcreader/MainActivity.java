package com.serhat.nfcreader;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.nfc.tech.NdefFormatable;
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcV;
import android.nfc.tech.TagTechnology;
import android.os.Bundle;
import android.os.SystemClock;
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
    private TextView status, output, analysis, decodedOutput;
    private Button save;
    private volatile boolean foreground;
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final Object connectionLock = new Object();
    private TagTechnology active;
    private volatile long generation;
    private volatile ReadOptions options;
    private String lastReport, pendingExport;
    private String lastUid;
    private boolean lastHasNdef, lastCanFormat;
    private final CardWriteGate writeGate = new CardWriteGate();
    private byte[] copiedMessage;
    private String copiedSourceUid;
    private TextView copyStatus;
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
        analysis = label(layout, "Kart analizi, ilk okuma tamamlanınca burada gösterilecek.", 14);
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
        label(layout, "Baytları metin ve sayıya çevir", 18);
        label(layout, "Okunan UID, NDEF kayıt içeriği ve Classic veri blokları burada otomatik çözümlenir. Okunamayan alanlar çözümlenmez.", 14);
        decodedOutput = label(layout, "Çözümleme için kartı okut.", 14);
        decodedOutput.setTypeface(Typeface.MONOSPACE); decodedOutput.setTextIsSelectable(true);
        EditText hexInput = new EditText(this);
        hexInput.setHint("HEX yapıştır: 48 65 6C 6C 6F (en fazla 256 bayt)");
        hexInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        hexInput.setSaveEnabled(false); layout.addView(hexInput);
        TextView manualResult = label(layout, "Elle girilen HEX sonucu burada gösterilir.", 14);
        manualResult.setTypeface(Typeface.MONOSPACE); manualResult.setTextIsSelectable(true);
        button(layout, "Girilen HEX verisini çözümle").setOnClickListener(v -> {
            try { manualResult.setText(ByteDecoder.decode(ByteDecoder.parseInput(hexInput.getText().toString())).display()); }
            catch (IllegalArgumentException e) { manualResult.setText(e.getMessage()); }
        });
        label(layout, "NDEF yazma ve formatlama", 18);
        label(layout, "Önce hedef kartı okut. Yazma mevcut NDEF kaydını değiştirir; formatlama kartın veri düzenini değiştirebilir.", 14);
        EditText content = new EditText(this);
        content.setHint("Yazılacak metin veya https:// bağlantısı"); layout.addView(content);
        CheckBox uriMode = new CheckBox(this);
        uriMode.setText("Bağlantı olarak yaz (HTTP/HTTPS)"); layout.addView(uriMode);
        button(layout, "NDEF kaydı yaz").setOnClickListener(v -> {
            try {
                String text = content.getText().toString();
                if (text.trim().isEmpty()) throw new IllegalArgumentException("Yazılacak içerik boş olamaz.");
                NdefRecord record;
                if (uriMode.isChecked()) {
                    android.net.Uri uri = android.net.Uri.parse(text.trim());
                    if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                            || uri.getHost() == null || uri.getHost().isEmpty())
                        throw new IllegalArgumentException("Geçerli bir HTTP/HTTPS bağlantısı gir.");
                    record = NdefRecord.createUri(uri);
                } else record = NdefRecord.createTextRecord("tr", text);
                confirmWrite(CardWriteGate.Mode.WRITE, new NdefMessage(new NdefRecord[]{record}), text);
            } catch (IllegalArgumentException e) { toast(e.getMessage()); }
        });
        button(layout, "NDEF içeriğini temizle").setOnClickListener(v ->
            confirmWrite(CardWriteGate.Mode.CLEAR, emptyMessage(), "Mevcut NDEF kaydı boş kayıtla değiştirilecek."));
        button(layout, "NDEF biçiminde formatla").setOnClickListener(v ->
            confirmWrite(CardWriteGate.Mode.FORMAT, emptyMessage(), "Kart NDEF biçimine dönüştürülecek; eski uygulama verileri kaybolabilir."));
        button(layout, "Bekleyen yazma işlemini iptal et").setOnClickListener(v -> {
            writeGate.cancel(); status.setText("Yazma iptal edildi. Genel okuma etkin.");
        });
        label(layout, "Metin ve bağlantı kayıtlarını başka etikete kopyala", 18);
        copyStatus = label(layout, "Kaynak etiketi okut ve kaynağı seç. Ardından hedef etiketi normal şekilde okut.", 14);
        button(layout, "Okunan etiketi kopyalama kaynağı seç").setOnClickListener(v -> {
            try {
                if (busy.get() || lastReport == null) throw new IllegalArgumentException("Önce kaynak okumasının tamamlanmasını bekle.");
                JSONObject report = new JSONObject(lastReport);
                if (report.has("yazma_islemi")) throw new IllegalArgumentException("Kaynak etiketi yeniden normal okuma ile okut.");
                String hex = report.optString("ndef_mesaj_hex", "");
                if (hex.isEmpty()) throw new IllegalArgumentException("Kaynakta okunabilen NDEF mesajı yok.");
                NdefMessage message = new NdefMessage(Codec.parseHex(hex));
                for (NdefRecord record : message.getRecords()) {
                    boolean textRecord = record.getTnf() == NdefRecord.TNF_WELL_KNOWN && Arrays.equals(record.getType(), NdefRecord.RTD_TEXT);
                    boolean uriRecord = record.getTnf() == NdefRecord.TNF_WELL_KNOWN && Arrays.equals(record.getType(), NdefRecord.RTD_URI);
                    if (textRecord) Codec.decodeText(record.getPayload());
                    else if (uriRecord) {
                        android.net.Uri uri = record.toUri();
                        if (uri == null || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                                || uri.getHost() == null || uri.getHost().isEmpty())
                            throw new IllegalArgumentException("Yalnızca web bağlantısı ve metin kayıtları kopyalanabilir.");
                    } else throw new IllegalArgumentException("Kaynak özel kayıt içeriyor. Yalnızca standart metin ve web bağlantısı mesajları kopyalanabilir.");
                }
                copiedMessage = message.toByteArray(); copiedSourceUid = report.getString("uid_hex");
                copyStatus.setText("Kaynak seçildi: " + copiedSourceUid + ". Hedef etiketi normal şekilde okut, ardından hedefe yaz düğmesine dokun.");
            } catch (Exception e) { copiedMessage = null; copiedSourceUid = null; toast(e.getMessage() == null ? "Kaynak mesaj alınamadı." : e.getMessage()); }
        });
        button(layout, "Seçilen mesajı okunan hedef etikete yaz").setOnClickListener(v -> {
            try {
                if (copiedMessage == null) throw new IllegalArgumentException("Önce kopyalama kaynağını seç.");
                if (copiedSourceUid.equals(lastUid)) throw new IllegalArgumentException("Önce farklı hedef etiketi normal şekilde okut.");
                confirmWrite(CardWriteGate.Mode.WRITE, new NdefMessage(copiedMessage),
                    "Kaynak: " + copiedSourceUid + "\nMetin/web bağlantısı mesajı hedefteki NDEF mesajının yerine yazılacak.");
            } catch (Exception e) { toast(e.getMessage() == null ? "Kopyalama başlatılamadı." : e.getMessage()); }
        });
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
            if (lastReport != null) {
                output.setText(lastReport); save.setEnabled(true);
                try {
                    JSONObject restored = new JSONObject(lastReport);
                    analysis.setText(restored.optString("kart_analizi", "Analiz için kartı yeniden okut."));
                    decodedOutput.setText(decodedDisplay(restored));
                }
                catch (Exception ignored) { analysis.setText("Analiz için kartı yeniden okut."); }
            }
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

    private NdefMessage emptyMessage() {
        return new NdefMessage(new NdefRecord[]{new NdefRecord(NdefRecord.TNF_EMPTY, new byte[0], new byte[0], new byte[0])});
    }
    private void confirmWrite(CardWriteGate.Mode mode, NdefMessage message, String preview) {
        writeGate.cancel();
        if (busy.get()) { toast("Önce mevcut okumanın tamamlanmasını bekle."); return; }
        if (lastUid == null || lastUid.isEmpty()) { toast("Önce hedef kartı okut."); return; }
        if (mode == CardWriteGate.Mode.FORMAT && (lastHasNdef || !lastCanFormat)) {
            toast(lastHasNdef ? "Kart zaten NDEF biçiminde. İçeriği temizle seçeneğini kullan." : "Bu kart için NDEF formatlama desteklenmiyor."); return;
        }
        if (mode != CardWriteGate.Mode.FORMAT && !lastHasNdef) { toast("Bu kartta NDEF yazma desteği algılanmadı."); return; }
        final String uid = lastUid;
        String detail = preview.length() > 400 ? preview.substring(0, 400) + "…" : preview;
        new AlertDialog.Builder(this).setTitle(mode == CardWriteGate.Mode.FORMAT ? "Formatlamayı onayla" : "Yazmayı onayla")
            .setMessage("Hedef kart: " + uid + "\n\n" + detail + "\n\nOnaydan sonra aynı kartı 30 saniye içinde yeniden yaklaştır. Kartı işlem bitene kadar sabit tut.")
            .setNegativeButton("Vazgeç", (dialog, which) -> writeGate.cancel())
            .setPositiveButton("Onayla", (dialog, which) -> {
                if (!foreground || busy.get()) { toast("Kart işlemi sürüyor; yeniden dene."); return; }
                writeGate.arm(uid, mode, message.toByteArray(), SystemClock.elapsedRealtime());
                status.setText("İşlem onaylandı. Aynı kartı uzaklaştırıp 30 saniye içinde yeniden yaklaştır.");
            }).show();
    }

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
        writeGate.cancel();
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
        CardWriteGate.Request writeRequest = writeGate.consume();
        runOnUiThread(() -> { if (foreground && generation == scanGeneration) {
            status.setText("Okunuyor… Kartı sabit tut."); lastReport = null; save.setEnabled(false);
            decodedOutput.setText("Okunuyor…");
        }});
        try {
            JSONObject report = new JSONObject();
            report.put("uid_hex", Codec.hex(tag.getId()));
            report.put("okuma_zamani_utc", new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.ROOT) {{ setTimeZone(java.util.TimeZone.getTimeZone("UTC")); }}.format(new java.util.Date()));
            report.put("teknolojiler", new JSONArray(Arrays.asList(tag.getTechList())));
            report.put("ndef_formatlanabilir", NdefFormatable.get(tag) != null);
            report.put("not", "UID bakiye değildir. Ham verinin anlamı üreticinin veri düzenine bağlıdır.");
            NfcA a = NfcA.get(tag);
            if (a != null) report.put("nfc_a", new JSONObject().put("atqa_hex", Codec.hex(a.getAtqa())).put("sak", a.getSak() & 65535));
            NfcV v = NfcV.get(tag);
            if (v != null) report.put("nfc_v", new JSONObject().put("dsfid", v.getDsfId() & 255).put("response_flags", v.getResponseFlags() & 255));
            IsoDep dep = IsoDep.get(tag);
            if (dep != null) report.put("iso_dep", new JSONObject().put("historical_bytes_hex", Codec.hex(dep.getHistoricalBytes())).put("hi_layer_response_hex", Codec.hex(dep.getHiLayerResponse())));
            readNdef(tag, report);
            if (writeRequest != null) {
                if (writeRequest.matches(Codec.hex(tag.getId()), SystemClock.elapsedRealtime()))
                    writeCard(tag, report, writeRequest, scanGeneration);
                else report.put("yazma_durum", "Yazma iptal edildi: kart farklı veya 30 saniyelik onay süresi doldu. Kart değiştirilmedi.");
            } else readClassic(tag, report, scanOptions);
            JSONArray records = report.optJSONArray("ndef_kayitlar");
            JSONArray blocks = report.optJSONArray("classic_bloklar");
            Boolean writable = report.has("ndef_yazilabilir") ? report.getBoolean("ndef_yazilabilir") : null;
            String assessment = CardAnalysis.summarize(tag.getTechList(), Ndef.get(tag) != null, writable,
                records != null, NdefFormatable.get(tag) != null, records == null ? 0 : records.length(),
                blocks == null ? 0 : blocks.length());
            report.put("kart_analizi", assessment);
            report.put("dolum_protokolu_dogrulandi", false);
            report.put("bakiye_islemi_destekleniyor", false);
            addDecodings(report);
            String decoding = decodedDisplay(report);
            String json = report.toString(2);
            runOnUiThread(() -> { if (foreground && generation == scanGeneration) {
                lastReport = json; output.setText(json); save.setEnabled(true);
                analysis.setText(assessment);
                decodedOutput.setText(decoding);
                lastUid = Codec.hex(tag.getId()); lastHasNdef = Ndef.get(tag) != null;
                lastCanFormat = NdefFormatable.get(tag) != null;
                status.setText(writeRequest == null ? "Okuma tamamlandı. Ayrıntılar aşağıda." : "İşlem sonucu aşağıda. Doğrulamak için kartı yeniden okut.");
            }});
        } catch (Exception e) {
            runOnUiThread(() -> { if (foreground && generation == scanGeneration) {
                status.setText("Okuma tamamlanamadı. Kartı uzaklaştırıp yeniden yaklaştır.");
                output.setText("Yeni kartın raporu oluşturulamadı."); lastReport = null; save.setEnabled(false);
                lastUid = null;
                analysis.setText("Bu okumada kart analizi tamamlanamadı.");
                decodedOutput.setText("Yeni kartın baytları çözümlenemedi.");
            }});
        } finally { busy.set(false); }
    }

    private JSONObject byteInterpretation(String hex) throws Exception {
        ByteDecoder.Result decoded = ByteDecoder.decode(Codec.parseHex(hex));
        JSONArray numbers = new JSONArray();
        for (ByteDecoder.NumberRow row : decoded.numbers) {
            numbers.put(new JSONObject().put("bayt_konumu", row.offset).put("bit", row.width * 8)
                .put("hex", row.hex).put("le_isaretsiz", row.unsignedLE).put("le_isaretli", row.signedLE)
                .put("be_isaretsiz", row.unsignedBE).put("be_isaretli", row.signedBE));
        }
        return new JSONObject().put("bayt_sayisi", decoded.byteCount).put("onizleme_bayt_sayisi", decoded.previewCount)
            .put("onizleme_kirpildi", decoded.previewTruncated).put("hex_onizleme", decoded.hex)
            .put("ascii_onizleme", decoded.ascii).put("utf8_gecerli", decoded.utf8Valid)
            .put("utf8_onizleme", decoded.utf8).put("utf8_metin_kirpildi", decoded.utf8TextTruncated)
            .put("isaretsiz_baytlar", new JSONArray(decoded.unsignedBytes))
            .put("isaretli_baytlar", new JSONArray(decoded.signedBytes)).put("sayilar", numbers)
            .put("aciklama", decoded.display());
    }

    private void addDecodings(JSONObject report) throws Exception {
        JSONArray sources = new JSONArray();
        sources.put(new JSONObject().put("kaynak", "UID (kart kimliği)")
            .put("cozumleme", byteInterpretation(report.getString("uid_hex"))));
        JSONArray blocks = report.optJSONArray("classic_bloklar");
        if (blocks != null) for (int i = 0; i < blocks.length(); i++) {
            JSONObject block = blocks.getJSONObject(i);
            sources.put(new JSONObject().put("kaynak", "Classic blok " + block.getInt("blok"))
                .put("cozumleme", byteInterpretation(block.getString("hex"))));
        }
        JSONArray records = report.optJSONArray("ndef_kayitlar");
        int recordCount = records == null ? 0 : Math.min(records.length(), 32);
        for (int i = 0; i < recordCount; i++) sources.put(new JSONObject()
            .put("kaynak", "NDEF kayıt " + i + " ham içerik (başlık baytları dahil)")
            .put("cozumleme", byteInterpretation(records.getJSONObject(i).getString("payload_hex"))));
        report.put("bayt_cozumlemeleri", sources);
        report.put("ndef_cozumleme_kayit_limiti", 32);
        report.put("ndef_cozumleme_kirpildi", records != null && records.length() > recordCount);
        report.put("cozumleme_notu", "64 bit dahil sayılar kayıpsız ondalık metin olarak dışa aktarılır. Dönüşüm alanın işlevini doğrulamaz.");
    }

    private String decodedDisplay(JSONObject report) throws Exception {
        JSONArray sources = report.optJSONArray("bayt_cozumlemeleri");
        if (sources == null) return "Çözümleme için kartı yeniden okut.";
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < sources.length(); i++) {
            JSONObject source = sources.getJSONObject(i);
            text.append(source.getString("kaynak")).append('\n')
                .append(source.getJSONObject("cozumleme").getString("aciklama")).append("\n\n");
        }
        if (report.has("classic_durum")) text.append(report.getString("classic_durum")).append('\n');
        if (report.optBoolean("ndef_cozumleme_kirpildi"))
            text.append("İlk 32 NDEF kayıt içeriği çözümlendi. Tüm ham kayıtlar JSON raporunda korunur.\n");
        return text.toString();
    }

    private void writeCard(Tag tag, JSONObject report, CardWriteGate.Request request, long scanGeneration) throws Exception {
        report.put("yazma_islemi", request.mode.name());
        NdefMessage message = new NdefMessage(request.message());
        Ndef ndef = Ndef.get(tag);
        boolean commandStarted = false;
        TagTechnology connection = null;
        try {
            if (request.mode == CardWriteGate.Mode.FORMAT) {
                if (ndef != null) { report.put("yazma_durum", "Kart zaten NDEF biçiminde; formatlama yapılmadı."); return; }
                NdefFormatable formatable = NdefFormatable.get(tag);
                if (formatable == null) { report.put("yazma_durum", "NDEF formatlama desteklenmiyor; kart değiştirilmedi."); return; }
                connection = formatable; open(formatable);
                if (!foreground || generation != scanGeneration) throw new java.io.IOException("İşlem iptal edildi.");
                commandStarted = true; formatable.format(message);
                report.put("yazma_durum", "NDEF formatlama çağrısı tamamlandı. Sonucu doğrulamak için kartı yeniden okut.");
                report.put("yazma_dogrulandi", false);
            } else {
                if (ndef == null) { report.put("yazma_durum", "NDEF desteği yok; kart değiştirilmedi."); return; }
                connection = ndef; open(ndef);
                if (!ndef.isWritable()) { report.put("yazma_durum", "Kart salt okunur; kart değiştirilmedi."); return; }
                if (message.toByteArray().length > ndef.getMaxSize()) { report.put("yazma_durum", "İçerik kart kapasitesini aşıyor; kart değiştirilmedi."); return; }
                NdefMessage previous = ndef.getNdefMessage();
                report.put("onceki_ndef_hex", previous == null ? "" : Codec.hex(previous.toByteArray()));
                if (!foreground || generation != scanGeneration) throw new java.io.IOException("İşlem iptal edildi.");
                commandStarted = true; ndef.writeNdefMessage(message);
                NdefMessage verify = ndef.getNdefMessage();
                boolean matches = verify != null && Arrays.equals(message.toByteArray(), verify.toByteArray());
                report.put("yazma_dogrulandi", matches);
                report.put("yazma_durum", matches ? "NDEF yazıldı ve karttan tekrar okunarak doğrulandı." : "Yazma çağrısı tamamlandı, ancak veri doğrulanamadı. Kartı yeniden okut.");
            }
        } catch (Exception e) {
            report.put("yazma_dogrulandi", false);
            report.put("yazma_durum", commandStarted ? "İşlem kesildi; kartın değişip değişmediği doğrulanamadı. Kartı yeniden okuyarak kontrol et. Otomatik tekrar yapılmadı." : "İşlem başlatılamadı; kart değiştirilmedi.");
        } finally { if (connection != null) close(connection); }
        // The main NDEF fields describe the scan before the mutation; distinguish them explicitly.
        report.put("ndef_kayitlari_asamasi", "İşlem öncesi okuma");
    }

    private void readNdef(Tag tag, JSONObject report) throws Exception {
        Ndef ndef = Ndef.get(tag);
        if (ndef == null) { report.put("ndef_durum", "NDEF algılanmadı; bu, kartın boş olduğu anlamına gelmez."); return; }
        try {
            open(ndef);
            report.put("ndef_turu", ndef.getType()); report.put("ndef_kapasite_bayt", ndef.getMaxSize());
            report.put("ndef_yazilabilir", ndef.isWritable());
            NdefMessage message = ndef.getNdefMessage();
            if (message != null) report.put("ndef_mesaj_hex", Codec.hex(message.toByteArray()));
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
