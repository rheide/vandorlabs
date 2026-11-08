package com.vandorlabs.coupler;

import java.util.Objects;
import java.util.UUID;

/** Immutable ownership-scoped evidence. Callers supply receipts only after complete chosen publication. */
public final class CouplerAssociation {
    public static final int UNPAIRED=1,EXPECTED_PAIR=2;
    public enum Boundary { NEW_HEAD_INSTALLATION, CAPTURED_REFRESH, NEW_ASSEMBLY, DURABLE_RELEASE, GENUINE_ATTACH }
    public final int state;
    public final UUID head,vehicle,generation;
    public CouplerAssociation(int state,UUID head,UUID vehicle,UUID generation){
        if(state!=UNPAIRED&&state!=EXPECTED_PAIR)throw new IllegalArgumentException("Unknown Coupler association state");
        this.state=state;this.head=Objects.requireNonNull(head);this.vehicle=Objects.requireNonNull(vehicle);this.generation=Objects.requireNonNull(generation);
    }
    public boolean scope(UUID head,UUID vehicle,UUID generation){return this.head.equals(head)&&this.vehicle.equals(vehicle)&&this.generation.equals(generation);}
    public boolean permitsUnpaired(UUID head,UUID vehicle,UUID generation){return state==UNPAIRED&&scope(head,vehicle,generation);}
    /** No observation, load, serializer or incomplete search may renew an ownership claim. */
    public static CouplerAssociation chosen(CouplerAssociation prior,UUID head,UUID vehicle,UUID generation,Boundary boundary,boolean complete,boolean paired){
        Objects.requireNonNull(boundary);Objects.requireNonNull(head);Objects.requireNonNull(vehicle);Objects.requireNonNull(generation);
        if(!complete)throw new IllegalArgumentException("Complete Coupler ownership receipt required");
        if(boundary==Boundary.GENUINE_ATTACH&&!paired)throw new IllegalArgumentException("Attach receipt requires an actual pair");
        boolean renewal=boundary==Boundary.NEW_HEAD_INSTALLATION||boundary==Boundary.NEW_ASSEMBLY||boundary==Boundary.DURABLE_RELEASE||boundary==Boundary.GENUINE_ATTACH;
        if(prior!=null&&!renewal&&!prior.scope(head,vehicle,generation))throw new IllegalArgumentException("Coupler association ownership changed without a receipt");
        if(!paired&&!renewal){
            if(prior!=null&&prior.state==EXPECTED_PAIR)throw new IllegalArgumentException("Previously paired Coupler lost its mate");
            if(prior==null&&boundary==Boundary.CAPTURED_REFRESH)throw new IllegalArgumentException("Legacy captured Coupler has no unpaired proof");
        }
        int state=paired?EXPECTED_PAIR:UNPAIRED;
        return prior!=null&&prior.state==state&&prior.scope(head,vehicle,generation)?prior:new CouplerAssociation(state,head,vehicle,generation);
    }
}
