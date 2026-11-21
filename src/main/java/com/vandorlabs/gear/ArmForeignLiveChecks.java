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
        if(stage==10){
            if(ticks==40)for(int i=0;i<2;i++)world.notifyNeighborsOfStateChange(ROOT.east(i*4).up(),world.getBlockState(ROOT.east(i*4).up()).getBlock(),false);
            if(ticks<60)return false;
            for(int i=0;i<2;i++){
                BlockPos mount=ROOT.east(i*4);GearTileEntity owner=((GearTileEntity)world.getTileEntity(mount)).loadedController();
                require(TransferNetworkCompat.component(world.getTileEntity(mount.up(2))),"conduit still on the tip "+i);
                IFluidHandler input=owner.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,EnumFacing.DOWN);
                int accepted=input==null?0:input.fill(new FluidStack(FluidRegistry.WATER,100),true);
                if(accepted>0)continue;
                TileEntity pipe=world.getTileEntity(mount.up(2)),tipTile=world.getTileEntity(mount.up());
                IFluidHandler direct=pipe.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,EnumFacing.DOWN);
                require(false,"arm delivers fluid into the conduit on its tip "+(i==0?"thermaldynamics":"mekanism")+" error="+owner.error()
                        +" tipExposes="+tipTile.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,EnumFacing.UP)+" tipClass="+tipTile.getClass().getSimpleName()
                        +" conduitFace="+(direct!=null)+" directFill="+(direct==null?-1:direct.fill(new FluidStack(FluidRegistry.WATER,10),false))+" input="+(input!=null)+" "+pipeState(pipe));
            }
            System.out.println("[vandorlabs] arm-foreign conduits PASS tip ducts and pipes stay native and receive fluid");
            freezeDuringTileTick(world,player);stage=11;ticks=0;return false;
        }
        if(stage==11){
            // The replaced tank must not be invalidated a second time without a world.
            if(ticks<10)return false;
            require(com.vandorlabs.copier.CopyWorld.frozen(world,FROZEN),"frozen proxy installed after the pending tile list settles");
            com.vandorlabs.copier.CopyWorld.publish(world,FROZEN);
            require(world.getTileEntity(FROZEN)!=null&&world.getTileEntity(FROZEN).getClass().getName().equals("mekanism.common.tile.TileEntityFluidTank"),"published tank restores the native owner");
            System.out.println("[vandorlabs] arm-foreign freeze-during-tick PASS");
            System.out.println("[vandorlabs] arm-foreign PASS");return true;
        }
        if(stage==2){if(!visual||ticks<20)return false;arm.target(true);stage=3;ticks=0;return false;}
        if(stage==3){if(arm.progress()!=1||ArmTransactions.locked(world,ROOT)||ArmTransactions.locked(world,SOURCE))return false;
            require(arm.payload()!=null&&arm.payload().materialized(),"reverse restores native tank");if(fixture<2)require(tank(world.getTileEntity(SOURCE)).getFluidAmount()==1275,"native endpoint owns exactly 1275mB after reverse");else require(((net.minecraft.inventory.IInventory)world.getTileEntity(SOURCE)).getStackInSlot(0).getCount()==3,"native machine owns exactly three diamonds after reverse");
            System.out.println("[vandorlabs] arm-foreign case PASS size="+(fixture%2==0?"regular":"Large")+" real click asynchronous journals rotated fill simulation snapshot reload client fullness reverse exact fluid");
            if(++fixture==4){MachineBlockChecks.run(world);conduits(world,player);stage=10;ticks=0;return false;}stage=0;ticks=0;clicked=false;visual=false;
        }
        return false;
    }
    private static String pipeState(TileEntity pipe){
        StringBuilder out=new StringBuilder();
        IFluidHandler up=pipe.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,EnumFacing.UP);
        out.append(" upFace=").append(up!=null).append(" upFill=").append(up==null?-1:up.fill(new FluidStack(FluidRegistry.WATER,10),false));
        out.append(" nbt=").append(pipe.writeToNBT(new NBTTagCompound()).toString(),0,Math.min(300,pipe.writeToNBT(new NBTTagCompound()).toString().length()));
        return out.toString();
    }
    private static final BlockPos FROZEN=ROOT.add(-5,0,-5);
    /**
     * Arms freeze journal sources from inside a ticking tile. World.removeTileEntity then
     * leaves the old tile in the chunk map, and the pending proxy invalidates it again at the
     * end of the tick. Reproduce that phase with a real Mekanism tank.
     */
    private static void freezeDuringTileTick(WorldServer world,EntityPlayerMP player)throws Exception{
        IBlockState tank=null;
        scan:for(Block b:Block.REGISTRY)if(b.getRegistryName().getResourceDomain().equals("mekanism"))for(int meta=0;meta<16;meta++){
            IBlockState state;try{state=b.getStateFromMeta(meta);}catch(RuntimeException unusedMeta){continue;}if(!b.hasTileEntity(state))continue;
            TileEntity candidate=b.createTileEntity(world,state);if(candidate!=null&&candidate.getClass().getName().equals("mekanism.common.tile.TileEntityFluidTank")){tank=state;break scan;}
        }
        require(tank!=null,"installed Mekanism fluid tank");
        world.setBlockState(FROZEN.down(),Blocks.STONE.getDefaultState(),3);
        player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(tank.getBlock(),1,tank.getBlock().getMetaFromState(tank)));
        require(player.getHeldItemMainhand().onItemUse(player,world,FROZEN.down(),EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F)==EnumActionResult.SUCCESS,"tank placement");
        player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
        require(tank(world.getTileEntity(FROZEN)).fill(new FluidStack(FluidRegistry.WATER,500),true)==500,"tank holds fluid");
        net.minecraftforge.fml.common.ObfuscationReflectionHelper.setPrivateValue(net.minecraft.world.World.class,world,true,"field_147481_N");
        TileEntity original=world.getTileEntity(FROZEN);
        try{com.vandorlabs.copier.CopyWorld.freeze(world,FROZEN);}
        finally{net.minecraftforge.fml.common.ObfuscationReflectionHelper.setPrivateValue(net.minecraft.world.World.class,world,false,"field_147481_N");}
        // When the old tile was already visited this tick, it is still mapped when World.updateEntities
        // publishes pending tiles; perform that exact Chunk.addTileEntity step now.
        java.util.List<TileEntity> pending=net.minecraftforge.fml.common.ObfuscationReflectionHelper.getPrivateValue(net.minecraft.world.World.class,world,"field_147484_a");
        TileEntity proxy=null;for(TileEntity candidate:pending)if(candidate.getPos().equals(FROZEN))proxy=candidate;
        require(proxy!=null&&world.getChunkFromBlockCoords(FROZEN).getTileEntityMap().get(FROZEN)!=original,"detached tank is unmapped while its proxy is pending");
        world.getChunkFromBlockCoords(FROZEN).addTileEntity(FROZEN,proxy);
    }
    /** A duct or pipe on an arm's tip is a transfer connection, never a captured payload. */
    private static void conduits(WorldServer world,EntityPlayerMP player)throws Exception{
        String[][] kinds={{"thermaldynamics","cofh.thermaldynamics.duct.tiles.","Fluid"},{"mekanism","mekanism.common.tile.transmitter.TileEntityMechanicalPipe",""}};
        for(BlockPos pos:BlockPos.getAllInBox(ROOT.add(-3,-2,-3),ROOT.add(7,5,3)))world.setBlockState(pos,pos.getY()==ROOT.getY()-1?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState(),2);
        for(int index=0;index<kinds.length;index++){
            String[] kind=kinds[index];BlockPos mount=ROOT.east(index*4),tip=mount.up(2);
            player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(GearRegistry.GEAR_SWING_ARM,2));
            for(BlockPos support:new BlockPos[]{mount.down(),mount})require(player.getHeldItemMainhand().onItemUse(player,world,support,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F)==EnumActionResult.SUCCESS,"arm for "+kind[0]);
            IBlockState conduit=null;
            scan:for(Block b:Block.REGISTRY)if(b.getRegistryName().getResourceDomain().equals(kind[0]))for(int meta=0;meta<16;meta++){
                IBlockState state;try{state=b.getStateFromMeta(meta);}catch(RuntimeException unusedMeta){continue;}if(!b.hasTileEntity(state))continue;
                TileEntity tile=b.createTileEntity(world,state);String name=tile==null?"":tile.getClass().getName();
                if(name.startsWith(kind[1])&&name.contains(kind[2])&&TransferNetworkCompat.component(tile)){conduit=state;break scan;}
            }
            require(conduit!=null,"installed "+kind[0]+" fluid conduit");
            player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(conduit.getBlock(),1,conduit.getBlock().getMetaFromState(conduit)));
            require(player.getHeldItemMainhand().onItemUse(player,world,mount.up(),EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F)==EnumActionResult.SUCCESS,"ordinary conduit placement on the arm tip");
            IBlockState placed=world.getBlockState(tip);TileEntity tile=world.getTileEntity(tip);
            require(TransferNetworkCompat.component(tile),"conduit placed on the tip "+placed);
            GearTileEntity arm=(GearTileEntity)world.getTileEntity(mount);require(arm.rebuild(),"arm forms under "+kind[0]);arm.operator(player);
            NBTTagCompound settings=arm.settings();settings.setInteger(ArmResource.CHOICE,ArmResource.FLUID.ordinal());arm.configure(settings);
            require(ArmPayload.discover(arm.loadedController(),player)==null,"conduit on the tip is not captured "+kind[0]);
            require(world.getBlockState(tip)==placed&&world.getTileEntity(tip)==tile,"conduit stays in place "+kind[0]);
            if(index==1){
                // A Mekanism pipe accepts fluid only when its network has a destination.
                IBlockState tank=null;
                scan:for(Block b:Block.REGISTRY)if(b.getRegistryName().getResourceDomain().equals("mekanism"))for(int meta=0;meta<16;meta++){
                    IBlockState state;try{state=b.getStateFromMeta(meta);}catch(RuntimeException unusedMeta){continue;}if(!b.hasTileEntity(state))continue;
                    TileEntity candidate=b.createTileEntity(world,state);if(candidate!=null&&candidate.getClass().getName().equals("mekanism.common.tile.TileEntityFluidTank")){tank=state;break scan;}
                }
                require(tank!=null,"installed Mekanism fluid tank destination");
                player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(tank.getBlock(),1,tank.getBlock().getMetaFromState(tank)));
                require(player.getHeldItemMainhand().onItemUse(player,world,tip,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F)==EnumActionResult.SUCCESS,"pipe destination tank");
            }
            world.notifyNeighborsOfStateChange(mount,world.getBlockState(mount).getBlock(),true);world.notifyNeighborsOfStateChange(mount.up(),world.getBlockState(mount.up()).getBlock(),true);
            player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
        }
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
