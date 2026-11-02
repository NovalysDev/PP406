package com.cockpit406.diagnostic;

import java.io.IOException;

/** Monotonic, worker-only scheduling. Long reads never cause a burst of catch-up requests. */
public final class PeriodicRead implements Mp70Session.AuxiliaryRead {
    public interface Clock {long millis();}
    public interface Task {boolean read()throws IOException;}
    private final long interval;
    private final Clock clock;
    private final Task task;
    private long next;
    private boolean enabled=true;
    public PeriodicRead(long interval,Clock clock,Task task){
        this(interval,interval,clock,task);
    }
    public PeriodicRead(long interval,long initialDelay,Clock clock,Task task){
        if(interval<1000)throw new IllegalArgumentException("Interval");
        if(initialDelay<0||initialDelay>interval)throw new IllegalArgumentException("Initial delay");
        this.interval=interval;this.clock=clock;this.task=task;next=clock.millis()+initialDelay;
    }
    public boolean due(){return enabled&&clock.millis()>=next;}
    public void read()throws IOException{
        if(!due())return;
        long started=clock.millis();enabled=false;
        boolean again=task.read();long ended=clock.millis();
        next=ended>=started+interval?ended+interval:started+interval;enabled=again;
    }
}
