package io.github.rich073.finanze;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Controllo della lettura: quali app ci sono, se il servizio è collegato, quando ha visto l'ultima notifica. */
final class Diagnosi {
    static final String EXTRA_PROVA = "finanze_prova";
    private static final int ID_PROVA = 7302;

    private Diagnosi() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("diagnosi", Context.MODE_PRIVATE);
    }

    static void connesso(Context c) {
        sp(c).edit().putLong("connesso", System.currentTimeMillis()).putLong("fermoDa", 0).apply();
        NotificationManagerCompat.from(c).cancel(ID_AVVISO_FERMO);
    }

    private static final int ID_AVVISO_FERMO = 7304;

    /** Chiamato ogni 15 minuti: se la lettura resta ferma (o il permesso è stato tolto) avvisa con una notifica. */
    static void controllaEAvvisa(Context c) {
        SharedPreferences p = sp(c);
        long ora = System.currentTimeMillis();
        boolean permesso = Coda.permesso(c);
        if (permesso && AscoltaNotifiche.attivo != null) {
            p.edit().putLong("fermoDa", 0).apply();
            NotificationManagerCompat.from(c).cancel(ID_AVVISO_FERMO);
            return;
        }
        long fermoDa = p.getLong("fermoDa", 0);
        if (fermoDa == 0) {
            p.edit().putLong("fermoDa", ora).apply();
            return;
        }
        // ferma da più di 20 minuti; al massimo un avviso ogni 6 ore
        if (ora - fermoDa < 20 * 60 * 1000L || ora - p.getLong("avvisato", 0) < 6 * 3600 * 1000L) return;
        try {
            NotificationManagerCompat nm = NotificationManagerCompat.from(c);
            if (!nm.areNotificationsEnabled()) return;
            NotificationManager sistema = c.getSystemService(NotificationManager.class);
            if (sistema != null && sistema.getNotificationChannel("avvisi") == null) {
                sistema.createNotificationChannel(new NotificationChannel("avvisi", "Problemi di lettura", NotificationManager.IMPORTANCE_DEFAULT));
            }
            android.content.Intent apri = new android.content.Intent(c, MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
            android.app.PendingIntent pi = android.app.PendingIntent.getActivity(c, 1, apri, android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT);
            String testo = permesso ? "Il telefono ha fermato la lettura dei pagamenti: tocca per sistemare"
                    : "L'accesso alle notifiche è stato tolto: tocca per riattivarlo";
            nm.notify(ID_AVVISO_FERMO, new NotificationCompat.Builder(c, "avvisi")
                    .setSmallIcon(R.drawable.ic_notifica)
                    .setContentTitle("⚠️ NotiPay non sta leggendo i pagamenti")
                    .setContentText(testo)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(testo))
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build());
            p.edit().putLong("avvisato", ora).apply();
        } catch (SecurityException ignored) {
        }
    }

    static void visto(Context c, String fonte) {
        sp(c).edit().putLong("visto_" + fonte, System.currentTimeMillis()).apply();
    }

    static void prova(Context c) {
        sp(c).edit().putLong("prova", System.currentTimeMillis()).apply();
        NotificationManagerCompat.from(c).cancel(ID_PROVA);
    }

    /** Se il permesso c'è ma il servizio è stato fermato (es. dal risparmio batteria), lo ricollega. */
    private static final long AVVIO = android.os.SystemClock.elapsedRealtime();
    private static long ultimoRiavvio = 0;

    static void ricollega(Context c) {
        try {
            if (!Coda.permesso(c) || AscoltaNotifiche.attivo != null) return;
            ComponentName cn = new ComponentName(c, AscoltaNotifiche.class);
            NotificationListenerService.requestRebind(cn);
            long ora = android.os.SystemClock.elapsedRealtime();
            // se dopo qualche secondo non si è ancora collegato, spegne e riaccende il servizio:
            // Android allora è obbligato a ricollegarlo (stesso effetto di togliere e rimettere il permesso)
            if (ora - AVVIO > 5000 && ora - ultimoRiavvio > 60000) {
                ultimoRiavvio = ora;
                android.content.pm.PackageManager pm = c.getPackageManager();
                pm.setComponentEnabledSetting(cn, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP);
                pm.setComponentEnabledSetting(cn, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED, android.content.pm.PackageManager.DONT_KILL_APP);
                NotificationListenerService.requestRebind(cn);
            }
        } catch (Exception ignored) {
        }
    }

    static String json(Context c) {
        JSONObject o = new JSONObject();
        try {
            ricollega(c);
            SharedPreferences p = sp(c);
            o.put("permesso", Coda.permesso(c));
            o.put("collegato", AscoltaNotifiche.attivo != null);
            o.put("connesso", p.getLong("connesso", 0));
            o.put("prova", p.getLong("prova", 0));
            o.put("notificheConsentite", NotificationManagerCompat.from(c).areNotificationsEnabled());

            Map<String, Integer> presenti = new HashMap<>();
            AscoltaNotifiche s = AscoltaNotifiche.attivo;
            if (s != null) {
                try {
                    StatusBarNotification[] attive = s.getActiveNotifications();
                    if (attive != null) for (StatusBarNotification sbn : attive) {
                        String f = Fonti.fonte(c, sbn.getPackageName());
                        if (f != null) presenti.put(f, (presenti.containsKey(f) ? presenti.get(f) : 0) + 1);
                    }
                } catch (Exception ignored) {
                }
            }

            List<PackageInfo> installate = c.getPackageManager().getInstalledPackages(0);
            JSONObject regole = new JSONObject(Fonti.leggi(c));
            JSONObject fonti = new JSONObject();
            Iterator<String> it = regole.keys();
            while (it.hasNext()) {
                String fonte = it.next();
                JSONArray trovate = new JSONArray();
                for (PackageInfo pi : installate) {
                    if (fonte.equals(Fonti.fonte(c, pi.packageName))) trovate.put(pi.packageName);
                }
                JSONObject x = new JSONObject();
                x.put("installate", trovate);
                x.put("visto", p.getLong("visto_" + fonte, 0));
                x.put("presenti", presenti.containsKey(fonte) ? presenti.get(fonte) : 0);
                fonti.put(fonte, x);
            }
            o.put("fonti", fonti);
        } catch (Exception ignored) {
        }
        return o.toString();
    }

    /** Manda una notifica di prova: se il servizio la vede, la lettura funziona. */
    static boolean inviaProva(Context c) {
        try {
            NotificationManagerCompat nm = NotificationManagerCompat.from(c);
            if (!nm.areNotificationsEnabled()) return false;
            NotificationManager sistema = c.getSystemService(NotificationManager.class);
            if (sistema != null && sistema.getNotificationChannel("prova") == null) {
                sistema.createNotificationChannel(new NotificationChannel("prova", "Prova della lettura", NotificationManager.IMPORTANCE_LOW));
            }
            Bundle extra = new Bundle();
            extra.putBoolean(EXTRA_PROVA, true);
            nm.notify(ID_PROVA, new NotificationCompat.Builder(c, "prova")
                    .setSmallIcon(R.drawable.ic_notifica)
                    .setContentTitle("Prova NotiPay")
                    .setContentText("Notifica di prova: se l'app la vede, la lettura funziona")
                    .addExtras(extra)
                    .setAutoCancel(true)
                    .build());
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }
}
