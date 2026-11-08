package com.vandorlabs.coupler;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.nbt.NBTTagCompound;

/** Strict small own-data envelope; never examines provider-owned capabilities. */
public final class CouplerAssociationData {
    public static final String KEY="VehicleArmCouplerAssociation";
    private static final Set<String> KEYS=new HashSet<>(Arrays.asList("Version","State","HeadMost","HeadLeast","VehicleMost","VehicleLeast","GenerationMost","GenerationLeast"));
    public static CouplerAssociation read(NBTTagCompound root){
        if(!root.hasKey(KEY))return null;
        if(!root.hasKey(KEY,10))throw new IllegalArgumentException("Malformed Coupler association envelope");
        NBTTagCompound tag=root.getCompoundTag(KEY);
        if(!KEYS.equals(tag.getKeySet())||!tag.hasKey("Version",3)||tag.getInteger("Version")!=1||!tag.hasKey("State",1))throw new IllegalArgumentException("Unsupported Coupler association format");
        for(String key:new String[]{"Head","Vehicle","Generation"})if(!tag.hasKey(key+"Most",4)||!tag.hasKey(key+"Least",4))throw new IllegalArgumentException("Malformed Coupler association identity");
        return new CouplerAssociation(tag.getByte("State"),tag.getUniqueId("Head"),tag.getUniqueId("Vehicle"),tag.getUniqueId("Generation"));
    }
    public static void write(NBTTagCompound root,CouplerAssociation proof){
        if(proof==null){root.removeTag(KEY);return;}
        NBTTagCompound tag=new NBTTagCompound();tag.setInteger("Version",1);tag.setByte("State",(byte)proof.state);tag.setUniqueId("Head",proof.head);tag.setUniqueId("Vehicle",proof.vehicle);tag.setUniqueId("Generation",proof.generation);root.setTag(KEY,tag);
    }
    /** Chosen publication only: defensive patch; the exact source preimage remains untouched. */
    public static NBTTagCompound chosenPatch(NBTTagCompound original,java.util.UUID vehicle,java.util.UUID generation,CouplerAssociation.Boundary boundary,boolean complete,boolean paired){
        if(!complete)throw new IllegalArgumentException("Complete Coupler ownership receipt required");
        net.minecraft.util.ResourceLocation id=net.minecraft.tileentity.TileEntity.getKey(TileCoupler.class);
        if(original==null||id==null||!original.hasKey("id",8)||!id.toString().equals(original.getString("id")))throw new IllegalArgumentException("Original Coupler identity unavailable");
        boolean anyIdentity=original.hasKey("CouplerControlIdentityMost")||original.hasKey("CouplerControlIdentityLeast");
        if(anyIdentity&&(!original.hasKey("CouplerControlIdentityMost",4)||!original.hasKey("CouplerControlIdentityLeast",4)))throw new IllegalArgumentException("Malformed Coupler control identity");
        CouplerAssociation prior=read(original);java.util.UUID head=anyIdentity?original.getUniqueId("CouplerControlIdentity"):java.util.UUID.randomUUID();
        if(prior!=null&&!prior.head.equals(head))throw new IllegalArgumentException("Coupler association head identity changed");
        CouplerAssociation next=CouplerAssociation.chosen(prior,head,vehicle,generation,boundary,true,paired);
        NBTTagCompound result=original.copy();result.setUniqueId("CouplerControlIdentity",head);write(result,next);return result;
    }
    private CouplerAssociationData(){}
}
