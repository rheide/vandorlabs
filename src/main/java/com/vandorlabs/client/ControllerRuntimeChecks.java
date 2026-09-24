package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockRampController;
import com.vandorlabs.blocks.BlockVandorDirectional;
import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.tiles.TileEntityControlledRamp;
import com.vandorlabs.tiles.TileEntityRampController;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.ArrayList;
import java.util.List;

/** Real Forge-world transactions, event activation, collision and passenger transport. */
public final class ControllerRuntimeChecks {
    public static final BlockPos FIXTURE=new BlockPos(40,24,0);
    private static int assertions;
    private ControllerRuntimeChecks() { }
    private static void require(boolean condition,String message) {
        assertions++;
        if (!condition) throw new IllegalStateException("ramp controller check: "+message);
    }
    private static BlockRampController block() {
        return (BlockRampController)Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:programmable_ramp"));
    }
    private static TileEntityRampController place(World world,BlockPos p,EnumFacing face) {
        world.setBlockState(p,block().getDefaultState().withProperty(BlockVandorDirectional.FACING,face),3);
        return (TileEntityRampController)world.getTileEntity(p);
    }
    /** Advance the actual persisted timeline without changing global world time. */
    public static void elapsed(TileEntityRampController controller,int elapsed) {
        World world=controller.getWorld();
        NBTTagCompound tag=controller.writeToNBT(new NBTTagCompound());
        tag.setLong("StartTick",world.getTotalWorldTime()-elapsed);
        tag.setLong("LastStepTick",world.getTotalWorldTime()-1);
        controller.readFromNBT(tag);
        NBTTagList cells=tag.getTagList("Sources",10);
        for (int i=0;i<cells.tagCount();i++) {
            BlockPos source=BlockPos.fromLong(cells.getCompoundTagAt(i).getLong("Pos"));
            for (int d=Math.min(0,Math.min(controller.startOffset,controller.endOffset()));d<=Math.max(0,Math.max(controller.startOffset,controller.endOffset()));d++) {
                BlockPos p=source.up(d);
                if (world.getTileEntity(p) instanceof TileEntityControlledRamp)
                    ((TileEntityControlledRamp)world.getTileEntity(p)).move(controller.isOpen(),
                            tag.getDouble("StartPose"),tag.getLong("StartTick"),controller.durationTicks(),controller.isMoving());
            }
        }
        controller.scheduledTick();
    }
    private static void finish(TileEntityRampController controller) { elapsed(controller,controller.durationTicks()+1); }
    private static void clear(World world,BlockPos pos) {
        if (world.getTileEntity(pos) instanceof TileEntityRampController)
            ((TileEntityRampController)world.getTileEntity(pos)).recover(true);
        for (BlockPos p:BlockPos.getAllInBox(pos.add(-5,-4,-5),pos.add(5,6,5))) world.setBlockToAir(p);
    }
    private static List<BlockPos> platform(World world,BlockPos pos,EnumFacing facing,IBlockState material) {
        List<BlockPos> sources=new ArrayList<>();
        for (int row=1;row<=3;row++) for (int width=0;width<2;width++) {
            BlockPos p=pos.offset(facing,row).offset(facing.rotateY(),width);
            world.setBlockState(p,material,3); sources.add(p);
        }
        return sources;
    }
    private static void cleanOriginals(World world,List<BlockPos> sources,IBlockState material,int drop) {
        for (BlockPos source:sources) {
            require(world.getBlockState(source).equals(material),"exact original blockstate restored");
            for (int d=-drop;d<=drop;d++) if (d!=0)
                require(world.getBlockState(source.up(d)).getBlock()!=ModBlocks.CONTROLLED_RAMP,"no residual reservation");
        }
    }
    public static void run(World world,EntityPlayerMP player) {
        double px=player.posX,py=player.posY,pz=player.posZ;
        BlockPos pos=new BlockPos(24,12,24);
        player.setPosition(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5);
        for (EnumFacing facing:EnumFacing.HORIZONTALS) for (boolean top:new boolean[]{true,false})
        for (boolean elevator:new boolean[]{false,true}) for (int kind=0;kind<3;kind++) {
            clear(world,pos);
            TileEntityRampController controller=place(world,pos,facing);
            IBlockState material=kind==2?Blocks.PLANKS.getStateFromMeta(2):Blocks.STONE_SLAB.getStateFromMeta(kind==1?8:0);
            List<BlockPos> sources=platform(world,pos,facing,material);
            BlockPos seed=pos.offset(facing),power=pos.offset(facing.getOpposite());
            require(!(controller instanceof ITickable),"controller has no polling tick");
            require(controller.configure(player,2,kind==0?8:2,top,true,false,elevator),"save settings without connecting");
            require(!controller.attached() && world.getBlockState(seed).equals(material),"idle platform remains ordinary editable blocks");
            BlockPos obstruction=seed.up(top?-1:1);
            world.setBlockState(obstruction,Blocks.DIAMOND_BLOCK.getDefaultState(),3);
            world.setBlockState(power,Blocks.REDSTONE_BLOCK.getDefaultState(),3);
            controller.updatePower();
            require(controller.error && !controller.attached(),"blocked trigger rejects entire capture");
            require(world.getBlockState(seed).equals(material),"failed deployment leaves source untouched");
            world.setBlockToAir(obstruction);
            controller.updatePower();
            require(!controller.attached(),"no retry or scanning without a new signal edge");
            require(controller.configure(player,2,kind==0?8:2,top,true,false,elevator),"configuration retries after fixing obstruction");
            require(controller.area()==6 && controller.isOpen(),"automatic matching selection on trigger");
            require(world.getTileEntity(pos)==controller,"on/off appearance preserves controller and recovery journal");
            require(world.getBlockState(pos).getValue(BlockRampController.ACTIVE),"on texture state");
            require(!ModBlocks.CONTROLLED_RAMP.removedByPlayer(world.getBlockState(seed),world,seed,player,false),"creative cannot erase journals");
            TileEntityControlledRamp part=(TileEntityControlledRamp)world.getTileEntity(seed);
            require(!(part instanceof ITickable),"reservation cells do not poll");
            elapsed(controller,controller.durationTicks()/2);
            require(Math.abs(controller.pose(0)-.5)<1e-6,"scaled animation midpoint");
            checkSideTextures(controller);
            world.setBlockToAir(power); controller.updatePower();
            require(!controller.isOpen() && Math.abs(controller.pose(0)-.5)<1e-6,"redstone reversal is continuous");
            finish(controller);
            require(!controller.attached() && !controller.isMoving(),"retraction restores and releases automatically");
            require(!world.getBlockState(pos).getValue(BlockRampController.ACTIVE),"off texture state");
            cleanOriginals(world,sources,material,2);
            // Inversion triggers immediately on saving while unpowered.
            require(controller.configure(player,2,2,top,false,true,elevator),"inverted trigger saves: "+controller.status
                    +" facing="+facing+" top="+top+" lift="+elevator+" kind="+kind);
            require(controller.attached() && controller.isOpen(),"unpowered deployment");
            finish(controller);
            require(controller.durationTicks()==48,"slow uses sixteen ticks per block");
            NBTTagCompound saved=controller.writeToNBT(new NBTTagCompound());
            controller.setRedstoneChannel(4271);
            saved=controller.writeToNBT(new NBTTagCompound());
            TileEntityRampController restored=new TileEntityRampController();
            restored.readFromNBT(saved);
            require(restored.top==top && restored.elevator==elevator && restored.slow && !restored.activateOnPower
                    && restored.getRedstoneChannel()==4271,"settings NBT");
            BlockPos far=sources.get(sources.size()-1).up(top?-2:2);
            require(!((TileEntityControlledRamp)world.getTileEntity(far)).boxes(0).isEmpty(),"correct signed endpoint height");
            require(controller.configure(player,3,2,top,false,true,elevator),"active geometry resets before new settings");
            require(controller.drop==3 && controller.pose(0)==0,"reset starts from original geometry");
            require(controller.configure(player,2,2,top,false,true,elevator),"second immediate edit preserves original journal");
            world.setBlockState(power,Blocks.REDSTONE_BLOCK.getDefaultState(),3); controller.updatePower();
            finish(controller);
            cleanOriginals(world,sources,material,2);
            world.setBlockToAir(power);
            controller.updatePower();
            require(controller.attached(),"fresh selection on next edge");
            world.setBlockToAir(pos);
            cleanOriginals(world,sources,material,2);
        }
        clear(world,pos);
        TileEntityRampController controller=place(world,pos,EnumFacing.SOUTH);
        List<BlockPos> sources=platform(world,pos,EnumFacing.SOUTH,Blocks.STONE_SLAB.getDefaultState());
        controller.setOwner(player);
        require(controller.request(true),"direct event adapter deployment");
        world.setBlockState(pos.south().down(),Blocks.DIAMOND_BLOCK.getDefaultState(),3);
        require(!controller.request(false) && controller.error,"damaged undeployment reports an error");
        require(world.getBlockState(pos.south().down()).getBlock()==Blocks.DIAMOND_BLOCK,"recovery never overwrites foreign blocks");
        cleanOriginals(world,sources,Blocks.STONE_SLAB.getDefaultState(),3);
        clear(world,pos);
        controller=place(world,pos,EnumFacing.SOUTH);
        sources=platform(world,pos,EnumFacing.SOUTH,Blocks.STONE_SLAB.getDefaultState());
        FaultController fault=new FaultController();
        world.setTileEntity(pos,fault);
        require(!fault.request(true) && fault.error,"injected partial-install failure reported");
        require(!fault.attached(),"failed install journal fully released");
        cleanOriginals(world,sources,Blocks.STONE_SLAB.getDefaultState(),3);
        clear(world,pos);
        // An interrupted save can leave loaded cells without their controller.
        controller=place(world,pos,EnumFacing.SOUTH);
        sources=platform(world,pos,EnumFacing.SOUTH,Blocks.STONE_SLAB.getDefaultState());
        require(controller.request(true),"orphan fixture deploys");
        List<TileEntityControlledRamp> orphans=new ArrayList<>();
        for (BlockPos source:sources) for (int d=0;d<=3;d++)
            orphans.add((TileEntityControlledRamp)world.getTileEntity(source.down(d)));
        world.removeTileEntity(pos);
        world.setBlockToAir(pos);
        for (TileEntityControlledRamp orphan:orphans) if (orphan!=null) orphan.recoverOnEvent();
        cleanOriginals(world,sources,Blocks.STONE_SLAB.getDefaultState(),3);
        clear(world,pos);
        controller=place(world,pos,EnumFacing.SOUTH);
        world.setBlockState(pos.south(),Blocks.FURNACE.getDefaultState(),3);
        require(!controller.request(true),"tile entities excluded");
        require(!controller.configure(player,9,2,true,true,false,false),"travel above eight rejected");

        // Real passenger motion in both directions, including collision at every intermediate tick.
        clear(world,pos);
        controller=place(world,pos,EnumFacing.SOUTH);
        world.setBlockState(pos.south(),Blocks.STONE_SLAB.getDefaultState(),3);
        require(controller.configure(player,2,2,false,true,false,true),"elevator config");
        EntityArmorStand rider=new EntityArmorStand(world,pos.getX()+.5,pos.getY()+.5,pos.getZ()+1.5);
        rider.setNoGravity(true); world.spawnEntity(rider); rider.onGround=true;
        require(controller.request(true),"elevator deploy");
        int ticks=controller.durationTicks();
        for (int tick=1;tick<=ticks;tick++) elapsed(controller,tick);
        require(Math.abs(rider.posY-(pos.getY()+2.5))<.05,"elevator carries standing entity upward");
        require(controller.request(false),"elevator retract");
        for (int tick=1;tick<=ticks;tick++) elapsed(controller,tick);
        require(Math.abs(rider.posY-(pos.getY()+.5))<.05,"elevator carries standing entity downward");
        require(!controller.attached(),"elevator retraction restores slab");
        rider.setDead();
        boolean flying=player.capabilities.isFlying,noClip=player.noClip;
        player.capabilities.isFlying=false; player.noClip=false;
        player.setPosition(pos.getX()+.5,pos.getY()+.5,pos.getZ()+1.5);
        player.onGround=true;
        require(controller.request(true),"player elevator deployment");
        for (int tick=1;tick<=ticks;tick++) elapsed(controller,tick);
        require(Math.abs(player.posY-(pos.getY()+2.5))<.05,"server player rides up with position synchronization");
        require(controller.request(false),"player elevator retraction");
        for (int tick=1;tick<=ticks;tick++) elapsed(controller,tick);
        require(Math.abs(player.posY-(pos.getY()+.5))<.05,"server player rides down");
        // Exercise vanilla movement/teleport handshakes, not just direct position changes.
        player.connection.setPlayerLocation(player.posX,player.posY,player.posZ,player.rotationYaw,player.rotationPitch);
        int teleport=teleportId(player);
        player.connection.processConfirmTeleport(new net.minecraft.network.play.client.CPacketConfirmTeleport(teleport));
        for (IBlockState riderMaterial:new IBlockState[]{Blocks.STONE_SLAB.getDefaultState(),Blocks.STONE.getDefaultState()})
        for (boolean upper:new boolean[]{true,false}) {
            world.setBlockState(pos.south(),riderMaterial,3);
            world.setBlockState(pos.south().east(),riderMaterial,3);
            require(controller.configure(player,3,2,upper,true,false,true),"network rider direction config");
            player.setPosition(pos.getX()+.95,pos.getY()+riderMaterial.getBoundingBox(world,pos.south()).maxY,pos.getZ()+1.5); player.motionY=0;
            require(controller.request(true),"network rider deployment");
            double clientY=player.posY;
            for (int tick=0;tick<controller.durationTicks();tick++) {
                player.connection.update();
                double packetY=clientY;
                world.setTotalWorldTime(world.getTotalWorldTime()+1);
                controller.scheduledTick();
                clientY=player.posY; // The preceding correction arrives at the client for its next packet.
                require(!controller.error,"network rider remains supported");
                require(teleportId(player)==teleport,"transport never starts a teleport handshake");
                double x=pos.getX()+.95+(tick%2==0?.08:-.08);
                // Simulate movement with a one-tick-old support height while the lift is active.
                player.connection.processPlayer(new net.minecraft.network.play.client.CPacketPlayer.PositionRotation(
                        x,controller.isMoving()?packetY:player.posY,pos.getZ()+1.5,tick*3,12,true));
                require(Math.abs(player.posX-x)<1e-7,"walking packets accepted during elevator motion: tick="+tick+" upper="+upper
                        +" x="+player.posX+" wanted="+x+" y="+player.posY+" packetY="+packetY+" teleport="+teleportId(player)+" initial="+teleport);
                require(Math.abs(player.rotationYaw-tick*3)<1e-7,"camera rotation is not locked during transport");
                require(teleportId(player)==teleport,"moving platform does not trigger vanilla collision rubber-banding");
            }
            require(controller.recover(false),"network rider fixture restoration");
        }
        world.setBlockState(pos.south(),Blocks.STONE_SLAB.getDefaultState(),3);
        world.setBlockToAir(pos.south().east());
        // The client correction changes only support height, preserving input and ignoring jumps.
        player.setPosition(pos.getX()+.5,pos.getY()+.5,pos.getZ()+1.5);
        player.motionX=.13; player.motionZ=.07; player.motionY=0; player.rotationYaw=73;
        com.vandorlabs.network.MessagePlatformMotion correction=new com.vandorlabs.network.MessagePlatformMotion(player,player.posY,player.posY+.1);
        io.netty.buffer.ByteBuf encoded=io.netty.buffer.Unpooled.buffer(); correction.toBytes(encoded);
        com.vandorlabs.network.MessagePlatformMotion decoded=new com.vandorlabs.network.MessagePlatformMotion();
        decoded.fromBytes(encoded); encoded.release(); decoded.apply(player);
        require(Math.abs(player.posY-(pos.getY()+.6))<1e-7,"vertical support packet roundtrip");
        require(player.motionX==.13 && player.motionZ==.07 && player.rotationYaw==73,"support correction preserves horizontal momentum and camera");
        player.motionY=.42; decoded.apply(player);
        require(player.motionY==.42,"late support update never cancels jumping");
        player.motionY=0; player.motionX=0; player.motionZ=0;
        // Use advancing world ticks and interleaved entity physics, not only timeline jumps.
        for (boolean upper:new boolean[]{true,false}) {
            require(controller.configure(player,3,2,upper,true,false,true),"rider direction edit resets");
            player.setPosition(pos.getX()+.5,pos.getY()+.5,pos.getZ()+1.5);
            player.motionY=0; player.onGround=true;
            require(controller.request(true),"real-tick rider deployment");
            for (int tick=0;tick<controller.durationTicks()+1;tick++) {
                world.setTotalWorldTime(world.getTotalWorldTime()+1);
                // Player gravity may run against the interpolated surface before transport.
                player.move(net.minecraft.entity.MoverType.SELF,0,-.08,0);
                controller.scheduledTick();
                require(!controller.error,"rider is not their own obstruction");
            }
            require(Math.abs(player.posY-(pos.getY()+.5+(upper?-3:3)))<.05,"real-tick player reaches endpoint");
            require(world.isAirBlock(pos.south()),"source is unlocked after platform leaves");
            BlockPos obstruction=pos.south().up(upper?-1:1);
            require(world.isAirBlock(obstruction),"empty travel space is released at rest");
            world.setBlockState(obstruction,Blocks.DIAMOND_BLOCK.getDefaultState(),3);
            require(controller.request(false),"return starts before reaching new obstruction");
            for (int tick=0;tick<controller.durationTicks()+1 && controller.isMoving();tick++) {
                world.setTotalWorldTime(world.getTotalWorldTime()+1); controller.scheduledTick();
            }
            require(controller.error && controller.attached() && !controller.isMoving(),"dynamic obstruction stops without losing platform");
            require(world.getBlockState(obstruction).getBlock()==Blocks.DIAMOND_BLOCK,"new construction is never overwritten");
            world.setBlockToAir(obstruction);
            require(controller.request(false),"signal event can retry blocked return");
            for (int tick=0;tick<controller.durationTicks()+1 && controller.isMoving();tick++) {
                world.setTotalWorldTime(world.getTotalWorldTime()+1); controller.scheduledTick();
            }
            require(!controller.attached(),"retry restores originals and clears dynamic cells");
        }
        // A real ceiling must still stop transport, without moving a rider into it.
        require(controller.configure(player,3,2,false,true,false,true),"ceiling fixture config");
        player.setPosition(pos.getX()+.5,pos.getY()+.5,pos.getZ()+1.5); player.motionY=0;
        require(controller.request(true),"ceiling fixture deployment");
        world.setBlockState(pos.south().up(4),Blocks.STONE.getDefaultState(),3);
        for (int tick=0;tick<controller.durationTicks()+1 && controller.isMoving();tick++) {
            world.setTotalWorldTime(world.getTotalWorldTime()+1); controller.scheduledTick();
        }
        require(controller.error && controller.status.contains("rider"),"foreign ceiling remains a rider obstruction");
        require(player.getEntityBoundingBox().maxY<=pos.getY()+4+1e-6,"rider never penetrates ceiling");
        world.setBlockToAir(pos.south().up(4));
        require(controller.configure(player,2,8,true,true,true,false),"settings safely reset a stopped occupied elevator");
        require(!controller.attached() && controller.drop==2 && controller.top && !controller.elevator,"unpowered settings reset restores and updates immediately");
        player.setPosition(pos.getX()+2,pos.getY()+1,pos.getZ());
        require(controller.configure(player,3,2,false,true,false,true),"dynamic reset obstruction config");
        require(controller.request(true),"dynamic reset obstruction deploy"); finish(controller);
        world.setBlockState(pos.south(),Blocks.DIAMOND_BLOCK.getDefaultState(),3);
        require(!controller.configure(player,1,8,true,true,true,false),"blocked source prevents destructive settings reset");
        require(controller.attached() && controller.drop==3 && !controller.top && controller.elevator,"rejected edit preserves geometry and journal");
        require(world.getBlockState(pos.south()).getBlock()==Blocks.DIAMOND_BLOCK,"rejected edit preserves construction");
        world.setBlockToAir(pos.south());
        require(controller.configure(player,1,8,true,true,true,false),"edit retries reset immediately after source cleared");
        require(!controller.attached() && controller.drop==1,"successful edit restores source without Apply");
        clear(world,pos);
        place(world,pos,EnumFacing.SOUTH);
        world.setBlockState(pos.south(),Blocks.STONE_SLAB.getDefaultState(),3);
        FaultController dynamicFault=new FaultController(2);
        world.setTileEntity(pos,dynamicFault);
        require(dynamicFault.configure(player,3,2,false,true,false,true),"dynamic fault config");
        require(dynamicFault.request(true),"first dynamic cell installs");
        for (int tick=0;tick<dynamicFault.durationTicks()+1 && dynamicFault.isMoving();tick++) {
            world.setTotalWorldTime(world.getTotalWorldTime()+1); dynamicFault.scheduledTick();
        }
        require(dynamicFault.error && dynamicFault.attached(),"mid-animation install failure retains source journal");
        require(world.isAirBlock(pos.south().up()),"failed newly installed cell rolled back");
        require(dynamicFault.recover(false),"dynamic install fault remains recoverable");
        cleanOriginals(world,java.util.Collections.singletonList(pos.south()),Blocks.STONE_SLAB.getDefaultState(),3);
        // The scan arrow and the slope direction are independent for every compass pairing.
        for (EnumFacing scan:EnumFacing.HORIZONTALS) for (EnumFacing slope:EnumFacing.HORIZONTALS) {
            clear(world,pos);
            controller=place(world,pos,scan);
            sources=platform(world,pos,scan,Blocks.STONE_SLAB.getDefaultState());
            require(controller.configure(player,2,8,true,true,false,false,slope),"independent slope direction saves");
            require(world.getBlockState(pos).getValue(BlockVandorDirectional.FACING)==scan,"direction edit never rotates selection arrow");
            require(controller.request(true) && controller.area()==6,"side-mounted controller selects arrow-side blocks");
            finish(controller);
            int farProjection=Integer.MIN_VALUE;
            BlockPos farSource=null;
            for (BlockPos source:sources) {
                int projection=source.getX()*slope.getFrontOffsetX()+source.getZ()*slope.getFrontOffsetZ();
                if (projection>farProjection) { farProjection=projection; farSource=source; }
            }
            TileEntityControlledRamp farPart=(TileEntityControlledRamp)world.getTileEntity(farSource.down(2));
            require(farPart!=null && !farPart.boxes(0).isEmpty(),"far tread descends along configured compass direction");
            require(world.getBlockState(farSource.down(2)).getValue(BlockVandorDirectional.FACING)==slope,"collision/render orientation follows slope not scan arrow");
            NBTTagCompound directionSave=controller.writeToNBT(new NBTTagCompound());
            TileEntityRampController copy=new TileEntityRampController(); copy.readFromNBT(directionSave);
            require(copy.rampDirection()==slope,"independent direction survives NBT");
            require(controller.configure(player,2,8,true,true,false,false,slope.rotateY()),"direction edit resets deployed ramp");
            cleanOriginals(world,sources,Blocks.STONE_SLAB.getDefaultState(),2);
        }
        clear(world,pos);
        controller=place(world,pos,EnumFacing.SOUTH);
        List<BlockPos> large=new ArrayList<>();
        for (int x=0;x<10;x++) for (int z=1;z<=18;z++) {
            BlockPos p=pos.add(x,0,z); world.setBlockState(p,Blocks.STONE_SLAB.getDefaultState(),3); large.add(p);
        }
        require(controller.configure(player,8,2,false,true,false,true),"eight block travel accepted");
        require(controller.request(true) && controller.area()==128,"large floor selects only eight-by-sixteen");
        for (BlockPos p:large) if (p.getX()>=pos.getX()+8 || p.getZ()>pos.getZ()+16)
            require(world.getBlockState(p).equals(Blocks.STONE_SLAB.getDefaultState()),"matching blocks beyond bounds untouched");
        finish(controller);
        require(!controller.error,"full 128-block lift moves");
        TileEntityRampController largeSave=new TileEntityRampController();
        largeSave.readFromNBT(controller.writeToNBT(new NBTTagCompound()));
        require(largeSave.area()==128,"all 128 sources survive saving");
        require(controller.recover(false),"bounded floor restores");
        require(controller.configureTreads(player,2,-3,1,true,false,false,EnumFacing.SOUTH),"sixteen-long fine ramp config");
        require(controller.area()==128 && controller.request(true),"sixteen-long fine ramp deploys");
        finish(controller);
        require(!controller.error,"sixteen-long ramp reaches end"); checkSideTextures(controller);
        require(controller.request(false),"sixteen-long ramp retracts"); finish(controller);
        require(!controller.error && controller.attached(),"sixteen-long ramp retains raised start");
        require(controller.recover(false),"sixteen-long ramp restores");
        for (BlockPos p:large) {
            require(world.getBlockState(p).equals(Blocks.STONE_SLAB.getDefaultState()),"all clipped and ignored floor blocks preserved");
            world.setBlockToAir(p);
        }
        for (boolean lift:new boolean[]{false,true}) {
            clear(world,pos);
            controller=place(world,pos,EnumFacing.SOUTH);
            List<BlockPos> signedSources=platform(world,pos,EnumFacing.SOUTH,Blocks.STONE_SLAB.getDefaultState());
            EntityArmorStand signedRider=null;
            if (lift) {
                signedRider=new EntityArmorStand(world,pos.getX()+.5,pos.getY()+.5,pos.getZ()+1.5);
                signedRider.setNoGravity(true); world.spawnEntity(signedRider); signedRider.onGround=true;
            }
            require(controller.configureOffsets(player,2,-3,8,true,false,lift,EnumFacing.SOUTH),"signed offsets configure while off");
            require(controller.attached() && !controller.isMoving() && !controller.isOpen(),"raised off position stays attached and idle");
            TileEntityRampController saved=new TileEntityRampController();
            saved.readFromNBT(controller.writeToNBT(new NBTTagCompound()));
            require(saved.startOffset==2 && saved.endOffset()==-3,"signed endpoints survive save");
            if (lift) require(Math.abs(signedRider.posY-(pos.getY()+2.5))<.05,"initial raised position carries rider");
            require(controller.request(true),"signed deployment starts");
            for (int tick=1;tick<=controller.durationTicks();tick++) elapsed(controller,tick);
            if (lift) require(Math.abs(signedRider.posY-(pos.getY()-2.5))<.05,"rider crosses original height downward");
            require(!controller.error && controller.isOpen(),"signed deployment finishes");
            checkSideTextures(controller);
            require(controller.request(false),"return to raised start");
            for (int tick=1;tick<=controller.durationTicks();tick++) elapsed(controller,tick);
            if (lift) {
                require(Math.abs(signedRider.posY-(pos.getY()+2.5))<.05,"rider crosses original height upward");
                signedRider.setDead();
            }
            require(controller.attached() && !controller.error && !controller.isMoving(),"raised start retained after retraction");
            checkSideTextures(controller);
            require(controller.recover(false),"signed recovery");
            cleanOriginals(world,signedSources,Blocks.STONE_SLAB.getDefaultState(),3);
            require(controller.configureOffsets(player,2,2,2,true,false,lift,EnumFacing.SOUTH),"equal endpoints accepted");
            require(controller.request(true) && !controller.error,"equal endpoints switch safely"); finish(controller);
            require(controller.configureOffsets(player,0,0,2,true,false,lift,EnumFacing.SOUTH),"zero endpoints accepted");
            require(!controller.attached(),"zero off restores editable originals");
        }
        for (int pixels=1;pixels<=16;pixels*=2) {
            clear(world,pos);
            controller=place(world,pos,EnumFacing.SOUTH);
            List<BlockPos> pixelSources=platform(world,pos,EnumFacing.SOUTH,Blocks.STONE_SLAB.getDefaultState());
            require(controller.configureTreads(player,2,-3,pixels,true,false,false,EnumFacing.SOUTH),"pixel tread config");
            require(!controller.configureTreads(player,2,-3,3,true,false,false,EnumFacing.SOUTH)
                    && controller.treadPixels==pixels,"unsupported tread size rejected without changing settings");
            require(controller.request(true),"pixel tread deployment"); finish(controller);
            require(!controller.error,"pixel tread deployment finishes"); checkSideTextures(controller);
            TileEntityRampController pixelCopy=new TileEntityRampController();
            pixelCopy.readFromNBT(controller.writeToNBT(new NBTTagCompound()));
            require(pixelCopy.treadPixels==pixels,"pixel tread size survives controller NBT");
            require(controller.request(false),"pixel tread returns to start"); finish(controller);
            require(!controller.error,"pixel tread retraction finishes"); checkSideTextures(controller);
            require(controller.recover(false),"pixel tread restoration");
            cleanOriginals(world,pixelSources,Blocks.STONE_SLAB.getDefaultState(),3);
        }
        clear(world,pos); controller=place(world,pos,EnumFacing.SOUTH);
        world.setBlockState(pos.south(),Blocks.STONE_SLAB.getDefaultState(),3);
        require(controller.configureTreads(player,0,-3,16,true,false,false,EnumFacing.SOUTH),"single full-block tread config");
        require(controller.request(true),"single full-block tread deployment"); finish(controller);
        require(!controller.error && world.getTileEntity(pos.south().down(3)) instanceof TileEntityControlledRamp,"single full-block tread reaches endpoint without division by zero");
        require(controller.recover(false),"single full-block tread recovery");
        for (int travel:new int[]{com.vandorlabs.ramp.RampGeometry.LEFT,com.vandorlabs.ramp.RampGeometry.RIGHT})
        for (boolean extend:new boolean[]{false,true}) {
            clear(world,pos);
            controller=place(world,pos,EnumFacing.SOUTH);
            BlockPos source=pos.south();
            world.setBlockState(source,Blocks.STONE_SLAB.getDefaultState(),3);
            require(controller.configureTreads(player,0,2,16,true,false,true,EnumFacing.SOUTH,travel,extend),
                    "side travel config accepted");
            require(controller.request(true),"side travel starts");
            finish(controller);
            require(!controller.error && controller.isOpen(),"side travel finishes");
            EnumFacing side=travel==com.vandorlabs.ramp.RampGeometry.LEFT?EnumFacing.EAST:EnumFacing.WEST;
            for (int distance=0;distance<=2;distance++) {
                BlockPos cell=source.offset(side,distance);
                boolean shouldExist=extend || distance==2;
                require((world.getTileEntity(cell) instanceof TileEntityControlledRamp)==shouldExist,
                        "side fill controls intermediate cells");
                if (shouldExist) require(!((TileEntityControlledRamp)world.getTileEntity(cell)).boxes(0).isEmpty(),
                        "side cell has rendered and collidable geometry");
            }
            TileEntityRampController copy=new TileEntityRampController();
            copy.readFromNBT(controller.writeToNBT(new NBTTagCompound()));
            require(copy.travelAxis==com.vandorlabs.ramp.RampGeometry.RIGHT && copy.extendSegments==extend
                    && copy.endOffset()==(travel==com.vandorlabs.ramp.RampGeometry.LEFT?-2:2),
                    "side settings survive NBT with signed travel");
            require(controller.recover(false),"side travel restores source");
            require(world.getBlockState(source).equals(Blocks.STONE_SLAB.getDefaultState()),
                    "side recovery restores material");
            for (int distance=1;distance<=2;distance++)
                require(world.isAirBlock(source.offset(side,distance)),"side recovery clears reservations");
        }
        clear(world,pos);
        controller=place(world,pos,EnumFacing.SOUTH);
        sources=platform(world,pos,EnumFacing.SOUTH,Blocks.STONE_SLAB.getDefaultState());
        require(controller.configureTreads(player,0,2,8,true,false,false,EnumFacing.SOUTH,
                com.vandorlabs.ramp.RampGeometry.LEFT,true),"filled ramp config accepted");
        require(!controller.elevator && controller.extendSegments,"filled ramp keeps per-tread travel");
        require(controller.request(true),"filled ramp starts"); finish(controller);
        require(!controller.error,"filled ramp finishes");
        require(controller.recover(false),"filled ramp recovers");
        require(controller.configureTreads(player,0,2,8,true,false,true,EnumFacing.SOUTH,
                com.vandorlabs.ramp.RampGeometry.LEFT,true),"extend mode config accepted");
        require(controller.elevator && controller.extendSegments,"extend mode uses whole platform travel");
        require(controller.request(true),"whole platform extension starts"); finish(controller);
        require(!controller.error,"whole platform extension finishes");
        for (BlockPos source:sources) {
            require(world.getTileEntity(source) instanceof TileEntityControlledRamp,
                    "extend keeps each starting block");
            require(world.getTileEntity(source.east(2)) instanceof TileEntityControlledRamp,
                    "extend reaches the same distance for every row");
            require(((TileEntityControlledRamp)world.getTileEntity(source)).boxes(0).size()==1,
                    "overlapping fill has one collision and render box per cell");
        }
        require(controller.recover(false),"whole platform extension recovers");
        clear(world,pos);
        controller=place(world,pos,EnumFacing.SOUTH);
        sources=platform(world,pos,EnumFacing.SOUTH,Blocks.STONE_SLAB.getDefaultState());
        require(controller.configureTreads(player,0,-3,8,false,false,false,EnumFacing.SOUTH,
                com.vandorlabs.ramp.RampGeometry.VERTICAL,true),"vertical filled ramp config accepted");
        require(controller.isOpen() && controller.extendSegments && !controller.elevator,
                "filled ramp retains tread slope and starts on inverted redstone");
        finish(controller);
        BlockPos near=pos.south(),far=pos.south(3);
        require(!controller.error && world.getTileEntity(far) instanceof TileEntityControlledRamp,
                "filled ramp keeps its starting surface");
        for (int depth=1;depth<=3;depth++)
            require(world.getTileEntity(far.down(depth)) instanceof TileEntityControlledRamp,
                    "far tread fills every intermediate block");
        require(world.isAirBlock(near.down(3)),"hinge does not fill to far tread depth");
        TileEntityRampController filledCopy=new TileEntityRampController();
        filledCopy.readFromNBT(controller.writeToNBT(new NBTTagCompound()));
        require(filledCopy.extendSegments && !filledCopy.elevator,"filled ramp mode survives NBT");
        require(controller.request(false),"filled ramp retracts"); finish(controller);
        require(!controller.attached() && !controller.error,"filled ramp releases all cells on retraction");
        for (BlockPos source:sources)
            require(world.getBlockState(source).equals(Blocks.STONE_SLAB.getDefaultState()),
                    "filled ramp restores its source blocks");
        // Changing modes through the GUI while powered resets a deployed ramp and
        // immediately captures the same source platform for the new mode.
        clear(world,pos);
        controller=place(world,pos,EnumFacing.SOUTH);
        sources=platform(world,pos,EnumFacing.SOUTH,Blocks.STONE_SLAB.getDefaultState());
        BlockPos powered=pos.north();
        world.setBlockState(powered,Blocks.REDSTONE_BLOCK.getDefaultState(),3);
        controller.updatePower();
        require(controller.configureTreads(player,0,-3,2,true,false,false,EnumFacing.SOUTH),
                "powered ramp config: "+controller.status);
        finish(controller);
        require(controller.isOpen() && !controller.error,"powered ramp deployed");
        require(controller.configureTreads(player,0,-3,2,true,false,false,EnumFacing.SOUTH,
                com.vandorlabs.ramp.RampGeometry.VERTICAL,true),
                "powered mode switch to filled ramp: "+controller.status);
        require(controller.extendSegments && !controller.elevator && controller.attached() && !controller.error,
                "powered filled ramp recaptured platform");
        finish(controller);
        require(controller.isOpen() && !controller.error,"powered filled ramp finishes: "+controller.status);
        require(controller.configureTreads(player,0,-3,2,true,false,true,EnumFacing.SOUTH,
                com.vandorlabs.ramp.RampGeometry.VERTICAL,true),
                "powered mode switch to extend: "+controller.status);
        require(controller.extendSegments && controller.attached() && !controller.error,
                "powered extend recaptured platform");
        finish(controller);
        require(controller.isOpen() && !controller.error,"powered extend finishes: "+controller.status);
        require(controller.recover(false),"powered extend recovers");
        world.setBlockToAir(powered);
        clear(world,pos);
        controller=place(world,pos,EnumFacing.SOUTH);
        sources=platform(world,pos,EnumFacing.SOUTH,Blocks.STONE_SLAB.getDefaultState());
        require(controller.configureTreads(player,0,-3,2,false,false,false,EnumFacing.SOUTH),
                "unpowered ramp config: "+controller.status);
        finish(controller);
        require(controller.configureTreads(player,0,-3,2,false,false,true,EnumFacing.SOUTH,
                com.vandorlabs.ramp.RampGeometry.VERTICAL,true),
                "unpowered mode switch to extend: "+controller.status);
        finish(controller);
        require(controller.isOpen() && !controller.error,"unpowered extend finishes");
        require(controller.recover(false),"unpowered extend recovers");
        clear(world,pos);
        controller=place(world,pos,EnumFacing.SOUTH);
        sources=platform(world,pos,EnumFacing.SOUTH,Blocks.STONE_SLAB.getDefaultState());
        require(controller.configureTreads(player,0,2,8,true,false,true,EnumFacing.SOUTH,
                com.vandorlabs.ramp.RampGeometry.RIGHT,false,0),"fast mode config accepted");
        require(controller.request(true),"fast lift starts");
        require(controller.speed==0 && controller.durationTicks()==12,"fast speed uses four ticks per block");
        elapsed(controller,controller.durationTicks()-1);
        require(!controller.error && controller.pose(0)>0.9,"fast lift does not slow sharply near the end");
        boolean movingTexture=false;
        NBTTagList fastCells=controller.writeToNBT(new NBTTagCompound()).getTagList("Cells",10);
        for (int i=0;i<fastCells.tagCount();i++) {
            BlockPos cell=BlockPos.fromLong(fastCells.getCompoundTagAt(i).getLong("Pos"));
            if (!(world.getTileEntity(cell) instanceof TileEntityControlledRamp)) continue;
            TileEntityControlledRamp part=(TileEntityControlledRamp)world.getTileEntity(cell);
            for (com.vandorlabs.ramp.RampGeometry.Box box:part.geometry(0)) {
                double[] shift=part.textureShift(box,0);
                if (Math.abs(shift[0])<1e-4 && Math.abs(shift[1])<1e-4) continue;
                movingTexture=true;
                require(box.minX+shift[0]>=-1e-6 && box.maxX+shift[0]<=1+1e-6
                        && box.minZ+shift[1]>=-1e-6 && box.maxZ+shift[1]<=1+1e-6,
                        "moving top texture samples source block coordinates");
            }
        }
        require(movingTexture,"side travel moves top texture coordinates with segment");
        finish(controller);
        require(!controller.error,"fast lift finishes");
        require(controller.recover(false),"fast lift recovers");
        for (boolean lift:new boolean[]{false,true}) for (boolean fill:new boolean[]{false,true})
        for (int travel:new int[]{com.vandorlabs.ramp.RampGeometry.VERTICAL,
                com.vandorlabs.ramp.RampGeometry.RIGHT}) for (int end:new int[]{-2,2}) {
            clear(world,pos);
            controller=place(world,pos,EnumFacing.SOUTH);
            sources=platform(world,pos,EnumFacing.SOUTH,Blocks.STONE_SLAB.getDefaultState());
            require(controller.configureTreads(player,0,end,8,true,false,lift,EnumFacing.SOUTH,
                    travel,fill),"mode/axis/sign matrix config");
            require(controller.elevator==lift && controller.extendSegments==fill
                    && controller.travelAxis==travel,"mode/axis/sign matrix settings");
            require(controller.request(true),"mode/axis/sign matrix deployment starts");
            finish(controller);
            require(controller.isOpen() && !controller.error,"mode/axis/sign matrix completes: "
                    +lift+" "+fill+" "+travel+" "+end+" "+controller.status);
            require(controller.request(false),"mode/axis/sign matrix retraction starts");
            finish(controller);
            require(!controller.attached() && !controller.error,
                    "mode/axis/sign matrix retraction restores platform");
            for (BlockPos source:sources)
                require(world.getBlockState(source).equals(Blocks.STONE_SLAB.getDefaultState()),
                        "mode/axis/sign matrix source restored");
        }
        player.capabilities.isFlying=flying; player.noClip=noClip;
        clear(world,pos);
        player.connection.setPlayerLocation(px,py,pz,player.rotationYaw,player.rotationPitch);
        System.out.println("[vandorlabs][reprolab] controller-runtime PASS ("+assertions+" assertions)");
    }
    private static final class FaultController extends TileEntityRampController {
        private int writes;
        private final int failAt;
        private FaultController() { this(3); }
        private FaultController(int failAt) { this.failAt=failAt; }
        @Override protected boolean placePart(BlockPos p,IBlockState state) {
            boolean placed=super.placePart(p,state);
            return ++writes!=failAt && placed; // Fail after mutation, before tile initialization.
        }
    }
    private static int teleportId(EntityPlayerMP player) {
        return net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(
                net.minecraft.network.NetHandlerPlayServer.class,player.connection,"teleportId","field_184363_z");
    }
    private static void checkSideTextures(TileEntityRampController controller) {
        NBTTagList cells=controller.writeToNBT(new NBTTagCompound()).getTagList("Cells",10);
        for (int i=0;i<cells.tagCount();i++) {
            BlockPos p=BlockPos.fromLong(cells.getCompoundTagAt(i).getLong("Pos"));
            TileEntityControlledRamp part=(TileEntityControlledRamp)controller.getWorld().getTileEntity(p);
            for (double partial:new double[]{0,.5}) for (net.minecraft.util.math.AxisAlignedBB box:part.boxes(partial)) {
                double bottom=part.sideTextureV(box,box.minY,partial),top=part.sideTextureV(box,box.maxY,partial);
                require(top>=1-part.high-1e-7 && bottom<=1-part.low+1e-7,"clipped side samples original slab/cube texture region");
                require(Math.abs((bottom-top)-(box.maxY-box.minY))<1e-7,"clipped side texture is not stretched or reset per cell");
                if (box.minY>1e-7) require(Math.abs(bottom-(1-part.low))<1e-7,"unclipped bottom keeps its original texture coordinate");
                if (box.maxY<1-1e-7) require(Math.abs(top-(1-part.high))<1e-7,"unclipped top keeps its original texture coordinate");
            }
        }
    }

