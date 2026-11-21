package io.github.rich073.finanze;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finestrella "Aggiungi spesa" / "Detta una spesa": registra una spesa senza aprire l'app. */
public class AggiungiSpesa extends AppCompatActivity {

    private ActivityResultLauncher<Intent> voce;
    private EditText importo, descrizione;
    private TextView sentito;

    @Override
    protected void onCreate(Bundle stato) {
        super.onCreate(stato);
        voce = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
            if (r.getResultCode() == RESULT_OK && r.getData() != null) {
                ArrayList<String> l = r.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (l != null && !l.isEmpty()) {
                    sentito.setText("Ho sentito: \"" + l.get(0) + "\"");
                    for (String t : l) {
                        String v = importoDaTesto(t);
                        if (v != null) {
                            importo.setText(v);
                            importo.setSelection(v.length());
                            break;
                        }
                    }
                }
            }
        });
        mostra();
        Uri u = getIntent().getData();
        if (stato == null && u != null && "voce".equals(u.getHost())) ascolta();
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private void mostra() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(8), dp(22), 0);

        String residuo = residuoDiOggi(this);
        if (residuo != null) {
            TextView r = new TextView(this);
            r.setText("Puoi ancora spendere oggi: " + residuo);
            r.setPadding(0, 0, 0, dp(10));
            box.addView(r);
        }

        importo = new EditText(this);
        importo.setHint("Importo, es. 3,50");
        importo.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        importo.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789.,"));
        importo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        importo.setGravity(Gravity.CENTER);
        box.addView(importo);

        descrizione = new EditText(this);
        descrizione.setHint("Cosa hai comprato (facoltativo)");
        descrizione.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        box.addView(descrizione);

        sentito = new TextView(this);
        sentito.setPadding(0, dp(6), 0, 0);
        box.addView(sentito);

        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("Aggiungi spesa")
                .setView(box)
                .setPositiveButton("Salva", null)
                .setNegativeButton("Annulla", (x, y) -> finish())
                .setNeutralButton("🎤 Detta", null)
                .setOnCancelListener(x -> finish())
                .create();
        d.setOnShowListener(x -> {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> salva());
            d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> ascolta());
            importo.requestFocus();
        });
        d.show();
    }

    private void ascolta() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT");
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Di' l'importo, es. \"3 euro e 50\"");
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        try {
            voce.launch(i);
        } catch (Exception e) {
            Toast.makeText(this, "Riconoscimento vocale non disponibile", Toast.LENGTH_SHORT).show();
        }
    }

    private void salva() {
        String t = importo.getText().toString().trim().replace(',', '.');
        double v;
        try {
            v = Double.parseDouble(t);
        } catch (Exception e) {
            importo.setError("Scrivi l'importo");
            return;
        }
        if (v <= 0 || v > 100000) {
            importo.setError("Importo non valido");
            return;
        }
        v = Math.round(v * 100) / 100.0;
        Coda.aggiungiSpesa(this, v, descrizione.getText().toString().trim());
        String euro = String.format(Locale.ITALY, "%.2f €", v);
        String resto = scalaResiduo(this, v);
        Toast.makeText(this, "Spesa di " + euro + " salvata" + (resto != null ? " · oggi restano " + resto : ""), Toast.LENGTH_LONG).show();
        MainActivity.avvisaSeAperta();
        finish();
    }

    /** "3 euro e 50", "3,50 €", "ho speso 12" → "3,50" / "12" */
    static String importoDaTesto(String testo) {
        String t = testo.toLowerCase(Locale.ITALY);
        Matcher m = Pattern.compile("(\\d+)\\s*(?:euro|€)\\s*(?:e\\s*)?(\\d{1,2})\\b").matcher(t);
        if (m.find()) return m.group(1) + "," + (m.group(2).length() == 1 ? m.group(2) + "0" : m.group(2));
        m = Pattern.compile("(\\d+)(?:[.,](\\d{1,2}))?").matcher(t);
        if (m.find()) return m.group(1) + (m.group(2) != null ? "," + (m.group(2).length() == 1 ? m.group(2) + "0" : m.group(2)) : "");
        return null;
    }

    private static String oggi() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
    }

    /** Il "puoi ancora spendere oggi" che l'app ha calcolato l'ultima volta che l'hai aperta (solo se è di oggi). */
    static String residuoDiOggi(Context c) {
        SharedPreferences p = c.getSharedPreferences("budget", MODE_PRIVATE);
        if (!oggi().equals(p.getString("giorno", ""))) return null;
        float r = p.getFloat("residuo", Float.NaN);
        return Float.isNaN(r) ? null : String.format(Locale.ITALY, "%.2f €", r);
    }

    static void impostaResiduo(Context c, double residuo) {
        c.getSharedPreferences("budget", MODE_PRIVATE).edit()
                .putString("giorno", oggi()).putFloat("residuo", (float) residuo).apply();
    }

    private static String scalaResiduo(Context c, double spesa) {
        SharedPreferences p = c.getSharedPreferences("budget", MODE_PRIVATE);
        if (!oggi().equals(p.getString("giorno", ""))) return null;
        float r = p.getFloat("residuo", Float.NaN);
        if (Float.isNaN(r)) return null;
        float nuovo = (float) (r - spesa);
        p.edit().putFloat("residuo", nuovo).apply();
        return String.format(Locale.ITALY, "%.2f €", nuovo);
    }
}
