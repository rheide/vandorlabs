package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.network.*;
import com.vandorlabs.rack.*;
import com.vandorlabs.redstone.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameType;
import java.io.File;
import java.lang.reflect.Field;
import javax.imageio.ImageIO;

/** One player module journey plus physical old-block and unchanged old-snapshot compatibility. */
@net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
final class ModuleRackAutopilotLiveChecks {
    private static int stage,wait,checks;
    private static boolean done;
    private static BlockPos at(){return ModuleRackLiveChecks.placedRackPosition();}
    private static EntityPlayerMP actor(Minecraft mc){return ModuleRackLiveChecks.player(mc);}
    private static TileEntityModuleRack rack(net.minecraft.world.World world){return world.getTileEntity(at()) instanceof TileEntityModuleRack?(TileEntityModuleRack)world.getTileEntity(at()):null;}
    private static void need(boolean value,String reason){checks++;if(!value)throw new IllegalStateException("Rack Autopilot stage="+stage+" age="+wait+": "+reason);}
    private static void next(int value){stage=value;wait=0;}
    private static void held(Minecraft mc,ItemStack stack)throws Exception{
        ModuleRackLiveChecks.server(mc,()->{EntityPlayerMP player=actor(mc);player.inventory.currentItem=0;player.setHeldItem(EnumHand.MAIN_HAND,stack);player.setHeldItem(EnumHand.OFF_HAND,ItemStack.EMPTY);player.inventoryContainer.detectAndSendChanges();});
    }
    private static void use(Minecraft mc,int slot){
        double u=slot<0?.5:7/16D,v=slot<0?.98:ModuleRackGeometry.slotCenterY(slot)/16;
        need(mc.playerController.processRightClickBlock(mc.player,mc.world,at(),EnumFacing.SOUTH,new Vec3d(at().getX()+u,at().getY()+v,at().getZ()+1),EnumHand.MAIN_HAND)==EnumActionResult.SUCCESS,"ordinary consuming Rack player packet");
    }
    private static void click(GuiModuleRack gui,int id,int mouse)throws Exception{
        GuiButton button=gui==null?null:gui.control(id);need(button!=null&&button.enabled,"Rack control "+id);
        gui.mouseClicked(button.x+button.width/2,button.y+10,mouse);
    }
    private static GuiTextField channels(GuiModuleRack gui)throws Exception{Field field=GuiModuleRack.class.getDeclaredField("channels");field.setAccessible(true);return (GuiTextField)field.get(gui);}
    private static void shot(Minecraft mc,File directory,String name)throws Exception{
        ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(directory,"module_rack_autopilot_"+name+".png"));
    }
    static boolean tick(Minecraft mc,File directory)throws Exception{
        if(done)return true;
        TileEntityModuleRack current=rack(mc.world);GuiModuleRack gui=mc.currentScreen instanceof GuiModuleRack?(GuiModuleRack)mc.currentScreen:null;
        if(++wait%80==0)System.out.println("[vandorlabs][reprolab] Rack Autopilot waiting stage="+stage+" age="+wait+" mode="+mc.playerController.getCurrentGameType()+" held="+mc.player.getHeldItemMainhand()+" position="+mc.player.getPositionVector()+" screen="+mc.currentScreen);
        need(wait<240,"bounded mode="+mc.playerController.getCurrentGameType()+" held="+mc.player.getHeldItemMainhand()+" position="+mc.player.getPositionVector()+" screen="+mc.currentScreen);
        mc.gameSettings.pauseOnLostFocus=false;mc.gameSettings.hideGUI=false;mc.ingameGUI.getChatGUI().clearChatMessages(true);
        if(stage==0){
            if(current==null||mc.playerController.getCurrentGameType()!=GameType.SURVIVAL)return false;
            held(mc,new ItemStack(ModItems.AUTOPILOT_MODULE,2));next(1);
        }else if(stage==1){
            if(mc.player.getHeldItemMainhand().getItem()!=ModItems.AUTOPILOT_MODULE||mc.player.getHeldItemMainhand().getCount()!=2)return false;
            use(mc,0);next(2);
        }else if(stage==2){
            if(current==null||current.moduleKind(0)!=2||mc.player.getHeldItemMainhand().getCount()!=1||ModuleRackAnimation.activeCount()!=0)return false;
            net.minecraft.block.state.IBlockState state=mc.world.getBlockState(at());net.minecraft.client.renderer.block.model.IBakedModel model=mc.getBlockRendererDispatcher().getModelForState(state);
            java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> quads=model.getQuads(state.getBlock().getExtendedState(state,mc.world,at()),null,0);
            need(quads.stream().filter(q->q.getSprite().getIconName().equals("vandorlabs:blocks/module_rack/indicators")).count()>=53,"actual seated abstract route indicator, distinct from blank cassette");
            held(mc,ItemStack.EMPTY);next(3);
        }else if(stage==3){if(!mc.player.getHeldItemMainhand().isEmpty())return false;use(mc,-1);next(4);
        }else if(stage==4){if(gui==null)return false;click(gui,102,0);click(gui,201,0);click(gui,2,1);next(5);
        }else if(stage==5){
            if(current.moduleSettings(0).activation!=0)return false;
            GuiTextField field=channels(gui);need(field!=null,"standard receive channels field");field.setText("73, 74");click(gui,2,0);next(6);
        }else if(stage==6){
            if(current.moduleSettings(0).activation!=1||!current.moduleSettings(0).channels.equals(ChannelList.of(73,74)))return false;
            ModuleRackLiveChecks.server(mc,()->{
                EntityPlayerMP player=actor(mc);TileEntityModuleRack host=rack(player.world);long sequence=host.getInsertionSequence(0);
                AutopilotModuleSettings wrong=new AutopilotModuleSettings(ChannelList.of(91),2,"minecraft:concrete",3);
                need(!MessageModuleAutopilot.apply(player,new MessageModuleAutopilot(player.openContainer.windowId,at(),0,sequence+1,wrong)),"stale installed module sequence refused");
                boolean edit=player.capabilities.allowEdit;player.capabilities.allowEdit=false;
                try{need(!MessageModuleAutopilot.apply(player,new MessageModuleAutopilot(player.openContainer.windowId,at(),0,sequence,wrong)),"module permission refusal");}finally{player.capabilities.allowEdit=edit;}
            });
            PacketHandler.INSTANCE.sendToServer(new MessageModuleAutopilot(gui.inventorySlots.windowId+1,at(),0,current.getInsertionSequence(0),new AutopilotModuleSettings(ChannelList.of(91),2,"minecraft:concrete",3)));
            shot(mc,directory,"receiving");next(7);
        }else if(stage==7){
            if(wait<8)return false;need(current.moduleSettings(0).activation==1&&current.moduleSettings(0).channels.equals(ChannelList.of(73,74)),"real stale-window C2S refused");
            click(gui,200,0);
            ModuleRackLiveChecks.server(mc,()->{EntityPlayerMP player=actor(mc);player.inventory.setInventorySlotContents(1,new ItemStack(net.minecraft.init.Blocks.WOOL,3,14));player.inventoryContainer.detectAndSendChanges();});next(70);
        }else if(stage==70){
            ItemStack sample=mc.player.inventory.getStackInSlot(1);if(wait<3||sample.getCount()!=3||sample.getMetadata()!=14)return false;
            Field field=GuiModuleRack.class.getDeclaredField("routePicker");field.setAccessible(true);InventorySamplePicker picker=(InventorySamplePicker)field.get(gui);
            need(picker!=null,"existing non-consuming marker picker");gui.mouseClicked(picker.x+18+8,picker.y+16+8,0);next(71);
        }else if(stage==71){
            if(wait<3||!current.moduleSettings(0).marker.equals("minecraft:wool")||current.moduleSettings(0).markerMetadata!=14)return false;
            need(mc.player.inventory.getStackInSlot(1).getCount()==3,"marker picker preserves sampled items and color metadata");
            shot(mc,directory,"general");click(gui,1,0);held(mc,new ItemStack(ModItems.AUTOPILOT_MODULE));next(8);
        }else if(stage==8){if(mc.player.getHeldItemMainhand().getItem()!=ModItems.AUTOPILOT_MODULE)return false;use(mc,2);next(9);
        }else if(stage==9){
            if(current.autopilotCount()!=2||!mc.player.getHeldItemMainhand().isEmpty())return false;
            ModuleRackLiveChecks.server(mc,()->{
                EntityPlayerMP player=actor(mc);TileEntityModuleRack host=rack(player.world);
                need(host.configureModule(2,host.getInsertionSequence(2),new AutopilotModuleSettings(ChannelList.of(74),1,"minecraft:concrete",4)),"second module has independent existing receiver settings");
                BlockPos source=at().west(3);net.minecraft.block.Block block=net.minecraft.block.Block.getBlockFromName("vandorlabs:module_rack");player.world.setBlockState(source,block.getDefaultState(),3);
                TileEntityModuleRack sender=(TileEntityModuleRack)player.world.getTileEntity(source);sender.insertModule(0,new ItemStack(ModItems.BLANK_MODULE),false);
                sender.setEmitsRedstone(true);TransmitterSlots.configure(sender.output,ChannelList.of(73,74),32,false);DeferredTileLoad.flush(player.world);
                need(ModuleAutopilotData.state(host.getStackInSlot(0)).getBoolean("AutopilotEnabled")&&ModuleAutopilotData.state(host.getStackInSlot(2)).getBoolean("AutopilotEnabled"),"real channel bank forwards receiving power to every inserted module");
                need(host.autopilotCount()==2&&!host.enabled()&&ModuleAutopilots.count(host)==2,"multiple navigation modules retain their items and refuse silent controller selection");
            });use(mc,2);next(10);
        }else if(stage==10){
            if(current.autopilotCount()!=1||mc.player.getHeldItemMainhand().isEmpty())return false;
            need(mc.player.getHeldItemMainhand().getItem()==ModItems.AUTOPILOT_MODULE,"ordinary extraction returns the one physical module");
            ModuleRackLiveChecks.server(mc,()->{physicalLegacy(mc);navigation(actor(mc).getServerWorld());});next(11);
        }else if(stage==11){
            ModuleRackLiveChecks.server(mc,()->{
                EntityPlayerMP player=actor(mc);TileEntityModuleRack host=rack(player.world);NBTTagCompound before=host.writeToNBT(new NBTTagCompound());
                net.minecraft.tileentity.TileEntity restored=com.vandorlabs.copier.PhysicalTileFactory.create(player.getServerWorld(),player.world.getBlockState(at()),before);
                need(restored instanceof TileEntityModuleRack&&((TileEntityModuleRack)restored).moduleSettings(0).channels.equals(ChannelList.of(73,74)),"physical module settings survive native reload factory");
                need(!host.getUpdateTag().hasKey("Items")&&!host.getUpdateTag().hasKey(LegacyAutopilotMigration.BACKUP),"public Rack data excludes owned items and legacy backup");
            });
            if("write".equals(System.getProperty("vandorlabs.moduleRackMigrationPhase","")))ModuleRackMigrationLiveChecks.write(mc,directory);
            done=true;
            System.out.println("[vandorlabs][reprolab] module-rack PASS autopilot checks="+checks+" SurvivalInsert/routeIcon/perModuleTabs/receiverForwarding/previous/C2S/stale/permissions/extract/oldPhysicalMigration/oldSnapshot/reload");return true;
        }
        return false;
    }
    private static void navigation(net.minecraft.world.WorldServer world) {
        BlockPos base=at().south(12);for(int x=0;x<=3;x++)world.setBlockState(base.east(x).down(),net.minecraft.init.Blocks.CONCRETE.getDefaultState(),2);
        TileEntityModuleRack rack=new TileEntityModuleRack();rack.setPos(BlockPos.ORIGIN);rack.insertModule(0,new ItemStack(ModItems.AUTOPILOT_MODULE),false);
        TileEntityPilotSeat seat=new TileEntityPilotSeat();seat.setPos(BlockPos.ORIGIN.up());
        net.minecraft.block.Block block=net.minecraft.block.Block.getBlockFromName("vandorlabs:module_rack");
        java.util.List<VehicleStructure.Cell> cells=java.util.Arrays.asList(
                new VehicleStructure.Cell(BlockPos.ORIGIN,block.getDefaultState().withProperty(BlockModuleRack.FACING,EnumFacing.UP),rack.writeToNBT(new NBTTagCompound())),
                new VehicleStructure.Cell(BlockPos.ORIGIN.up(),ModBlocks.PILOT_SEAT.getDefaultState().withProperty(BlockPilotSeat.FACING,EnumFacing.EAST),seat.writeToNBT(new NBTTagCompound())));
        EntityGroundVehicle craft=new EntityGroundVehicle(world);craft.install(new VehicleStructure(cells,BlockPos.ORIGIN.up(),EnumFacing.EAST,1));craft.setPosition(base.getX(),base.getY(),base.getZ());
        TileEntityAutopilot controller=craft.autopilot.controller();need(controller instanceof TileEntityModuleRack,"vehicle selects actual installed module host with vertical front");
        controller.setChannelSignal(true);controller.setChannelSignal(false);
        try {
            for(int tick=0;tick<12;tick++)craft.autopilot.tick();
            need(craft.posX>base.getX()+.1&&controller.enabled(),"module follows pilot-seat heading, never old Autopilot block FACING");
            craft.signals.persist();NBTTagCompound saved=craft.structure.write(),journey=craft.autopilot.write();
            EntityGroundVehicle restored=new EntityGroundVehicle(world);restored.install(VehicleStructure.read(saved));restored.setPosition(craft.posX,craft.posY,craft.posZ);restored.rotationYaw=craft.rotationYaw;restored.autopilot.read(journey);craft.setDead();craft=restored;controller=craft.autopilot.controller();
            for(int tick=0;tick<100&&controller.enabled();tick++)craft.autopilot.tick();
            need(!controller.enabled()&&controller.status.equals("End of route")&&Math.abs(craft.posX-base.getX()-3)<.025,"typed module route and runtime survive saved vehicle reload and end at exact marker");
        }finally{craft.setDead();}
    }
    private static void physicalLegacy(Minecraft mc){
        EntityPlayerMP player=actor(mc);net.minecraft.world.WorldServer world=player.getServerWorld();BlockPos legacy=at().east(3);
        world.setBlockState(legacy,ModBlocks.AUTOPILOT.getDefaultState().withProperty(BlockVandorDirectional.FACING,EnumFacing.WEST),3);
        TileEntityAutopilot old=(TileEntityAutopilot)world.getTileEntity(legacy);NBTTagCompound raw=old.writeToNBT(new NBTTagCompound());
        raw.setString("ForeignNavigationData","untouched old computer");NBTTagCompound caps=new NBTTagCompound();caps.setString("OpaqueState","minecraft:air retained exactly");raw.setTag("ForgeCaps",caps);
        raw.setLong("AutopilotDepartureAt",37);raw.setInteger("AutopilotTrigger",0);old.readFromNBT(raw);WorldActionAuthorization.authorize(old,player);
        NBTTagCompound before=old.writeToNBT(new NBTTagCompound());
        TileEntityModuleRack converted=LegacyAutopilotMigration.convert(old);
        need(converted!=null&&world.getTileEntity(legacy)==converted&&world.getBlockState(legacy).getValue(BlockModuleRack.FACING)==EnumFacing.WEST,"actual legacy block becomes one same-position Rack with its own facing property");
        NBTTagCompound saved=converted.writeToNBT(new NBTTagCompound());
        need(converted.autopilotCount()==1&&converted.departureAt==37&&saved.getCompoundTag("ForgeCaps").equals(caps)
                &&saved.getString("ForeignNavigationData").equals("untouched old computer"),"legacy capabilities/navigation/unknown fields remain exact on physical host");
        need(player.getUniqueID().equals(WorldActionAuthorization.actorId(converted)),"trusted existing automation authorization rebinds to physical Rack identity");
        NBTTagCompound relative=VehicleTileData.translated(before,BlockPos.ORIGIN.subtract(legacy));
        VehicleStructure previous=VehicleStructure.display(java.util.Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,ModBlocks.AUTOPILOT.getDefaultState(),relative)));
        VehicleWorld view=new VehicleWorld(world,previous);net.minecraft.tileentity.TileEntity snapshot=view.getTileEntity(BlockPos.ORIGIN);
        need(snapshot.getClass()==TileEntityAutopilot.class&&previous.at(BlockPos.ORIGIN).tileData().equals(relative),"previous saved vehicle and pending preimage retain their old tile/block IDs");
        need(snapshot.writeToNBT(new NBTTagCompound()).equals(relative),"old snapshot round trip preserves opaque capability and foreign own fields");
        world.setBlockState(legacy.up(),ModBlocks.WAYPOINT.getDefaultState(),3);TileEntityWaypoint waypoint=(TileEntityWaypoint)world.getTileEntity(legacy.up());
        need(LegacyAutopilotMigration.convert(waypoint)==null&&world.getTileEntity(legacy.up())==waypoint,"Waypoint remains its existing block and behavior");
    }
}
