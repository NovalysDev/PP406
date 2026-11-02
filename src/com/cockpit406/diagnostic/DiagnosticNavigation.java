package com.cockpit406.diagnostic;

import java.util.ArrayDeque;

/** Navigation changes views only; it never starts or resets a diagnostic link. */
public final class DiagnosticNavigation {
    public static final class Page {
        public final String module; public final int tab;
        Page(String module,int tab){this.module=module;this.tab=tab;}
    }
    private final ArrayDeque<Page> history=new ArrayDeque<>();
    private Page current=new Page("motor",0);
    public void visit(String module,int tab) {
        if(current.module.equals(module)&&current.tab==tab)return;
        history.push(current);if(history.size()>80)history.removeLast();
        current=new Page(module,tab);
    }
    public Page back(){if(history.isEmpty())return null;current=history.pop();return current;}
    public boolean hasPrevious(){return !history.isEmpty();}
    public static boolean offerVehicleChoice(boolean engineSearchFailed,boolean engineIdentified,boolean cancelled) {
        return engineSearchFailed&&!engineIdentified&&!cancelled;
    }
}
