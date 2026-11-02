package com.cockpit406.diagnostic;

/**
 * Offline sequencing model; no diagnostic transport, UI or real key learning.
 * Successful access and command acknowledgements remain unvalidated on car.
 */
public final class ValeoLearningFlow {
    public enum Phase { PREPARATION, ACCESS, STATUS, BEGIN, PRESENT_KEYS, END, VERIFY, COMPLETE, REFUSED, UNCERTAIN }
    private Phase phase=Phase.PREPARATION;
    private int expectedKeys,presented,verified;
    private boolean pending;

    public Phase phase(){return phase;}

    public void prepare(String reference,int keyCount,boolean allKeysPresent,boolean stablePower) {
        require(Phase.PREPARATION);
        if(!"9624799080".equals(reference)||keyCount<1||keyCount>5||!allKeysPresent||!stablePower)
            throw new IllegalArgumentException("Préparation incomplète");
        expectedKeys=keyCount;phase=Phase.ACCESS;
    }
    public void access(ValeoAccessProtocol.Result result) {
        require(Phase.ACCESS);
        if(result==null||result==ValeoAccessProtocol.Result.UNKNOWN)phase=Phase.UNCERTAIN;
        else if(result==ValeoAccessProtocol.Result.REFUSED)phase=Phase.REFUSED;
        else phase=Phase.STATUS;
    }
    /** Simulation only; a positive status has not been validated on a vehicle. */
    public void simulateAccessStatus(boolean accepted) {
        require(Phase.STATUS);
        phase=accepted?Phase.BEGIN:Phase.REFUSED;
    }
    public void simulateBeginSent() { require(Phase.BEGIN);if(pending)throw new IllegalStateException("Déjà envoyé");pending=true; }
    public void simulateBeginReply(boolean accepted) {
        requirePending(Phase.BEGIN);pending=false;phase=accepted?Phase.PRESENT_KEYS:Phase.UNCERTAIN;
    }
    public void simulateKey(int index,boolean accepted) {
        require(Phase.PRESENT_KEYS);
        if(index!=presented+1||index>expectedKeys)throw new IllegalArgumentException("Clé hors séquence");
        if(!accepted){phase=Phase.UNCERTAIN;return;}
        presented++;if(presented==expectedKeys)phase=Phase.END;
    }
    public void simulateEndSent() { require(Phase.END);if(pending)throw new IllegalStateException("Déjà envoyé");pending=true; }
    public void simulateEndReply(boolean accepted,int countReadBack) {
        requirePending(Phase.END);pending=false;phase=accepted&&countReadBack==expectedKeys?Phase.VERIFY:Phase.UNCERTAIN;
    }
    public void simulateVerification(int index,boolean keyValid,boolean engineUnlocked) {
        require(Phase.VERIFY);
        if(index!=verified+1||index>expectedKeys)throw new IllegalArgumentException("Clé hors séquence");
        if(!keyValid||!engineUnlocked){phase=Phase.UNCERTAIN;return;}
        verified++;if(verified==expectedKeys)phase=Phase.COMPLETE;
    }
    /** Any interruption is treated as uncertain; no implicit retry. */
    public void interrupted() {if(phase!=Phase.COMPLETE&&phase!=Phase.REFUSED)phase=Phase.UNCERTAIN;pending=false;}
    private void require(Phase expected){if(phase!=expected)throw new IllegalStateException("Étape non autorisée");}
    private void requirePending(Phase expected){require(expected);if(!pending)throw new IllegalStateException("Aucun envoi en attente");}
}
