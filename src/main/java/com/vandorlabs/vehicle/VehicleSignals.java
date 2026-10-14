package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.redstone.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.*;
import net.minecraft.block.state.IBlockState;
import java.util.*;
import java.io.*;

/** Explicit, event-driven subscriptions to the parent world's channel bank. */
public final class VehicleSignals {
    private final EntityGroundVehicle craft;
    private final Map<RedstoneChannelMember,Link> links=new IdentityHashMap<>();
    private final Set<BlockPos> dirty=new LinkedHashSet<>();
    private boolean detached;
    private int revision=-1;
    VehicleSignals(EntityGroundVehicle craft){this.craft=craft;}
    public void changed(BlockPos pos){if(!detached)dirty.add(pos.toImmutable());}
    private class Link implements RedstoneChannelMember {
        final RedstoneChannelMember source;final TileEntity anchor=new TileEntity(){};
        Link(RedstoneChannelMember source){this.source=source;anchor.setWorld(craft.world);}
        public TileEntity channelTile(){return anchor;}
        public int getRedstoneChannel(){return source.getRedstoneChannel();}
        public ChannelList getRedstoneChannels(){return source.getRedstoneChannels();}
        public void setRedstoneChannel(int ignored){throw new UnsupportedOperationException();}
        public boolean hasLocalRedstoneSignal(){return false;}
        public void setChannelSignal(boolean on){setChannelLevel(on?15:0);}
        public void setChannelLevel(int level){
            if(detached || craft.isDead || craft.ownershipBlocked)return;
            TileEntity tile=source.channelTile();BlockPos pos=tile.getPos();
            if(tile instanceof TileEntityCanopy){TileEntityCanopy canopy=(TileEntityCanopy)tile;if(canopy.targetOpen!=(level>0))VehicleCanopies.toggle(craft,pos);}
            else if(tile instanceof TileEntityLandingGear){TileEntityLandingGear gear=(TileEntityLandingGear)tile;if(gear.getMode()!=0){
                IBlockState state=craft.view.getBlockState(pos);boolean extend=gear.getMode()==1?level>0:level==0;
                if(state.getValue(BlockTelescopicLandingGear.EXTENDED)!=extend){replaceState(pos,state.withProperty(BlockTelescopicLandingGear.EXTENDED,extend));dirty.add(pos);}
            }}
            else if(VehicleDoors.supports(tile.getBlockType())){
                int mode=tile instanceof TileEntitySpaceDoor?((TileEntitySpaceDoor)tile).getTrigger():tile instanceof TileEntityProgrammableTrapdoor?((TileEntityProgrammableTrapdoor)tile).getTrigger():1;
                if(mode!=0){boolean open=mode==1?level>0:level==0;IBlockState actual=tile.getBlockType().getActualState(craft.view.getBlockState(pos),craft.view,pos);
                    if(actual.getValue(net.minecraft.block.BlockDoor.OPEN)!=open)VehicleDoors.toggle(craft,pos);}
            }else {source.setChannelLevel(level);
                if(tile instanceof TileEntityProgrammableLight)SignalUpdateBatch.afterSignals(tile,()->ProgrammableLightConnections.refreshVehicle(craft.view,pos));
                if(tile instanceof TileEntityRedstoneLight){TileEntityRedstoneLight light=(TileEntityRedstoneLight)tile;IBlockState state=craft.view.getBlockState(pos);
                    boolean on=light.isSignalBrightness()?light.getActivationLevel()>0:(level>0)!=light.isRedstoneInverted();
                    net.minecraft.block.Block target=on && state.getBlock() instanceof BlockLampOff?((BlockLampOff)state.getBlock()).getOnBlock():!on && state.getBlock() instanceof BlockLamp?BlockLampOff.byOn(state.getBlock()):null;
                    if(target!=null)replaceState(pos,target.getDefaultState().withProperty(BlockVandorConsole.FACING,state.getValue(BlockVandorConsole.FACING)).withProperty(BlockVandorConsole.VERTICAL,state.getValue(BlockVandorConsole.VERTICAL)));
                }
                dirty.add(pos);}
        }
    }
    private final class LatchLink extends Link implements RedstoneChannelLatch {
        final RedstoneChannelLatch latch;
        LatchLink(RedstoneChannelLatch source){super(source);latch=source;}
        public boolean isChannelLatch(){return latch.isChannelLatch();}
        public boolean latchOn(){return latch.latchOn();}
        public ChannelList latchedChannels(){return latch.latchedChannels();}
        public int latchedLevel(int channel){return latch.latchedLevel(channel);}
        public int localSignalLevel(int channel){return latch.localSignalLevel(channel);}
        public void applyLinkedLatch(boolean value){latch.applyLinkedLatch(value);dirty.add(source.channelTile().getPos());}
        public void applyLinkedChannels(ChannelList value){latch.applyLinkedChannels(value);dirty.add(source.channelTile().getPos());}
        public void applyLinkedLevels(Map<Integer,Integer> value){latch.applyLinkedLevels(value);dirty.add(source.channelTile().getPos());}
    }
    private void replaceState(BlockPos pos,IBlockState state){
        List<VehicleStructure.Cell> cells=new ArrayList<>();for(VehicleStructure.Cell cell:craft.structure.cells)cells.add(cell.pos.equals(pos)?new VehicleStructure.Cell(pos,state,cell.tileData()):cell);
        boolean geometry=craft.structure.at(pos).state!=state;
        craft.updateCanopies(new VehicleStructure(cells,craft.structure.seat,craft.structure.facing,craft.structure.gearCount),geometry);
        craft.view.getTileEntity(pos).updateContainingBlockInfo();
    }
    private void add(Set<RedstoneChannelMember> wanted,RedstoneChannelMember source){if(!source.getRedstoneChannels().isEmpty())wanted.add(source);}
    public void tick(){
        if(craft.ownershipBlocked){close();return;}
        if(craft.world.isRemote || craft.view==null || craft.isDead || detached || craft.ownershipBlocked)return;
        if(revision!=craft.view.tileRevision){
        revision=craft.view.tileRevision;
        Set<RedstoneChannelMember> wanted=Collections.newSetFromMap(new IdentityHashMap<>());
        for(TileEntity tile:craft.view.tiles.values()){
            if(tile instanceof TileEntityCanopy || tile instanceof TileEntitySlidingDoor || tile instanceof TileEntityProgrammableTrapdoor
                    || tile instanceof TileEntityLandingGear && ((TileEntityLandingGear)tile).isRoot()
                    || tile instanceof TileEntityRedstoneLight
                    || tile instanceof TileEntityAnimatedScreenSelector && tile.getBlockType() instanceof BlockProgrammableLight)
                add(wanted,(RedstoneChannelMember)tile);
            if(tile instanceof TileEntityAnimatedScreenSelector && RedstoneScreenInteractions.supports(tile.getBlockType())){
                TileEntityAnimatedScreenSelector screen=(TileEntityAnimatedScreenSelector)tile;
                for(int slot=0;slot<2;slot++)if(screen.hasRedstoneScreen(slot) && RedstoneScreenInteractions.supportsSlot(tile.getBlockType(),slot))for(RedstoneScreenContents.Row row:screen.redstoneScreen(slot).rows())add(wanted,row);
            }
        }
        for(RedstoneChannelMember source:new ArrayList<>(links.keySet()))if(!wanted.contains(source)){Link old=links.remove(source);RedstoneChannels.unregister(old);}
        for(RedstoneChannelMember source:wanted)if(!links.containsKey(source)){
            Link link=source instanceof RedstoneChannelLatch?new LatchLink((RedstoneChannelLatch)source):new Link(source);links.put(source,link);RedstoneChannels.register(link);
        }
        }
        flush();
    }
    public void close(){detached=true;for(Link link:links.values())RedstoneChannels.unregister(link);links.clear();dirty.clear();}
    public void open(){detached=false;revision=-1;}
    public void persist(){if(!craft.world.isRemote && !detached)flush();}
    public static boolean supports(VehicleWorld world,BlockPos pos){
        TileEntity raw=world.getTileEntity(pos);if(!(raw instanceof TileEntityAnimatedScreenSelector))return false;
        TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)raw;
        for(int slot=0;slot<2;slot++)if(tile.hasRedstoneScreen(slot) && RedstoneScreenInteractions.supportsSlot(tile.getBlockType(),slot))return true;
        return false;
    }
    public boolean click(BlockPos pos,net.minecraft.entity.player.EntityPlayer player){
        if(player.isSneaking() || !(craft.view.getTileEntity(pos) instanceof TileEntityAnimatedScreenSelector))return false;
        TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)craft.view.getTileEntity(pos);IBlockState state=craft.view.getBlockState(pos);
        double reach=Math.min(8,player.getEntityAttribute(net.minecraft.entity.player.EntityPlayer.REACH_DISTANCE).getAttributeValue());
        Vec3d eye=player.getPositionEyes(1),end=eye.add(Vec3d.fromPitchYaw(player.rotationPitch,player.rotationYaw).scale(reach));eye=craft.toLocal(eye).subtract(new Vec3d(pos));end=craft.toLocal(end).subtract(new Vec3d(pos));
        tick();
        for(int slot=0;slot<2;slot++)if(tile.hasRedstoneScreen(slot) && RedstoneScreenInteractions.supportsSlot(state.getBlock(),slot)){
            RedstoneScreenContents contents=tile.redstoneScreen(slot);int index=RedstoneScreenInteractions.hitRow(state,tile,slot,eye,end,contents.rows().size());if(index<0)continue;
            RedstoneScreenContents.Row row=contents.rows().get(index);Link link=links.get(row);if(!(link instanceof LatchLink))return false;
            int level;
            if(row.slider){double column=RedstoneScreenInteractions.hitColumn(state,tile,slot,eye,end);if(column<RedstoneScreenContents.SLIDER_LEFT || column>=RedstoneScreenContents.SLIDER_RIGHT)return false;
                level=row.segmentValue((int)((column-RedstoneScreenContents.SLIDER_LEFT)*row.segments()/(RedstoneScreenContents.SLIDER_RIGHT-RedstoneScreenContents.SLIDER_LEFT)));
            }else level=RedstoneChannels.allPowered(craft.world,row.channels)?0:15;
            RedstoneChannels.latchLevelChanged((LatchLink)link,level);flush();return true;
        }
        return false;
    }
    private void flush(){
        if(dirty.isEmpty())return;
        Map<BlockPos,NBTTagCompound> saved=new HashMap<>();
        for(BlockPos pos:new ArrayList<>(dirty)){
            TileEntity tile=craft.view.getTileEntity(pos);if(tile==null)continue;
            NBTTagCompound data=tile.writeToNBT(new NBTTagCompound());saved.put(pos,data);
            try{ByteArrayOutputStream bytes=new ByteArrayOutputStream();net.minecraft.nbt.CompressedStreamTools.writeCompressed(data,bytes);
                if(bytes.size()>VehicleNetwork.FRAGMENT)continue;
                VehicleNetwork.Packet packet=new VehicleNetwork.Packet(VehicleNetwork.TILE);packet.entity=craft.getEntityId();packet.token=craft.getUniqueID();packet.origin=pos;packet.data=bytes.toByteArray();packet.sequence=net.minecraft.block.Block.getStateId(craft.view.getBlockState(pos));
                com.vandorlabs.network.PacketHandler.INSTANCE.sendToAllTracking(packet,craft);
                if(craft.getControllingPassenger() instanceof net.minecraft.entity.player.EntityPlayerMP)com.vandorlabs.network.PacketHandler.INSTANCE.sendTo(packet,(net.minecraft.entity.player.EntityPlayerMP)craft.getControllingPassenger());
            }catch(IOException error){throw new IllegalStateException("Cannot synchronize vehicle control",error);}
        }
        if(!saved.isEmpty()){
            List<VehicleStructure.Cell> cells=new ArrayList<>();
            for(VehicleStructure.Cell cell:craft.structure.cells)cells.add(saved.containsKey(cell.pos)?new VehicleStructure.Cell(cell.pos,cell.state,saved.get(cell.pos)):cell);
            craft.updateCanopies(new VehicleStructure(cells,craft.structure.seat,craft.structure.facing,craft.structure.gearCount),false);
        }
        dirty.clear();
    }
    static void save(EntityGroundVehicle craft,BlockPos pos,NBTTagCompound data,IBlockState state){
        List<VehicleStructure.Cell> cells=new ArrayList<>();for(VehicleStructure.Cell cell:craft.structure.cells)cells.add(cell.pos.equals(pos)?new VehicleStructure.Cell(pos,state,data):cell);
        boolean geometry=craft.structure.at(pos).state!=state || craft.view.getTileEntity(pos) instanceof TileEntityLandingGear;
        craft.updateCanopies(new VehicleStructure(cells,craft.structure.seat,craft.structure.facing,craft.structure.gearCount),geometry);
    }
    public static void apply(EntityGroundVehicle craft,VehicleNetwork.Packet packet){
        TileEntity tile=craft.view.getTileEntity(packet.origin);if(tile==null)return;
        try{NBTTagCompound data=net.minecraft.nbt.CompressedStreamTools.readCompressed(new ByteArrayInputStream(packet.data));
            if(!data.getString("id").equals(tile.writeToNBT(new NBTTagCompound()).getString("id")))return;
            tile.readFromNBT(data);save(craft,packet.origin,data,net.minecraft.block.Block.getStateById(packet.sequence));tile.updateContainingBlockInfo();
        }catch(IOException | RuntimeException error){VehicleCompatibility.warn(tile.getBlockType(),"control update",error);}
    }
}
