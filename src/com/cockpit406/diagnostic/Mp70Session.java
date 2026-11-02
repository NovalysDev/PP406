package com.cockpit406.diagnostic;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Read/session catalogue validated on a 406 Coupe, ECU 0261204407.
 * Fault erasure is separately gated by an explicit, one-use UI request.
 * Runtime ASCII communication tables are not firmware images.
 */
public final class Mp70Session {
    private static final class PreconditionsChanged extends IOException {PreconditionsChanged(String text){super(text);}}
    private static final class ReadUnavailable extends IOException {ReadUnavailable(String text){super(text);}}
    public interface Transport { byte[] exchange(byte[] body) throws IOException; }
    public interface Waiter {void pause(long millis)throws IOException;}
    /** Runs only after a confirmed motor-session end, on this same serialized transport. */
    public interface AuxiliaryRead {boolean due();void read()throws IOException;}
    public interface Listener {
        void record(String name, byte[] request, byte[] response, int status, byte[] data) throws IOException;
        void state(String message);
        void values(int group, String raw, Map<String, Double> decoded);
        void faults(List<String> codes);
        default void operation(String kind, String message) {}
        default void identified(String reference) {}
        default void plannedPause(boolean paused) {}
        default void procedure(String id,String kind,String message)throws IOException { operation(kind,message); }
    }
    private final Transport transport;
    private final Listener listener;
    private final Waiter waiter;
    private final byte[] coupler, program;
    private final List<String> faultCodes = new ArrayList<>();
    private volatile boolean stop;
    private volatile boolean ready, faultReadRequested, clearRequested, faultsVerified;
    private volatile boolean readingFaults,clearExecuting;
    private volatile boolean procedureRunning;
    private volatile boolean procedureCancel;
    private volatile String procedureRequested;
    private boolean usable = true, initialized, eraseArmed;
    private volatile double rpm = -1, speed = -1;
    private volatile long measuredAt;
    private volatile int[] measurementGroups={2,0,1,3};
    private AuxiliaryRead auxiliaryRead;
    private volatile boolean auxiliaryReading;
    public void setAuxiliaryRead(AuxiliaryRead read){auxiliaryRead=read;}
    public boolean isAuxiliaryReading(){return auxiliaryReading;}
    /** Changes only future measurement reads. Pending requests are never abandoned. */
    public void setMeasurementGroups(int[] requested) {
        if(requested==null||requested.length==0||requested.length>4)throw new IllegalArgumentException("Measurement groups");
        boolean[] seen=new boolean[4];
        for(int group:requested){if(group<0||group>3||seen[group])throw new IllegalArgumentException("Measurement groups");seen[group]=true;}
        if(!seen[2])throw new IllegalArgumentException("Group 2 required for fresh operating conditions");
        measurementGroups=requested.clone();
    }
    public Mp70Session(Transport transport, Listener listener, byte[] coupler, byte[] program) {
        this(transport,listener,coupler,program,Mp70Session::sleep);
    }
    public Mp70Session(Transport transport, Listener listener, byte[] coupler, byte[] program,Waiter waiter) {
        checkDefinition(coupler, 28, "da7ae7147051985b1ee9569558d0ef9dce79afe0599e87f0434bc46c871c04c6");
        checkDefinition(program, 2520, "aaa798695072507457cd72c9284f9362dab3d8c39b5663168154c848a052a306");
        this.transport = transport; this.listener = listener;
        this.waiter=waiter;
        this.coupler = coupler.clone(); this.program = program.clone();
    }
    private static void checkDefinition(byte[] data, int length, String sha) {
        try {
            if (data.length != length || !VciUsbProtocol.hex(MessageDigest.getInstance("SHA-256").digest(data)).equals(sha))
                throw new IllegalArgumentException("Communication definition changed");
            for (byte b : data) if (b < 32 || b > 126) throw new IllegalArgumentException("Non-ASCII definition");
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public synchronized void stop() { stop = true; clearRequested = false; faultReadRequested = false; faultsVerified = false;procedureRequested=null; }
    public boolean isReady() { return ready && !stop; }
    public boolean hasVerifiedFaults() { return isReady() && faultsVerified; }
    public boolean isOperationPending() {return clearRequested||clearExecuting||faultReadRequested||readingFaults||procedureRequested!=null||procedureRunning;}
    public synchronized boolean queueConfirmedProcedure(String id) {
        if(MotorOperations.plan(id)==null||!isReady()||isOperationPending()||!faultsVerified||!stationaryEngineOff())return false;
        procedureCancel=false;procedureRequested=id;return true;
    }
    public synchronized boolean cancelProcedure() {
        if(procedureRequested==null&&!procedureRunning)return false;
        procedureCancel=true;return true;
    }
    public boolean isProcedurePending(){return procedureRequested!=null||procedureRunning;}
    public synchronized boolean queueFaultRead() { if (!isReady() || isOperationPending()) return false; faultReadRequested = true; return true; }
    public synchronized boolean queueConfirmedClear() {
        if (!isReady() || isOperationPending() || !faultsVerified || !stationaryEngineOff()) return false;
        clearRequested = true; return true;
    }
    private boolean stationaryEngineOff() {
        return rpm == 0 && speed == 0 && measuredAt > 0 && System.nanoTime() - measuredAt < 3000000000L;
    }

    public byte[] body(String name) {
        int family = 0, op; byte[] data = new byte[0];
        switch (name) {
            case "serial": op = 9; data = VciUsbProtocol.unhex("1606"); break;
            case "reference": op = 9; data = VciUsbProtocol.unhex("060a"); break;
            case "version": op = 0xFA; break;
            case "board_init": op = 7; break;
            case "board_version": op = 0x12; break;
            case "board_state": op = 0xFE; break;
            case "supply_read": op = 0x0B; data = new byte[] {3}; break;
            case "coupler": op = 5; data = coupler; break;
            case "program": op = 4; data = program; break;
            case "init": family = 0x2A; op = 1; break;
            case "finish": family = 0x2A; op = 2; break;
            case "identify": family = 0x2A; op = 4; break;
            case "faults": family = 0x2A; op = 0x0A; break;
            case "erase":
                if (!eraseArmed || stop) throw new IllegalArgumentException("Effacement sans confirmation active");
                family = 0x2A; op = 0x0C; break;
            case "group0": case "group1": case "group2": case "group3":
                family = 0x2A; op = 5; data = new byte[] {(byte) (name.charAt(5) - '0')}; break;
            default:
                if (!name.startsWith("context:") || !faultCodes.contains(name.substring(8)))
                    throw new IllegalArgumentException("Commande hors du catalogue de lecture");
                family = 0x2A; op = 0x0B; data = VciUsbProtocol.unhex(name.substring(8));
        }
        byte[] body = new byte[20 + data.length];
        body[0] = (byte) family; body[1] = (byte) op;
        body[2] = (byte) data.length; body[3] = (byte) (data.length >>> 8); body[4] = (byte) 0xAA;
        System.arraycopy(data, 0, body, 20, data.length);
        return body;
    }
    private byte[] request(String name, boolean cold) throws IOException {
        byte[] request,response;
        synchronized(this) {
            if(name.equals("erase"))checkStop();
            request=body(name);
            if(name.equals("erase"))eraseArmed=false; // consumed before USB, never retried
        }
        return exchange(name,request,cold);
    }
    private byte[] exchange(String name,byte[] request,boolean cold)throws IOException {
        byte[] response;
        // A lost USB reply must not be followed by speculative ECU requests.
        try { response = transport.exchange(request); }
        catch (IOException e) { usable = false; throw e; }
        if (response.length < 20 || response[10] != request[0] || response[11] != request[1]
                || (response[15] & 255) != 0xAA
                || ((response[12] & 255) | ((response[13] & 255) << 8)) != response.length - 20) {
            usable = false; throw new IOException("Enveloppe de réponse non reconnue : " + name);
        }
        byte[] data = Arrays.copyOfRange(response, 20, response.length);
        int status = response[14] & 255;
        int expected = name.equals("board_init") || name.equals("board_version") || name.equals("coupler") ? 3 : 1;
        boolean acceptedCold=cold && name.equals("board_init") && status == 12 && data.length == 0;
        boolean unavailable=name.matches("group[0-3]")&&data.length==0&&(status==212||status==218);
        if(!acceptedCold && !unavailable && status!=expected)usable=false;
        listener.record(name, request, response, status, data);
        if(acceptedCold)return data;
        if(unavailable)throw new ReadUnavailable(name+" : aucune réponse calculateur · état Lexia "+status);
        if (status != expected) { usable = false; throw new IOException(name + " : état Lexia " + status + " (attendu " + expected + ")"); }
        if (name.equals("board_version") && !FirmwareIdentity.matches(data))
            throw new IOException(FirmwareIdentity.ERROR);
        if (name.equals("program") && !VciUsbProtocol.hex(data).equals("38")) throw new IOException("Table de communication refusée");
        if (name.equals("finish") && !VciUsbProtocol.hex(data).equals("0f")) {usable=false;throw new IOException("Fin de session non confirmée");}
        // GPC_PP2000 COMMAND 4030 / runtime C2A0C: positive ECU reply C3.
        if (name.equals("erase") && !VciUsbProtocol.hex(data).equals("c3"))
            throw new IOException("Effacement : réponse non reconnue, résultat à vérifier par une nouvelle lecture");
        if (name.equals("init") || name.equals("identify")) identify(data, name.equals("init"));
        if (name.equals("supply_read")) {
            if (data.length != 2 || (data[1] & 255) >= 100) throw new IOException("Tension d’interface non reconnue");
            listener.state(String.format(java.util.Locale.FRANCE, "Alimentation voiture : %.2f V", (data[0] & 255) + (data[1] & 255) / 100.0));
        }
        return data;
    }
    private static void identify(byte[] data, boolean init) throws IOException {
        if (init) {
            if (data.length != 28) throw new IOException("Initialisation moteur non reconnue");
            data = Arrays.copyOfRange(data, 4, 27);
        }
        if (data.length != 23 || (data[0] & 255) != 0xEE || data[1] != 0
                || !VciUsbProtocol.hex(Arrays.copyOfRange(data, 9, 14)).equals("0261204407")
                || !Arrays.equals(Arrays.copyOfRange(data, 9, 14), Arrays.copyOfRange(data, 14, 19)))
            throw new IOException("Référence moteur différente de 0261204407 : arrêt");
    }
    private void readFaults(boolean contexts) throws IOException {
        faultsVerified = false;
        checkStop();
        byte[] data = request("faults", false);
        // MP7.0's explicit no-fault response. Observed after C3 erase and on
        // subsequent connections; CAL72 _LECTDEF616 handles 99 08 separately
        // at 00401CAE..00401D0C, returning a zero-count fault list.
        if (Arrays.equals(data, new byte[] {(byte) 0x99, 0x08})) {
            faultCodes.clear();
            listener.faults(new ArrayList<>(faultCodes));
            faultsVerified = true;
            return; // no fault contexts to request
        }
        if (data.length < 1 || data.length > 65 || data[0] != 0x33 || ((data.length - 1) & 1) != 0)
            throw new FaultPayloadException("Liste des défauts non reconnue : " + VciUsbProtocol.hex(data));
        faultCodes.clear();
        for (int i = 1; i < data.length; i += 2) faultCodes.add(VciUsbProtocol.hex(Arrays.copyOfRange(data, i, i + 2)));
        listener.faults(new ArrayList<>(faultCodes));
        if (contexts) for (String code : faultCodes) {
            checkStop();
            byte[] context = request("context:" + code, false);
            if (context.length != 6 || context[0] != 0x3C || !VciUsbProtocol.hex(Arrays.copyOfRange(context, 1, 3)).equals(code))
                throw new FaultPayloadException("Contexte de défaut non reconnu : " + code + " / " + VciUsbProtocol.hex(context));
        }
        faultsVerified = contexts;
    }
    private boolean readFaultsForDisplay(boolean contexts) throws IOException {
        try { readFaults(contexts); return true; }
        catch (FaultPayloadException e) {
            listener.operation("faults_unavailable", "Lecture des défauts incomplète · " + e.getMessage() + " · valeurs toujours lisibles");
            return false;
        }
    }
    private void group(int index) throws IOException {
        byte[] data = request("group" + index, false);
        if (data.length != new int[] {20, 21, 24, 22}[index] || data[0] != 0x11 || data[1] != index)
            throw new IOException("Groupe de paramètres non reconnu : " + index);
        String hex = VciUsbProtocol.hex(data);
        Map<String, Double> decoded = index == 0 || index == 2 ? Mp70Decoder.decode(hex) : null;
        if (index == 2) { rpm = decoded.get("rpm"); speed = decoded.get("speed_kmh"); measuredAt = System.nanoTime(); }
        listener.values(index, hex, decoded);
    }
    private void actions() throws IOException {
        if (stop) return;
        if(procedureRequested!=null){procedure();return;}
        if (clearRequested) {
            synchronized(this) {clearRequested=false;clearExecuting=true;}
            boolean sent = false, accepted = false;
            try {
                listener.operation("clear_precheck", "Effacement confirmé · sauvegarde et contrôle moteur");
                checkStop();
                group(2);
                checkStop();
                if (!stationaryEngineOff()) { listener.operation("clear_rejected", "Effacement annulé : moteur tournant ou véhicule en mouvement"); return; }
                readFaults(true); // save current list and contexts before any destructive command
                group(2);
                if (stop) return;
                if (!stationaryEngineOff()) { listener.operation("clear_rejected", "Effacement annulé : conditions moteur modifiées"); return; }
                listener.operation("clear_send", "Envoi unique de l’effacement au MP7.0");
                checkStop();
                faultsVerified=false;
                eraseArmed = true;
                try { sent = true; request("erase", false); accepted = true; } finally { eraseArmed = false; }
                listener.operation("clear_readback", "Effacement accepté · relecture des défauts et contextes");
                readFaults(true);
                clearExecuting=false;
                listener.operation("clear_result", faultCodes.isEmpty()
                    ? "Effacement confirmé · aucun défaut retourné à la relecture"
                    : "Commande acceptée · " + faultCodes.size() + " défaut(s) encore présent(s) ou réapparu(s)");
            } catch (IOException e) {
                if (!sent && e instanceof FaultPayloadException) listener.operation("faults_unavailable", "Défauts ou contextes incomplets · " + e.getMessage());
                if (!sent) listener.operation("clear_rejected", "Effacement non envoyé · " + e.getMessage());
                else if (accepted) listener.operation("clear_unverified", "Effacement accepté · relecture non confirmée · " + e.getMessage());
                else listener.operation("clear_unknown", "Résultat d’effacement inconnu · relire les défauts avant toute autre tentative");
                if (!(e instanceof FaultPayloadException)) throw e;
            } finally { clearExecuting = false; }
        } else if (faultReadRequested) {
            synchronized(this) {readingFaults=true;faultReadRequested=false;}
            boolean complete;
            try{complete=readFaultsForDisplay(true);}finally{readingFaults=false;}
            if(complete)listener.operation("faults_read", "Lecture des défauts et contextes terminée");
        }
    }
    private void checkStop() throws IOException { if (stop) throw new IOException("Arrêt demandé"); }
    private void checkProcedureStop()throws IOException {if(stop||procedureCancel)throw new IOException("Arrêt demandé");}

    private void procedure()throws IOException {
        final String id;
        synchronized(this){id=procedureRequested;procedureRequested=null;if(id==null||stop)return;procedureRunning=true;}
        final boolean[] sent={false};
        try {
            listener.procedure(id,"procedure_precheck","Contrôle 0 tr/min, 0 km/h et sauvegarde des défauts/contextes");
            checkProcedureStop();group(2);checkProcedureStop();
            if(!stationaryEngineOff())throw new PreconditionsChanged("Moteur tournant, mouvement ou mesure trop ancienne");
            readFaults(true);checkProcedureStop();group(2);checkProcedureStop();
            if(!stationaryEngineOff())throw new PreconditionsChanged("Conditions moteur modifiées");
            faultsVerified=false;
            MotorOperations.execute(id,new MotorOperations.Port(){
                public byte[] exchange(byte[] body,String name)throws IOException {sent[0]=true;return Mp70Session.this.exchange(name,body,false);}
                public boolean usable(){return usable;}public boolean stopped(){return stop||procedureCancel;}
                public void pause(long millis)throws IOException {Mp70Session.this.pause(millis);}
                public void event(String kind,String text)throws IOException {listener.procedure(id,kind,text);}
            });
            listener.procedure(id,"procedure_readback","Arrêt confirmé · relecture des valeurs et des défauts");
            checkProcedureStop();group(2);readFaults(true);checkProcedureStop();
            procedureRunning=false;
            listener.procedure(id,"procedure_result","Séquence terminée · arrêt reçu et défauts relus ; effet physique à contrôler, premier essai véhicule à valider");
        }catch(IOException e){
            if(e instanceof PreconditionsChanged&&!sent[0]&&usable&&!stop) {
                procedureRunning=false;listener.procedure(id,"procedure_rejected","Test non envoyé · "+e.getMessage()+" · connexion conservée");return;
            }
            boolean cancelled=procedureCancel&&"Arrêt demandé".equals(e.getMessage());
            if((e instanceof MotorOperations.ReplyException||cancelled)&&usable&&!stop&&e.getSuppressed().length==0) {
                // execute() only preserves this type after the explicit E1 stop.
                // An unknown actuator acknowledgement must not destroy a healthy
                // read session. Readback is mandatory before reporting recovery.
                listener.procedure(id,"procedure_readback",!sent[0]?"Annulation avant envoi · contrôle de la session":
                    "Arrêt reçu · relecture des valeurs et des défauts");
                group(2);readFaults(true);checkStop();procedureRunning=false;
                listener.procedure(id,cancelled?"procedure_cancelled":"procedure_unverified",(cancelled?"Test interrompu":e.getMessage())+
                    (!sent[0]?" · aucune activation envoyée":" · arrêt confirmé")+", valeurs et défauts relus ; connexion conservée");
                return;
            }
            String kind=!sent[0]?"procedure_rejected":stop&&usable&&e.getSuppressed().length==0&&"Arrêt demandé".equals(e.getMessage())?"procedure_cancelled":"procedure_unverified";
            try{listener.procedure(id,kind,(!sent[0]?"Test non envoyé · ":"Test interrompu · ")+e.getMessage());}catch(IOException log){e.addSuppressed(log);}
            if(!(e instanceof FaultPayloadException && !sent[0]))throw e;
        }finally{procedureRunning=false;procedureCancel=false;}
    }

    private void auxiliary()throws IOException {
        boolean verified;
        synchronized(this){
            if(stop||!ready||isOperationPending()||auxiliaryRead==null||!auxiliaryRead.due())return;
            verified=faultsVerified;ready=false;faultsVerified=false;auxiliaryReading=true;
        }
        try{
            listener.plannedPause(true);
            listener.state("Actualisation de la température extérieure · USB conservé");
            checkStop();request("finish",false);initialized=false;
            checkStop();
            try{auxiliaryRead.read();}catch(IOException e){usable=false;throw e;}
            checkStop();
            // The climate reader has finished its ECU session. Restore the complete
            // known motor configuration; no USB close/open and no write replay.
            String[] restore={"board_init","board_version","board_init","board_version","board_state","supply_read","coupler","program","init","identify"};
            for(int i=0;i<restore.length;i++){
                checkStop();if(restore[i].equals("init"))initialized=true;
                request(restore[i],false);if(i==3)pause(1000);
            }
            checkStop();listener.plannedPause(false);group(2);checkStop();
            faultsVerified=verified;ready=true;
            listener.state("Lecture moteur rétablie · connexion USB conservée");
        }finally{auxiliaryReading=false;}
    }
    /** maxCycles=0 means continuous until stop/background/unplug. */
    public void run(int maxCycles) throws IOException {
        IOException failure = null;
        try {
            String[] boot = {"serial", "version", "reference", "board_init", "board_version", "board_init", "board_version", "board_state", "supply_read", "coupler", "program", "init", "identify"};
            for (int i = 0; i < boot.length; i++) {
                checkStop(); listener.state("Connexion · " + boot[i]);
                // Once init is sent it may have reached the ECU even on a lost reply.
                if (boot[i].equals("init")) initialized = true;
                request(boot[i], i == 3);
                if (i == 6) pause(1000);
            }
            listener.identified("0261204407"); // identity validated before fault labels are rendered
            readFaultsForDisplay(true);
            checkStop();
            ready = true;
            listener.state("Lecture moteur · Bosch MP7.0 / 0261204407");
            int cycle = 0, recoveries=0;
            while (!stop && (maxCycles == 0 || cycle < maxCycles)) {
                for (int group : measurementGroups) {
                    if (stop) break;
                    try{group(group);}
                    catch(ReadUnavailable e){
                        // Complete Lexia reply, no USB desynchronization. Never use
                        // this path for erase, actuator, malformed or missing USB replies.
                        synchronized(this){
                            ready=false;faultsVerified=false;
                            if(isOperationPending()||recoveries++>=1)throw e;
                        }
                        listener.state("Réponse moteur perdue · reprise en lecture seule, aucune activation répétée");
                        checkStop();request("finish",false);initialized=false;
                        checkStop();initialized=true;request("init",false);request("identify",false);
                        readFaultsForDisplay(true);checkStop();group(2);ready=true;
                        listener.state("Lecture moteur rétablie · connexion USB conservée");
                    }
                    pause(50);
                    actions();
                }
                cycle++;
                if(maxCycles==0||cycle<maxCycles)auxiliary();
            }
            if (!stop) readFaultsForDisplay(false);
        } catch (IOException e) { if (!(stop && "Arrêt demandé".equals(e.getMessage()) && e.getSuppressed().length==0)) failure = e; }
        finally {
            ready = false; auxiliaryReading=false;clearRequested = false; faultReadRequested = false; faultsVerified = false;readingFaults=false;clearExecuting=false;procedureRunning=false;procedureRequested=null;
            if (initialized && usable) {
                try { request("finish", false); }
                catch (IOException e) { if (failure == null) failure = e; else failure.addSuppressed(e); }
            }
        }
        if (failure != null) throw failure;
    }
    private void pause(long ms)throws IOException {waiter.pause(ms);}
    private static void sleep(long ms) throws IOException {
        try { Thread.sleep(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Lecture interrompue", e); }
    }
}
