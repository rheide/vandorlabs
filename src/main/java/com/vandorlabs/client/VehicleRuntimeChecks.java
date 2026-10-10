package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.items.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;

/** Real server-world round trips, collision/control contracts and client texture rendering. */
public final class VehicleRuntimeChecks {
    private static int ticks,vehicleId;
    private static UUID vehicleUUID;
    private static final BlockPos ORIGIN=new BlockPos(8,101,8), SEAT=ORIGIN.add(1,2,1);
    private static VehicleStructure expected;
    private static double drivenFrom,lastRenderedX;
    private static int backwardsJumps;
    private static EntityGroundVehicle distantRender;
    public static final class CancelSpawn {
        @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public void spawn(net.minecraftforge.event.entity.EntityJoinWorldEvent e){if(e.getEntity() instanceof EntityGroundVehicle)e.setCanceled(true);}
    }
    public static final class CancelParking {
        @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public void place(net.minecraftforge.event.world.BlockEvent.MultiPlaceEvent e){e.setCanceled(true);}
    }
    private static void require(boolean test,String message){if(!test)throw new IllegalStateException("vehicle: "+message);}
    private static VehicleScan scan(WorldServer world,BlockPos seat) {
        VehicleScan scan=new VehicleScan(world,seat);for(int i=0;i<200 && !scan.step(128);i++);
        require(scan.error==null && scan.result!=null,"scan: "+scan.error);return scan;
    }
    private static final class SignalSource extends net.minecraft.tileentity.TileEntity implements com.vandorlabs.redstone.RedstoneChannelMember {
        boolean high;public net.minecraft.tileentity.TileEntity channelTile(){return this;}public int getRedstoneChannel(){return 5317;}public void setRedstoneChannel(int channel){}public boolean hasLocalRedstoneSignal(){return high;}public void setChannelSignal(boolean signal){}
        void power(boolean on){high=on;com.vandorlabs.redstone.RedstoneChannels.inputChanged(this);}
    }
    private static void channelConversion(WorldServer world,EntityPlayerMP player,Block gear) throws Exception {
        BlockPos base=new BlockPos(40,101,12),seat=base.up(2);
        world.setBlockState(base,gear.getDefaultState(),2);((TileEntityLandingGear)world.getTileEntity(base)).configure(0,0,0,0);
        world.setBlockState(base.up(),Blocks.STONE.getDefaultState(),2);
        IBlockState state=ModBlocks.PILOT_SEAT.getDefaultState();world.setBlockState(seat,state,2);ModBlocks.PILOT_SEAT.onBlockPlacedBy(world,seat,state,player,new ItemStack(ModBlocks.PILOT_SEAT));
        SignalSource source=new SignalSource();source.setWorld(world);source.setPos(base.west(3));com.vandorlabs.redstone.RedstoneChannels.register(source);
        try {
            TileEntityPilotSeat pilot=(TileEntityPilotSeat)world.getTileEntity(seat);pilot.setRedstoneChannel(5317);
            player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);player.setPositionAndUpdate(seat.getX(),seat.getY(),seat.getZ());
            ModBlocks.PILOT_SEAT.onBlockActivated(world,seat,state,player,EnumHand.MAIN_HAND,EnumFacing.NORTH,.5F,.5F,.5F);
            require(player.getRidingEntity() instanceof com.vandorlabs.entity.EntityChairSeat,"pilot sits before channel assembly");
            source.power(true);pilot.update();VehicleService.tick(new net.minecraftforge.fml.common.gameevent.TickEvent.WorldTickEvent(net.minecraftforge.fml.relauncher.Side.SERVER,net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END,world));
            require(player.getRidingEntity() instanceof EntityGroundVehicle,"rising channel signal assembles with seated pilot");
            EntityGroundVehicle craft=(EntityGroundVehicle)player.getRidingEntity();for(int settle=0;settle<30;settle++)craft.onUpdate();VehicleService.tick(new net.minecraftforge.fml.common.gameevent.TickEvent.WorldTickEvent(net.minecraftforge.fml.relauncher.Side.SERVER,net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END,world));
            require(!craft.isDead,"held high signal does not toggle repeatedly");
            source.power(false);craft.onUpdate();source.power(true);craft.onUpdate();
            VehicleService.tick(new net.minecraftforge.fml.common.gameevent.TickEvent.WorldTickEvent(net.minecraftforge.fml.relauncher.Side.SERVER,net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END,world));
            require(craft.isDead && player.getRidingEntity() instanceof com.vandorlabs.entity.EntityChairSeat,"second rising edge parks with pilot safely seated: dead="+craft.isDead+", riding="+player.getRidingEntity()+", y="+craft.posY+", dy="+craft.motionY);
            TileEntityPilotSeat parked=(TileEntityPilotSeat)world.getTileEntity(seat);require(parked!=null && parked.getRedstoneChannel()==5317 && parked.owner.equals(player.getUniqueID()),"parking retains pilot channel and owner");
            parked.update();VehicleService.tick(new net.minecraftforge.fml.common.gameevent.TickEvent.WorldTickEvent(net.minecraftforge.fml.relauncher.Side.SERVER,net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END,world));
            require(world.getBlockState(seat).getBlock()==ModBlocks.PILOT_SEAT,"held high remains stable after parking");
            player.dismountRidingEntity();player.setPositionAndUpdate(4,101,8);
        }finally{com.vandorlabs.redstone.RedstoneChannels.unregister(source);}
    }
    private static void chunkSerialization(WorldServer world) {
        Block light=null,lever=null;
        for(Block block:Block.REGISTRY) {
            if(block instanceof BlockPropulsionLight)light=block;
            if(block instanceof BlockIndustrialLever)lever=block;
        }
        require(light!=null && lever!=null,"chunk serialization fixtures");
        BlockPos at=new BlockPos(72,101,8),source=at.east();
        world.setBlockState(at,light.getDefaultState(),2);
        world.setBlockState(source,lever.getDefaultState().withProperty(BlockIndustrialLever.POWERED,true),2);
        TileEntityRedstoneLight tile=(TileEntityRedstoneLight)world.getTileEntity(at);
        NBTTagCompound data=tile.writeToNBT(new NBTTagCompound());
        data.setInteger("ParticleLevel",3);data.setBoolean("SignalBrightness",true);tile.readFromNBT(data);
        // Older saves can contain a block without the tile required by newer code.
        world.removeTileEntity(source);
        net.minecraft.world.chunk.Chunk chunk=world.getChunkFromBlockCoords(at);
        int count=chunk.getTileEntityMap().size();
        new net.minecraft.network.play.server.SPacketChunkData(chunk,65535);
        require(chunk.getTileEntityMap().size()==count && !chunk.getTileEntityMap().containsKey(source),"chunk serialization never creates neighbouring tiles");
        require(tile.getUpdateTag().getBoolean("ParticleStream"),"particle selection persists independently of power");
        tile.isParticleStreamSelected();
        require(chunk.getTileEntityMap().containsKey(source),"fixture reproduces lazy tile creation through runtime power lookup");
        world.setBlockToAir(at);world.setBlockToAir(source);
        Block canopy=null;for(Block block:Block.REGISTRY)if(block instanceof BlockCanopy && ((BlockCanopy)block).kind.equals("regular_canopy_glass"))canopy=block;
        require(canopy!=null,"canopy serialization fixture");
        world.setBlockState(at,canopy.getDefaultState(),2);world.setBlockState(source,canopy.getDefaultState(),2);
        world.removeTileEntity(source);count=chunk.getTileEntityMap().size();
        new net.minecraft.network.play.server.SPacketChunkData(chunk,65535);
        require(chunk.getTileEntityMap().size()==count && !chunk.getTileEntityMap().containsKey(source),"canopy connection serialization never creates neighbouring tiles");
        world.setBlockToAir(at);world.setBlockToAir(source);
        System.out.println("[vandorlabs][reprolab] chunk-serialization PASS legacy-missing-tile redstone-particle-selection");
    }
    private static void extendedHull(WorldServer world,EntityPlayerMP player) {
        List<VehicleStructure.Cell> cells=new ArrayList<>();
        cells.add(new VehicleStructure.Cell(BlockPos.ORIGIN,ModBlocks.PILOT_SEAT.getDefaultState(),null));
        cells.add(new VehicleStructure.Cell(new BlockPos(40,20,0),Blocks.STONE_SLAB.getDefaultState(),null));
        cells.add(new VehicleStructure.Cell(new BlockPos(40,20,1),ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState(),null));
        EntityGroundVehicle craft=new EntityGroundVehicle(world);
        craft.install(new VehicleStructure(cells,BlockPos.ORIGIN,EnumFacing.NORTH,1));craft.setPosition(160,120,8);
        world.getChunkFromChunkCoords(10,0);world.getChunkFromChunkCoords(12,0);
        require(world.spawnEntity(craft),"extended hull spawn");
        try {
            for(int z=0;z<2;z++) {
                double top=z==0?140.5:141;
                player.setPosition(200.5,top,8.5+z);
                AxisAlignedBB body=player.getEntityBoundingBox();double down=-.25;
                for(AxisAlignedBB floor:world.getCollisionBoxes(player,body.expand(0,down,0)))down=floor.calculateYOffset(body,down);
                require(Math.abs(down)<.00001,"far hull supports "+(z==0?"slab":"programmable floor")+" across chunk and vertical section");
            }
            player.setPosition(198,139,9.5);player.rotationYaw=-90;player.rotationPitch=0;
            require(VehicleLookup.pointed(player)==craft,"configurizer targets far hull without origin entity query");
            require(craft.getRenderBoundingBox().contains(new Vec3d(200.5,140.5,9.5)),"render bounds contain far hull");
        }finally{craft.setDead();player.setPosition(4,101,8);}
        System.out.println("[vandorlabs][reprolab] vehicle-extended-hull PASS slab programmable-floor distant-target render-bounds");
    }
    private static void server(Minecraft mc) throws Exception {
        WorldServer w=mc.getIntegratedServer().getWorld(0);EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
        chunkSerialization(w);
        extendedHull(w,p);
        p.dismountRidingEntity();p.setSneaking(false);p.setPositionAndUpdate(4,101,8);p.capabilities.isFlying=true;p.sendPlayerAbilities();
        for(BlockPos pos:BlockPos.getAllInBox(new BlockPos(0,100,0),new BlockPos(48,100,32)))w.setBlockState(pos,Blocks.STONE.getDefaultState(),2);
        Block gear=null;for(Block block:Block.REGISTRY)if(block instanceof BlockTelescopicLandingGear){gear=block;break;}
        require(gear!=null,"registered landing gear");
        w.setWorldTime(6000);w.getGameRules().setOrCreateGameRule("doMobSpawning","false");
        for(int x=0;x<3;x++)for(int z=0;z<3;z++) {
            BlockPos at=ORIGIN.add(x,1,z);w.setBlockState(at,ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState(),3);
            TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)w.getTileEntity(at);
            int texture=x==0?FilesystemTextures.identifier("Example/sample_panel.png"):x==1?CustomBlockMaterials.choice(new ItemStack(Blocks.GOLD_BLOCK)):14;
            tile.setHousingTexture(texture);
            if(z==0)tile.setFaceTextures(new FaceTextures(true,new int[]{0,1,2,3,4,5}));
            if(x!=1 && z!=1){BlockPos root=ORIGIN.add(x,0,z);w.setBlockState(root,gear.getDefaultState(),3);((TileEntityLandingGear)w.getTileEntity(root)).configure(0,0,0,0);}
        }
        IBlockState seat=ModBlocks.PILOT_SEAT.getDefaultState().withProperty(BlockPilotSeat.FACING,EnumFacing.EAST);
        w.setBlockState(SEAT,seat,3);ModBlocks.PILOT_SEAT.onBlockPlacedBy(w,SEAT,seat,p,new ItemStack(ModBlocks.PILOT_SEAT));
        String[] engines={"rocket_thruster","ion_drive","plasma_vent","impulse_engine"};
        for(int i=0;i<engines.length;i++) {
            Block engine=Block.getBlockFromName("vandorlabs:"+engines[i]);require(engine instanceof BlockPropulsionLight,"propulsion fixture "+engines[i]);
            BlockPos at=ORIGIN.add(i,2,2);w.setBlockState(at,engine.getDefaultState().withProperty(BlockPropulsionLight.FACING,EnumFacing.NORTH),3);
            TileEntityRedstoneLight tile=(TileEntityRedstoneLight)w.getTileEntity(at);tile.setManualMode(i==0?1:0,false);tile.configureSignalBrightness(true,15);tile.setParticleLevel(3,false);
        }
        for(int i=0;i<4;i++)w.setBlockState(ORIGIN.add(i,1,3),Blocks.QUARTZ_STAIRS.getDefaultState().withProperty(net.minecraft.block.BlockStairs.FACING,EnumFacing.HORIZONTALS[i]),3);
        Block ladder=Block.getBlockFromName("immersiveengineering:metal_ladder");require(ladder!=null,"IE ladder fixture installed");
        for(int i=0;i<3;i++) {
            BlockPos at=ORIGIN.add(i,2,3);w.setBlockState(at,ladder.getStateFromMeta(i),2);
            net.minecraft.tileentity.TileEntity tile=w.getTileEntity(at);NBTTagCompound tag=tile.writeToNBT(new NBTTagCompound());
            tag.setInteger("facing",EnumFacing.SOUTH.getIndex());tile.readFromNBT(tag);tile.markDirty();
        }
        BlockShipSystem dampener=(BlockShipSystem)Block.getBlockFromName("vandorlabs:vektor_dampener_large");
        require(dampener!=null,"registered inertial dampener");BlockPos dampenerPos=ORIGIN.add(0,2,4);
        IBlockState dampenerState=dampener.getDefaultState().withProperty(BlockShipSystem.FACING,EnumFacing.NORTH);
        w.setBlockState(dampenerPos,dampenerState,2);dampener.onBlockPlacedBy(w,dampenerPos,dampenerState,p,new ItemStack(dampener));
        require(dampener.cells(dampenerPos,EnumFacing.NORTH).size()==12,"dampener has a complete twelve-cell footprint");
        // Doors retain both halves and can be operated after assembly.
        Block ownDoor=null;for(Block b:Block.REGISTRY)if(b instanceof BlockVandorDoor && !(b instanceof BlockLargeProgrammableDoor) && !(b instanceof BlockConfigurableSpaceDoor)){ownDoor=b;break;}
        require(ownDoor!=null,"registered Vandor door");
        BlockPos vanillaDoor=ORIGIN.add(0,2,0),vandorDoor=ORIGIN.add(2,2,0);
        for(BlockPos at:new BlockPos[]{vanillaDoor,vandorDoor}) {
            Block b=at.equals(vanillaDoor)?Blocks.OAK_DOOR:ownDoor;
            w.setBlockState(at,b.getDefaultState().withProperty(net.minecraft.block.BlockDoor.HALF,net.minecraft.block.BlockDoor.EnumDoorHalf.LOWER),2);
            w.setBlockState(at.up(),b.getDefaultState().withProperty(net.minecraft.block.BlockDoor.HALF,net.minecraft.block.BlockDoor.EnumDoorHalf.UPPER),2);
        }
        BlockPos rampPos=ORIGIN.add(0,2,6);
        w.setBlockState(rampPos,ModBlocks.PROGRAMMABLE_RAMP.getDefaultState().withProperty(BlockRampController.FACING,EnumFacing.SOUTH),2);
        for(int n=1;n<=2;n++) {
            BlockPos at=rampPos.south(n);w.setBlockState(at,ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState(),2);
            ((TileEntityAnimatedScreenSelector)w.getTileEntity(at)).setHousingTexture(CustomBlockMaterials.choice(new ItemStack(Blocks.GOLD_BLOCK)));
        }
        TileEntityRampController ramp=(TileEntityRampController)w.getTileEntity(rampPos);ramp.setOwner(p);
        p.setPositionAndUpdate(rampPos.getX()-2,rampPos.getY(),rampPos.getZ());
        require(ramp.configureHalfOffsets(p,0,-2,8,false,false,false,EnumFacing.SOUTH,com.vandorlabs.ramp.RampGeometry.VERTICAL,true,1,true),"extended programmable ramp configures");
        ControllerRuntimeChecks.elapsed(ramp,ramp.durationTicks()+1);
        require(ramp.attached() && !ramp.isMoving() && ramp.isOpen(),"ramp fully extended before assembly");
        p.setPositionAndUpdate(4,101,8);
        VehicleScan scan=scan(w,SEAT);expected=scan.result;
        require(expected.cells.size()>42 && expected.gearCount==4,"gear boundary excludes floor while all branches remain");
        VehicleStructure decoded=VehicleStructure.decode(expected.encode());require(decoded.write().equals(expected.write()),"snapshot state/NBT round trip");
        VehicleWorld local=new VehicleWorld(w,decoded);
        require(((TileEntityAnimatedScreenSelector)local.getTileEntity(new BlockPos(0,1,1))).getHousingTexture()==FilesystemTextures.identifier("Example/sample_panel.png"),"filesystem texture identifier");
        TileEntityShipSystem localDampener=(TileEntityShipSystem)local.getTileEntity(new BlockPos(0,2,4));
        require(localDampener!=null && localDampener.formed() && localDampener.draws(),"inertial dampener keeps local ownership and draw root");
        require(!local.getBlockState(new BlockPos(0,2,2)).getValue(BlockPropulsionLight.POWERED) && ((TileEntityRedstoneLight)local.getTileEntity(new BlockPos(0,2,2))).getBrightness()==0,"previously powered thruster starts dark on assembly");
        TileEntityRampController localRamp=(TileEntityRampController)local.getTileEntity(rampPos.subtract(ORIGIN));
        require(localRamp!=null && localRamp.attached() && localRamp.isOpen(),"ramp controller keeps extended ownership");
        boolean visibleRamp=false;
        for(net.minecraft.tileentity.TileEntity raw:local.tiles.values())if(raw instanceof TileEntityControlledRamp) {
            TileEntityControlledRamp part=(TileEntityControlledRamp)raw;
            require(part.controller.equals(localRamp.getPos()) && localRamp.owns(part),"translated ramp journals and ownership");
            visibleRamp|=!part.boxes(0).isEmpty();
        }
        require(visibleRamp,"extended ramp geometry survives local-world translation");
        local.propulsionLevel=12;
        for(int i=0;i<4;i++) {
            BlockPos at=new BlockPos(i,2,2);IBlockState state=local.getBlockState(at);state=state.getBlock().getActualState(state,local,at);
            require(state.getValue(BlockPropulsionLight.POWERED) && !state.getValue(BlockPropulsionLight.PARTICLES),"driving overrides redstone without particle stream");
            require(((TileEntityRedstoneLight)local.getTileEntity(at)).getBrightness()==12,"propulsion brightness ramp ignores signal settings");
        }
        require(decoded.write().equals(expected.write()),"brightness overlay leaves stored settings unchanged");
        BlockPos chestPos=ORIGIN.add(3,1,1);w.setBlockState(chestPos,Blocks.CHEST.getDefaultState(),3);
        ((net.minecraft.tileentity.TileEntityChest)w.getTileEntity(chestPos)).setInventorySlotContents(0,new ItemStack(Blocks.DIAMOND_BLOCK,7));
        VehicleScan withInventory=scan(w,SEAT);
        VehicleWorld inventoryView=new VehicleWorld(w,withInventory.result);
        require(((net.minecraft.tileentity.TileEntityChest)inventoryView.getTileEntity(chestPos.subtract(withInventory.origin))).getStackInSlot(0).getCount()==7,"generic tile NBT preserved without ticking");
        expected=withInventory.result;scan=withInventory;
        List<VehicleStructure.Cell> large=new ArrayList<>();
        for(int x=0;x<16;x++)for(int y=0;y<16;y++)for(int z=0;z<16;z++)large.add(new VehicleStructure.Cell(new BlockPos(x,y,z),x==0&&y==0&&z==0?seat:Blocks.STONE.getDefaultState(),null));
        VehicleStructure full=new VehicleStructure(large,BlockPos.ORIGIN,EnumFacing.EAST,1);
        require(VehicleStructure.decode(full.encode()).cells.size()==4096,"4096-block craft codec boundary");
        large.add(new VehicleStructure.Cell(new BlockPos(16,0,0),Blocks.STONE.getDefaultState(),null));
        boolean oversize=false;try{new VehicleStructure(large,BlockPos.ORIGIN,EnumFacing.EAST,1);}catch(IllegalArgumentException expectedFailure){oversize=true;}
        require(oversize,"4097-block craft rejected");
        BlockPos bigOrigin=new BlockPos(64,112,0);
        for(int cx=3;cx<=5;cx++)for(int cz=-1;cz<=1;cz++)w.getChunkFromChunkCoords(cx,cz);
        for(int x=0;x<16;x++)for(int y=0;y<16;y++)for(int z=0;z<16;z++)w.setBlockState(bigOrigin.add(x,y,z),Blocks.STONE.getDefaultState(),2);
        w.setBlockState(bigOrigin,seat,2);ModBlocks.PILOT_SEAT.onBlockPlacedBy(w,bigOrigin,seat,p,new ItemStack(ModBlocks.PILOT_SEAT));
        BlockPos bigGear=bigOrigin.add(15,0,15);w.setBlockState(bigGear,gear.getDefaultState(),2);((TileEntityLandingGear)w.getTileEntity(bigGear)).configure(0,0,0,0);
        require(scan(w,bigOrigin).result.cells.size()==4096,"4096 occupied blocks discovered across bounded scan slices");
        w.setBlockState(bigOrigin.add(16,0,0),Blocks.STONE.getDefaultState(),2);
        VehicleScan rejected=new VehicleScan(w,bigOrigin);for(int n=0;n<200 && !rejected.step(256);n++);
        require(rejected.error!=null && rejected.error.contains("4096"),"scan rejects 4097 occupied blocks before conversion");
        for(BlockPos at:BlockPos.getAllInBox(bigOrigin,bigOrigin.add(16,15,15)))w.setBlockToAir(at);

        channelConversion(w,p,gear);
        CancelSpawn cancel=new CancelSpawn();net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(cancel);
        boolean denied=false;try{VehicleTransfer.assemble(w,expected,scan.origin);}catch(Exception expectedFailure){denied=true;}finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(cancel);}
        require(denied && expected.matches(w,scan.origin) && !VehicleTransfer.pending(w),"spawn cancellation rolls blocks back intact");
        // Simulate an interrupted removal with a durable journal and a stale entity.
        EntityGroundVehicle ghost=new EntityGroundVehicle(w);ghost.install(expected);ghost.setPosition(scan.origin.getX(),scan.origin.getY(),scan.origin.getZ());w.spawnEntity(ghost);
        NBTTagCompound journal=new NBTTagCompound();journal.setInteger("Schema",1);journal.setTag("Craft",expected.write());journal.setLong("Origin",scan.origin.toLong());journal.setUniqueId("Entity",ghost.getUniqueID());journal.setDouble("X",ghost.posX);journal.setDouble("Y",ghost.posY);journal.setDouble("Z",ghost.posZ);
        File journalFile=new File(w.getSaveHandler().getWorldDirectory(),"data/vandorlabs-vehicle-0.nbt");
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(journalFile)){net.minecraft.nbt.CompressedStreamTools.writeCompressed(journal,out);}
        w.setBlockToAir(ORIGIN.add(1,1,1));VehicleTransfer.recover(w);
        require(expected.matches(w,scan.origin) && ghost.isDead && !VehicleTransfer.pending(w),"interrupted transfer recovery");
        EntityGroundVehicle stale=new EntityGroundVehicle(w);stale.setUniqueId(ghost.getUniqueID());stale.install(expected);stale.setPosition(ghost.posX,ghost.posY,ghost.posZ);require(!w.spawnEntity(stale),"stale recovered entity cannot load later");
        EntityGroundVehicle entity=VehicleTransfer.assemble(w,expected,scan.origin);
        for(VehicleStructure.Cell cell:expected.cells)require(w.isAirBlock(scan.origin.add(cell.pos)),"source cleared");
        require(w.getBlockState(ORIGIN.down()).getBlock()==Blocks.STONE,"ground preserved");
        require(!VehicleTransfer.pending(w),"assembly journal retired after flush");
        for(BlockPos door:new BlockPos[]{vanillaDoor.subtract(ORIGIN),vandorDoor.subtract(ORIGIN)}) {
            require(VehicleDoors.toggle(entity,door),"assembled door opens");
            require(entity.view.getBlockState(door).getValue(net.minecraft.block.BlockDoor.OPEN),"lower door open state");
            require(entity.view.getBlockState(door.up()).getValue(net.minecraft.block.BlockDoor.OPEN),"upper door open state");
            require(VehicleDoors.toggle(entity,door),"assembled door closes");
        }
        require(entity.structure.write().equals(expected.write()),"door operation keeps all private tile and ramp data intact");
        p.startRiding(entity,true);
        double start=entity.posX;
        for(int n=0;n<35;n++){entity.input(p,n,(byte)1,(byte)0,false);entity.onUpdate();entity.updatePassenger(p);}
        require(entity.posX>start+5 && Math.abs(entity.posZ-ORIGIN.getZ())<.001,"forward uses seat-facing X axis");
        require(entity.rotationYaw==0 && entity.rotationPitch==0,"straight driving keeps its heading");
        double beforeZ=entity.posZ;
        for(int n=35;n<55;n++){entity.input(p,n,(byte)1,(byte)1,false);entity.onUpdate();entity.updatePassenger(p);}
        require(entity.rotationYaw>10 && entity.posZ>beforeZ+.5,"right key steers the moving craft");
        for(int n=55;n<75;n++){entity.input(p,n,(byte)0,(byte)0,true);entity.onUpdate();entity.updatePassenger(p);}
        require(entity.stopped(),"brake stops craft");
        NBTTagCompound saved=new NBTTagCompound();entity.writeToNBT(saved);EntityGroundVehicle loaded=new EntityGroundVehicle(w);loaded.readFromNBT(saved);
        require(loaded.structure.write().equals(expected.write()) && loaded.stopped(),"entity save/reload retains textures and stops inputs");
        entity.rotationYaw=0;entity.steering=0;entity.setPosition(entity.posX,entity.posY,ORIGIN.getZ());
        for(BlockPos at:BlockPos.getAllInBox(new BlockPos(32,101,0),new BlockPos(32,106,32)))w.setBlockState(at,Blocks.STONE.getDefaultState(),2);
        for(int n=75;n<175;n++){entity.input(p,n,(byte)1,(byte)0,false);entity.onUpdate();entity.updatePassenger(p);}
        require(entity.getEntityBoundingBox().maxX<=32.000001 && entity.posX>25,"compound wall collision without tunnelling");
        for(int n=175;n<190;n++){entity.input(p,n,(byte)0,(byte)0,true);entity.onUpdate();}
        p.dismountRidingEntity();p.setPositionAndUpdate(4,101,8);
        BlockPos parked=new BlockPos(ORIGIN.getX()+10,ORIGIN.getY(),ORIGIN.getZ());
        entity.setPosition(parked.getX(),parked.getY(),parked.getZ());entity.motionX=entity.motionY=entity.motionZ=0;
        UUID id=entity.getUniqueID();CancelParking preventParking=new CancelParking();net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(preventParking);
        denied=false;try{VehicleTransfer.park(w,entity,parked,p);}catch(Exception expectedFailure){denied=true;}finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(preventParking);}
        require(denied && !VehicleTransfer.pending(w),"cancelled parking rolls back");
        entity=(EntityGroundVehicle)w.getEntityFromUuid(id);require(entity!=null && !entity.isDead,"rollback restores a single live vehicle");
        VehicleTransfer.park(w,entity,parked,null);
        require(expected.matches(w,parked),"parking restores full states, file/custom/per-face textures and tile ownership");
        require(!VehicleTransfer.pending(w),"parking journal retired");
        require(!w.spawnEntity(loaded),"stale pre-parking entity rejected");
        VehicleScan again=scan(w,parked.add(expected.seat));
        require(again.result.write().equals(expected.write()),"repeated assembly has stable local data");
        entity=VehicleTransfer.assemble(w,again.result,again.origin);vehicleId=entity.getEntityId();vehicleUUID=entity.getUniqueID();
        p.connection.setPlayerLocation(parked.getX()-5,105,parked.getZ()-5,-45,20);
        p.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(ModItems.CONFIGURIZER));
        System.out.println("[vandorlabs][reprolab] vehicle-world PASS gear-boundary custom-textures snapshots drive steer brake collision park reload recovery cancellation stale-entities propulsion");
    }
    private static void safeExit(EntityPlayerMP player,String kind) {
        AxisAlignedBB box=player.getEntityBoundingBox();
        require(!player.isRiding() && player.posY>=101,"safe "+kind+" dismount above floor");
        require(player.world.getCollisionBoxes(player,box.shrink(.001)).isEmpty(),kind+" dismount has body clearance");
        require(!player.world.getCollisionBoxes(player,new AxisAlignedBB(player.posX-.1,box.minY-.06,player.posZ-.1,player.posX+.1,box.minY,player.posZ+.1)).isEmpty(),kind+" dismount is supported after physics ticks");
    }
    public static void tick(Minecraft mc,File output) {
        try {
            ticks++;
            if(ticks>240 && ticks<270 && mc.player.getRidingEntity() instanceof EntityGroundVehicle) {
                EntityGroundVehicle driven=(EntityGroundVehicle)mc.player.getRidingEntity();double rendered=driven.posX+driven.renderCorrection().x;
                if(ticks>241 && rendered-lastRenderedX>.05)backwardsJumps++;lastRenderedX=rendered;
            }
            if(ticks==20){mc.displayGuiScreen(null);mc.getIntegratedServer().addScheduledTask(()->{try{server(mc);}catch(Exception e){throw new IllegalStateException(e);}}).get();mc.gameSettings.hideGUI=true;}
            if(ticks==160) {
                EntityGroundVehicle entity=null;
                for(net.minecraft.entity.Entity raw:mc.world.loadedEntityList)if(raw instanceof EntityGroundVehicle && raw.getUniqueID().equals(vehicleUUID))entity=(EntityGroundVehicle)raw;
                require(entity!=null && entity.structure!=null,"client tracking snapshot received (id="+vehicleId+", entity="+entity+", ticks="+(entity==null?-1:entity.ticksExisted)+")");
                require(entity.structure.write().equals(expected.write()),"client exact snapshot");
                require(mc.getRenderManager().getEntityRenderObject(entity).getClass()==RenderGroundVehicle.class,"vehicle uses registered craft renderer, not fallback rectangle");
                TileEntityShipSystem dampener=(TileEntityShipSystem)entity.view.getTileEntity(new BlockPos(0,2,4));
                require(dampener!=null && dampener.formed() && dampener.draws() && net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher.instance.getRenderer(dampener).getClass()==TEShipSystem.class,"client inertial dampener uses complete machinery renderer");
                IBlockState s=entity.view.getBlockState(new BlockPos(1,1,1));
                net.minecraft.client.renderer.block.model.IBakedModel model=mc.getBlockRendererDispatcher().getModelForState(s);
                s=s.getBlock().getActualState(s,entity.view,new BlockPos(1,1,1));s=s.getBlock().getExtendedState(s,entity.view,new BlockPos(1,1,1));
                java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> quads=new ArrayList<>();
                net.minecraftforge.client.ForgeHooksClient.setRenderLayer(BlockRenderLayer.SOLID);
                quads.addAll(model.getQuads(s,null,0));for(EnumFacing face:EnumFacing.values())quads.addAll(model.getQuads(s,face,0));
                net.minecraftforge.client.ForgeHooksClient.setRenderLayer(null);
                require(!quads.isEmpty(),"programmable hull has baked geometry");
                ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"shot_vehicle_assembled.png"));
                for(net.minecraft.client.renderer.block.model.BakedQuad q:quads)require(q.getSprite().getIconName().contains("gold_block"),"custom block texture on moving hull: "+q.getSprite().getIconName()+" model="+model.getClass().getName()+" state="+s+" tile="+entity.view.getTileEntity(new BlockPos(1,1,1)));
                mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);EntityGroundVehicle e=(EntityGroundVehicle)((WorldServer)p.world).getEntityFromUuid(vehicleUUID);p.setPositionAndUpdate(e.posX-2,e.posY+2,e.posZ);e.processInitialInteract(p,EnumHand.MAIN_HAND);}).get();
                System.out.println("[vandorlabs][reprolab] vehicle-render PASS network programmable-custom-material");
            }
            if(ticks==210) {
                require(mc.currentScreen!=null,"parking preview received");
                ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"shot_vehicle_parking_preview.png"));
                mc.displayGuiScreen(null);
                mc.getIntegratedServer().addScheduledTask(()->{
                    EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
                    EntityGroundVehicle e=(EntityGroundVehicle)((WorldServer)p.world).getEntityFromUuid(vehicleUUID);
                    p.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);p.capabilities.isFlying=false;p.sendPlayerAbilities();
                    e.processInitialInteract(p,EnumHand.MAIN_HAND);drivenFrom=e.posX;
                }).get();
            }
            if(ticks==240) {
                require(mc.player.getRidingEntity() instanceof EntityGroundVehicle,"client mounts driver");
                net.minecraft.client.settings.KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(),true);
                mc.gameSettings.thirdPersonView=1;
                net.minecraft.client.settings.KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(),true);
            }
            if(ticks==270) {
                EntityGroundVehicle e=(EntityGroundVehicle)mc.player.getRidingEntity();
                require(e.view.propulsionLevel>0,"moving propulsion ramps brightness (x="+e.posX+", start="+drivenFrom+", key="+mc.gameSettings.keyBindBack.isKeyDown()+", gui="+mc.currentScreen+")");
                require(Math.abs(e.rotationYaw)>5,"actual right-key packets rotate the driven craft");
                require(backwardsJumps==0,"predicted reverse motion has no visible backward corrections");
                require(VehicleClient.cameraDistance()>4,"third-person camera fits the larger craft");
                ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"shot_vehicle_driving.png"));
                net.minecraft.client.settings.KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(),false);
                net.minecraft.client.settings.KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(),false);
                net.minecraft.client.settings.KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(),true);
            }
            if(ticks==300) {
                EntityGroundVehicle e=(EntityGroundVehicle)mc.player.getRidingEntity();require(e.view.propulsionLevel==0,"stopped propulsion turns off");
                net.minecraft.client.settings.KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(),false);
                mc.getIntegratedServer().addScheduledTask(()->{
                    EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
                    EntityGroundVehicle vehicle=(EntityGroundVehicle)p.getRidingEntity();
                    require(vehicle.posX<drivenFrom-2 && vehicle.stopped() && Math.abs(vehicle.rotationYaw)>5,"network driving, steering and brake");
                    p.dismountRidingEntity();
                }).get();
            }
            if(ticks==330) {
                mc.getIntegratedServer().addScheduledTask(()->{
                    EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);safeExit(p,"vehicle");
                    BlockPos pos=new BlockPos(4,101,16);IBlockState state=ModBlocks.PILOT_SEAT.getDefaultState();
                    p.world.setBlockState(pos,state,3);ModBlocks.PILOT_SEAT.onBlockPlacedBy(p.world,pos,state,p,new ItemStack(ModBlocks.PILOT_SEAT));
                    ModBlocks.PILOT_SEAT.onBlockActivated(p.world,pos,state,p,EnumHand.MAIN_HAND,EnumFacing.NORTH,.5F,.5F,.5F);
                    require(p.isRiding(),"placed Pilot Seat mounts");
                }).get();
            }
            if(ticks==350)mc.getIntegratedServer().addScheduledTask(()->mc.getIntegratedServer().getPlayerList().getPlayers().get(0).dismountRidingEntity()).get();
            if(ticks==380) {
                mc.getIntegratedServer().addScheduledTask(()->safeExit(mc.getIntegratedServer().getPlayerList().getPlayers().get(0),"placed seat")).get();
                List<VehicleStructure.Cell> cells=new ArrayList<>();
                cells.add(new VehicleStructure.Cell(BlockPos.ORIGIN,ModBlocks.PILOT_SEAT.getDefaultState(),null));
                cells.add(new VehicleStructure.Cell(new BlockPos(40,20,1),Blocks.GOLD_BLOCK.getDefaultState(),null));
                distantRender=new EntityGroundVehicle(mc.world);distantRender.install(new VehicleStructure(cells,BlockPos.ORIGIN,EnumFacing.NORTH,1));distantRender.setPosition(160,120,8);
                mc.world.addEntityToWorld(-40000,distantRender);mc.gameSettings.thirdPersonView=0;
            }
            if(ticks>=380) {
                // Origin section is behind/below the camera; only the far gold hull is visible.
                distantRender.setPosition(160,120,8);distantRender.prevPosX=distantRender.lastTickPosX=160;distantRender.prevPosY=distantRender.lastTickPosY=120;distantRender.prevPosZ=distantRender.lastTickPosZ=8;
                mc.player.setPosition(198,139,9.5);mc.player.prevPosX=mc.player.lastTickPosX=198;mc.player.prevPosY=mc.player.lastTickPosY=139;mc.player.prevPosZ=mc.player.lastTickPosZ=9.5;
                mc.player.rotationYaw=mc.player.prevRotationYaw=mc.player.rotationYawHead=mc.player.prevRotationYawHead=-90;mc.player.rotationPitch=mc.player.prevRotationPitch=3;
            }
            if(ticks==425) {
                require(RenderGroundVehicle.fallbackDraws()>0,"visible hull rendered with origin section behind camera");
                ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"shot_vehicle_distant_hull.png"));
                System.out.println("[vandorlabs][reprolab] vehicle-distant-render PASS hidden-origin-section visible-hull");
                System.out.println("[vandorlabs][reprolab] vehicle-live PASS rendering generic-blocks network-driving propulsion safe-dismount doors ramps channel-conversion seated-conversion 4096-blocks");mc.shutdown();
            }
        }catch(Exception e){throw new IllegalStateException("vehicle live checks",e);}
    }
}
