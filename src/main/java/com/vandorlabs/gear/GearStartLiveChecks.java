package com.vandorlabs.gear;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Changing the source endpoint uses the same journal and collision path as an ordinary stroke. */
@net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class GearStartLiveChecks {
    private static int fixture,stage,ticks;
    private static final BlockPos ROOT=new BlockPos(620,170,260);
    private static void require(boolean ok,String reason){if(!ok)throw new IllegalStateException("start-angle fixture="+fixture+" stage="+stage+" ticks="+ticks+": "+reason);}
    public static boolean tick(Minecraft mc)throws Exception{
        if(++ticks==1){mc.displayGuiScreen(null);mc.gameSettings.pauseOnLostFocus=false;}
        if(ticks<80)return false;
        return mc.getIntegratedServer().callFromMainThread(()->{
            WorldServer world=mc.getIntegratedServer().getWorld(0);EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
            if(fixture==24){System.out.println("[vandorlabs][reprolab] arm-start PASS regular-Large six-mounts both-payload-modes native-payload source-retarget journal motion reversal reload endpoint-pose");return true;}
            EnumFacing face=EnumFacing.values()[fixture%6];GearBlock block=fixture%12<6?GearRegistry.GEAR_SWING_ARM:GearRegistry.LARGE_GEAR_SWING_ARM;
            GearTileEntity arm=(GearTileEntity)world.getTileEntity(ROOT);
            if(stage==0){
                for(BlockPos at:BlockPos.getAllInBox(ROOT.add(-7,-7,-7),ROOT.add(7,7,7)))world.setBlockToAir(at);
                world.setBlockState(ROOT.offset(face.getOpposite()),Blocks.STONE.getDefaultState(),3);
                actor.setPosition(ROOT.getX()+.5,ROOT.getY()+4,ROOT.getZ()+.5);
                for(int n=0;n<2;n++){BlockPos at=ROOT.offset(face,n);require(((ItemBlock)Item.getItemFromBlock(block)).placeBlockAt(new ItemStack(block),actor,world,at,face,.5F,.5F,.5F,block.getDefaultState()),"ordinary arm placement");}
                arm=(GearTileEntity)world.getTileEntity(ROOT);arm.operator(actor);require(arm.rebuild(),"native arm forms "+arm.error());
                NBTTagCompound options=arm.settings();options.setBoolean("RotatePayload",fixture<12);arm.configure(options);arm.rebuild();
                world.setBlockState(ROOT.offset(face,2),Blocks.IRON_BLOCK.getDefaultState(),3);
                options=arm.settings();options.setDouble("StartAngle",90);options.setDouble("ActiveAngle",-90);arm.configure(options);stage=1;ticks=80;return false;
            }
            require(ticks<1000,"motion timeout progress="+arm.progress()+" target="+arm.target()+" start="+arm.physicalStartAngle()+" pose="+arm.pose(arm.progress()).angleDegrees()+" error="+arm.error());
            if(stage==1&&arm.physicalStartAngle()==90&&arm.progress()==1&&!ArmTransactions.locked(world,ROOT)){
                require(arm.pose(1).angleDegrees()==90&&arm.pose(0).angleDegrees()==-90,"separate configured endpoints");
                NBTTagCompound saved=arm.writeToNBT(new NBTTagCompound());GearTileEntity reloaded=(GearTileEntity)net.minecraft.tileentity.TileEntity.create(world,saved);require(reloaded!=null&&reloaded.startAngle()==90&&reloaded.configuredEndAngle()==-90,"saved endpoints survive reload");
                arm.target(false);stage=2;return false;
            }
            if(stage==2&&arm.progress()==0&&!ArmTransactions.locked(world,ROOT)){require(arm.pose(0).angleDegrees()==-90,"configured end angle reached");arm.target(true);stage=3;return false;}
            if(stage==3&&arm.progress()==1&&!ArmTransactions.locked(world,ROOT)){
                require(arm.pose(1).angleDegrees()==90&&arm.payload()!=null,"reversal reaches configured source with retained ownership");
                System.out.println("[vandorlabs][reprolab] arm-start case PASS fixture="+fixture);fixture++;stage=0;ticks=80;
            }
            return false;
        }).get();
    }
}
