package com.cockpit406.diagnostic;

import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Per-frame, monotonic real readings. Recording failures never interrupt vehicle I/O. */
public final class FuelRecording implements Closeable {
    public static final String[] FUELS={"SP98","SP95","E85","50 % SP98 + 50 % E85","50 % SP95 + 50 % E85"};
    public static final String[][] METRICS={
        {"rpm","Régime moteur","tr/min"},{"speed_kmh","Vitesse","km/h"},
        {"injection_ms","Temps d’injection","ms"},{"map_mbar","Pression collecteur","mbar"},
        {"throttle_deg","Papillon","°"},{"oxygen_mv","Sonde à oxygène","mV"},
        {"coolant_r_c","Eau R","°C"},{"coolant_s_c","Eau S","°C"},
        {"air_r_c","Admission R","°C"},{"air_s_c","Admission S","°C"},
        {"battery_v","Tension","V"},{"canister_duty_pct","Canister","%"},
        {"idle_duty_pct","Commande ralenti","%"},{"throttle_mv","Signal papillon","mV"},
        {"load_ms","Charge (libellé calculateur)","ms"}};
    public final File file;
    private BufferedWriter writer;
    private FileOutputStream output;
    private final long origin;
    private long plannedPauseStart=-1,plannedPauseTotal;
    private int frames;
    private long lastFlush;
    private boolean identified;
    private String issue="";
    public FuelRecording(File root,String fuel,String vin,String carId,String profile,long wall,long elapsed){
        origin=elapsed;File created=null;
        try{
            if(!Arrays.asList(FUELS).contains(fuel))throw new IOException("Carburant non choisi");
            File directory=new File(root,"fuel-recordings");
            if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Dossier inaccessible");
            created=File.createTempFile("trip-"+wall+"-",".jsonl",directory);
            output=new FileOutputStream(created);writer=new BufferedWriter(new OutputStreamWriter(output,StandardCharsets.UTF_8));
            JSONObject h=new JSONObject();h.put("kind","header");h.put("schema",1);h.put("fuel",fuel);h.put("vin",vin);h.put("carId",carId);h.put("profile",profile);h.put("startedAt",wall);h.put("blendBasis","volume déclaré par l’utilisateur, teneur réelle en éthanol non mesurée");write(h);writer.flush();
        }catch(Exception e){failed(e);}
        file=created;
    }
    public synchronized void identified(String reference){
        if(writer==null)return;
        try{JSONObject row=new JSONObject();row.put("kind","identity");row.put("reference",reference);write(row);writer.flush();identified=true;}catch(Exception e){failed(e);}
    }
    public synchronized void sample(int group,Map<String,Double> values,long elapsed,long wall){
        if(writer==null||!identified||values==null||elapsed<origin||plannedPauseStart>=0)return;
        try{
            JSONObject numbers=new JSONObject();
            for(String[] metric:METRICS){Double v=values.get(metric[0]);if(v!=null&&!v.isNaN()&&!v.isInfinite())numbers.put(metric[0],v);}
            if(numbers.length()==0)return;
            JSONObject row=new JSONObject();row.put("kind","sample");row.put("elapsedMs",elapsed-origin);row.put("readingMs",elapsed-origin-plannedPauseTotal);row.put("observedAt",wall);row.put("group",group);row.put("values",numbers);write(row);
            if(++frames==1||elapsed-lastFlush>=1000){writer.flush();lastFlush=elapsed;}
        }catch(Exception e){failed(e);}
    }
    /** Separate comparison event: never changes the received or plotted ECU speed. */
    public synchronized void speedReference(int rawByte,double ecu,SpeedReference.Fix gps,long elapsed,long wall){
        if(writer==null||!identified||plannedPauseStart>=0||elapsed<origin||gps==null||!gps.usable(elapsed)||rawByte<0||rawByte>255||!ValueAlerts.finite(ecu))return;
        try{
            JSONObject row=new JSONObject();row.put("kind","speed_reference");row.put("elapsedMs",elapsed-origin);row.put("observedAt",wall);
            row.put("ecuRawByte",rawByte);row.put("ecuKmh",ecu);row.put("conversion","floor(raw*39/35)");row.put("gpsKmh",gps.kmh);
            row.put("gpsAgeMs",elapsed-gps.elapsedMs);row.put("gpsAccuracyMeters",gps.accuracyMeters);
            if(!Double.isNaN(gps.speedAccuracyMps))row.put("gpsSpeedAccuracyMps",gps.speedAccuracyMps);
            row.put("deltaKmh",gps.kmh-ecu);row.put("calibrationApplied",false);write(row);
        }catch(Exception e){failed(e);}
    }
    /** Only an explicitly scheduled climate interruption may compress the graph's time axis. */
    public synchronized void plannedPause(boolean start,long elapsed,long wall){
        if(writer==null||!identified||elapsed<origin)return;
        try{
            if(start){if(plannedPauseStart>=0)return;plannedPauseStart=elapsed;}
            else{if(plannedPauseStart<0||elapsed<plannedPauseStart)return;plannedPauseTotal+=elapsed-plannedPauseStart;plannedPauseStart=-1;}
            JSONObject row=new JSONObject();row.put("kind",start?"planned_pause":"planned_resume");row.put("reason","outside_temperature");row.put("elapsedMs",elapsed-origin);row.put("observedAt",wall);row.put("excludedMs",plannedPauseTotal);write(row);writer.flush();
        }catch(Exception e){failed(e);}
    }
    private void write(JSONObject row)throws IOException{writer.write(row.toString());writer.newLine();}
    private void failed(Exception e){issue="Enregistrement indisponible : "+e.getMessage();try{if(writer!=null)writer.close();}catch(IOException ignored){}writer=null;}
    public synchronized String issue(){return issue;}
    /** Called by the backup worker, never performs a network request. */
    public synchronized void checkpoint(){if(writer!=null)try{writer.flush();output.getFD().sync();}catch(IOException e){failed(e);}}
    public synchronized void close(){if(writer!=null)try{writer.flush();writer.close();}catch(IOException e){issue="Enregistrement incomplet : "+e.getMessage();}finally{writer=null;}}

