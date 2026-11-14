package com.vandorlabs.ports;

import com.vandorlabs.copier.CopyMaterials;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.vehicle.*;
import java.util.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Only the new port family's resource-carrier and typed-settings integration. */
public final class ResourcePortOwnershipChecks {
    private static int checks;
    private static void need(boolean value,String why){checks++;if(!value)throw new IllegalStateException("Resource port ownership: "+why);}
    private static VehicleStructure snapshot(ResourcePortBlock block,NBTTagCompound raw){return VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,block.getDefaultState(),raw)));}
    public static void run(WorldServer world){
        BlockPos at=new BlockPos(958,110,32);world.getChunkFromBlockCoords(at);
        try{for(ResourcePortBlock block:ResourcePorts.BLOCKS){
            ResourcePortTile tile=ResourcePortRuntimeChecks.put(world,at,block);ResourcePortRuntimeChecks.fill(tile,37);ResourcePortSettings.Face[] disabled=new ResourcePortSettings.Face[6];Arrays.fill(disabled,ResourcePortSettings.Face.DISABLED);tile.configure(new ResourcePortSettings(disabled,7,true,ResourcePortSettings.OnTexture.CONNECTED),2,ChannelList.of(64123));
            NBTTagCompound raw=VehicleTileData.translated(tile.writeToNBT(new NBTTagCompound()),BlockPos.ORIGIN.subtract(at));NBTTagCompound caps=new NBTTagCompound();caps.setString("PortEnergy","opaque, never interpreted");caps.setIntArray("PortFaces",new int[]{731,-29,508});raw.setTag("ForgeCaps",caps);
            VehicleStructure source=snapshot(block,raw);VehicleWorld view=new VehicleWorld(world,source);MovingBlockSupport.validate(source,view);List<CopyMaterials.Need> needs=CopyMaterials.forSnapshot(source,view,false);need(needs.size()==1&&needs.get(0).exact&&needs.get(0).count==1,"all-disabled filled port requires ONE exact resource-bearing material item");CopyMaterials.Need carrier=needs.get(0);need(!CopyMaterials.satisfies(carrier,new ItemStack(block))&&CopyMaterials.satisfies(carrier,carrier.item.copy()),"bare blocks cannot fund stored resources");need(carrier.item.getSubCompound("BlockEntityTag").getCompoundTag("ForgeCaps").equals(caps),"material carrier preserves exact original capability snapshot");
            NBTTagCompound empty=raw.copy();if(block==ResourcePorts.POWER)empty.setInteger("PortEnergy",0);else if(block==ResourcePorts.FLUID)empty.removeTag("PortFluid");else empty.setTag("Items",new NBTTagList());VehicleStructure emptySource=snapshot(block,empty);List<CopyMaterials.Need> emptyNeeds=CopyMaterials.forSnapshot(emptySource,new VehicleWorld(world,emptySource),false);need(emptyNeeds.size()==1&&!emptyNeeds.get(0).exact,"empty port can use ordinary building material");need(!CopyMaterials.satisfies(emptyNeeds.get(0),carrier.item),"filled carriers never consumed as empty port blocks");
            ResourcePortSettings.Face[] faces={ResourcePortSettings.Face.OUTPUT,ResourcePortSettings.Face.INPUT,ResourcePortSettings.Face.DISABLED,ResourcePortSettings.Face.INPUT,ResourcePortSettings.Face.OUTPUT,ResourcePortSettings.Face.DISABLED};ResourcePortSettings settings=new ResourcePortSettings(faces,7,true,ResourcePortSettings.OnTexture.CONNECTED);ResourcePortData.write(raw,settings,2,ChannelList.of(64123));source=snapshot(block,raw);
            for(int turns=0;turns<4;turns++){
                VehicleStructure rotated=VehicleRotation.rotate(source,new VehicleWorld(world,source),turns);VehicleStructure.Cell cell=rotated.cells.get(0);NBTTagCompound data=cell.tileData();ResourcePortSettings selected=ResourcePortData.read(data);
                for(EnumFacing side:EnumFacing.values())need(selected.face(Rotation.values()[turns].rotate(side).getIndex())==settings.face(side.getIndex()),"face configuration rotates in its actual world-side basis");need(data.getCompoundTag("ForgeCaps").equals(caps),"rotation never traverses foreign capabilities");
                VehicleStructure restored=VehicleRotation.rotate(rotated,new VehicleWorld(world,rotated),4-turns);need(restored.cells.get(0).state==source.cells.get(0).state&&restored.cells.get(0).tileData().equals(raw),"all private resources and settings survive exact typed rotation inverse");
            }
        }}finally{world.removeTileEntity(at);world.setBlockToAir(at);}
        System.out.println("[vandorlabs][reprolab] resource-port-ownership PASS checks="+checks+" all-disabled exact carriers/no empty substitution/12 typed port headings/private resources/opaque capability inverse");
    }
    private ResourcePortOwnershipChecks(){}
}
