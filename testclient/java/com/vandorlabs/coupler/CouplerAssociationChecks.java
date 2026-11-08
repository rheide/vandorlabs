package com.vandorlabs.coupler;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
public final class CouplerAssociationChecks {
 private static int checks;
 private static void need(boolean b){checks++;if(!b)throw new AssertionError("association check "+checks);}
 private static void refused(Runnable r){try{r.run();throw new AssertionError("accepted missing receipt");}catch(IllegalArgumentException expected){checks++;}}
 public static void main(String[] args){net.minecraft.init.Bootstrap.register();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileCoupler.class,new net.minecraft.util.ResourceLocation("vandorlabs:square_coupler"));UUID h=new UUID(1,2),v=new UUID(3,4),g=new UUID(5,6),next=new UUID(7,8);
  CouplerAssociation alone=CouplerAssociation.chosen(null,h,v,g,CouplerAssociation.Boundary.NEW_HEAD_INSTALLATION,true,false);
  need(alone.permitsUnpaired(h,v,g));need(!alone.permitsUnpaired(h,v,next));
  refused(()->CouplerAssociation.chosen(null,h,v,g,CouplerAssociation.Boundary.CAPTURED_REFRESH,true,false));
  for(CouplerAssociation.Boundary b:CouplerAssociation.Boundary.values()){
   refused(()->CouplerAssociation.chosen(alone,h,v,g,b,false,false));
   if(b!=CouplerAssociation.Boundary.GENUINE_ATTACH){CouplerAssociation retained=CouplerAssociation.chosen(alone,h,v,g,b,true,false);need(retained==alone);}
   CouplerAssociation paired=CouplerAssociation.chosen(alone,h,v,g,b,true,true);need(paired.state==CouplerAssociation.EXPECTED_PAIR);need(!paired.permitsUnpaired(h,v,g));
   if(b==CouplerAssociation.Boundary.CAPTURED_REFRESH){refused(()->CouplerAssociation.chosen(paired,h,v,g,b,true,false));refused(()->CouplerAssociation.chosen(paired,h,v,next,b,true,true));}
   else if(b!=CouplerAssociation.Boundary.GENUINE_ATTACH)need(CouplerAssociation.chosen(paired,h,v,next,b,true,false).permitsUnpaired(h,v,next));
  }
  refused(()->CouplerAssociation.chosen(alone,h,v,g,CouplerAssociation.Boundary.GENUINE_ATTACH,true,false));
  NBTTagCompound root=new NBTTagCompound(),caps=new NBTTagCompound();caps.setByteArray("unknown",new byte[]{2,4,6});root.setString("id","vandorlabs:square_coupler");root.setUniqueId("CouplerControlIdentity",h);root.setTag("ForgeCaps",caps);root.setString("FutureOwn","kept");CouplerAssociationData.write(root,alone);NBTTagCompound original=root.copy();CouplerAssociation decoded=CouplerAssociationData.read(root);need(decoded.permitsUnpaired(h,v,g));need(root.equals(original));
  TileCoupler tile=new TileCoupler();tile.readFromNBT(root);NBTTagCompound saved=tile.currentData(root);need(CouplerAssociationData.read(saved).permitsUnpaired(h,v,g));need(saved.getCompoundTag("ForgeCaps").equals(caps));need(saved.getString("FutureOwn").equals("kept"));
  NBTTagCompound fresh=root.copy();fresh.removeTag(CouplerAssociationData.KEY);fresh.removeTag("CouplerControlIdentityMost");fresh.removeTag("CouplerControlIdentityLeast");NBTTagCompound preimage=fresh.copy();NBTTagCompound patch=CouplerAssociationData.chosenPatch(fresh,v,g,CouplerAssociation.Boundary.NEW_HEAD_INSTALLATION,true,false);CouplerAssociation stamped=CouplerAssociationData.read(patch);need(stamped!=null&&stamped.head.equals(patch.getUniqueId("CouplerControlIdentity")));need(fresh.equals(preimage)&&patch.getCompoundTag("ForgeCaps").equals(caps));refused(()->CouplerAssociationData.chosenPatch(fresh,v,g,CouplerAssociation.Boundary.CAPTURED_REFRESH,true,false));
  NBTTagCompound pairedPatch=CouplerAssociationData.chosenPatch(patch,v,g,CouplerAssociation.Boundary.GENUINE_ATTACH,true,true);need(CouplerAssociationData.read(pairedPatch).state==CouplerAssociation.EXPECTED_PAIR);refused(()->CouplerAssociationData.chosenPatch(pairedPatch,v,g,CouplerAssociation.Boundary.CAPTURED_REFRESH,true,false));
  NBTTagCompound wrongHead=root.copy();wrongHead.setUniqueId("CouplerControlIdentity",next);refused(()->new TileCoupler().readFromNBT(wrongHead));
  for(String key:new String[]{"Version","State","HeadMost","HeadLeast","VehicleMost","VehicleLeast","GenerationMost","GenerationLeast"}){NBTTagCompound bad=root.copy();bad.getCompoundTag(CouplerAssociationData.KEY).setString(key,"bad");refused(()->CouplerAssociationData.read(bad));}
  NBTTagCompound bad=root.copy();bad.getCompoundTag(CouplerAssociationData.KEY).setByte("State",(byte)3);refused(()->CouplerAssociationData.read(bad));final NBTTagCompound unknown=root.copy();unknown.getCompoundTag(CouplerAssociationData.KEY).setString("FutureVersionField","unknown");refused(()->CouplerAssociationData.read(unknown));
  System.out.println("Coupler association policy PASS checks="+checks+" scoped proof/legacy refusal/lost mate/explicit receipts/strict codec/opaque preservation; runtime boundary binding pending");
 }
}