    public static final class Trip {
        public final File file;public final JSONObject header;
        public String reference="";public final List<JSONObject> samples=new ArrayList<>();
        Trip(File file,JSONObject header){this.file=file;this.header=header;}
        public String label(){return header.optString("fuel")+" · "+new java.text.SimpleDateFormat("dd/MM HH:mm",Locale.FRANCE).format(new Date(header.optLong("startedAt")));}
        public boolean comparable(Trip other){return !reference.isEmpty()&&reference.equals(other.reference)&&header.optString("profile").equals(other.header.optString("profile"))&&header.optString("vin").equals(other.header.optString("vin"))&&header.optString("carId").equals(other.header.optString("carId"));}
    }
    public static Trip read(File file,boolean samples)throws IOException {
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(new FileInputStream(file),StandardCharsets.UTF_8))){
            String line=reader.readLine();JSONObject header;
            try{header=new JSONObject(line==null?"{}":line);if(header.optInt("schema")!=1||!header.optString("kind").equals("header"))throw new IOException("Format inconnu");}catch(JSONException e){throw new IOException(e);}
            Trip trip=new Trip(file,header);
            while((line=reader.readLine())!=null){
                try{JSONObject row=new JSONObject(line);String kind=row.optString("kind");if(kind.equals("identity"))trip.reference=row.optString("reference");else if(kind.equals("sample")&&!trip.reference.isEmpty()&&row.optJSONObject("values")!=null)trip.samples.add(row);}catch(JSONException ignored){/* An interrupted last line cannot become a reading. */}
                if(!samples&&!trip.samples.isEmpty())break;
            }
            return trip;
        }
    }
    public static List<Trip> list(File root){
        List<Trip> trips=new ArrayList<>();File[] files=new File(root,"fuel-recordings").listFiles();
        if(files!=null)for(File file:files)if(file.getName().endsWith(".jsonl"))try{Trip trip=read(file,false);if(!trip.samples.isEmpty())trips.add(trip);}catch(IOException ignored){}
        trips.sort((a,b)->Long.compare(b.header.optLong("startedAt"),a.header.optLong("startedAt")));return trips;
    }
}
