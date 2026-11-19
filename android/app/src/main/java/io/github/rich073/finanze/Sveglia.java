package io.github.rich073.finanze;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/** Ogni 15 minuti controlla che la lettura delle notifiche sia attiva e, se serve, la ricollega. */
public class Sveglia extends JobService {
    private static final int ID = 7303;

    static void programma(Context c) {
        try {
            JobScheduler js = c.getSystemService(JobScheduler.class);
            if (js == null || js.getPendingJob(ID) != null) return;
            js.schedule(new JobInfo.Builder(ID, new ComponentName(c, Sveglia.class))
                    .setPeriodic(15 * 60 * 1000L)
                    .setPersisted(true)
                    .build());
        } catch (Exception ignored) {
        }
    }

    @Override
    public boolean onStartJob(JobParameters p) {
        Diagnosi.controllaEAvvisa(this);
        Diagnosi.ricollega(this);
        AscoltaNotifiche.recupera(this);
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters p) {
        return true;
    }
}
