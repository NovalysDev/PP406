package com.cockpit406.diagnostic;

/** Timed presentation; completion always requires a verified firmware identity. */
final class ConnectionTimeline {
    enum Stage { DETECTING, FIRMWARE, CONNECTED }
    static final class Frame {
        final Stage stage;
        final int progress;
        final boolean complete;
        Frame(Stage stage,int progress,boolean complete){this.stage=stage;this.progress=progress;this.complete=complete;}
    }
    private final long started;
    private String firmware;
    private long firmwareShown=-1,connectedShown=-1;
    ConnectionTimeline(long started){this.started=started;}
    void verified(String actualFirmware){firmware=actualFirmware;}
    String firmware(){return firmware;}
    Frame frame(long now){
        long elapsed=Math.max(0,now-started);
        if(firmware!=null&&elapsed>=1000&&firmwareShown<0)firmwareShown=now;
        if(firmwareShown>=0&&elapsed>=4000&&now-firmwareShown>=1200&&connectedShown<0)connectedShown=now;
        if(connectedShown>=0){
            long connectedFor=Math.max(0,now-connectedShown);
            int progress=900+(int)(Math.min(1000,connectedFor)*100/1000);
            return new Frame(Stage.CONNECTED,progress,elapsed>=5000&&connectedFor>=1000);
        }
        int progress=(int)(Math.min(4000,elapsed)*900/4000);
        return new Frame(firmwareShown>=0?Stage.FIRMWARE:Stage.DETECTING,progress,false);
    }
}
