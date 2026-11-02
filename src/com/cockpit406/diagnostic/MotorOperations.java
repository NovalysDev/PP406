package com.cockpit406.diagnostic;

import java.io.IOException;

/** Five fixed MP7.0 commands. Injection and ignition intentionally have no dispatch. */
public final class MotorOperations {
    /** An ECU payload mismatch, distinct from a lost/invalid transport. */
    public static final class ReplyException extends IOException {
        ReplyException(String text){super(text);}
    }
    public static final class Plan {
        public final String id,title;
        public final long durationMs=5000;
        private final int selector;
        Plan(String id,String title,int selector){this.id=id;this.title=title;this.selector=selector;}
    }
    public static Plan plan(String id) {
        switch(id) {
            case "oxygen_heater":return new Plan(id,"Chauffage de sonde à oxygène",0x21);
            case "fuel_pump":return new Plan(id,"Pompe à essence",0x23);
            case "tach_signal":return new Plan(id,"Signal compte-tours",0x32);
            case "idle_actuator":return new Plan(id,"Commande de ralenti",0x41);
            case "canister":return new Plan(id,"Purge canister",0x61);
            default:return null;
        }
    }
    public interface Port {
        byte[] exchange(byte[] body,String name)throws IOException;
        boolean usable(); boolean stopped();
        void pause(long millis)throws IOException;
        void event(String kind,String text)throws IOException;
    }
    private static byte[] body(int op,int selector) {
        byte[] b=new byte[op==8?21:20];b[0]=0x2a;b[1]=(byte)op;b[2]=(byte)(b.length-20);b[4]=(byte)0xaa;
        if(b.length>20)b[20]=(byte)selector;return b;
    }
    private static void ack(byte[] data,int expected,String what)throws IOException {
        if(data.length!=1||(data[0]&255)!=expected)throw new IOException(what+" non confirmé : "+VciUsbProtocol.hex(data));
    }
    private static void activationAck(byte[] data,Plan plan)throws IOException {
        // Captured on this vehicle: B4 32 (tachometer), B4 61 (canister).
        // Runtime D2A08 receives the selector echo. Never accept another selector.
        if(data.length!=2||(data[0]&255)!=0xb4||(data[1]&255)!=plan.selector)
            throw new ReplyException("Réponse d’activation non confirmée : "+VciUsbProtocol.hex(data));
    }
    public static void execute(String id,Port port)throws IOException {
        Plan plan=plan(id);if(plan==null)throw new IOException("Test moteur non pris en charge");
        if(port.stopped())throw new IOException("Arrêt demandé");
        port.event("procedure_start","Envoi unique · "+plan.title+" · fenêtre de test de 5 s avant demande d’arrêt");
        boolean attempted=false;IOException failure=null;
        try {
            if(port.stopped())throw new IOException("Arrêt demandé");
            attempted=true;
            activationAck(port.exchange(body(8,plan.selector),"motor_activate:"+id),plan);
            port.event("procedure_wait","Activation confirmée · attente logicielle maximale de 5 s avant l’arrêt");
            for(long remaining=plan.durationMs;remaining>0&&!port.stopped();remaining-=100)
                port.pause(Math.min(remaining,100));
        }catch(IOException e){failure=e;}
        finally {
            if(attempted&&port.usable()) {
                try{port.event("procedure_cleanup","Arrêt de l’actionneur · attente de l’accusé");}
                catch(IOException e){if(failure==null)failure=e;else failure.addSuppressed(e);}
                try{ack(port.exchange(body(9,0),"motor_activation_stop"),0xe1,"Arrêt de l’activation");}
                catch(IOException e){IOException cleanup=new IOException("Arrêt de l’actionneur non confirmé · conserver le journal et ne pas relancer",e);if(failure!=null)cleanup.addSuppressed(failure);failure=cleanup;}
            }
        }
        if(failure!=null)throw failure;
        if(port.stopped())throw new IOException("Arrêt demandé");
    }
    private MotorOperations() {}
}
