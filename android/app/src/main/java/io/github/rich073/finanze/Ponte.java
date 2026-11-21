package io.github.rich073.finanze;

import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.Settings;
import android.webkit.JavascriptInterface;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Funzioni del telefono che la pagina può usare (window.FinanzeAndroid). */
public class Ponte {
    private final MainActivity a;

    Ponte(MainActivity a) {
        this.a = a;
    }

    @JavascriptInterface
    public String leggiCoda() {
        return Coda.leggi(a);
    }

    @JavascriptInterface
    public void confermaCoda(double id) {
        Coda.conferma(a, (long) id);
    }

    @JavascriptInterface
    public boolean permessoNotifiche() {
        return Coda.permesso(a);
    }

    @JavascriptInterface
    public void apriPermessoNotifiche() {
        a.runOnUiThread(() -> {
            try {
                a.startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            } catch (Exception e) {
                a.startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });
    }

    @JavascriptInterface
    public String diagnostica() {
        return Diagnosi.json(a);
    }

    @JavascriptInterface
    public boolean provaLettura() {
        return Diagnosi.inviaProva(a);
    }

    /** La pagina comunica il "puoi ancora spendere oggi", da mostrare nella finestrella "Aggiungi spesa". */
    @JavascriptInterface
    public void impostaResiduo(double residuo) {
        AggiungiSpesa.impostaResiduo(a, residuo);
    }

    @JavascriptInterface
    public String versione() {
        return BuildConfig.VERSION_NAME;
    }

    @JavascriptInterface
    public String fonti() {
        return Fonti.leggi(a);
    }

    @JavascriptInterface
    public String paroleSms() {
        return Fonti.paroleSms(a);
    }

    @JavascriptInterface
    public boolean impostaFonti(String app, String parole) {
        return Fonti.imposta(a, app, parole);
    }

    @JavascriptInterface
    public void ascolta() {
        a.runOnUiThread(a::ascolta);
    }

    @JavascriptInterface
    public boolean improntaDisponibile() {
        return a.improntaDisponibile();
    }

    @JavascriptInterface
    public void impronta() {
        a.runOnUiThread(a::chiediImpronta);
    }

    /** Salva il backup nella cartella Download del telefono. */
    @JavascriptInterface
    public boolean salvaFile(String nome, String testo) {
        try {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, nome);
            v.put(MediaStore.Downloads.MIME_TYPE, "application/json");
            Uri u = a.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (u == null) return false;
            try (OutputStream o = a.getContentResolver().openOutputStream(u)) {
                if (o == null) return false;
                o.write(testo.getBytes(StandardCharsets.UTF_8));
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Invia il backup a Google Drive, email, WhatsApp… */
    @JavascriptInterface
    public void condividiFile(String nome, String testo) {
        try {
            File f = new File(a.getCacheDir(), nome);
            try (FileOutputStream o = new FileOutputStream(f)) {
                o.write(testo.getBytes(StandardCharsets.UTF_8));
            }
            Uri u = FileProvider.getUriForFile(a, a.getPackageName() + ".file", f);
            Intent s = new Intent(Intent.ACTION_SEND)
                    .setType("application/json")
                    .putExtra(Intent.EXTRA_STREAM, u)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.runOnUiThread(() -> a.startActivity(Intent.createChooser(s, "Backup Spiccioli")));
        } catch (Exception ignored) {
        }
    }
}
