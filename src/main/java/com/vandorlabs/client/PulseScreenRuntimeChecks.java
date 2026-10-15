package com.vandorlabs.client;

import com.vandorlabs.GuiHandler;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.redstone.*;
import com.vandorlabs.vehicle.*;
import com.vandorlabs.render.ScreenSurface;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import java.io.File;
import java.lang.reflect.*;
import java.util.*;

/** Real channel aggregation, save/unload, captured adapters and integrated mouse packets. */
final class PulseScreenRuntimeChecks {
    private static final BlockPos POS=new BlockPos(112,100,112);
    private static final int A=23971,B=23972;
    private static int stage,frame,total;private static boolean requestedGui;private static long firstDeadline,extendedDeadline;
    private static Vec3d hit;
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("pulse screen: "+message);}
    private static WorldServer world(Minecraft mc){return mc.getIntegratedServer().getWorld(0);}
    private static EntityPlayerMP owner(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());}
    private static RedstoneScreenContents contents(Minecraft mc){return ((TileEntityAnimatedScreenSelector)world(mc).getTileEntity(POS)).redstoneScreen(0);}
    private static NBTTagList rows(){
        NBTTagList rows=new NBTTagList();NBTTagCompound pulse=new NBTTagCompound();pulse.setString("Label","Activate");ChannelData.write(pulse,ChannelList.of(A,B));pulse.setBoolean("Pulse",true);rows.appendTag(pulse);
        NBTTagCompound toggle=new NBTTagCompound();toggle.setString("Label","Other source");ChannelData.write(toggle,ChannelList.of(B));rows.appendTag(toggle);return rows;
    }
    private static void configure(RedstoneScreenContents contents){require(contents.applyRowConfiguration("PULSE CONTROL",rows()),"configure Pulse and independent Toggle");}
    static void begin(Minecraft mc)throws Exception{
        stage=frame=total=0;requestedGui=false;
        mc.getIntegratedServer().addScheduledTask(()->{
            WorldServer w=world(mc);EntityPlayerMP p=owner(mc);p.dismountRidingEntity();p.setSneaking(false);p.capabilities.isCreativeMode=true;p.capabilities.allowFlying=true;p.capabilities.isFlying=true;p.sendPlayerAbilities();p.setHeldItem(EnumHand.MAIN_HAND,net.minecraft.item.ItemStack.EMPTY);
            worldChecks(w,p);capturedChecks(w,p);
            for(BlockPos at:BlockPos.getAllInBox(POS.add(-3,-3,-3),POS.add(3,3,3)))w.setBlockToAir(at);
            w.setBlockState(POS,ModBlocks.ANIMATED_SCREEN_SELECTOR.getDefaultState(),3);
            RedstoneScreenContents c=contents(mc);require(c.configureRows(Arrays.asList("Activate","Other source"),Arrays.asList(ChannelList.of(A,B),ChannelList.of(B)),0),"GUI fixture");
            p.setPositionAndUpdate(POS.getX()+.5,POS.getY(),POS.getZ()-2);p.setHeldItem(EnumHand.MAIN_HAND,new net.minecraft.item.ItemStack(com.vandorlabs.items.ModItems.CONFIGURIZER));p.inventoryContainer.detectAndSendChanges();
        }).get();
    }
    private static void worldChecks(WorldServer world,EntityPlayerMP player){
        Block[] blocks={ModBlocks.ANIMATED_SCREEN_SELECTOR,ModBlocks.PROGRAMMABLE_DIAGONAL_SCREEN,ModBlocks.PROGRAMMABLE_INPUT,ModBlocks.PROGRAMMABLE_FULL_INPUT,ModBlocks.PROGRAMMABLE_CONSOLE,ModBlocks.PROGRAMMABLE_HALF_CONSOLE,Block.getBlockFromName("vandorlabs:programmable_diagonal_half_console")};
        for(Block block:blocks){
            world.setBlockToAir(POS);world.setBlockState(POS,block.getDefaultState(),3);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(POS);
            for(int slot=0;slot<2;slot++)if(RedstoneScreenInteractions.supportsSlot(block,slot)){
                RedstoneScreenContents c=tile.redstoneScreen(slot);configure(c);c.toggleRow(1);c.toggleRow(0);
                require(RedstoneChannels.level(world,A)==15&&RedstoneChannels.level(world,B)==15,"Pulse powers complete bank for "+block+" slot="+slot);
                require(!c.rows().get(0).isChannelLatch(),"Pulse never joins persistent latch bank");
                require(!c.rowConfiguration().getCompoundTagAt(0).hasKey("PulseUntil"),"configuration excludes live deadline");
                NBTTagCompound saved=c.writeToNBT(new NBTTagCompound());require(saved.getTagList("Rows",10).getCompoundTagAt(0).getLong("PulseUntil")==world.getTotalWorldTime()+20,"server owns twenty-tick deadline");
                c.onChunkUnload();require(RedstoneChannels.level(world,A)==0,"unloaded pulse unsubscribes");c.readFromNBT(saved);c.finishLoading();require(RedstoneChannels.level(world,A)==15,"reload resumes remaining pulse");
                c.expirePulses(world.getTotalWorldTime()+20);require(RedstoneChannels.level(world,A)==0&&RedstoneChannels.level(world,B)==15,"expiry retains independent row source");
                saved.getTagList("Rows",10).getCompoundTagAt(0).setLong("PulseUntil",world.getTotalWorldTime()-1);c.onChunkUnload();c.readFromNBT(saved);c.finishLoading();require(RedstoneChannels.level(world,A)==0,"expired loaded pulse stays off");
                NBTTagList invalid=rows();invalid.getCompoundTagAt(0).setBoolean("Slider",true);require(!c.applyRowConfiguration("Invalid",invalid),"conflicting row types rejected");
                c.setEnabled(false);
            }
        }
        world.setBlockToAir(POS);System.out.println("[vandorlabs][reprolab] pulse-screen-world PASS all-screen-input-console-slots bank-aggregation twenty-tick NBT config-only unload reload stale-expiry validation");
    }
    private static void capturedChecks(WorldServer world,EntityPlayerMP player){
        BlockPos local=new BlockPos(0,1,0),seat=new BlockPos(3,0,0);TileEntityAnimatedScreenSelector tile=new TileEntityAnimatedScreenSelector();tile.setPos(local);configure(tile.redstoneScreen(0));
        List<VehicleStructure.Cell> cells=new ArrayList<>();cells.add(new VehicleStructure.Cell(local,ModBlocks.ANIMATED_SCREEN_SELECTOR.getDefaultState(),tile.writeToNBT(new NBTTagCompound())));cells.add(new VehicleStructure.Cell(seat,ModBlocks.PILOT_SEAT.getDefaultState(),null));
        EntityGroundVehicle craft=new EntityGroundVehicle(world);craft.install(new VehicleStructure(cells,seat,EnumFacing.NORTH,1));craft.setPosition(260,100,260);craft.signals.tick();
        try{
            RedstoneScreenContents c=((TileEntityAnimatedScreenSelector)craft.view.getTileEntity(local)).redstoneScreen(0);
            point(player,craft.view.getBlockState(local),(TileEntityAnimatedScreenSelector)craft.view.getTileEntity(local),0,1,craft,local);require(craft.signals.click(local,player),"captured Toggle actual ray click");
            point(player,craft.view.getBlockState(local),(TileEntityAnimatedScreenSelector)craft.view.getTileEntity(local),0,0,craft,local);require(craft.signals.click(local,player),"captured Pulse actual ray click");
            require(RedstoneChannels.level(world,A)==15,"captured pulse contributes parent channel source");
            NBTTagCompound saved=craft.structure.write();craft.signals.close();require(RedstoneChannels.level(world,A)==0,"captured detach unsubscribes");
            craft.install(VehicleStructure.read(saved));craft.signals.open();craft.signals.tick();c=((TileEntityAnimatedScreenSelector)craft.view.getTileEntity(local)).redstoneScreen(0);require(c.rows().get(0).pulse&&RedstoneChannels.level(world,A)==15,"captured reload resumes persisted deadline");
            c.rows().get(0).expirePulse(world.getTotalWorldTime()+20);craft.signals.close();craft.signals.open();craft.signals.tick();require(RedstoneChannels.level(world,A)==0&&RedstoneChannels.level(world,B)==15,"captured expired source preserves linked Toggle");
            // Parking restores the same private screen NBT into a placed tile.
            NBTTagCompound parked=((TileEntityAnimatedScreenSelector)craft.view.getTileEntity(local)).writeToNBT(new NBTTagCompound());parked.setInteger("x",POS.getX());parked.setInteger("y",POS.getY());parked.setInteger("z",POS.getZ());
            craft.signals.close();world.setBlockToAir(POS);world.setBlockState(POS,ModBlocks.ANIMATED_SCREEN_SELECTOR.getDefaultState(),3);TileEntityAnimatedScreenSelector placed=(TileEntityAnimatedScreenSelector)world.getTileEntity(POS);placed.readFromNBT(parked);placed.redstoneScreen(0).finishLoading();require(RedstoneChannels.level(world,A)==0,"expired captured pulse cannot revive when parked");
            world.setBlockToAir(POS);
        }finally{craft.setDead();}
        System.out.println("[vandorlabs][reprolab] pulse-screen-captured PASS actual-ray-click parent-bank source-isolation save-reload detach park");
    }
    private static void point(EntityPlayerMP player,IBlockState state,TileEntityAnimatedScreenSelector tile,int slot,int row,EntityGroundVehicle craft,BlockPos at){
        ScreenSurface.Quad q=RedstoneScreenInteractions.surface(state,tile,slot);double u=109/128D,v=(RedstoneScreenInteractions.rowTop(state.getBlock(),slot)+row*12+5D)/RedstoneScreenInteractions.displayHeight(state.getBlock(),slot);
        Vec3d target=new Vec3d((q.topRight.x+(q.topLeft.x-q.topRight.x)*u)/16,(q.topRight.y+(q.bottomRight.y-q.topRight.y)*v)/16,(q.topRight.z+(q.bottomRight.z-q.topRight.z)*v)/16).add(new Vec3d(at));
        Vec3d eye=target.addVector(0,q.ny*2,q.nz*2);if(craft!=null){target=craft.toWorld(target);eye=craft.toWorld(eye);}hit=target;Vec3d d=target.subtract(eye);float yaw=(float)Math.toDegrees(Math.atan2(-d.x,d.z)),pitch=(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z)));player.connection.setPlayerLocation(eye.x,eye.y-player.getEyeHeight(),eye.z,yaw,pitch);
    }
    private static void next(){stage++;frame=0;}
    private static long time(Minecraft mc)throws Exception{long[] result={0};mc.getIntegratedServer().addScheduledTask(()->{result[0]=world(mc).getTotalWorldTime();}).get();return result[0];}
    private static void click(Minecraft mc)throws Exception{mc.playerController.processRightClickBlock(mc.player,mc.world,POS,EnumFacing.NORTH,hit,EnumHand.MAIN_HAND);}
    private static GuiButton button(GuiScreen gui,int id){List<GuiButton> buttons=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(GuiScreen.class,gui,"buttonList","field_146292_n");for(GuiButton b:buttons)if(b.id==id)return b;throw new IllegalStateException("Missing button "+id);}
    private static void mouse(GuiScreen gui,int id,int which)throws Exception{Method m;try{m=GuiScreen.class.getDeclaredMethod("mouseClicked",int.class,int.class,int.class);}catch(NoSuchMethodException e){m=GuiScreen.class.getDeclaredMethod("func_73864_a",int.class,int.class,int.class);}m.setAccessible(true);GuiButton b=button(gui,id);m.invoke(gui,b.x+b.width/2,b.y+b.height/2,which);}
    static boolean tick(Minecraft mc,File output)throws Exception{
        require(++total<400,"bounded focused client timeout stage="+stage+" gui="+(mc.currentScreen==null?"none":mc.currentScreen.getClass().getSimpleName())+" requested="+requestedGui+" clientTile="+mc.world.getTileEntity(POS));frame++;
        if(stage==0){
            if(!requestedGui){if(frame<8||!(mc.world.getTileEntity(POS) instanceof TileEntityAnimatedScreenSelector)||mc.player.getHeldItemMainhand().getItem()!=com.vandorlabs.items.ModItems.CONFIGURIZER)return false;mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP p=owner(mc);p.openGui(VandorLabs.instance,GuiHandler.GUI_REDSTONE_SCREEN,world(mc),POS.getX(),POS.getY(),POS.getZ());System.out.println("[vandorlabs][reprolab] pulse-screen-dialog requested server-distance="+p.getDistanceSqToCenter(POS)+" container="+p.openContainer.getClass().getSimpleName());}).get();requestedGui=true;frame=0;return false;}
            if(!(mc.currentScreen instanceof GuiRedstoneScreen))return false;GuiScreen gui=mc.currentScreen;mouse(gui,7,1);require(button(gui,7).displayString.equals("Control: Pulse"),"right-click selects previous Pulse from Toggle");mouse(gui,7,0);require(button(gui,7).displayString.equals("Control: Toggle"),"forward restores Toggle");mouse(gui,7,1);mouse(gui,4,0);next();return false;
        }
        if(stage==1&&frame>8){mc.getIntegratedServer().addScheduledTask(()->{require(contents(mc).rows().get(0).pulse,"actual GUI packet saved Pulse type");owner(mc).setHeldItem(EnumHand.MAIN_HAND,net.minecraft.item.ItemStack.EMPTY);owner(mc).inventoryContainer.detectAndSendChanges();contents(mc).toggleRow(1);point(owner(mc),world(mc).getBlockState(POS),(TileEntityAnimatedScreenSelector)world(mc).getTileEntity(POS),0,0,null,POS);}).get();next();return false;}
        if(stage==2&&frame>5){click(mc);next();return false;}
        if(stage==3&&frame>3){mc.getIntegratedServer().addScheduledTask(()->{RedstoneScreenContents c=contents(mc);require(c.rows().get(0).active()&&RedstoneChannels.level(world(mc),A)==15,"actual placed click starts pulse");firstDeadline=c.rows().get(0).pulseUntil();}).get();javax.imageio.ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"shot_pulse_screen_on.png"));next();return false;}
        if(stage==4&&frame>5){click(mc);next();return false;}
        if(stage==5&&frame>3){mc.getIntegratedServer().addScheduledTask(()->{extendedDeadline=contents(mc).rows().get(0).pulseUntil();require(extendedDeadline>firstDeadline,"repeat click extends server deadline");}).get();next();return false;}
        if(stage==6){long now=time(mc);if(now<firstDeadline)return false;if(now<extendedDeadline)mc.getIntegratedServer().addScheduledTask(()->require(RedstoneChannels.level(world(mc),A)==15,"pulse survives original deadline after repeat")).get();next();return false;}
        if(stage==7){long now=time(mc);if(now<=extendedDeadline+1)return false;mc.getIntegratedServer().addScheduledTask(()->{require(RedstoneChannels.level(world(mc),A)==0&&RedstoneChannels.level(world(mc),B)==15,"active-only world tick expires pulse without clobbering Toggle");require(!contents(mc).rows().get(0).active(),"pulse returns to idle");}).get();javax.imageio.ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"shot_pulse_screen_off.png"));System.out.println("[vandorlabs][reprolab] pulse-screen-client PASS actual-mouse reverse-cycle GUI-packet block-click repeat-extends twenty-tick-expiry independent-source screenshots");next();return true;}
        return stage>7;
    }
    private PulseScreenRuntimeChecks(){}
}
