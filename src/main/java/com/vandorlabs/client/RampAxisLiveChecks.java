package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.ramp.RampGeometry;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import java.io.File;
import java.util.*;

/** Actual source capture, modes, recovery, snapshots and dialog packets for the Y profile. */
final class RampAxisLiveChecks {
    private static final BlockPos ROOT=new BlockPos(740,100,420);
    private static int stage,ticks,cases,renderMode,mountIndex;
    private static final EnumFacing[] MOUNTS={EnumFacing.EAST,EnumFacing.SOUTH,EnumFacing.WEST,EnumFacing.NORTH,EnumFacing.UP,EnumFacing.DOWN};
    private static String fixture="setup";
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("ramp axis "+fixture+": "+why);}
    private static void clear(WorldServer world){
        TileEntity raw=world.getTileEntity(ROOT);if(raw instanceof TileEntityRampController)((TileEntityRampController)raw).recover(true);
        for(BlockPos p:BlockPos.getAllInBox(ROOT.add(-8,-5,-8),ROOT.add(8,9,8))){world.getChunkFromBlockCoords(p);world.setBlockToAir(p);}
    }
    private static TileEntityRampController place(WorldServer world,EntityPlayerMP actor,BlockRampController block,EnumFacing scan){
        EnumFacing direction=scan.getAxis()==EnumFacing.Axis.Y?EnumFacing.EAST:scan;
        world.setBlockState(ROOT,block.getDefaultState().withProperty(BlockRampController.FACING,direction).withProperty(BlockRampController.ACTIVE_FACE,scan),3);
        TileEntityRampController tile=(TileEntityRampController)world.getTileEntity(ROOT);tile.setOwner(actor);tile.setPlacementDirection(direction);return tile;
    }
    private static List<BlockPos> panel(WorldServer world,EnumFacing face,IBlockState material){
        List<BlockPos> sources=new ArrayList<>();for(int row=0;row<3;row++)for(int width=0;width<2;width++){BlockPos p=ROOT.offset(face).up(row).offset(face.rotateY(),width);world.setBlockState(p,material,3);sources.add(p);}return sources;
    }
    private static void vehicle(WorldServer world,TileEntityRampController ramp){
        BlockPos offset=ROOT.add(-4,-3,-4);List<com.vandorlabs.vehicle.VehicleStructure.Cell> cells=new ArrayList<>();Set<BlockPos> positions=new HashSet<>(ramp.vehicleCells());positions.add(ROOT);
        for(BlockPos p:positions){TileEntity raw=world.getTileEntity(p);NBTTagCompound tag=raw==null?null:com.vandorlabs.vehicle.VehicleEscalators.paused(com.vandorlabs.vehicle.VehicleTileData.translated(raw.writeToNBT(new NBTTagCompound()),new BlockPos(-offset.getX(),-offset.getY(),-offset.getZ())));cells.add(new com.vandorlabs.vehicle.VehicleStructure.Cell(p.subtract(offset),world.getBlockState(p),tag));}
        BlockPos seat=new BlockPos(1,1,1);cells.add(new com.vandorlabs.vehicle.VehicleStructure.Cell(seat,ModBlocks.PILOT_SEAT.getDefaultState(),null));
        com.vandorlabs.vehicle.VehicleStructure craft=new com.vandorlabs.vehicle.VehicleStructure(cells,seat,EnumFacing.NORTH,3);
        com.vandorlabs.vehicle.VehicleWorld view=new com.vandorlabs.vehicle.VehicleWorld(world,craft);
        for(int turn=0;turn<4;turn++){com.vandorlabs.vehicle.VehicleStructure rotated=com.vandorlabs.vehicle.VehicleRotation.rotate(craft,view,turn);com.vandorlabs.vehicle.VehicleWorld result=new com.vandorlabs.vehicle.VehicleWorld(world,rotated);for(com.vandorlabs.vehicle.VehicleStructure.Cell cell:rotated.cells)com.vandorlabs.vehicle.MovingBlockSupport.requireExactTileData(cell,result.getTileEntity(cell.pos));}
    }
    private static void carriedEndpoints(WorldServer world,TileEntityRampController ramp,List<BlockPos> sources){
        BlockPos offset=ROOT.add(-4,-3,-4);List<com.vandorlabs.vehicle.VehicleStructure.Cell> cells=new ArrayList<>();List<BlockPos> positions=new ArrayList<>(sources);positions.add(ROOT);
        for(BlockPos p:positions){TileEntity raw=world.getTileEntity(p);NBTTagCompound tag=raw==null?null:com.vandorlabs.vehicle.VehicleTileData.translated(raw.writeToNBT(new NBTTagCompound()),new BlockPos(-offset.getX(),-offset.getY(),-offset.getZ()));if(p.equals(ROOT))tag=com.vandorlabs.vehicle.VehicleEscalators.paused(tag);cells.add(new com.vandorlabs.vehicle.VehicleStructure.Cell(p.subtract(offset),world.getBlockState(p),tag));}
        BlockPos seat=new BlockPos(1,1,1),relative=ROOT.subtract(offset).subtract(seat);cells.add(new com.vandorlabs.vehicle.VehicleStructure.Cell(seat,ModBlocks.PILOT_SEAT.getDefaultState(),null));
        com.vandorlabs.vehicle.EntityGroundVehicle craft=new com.vandorlabs.vehicle.EntityGroundVehicle(world);
        try{craft.install(new com.vandorlabs.vehicle.VehicleStructure(cells,seat,EnumFacing.NORTH,3));craft.setPosition(ROOT.getX()+48,ROOT.getY()+16,ROOT.getZ()+48);craft.view.origin=new BlockPos(craft);for(BlockPos p:BlockPos.getAllInBox(new BlockPos(craft).add(-16,-8,-16),new BlockPos(craft).add(16,16,16)))world.getChunkFromBlockCoords(p);
            for(boolean deployed:new boolean[]{true,false,true,false}){require(com.vandorlabs.vehicle.VehicleRamps.signal(craft,craft.structure.seat.add(relative),deployed),"carried Y endpoint refused "+deployed);TileEntityRampController carried=(TileEntityRampController)craft.view.getTileEntity(craft.structure.seat.add(relative));require(carried.verticalLayout&&carried.isOpen()==deployed&&carried.area()==6,"carried Y endpoint lost its layout or source ownership");}
        }finally{craft.signals.close();craft.setDead();}
    }
    private static void riders(WorldServer world,EntityPlayerMP actor){
        for(IBlockState material:new IBlockState[]{Blocks.STONE.getDefaultState(),Blocks.OAK_STAIRS.getDefaultState()}){
            clear(world);TileEntityRampController ramp=place(world,actor,(BlockRampController)ModBlocks.PROGRAMMABLE_RAMP,EnumFacing.EAST);panel(world,EnumFacing.EAST,material);
            require(ramp.configureHalfOffsets(actor,0,4,16,true,false,true,EnumFacing.EAST,0,false,1,true,false,0,0,false,true),"rider fixture configure");
            com.vandorlabs.ramp.RampGeometry.Box top=new com.vandorlabs.ramp.RampGeometry.Box(0,0,0,1,1,1);
            if(com.vandorlabs.compat.RampStairs.supported(material))for(com.vandorlabs.ramp.RampGeometry.Box shape:com.vandorlabs.compat.RampStairs.boxes(world,material))if(shape.minY>top.minY)top=shape;
            double x=ROOT.getX()+1+(top.minX+top.maxX)/2,y=ROOT.getY()+2+top.maxY,z=ROOT.getZ()+(top.minZ+top.maxZ)/2;
            net.minecraft.entity.item.EntityArmorStand rider=new net.minecraft.entity.item.EntityArmorStand(world,x,y,z);rider.setNoGravity(true);rider.onGround=true;require(world.spawnEntity(rider),"native rider spawn");
            try{world.setBlockState(ROOT.west(),Blocks.REDSTONE_BLOCK.getDefaultState(),3);ramp.updatePower();for(int t=1,total=ramp.durationTicks()+1;t<=total;t++)ControllerRuntimeChecks.elapsed(ramp,t);require(!ramp.error&&Math.abs(rider.posX-x-2)<1e-6&&Math.abs(rider.posY-y)<1e-6&&Math.abs(rider.posZ-z)<1e-6,"Y lift must carry rider without changing native height/lateral offset");
                world.setBlockToAir(ROOT.west());for(int t=1,total=ramp.durationTicks()+1;t<=total;t++)ControllerRuntimeChecks.elapsed(ramp,t);require(!ramp.attached()&&Math.abs(rider.posX-x)<1e-6,"Y lift must return rider and native source blocks");
                BlockPos obstacle=new BlockPos(x+2,y,z);world.setBlockState(obstacle,Blocks.DIAMOND_BLOCK.getDefaultState(),3);world.setBlockState(ROOT.west(),Blocks.REDSTONE_BLOCK.getDefaultState(),3);ramp.updatePower();NBTTagCompound jump=ramp.writeToNBT(new NBTTagCompound());jump.setLong("StartTick",world.getTotalWorldTime()-ramp.durationTicks()-1);jump.setLong("LastStepTick",jump.getLong("StartTick"));ControllerRuntimeChecks.restoreState(ramp,jump);ramp.scheduledTick();require(ramp.error&&Math.abs(rider.posX-x)<1e-6,"blocked rider must not be partly transported");require(world.getBlockState(obstacle).getBlock()==Blocks.DIAMOND_BLOCK,"rider collision must preserve obstacle");world.setBlockToAir(obstacle);world.setBlockToAir(ROOT.west());ramp.recover(true);
            }finally{rider.setDead();ramp.recover(true);}
        }
        clear(world);System.out.println("[vandorlabs][reprolab] ramp-axis-riders PASS native cube/stair lift transport reversal exact lateral/height blocked-rider atomicity");
    }
    private static void packets(){
        int legacyBytes=0;
        for(int variant=0;variant<3;variant++){
            com.vandorlabs.network.MessageRampController packet=com.vandorlabs.network.MessageRampController.halfOffsets(ROOT,-3,7,4,true,false,false,EnumFacing.EAST,12,0,false,1,true).withChannels(com.vandorlabs.redstone.ChannelList.of(12,87)).withAnalogDeployment(true).withInterpolation(2);
            if(variant>0)packet.withVerticalLayout(variant==2);
            io.netty.buffer.ByteBuf bytes=io.netty.buffer.Unpooled.buffer(),again=io.netty.buffer.Unpooled.buffer();
            try{packet.toBytes(bytes);byte[] before=new byte[bytes.readableBytes()];bytes.getBytes(bytes.readerIndex(),before);if(variant==0)legacyBytes=before.length;else require(before.length==legacyBytes+5,"axis extension must be exactly five bytes");
                com.vandorlabs.network.MessageRampController decoded=new com.vandorlabs.network.MessageRampController();decoded.fromBytes(bytes);require(!bytes.isReadable(),"packet left trailing bytes");decoded.toBytes(again);byte[] after=new byte[again.readableBytes()];again.readBytes(after);require(Arrays.equals(before,after),"axis/analog/channels/signed offsets packet round trip");
            }finally{bytes.release();again.release();}
        }
        System.out.println("[vandorlabs][reprolab] ramp-axis-packets PASS legacy="+legacyBytes+" axis="+(legacyBytes+5)+" bytes exact round trips legacy/false/true channels analog signed-half-offsets interpolation");
    }
    private static void filledMaterials(WorldServer world,EntityPlayerMP actor){
        actor.setGameType(net.minecraft.world.GameType.CREATIVE);actor.setPosition(ROOT.getX()+.5,ROOT.getY()+1,ROOT.getZ()+.5);int assertions=0;
        for(EnumFacing face:EnumFacing.HORIZONTALS)for(boolean fill:new boolean[]{false,true}){
            clear(world);TileEntityRampController ramp=place(world,actor,(BlockRampController)ModBlocks.PROGRAMMABLE_RAMP,face);
            List<BlockPos> sources=panel(world,face,ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState());Map<BlockPos,int[]> choices=new HashMap<>();
            for(int index=0;index<sources.size();index++){BlockPos at=sources.get(index);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(at);int[] textures=new int[6];for(int f=0;f<6;f++)textures[f]=9+index*6+f;tile.setHousingTexture(9);tile.setFaceTextures(new FaceTextures(true,textures));choices.put(at,textures);}
            require(ramp.configureHalfOffsets(actor,0,4,8,true,false,false,face,0,fill,1,false,false,0,0,false,true),"mixed source configure "+ramp.status);
            ramp.setChannelSignal(true);ControllerRuntimeChecks.elapsed(ramp,ramp.durationTicks()+1);require(ramp.attached()&&!ramp.error,"mixed source deploy "+ramp.status);
            for(BlockPos at:ramp.vehicleCells()){TileEntityControlledRamp part=(TileEntityControlledRamp)world.getTileEntity(at);for(com.vandorlabs.ramp.RampGeometry.Box box:part.geometry(0)){
                int y=(int)Math.floor(at.getY()+(box.minY+box.maxY)*.5),width=face.getAxis()==EnumFacing.Axis.X?(int)Math.floor(at.getZ()+(box.minZ+box.maxZ)*.5):(int)Math.floor(at.getX()+(box.minX+box.maxX)*.5);
                BlockPos original=sources.stream().filter(p->p.getY()==y&&(face.getAxis()==EnumFacing.Axis.X?p.getZ():p.getX())==width).findFirst().orElseThrow(()->new IllegalStateException("missing expected material lane"));
                for(EnumFacing normal:EnumFacing.values()){int expected=choices.get(original)[normal.getIndex()];int actual=part.sourceHousing(box,0,normal);require(actual==expected,"Y fill selected wrong source face="+face+" fill="+fill+" cell="+at+" original="+original+" normal="+normal+" expected="+expected+" actual="+actual);assertions++;}
            }}
            require(ramp.recover(false),"mixed source recovery");
        }
        clear(world);System.out.println("[vandorlabs][reprolab] ramp-axis-materials PASS assertions="+assertions+" six distinct faces per source all heights/columns four facings filled/ordinary");
    }
    private static void matrix(WorldServer world,EntityPlayerMP actor){
        packets();
        filledMaterials(world,actor);
        actor.setGameType(net.minecraft.world.GameType.CREATIVE);actor.capabilities.allowFlying=true;actor.capabilities.isFlying=true;actor.sendPlayerAbilities();actor.setPosition(ROOT.getX()+.5,ROOT.getY()+1,ROOT.getZ()+.5);
        IBlockState[] materials={Blocks.STONE.getDefaultState(),Blocks.STONE_SLAB.getStateFromMeta(0),Blocks.STONE_SLAB.getStateFromMeta(8),Blocks.OAK_STAIRS.getDefaultState(),ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState(),Blocks.GLASS.getDefaultState()};
        for(BlockRampController block:new BlockRampController[]{(BlockRampController)ModBlocks.PROGRAMMABLE_RAMP,(BlockRampController)ModBlocks.PROGRAMMABLE_RAMP_SLAB})for(EnumFacing face:EnumFacing.HORIZONTALS)for(int mode=0;mode<7;mode++)for(int material=0;material<materials.length;material++){
            fixture=block.getRegistryName()+"/"+face+"/mode"+mode+"/material"+material;clear(world);TileEntityRampController ramp=place(world,actor,block,face);List<BlockPos> sources=panel(world,face,materials[material]);
            boolean lift=mode==2||mode==3,fill=mode<4&&(mode&1)!=0,moat=mode==6;int escalator=mode==4?1:mode==5?2:0;
            require(ramp.configureHalfOffsets(actor,0,moat?-2:4,8,true,false,lift,face,0,fill,1,true,false,0,escalator,moat,true),"configure "+ramp.status);
            world.setBlockState(ROOT.offset(face.getOpposite()),Blocks.REDSTONE_BLOCK.getDefaultState(),3);ramp.updatePower();ControllerRuntimeChecks.elapsed(ramp,ramp.durationTicks()+1);
            require(!ramp.error&&ramp.attached()&&!ramp.isMoving(),"deploy "+ramp.status);if(escalator!=0){require(ramp.isEscalatorRunning(),"powered nearby Y escalator must run");}
            require(ramp.area()==6,"capture exactly the vertical panel");require(new HashSet<>(ramp.vehicleSources()).equals(new HashSet<>(sources)),"wrong source plane");
            int visible=0;for(BlockPos p:ramp.vehicleCells()){
                require(world.getTileEntity(p) instanceof TileEntityControlledRamp,"missing generated cell "+p);TileEntityControlledRamp part=(TileEntityControlledRamp)world.getTileEntity(p);require(part.verticalLayout,"part lost Y axis");visible+=part.geometry(0).size();NBTTagCompound tag=part.writeToNBT(new NBTTagCompound());TileEntityControlledRamp reloaded=new TileEntityControlledRamp();reloaded.setWorld(world);reloaded.setPos(p);reloaded.readFromNBT(tag);require(reloaded.verticalLayout&&reloaded.geometry(0).size()==part.geometry(0).size(),"part reload geometry differs at "+p+" old="+part.geometry(0).size()+" new="+reloaded.geometry(0).size()+" origins="+part.origins.size()+" tag="+tag);
            }
            require(visible>0,"empty deployed shape");require(ramp.writeToNBT(new NBTTagCompound()).getBoolean("RampVerticalLayout"),"controller did not save Y axis");
            if(block==ModBlocks.PROGRAMMABLE_RAMP&&face==EnumFacing.EAST&&material==0)vehicle(world,ramp);
            ItemStack picked=block.getPickBlock(world.getBlockState(ROOT),null,world,ROOT,actor);
            world.setBlockToAir(ROOT.offset(face.getOpposite()));require(ramp.recover(false),"recover "+ramp.status);for(BlockPos p:sources)require(world.getBlockState(p).equals(materials[material]),"original source not restored at "+p);
            if(block==ModBlocks.PROGRAMMABLE_RAMP&&face==EnumFacing.EAST&&material==0&&escalator==0)carriedEndpoints(world,ramp,sources);
            if(mode==0&&material==0){
                clear(world);TileEntityRampController placed=place(world,actor,block,face.getOpposite());block.onBlockPlacedBy(world,ROOT,world.getBlockState(ROOT),actor,picked);require(placed.verticalLayout&&placed.rampDirection()==face.getOpposite(),"configured Y item must face outward from its new mount");
                NBTTagCompound copied=com.vandorlabs.items.ProgrammableSettings.capture(world,ROOT);require(copied.getBoolean(com.vandorlabs.items.ProgrammableSettings.RAMP_VERTICAL_LAYOUT),"Duplifier capture omitted Y layout");placed.verticalLayout=false;require(com.vandorlabs.items.ProgrammableSettings.apply(world,ROOT,copied,actor)&&placed.verticalLayout,"Duplifier apply omitted Y layout");
            }
            cases++;
        }
        // Mid-panel seeds include blocks above and below, without spreading through depth.
        clear(world);TileEntityRampController ramp=place(world,actor,(BlockRampController)ModBlocks.PROGRAMMABLE_RAMP,EnumFacing.EAST);for(int y=-2;y<=2;y++)world.setBlockState(ROOT.east().up(y),Blocks.STONE.getDefaultState(),3);
        require(ramp.configureHalfOffsets(actor,0,2,16,true,false,false,EnumFacing.EAST,0,false,1,true,false,0,0,false,true),"middle seed configure");ramp.setChannelSignal(true);ControllerRuntimeChecks.elapsed(ramp,ramp.durationTicks()+1);require(ramp.area()==5&&!ramp.error,"middle seed includes both Y directions");
        BlockPos blockedSource=ROOT.east().up(2);require(world.isAirBlock(blockedSource),"test needs a vacated source");world.setBlockState(blockedSource,Blocks.DIAMOND_BLOCK.getDefaultState(),3);long beforeRevision=ramp.configurationRevision();
        require(!ramp.configureHalfOffsets(actor,0,2,16,true,false,false,EnumFacing.EAST,0,false,1,true,false,0,0,false,false)&&ramp.verticalLayout&&ramp.configurationRevision()==beforeRevision,"blocked axis edit must retain old layout and journal");require(world.getBlockState(blockedSource).getBlock()==Blocks.DIAMOND_BLOCK,"blocked reset erased replacement");world.setBlockToAir(blockedSource);ramp.recover(true);
        riders(world,actor);
        for(EnumFacing scan:new EnumFacing[]{EnumFacing.UP,EnumFacing.DOWN}){clear(world);ramp=place(world,actor,(BlockRampController)ModBlocks.PROGRAMMABLE_RAMP,scan);long revision=ramp.configurationRevision();require(!ramp.configureHalfOffsets(actor,0,2,16,true,false,false,EnumFacing.EAST,0,false,1,true,false,0,0,false,true)&&revision==ramp.configurationRevision(),"top/bottom mount must refuse Y");}
        System.out.println("[vandorlabs][reprolab] ramp-axis-world PASS cases="+cases+" full/slab all4faces all7modes six-source-materials exact-source recovery part-reload four-rotation-exact-NBT middle-seed and mount restrictions");clear(world);
    }
    private static List<GuiButton> buttons(GuiRampController gui){return net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(net.minecraft.client.gui.GuiScreen.class,gui,"buttonList","field_146292_n");}
    static boolean tick(Minecraft mc,File output)throws Exception{
        mc.gameSettings.pauseOnLostFocus=false;if(++ticks<30)return false;require(ticks<360,"dialog timeout stage="+stage+" screen="+mc.currentScreen);
        if(stage==0){mc.displayGuiScreen(null);mc.playerController.setGameType(net.minecraft.world.GameType.CREATIVE);mc.getIntegratedServer().addScheduledTask(()->{WorldServer world=mc.getIntegratedServer().getWorld(0);EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);matrix(world,actor);place(world,actor,(BlockRampController)ModBlocks.PROGRAMMABLE_RAMP,EnumFacing.EAST);actor.connection.setPlayerLocation(ROOT.getX()+.5,ROOT.getY()+1,ROOT.getZ()+3,180,0);actor.inventory.currentItem=0;actor.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,new ItemStack(com.vandorlabs.items.ModItems.CONFIGURIZER));actor.inventoryContainer.detectAndSendChanges();}).get();fixture="dialog";stage++;ticks=0;return false;}
        if(stage==1){if(!(mc.world.getTileEntity(ROOT) instanceof TileEntityRampController)||BlockRampController.scanFacing(mc.world.getBlockState(ROOT))!=MOUNTS[mountIndex]||mc.player.getDistanceSq(ROOT.getX()+.5,ROOT.getY()+1,ROOT.getZ()+3)>.1)return false;mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);actor.openGui(com.vandorlabs.VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_RAMP_CONTROLLER,actor.world,ROOT.getX(),ROOT.getY(),ROOT.getZ());}).get();stage++;ticks=0;return false;}
        if(stage==2){if(!(mc.currentScreen instanceof GuiRampController))return false;GuiRampController gui=(GuiRampController)mc.currentScreen;gui.actionPerformed(buttons(gui).stream().filter(b->b.id==24).findFirst().get());stage++;ticks=0;return false;}
        if(stage==3){boolean ready=mc.getIntegratedServer().callFromMainThread(()->((TileEntityRampController)mc.getIntegratedServer().getWorld(0).getTileEntity(ROOT)).selectedAxis()==(MOUNTS[mountIndex].getAxis()==EnumFacing.Axis.Y?EnumFacing.Axis.Z:EnumFacing.Axis.Y)).get();if(!ready)return false;GuiRampController gui=(GuiRampController)mc.currentScreen;for(int[] size:new int[][]{{320,240},{480,320}}){gui.setWorldAndResolution(mc,size[0],size[1]);javax.imageio.ImageIO.write(DialogRenderCapture.image(mc,gui,size[0],size[1]),"png",new File(output,"ramp-axis-dialog-"+MOUNTS[mountIndex].getName()+"-"+size[0]+".png"));}GuiButton button=buttons(gui).stream().filter(b->b.id==24).findFirst().get();require(button.displayString.equals("Axis: "+(MOUNTS[mountIndex].getAxis()==EnumFacing.Axis.Y?"Z":"Y")),"selected axis caption");gui.mouseClicked(button.x+4,button.y+4,1);stage++;ticks=0;return false;}
        if(stage==4){boolean ready=mc.getIntegratedServer().callFromMainThread(()->((TileEntityRampController)mc.getIntegratedServer().getWorld(0).getTileEntity(ROOT)).selectedAxis()==(MOUNTS[mountIndex].getAxis()==EnumFacing.Axis.Y?EnumFacing.Axis.X:MOUNTS[mountIndex].getAxis())).get();if(!ready)return false;require(buttons((GuiRampController)mc.currentScreen).stream().anyMatch(b->b.id==24&&b.displayString.equals("Axis: "+(MOUNTS[mountIndex].getAxis()==EnumFacing.Axis.Y?"X":MOUNTS[mountIndex].getAxis().name()))),"right-click restores mounting axis");mc.player.closeScreen();if(++mountIndex<MOUNTS.length){mc.getIntegratedServer().addScheduledTask(()->{WorldServer world=mc.getIntegratedServer().getWorld(0);EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);clear(world);place(world,actor,(BlockRampController)ModBlocks.PROGRAMMABLE_RAMP,MOUNTS[mountIndex]);}).get();stage=1;}else stage=5;ticks=0;return false;}
        if(stage==5){mc.gameSettings.hideGUI=true;mc.gameSettings.fovSetting=50;mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);mc.getIntegratedServer().addScheduledTask(()->{WorldServer world=mc.getIntegratedServer().getWorld(0);EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);clear(world);actor.setPosition(ROOT.getX()+.5,ROOT.getY()+1,ROOT.getZ()+3);actor.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,ItemStack.EMPTY);actor.inventoryContainer.detectAndSendChanges();TileEntityRampController ramp=place(world,actor,(BlockRampController)ModBlocks.PROGRAMMABLE_RAMP,EnumFacing.EAST);panel(world,EnumFacing.EAST,(renderMode==1?Blocks.IRON_BLOCK:Blocks.STONE).getDefaultState());require(ramp.configureHalfOffsets(actor,0,renderMode==6?-2:6,8,true,false,renderMode==2||renderMode==3,EnumFacing.EAST,0,renderMode<4&&(renderMode&1)!=0,1,true,false,0,renderMode==4?1:renderMode==5?2:0,renderMode==6,true),"render fixture configure "+ramp.status);world.setBlockState(ROOT.west(),Blocks.REDSTONE_BLOCK.getDefaultState(),3);ramp.updatePower();ControllerRuntimeChecks.elapsed(ramp,ramp.durationTicks()+1);require(!ramp.error,"render fixture deploy "+ramp.status);actor.connection.setPlayerLocation(ROOT.getX()+7,ROOT.getY()+4,ROOT.getZ()+6,138,30);}).get();stage=6;ticks=0;return false;}
        if(stage==6){if(ticks<60)return false;TileEntity raw=mc.world.getTileEntity(ROOT);if(!(raw instanceof TileEntityRampController)||!((TileEntityRampController)raw).verticalLayout||!((TileEntityRampController)raw).attached())return false;
            if(renderMode==4||renderMode==5)require(mc.getIntegratedServer().callFromMainThread(()->Math.abs(((TileEntityRampController)mc.getIntegratedServer().getWorld(0).getTileEntity(ROOT)).beltDistance(0))>0).get(),"native scheduled Y belt did not advance");
            javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"ramp-axis-mode-"+renderMode+".png"));if(renderMode==1){int vertices=0;for(BlockPos at:BlockPos.getAllInBox(ROOT.add(-4,-4,-4),ROOT.add(8,8,8))){net.minecraft.tileentity.TileEntity partTile=mc.world.getTileEntity(at);if(partTile instanceof TileEntityControlledRamp&&((TileEntityControlledRamp)partTile).source.getBlock()==Blocks.IRON_BLOCK)vertices+=RampAtlasBoundsChecks.check(mc,(TileEntityControlledRamp)partTile);}require(vertices>0,"native iron fill mesh coverage");System.out.println("[vandorlabs][reprolab] ramp-iron-atlas PASS vertices="+vertices+" all emitted UVs inside source sprite");}renderMode++;stage=renderMode<7?5:7;ticks=0;return false;}
        if(stage==7){mc.getIntegratedServer().addScheduledTask(()->{WorldServer world=mc.getIntegratedServer().getWorld(0);EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);clear(world);ControllerRuntimeChecks.run(world,actor);RampAnalogRuntimeChecks.run(world,actor);}).get();System.out.println("[vandorlabs][reprolab] ramp-axis-live PASS actual-dialog all6mounts axis X/Y Z/Y X/Z left/right clicks packets compact/wide seven rendered modes native running belts and legacy controller/analog regressions");return true;}
        return false;
    }
    private RampAxisLiveChecks(){}
}
