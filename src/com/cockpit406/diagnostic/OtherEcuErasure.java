package com.cockpit406.diagnostic;

/** Fixed fault-erasure procedures, separate from actuator, adaptation and reset commands.
 * Command parameters come from each ECU's EFFACE_DEFAUTS definition. Physical trials pending. */
public final class OtherEcuErasure {
    public static final class Plan {
        public final int family,operation,completionMs;
        public final String data,ack;
        public final boolean restart,stopDemand;
        private Plan(int family,int operation,String data,String ack,int completionMs,boolean restart,boolean stopDemand) {
            this.family=family;this.operation=operation;this.data=data;this.ack=ack;this.completionMs=completionMs;this.restart=restart;this.stopDemand=stopDemand;
        }
    }
    public static Plan plan(String profile) {
        switch(profile) {
            case "abs5":return new Plan(0x05,0x04,"","c3",0,true,false);
            case "abs53":return new Plan(0x3d,0x04,"","c3",0,true,false);
            case "seat":return new Plan(0x06,0x04,"","c3",0,false,true);
            case "suspension":return new Plan(0x2d,0x09,"","c3",0,false,false);
            case "auto4hp20":return new Plan(0x10,0x0a,"","c3",0,false,false);
            // CAL137 _EFFDEF965 compares 54 FF 00 then waits 1000 ms.
            // No other 14xx reset parameter may be substituted here.
            case "autoal4":return new Plan(0x3e,0x06,"ff00","54ff00",1000,false,false);
            default:return null;
        }
    }
    /** Initialization and identification must agree, including on KWP.
     * An empty/synthetic identity cannot authorize a clear. This establishes
     * the selected diagnostic profile, not VIN/equipment or coding compatibility. */
    public static boolean identified(byte[] identity,int consistentReads,boolean kwp) {
        if(identity==null||consistentReads<2||identity.length<8)return false;
        int start=kwp?2:1;
        boolean value=false;
        for(int i=start;i<identity.length;i++)if(identity[i]!=0&&identity[i]!=(byte)0xff)value=true;
        return value;
    }
    private OtherEcuErasure(){}
}
