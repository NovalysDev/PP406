package com.cockpit406.diagnostic;
/** One warning per low-battery episode, rearmed above 25 percent. */
public final class BatteryAlertState {
    public boolean warned;
    public BatteryAlertState(boolean warned){this.warned=warned;}
    public boolean update(int percent,boolean charging){
        if(percent<0||percent>100)return false;
        if(percent>25)warned=false;
        if(percent<=20&&!charging&&!warned){warned=true;return true;}
        return false;
    }
}
