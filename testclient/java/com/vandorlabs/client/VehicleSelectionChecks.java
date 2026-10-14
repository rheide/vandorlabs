package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockPilotSeat;
import com.vandorlabs.vehicle.*;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import java.util.*;

/** Headless selection coverage; does not substitute for real click/network boarding checks. */
public final class VehicleSelectionChecks {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        net.minecraft.init.Bootstrap.register();
        VehicleMovementChecks.run();
        BlockPilotSeat seat = new BlockPilotSeat();
        // Headless bootstrap has no active mod container to supply the registration namespace.
        net.minecraftforge.fml.relauncher.ReflectionHelper.setPrivateValue(net.minecraftforge.registries.IForgeRegistryEntry.Impl.class,seat,null,"registryName");
        seat.setRegistryName("vandorlabs","pilot_seat");
        BlockPos position = new BlockPos(40, 20, 40);
        List<VehicleStructure.Cell> cells = new ArrayList<>();
        cells.add(new VehicleStructure.Cell(BlockPos.ORIGIN,Blocks.STONE.getDefaultState(),null));
        cells.add(new VehicleStructure.Cell(position, seat.getDefaultState(), null));
        cells.add(new VehicleStructure.Cell(position.up(), seat.getDefaultState().withProperty(BlockPilotSeat.UPPER, true), null));
        VehicleStructure structure = new VehicleStructure(cells, position, EnumFacing.NORTH, 1);
        NonRenderingChecks.MemoryWorld world = new NonRenderingChecks.MemoryWorld(false);
        for (VehicleStructure.Cell cell : cells) world.states.put(cell.pos, cell.state);
        for (int y : new int[]{20, 21}) {
            Vec3d start = new Vec3d(40.5, y + .5, 38), end = start.addVector(0, 0, 6);
            VehicleSelection.Hit hit = VehicleSelection.local(structure, world, start, end, 36);
            require(hit != null && hit.action == VehicleSelection.Action.BOARD && hit.cell.pos.getY() == y,
                    "distant seat lower/upper selection");
            require(VehicleSelection.local(structure, world, start, end, hit.distanceSq) == null,
                    "terrain at hit distance occludes seat");
        }
        Vec3d start = new Vec3d(39.9, 20.5, 38), end = start.addVector(0, 0, 6);
        require(VehicleSelection.local(structure, world, start, end, 36) != null,
                "seat protruding outside its occupied cell remains selectable");
        BlockPos overhead=position.up(2);List<VehicleStructure.Cell> cockpit=new ArrayList<>(cells);
        cockpit.add(new VehicleStructure.Cell(overhead,Blocks.GLASS.getDefaultState(),null));world.states.put(overhead,Blocks.GLASS.getDefaultState());
        VehicleStructure cabin=new VehicleStructure(cockpit,position,EnumFacing.NORTH,1);
        Vec3d pilotEye=new Vec3d(40.5,21.2,40.5),roof=pilotEye.addVector(0,4,0);
        require(VehicleSelection.local(cabin,world,pilotEye,roof,16).action==VehicleSelection.Action.BOARD,"occupied seat would intercept overhead selection");
        VehicleSelection.Hit rider=VehicleSelection.local(cabin,world,pilotEye,roof,16,position);
        require(rider!=null && rider.cell.pos.equals(overhead),"own seat does not swallow seated overhead selection");
        require(VehicleSelection.local(cabin,world,pilotEye,roof,rider.distanceSq,position)==null,"terrain still blocks seated selection at equal distance");
        cells.add(new VehicleStructure.Cell(position.north(), Blocks.STONE.getDefaultState(), null));
        world.states.put(position.north(), Blocks.STONE.getDefaultState());
        structure = new VehicleStructure(cells, position, EnumFacing.NORTH, 1);
        start = new Vec3d(40.5, 20.5, 38); end = start.addVector(0, 0, 6);
        require(VehicleSelection.local(structure, world, start, end, 36).action == VehicleSelection.Action.SURFACE,
                "nearer captured wall occludes seat");
        require(VehicleSelection.local(structure, world, new Vec3d(5, 20.5, 38), new Vec3d(5, 20.5, 44), 36) == null,
                "empty cabin space is not enclosing-hull geometry");
        require(VehicleSelection.local(structure, world, new Vec3d(Double.NaN, 0, 0), end, 36) == null,
                "nonfinite ray rejected");
        require(VehicleSelection.local(structure, world, start, end, -1) == null, "negative reach rejected");
        BlockPos door = position.east(2), slab = position.east(4);
        cells.add(new VehicleStructure.Cell(door, Blocks.OAK_DOOR.getDefaultState(), null));
        cells.add(new VehicleStructure.Cell(slab, Blocks.STONE_SLAB.getDefaultState(), null));
        world.states.put(door, Blocks.OAK_DOOR.getDefaultState());
        world.states.put(slab, Blocks.STONE_SLAB.getDefaultState());
        structure = new VehicleStructure(cells, position, EnumFacing.NORTH, 1);
        start = new Vec3d(42.5, 20.5, 38); end = start.addVector(0, 0, 6);
        require(VehicleSelection.local(structure, world, start, end, 36).action == VehicleSelection.Action.DOOR,
                "door surface action classified independently from boarding");
        start = new Vec3d(44.5, 20.75, 38); end = start.addVector(0, 0, 6);
        require(VehicleSelection.local(structure, world, start, end, 36) == null, "ray over slab is not a full-cell hit");
        start = new Vec3d(44.5, 20.25, 38); end = start.addVector(0, 0, 6);
        require(VehicleSelection.local(structure, world, start, end, 36) != null, "slab actual surface selectable");
        EntityGroundVehicle craft=new EntityGroundVehicle(world);
        craft.structure=structure;craft.view=new VehicleWorld(world,structure);craft.setPosition(-64,100,128);
        for(float yaw:new float[]{0,90,179,-179}) {
            craft.rotationYaw=yaw;
            Vec3d eye=craft.toWorld(new Vec3d(40.5,21.5,38)),endWorld=craft.toWorld(new Vec3d(40.5,21.5,44));
            VehicleSelection.Hit hit=VehicleSelection.trace(craft,eye,endWorld,36);
            require(hit!=null && hit.cell.pos.equals(position.up()),"simulation-pose ray at heading "+yaw);
            require(Math.abs(hit.distanceSq-4)<1e-8,"rotation preserves actual-surface reach");
        }
        for(int turn=0;turn<4;turn++)require(VehicleRotation.turns(turn*90)==turn,"cardinal headings accepted");
        for(float yaw:new float[]{45,135,-45}) {
            boolean denied=false;try{VehicleRotation.turns(yaw);}catch(IllegalArgumentException expected){denied=true;}
            require(denied,"diagonal restoration refused");
        }
        VehicleStructure cycle=structure;
        for(int turn=0;turn<4;turn++)cycle=VehicleRotation.rotate(cycle,new VehicleWorld(world,cycle),1);
        require(cycle.write().equals(structure.write()),"four rotations restore cell coordinates and native block states");
        net.minecraft.block.Block unknown=new net.minecraft.block.Block(net.minecraft.block.material.Material.ROCK){};
        unknown.setRegistryName("unknown_fixture","directional_component");
        List<VehicleStructure.Cell> unsupportedCells=new ArrayList<>(structure.cells);
        unsupportedCells.add(new VehicleStructure.Cell(new BlockPos(1,0,1),unknown.getDefaultState(),null));
        VehicleStructure unsupported=new VehicleStructure(unsupportedCells,position,EnumFacing.NORTH,1);
        require(VehicleRotation.rotate(unsupported,new VehicleWorld(world,unsupported),0)==unsupported,"unknown block can return at original orientation");
        boolean refused=false;try{VehicleRotation.rotate(unsupported,new VehicleWorld(world,unsupported),1);}catch(IllegalArgumentException expected){refused=true;}
        require(refused,"unknown mod block rotation refused before mutation");
        net.minecraft.block.Block replacement=new net.minecraft.block.Block(net.minecraft.block.material.Material.ROCK){};
        replacement.setRegistryName("minecraft","unknown_replacement");
        unsupportedCells=new ArrayList<>(structure.cells);unsupportedCells.add(new VehicleStructure.Cell(new BlockPos(1,0,1),replacement.getDefaultState(),null));
        unsupported=new VehicleStructure(unsupportedCells,position,EnumFacing.NORTH,1);
        refused=false;try{VehicleRotation.rotate(unsupported,new VehicleWorld(world,unsupported),1);}catch(IllegalArgumentException expected){refused=true;}
        require(refused,"an unknown replacement cannot claim vanilla rotation support through its registry namespace");
        VehicleNetwork.Packet sent=new VehicleNetwork.Packet(VehicleNetwork.INTERACT);
        sent.entity=42;sent.token=java.util.UUID.randomUUID();sent.origin=position;
        io.netty.buffer.ByteBuf buffer=io.netty.buffer.Unpooled.buffer();
        try {
            sent.toBytes(buffer);VehicleNetwork.Packet received=new VehicleNetwork.Packet();received.fromBytes(buffer);
            require(received.valid && received.type==VehicleNetwork.INTERACT && received.entity==42
                    && received.token.equals(sent.token) && received.origin.equals(position),"interaction identity and cell codec");
            // HEIGHT, INSPECTION and CANOPIES were added after SETTINGS.
            // Exercise the current protocol boundary rather than an older valid packet ID.
            buffer.clear();sent.type=VehicleNetwork.CANOPIES;sent.toBytes(buffer);received=new VehicleNetwork.Packet();received.fromBytes(buffer);
            require(received.valid && received.type==VehicleNetwork.CANOPIES,"latest supported packet type accepted");
            for(int unknownType:new int[]{VehicleNetwork.CANOPIES+1,255}){
                buffer.clear();sent.type=unknownType;sent.toBytes(buffer);received=new VehicleNetwork.Packet();received.fromBytes(buffer);
                require(!received.valid,"unknown interaction packet type rejected: "+unknownType);
            }
        } finally {buffer.release();}
        System.out.println("PASS: distant seat cells, protruding selection, cabin gaps, slab/door shapes, captured/terrain occlusion and invalid rays");
    }
}
