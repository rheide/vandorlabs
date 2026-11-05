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

/** Disposable real-world mounted-control scene; no production or client linkage. */
public final class BinaryControlScene {
    public static final double X=1000.5,Y=84,Z=996.5;
    private static final BlockPos[] SUPPORTS={new BlockPos(997,88,1005),new BlockPos(1000,83,1005),new BlockPos(1003,85,1005),new BlockPos(997,85,1006),new BlockPos(1000,88,1005),new BlockPos(1003,88,1005)};
    public static void setup(ServerLevel level){
        for(int x=994;x<=1007;x++)for(int z=993;z<=1011;z++)level.setBlock(new BlockPos(x,82,z),Blocks.SMOOTH_STONE.defaultBlockState(),3);
        for(Direction face:Direction.values()){
            int i=face.get3DDataValue();BlockPos support=SUPPORTS[i],pos=support.relative(face);level.setBlock(support,Blocks.IRON_BLOCK.defaultBlockState(),3);var block=i%2==0?VandorLabsBootstrap.PUSH_BUTTON.get():VandorLabsBootstrap.ROCKER_SWITCH.get();var state=block.defaultBlockState().setValue(NativeMountedSwitchBlock.FACING,face).setValue(NativeMountedSwitchBlock.ROTATION,0);level.setBlock(pos,state,3);
            var tile=(NativeBinaryControlBlockEntity)level.getBlockEntity(pos);var mount=ControlMountSettings.of(i%4,i%3,i%4);tile.configureMount(mount);
            var actual=state.getCollisionShape(level,pos);var expected=NativeMountedControlGeometry.shape(state,mount,0);require(!net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(actual,expected,net.minecraft.world.phys.shapes.BooleanOp.NOT_SAME),"actual collision differs "+face);
            var pick=state.getShape(level,pos);require(!net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(pick,expected,net.minecraft.world.phys.shapes.BooleanOp.NOT_SAME),"actual selection differs "+face);
        }
        System.out.println("[vandorlabs][binary-scene] server PASS six real mounted controls with authored collision/selection");
    }
    public static void finish(ServerPlayer player){
        player.closeContainer();player.setGameMode(GameType.CREATIVE);player.getAbilities().mayfly=true;player.getAbilities().flying=true;player.onUpdateAbilities();player.teleportTo(X,Y,Z);player.connection.teleport(X,Y,Z,0,-12);player.getInventory().selected=2;player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(2));player.getInventory().setItem(2,ItemStack.EMPTY);
        for(int i=3;i<9;i++){var stack=new ItemStack(i%2==0?VandorLabsBootstrap.PUSH_BUTTON_ITEM.get():VandorLabsBootstrap.ROCKER_SWITCH_ITEM.get());var tag=new CompoundTag();ControlMountSettings.write(new ModernHousingTags(tag),(i-3)%4,(i-3)%4,(i-3)%4);var wrapper=new CompoundTag();wrapper.put("RedstoneChannelSettings",tag);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(wrapper));player.getInventory().setItem(i,stack);}player.inventoryMenu.broadcastChanges();player.containerMenu.broadcastChanges();
    }
    private static void require(boolean pass,String message){if(!pass)throw new IllegalStateException("Binary scene: "+message);}
    private BinaryControlScene(){}
}
