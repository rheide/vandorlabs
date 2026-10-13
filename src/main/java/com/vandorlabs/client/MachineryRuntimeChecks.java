package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockShipSystem;
import com.vandorlabs.shipsystems.*;
import com.vandorlabs.tiles.TileEntityShipSystem;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Focused rendered fixtures and persistence/connection regressions for retained machinery. */
public final class MachineryRuntimeChecks {
    private static void require(boolean value,String message) { if(!value)throw new IllegalStateException("machinery: "+message); }
    public static void run(World world,BlockPos origin) {
        String[] kept={"rivet_computer_large_g64","rivet_reactor_large_g64","rivet_crossflow_large_g64","rivet_dampener_large_g64","rivet_tractor_large_g64","kestrel_warp_cassette_large","kestrel_phase_drive_large","hadron_console","vektor_console","hadron_analysis_large"};
        for(int index=0;index<kept.length;index++) {
            Block block=Block.getBlockFromName("vandorlabs:"+kept[index]);require(block instanceof BlockShipSystem,"retained registry "+kept[index]);
            BlockShipSystem machine=(BlockShipSystem)block;BlockPos pos=origin.add((index%3)*7,0,(index/3)*7);
            for(BlockPos cell:machine.cells(pos,EnumFacing.NORTH)) {
                if(cell.getY()==pos.getY())world.setBlockState(cell.down(),Blocks.STONE.getDefaultState(),2);
                world.setBlockState(cell,machine.getDefaultState().withProperty(BlockShipSystem.FACING,EnumFacing.NORTH),2);
                TileEntityShipSystem member=TileEntityShipSystem.at(world,cell);member.anchor=pos;member.mount=EnumFacing.UP;member.sync();
            }
            TileEntityShipSystem tile=TileEntityShipSystem.at(world,pos);tile.refreshAppearance();tile.setAnimated(true);
            require(tile.isAnimated(),"animation enabled");NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());TileEntityShipSystem restored=new TileEntityShipSystem();restored.setPos(pos);restored.readFromNBT(saved);require(restored.isAnimated(),"animation persistence");

        }
        checkReactor(world,origin.add(7,0,0));
        BlockShipSystem console=(BlockShipSystem)Block.getBlockFromName("vandorlabs:vektor_console");BlockPos consoleOrigin=origin.add(21,0,0);
        for(int x=0;x<2;x++)for(int z=0;z<3;z++) {
            BlockPos p=consoleOrigin.add(x,0,z);world.setBlockState(p.down(),Blocks.STONE.getDefaultState(),2);world.setBlockState(p,console.getDefaultState(),2);
            TileEntityShipSystem tile=TileEntityShipSystem.at(world,p);tile.anchor=p;tile.mount=EnumFacing.UP;tile.sync();
        }
        ConsoleConnections.refresh(world,consoleOrigin);
        TileEntityShipSystem owner=TileEntityShipSystem.at(world,consoleOrigin);require(owner.consoleWidth==2 && owner.consoleDepth==3,"2x3 grouping");
        require(owner.model().equals("vektor_console_2_depth_3_on"),"2x3 model");owner.setAnimated(true);
        checkOpposed(world,console,origin.add(28,0,0));
        System.out.println("[vandorlabs][reprolab] machinery-live PASS retained=10 rectangle=2x3 animation=persisted collision=cached");
    }
    private static void checkOpposed(World world,BlockShipSystem console,BlockPos origin) {
        for(EnumFacing facing:new EnumFacing[]{EnumFacing.NORTH,EnumFacing.EAST})for(int width=1;width<=3;width++) {
            BlockPos pos=origin.add(width*5,0,facing==EnumFacing.NORTH?0:7);MountFrame frame=new MountFrame(facing,EnumFacing.UP);
            for(int x=0;x<width;x++)for(int row=0;row<2;row++) {
                BlockPos cell=frame.cell(pos,x,0,row);world.setBlockState(cell.down(),Blocks.STONE.getDefaultState(),2);
                world.setBlockState(cell,console.getDefaultState().withProperty(BlockShipSystem.FACING,row==0?facing:facing.getOpposite()),2);
                TileEntityShipSystem tile=TileEntityShipSystem.at(world,cell);tile.anchor=cell;tile.mount=EnumFacing.UP;
            }
            require(ConsoleConnections.refresh(world,frame.cell(pos,width-1,0,1)),"opposed refresh from reversed row");
            TileEntityShipSystem owner=TileEntityShipSystem.at(world,pos);
            require(owner.consoleOpposed && owner.consoleWidth==width && owner.consoleDepth==2,"opposed dimensions");
            require(owner.model().equals("vektor_console_"+width+"_opposed_on"),"opposed render model");
            int drawn=0;
            for(int x=0;x<width;x++)for(int row=0;row<2;row++) {
                TileEntityShipSystem member=TileEntityShipSystem.at(world,frame.cell(pos,x,0,row));
                require(member.configurationOwner()==owner,"opposed configuration owner");
                require(!member.collisionBoxes().isEmpty(),"opposed cell collision");
                require(member.selectionBounds().equals(owner.selectionBounds()),"opposed shared selection");
                if(member.draws())drawn++;
                NBTTagCompound saved=member.writeToNBT(new NBTTagCompound());TileEntityShipSystem restored=new TileEntityShipSystem();restored.setPos(member.getPos());restored.readFromNBT(saved);
                require(restored.consoleOpposed,"opposed persisted");
            }
            require(drawn==1,"opposed renders once");
            owner.setAnimated(true);
            require(TileEntityShipSystem.at(world,frame.cell(pos,width-1,0,1)).isAnimated(),"opposed animation configuration propagates");
            world.setBlockToAir(frame.cell(pos,width-1,0,1));ConsoleConnections.refresh(world,pos);
            if(width==1)require(!owner.consoleOpposed && owner.consoleDepth==1,"removed reverse row splits assembly");
        }
        System.out.println("[vandorlabs][reprolab] machinery-opposed PASS widths=1,2,3 axes=NS,EW persistence=shared collision=all-cells");
    }
    private static void checkReactor(World world,BlockPos pos) {
        TileEntityShipSystem reactor=TileEntityShipSystem.at(world,pos);BlockShipSystem block=reactor.block();
        int footprint=0;
        for(BlockPos cell:block.cells(pos,EnumFacing.NORTH)) {
            TileEntityShipSystem member=TileEntityShipSystem.at(world,cell);
            java.util.List<net.minecraft.util.math.AxisAlignedBB> boxes=member.collisionBoxes();
            require(boxes==member.collisionBoxes(),"collision list cached per occupied cell");
            require(member.getRenderBoundingBox()==member.getRenderBoundingBox(),"render bounds cached per occupied cell");
            require(block.getBoundingBox(world.getBlockState(cell),world,cell).offset(cell).equals(reactor.selectionBounds()),"full reactor selection bounds from every cell");
            if(cell.getY()!=pos.getY())continue;
            footprint++;require(!boxes.isEmpty(),"reactor collision in footprint cell "+cell);
            net.minecraft.util.math.AxisAlignedBB box=boxes.get(0);net.minecraft.util.math.Vec3d center=new net.minecraft.util.math.Vec3d((box.minX+box.maxX)/2,(box.minY+box.maxY)/2,(box.minZ+box.maxZ)/2);
            require(block.collisionRayTrace(world.getBlockState(cell),world,cell,center.addVector(0,3,0),center.addVector(0,-3,0))!=null,"reactor selectable in footprint cell "+cell);
            java.util.List<net.minecraft.util.math.AxisAlignedBB> collision=new java.util.ArrayList<>();
            block.addCollisionBoxToList(world.getBlockState(cell),world,cell,new net.minecraft.util.math.AxisAlignedBB(cell),collision,null,false);
            require(!collision.isEmpty(),"reactor solid in footprint cell "+cell);
        }
        require(footprint==4,"reactor footprint covers four cells");
        // Conservative previous hot path: fresh world offsets and union of every authored cuboid.
        // The old implementation additionally transformed two Vec3d corners per box.
        for(int i=0;i<1000;i++)reactor.getRenderBoundingBox();
        long start=System.nanoTime();for(int i=0;i<20000;i++)reactor.getRenderBoundingBox();long cached=System.nanoTime()-start;
        start=System.nanoTime();double consume=0;
        for(int i=0;i<100;i++) {
            net.minecraft.util.math.AxisAlignedBB bounds=new net.minecraft.util.math.AxisAlignedBB(pos);
            for(net.minecraft.util.math.AxisAlignedBB box:ShipSystemMesh.boxes(reactor.model(),reactor.facing(),reactor.mount,pos))bounds=bounds.union(box);
            consume+=bounds.maxX;
        }
        long uncached=System.nanoTime()-start;
        require(consume>0,"profile consumes expanded bounds");
        System.out.println("[vandorlabs][reprolab] machinery-bounds-profile authored="+ShipSystemMesh.authoredBoxCount(reactor.model())+" cached_ns_per_query="+(cached/20000)+" uncached_offset_union_ns_per_query="+(uncached/100)+" reactor-footprint=4 PASS");
    }
    private MachineryRuntimeChecks() {}
}
