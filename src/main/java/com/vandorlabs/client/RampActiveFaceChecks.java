package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.client.Minecraft;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;

/** Vertical scan metadata is distinct from the unchanged horizontal deployment direction. */
final class RampActiveFaceChecks {
    static void run(Minecraft mc)throws Exception{
        mc.getIntegratedServer().addScheduledTask(()->{
            WorldServer world=mc.getIntegratedServer().getWorld(0);VehicleAttachmentChecks.run(world);EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);BlockRampController block=(BlockRampController)ModBlocks.PROGRAMMABLE_RAMP;BlockPos pos=new BlockPos(28,70,28);Vec3d old=player.getPositionVector();float yaw=player.rotationYaw,pitch=player.rotationPitch;int cases=0;
            try{for(EnumFacing horizontal:EnumFacing.HORIZONTALS)for(int vertical=-1;vertical<=1;vertical++){
                for(BlockPos p:BlockPos.getAllInBox(pos.add(-4,-5,-4),pos.add(4,5,4)))world.setBlockToAir(p);
                player.setPosition(pos.getX()+.5,pos.getY()+1,pos.getZ()+2);player.rotationYaw=horizontal.getHorizontalAngle();player.rotationPitch=vertical*80;
                IBlockState state=block.getStateForPlacement(world,pos,EnumFacing.UP,.5F,.5F,.5F,0,player);EnumFacing scan=vertical<0?EnumFacing.UP:vertical>0?EnumFacing.DOWN:horizontal;
                require(BlockRampController.scanFacing(state)==scan,"placement follows vertical view or horizontal direction");world.setBlockState(pos,state,2);block.onBlockPlacedBy(world,pos,state,player,new ItemStack(block));TileEntityRampController tile=(TileEntityRampController)world.getTileEntity(pos);require(tile.rampDirection()==horizontal,"horizontal deployment retained");
                for(boolean active:new boolean[]{false,true}){IBlockState roundTrip=block.getStateFromMeta(block.getMetaFromState(state.withProperty(BlockRampController.ACTIVE,active)));require(BlockRampController.scanFacing(roundTrip)==scan&&roundTrip.getValue(BlockRampController.ACTIVE)==active,"six scan faces preserve legacy metadata and active bit");}
                for(int legacy=0;legacy<8;legacy++)require(block.getMetaFromState(block.getStateFromMeta(legacy))==legacy,"legacy controller metadata unchanged");
                NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());TileEntityRampController copy=new TileEntityRampController();copy.setWorld(world);copy.setPos(pos);copy.readFromNBT(saved);require(copy.rampDirection()==horizontal,"vertical placement travel direction survives NBT");
                java.util.List<BlockPos> sources=new java.util.ArrayList<>();BlockPos seed=pos.offset(scan);for(int i=0;i<2;i++)for(int j=0;j<2;j++){BlockPos source=seed.offset(horizontal,i).offset(horizontal.rotateY(),j);sources.add(source);world.setBlockState(source,Blocks.STONE.getDefaultState(),2);}
                require(tile.configure(player,2,8,scan==EnumFacing.DOWN,true,false,false),"configure active-face capture");BlockPos power=pos.offset(scan.getOpposite());world.setBlockState(power,Blocks.REDSTONE_BLOCK.getDefaultState(),3);tile.updatePower();require(tile.attached(),"platform captured through "+scan+" error="+tile.status);require(tile.rampDirection()==horizontal,"capture does not rotate deployment vertically");require(tile.recover(true),"captured platform safely restored");for(BlockPos source:sources)require(world.getBlockState(source).getBlock()==Blocks.STONE,"source material restored");cases++;
            }}finally{for(BlockPos p:BlockPos.getAllInBox(pos.add(-4,-5,-4),pos.add(4,5,4)))world.setBlockToAir(p);player.setPosition(old.x,old.y,old.z);player.rotationYaw=yaw;player.rotationPitch=pitch;}
            System.out.println("[vandorlabs][reprolab] ramp-active-face PASS placements="+cases+" six scan faces, legacy metadata, horizontal travel, capture and recovery");
        }).get();
        BlockRampController block=(BlockRampController)ModBlocks.PROGRAMMABLE_RAMP;
        for(EnumFacing face:EnumFacing.values())for(int mode=0;mode<7;mode++)for(boolean active:new boolean[]{false,true}){
            IBlockState state=block.getDefaultState().withProperty(BlockRampController.ACTIVE_FACE,face).withProperty(BlockRampController.FACING,face.getAxis().isHorizontal()?face:EnumFacing.NORTH).withProperty(BlockRampController.MODE,mode).withProperty(BlockRampController.ACTIVE,active);
            net.minecraft.client.renderer.block.model.IBakedModel model=mc.getBlockRendererDispatcher().getModelForState(state);java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> quads=model.getQuads(state,face,0);String expected="vandorlabs:block/ramp_mode_"+BlockRampController.MODE_TEXTURES[mode];require(quads.stream().anyMatch(q->q.getSprite().getIconName().equals(expected)),"active-face model orientation "+face+"/"+mode);
        }
        System.out.println("[vandorlabs][reprolab] ramp-active-face-models PASS six faces seven modes active/inactive");
    }
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("Ramp active face: "+message);}
}
