package com.cockpit406.diagnostic;
import android.app.job.*;
public final class TripBackupJob extends JobService {
    @Override public boolean onStartJob(JobParameters parameters){TripBackups.kick(this,null,()->jobFinished(parameters,false));return true;}
    @Override public boolean onStopJob(JobParameters parameters){return true;}
}
