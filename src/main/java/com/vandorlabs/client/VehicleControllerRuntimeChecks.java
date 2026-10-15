package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import java.io.File;
import java.util.*;

/** Real controller conversion and authoritative multi-seat passenger contracts. */
public final class VehicleControllerRuntimeChecks {
    private static final BlockPos BASE=new BlockPos(200,121,200),CONTROL=BASE.up(2),FIRST=BASE.add(2,2,0),SECOND=BASE.add(5,2,0);
    private static int state,ticks,standaloneStage;private static boolean done;private static UUID craftId;
    private static BlockPos parkedFirst,parkedSecond;
    private static final UUID CREW_A=UUID.fromString("625d9baf-3d0e-427a-8785-39c10ca5eb9a"),CREW_B=UUID.fromString("870c9095-c728-44fb-bd1e-8f4f3489de15");
    private static void require(boolean yes,String message){if(!yes)throw new IllegalStateException("vehicle-controller: "+message);}
    private static EntityPlayerMP player(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayers().get(0);}
    private static WorldServer world(Minecraft mc){return mc.getIntegratedServer().getWorld(0);}
    private static Block controller(){Block block=Block.getBlockFromName("vandorlabs:vehicle_controller");require(block instanceof BlockVehicleController,"dedicated controller registered");return block;}
    private static Object field(Object object,String name)throws Exception{java.lang.reflect.Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}
    private static VehicleNetwork.Packet header(Minecraft mc)throws Exception{return (VehicleNetwork.Packet)field(field(mc.currentScreen,"data"),"header");}
    private static List<GuiButton> buttons(Minecraft mc){return net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(GuiScreen.class,mc.currentScreen,"buttonList","field_146292_n");}
    private static void setup(Minecraft mc){
        WorldServer world=world(mc);EntityPlayerMP actor=player(mc);actor.dismountRidingEntity();actor.setSneaking(false);actor.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
        actor.capabilities.allowFlying=true;actor.capabilities.isFlying=true;actor.sendPlayerAbilities();
        for(BlockPos pos:BlockPos.getAllInBox(BASE.add(-4,-1,-4),BASE.add(10,6,5)))world.setBlockToAir(pos);
        for(BlockPos pos:BlockPos.getAllInBox(BASE.add(-4,-1,-4),BASE.add(10,-1,5)))world.setBlockState(pos,Blocks.STONE.getDefaultState(),2);
        Block gear=null;for(Block block:Block.REGISTRY)if(block instanceof BlockTelescopicLandingGear){gear=block;break;}require(gear!=null,"landing gear registered");
        for(int x:new int[]{0,3,6}){world.setBlockState(BASE.east(x),gear.getDefaultState(),2);((TileEntityLandingGear)world.getTileEntity(BASE.east(x))).configure(0,0,0,0);}
        for(int x=0;x<=6;x++)for(int z=0;z<=1;z++)world.setBlockState(BASE.add(x,1,z),Blocks.IRON_BLOCK.getDefaultState(),2);
        world.setBlockState(CONTROL,controller().getDefaultState(),3);
        world.setBlockState(BASE.add(6,2,0),Block.getBlockFromName("vandorlabs:impulse_engine").getDefaultState(),2);
        VehicleScan empty=new VehicleScan(world,CONTROL);for(int step=0;step<100&&!empty.step(256);step++){}
        require(empty.result==null&&empty.error!=null&&empty.error.contains("Pilot Seat"),"zero-seat controller scan rejects conversion");
        for(BlockPos pos:new BlockPos[]{FIRST,SECOND}){
            world.setBlockState(pos,ModBlocks.PILOT_SEAT.getDefaultState(),3);ModBlocks.PILOT_SEAT.onBlockPlacedBy(world,pos,world.getBlockState(pos),actor,new ItemStack(ModBlocks.PILOT_SEAT));
            ((TileEntityPilotSeat)world.getTileEntity(pos)).setTaxiMode(false);
        }
        VehicleScan multiple=new VehicleScan(world,CONTROL);for(int step=0;step<100&&!multiple.step(256);step++){}
        require(multiple.result!=null&&multiple.result.pilotSeats().size()==2,"connected controller accepts two pilot seats: "+multiple.error);
        migrationChecks(world,multiple.result);
        actor.setPositionAndUpdate(CONTROL.getX()+.5,CONTROL.getY(),CONTROL.getZ()-2.5);
    }
    private static void standalone(Minecraft mc,File output)throws Exception{
        BlockPos at=BASE.west(12);
        if(standaloneStage==0){mc.getIntegratedServer().addScheduledTask(()->{
            EntityPlayerMP actor=player(mc);actor.dismountRidingEntity();actor.setSneaking(false);actor.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
            for(BlockPos pos:BlockPos.getAllInBox(at.add(-2,-2,-2),at.add(2,3,2)))world(mc).setBlockToAir(pos);
            // Support the observer independently: the controller itself must stay isolated.
            for(BlockPos floor:BlockPos.getAllInBox(at.add(-1,-1,-3),at.add(1,-1,-1)))world(mc).setBlockState(floor,Blocks.STONE.getDefaultState(),2);
            actor.capabilities.allowFlying=true;actor.capabilities.isFlying=true;actor.sendPlayerAbilities();
            world(mc).setBlockState(at,controller().getDefaultState(),3);actor.setPositionAndUpdate(at.getX()+.5,at.getY(),at.getZ()-2);
            VehicleService.previewController(actor,at);
        }).get();standaloneStage=1;ticks=0;return;}
        if(standaloneStage==1&&ticks>=40){
            require(mc.currentScreen!=null&&header(mc).forward==1,"isolated controller opens settings despite missing Pilot Seat");
            require(field(field(mc.currentScreen,"data"),"structure")==null,"invalid scan supplies real controller data without a fake hull");
            require(buttons(mc).stream().anyMatch(b->b.id==0&&!b.enabled),"incomplete craft cannot assemble");
            for(int id:new int[]{4,6})require(buttons(mc).stream().anyMatch(b->b.id==id&&b.enabled),"standalone control available: "+id);
            GuiOptionCycleRuntimeChecks.check(mc);VehicleTaxiRuntimeChecks.type(mc,(GuiTextField)field(mc.currentScreen,"channels"),"28811");mc.currentScreen.updateScreen();
            standaloneStage=2;ticks=0;return;
        }
        if(standaloneStage==2&&ticks>=40){UUID token=header(mc).token;mc.getIntegratedServer().addScheduledTask(()->{
            TileEntityVehicleController tile=(TileEntityVehicleController)world(mc).getTileEntity(at);require(player(mc).getDistanceSqToCenter(at)<=64,"standalone observer remains within settings reach: "+player(mc).getPositionVector());require(tile.getRedstoneChannel()==28811&&tile.controlsInitialized(),"standalone settings autoapply and initialize controls before seats exist");
            NBTTagCompound before=tile.writeToNBT(new NBTTagCompound());
            VehicleNetwork.Packet height=new VehicleNetwork.Packet(VehicleNetwork.HEIGHT);height.token=token;height.entity=-1;height.origin=at;height.strafe=3;VehicleService.configureHeight(player(mc),height);
            require(before.equals(tile.writeToNBT(new NBTTagCompound())),"controller token rejects forged seat Height");
            EntityPlayerMP actor=player(mc);double x=actor.posX,y=actor.posY,z=actor.posZ;actor.setPosition(x+100,y,z);
            VehicleNetwork.Packet denied=new VehicleNetwork.Packet(VehicleNetwork.SETTINGS);denied.token=token;denied.entity=-1;denied.origin=at;denied.text="28812";denied.forward=1;
            try{VehicleService.configureChannels(actor,denied);}finally{actor.setPosition(x,y,z);}
            require(before.equals(tile.writeToNBT(new NBTTagCompound())),"standalone settings reject out-of-reach packets");
        }).get();VehicleTaxiRuntimeChecks.screenshot(mc,output,"shot_vehicle_controller_incomplete_settings.png");VehicleTaxiRuntimeChecks.button(mc,1);standaloneStage=3;ticks=0;return;}
        if(standaloneStage==3&&ticks>=10){mc.getIntegratedServer().addScheduledTask(()->world(mc).setBlockToAir(at)).get();standaloneStage=4;ticks=0;}
    }
    private static void migrationChecks(WorldServer world,VehicleStructure original){
        VehicleStructure.Cell control=original.controller(),primary=original.at(original.seat);
        NBTTagCompound legacy=primary.tileData();legacy.setBoolean("TaxiMode",false);legacy.setInteger("PilotRedstoneMode",2);legacy.setInteger("PilotHeight",3);
        com.vandorlabs.redstone.ChannelData.write(legacy,com.vandorlabs.redstone.ChannelList.of(28801,28802));
        NBTTagCompound modes=new NBTTagCompound();com.vandorlabs.redstone.ChannelData.write(modes,com.vandorlabs.redstone.ChannelList.of(28803));legacy.setTag("VehicleModeChannels1",modes);
        NBTTagCompound blank=control.tileData();blank.setBoolean("VehicleControllerControls",false);
        VehicleStructure source=VehicleSeatSettings.replaceAt(original,primary.pos,legacy,BlockPos.ORIGIN);
        source=VehicleSeatSettings.replaceAt(source,control.pos,blank,BlockPos.ORIGIN);
        VehicleStructure migrated=VehicleControlSettings.migrate(source);
        TileEntityVehicleController settings=new TileEntityVehicleController();settings.readFromNBT(migrated.controller().tileData());
        require(settings.controlsInitialized()&&settings.getRedstoneMode()==2,"legacy controller controls imported once");
        require(settings.getRedstoneChannels().toString().equals("28801, 28802")&&settings.modeChannels(1).toString().equals("28803"),"legacy toggle and mode channel lists preserved");
        require(migrated.at(primary.pos).tileData().equals(legacy),"migration preserves independent selected seat height and full NBT");
        require(VehicleControlSettings.migrate(migrated)==migrated,"initialized migration is idempotent");
        settings.setTaxiMode(true);settings.setVehicleChannels(com.vandorlabs.redstone.ChannelList.of(28804),world);
        VehicleStructure edited=VehicleSeatSettings.replaceAt(migrated,control.pos,settings.writeToNBT(new NBTTagCompound()),BlockPos.ORIGIN);
        require(VehicleControlSettings.migrate(edited)==edited,"explicit controller choices never overwritten by legacy seat settings");
        TileEntityVehicleController live=(TileEntityVehicleController)world.getTileEntity(CONTROL);
        NBTTagCompound restore=live.writeToNBT(new NBTTagCompound());BlockPos origin=CONTROL.subtract(control.pos);
        try{
            // The scan saved an uninitialized controller before the player
            // changed standalone settings, then completed after those edits.
            live.readFromNBT(VehicleTileData.translated(blank,origin));
            live.setTaxiMode(true);live.setVehicleChannels(com.vandorlabs.redstone.ChannelList.of(28821),world);
            live.setModeChannels(2,com.vandorlabs.redstone.ChannelList.of(28822));live.configure(0,0,world);
            NBTTagCompound explicit=live.writeToNBT(new NBTTagCompound());
            VehicleStructure refreshed=VehicleControlSettings.migratePlaced(world,origin,source);
            require(explicit.equals(live.writeToNBT(new NBTTagCompound())),"stale uninitialized scan never overwrites explicit live controller choices");
            require(refreshed.controller().tileData().equals(VehicleTileData.translated(explicit,BlockPos.ORIGIN.subtract(origin))),"completed stale scan publishes current live controller NBT in local coordinates");
            VehicleControlSettings.migratePlaced(world,origin,source);
            require(explicit.equals(live.writeToNBT(new NBTTagCompound())),"repeated stale migration remains harmless");
        }finally{live.readFromNBT(restore);live.markDirty();world.notifyBlockUpdate(CONTROL,world.getBlockState(CONTROL),world.getBlockState(CONTROL),2);}
        EntityGroundVehicle saved=new EntityGroundVehicle(world);saved.install(source);NBTTagCompound tag=new NBTTagCompound();saved.writeToNBT(tag);
        EntityGroundVehicle loaded=new EntityGroundVehicle(world);loaded.readFromNBT(tag);
        require(loaded.structure.controller().tileData().getBoolean("VehicleControllerControls")&&loaded.structure.at(primary.pos).tileData().getInteger("PilotHeight")==3,"captured migration and independent height survive entity save");
    }
    private static void click(Minecraft mc)throws Exception{
        mc.player.setSneaking(false);mc.player.setPositionAndRotation(CONTROL.getX()+.5,CONTROL.getY(),CONTROL.getZ()-2.5,0,20);
        mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketEntityAction(mc.player,net.minecraft.network.play.client.CPacketEntityAction.Action.STOP_SNEAKING));
        mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketPlayer.PositionRotation(mc.player.posX,mc.player.posY,mc.player.posZ,0,20,false));
        mc.objectMouseOver=new RayTraceResult(new Vec3d(CONTROL).addVector(.5,.5,0),EnumFacing.NORTH,CONTROL);
        net.minecraftforge.fml.relauncher.ReflectionHelper.findMethod(Minecraft.class,"rightClickMouse","func_147121_ag").invoke(mc);
    }
    private static void panel(Minecraft mc)throws Exception{
        require(mc.currentScreen!=null&&mc.currentScreen.getClass().getName().endsWith("VehicleClient$Preview"),"controller opens integrated panel");
        require(header(mc).forward==1,"controller panel metadata distinct from seat");
        require(buttons(mc).stream().anyMatch(button->button.id==0&&button.enabled),"controller assembly action available");
        require(buttons(mc).stream().noneMatch(button->button.id==5||button.id==2),"controller has no individual seat Height or Taxi setting");
        for(int id:new int[]{4,6})require(buttons(mc).stream().anyMatch(button->button.id==id&&button.enabled),"controller owns trigger and mode channels: "+id);
    }
    public static boolean tick(Minecraft mc,File output)throws Exception{
        if(done)return true;require(++ticks<400,"timeout state="+state);
        if(standaloneStage<4){standalone(mc,output);return false;}
        if(state==0){mc.displayGuiScreen(null);VehicleControllerItemChecks.run(mc,output);mc.getIntegratedServer().addScheduledTask(()->setup(mc)).get();state=1;ticks=0;return false;}
        if(state==1&&ticks>=30){if(mc.world.getBlockState(CONTROL).getBlock()!=controller())return false;click(mc);state=2;ticks=0;return false;}
        if(state==2&&ticks>=30){panel(mc);VehicleTaxiRuntimeChecks.screenshot(mc,output,"shot_vehicle_controller_assembly.png");UUID token=header(mc).token;
            mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP actor=player(mc);NBTTagCompound before=world(mc).getTileEntity(FIRST).writeToNBT(new NBTTagCompound());
                VehicleNetwork.Packet forged=new VehicleNetwork.Packet(VehicleNetwork.SETTINGS);forged.token=token;forged.entity=-1;forged.origin=BlockPos.ORIGIN;forged.text="29999";VehicleService.configureChannels(actor,forged);
                require(before.equals(world(mc).getTileEntity(FIRST).writeToNBT(new NBTTagCompound())),"controller token cannot mutate a seat");
                double x=actor.posX,y=actor.posY,z=actor.posZ;actor.setPosition(x+100,y,z);VehicleService.confirm(actor,token);actor.setPosition(x,y,z);
                require(world(mc).getBlockState(CONTROL).getBlock()==controller(),"out-of-reach confirmation preserves controller and craft");VehicleService.previewController(actor,CONTROL);
            }).get();state=3;ticks=0;return false;
        }
        if(state==3&&ticks>=30){panel(mc);UUID token=header(mc).token;mc.getIntegratedServer().addScheduledTask(()->{
            EntityPlayerMP actor=player(mc);actor.setGameType(GameType.ADVENTURE);try{VehicleService.confirm(actor,token);}finally{actor.setGameType(GameType.CREATIVE);}
            require(world(mc).getBlockState(CONTROL).getBlock()==controller(),"protected controller conversion preserves blocks");VehicleService.previewController(actor,CONTROL);
        }).get();state=4;ticks=0;return false;}
        if(state==4&&ticks>=30){panel(mc);mc.getIntegratedServer().addScheduledTask(()->{
            int index=0;for(BlockPos at:new BlockPos[]{FIRST,SECOND}){
                EntityPlayerMP crew=crew(mc,index++==0);crew.setSneaking(false);crew.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
                ModBlocks.PILOT_SEAT.onBlockActivated(world(mc),at,world(mc).getBlockState(at),crew,EnumHand.MAIN_HAND,EnumFacing.NORTH,.5F,.3F,.5F);
                require(crew.getRidingEntity() instanceof com.vandorlabs.entity.EntityChairSeat,"world crew sits before controller assembly");
            }
        }).get();VehicleTaxiRuntimeChecks.button(mc,0);state=5;ticks=0;return false;}
        if(state==5&&ticks>=30){mc.getIntegratedServer().addScheduledTask(()->{
            EntityGroundVehicle craft=null;for(EntityGroundVehicle candidate:VehicleLookup.loaded(world(mc)))if(candidate.getDistanceSq(BASE.getX(),BASE.getY(),BASE.getZ())<400&&candidate.structure!=null&&candidate.structure.pilotSeats().size()==2)craft=candidate;
            require(craft!=null,"actual controller confirmation creates two-seat vehicle");craftId=craft.getUniqueID();
            require(world(mc).getBlockState(CONTROL).getBlock()!=controller(),"actual assembly consumed controller world cell");
            require(crew(mc,true).getRidingEntity()==craft&&crew(mc,false).getRidingEntity()==craft,"controller assembly carries already seated crew");
            require(craft.seatForPassenger(crew(mc,true)).equals(FIRST.subtract(BASE))&&craft.seatForPassenger(crew(mc,false)).equals(SECOND.subtract(BASE)),"assembly retains both crew seat identities");
            crew(mc,true).dismountRidingEntity();crew(mc,false).dismountRidingEntity();passengers(mc,craft);
        }).get();state=6;ticks=0;return false;}
        if(state==6&&ticks>=20){
            EntityGroundVehicle craft=null;for(EntityGroundVehicle candidate:VehicleLookup.loaded(mc.world))if(candidate.getUniqueID().equals(craftId))craft=candidate;
            require(craft!=null&&craft.structure!=null&&craft.structure.pilotSeats().size()==2,"client receives multi-seat snapshot");
            clientPassengerAuthority(mc,craft.structure);
            javax.imageio.ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"shot_vehicle_controller_multipilot.png"));
            mc.getIntegratedServer().addScheduledTask(()->parkCrew(mc)).get();state=7;ticks=0;return false;
        }
        if(state==7&&ticks>=20){require(mc.world.getBlockState(parkedFirst).getBlock()==ModBlocks.PILOT_SEAT&&mc.world.getBlockState(parkedSecond).getBlock()==ModBlocks.PILOT_SEAT,"client receives both restored seats");
            javax.imageio.ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"shot_vehicle_controller_parked_crew.png"));
            done=true;System.out.println("[vandorlabs][reprolab] vehicle-controller-live PASS actual-click controller-conversion zero-seat-rejected multi-seat-accepted distinct-passengers first-driver handoff input-authority occupied-seat client-passenger-authority Hover-passenger-exit NBT-mapping forged-token protected-reach screenshots");return true;
        }
        return false;
    }
    private static EntityPlayerMP fake(Minecraft mc,UUID id,String name){
        EntityPlayerMP fake=net.minecraftforge.common.util.FakePlayerFactory.get(world(mc),new com.mojang.authlib.GameProfile(id,name));
        // Transfer/dismount uses normal server teleports. A disconnected manager
        // buffers those packets while retaining the real NetHandler pose logic.
        if(fake.connection==null)fake.connection=new net.minecraft.network.NetHandlerPlayServer(mc.getIntegratedServer(),
                new net.minecraft.network.NetworkManager(net.minecraft.network.EnumPacketDirection.SERVERBOUND),fake);
        return fake;
    }
    private static EntityPlayerMP crew(Minecraft mc,boolean first){return fake(mc,first?CREW_A:CREW_B,first?"ControllerCrewA":"ControllerCrewB");}
    private static void parkCrew(Minecraft mc){
        EntityGroundVehicle craft=(EntityGroundVehicle)world(mc).getEntityFromUuid(craftId);require(craft!=null,"craft remains before occupied parking");
        BlockPos a=FIRST.subtract(BASE),b=SECOND.subtract(BASE);EntityPlayerMP first=crew(mc,true),second=crew(mc,false);
        require(craft.board(first,a)&&craft.board(second,b),"crew boards selected seats before parking");craft.stopForParking();craft.rotationYaw=90;
        VehicleRotation.Plan plan=VehicleRotation.plan(craft);int width=(int)craft.structure.bounds.maxX,depth=(int)craft.structure.bounds.maxZ;
        parkedFirst=plan.origin.add(VehicleRotation.cell(a,width,depth,1));parkedSecond=plan.origin.add(VehicleRotation.cell(b,width,depth,1));
        try{VehicleTransfer.park(world(mc),craft,plan.origin,player(mc));}catch(Exception failure){throw new IllegalStateException("occupied parking",failure);}
        require(first.getRidingEntity() instanceof com.vandorlabs.entity.EntityChairSeat&&second.getRidingEntity() instanceof com.vandorlabs.entity.EntityChairSeat,"all parked crew remount world seats");
        require(((com.vandorlabs.entity.EntityChairSeat)first.getRidingEntity()).getChairPos().equals(parkedFirst)&&((com.vandorlabs.entity.EntityChairSeat)second.getRidingEntity()).getChairPos().equals(parkedSecond),"cardinal parking maps every passenger to the corresponding rotated seat");
        first.dismountRidingEntity();second.dismountRidingEntity();
    }
    private static void passengers(Minecraft mc,EntityGroundVehicle craft){
        EntityPlayerMP first=player(mc),second=fake(mc,UUID.fromString("a829b83f-cf64-4e4a-809a-c3d8a9356b22"),"PilotPassenger");
        BlockPos a=FIRST.subtract(BASE),b=SECOND.subtract(BASE);
        NBTTagCompound enabled=craft.structure.at(a).tileData();enabled.setBoolean("TaxiMode",true);craft.updateSettings(VehicleSeatSettings.replaceAt(craft.structure,a,enabled,BlockPos.ORIGIN));
        require(VehicleTaxiMode.enabled(craft.structure,a)&&!VehicleTaxiMode.enabled(craft.structure,b),"seats retain independent automatic Taxi choices");
        require(craft.board(first,a)&&craft.board(second,b),"two people occupy two chosen seats");
        require(craft.getControllingPassenger()==first&&craft.seatForPassenger(first).equals(a)&&craft.seatForPassenger(second).equals(b),"first seated controls; mappings distinct");
        craft.updatePassenger(first);craft.updatePassenger(second);require(first.getPositionVector().squareDistanceTo(second.getPositionVector())>1,"passengers have distinct visible positions");
        EntityPlayerMP third=fake(mc,UUID.fromString("3ce983a8-bd1b-4ed8-87d3-399c905c6464"),"PilotThird");require(!craft.board(third,a),"occupied seat cannot be reused");
        craft.stopForParking();craft.input(second,23,(byte)1,(byte)0,false);craft.onUpdate();require(craft.acknowledgedInput()==-1,"second passenger cannot submit steering");
        craft.input(first,7,(byte)1,(byte)0,false);craft.onUpdate();require(craft.acknowledgedInput()==7,"first seated passenger drives");
        NBTTagCompound saved=new NBTTagCompound();craft.writeToNBT(saved);EntityGroundVehicle reload=new EntityGroundVehicle(world(mc));reload.readFromNBT(saved);
        require(reload.seatForPassenger(first).equals(a)&&reload.seatForPassenger(second).equals(b)&&reload.structure.pilotSeats().size()==2,"entity NBT retains passenger UUID-to-seat assignments");
        BlockPos shift=new BlockPos(1,1,1);List<VehicleStructure.Cell> rebased=new ArrayList<>();
        for(VehicleStructure.Cell cell:reload.structure.cells)rebased.add(new VehicleStructure.Cell(cell.pos.add(shift),cell.state,cell.hasTileData()?VehicleTileData.translated(cell.tileData(),shift):null));
        reload.install(new VehicleStructure(rebased,reload.structure.seat.add(shift),reload.structure.facing,reload.structure.gearCount));
        require(reload.seatForPassenger(first).equals(a.add(shift))&&reload.seatForPassenger(second).equals(b.add(shift)),"hull rebasing retains both passenger seat identities");
        first.dismountRidingEntity();require(craft.getControllingPassenger()==second&&craft.seatForPassenger(second).equals(b),"control hands to remaining passenger without moving seats");
        craft.input(first,24,(byte)1,(byte)0,false);craft.onUpdate();require(craft.acknowledgedInput()==-1,"departed driver loses control");
        craft.input(second,8,(byte)1,(byte)0,false);craft.onUpdate();require(craft.acknowledgedInput()==8,"new first passenger can drive after handoff");
        second.dismountRidingEntity();require(craft.getPassengers().isEmpty(),"all passenger assignments release on exit");
        require(b.equals(craft.taxiReturnSeat)&&!craft.taxiReturnPending,"final controlling pilot's disabled seat prevents autopark despite primary seat enabled");
        NBTTagCompound last=new NBTTagCompound();craft.writeToNBT(last);EntityGroundVehicle remembered=new EntityGroundVehicle(world(mc));remembered.readFromNBT(last);
        require(b.equals(remembered.taxiReturnSeat),"remembered controlling seat survives save for pending parking retries");craft.stopForParking();
        hoverPassengerExit(mc,craft.structure);
    }
    /** Exercise the actual client tick with a local rider behind another pilot. */
    private static void clientPassengerAuthority(Minecraft mc,VehicleStructure structure)throws Exception{
        require(mc.player.getRidingEntity()==null,"client authority fixture starts unmounted");
        EntityGroundVehicle local=new EntityGroundVehicle(mc.world);local.install(structure);local.setPosition(BASE.getX(),BASE.getY()+8,BASE.getZ());
        net.minecraft.client.entity.EntityOtherPlayerMP pilot=new net.minecraft.client.entity.EntityOtherPlayerMP(mc.world,new com.mojang.authlib.GameProfile(CREW_A,"ClientFirstPilot"));
        final int id=-20261015;require(mc.world.getEntityByID(id)==null,"isolated client fixture entity id free");
        mc.world.addEntityToWorld(id,local);
        try{
            require(pilot.startRiding(local)&&mc.player.startRiding(local),"client two-seat passenger fixture mounts");
            local.setLocalDriver(true);
            VehicleClient handler=new VehicleClient();
            handler.tick(new net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent(net.minecraftforge.fml.common.gameevent.TickEvent.Phase.START));
            require(local.getControllingPassenger()==pilot&&!local.localDriver&&field(local,"nextPrediction")==null,"second client passenger neither owns prediction nor submits predicted input");
            pilot.dismountRidingEntity();
            handler.tick(new net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent(net.minecraftforge.fml.common.gameevent.TickEvent.Phase.START));
            require(local.getControllingPassenger()==mc.player&&local.localDriver&&field(local,"nextPrediction")!=null,"client handoff enables prediction only for new first pilot");
        }finally{
            if(mc.player.getRidingEntity()==local)mc.player.dismountRidingEntity();
            if(pilot.getRidingEntity()==local)pilot.dismountRidingEntity();
            mc.world.removeEntityFromWorld(id);local.setDead();
        }
    }
    private static void hoverPassengerExit(Minecraft mc,VehicleStructure original){
        List<VehicleStructure.Cell> cells=new ArrayList<>(original.cells);
        String[] lifts={"antigravity_plate","repulsor_array","vertical_hover_thruster"};
        for(int i=0;i<lifts.length;i++)cells.add(new VehicleStructure.Cell(new BlockPos(7+i,2,1),Block.getBlockFromName("vandorlabs:"+lifts[i]).getDefaultState(),null));
        EntityGroundVehicle hover=new EntityGroundVehicle(world(mc));hover.install(new VehicleStructure(cells,original.seat,original.facing,original.gearCount));hover.setPosition(BASE.getX(),BASE.getY()+8,BASE.getZ());
        EntityPlayerMP pilot=crew(mc,true),passenger=crew(mc,false);
        try{
            require(hover.setHover(true)&&hover.board(pilot,FIRST.subtract(BASE))&&hover.board(passenger,SECOND.subtract(BASE)),"two-seat Hover exit fixture mounts");
            hover.autopilotControls((byte)1,(byte)-1,false);hover.flightInput(pilot,(byte)1,false);
            hover.driveSpeed=.2;hover.motionX=.1;hover.motionY=.08;hover.motionZ=.06;hover.hoverTarget=hover.posY+2;
            double target=hover.hoverTarget;hover.input(pilot,97,(byte)1,(byte)-1,false);
            hover.exitFlight(passenger);
            require(passenger.getRidingEntity()==null&&hover.getControllingPassenger()==pilot,"explicit Hover passenger exit preserves active pilot");
            require(hover.hoverTarget==target&&hover.driveSpeed==.2&&hover.motionX==.1&&hover.motionY==.08&&hover.motionZ==.06,"passenger exit does not stop motion or latch pilot height");
            require(((Byte)field(hover,"forward"))==1&&((Byte)field(hover,"strafe"))==-1&&((Byte)field(hover,"vertical"))==1&&!((Boolean)field(hover,"brake")),"passenger exit preserves pilot drive and vertical controls");
            hover.onUpdate();require(hover.acknowledgedInput()==97,"passenger exit retains queued active-pilot input");
        }catch(Exception failure){throw new IllegalStateException("Hover passenger exit regression",failure);}
        finally{hover.transferInProgress=true;if(passenger.getRidingEntity()==hover)passenger.dismountRidingEntity();if(pilot.getRidingEntity()==hover)pilot.dismountRidingEntity();hover.setDead();}
    }
    private VehicleControllerRuntimeChecks(){}
}
