package com.vandorlabs.copychecks;

import com.vandorlabs.neoforge.*;
import com.vandorlabs.persistence.ControlMountSettings;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

/** Disposable actual four-detent control scene, separate from the production model/backend. */
public final class SignalControlScene {
    public static final double X=1000.5,Y=85,Z=994.5;
    public static BlockPos support(int row,int column){return new BlockPos(996+column*3,row==0?83:87,1005);}
    public static BlockPos position(int row,int column){return row==0?support(row,column).above():support(row,column).north();}
    public static NativeSignalControlBlock block(int column){return switch(column){case 0->VandorLabsBootstrap.THRUSTER_LEVER.get();case 1->VandorLabsBootstrap.AIRLINER_THROTTLE.get();case 2->VandorLabsBootstrap.FIGHTER_THROTTLE.get();case 3->VandorLabsBootstrap.WALL_SLIDER.get();default->throw new IllegalArgumentException();};}
    public static int step(int row,int column){return (row+column)%4;}
    public static void setup(ServerLevel level){
        for(int x=993;x<=1008;x++)for(int z=992;z<=1009;z++)level.setBlock(new BlockPos(x,82,z),Blocks.SMOOTH_STONE.defaultBlockState(),3);
        for(int row=0;row<2;row++)for(int column=0;column<4;column++){
            var block=block(column);BlockPos support=support(row,column),pos=position(row,column);level.setBlock(support,Blocks.IRON_BLOCK.defaultBlockState(),3);Direction heading=row==0?Direction.UP:Direction.NORTH;
            var state=block.defaultBlockState().setValue(NativeSignalControlBlock.FACING,heading);level.setBlock(pos,state,3);var tile=(NativeSignalControlBlockEntity)level.getBlockEntity(pos);var mount=ControlMountSettings.of(row==0?2:3,row==0?1:3,column);require(tile.configureMount(mount),"actual mount edit refused");tile.setStep(step(row,column));state=level.getBlockState(pos);
            var expected=NativeMountedControlGeometry.signalShape(state,mount,0);require(!net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(state.getCollisionShape(level,pos),expected,net.minecraft.world.phys.shapes.BooleanOp.NOT_SAME),"actual collision differs "+row+"/"+column);require(!net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(state.getShape(level,pos),expected,net.minecraft.world.phys.shapes.BooleanOp.NOT_SAME),"actual selection differs "+row+"/"+column);
            var b=NativeMountedControlGeometry.signalBoxes(state,mount,0).getFirst();Vec3 center=new Vec3(pos.getX()+(b.minX+b.maxX)/2,pos.getY()+(b.minY+b.maxY)/2,pos.getZ()+(b.minZ+b.maxZ)/2);Direction outward=row==0?Direction.UP:Direction.NORTH;Vec3 delta=new Vec3(outward.getStepX()*2,outward.getStepY()*2,outward.getStepZ()*2);require(state.getShape(level,pos).clip(center.add(delta),center.subtract(delta),pos)!=null,"actual selection ray misses "+row+"/"+column);
        }
        System.out.println("[vandorlabs][signal-scene] server PASS eight actual floor/wall four-detent controls four registry IDs all detents with real collision/selection/rays");
    }
    public static void finish(ServerPlayer player){
        player.closeContainer();player.setGameMode(GameType.CREATIVE);player.getAbilities().mayfly=true;player.getAbilities().flying=true;player.onUpdateAbilities();player.getInventory().clearContent();player.getInventory().selected=8;player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(8));player.connection.teleport(X,Y,Z,0,0);
        for(int i=0;i<8;i++){var stack=new ItemStack(i%4==3?VandorLabsBootstrap.WALL_SLIDER_ITEM.get():VandorLabsBootstrap.THRUSTER_LEVER_ITEM.get());var tag=new CompoundTag();ControlMountSettings.write(new ModernHousingTags(tag),i<4?0:3,i<4?0:3,i<4?0:3);tag.putInt("ControlType",i%4==3?0:i%4);var wrapper=new CompoundTag();wrapper.put("RedstoneChannelSettings",tag);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(wrapper));player.getInventory().setItem(i,stack);}player.inventoryMenu.broadcastChanges();player.containerMenu.broadcastChanges();
    }
    private static void require(boolean pass,String message){if(!pass)throw new IllegalStateException("Signal scene: "+message);}
    private SignalControlScene(){}
}
