package io.github.rich073.finanze;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Locale;

/**
 * Quali app ascoltare e con quale "fonte" passarle alla pagina.
 * Si possono cambiare dall'app (Impostazioni → App Android).
 */
final class Fonti {
    private Fonti() {}

    static final String PREDEFINITE = "{"
            + "\"wallet\":[\"com.google.android.apps.walletnfcrel\"],"
            + "\"banca\":[\"it.phoenixspa\"],"
            + "\"paypal\":[\"com.paypal.android.p2pmobile\"],"
            + "\"sms\":[\"com.google.android.apps.messaging\",\"com.android.mms\",\"com.samsung.android.messaging\"]"
            + "}";
    static final String PAROLE_SMS = "[\"inbank\"]";

    static String leggi(Context c) {
        return c.getSharedPreferences("fonti", Context.MODE_PRIVATE).getString("app", PREDEFINITE);
    }

    static String paroleSms(Context c) {
        return c.getSharedPreferences("fonti", Context.MODE_PRIVATE).getString("paroleSms", PAROLE_SMS);
    }

    static boolean imposta(Context c, String app, String parole) {
        try {
            new JSONObject(app);
            new JSONArray(parole);
            c.getSharedPreferences("fonti", Context.MODE_PRIVATE).edit()
                    .putString("app", app).putString("paroleSms", parole).commit();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** La fonte ("wallet", "banca", "paypal", "sms") dell'app che ha mandato la notifica, o null. */
    static String fonte(Context c, String pacchetto) {
        try {
            JSONObject o = new JSONObject(leggi(c));
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String fonte = it.next();
                JSONArray pk = o.getJSONArray(fonte);
                for (int i = 0; i < pk.length(); i++) {
                    String p = pk.getString(i).trim();
                    if (!p.isEmpty() && (pacchetto.equals(p) || pacchetto.startsWith(p + "."))) return fonte;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** Gli SMS si leggono solo se vengono dalla banca (es. mittente "Inbank"). */
    static boolean smsDellaBanca(Context c, String testo) {
        try {
            String t = testo.toLowerCase(Locale.ROOT);
            JSONArray parole = new JSONArray(paroleSms(c));
            for (int i = 0; i < parole.length(); i++) {
                String p = parole.getString(i).trim().toLowerCase(Locale.ROOT);
                if (!p.isEmpty() && t.contains(p)) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}
