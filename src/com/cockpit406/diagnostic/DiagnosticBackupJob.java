package com.cockpit406.diagnostic;
import android.app.job.*;
public final class DiagnosticBackupJob extends JobService {
    @Override public boolean onStartJob(JobParameters parameters){DiagnosticBackups.kick(this,()->jobFinished(parameters,DiagnosticBackups.hasPendingFeedback(this)));return true;}
    @Override public boolean onStopJob(JobParameters parameters){return true;}
}
