import com.cockpit406.diagnostic.VanDashboardFrame;

public final class VanDashboardFrameTest {
    private static void check(boolean value){if(!value)throw new AssertionError();}
    private static void rejects(Runnable task){try{task.run();throw new AssertionError("Invalid frame accepted");}catch(IllegalArgumentException expected){}}
    public static void main(String[] args){
        // Synthetic samples, not captures from any car: 8000/8 rpm and 5000/100 km/h.
        VanDashboardFrame a=VanDashboardFrame.decode(0x824,VanDashboardFrame.parsePayload("1F 40 13 88 00 00 00"));
        check(a.rpm==1000.0&&a.speedKmh==50.0);
        VanDashboardFrame b=VanDashboardFrame.decode(0x824,VanDashboardFrame.parsePayload("FF FF 00 00 01 02 03"));
        check(b.rpm==null&&b.speedKmh==0.0);
        VanDashboardFrame c=VanDashboardFrame.decode(0x824,VanDashboardFrame.parsePayload("00:00:FF:FF:00:00:00"));
        check(c.rpm==0.0&&c.speedKmh==null);
        VanDashboardFrame d=VanDashboardFrame.decode(0x824,VanDashboardFrame.parsePayload("00010001000000"));
        check(d.rpm==0.125&&d.speedKmh==0.01);
        // Published PSA VAN example, 307-oriented dataset: not a 406 validation.
        VanDashboardFrame observed=VanDashboardFrame.decode(0x824,VanDashboardFrame.parsePayload("18 F8 00 00 00 00 44"));
        check(observed.rpm==799.0&&observed.speedKmh==0.0);
        rejects(()->VanDashboardFrame.decode(0x825,new byte[7]));
        rejects(()->VanDashboardFrame.decode(0x824,new byte[6]));
        rejects(()->VanDashboardFrame.parsePayload("82 1F 40 13 88 00 00 00"));
        rejects(()->VanDashboardFrame.parsePayload("1F 40 13 ZZ 00 00 00"));
        System.out.println("VAN 824 decoder: synthetic and published PSA example passed; no 406 transport tested.");
    }
}
