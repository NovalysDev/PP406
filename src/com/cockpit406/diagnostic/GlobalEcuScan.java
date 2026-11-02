package com.cockpit406.diagnostic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/** One read-only discovery pass. No ECU failure is translated to equipment absent. */
public final class GlobalEcuScan {
    public enum State { WAITING, SEARCHING, IDENTIFIED, READABLE, RESPONSE, NO_RESPONSE, SKIPPED, INTERRUPTED }
    public static final class Row {
        public final String profile,module,title;
        public State state=State.WAITING;
        public String detail="En attente";
        public Row(String profile,String module,String title){this.profile=profile;this.module=module;this.title=title;}
        public String label(){
            String icon=state==State.IDENTIFIED?"✓":state==State.RESPONSE||state==State.READABLE?"⚠":state==State.SEARCHING?"…":
                state==State.NO_RESPONSE?"✕":state==State.SKIPPED?"—":"○";
            return icon+" "+title+" · "+detail;
        }
    }
    public static final class Result {
        public final State state;public final String detail;public final boolean abort;
        public Result(State state,String detail,boolean abort){this.state=state;this.detail=detail;this.abort=abort;}
    }
    public interface Runner {Result probe(String profile)throws IOException;boolean stopped();}
    public interface Listener {void update(List<Row> rows,int complete,String current)throws IOException;}
    public enum Decision { CONTINUE, SELECT_VEHICLE, STOP }
    public interface EngineFailure {Decision choose(Row engine)throws IOException;}
    /** A user choice blocks discovery, never the Android UI or an ECU exchange. */
    public static final class DecisionGate {
        private Decision decision;
        public synchronized boolean pending(){return decision==null;}
        public synchronized boolean choose(Decision choice){
            if(choice==null)throw new IllegalArgumentException("Missing choice");
            if(decision!=null)return false;
            decision=choice;notifyAll();return true;
        }
        public synchronized Decision await()throws InterruptedException {
            while(decision==null)wait();return decision;
        }
    }
    /** Kept across the vehicle picker: resuming starts at the next unprobed row. */
    public static final class Pass {
        private final List<Row> rows;
        private final ScanEquipment equipment;
        private final Set<String> identified=new HashSet<>();
        private int next,complete;
        private boolean abort,paused,finished;
        public Pass(){this(GlobalEcuScan.rows());}
        public Pass(List<Row> plan){this(plan,ScanEquipment.searchAll());}
        public Pass(List<Row> plan,ScanEquipment equipment){this.equipment=equipment;rows=new ArrayList<>();for(Row r:plan)rows.add(new Row(r.profile,r.module,r.title));}
        public boolean paused(){return paused;}
        public int complete(){return complete;}
    }
    public static List<Row> rows(){
        List<Row> r=new ArrayList<>();
        r.add(new Row("motor","motor","Moteur Bosch MP7.0"));
        r.add(new Row("airbag","airbag","Airbags"));r.add(new Row("steering","steering","Direction assistée"));
        r.add(new Row("climate","climate","Climatisation"));r.add(new Row("keys","keys","Antidémarrage"));
        r.add(new Row("abs53","abs","ABS Bosch 5.3"));r.add(new Row("abs5","abs","ABS Bosch 5"));
        r.add(new Row("seat","seat","Siège à mémoire"));r.add(new Row("suspension","suspension","Suspension"));
        r.add(new Row("auto4hp20","automatic","Boîte ZF 4HP20"));r.add(new Row("autoal4","automatic","Boîte AL4"));
        // Its available finish command aliases erasure: never send it. Last
        // probe, with contact-cycle instruction before any subsequent session.
        r.add(new Row("locking","locking","Verrouillage Kiekert"));return r;
    }
    public static List<Row> run(Runner runner,Listener listener)throws IOException {
        return run(new Pass(),runner,listener,engine->Decision.CONTINUE);
    }
    public static List<Row> run(Pass pass,Runner runner,Listener listener,EngineFailure engineFailure)throws IOException {
        if(pass.finished)throw new IllegalStateException("Scan already finished");
        pass.paused=false;
        List<Row> rows=pass.rows;
        listener.update(rows,pass.complete,pass.next==0?"Préparation de la recherche":"Reprise de la recherche des autres calculateurs");
        while(pass.next<rows.size()) {
            Row row=rows.get(pass.next++);
            if(pass.abort||runner.stopped()){row.state=State.INTERRUPTED;row.detail="Non recherché · scan interrompu";continue;}
            if(!pass.equipment.searches(row.module)) {row.state=State.SKIPPED;row.detail="Non recherché · déclaré non équipé";pass.complete++;listener.update(rows,pass.complete,row.title);continue;}
            if(pass.identified.contains(row.module)) {row.state=State.SKIPPED;row.detail="Autre variante identifiée";pass.complete++;listener.update(rows,pass.complete,row.title);continue;}
            row.state=State.SEARCHING;row.detail="Recherche…";listener.update(rows,pass.complete,row.title);
            Result result=runner.probe(row.profile);
            row.state=result.state;row.detail=result.detail;pass.abort=result.abort;
            if(row.state==State.IDENTIFIED||row.state==State.READABLE)pass.identified.add(row.module);
            pass.complete++;listener.update(rows,pass.complete,row.title);
            // Prompt here, before airbag or any other ECU. A lost transport must
            // finish cleanup instead of offering to send more commands on it.
            if(row.module.equals("motor")&&row.state!=State.IDENTIFIED&&row.state!=State.READABLE&&!pass.abort&&!runner.stopped()) {
                listener.update(rows,pass.complete,"Moteur non reconnu · choix requis avant de poursuivre");
                Decision choice=engineFailure.choose(row);
                if(choice==Decision.STOP||runner.stopped())pass.abort=true;
                else if(choice==Decision.SELECT_VEHICLE){
                    pass.paused=true;
                    listener.update(rows,pass.complete,"Recherche en pause · choix du véhicule");return rows;
                }
            }
        }
        pass.finished=true;
        listener.update(rows,pass.complete,pass.abort||runner.stopped()?"Recherche interrompue":"Recherche terminée");return rows;
    }
    public static String text(List<Row> rows){StringBuilder s=new StringBuilder();for(Row r:rows)s.append(r.label()).append('\n');return s.toString().trim();}
    private GlobalEcuScan(){}
}
