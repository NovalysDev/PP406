package com.cockpit406.diagnostic;

/** An Android grant can arrive before onResume. Consume it once, on resume,
 * after checking that the same device is still attached and permitted. */
public final class UsbPermissionFlow {
    public enum Reply { IGNORED, GRANTED, DENIED }
    public enum Ready { NONE, START, DETACHED, REVOKED }
    private long sequence,token;
    private String device;
    private boolean waiting,approved;
    public long request(String name){
        if(name==null)throw new IllegalArgumentException("Missing device");
        device=name;token=++sequence;waiting=true;approved=false;return token;
    }
    public Reply reply(long id,String name,boolean systemGranted){
        if(!waiting||id!=token||device==null||!device.equals(name))return Reply.IGNORED;
        waiting=false;approved=systemGranted;
        if(!approved)device=null;
        return approved?Reply.GRANTED:Reply.DENIED;
    }
    public boolean approved(){return approved;}
    public String device(){return device;}
    public Ready resume(boolean foreground,boolean attached,boolean permitted){
        if(!foreground||!approved)return Ready.NONE;
        Ready result=!attached?Ready.DETACHED:!permitted?Ready.REVOKED:Ready.START;
        cancel();return result;
    }
    public void cancel(){waiting=false;approved=false;device=null;}
}
