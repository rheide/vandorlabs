package com.vandorlabs.client;

import net.minecraft.tileentity.TileEntity;

import com.vandorlabs.navigation.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.redstone.*;
import com.vandorlabs.network.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import io.netty.buffer.*;
import java.io.File;
import java.util.*;

/** Exact-JAR live seed sampling, reader persistence, packet bounds and real player clicks. */
final class NavigationWorldChecks {
    private static final BlockPos POS=new BlockPos(88,110,88),BUILDER=POS.east(12);
    private static final BlockPos SCREEN=POS.south(7);
    private static final String[] SCREENS={"programmable_diagonal_half_screen","programmable_diagonal_screen","programmable_half_console","programmable_console","programmable_viewscreen","programmable_viewscreen"};
    private static boolean enhancements,refinementSeen;
    private static MessageNavigationImage preview;
    private static long previewAt;
    private static boolean flightComplete;
    private static int stage,ticks,kind;private static boolean captured;private static File output;private static String shot;
    private static void require(boolean value,String why){if(!value)throw new IllegalStateException("navigation-world: "+why);}
    private static EntityPlayerMP player(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayers().get(0);}
    private static void server(Minecraft mc,Runnable work)throws Exception{mc.getIntegratedServer().addScheduledTask(work).get();}
    private static TileEntityNavigationTable table(World w){return ((TileEntityNavigationTable)w.getTileEntity(POS)).reader();}
    private static TileEntityConstructor build(WorldServer w,BlockPos at,String name){FrameMachineDesign spec=FrameMachineDesign.get(name);BridgeMachineChecks.frame(w,at,spec,0);BridgeMachineChecks.frame(w,at,spec,2);TileEntityConstructor tile=(TileEntityConstructor)w.getTileEntity(at);require(tile.assemble(),"assemble "+name+" "+tile.assemblyProblem());DeferredTileLoad.flush(w);return tile;}
    static boolean tick(Minecraft mc,File directory)throws Exception{
        output=directory;mc.gameSettings.pauseOnLostFocus=false;if(++ticks>1600)throw new IllegalStateException("navigation timeout stage="+stage+" kind="+kind+" gui="+(mc.currentScreen==null?"none":mc.currentScreen.getClass().getName())+" clientTile="+mc.world.getTileEntity(SCREEN)+" mode="+mc.player.isCreative()+" slot="+mc.player.inventory.currentItem+" held="+mc.player.getHeldItemMainhand());
        if(stage>0&&!refinementSeen)observeRefinement();
        if(stage==0){mc.player.closeScreen();mc.playerController.setGameType(GameType.SURVIVAL);mc.player.inventory.currentItem=0;mc.getConnection().sendPacket(new net.minecraft.network.play.client.CPacketHeldItemChange(0));server(mc,()->{
            EntityPlayerMP p=player(mc);WorldServer w=p.getServerWorld();p.interactionManager.setGameType(GameType.SURVIVAL);p.inventory.clear();p.inventory.currentItem=0;p.capabilities.allowFlying=true;p.capabilities.isFlying=true;p.sendPlayerAbilities();
            for(BlockPos at:BlockPos.getAllInBox(POS.add(-4,-1,-4),POS.add(22,7,8)))w.setBlockState(at,at.getY()==POS.getY()-1?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState(),2);
            for(int x=0;x<2;x++)for(int z=0;z<2;z++)w.setBlockState(POS.add(x,0,z),Block.getBlockFromName("vandorlabs:navigation_table_flat").getDefaultState().withProperty(BlockVandorDirectional.FACING,z==0?EnumFacing.NORTH:EnumFacing.SOUTH),3);
            NavigationAssemblies.rebuild(w,POS);TileEntityNavigationTable t=table(w);require(t.screenSettings().texture==NavigationMap.SURFACE,"fresh Navigation default");
            NBTTagCompound old=t.writeToNBT(new NBTTagCompound());old.setInteger("NavigationTexture",-1);TileEntityNavigationTable prior=new TileEntityNavigationTable();prior.readFromNBT(old);require(prior.screenSettings().texture==-1,"saved black choice preserved");
            TileEntityConstructor dish=build(w,POS.east(4),"standalone_dish"),array=build(w,POS.west(4),"standalone_antenna");
            TransmitterSlots.configure(dish,ChannelList.of(42),256,false);TransmitterSlots.configure(array,ChannelList.of(43),256,false);
            t.configureDisplay(new NavigationScreenSettings(NavigationMap.SURFACE,true,1,false,false),true,127,0,ChannelList.of(42,43));
            require(NavigationSources.noPower(w,t.getPos(),ChannelList.of(42,43))&&NavigationService.coverage(t).isEmpty(),"linked observers without input report no power");
            for(TileEntityConstructor observer:new TileEntityConstructor[]{dish,array}){
                // Exactly one bottom cell, the centre Power Port, accepts RF. On the 2x2 Array the
                // port is cell(1,0,1), so check every bottom cell instead of a fixed corner.
                for(int x=0;x<observer.structureWidth();x++)for(int z=0;z<observer.structureDepth();z++){
                    BlockPos at=observer.cell(x,0,z);TileEntity cell=w.getTileEntity(at);
                    boolean accepts=cell!=null&&cell.getCapability(net.minecraftforge.energy.CapabilityEnergy.ENERGY,EnumFacing.DOWN)!=null;
                    require(accepts==at.equals(observer.observerPowerPort()),"only the centre Power Port accepts RF cell="+x+","+z+" accepts="+accepts+" port="+observer.observerPowerPort().subtract(observer.getPos()));
                }
                TileEntity member=w.getTileEntity(observer.observerPowerPort());
                net.minecraftforge.energy.IEnergyStorage input=member.getCapability(net.minecraftforge.energy.CapabilityEnergy.ENERGY,EnumFacing.DOWN);
                require(input!=null&&member.getCapability(net.minecraftforge.energy.CapabilityEnergy.ENERGY,EnumFacing.UP)==null,"only the underside of the centre Power Port accepts power");
                require(input.receiveEnergy(ObserverPower.RF_PER_TICK,true)==ObserverPower.RF_PER_TICK&&!observer.observerPowered(),"simulated input stays unpowered");
                require(input.receiveEnergy(ObserverPower.RF_PER_TICK,false)==ObserverPower.RF_PER_TICK&&observer.observerPowered(),"RF makes the installed observer available");
            }
            require(NavigationService.coverage(t).size()==2,"multiple channels union");require(NavigationSources.contains(NavigationService.coverage(t),dish.getPos().getX()+NavigationSources.radius(dish),dish.getPos().getZ()),"dish radius inclusive");require(!NavigationSources.contains(NavigationService.coverage(t),dish.getPos().getX()+NavigationSources.radius(dish)+1,dish.getPos().getZ()),"beyond dish radius excluded");
            radar(p,w,t,dish,array);
            t.setRedstoneChannels(ChannelList.of(43));require(NavigationService.coverage(t).size()==1&&NavigationService.coverage(t).get(0).radius==NavigationSources.radius(array),"array radius");t.setRedstoneChannels(ChannelList.of(42,43));
            TransmitterSlots.configure(dish,ChannelList.of(42),4,false);require(NavigationService.coverage(t).size()==2,"connection radius inclusive");TransmitterSlots.configure(dish,ChannelList.of(42),256,false);
            dish.dismantle();array.dismantle();require(NavigationService.coverage(t).isEmpty(),"unformed sources disconnected");require(dish.assemble()&&array.assemble(),"source repair reconnects");
            power(w,dish);power(w,array);
            ItemStack chip=new ItemStack(com.vandorlabs.items.ModItems.DATA_CHIP,1,3);NavigationChip.write(chip,0,120000,-130000);t.setInventorySlotContents(0,chip);NavigationService.View view=NavigationService.view(t);require(view.x==120000&&view.z==-130000,"inserted pin recenters");require(NavigationSources.contains(NavigationService.coverage(t),121000,-130000),"saved chip radius independent of source");
            view.x+=200;ItemStack removed=t.removeStackFromSlot(0);NavigationService.view(t);t.setInventorySlotContents(0,removed);require(NavigationService.view(t).x==120000,"reinsertion recenters");
            ItemStack retained=t.getStackInSlot(0).copy();TileEntityNavigationTable saved=new TileEntityNavigationTable();saved.readFromNBT(t.writeToNBT(new NBTTagCompound()));require(ItemStack.areItemStacksEqual(retained,saved.getStackInSlot(0)),"saved coordinates and chip metadata round trip");require(NavigationChip.value(retained,"Y")==64,"unused default Y stored");
            t.removeStackFromSlot(0);NavigationService.view(t);t.setInventorySlotContents(0,new ItemStack(com.vandorlabs.items.ModItems.DATA_CHIP));view.x=POS.getX();view.z=POS.getZ();view.zoom=1;
            int chunks=w.getChunkProvider().getLoadedChunkCount();Set<Integer> colors=new HashSet<>();for(int x=0;x<48;x++)for(int z=0;z<48;z++)colors.add(NavigationService.color(w,100000+x*128,100000+z*128));require(colors.size()>3,"seed image contains terrain diversity");require(w.getChunkProvider().getLoadedChunkCount()==chunks,"seed requests never load chunks");
            generatorError(w);artworkPreservation(w);wire(retained);support(w,p);NavigationObserverChecks.run(w);
            TileEntityConstructor builder=build(w,BUILDER,"synthesis_tower");builder.configureSynthesisRedstone(1,ChannelList.EMPTY);require(!builder.synthesisEnabled(),"activation starts off");
            p.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(Blocks.LEVER,2));p.inventoryContainer.detectAndSendChanges();aim(p,new Vec3d(BUILDER).addVector(1.5,1.5,-2),new Vec3d(BUILDER).addVector(1.5,1.5,0));
            System.out.println("[vandorlabs] navigation-world-runtime PASS coverage channels radii chip-persistence seed-colors="+colors.size()+" chunks="+chunks+" unchanged");
        });stage=1;ticks=0;return false;}
        if(stage==1){if(ticks<20||mc.player.isCreative()||mc.player.inventory.currentItem!=0||mc.player.getHeldItemMainhand().getItem()!=net.minecraft.item.Item.getItemFromBlock(Blocks.LEVER)||mc.player.getHeldItemMainhand().getCount()!=2)return false;
            BlockPos wall=BUILDER.add(1,1,0);if(!(mc.world.getTileEntity(wall) instanceof TileEntityConstructorPart))return false;
            Vec3d target=new Vec3d(wall).addVector(.5,.5,0);mc.playerController.processRightClickBlock(mc.player,mc.world,wall,EnumFacing.NORTH,target,EnumHand.MAIN_HAND);stage=2;ticks=0;return false;}
        if(stage==2){BlockPos lever=BUILDER.add(1,1,-1);if(mc.world.getBlockState(lever).getBlock()!=Blocks.LEVER)return false;server(mc,()->{EntityPlayerMP p=player(mc);require(p.getHeldItemMainhand().getCount()==1,"Survival wall attachment consumes one lever");});mc.playerController.processRightClickBlock(mc.player,mc.world,lever,EnumFacing.NORTH,new Vec3d(lever).addVector(.5,.5,.5),EnumHand.MAIN_HAND);stage=3;ticks=0;return false;}
        if(stage==3){if(ticks<15)return false;server(mc,()->{EntityPlayerMP p=player(mc);require(((TileEntityConstructor)p.world.getTileEntity(BUILDER)).synthesisEnabled(),"wall lever activates assembler");p.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);p.inventoryContainer.detectAndSendChanges();NavigationLayout.Screen screen=NavigationLayout.mapScreen(NavigationLayout.Kind.FLAT,NavigationLayout.Facing.NORTH,new NavigationLayout.Cell(POS.getX(),POS.getY(),POS.getZ()),2,2);NavigationLayout.Vec3 at=screen.overlayPoint(NavigationViewport.canvas(table(p.world),0).buttonU(1),NavigationViewport.canvas(table(p.world),0).buttonV(1));Vec3d target=new Vec3d(at.x,at.y,at.z);aim(p,target.addVector(0,3,-.8),target);});stage=4;ticks=0;return false;}
        if(stage==4){if(ticks<20||!mc.player.getHeldItemMainhand().isEmpty())return false;TileEntityNavigationTable tile=table(mc.world);if(tile==null)return false;
            mc.playerController.processRightClickBlock(mc.player,mc.world,POS,EnumFacing.UP,new Vec3d(POS).addVector(.5,.875,.5),EnumHand.MAIN_HAND);server(mc,()->{});stage=5;ticks=0;return false;}
        if(stage==5){if(ticks<20)return false;server(mc,()->{EntityPlayerMP p=player(mc);TileEntityNavigationTable t=table(p.world);NavigationService.View v=NavigationService.view(t);require(v.x<POS.getX(),"real table button pans map");v.x=POS.getX();v.z=POS.getZ();NavigationLayout.Screen s=NavigationLayout.mapScreen(NavigationLayout.Kind.FLAT,NavigationLayout.Facing.NORTH,new NavigationLayout.Cell(POS.getX(),POS.getY(),POS.getZ()),2,2);NavigationLayout.Vec3 at=s.overlayPoint(NavigationViewport.canvas(t,0).buttonU(7),NavigationViewport.canvas(t,0).buttonV(7));Vec3d target=new Vec3d(at.x,at.y,at.z);aim(p,target.addVector(0,3,-.8),target);});stage=6;ticks=0;return false;}
        if(stage==6){
            TileEntityNavigationTable tile=table(mc.world);
            NavigationMapRenderer.bind(tile,0,1);
            if(ticks<15||ticks%5!=0||!surfaceReady(mc))return false;
            mc.playerController.processRightClickBlock(mc.player,mc.world,POS,EnumFacing.UP,
                    new Vec3d(POS).addVector(.5,.875,.5),EnumHand.MAIN_HAND);
            stage=7;ticks=0;return false;
        }
        if(stage==7){if(ticks<30)return false;server(mc,()->{EntityPlayerMP p=player(mc);require(NavigationChip.programmed(table(p.world).getStackInSlot(0)),"real write button programs retained chip");aim(p,new Vec3d(POS).addVector(1,4,-1.5),new Vec3d(POS).addVector(1,.875,1));});mc.gameSettings.hideGUI=true;stage=8;ticks=0;return false;}
        if(stage==8){if(ticks<180||!NavigationMapRenderer.ready(POS)||!refinementSeen)return false;if(!enhancements){server(mc,()->surface(player(mc)));NavigationObserverChecks.visual(mc);NavigationEnhancementChecks.run(mc,output,table(mc.world));enhancements=true;}shot="navigation-world-table.png";captured=false;stage=9;ticks=0;return false;}
        if(stage==9&&captured){stage=10;ticks=0;return false;}
        if(stage==10){mc.gameSettings.hideGUI=false;mc.player.closeScreen();server(mc,()->{
            EntityPlayerMP p=player(mc);WorldServer w=p.getServerWorld();for(BlockPos at:BlockPos.getAllInBox(SCREEN.add(-1,0,-1),SCREEN.add(3,2,2)))w.setBlockToAir(at);
            Block block=Block.getBlockFromName("vandorlabs:"+SCREENS[kind]);require(block!=null,"screen family "+SCREENS[kind]);w.setBlockState(SCREEN,block.getDefaultState(),3);if(kind==5)w.setBlockState(SCREEN.east(),block.getDefaultState(),3);
            TileEntityAnimatedScreenSelector t=(TileEntityAnimatedScreenSelector)w.getTileEntity(SCREEN);int slot=ScreenChipSupport.displaySlot(block);require(ScreenChipSupport.navigationAvailable(t,slot),"Navigation supported on existing mount family");
            ItemStack pin=new ItemStack(com.vandorlabs.items.ModItems.DATA_CHIP,1,2);NavigationChip.write(pin,0,88,88);
            if(kind>=4){BlockPos mount=SCREEN.east(kind==5?2:1);w.setBlockState(mount,Block.getBlockFromName("vandorlabs:data_chip_slot").getDefaultState().withProperty(BlockVandorSwitch.FACING,EnumFacing.EAST),3);((TileEntityTokenSlot)w.getTileEntity(mount)).setInventorySlotContents(0,pin);}else t.ownedChipReader().setInventorySlotContents(0,pin);
            aim(p,new Vec3d(SCREEN).addVector(.5,1.2,-2),new Vec3d(SCREEN).addVector(.5,.5,.5));p.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(com.vandorlabs.items.ModItems.CONFIGURIZER));p.inventoryContainer.detectAndSendChanges();
        });stage=101;ticks=0;return false;}
        if(stage==101){if(ticks<20||!(mc.world.getTileEntity(SCREEN) instanceof TileEntityAnimatedScreenSelector)||mc.player.getHeldItemMainhand().getItem()!=com.vandorlabs.items.ModItems.CONFIGURIZER)return false;mc.playerController.processRightClickBlock(mc.player,mc.world,SCREEN,EnumFacing.NORTH,new Vec3d(SCREEN).addVector(.5,.5,0),EnumHand.MAIN_HAND);stage=11;ticks=0;return false;}
        if(stage==11){
            if(ticks<15||mc.currentScreen==null||!(mc.world.getTileEntity(SCREEN) instanceof TileEntityAnimatedScreenSelector))return false;
            Object gui=mc.currentScreen;ScreenTextureList textures=(ScreenTextureList)field(gui,kind==2?"topList":"screenList");HousingTextureList picker=(HousingTextureList)field(textures,"list");require(field(picker,"navigationAction")!=null,"Navigation picker row "+SCREENS[kind]);
            picker.setSelected(NavigationMap.SURFACE);@SuppressWarnings("unchecked") java.util.List<Integer> rows=(java.util.List<Integer>)field(picker,"rows");int index=rows.indexOf(-100004);require(index>=0,"Navigation appended row");int x=(Integer)field(picker,"x")+8,y=(Integer)field(picker,"y")+(index-(Integer)field(picker,"scroll"))*HousingTextureList.ROW_HEIGHT+8;picker.click(x,y,0);mc.player.closeScreen();stage=12;ticks=0;return false;
        }
        if(stage==12){
            if(ticks<20)return false;TileEntityAnimatedScreenSelector t=(TileEntityAnimatedScreenSelector)mc.world.getTileEntity(SCREEN);int slot=ScreenChipSupport.displaySlot(t.getBlockType());if(!t.hasNavigation(slot))return false;
            server(mc,()->{TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)player(mc).world.getTileEntity(SCREEN);require(tile.hasNavigation(slot),"authorized Navigation selection round trip");require(NavigationChip.programmed(NavigationService.chip(tile)),"existing chip mount supplies pin");require(NavigationService.coverage(tile).size()==1,"chip-only navigation coverage");if(kind==5){require(((TileEntityAnimatedScreenSelector)player(mc).world.getTileEntity(SCREEN.east())).hasNavigation(0),"joined Navigation selection");require(ScreenChipGroups.group(tile).members.size()==2,"joined reader retained");}
                player(mc).setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);player(mc).inventoryContainer.detectAndSendChanges();aim(player(mc),new Vec3d(SCREEN).addVector(1,2,-2.5),new Vec3d(SCREEN).addVector(kind==5?1:.5,.5,.5));});mc.gameSettings.hideGUI=true;stage=13;ticks=0;return false;
        }
        if(stage==13){
            if(ticks<200||!NavigationMapRenderer.ready(SCREEN))return false;
            if(kind==0){
                server(mc,()->{
                    WorldServer w=player(mc).getServerWorld();
                    TileEntityConstructor dish=(TileEntityConstructor)w.getTileEntity(POS.east(4));
                    w.setBlockToAir(dish.observerPowerPort().down());
                    ((TileEntityAnimatedScreenSelector)w.getTileEntity(SCREEN)).setRedstoneChannels(ChannelList.of(42));
                });
                NavigationMapRenderer.changed(SCREEN);stage=131;ticks=0;return false;
            }
            shot="navigation-world-screen-"+kind+".png";captured=false;stage=14;ticks=0;return false;
        }
        if(stage==131){
            if(!hasPowerHaiku(SCREEN))return false;
            server(mc,()->{
                WorldServer w=player(mc).getServerWorld();
                power(w,(TileEntityConstructor)w.getTileEntity(POS.east(4)));
                ((TileEntityAnimatedScreenSelector)w.getTileEntity(SCREEN)).setRedstoneChannels(ChannelList.EMPTY);
            });
            System.out.println("[vandorlabs] navigation-screen power-haiku PASS");
            shot="navigation-world-screen-"+kind+".png";captured=false;stage=14;ticks=0;return false;
        }
        if(stage==14&&captured){if(++kind<SCREENS.length){stage=10;ticks=0;return false;}server(mc,()->{EntityPlayerMP p=player(mc);BlockPos at=POS.west(4);p.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(com.vandorlabs.items.ModItems.CONFIGURIZER));p.inventoryContainer.detectAndSendChanges();aim(p,new Vec3d(at).addVector(.5,1,-2),new Vec3d(at).addVector(.5,.5,.5));p.openGui(com.vandorlabs.VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_TOKEN_SLOT,p.world,at.getX(),at.getY(),at.getZ());});stage=15;ticks=0;return false;}
        if(stage==15){if(ticks<20||!(mc.currentScreen instanceof GuiNavigationTable))return false;GuiNavigationTable gui=(GuiNavigationTable)mc.currentScreen;@SuppressWarnings("unchecked") java.util.List<net.minecraft.client.gui.GuiButton> buttons=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(net.minecraft.client.gui.GuiScreen.class,gui,"buttonList","field_146292_n");for(net.minecraft.client.gui.GuiButton button:buttons)if(button.displayString.equals("Redstone")){gui.mouseClicked(button.x+4,button.y+4,0);break;}stage=16;ticks=0;return false;}
        if(stage==16){if(ticks<15)return false;GuiNavigationTable gui=(GuiNavigationTable)mc.currentScreen;net.minecraft.client.gui.GuiTextField field=InlineTransmitterWidget.channelsField(gui);require(field!=null,"observer inline channel editor");field.setText("47,48");field.setFocused(true);InlineTransmitterWidget.keyInput(gui,' ',org.lwjgl.input.Keyboard.KEY_SPACE);InlineTransmitterWidget.keyInput(gui,'\r',org.lwjgl.input.Keyboard.KEY_RETURN);for(net.minecraft.client.gui.GuiButton control:InlineTransmitterWidget.controls(gui))if(control instanceof net.minecraftforge.fml.client.config.GuiSlider){InlineTransmitterWidget.mouseInput(gui,control.x+5,control.y+5,0);control.mouseReleased(0,0);}stage=17;ticks=0;return false;}
        if(stage==17){if(ticks<20)return false;server(mc,()->{EntityPlayerMP p=player(mc);TileEntityConstructor source=(TileEntityConstructor)p.world.getTileEntity(POS.west(4));require(source.transmittedChannels().equals(ChannelList.of(47,48))&&source.transmissionRange()==4,"observer range/channels actual settings packets");TileEntityNavigationTable t=table(p.world);t.removeStackFromSlot(0);TransmitterSlots.configure(source,ChannelList.of(47,48),256,false);
            p.world.setBlockToAir(source.observerPowerPort().down());
            t.setRedstoneChannels(ChannelList.of(47));aim(p,new Vec3d(POS).addVector(1,4,-1.5),new Vec3d(POS).addVector(1,.875,1));});mc.player.closeScreen();mc.gameSettings.hideGUI=true;NavigationMapRenderer.changed(POS);stage=18;ticks=0;return false;}
        if(stage==18){NavigationMapRenderer.bind(table(mc.world),0,1);if(ticks<200||!hasPowerHaiku(POS))return false;System.out.println("[vandorlabs] navigation-table power-haiku PASS");shot="navigation-world-no-signal.png";captured=false;stage=19;ticks=0;return false;}
        if(stage==19&&captured){if(!flightComplete){if(!NavigationFlightChecks.tick(mc,output))return false;flightComplete=true;}if(!NavigationModuleLiveChecks.tick(mc,output))return false;System.out.println("[vandorlabs] navigation-world PASS rendered seed map families=6 joined external mount actual picker and observer range/channel packets real pan/write clicks Survival lever placement activation bounded wire no-signal");return true;}
        return false;
    }
    static BlockPos power(WorldServer world,TileEntityConstructor observer){
        // Observers accept RF only through the centre Power Port's underside.
        BlockPos port=observer.observerPowerPort().down();
        powerPort(world,port);
        return port;
    }
    static void powerPort(WorldServer world,BlockPos port){
        Block block=Block.getBlockFromName("vandorlabs:power_port");
        world.setBlockState(port,block.getDefaultState(),3);
        com.vandorlabs.ports.PowerPortTile feed=(com.vandorlabs.ports.PowerPortTile)world.getTileEntity(port);
        feed.place(EnumFacing.DOWN);
        DeferredTileLoad.flush(world);
        require(feed.getCapability(net.minecraftforge.energy.CapabilityEnergy.ENERGY,EnumFacing.DOWN)
                .receiveEnergy(1000000,false)==1000000,"power supply accepts RF");
    }
    private static boolean hasPowerHaiku(BlockPos pos)throws Exception{
        java.lang.reflect.Field images=NavigationMapRenderer.class.getDeclaredField("IMAGES");
        images.setAccessible(true);
        Object entry=((Map<?,?>)images.get(null)).get(pos);
        if(entry==null)return false;
        MessageNavigationImage image=(MessageNavigationImage)field(entry,"data");
        if(image==null||!image.noPower)return false;
        int[] pixels=((net.minecraft.client.renderer.texture.DynamicTexture)field(entry,"ui")).getTextureData();
        int glyphs=0;
        for(int pixel=384*64;pixel<pixels.length;pixel++)if((pixels[pixel]&0xffffff)==0xcce7ee)glyphs++;
        require(glyphs>100,"all three no-power haiku lines rendered");
        return true;
    }
    private static Object field(Object value,String name)throws Exception{Class<?> type=value.getClass();while(type!=null){try{java.lang.reflect.Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(value);}catch(NoSuchFieldException absent){type=type.getSuperclass();}}throw new NoSuchFieldException(name);}
    private static void aim(EntityPlayerMP p,Vec3d eye,Vec3d target){Vec3d d=target.subtract(eye);p.connection.setPlayerLocation(eye.x,eye.y-p.getEyeHeight(),eye.z,(float)Math.toDegrees(Math.atan2(-d.x,d.z)),(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));p.motionX=p.motionY=p.motionZ=0;}
    private static void support(WorldServer w,EntityPlayerMP p){
        BlockPos root=BUILDER;for(String name:new String[]{"synthesis_tower","assembly_press","enclosed_gantry"}){TileEntityConstructor tile=build(w,root,name);for(BlockPos at:new BlockPos[]{root,root.add(1,1,0)})for(EnumFacing side:EnumFacing.HORIZONTALS){require(w.getBlockState(at).isSideSolid(w,at,side),"assembler attachment support "+name+side);require(Blocks.LEVER.canPlaceBlockOnSide(w,at.offset(side),side),"vanilla lever placement "+name+side);Block lever=Block.getBlockFromName("vandorlabs:industrial_power_lever");require(lever.canPlaceBlockOnSide(w,at.offset(side),side),"industrial lever placement "+name+side);}tile.dismantle();for(BlockPos at:BlockPos.getAllInBox(root,root.add(6,5,4)))w.setBlockToAir(at);}
        System.out.println("[vandorlabs] assembler-wall-support PASS builders=3 vanilla and industrial levers");
    }
    private static void generatorError(WorldServer world){
        java.lang.reflect.Field f=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(net.minecraft.world.WorldProvider.class,"biomeProvider","field_76578_c");
        try{Object original=f.get(world.provider);try{f.set(world.provider,new net.minecraft.world.biome.BiomeProvider(){@Override public net.minecraft.world.biome.Biome[] getBiomes(net.minecraft.world.biome.Biome[] a,int x,int z,int w,int h,boolean cache){throw new IllegalStateException("injected navigation sampling failure");}});require(NavigationService.color(world,-29999000,29999000)==NavigationMap.ERROR,"generator failure colored distinctly");}finally{f.set(world.provider,original);}require(NavigationService.color(world,-29999000,29999000)!=NavigationMap.ERROR,"transient generator error retries");}catch(IllegalAccessException e){throw new IllegalStateException(e);}
    }
    private static void artworkPreservation(WorldServer world){
        BlockPos at=POS.north(4);Block block=Block.getBlockFromName("vandorlabs:navigation_table_flat");world.setBlockState(at.east(),block.getDefaultState(),2);TileEntityNavigationTable old=(TileEntityNavigationTable)world.getTileEntity(at.east());NBTTagCompound saved=old.writeToNBT(new NBTTagCompound());saved.setInteger("NavigationTexture",-1);saved.removeTag("NavigationExplicit");old.readFromNBT(saved);NavigationAssemblies.rebuild(world,at.east());world.setBlockState(at,block.getDefaultState(),2);NavigationAssemblies.rebuild(world,at);require(((TileEntityNavigationTable)world.getTileEntity(at)).reader().screenSettings().texture==-1,"joining fresh module preserves previous-release black artwork");world.setBlockToAir(at);world.setBlockToAir(at.east());
    }
    private static boolean surfaceReady(Minecraft mc)throws Exception{
        return mc.getIntegratedServer().callFromMainThread(()->{
            TileEntityNavigationTable tile=table(player(mc).world);
            NavigationService.View view=NavigationService.view(tile);
            java.lang.reflect.Method capture=NavigationService.class.getDeclaredMethod("captureSurface",
                    TileEntityNavigationTable.class,int.class,int.class);
            capture.setAccessible(true);
            return capture.invoke(null,tile,view.x,view.z)!=null;
        }).get();
    }
    private static void surface(EntityPlayerMP player){
        WorldServer world=player.getServerWorld();
        TileEntityNavigationTable table=table(world);
        NavigationSurfaceData saved=(NavigationSurfaceData)world.getPerWorldStorage()
                .getOrLoadData(NavigationSurfaceData.class,NavigationSurface.STORAGE);
        require(saved!=null,"live table schedules scans");
        try{
            NavigationSurface.Atlas atlas=(NavigationSurface.Atlas)field(saved,"atlas");
            require(atlas.tileCount()>0,"scanner records loaded columns");
            java.lang.reflect.Method sample=NavigationSurfaceData.class.getDeclaredMethod("sample",WorldServer.class,int.class,int.class);
            sample.setAccessible(true);
            int chunks=world.getChunkProvider().getLoadedChunkCount();
            BlockPos column=table.getPos().add(0,96,0);
            net.minecraft.world.chunk.Chunk chunk=world.getChunkProvider().getLoadedChunk(column.getX()>>4,column.getZ()>>4);
            require(chunk!=null,"fixture table column already loaded");
            int height=chunk.getHeightValue(column.getX()&15,column.getZ()&15);
            require(column.getY()>height,"fixture gold is above the existing surface at "+height);
            IBlockState original=world.getBlockState(column);
            try{
                require(world.setBlockState(column,Blocks.GOLD_BLOCK.getDefaultState(),3),"fixture places the gold surface");
                int color=(int)sample.invoke(saved,world,column.getX(),column.getZ());
                require(color/4==net.minecraft.block.material.MapColor.GOLD.colorIndex,
                        "scan uses the placed gold surface, packed="+color+" height="+chunk.getHeightValue(column.getX()&15,column.getZ()&15)
                                +" state="+chunk.getBlockState(column));
            }finally{world.setBlockState(column,original,3);}
            require((int)sample.invoke(saved,world,25000000,25000000)==0&&world.getChunkProvider().getLoadedChunkCount()==chunks,
                    "scan skips absent chunks without loading one");
            NavigationSurfaceData restored=new NavigationSurfaceData(NavigationSurface.STORAGE);
            restored.readFromNBT(saved.writeToNBT(new NBTTagCompound()));
            require(((NavigationSurface.Atlas)field(restored,"atlas")).tileCount()==atlas.tileCount(),"world atlas saved-data round trip");
            ItemStack chip=table.getStackInSlot(0);
            require(chip.hasTagCompound()&&chip.getTagCompound().getCompoundTag("NavigationSurface").getByteArray("Colors").length>=16384,
                    "SET writes a portable surface snapshot");
            System.out.println("[vandorlabs] navigation-surface PASS loaded column, no chunk load, saved atlas and SET snapshot tiles="+atlas.tileCount());
        }catch(Exception failure){throw new IllegalStateException(failure);}
    }
    private static void observeRefinement()throws Exception{
        java.lang.reflect.Field images=NavigationMapRenderer.class.getDeclaredField("IMAGES");
        images.setAccessible(true);
        Object entry=((Map<?,?>)images.get(null)).get(POS);
        if(entry==null)return;
        MessageNavigationImage image=(MessageNavigationImage)field(entry,"data");
        if(image==null||image.pixels==null||image.noSignal)return;
        NavigationCanvas canvas=new NavigationCanvas(image.canvasWidth,image.canvasHeight,true);
        if(image.zoom>1||!canvas.canRefine(image.zoom))return;
        if(image.width==canvas.gridWidth(image.zoom)&&image.height==canvas.gridHeight(image.zoom)){
            if(image!=preview){preview=image;previewAt=Minecraft.getSystemTime();}
        }else if(preview!=null&&preview.x==image.x&&preview.z==image.z&&preview.zoom==image.zoom
                &&preview.canvasWidth==image.canvasWidth&&preview.canvasHeight==image.canvasHeight){
            require(image.revision>preview.revision&&image.width==canvas.gridWidth(image.zoom,true)
                    &&image.height==canvas.gridHeight(image.zoom,true),"preview upgrades in place at twice the raster density");
            refinementSeen=true;
            System.out.println("[vandorlabs] navigation-refinement PASS "+preview.width+"x"+preview.height+" -> "
                    +image.width+"x"+image.height+" zoom="+NavigationOverlay.zoomLabel(image.zoom)+" preview-to-detail-ms="+(Minecraft.getSystemTime()-previewAt));
            preview=null;
        }
    }
    private static void radar(EntityPlayerMP viewer,WorldServer world,TileEntityNavigationTable table,TileEntityConstructor dish,TileEntityConstructor array){
        net.minecraftforge.common.util.FakePlayer target=new net.minecraftforge.common.util.FakePlayer(world,new com.mojang.authlib.GameProfile(new UUID(0,211256),"RadarContact"));
        MessageNavigationImage image=new MessageNavigationImage(table.getPos(),0,1,dish.getPos().getX(),dish.getPos().getZ(),4,16,false,ItemStack.EMPTY,true,world.provider.getDimension(),null);
        world.playerEntities.add(target);
        dish.refreshInputs();
        array.refreshInputs();
        table.setRedstoneChannels(ChannelList.of(42));
        try{
            java.lang.reflect.Method snapshot=NavigationService.class.getDeclaredMethod("overlay",EntityPlayerMP.class,TileEntity.class,MessageNavigationImage.class);
            snapshot.setAccessible(true);
            int x=dish.getPos().getX(),z=dish.getPos().getZ(),sea=world.getSeaLevel();
            target.setPosition(x+256,sea+1,z);
            NavigationOverlay data=(NavigationOverlay)snapshot.invoke(null,viewer,table,image);
            require(data.radars.length==3&&data.radars[2]==256&&data.players.length==2&&data.players[0]==x+256,"active dish detects surface contact at exact radar radius");
            target.setPosition(x+257,sea+1,z);
            require(((NavigationOverlay)snapshot.invoke(null,viewer,table,image)).players.length==0,"contact beyond 256 blocks excluded");
            target.setPosition(x+1,sea,z);
            require(((NavigationOverlay)snapshot.invoke(null,viewer,table,image)).players.length==0,"sea-level contact excluded");
            target.setPosition(x+1,sea+1,z);
            target.setSneaking(true);
            require(((NavigationOverlay)snapshot.invoke(null,viewer,table,image)).players.length==0,"crouching contact excluded");
            target.setSneaking(false);
            dish.cycleActivity();
            data=(NavigationOverlay)snapshot.invoke(null,viewer,table,image);
            require(data.radars.length==0&&data.players.length==0&&data.circles.length==3,"off observer retains map coverage and clears live radar");
            table.setRedstoneChannels(ChannelList.of(42,43));
            x=array.getPos().getX();
            z=array.getPos().getZ();
            target.setPosition(x-128,sea+1,z);
            data=(NavigationOverlay)snapshot.invoke(null,viewer,table,image);
            require(data.circles.length==6&&data.circles[2]*data.circles[5]<0&&data.radars.length==3&&data.radars[0]==x
                    &&data.radars[1]==z&&data.radars[2]==128,"both observers show terrain coverage, only the active array has radar");
            require(data.players.length==2&&data.players[0]==x-128,"array detects contact at exact radar radius");
            target.setPosition(x-129,sea+1,z);
            require(((NavigationOverlay)snapshot.invoke(null,viewer,table,image)).players.length==0,"contact beyond 128 blocks excluded");
            while(dish.activity()!=2)dish.cycleActivity();
            data=(NavigationOverlay)snapshot.invoke(null,viewer,table,image);
            require(data.circles.length==6&&data.radars.length==6
                    &&((data.radars[2]==128&&data.radars[5]==256)||(data.radars[2]==256&&data.radars[5]==128)),
                    "linked dish and array each show their own coverage and radar");
        }catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
        finally{
            while(dish.activity()!=2)dish.cycleActivity();
            table.setRedstoneChannels(ChannelList.of(42,43));
            world.playerEntities.remove(target);
        }
        System.out.println("[vandorlabs] navigation-radar PASS mixed-observers active/off 128/256-boundaries sea-level crouching snapshot");
    }
    private static void wire(ItemStack chip){
        MessageNavigationImage m=new MessageNavigationImage(POS,0,123,-20,30,4,16,false,chip,true,0,new int[NavigationMap.SIZE*NavigationMap.SIZE]).linkState(false,true);ByteBuf a=Unpooled.buffer(),b=Unpooled.buffer();try{m.toBytes(a);int size=a.readableBytes();MessageNavigationImage copy=new MessageNavigationImage();copy.fromBytes(a.copy());copy.toBytes(b);require(a.equals(b)&&copy.noPower&&!copy.unlinked,"raster and no-power link state round trip");java.util.Arrays.fill(m.pixels,NavigationMap.ERROR);m.hasChip=false;m.marker=false;require(NavigationMapRenderer.image(m,"").getRGB(1,80)==NavigationMap.ERROR,"error pixels rendered");java.util.Arrays.fill(m.pixels,NavigationMap.OUTSIDE);require(NavigationMapRenderer.image(m,"").getRGB(1,80)==NavigationMap.OUTSIDE,"uncovered pixels rendered differently");require(size==MessageNavigationImage.HEADER+2+4+96*96,"bounded palette image bytes");a.writeByte(0);try{new MessageNavigationImage().fromBytes(a);throw new IllegalStateException("oversize accepted");}catch(IllegalArgumentException expected){}System.out.println("[vandorlabs] navigation-wire PASS request=18 image="+size+" bytes round-trip bounded");}finally{a.release();b.release();}
    }
    static void render(Minecraft mc){if(shot==null)return;try{javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,shot));shot=null;captured=true;}catch(Exception failure){throw new IllegalStateException(failure);}}
}
