package com.vandorlabs.coupler;

/** Shared input edge policy; manual choice expires only on a real power edge or configuration. */
public final class CouplerInput {
    private boolean override,manual,known,power;
    public void read(boolean override,boolean manual){this.override=override;this.manual=manual;known=false;}
    public void restoreOverride(boolean override,boolean manual){this.override=override;this.manual=manual;}
    public void clearOverride(){override=false;}
    public void manual(boolean requested){override=true;manual=requested;}
    public boolean overridden(){return override;}
    public boolean manualRequested(){return manual;}
    public boolean sample(boolean powered,boolean inverted){if(known&&powered!=power)override=false;known=true;power=powered;return override?manual:powered!=inverted;}
}
