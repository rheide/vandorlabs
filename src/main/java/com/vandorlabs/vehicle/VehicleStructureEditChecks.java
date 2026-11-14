package com.vandorlabs.vehicle;

import java.util.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;

/** Real installed owners survive failures in same-generation structural publication. */
public final class VehicleStructureEditChecks {
    private static int checks;
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Structure publication rollback: "+why);}
    public static void run(WorldServer world,EntityPlayerMP actor)throws Exception {
        checks=0;Vec3d position=actor.getPositionVector();BlockPos base=new BlockPos(1070,150,400);
        try{
            for(boolean scoped:new boolean[]{false,true})for(String stage:new String[]{"installed","positioned","signals","broadcast","rollback-installed"}){
                try(VehicleCouplingRuntimeChecks.Fixture fixture=new VehicleCouplingRuntimeChecks.Fixture(world,actor,base)){
                    if(scoped){fixture.seedNativeReceipt();fixture.submit(fixture.attachPlan());}
                    EntityGroundVehicle craft=fixture.craft;need(craft.board(actor),"actual occupied Pilot Seat");craft.taxiReturnSeat=craft.structure.seat;craft.hoverTarget=craft.posY+12.5;craft.signals.persist();
                    VehicleStructure before=craft.structure;NBTTagCompound original=before.write(),seats=craft.occupiedSeatAssignments(),metadata=new NBTTagCompound();craft.writeCouplerOwnershipMetadata(metadata);
                    double x=craft.posX,y=craft.posY,z=craft.posZ,hover=craft.hoverTarget;BlockPos taxi=craft.taxiReturnSeat;UUID generation=craft.couplerAssociationGeneration();
                    List<VehicleStructure.Cell> expanded=new ArrayList<>(before.cells);expanded.add(new VehicleStructure.Cell(new BlockPos(-1,0,0),Blocks.STONE.getDefaultState(),null));
                    Set<String> fired=new HashSet<>();VehicleStructureEdits.testFault=point->{if(stage.equals("rollback-installed")&&(point.equals("installed")||point.equals("rollback-installed"))){fired.add(point);throw new IllegalStateException("injected "+point);}if(point.equals(stage)){fired.add(point);throw new IllegalStateException("injected "+point);}};
                    boolean refused=false;try{VehicleStructureEdits.install(craft,expanded,"Fixture expansion");}catch(IllegalArgumentException expected){refused=true;}finally{VehicleStructureEdits.testFault=null;}
                    need(refused&&fired.contains(stage),"real "+stage+" failure boundary");need(craft.posX==x&&craft.posY==y&&craft.posZ==z&&craft.hoverTarget==hover,"exact physical root and hover restored");
                    need(craft.occupiedSeatAssignments().equals(seats)&&taxi.equals(craft.taxiReturnSeat),"occupied and return seats restored after rebasing");need(Objects.equals(generation,craft.couplerAssociationGeneration()),"same-generation scope restored");
                    NBTTagCompound restoredMetadata=new NBTTagCompound();craft.writeCouplerOwnershipMetadata(restoredMetadata);need(restoredMetadata.equals(metadata),"original ownership and body-pose metadata retained");
                    if(stage.equals("rollback-installed")){
                        need(fired.contains("installed")&&craft.structure==null&&craft.view==null&&craft.collision==null,"failed rollback becomes inert");
                        need(craft.writeToNBT(new NBTTagCompound()).getCompoundTag("Craft").equals(original),"failed rollback saves exact original full snapshot");
                    }else{
                        need(craft.structure.write().equals(original)&&craft.view.structure==craft.structure,"original usable immutable owner restored");need(craft.couplerScopeReady()&&craft.generalPoseReady(),"restored scope and pose installed");
                        if(scoped){TileEntityChest cargo=null;for(net.minecraft.tileentity.TileEntity tile:craft.view.tiles.values())if(tile instanceof TileEntityChest)cargo=(TileEntityChest)tile;need(cargo!=null&&cargo.getStackInSlot(0).getCount()==7&&cargo.getStackInSlot(0).getTagCompound().getByteArray("NativePreimage").length==5,"seven opaque diamonds remain one carried owner");need(world.isAirBlock(fixture.chest),"no duplicate native cargo");}
                        VehicleStructureEdits.install(craft,expanded,"Fixture retry");need(craft.structure.cells.size()==before.cells.size()+1&&craft.couplerScopeReady(),"same-owner retry publishes once");
                    }
                    actor.dismountRidingEntity();
                }
            }
        }finally{VehicleStructureEdits.testFault=null;actor.dismountRidingEntity();actor.setPositionAndUpdate(position.x,position.y,position.z);}
        System.out.println("[vandorlabs][reprolab] structure-publication-rollback PASS checks="+checks+" ten actual publications scoped/legacy four caught stages failed-rollback inert exact source/cargo/seat/pose retry");
    }
    private VehicleStructureEditChecks(){}
}
