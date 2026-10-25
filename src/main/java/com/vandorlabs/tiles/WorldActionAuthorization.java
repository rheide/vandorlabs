package com.vandorlabs.tiles;

import java.util.*;
import net.minecraft.entity.player.*;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.event.world.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Location receipts are world data, never portable tile configuration. */
@Mod.EventBusSubscriber(modid="vandorlabs")
public final class WorldActionAuthorization {
    private static final Map<TileEntity,UUID> BOUND=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<TileEntity,UUID> PENDING=Collections.synchronizedMap(new WeakHashMap<>());
    private WorldActionAuthorization(){}
    public static boolean supported(TileEntity tile){return tile instanceof TileEntityRampController||tile instanceof TileEntityPilotSeat||tile instanceof TileEntityAutopilot&&!(tile instanceof TileEntityWaypoint)||tile instanceof com.vandorlabs.gear.GearTileEntity||tile instanceof TileEntityTurntableController||tile instanceof com.vandorlabs.copier.CopyControllerTile;}
    private static boolean server(TileEntity tile){return tile!=null&&tile.getWorld() instanceof WorldServer&&!tile.getWorld().isRemote;}
    private static String type(TileEntity tile){return tile.getClass().getName();}
    private static Data data(World world){String name="vandorlabs_action_authorization_"+world.provider.getDimension();Data result=(Data)world.getPerWorldStorage().getOrLoadData(Data.class,name);if(result==null){result=new Data(name);world.getPerWorldStorage().setData(name,result);}return result;}
    public static void authorize(TileEntity tile,EntityPlayer player){if(player instanceof EntityPlayerMP&&server(tile)&&player.world==tile.getWorld()&&!player.isDead&&!player.isSpectator())grant(tile,player.getUniqueID());}
    public static void grant(TileEntity tile,UUID actor){if(!server(tile)||!supported(tile)||actor==null||tile.isInvalid()||tile.getWorld().getTileEntity(tile.getPos())!=tile)return;Receipt receipt=new Receipt(actor,UUID.randomUUID(),type(tile));Data store=data(tile.getWorld());store.rows.put(tile.getPos().toLong(),receipt);BOUND.put(tile,receipt.nonce);store.markDirty();tile.markDirty();}
    public static void installed(WorldServer world,com.vandorlabs.vehicle.VehicleStructure structure,BlockPos origin,UUID actor){if(actor!=null)for(com.vandorlabs.vehicle.VehicleStructure.Cell cell:structure.cells)grant(world.getTileEntity(origin.add(cell.pos)),actor);}
    public static UUID actorId(TileEntity tile){if(!server(tile)||tile.isInvalid()||!tile.getWorld().isBlockLoaded(tile.getPos())||tile.getWorld().getTileEntity(tile.getPos())!=tile)return null;Receipt receipt=data(tile.getWorld()).rows.get(tile.getPos().toLong());return receipt!=null&&receipt.type.equals(type(tile))&&receipt.nonce.equals(BOUND.get(tile))?receipt.actor:null;}
    public static EntityPlayerMP actor(TileEntity tile){UUID id=actorId(tile);EntityPlayer player=id==null?null:tile.getWorld().getPlayerEntityByUUID(id);if(!(player instanceof EntityPlayerMP)||player.world!=tile.getWorld()||player.isDead||player.isSpectator()||((EntityPlayerMP)player).connection==null||!((EntityPlayerMP)player).connection.netManager.isChannelOpen())throw new IllegalArgumentException("Reconfigure to authorize automation; the authorizing player must be online in this dimension");return (EntityPlayerMP)player;}
    public static EntityPlayerMP permissionActor(TileEntity tile){UUID id=actorId(tile);if(id==null)throw new IllegalArgumentException("Reconfigure to authorize automation");return net.minecraftforge.common.util.FakePlayerFactory.get((WorldServer)tile.getWorld(),new com.mojang.authlib.GameProfile(id,"Automation"));}
    /** Real replacements and in-place external NBT replacement invalidate the old receipt. */
    public static void revoke(TileEntity tile){UUID nonce=BOUND.remove(tile),pending=PENDING.remove(tile);if(nonce==null)nonce=pending;if(nonce==null||!server(tile))return;Data store=data(tile.getWorld());Receipt receipt=store.rows.get(tile.getPos().toLong());if(receipt!=null&&receipt.nonce.equals(nonce)){store.rows.remove(tile.getPos().toLong());store.markDirty();}}
    public static void unloaded(TileEntity tile){BOUND.remove(tile);PENDING.remove(tile);}
    @SubscribeEvent public static void unload(WorldEvent.Unload event){synchronized(BOUND){BOUND.keySet().removeIf(tile->tile.getWorld()==event.getWorld());PENDING.keySet().removeIf(tile->tile.getWorld()==event.getWorld());}}
    /** Chunk metadata is not part of portable tile NBT. Save ordering mismatches deny authority. */
    @SubscribeEvent public static void saving(ChunkDataEvent.Save event){if(event.getWorld().isRemote)return;NBTTagList proofs=new NBTTagList();for(TileEntity tile:event.getChunk().getTileEntityMap().values()){UUID nonce=BOUND.get(tile);if(nonce!=null&&supported(tile)&&!tile.isInvalid()){NBTTagCompound row=new NBTTagCompound();row.setLong("Position",tile.getPos().toLong());row.setString("Type",type(tile));row.setUniqueId("Nonce",nonce);proofs.appendTag(row);}}event.getData().setTag("VandorActionProofs",proofs);}
    @SubscribeEvent public static void loaded(ChunkDataEvent.Load event){if(event.getWorld().isRemote)return;NBTTagList proofs=event.getData().getTagList("VandorActionProofs",10);for(int i=0;i<proofs.tagCount();i++){NBTTagCompound row=proofs.getCompoundTagAt(i);TileEntity tile=event.getChunk().getTileEntityMap().get(BlockPos.fromLong(row.getLong("Position")));if(tile==null||!supported(tile)||!row.hasUniqueId("Nonce")||!row.getString("Type").equals(type(tile)))continue;UUID expected=row.getUniqueId("Nonce");PENDING.put(tile,expected);DeferredTileLoad.schedule(tile,()->{if(!expected.equals(PENDING.remove(tile)))return;Receipt receipt=data(tile.getWorld()).rows.get(tile.getPos().toLong());if(receipt!=null&&receipt.type.equals(type(tile))&&expected.equals(receipt.nonce))BOUND.put(tile,expected);});}}
    private static final class Receipt {final UUID actor,nonce;final String type;Receipt(UUID actor,UUID nonce,String type){this.actor=actor;this.nonce=nonce;this.type=type;}}
    public static final class Data extends WorldSavedData {
        private final Map<Long,Receipt> rows=new HashMap<>();
        public Data(String name){super(name);}
        @Override public void readFromNBT(NBTTagCompound tag){rows.clear();NBTTagList list=tag.getTagList("Receipts",10);for(int i=0;i<list.tagCount();i++){NBTTagCompound row=list.getCompoundTagAt(i);if(row.hasUniqueId("Actor")&&row.hasUniqueId("Nonce"))rows.put(row.getLong("Position"),new Receipt(row.getUniqueId("Actor"),row.getUniqueId("Nonce"),row.getString("Type")));}}
        @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){NBTTagList list=new NBTTagList();for(Map.Entry<Long,Receipt> entry:rows.entrySet()){NBTTagCompound row=new NBTTagCompound();row.setLong("Position",entry.getKey());row.setUniqueId("Actor",entry.getValue().actor);row.setUniqueId("Nonce",entry.getValue().nonce);row.setString("Type",entry.getValue().type);list.appendTag(row);}tag.setTag("Receipts",list);return tag;}
    }
}
