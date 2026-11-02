package com.cockpit406.diagnostic;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Fixed diagnostic dispatch. Mutations require a one-use confirmation.
 * No variable addresses, coding, factory reset or key-access commands.
 */
public final class OtherEcuSession {
    public interface Listener {
        void record(String name,byte[] request,byte[] response,int status,byte[] data) throws IOException;
        void state(String text);
        void identified(String reference,String raw);
        default void identityObserved(String reference,String raw) {}
        void values(String text);
        default void measurements(Map<String,Double> values) {}
        void faults(String text);
        default void faultsUnavailable(String text) {}
        default void operation(String kind,String text) throws IOException {}
        default void procedure(String id,String kind,String text)throws IOException {operation(kind,text);}
    }
    public interface Waiter { void pause(long millis) throws IOException; }
    public static final class Profile {
        public final String module,reference,couplerSha,programSha;
        public final int family,idLength,referenceOffset;
        public boolean discovery,kwp;
        public String title;
        public int initLength;
        final Map<String,byte[]> commands=new LinkedHashMap<>();
        Profile(String module,String reference,int family,int idLength,int referenceOffset,String couplerSha,String programSha) {
            this.module=module;this.reference=reference;this.family=family;this.idLength=idLength;this.referenceOffset=referenceOffset;
            this.couplerSha=couplerSha;this.programSha=programSha;
        }
        Profile read(String name,int op,String data) { commands.put(name,makeBody(family,op,VciUsbProtocol.unhex(data))); return this; }
        Profile discovery(String title,int length,boolean kwp) {discovery=true;this.title=title;initLength=length;this.kwp=kwp;return this;}
        Profile readFamily(String name,int family,int op,String data) {commands.put(name,makeBody(family,op,VciUsbProtocol.unhex(data)));return this;}
    }
    public static Profile profile(String module) {
        switch(module) {
            case "airbag": return new Profile(module,"9628757180",4,17,2,
                "76d19d5c379bf3fc832e2ff6c7a80b087d70a6bc653d789e815fe9e9d6924722","8c5589ece38e8b39ffaf6f079376a65df548d8132ec0f5ed2a9339fb8bba7068")
                .read("init",1,"b3").read("identify",8,"00").read("faults",4,"").read("finish",2,"");
            case "climate": return new Profile(module,"9630231877",7,28,1,
                "d71f9f04bfb00048742c3bb6d22be1cdcac236ed6f1271d1a4801ec3b69daa02","47650447d16e539fcfe0cb41953735868a314efb822dfac6e6609344b080795d")
                .read("init",1,"31").read("identify",0x13,"").read("faults",9,"").read("faults_next",0xb,"")
                .read("group1",7,"01").read("group2",7,"02").read("group3",0xc,"01").read("finish",6,"");
            case "steering": return new Profile(module,"9629846780",0xd,28,8,
                "c43b8baa25c757ee20d3b418536cd89e90d2272af5997191aa4968ff58381fcc","ea8c2aff3675a2030534e19d44699cd9b4e7d2d6f6b7c3025f194006f3691f53")
                .read("init",1,"").read("identify",5,"").read("faults",6,"").read("finish",3,"");
            case "keys": return new Profile(module,"9624799080",0x37,21,2,
                "e04f668245d80d0a0381cf49ff7a52237828a4232910b73d20b13f16fe26d538","c5e402c2fea13c8079eaebbca2c76e9d0faba4bb076388dc11c3b2789ccf3482")
                .read("init",1,"").read("identify",0xa,"00").read("faults",9,"").read("group1",4,"01").read("group2",4,"02").read("finish",2,"");
            default: return ExtendedProfiles.get(module);
        }
    }
    /** Read identity independently repeated in the owner's two 0.8.2 captures.
     * This does not authorize writes or validate physical-value conversions. */
    public static String confirmedReadReference(String profile,String raw) {
        if(!profile.equals("abs53")||raw==null)return null;
        byte[] id=VciUsbProtocol.unhex(raw);
        return id.length==21&&id[0]==(byte)0xee&&id[1]==0
            &&VciUsbProtocol.hex(Arrays.copyOfRange(id,2,7)).equals("9625275080")
            &&id[10]==(byte)0xab&&id[11]==0x53?"9625275080":null;
    }
    private final Profile profile;
    private final Mp70Session.Transport transport;
    private final Listener listener;
    private final Waiter waiter;
    private final Map<String,byte[]> commands=new LinkedHashMap<>();
    private final Map<String,String> groups=new LinkedHashMap<>();
    private volatile boolean stopped,ready,faultRead,readingFaults,clearRequested,faultsVerified;
    private volatile String procedureRequested;
    private volatile boolean procedureRunning;
    private volatile boolean procedureCancel;
    private boolean initialized,usable=true,eraseArmed;
    private byte[] discoveryIdentity;
    private int identityChecks;
    private boolean lastFaultsEmpty;
    private final LexiaKwp.Contract kwpContract;
    private boolean closed=true;
    public boolean mayTryAnotherProfile(){return usable&&closed;}
    public OtherEcuSession(String module,Mp70Session.Transport transport,Listener listener,byte[] coupler,byte[] program) {
        this(module,transport,listener,coupler,program,OtherEcuSession::pause);
    }
    public OtherEcuSession(String module,Mp70Session.Transport transport,Listener listener,byte[] coupler,byte[] program,Waiter waiter) {
        profile=profile(module);if(profile==null) throw new IllegalArgumentException("Module non pris en charge");
        check(coupler,profile.couplerSha);check(program,profile.programSha);
        this.transport=transport;this.listener=listener;this.waiter=waiter;commands.putAll(profile.commands);
        kwpContract=profile.kwp?LexiaKwp.fromProgram(program,commands.get("init"),commands.get("identify")):null;
        commands.put("serial",makeBody(0,9,VciUsbProtocol.unhex("1606")));commands.put("version",makeBody(0,0xfa,new byte[0]));
        commands.put("reference",makeBody(0,9,VciUsbProtocol.unhex("060a")));commands.put("board_init",makeBody(0,7,new byte[0]));
        commands.put("board_version",makeBody(0,0x12,new byte[0]));commands.put("supply_read",makeBody(0,0xb,new byte[]{3}));
        commands.put("coupler",makeBody(0,5,coupler));commands.put("program",makeBody(0,4,program));
    }
    private static void check(byte[] bytes,String expected) {
        try {
            if(!VciUsbProtocol.hex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(expected)) throw new IllegalArgumentException("Définition de communication modifiée");
        } catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public synchronized void stop() { stopped=true;faultRead=false;clearRequested=false;procedureRequested=null;faultsVerified=false; }
    public boolean isReady() { return ready&&!stopped; }
    public boolean isFaultReadPending() { return faultRead || readingFaults || clearRequested || procedureRequested!=null || procedureRunning; }
    public boolean hasVerifiedFaults() { return isReady() && faultsVerified; }
    public static boolean supportsClear(String module) { return module.equals("climate")||module.equals("steering")||module.equals("airbag")||OtherEcuErasure.plan(module)!=null; }
    public synchronized boolean queueFaultRead() { if(!isReady() || isFaultReadPending())return false;faultRead=true;return true; }
    public synchronized boolean queueConfirmedClear() {
        if(!supportsClear(profile.module)||!hasVerifiedFaults()||isFaultReadPending())return false;
        clearRequested=true;return true;
    }
    public synchronized boolean queueConfirmedProcedure(String id) {
        if(!profile.module.equals("climate")||ClimateOperations.plan(id)==null||!hasVerifiedFaults()||isFaultReadPending())return false;
        procedureCancel=false;procedureRequested=id;return true;
    }
    public synchronized boolean cancelProcedure(){if(procedureRequested==null&&!procedureRunning)return false;procedureCancel=true;return true;}
    public boolean isProcedurePending(){return procedureRequested!=null||procedureRunning;}
    public synchronized byte[] body(String name) {
        if(name.equals("erase")) {
            if(!eraseArmed || !isReady() || !supportsClear(profile.module)) throw new IllegalArgumentException("Effacement sans confirmation active");
            OtherEcuErasure.Plan plan=OtherEcuErasure.plan(profile.module);
            if(plan!=null) {
                if(!OtherEcuErasure.identified(discoveryIdentity,identityChecks,profile.kwp))throw new IllegalArgumentException("Identifications concordantes requises avant effacement");
                return makeBody(plan.family,plan.operation,VciUsbProtocol.unhex(plan.data));
            }
            int op=profile.module.equals("climate")?4:profile.module.equals("airbag")?7:2;
            return makeBody(profile.family,op,new byte[0]);
        }
        byte[] value=commands.get(name); if(value==null) throw new IllegalArgumentException("Commande hors lecture autorisée : "+name);
        return value.clone();
    }
    private static byte[] makeBody(int family,int op,byte[] data) {
        byte[] body=new byte[20+data.length];body[0]=(byte)family;body[1]=(byte)op;body[2]=(byte)data.length;body[3]=(byte)(data.length>>>8);body[4]=(byte)0xaa;
        System.arraycopy(data,0,body,20,data.length);return body;
    }
    private byte[] request(String name,boolean cold) throws IOException {
        byte[] request,response;
        synchronized(this) {
            if(name.equals("erase")&&stopped)throw new IOException("Arrêt demandé");
            request=body(name);
            if(name.equals("erase"))eraseArmed=false; // consume before USB, no automatic retry
        }
        return exchange(name,request,cold);
    }
    private byte[] exchange(String name,byte[] request,boolean cold)throws IOException {
        byte[] response;
        try { response=transport.exchange(request); }
        catch(IOException e) {usable=false;throw e;}
        if(response.length<20 || response[10]!=request[0] || response[11]!=request[1] || (response[15]&255)!=0xaa
            || ((response[12]&255)|((response[13]&255)<<8))!=response.length-20) {
            usable=false;throw new IOException("Réponse USB non reconnue : "+name);
        }
        byte[] data=Arrays.copyOfRange(response,20,response.length);int status=response[14]&255;
        boolean acceptedCold=cold&&name.equals("board_init")&&status==12&&data.length==0;
        boolean refusedInit=name.equals("init")&&data.length==0&&(status==129||status==212||status==218);
        boolean refusedSnapshot=snapshotCancelled!=null&&profile.module.equals("climate")&&name.equals("group1")&&data.length==0&&(status==212||status==218);
        int expected=name.equals("board_init")||name.equals("board_version")||name.equals("coupler")?3:1;
        if(!acceptedCold&&!refusedInit&&!refusedSnapshot&&status!=expected)usable=false;
        listener.record(name,request,response,status,data);
        if(acceptedCold)return data;
        if(status!=expected) {if(refusedInit){initialized=false;closed=true;}else if(!refusedSnapshot)usable=false;throw new IOException(name+" : état Lexia "+status);}
        if(name.equals("program")&&(data.length!=1||(data[0]&255)!=(commands.get("program").length-20)/45))throw new IOException("Table de communication refusée");
        if(name.equals("board_version")&&!FirmwareIdentity.matches(data))throw new IOException(FirmwareIdentity.ERROR);
        if(kwpContract!=null&&request[0]!=0)data=kwpContract.payload(data,request);
        if(name.equals("finish")) {
            if(!profile.discovery&&!Arrays.equals(data,new byte[]{0x0f}))throw new IOException("Fin de session non confirmée");
            if(profile.discovery) {
                if(profile.kwp) {if(data.length!=1||(data[0]&255)!=0xc2)throw new IOException("Fin de session KWP non confirmée : "+VciUsbProtocol.hex(data));}
                else if(!profile.module.equals("locking")&&!Arrays.equals(data,new byte[]{0x0f}))throw new IOException("Fin de session non confirmée : "+VciUsbProtocol.hex(data));
                else if(profile.module.equals("locking"))listener.state("Fermeture Kiekert envoyée · réponse conservée dans le journal, contrôle physique à faire");
            }
        }
        if(name.equals("stop_demand")&&!Arrays.equals(data,new byte[]{(byte)0xe1})){
            if(profile.module.equals("seat183"))usable=false;
            throw new IOException("Arrêt de demande non confirmé : "+VciUsbProtocol.hex(data));
        }
        if(name.equals("erase")) {
            OtherEcuErasure.Plan plan=OtherEcuErasure.plan(profile.module);
            if(!Arrays.equals(data,VciUsbProtocol.unhex(plan==null?"c3":plan.ack)))throw new ErasureReplyException("Accusé d’effacement non reconnu : "+VciUsbProtocol.hex(data));
        }
        return data;
    }
    private static final class ErasureReplyException extends IOException {ErasureReplyException(String text){super(text);}}
    private void identity(byte[] data) throws IOException {
        if(profile.discovery) {
            if(profile.kwp) {
                if(data.length<3||data.length>128||data[0]!=0x61||data[1]!=(byte)0x80)throw new IOException("Identification AL4 non reconnue : "+VciUsbProtocol.hex(data));
            } else {
                if(data.length!=profile.initLength-5||data[0]!=(byte)0xee)throw new IOException("Format d’identification non reconnu : "+VciUsbProtocol.hex(data));
            }
            if(discoveryIdentity!=null&&!Arrays.equals(discoveryIdentity,data))throw new IOException("Les identifications diffèrent · aucune autre lecture envoyée");
            discoveryIdentity=data.clone();identityChecks++;listener.identityObserved(profile.reference,VciUsbProtocol.hex(data));return;
        }
        if(data.length!=profile.idLength || data[0]!=(byte)0xee
            || !VciUsbProtocol.hex(Arrays.copyOfRange(data,profile.referenceOffset,profile.referenceOffset+5)).equals(profile.reference))
            throw new IOException("Référence de "+profile.module+" non prise en charge : aucune autre lecture envoyée");
        listener.identityObserved(profile.reference,VciUsbProtocol.hex(data));
    }
    private void faults() throws IOException {
        faultsVerified=false;
        String result;
        checkStop();
        byte[] data;
        try {data=request("faults",false);}
        finally {
            // CAL130 _LECTDEF64 ends each successful fault request with
            // ARRET_DEMANDE before interpreting its bitfields.
            if(usable&&(profile.module.equals("seat")||profile.module.equals("seat183"))){
                exchange("stop_demand",makeBody(profile.module.equals("seat183")?0x65:0x06,0x05,new byte[0]),false);
            }
        }
        if(profile.discovery) {
            if(profile.module.equals("locking")) {
                if(data.length!=17)throw new FaultPayloadException("Réponse Kiekert non reconnue : "+VciUsbProtocol.hex(data));
                // CAL18 _LECTDEF230 uses LEC_COMPT_DEF, not LECTURE_DEFAUTS.
                // Preserve the first response in the journal, then read the
                // actual counters before attaching their fault descriptions.
                checkStop();data=request("group1",false);
                if(data.length!=17)throw new FaultPayloadException("Compteurs Kiekert non reconnus : "+VciUsbProtocol.hex(data));
            } else if(profile.kwp) {
                if(data.length<2||data.length>128||data[0]!=0x57)throw new FaultPayloadException("Réponse défauts AL4 non reconnue : "+VciUsbProtocol.hex(data));
            } else if(data.length<1||data.length>129||data[0]!=0x33)throw new FaultPayloadException("Réponse défauts non reconnue : "+VciUsbProtocol.hex(data));
            if(OtherEcuErasure.plan(profile.module)!=null&&OtherEcuErasure.identified(discoveryIdentity,identityChecks,profile.kwp)&&EcuFaults.complete(profile.module,data)) {
                lastFaultsEmpty=EcuFaults.empty(profile.module,data);faultsVerified=true;
            }
            listener.faults((profile.module.equals("seat183")?"Défauts · trame brute, décodage à confirmer : "+VciUsbProtocol.hex(data):EcuFaults.format(profile.module,data))+"\n\nProfil à confirmer sur véhicule · réponse réelle : "+VciUsbProtocol.hex(data));
            return; // Only the fixed erasure plan can consume this evidence; no coding/reset authorization.
        }
        if(data.length<1 || data[0]!=0x33)throw new FaultPayloadException("Trame de défauts non reconnue : "+VciUsbProtocol.hex(data));
        if(profile.module.equals("climate")) {
            java.util.List<byte[]> pages=new java.util.ArrayList<>();int blocks=0;
            while(data.length>1) {
                if(data.length!=30 || ++blocks>4)throw new FaultPayloadException("Pagination des défauts climatisation non reconnue : "+VciUsbProtocol.hex(data));
                pages.add(data.clone());
                checkStop();
                data=request("faults_next",false);
                if(data.length<1||data[0]!=0x33)throw new FaultPayloadException("Suite de défauts climatisation non reconnue : "+VciUsbProtocol.hex(data));
            }
            result="Lecture complète · "+blocks+" blocs reçus\n\n"+EcuFaults.climate(pages);
            lastFaultsEmpty=blocks==0;
        } else {
            if(data.length>65 || ((data.length-1)&1)!=0)throw new FaultPayloadException("Liste de défauts non reconnue : "+VciUsbProtocol.hex(data));
            result=data.length==1?"Aucun défaut retourné par ce calculateur.":EcuFaults.format(profile.module,data);
            lastFaultsEmpty=data.length==1;
        }
        faultsVerified=true;
        listener.faults(result);
    }
    private void readFaultsForDisplay() throws IOException {
        readingFaults=true;
        try { faults(); }
        catch(FaultPayloadException e) { listener.faultsUnavailable("Lecture des défauts incomplète · "+e.getMessage()+"\nAucune conclusion sur l’absence de défaut. Les autres lectures restent disponibles."); }
        finally { readingFaults=false; }
    }
    private void checkStop() throws IOException { if(stopped||snapshotCancelled!=null&&snapshotCancelled.getAsBoolean())throw new IOException("Arrêt demandé"); }
    private void checkProcedureStop()throws IOException {if(stopped||procedureCancel)throw new IOException("Arrêt demandé");}
    private void clearFaults() throws IOException {
        boolean sent=false,accepted=false;
        OtherEcuErasure.Plan plan=OtherEcuErasure.plan(profile.module);
        try {
            listener.operation("clear_precheck","Sauvegarde des défauts avant effacement · "+profile.reference);
            checkStop();
            if(plan!=null)identity(request("identify",false));
            faults(); // complete list/pagination recorded before any erasure
            checkStop();
            if(!faultsVerified)throw new FaultPayloadException("Format des défauts ou identification non confirmé · effacement non envoyé");
            listener.operation("clear_send","Envoi unique de l’effacement · "+profile.reference);
            synchronized(this) { checkStop();eraseArmed=true; }
            faultsVerified=false;
            try { sent=true;request("erase",false);accepted=true; }
            finally {
                eraseArmed=false;
                if(sent&&plan!=null&&plan.completionMs>0) {
                    // Wait even on an unknown reply or Stop: the ECU may have accepted.
                    try{listener.operation("clear_wait","Attente de fin d’effacement · "+plan.completionMs+" ms");}
                    finally{try{waiter.pause(plan.completionMs);}catch(IOException e){usable=false;throw e;}}
                }
                if(sent && profile.module.equals("airbag")) {
                    // CAL3 _EFFDEF40: GetTickCount()+0x80e8 before _LECTDEF39.
                    // Also wait on an unrecognised ECU acknowledgement: it may have
                    // accepted the request. Stop cancels readback, not this interval.
                    try { listener.operation("clear_wait","Envoi airbag tenté · attente de 33 s avant toute autre commande ou reconnexion"); }
                    finally {
                        IOException progressFailure=null;
                        try{for(int i=0;i<330;i++) {
                            waiter.pause(100);
                            // Logging/progress must never shorten this mandatory wait.
                            if((i+1)%10==0&&progressFailure==null)try {
                                listener.operation("clear_progress","Attente airbag · "+(33-(i+1)/10)+" s restantes avant relecture");
                            }catch(IOException e){progressFailure=e;}
                        }}
                        catch(IOException e){usable=false;throw new IOException("Délai de fin d’effacement airbag interrompu · état non confirmé",e);}
                        if(progressFailure!=null)throw progressFailure;
                    }
                }
            }
            checkStop();
            listener.operation("clear_readback","Effacement accepté · relecture des défauts");
            readAfterClear(plan);
            clearRequested=false;
            listener.operation("clear_result",lastFaultsEmpty?"Effacement accepté · aucun défaut retourné à la relecture":
                "Effacement accepté · des défauts sont encore présents ou réapparus ; consulter la liste relue");
        } catch(IOException e) {
            if(e instanceof ErasureReplyException&&usable&&!stopped) {
                // A valid USB reply with an unexpected ECU acknowledgement is
                // not a lost USB link. Never resend the clear. Recover reads first.
                listener.operation("clear_readback","Réponse d’effacement inconnue · rétablissement de la lecture sans nouvel effacement");
                try {readAfterClear(plan);}
                catch(IOException recovery){e.addSuppressed(recovery);usable=false;listener.operation("clear_unknown","Effacement et reprise de lecture non confirmés · "+e.getMessage()+" · "+recovery.getMessage());throw e;}
                clearRequested=false;
                listener.operation("clear_unknown",e.getMessage()+" · lecture rétablie ; résultat de l’effacement non confirmé");
                return;
            }
            if(!sent)listener.operation("clear_rejected","Effacement non envoyé · "+e.getMessage());
            else if(accepted)listener.operation("clear_unverified","Effacement accepté · relecture non confirmée · "+e.getMessage());
            else listener.operation("clear_unknown","Résultat d’effacement inconnu · nouvelle lecture requise avant toute autre tentative");
            if(e instanceof FaultPayloadException)listener.faultsUnavailable("Lecture complète non confirmée · "+e.getMessage()+"\nRelire les défauts avant toute nouvelle opération.");
            else throw e;
        } finally {clearRequested=false;eraseArmed=false;}
    }
    private void readAfterClear(OtherEcuErasure.Plan plan)throws IOException {
        if(plan!=null&&plan.stopDemand) {
            listener.operation("clear_cleanup","Arrêt de demande du siège avant relecture");
            exchange("stop_demand",makeBody(0x06,0x05,new byte[0]),false);
        }
        checkStop();faults();checkStop();
        if(plan!=null&&plan.restart) {
            listener.operation("clear_reconnect","Reprise du diagnostic ABS · liaison USB conservée");
            request("finish",false);initialized=false;closed=true;checkStop();
            initialized=true;closed=false;byte[] init=request("init",false);
            if(init.length!=profile.initLength||init[0]!=0x55)throw new IOException("Réinitialisation ABS non reconnue");
            identity(Arrays.copyOfRange(init,4,init.length-1));checkStop();identity(request("identify",false));
            checkStop();faults();checkStop();
        }
        if(!faultsVerified)throw new FaultPayloadException("Relecture des défauts incomplète");
    }
    private void procedure() throws IOException {
        final String id;
        synchronized(this) {
            id=procedureRequested;procedureRequested=null;
            if(id==null||stopped)return;
            procedureRunning=true;
        }
        final boolean[] sent={false};
        try {
            listener.procedure(id,"procedure_precheck","Sauvegarde des défauts et des groupes avant "+ClimateOperations.plan(id).title);
            checkProcedureStop();faults();checkProcedureStop();
            for(String group:new String[]{"group1","group2","group3"}) {group(group);checkProcedureStop();}
            faultsVerified=false;
            String result=ClimateOperations.execute(id,new ClimateOperations.Port() {
                public byte[] exchange(byte[] body,String name)throws IOException {
                    sent[0]=true;
                    return OtherEcuSession.this.exchange(name,body,false);
                }
                public boolean usable(){return usable;}
                public boolean stopped(){return stopped||procedureCancel;}
                public void pause(long millis)throws IOException {
                    try{waiter.pause(millis);}catch(IOException e){usable=false;throw e;}
                }
                public void event(String kind,String text)throws IOException{listener.procedure(id,kind,text);}
            });
            checkProcedureStop();
            listener.procedure(id,"procedure_readback","Mode normal rétabli · relecture des valeurs et des défauts");
            for(String group:new String[]{"group1","group2","group3"}) {group(group);checkStop();}
            faults();checkStop();
            procedureRunning=false;
            listener.procedure(id,"procedure_result",result+" · mode normal rétabli, groupes et défauts relus");
        } catch(IOException e) {
            boolean cancelled=procedureCancel&&"Arrêt demandé".equals(e.getMessage());
            if((cancelled||e instanceof ClimateOperations.ReplyException)&&!stopped&&usable&&e.getSuppressed().length==0) {
                // ClimateOperations only returns this cancellation after its
                // protected learning interval and confirmed normal-mode exit.
                listener.procedure(id,"procedure_readback",sent[0]?"Retour au mode normal confirmé · relecture":"Annulation avant envoi · relecture");
                for(String group:new String[]{"group1","group2","group3"}){group(group);checkStop();}
                faults();checkStop();procedureRunning=false;
                listener.procedure(id,cancelled?"procedure_cancelled":"procedure_unverified",(cancelled?"Test interrompu":e.getMessage())+" · "+
                    (sent[0]?"mode normal rétabli":"aucune activation envoyée")+", valeurs et défauts relus ; connexion conservée");
                return;
            }
            String kind=!sent[0]?"procedure_rejected":stopped&&usable&&"Arrêt demandé".equals(e.getMessage())&&e.getSuppressed().length==0?"procedure_cancelled":"procedure_unverified";
            String text=!sent[0]?"Procédure non envoyée · "+e.getMessage():
                "Procédure interrompue · "+e.getMessage()+" · consulter le journal et refaire une lecture avant un nouveau test";
            try{listener.procedure(id,kind,text);}catch(IOException logging){e.addSuppressed(logging);}
            if(e instanceof FaultPayloadException && !sent[0])listener.faultsUnavailable("Sauvegarde des défauts incomplète · procédure non envoyée");
            else throw e;
        } finally {procedureRunning=false;procedureCancel=false;}
    }
    public static String keyValues(byte[] data) throws IOException {
        if(data.length==6&&data[0]==0x11&&data[1]==1) {
            int state=data[2]&255,ecu=data[4]&255;
            // Static CAL128.dll: exports 874..880 read bytes 2 and 4 (zero based).
            return "Contact après contact : "+yes(state,1)+"\nPorte conducteur (état brut) : "+((state>>>1)&1)
                +"\nCode de clé reçu : "+yes(state,4)+"\nCode de clé valide : "+yes(state,8)
                +"\nBatterie (état binaire brut) : "+((state>>>4)&1)
                +"\nCalculateur moteur déverrouillé : "+yes(ecu,1)+"\nCalculateur moteur verrouillé : "+yes(ecu,2);
        }
        if(data.length==4&&data[0]==0x11&&data[1]==2) {
            int config=data[2]&255;
            // CAL128 exports 793..795 expose configuration bits, not live lamp states.
            return "Configuration témoin de verrouillage : "+(config&1)
                +"\nConfiguration témoin de déverrouillage : "+((config>>>2)&1)
                +"\nConfiguration avertisseur sonore : "+((config>>>1)&1)
                +"\nNombre de clés mémorisées : "+(data[3]&255);
        }
        throw new IOException("Groupe antidémarrage non reconnu");
    }
    private static String yes(int bits,int mask) {return (bits&mask)!=0?"oui":"non";}
    private void group(String name) throws IOException {
        byte[] data=request(name,false);
        if(profile.discovery) {
            if(profile.module.equals("locking")) {
                if(data.length!=17)throw new IOException("Compteurs Kiekert non reconnus : "+VciUsbProtocol.hex(data));
            } else if(profile.kwp) {
                byte[] req=commands.get(name);
                if(data.length<3||data.length>128||data[0]!=0x61||data[1]!=req[20])throw new IOException("Groupe AL4 non reconnu : "+VciUsbProtocol.hex(data));
            } else {
                if(data.length<3||data.length>128||data[0]!=0x11)throw new IOException("Groupe non reconnu : "+VciUsbProtocol.hex(data));
            }
            groups.put(name,ExtendedValues.decode(profile.module,name,data));
            StringBuilder text=new StringBuilder(profile.title+" · premier essai physique à effectuer\n\n");
            for(String value:groups.values())text.append(value).append("\n\n");
            listener.values(text.toString());return;
        }
        if(profile.module.equals("keys")) groups.put(name,keyValues(data));
        else {
            int expected=name.equals("group1")?24:name.equals("group2")?14:6;
            int marker=name.equals("group3")?0x87:0x11,group=name.equals("group2")?2:1;
            if(data.length!=expected||(data[0]&255)!=marker||(data[1]&255)!=group)throw new IOException("Groupe climatisation non reconnu");
            if(name.equals("group1"))listener.measurements(ClimateDecoder.decode(data));
            groups.put(name,name+" · "+VciUsbProtocol.hex(data));
        }
        StringBuilder text=new StringBuilder();for(String value:groups.values())text.append(value).append("\n\n");
        if(profile.module.equals("climate"))text.append("Réponses réelles · conversions physiques à valider avant affichage des températures.");
        listener.values(text.toString());
    }
    private byte[] openReadSession()throws IOException {
            String[] startup={"serial","version","reference","board_init","board_version","board_init","board_version","supply_read","coupler","program"};
            for(int i=0;i<startup.length;i++) {checkStop();request(startup[i],i==3);if(i==6)waiter.pause(1000);}
            checkStop();
            initialized=true;closed=false;byte[] init=request("init",false);
            byte[] id;
            if(profile.discovery) {
                if(profile.kwp) {
                    kwpContract.checkInit(init);
                } else if(profile.module.equals("locking")) {
                    if(init.length!=profile.initLength)throw new IOException("Initialisation Kiekert non reconnue");
                } else {
                    if(init.length!=profile.initLength||init[0]!=0x55)throw new IOException("Initialisation de profil non reconnue : "+VciUsbProtocol.hex(init));
                    identity(Arrays.copyOfRange(init,4,init.length-1));
                }
                checkStop();
                id=commands.containsKey("identify")?request("identify",false):init;
                if(commands.containsKey("identify"))identity(id);
                if(profile.kwp&&OtherEcuErasure.plan(profile.module)!=null){checkStop();identity(request("identify",false));}
            } else {
                // CAL128 returns the identity without the trailing byte used by the other profiles.
                int initTrailer=profile.module.equals("keys")?0:1;
                if(init.length!=profile.idLength+4+initTrailer||init[0]!=0x55)throw new IOException("Initialisation non reconnue");
                identity(Arrays.copyOfRange(init,4,init.length-initTrailer));
                checkStop();
                id=request("identify",false);identity(id);
            }
            return id;
    }
    private java.util.function.BooleanSupplier snapshotCancelled;
    /** One complete fault list with the fixed profile's identity checks and confirmed exit. */
    public void readFaultSnapshot(java.util.function.BooleanSupplier cancelled)throws IOException {
        if(profile.discovery||!commands.containsKey("finish"))throw new IllegalArgumentException("Exact profile with a separate finish required");
        snapshotCancelled=cancelled;IOException failure=null;
        try{
            byte[] id=openReadSession();checkStop();faults();checkStop();
            listener.identified(profile.reference,VciUsbProtocol.hex(id));
        }catch(IOException e){failure=e;}
        finally{
            ready=false;
            if(initialized&&usable)try{request("finish",false);closed=true;initialized=false;}
            catch(IOException e){usable=false;if(failure==null)failure=e;else failure.addSuppressed(e);}
            snapshotCancelled=null;
        }
        if(failure!=null)throw failure;
    }
    /** Exact known Valeo profile, one parameter block, no faults, erase or actuator requests. */
    public void readClimateSnapshot(java.util.function.BooleanSupplier cancelled)throws IOException {
        if(!profile.module.equals("climate")||profile.discovery)throw new IllegalArgumentException("Climate snapshot only");
        snapshotCancelled=cancelled;IOException failure=null;
        try{
            openReadSession();checkStop();group("group1");checkStop();
        }catch(IOException e){failure=e;}
        finally{
            ready=false;
            if(initialized&&usable)try{request("finish",false);closed=true;initialized=false;}
            catch(IOException e){usable=false;if(failure==null)failure=e;else failure.addSuppressed(e);}
            snapshotCancelled=null;
        }
        if(failure!=null)throw failure;
    }
    public void run(int maxCycles) throws IOException {
        IOException failure=null;
        try {
            byte[] id=openReadSession();
            readFaultsForDisplay();checkStop();
            ready=true;listener.identified(profile.reference,VciUsbProtocol.hex(id));
            int cycle=0;
            while(!stopped&&(maxCycles==0||cycle<maxCycles)) {
                for(String name:new String[]{"group1","group2","group3"}) {
                    if(stopped)break;
                    if(commands.containsKey(name))group(name);
                }
                if(procedureRequested!=null&&!stopped)procedure();
                else if(clearRequested&&!stopped)clearFaults();
                else if(faultRead&&!stopped) {
                    synchronized(this){readingFaults=true;faultRead=false;}
                    readFaultsForDisplay();
                    listener.operation("faults_read",faultsVerified?"Lecture complète des défauts terminée":"Lecture incomplète ou profil à confirmer · consulter le résultat");
                }
                cycle++;
                if(!stopped)waiter.pause(400);
            }
        } catch(IOException e) {if(!(stopped && "Arrêt demandé".equals(e.getMessage()) && e.getSuppressed().length==0))failure=e;}
        finally {
            ready=false;faultRead=false;readingFaults=false;clearRequested=false;faultsVerified=false;eraseArmed=false;procedureRequested=null;procedureRunning=false;
            if(initialized&&usable&&commands.containsKey("finish"))try{request("finish",false);closed=true;}catch(IOException e){usable=false;if(failure==null)failure=e;else failure.addSuppressed(e);}
            if(initialized&&usable&&profile.module.equals("locking"))listener.state("Lecture Kiekert arrêtée · commande de fin omise car identique à l’effacement ; couper le contact avant de changer de module");
        }
        if(failure!=null)throw failure;
    }
    private static void pause(long ms)throws IOException {
        try{Thread.sleep(ms);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Lecture interrompue",e);}
    }
}
