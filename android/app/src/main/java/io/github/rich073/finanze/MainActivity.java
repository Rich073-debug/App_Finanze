package io.github.rich073.finanze;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    static final String INDIRIZZO = "https://rich073-debug.github.io/App_Finanze/";
    static final String HOST = "rich073-debug.github.io";

    private static volatile MainActivity inPrimoPiano;

    WebView web;
    private ValueCallback<Uri[]> attesaFile;
    ActivityResultLauncher<Intent> sceltaFile;
    ActivityResultLauncher<Intent> voce;

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle stato) {
        super.onCreate(stato);

        sceltaFile = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
            if (attesaFile != null) {
                attesaFile.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(r.getResultCode(), r.getData()));
                attesaFile = null;
            }
        });
        voce = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
            String lista = "null";
            if (r.getResultCode() == RESULT_OK && r.getData() != null) {
                ArrayList<String> l = r.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (l != null) lista = new JSONArray(l).toString();
            }
            js("window.risultatoVoce&&risultatoVoce(" + lista + ")");
        });

        web = new WebView(this);
        web.setBackgroundColor(0xFF111827);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setUserAgentString(s.getUserAgentString() + " FinanzeApp/" + BuildConfig.VERSION_NAME);
        web.addJavascriptInterface(new Ponte(this), "FinanzeAndroid");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (HOST.equals(u.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception ignored) {
                }
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (attesaFile != null) attesaFile.onReceiveValue(null);
                attesaFile = cb;
                try {
                    Intent i = new Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
                    sceltaFile.launch(i);
                } catch (Exception e) {
                    attesaFile = null;
                    return false;
                }
                return true;
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                web.evaluateJavascript("window.indietroAndroid?indietroAndroid():false", v -> {
                    if ("true".equals(v)) return;
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                });
            }
        });

        if (stato == null || web.restoreState(stato) == null) web.loadUrl(INDIRIZZO);

        Sveglia.programma(this);

        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    private long inPausaDa = 0;

    @Override
    protected void onResume() {
        super.onResume();
        // dopo mezz'ora in background ricarica la pagina: così prende sempre l'ultima versione
        if (inPausaDa > 0 && System.currentTimeMillis() - inPausaDa > 30 * 60 * 1000L) web.reload();
        inPausaDa = 0;
        inPrimoPiano = this;
        web.onResume();
        Diagnosi.ricollega(this);
        AscoltaNotifiche.recupera(this);
        js("window.nuoveNotifiche&&nuoveNotifiche()");
    }

    @Override
    protected void onPause() {
        inPrimoPiano = null;
        inPausaDa = System.currentTimeMillis();
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    void js(String codice) {
        runOnUiThread(() -> {
            if (web != null) web.evaluateJavascript(codice, null);
        });
    }

    /** Se l'app è aperta, registra subito la notifica appena arrivata. */
    static boolean avvisaSeAperta() {
        MainActivity a = inPrimoPiano;
        if (a == null) return false;
        a.js("window.nuoveNotifiche&&nuoveNotifiche()");
        return true;
    }

    boolean improntaDisponibile() {
        return BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                == BiometricManager.BIOMETRIC_SUCCESS;
    }

    void chiediImpronta() {
        BiometricPrompt p = new BiometricPrompt(this, ContextCompat.getMainExecutor(this), new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) {
                js("window.risultatoImpronta&&risultatoImpronta(true)");
            }

            @Override
            public void onAuthenticationError(int codice, CharSequence msg) {
                js("window.risultatoImpronta&&risultatoImpronta(false)");
            }
        });
        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("NotiPay")
                .setSubtitle("Sblocca con impronta o volto")
                .setNegativeButtonText("Usa il PIN")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build();
        try {
            p.authenticate(info);
        } catch (Exception e) {
            js("window.risultatoImpronta&&risultatoImpronta(false)");
        }
    }

    void ascolta() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT");
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Di' l'importo, es. \"3 euro e 50\"");
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        try {
            voce.launch(i);
        } catch (Exception e) {
            js("window.risultatoVoce&&risultatoVoce(null)");
        }
    }
}
