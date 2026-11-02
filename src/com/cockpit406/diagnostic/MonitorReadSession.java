package com.cockpit406.diagnostic;

import java.io.IOException;
import java.util.*;

/** A monitor has no entry point for an erase, actuator, coding or learning action. */
public final class MonitorReadSession {
    public interface Assets {byte[] read(String name)throws IOException;}
    public interface Trace {void record(String name,byte[] request,byte[] response)throws IOException;}
    public static final class Result {
        public final String identity,faults;
        Result(String identity,String faults){this.identity=identity;this.faults=faults;}
    }
    private volatile boolean stopped;
    private volatile Mp70Session motor;
    private volatile OtherEcuSession other;
    private boolean safe;
    public static boolean supported(String profile){if(profile==null)return false;OtherEcuSession.Profile p=OtherEcuSession.profile(profile);return profile.equals("motor")||p!=null&&!p.discovery;}
    public void stop(){stopped=true;Mp70Session m=motor;if(m!=null)m.stop();OtherEcuSession o=other;if(o!=null)o.stop();}
    public boolean mayContinue(){return safe;}
    public Result read(String profile,String expectedIdentity,Mp70Session.Transport transport,Assets assets,Trace trace,Mp70Session.Waiter waiter)throws IOException{
        if(!supported(profile))throw new IOException("Surveillance non intégrée pour ce profil");
        if(expectedIdentity==null||expectedIdentity.isEmpty())throw new IOException("Premier scan requis");
        if(stopped){safe=true;throw new IOException("Arrêt demandé");}
        safe=false;String[] identity={null},faults={null};boolean[] finished={false};
        if(profile.equals("motor")){
            motor=new Mp70Session(transport,new Mp70Session.Listener(){
                public void record(String name,byte[] request,byte[] response,int status,byte[] data)throws IOException{trace.record(name,request,response);if(name.equals("finish"))finished[0]=status==1&&VciUsbProtocol.hex(data).equals("0f");}
                public void state(String text){}
                public void identified(String reference){identity[0]=reference;}
                public void values(int group,String raw,Map<String,Double> values){}
                public void faults(List<String> codes){faults[0]=EcuFaults.motor(FunctionCatalog.MP70_REFERENCE,codes);}
                public void operation(String kind,String text){if(kind.equals("faults_unavailable"))faults[0]=null;}
            },assets.read("mp70-coupler.bin"),assets.read("mp70-program.bin"),waiter);
            motor.setMeasurementGroups(new int[]{2});if(stopped)motor.stop();
            try{motor.run(1);}finally{safe=finished[0];motor=null;}
        }else{
            other=new OtherEcuSession(profile,transport,new OtherEcuSession.Listener(){
                public void record(String name,byte[] request,byte[] response,int status,byte[] data)throws IOException{trace.record(name,request,response);}
                public void state(String text){}
                public void identified(String reference,String raw){identity[0]=raw;}
                public void values(String text){}
                public void faults(String text){faults[0]=text;}
            },assets.read(profile+"-coupler.bin"),assets.read(profile+"-program.bin"),waiter::pause);
            if(stopped)other.stop();
            try{other.readFaultSnapshot(()->stopped);}finally{safe=other.mayTryAnotherProfile();other=null;}
        }
        if(stopped)throw new IOException("Arrêt demandé");
        if(!expectedIdentity.equals(identity[0]))throw new IOException("Identité différente du relevé mémorisé · refaire un scan");
        if(faults[0]==null||!safe)throw new IOException("Liste complète des défauts non confirmée");
        return new Result(identity[0],faults[0]);
    }
}
