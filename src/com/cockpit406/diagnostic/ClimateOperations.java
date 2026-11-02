package com.cockpit406.diagnostic;

import java.io.IOException;
import java.util.Arrays;

/** Fixed CAL5 procedures for Valeo 9630231877. No variable wire commands.
 * Durations come from the actual procedure bodies, not a general OBD routine.
 * ECU acknowledgement formats still require the first physical validation.
 */
public final class ClimateOperations {
    public static final class ReplyException extends IOException {ReplyException(String text){super(text);}}
    public static final class Plan {
        public final String id, title;
        public final long durationMs;
        public final boolean learning;
        private final String start, off;
        private Plan(String id,String title,String start,String off,long durationMs,boolean learning) {
            this.id=id;this.title=title;this.start=start;this.off=off;this.durationMs=durationMs;this.learning=learning;
        }
    }
    public interface Port {
        byte[] exchange(byte[] body,String name) throws IOException;
        boolean usable();
        boolean stopped();
        void pause(long millis) throws IOException;
        void event(String kind,String text) throws IOException;
    }
    private static final Plan[] PLANS={
        new Plan("climate_ac","Commande climatisation", "0501","0500",10000,false),
        new Plan("climate_ac_line","Ligne de commande climatisation", "0601","0600",1000,false),
        new Plan("climate_blower","Pulseur · marche", "043c",null,5000,false),
        new Plan("climate_blower_off","Pulseur · arrêt", "0400",null,5000,false),
        new Plan("climate_mix","Mixage · butée 100 %", "0164",null,10000,false),
        new Plan("climate_mix_cold","Mixage · butée 0 %", "0100",null,10000,false),
        new Plan("climate_distribution","Distribution · butée 100 %", "0264",null,10000,false),
        new Plan("climate_distribution_other","Distribution · butée 0 %", "0200",null,10000,false),
        new Plan("climate_recycle","Recyclage · air extérieur", "0300",null,10000,false),
        new Plan("climate_recycle_inside","Recyclage · air intérieur", "0301",null,10000,false),
        new Plan("climate_learn","Apprentissage des butées de volets", null,null,30000,true)
    };
    public static Plan plan(String id) {
        for(Plan plan:PLANS)if(plan.id.equals(id))return plan;
        return null;
    }
    private static byte[] body(int op,String hex) {
        byte[] data=VciUsbProtocol.unhex(hex),body=new byte[20+data.length];
        body[0]=7;body[1]=(byte)op;body[2]=(byte)data.length;body[4]=(byte)0xaa;
        System.arraycopy(data,0,body,20,data.length);return body;
    }
    private static byte[] command(Port port,String name,int op,String data,int marker) throws IOException {
        byte[] reply=port.exchange(body(op,data),"climate_op:"+name);
        // Intentionally conservative before physical acknowledgement captures:
        // only the one-byte service marker is accepted. Never continue a sequence
        // on an unknown payload; preserve it in the journal for the later trial.
        if(!Arrays.equals(reply,new byte[]{(byte)marker}))
            throw new ReplyException("Réponse de "+name+" à valider : "+VciUsbProtocol.hex(reply));
        return reply;
    }
    private static void checkStop(Port port)throws IOException {
        if(port.stopped())throw new IOException("Arrêt demandé");
    }
    private static void waitFor(Port port,long millis)throws IOException {
        for(long remaining=millis;remaining>0;remaining-=100) {
            checkStop(port);
            port.pause(Math.min(remaining,100));
        }
        checkStop(port);
    }
    /** COMMVEH Buf@get(int*,offset) copies two bytes without swapping on x86.
     * CAL5 _APPRENTISSAGE127 uses offsets 2/4 and these strict ranges.
     */
    public static String learningResult(byte[] group) throws IOException {
        if(group.length!=6||group[0]!=(byte)0x87||group[1]!=1)
            throw new IOException("Mesure des butées non reconnue : "+VciUsbProtocol.hex(group));
        int mix=(group[2]&255)|((group[3]&255)<<8),distribution=(group[4]&255)|((group[5]&255)<<8);
        if(mix<=1370||mix>=1512||distribution<=1977||distribution>=2185)
            throw new ReplyException("Apprentissage non confirmé · mixage "+mix+" (attendu 1371 à 1511), distribution "+distribution+" (attendu 1978 à 2184)");
        return "Butées dans les plages attendues · mixage "+mix+" · distribution "+distribution;
    }
    private static IOException cleanupFailure(IOException earlier,IOException cleanup,String phase) {
        IOException failure=new IOException(phase+" non confirmé : "+cleanup.getMessage(),cleanup);
        if(earlier!=null)failure.addSuppressed(earlier);
        return failure;
    }
    public static String execute(String id,Port port) throws IOException {
        Plan plan=plan(id);if(plan==null)throw new IllegalArgumentException("Procédure climatisation inconnue");
        boolean modeAttempted=false,activationAttempted=false;
        long learningRemaining=0;
        IOException failure=null;String result=null;
        try {
            checkStop(port);
            port.event("procedure_start",plan.title+" · "+(plan.durationMs/1000)+" s");
            checkStop(port);
            port.event("procedure_mode","Passage du calculateur en mode test");
            modeAttempted=true;
            command(port,"sav2",3,"0102",0xf0);
            checkStop(port);
            if(plan.learning) {
                port.event("procedure_activate","Lancement de l’apprentissage des volets");
                // A learning request may already be running even when its
                // acknowledgement or the journal fails. Protect its 30 s interval.
                learningRemaining=plan.durationMs;
                command(port,"learn_mix",0xf,"0d",0x4b);
                checkStop(port);
                command(port,"learn_distribution",0xf,"0e",0x4b);
                port.event("procedure_wait","Apprentissage en cours · attente protégée de 30 s");
                while(learningRemaining>0) {port.pause(100);learningRemaining-=100;}
                checkStop(port);
                result=learningResult(port.exchange(body(0xc,"01"),"climate_op:learning_readback"));
            } else {
                port.event("procedure_activate","Envoi unique de la commande · "+plan.title);
                activationAttempted=true;
                command(port,"activate",0x10,plan.start,0xb4);
                port.event("procedure_wait","Test en cours · attente de "+(plan.durationMs/1000)+" s");
                waitFor(port,plan.durationMs);
                result="Séquence de test terminée · effet physique à contrôler visuellement";
            }
        } catch(IOException e) { failure=e; }
        finally {
            if(learningRemaining>0) {
                // No read/exit request while an interrupted learning may be busy.
                try { while(learningRemaining>0) {port.pause(100);learningRemaining-=100;} }
                catch(IOException e) { failure=cleanupFailure(failure,e,"Délai de fin d’apprentissage"); }
            }
            // Cleanup also runs after Stop, unknown acknowledgement or logging
            // failure. No retry of an activation, no writes after lost framing.
            if(modeAttempted && port.usable()) {
                // A progress/log failure cannot bypass an actuator stop or SAV1.
                try{port.event("procedure_cleanup","Fin de test · arrêt et retour au mode normal");}
                catch(IOException e){if(failure==null)failure=e;else failure.addSuppressed(e);}
                if(activationAttempted && plan.off!=null) {
                    try { command(port,"deactivate",0x10,plan.off,0xb4); }
                    catch(IOException e) { failure=cleanupFailure(failure,e,"Arrêt de la commande"); }
                }
                if(port.usable())try { command(port,"sav1",3,"0101",0xf0); }
                catch(IOException e) { failure=cleanupFailure(failure,e,"Retour au mode normal"); }
            }
        }
        if(failure!=null)throw failure;
        checkStop(port);
        return result;
    }
    private ClimateOperations() {}
}
