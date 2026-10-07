package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityRedstoneChannel;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Real placement/power and baked-artwork checks for the compact binary switch. */
final class ToggleSwitchChecks {
    static void server(World world,EntityPlayer player){
        BlockToggleSwitch block=(BlockToggleSwitch)Block.getBlockFromName("vandorlabs:toggle_switch");
        BlockPos pos=new BlockPos(20,100,40);boolean sneaking=player.isSneaking();player.setSneaking(false);int mounts=0;
        try{
            for(EnumFacing face:EnumFacing.values())for(int rotation=0;rotation<(face.getAxis()==EnumFacing.Axis.Y?4:1);rotation++){
                BlockPos support=pos.offset(face.getOpposite());world.setBlockState(support,Blocks.STONE.getDefaultState(),3);
                require(block.canPlaceBlockOnSide(world,pos,face),"toggle cannot attach to "+face);
                IBlockState state=block.getDefaultState().withProperty(BlockVandorSwitch.FACING,face);
                world.setBlockState(pos,state,3);TileEntityRedstoneChannel tile=(TileEntityRedstoneChannel)world.getTileEntity(pos);tile.setMountRotation(rotation);
                for(boolean on:new boolean[]{true,false}){
                    block.onBlockActivated(world,pos,world.getBlockState(pos),player,EnumHand.MAIN_HAND,face,.5F,.5F,.5F);
                    state=world.getBlockState(pos);require(state.getValue(BlockVandorSwitch.ON)==on && tile.isLocalOn()==on,"toggle click/latch");
                    require(block.getStateFromMeta(block.getMetaFromState(state)).getValue(BlockVandorSwitch.ON)==on,"toggle state persistence");
                    for(EnumFacing side:EnumFacing.values())require(block.getWeakPower(state,world,pos,side)==(on?15:0) && block.getStrongPower(state,world,pos,side)==(on?15:0),"toggle physical redstone output");
                }
                tile.setRedstoneChannel(16980);TileEntityRedstoneChannel restored=new TileEntityRedstoneChannel();restored.readFromNBT(tile.writeToNBT(new NBTTagCompound()));
                require(restored.getRedstoneChannel()==16980 && restored.getMountRotation()==rotation,"toggle channel/mount NBT");
                tile.setRedstoneChannel(0);world.setBlockToAir(support);require(world.isAirBlock(pos),"toggle remained after support removal");mounts++;
            }
        }finally{world.setBlockToAir(pos);for(EnumFacing face:EnumFacing.values())world.setBlockToAir(pos.offset(face));player.setSneaking(sneaking);}
        System.out.println("[vandorlabs][reprolab] toggle-switch-runtime PASS mounts="+mounts+" (toggle, all-side power, persistence and support removal)");
    }
    static void checkModels(Minecraft mc){
        BlockToggleSwitch block=(BlockToggleSwitch)Block.getBlockFromName("vandorlabs:toggle_switch");int poses=0;
        for(EnumFacing face:EnumFacing.values())for(int rotation=0;rotation<4;rotation++){
            java.util.Set<String> signatures=new java.util.HashSet<>();
            for(boolean on:new boolean[]{false,true}){
                IBlockState state=block.getDefaultState().withProperty(BlockVandorSwitch.FACING,face).withProperty(BlockVandorSwitch.ROTATION,rotation).withProperty(BlockVandorSwitch.ON,on);
                IBakedModel model=mc.getBlockRendererDispatcher().getBlockModelShapes().getModelForState(state);
                require(model!=mc.getBlockRendererDispatcher().getBlockModelShapes().getModelManager().getMissingModel(),"missing toggle model");
                AxisAlignedBB bounds=block.getBoundingBox(state,mc.world,new BlockPos(0,250,0)).grow(.00002);StringBuilder signature=new StringBuilder();int count=0;boolean indicator=false;
                for(EnumFacing side:new EnumFacing[]{null,EnumFacing.UP,EnumFacing.DOWN,EnumFacing.NORTH,EnumFacing.SOUTH,EnumFacing.EAST,EnumFacing.WEST})for(BakedQuad q:model.getQuads(state,side,0)){
                    count++;signature.append(java.util.Arrays.hashCode(q.getVertexData()));indicator|=q.getFace()==face && q.getSprite().getIconName().endsWith(on?"/cyan":"/amber");
                    int[] data=q.getVertexData();int stride=data.length/4;
                    for(int i=0;i<4;i++)require(bounds.contains(new Vec3d(Float.intBitsToFloat(data[i*stride]),Float.intBitsToFloat(data[i*stride+1]),Float.intBitsToFloat(data[i*stride+2]))),"toggle selection excludes artwork "+face+"/"+rotation);
                }
                require(count>0 && indicator,"toggle outward indicator missing");signatures.add(signature.toString());poses++;
            }
            require(signatures.size()==2,"toggle artwork repeats between states");
        }
        System.out.println("[vandorlabs][reprolab] toggle-switch-models PASS poses="+poses+" (binary artwork, every mount/rotation, selection and outward indicators)");
    }
    private static void require(boolean pass,String message){if(!pass)throw new IllegalStateException(message);}
}
