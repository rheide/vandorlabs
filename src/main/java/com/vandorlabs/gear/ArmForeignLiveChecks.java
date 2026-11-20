package com.vandorlabs.gear;

import com.vandorlabs.gear.core.ArmResource;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraftforge.fluids.*;
import net.minecraftforge.fluids.capability.*;

/** Installed Mekanism tank, real arm click, journal capture, rotated fill and native reversal. */
@net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class ArmForeignLiveChecks {
    private static final BlockPos ROOT=new BlockPos(330,115,10),SOURCE=ROOT.up(2);
    private static int stage,ticks,fixture;
    private static boolean clicked,visual;
    private static IBlockState tankState;
    private static GearBlock armBlock(){return fixture%2==0?GearRegistry.GEAR_SWING_ARM:GearRegistry.LARGE_GEAR_SWING_ARM;}
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("arm-foreign fixture="+fixture+" stage="+stage+" tick="+ticks+": "+why);}
    private static IFluidTank tank(TileEntity tile)throws Exception{return (IFluidTank)tile.getClass().getField("fluidTank").get(tile);}
    public static boolean tick(Minecraft mc)throws Exception{
        mc.displayGuiScreen(null);require(++ticks<600,"bounded real motion");
        if(stage==0){mc.getIntegratedServer().callFromMainThread(()->{setup(mc.getIntegratedServer().getWorld(0),mc.getIntegratedServer().getPlayerList().getPlayers().get(0));return null;}).get();stage=1;ticks=0;return false;}
        if(stage==1&&!clicked&&ticks>20&&mc.playerController.getCurrentGameType()==GameType.CREATIVE&&mc.player.inventory.currentItem==0&&mc.player.getHeldItemMainhand().isEmpty()&&mc.player.getDistanceSq(333.5,117,13.5)<1&&mc.world.getBlockState(SOURCE)==tankState){
            mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock(ROOT,EnumFacing.UP,EnumHand.MAIN_HAND,.5F,.5F,.5F));clicked=true;
        }
        if(stage==2){
            TileEntity raw=mc.world.getTileEntity(ROOT);if(!(raw instanceof GearTileEntity))return false;GearTileEntity client=((GearTileEntity)raw).loadedController();
            if(client==null||client.payload()==null||client.payload().materialized())return false;
            TileEntity carried=client.payload().view(client).tiles.values().iterator().next();
            if(fixture>=2){require(((net.minecraft.inventory.IInventory)carried).getStackInSlot(0).getCount()==3,"client machine inventory retained");visual=true;}
            else{
            if(tank(carried).getFluidAmount()!=1275)return false;
            require(carried.getClass().getField("prevScale").getFloat(carried)>0,"saved fluid initializes native Mekanism TESR fullness");
            if(!visual){
                java.awt.image.BufferedImage filled=renderTank(mc,carried);
                java.lang.reflect.Field scale=carried.getClass().getField("prevScale");float previous=scale.getFloat(carried);scale.setFloat(carried,0);
                java.awt.image.BufferedImage empty;try{empty=renderTank(mc,carried);}finally{scale.setFloat(carried,previous);}
                int changed=0;for(int y=0;y<128;y++)for(int x=0;x<128;x++)if(filled.getRGB(x,y)!=empty.getRGB(x,y))changed++;
                require(changed>100,"actual native tank TESR renders saved fluid pixels: "+changed);
                java.io.File out=new java.io.File(System.getenv("VANDOR_LABS_REPRO_OUT"));out.mkdirs();
                javax.imageio.ImageIO.write(filled,"png",new java.io.File(out,"arm_foreign_tank_"+fixture+".png"));
                visual=true;
            }
            }
        }
        return mc.getIntegratedServer().callFromMainThread(()->run(mc.getIntegratedServer().getWorld(0),mc.getIntegratedServer().getPlayerList().getPlayers().get(0))).get();
    }
    private static void setup(WorldServer world,EntityPlayerMP player)throws Exception{
        player.setGameType(GameType.CREATIVE);player.capabilities.isFlying=true;player.sendPlayerAbilities();player.inventory.currentItem=0;
        player.connection.setPlayerLocation(333.5,117,13.5,135,20);
        for(BlockPos pos:BlockPos.getAllInBox(ROOT.add(-7,-2,-7),ROOT.add(7,7,7))){world.getChunkFromBlockCoords(pos);world.setBlockState(pos,pos.getY()==ROOT.getY()-1?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState(),2);}
        player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(armBlock(),2));
        for(BlockPos support:new BlockPos[]{ROOT.down(),ROOT})require(player.getHeldItemMainhand().onItemUse(player,world,support,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F)==EnumActionResult.SUCCESS,"ordinary supported rotating-arm placement");
        tankState=null;
        scan:for(Block b:Block.REGISTRY)if(b.getRegistryName().getResourceDomain().equals(fixture==2?"thermalexpansion":"mekanism"))for(int meta=0;meta<16;meta++){
            IBlockState state;try{state=b.getStateFromMeta(meta);}catch(RuntimeException unusedMeta){continue;}if(!b.hasTileEntity(state))continue;TileEntity tile=b.createTileEntity(world,state);
            if(tile!=null&&tile.getClass().getName().equals(fixture==2?"cofh.thermalexpansion.block.machine.TilePulverizer":fixture==3?"mekanism.common.tile.TileEntityEnrichmentChamber":"mekanism.common.tile.TileEntityFluidTank")){tankState=state;break scan;}
        }
        require(tankState!=null,"installed Mekanism fluid tank");
        player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(tankState.getBlock(),1,tankState.getBlock().getMetaFromState(tankState)));
        require(player.getHeldItemMainhand().onItemUse(player,world,ROOT.up(),EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F)==EnumActionResult.SUCCESS,"ordinary payload placement");
        tankState=world.getBlockState(SOURCE);if(fixture<2)require(tank(world.getTileEntity(SOURCE)).fill(new FluidStack(FluidRegistry.WATER,1000),true)==1000,"initial physical fluid owner");else ((net.minecraft.inventory.IInventory)world.getTileEntity(SOURCE)).setInventorySlotContents(0,new ItemStack(net.minecraft.init.Items.DIAMOND,3));
        GearTileEntity arm=(GearTileEntity)world.getTileEntity(ROOT);require(arm.rebuild(),"rotating arm forms");arm.operator(player);
        NBTTagCompound settings=arm.settings();settings.setInteger(ArmResource.CHOICE,ArmResource.FLUID.ordinal());arm.configure(settings);
        player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);player.inventoryContainer.detectAndSendChanges();
    }
    private static boolean run(WorldServer world,EntityPlayerMP player)throws Exception{
        GearTileEntity arm=((GearTileEntity)world.getTileEntity(ROOT)).loadedController();require(arm!=null,"native arm owner");
        if(stage==1){
            if(!clicked||ticks<30||ArmTransactions.locked(world,ROOT)||arm.progress()!=0)return false;
            require(arm.payload()!=null&&!arm.payload().materialized()&&world.isAirBlock(SOURCE),"rotated tank has one captured owner: "+arm.error());
            if(fixture<2){
            IFluidHandler input=arm.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,EnumFacing.DOWN);
            require(input!=null,"mount fluid capability");FluidStack offered=new FluidStack(FluidRegistry.WATER,275);
            require(input.fill(offered,false)==275&&tank(arm.payload().view(arm).tiles.values().iterator().next()).getFluidAmount()==1000,"simulation keeps snapshot unchanged");
            require(input.fill(offered,true)==275,"rotated tank receives through arm");
            require(tank(arm.payload().view(arm).tiles.values().iterator().next()).getFluidAmount()==1275,"committed fluid published in saved snapshot");
            }
            NBTTagCompound saved=arm.writeToNBT(new NBTTagCompound());arm.readFromNBT(saved);arm.operator(player);arm.onLoad();
            if(fixture<2)require(tank(arm.payload().view(arm).tiles.values().iterator().next()).getFluidAmount()==1275,"reload keeps exactly one fluid owner");else require(((net.minecraft.inventory.IInventory)arm.payload().view(arm).tiles.values().iterator().next()).getStackInSlot(0).getCount()==3,"machine reload keeps exactly three diamonds");
            world.notifyBlockUpdate(ROOT,world.getBlockState(ROOT),world.getBlockState(ROOT),3);stage=2;ticks=0;return false;
        }
        if(stage==2){if(!visual||ticks<20)return false;arm.target(true);stage=3;ticks=0;return false;}
        if(stage==3){if(arm.progress()!=1||ArmTransactions.locked(world,ROOT)||ArmTransactions.locked(world,SOURCE))return false;
            require(arm.payload()!=null&&arm.payload().materialized(),"reverse restores native tank");if(fixture<2)require(tank(world.getTileEntity(SOURCE)).getFluidAmount()==1275,"native endpoint owns exactly 1275mB after reverse");else require(((net.minecraft.inventory.IInventory)world.getTileEntity(SOURCE)).getStackInSlot(0).getCount()==3,"native machine owns exactly three diamonds after reverse");
            System.out.println("[vandorlabs] arm-foreign case PASS size="+(fixture%2==0?"regular":"Large")+" real click asynchronous journals rotated fill simulation snapshot reload client fullness reverse exact fluid");
            if(++fixture==4){MachineBlockChecks.run(world);System.out.println("[vandorlabs] arm-foreign PASS");return true;}stage=0;ticks=0;clicked=false;visual=false;
        }
        return false;
    }
    private static java.awt.image.BufferedImage renderTank(Minecraft mc,TileEntity tile){
        net.minecraft.client.shader.Framebuffer frame=new net.minecraft.client.shader.Framebuffer(128,128,true);
        net.minecraft.client.renderer.GlStateManager.matrixMode(5889);net.minecraft.client.renderer.GlStateManager.pushMatrix();net.minecraft.client.renderer.GlStateManager.loadIdentity();net.minecraft.client.renderer.GlStateManager.ortho(-.7,.7,-.7,.7,-4,4);
        net.minecraft.client.renderer.GlStateManager.matrixMode(5888);net.minecraft.client.renderer.GlStateManager.pushMatrix();
        try{
            frame.bindFramebuffer(true);net.minecraft.client.renderer.GlStateManager.loadIdentity();net.minecraft.client.renderer.GlStateManager.clearColor(.1F,.15F,.2F,1);net.minecraft.client.renderer.GlStateManager.clear(16640);mc.entityRenderer.disableLightmap();
            net.minecraft.client.renderer.GlStateManager.rotate(20,1,0,0);net.minecraft.client.renderer.GlStateManager.rotate(30,0,1,0);
            net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer<TileEntity> renderer=net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher.instance.getRenderer(tile);
            require(renderer!=null,"installed native tank TESR");renderer.render(tile,-.5,-.5,-.5,0,-1,1);
            return net.minecraft.util.ScreenShotHelper.createScreenshot(128,128,frame);
        }finally{net.minecraft.client.renderer.GlStateManager.popMatrix();net.minecraft.client.renderer.GlStateManager.matrixMode(5889);net.minecraft.client.renderer.GlStateManager.popMatrix();net.minecraft.client.renderer.GlStateManager.matrixMode(5888);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }
    private ArmForeignLiveChecks(){}
}
