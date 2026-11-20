package com.vandorlabs.gear.client;

import com.vandorlabs.gear.*;
import com.vandorlabs.gear.core.ArmResource;
import com.vandorlabs.ports.*;
import com.vandorlabs.redstone.ChannelList;
import java.io.File;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;

/** Actual Forge atlas geometry, incoming transfer events and in-world frames. */
public final class ArmResourceIndicatorLiveChecks {
    private static final ArmResource[] MODES={ArmResource.POWER,ArmResource.FLUID,ArmResource.ITEM,ArmResource.DISABLED};
    private static final String[] SPRITES={"power","fluid","matter"};
    private static final BlockPos ROOT=new BlockPos(0,100,0);
    private static final double X=5,Y=99.4,Z=-10;
    private static int stage,ticks,frames;

    private static BlockPos mount(int index){return ROOT.east(index*3);}
    private static GearTileEntity arm(net.minecraft.world.World world,int index){return (GearTileEntity)world.getTileEntity(mount(index));}
    private static void require(boolean good,String description){
        if(!good)throw new IllegalStateException("Arm indicator stage="+stage+" tick="+ticks+": "+description);
    }
    private static void setup(WorldServer world,EntityPlayerMP actor){
        for(BlockPos p:BlockPos.getAllInBox(ROOT.add(-3,-3,-12),ROOT.add(18,4,4))){
            world.getChunkFromBlockCoords(p);
            world.setBlockState(p,p.getY()==97?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState(),2);
        }
        for(int index=0;index<MODES.length;index++){
            BlockPos at=mount(index);
            world.setBlockState(at.up(),Blocks.STONE.getDefaultState(),2);
            world.setBlockState(at,GearRegistry.GEAR_MOUNT.getDefaultState().withProperty(GearBlock.FACING,EnumFacing.NORTH),2);
            GearTileEntity tile=arm(world,index);
            require(tile.rebuild(),"mounted base formed");
            NBTTagCompound settings=tile.settings();
            settings.setInteger(ArmResource.CHOICE,MODES[index].ordinal());
            tile.configure(settings);
            if(index<3){
                world.setBlockState(at.down(),ResourcePorts.BLOCKS[index].getDefaultState(),2);
                ((ResourcePortTile)world.getTileEntity(at.down())).configure(ResourcePortSettings.placed(EnumFacing.UP.getIndex()),0,ChannelList.EMPTY);
            }
        }
        for(int index=4;index<6;index++){
            BlockPos at=mount(index);world.setBlockState(at.up(),Blocks.STONE.getDefaultState(),2);
            actor.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,new ItemStack(index==4?GearRegistry.LARGE_GEAR_EXTENDER:GearRegistry.LARGE_GEAR_SWING_ARM,2));
            for(BlockPos support:new BlockPos[]{at.up(),at})require(actor.getHeldItemMainhand().onItemUse(actor,world,support,net.minecraft.util.EnumHand.MAIN_HAND,EnumFacing.DOWN,.5F,.5F,.5F)==net.minecraft.util.EnumActionResult.SUCCESS,"ordinary Large Arm section placement");
            GearTileEntity tile=arm(world,index);require(tile.rebuild(),"Large Arm forms");NBTTagCompound options=tile.settings();options.setInteger(ArmResource.CHOICE,ArmResource.POWER.ordinal());tile.configure(options);
        }
        world.setWorldTime(6000);
        actor.setGameType(GameType.CREATIVE);actor.capabilities.isFlying=true;actor.sendPlayerAbilities();
        actor.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,ItemStack.EMPTY);
        actor.connection.setPlayerLocation(X,Y,Z,0,2);
    }
    private static void transfer(WorldServer world,boolean simulation){
        require(arm(world,0).getCapability(CapabilityEnergy.ENERGY,EnumFacing.UP).receiveEnergy(4,simulation)==4,"power accepted");
        require(arm(world,1).getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,EnumFacing.UP).fill(new FluidStack(FluidRegistry.WATER,4),!simulation)==4,"water accepted");
        require(arm(world,2).getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,EnumFacing.UP).insertItem(0,new ItemStack(Items.GOLD_INGOT),simulation).isEmpty(),"item accepted");
    }
    private static boolean ready(Minecraft mc){
        if(mc.player.getDistanceSq(X,Y,Z)>.01||mc.playerController.getCurrentGameType()!=GameType.CREATIVE)return false;
        for(int i=0;i<MODES.length;i++){
            GearTileEntity tile=arm(mc.world,i);
            if(tile==null||!tile.isOperational()||tile.resourceTransfer()!=MODES[i])return false;
        }
        for(int i=4;i<6;i++)if(arm(mc.world,i)==null||!arm(mc.world,i).isOperational()||arm(mc.world,i).resourceTransfer()!=ArmResource.POWER)return false;
        return true;
    }
    private static boolean lit(Minecraft mc,boolean want){
        for(int i=0;i<MODES.length;i++)
            if(arm(mc.world,i).resourceBarActive(MODES[i])!=(want&&i<3))return false;
        return true;
    }
    private static int count(BufferBuilder buffer,TextureAtlasSprite sprite){
        int total=0,offset=buffer.getVertexFormat().getUvOffsetById(0),stride=buffer.getVertexFormat().getNextOffset();
        java.nio.ByteBuffer bytes=buffer.getByteBuffer();
        for(int v=0;v<buffer.getVertexCount();v++){
            float u=bytes.getFloat(v*stride+offset),t=bytes.getFloat(v*stride+offset+4);
            if(u>sprite.getMinU()&&u<sprite.getMaxU()&&t>sprite.getMinV()&&t<sprite.getMaxV()){
                require(Math.abs(bytes.getFloat(v*stride+8)+.02/16)<1e-4,"bar lies just in front of the stationary base face");
                require(t>=sprite.getInterpolatedV(1.25)-1e-5&&t<=sprite.getInterpolatedV(3)+1e-5,"bar uses only the top crop");
                total++;
            }
        }
        return total;
    }
    private static void mesh(Minecraft mc,boolean active){
        for(int index=0;index<6;index++){
            BufferBuilder buffer=new BufferBuilder(1024);
            buffer.begin(7,DefaultVertexFormats.BLOCK);
            new GearRenderer().renderUncached(arm(mc.world,index),0,0,0,0,buffer);
            buffer.finishDrawing();
            for(int family=0;family<3;family++)for(boolean lit:new boolean[]{false,true}){
                TextureAtlasSprite sprite=mc.getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/resource_ports/"+SPRITES[family]+"_in_"+(lit?"on":"off"));
                require(count(buffer,sprite)==((index<4?index==family&&lit==active:family==0&&!lit)?4:0),"one selected uncullable bar quad for "+index+" and activity "+active);
            }
        }
    }
    private static void capture(Minecraft mc,File folder,String label)throws Exception{
        ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),
                "png",new File(folder,"arm-resource-indicators-"+label+".png"));
    }
    public static boolean tick(Minecraft mc,File out)throws Exception{
        require(++ticks<250,"bounded rendering fixture");
        if(stage==0){
            mc.displayGuiScreen(null);mc.gameSettings.hideGUI=true;
            mc.getIntegratedServer().callFromMainThread(()->{
                setup(mc.getIntegratedServer().getWorld(0),mc.getIntegratedServer().getPlayerList().getPlayers().get(0));return null;
            }).get();
            stage=1;return false;
        }
        if(!ready(mc))return false;
        if(stage==1){
            if(ticks<35)return false;
            com.vandorlabs.client.GearItemRuntimeChecks.run(mc,out);
            require(lit(mc,false),"selected receivers idle before a transfer");
            mesh(mc,false);capture(mc,out,"idle");
            mc.getIntegratedServer().callFromMainThread(()->{transfer(mc.getIntegratedServer().getWorld(0),true);return null;}).get();
            stage=2;frames=0;return false;
        }
        if(stage==2){
            require(lit(mc,false),"simulations produce no indicator pulses");
            if(++frames<8)return false;
            stage=3;frames=0;
        }
        if(stage==3){
            mc.getIntegratedServer().callFromMainThread(()->{transfer(mc.getIntegratedServer().getWorld(0),false);return null;}).get();
            if(!lit(mc,true)||++frames<4)return false;
            mesh(mc,true);capture(mc,out,"transferring");
            stage=4;frames=0;return false;
        }
        if(++frames<15||!lit(mc,false))return false;
        mesh(mc,false);mc.gameSettings.hideGUI=false;
        System.out.println("[vandorlabs][reprolab] arm-resource-indicator PASS allThreePortBars/defaultOff/simulationIdle/realVanillaEvents/oneQuad/croppedTop/settledIdle");
        return true;
    }
    private ArmResourceIndicatorLiveChecks(){}
}
