package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.compat.RampMaterials;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.*;

/** Every full glass material crosses the real journal, channel, save and recovery paths. */
final class RampGlassRuntimeChecks {
    private static void require(boolean yes,String message){if(!yes)throw new IllegalStateException("Ramp glass: "+message);}
    private static boolean sameGeometry(TileEntityControlledRamp a,TileEntityControlledRamp b){
        List<com.vandorlabs.ramp.RampGeometry.Box> left=a.geometry(0),right=b.geometry(0);
        if(left.size()!=right.size())return false;
        for(int i=0;i<left.size();i++){
            com.vandorlabs.ramp.RampGeometry.Box x=left.get(i),y=right.get(i);
            if(Math.abs(x.minX-y.minX)>1e-9||Math.abs(x.minY-y.minY)>1e-9||Math.abs(x.minZ-y.minZ)>1e-9
                    ||Math.abs(x.maxX-y.maxX)>1e-9||Math.abs(x.maxY-y.maxY)>1e-9||Math.abs(x.maxZ-y.maxZ)>1e-9)return false;
        }
        return true;
    }
    static void run(World world,EntityPlayerMP owner){
        List<IBlockState> materials=new ArrayList<>();materials.add(Blocks.GLASS.getDefaultState());
        materials.addAll(Blocks.STAINED_GLASS.getBlockState().getValidStates());
        for(Block block:Block.REGISTRY)if(block instanceof BlockCockpitGlass)materials.addAll(block.getBlockState().getValidStates());
        require(materials.size()>=20,"plain, sixteen stained and three cockpit variants registered");
        require(!RampMaterials.isFullGlass(Blocks.GLASS_PANE.getDefaultState())&&!RampMaterials.isFullGlass(Blocks.STAINED_GLASS_PANE.getDefaultState()),"panes retain their distinct geometry");
        BlockPos root=new BlockPos(110,45,110);double x=owner.posX,y=owner.posY,z=owner.posZ;
        try{
            owner.setPosition(root.getX()+.5,root.getY()+2,root.getZ()-1.5);
            for(IBlockState material:materials){
                require(RampMaterials.isFullGlass(material),"explicit full-glass family: "+material);
                for(int mode=0;mode<3;mode++){
                    clear(world,root);
                    world.setBlockState(root,ModBlocks.PROGRAMMABLE_RAMP.getDefaultState().withProperty(BlockVandorDirectional.FACING,EnumFacing.SOUTH),3);
                    for(int row=1;row<=2;row++)world.setBlockState(mode==2?root.south().up(row-1):root.south(row),material,3);
                    TileEntityRampController ramp=(TileEntityRampController)world.getTileEntity(root);
                    require(ramp.configureHalfOffsets(owner,0,2,8,true,false,false,EnumFacing.SOUTH,0,mode==1,1,true,false,0,0,mode==2),"configure mode="+mode+" source="+material+" status="+ramp.status);
                    ramp.setChannelSignal(true);ControllerRuntimeChecks.elapsed(ramp,ramp.durationTicks()+1);
                    require(ramp.attached()&&ramp.isOpen(),"channel deploys "+material);
                    int cells=0;
                    for(BlockPos p:BlockPos.getAllInBox(root.add(-3,-3,-3),root.add(3,6,6)))if(world.getTileEntity(p) instanceof TileEntityControlledRamp){
                        TileEntityControlledRamp part=(TileEntityControlledRamp)world.getTileEntity(p);if(!part.belongsTo(root))continue;
                        require(part.source.equals(material)&&part.shouldRenderInPass(1)&&!part.shouldRenderInPass(0),"generated material and translucent pass");
                        TileEntityControlledRamp copy=new TileEntityControlledRamp();copy.setWorld(world);copy.setPos(p);copy.readFromNBT(part.writeToNBT(new NBTTagCompound()));
                        require(copy.source.equals(material)&&sameGeometry(copy,part),"generated save preserves glass state and geometry");cells++;
                    }
                    require(cells>0,"generated glass cells exist");
                    ramp.readFromNBT(ramp.writeToNBT(new NBTTagCompound()));
                    require(ramp.recover(false),"saved controller recovery");
                    for(int row=1;row<=2;row++)require(world.getBlockState(mode==2?root.south().up(row-1):root.south(row)).equals(material),"recovery restores exact color/material "+material);
                    for(BlockPos p:BlockPos.getAllInBox(root.add(-3,-3,-3),root.add(3,6,6)))require(world.getBlockState(p).getBlock()!=ModBlocks.CONTROLLED_RAMP,"recovery clears reservations");
                }
                carried(world,material);
            }
            System.out.println("[vandorlabs][reprolab] ramp-glass-world PASS variants="+materials.size()+" ordinary-filled-moat channels source-state tile-save recovery carried-rotation");
        }finally{clear(world,root);owner.setPosition(x,y,z);}
    }
    private static void clear(World world,BlockPos root){
        if(world.getTileEntity(root) instanceof TileEntityRampController)((TileEntityRampController)world.getTileEntity(root)).recover(false);
        for(BlockPos p:BlockPos.getAllInBox(root.add(-3,-3,-3),root.add(3,6,6)))world.setBlockToAir(p);
    }
    private static void carried(World world,IBlockState material){
        BlockPos base=new BlockPos(550,150,550);
        for(BlockPos p:BlockPos.getAllInBox(base.add(-2,-2,-2),base.add(14,12,14)))world.setBlockToAir(p);
        BlockPos control=new BlockPos(3,1,3);List<VehicleStructure.Cell> cells=new ArrayList<>();
        cells.add(new VehicleStructure.Cell(BlockPos.ORIGIN,ModBlocks.PILOT_SEAT.getDefaultState(),null));
        TileEntityRampController tile=new TileEntityRampController();tile.setPos(control);tile.moat=true;
        NBTTagCompound data=tile.writeToNBT(new NBTTagCompound());data.setInteger("StartOffsetHalfSteps",0);data.setInteger("EndOffsetHalfSteps",2);
        cells.add(new VehicleStructure.Cell(control,ModBlocks.PROGRAMMABLE_RAMP.getDefaultState().withProperty(BlockVandorDirectional.FACING,EnumFacing.SOUTH),data));
        for(int row=1;row<=2;row++)cells.add(new VehicleStructure.Cell(control.south().up(row-1),material,null));
        EntityGroundVehicle craft=new EntityGroundVehicle(world);craft.install(new VehicleStructure(cells,BlockPos.ORIGIN,EnumFacing.NORTH,1));craft.setPosition(550,150,550);
        try{
            require(VehicleRamps.signal(craft,control,false)&&VehicleRamps.signal(craft,control,true),"carried glass endpoints "+material);
            VehicleStructure rotated=VehicleStructure.read(craft.structure.write());
            for(int turn=0;turn<4;turn++)rotated=VehicleRotation.rotate(rotated,new VehicleWorld(world,rotated),1);
            VehicleWorld view=new VehicleWorld(world,rotated);int count=0;
            for(net.minecraft.tileentity.TileEntity raw:view.tiles.values())if(raw instanceof TileEntityRampController){
                for(VehicleStructure.Cell original:VehicleEscalators.originals((TileEntityRampController)raw)){require(original.state.equals(material),"quarter-turn journal retains glass material/color");count++;}
            }
            require(count==2,"four-turn controller journal retains both originals");
        }finally{craft.setDead();for(BlockPos p:BlockPos.getAllInBox(base.add(-2,-2,-2),base.add(14,12,14)))world.setBlockToAir(p);}
    }
    private RampGlassRuntimeChecks(){}
}
