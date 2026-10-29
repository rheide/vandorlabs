package com.vandorlabs.gear;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Native placement, full world ticks and actual asynchronous ownership journals. */
@net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class GearPushLiveChecks {
    private static int fixture,stage,ticks;
    private static final BlockPos ROOT=new BlockPos(620,170,260);
    private static void require(boolean ok,String reason){if(!ok)throw new IllegalStateException("push fixture="+fixture+" stage="+stage+" ticks="+ticks+": "+reason);}
    public static boolean tick(Minecraft mc)throws Exception{
        if(++ticks==1){mc.displayGuiScreen(null);mc.gameSettings.pauseOnLostFocus=false;}
        if(ticks<80)return false;
        return mc.getIntegratedServer().callFromMainThread(()->{
            WorldServer world=mc.getIntegratedServer().getWorld(0);EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
            if(fixture==8){System.out.println("[vandorlabs][reprolab] arm-push PASS regular-Large four-horizontal-faces retracted-config incremental-gap no-pull reload real-asynchronous-journals");return true;}
            EnumFacing face=EnumFacing.HORIZONTALS[fixture%4];GearBlock block=fixture<4?GearRegistry.GEAR_EXTENDER:GearRegistry.LARGE_GEAR_EXTENDER;
            GearTileEntity arm=(GearTileEntity)world.getTileEntity(ROOT);
            if(stage==0){
                for(BlockPos at:BlockPos.getAllInBox(ROOT.add(-8,-3,-8),ROOT.add(8,3,8)))world.setBlockToAir(at);
                world.setBlockState(ROOT.offset(face.getOpposite()),Blocks.STONE.getDefaultState(),3);
                actor.setPosition(ROOT.getX()+.5,ROOT.getY()+2,ROOT.getZ()+.5);
                for(int n=0;n<4;n++){BlockPos at=ROOT.offset(face,n);require(((ItemBlock)Item.getItemFromBlock(block)).placeBlockAt(new ItemStack(block),actor,world,at,face,.5F,.5F,.5F,block.getDefaultState()),"ordinary arm placement");}
                arm=(GearTileEntity)world.getTileEntity(ROOT);arm.operator(actor);require(arm.rebuild(),"native arm forms "+arm.error());world.setBlockState(ROOT.offset(face,4),net.minecraft.block.Block.getBlockFromName("vandorlabs:turntable_surface").getDefaultState(),3);world.setBlockState(ROOT.offset(face,4).up(),net.minecraft.block.Block.getBlockFromName("vandorlabs:turntable_surface").getDefaultState(),3);arm.target(false);stage=1;ticks=80;return false;
            }
            require(ticks<800,"motion timeout progress="+arm.progress()+" target="+arm.target()+" error="+arm.error());
            if(stage==1&&arm.progress()==0&&!ArmTransactions.locked(world,ROOT)){
                NBTTagCompound options=arm.settings();options.setBoolean("PushBlocks",true);arm.configure(options);
                world.setBlockState(ROOT.offset(face,3).up(),Blocks.IRON_BLOCK.getDefaultState(),3);
                // A late immovable block must refuse before detaching the Surface.
                BlockPos obstruction=ROOT.offset(face,5).up();world.setBlockState(obstruction,Blocks.BEDROCK.getDefaultState(),3);
                arm.target(true);require(!arm.preparePayloadMotion()&&arm.progress()==0&&arm.payload().materialized()&&world.getBlockState(ROOT.offset(face,1).up()).getBlock() instanceof com.vandorlabs.blocks.BlockTurntableSurface,"complete push path refuses before source removal");
                world.setBlockToAir(obstruction);arm.target(true);stage=2;return false;
            }
            if(stage==2&&arm.progress()==1&&!ArmTransactions.locked(world,ROOT)){
                require(world.getBlockState(ROOT.offset(face,4)).getBlock() instanceof com.vandorlabs.blocks.BlockTurntableSurface,"initial Surface moves three blocks "+arm.error());
                require(world.getBlockState(ROOT.offset(face,5).up()).getBlock()==Blocks.IRON_BLOCK,"block beyond air moves only two blocks "+arm.error());
                NBTTagCompound saved=arm.writeToNBT(new NBTTagCompound());require(GearTileEntity.class.isInstance(net.minecraft.tileentity.TileEntity.create(world,saved)),"push settings reload");
                arm.target(false);stage=3;return false;
            }
            if(stage==3&&arm.progress()==0&&!ArmTransactions.locked(world,ROOT)){
                require(world.getBlockState(ROOT.offset(face,4)).getBlock() instanceof com.vandorlabs.blocks.BlockTurntableSurface&&world.getBlockState(ROOT.offset(face,5).up()).getBlock()==Blocks.IRON_BLOCK,"retraction does not pull pushed blocks "+arm.error());
                require(world.getBlockState(ROOT.offset(face.getOpposite())).getBlock()==Blocks.STONE,"mounting support remains in place");fixture++;stage=0;ticks=80;
            }
            return false;
        }).get();
    }
}
