package com.vandorlabs.neoforge.shape;

import com.vandorlabs.neoforge.ProgrammableBlockEntity;
import com.vandorlabs.neoforge.VandorLabsModern;
import com.vandorlabs.persistence.StairState;
import com.vandorlabs.render.StairGeometry;
import com.vandorlabs.ui.SlabStairDialog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;

/** Installed shape settings, exact joins, permissions, complete item snapshots and merge semantics. */
public final class ShapeRuntimeChecks {
    private static final BlockPos SLAB=new BlockPos(240,80,8),STAIR=new BlockPos(244,80,8),MERGE=new BlockPos(256,80,8);
    private ShapeRuntimeChecks(){}
    private static void need(boolean ok,String message){if(!ok)throw new IllegalStateException("Modern programmable shape: "+message);}
    private static SlabStairDialog.State state(int main,int side,int layout,boolean faces,StairState stairs){return new SlabStairDialog.State(main,side,layout,faces,new int[]{0,1,2,3,-1,5},-1,stairs);}
    public static ProgrammableShapeEntity install(ServerLevel level,BlockPos pos,BlockState block){level.getChunkAt(pos);level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);for(Direction side:Direction.values())level.setBlock(pos.relative(side),Blocks.AIR.defaultBlockState(),3);level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);level.setBlock(pos,block,3);need(level.getBlockEntity(pos) instanceof ProgrammableShapeEntity,"registered tile identity");return (ProgrammableShapeEntity)level.getBlockEntity(pos);}
    public static void run(ServerLevel level){
        var slab=install(level,SLAB,ShapeRegistration.SLAB.get().defaultBlockState());var stairs=install(level,STAIR,ShapeRegistration.STAIRS.get().defaultBlockState());
        for(var tile:java.util.List.of(slab,stairs)){need(tile.settings().layout==1&&tile.sideTexture()==-1&&tile.stairState().packed()==8,"fresh authored defaults");need(!tile.containedFluids().any(),"fresh fluid slots empty");need(tile.getBlockState().getDestroySpeed(level,tile.getBlockPos())==5&&tile.getBlockState().requiresCorrectToolForDrops(),"metal hardness/tool");}
        need(slab.getBlockState().getLightBlock(level,SLAB)==0&&stairs.getBlockState().getLightBlock(level,STAIR)==15,"reference slab/stair opacity");
        var player=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);player.setGameMode(GameType.CREATIVE);player.setShiftKeyDown(false);player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        int cases=0;
        for(var tile:java.util.List.of(slab,stairs)){
            BlockPos pos=tile.getBlockPos();player.setPos(pos.getX()+.5,80,pos.getZ()-3);var menu=new ShapeMenu(211,player.getInventory(),pos);player.containerMenu=menu;
            var configured=state(5,6,2,true,new StairState(4,1));var packet=new ShapePayload(211,pos,configured);need(ShapePayload.apply(player,packet),"current exact menu accepts settings");
            need(!ShapePayload.apply(player,new ShapePayload(212,pos,configured))&&!ShapePayload.apply(player,new ShapePayload(211,pos.east(),configured)),"wrong session/position rejected");
            need(!ShapePayload.apply(player,new ShapePayload(211,pos,state(5,-2,2,true,new StairState(4,1))))&&!ShapePayload.apply(player,new ShapePayload(211,pos,state(5,6,3,true,new StairState(4,1))))&&!ShapePayload.apply(player,new ShapePayload(211,pos,state(5,6,2,true,new StairState(3,1)))) ,"bounded side/layout/stair settings");
            player.setPos(1000,80,1000);need(!ShapePayload.apply(player,packet),"remote menu rejected");player.setPos(pos.getX()+.5,80,pos.getZ()-3);
            player.setGameMode(GameType.SURVIVAL);need(!ShapePayload.apply(player,packet),"survival without Configurizer rejected");player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VandorLabsModern.CONFIGURIZER.get()));need(ShapePayload.apply(player,packet),"survival Configurizer accepted");player.setGameMode(GameType.CREATIVE);player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            var bytes=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),level.registryAccess());try{ShapePayload.CODEC.encode(bytes,packet);var copy=ShapePayload.CODEC.decode(bytes);need(copy.menuId()==211&&copy.pos().equals(pos)&&copy.settings().side==6&&copy.settings().stairs.packed()==36&&java.util.Arrays.equals(copy.settings().faces(),configured.faces()),"fixed-size six-face payload roundtrip");}finally{bytes.release();}
            var saved=tile.saveWithoutMetadata(level.registryAccess());var unknown=new net.minecraft.nbt.CompoundTag();unknown.putString("owner","opaque-reference");unknown.putIntArray("values",new int[]{7,2,19});saved.put("FutureSettings",unknown);var fluids=new net.minecraft.nbt.CompoundTag();var raw=new net.minecraft.nbt.CompoundTag();raw.putString("FluidName","missing_test:retained_fluid");raw.putInt("Amount",1000);raw.put("Tag",unknown.copy());fluids.put("Side1",raw);saved.put("ContainedFluids",fluids);tile.loadWithComponents(saved,level.registryAccess());
            need(tile.containedFluids().occupied(1)&&tile.containedFluids().get(1).isEmpty(),"unresolved opaque fluid remains occupied/inert");
            var picked=tile.getBlockState().getBlock().getCloneItemStack(tile.getBlockState(),new BlockHitResult(Vec3.atCenterOf(pos),Direction.NORTH,pos,false),level,pos,player);
            var drops=Block.getDrops(tile.getBlockState(),level,pos,tile,player,new ItemStack(Items.STONE_PICKAXE));need(drops.size()==1,"single configured drop");
            for(var stack:java.util.List.of(picked,drops.get(0))){var data=stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);need(data!=null,"configured block entity component");var tag=data.copyTag();need(tag.getCompound("FutureSettings").equals(unknown)&&tag.getCompound("ContainedFluids").getCompound("Side1").equals(raw)&&tag.getInt("SideTexture")==6&&tag.getInt("StairTread")==4&&tag.getInt("StairRoll")==1&&tag.getBoolean("FaceTexturesEnabled"),"complete configured snapshot including opaque fluid/settings");}
            var dormant=state(5,6,2,false,new StairState(4,1));need(tile.applyShape(dormant)&&java.util.Arrays.equals(tile.settings().faces(),configured.faces()),"disabled face choices retained");tile.applyShape(configured);cases++;
        }
        player.containerMenu=player.inventoryMenu;
        // Actual loaded neighbour joins, including a different vanilla stair material and opposite-half exclusion.
        var block=ShapeRegistration.STAIRS.get();for(Direction face:Direction.Plane.HORIZONTAL)for(boolean upper:new boolean[]{false,true}){
            var root=block.defaultBlockState().setValue(StairBlock.FACING,face).setValue(StairBlock.HALF,upper?Half.TOP:Half.BOTTOM);stairs=install(level,STAIR,root);stairs.applyShape(state(0,-1,1,false,new StairState(8,0)));
            need(block.connected(root,level,STAIR).getValue(StairBlock.SHAPE)==StairsShape.STRAIGHT,"straight join");
            var turn=Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING,face.getCounterClockWise()).setValue(StairBlock.HALF,upper?Half.TOP:Half.BOTTOM);level.setBlock(STAIR.relative(face),turn,3);need(block.connected(root,level,STAIR).getValue(StairBlock.SHAPE)==StairsShape.OUTER_LEFT,"outer vanilla-material join");level.setBlock(STAIR.relative(face),Blocks.AIR.defaultBlockState(),3);level.setBlock(STAIR.relative(face.getOpposite()),turn,3);need(block.connected(root,level,STAIR).getValue(StairBlock.SHAPE)==StairsShape.INNER_LEFT,"inner vanilla-material join");stairs.applyShape(state(0,-1,1,false,new StairState(8,1)));need(block.connected(root,level,STAIR).getValue(StairBlock.SHAPE)==StairsShape.STRAIGHT,"rolled stairs ignore all joins");cases+=4;
        }
        int shapes=0;for(Direction face:Direction.Plane.HORIZONTAL)for(boolean upper:new boolean[]{false,true})for(StairsShape shape:StairsShape.values())for(int tread:new int[]{1,2,4,8,16})for(int roll=0;roll<3;roll++){
            var rawState=block.defaultBlockState().setValue(StairBlock.FACING,face).setValue(StairBlock.HALF,upper?Half.TOP:Half.BOTTOM).setValue(StairBlock.SHAPE,shape);var settings=new StairState(tread,roll);var nativeShape=ShapeGeometry.shape(rawState,settings);var boxes=ShapeGeometry.boxes(rawState,settings);need(!nativeShape.isEmpty()&&boxes.size()==(16/tread)*(16/tread),"authored native shape matrix");shapes++;
        }
        merge(level,player);slab=install(level,SLAB,ShapeRegistration.SLAB.get().defaultBlockState());stairs=install(level,STAIR,block.defaultBlockState());slab.applyShape(state(5,6,2,true,new StairState(4,1)));stairs.applyShape(state(7,8,1,true,new StairState(2,2)));
        System.out.println("[vandorlabs][modern] shape-write PASS "+cases+" installed/session/join cases, "+shapes+" native authored shape states, full opaque configured snapshots and real slab merge");
    }
    private static void merge(ServerLevel level,net.minecraft.server.level.ServerPlayer player){
        var tile=install(level,MERGE,ShapeRegistration.SLAB.get().defaultBlockState().setValue(ProgrammableSlabBlock.FACING,Direction.WEST));tile.applyShape(state(9,10,2,false,new StairState(2,2)));var tag=tile.saveWithoutMetadata(level.registryAccess());tag.putString("OpaqueMergeField","must-survive-full-cube");var retainedFluid=new net.minecraft.nbt.CompoundTag();retainedFluid.putString("FluidName","missing_test:merged_fluid");retainedFluid.putInt("Amount",1000);var fluidSlots=new net.minecraft.nbt.CompoundTag();fluidSlots.put("Side0",retainedFluid);tag.put("ContainedFluids",fluidSlots);tile.loadWithComponents(tag,level.registryAccess());
        player.setPos(MERGE.getX()+.5,80,MERGE.getZ()-3);player.setGameMode(GameType.SURVIVAL);ItemStack held=new ItemStack(ShapeRegistration.SLAB_ITEM.get(),3);player.setItemInHand(InteractionHand.MAIN_HAND,held);
        var result=held.getItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(MERGE).add(0,.5,0),Direction.UP,MERGE,false)));need(result.consumesAction()&&level.getBlockState(MERGE).is(VandorLabsModern.PROGRAMMABLE_BLOCK.get())&&held.getCount()==2,"actual direct slab merge consumes one item");need(level.getBlockEntity(MERGE) instanceof ProgrammableBlockEntity,"merged cube entity");var saved=level.getBlockEntity(MERGE).saveWithoutMetadata(level.registryAccess());need(saved.getInt("housingTexture")==9&&saved.getInt("SideTexture")==10&&saved.getInt("StairRoll")==2&&saved.getInt("StairTread")==2&&saved.getString("OpaqueMergeField").equals("must-survive-full-cube")&&java.util.Arrays.equals(saved.getIntArray("FaceTextures"),new int[]{0,1,2,3,-1,5}),"merge retains installed entire snapshot and dormant choices");need(saved.getCompound("ContainedFluids").getCompound("Side0").equals(retainedFluid),"merged cube retains opaque contained fluid snapshot");player.setGameMode(GameType.CREATIVE);player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
    }
    public static void prepareRead(ServerLevel level){level.getChunkAt(SLAB);level.getChunkAt(STAIR);level.getChunkAt(MERGE);}
    public static void read(ServerLevel level){var slab=(ProgrammableShapeEntity)level.getBlockEntity(SLAB);var stairs=(ProgrammableShapeEntity)level.getBlockEntity(STAIR);need(slab.shapeSettings().main==5&&slab.sideTexture()==6&&slab.settings().layout==2&&slab.settings().facesEnabled&&stairs.shapeSettings().main==7&&stairs.sideTexture()==8&&stairs.stairState().packed()==66,"subsequent process restores shape/material state");need(level.getBlockEntity(MERGE).saveWithoutMetadata(level.registryAccess()).getString("OpaqueMergeField").equals("must-survive-full-cube"),"merged opaque snapshot persisted");System.out.println("[vandorlabs][modern] shape-read PASS slab/stair side/face/layout/roll/tread and full merged snapshot across process restart");}
    public static void clientFixture(ServerLevel level){for(int x=112;x<=252;x++)for(int z=-6;z<=11;z++)level.setBlock(new BlockPos(x,79,z),Blocks.SMOOTH_STONE.defaultBlockState(),3);for(var entry:java.util.List.of(new Object[]{SLAB,ShapeRegistration.SLAB.get().defaultBlockState()},new Object[]{STAIR,ShapeRegistration.STAIRS.get().defaultBlockState()})){var tile=install(level,(BlockPos)entry[0],(BlockState)entry[1]);int[] faces=new int[6];java.util.Arrays.fill(faces,-1);tile.applyShape(new SlabStairDialog.State(0,-1,1,false,faces,-1,new StairState(8,0)));}}
}