    public static void buildFixture(World world,EntityPlayerMP player) {
        double px=player.posX,py=player.posY,pz=player.posZ;
        for (int variant=0;variant<4;variant++) {
            BlockPos root=FIXTURE.add(variant*8,0,0);
            player.setPosition(root.getX(),root.getY()+1,root.getZ());
            TileEntityRampController controller=place(world,root,EnumFacing.SOUTH);
            for (int x=0;x<3;x++) for (int z=1;z<=4;z++) {
                BlockPos p=root.add(x,0,z);
                for (int y=-3;y<=3;y++) world.setBlockToAir(p.up(y));
                world.setBlockState(p,variant>=2?Blocks.BRICK_BLOCK.getDefaultState():Blocks.STONE_SLAB.getDefaultState(),3);
            }
            require(controller.configure(player,3,8,variant%2==0,true,false,variant>=2),"render fixture config");
        }
        player.setPosition(px,py,pz);
    }

    /** Build and fully deploy one isolated documentation fixture using the real controller path. */
    public static void buildGalleryFixture(World world,EntityPlayerMP player,BlockPos root,
            boolean upper,boolean smooth) {
        double px=player.posX,py=player.posY,pz=player.posZ;
        player.setPosition(root.getX()+.5,root.getY()+1,root.getZ()+.5);
        TileEntityRampController controller=place(world,root,EnumFacing.SOUTH);
        for (int row=1;row<=4;row++) for (int width=-1;width<=1;width++)
            world.setBlockState(root.south(row).west(width),
                    Blocks.STONE_SLAB.getDefaultState(),3);
        if (!controller.configure(player,3,smooth?8:2,upper,true,false,false,
                EnumFacing.SOUTH))
            throw new IllegalStateException("gallery ramp configuration failed: "+controller.status);
        world.setBlockState(root.north(),Blocks.REDSTONE_BLOCK.getDefaultState(),3);
        controller.updatePower();
        elapsed(controller,controller.durationTicks()+1);
        player.setPosition(px,py,pz);
    }
}
