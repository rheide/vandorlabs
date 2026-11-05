package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.shape.*;
import com.vandorlabs.persistence.StairState;
import com.vandorlabs.ui.ProgrammableBlockDialog;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Small actual server-menu and fixed-payload boundary. Native widget/client acceptance remains separate. */
@EventBusSubscriber(modid="vandorlabs")
public final class ShapeEditorChecks {
    private static int checks;
    private static void require(boolean value,String message){checks++;if(!value)throw new IllegalStateException("Native shape editor: "+message);}
    private static ProgrammableShapeEntity.Appearance edit(){return new ProgrammableShapeEntity.Appearance(new ProgrammableBlockDialog.State(7,2,false,new int[]{1,2,3,4,5,6},-1),9,new StairState(16,2));}
    private static byte[] encode(ServerLevel level,ShapeAppearancePayload value){var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),level.registryAccess());try{ShapeAppearancePayload.CODEC.encode(buffer,value);byte[] bytes=new byte[buffer.readableBytes()];buffer.readBytes(bytes);return bytes;}finally{buffer.release();}}
    private static ShapeAppearancePayload decode(ServerLevel level,byte[] bytes){var buffer=new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes),level.registryAccess());try{return ShapeAppearancePayload.CODEC.decode(buffer);}finally{buffer.release();}}
    private static void rejected(ServerLevel level,byte[] bytes){boolean failed=false;try{decode(level,bytes);}catch(IllegalArgumentException expected){failed=true;}require(failed,"malformed fixed-size payload rejected before use");}
    @SubscribeEvent public static void start(ServerStartedEvent event)throws Exception{
        if(!System.getProperty("vandorlabs.transmitterPhase","").equals("shape-editor"))return;var level=event.getServer().overworld();var pos=new BlockPos(2500,100,2500);level.getChunkAt(pos);level.setBlock(pos,VandorLabsBootstrap.PROGRAMMABLE_STAIRS.get().defaultBlockState(),3);var tile=(ProgrammableShapeEntity)level.getBlockEntity(pos);
        var player=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+2.5);player.setGameMode(GameType.SURVIVAL);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get()));player.containerMenu=new ProgrammableMenu(57,player.getInventory(),pos);var packet=new ShapeAppearancePayload(pos,57,edit());
        byte[] bytes=encode(level,packet);require(bytes.length==48,"fixed48 byte message");var round=decode(level,bytes);require(round.pos().equals(pos)&&round.windowId()==57&&round.appearance().sideTexture()==9&&round.appearance().stairs().packed()==StairState.packed(16,2)&&Arrays.equals(round.appearance().housing().faces(),new int[]{1,2,3,4,5,6}),"complete bounded roundtrip and dormant arrays");
        for(int length=0;length<48;length++)rejected(level,Arrays.copyOf(bytes,length));rejected(level,Arrays.copyOf(bytes,49));
        for(int offset:new int[]{20,21,46,47}){byte[] invalid=bytes.clone();invalid[offset]=(byte)(offset==21?2:3);rejected(level,invalid);}
        for(int offset:new int[]{8,12,16,22}){byte[] invalid=bytes.clone();var buffer=Unpooled.wrappedBuffer(invalid);buffer.setInt(offset,-2);buffer.release();rejected(level,invalid);}
        require(ShapeAppearancePayload.apply(player,round)&&tile.appearance().stairs().packed()==StairState.packed(16,2)&&tile.appearance().sideTexture()==9,"actual authenticated immediate menu edit");var stable=tile.appearance();require(!ShapeAppearancePayload.apply(player,new ShapeAppearancePayload(pos,58,edit()))&&tile.appearance()==stable,"stale session rejected atomically");player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);require(!ShapeAppearancePayload.apply(player,packet),"survival authorizing held tool remains required");player.setGameMode(GameType.CREATIVE);require(ShapeAppearancePayload.apply(player,packet),"creative editor permitted");player.setGameMode(GameType.SPECTATOR);require(!ShapeAppearancePayload.apply(player,packet),"spectator denied");player.setGameMode(GameType.CREATIVE);player.setPos(pos.getX()+20,pos.getY(),pos.getZ());require(!ShapeAppearancePayload.apply(player,packet),"out of reach denied");player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+2.5);
        var offThread=new java.util.concurrent.atomic.AtomicBoolean(true);Thread other=new Thread(()->offThread.set(ShapeAppearancePayload.apply(player,packet)));other.start();other.join();require(!offThread.get(),"worker cannot enter world edit path");level.removeBlock(pos,false);level.setBlock(pos,VandorLabsBootstrap.PROGRAMMABLE_STAIRS.get().defaultBlockState(),3);require(!ShapeAppearancePayload.apply(player,packet),"replaced same-position tile denied by exact identity");extension();
        System.out.println("[vandorlabs][shape-editor] dedicated PASS checks="+checks+" bounded48byte codec actual session auth and atomic local extension hooks");event.getServer().halt(false);
    }
    public static final class Extension extends ProgrammableShapeEntity {
        boolean initialized;int published;long key;
        Extension(){super(VandorLabsBootstrap.PROGRAMMABLE_ENTITY.get(),BlockPos.ZERO,VandorLabsBootstrap.PROGRAMMABLE_SLAB.get().defaultBlockState(),false);initialized=true;}
        @Override protected void appearancePublished(){require(initialized,"no virtual publication from base constructor");published++;require(appearance().housing()==settings(),"derived projection observes completed housing");}
        @Override protected long fluidGeometryKey(){return key;}
        @Override protected java.util.List<net.minecraft.world.phys.AABB> fluidSolidBoxes(net.minecraft.world.level.block.state.BlockState state,long value){return java.util.List.of(new net.minecraft.world.phys.AABB(0,value==0?0:.5,0,1,value==0?.5:1,1));}
    }
    private static void extension(){var host=new Extension();require(host.published==0,"constructor leaves derived fields alone");host.applyShape(edit());require(host.published==1,"one complete derived projection per transaction");host.refreshFluidGeometry();var volume=host.fluidVolumeSnapshot();long revision=host.fluidGeometryRevision();host.refreshFluidGeometry();require(host.fluidVolumeSnapshot()==volume&&host.fluidGeometryRevision()==revision,"same local geometry key reuses immutable volume");host.key++;host.refreshFluidGeometry();require(host.fluidGeometryRevision()==revision+1&&host.fluidVolumeSnapshot()!=volume&&!host.fluidVolumeSnapshot().facesMaterialized(),"family geometry key invalidates once without eager faces");require(host.getModelData().get(com.vandorlabs.neoforge.fluid.NativeFluidRenderSnapshot.DATA)==host.fluidRenderSnapshot(),"worker model data reads published fluid snapshot field only");}
}
