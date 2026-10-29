package com.vandorlabs.client;

import com.vandorlabs.cockpit.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;

final class CockpitMechanismRuntimeChecks {
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("cockpit mechanism: "+message);}
    static void run(WorldServer world,EntityPlayerMP player,TileEntityCockpit tile){
        if(tile.design().equals("chair_siderests")){
            CockpitData original=CockpitData.get(tile.design());
            for(int height=0;height<4;height++){
                com.vandorlabs.container.ContainerCockpit dialog=new com.vandorlabs.container.ContainerCockpit(tile,null);require(dialog.canInteractWith(player),"height dialog reachable");
                NBTTagCompound casing=new NBTTagCompound();casing.setInteger("Height",height);casing.setInteger("SideTexture",tile.sideTexture());dialog.update(player,0,casing);
                NBTTagCompound pilot=new NBTTagCompound();pilot.setBoolean("Pilot",true);pilot.setBoolean("Taxi",false);pilot.setInteger("Trigger",tile.getRedstoneMode());pilot.setString("Channels",ChannelFields.text(tile.getRedstoneChannels()));for(int mode=0;mode<3;mode++)pilot.setString("Mode"+mode,tile.modeChannels(mode).toString());dialog.update(player,0,pilot);
                dialog=new com.vandorlabs.container.ContainerCockpit(tile,null);require(dialog.tile.getHeight()==height,"height survives pilot page save and dialog reopening");double drop=(6-height*2)/16D;
                require(Math.abs(tile.data().seat.y-(original.seat.y-drop))<1e-8,"cushion height in two-pixel steps");
                require(Math.abs(tile.data().surfaces.get(0).origin.y-(original.surfaces.get(0).origin.y-drop))<1e-8,"armrest touch height follows seat");
                require(Math.abs(tile.seatHeight()-(original.riderY+.725-drop))<1e-8,"rider follows cushion");
                TileEntityCockpit copy=(TileEntityCockpit)net.minecraft.tileentity.TileEntity.create(world,tile.writeToNBT(new NBTTagCompound()));require(copy.getHeight()==height,"height survives save");
                sliders(tile,player);
                for(AxisAlignedBB b:tile.data().boxes)require(b.minY>=0&&b.maxY>b.minY,"lowered supports stay above floor");
            }
            tile.setHeight(0);return;
        }
        if(!tile.design().equals("cockpit"))return;
        CockpitActuator skew=new CockpitActuator();skew.toggle(100);require(skew.sample(98)==0&&skew.sample(115)>.4&&skew.sample(130)==1,"small client clock skew holds initial pose");
        net.minecraft.block.Block block=world.getBlockState(tile.getPos()).getBlock();
        require(block.getStateForPlacement(world,tile.getPos(),net.minecraft.util.EnumFacing.UP,0,0,0,0,player).getValue(com.vandorlabs.blocks.BlockVandorDirectional.FACING)==player.getHorizontalFacing(),"cockpit places along player facing");
        require(tile.data().moving.values().stream().filter(part->part==1).count()<=32,"rigid seat collision boxes are not sliced redundantly");
        PlainCanopyGlassPilotChecks.run(world,player,tile.facing());canopies(tile,player);rearCanopies(tile,player);wallCanopies(tile,player);CockpitCanopyInteractionChecks.movement(tile,player);
        CanopyOrientationChecks.run(world,player);
        levers(tile,player);
        NBTTagCompound before=tile.writeToNBT(new NBTTagCompound());
        tile.toggleScreens();require(tile.screenLift.target(),"screen button retracts");finish(tile,"CockpitScreenLift",world.getTotalWorldTime());require(Math.abs(tile.screens(0)-1)<1e-8,"screen bank reaches housing");
        TileEntityCockpit saved=(TileEntityCockpit)net.minecraft.tileentity.TileEntity.create(world,tile.writeToNBT(new NBTTagCompound()));saved.setWorld(world);require(saved.screenLift.target()&&saved.screens(0)==1,"screen pose persists");
        tile.readFromNBT(before);
        java.util.Set<BlockPos> rear=CockpitReservations.rear(tile,tile.facing());require(!rear.isEmpty(),"seat has a rear sweep");BlockPos obstruction=rear.iterator().next();
        world.setBlockState(obstruction,net.minecraft.init.Blocks.STONE.getDefaultState(),2);require(!tile.toggleSeat(player)&&!tile.seatSlide.target(),"occupied rear refuses extension");world.setBlockToAir(obstruction);
        require(tile.toggleSeat(player),"clear seat extension");for(BlockPos p:rear)require(CockpitAssembly.root(world,p)==tile,"rear reservation owned");
        finish(tile,"CockpitSeatSlide",world.getTotalWorldTime());require(Math.abs(tile.seatLocation().z-tile.data().seat.z-1)<1e-8,"seat travels one block backwards");levers(tile,player);
        require(tile.toggleSeat(player),"seat returns");finish(tile,"CockpitSeatSlide",world.getTotalWorldTime());CockpitReservations.tick(tile);for(BlockPos p:rear)require(world.isAirBlock(p),"rear cells released after return");
        tile.readFromNBT(before);
        com.vandorlabs.entity.EntityChairSeat seat=new com.vandorlabs.entity.EntityChairSeat(world,tile.getPos(),tile.seatHeight());
        io.netty.buffer.ByteBuf spawn=io.netty.buffer.Unpooled.buffer();seat.writeSpawnData(spawn);com.vandorlabs.entity.EntityChairSeat received=new com.vandorlabs.entity.EntityChairSeat(world);received.readSpawnData(spawn);spawn.release();require(tile.getPos().equals(received.getChairPos()),"mount spawn carries exact chair anchor");
        for(boolean returning:new boolean[]{false,true})for(int age=0;age<=30;age++){
            NBTTagCompound motion=before.copy(),state=new NBTTagCompound();state.setBoolean("Target",!returning);state.setDouble("From",returning?1:0);state.setLong("Started",world.getTotalWorldTime()-age);motion.setTag("CockpitSeatSlide",state);tile.readFromNBT(motion);
            for(float partial:new float[]{0,.25F,.5F,.75F}){Vec3d expected=CockpitData.rotate(tile.seatLocation(partial),tile.facing()).add(new Vec3d(tile.getPos()));Vec3d rider=received.cockpitRiderPosition(player,partial);require(Math.abs(expected.x-rider.x)<1e-8&&Math.abs(expected.z-rider.z)<1e-8,"rider stays on cushion throughout reversible motion");}
        }
        tile.readFromNBT(before);
    }
    private static void wallCanopies(TileEntityCockpit tile,EntityPlayerMP player){
        net.minecraft.world.World world=tile.getWorld();net.minecraft.util.EnumFacing facing=tile.facing(),back=facing.getOpposite();
        java.util.List<BlockPos> body=tile.data().cells(tile.getPos(),facing);net.minecraft.item.ItemStack held=player.getHeldItemMainhand();boolean creative=player.capabilities.isCreativeMode;Vec3d old=player.getPositionVector();float yaw=player.rotationYaw,pitch=player.rotationPitch;int cases=0;
        try{player.capabilities.isCreativeMode=true;
            for(String id:new String[]{"canopy","long_canopy","regular_canopy_glass"})for(BlockPos rear:body){
                if(rear.getY()!=tile.getPos().getY()+1||body.contains(rear.offset(back)))continue;
                BlockPos wall=rear.offset(back);world.setBlockState(wall,net.minecraft.init.Blocks.STONE.getDefaultState(),2);
                com.vandorlabs.blocks.BlockCanopy block=(com.vandorlabs.blocks.BlockCanopy)net.minecraft.block.Block.getBlockFromName("vandorlabs:"+id);net.minecraft.item.ItemStack stack=new net.minecraft.item.ItemStack(block);player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,stack);
                Vec3d target=new Vec3d(wall).addVector(.5,.75,.5),eye=target.add(new Vec3d(facing.getDirectionVec()).scale(3));player.setPosition(eye.x,eye.y-player.getEyeHeight(),eye.z);player.rotationYaw=back.getHorizontalAngle();player.rotationPitch=0;
                RayTraceResult hit=world.rayTraceBlocks(player.getPositionEyes(1),player.getPositionEyes(1).add(player.getLookVec().scale(5)),false,false,false);
                require(hit!=null&&hit.getBlockPos().equals(wall)&&hit.sideHit==facing,"wall-behind-seat ray reaches wall face");
                require(((net.minecraft.item.ItemBlock)stack.getItem()).canPlaceBlockOnSide(world,wall,facing,player,stack),"client accepts canopy from wall directly behind cockpit "+id+"/"+facing);
                require(player.interactionManager.processRightClickBlock(player,world,stack,net.minecraft.util.EnumHand.MAIN_HAND,wall,facing,(float)(hit.hitVec.x-wall.getX()),(float)(hit.hitVec.y-wall.getY()),(float)(hit.hitVec.z-wall.getZ()))==net.minecraft.util.EnumActionResult.SUCCESS,"server wall-behind-seat placement");
                TileEntityCanopy shell=TileEntityCanopy.at(world,rear);require(shell!=null&&shell.constituent().getPos().equals(rear.offset(facing,block.length-1)),"canopy rear sits flush against wall and extends forward");
                require((!block.opening()||shell.owner().facing()==facing)&&CockpitAssembly.root(world,rear)==tile&&!CockpitAssembly.boxes(world,rear).isEmpty(),"shared cockpit geometry preserved");
                world.destroyBlock(shell.constituent().getPos(),false);require(world.getBlockState(wall).getBlock()==net.minecraft.init.Blocks.STONE&&CockpitAssembly.root(world,rear)==tile,"wall and cockpit retained after canopy removal");world.setBlockToAir(wall);cases++;
            }
        }finally{player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,held);player.capabilities.isCreativeMode=creative;player.setPosition(old.x,old.y,old.z);player.rotationYaw=yaw;player.rotationPitch=pitch;}
        require(cases==6,"both wall columns, opening canopies and plain glass checked");System.out.println("[vandorlabs][reprolab] cockpit-wall-canopy PASS cases="+cases+" "+facing+" real wall ray client prediction server placement retained wall and cockpit");
    }
    private static void rearCanopies(TileEntityCockpit tile,EntityPlayerMP player){
        net.minecraft.world.World world=tile.getWorld();net.minecraft.util.EnumFacing facing=tile.facing(),back=facing.getOpposite();
        BlockPos rear=tile.getPos();for(BlockPos cell:tile.data().cells(tile.getPos(),facing))if(cell.getY()==tile.getPos().getY() && cell.subtract(rear).getX()*back.getDirectionVec().getX()+cell.subtract(rear).getZ()*back.getDirectionVec().getZ()>0)rear=cell;
        net.minecraft.item.ItemStack held=player.getHeldItemMainhand();boolean creative=player.capabilities.isCreativeMode;player.capabilities.isCreativeMode=true;
        try{for(String id:new String[]{"canopy","long_canopy"})for(int height=0;height<=1;height++){
            BlockPos clicked=rear.up(height),wall=rear.offset(back).up();world.setBlockState(wall,net.minecraft.init.Blocks.STONE.getDefaultState(),2);
            com.vandorlabs.blocks.BlockCanopy canopy=(com.vandorlabs.blocks.BlockCanopy)net.minecraft.block.Block.getBlockFromName("vandorlabs:"+id);
            net.minecraft.item.ItemStack stack=new net.minecraft.item.ItemStack(canopy);player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,stack);
            require(((net.minecraft.item.ItemBlock)stack.getItem()).canPlaceBlockOnSide(world,clicked,back,player,stack),"client rear cockpit placement accepted");
            require(player.interactionManager.processRightClickBlock(player,world,stack,net.minecraft.util.EnumHand.MAIN_HAND,clicked,back,.5F,.5F,.5F)==net.minecraft.util.EnumActionResult.SUCCESS,"rear canopy placement without rear clearance "+id+"/"+facing+"/"+height);
            TileEntityCanopy shell=TileEntityCanopy.at(world,rear.up());require(shell!=null,"canopy back aligned with cockpit back");shell=shell.constituent();
            for(BlockPos cell:shell.cells())require(cell.subtract(rear).getX()*back.getDirectionVec().getX()+cell.subtract(rear).getZ()*back.getDirectionVec().getZ()<=0,"shell does not extend behind cockpit");
            world.destroyBlock(shell.constituent().getPos(),false);require(world.getBlockState(wall).getBlock()==net.minecraft.init.Blocks.STONE,"rear wall retained");world.setBlockToAir(wall);
            require(CockpitAssembly.root(world,rear.up())==tile,"cockpit restored after aligned canopy removal");
            System.out.println("[vandorlabs][reprolab] cockpit-rear-canopy PASS "+id+"/"+facing+"/"+height);
        }}finally{player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,held);player.capabilities.isCreativeMode=creative;}
    }
    private static void canopies(TileEntityCockpit tile,EntityPlayerMP player){
        net.minecraft.item.ItemStack held=player.getHeldItemMainhand();boolean creative=player.capabilities.isCreativeMode;player.capabilities.isCreativeMode=true;
        try{for(String id:new String[]{"canopy","long_canopy","regular_canopy_glass"})for(BlockPos anchor:tile.data().cells(tile.getPos(),tile.facing())){
            if(anchor.getY()!=tile.getPos().getY()+1)continue;
            com.vandorlabs.blocks.BlockCanopy canopy=(com.vandorlabs.blocks.BlockCanopy)net.minecraft.block.Block.getBlockFromName("vandorlabs:"+id);
            net.minecraft.item.ItemStack stack=new net.minecraft.item.ItemStack(canopy);player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,stack);
            require(!CockpitAssembly.activate(tile.getWorld(),tile.getPos(),player,net.minecraft.util.EnumHand.MAIN_HAND),"held canopy reaches placement");
            java.util.List<AxisAlignedBB> body=CockpitAssembly.boxes(tile.getWorld(),anchor);
            require(!body.isEmpty(),"raised cockpit geometry exercised");
            require(((net.minecraft.item.ItemBlock)stack.getItem()).canPlaceBlockOnSide(tile.getWorld(),anchor,net.minecraft.util.EnumFacing.UP,player,stack),"client accepts raised cockpit click");
            require(player.interactionManager.processRightClickBlock(player,tile.getWorld(),stack,net.minecraft.util.EnumHand.MAIN_HAND,anchor,net.minecraft.util.EnumFacing.UP,.5F,.1F,.5F)==net.minecraft.util.EnumActionResult.SUCCESS,"canopy over raised cockpit "+id+tile.facing());
            require(tile.getWorld().getBlockState(anchor).getBlock()==canopy,"shell shares upper cockpit reservation");
            require(CockpitAssembly.root(tile.getWorld(),tile.getPos())==tile,"canopy retains cockpit");
            require(CockpitAssembly.boxes(tile.getWorld(),anchor).equals(body),"shared raised cockpit collision retained");
            require(CockpitAssembly.root(tile.getWorld(),anchor)==tile,"shared cell resolves cockpit controls");
            TileEntityCanopy shell=TileEntityCanopy.at(tile.getWorld(),anchor);
            CockpitCanopyInteractionChecks.configuration(tile,shell,player);
            if(shell.opening()){
                shell.targetOpen=true;shell.progress=shell.previous=1;
                com.vandorlabs.canopy.CanopyReservations.releaseOpen(shell);
                require(CockpitAssembly.root(tile.getWorld(),anchor)==tile,"opening shell preserves cockpit");
                require(com.vandorlabs.canopy.CanopyReservations.restoreFootprint(shell),"closing shell accepts shared cockpit helpers");
                shell.targetOpen=false;shell.progress=shell.previous=0;
            }
            tile.getWorld().destroyBlock(anchor,false);
            require(tile.getWorld().getTileEntity(anchor) instanceof TileEntityCockpitPart,"removing canopy restores upper reservation");require(CockpitAssembly.root(tile.getWorld(),tile.getPos())==tile,"canopy removal retains cockpit");
        }}finally{player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,held);player.capabilities.isCreativeMode=creative;}
    }
    private static void sliders(TileEntityCockpit tile,EntityPlayerMP player){
        Vec3d eye=new Vec3d(tile.data().seat.x,tile.data().riderY+player.getEyeHeight(),tile.data().seat.z);
        for(java.util.Map.Entry<String,Vec3d> part:tile.data().pivots.entrySet())for(int step=0;step<4;step++){
            for(TileEntityCockpit.Control output:tile.controls)output.level=step*5;
            TileEntityCockpit.Control selected=tile.control(part.getKey());
            Vec3d target=part.getValue().addVector(0,.1,-step/16D);
            Vec3d start=CockpitData.rotate(eye,tile.facing()).add(new Vec3d(tile.getPos()));
            Vec3d end=start.add(CockpitData.rotate(target,tile.facing()).add(new Vec3d(tile.getPos())).subtract(start).normalize().scale(6));
            RayTraceResult hit=tile.getWorld().rayTraceBlocks(start,end,false,false,false);
            require(hit!=null&&CockpitAssembly.root(tile.getWorld(),hit.getBlockPos())==tile,"world ray reaches armrest "+part.getKey());
            require(tile.use(start.subtract(new Vec3d(tile.getPos())),end.subtract(new Vec3d(tile.getPos())),player),"armrest activation");
            require(selected.level==(step+1)%4*5,"correct armrest slider "+part.getKey()+" height="+tile.getHeight()+" facing="+tile.facing()+" step="+step);
            for(TileEntityCockpit.Control other:tile.controls)if(other!=selected)require(other.level==step*5,"other armrest output unchanged");
        }
    }
    private static void finish(TileEntityCockpit tile,String key,long now){NBTTagCompound tag=tile.writeToNBT(new NBTTagCompound());tag.getCompoundTag(key).setLong("Started",now-CockpitActuator.DURATION);tile.readFromNBT(tag);}
    static void levers(TileEntityCockpit tile,EntityPlayerMP player){
        Vec3d eye=new Vec3d(tile.seatLocation().x,tile.data().riderY+player.getEyeHeight(),tile.seatLocation().z);
        for(java.util.Map.Entry<String,Vec3d> part:tile.data().pivots.entrySet())if(part.getKey().startsWith("throttle_"))for(int step=0;step<4;step++){
            TileEntityCockpit.Control c=tile.control(part.getKey());c.kind=1;c.level=step*5;double angle=Math.toRadians(22.5-step*22.5);
            Vec3d target=part.getValue().addVector(0,Math.cos(angle)*4.5/16,Math.sin(angle)*4.5/16+tile.seatSlide(0)),end=eye.add(target.subtract(eye).normalize().scale(8));
            require(tile.controlRay(CockpitData.rotate(eye,tile.facing()),CockpitData.rotate(end,tile.facing()))!=null,"actual grip selection ray");
            require(tile.use(CockpitData.rotate(eye,tile.facing()),CockpitData.rotate(end,tile.facing()),player),"seated lever ray");require(c.level==(step+1)%4*5,"seated ray selects correct lever "+part.getKey()+" step "+step);
        }
    }
    static void vehicle(EntityGroundVehicle craft,TileEntityCockpit tile,EntityPlayerMP player){
        if(player.getRidingEntity()!=craft)require(craft.board(player,craft.structure.seat),"pilot seated for mechanism check");
        tile.toggleScreens();require(tile.screenLift.target(),"server-owned vehicle screen button");tile.toggleScreens();
        levers(tile,player);Vec3d before=craft.toWorld(new Vec3d(tile.getPos()).add(tile.data().seat));
        require(tile.toggleSeat(player),"vehicle seat extends with clear sweep: "+tile.seatBlockReason);TileEntityCockpit moved=(TileEntityCockpit)craft.view.getTileEntity(craft.structure.seat);
        Vec3d after=craft.toWorld(new Vec3d(moved.getPos()).add(moved.data().seat));require(before.squareDistanceTo(after)<1e-8,"expanding vehicle preserves anchor world pose");
        require(moved.seatSlide.target(),"vehicle extension target survives snapshot");finish(moved,"CockpitSeatSlide",craft.world.getTotalWorldTime());
        for(BlockPos p:CockpitReservations.rear(moved,moved.facing()))require(CockpitAssembly.root(craft.view,p)==moved,"vehicle contains rear seat reservations");
        require(moved.toggleSeat(player),"vehicle seat returns");finish(moved,"CockpitSeatSlide",craft.world.getTotalWorldTime());craft.signals.cockpitChanged(moved);
        System.out.println("[vandorlabs][reprolab] cockpit-mechanisms PASS four heights seated-lever-rays reversible-screen-lift blocked-seat-sweep rear-reservations pose-preserving-vehicle-extension");
    }
}
