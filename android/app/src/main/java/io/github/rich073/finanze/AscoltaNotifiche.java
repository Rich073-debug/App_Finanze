package io.github.rich073.finanze;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/** Legge le notifiche di Wallet, banca, SMS della banca e PayPal e le mette in coda. */
public class AscoltaNotifiche extends NotificationListenerService {

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            String pacchetto = sbn.getPackageName();
            if (pacchetto == null || pacchetto.equals(getPackageName())) return;
            String fonte = Fonti.fonte(this, pacchetto);
            if (fonte == null) return;
            Notification n = sbn.getNotification();
            if (n == null || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
            Bundle ex = n.extras;
            if (ex == null) return;
            String titolo = testo(ex.getCharSequence(Notification.EXTRA_TITLE));
            String breve = testo(ex.getCharSequence(Notification.EXTRA_TEXT));
            String lungo = testo(ex.getCharSequence(Notification.EXTRA_BIG_TEXT));
            String corpo = lungo.length() > breve.length() ? lungo : breve;
            if (titolo.isEmpty() && corpo.isEmpty()) return;
            if ("sms".equals(fonte) && !Fonti.smsDellaBanca(this, titolo + " " + corpo)) return;
            if (Coda.aggiungi(this, fonte, titolo, corpo, sbn.getPostTime())) {
                if (!MainActivity.avvisaSeAperta()) Coda.avviso(this);
            }
        } catch (Exception ignored) {
        }
    }

    private static String testo(CharSequence cs) {
        return cs == null ? "" : cs.toString().trim();
    }
}
