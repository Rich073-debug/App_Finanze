package io.github.rich073.finanze;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Coda delle notifiche dei pagamenti: il servizio le aggiunge, l'app (la pagina web)
 * le legge e le conferma una alla volta dopo averle registrate.
 */
final class Coda {
    private static final String PREF = "coda";
    private static final long DIECI_MINUTI = 10 * 60 * 1000L;
    private static final String CANALE = "pagamenti";
    private static final int ID_AVVISO = 7301;

    private Coda() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** Aggiunge una notifica. Restituisce false se è un doppione arrivato da poco. */
    static synchronized boolean aggiungi(Context c, String fonte, String titolo, String testo, long ts, String chiave) {
        try {
            SharedPreferences p = sp(c);
            // ogni notifica (identificata dal telefono) si mette in coda una volta sola, anche se la si ripesca più tardi
            JSONObject chiavi = new JSONObject(p.getString("chiavi", "{}"));
            long adesso = System.currentTimeMillis();
            JSONObject chiaviTenute = new JSONObject();
            java.util.Iterator<String> itc = chiavi.keys();
            while (itc.hasNext()) {
                String k = itc.next();
                long t = chiavi.optLong(k);
                if (adesso - t < 7L * 24 * 3600 * 1000) chiaviTenute.put(k, t);
            }
            if (chiave != null && chiaviTenute.has(chiave)) return false;
            if (chiave != null) chiaviTenute.put(chiave, adesso);
            p.edit().putString("chiavi", chiaviTenute.toString()).commit();
            JSONArray eventi = new JSONArray(p.getString("eventi", "[]"));
            JSONArray recenti = new JSONArray(p.getString("recenti", "[]"));
            String firma = fonte + "|" + titolo + "|" + testo;
            long ora = System.currentTimeMillis();
            JSONArray tenuti = new JSONArray();
            boolean doppione = false;
            for (int i = 0; i < recenti.length(); i++) {
                JSONObject r = recenti.getJSONObject(i);
                if (ora - r.optLong("ts") > DIECI_MINUTI) continue;
                if (firma.equals(r.optString("firma"))) doppione = true;
                tenuti.put(r);
            }
            if (doppione) {
                p.edit().putString("recenti", tenuti.toString()).commit();
                return false;
            }
            long id = p.getLong("prossimo", 1);
            JSONObject e = new JSONObject();
            e.put("id", id);
            e.put("ts", ts > 0 ? ts : ora);
            e.put("fonte", fonte);
            e.put("titolo", titolo);
            e.put("testo", testo);
            eventi.put(e);
            // al massimo 300 in attesa: le più vecchie si perdono
            while (eventi.length() > 300) eventi.remove(0);
            JSONObject r = new JSONObject();
            r.put("firma", firma);
            r.put("ts", ora);
            tenuti.put(r);
            p.edit()
                    .putString("eventi", eventi.toString())
                    .putString("recenti", tenuti.toString())
                    .putLong("prossimo", id + 1)
                    .commit();
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    static synchronized String leggi(Context c) {
        return sp(c).getString("eventi", "[]");
    }

    static synchronized int quante(Context c) {
        try {
            return new JSONArray(leggi(c)).length();
        } catch (Exception e) {
            return 0;
        }
    }

    /** Toglie dalla coda la notifica già registrata dall'app. */
    static synchronized void conferma(Context c, long id) {
        try {
            JSONArray eventi = new JSONArray(leggi(c));
            JSONArray resto = new JSONArray();
            for (int i = 0; i < eventi.length(); i++) {
                JSONObject e = eventi.getJSONObject(i);
                if (e.optLong("id") != id) resto.put(e);
            }
            sp(c).edit().putString("eventi", resto.toString()).commit();
            if (resto.length() == 0) NotificationManagerCompat.from(c).cancel(ID_AVVISO);
            else avviso(c);
        } catch (Exception ignored) {
        }
    }

    static boolean permesso(Context c) {
        return NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.getPackageName());
    }

    /** Piccolo avviso silenzioso: "2 pagamenti salvati, apri Finanze per vederli nel budget". */
    static void avviso(Context c) {
        try {
            int n = quante(c);
            NotificationManagerCompat nm = NotificationManagerCompat.from(c);
            if (n == 0) {
                nm.cancel(ID_AVVISO);
                return;
            }
            if (!nm.areNotificationsEnabled()) return;
            NotificationManager sistema = c.getSystemService(NotificationManager.class);
            if (sistema != null && sistema.getNotificationChannel(CANALE) == null) {
                NotificationChannel ch = new NotificationChannel(CANALE, "Pagamenti salvati", NotificationManager.IMPORTANCE_LOW);
                ch.setDescription("Avvisa quando ci sono pagamenti salvati da vedere nell'app");
                ch.setShowBadge(true);
                sistema.createNotificationChannel(ch);
            }
            Intent apri = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pi = PendingIntent.getActivity(c, 0, apri, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            String testo = n == 1 ? "1 pagamento salvato: apri Finanze per vederlo nel budget"
                    : n + " pagamenti salvati: apri Finanze per vederli nel budget";
            NotificationCompat.Builder b = new NotificationCompat.Builder(c, CANALE)
                    .setSmallIcon(R.drawable.ic_notifica)
                    .setContentTitle("Finanze")
                    .setContentText(testo)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW);
            nm.notify(ID_AVVISO, b.build());
        } catch (SecurityException ignored) {
            // permesso per le notifiche non concesso: pazienza, la coda resta comunque
        }
    }
}
