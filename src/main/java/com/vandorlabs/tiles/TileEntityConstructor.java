package com.vandorlabs.tiles;

import com.vandorlabs.blocks.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.*;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;

/** Validate first, then reserve a footprint; reconstruction restores the consumed frame. */
public final class TileEntityConstructor extends TileEntitySecuredDisplay implements ITickable {
    private final com.vandorlabs.machines.SynthesisRuntime synthesisRuntime=new com.vandorlabs.machines.SynthesisRuntime(this);
    private boolean synthesisSync,synthesisInputsReady;
    public boolean synthesis(){BlockConstructorCore core=core();return core!=null&&core.design.equals("synthesis_tower");}
    public net.minecraftforge.items.ItemStackHandler synthesisInventory(){return synthesisRuntime.inventory;}
    public BlockPos synthesisPowerPort(){return cell(2,0,2);}
    public int selectedDesign(){return synthesisRuntime.selected();}
    public int buildDesign(){return synthesisRuntime.building();}
    public float buildProgress(){return synthesisRuntime.progress();}
    public boolean processing(){return synthesisRuntime.active();}
    public boolean externalPowerConnected(){return synthesisRuntime.connected;}
    public int batteryEnergy(){return com.vandorlabs.rack.ModuleBatteryData.energy(synthesisInventory().getStackInSlot(2));}
    public int batteryCapacity(){return com.vandorlabs.rack.ModuleBatteryData.isBattery(synthesisInventory().getStackInSlot(2))?com.vandorlabs.rack.ModuleBatteryData.CAPACITY:0;}
    public void selectDesign(int design){if(authoritative()&&formed()&&synthesis())synthesisRuntime.select(design);}
    public void synthesisChanged(){markDirty();synthesisSync=true;}
    private int synthesisActivation;
    private boolean synthesisInput;
    public int synthesisActivation(){return synthesisActivation;}
    public boolean synthesisEnabled(){return synthesisInputsReady&&(synthesisActivation==0||synthesisActivation==1&&synthesisInput||synthesisActivation==2&&!synthesisInput);}
    public void configureSynthesisRedstone(int mode,com.vandorlabs.redstone.ChannelList channels){if(!authoritative()||!synthesis()||mode<0||mode>2||channels==null)return;synthesisActivation=mode;setRedstoneChannels(channels);refreshInputs();publish();}
    public boolean synthesisModuleInteraction(net.minecraft.entity.player.EntityPlayer player,EnumHand hand){
        if(!synthesis()||!formed()||!authoritative()||!isUsableByPlayer(player)||!com.vandorlabs.items.ConfigurationAccess.canEditDialog(player,world,pos))return false;
        Vec3d local=new Vec3d(.67,.56,0);
        MachineMountSurface mount=new MachineMountSurface(MachineMountSurface.rotate(local,facing(),core().width,core().depth).add(new Vec3d(pos.subtract(pivot()))),facing(),.72,.72);
        if(!mount.hit(player))return false;
        net.minecraft.item.ItemStack held=player.getHeldItem(hand),installed=synthesisInventory().getStackInSlot(2);
        if(installed.isEmpty()&&com.vandorlabs.rack.ModuleBatteryData.isBattery(held)){net.minecraft.item.ItemStack one=held.copy();one.setCount(1);synthesisInventory().setStackInSlot(2,one);held.shrink(1);publish();return true;}
        if(held.isEmpty()&&!installed.isEmpty()){player.setHeldItem(hand,synthesisInventory().extractItem(2,1,false));publish();return true;}return false;
    }
    public boolean completedCoreHit(net.minecraft.entity.player.EntityPlayer player){
        if(!synthesis()||synthesisInventory().getStackInSlot(1).isEmpty())return false;
        Vec3d origin=new Vec3d(pos.subtract(pivot()));
        Vec3d a=MachineMountSurface.rotate(new Vec3d(1,2.125,1.125),facing(),core().width,core().depth).add(origin);
        Vec3d b=MachineMountSurface.rotate(new Vec3d(2,3.125,2.125),facing(),core().width,core().depth).add(origin);
        AxisAlignedBB bounds=new AxisAlignedBB(Math.min(a.x,b.x),a.y,Math.min(a.z,b.z),Math.max(a.x,b.x),b.y,Math.max(a.z,b.z));
        Vec3d eye=player.getPositionEyes(1);double reach=player.getEntityAttribute(net.minecraft.entity.player.EntityPlayer.REACH_DISTANCE).getAttributeValue();RayTraceResult hit=bounds.calculateIntercept(eye,eye.add(player.getLook(1).scale(reach)));return hit!=null&&eye.squareDistanceTo(hit.hitVec)<=reach*reach;
    }
    public boolean takeCompletedCore(net.minecraft.entity.player.EntityPlayer player,EnumHand hand){
        if(!authoritative()||!isUsableByPlayer(player)||!com.vandorlabs.items.ConfigurationAccess.canEditDialog(player,world,pos)||!completedCoreHit(player))return false;
        net.minecraft.item.ItemStack stack=synthesisInventory().extractItem(1,1,false);
        if(!player.inventory.addItemStackToInventory(stack))player.dropItem(stack,false);publish();return true;
    }
    public <T>T synthesisCapability(net.minecraftforge.common.capabilities.Capability<T> capability,BlockPos at,EnumFacing side){
        if(!formed()||!synthesis()||side==null||world.isRemote||com.vandorlabs.gear.ArmTransactions.locked(world,pos))return null;
        if(capability==net.minecraftforge.energy.CapabilityEnergy.ENERGY&&at.equals(synthesisPowerPort())&&(side==facing().getOpposite()||side==EnumFacing.DOWN))return net.minecraftforge.energy.CapabilityEnergy.ENERGY.cast(synthesisRuntime.energy);
        if(capability==net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY&&at.equals(cell(0,0,2))){
            if(side==facing().getOpposite())return net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(synthesisRuntime.port(true));
            if(side==facing().rotateYCCW()||side==EnumFacing.DOWN)return net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(synthesisRuntime.port(false));
        }return null;
    }
    @Override public boolean hasCapability(net.minecraftforge.common.capabilities.Capability<?> cap,EnumFacing side){return synthesisCapability(cap,pos,side)!=null||super.hasCapability(cap,side);}
    @Override public <T>T getCapability(net.minecraftforge.common.capabilities.Capability<T> cap,EnumFacing side){T result=synthesisCapability(cap,pos,side);return result!=null?result:super.getCapability(cap,side);}
    public TileEntityConstructor(){super();}
    @Override public boolean chip(){return true;}
    @Override protected boolean displayBlock(Block block){return block instanceof BlockConstructorCore;}
    @Override public boolean accepts(net.minecraft.item.ItemStack stack){BlockConstructorCore core=core();return core!=null&&core.specification().hasChip()&&super.accepts(stack);}
    @Override public boolean isItemValidForSlot(int slot,net.minecraft.item.ItemStack stack){return formed()&&super.isItemValidForSlot(slot,stack);}
    private int[] surfaceTextures=new int[0];
    @Override public boolean nativeArtwork(){return true;}
    @Override public int surfaceCount(){BlockConstructorCore core=core();return core==null||core.specification().surfaces==null?0:core.specification().surfaces.length;}
    @Override public String surfaceLabel(int slot){return core().specification().surfaces[slot].label;}
    @Override public int surfaceChoice(int slot){return slot<surfaceTextures.length?surfaceTextures[slot]:-2;}
    public static boolean validSurface(int choice){return choice==-2||com.vandorlabs.navigation.NavigationScreenSettings.validTexture(choice);}
    public void configureMachine(com.vandorlabs.navigation.NavigationScreenSettings config,int[] textures,boolean any,int allowed,int behavior,com.vandorlabs.redstone.ChannelList channels){
        if(!authoritative()||textures.length!=surfaceCount()||textures.length>3)return;
        for(int choice:textures)if(!validSurface(choice))return;
        surfaceTextures=textures.clone();configureDisplay(config,any,allowed,behavior,channels);publish();
    }
    private com.vandorlabs.drives.DriveSettings drive=com.vandorlabs.drives.DriveSettings.DEFAULT;
    private int driveChannelLevel,drivePhysicalLevel,driveLevel=15;private boolean driveConfigPresent;
    private final java.util.Map<BlockPos,Integer> drivePhysicalInputs=new java.util.HashMap<>();
    public boolean isDrive(){BlockConstructorCore block=core();return block!=null&&(block.design.equals("longburn_drive")||block.design.equals("meridian_core")||block.design.equals("oculus_drive"));}
    public boolean driveHasParticles(){return isDrive()&&core().design.equals("longburn_drive");}
    public com.vandorlabs.drives.DriveSettings driveSettings(){if(isDrive()&&!driveHasParticles()&&drive.particles!=0)drive=new com.vandorlabs.drives.DriveSettings(drive.levels,drive.inverted,0,drive.threshold);return drive;}
    public int driveLevel(){return driveLevel;}
    public void configureDrive(com.vandorlabs.drives.DriveSettings settings){if(!authoritative()||!isDrive()||settings==null)return;drive=settings;driveSettings();driveConfigPresent=true;refreshInputs();publish();}
    @Override protected boolean inputPowered(boolean aggregate){if(synthesis()){synthesisInput=aggregate;return synthesisEnabled();}if(!isDrive())return aggregate;int next=drive.activation(screenSettings().redstone,Math.max(driveChannelLevel,drivePhysicalLevel)==0&&aggregate?15:Math.max(driveChannelLevel,drivePhysicalLevel));if(driveLevel!=next){driveLevel=next;publish();}return next>0;}
    @Override public void setChannelLevel(int level){int next=com.vandorlabs.drives.DriveSettings.clamp(level);boolean changed=driveChannelLevel!=next;driveChannelLevel=next;setChannelSignal(next>0);if(isDrive()&&changed){refresh();publish();}}
    @Override public void update(){if(world!=null&&!world.isRemote&&formed()&&synthesis()){if(!com.vandorlabs.gear.ArmTransactions.locked(world,pos))synthesisRuntime.tick();if(synthesisSync&&world.getTotalWorldTime()%5==0){synthesisSync=false;publish();}}if(world!=null&&world.isRemote&&isDrive()&&renderReady()&&screenOn()&&drive.plume(true,driveLevel))com.vandorlabs.drives.DriveParticles.emit(this);}
    private boolean changing;private IBlockState removedState;
    private int coreX,coreZ;
    private java.util.UUID renderIdentity;
    public java.util.UUID renderEpoch;public BlockPos renderOwner;public int renderCell=-1;private boolean renderAppearance;
    public java.util.UUID renderIdentity(){if(renderIdentity==null)renderIdentity=java.util.UUID.randomUUID();return renderIdentity;}
    public void renderIdentity(java.util.UUID identity){renderIdentity=identity;}
    public boolean renderAppearanceReady(){return renderAppearance;}
    private void renderPacket(NBTTagCompound tag){
        renderAppearance=tag.hasKey("CoreRenderReady",1)&&tag.getBoolean("CoreRenderReady");
        if(tag.hasUniqueId("CoreRenderIdentity"))renderIdentity=tag.getUniqueId("CoreRenderIdentity");renderEpoch=null;renderOwner=null;renderCell=-1;
        if(tag.hasUniqueId("CoreRenderEpoch")&&tag.hasKey("CoreRenderOwner",4)&&tag.hasKey("CoreRenderCell",3)){int cell=tag.getInteger("CoreRenderCell");if(cell>=0&&cell<com.vandorlabs.gear.ArmPayload.MAX_BLOCKS){renderEpoch=tag.getUniqueId("CoreRenderEpoch");renderOwner=BlockPos.fromLong(tag.getLong("CoreRenderOwner"));if(Math.abs((long)renderOwner.getX()-pos.getX())>128||Math.abs((long)renderOwner.getY()-pos.getY())>128||Math.abs((long)renderOwner.getZ()-pos.getZ())>128){renderOwner=null;renderEpoch=null;}else renderCell=cell;}}
        if(com.vandorlabs.VandorLabs.proxy!=null)com.vandorlabs.VandorLabs.proxy.coreAppearanceChanged(this);
    }
    private com.vandorlabs.machines.CoreLayout cachedLayout;private BlockConstructorCore cachedLayoutCore;
    private int activity=-1;
    private String assemblyProblem="";
    /** Facing/formed changes belong to this assembly; replacing the tile loses its detected corner. */
    @Override public boolean shouldRefresh(net.minecraft.world.World world,BlockPos pos,IBlockState before,IBlockState after){if(before!=after)renderAppearance=false;return before.getBlock()!=after.getBlock();}
    /** Render lists and shadow passes may retain this tile after its block disappears. */
    private IBlockState coreState(){
        if(removedState!=null)return removedState.getBlock() instanceof BlockConstructorCore?removedState:null;
        if(world==null||isInvalid()||!world.isBlockLoaded(pos))return null;
        IBlockState state=world.getBlockState(pos);return state.getBlock() instanceof BlockConstructorCore?state:null;
    }
    public BlockConstructorCore core(){IBlockState state=coreState();return state==null?null:(BlockConstructorCore)state.getBlock();}
    public EnumFacing facing(){IBlockState state=coreState();return state==null?EnumFacing.NORTH:state.getValue(BlockVandorDirectional.FACING);}
    public boolean formed(){IBlockState state=coreState();return state!=null&&state.getValue(BlockConstructorCore.FORMED);}
    public com.vandorlabs.machines.CoreLayout layout(){
        BlockConstructorCore core=core();if(core==null)return null;int face=facing().getIndex();
        if(cachedLayout==null||cachedLayoutCore!=core||cachedLayout.facing!=face||cachedLayout.cornerX!=coreX||cachedLayout.cornerZ!=coreZ){cachedLayout=new com.vandorlabs.machines.CoreLayout(core.width,core.height,core.depth,face,coreX,coreZ);cachedLayoutCore=core;}return cachedLayout;
    }
    public int activity(){BlockConstructorCore core=core();return activity>=0?activity:core!=null&&core.specification().state_labels!=null?2:1;}
    public void cycleActivity(){activity=(activity()+1)%3;markDirty();world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),2);}
    public BlockPos pivot(){com.vandorlabs.machines.CoreLayout shape=layout();return shape==null?BlockPos.ORIGIN:new BlockPos(shape.pivotX(),0,shape.pivotZ());}
    public BlockPos cell(int x,int y,int z){com.vandorlabs.machines.CoreLayout shape=layout();return shape==null?pos:pos.add(shape.x(x,z),y,shape.z(x,z));}
    public String assemblyProblem(){return assemblyProblem;}
    public boolean frame(int x,int y,int z){com.vandorlabs.machines.CoreLayout shape=layout();return shape!=null&&shape.frame(x,y,z);}
    public boolean assemble(){
        if(world==null || world.isRemote || changing || coreState()==null || formed())return false;
        Block matter=Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:programmable_matter_block"));
        EnumFacing selected=null;int selectedX=0,selectedZ=0,best=Integer.MAX_VALUE;
        EnumFacing preferred=facing();
        // Prefer the placed facing, but infer the footprint rather than requiring a hidden corner convention.
        for(int turn=0;turn<4 && selected==null;turn++){
            EnumFacing direction=EnumFacing.getHorizontal((preferred.getHorizontalIndex()+turn)%4);
            for(int cornerX:new int[]{0,core().width-1})for(int cornerZ:new int[]{0,core().depth-1}){
                com.vandorlabs.machines.CoreLayout candidate=new com.vandorlabs.machines.CoreLayout(core().width,core().height,core().depth,direction.getIndex(),cornerX,cornerZ);
                int errors=0;String first="";
                for(int y=0;y<core().height;y++)for(int x=0;x<core().width;x++)for(int z=0;z<core().depth;z++){
                    BlockPos p=pos.add(candidate.x(x,z),y,candidate.z(x,z));if(p.equals(pos))continue;
                    boolean loaded=world.isBlockLoaded(p);
                    if(!loaded || (frame(x,y,z)?world.getBlockState(p).getBlock()!=matter:!world.isAirBlock(p))){
                        if(errors++==0)first=(loaded?(frame(x,y,z)?world.getBlockState(p).getBlock() instanceof BlockConstructorCore?"Use a Matter Block, not another Core":"Matter Block required":"Interior must be air"):"Chunk not loaded")+" at "+p.getX()+", "+p.getY()+", "+p.getZ();
                    }
                }
                if(errors<best){best=errors;assemblyProblem=first+" ("+errors+" mismatched cells in closest frame).";}
                if(errors==0 && selected==null){selected=direction;selectedX=cornerX;selectedZ=cornerZ;}
            }
        }
        if(selected==null)return false;
        changing=true;
        try{
            coreX=selectedX;coreZ=selectedZ;
            world.setBlockState(pos,world.getBlockState(pos).withProperty(BlockVandorDirectional.FACING,selected),2);
            Block part=Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:constructor_part"));
            for(int y=0;y<core().height;y++)for(int x=0;x<core().width;x++)for(int z=0;z<core().depth;z++){
                BlockPos p=cell(x,y,z);if(p.equals(pos))continue;
                world.setBlockState(p,part.getDefaultState(),2);((TileEntityConstructorPart)world.getTileEntity(p)).owner(pos,frame(x,y,z));
            }
            world.setBlockState(pos,world.getBlockState(pos).withProperty(BlockConstructorCore.FORMED,true),3);renderAppearance=true;markDirty();
            world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),3);refreshInputs();return true;
        }finally{changing=false;}
    }
    public void dismantle(IBlockState state){removedState=state;try{dismantle();}finally{removedState=null;}}
    public void dismantle(){
        if(world==null || world.isRemote || changing || !formed())return;
        changing=true;
        try{
            TokenSlotInteractions.drop(world,pos);if(synthesis())synthesisRuntime.drop();
            Block matter=Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:programmable_matter_block"));
            for(int y=0;y<core().height;y++)for(int x=0;x<core().width;x++)for(int z=0;z<core().depth;z++){
                BlockPos p=cell(x,y,z);if(p.equals(pos) || !world.isBlockLoaded(p))continue;TileEntity part=world.getTileEntity(p);
                if(part instanceof TileEntityConstructorPart && ((TileEntityConstructorPart)part).owner().equals(pos))world.setBlockState(p,frame(x,y,z)?matter.getDefaultState():net.minecraft.init.Blocks.AIR.getDefaultState(),2);
            }
            if(removedState==null)world.setBlockState(pos,world.getBlockState(pos).withProperty(BlockConstructorCore.FORMED,false),3);markDirty();refresh();
        }finally{changing=false;}
    }
    @Override public boolean supportsSecurity(){return false;}
    private final java.util.Set<BlockPos> poweredInputs=new java.util.HashSet<>();
    @Override protected boolean physicalPower(){
        poweredInputs.clear();drivePhysicalInputs.clear();drivePhysicalLevel=0;if(!formed())return false;
        BlockConstructorCore core=core();
        for(int y=0;y<core.height;y++)for(int x=0;x<core.width;x++)for(int z=0;z<core.depth;z++){
            BlockPos at=cell(x,y,z);if(world.isBlockLoaded(at)){int level=com.vandorlabs.redstone.LoadedRedstonePower.level(world,at);if(level>0)poweredInputs.add(at);if(isDrive()&&level>0){drivePhysicalInputs.put(at,level);drivePhysicalLevel=Math.max(drivePhysicalLevel,level);}}
        }
        return !poweredInputs.isEmpty();
    }
    public void sampleInput(BlockPos at){
        if(!authoritative()||changing)return;
        if(!formed()){poweredInputs.clear();physicalInput(false);return;}
        if(!world.isBlockLoaded(at))return;
        boolean member=at.equals(pos);net.minecraft.tileentity.TileEntity raw=world.getTileEntity(at);
        member|=raw instanceof TileEntityConstructorPart&&((TileEntityConstructorPart)raw).owner().equals(pos);
        if(!member)return;
        int level=com.vandorlabs.redstone.LoadedRedstonePower.level(world,at);if(level>0)poweredInputs.add(at.toImmutable());else poweredInputs.remove(at);
        if(isDrive()){if(level>0)drivePhysicalInputs.put(at.toImmutable(),level);else drivePhysicalInputs.remove(at);drivePhysicalLevel=0;for(int input:drivePhysicalInputs.values())drivePhysicalLevel=Math.max(drivePhysicalLevel,input);}
        physicalInput(!poweredInputs.isEmpty());if(isDrive())refresh();
    }
    @Override public void refreshInputs(){super.refreshInputs();if(authoritative()&&synthesis()){synthesisInputsReady=true;refresh();}}
    @Override public void onLoad(){synthesisInputsReady=false;super.onLoad();DeferredTileLoad.schedule(this,()->{refreshInputs();if(com.vandorlabs.VandorLabs.proxy!=null)com.vandorlabs.VandorLabs.proxy.coreAppearanceChanged(this);});}
    public com.vandorlabs.blocks.MachineMountSurface mount(){
        if(!renderReady()||!core().specification().hasChip())return null;FrameMachineDesign spec=core().specification();
        Vec3d local=new Vec3d(spec.chip_anchor[0],spec.chip_anchor[1],spec.chip_anchor[2]);
        Vec3d center=MachineMountSurface.rotate(local,facing(),core().width,core().depth).add(new Vec3d(pos.subtract(pivot())));
        return new MachineMountSurface(center,facing(),TokenSlotGeometry.CHIP_SIZE,TokenSlotGeometry.CHIP_SIZE);
    }
    @Override public boolean isUsableByPlayer(net.minecraft.entity.player.EntityPlayer player){return formed()&&super.isUsableByPlayer(player);}
    @Override public AxisAlignedBB getRenderBoundingBox(){if(!formed())return new AxisAlignedBB(pos);BlockPos origin=pos.subtract(pivot());boolean turn=facing().getAxis()==EnumFacing.Axis.X;return new AxisAlignedBB(origin.getX(),origin.getY(),origin.getZ(),origin.getX()+(turn?core().depth:core().width),origin.getY()+core().height,origin.getZ()+(turn?core().width:core().depth));}
    @Override public double getMaxRenderDistanceSquared(){return 65536;}
    @Override public boolean hasFastRenderer(){BlockConstructorCore core=core();return core==null||!core.specification().hasChip()&&surfaceCount()==0;}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){super.writeToNBT(tag);synthesisRuntime.write(tag);tag.setInteger("SynthesisActivation",synthesisActivation);tag.setInteger("ConstructorCoreX",coreX);tag.setInteger("ConstructorCoreZ",coreZ);tag.setInteger("MachineActivity",activity());tag.setIntArray("MachineSurfaceTextures",surfaceTextures);if(isDrive()||driveConfigPresent){driveSettings().write(tag);tag.setInteger("DriveLevel",driveLevel);}return tag;}
    @Override public void readFromNBT(NBTTagCompound tag){synthesisInputsReady=false;renderAppearance=false;super.readFromNBT(tag);synthesisRuntime.read(tag);synthesisActivation=Math.max(0,Math.min(2,tag.getInteger("SynthesisActivation")));driveConfigPresent=tag.hasKey("DriveParticles",3);drive=com.vandorlabs.drives.DriveSettings.read(tag);driveLevel=com.vandorlabs.drives.DriveSettings.clamp(tag.hasKey("DriveLevel",3)?tag.getInteger("DriveLevel"):15);coreX=Math.max(0,Math.min(15,tag.getInteger("ConstructorCoreX")));coreZ=Math.max(0,Math.min(15,tag.getInteger("ConstructorCoreZ")));surfaceTextures=tag.getIntArray("MachineSurfaceTextures").clone();if(surfaceTextures.length>3)surfaceTextures=new int[0];for(int i=0;i<surfaceTextures.length;i++)if(!validSurface(surfaceTextures[i]))surfaceTextures[i]=-2;activity=tag.hasKey("MachineActivity")?Math.max(0,Math.min(2,tag.getInteger("MachineActivity"))):-1;renderAppearance=true;}
    @Override public NBTTagCompound getUpdateTag(){NBTTagCompound tag=writeToNBT(new NBTTagCompound());tag.setBoolean("CoreRenderReady",renderAppearance);tag.setUniqueId("CoreRenderIdentity",renderIdentity());if(renderEpoch!=null){tag.setUniqueId("CoreRenderEpoch",renderEpoch);tag.setLong("CoreRenderOwner",renderOwner.toLong());tag.setInteger("CoreRenderCell",renderCell);}return tag;}
    @Override public void handleUpdateTag(NBTTagCompound tag){readFromNBT(tag);renderPacket(tag);}
    @Override public SPacketUpdateTileEntity getUpdatePacket(){return new SPacketUpdateTileEntity(pos,0,getUpdateTag());}
    @Override public void onDataPacket(NetworkManager manager,SPacketUpdateTileEntity packet){readFromNBT(packet.getNbtCompound());renderPacket(packet.getNbtCompound());}
}
