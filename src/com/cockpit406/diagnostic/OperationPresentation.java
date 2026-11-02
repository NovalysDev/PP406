package com.cockpit406.diagnostic;

import java.util.HashSet;
import java.util.Set;

/** UI-only state. Hiding a result never changes ECU evidence or sends a command. */
public final class OperationPresentation {
    private final Set<String> hiddenFaults=new HashSet<>();
    public String module,title,message;
    public boolean open,finished;
    private long timedStart,timedDuration;
    public void begin(String module,String title,String message) {
        if(open)throw new IllegalStateException("Valider le résultat précédent");
        this.module=module;this.title=title;this.message=message;
        open=true;finished=false;timedDuration=0;
    }
    public void phase(String message) {if(!open||finished)return;this.message=message;timedDuration=0;}
    public void timed(String message,long now,long duration) {phase(message);if(open&&!finished){timedStart=now;timedDuration=duration;}}
    public void finish(String message) {if(!open)return;this.message=message;finished=true;timedDuration=0;}
    public boolean determinate(){return finished||timedDuration>0;}
    public int progress(long now) {return finished?100:timedDuration==0?0:(int)Math.max(0,Math.min(99,(now-timedStart)*100/timedDuration));}
    public boolean mayAcknowledge(boolean coreBusy,boolean closing){return open&&finished&&!coreBusy&&!closing;}
    public void acknowledge(boolean coreBusy,boolean closing) {
        if(!mayAcknowledge(coreBusy,closing))throw new IllegalStateException("Opération pas encore terminée");
        hiddenFaults.add(module);open=false;
    }
    public void scan(String module){hiddenFaults.remove(module);}
    public boolean faultsHidden(String module){return hiddenFaults.contains(module);}
    public static boolean terminal(String kind) {
        return kind.equals("clear_result")||kind.equals("clear_rejected")||kind.equals("clear_unknown")||kind.equals("clear_unverified")
            ||kind.equals("faults_read")||kind.equals("faults_unavailable")||kind.equals("procedure_result")
            ||kind.equals("procedure_rejected")||kind.equals("procedure_unverified")||kind.equals("procedure_cancelled");
    }
}
