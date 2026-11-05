package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.shape.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Registered wall factory, physical topology and configured item boundaries; rendered acceptance is separate. */
@EventBusSubscriber(modid="vandorlabs")
public final class WallRegistrationChecks {
    private static int checks;
    private static void require(boolean value,String message){checks++;if(!value)throw new IllegalStateException("Native wall registration: "+message);}
    @SubscribeEvent public static void start(ServerStartedEvent event){
        if(!System.getProperty("vandorlabs.transmitterPhase","").equals("wall-registration"))return;
        var level=event.getServer().overworld();var pos=new BlockPos(2800,100,2800);level.getChunkAt(pos);var block=VandorLabsBootstrap.PROGRAMMABLE_WALL.get();int cases=0;
        for(Direction facing:Direction.Plane.HORIZONTAL)for(int depth=0;depth<3;depth++)for(boolean inverted:new boolean[]{false,true}){
            var state=block.defaultBlockState().setValue(NativeFlatWallBlock.FACING,facing).setValue(NativeFlatWallBlock.DEPTH,depth).setValue(NativeFlatWallBlock.INVERTED,inverted);level.setBlock(pos,state,3);
            var tile=(NativeWallEntity)level.getBlockEntity(pos);require(tile!=null&&tile.getType()==VandorLabsBootstrap.PROGRAMMABLE_ENTITY.get(),"canonical shared BE factory");require(tile.settings().layout==0&&!tile.isPorthole(),"current Stretch factory default");tile.refreshFluidGeometry();
            var shape=state.getShape(level,pos);double volume=0;for(AABB box:shape.toAabbs())volume+=box.getXsize()*box.getYsize()*box.getZsize();require(volume==.25,"four-pixel wall physical volume");
            for(Direction side:Direction.values())require(!state.isFaceSturdy(level,pos,side,SupportType.CENTER),"legacy non-solid panel attachment face");level.removeBlock(pos,false);cases++;
        }
        var north=block.defaultBlockState();level.setBlock(pos,north,3);level.setBlock(pos.north(),north.setValue(NativeFlatWallBlock.FACING,Direction.EAST),3);level.setBlock(pos.south(),north.setValue(NativeFlatWallBlock.FACING,Direction.WEST),3);level.setBlock(pos.east(),north,3);
        var tile=(NativeWallEntity)level.getBlockEntity(pos);tile.refreshFluidGeometry();var shape=north.getShape(level,pos);require(shape.toAabbs().stream().noneMatch(box->box.contains(.9,.5,.1))&&shape.toAabbs().stream().anyMatch(box->box.contains(.9,.5,.5)),"actual joined T keeps empty corners and the straight arm");long revision=tile.modelDataRevision();level.removeBlock(pos.north(),false);tile.refreshFluidGeometry();require(tile.modelDataRevision()>revision,"derived dry topology changes model revision");
        var tag=tile.saveWithoutMetadata(level.registryAccess());tag.putInt("HousingTextureVersion",1);tag.putInt("housingTexture",7);tag.putInt("SideTexture",9);CompoundTag opaque=new CompoundTag();opaque.putString("owner","foreign:wall");tag.put("ForgeCaps",opaque);tile.loadWithComponents(tag,level.registryAccess());
        var picked=block.getCloneItemStack(north,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false),level,pos,null);require(picked.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA).copyTag().getCompound("ForgeCaps").equals(opaque),"configured pick retains opaque data");var drops=Block.getDrops(north,level,pos,tile,null,new ItemStack(Items.IRON_PICKAXE));require(drops.size()==1&&drops.getFirst().is(VandorLabsBootstrap.PROGRAMMABLE_WALL_ITEM.get())&&drops.getFirst().get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA)!=null,"configured wall loot");
        level.removeBlock(pos,false);level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);var player=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);player.setGameMode(GameType.SURVIVAL);player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+3);var held=new ItemStack(VandorLabsBootstrap.PROGRAMMABLE_WALL_ITEM.get(),2);player.setItemInHand(InteractionHand.MAIN_HAND,held);
        var result=held.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(new Vec3(pos.getX()+.5,pos.getY(),pos.getZ()+.1),Direction.UP,pos.below(),false)));require(result.consumesAction()&&held.getCount()==1&&level.getBlockState(pos).is(block),"real survival wall item placement consumes one");require(level.getBlockState(pos).getValue(NativeFlatWallBlock.DEPTH)==1,"edge click selects near depth");
        System.out.println("[vandorlabs][wall-registration] dedicated PASS cases="+cases+" checks="+checks+" actual factory collision topology configured pick/loot and survival placement");event.getServer().halt(false);
    }
}
