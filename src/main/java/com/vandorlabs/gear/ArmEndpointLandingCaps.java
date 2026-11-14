package com.vandorlabs.gear;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
/** Focus-only provider detects accidental adoption of a synthetic payload view's capability bytes. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="vandorlabs")
public final class ArmEndpointLandingCaps {
 private static boolean enabled;public static final ResourceLocation KEY=new ResourceLocation("vandorlabs","landing_endpoint_probe");
 public static void enable(){enabled=true;}
 @SubscribeEvent public static void attach(AttachCapabilitiesEvent<TileEntity> event){boolean cold=System.getProperty("vandorlabs.armRecoveryPhase","").contains("landing-attach-");TileEntity tile=event.getObject();if((enabled||cold)&&(tile instanceof GearTileEntity||cold&&(tile instanceof net.minecraft.tileentity.TileEntityChest||tile instanceof net.minecraft.tileentity.TileEntitySign)))event.addCapability(KEY,new Probe(tile));}
 private static final class Probe implements ICapabilitySerializable<NBTTagCompound>{
  final TileEntity tile;boolean physical=System.getProperty("vandorlabs.armRecoveryPhase","").contains("landing-attach-");NBTTagCompound opaque=new NBTTagCompound();
  Probe(TileEntity tile){this.tile=tile;opaque.setIntArray("PositionLike",new int[]{991,-47,236});NBTTagCompound nested=new NBTTagCompound();nested.setString("ArmMountDirection","do not interpret capability fields");nested.setInteger("x",731);opaque.setTag("Nested",nested);}
  public boolean hasCapability(Capability<?> cap,EnumFacing side){return false;}public <T>T getCapability(Capability<T> cap,EnumFacing side){return null;}
  public NBTTagCompound serializeNBT(){NBTTagCompound data=new NBTTagCompound();data.setInteger("Representation",tile.getWorld() instanceof com.vandorlabs.vehicle.VehicleWorld?47:22);if(physical)data.setBoolean("RequirePhysicalWorld",true);data.setTag("Opaque",opaque.copy());return data;}
  public void deserializeNBT(NBTTagCompound tag){physical=tag.getBoolean("RequirePhysicalWorld");if(physical&&(tile.getWorld()==null||tile.getWorld() instanceof com.vandorlabs.vehicle.VehicleWorld))throw new IllegalStateException("Landing capability requires the physical world during deserialization");if(tag.hasKey("Opaque",10))opaque=tag.getCompoundTag("Opaque").copy();}
 }
 private ArmEndpointLandingCaps(){}
}
