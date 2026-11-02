import com.cockpit406.diagnostic.BsiSiemensIdentityFrame;
import java.util.Arrays;

public final class BsiSiemensIdentityFrameTest {
    static byte[] bytes(String hex){String[] parts=hex.split(" ");byte[] out=new byte[parts.length];for(int i=0;i<parts.length;i++)out[i]=(byte)Integer.parseInt(parts[i],16);return out;}
    static void check(boolean b){if(!b)throw new AssertionError();}
    static void rejects(byte[] b){try{BsiSiemensIdentityFrame.decode(b);throw new AssertionError("Trame non valide acceptee");}catch(IllegalArgumentException expected){}}
    public static void main(String[] args){
        // Published annotated PP2000 captures for a 406 Coupe, not this user's vehicle.
        byte[] a=bytes("98 F1 D0 61 80 96 37 43 64 80 00 04 40 03 00 00 00 49 10 00 00 00 00 00 00 00 00 CE");
        byte[] b=bytes("8A F1 D0 61 81 06 9D A2 06 04 99 00 00 15");
        BsiSiemensIdentityFrame id=BsiSiemensIdentityFrame.decode(a),date=BsiSiemensIdentityFrame.decode(b);
        check("9637436480".equals(id.psaReference)&&"4910".equals(id.softwareIndex)&&id.serial==null&&id.matchesPublishedReference());
        check("069DA2".equals(date.serial)&&"06/04/99".equals(date.productionDate)&&date.psaReference==null);
        check(!date.matchesPublishedReference());
        BsiSiemensIdentityFrame payload=BsiSiemensIdentityFrame.decodePayload(Arrays.copyOfRange(a,3,a.length-1));
        check("9637436480".equals(payload.psaReference)&&"4910".equals(payload.softwareIndex));
        payload=BsiSiemensIdentityFrame.decodePayload(Arrays.copyOfRange(b,3,b.length-1));
        check("069DA2".equals(payload.serial)&&"06/04/99".equals(payload.productionDate));
        try{BsiSiemensIdentityFrame.decodePayload(new byte[]{0x7f,0x21,0x12});throw new AssertionError("ECU refusal accepted");}catch(IllegalArgumentException expected){}
        try{BsiSiemensIdentityFrame.decodePayload(Arrays.copyOfRange(a,3,a.length));throw new AssertionError("Transport checksum accepted inside payload");}catch(IllegalArgumentException expected){}
        byte[] wrong=a.clone();wrong[5]^=1;rejects(wrong);
        wrong=b.clone();wrong[2]=0;rejects(wrong);
        wrong=b.clone();wrong[8]=0x40;wrong[13]=(byte)((wrong[13]+0x3a)&255);rejects(wrong);
        rejects(new byte[13]);rejects(null);
        System.out.println("BSI Siemens: published identity and date captures plus malformed frames passed; no vehicle communication tested.");
    }
}
