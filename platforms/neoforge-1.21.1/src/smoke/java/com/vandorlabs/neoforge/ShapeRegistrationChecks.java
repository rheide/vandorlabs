package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.shape.*;
import com.vandorlabs.neoforge.fluid.NativeFluidWorld;
import com.vandorlabs.persistence.StairState;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Small installed registration/player-item boundary; exhaustive renderer/live acceptance is separate. */
@EventBusSubscriber(modid="vandorlabs")
public final class ShapeRegistrationChecks {
    private static int checks;
    private static void require(boolean value,String message){checks++;if(!value)throw new IllegalStateException("Native shape registration: "+message);}
    @SubscribeEvent public static void start(ServerStartedEvent event){
        if(!System.getProperty("vandorlabs.transmitterPhase","").equals("shape-registration"))return;
        var level=event.getServer().overworld();var pos=new BlockPos(2400,100,2400);level.getChunkAt(pos);int cases=0;
        for(Direction face:Direction.values())for(Half half:Half.values()){
            var state=VandorLabsBootstrap.PROGRAMMABLE_SLAB.get().defaultBlockState().setValue(NativeProgrammableSlab.FACING,face).setValue(NativeProgrammableSlab.HALF,half);level.setBlock(pos,state,3);var tile=(ProgrammableShapeEntity)level.getBlockEntity(pos);require(tile!=null&&!tile.isStair()&&tile.getType()==VandorLabsBootstrap.PROGRAMMABLE_ENTITY.get(),"shared actual registered slab BE type");require(tile.saveWithId(level.registryAccess()).getString("id").equals("vandorlabs:programmable_viewscreen"),"canonical legacy shared BE id");require(state.getShape(level,pos).bounds().getYsize()==.5,"native half-slab collision");NativeFluidWorld.changed(tile);require(!tile.fluidVolumeSnapshot().cells().isEmpty(),"actual deferred-service geometry resolves free slab volume");level.removeBlock(pos,false);cases++;
        }
        var lower=VandorLabsBootstrap.PROGRAMMABLE_SLAB.get().defaultBlockState();level.setBlock(pos,lower,3);level.setBlock(pos.east(),lower,3);require(!Block.shouldRenderFace(lower,level,pos,Direction.EAST,pos.east()),"matching half slab contact culled");level.setBlock(pos.east(),lower.setValue(NativeProgrammableSlab.HALF,Half.TOP),3);require(Block.shouldRenderFace(lower,level,pos,Direction.EAST,pos.east()),"opposite half slab leaves visible face");level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);require(!Block.shouldRenderFace(lower,level,pos,Direction.DOWN,pos.below()),"full floor culls lower slab underside");level.removeBlock(pos.east(),false);
        for(Half half:Half.values()){
            var state=VandorLabsBootstrap.PROGRAMMABLE_STAIRS.get().defaultBlockState().setValue(NativeProgrammableStairs.HALF,half);level.setBlock(pos,state,3);var tile=(ProgrammableShapeEntity)level.getBlockEntity(pos);require(tile!=null&&tile.isStair()&&!state.getValue(NativeProgrammableStairs.WATERLOGGED),"native stair registered and vanilla fluid bit stays false");var saved=tile.saveWithoutMetadata(level.registryAccess());saved.putInt("StairTread",16);saved.putInt("StairRoll",2);saved.putInt("SideTexture",9);saved.putIntArray("FaceTextures",new int[]{1,2,3,4,5,6});saved.putBoolean("FaceTexturesEnabled",false);var opaque=new CompoundTag();opaque.putString("owner","foreign:shape");saved.put("ForgeCaps",opaque);tile.loadWithComponents(saved,level.registryAccess());require(tile.stairState().packed()==StairState.packed(16,2),"actual installed saved tread16 roll2");var picked=state.getBlock().getCloneItemStack(state,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false),level,pos,null);require(picked.is(VandorLabsBootstrap.PROGRAMMABLE_STAIRS_ITEM.get())&&picked.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA).copyTag().getCompound("ForgeCaps").equals(opaque),"configured stair pick owns opaque component");var drops=Block.getDrops(state,level,pos,tile,null,new ItemStack(Items.IRON_PICKAXE));require(drops.size()==1&&drops.getFirst().is(VandorLabsBootstrap.PROGRAMMABLE_STAIRS_ITEM.get())&&drops.getFirst().get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA)!=null,"configured loot pipeline");level.removeBlock(pos,false);cases++;
        }
        var slab=VandorLabsBootstrap.PROGRAMMABLE_SLAB.get().defaultBlockState();level.setBlock(pos,slab,3);var tile=(ProgrammableShapeEntity)level.getBlockEntity(pos);var source=tile.saveWithoutMetadata(level.registryAccess());source.putInt("HousingTextureVersion",1);source.putInt("housingTexture",7);source.putInt("SideTexture",9);source.putIntArray("FaceTextures",new int[]{1,2,3,4,5,6});source.putBoolean("FaceTexturesEnabled",false);tile.loadWithComponents(source,level.registryAccess());var player=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+3);var stack=new ItemStack(VandorLabsBootstrap.PROGRAMMABLE_SLAB_ITEM.get(),2);player.setItemInHand(InteractionHand.MAIN_HAND,stack);var result=stack.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos).add(0,.5,0),Direction.UP,pos,false)));require(result.consumesAction()&&stack.getCount()==1&&level.getBlockState(pos).is(VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get()),"actual survival slab-item merge consumes exactly one");var merged=(ProgrammableBlockEntity)level.getBlockEntity(pos);require(merged.settings().mainTexture==7&&!merged.settings().facesEnabled&&merged.settings().face(0)==1&&merged.saveWithoutMetadata(level.registryAccess()).getInt("SideTexture")==9,"merge keeps placed side and dormant faces");
        System.out.println("[vandorlabs][shape-registration] dedicated PASS cases="+cases+" checks="+checks+" actual registered tile identity collision configured pick/loot survival item merge");event.getServer().halt(false);
    }
}
