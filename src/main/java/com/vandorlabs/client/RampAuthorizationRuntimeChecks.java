package com.vandorlabs.client;

import com.vandorlabs.GuiHandler;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Saved attached ramps must accept a fresh permitted interaction before recovery. */
public final class RampAuthorizationRuntimeChecks {
    private static void require(boolean value,String text){if(!value)throw new IllegalStateException("ramp authorization: "+text);}
    public static void run(World world,EntityPlayerMP actor){
        BlockPos root=new BlockPos(722,178,296);double x=actor.posX,y=actor.posY,z=actor.posZ;
        TileEntityRampController ramp=null;
        try{
            for(BlockPos at:BlockPos.getAllInBox(root.add(-2,-2,-2),root.add(3,6,5))){world.getChunkFromBlockCoords(at);world.setBlockToAir(at);}
            actor.setPosition(root.getX()+.5,root.getY()+1,root.getZ()-.5);
            world.setBlockState(root,ModBlocks.PROGRAMMABLE_RAMP.getDefaultState().withProperty(BlockVandorDirectional.FACING,EnumFacing.SOUTH),3);
            world.setBlockState(root.south(),Blocks.STONE.getDefaultState(),3);
            ramp=(TileEntityRampController)world.getTileEntity(root);ramp.setOwner(actor);
            require(configure(ramp,actor,true,1),"initial setup");require(ramp.request(true),"initial activation");ControllerRuntimeChecks.elapsed(ramp,ramp.durationTicks()+1);
            for(int step=0;step<4;step++){
                // Old portable saves deliberately lack a location receipt, even with Owner UUID.
                NBTTagCompound saved=ramp.writeToNBT(new NBTTagCompound());ramp.readFromNBT(saved);
                require(WorldActionAuthorization.actorId(ramp)==null&&ramp.attached(),"saved attached fixture has no portable authority");
                if(step==0){
                    require(!ramp.configureHalfOffsets(actor,0,2,3,true,false,false,EnumFacing.SOUTH,0,false,1,true),"invalid tread rejected");
                    require(WorldActionAuthorization.actorId(ramp)==null,"invalid settings cannot grant authority");
                }
                require(configure(ramp,actor,step%2==0,step%3),"activation/deactivation or setting change authorizes BEFORE old geometry recovery: "+step+" "+ramp.status);
                require(actor.getUniqueID().equals(WorldActionAuthorization.actorId(ramp)),"interaction binds actual actor");
                require(ramp.request(true),"recapture after setting");ControllerRuntimeChecks.elapsed(ramp,ramp.durationTicks()+1);
            }
            WorldActionAuthorization.revoke(ramp);
            actor.setPosition(root.getX()+64,root.getY()+1,root.getZ());
            require(new GuiHandler().getServerGuiElement(GuiHandler.GUI_RAMP_CONTROLLER,actor,world,root.getX(),root.getY(),root.getZ())==null&&WorldActionAuthorization.actorId(ramp)==null,"out-of-reach opening cannot grant authority");
            actor.setPosition(root.getX()+.5,root.getY()+1,root.getZ()-.5);
            world.getBlockState(root).getBlock().onBlockActivated(world,root,world.getBlockState(root),actor,EnumHand.MAIN_HAND,EnumFacing.NORTH,.5F,.5F,.5F);
            require(actor.getUniqueID().equals(WorldActionAuthorization.actorId(ramp)),"ordinary right-click grants authority through shared dialog entry");actor.closeScreen();
            WorldActionAuthorization.revoke(ramp);
            require(new GuiHandler().getServerGuiElement(GuiHandler.GUI_RAMP_CONTROLLER,actor,world,root.getX(),root.getY(),root.getZ())!=null,"Configurizer shared dialog entry opens");
            require(actor.getUniqueID().equals(WorldActionAuthorization.actorId(ramp)),"Configurizer shared entry grants authority");
            ControllerRuntimeChecks.reload(ramp);require(actor.getUniqueID().equals(WorldActionAuthorization.actorId(ramp)),"fresh receipt survives real chunk proof reload");
            require(ramp.request(false),"freshly authorized saved geometry retracts");ControllerRuntimeChecks.elapsed(ramp,ramp.durationTicks()+1);
            require(!ramp.attached()&&world.getBlockState(root.south()).getBlock()==Blocks.STONE,"one native source owner after recovery");
            System.out.println("[vandorlabs][reprolab] ramp-authorization PASS saved-attached activation deactivation setting-change invalid-input right-click Configurizer-shared-entry proof-reload native-ownership");
        }finally{
            if(ramp!=null){ramp.setOwner(actor);ramp.recover(true);}for(BlockPos at:BlockPos.getAllInBox(root.add(-2,-2,-2),root.add(3,6,5)))world.setBlockToAir(at);actor.setPosition(x,y,z);
        }
    }
    private static boolean configure(TileEntityRampController ramp,EntityPlayerMP actor,boolean power,int speed){return ramp.configureHalfOffsets(actor,0,2,16,power,speed==2,false,EnumFacing.SOUTH,0,false,speed,true);}
    private RampAuthorizationRuntimeChecks(){}
}
