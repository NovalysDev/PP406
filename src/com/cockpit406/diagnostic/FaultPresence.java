package com.cockpit406.diagnostic;

import java.util.*;

/** A complete fault list is required to establish absence. No timers or I/O here. */
public final class FaultPresence {
    public static final class Change {
        public final boolean baseline;
        public final Set<String> present,appeared;
        Change(boolean baseline,Set<String> present,Set<String> appeared){this.baseline=baseline;this.present=Collections.unmodifiableSet(present);this.appeared=Collections.unmodifiableSet(appeared);}
    }
    public static Change compare(Set<String> previous,Collection<String> current,boolean complete){
        Set<String> present=new LinkedHashSet<>();for(String code:current)if(code!=null&&!code.trim().isEmpty())present.add(code.toUpperCase(Locale.ROOT));
        Set<String> appeared=new LinkedHashSet<>();
        if(complete&&previous!=null){appeared.addAll(present);for(String code:previous)appeared.remove(code.toUpperCase(Locale.ROOT));}
        return new Change(previous==null,present,appeared);
    }
    private FaultPresence(){}
}
