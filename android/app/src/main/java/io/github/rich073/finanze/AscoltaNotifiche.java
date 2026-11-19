package io.github.rich073.finanze;

import android.app.Notification;
import android.content.Context;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/** Legge le notifiche di Wallet, banca, SMS della banca e PayPal e le mette in coda. */
public class AscoltaNotifiche extends NotificationListenerService {

    static volatile AscoltaNotifiche attivo;

    @Override
    public void onListenerConnected() {
        attivo = this;
        Diagnosi.connesso(this);
        recupera(this);
    }

    @Override
    public void onListenerDisconnected() {
        attivo = null;
        Diagnosi.ricollega(this);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (elabora(this, sbn, true)) {
            if (!MainActivity.avvisaSeAperta()) Coda.avviso(this);
        }
    }

    /**
     * Ripesca i pagamenti ancora nella barra delle notifiche: se il telefono ha fermato
     * la lettura per un po', quelli arrivati nel frattempo non si perdono.
     */
    static void recupera(Context c) {
        AscoltaNotifiche s = attivo;
        if (s == null) return;
        boolean nuovi = false;
        try {
            StatusBarNotification[] tutte = s.getActiveNotifications();
            if (tutte != null) for (StatusBarNotification sbn : tutte) {
                if (elabora(c, sbn, false)) nuovi = true;
            }
        } catch (Exception ignored) {
        }
        if (nuovi && !MainActivity.avvisaSeAperta()) Coda.avviso(c);
    }

    /** Mette in coda la notifica se viene da una delle app dei pagamenti. */
    static boolean elabora(Context c, StatusBarNotification sbn, boolean nuova) {
        try {
            String pacchetto = sbn.getPackageName();
            if (pacchetto == null) return false;
            Notification n = sbn.getNotification();
            if (pacchetto.equals(c.getPackageName())) {
                if (nuova && n != null && n.extras != null && n.extras.getBoolean(Diagnosi.EXTRA_PROVA)) Diagnosi.prova(c);
                return false;
            }
            String fonte = Fonti.fonte(c, pacchetto);
            if (fonte == null) return false;
            if (nuova) Diagnosi.visto(c, fonte);
            if (n == null || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return false;
            Bundle ex = n.extras;
            if (ex == null) return false;
            String titolo = testo(ex.getCharSequence(Notification.EXTRA_TITLE));
            String breve = testo(ex.getCharSequence(Notification.EXTRA_TEXT));
            String lungo = testo(ex.getCharSequence(Notification.EXTRA_BIG_TEXT));
            String corpo = lungo.length() > breve.length() ? lungo : breve;
            if (titolo.isEmpty() && corpo.isEmpty()) return false;
            if ("sms".equals(fonte) && !Fonti.smsDellaBanca(c, titolo + " " + corpo)) return false;
            String chiave = sbn.getKey() + "@" + sbn.getPostTime();
            return Coda.aggiungi(c, fonte, titolo, corpo, sbn.getPostTime(), chiave);
        } catch (Exception e) {
            return false;
        }
    }

    private static String testo(CharSequence cs) {
        return cs == null ? "" : cs.toString().trim();
    }
}
