package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockProgrammableGlass;
import com.vandorlabs.network.MessageProgrammableGlass;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import io.netty.buffer.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;

/** Actual Forge light propagation, settings notifications, wire bytes and saved-data parity. */
public final class GlassConfigurationChecks {
    private static BlockProgrammableGlass glass;
    private static int luminous;
    private static final BlockPos POS=new BlockPos(8,80,8);
    private static final Method APPLY;
    static {try{APPLY=MessageProgrammableGlass.class.getDeclaredMethod("apply",TileEntityProgrammableGlass.class);APPLY.setAccessible(true);}catch(Exception error){throw new ExceptionInInitializerError(error);}}
    private static void need(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static Object field(MessageProgrammableGlass message,String name)throws Exception{Field field=MessageProgrammableGlass.class.getDeclaredField(name);field.setAccessible(true);return field.get(message);}

    /** Storage-only world: the inherited World.checkLightFor is the real Forge algorithm. */
    private static final class LightWorld extends World {
        final Map<BlockPos,IBlockState> states=new HashMap<>();
        final Map<BlockPos,TileEntity> tiles=new HashMap<>();
        final Map<BlockPos,Integer> lights=new HashMap<>();
        int updates,ranges,dirty,checks,chunkRequests;
        LightWorld(boolean remote){super(null,new net.minecraft.world.storage.WorldInfo(new NBTTagCompound()),new WorldProviderSurface(){@Override public boolean hasSkyLight(){return false;}},new net.minecraft.profiler.Profiler(),remote);}
        @Override protected net.minecraft.world.chunk.IChunkProvider createChunkProvider(){return new net.minecraft.world.chunk.IChunkProvider(){
            public net.minecraft.world.chunk.Chunk getLoadedChunk(int x,int z){chunkRequests++;throw new AssertionError("unexpected chunk request");}
            public net.minecraft.world.chunk.Chunk provideChunk(int x,int z){return getLoadedChunk(x,z);}
            public boolean tick(){return false;}public String makeString(){return "glass-light-storage";}public boolean isChunkGeneratedAt(int x,int z){return true;}
        };}
        @Override protected boolean isChunkLoaded(int x,int z,boolean empty){return true;}
        @Override public IBlockState getBlockState(BlockPos pos){return states.getOrDefault(pos,Blocks.AIR.getDefaultState());}
        @Override public TileEntity getTileEntity(BlockPos pos){return tiles.get(pos);}
        @Override public int getLightFor(EnumSkyBlock type,BlockPos pos){return type==EnumSkyBlock.BLOCK?lights.getOrDefault(pos,0):0;}
        @Override public void setLightFor(EnumSkyBlock type,BlockPos pos,int value){if(type==EnumSkyBlock.BLOCK){if(value==0)lights.remove(pos);else lights.put(pos.toImmutable(),value);}}
        @Override public int getCombinedLight(BlockPos pos,int minimum){return Math.max(getLightFor(EnumSkyBlock.BLOCK,pos),minimum)<<4;}
        @Override public boolean checkLightFor(EnumSkyBlock type,BlockPos pos){checks++;return super.checkLightFor(type,pos);}
        @Override public void markChunkDirty(BlockPos pos,TileEntity tile){dirty++;}
        @Override public void updateComparatorOutputLevel(BlockPos pos,Block block){}
        @Override public void notifyBlockUpdate(BlockPos pos,IBlockState before,IBlockState after,int flags){updates++;super.notifyBlockUpdate(pos,before,after,flags);}
        @Override public void markBlockRangeForRenderUpdate(BlockPos a,BlockPos b){ranges++;super.markBlockRangeForRenderUpdate(a,b);}
        void reset(){updates=ranges=dirty=checks=chunkRequests=0;}
    }
    private static TileEntityProgrammableGlass tile(World world){TileEntityProgrammableGlass tile=new TileEntityProgrammableGlass();tile.frameAppearance.front=luminous;tile.setWorld(world);tile.setPos(POS);return tile;}
    private static TileEntityProgrammableGlass install(LightWorld world){TileEntityProgrammableGlass tile=tile(world);world.states.put(POS,glass.getDefaultState());world.tiles.put(POS,tile);return tile;}
    /** Frozen old appearance setter, including its notifications but no light refresh. */
    private static void oldAppearance(TileEntityProgrammableGlass tile,int tint,boolean framed)throws Exception{
        Field t=TileEntityProgrammableGlass.class.getDeclaredField("tint"),f=TileEntityProgrammableGlass.class.getDeclaredField("framed");t.setAccessible(true);f.setAccessible(true);t.setInt(tile,tint);f.setBoolean(tile,framed);tile.markDirty();
        World world=tile.getWorld();IBlockState state=world.getBlockState(POS);world.notifyBlockUpdate(POS,state,state,3);world.markBlockRangeForRenderUpdate(POS.add(-1,-1,-1),POS.add(1,1,1));
    }
    private static void lighting()throws Exception{
        int negatives=0;
        for(boolean remote:new boolean[]{false,true}){
            LightWorld before=new LightWorld(remote);TileEntityProgrammableGlass old=install(before);int level=old.frameAppearance.light();before.checkLight(POS);need(before.getLightFor(EnumSkyBlock.BLOCK,POS.east())==level-1,"native light fixture did not propagate");before.reset();oldAppearance(old,old.getTint(),false);
            need(glass.getLightValue(glass.getDefaultState(),before,POS)==0&&before.getLightFor(EnumSkyBlock.BLOCK,POS)==level&&before.checks==0,"old missing light refresh was not reproduced");negatives++;
            LightWorld world=new LightWorld(remote);TileEntityProgrammableGlass tile=install(world);world.checkLight(POS);world.reset();
            tile.setAppearance(tile.getTint(),false);need(world.checks==1&&world.getLightFor(EnumSkyBlock.BLOCK,POS)==0&&world.getLightFor(EnumSkyBlock.BLOCK,POS.east())==0,"frame-off propagation stale");
            tile.setAppearance(tile.getTint(),true);need(world.checks==2&&world.getLightFor(EnumSkyBlock.BLOCK,POS)==level&&world.getLightFor(EnumSkyBlock.BLOCK,POS.east())==level-1,"frame-on propagation stale");
            tile.setAppearance(2,true);need(world.checks==2,"tint-only edit refreshed unchanged light");
            NBTTagCompound saved=tile.getUpdateTag();saved.setBoolean("GlassFramed",false);world.reset();tile.onDataPacket(null,new net.minecraft.network.play.server.SPacketUpdateTileEntity(POS,0,saved));
            need(world.checks==1&&world.getLightFor(EnumSkyBlock.BLOCK,POS.east())==0&&world.updates==0,"incoming tile update left stale light or echoed a packet");
            tile.frameAppearance.front=-1;world.reset();tile.setAppearance(2,true);need(world.checks==0,"dark frame needlessly refreshed light");need(world.chunkRequests==0,"light fixture loaded chunks");
        }
        System.out.println("glass-configuration light PASS native World.checkLightFor propagation server/client on/off/tint/NBT; missing-refresh negative controls="+negatives);
    }
    /** Identical pre-batching setter order; shares the corrected single-setter light behavior. */
    private static void unbatched(MessageProgrammableGlass message,TileEntityProgrammableGlass tile)throws Exception{
        tile.setSize((Integer)field(message,"size"));int mode=(Integer)field(message,"playbackMode");if(mode>=0)tile.glassPlayback.set(mode,(Integer)field(message,"playbackSpeed"));
        tile.setShade((Integer)field(message,"shade"));tile.setMaterial((Integer)field(message,"material"));tile.setTextureLayout((Integer)field(message,"layout"));tile.setJoin((Boolean)field(message,"join"));tile.setAppearance((Integer)field(message,"tint"),(Boolean)field(message,"framed"));
    }
    private static MessageProgrammableGlass roundtrip(MessageProgrammableGlass input)throws Exception{
        ByteBuf bytes=Unpooled.buffer(),again=Unpooled.buffer();try{
            input.toBytes(bytes);int expected=(Integer)field(input,"playbackMode")<0?25:28;need(bytes.readableBytes()==expected,"editor wire size changed");byte[] wire=new byte[expected];bytes.getBytes(0,wire);
            MessageProgrammableGlass decoded=new MessageProgrammableGlass();decoded.fromBytes(bytes);need(!bytes.isReadable(),"trailing packet bytes");decoded.toBytes(again);byte[] copy=new byte[again.readableBytes()];again.readBytes(copy);need(Arrays.equals(wire,copy),"wire roundtrip changed");return decoded;
        }finally{bytes.release();again.release();}
    }
    private static void transactions()throws Exception{
        int cases=0;
        for(int size=0;size<3;size++)for(int tint=0;tint<4;tint++)for(int layout=0;layout<3;layout++)for(int flags=0;flags<4;flags++)for(int mode=-1;mode<3;mode++)for(int speed=0;speed<3;speed++){
            MessageProgrammableGlass message=new MessageProgrammableGlass(POS,size,cases%GlassTextures.IDS.length,(flags&1)!=0).withAppearance(tint,(flags&2)!=0).withMaterial((cases&1)==0?-1:0).withTextureLayout(layout);
            if(mode>=0)message.withPlayback(mode,speed);message=roundtrip(message);
            TileEntityProgrammableGlass before=new TileEntityProgrammableGlass(),after=new TileEntityProgrammableGlass();before.glassPlayback.set(2,2);after.glassPlayback.set(2,2);unbatched(message,before);APPLY.invoke(message,after);
            NBTTagCompound expected=before.writeToNBT(new NBTTagCompound()),actual=after.writeToNBT(new NBTTagCompound());need(expected.equals(actual),"transaction changed native save state");
            TileEntityProgrammableGlass loaded=new TileEntityProgrammableGlass();loaded.readFromNBT(actual);need(actual.equals(loaded.writeToNBT(new NBTTagCompound())),"saved settings roundtrip changed");cases++;
        }
        MessageProgrammableGlass edit=roundtrip(new MessageProgrammableGlass(POS,0,2,false).withAppearance(2,false).withMaterial(0).withTextureLayout(0).withPlayback(2,0));
        LightWorld oldWorld=new LightWorld(false),world=new LightWorld(false);TileEntityProgrammableGlass before=install(oldWorld),after=install(world);oldWorld.checkLight(POS);world.checkLight(POS);oldWorld.reset();world.reset();
        unbatched(edit,before);APPLY.invoke(edit,after);
        need(oldWorld.updates==7&&world.updates==1&&oldWorld.dirty==7&&world.dirty==1&&oldWorld.ranges==4&&world.ranges==1,"settings update work not coalesced");
        need(world.checks==1&&before.getUpdateTag().equals(after.getUpdateTag()),"batched final payload/light differs");
        System.out.println("glass-configuration transaction counts old/new notify="+oldWorld.updates+"/"+world.updates+" dirty="+oldWorld.dirty+"/"+world.dirty+" render-range="+oldWorld.ranges+"/"+world.ranges+" incoming-bytes=28/28; outbound packet savings not assumed");
        world.reset();APPLY.invoke(edit,after);need(world.updates==0&&world.dirty==0&&world.ranges==0&&world.checks==0,"identical transaction produced work");
        ByteBuf wire=Unpooled.buffer();try{
            edit.toBytes(wire);
            for(int length:new int[]{17,18,19,23,24,25}){ByteBuf legacy=wire.copy(0,length);try{MessageProgrammableGlass decoded=new MessageProgrammableGlass();decoded.fromBytes(legacy);TileEntityProgrammableGlass a=new TileEntityProgrammableGlass(),b=new TileEntityProgrammableGlass();a.glassPlayback.set(2,0);b.glassPlayback.set(2,0);unbatched(decoded,a);APPLY.invoke(decoded,b);need(a.getUpdateTag().equals(b.getUpdateTag())&&b.glassPlayback.mode()==2,"legacy packet/default playback changed");}finally{legacy.release();}}
        }finally{wire.release();}
        System.out.println("glass-configuration transactions PASS cases="+cases+" exact saved state,25/28-byte wire roundtrips,legacy suffixes,unchanged replay");
    }
    private static void carried()throws Exception{
        for(boolean remote:new boolean[]{false,true}){
            LightWorld parent=new LightWorld(remote);VehicleWorld view=new VehicleWorld(parent,VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(POS,glass.getDefaultState(),null))));TileEntityProgrammableGlass tile=tile(view);view.tiles.put(POS,tile);int level=tile.frameAppearance.light();
            need(view.lightSources().get(POS)==level,"carried light fixture");oldAppearance(tile,tile.getTint(),false);need(view.lightSources().get(POS)==level,"old carried stale cache not reproduced");
            tile.setAppearance(tile.getTint(),true);tile.setAppearance(tile.getTint(),false);need(!view.lightSources().containsKey(POS)&&view.getLightFor(EnumSkyBlock.BLOCK,POS.east())==0,"carried frame off retained local light");
            tile.setAppearance(tile.getTint(),true);need(view.getLightFor(EnumSkyBlock.BLOCK,POS.east())==level-1,"carried frame on failed local propagation");
            NBTTagCompound tag=tile.getUpdateTag();tag.setBoolean("GlassFramed",false);tile.handleUpdateTag(tag);need(!view.lightSources().containsKey(POS),"carried NBT retained local light");
            final int[] updates={0};view.controlUpdates=pos->updates[0]++;APPLY.invoke(new MessageProgrammableGlass(POS,0,2,false).withAppearance(2,true).withMaterial(0).withTextureLayout(0).withPlayback(2,0),tile);
            need(updates[0]==2,"carried transaction not one dirty plus one update");need(parent.updates==0&&parent.dirty==0&&parent.checks==0&&parent.chunkRequests==0,"carried light edit mutated or loaded parent chunks");
        }
        System.out.println("glass-configuration carried PASS local source/propagation invalidation,server/client parents,NBT,one dirty+update,no parent mutation/chunk requests");
    }
    private static void loadSafety(){
        com.vandorlabs.CommonProxy previous=com.vandorlabs.VandorLabs.proxy;
        com.vandorlabs.VandorLabs.proxy=new com.vandorlabs.CommonProxy(){@Override public void glassLightRefreshNeeded(TileEntityProgrammableGlass tile){if(tile.getWorld().isRemote)GlassLightUpdates.schedule(tile);else super.glassLightRefreshNeeded(tile);}};
        try{
            TileEntityProgrammableGlass source=new TileEntityProgrammableGlass();source.setPos(POS);source.frameAppearance.front=luminous;NBTTagCompound data=source.getUpdateTag();
            for(boolean remote:new boolean[]{false,true})for(boolean replace:new boolean[]{false,true})for(boolean worldFirst:new boolean[]{false,true}){
                NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(remote);world.states.put(POS,glass.getDefaultState());world.tileLoadGuard=true;
                TileEntityProgrammableGlass tile;
                try{
                    if(worldFirst){tile=new TileEntityProgrammableGlass();tile.setWorld(world);tile.readFromNBT(data.copy());}
                    else{tile=(TileEntityProgrammableGlass)TileEntity.create(world,data.copy());need(tile!=null,"native TileEntity.create failed");need(tile.getWorld()==null,"native create fixture no longer decodes unattached");}
                    tile.setWorld(world);tile.setPos(POS);world.tiles.put(POS,tile);tile.onLoad();
                    need(world.stateReads==0&&world.lightChecks==0&&world.updates==0,"NBT/onLoad traversed the world before installation");
                }finally{world.tileLoadGuard=false;}
                TileEntity installed=replace?new TileEntityProgrammableGlass():tile;installed.setWorld(world);installed.setPos(POS);world.tiles.put(POS,installed);
                if(remote)GlassLightUpdates.drain();else DeferredTileLoad.flush(world);
                need(replace?world.lightChecks==0:world.lightChecks>0,"deferred light refresh ignored installed tile identity remote="+remote+" replaced="+replace+" worldFirst="+worldFirst);
            }
            for(boolean remote:new boolean[]{false,true}){
                NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(remote);world.states.put(POS,glass.getDefaultState());TileEntityProgrammableGlass tile=new TileEntityProgrammableGlass();tile.setWorld(world);tile.readFromNBT(data.copy());
                if(remote)GlassLightUpdates.drain();else DeferredTileLoad.flush(world);
                need(world.lightChecks==0,"uninstalled tile refreshed light");
                world.tiles.put(POS,tile);world.tileLoadGuard=true;int reads=world.stateReads;try{tile.onLoad();need(world.stateReads==reads,"rearmed light traversed world during onLoad");}finally{world.tileLoadGuard=false;}
                if(remote)GlassLightUpdates.drain();else DeferredTileLoad.flush(world);
                need(world.lightChecks>0,"skipped pending light was not rearmed on later installation");
            }
        }finally{com.vandorlabs.VandorLabs.proxy=previous;}
        System.out.println("glass-configuration load PASS actual TileEntity.create and local onLoad have zero eager world/light reads; server/client settlement skips replacement tiles");
    }
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();com.vandorlabs.VandorLabs.proxy=new com.vandorlabs.CommonProxy();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityProgrammableGlass.class,new net.minecraft.util.ResourceLocation("minecraft","glass_configuration_checks"));
        glass=new BlockProgrammableGlass("glass_configuration_checks");luminous=-1;for(int i=0;i<ScreenHousingTextures.IDS.length;i++){FrameAppearance frame=new FrameAppearance();frame.front=i;if(FrameAppearance.valid(i)&&frame.light()>0){luminous=i;break;}}need(luminous>=0,"missing luminous frame fixture");
        lighting();transactions();carried();loadSafety();System.out.println("glass-configuration PASS");
    }
    private GlassConfigurationChecks(){}
}
