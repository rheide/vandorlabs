package com.vandorlabs.client;

import com.vandorlabs.tiles.*;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.programmer.*;
import com.vandorlabs.navigation.*;
import com.vandorlabs.appliance.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.*;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.storage.MapData;
import java.io.File;
import java.util.*;

/** Real Survival attachment/frame/button/menu packets, then document and inventory failure paths. */
final class ChipApplianceLiveChecks {
    private static final BlockPos AT=new BlockPos(40,90,40);private static int stage,ticks,checks,mapId;private static ItemStack imported;
    private static void need(boolean ok,String why){checks++;if(!ok)throw new IllegalStateException("chip appliance stage="+stage+": "+why);}
    private static EntityPlayerMP actor(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayers().get(0);}
    private static void server(Minecraft mc,Runnable action)throws Exception{mc.getIntegratedServer().addScheduledTask(action).get();}
    private static TileEntityChipAppliance tile(World w){return (TileEntityChipAppliance)w.getTileEntity(AT);}
    private static void held(EntityPlayerMP p,ItemStack stack,boolean top){p.inventory.currentItem=0;p.setHeldItem(EnumHand.MAIN_HAND,stack);p.connection.sendPacket(new net.minecraft.network.play.server.SPacketHeldItemChange(0));p.inventoryContainer.detectAndSendChanges();p.connection.setPlayerLocation(AT.getX()+.5,AT.getY()+(top?2:0),AT.getZ()+(top?.5:-2),0,top?90:0);}
    private static void click(Minecraft mc,EnumFacing side,double x,double y,double z){mc.playerController.processRightClickBlock(mc.player,mc.world,AT,side,new Vec3d(AT).addVector(x,y,z),EnumHand.MAIN_HAND);}
    static boolean tick(Minecraft mc,File output)throws Exception{
        if(++ticks>600)throw new IllegalStateException("chip appliance timeout stage="+stage+" mode="+mc.playerController.getCurrentGameType()+" held="+mc.player.getHeldItemMainhand());
        if(stage==0){server(mc,()->{EntityPlayerMP p=actor(mc);need(tile(p.world).scanner(),"scanner remains after real Survival placement");p.inventory.clear();held(p,new ItemStack(ModItems.DATA_CHIP,1,3),false);});stage=1;ticks=0;return false;}
        if(stage==1){if(ticks<20||mc.playerController.getCurrentGameType()!=GameType.SURVIVAL||mc.player.inventory.currentItem!=0||mc.player.getHeldItemMainhand().getItem()!=ModItems.DATA_CHIP)return false;click(mc,EnumFacing.NORTH,.75,.75,0);stage=2;ticks=0;return false;}
        if(stage==2){if(ticks<20)return false;server(mc,()->{EntityPlayerMP p=actor(mc);need(tile(p.world).getStackInSlot(0).getItem()==ModItems.DATA_CHIP&&p.getHeldItemMainhand().isEmpty(),"actual mount packet retains one physical chip");ItemStack map=ItemMap.setupNewMap(p.world,120000,-120000,(byte)4,true,false);mapId=map.getMetadata();MapData data=(MapData)p.world.loadData(MapData.class,"map_"+mapId);Arrays.fill(data.colors,(byte)18);data.colors[0]=0;data.markDirty();held(p,map,true);});stage=3;ticks=0;return false;}
        if(stage==3){if(ticks<20||mc.player.getHeldItemMainhand().getItem()!=Items.FILLED_MAP||Math.abs(mc.player.posY-(AT.getY()+2))>.1)return false;click(mc,EnumFacing.UP,.5,1,.5);stage=4;ticks=0;return false;}
        if(stage==4){if(ticks<20)return false;server(mc,()->{EntityPlayerMP p=actor(mc);need(tile(p.world).getStackInSlot(1).getMetadata()==mapId&&tile(p.world).getStackInSlot(1).getItem()==Items.FILLED_MAP&&p.getHeldItemMainhand().isEmpty(),"top frame owns exact original map after actual packet");held(p,ItemStack.EMPTY,false);});stage=5;ticks=0;return false;}
        if(stage==5){if(ticks<20||!mc.player.getHeldItemMainhand().isEmpty()||Math.abs(mc.player.posZ-(AT.getZ()-2))>.1)return false;click(mc,EnumFacing.NORTH,.25,.75,0);stage=6;ticks=0;return false;}
        if(stage==6){if(ticks<20)return false;server(mc,()->{EntityPlayerMP p=actor(mc);TileEntityChipAppliance tile=tile(p.world);imported=tile.getStackInSlot(0).copy();SavedMapPixels map=NavigationChip.savedMap(imported);need(map!=null&&map.scale()==4,"actual button programs map snapshot");need(map.contains(map.minX(),map.minZ())&&!map.contains(map.maxXExclusive(),map.minZ()),"full scale4 square and exact boundary");need(map.colorAt(map.minX(),map.minZ())==NavigationMap.OUTSIDE,"unexplored pixel remains blank");need(tile.getStackInSlot(1).getMetadata()==mapId,"scan retains original map");TileEntityChipAppliance copy=new TileEntityChipAppliance();copy.readFromNBT(tile.writeToNBT(new NBTTagCompound()));need(NavigationChip.savedMap(copy.getStackInSlot(0)).equals(map),"map snapshot survives save/reload");need(tile.getUpdateTag().getCompoundTag("InsertedToken").getCompoundTag("tag").getCompoundTag("NavigationMapPixels").getByteArray("Colors").length==0,"public update omits saved pixel payload");need(tile.writeToNBT(new NBTTagCompound()).getCompoundTag("InsertedToken").getCompoundTag("tag").getCompoundTag("NavigationMapPixels").getByteArray("Colors").length==16384,"disk retains full pixel payload");});need(tile(mc.world).mapPreview!=null,"client top frame receives original map image");MapData rendered=TEChipAppliance.mapData(tile(mc.world));need(rendered!=null,"top frame hydrates a stable vanilla map texture");Object maps=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(net.minecraft.client.gui.MapItemRenderer.class,mc.entityRenderer.getMapItemRenderer(),"loadedMaps","field_148252_c");Object instance=((Map<?,?>)maps).get(rendered.mapName);java.lang.reflect.Field tex=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(instance.getClass(),"mapTextureData","field_148241_e");tex.setAccessible(true);int colored=0;for(int pixel:(int[])tex.get(instance))if((pixel>>>24)==255)colored++;need(colored>16000,"top frame actually uploads original map pixels: "+colored);ApplianceVisualChecks.checkMountedMap(mc,output,tile(mc.world),ChipApplianceService.palette()[18]);click(mc,EnumFacing.NORTH,.5,.25,0);stage=7;ticks=0;return false;}
        if(stage==7){if(ticks<20||!(mc.currentScreen instanceof GuiChipAppliance))return false;need(mc.player.openContainer instanceof com.vandorlabs.container.ContainerChipAppliance,"standard appliance container via ordinary right-click");mc.playerController.sendEnchantPacket(mc.player.openContainer.windowId,0);
            GuiChipAppliance dialog=(GuiChipAppliance)mc.currentScreen;
            redstoneTabs(dialog);
            stage=8;ticks=0;return false;}
        if(stage==8){if(ticks<20)return false;mc.player.closeScreen();server(mc,()->{need(tile(actor(mc).world).activation()==ApplianceAutomation.ON,"Redstone dialog settings reach the installed Scanner");matrix(actor(mc));automation(actor(mc).getServerWorld());offline(actor(mc));held(actor(mc),ItemStack.EMPTY,false);});stage=81;ticks=0;return false;}
        if(stage==81){if(ticks<20||tile(mc.world)==null||tile(mc.world).scanner())return false;click(mc,EnumFacing.NORTH,.5,.25,0);stage=82;ticks=0;return false;}
        if(stage==82){if(ticks<10||!(mc.currentScreen instanceof GuiChipAppliance))return false;redstoneTabs((GuiChipAppliance)mc.currentScreen);mc.player.closeScreen();stage=83;ticks=0;return false;}
        if(stage==83){if(ticks<10)return false;server(mc,()->need(tile(actor(mc).world).activation()==ApplianceAutomation.ON,"mouse-click Redstone settings reach the installed Printer"));System.out.println("[vandorlabs] appliance-tab-clicks PASS Printer and Scanner actual mouse dispatch stable controls");stage=9;ticks=0;return false;}
        if(stage==9){if(ticks<30||!(mc.world.getTileEntity(AT.east(4)) instanceof TileEntityNavigationTable)||!(mc.world.getTileEntity(AT.east(6)) instanceof TileEntityAnimatedScreenSelector))return false;NavigationMapRenderer.bind(mc.world.getTileEntity(AT.east(4)),0,1);NavigationMapRenderer.bind(mc.world.getTileEntity(AT.east(6)),0,1);stage=10;ticks=0;return false;}
        if(stage==10){if(ticks<60||!NavigationMapRenderer.ready(AT.east(4))||!NavigationMapRenderer.ready(AT.east(6)))return false;server(mc,()->verifyDelivery(actor(mc).getServerWorld()));for(BlockPos at:new BlockPos[]{AT.east(4),AT.east(6)})snapshotRaster(at);System.out.println("[vandorlabs] chip-appliances PASS checks="+checks+" Survival mount/frame/button/menu packets maps/books/SGA/catalog costs failure paths offline-square save/client summary");return true;}
        return false;
    }
    private static void redstoneTabs(GuiChipAppliance dialog)throws Exception{
        List<net.minecraft.client.gui.GuiButton> buttons=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(
                net.minecraft.client.gui.GuiScreen.class,dialog,"buttonList","field_146292_n");
        List<net.minecraft.client.gui.GuiButton> original=new ArrayList<>(buttons);
        net.minecraft.client.gui.GuiButton inventory=findButton(buttons,1),redstone=findButton(buttons,2),trigger=findButton(buttons,3);
        for(int i=0;i<3;i++){
            CompleteRedstoneDialogAudit.clickTab(dialog,redstone);
            need(!redstone.enabled&&inventory.enabled&&trigger.visible&&buttons.equals(original),"one click opens Redstone with the same controls");
            CompleteRedstoneDialogAudit.clickTab(dialog,inventory);
            need(!inventory.enabled&&redstone.enabled&&!trigger.visible&&buttons.equals(original),"one click returns to Inventory with the same controls");
        }
        CompleteRedstoneDialogAudit.clickTab(dialog,redstone);
        CompleteRedstoneDialogAudit.clickTab(dialog,trigger);
        need(trigger.displayString.equals("Trigger: "+ApplianceAutomation.label(ApplianceAutomation.ON)),
                "trigger click cycles exactly once");
    }
    private static net.minecraft.client.gui.GuiButton findButton(List<net.minecraft.client.gui.GuiButton> buttons,int id){
        return buttons.stream().filter(button->button.id==id).findFirst().orElseThrow(IllegalStateException::new);
    }
    private static void offline(EntityPlayerMP p){
        WorldServer w=p.getServerWorld();BlockPos tablePos=AT.east(4),screenPos=AT.east(6);w.setBlockState(tablePos,Block.getBlockFromName("vandorlabs:navigation_table_flat").getDefaultState(),3);NavigationAssemblies.rebuild(w,tablePos);TileEntityNavigationTable table=(TileEntityNavigationTable)w.getTileEntity(tablePos);table.setInventorySlotContents(0,imported);
        w.setBlockState(screenPos,Block.getBlockFromName("vandorlabs:programmable_diagonal_half_screen").getDefaultState(),3);TileEntityAnimatedScreenSelector screen=(TileEntityAnimatedScreenSelector)w.getTileEntity(screenPos);screen.setSurfaceTexture(0,TileEntityAnimatedScreenSelector.NAVIGATION_SURFACE);screen.ownedChipReader().setInventorySlotContents(0,imported);SavedMapPixels map=NavigationChip.savedMap(imported);
        for(net.minecraft.tileentity.TileEntity display:new net.minecraft.tileentity.TileEntity[]{table,screen}){NavigationService.View view=NavigationService.view(display);view.x=map.minX()+8;view.z=map.minZ()+8;view.zoom=0;need(!NavigationSources.contains(NavigationService.coverage(display),view.x,view.z),"fixture lies beyond normal pin/source coverage");need(NavigationService.overrideMapSnapshot(display).contains(view.x,view.z),"saved map authorizes complete square");}
        p.connection.setPlayerLocation(AT.getX()+5.5,AT.getY()+1,AT.getZ()-2,0,20);
    }
    private static void snapshotRaster(BlockPos at)throws Exception{
        java.lang.reflect.Field images=NavigationMapRenderer.class.getDeclaredField("IMAGES");images.setAccessible(true);Object entry=((Map<?,?>)images.get(null)).get(at);java.lang.reflect.Field data=entry.getClass().getDeclaredField("data");data.setAccessible(true);com.vandorlabs.network.MessageNavigationImage image=(com.vandorlabs.network.MessageNavigationImage)data.get(entry);need(image!=null&&!image.noSignal,"offline display receives signal");int colored=0,blank=0;int expected=ChipApplianceService.palette()[18];java.lang.reflect.Field texture=entry.getClass().getDeclaredField("texture");texture.setAccessible(true);int[] pixels=((net.minecraft.client.renderer.texture.DynamicTexture)texture.get(entry)).getTextureData();for(int pixel:pixels){if(pixel==expected)colored++;if(pixel==NavigationMap.OUTSIDE)blank++;}need(colored>20&&blank>20,"actual offline screen/table raster uses original pixels and blanks beyond pin radius: "+colored+"/"+blank);
    }
    private static List<EntityItem> products(World w){List<EntityItem> items=w.getEntitiesWithinAABB(EntityItem.class,new AxisAlignedBB(AT).grow(3));items.removeIf(e->e.isDead);return items;}
    private static ItemStack product(World w){List<EntityItem> items=products(w);need(items.size()==1,"exactly one printed output: "+items.size());ItemStack item=items.get(0).getItem().copy();for(EntityItem entity:items)entity.setDead();return item;}
    private static void ink(TileEntityChipAppliance tile){tile.setInventorySlotContents(1,new ItemStack(Items.DYE,2,0));tile.setInventorySlotContents(2,new ItemStack(Items.PAPER,2));}
    private static void matrix(EntityPlayerMP p){WorldServer w=p.getServerWorld();TileEntityChipAppliance scanner=tile(w);scanner.removeStackFromSlot(1);ItemStack written=new ItemStack(Items.WRITTEN_BOOK);NBTTagCompound book=new NBTTagCompound();book.setString("title","Original journal");NBTTagList pages=new NBTTagList();pages.appendTag(new NBTTagString("{\"text\":\"A scanned journal page.\"}"));book.setTag("pages",pages);written.setTagCompound(book);scanner.setInventorySlotContents(1,written);need(scanner.getStackInSlot(1).getItem()==Items.WRITTEN_BOOK,"book inserted before scan: "+scanner.getStackInSlot(1)+" mode="+scanner.scanner());scanner.activate(p);need(ChipJournal.journal(scanner.getStackInSlot(0))&&ChipJournal.page(scanner.getStackInSlot(0),0).equals("A scanned journal page."),"written book plaintext import");need(scanner.getStackInSlot(1).getItem()==Items.WRITTEN_BOOK,"written book retained: "+scanner.getStackInSlot(1)+" scanner="+scanner.scanner()+" installed="+(w.getTileEntity(AT)==scanner));ItemStack journal=scanner.getStackInSlot(0).copy();scanner.setInventorySlotContents(1,new ItemStack(Items.ENCHANTED_BOOK));scanner.activate(p);need(ChipJournal.enchanting(scanner.getStackInSlot(0))&&!ChipJournal.page(scanner.getStackInSlot(0),0).isEmpty(),"enchanted journal uses SGA flag");need(!scanner.getStackInSlot(0).getTagCompound().hasKey("NavigationMapPixels"),"journal conversion clears saved map payload");scanner.clear();
        w.setBlockState(AT,Block.getBlockFromName("vandorlabs:printer").getDefaultState(),3);TileEntityChipAppliance printer=tile(w);p.setPosition(AT.getX()+.5,AT.getY(),AT.getZ()-2);printer.setInventorySlotContents(0,journal);ink(printer);printer.activate(p);ItemStack output=product(w);need(output.getItem()==Items.WRITTEN_BOOK&&output.getTagCompound().getString("title").equals("Original journal"),"Journal prints vanilla Written Book");need(printer.getStackInSlot(1).getCount()==1&&printer.getStackInSlot(2).getCount()==1&&ItemStack.areItemStacksEqual(printer.getStackInSlot(0),journal),"successful print consumes exactly one ink/paper and retains chip");
        printer.setInventorySlotContents(0,imported);ink(printer);int chunks=w.getChunkProvider().getLoadedChunkCount();long started=System.nanoTime();printer.activate(p);long millis=(System.nanoTime()-started)/1000000;output=product(w);need(output.getItem()==Items.FILLED_MAP,"Navigation prints vanilla Filled Map");MapData map=(MapData)w.loadData(MapData.class,"map_"+output.getMetadata());need(map.scale==0&&map.colors.length==16384,"highest zoom vanilla map");need(w.getChunkProvider().getLoadedChunkCount()==chunks,"printing never requests chunks");int painted=0;for(byte pixel:map.colors)if(pixel!=0)painted++;need(painted>16000,"prefilled imported Nav colors");System.out.println("[vandorlabs] printer-map sample elapsed-ms="+millis+" chunks unchanged="+chunks);
        ItemStack coordinates=new ItemStack(ModItems.DATA_CHIP);
        NavigationChip.write(coordinates,w.provider.getDimension(),65536,-65536);
        printer.setInventorySlotContents(0,coordinates);ink(printer);printer.activate(p);output=product(w);
        MapData terrain=(MapData)w.loadData(MapData.class,"map_"+output.getMetadata());
        int[] colors=ChipApplianceService.palette();
        for(int i=4;i<colors.length;i++)if(net.minecraft.block.material.MapColor.COLORS[i/4]==null)colors[i]=0;
        com.vandorlabs.appliance.PrinterPalette expected=new com.vandorlabs.appliance.PrinterPalette(colors);
        for(int z=0;z<128;z+=7)for(int x=0;x<128;x+=7){
            int rgb=NavigationService.color(w,terrain.xCenter-64+x,terrain.zCenter-64+z,true);
            need(terrain.colors[z*128+x]==(rgb==NavigationMap.ERROR?0:expected.pixel(rgb)),"printed seed colors match point samples");
        }
        need(w.getChunkProvider().getLoadedChunkCount()==chunks,"cold map printing keeps terrain unloaded");
        printer.setInventorySlotContents(0,CatalogChipData.masterStack());ink(printer);printer.activate(p);output=product(w);need(output.getItem()==Items.WRITTEN_BOOK&&output.getTagCompound().getTagList("pages",8).tagCount()<=50,"Catalog prints bounded vanilla recipes book");
        ink(printer);ItemStack vehicle=new ItemStack(ModItems.DATA_CHIP);com.vandorlabs.vehicle.VehicleChip.blank(vehicle);printer.setInventorySlotContents(0,vehicle);printer.activate(p);need(products(w).isEmpty()&&printer.getStackInSlot(1).getCount()==2&&printer.getStackInSlot(2).getCount()==2,"unsupported Vehicle does not consume inputs");printer.setInventorySlotContents(0,journal);w.setBlockState(AT.up(),Blocks.STONE.getDefaultState(),2);printer.activate(p);need(products(w).isEmpty()&&printer.getStackInSlot(1).getCount()==2,"blocked output consumes nothing");w.setBlockToAir(AT.up());
        CancelOutput cancelled=new CancelOutput();net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(cancelled);try{printer.activate(p);}finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(cancelled);}need(products(w).isEmpty()&&printer.getStackInSlot(1).getCount()==2&&printer.getStackInSlot(2).getCount()==2,"cancelled output spawn consumes nothing");
        printer.setInventorySlotContents(1,ItemStack.EMPTY);printer.activate(p);need(products(w).isEmpty()&&printer.getStackInSlot(2).getCount()==2,"missing ink consumes nothing");
    }
    private static final BlockPos AUTOMATED_PRINTER=AT.south(7),AUTOMATED_SCANNER=AT.south(10);
    private static net.minecraftforge.items.IItemHandler port(net.minecraft.tileentity.TileEntity tile,EnumFacing side){
        return tile.getCapability(net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,side);
    }
    private static void automation(WorldServer world){
        Block printer=Block.getBlockFromName("vandorlabs:printer"),scanner=Block.getBlockFromName("vandorlabs:scanner");
        for(Block block:new Block[]{printer,scanner})for(EnumFacing face:EnumFacing.HORIZONTALS){
            BlockPos pos=block==printer?AUTOMATED_PRINTER:AUTOMATED_SCANNER;
            world.setBlockState(pos,block.getDefaultState().withProperty(com.vandorlabs.blocks.BlockChipAppliance.FACING,face),3);
            TileEntityChipAppliance device=(TileEntityChipAppliance)world.getTileEntity(pos);
            EnumFacing outlet=block==printer?EnumFacing.UP:face.getOpposite();
            need(port(device,outlet).getSlots()==1&&port(device,face)==null&&port(device,EnumFacing.DOWN)==null,"rotated output and closed front/bottom");
            for(EnumFacing side:new EnumFacing[]{face.rotateY(),face.rotateYCCW()})
                need(port(device,side).getSlots()==(block==printer?3:2)&&port(device,side).getSlotLimit(0)==1,"rotated inputs and one-chip mount");
        }
        world.setBlockState(AUTOMATED_PRINTER,printer.getDefaultState(),3);
        world.setBlockState(AUTOMATED_SCANNER,scanner.getDefaultState(),3);
        TileEntityChipAppliance p=(TileEntityChipAppliance)world.getTileEntity(AUTOMATED_PRINTER),s=(TileEntityChipAppliance)world.getTileEntity(AUTOMATED_SCANNER);
        EnumFacing scanBack=world.getBlockState(AUTOMATED_SCANNER).getValue(com.vandorlabs.blocks.BlockChipAppliance.FACING).getOpposite();
        EnumFacing printFront=world.getBlockState(AUTOMATED_PRINTER).getValue(com.vandorlabs.blocks.BlockChipAppliance.FACING);
        BlockPos scanOutput=AUTOMATED_SCANNER.offset(scanBack),printOutput=AUTOMATED_PRINTER.up();
        Block duct=Block.getBlockFromName("thermaldynamics:duct_32");
        if(duct!=null){
            EntityPlayerMP installer=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(world);
            ItemStack ductStack=new ItemStack(duct,1,2);
            for(BlockPos segment:new BlockPos[]{printOutput,printOutput.south()}){
                world.setBlockState(segment,duct.getStateFromMeta(2),3);
                duct.onBlockPlacedBy(world,segment,world.getBlockState(segment),installer,ductStack);
            }
            world.setBlockState(printOutput.south(2),Blocks.CHEST.getDefaultState(),3);
            Item servo=Item.getByNameOrId("thermaldynamics:servo");
            need(servo!=null,"Thermal itemduct fixture can obtain a servo");
            installer.setPosition(AUTOMATED_PRINTER.getX()+.5,AUTOMATED_PRINTER.getY(),AUTOMATED_PRINTER.getZ()-2);
            installer.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(servo,1,4));
            need(servo.onItemUse(installer,world,AUTOMATED_PRINTER,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,1F,.5F)==EnumActionResult.SUCCESS,
                    "Thermal servo attaches to Printer outlet");
            world.setBlockState(printOutput.east(),Blocks.REDSTONE_BLOCK.getDefaultState(),3);
        }else world.setBlockState(printOutput,Blocks.CHEST.getDefaultState(),3);
        world.setBlockState(scanOutput,Blocks.CHEST.getDefaultState(),3);
        net.minecraft.inventory.IInventory scanChest=(net.minecraft.inventory.IInventory)world.getTileEntity(scanOutput);
        for(int i=0;i<scanChest.getSizeInventory();i++)scanChest.setInventorySlotContents(i,new ItemStack(Items.STICK,64));
        DeferredTileLoad.flush(world);
        ItemStack journal=new ItemStack(ModItems.DATA_CHIP);
        ChipJournal.write(journal,true,"Automated",new String[]{"A printed journal"});
        net.minecraftforge.items.IItemHandler input=port(p,printFront.rotateY());
        need(input.insertItem(0,journal,true).isEmpty()&&p.getStackInSlot(0).isEmpty(),"simulated input preserves owner");
        need(input.insertItem(0,journal,false).isEmpty()&&input.extractItem(0,1,false).isEmpty(),"left input accepts chip and cannot extract");
        need(port(p,printFront.rotateYCCW()).insertItem(1,new ItemStack(Items.DYE,2,0),false).isEmpty()
                &&input.insertItem(2,new ItemStack(Items.PAPER,2),false).isEmpty(),"both inputs accept consumables");
        need(port(p,EnumFacing.UP).insertItem(0,journal,false)==journal,"outlet rejects inputs");
        p.configureRedstone(ApplianceAutomation.ON,com.vandorlabs.redstone.ChannelList.EMPTY);
        p.update();
        world.setBlockState(AUTOMATED_PRINTER.offset(printFront),Blocks.REDSTONE_BLOCK.getDefaultState(),3);
        p.update();
        need(p.getStackInSlot(1).getCount()==1&&p.getStackInSlot(2).getCount()==1,"rising edge prints once with connected outlet");
        p.update();
        need(p.getStackInSlot(1).getCount()==1,"held signal does not print twice");
        world.setBlockToAir(AUTOMATED_PRINTER.offset(printFront));
        p.update();
        need(p.getStackInSlot(1).getCount()==1,"falling edge does not run ON mode");

        net.minecraftforge.items.IItemHandler scanIn=port(s,scanBack.rotateY());
        need(scanIn.insertItem(1,new ItemStack(Items.ENCHANTED_BOOK),false).isEmpty()
                &&scanIn.insertItem(0,new ItemStack(ModItems.DATA_CHIP),false).isEmpty(),"Scanner takes original and chip through its side");
        s.configureRedstone(ApplianceAutomation.OFF,com.vandorlabs.redstone.ChannelList.EMPTY);
        s.update();
        world.setBlockState(AUTOMATED_SCANNER.up(),Blocks.REDSTONE_BLOCK.getDefaultState(),3);
        s.update();
        need(s.getStackInSlot(0).getItem()==ModItems.DATA_CHIP,"Scanner OFF mode ignores rising edge");
        world.setBlockToAir(AUTOMATED_SCANNER.up());
        s.update();
        ItemStack pending=port(s,scanBack).extractItem(0,1,true);
        need(ChipJournal.enchanting(pending)&&s.getStackInSlot(0).isEmpty()
                &&s.getStackInSlot(1).getItem()==Items.ENCHANTED_BOOK,"falling edge buffers chip and retains original with full outlet");
        TileEntityChipAppliance reloaded=new TileEntityChipAppliance();
        reloaded.readFromNBT(s.writeToNBT(new NBTTagCompound()));
        need(ItemStack.areItemStacksEqual(reloaded.getStackInSlot(reloaded.outputSlot()),pending)
                &&reloaded.activation()==ApplianceAutomation.OFF&&!s.getUpdateTag().hasKey("ApplianceOutput"),"queued output and settings persist privately");
        scanChest.setInventorySlotContents(0,ItemStack.EMPTY);
    }
    private static void verifyDelivery(WorldServer world){
        BlockPos print=AUTOMATED_PRINTER.up();
        if(!(world.getTileEntity(print) instanceof net.minecraft.inventory.IInventory))print=print.south(2);
        net.minecraft.inventory.IInventory chest=(net.minecraft.inventory.IInventory)world.getTileEntity(print);
        need(chest!=null&&chest.getStackInSlot(0).getItem()==Items.WRITTEN_BOOK,"connected Printer delivers exactly one written book (buffer="+((TileEntityChipAppliance)world.getTileEntity(AUTOMATED_PRINTER)).getStackInSlot(3)+")");
        EnumFacing scanBack=world.getBlockState(AUTOMATED_SCANNER).getValue(com.vandorlabs.blocks.BlockChipAppliance.FACING).getOpposite();
        net.minecraft.inventory.IInventory scanChest=(net.minecraft.inventory.IInventory)world.getTileEntity(AUTOMATED_SCANNER.offset(scanBack));
        TileEntityChipAppliance scanner=(TileEntityChipAppliance)world.getTileEntity(AUTOMATED_SCANNER);
        need(ChipJournal.enchanting(scanChest.getStackInSlot(0))&&scanner.getStackInSlot(scanner.outputSlot()).isEmpty(),"blocked Scanner retries and exports exactly one chip");
        need(world.getEntitiesWithinAABB(EntityItem.class,new AxisAlignedBB(AUTOMATED_PRINTER).union(new AxisAlignedBB(AUTOMATED_SCANNER)).grow(2)).isEmpty(),
                "automation does not eject into the open world");
        System.out.println("[vandorlabs] appliance-automation PASS printer="+world.getBlockState(AUTOMATED_PRINTER.up()).getBlock().getRegistryName()+" scanner=chest");
    }
    public static final class CancelOutput {@net.minecraftforge.fml.common.eventhandler.SubscribeEvent public void spawn(net.minecraftforge.event.entity.EntityJoinWorldEvent event){if(event.getEntity() instanceof EntityItem)event.setCanceled(true);}}
}
