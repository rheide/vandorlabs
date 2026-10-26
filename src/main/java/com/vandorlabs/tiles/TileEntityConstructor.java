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
    @Override protected boolean inputPowered(boolean aggregate){if(!isDrive())return aggregate;int next=drive.activation(screenSettings().redstone,Math.max(driveChannelLevel,drivePhysicalLevel)==0&&aggregate?15:Math.max(driveChannelLevel,drivePhysicalLevel));if(driveLevel!=next){driveLevel=next;publish();}return next>0;}
    @Override public void setChannelLevel(int level){int next=com.vandorlabs.drives.DriveSettings.clamp(level);boolean changed=driveChannelLevel!=next;driveChannelLevel=next;setChannelSignal(next>0);if(isDrive()&&changed){refresh();publish();}}
    @Override public void update(){if(world!=null&&world.isRemote&&isDrive()&&renderReady()&&screenOn()&&drive.plume(true,driveLevel))com.vandorlabs.drives.DriveParticles.emit(this);}
    private boolean changing;private IBlockState removedState;
    private int coreX,coreZ;
    private int activity=-1;
    private String assemblyProblem="";
    /** Facing/formed changes belong to this assembly; replacing the tile loses its detected corner. */
    @Override public boolean shouldRefresh(net.minecraft.world.World world,BlockPos pos,IBlockState before,IBlockState after){return before.getBlock()!=after.getBlock();}
    /** Render lists and shadow passes may retain this tile after its block disappears. */
    private IBlockState coreState(){
        if(removedState!=null)return removedState.getBlock() instanceof BlockConstructorCore?removedState:null;
        if(world==null||isInvalid()||!world.isBlockLoaded(pos))return null;
        IBlockState state=world.getBlockState(pos);return state.getBlock() instanceof BlockConstructorCore?state:null;
    }
    public BlockConstructorCore core(){IBlockState state=coreState();return state==null?null:(BlockConstructorCore)state.getBlock();}
    public EnumFacing facing(){IBlockState state=coreState();return state==null?EnumFacing.NORTH:state.getValue(BlockVandorDirectional.FACING);}
    public boolean formed(){IBlockState state=coreState();return state!=null&&state.getValue(BlockConstructorCore.FORMED);}
    private BlockPos rotated(int x,int y,int z,EnumFacing direction){BlockConstructorCore core=core();return core==null?BlockPos.ORIGIN:com.vandorlabs.vehicle.VehicleRotation.cell(new BlockPos(x,y,z),core.width,core.depth,(direction.getHorizontalIndex()+2)&3);}
    public int activity(){BlockConstructorCore core=core();return activity>=0?activity:core!=null&&core.specification().state_labels!=null?2:1;}
    public void cycleActivity(){activity=(activity()+1)%3;markDirty();world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),2);}
    public BlockPos pivot(){return rotated(coreX,0,coreZ,facing());}
    public BlockPos cell(int x,int y,int z){return pos.add(rotated(x,y,z,facing()).subtract(pivot()));}
    private BlockPos candidateCell(int x,int y,int z,EnumFacing direction,int cornerX,int cornerZ){return pos.add(rotated(x,y,z,direction).subtract(rotated(cornerX,0,cornerZ,direction)));}
    public String assemblyProblem(){return assemblyProblem;}
    public boolean frame(int x,int y,int z){if(core()==null)return false;return y==0 || (x==0 || x==core().width-1) && (z==0 || z==core().depth-1) || y==core().height-1 && (x==0 || x==core().width-1 || z==0 || z==core().depth-1);}
    public boolean assemble(){
        if(world==null || world.isRemote || changing || coreState()==null || formed())return false;
        Block matter=Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:programmable_matter_block"));
        EnumFacing selected=null;int selectedX=0,selectedZ=0,best=Integer.MAX_VALUE;
        EnumFacing preferred=facing();
        // Prefer the placed facing, but infer the footprint rather than requiring a hidden corner convention.
        for(int turn=0;turn<4 && selected==null;turn++){
            EnumFacing direction=EnumFacing.getHorizontal((preferred.getHorizontalIndex()+turn)%4);
            for(int cornerX:new int[]{0,core().width-1})for(int cornerZ:new int[]{0,core().depth-1}){
                int errors=0;String first="";
                for(int y=0;y<core().height;y++)for(int x=0;x<core().width;x++)for(int z=0;z<core().depth;z++){
                    BlockPos p=candidateCell(x,y,z,direction,cornerX,cornerZ);if(p.equals(pos))continue;
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
            world.setBlockState(pos,world.getBlockState(pos).withProperty(BlockConstructorCore.FORMED,true),3);markDirty();
            world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),3);refreshInputs();return true;
        }finally{changing=false;}
    }
    public void dismantle(IBlockState state){removedState=state;try{dismantle();}finally{removedState=null;}}
    public void dismantle(){
        if(world==null || world.isRemote || changing || !formed())return;
        changing=true;
        try{
            TokenSlotInteractions.drop(world,pos);
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
    @Override public void onLoad(){super.onLoad();DeferredTileLoad.schedule(this,this::refreshInputs);}
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
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){super.writeToNBT(tag);tag.setInteger("ConstructorCoreX",coreX);tag.setInteger("ConstructorCoreZ",coreZ);tag.setInteger("MachineActivity",activity());tag.setIntArray("MachineSurfaceTextures",surfaceTextures);if(isDrive()||driveConfigPresent){driveSettings().write(tag);tag.setInteger("DriveLevel",driveLevel);}return tag;}
    @Override public void readFromNBT(NBTTagCompound tag){super.readFromNBT(tag);driveConfigPresent=tag.hasKey("DriveParticles",3);drive=com.vandorlabs.drives.DriveSettings.read(tag);driveLevel=com.vandorlabs.drives.DriveSettings.clamp(tag.hasKey("DriveLevel",3)?tag.getInteger("DriveLevel"):15);coreX=Math.max(0,Math.min(15,tag.getInteger("ConstructorCoreX")));coreZ=Math.max(0,Math.min(15,tag.getInteger("ConstructorCoreZ")));surfaceTextures=tag.getIntArray("MachineSurfaceTextures").clone();if(surfaceTextures.length>3)surfaceTextures=new int[0];for(int i=0;i<surfaceTextures.length;i++)if(!validSurface(surfaceTextures[i]))surfaceTextures[i]=-2;activity=tag.hasKey("MachineActivity")?Math.max(0,Math.min(2,tag.getInteger("MachineActivity"))):-1;}
    @Override public NBTTagCompound getUpdateTag(){return writeToNBT(new NBTTagCompound());}
    @Override public SPacketUpdateTileEntity getUpdatePacket(){return new SPacketUpdateTileEntity(pos,0,getUpdateTag());}
    @Override public void onDataPacket(NetworkManager manager,SPacketUpdateTileEntity packet){readFromNBT(packet.getNbtCompound());}
}
