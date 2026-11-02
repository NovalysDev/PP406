package com.cockpit406.diagnostic;

import java.util.*;

/** In-memory copies of measurements already read. This class has no USB or file access. */
public final class DiagnosticDataHub {
    private DiagnosticDataHub(){}
    public static final class Signal {
        public final String key,label,unit;public final double value;public final long elapsed;
        Signal(String key,String label,String unit,double value,long elapsed){this.key=key;this.label=label;this.unit=unit;this.value=value;this.elapsed=elapsed;}
        public boolean fresh(long now,boolean reading){return reading&&elapsed>=0&&elapsed<=now&&now-elapsed<3000;}
    }
    public static final class Snapshot {
        public final String vehicle,ecu,reference,note;public final boolean reading;
        public final List<Signal> signals;
        Snapshot(String vehicle,String ecu,String reference,String note,boolean reading,List<Signal> signals){
            this.vehicle=vehicle;this.ecu=ecu;this.reference=reference;this.note=note;this.reading=reading;this.signals=Collections.unmodifiableList(new ArrayList<>(signals));
        }
        public boolean live(long now){for(Signal s:signals)if(s.fresh(now,reading))return true;return false;}
    }
    private static Object owner;
    private static Snapshot current=new Snapshot("","","","Aucune mesure reçue depuis le démarrage de PP406",false,Collections.emptyList());
    public static synchronized void publish(Object source,String vehicle,String ecu,String reference,String[][] definitions,
            Map<String,Double> values,Map<String,Long> times,boolean reading,long now,String note){
        if(source==null)throw new IllegalArgumentException("Missing publisher");
        List<Signal> signals=new ArrayList<>();
        for(String[] definition:definitions){
            Double value=values.get(definition[0]);Long stamp=times.get(definition[0]);
            if(value!=null&&!Double.isNaN(value)&&!Double.isInfinite(value)&&stamp!=null&&stamp>=0&&stamp<=now)
                signals.add(new Signal(definition[0],definition[1],definition[2],value,stamp));
        }
        // A paused screen cannot overwrite a newer screen's measurements.
        if(source!=owner){
            boolean fresh=false;for(Signal s:signals)fresh|=s.fresh(now,reading);
            if(!fresh)return;owner=source;
        }
        current=new Snapshot(vehicle,ecu,reference,note,reading,signals);
    }
    public static synchronized void inactive(Object source){
        if(source==owner)current=new Snapshot(current.vehicle,current.ecu,current.reference,current.note,false,current.signals);
    }
    public static synchronized Snapshot snapshot(){return current;}
}
