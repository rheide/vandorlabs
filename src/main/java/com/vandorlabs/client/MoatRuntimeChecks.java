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
                for(int row=1;row<=3;row++)for(int lane=0;lane<2;lane++){BlockPos source=root.offset(face,row).offset(face.rotateY(),lane);world.setBlockState(source,material,3);sources.add(source);}
                require(ramp.configureHalfOffsets(owner,0,2,8,true,false,false,face,0,false,1,true,false,0,0,true),"configure closed: "+ramp.status);
                require(ramp.attached()&&!ramp.isOpen()&&!ramp.isMoving(),"off at zero holds an upright journal");
                AxisAlignedBB closed=bounds(world,root);
                require(Math.abs(closed.maxY-(root.getY()+4))<1e-5,"three rows form three-block upright wall");
                NBTTagCompound saved=ramp.writeToNBT(new NBTTagCompound());
                ramp.readFromNBT(saved);require(ramp.moat&&ramp.attached(),"controller reload retains mode and source journal");
                for(TileEntityControlledRamp part:parts(world,root)){
                    TileEntityControlledRamp copy=new TileEntityControlledRamp();copy.setWorld(world);copy.setPos(part.getPos());copy.readFromNBT(part.writeToNBT(new NBTTagCompound()));
                    require(copy.moat&&copy.boxes(0).equals(part.boxes(0)),"saved generated cells retain upright collision");
                    require(part.geometry(0)==part.geometry(.9),"settled upright geometry is cached");
                }
                ramp.setChannelSignal(true);ControllerRuntimeChecks.elapsed(ramp,ramp.durationTicks()/2);
                require(ramp.isOpen()&&ramp.isMoving()&&ramp.pose(0)>.4&&ramp.pose(0)<.6,"channel edge rotates through midpoint");
                bounds(world,root);
                ControllerRuntimeChecks.elapsed(ramp,ramp.durationTicks()*3/4);boolean outer=false;
                for(TileEntityControlledRamp part:parts(world,root)){
                    NBTTagCompound cell=part.writeToNBT(new NBTTagCompound());TileEntityControlledRamp loaded=new TileEntityControlledRamp();loaded.setWorld(world);loaded.setPos(part.getPos());loaded.readFromNBT(cell);
                    require(loaded.row==part.row,"mid-arc cell retains raw reservation row");require(loaded.boxes(0).equals(part.boxes(0)),"mid-arc saved collision retains one hinge");
                    if(part.row>=part.length&&!part.boxes(0).isEmpty())outer=true;
                }
                require(outer,"rotating far corner reaches row==length outside original deck");
                ControllerRuntimeChecks.elapsed(ramp,ramp.durationTicks()+1);
                AxisAlignedBB opened=bounds(world,root);
                require(Math.abs(opened.maxY-(root.getY()+2))<1e-5,"end slider uniformly raises horizontal bridge");
                require(opened.maxY-opened.minY<1.001,"open bridge remains horizontal across all rows");
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
                for(BlockPos source:sources)require(world.getBlockState(source).equals(material),"mode change restores flat platform");
                require(ramp.configureHalfOffsets(owner,0,0,8,true,false,false,face,0,false,1,true,false,0,0,true),"orphan fixture captures");
                List<TileEntityControlledRamp> owned=parts(world,root);
                for(TileEntityControlledRamp part:owned){part.onLoad();part.recoverOnEvent();}
                require(ramp.attached()&&!parts(world,root).isEmpty(),"legitimate cells survive recovery event");
                world.removeTileEntity(root);world.setBlockToAir(root);
                for(TileEntityControlledRamp part:owned)if(world.getTileEntity(part.getPos())==part)part.recoverOnEvent();
                for(BlockPos source:sources)require(world.getBlockState(source).equals(material),"missing controller tile restores exact source");
                require(parts(world,root).isEmpty(),"orphan event clears invisible reservation cells");
            }
            carried(world);
            System.out.println("[vandorlabs][reprolab] moat-world PASS cardinal slab source-journal closed-zero channel-midpoint endpoint-offset reload obstruction mode-reset cache orphan carried-endpoints");
        }finally{
            if(world.getTileEntity(root) instanceof TileEntityRampController)((TileEntityRampController)world.getTileEntity(root)).recover(false);
            for(BlockPos p:BlockPos.getAllInBox(root.add(-5,-4,-5),root.add(5,8,5)))world.setBlockToAir(p);
            owner.setPosition(x,y,z);
        }
    }
    private static void carried(World world){
        BlockPos base=new BlockPos(500,120,500),control=new BlockPos(4,1,4),seat=BlockPos.ORIGIN;
        for(BlockPos p:BlockPos.getAllInBox(base.add(-2,-1,-2),base.add(14,10,14)))world.setBlockToAir(p);
        List<VehicleStructure.Cell> original=new ArrayList<>();original.add(new VehicleStructure.Cell(seat,ModBlocks.PILOT_SEAT.getDefaultState(),null));
        TileEntityRampController tile=new TileEntityRampController();tile.setPos(control);tile.moat=true;
        NBTTagCompound data=tile.writeToNBT(new NBTTagCompound());data.setInteger("StartOffsetHalfSteps",0);data.setInteger("EndOffsetHalfSteps",2);
        original.add(new VehicleStructure.Cell(control,ModBlocks.PROGRAMMABLE_RAMP.getDefaultState().withProperty(BlockVandorDirectional.FACING,EnumFacing.SOUTH),data));
        for(int row=1;row<=3;row++){
            BlockPos at=control.south(row);TileEntityAnimatedScreenSelector source=new TileEntityAnimatedScreenSelector();source.setPos(at);source.setHousingTexture(CustomBlockMaterials.choice(new net.minecraft.item.ItemStack(Blocks.GOLD_BLOCK)));
            original.add(new VehicleStructure.Cell(at,ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState(),source.writeToNBT(new NBTTagCompound())));
        }
        EntityGroundVehicle craft=new EntityGroundVehicle(world);craft.install(new VehicleStructure(original,seat,EnumFacing.NORTH,1));craft.setPosition(base.getX(),base.getY(),base.getZ());
        try{
            require(VehicleRamps.signal(craft,control,false),"carried off edge creates upright wall");
            TileEntityRampController closed=(TileEntityRampController)craft.view.getTileEntity(control);require(closed.moat&&closed.attached()&&!closed.isOpen(),"carried closed mode and journal");
            require(VehicleRamps.signal(craft,control,true),"carried on edge deploys horizontal bridge");
            TileEntityRampController open=(TileEntityRampController)craft.view.getTileEntity(control);require(open.moat&&open.isOpen()&&open.endHalfSteps()==2,"carried settings survive endpoint replacement");
            for(VehicleStructure.Cell source:VehicleEscalators.originals(open))
                if(source.state.getBlock()==ModBlocks.PROGRAMMABLE_BLOCK)require(source.tileData()!=null,"carried journal retains programmable material NBT");
            NBTTagCompound saved=craft.structure.write();VehicleStructure restored=VehicleStructure.read(saved);VehicleWorld reload=new VehicleWorld(world,restored);
            require(((TileEntityRampController)reload.getTileEntity(control)).moat,"carried snapshot reload retains Moat");
            BlockPos obstruction=new BlockPos(craft.toWorld(new Vec3d(control.south(2).up(2))).addVector(.5,.5,.5));world.setBlockState(obstruction,Blocks.DIAMOND_BLOCK.getDefaultState(),3);
            VehicleStructure before=craft.structure;require(!VehicleRamps.signal(craft,control,false)&&craft.structure==before,"carried arc obstruction preserves exact snapshot");world.setBlockToAir(obstruction);
            require(VehicleRamps.signal(craft,control,false),"carried reverse after clearing arc");
        }finally{craft.setDead();for(BlockPos p:BlockPos.getAllInBox(base.add(-2,-1,-2),base.add(14,10,14)))world.setBlockToAir(p);}
    }
}
