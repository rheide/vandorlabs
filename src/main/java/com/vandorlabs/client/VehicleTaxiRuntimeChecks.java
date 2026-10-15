package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import com.vandorlabs.items.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import java.io.File;
import java.util.*;

/** Real UI/network trips plus immediate placement audits before restored machines tick. */
public final class VehicleTaxiRuntimeChecks {
    private static int ticks,phase,placements,dialogWait,parkingWait;
    private static BlockPos origin=new BlockPos(240,101,80),seat=origin.up(2);
    private static UUID craftId;
    private static VehicleStructure expected,unrotated;
    private static final Audit audit=new Audit();
    private static boolean cancelOnce,rollbackSeen,done;
    private static BlockPos blocker,unrelatedSeat;
    private static UUID stalePreview;
    private static int modeUiStage;
    private static boolean invalidChannelDraft;
    private static final PoweredChannels powered=new PoweredChannels();
    private static final class PoweredChannels extends TileEntity implements com.vandorlabs.redstone.RedstoneChannelMember {
        public TileEntity channelTile(){return this;}
        public int getRedstoneChannel(){return 5317;}
        public com.vandorlabs.redstone.ChannelList getRedstoneChannels(){return com.vandorlabs.redstone.ChannelList.of(5317,5319);}
        public void setRedstoneChannel(int channel){}
        public boolean hasLocalRedstoneSignal(){return true;}
        public void setChannelSignal(boolean signal){}
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("vehicle taxi: "+message);}
    private static EntityPlayerMP player(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayers().get(0);}
    private static WorldServer world(Minecraft mc){return mc.getIntegratedServer().getWorld(0);}
    private static EntityGroundVehicle craft(Minecraft mc){return (EntityGroundVehicle)world(mc).getEntityFromUuid(craftId);}
    public static final class Audit {
        @SubscribeEvent public void placed(net.minecraftforge.event.world.BlockEvent.MultiPlaceEvent event) {
            if(expected==null)return;
            require(expected.matches(event.getWorld(),origin),"all restored block states and complete tile NBT match the parking snapshot");
            if(cancelOnce){cancelOnce=false;event.setCanceled(true);rollbackSeen=true;return;}
            placements++;
        }
        @SubscribeEvent public void restored(net.minecraftforge.event.entity.EntityJoinWorldEvent event) {
            if(event.getWorld().isRemote || !rollbackSeen || !(event.getEntity() instanceof EntityGroundVehicle) || !event.getEntity().getUniqueID().equals(craftId))return;
            EntityGroundVehicle restored=(EntityGroundVehicle)event.getEntity();
            require(restored.taxiReturnPending && restored.structure.write().equals(unrotated.write()),"rollback retains complete original snapshot and pending return");
            event.getWorld().setBlockState(blocker,Blocks.STONE.getDefaultState(),2);
        }
    }
    private static Block required(String id){Block block=Block.getBlockFromName(id);require(block!=null,"fixture installed: "+id);return block;}
    private static void setup(Minecraft mc) throws Exception {
        WorldServer w=world(mc);TaxiSettingsRuntimeChecks.run(w);EntityPlayerMP p=player(mc);p.dismountRidingEntity();p.setSneaking(false);p.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
        p.capabilities.allowFlying=true;p.capabilities.isFlying=true;p.sendPlayerAbilities();
        for(BlockPos pos:BlockPos.getAllInBox(origin.add(-12,-1,-12),origin.add(20,-1,20)))w.setBlockState(pos,Blocks.STONE.getDefaultState(),2);
        Block gear=null;for(Block b:Block.REGISTRY)if(b instanceof BlockTelescopicLandingGear){gear=b;break;}
        require(gear!=null,"landing gear fixture");VehicleGearRuntimeChecks.run(w,(BlockTelescopicLandingGear)gear);
        w.setBlockState(origin,gear.getDefaultState(),2);((TileEntityLandingGear)w.getTileEntity(origin)).configure(0,0,0,0);
        for(int x=0;x<=8;x++)w.setBlockState(origin.add(x,1,0),Blocks.STONE.getDefaultState(),2);
        // Two independent roots touch vertically; the hull reaches both around either side.
        // Each root also queues itself, exercising duplicate ownership and hull paths together.
        for(BlockPos offset:new BlockPos[]{new BlockPos(8,2,0),new BlockPos(8,3,0),new BlockPos(9,3,0),
                new BlockPos(10,1,0),new BlockPos(10,2,0),new BlockPos(10,3,0)})
            w.setBlockState(origin.add(offset),Blocks.STONE.getDefaultState(),2);
        for(int y=1;y<=2;y++) {
            BlockPos at=origin.add(9,y,0);w.setBlockState(at,gear.getDefaultState(),2);
            ((TileEntityLandingGear)w.getTileEntity(at)).configure(0,0,0,0);
        }
        IBlockState state=ModBlocks.PILOT_SEAT.getDefaultState();w.setBlockState(seat,state,2);ModBlocks.PILOT_SEAT.onBlockPlacedBy(w,seat,state,p,new ItemStack(ModBlocks.PILOT_SEAT));
        require(((TileEntityPilotSeat)w.getTileEntity(seat)).isTaxiMode(),"new seats default to Taxi Mode");
        w.setBlockState(origin.add(1,2,0),Block.getBlockFromName("vandorlabs:impulse_engine").getDefaultState(),2);
        TileEntityPilotSeat legacy=new TileEntityPilotSeat();legacy.readFromNBT(new NBTTagCompound());require(legacy.isTaxiMode(),"legacy seats default to Taxi Mode");
        BlockPos furnacePos=origin.add(2,2,0);w.setBlockState(furnacePos,Blocks.FURNACE.getDefaultState().withProperty(net.minecraft.block.BlockFurnace.FACING,EnumFacing.EAST),2);
        TileEntityFurnace furnace=(TileEntityFurnace)w.getTileEntity(furnacePos);furnace.setCustomInventoryName("Taxi furnace");
        furnace.setInventorySlotContents(0,new ItemStack(Blocks.IRON_ORE,13));furnace.setInventorySlotContents(1,new ItemStack(Items.COAL,7));furnace.setInventorySlotContents(2,new ItemStack(Items.IRON_INGOT,5));
        furnace.setField(0,137);furnace.setField(2,73);furnace.setField(3,200);furnace.markDirty();
        BlockPos chestPos=origin.add(3,2,0);w.setBlockState(chestPos,Blocks.CHEST.getDefaultState().withProperty(net.minecraft.block.BlockChest.FACING,EnumFacing.WEST),2);
        ((TileEntityChest)w.getTileEntity(chestPos)).setInventorySlotContents(7,new ItemStack(Items.DIAMOND,11));
        if(Boolean.getBoolean("vandorlabs.foreignVehicleChecks")) {
            Block ie=required("immersiveengineering:metal_device0");BlockPos at=origin.add(4,2,0);w.setBlockState(at,ie.getStateFromMeta(0),2);
            TileEntity tile=w.getTileEntity(at);require(tile!=null && tile.getClass().getSimpleName().equals("TileEntityCapacitorLV"),"IE capacitor fixture");
            NBTTagCompound data=tile.writeToNBT(new NBTTagCompound());data.setInteger("ifluxEnergy",12345);
            for(int n=0;n<6;n++)data.setInteger("sideConfig_"+n,n%3);tile.readFromNBT(data);tile.markDirty();
            // Leave an air gap so normal pre-assembly ticking cannot empty the capacitor into the cube.
            Block mekanism=required("mekanism:energycube");at=origin.add(6,2,0);w.setBlockState(at,mekanism.getDefaultState(),2);tile=w.getTileEntity(at);
            require(tile!=null && tile.getClass().getSimpleName().equals("TileEntityEnergyCube"),"Mekanism energy cube fixture");
            tile.getClass().getMethod("setEnergy",double.class).invoke(tile,23456D);data=tile.writeToNBT(new NBTTagCompound());data.setInteger("facing",EnumFacing.EAST.getIndex());tile.readFromNBT(data);tile.markDirty();
            Block door=null;for(Block b:Block.REGISTRY)if(b.getRegistryName().getResourceDomain().equals("malisisdoors") && b.getClass().getName().equals("net.malisis.doors.block.Door")){door=b;break;}
            require(door!=null,"Malisis standard door fixture");at=origin.add(7,2,0);
            w.setBlockState(at,door.getDefaultState().withProperty(BlockDoor.FACING,EnumFacing.EAST).withProperty(BlockDoor.HALF,BlockDoor.EnumDoorHalf.LOWER),2);
            w.setBlockState(at.up(),door.getDefaultState().withProperty(BlockDoor.HALF,BlockDoor.EnumDoorHalf.UPPER),2);
            require(w.getTileEntity(at)!=null,"Malisis door tile installed");
            System.out.println("[vandorlabs][reprolab] vehicle-preservation fixtures IE-capacitor Mekanism-energy-cube "+door.getRegistryName());
        }
        VehicleScan rejected=new VehicleScan(w,seat);int rejectedSlices=0;
        Map<BlockPos,VehicleStructure.Cell> unchanged=new HashMap<>();
        for(BlockPos at:BlockPos.getAllInBox(origin,origin.add(10,4,0))) {
            TileEntity tile=w.getTileEntity(at);
            unchanged.put(at.toImmutable(),new VehicleStructure.Cell(at,w.getBlockState(at),tile==null?null:tile.writeToNBT(new NBTTagCompound())));
        }
        while(!rejected.step(1) && ++rejectedSlices<512);
        require(rejected.result==null && rejected.error!=null && rejected.error.contains("same Y value") && rejectedSlices<512,
                "vertically stacked gears in a hull loop are rejected within bounded scan slices");
        for(VehicleStructure.Cell cell:unchanged.values()) {
            TileEntity tile=w.getTileEntity(cell.pos);
            require(w.getBlockState(cell.pos).equals(cell.state) && Objects.equals(cell.tileData(),tile==null?null:tile.writeToNBT(new NBTTagCompound())),
                    "misaligned gear rejection preserves every block and tile");
        }
        for(int y=1;y<=2;y++)w.setBlockState(origin.add(9,y,0),Blocks.STONE.getDefaultState(),2);
        for(int x=9;x<=10;x++) {
            BlockPos at=origin.add(x,0,0);w.setBlockState(at,gear.getDefaultState(),2);
            require(((TileEntityLandingGear)w.getTileEntity(at)).configure(0,0,0,0),"aligned loop gear setup");
        }
        TileEntityLandingGear half=(TileEntityLandingGear)w.getTileEntity(origin.add(10,0,0));
        w.setBlockToAir(half.getPos().down());
        require(half.configure(0,0,8,0),"half-block extension setup");
        require(((BlockTelescopicLandingGear)gear).setExtended(w,half.getPos(),true),"extend half-block gear");half.progress=half.previous=.5F;
        VehicleScan halfScan=new VehicleScan(w,seat);int halfSlices=0;while(!halfScan.step(1) && ++halfSlices<512);
        require(halfScan.result==null && halfScan.error!=null && halfScan.error.contains("same Y value"),"half-block difference is not rounded into the same support plane");
        require(((BlockTelescopicLandingGear)gear).setExtended(w,half.getPos(),false),"retract half-block gear");half.progress=half.previous=0;
        require(half.configure(0,0,0,0),"restore aligned support");
        w.setBlockState(half.getPos().down(),Blocks.STONE.getDefaultState(),2);
        VehicleScan loop=new VehicleScan(w,seat);int slices=0;
        while(!loop.step(1) && ++slices<512);
        require(loop.error==null && loop.result!=null && slices<512,"aligned gear/hull loop finishes within bounded one-cell scan slices: "+loop.error+", slices="+slices);
        require(loop.result.gearCount==3,"aligned gear roots are each counted once");
        Set<BlockPos> unique=new HashSet<>();
        for(VehicleStructure.Cell cell:loop.result.cells) {
            require(unique.add(cell.pos),"cyclic discovery captures each cell once");
            require(loop.origin.add(cell.pos).getY()>=origin.getY(),"gear boundary excludes the supporting ground despite alternate hull paths");
        }
        for(int x=9;x<=10;x++)require(loop.result.at(origin.add(x,0,0).subtract(loop.origin))!=null,"both aligned gears are captured through the hull loop");
        powered.setWorld(w);powered.setPos(origin.west(4));com.vandorlabs.redstone.RedstoneChannels.register(powered);
        MinecraftForge.EVENT_BUS.register(audit);p.connection.setPlayerLocation(seat.getX()+.5,seat.getY(),seat.getZ()-2,0,0);
    }
    private static void blockClick(Minecraft mc,boolean settings) throws Exception {
        Vec3d eye=new Vec3d(seat.getX()+.5,seat.getY()+.5,seat.getZ()-1.5),at=new Vec3d(seat.getX()+.5,seat.getY()+.5,seat.getZ()+.5);
        mc.player.setPositionAndRotation(eye.x,eye.y-mc.player.getEyeHeight(),eye.z,0,0);
        mc.player.setSneaking(settings);
        mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketEntityAction(mc.player,settings?net.minecraft.network.play.client.CPacketEntityAction.Action.START_SNEAKING:net.minecraft.network.play.client.CPacketEntityAction.Action.STOP_SNEAKING));
        mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketPlayer.PositionRotation(mc.player.posX,mc.player.posY,mc.player.posZ,0,0,false));
        mc.objectMouseOver=new RayTraceResult(at,EnumFacing.NORTH,seat);
        net.minecraftforge.fml.relauncher.ReflectionHelper.findMethod(Minecraft.class,"rightClickMouse","func_147121_ag").invoke(mc);
    }
    private static Object field(Object object,String name)throws Exception {java.lang.reflect.Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}
    static void button(Minecraft mc,int id) throws Exception {
        GuiScreen screen=mc.currentScreen;require(screen!=null,"settings dialog open");
        List<GuiButton> buttons=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(GuiScreen.class,screen,"buttonList","field_146292_n");
        GuiButton selected=null;for(GuiButton b:buttons)if(b.id==id)selected=b;
        require(selected!=null && selected.enabled,"dialog button "+id);
        net.minecraftforge.fml.relauncher.ReflectionHelper.findMethod(screen.getClass(),"actionPerformed","func_146284_a",GuiButton.class).invoke(screen,selected);
    }
    static List<GuiButton> previewLayout(Minecraft mc){
        GuiScreen gui=mc.currentScreen;
        require(gui!=null&&gui.getClass().getName().endsWith("VehicleClient$Preview"),"both entry paths use the integrated preview");
        int originalWidth=gui.width,originalHeight=gui.height;
        try{gui.setWorldAndResolution(mc,320,240);previewControls(gui);}finally{gui.setWorldAndResolution(mc,originalWidth,originalHeight);}
        return previewControls(gui);
    }
    private static List<GuiButton> previewControls(GuiScreen gui){
        List<GuiButton> controls=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(GuiScreen.class,gui,"buttonList","field_146292_n");
        require(controls.stream().noneMatch(b->"Save".equalsIgnoreCase(b.displayString)),"preview applies valid changes without a Save button");
        GuiButton modes=null;
        for(GuiButton b:controls)if(b.visible){
            require(b.x>=0&&b.y>=0&&b.x+b.width<=gui.width&&b.y+b.height<=gui.height,"visible control fits screen: "+b.displayString);
            if(b.displayString.toLowerCase(java.util.Locale.ROOT).contains("mode channels"))modes=b;
        }
        require(modes!=null&&modes.enabled&&modes.width>=140&&modes.height>=20,"mode channels is a prominent usable main control");
        return controls;
    }
    static void screenshot(Minecraft mc,File output,String name)throws Exception{
        mc.entityRenderer.setupOverlayRendering();mc.currentScreen.drawScreen(-1,-1,0);
        javax.imageio.ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,name));
    }
    static void type(Minecraft mc,GuiTextField input,String text)throws Exception{
        GuiScreen gui=mc.currentScreen;
        java.lang.reflect.Method mouse=net.minecraftforge.fml.relauncher.ReflectionHelper.findMethod(GuiScreen.class,"mouseClicked","func_73864_a",int.class,int.class,int.class);
        java.lang.reflect.Method key=net.minecraftforge.fml.relauncher.ReflectionHelper.findMethod(GuiScreen.class,"keyTyped","func_73869_a",char.class,int.class);
        mouse.invoke(gui,input.x+4,input.y+input.height/2,0);
        require(input.isFocused(),"actual mouse click focuses channel field");
        key.invoke(gui,'\0',org.lwjgl.input.Keyboard.KEY_HOME);
        for(int n=input.getText().length();n>0;n--)key.invoke(gui,'\0',org.lwjgl.input.Keyboard.KEY_DELETE);
        for(char character:text.toCharArray())key.invoke(gui,character,0);
        require(input.getText().equals(text),"actual keyboard path enters channel draft");
    }
    private static void mounted(Minecraft mc) throws Exception {
        mc.getIntegratedServer().addScheduledTask(()->{
            require(player(mc).getRidingEntity() instanceof EntityGroundVehicle,"Taxi Mode assembles and mounts through client block click");
            EntityGroundVehicle e=(EntityGroundVehicle)player(mc).getRidingEntity();craftId=e.getUniqueID();
            require(e.structure.at(e.structure.seat).tileData().getBoolean("TaxiMode"),"saved automatic behavior");
        }).get();
        require(mc.player.getRidingEntity() instanceof EntityGroundVehicle,"client riding identity synchronized");
    }
    private static void prepareExit(Minecraft mc,int turn) throws Exception {
        mc.getIntegratedServer().addScheduledTask(()->{
            EntityGroundVehicle e=craft(mc);e.stopForParking();e.rotationYaw=turn*90;unrotated=e.structure;
            VehicleRotation.Plan plan=VehicleRotation.plan(e);expected=plan.structure;origin=plan.origin;seat=origin.add(expected.seat);blocker=origin.add(expected.cells.get(0).pos);
            Rotation rotation=Rotation.values()[turn];
            for(VehicleStructure.Cell before:e.structure.cells) {
                BlockPos rotatedPos=VehicleRotation.cell(before.pos,(int)e.structure.bounds.maxX,(int)e.structure.bounds.maxZ,turn);
                VehicleStructure.Cell after=expected.at(rotatedPos);
                if(before.state.getBlock() instanceof net.minecraft.block.BlockFurnace)
                    require(after.state.getValue(net.minecraft.block.BlockFurnace.FACING)==rotation.rotate(before.state.getValue(net.minecraft.block.BlockFurnace.FACING)),"furnace facing follows cardinal rotation");
                if(before.state.getBlock() instanceof net.minecraft.block.BlockChest)
                    require(after.state.getValue(net.minecraft.block.BlockChest.FACING)==rotation.rotate(before.state.getValue(net.minecraft.block.BlockChest.FACING)),"chest facing follows cardinal rotation");
                NBTTagCompound data=before.tileData();if(data==null)continue;
                String domain=before.state.getBlock().getRegistryName().getResourceDomain();
                if(domain.equals("immersiveengineering"))require(data.getInteger("ifluxEnergy")==12345,"IE fixture retains nonzero stored energy through every trip");
                if(domain.equals("mekanism"))require(data.getDouble("electricityStored")==23456D,"Mekanism fixture retains nonzero stored energy through every trip");
                if(domain.equals("mekanism") && data.hasKey("facing",3))
                    require(after.tileData().getInteger("facing")==rotation.rotate(EnumFacing.getFront(data.getInteger("facing"))).getIndex(),"Mekanism tile facing follows cardinal rotation");
                if(domain.equals("immersiveengineering"))for(EnumFacing side:EnumFacing.values())
                    require(after.tileData().getInteger("sideConfig_"+rotation.rotate(side).getIndex())==data.getInteger("sideConfig_"+side.getIndex()),"IE side configuration follows world-side rotation");
            }
            NBTTagCompound saved=new NBTTagCompound();e.writeToNBT(saved);EntityGroundVehicle loaded=new EntityGroundVehicle(world(mc));loaded.readFromNBT(saved);
            require(loaded.structure.write().equals(e.structure.write()),"entity save/read preserves all mod tile data");
            // Each family must return exactly to its initial states and NBT after four rotations.
            VehicleStructure cycle=e.structure;for(int n=0;n<4;n++)cycle=VehicleRotation.rotate(cycle,new VehicleWorld(world(mc),cycle),1);
            require(cycle.write().equals(e.structure.write()),"four cardinal rotations preserve full state/NBT");
        }).get();
    }
    public static boolean tick(Minecraft mc,File output) throws Exception {
        if(done)return true;if(ticks++%25!=0)return false;
        switch(phase++) {
            case 0:mc.getIntegratedServer().addScheduledTask(()->{try{setup(mc);}catch(Exception e){throw new IllegalStateException(e);}}).get();break;
            case 1:if(mc.world.getBlockState(seat).getBlock()!=ModBlocks.PILOT_SEAT){require(++dialogWait<16,"fixture chunks reach client");phase--;break;}blockClick(mc,true);break;
            case 2:if(mc.currentScreen==null||!mc.currentScreen.getClass().getName().endsWith("VehicleClient$Preview")){require(++dialogWait<24,"placed pilot seat opens integrated preview");blockClick(mc,true);phase--;break;}previewLayout(mc);screenshot(mc,output,"shot_pilot_shift_preview.png");button(mc,2);break;
            case 3:mc.getIntegratedServer().addScheduledTask(()->require(!((TileEntityPilotSeat)world(mc).getTileEntity(seat)).isTaxiMode(),"OFF setting saved through UI/network")).get();if(mc.currentScreen!=null){button(mc,1);phase--;break;}blockClick(mc,false);break;
            case 4:mc.getIntegratedServer().addScheduledTask(()->{require(player(mc).getRidingEntity() instanceof com.vandorlabs.entity.EntityChairSeat,"manual mode sits without conversion");player(mc).dismountRidingEntity();player(mc).setHeldItem(EnumHand.MAIN_HAND,new ItemStack(ModItems.CONFIGURIZER));VehicleService.previewAssembly(player(mc),seat);}).get();break;
            case 5:previewLayout(mc);screenshot(mc,output,"shot_pilot_configurizer_preview.png");require(field(mc.currentScreen,"channels") instanceof GuiTextField,"channels integrated into assembly dialog");button(mc,5);break;
            case 6:mc.getIntegratedServer().addScheduledTask(()->require(((TileEntityPilotSeat)world(mc).getTileEntity(seat)).getHeight()==1,"assembly preview height applies immediately")).get();type(mc,(GuiTextField)field(mc.currentScreen,"channels"),"5319");mc.currentScreen.updateScreen();break;
            case 7:mc.getIntegratedServer().addScheduledTask(()->{
                TileEntityPilotSeat pilot=(TileEntityPilotSeat)world(mc).getTileEntity(seat);
                require(pilot.getRedstoneChannel()==5319 && !player(mc).isRiding(),"assembly preview settings save placed seat without conversion on a held-high channel");
                player(mc).setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
            }).get();button(mc,1);break;
            case 8:mc.getIntegratedServer().addScheduledTask(()->require(world(mc).getTileEntity(seat) instanceof TileEntityPilotSeat,"held-high edit does not queue automatic conversion")).get();break;
            case 9:blockClick(mc,true);break;
            case 10:previewLayout(mc);button(mc,2);break;
            case 11:if(mc.currentScreen!=null){button(mc,1);phase--;break;}blockClick(mc,false);break;
            case 12:mounted(mc);prepareExit(mc,1);mc.getIntegratedServer().addScheduledTask(()->player(mc).dismountRidingEntity()).get();break;
            case 13:mc.getIntegratedServer().addScheduledTask(()->{require(craft(mc)==null || craft(mc).isDead,"90-degree taxi exit restores blocks");require(placements==1,"immediate complete tile preservation audit");require(!player(mc).isRiding(),"automatic parking does not remount or loop");}).get();blockClick(mc,false);break;
            case 14:mounted(mc);prepareExit(mc,0);mc.getIntegratedServer().addScheduledTask(()->{world(mc).setBlockState(blocker,Blocks.STONE.getDefaultState(),2);player(mc).dismountRidingEntity();}).get();break;
            case 15:mc.getIntegratedServer().addScheduledTask(()->{EntityGroundVehicle e=craft(mc);require(e!=null && e.taxiReturnPending && e.structure.write().equals(unrotated.write()),"obstructed taxi exit keeps full craft and retry state");require(world(mc).getBlockState(blocker).getBlock()==Blocks.STONE,"parking never overwrites obstacles");NBTTagCompound saved=new NBTTagCompound();e.writeToNBT(saved);EntityGroundVehicle loaded=new EntityGroundVehicle(world(mc));loaded.readFromNBT(saved);require(loaded.taxiReturnPending && loaded.taxiReturnActor.equals(player(mc).getUniqueID()),"pending parking survives entity reload");world(mc).setBlockToAir(blocker);}).get();break;
            case 16:if(!mc.getIntegratedServer().callFromMainThread(()->placements==2 && (craft(mc)==null || craft(mc).isDead)).get()){require(++parkingWait<40,"clearing obstruction automatically restores blocks");phase--;break;}parkingWait=0;blockClick(mc,false);break;
            case 17:mounted(mc);prepareExit(mc,3);cancelOnce=true;mc.getIntegratedServer().addScheduledTask(()->player(mc).dismountRidingEntity()).get();break;
            case 18:mc.getIntegratedServer().addScheduledTask(()->{EntityGroundVehicle e=craft(mc);require(rollbackSeen && e!=null && e.taxiReturnPending && e.structure.write().equals(unrotated.write()),"cancelled rotated placement rolls back to intact returnable craft");require(!VehicleTransfer.pending(world(mc)),"rollback retires recovery journal");rollbackSeen=false;world(mc).setBlockToAir(blocker);}).get();break;
            case 19:if(!mc.getIntegratedServer().callFromMainThread(()->placements==3 && (craft(mc)==null || craft(mc).isDead)).get()){require(++parkingWait<40,"taxi retries successfully after protection cancellation rollback");phase--;break;}parkingWait=0;blockClick(mc,false);break;
            case 20:mounted(mc);mc.getIntegratedServer().addScheduledTask(()->{
                EntityGroundVehicle e=craft(mc);VehicleStructure before=e.structure;
                unrelatedSeat=before.seat;world(mc).setBlockState(unrelatedSeat,ModBlocks.PILOT_SEAT.getDefaultState(),2);
                ((TileEntityPilotSeat)world(mc).getTileEntity(unrelatedSeat)).setRedstoneChannel(777);
                List<VehicleStructure.Cell> unknown=new ArrayList<>(before.cells);BlockPos extra=new BlockPos(0,0,2);TileEntityCommandBlock command=new TileEntityCommandBlock();command.setPos(extra);
                unknown.add(new VehicleStructure.Cell(extra,Blocks.COMMAND_BLOCK.getDefaultState(),command.writeToNBT(new NBTTagCompound())));
                e.install(new VehicleStructure(unknown,before.seat,before.facing,before.gearCount));e.rotationYaw=90;
                VehicleRotation.Plan commandPlan=VehicleRotation.plan(e);
                BlockPos rotatedCommand=VehicleRotation.cell(extra,(int)e.structure.bounds.maxX,(int)e.structure.bounds.maxZ,1);
                require(commandPlan.structure.at(rotatedCommand).state.getBlock()==Blocks.COMMAND_BLOCK,"vanilla command block rotates with complete private data");
                e.install(before);
                e.rotationYaw=45;player(mc).dismountRidingEntity();require(!player(mc).isRiding() && e.taxiReturnPending,"45-degree exit allows dismount without losing snapshot");require(e.board(player(mc)),"reboard after noncardinal exit");
                e.rotationYaw=0;player(mc).setHeldItem(EnumHand.MAIN_HAND,new ItemStack(ModItems.CONFIGURIZER));VehicleService.previewParking(player(mc),e);
            }).get();break;
            case 21: {
                if(modeUiStage==0){previewLayout(mc);screenshot(mc,output,"shot_pilot_captured_preview.png");button(mc,6);modeUiStage++;phase--;break;}
                if(modeUiStage==1){require(mc.currentScreen instanceof GuiPilotModes,"mode channel child opens from preview");screenshot(mc,output,"shot_pilot_mode_channels.png");GuiTextField[] fields=(GuiTextField[])field(mc.currentScreen,"fields");type(mc,fields[1],"6123");mc.currentScreen.updateScreen();modeUiStage++;phase--;break;}
                if(modeUiStage==2){mc.getIntegratedServer().addScheduledTask(()->require(((TileEntityPilotSeat)craft(mc).view.getTileEntity(craft(mc).structure.seat)).modeChannels(1).toString().equals("6123"),"child mode channels automatically apply")).get();((GuiTextField[])field(mc.currentScreen,"fields"))[1].setText("1,,2");mc.currentScreen.updateScreen();modeUiStage++;phase--;break;}
                if(modeUiStage==3){mc.getIntegratedServer().addScheduledTask(()->require(((TileEntityPilotSeat)craft(mc).view.getTileEntity(craft(mc).structure.seat)).modeChannels(1).toString().equals("6123"),"invalid child draft preserves saved mode channels")).get();button(mc,4);modeUiStage++;phase--;break;}
                previewLayout(mc);
                Object data=field(mc.currentScreen,"data");stalePreview=((VehicleNetwork.Packet)field(data,"header")).token;
                break;
            }
            case 22: {
                GuiOptionCycleRuntimeChecks.check(mc);
                GuiTextField channels=(GuiTextField)field(mc.currentScreen,"channels");channels.setText("5317, 5318");channels.setText("5319");button(mc,5);break;
            }
            case 23:require(((GuiTextField)field(mc.currentScreen,"channels")).getText().equals("5319"),"live height preserves current channel text");mc.getIntegratedServer().addScheduledTask(()->{require(((TileEntityPilotSeat)craft(mc).view.getTileEntity(craft(mc).structure.seat)).getHeight()==2,"vehicle preview height applies immediately");require(((TileEntityPilotSeat)craft(mc).view.getTileEntity(craft(mc).structure.seat)).getRedstoneChannels().toString().equals("5319"),"valid channel draft automatically applies");}).get();break;
            case 24: {
                GuiTextField channels=(GuiTextField)field(mc.currentScreen,"channels");
                if(!invalidChannelDraft){channels.setText("1,,2");mc.currentScreen.updateScreen();invalidChannelDraft=true;phase--;break;}
                List<GuiButton> controls=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(GuiScreen.class,mc.currentScreen,"buttonList","field_146292_n");
                require(controls.stream().noneMatch(b->b.id==3),"Save button removed");require(controls.stream().anyMatch(b->b.id==0 && !b.enabled),"invalid channel list disables conversion");
                mc.getIntegratedServer().addScheduledTask(()->require(((TileEntityPilotSeat)craft(mc).view.getTileEntity(craft(mc).structure.seat)).getRedstoneChannels().toString().equals("5319"),"invalid draft never mutates craft")).get();
                channels.setText("5318, 5317, 5318");mc.currentScreen.updateScreen();break;
            }
            case 25: {
                Object data=field(mc.currentScreen,"data");UUID currentPreview=((VehicleNetwork.Packet)field(data,"header")).token;
                mc.getIntegratedServer().addScheduledTask(()->{
                EntityGroundVehicle e=craft(mc);TileEntityPilotSeat pilot=(TileEntityPilotSeat)e.view.getTileEntity(e.structure.seat);
                require(pilot.getRedstoneChannels().toString().equals("5317, 5318"),"actual settings packet saves canonical channel list");
                require(com.vandorlabs.redstone.ChannelData.read(e.structure.at(e.structure.seat).tileData(),0).equals(pilot.getRedstoneChannels()),"authoritative seat snapshot and local view agree");
                NBTTagCompound saved=new NBTTagCompound();e.writeToNBT(saved);EntityGroundVehicle loaded=new EntityGroundVehicle(world(mc));loaded.readFromNBT(saved);
                require(loaded.structure.write().equals(e.structure.write()),"edited channels survive entity save/reload");
                VehicleNetwork.Packet stale=new VehicleNetwork.Packet(VehicleNetwork.SETTINGS);stale.token=stalePreview;stale.entity=e.getEntityId();stale.origin=e.structure.seat;stale.text="999";VehicleService.configureChannels(player(mc),stale);
                require(pilot.getRedstoneChannels().toString().equals("5317, 5318"),"superseded preview cannot edit channels");
                require(((TileEntityPilotSeat)world(mc).getTileEntity(unrelatedSeat)).getRedstoneChannel()==777,"assembled settings never edit a real tile at the local seat coordinates");
                VehicleNetwork.Packet denied=new VehicleNetwork.Packet(VehicleNetwork.SETTINGS);denied.token=currentPreview;denied.entity=e.getEntityId();denied.origin=e.structure.seat;denied.text="999";
                player(mc).setGameType(net.minecraft.world.GameType.ADVENTURE);
                try{VehicleService.configureChannels(player(mc),denied);}finally{player(mc).setGameType(net.minecraft.world.GameType.CREATIVE);}
                require(e.structure==loaded.structure || e.structure.write().equals(loaded.structure.write()),"denied settings preserve the authoritative snapshot");
                require(pilot.getRedstoneChannels().toString().equals("5317, 5318"),"protection rejects channel edits");
                denied.origin=e.structure.seat.up();VehicleService.configureChannels(player(mc),denied);
                require(pilot.getRedstoneChannels().toString().equals("5317, 5318"),"forged seat identity rejected");
            }).get();break;
            }
            case 26:require(ChannelFields.parse((GuiTextField)field(mc.currentScreen,"channels")).toString().equals("5317, 5318"),"integrated settings draft represents saved canonical list");break;
            case 27:require(mc.player.getRidingEntity() instanceof EntityGroundVehicle,"reboarding after non-cardinal exits keeps client able to steer");require(mc.currentScreen!=null && mc.currentScreen.getClass().getName().contains("VehicleClient$Preview"),"Configurator parking dialog");button(mc,2);break;
            case 28:mc.getIntegratedServer().addScheduledTask(()->require(!VehicleTaxiMode.enabled(craft(mc).structure),"assembled Taxi Mode OFF synchronized")).get();prepareExit(mc,0);button(mc,0);break;
            case 29:mc.getIntegratedServer().addScheduledTask(()->{require(craft(mc)==null || craft(mc).isDead,"manual Configurizer recovery remains available");require(placements==4,"manual parking preserves full snapshot");MinecraftForge.EVENT_BUS.unregister(audit);com.vandorlabs.redstone.RedstoneChannels.unregister(powered);}).get();
                javax.imageio.ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"shot_vehicle_taxi.png"));
                System.out.println("[vandorlabs][reprolab] vehicle-taxi PASS unified-entry-preview no-save autoapply valid-channels invalid-draft-preserved mode-channel-child minimum-layout screenshots held-high-baseline stale-preview canonical-list stacked-gear-loop bounded-discovery default-on manual-off actual-boarding 90-270-parking 45-dismount-preserved repeated-trips blocked-retry rollback-retry entity-save manual-configurizer full-state-NBT");done=true;return true;
            default:throw new IllegalStateException("Taxi test phase overflow");
        }
        return false;
    }
}
