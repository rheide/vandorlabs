package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.*;

/** Real source transactions and signal edges, including upright zero-offset saves. */
final class MoatRuntimeChecks {
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("Moat: "+message);}
    private static List<TileEntityControlledRamp> parts(World world,BlockPos root){
        List<TileEntityControlledRamp> result=new ArrayList<>();
        for(BlockPos p:BlockPos.getAllInBox(root.add(-5,-4,-5),root.add(5,8,5)))
            if(world.getTileEntity(p) instanceof TileEntityControlledRamp){TileEntityControlledRamp part=(TileEntityControlledRamp)world.getTileEntity(p);if(part.belongsTo(root))result.add(part);}
        return result;
    }
    private static AxisAlignedBB bounds(World world,BlockPos root){
        AxisAlignedBB result=null;
        for(TileEntityControlledRamp part:parts(world,root))for(AxisAlignedBB local:part.boxes(0)){AxisAlignedBB box=local.offset(part.getPos());result=result==null?box:result.union(box);}
        require(result!=null,"endpoint has no collision geometry");return result;
    }
    static void run(World world,EntityPlayerMP owner){
        BlockPos root=new BlockPos(88,40,88);double x=owner.posX,y=owner.posY,z=owner.posZ;
        try{
            owner.setPosition(root.getX()-10,root.getY()+8,root.getZ()-10);
            for(EnumFacing face:EnumFacing.HORIZONTALS)for(boolean slab:new boolean[]{false,true}){
                BlockPos actor=root.offset(face.getOpposite(),2).up(2);owner.setPosition(actor.getX()+.5,actor.getY(),actor.getZ()+.5);
                require(owner.getDistanceSqToCenter(root)<64,"configuration actor stays within reach: "+owner.getPosition());
                for(BlockPos p:BlockPos.getAllInBox(root.add(-5,-4,-5),root.add(5,8,5)))world.setBlockToAir(p);
                world.setBlockState(root,ModBlocks.PROGRAMMABLE_RAMP.getDefaultState().withProperty(BlockVandorDirectional.FACING,face),3);
                TileEntityRampController ramp=(TileEntityRampController)world.getTileEntity(root);
                IBlockState material=slab?Blocks.STONE_SLAB.getStateFromMeta(8):Blocks.PLANKS.getStateFromMeta(2);
                List<BlockPos> sources=new ArrayList<>();
                for(int level=0;level<3;level++)for(int lane=0;lane<2;lane++){BlockPos source=root.offset(face).up(level).offset(face.rotateY(),lane);world.setBlockState(source,material,3);sources.add(source);}
                require(ramp.configureHalfOffsets(owner,0,0,8,true,false,false,face,0,false,1,true,false,0,0,true),"configure vertical source tower: "+ramp.status);
                require(ramp.attached()&&!ramp.isOpen()&&!ramp.isMoving(),"off at zero holds an upright journal");
                AxisAlignedBB closed=bounds(world,root);
                require(Math.abs(closed.maxY-(root.getY()+3))<1e-5,"three source levels retain their actual upright positions");
                NBTTagCompound saved=ramp.writeToNBT(new NBTTagCompound());
                require(saved.getBoolean("MoatVerticalSources"),"new capture journals explicitly identify vertical sources");
                ramp.readFromNBT(saved);require(ramp.moat&&ramp.attached(),"controller reload retains mode and source journal");
                for(TileEntityControlledRamp part:parts(world,root)){
                    TileEntityControlledRamp copy=new TileEntityControlledRamp();copy.setWorld(world);copy.setPos(part.getPos());copy.readFromNBT(part.writeToNBT(new NBTTagCompound()));
                    require(copy.moat&&copy.verticalMoatSources&&copy.boxes(0).equals(part.boxes(0)),"saved generated cells retain vertical-source upright collision");
                    require(part.geometry(0)==part.geometry(.9),"settled upright geometry is cached");
                }
                ramp.setChannelSignal(true);ControllerRuntimeChecks.elapsed(ramp,ramp.durationTicks()/2);
                require(ramp.isOpen()&&ramp.isMoving()&&ramp.pose(0)>.4&&ramp.pose(0)<.6,"channel edge rotates through midpoint");
                bounds(world,root);
                ControllerRuntimeChecks.elapsed(ramp,ramp.durationTicks()*3/4);
                for(TileEntityControlledRamp part:parts(world,root)){
                    NBTTagCompound cell=part.writeToNBT(new NBTTagCompound());TileEntityControlledRamp loaded=new TileEntityControlledRamp();loaded.setWorld(world);loaded.setPos(part.getPos());loaded.readFromNBT(cell);
                    require(loaded.row==part.row,"mid-arc cell retains raw reservation row");require(loaded.boxes(0).equals(part.boxes(0)),"mid-arc saved collision retains one hinge");
                }
                ControllerRuntimeChecks.elapsed(ramp,ramp.durationTicks()+1);
                AxisAlignedBB opened=bounds(world,root);
                require(Math.abs(opened.maxY-root.getY())<1e-5,"deployed deck top meets the bottom adjacency hinge");
                require(Math.abs(opened.minY-(root.getY()-1))<1e-5,"source horizontal thickness rotates below the deck");
                BlockPos seed=root.offset(face);int hinge=seed.getX()*face.getFrontOffsetX()+seed.getZ()*face.getFrontOffsetZ();
                for(int level=0;level<3;level++){
                    double[] point=com.vandorlabs.ramp.MoatGeometry.vertex(com.vandorlabs.ramp.RampGeometry.Direction.valueOf(face.name()),hinge,root.getY(),3,0,0,1,false,seed.getX()+.5,root.getY()+level+.25,seed.getZ()+.5);
                    require(contains(world,root,new Vec3d(point[0],point[1],point[2]))!=slab,"physical top-slab gap rotates with source rather than stretching into a solid deck");
                }
                ramp.setChannelSignal(false);ControllerRuntimeChecks.elapsed(ramp,ramp.durationTicks()+1);
                require(ramp.attached()&&!ramp.isOpen()&&!ramp.isMoving(),"off signal returns upright without restoring flat sources");
                require(ramp.recover(false),"explicit recovery succeeds");
                for(BlockPos source:sources)require(world.getBlockState(source).equals(material),"original material and slab half restored");
                require(parts(world,root).isEmpty(),"recovery clears every generated cell");
                BlockPos obstacle=root.offset(face,2).up(2);world.setBlockState(obstacle,Blocks.DIAMOND_BLOCK.getDefaultState(),3);
                require(!ramp.configureHalfOffsets(owner,0,0,8,true,false,false,face,0,false,1,true,false,0,0,true),"swept interior obstruction rejects capture");
                for(BlockPos source:sources)require(world.getBlockState(source).equals(material),"failed arc capture preserves source");
                world.setBlockToAir(obstacle);
                require(ramp.configureHalfOffsets(owner,0,0,8,true,false,false,face,0,false,1,true,false,0,0,true),"retry after clearing arc");
                require(ramp.configureHalfOffsets(owner,0,0,8,true,false,false,face,0,false,1,true,false,0,0,false),"mode change safely restores journal");
                require(!ramp.attached()&&parts(world,root).isEmpty(),"ordinary zero-offset off mode releases cells");
                for(BlockPos source:sources)require(world.getBlockState(source).equals(material),"mode change restores original vertical tower");
                require(ramp.configureHalfOffsets(owner,0,0,8,true,false,false,face,0,false,1,true,false,0,0,true),"orphan fixture captures");
                List<TileEntityControlledRamp> owned=parts(world,root);
                for(TileEntityControlledRamp part:owned){part.onLoad();part.recoverOnEvent();}
                require(ramp.attached()&&!parts(world,root).isEmpty(),"legitimate cells survive recovery event");
                world.removeTileEntity(root);world.setBlockToAir(root);
                for(TileEntityControlledRamp part:owned)if(world.getTileEntity(part.getPos())==part)part.recoverOnEvent();
                for(BlockPos source:sources)require(world.getBlockState(source).equals(material),"missing controller tile restores exact source");
                require(parts(world,root).isEmpty(),"orphan event clears invisible reservation cells");
            }
            legacyFlatJournal(world,owner,root);
            angularAndTreads(world,owner,root);
            carried(world);
            System.out.println("[vandorlabs][reprolab] moat-world PASS cardinal physical-slab vertical-source bottom-hinge straight-rotation downward-thickness source-journal midpoint reload obstruction mode-reset cache orphan carried-endpoints legacy-flat-journal");
        }finally{
            if(world.getTileEntity(root) instanceof TileEntityRampController)((TileEntityRampController)world.getTileEntity(root)).recover(false);
            for(BlockPos p:BlockPos.getAllInBox(root.add(-5,-4,-5),root.add(5,8,5)))world.setBlockToAir(p);
            owner.setPosition(x,y,z);
        }
    }
    /** Reproduce an actual pre-vertical-source save: an expanded flat source journal,
     * with no version flag on either the controller or its generated cells. */
    private static void legacyFlatJournal(World world,EntityPlayerMP owner,BlockPos root){
        for(EnumFacing face:EnumFacing.HORIZONTALS){
            if(world.getTileEntity(root) instanceof TileEntityRampController)((TileEntityRampController)world.getTileEntity(root)).recover(false);
            for(BlockPos p:BlockPos.getAllInBox(root.add(-5,-4,-5),root.add(5,8,5)))world.setBlockToAir(p);
            BlockPos actor=root.offset(face.getOpposite(),2).up(2);owner.setPosition(actor.getX()+.5,actor.getY(),actor.getZ()+.5);
            world.setBlockState(root,ModBlocks.PROGRAMMABLE_RAMP.getDefaultState().withProperty(BlockVandorDirectional.FACING,face),3);
            TileEntityRampController ramp=(TileEntityRampController)world.getTileEntity(root);
            List<BlockPos> sources=new ArrayList<>();
            for(int row=1;row<=3;row++)for(int lane=0;lane<2;lane++){
                BlockPos at=root.offset(face,row).offset(face.rotateY(),lane);sources.add(at);world.setBlockState(at,Blocks.PLANKS.getStateFromMeta(2),3);
            }
            // The zero-offset ordinary endpoint is exactly the former Moat deployed
            // endpoint, so its journal/cells give the old save's native flat layout.
            require(ramp.configureHalfOffsets(owner,0,0,8,true,false,false,face,0,false,1,true,false,0,0,false),"legacy flat fixture configuration");
            require(ramp.request(true),"legacy flat fixture capture");ControllerRuntimeChecks.elapsed(ramp,ramp.durationTicks()+1);
            NBTTagCompound old=ramp.writeToNBT(new NBTTagCompound());old.setBoolean("MoatMode",true);old.removeTag("MoatVerticalSources");ramp.readFromNBT(old);
            for(TileEntityControlledRamp part:parts(world,root)){
                NBTTagCompound saved=part.writeToNBT(new NBTTagCompound());saved.setBoolean("MoatMode",true);saved.removeTag("MoatVerticalSources");part.readFromNBT(saved);
            }
            require(!ramp.writeToNBT(new NBTTagCompound()).getBoolean("MoatVerticalSources"),"unversioned controller retains legacy interpretation");
            require(ramp.request(false),"old flat journal closes without vertical recapture: "+ramp.status);ControllerRuntimeChecks.elapsed(ramp,ramp.durationTicks()+1);
            AxisAlignedBB closed=bounds(world,root);require(Math.abs(closed.minY-root.getY()-1)<1e-5&&Math.abs(closed.maxY-root.getY()-4)<1e-5,"legacy flat sources still become the original three-high raised wall");
            ramp.readFromNBT(ramp.writeToNBT(new NBTTagCompound()));
            for(TileEntityControlledRamp part:parts(world,root)){
                NBTTagCompound saved=part.writeToNBT(new NBTTagCompound());TileEntityControlledRamp loaded=new TileEntityControlledRamp();loaded.setWorld(world);loaded.setPos(part.getPos());loaded.readFromNBT(saved);
                require(!loaded.verticalMoatSources&&loaded.boxes(0).equals(part.boxes(0)),"legacy cell reload keeps flat-source hinge/collision");
            }
            require(ramp.request(true),"legacy saved wall reopens");ControllerRuntimeChecks.elapsed(ramp,ramp.durationTicks()+1);
            AxisAlignedBB open=bounds(world,root);require(Math.abs(open.minY-root.getY())<1e-5&&Math.abs(open.maxY-root.getY()-1)<1e-5,"legacy endpoint returns its original flat deck");
            if(face==EnumFacing.SOUTH)legacyCarried(world,root,ramp);
            require(ramp.recover(false),"legacy flat journal recovery");
            for(BlockPos at:sources)require(world.getBlockState(at).equals(Blocks.PLANKS.getStateFromMeta(2)),"legacy recovery restores original flat source positions");
            require(parts(world,root).isEmpty(),"legacy recovery removes all reservations");
        }
    }
    private static void legacyCarried(World world,BlockPos root,TileEntityRampController ramp){
        BlockPos control=new BlockPos(4,1,4),shift=control.subtract(root),seat=BlockPos.ORIGIN,base=new BlockPos(550,120,550);
        for(BlockPos p:BlockPos.getAllInBox(base.add(-2,-1,-2),base.add(14,10,14)))world.setBlockToAir(p);
        List<VehicleStructure.Cell> cells=new ArrayList<>();cells.add(new VehicleStructure.Cell(seat,ModBlocks.PILOT_SEAT.getDefaultState(),null));
        NBTTagCompound controller=VehicleTileData.translated(ramp.writeToNBT(new NBTTagCompound()),shift);controller.setBoolean("SignalKnown",false);
        cells.add(new VehicleStructure.Cell(control,world.getBlockState(root),controller));
        for(TileEntityControlledRamp part:parts(world,root))cells.add(new VehicleStructure.Cell(part.getPos().add(shift),world.getBlockState(part.getPos()),VehicleTileData.translated(part.writeToNBT(new NBTTagCompound()),shift)));
        EntityGroundVehicle craft=new EntityGroundVehicle(world);craft.install(VehicleStructure.read(new VehicleStructure(cells,seat,EnumFacing.NORTH,1).write()));craft.setPosition(base.getX(),base.getY(),base.getZ());
        try{
            require(VehicleRamps.signal(craft,control,false),"captured legacy flat journal closes");
            require(!craft.view.getTileEntity(control).writeToNBT(new NBTTagCompound()).getBoolean("MoatVerticalSources"),"captured old journal is not upgraded while attached");
            require(VehicleRamps.signal(craft,control,true),"captured legacy flat journal opens");
            TileEntityRampController open=(TileEntityRampController)craft.view.getTileEntity(control);
            for(BlockPos source:open.vehicleSources())require(source.getY()==control.getY(),"carried endpoint preserves flat journal rows");
            VehicleStructure saved=VehicleStructure.read(craft.structure.write());VehicleWorld loaded=new VehicleWorld(world,saved);
            require(!loaded.getTileEntity(control).writeToNBT(new NBTTagCompound()).getBoolean("MoatVerticalSources"),"carried save/reload preserves legacy discriminator");
            for(VehicleStructure.Cell cell:saved.cells)if(loaded.getTileEntity(cell.pos) instanceof TileEntityControlledRamp)require(!((TileEntityControlledRamp)loaded.getTileEntity(cell.pos)).verticalMoatSources,"carried generated legacy cells retain discriminator");
            VehicleStructure rotated=saved;
            for(int turn=0;turn<4;turn++)rotated=VehicleRotation.rotate(rotated,new VehicleWorld(world,rotated),1);
            TileEntityRampController fourTurns=(TileEntityRampController)new VehicleWorld(world,rotated).getTileEntity(control);
            require(!fourTurns.writeToNBT(new NBTTagCompound()).getBoolean("MoatVerticalSources")&&fourTurns.vehicleSources().equals(open.vehicleSources()),"four carried quarter-turns preserve legacy flat source journal and discriminator");
        }finally{craft.setDead();for(BlockPos p:BlockPos.getAllInBox(base.add(-2,-1,-2),base.add(14,10,14)))world.setBlockToAir(p);}
    }
    private static void angularAndTreads(World world,EntityPlayerMP owner,BlockPos root){
        int finest=0,coarsest=0;
        for(int pixels:new int[]{1,2,4,8,16}){
            TileEntityRampController ramp=tower(world,owner,root);
            require(ramp.configureHalfOffsets(owner,0,0,pixels,true,false,false,EnumFacing.SOUTH,0,false,1,true,false,0,0,true),"every tread size captures vertical tower: "+pixels+" "+ramp.status);
            ramp.setChannelSignal(true);ControllerRuntimeChecks.elapsed(ramp,ramp.durationTicks()/2);
            double pose=ramp.pose(0);int pieces=0;
            for(TileEntityControlledRamp part:parts(world,root)){require(part.treadPixels==pixels,"saved segment size reaches every occupied cell");pieces+=part.geometry(0).size();}
            if(pixels==1)finest=pieces;if(pixels==16)coarsest=pieces;
            for(double level:new double[]{.25,1.5,2.75}){
                double[] point=com.vandorlabs.ramp.MoatGeometry.vertex(com.vandorlabs.ramp.RampGeometry.Direction.SOUTH,root.getZ()+1,root.getY(),3,0,0,pose,false,root.getX()+.5,root.getY()+level,root.getZ()+1.5);
                require(contains(world,root,new Vec3d(point[0],point[1],point[2])),"straight mid-rotation source center remains inside collision, tread="+pixels+" level="+level);
            }
            require(ramp.recover(false),"segmented midpoint recovery");
        }
        require(finest>coarsest,"tread pixels controls straight rotation collision segmentation: fine="+finest+" coarse="+coarsest);
        TileEntityRampController ramp=tower(world,owner,root);
        require(ramp.configureHalfOffsets(owner,2,4,8,true,false,false,EnumFacing.SOUTH,0,false,1,true,false,0,0,true),"angular endpoints configure");
        AxisAlignedBB skew=bounds(world,root);ramp.setChannelSignal(true);ControllerRuntimeChecks.elapsed(ramp,ramp.durationTicks()+1);AxisAlignedBB slope=bounds(world,root);
        require(skew.maxZ-skew.minZ>1.2,"Start skews the tower instead of translating its height");
        require(slope.maxY-slope.minY>1.2,"End creates a sloped deck instead of a uniform elevation");
        for(double pose:new double[]{0,1}){
            double[] hinge=com.vandorlabs.ramp.MoatGeometry.vertex(com.vandorlabs.ramp.RampGeometry.Direction.SOUTH,root.getZ()+1,root.getY(),3,1,2,pose,false,root.getX()+.5,root.getY(),root.getZ()+1);
            require(Math.abs(hinge[1]-root.getY())<1e-8&&Math.abs(hinge[2]-(root.getZ()+1))<1e-8,"angular endpoints share controller-adjacent bottom hinge");
        }
        require(ramp.recover(false),"angular source recovery");
        ramp=tower(world,owner,root);
        require(ramp.configureHalfOffsets(owner,-2,4,8,true,false,false,EnumFacing.SOUTH,0,false,1,true,false,0,0,true),"negative Start leans toward its solid controller anchor: "+ramp.status);
        require(world.getTileEntity(root)==ramp&&world.getBlockState(root).getBlock()==ModBlocks.PROGRAMMABLE_RAMP,"negative Start preserves original anchor tile and block");
        AxisAlignedBB toward=bounds(world,root);require(toward.minZ<root.getZ()+1-1e-5,"negative Start actually extends behind the hinge above the anchor");
        ramp.setChannelSignal(true);ControllerRuntimeChecks.elapsed(ramp,ramp.durationTicks()+1);
        require(world.getTileEntity(root)==ramp&&ramp.isOpen()&&!ramp.isMoving(),"negative Start deploys without replacing anchor");
        ramp.setChannelSignal(false);ControllerRuntimeChecks.elapsed(ramp,ramp.durationTicks()+1);
        require(world.getTileEntity(root)==ramp&&ramp.attached()&&!ramp.isOpen(),"negative Start returns toward the intact anchor");
        require(ramp.recover(false)&&world.getTileEntity(root)==ramp,"negative Start source recovery retains controller");
        for(int level=0;level<3;level++)require(world.getBlockState(root.south().up(level)).getBlock()==Blocks.PLANKS,"negative Start recovery restores every tower source");
    }
    private static boolean contains(World world,BlockPos root,Vec3d point){
        for(TileEntityControlledRamp part:parts(world,root))for(AxisAlignedBB box:part.boxes(0))if(box.offset(part.getPos()).grow(1e-7).contains(point))return true;
        return false;
    }
    private static TileEntityRampController tower(World world,EntityPlayerMP owner,BlockPos root){
        if(world.getTileEntity(root) instanceof TileEntityRampController)((TileEntityRampController)world.getTileEntity(root)).recover(false);
        for(BlockPos p:BlockPos.getAllInBox(root.add(-5,-4,-5),root.add(5,8,5)))world.setBlockToAir(p);
        owner.setPosition(root.getX()+.5,root.getY()+2,root.getZ()-1.5);
        world.setBlockState(root,ModBlocks.PROGRAMMABLE_RAMP.getDefaultState().withProperty(BlockVandorDirectional.FACING,EnumFacing.SOUTH),3);
        for(int level=0;level<3;level++)world.setBlockState(root.south().up(level),Blocks.PLANKS.getDefaultState(),3);
        return (TileEntityRampController)world.getTileEntity(root);
    }
    private static void carried(World world){
        BlockPos base=new BlockPos(500,120,500),control=new BlockPos(4,1,4),seat=BlockPos.ORIGIN;
        for(BlockPos p:BlockPos.getAllInBox(base.add(-2,-1,-2),base.add(14,10,14)))world.setBlockToAir(p);
        List<VehicleStructure.Cell> original=new ArrayList<>();original.add(new VehicleStructure.Cell(seat,ModBlocks.PILOT_SEAT.getDefaultState(),null));
        TileEntityRampController tile=new TileEntityRampController();tile.setPos(control);tile.moat=true;
        NBTTagCompound data=tile.writeToNBT(new NBTTagCompound());data.setInteger("StartOffsetHalfSteps",-2);data.setInteger("EndOffsetHalfSteps",2);
        original.add(new VehicleStructure.Cell(control,ModBlocks.PROGRAMMABLE_RAMP.getDefaultState().withProperty(BlockVandorDirectional.FACING,EnumFacing.SOUTH),data));
        for(int row=1;row<=3;row++){
            BlockPos at=control.south().up(row-1);TileEntityAnimatedScreenSelector source=new TileEntityAnimatedScreenSelector();source.setPos(at);source.setHousingTexture(CustomBlockMaterials.choice(new net.minecraft.item.ItemStack(Blocks.GOLD_BLOCK)));
            original.add(new VehicleStructure.Cell(at,ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState(),source.writeToNBT(new NBTTagCompound())));
        }
        EntityGroundVehicle craft=new EntityGroundVehicle(world);craft.install(new VehicleStructure(original,seat,EnumFacing.NORTH,1));craft.setPosition(base.getX(),base.getY(),base.getZ());
        try{
            require(VehicleRamps.signal(craft,control,false),"carried off edge creates upright wall");
            TileEntityRampController closed=(TileEntityRampController)craft.view.getTileEntity(control);require(closed.moat&&closed.attached()&&!closed.isOpen()&&closed.writeToNBT(new NBTTagCompound()).getBoolean("MoatVerticalSources"),"carried closed mode and vertical source journal");
            require(craft.structure.at(control).state.getBlock()==ModBlocks.PROGRAMMABLE_RAMP&&closed.startHalfSteps()==-2,"carried negative Start retains solid anchor and toward skew");
            require(VehicleRamps.signal(craft,control,true),"carried on edge deploys horizontal bridge");
            TileEntityRampController open=(TileEntityRampController)craft.view.getTileEntity(control);require(open.moat&&open.isOpen()&&open.endHalfSteps()==2,"carried settings survive endpoint replacement");
            for(VehicleStructure.Cell source:VehicleEscalators.originals(open))
                if(source.state.getBlock()==ModBlocks.PROGRAMMABLE_BLOCK)require(source.tileData()!=null,"carried journal retains programmable material NBT");
            NBTTagCompound saved=craft.structure.write();VehicleStructure restored=VehicleStructure.read(saved);VehicleWorld reload=new VehicleWorld(world,restored);
            require(((TileEntityRampController)reload.getTileEntity(control)).moat,"carried snapshot reload retains Moat");
            BlockPos obstruction=new BlockPos(craft.toWorld(new Vec3d(control.south(2).up(2))).addVector(.5,.5,.5));world.setBlockState(obstruction,Blocks.DIAMOND_BLOCK.getDefaultState(),3);
            VehicleStructure before=craft.structure;require(!VehicleRamps.signal(craft,control,false)&&craft.structure==before,"carried arc obstruction preserves exact snapshot");world.setBlockToAir(obstruction);
            require(VehicleRamps.signal(craft,control,false),"carried reverse after clearing arc");
            TileEntityRampController returned=(TileEntityRampController)craft.view.getTileEntity(control);
            require(!returned.isOpen()&&returned.startHalfSteps()==-2&&craft.structure.at(control).state.getBlock()==ModBlocks.PROGRAMMABLE_RAMP,"carried negative Start returns without replacing solid anchor");
            List<VehicleStructure.Cell> recoverable=VehicleEscalators.originals(returned);require(recoverable.size()==3,"carried negative Start retains complete recovery journal");
            for(VehicleStructure.Cell source:recoverable)require(source.pos.getX()==control.getX()&&source.pos.getZ()==control.getZ()+1&&source.pos.getY()>=control.getY()&&source.pos.getY()<control.getY()+3&&source.tileData()!=null,"carried negative Start recovery retains vertical source positions and material NBT");
        }finally{craft.setDead();for(BlockPos p:BlockPos.getAllInBox(base.add(-2,-1,-2),base.add(14,10,14)))world.setBlockToAir(p);}
    }
}
