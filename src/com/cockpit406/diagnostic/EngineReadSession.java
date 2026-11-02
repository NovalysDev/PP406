package com.cockpit406.diagnostic;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Fixed diagnostic reads only. No erase, unlock, actuator or programming dispatch. */
public final class EngineReadSession {
    public static final class Profile {
        public final int id,initLength;public final String title,couplerSha,programSha;public final boolean kwp;
        private final Map<String,byte[]> commands=new LinkedHashMap<>();
        private int faultPrefix;
        private boolean identityOnly;
        public Profile(int id,String title,boolean kwp,int initLength,String couplerSha,String programSha) {
            this.id=id;this.title=title;this.kwp=kwp;this.initLength=initLength;this.couplerSha=couplerSha;this.programSha=programSha;
            faultPrefix=kwp?0x57:0x33;
        }
        public Profile faultPrefix(int value){if(value!=0x33&&value!=0x57&&value!=0x58&&value!=0x61)throw new IllegalArgumentException("Service de défauts inconnu");faultPrefix=value;return this;}
        public Profile identityOnly(){identityOnly=true;return this;}
        public Profile read(String name,int family,int op,String data) {
            if(!name.matches("init|identify|faults|finish|gateway_finish|group[1-9]"))throw new IllegalArgumentException("Commande hors lecture");
            commands.put(name,body(family,op,VciUsbProtocol.unhex(data)));return this;
        }
    }
    public interface Listener {
        void record(String name,byte[] request,byte[] response)throws IOException;
        void state(String message);void identity(String raw);void values(String message);void faults(String message);
        default void ready(){}
        default void measurements(int profileId,Map<String,Double> values){}
    }
    public interface Waiter {void pause(long millis)throws IOException;}
    private final Profile profile;private final Mp70Session.Transport transport;private final Listener listener;private final Waiter waiter;
    private final Map<String,byte[]> commands=new LinkedHashMap<>();
    private final Map<String,String> values=new LinkedHashMap<>();
    private volatile boolean stopped,reread,ready;
    private boolean initialized,healthy=true,closed=true;
    private String identity;
    private final LexiaKwp.Contract kwpContract;
    public EngineReadSession(Profile profile,byte[] coupler,byte[] program,Mp70Session.Transport transport,Listener listener,Waiter waiter) {
        check(coupler,profile.couplerSha);check(program,profile.programSha);
        this.profile=profile;this.transport=transport;this.listener=listener;this.waiter=waiter;
        commands.putAll(profile.commands);
        for(String required:profile.identityOnly?new String[]{"init","identify","finish"}:new String[]{"init","identify","faults","finish"})if(!commands.containsKey(required))throw new IllegalArgumentException("Profil incomplet");
        if(profile.identityOnly)for(String key:commands.keySet())if(!key.equals("init")&&!key.equals("identify")&&!key.equals("finish"))throw new IllegalArgumentException("Profil d'identification strictement limité");
        kwpContract=profile.kwp?LexiaKwp.fromProgram(program,commands.get("init"),commands.get("identify")):null;
        commands.put("serial",body(0,9,VciUsbProtocol.unhex("1606")));commands.put("version",body(0,250,new byte[0]));
        commands.put("reference",body(0,9,VciUsbProtocol.unhex("060a")));commands.put("board_init",body(0,7,new byte[0]));
        commands.put("board_version",body(0,18,new byte[0]));commands.put("board_state",body(0,254,new byte[0]));
        commands.put("supply_read",body(0,11,new byte[]{3}));commands.put("coupler",body(0,5,coupler));commands.put("program",body(0,4,program));
    }
    public void stop(){stopped=true;}
    public boolean isReady(){return ready&&!stopped;}
    public synchronized boolean queueFaultRead(){if(profile.identityOnly||!isReady()||reread)return false;reread=true;return true;}
    public void rereadFaults(){if(!profile.identityOnly)reread=true;}
    public boolean mayTryAnotherProfile(){return healthy&&closed;}
    private static void check(byte[] data,String hash) {
        try{if(!VciUsbProtocol.hex(MessageDigest.getInstance("SHA-256").digest(data)).equals(hash))throw new IllegalArgumentException("Définition modifiée");}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private static byte[] body(int family,int op,byte[] data) {
        byte[] b=new byte[20+data.length];b[0]=(byte)family;b[1]=(byte)op;b[2]=(byte)data.length;b[3]=(byte)(data.length>>>8);b[4]=(byte)170;
        System.arraycopy(data,0,b,20,data.length);return b;
    }
    public byte[] requestBody(String name){byte[] b=commands.get(name);if(b==null)throw new IllegalArgumentException("Commande hors lecture : "+name);return b.clone();}
    private byte[] request(String name,boolean cold)throws IOException {
        byte[] req=requestBody(name),res;
        listener.state(profile.title+" · "+name);
        try{res=transport.exchange(req);}catch(IOException e){healthy=false;throw e;}
        if(res.length<20||res[10]!=req[0]||res[11]!=req[1]||res[15]!=(byte)170||
           ((res[12]&255)|((res[13]&255)<<8))!=res.length-20){healthy=false;throw new IOException("Enveloppe USB non reconnue");}
        listener.record(name,req,res);
        int status=res[14]&255,expected=name.equals("board_init")||name.equals("board_version")||name.equals("coupler")?3:1;
        byte[] data=Arrays.copyOfRange(res,20,res.length);
        if(!(cold&&name.equals("board_init")&&status==12&&data.length==0)&&status!=expected){
            // Complete initialization refusals observed in captures. No ECU
            // session was accepted. Do not send a finish on a rejected init.
            if(name.equals("init")&&data.length==0&&(status==129||status==212||status==218)){initialized=false;closed=true;}
            else healthy=false;
            throw new IOException(name+" : état Lexia "+status);
        }
        if(name.equals("program")&&(data.length!=1||(data[0]&255)!=(commands.get("program").length-20)/45)){healthy=false;throw new IOException("Table de communication refusée");}
        if(name.equals("board_version")&&!FirmwareIdentity.matches(data)){healthy=false;throw new IOException(FirmwareIdentity.ERROR);}
        return kwpContract!=null&&req[0]!=0?(name.equals("gateway_finish")?kwpContract.gatewayPayload(data):kwpContract.payload(data,req)):data;
    }
    private void checkStop()throws IOException{if(stopped)throw new IOException("Arrêt demandé");}
    private void identify()throws IOException {
        byte[] init=request("init",false),fromInit=null;
        if(profile.kwp){kwpContract.checkInit(init);}
        else {
            if(init.length!=profile.initLength||init[0]!=0x55)throw new IOException("Initialisation PSA2 non reconnue : "+VciUsbProtocol.hex(init));
            fromInit=Arrays.copyOfRange(init,4,init.length-1);
            if(fromInit.length<2||fromInit[0]!=(byte)0xee)throw new IOException("Identification PSA2 absente");
        }
        checkStop();byte[] id=request("identify",false);
        if(profile.kwp){
            if(id.length<3||id.length>128||id[0]!=0x61||id[1]!=(byte)0x80)throw new IOException("Identification KWP non reconnue : "+VciUsbProtocol.hex(id));
            checkStop();if(!Arrays.equals(id,request("identify",false)))throw new IOException("Identification instable");
        } else if(!Arrays.equals(id,fromInit))throw new IOException("Les deux identifications PSA2 diffèrent");
        identity=VciUsbProtocol.hex(id);listener.identity(identity);
    }
    private void faults()throws IOException {
        checkStop();byte[] data=request("faults",false);
        byte[] faultRequest=commands.get("faults");
        boolean localMismatch=profile.faultPrefix==0x61&&(faultRequest.length!=21||data.length<2||data[1]!=faultRequest[20]);
        if(data.length<1||data.length>256||(data[0]&255)!=profile.faultPrefix||localMismatch) {
            listener.faults("Réponse de défauts non reconnue · aucune conclusion sur leur absence.\n"+VciUsbProtocol.hex(data));return;
        }
        listener.faults("Réponse réelle du calculateur · décodage des codes et libellés à intégrer pour ce profil.\n"+VciUsbProtocol.hex(data));
    }
    private void group(String name)throws IOException {
        byte[] data=request(name,false),req=commands.get(name);
        boolean valid=profile.kwp?data.length>=3&&data[0]==0x61&&req.length>20&&data[1]==req[20]:data.length>=2&&data[0]==0x11;
        if(!valid){values.put(name,name+" · réponse non reconnue : "+VciUsbProtocol.hex(data));}
        else if(profile.id==72&&identity!=null&&identity.length()==46&&identity.startsWith("ee000261204407")) {
            try{values.put(name,name+" · "+Mp70Decoder.decode(VciUsbProtocol.hex(data)).toString());}
            catch(IllegalArgumentException e){values.put(name,name+" · "+VciUsbProtocol.hex(data));}
        } else if(profile.id==13){
            try{Map<String,Double> decoded=Mp72Decoder.decode(data);listener.measurements(profile.id,decoded);values.put(name,name+" · "+VciUsbProtocol.hex(data));}
            catch(IllegalArgumentException e){values.put(name,name+" · "+VciUsbProtocol.hex(data)+" · "+e.getMessage());}
        } else values.put(name,name+" · "+VciUsbProtocol.hex(data));
        StringBuilder text=new StringBuilder("Mesures reçues en direct · formats bruts, conversions à confirmer pour ce profil.\n\n");
        for(String value:values.values())text.append(value).append("\n\n");listener.values(text.toString());
    }
    /** cycles=1 for discovery, 0 for continuous reads. Stop waits for the pending reply and known finish. */
    public void run(int cycles)throws IOException {
        IOException failure=null;
        try {
            String[] startup={"serial","version","reference","board_init","board_version","board_init","board_version","board_state","supply_read","coupler","program"};
            for(int i=0;i<startup.length;i++){checkStop();request(startup[i],i==3);if(i==6)waiter.pause(1000);}
            checkStop();initialized=true;closed=false;identify();
            if(profile.identityOnly){
                String detail="Identification reçue · lecture seule expérimentale ; défauts et codage non disponibles.\n\nRéponse brute : "+identity;
                try{BsiSiemensIdentityFrame b=BsiSiemensIdentityFrame.decodePayload(VciUsbProtocol.unhex(identity));detail="Référence PSA : "+b.psaReference+"\nIndice logiciel : "+b.softwareIndex+"\n\n"+detail;}
                catch(IllegalArgumentException ignored){}
                listener.values(detail);
            } else {
                faults();checkStop();ready=true;listener.ready();int n=0;
                while(!stopped&&(cycles==0||n<cycles)) {
                    for(String name:profile.commands.keySet()){if(stopped)break;if(name.startsWith("group"))group(name);}
                    if(reread&&!stopped){reread=false;faults();}
                    n++;if(!stopped)waiter.pause(400);
                }
            }
        }catch(IOException e){if(!stopped||!"Arrêt demandé".equals(e.getMessage()))failure=e;}
        finally {
            ready=false;
            if(initialized&&healthy)try {
                byte[] finish=request("finish",false);int expected=profile.kwp?0xc2:0x0f;
                if(finish.length!=1||(finish[0]&255)!=expected)throw new IOException("Fin de session non confirmée : "+VciUsbProtocol.hex(finish));
                if(commands.containsKey("gateway_finish")){
                    byte[] gateway=request("gateway_finish",false);
                    if(gateway.length!=1||(gateway[0]&255)!=0xc2)throw new IOException("Fin de passerelle non confirmée : "+VciUsbProtocol.hex(gateway));
                }
                closed=true;
            }catch(IOException e){healthy=false;if(failure==null)failure=e;else failure.addSuppressed(e);}
        }
        if(failure!=null)throw failure;
    }
}
