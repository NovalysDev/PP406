package com.cockpit406.diagnostic;

/** Process-wide ownership survives an Activity being recreated or reopened.
 * Only the worker releases ownership after ECU cleanup and USB close.
 */
public final class DiagnosticLease {
    private static Token owner;
    public static synchronized Token acquire() {
        if(owner!=null)return null;
        owner=new Token();return owner;
    }
    public static synchronized boolean held(){return owner!=null;}
    public static final class Token implements AutoCloseable {
        private Token(){}
        @Override public void close(){synchronized(DiagnosticLease.class){if(owner==this)owner=null;}}
    }
    private DiagnosticLease(){}
}
