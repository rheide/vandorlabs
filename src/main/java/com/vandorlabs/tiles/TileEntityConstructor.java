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
    // Old captured Cores and recovery receipts compare their own NBT exactly.
    // Preserve absent 2.10 fields until the corresponding state is actually edited.
    private boolean synthesisDataPresent=true,synthesisSequencesPresent=true,synthesisActivationPresent=true;
    public boolean builder(){return core()!=null&&com.vandorlabs.machines.BuilderRecipeCatalog.machine(core().design);}
    public String builderMachine(){return core()==null?"synthesis_tower":core().design;}
    public java.util.List<String> builderRecipes(){return synthesisRuntime.recipes();}
    public String builderRecipe(int index){return synthesisRuntime.recipe(index);}
    public int builderRecipeIndex(String id){return synthesisRuntime.index(id);}
    public net.minecraft.item.ItemStack builderResult(int index){return synthesisRuntime.output(index);}
    public boolean builderInput(net.minecraft.item.ItemStack stack){return synthesisRuntime.input(stack);}
    public boolean synthesis(){BlockConstructorCore core=core();return core!=null&&core.design.equals("synthesis_tower");}
    public net.minecraftforge.items.ItemStackHandler synthesisInventory(){return synthesisRuntime.inventory;}
    public BlockPos synthesisPowerPort(){return cell(core().width-1,0,core().depth-1);}
    public BlockPos synthesisItemPort(){return cell(0,0,core().depth-1);}
    public int selectedDesign(){return synthesisRuntime.selected();}
    public int buildDesign(){return synthesisRuntime.building();}
    public float buildProgress(){return synthesisRuntime.progress();}
    public boolean digitizing(){return synthesis()&&synthesisRuntime.digitizing();}
    public boolean setDigitizing(boolean value){return synthesisRuntime.mode(value);}
    public net.minecraft.item.ItemStack digitizeBlock(){return synthesisRuntime.scanningBlock();}
    public boolean processing(){return synthesisRuntime.active();}
    public boolean externalPowerConnected(){return synthesisRuntime.connected;}
    private int synthesisConnections;private boolean readingSynthesis;
    public int synthesisConnections(){return synthesisConnections;}
    private final long[] batterySequences=new long[4];
    public long batterySequence(int bay){return bay>=0&&bay<4?batterySequences[bay]:0;}
    public net.minecraft.item.ItemStack batteryStack(int bay){return bay>=0&&bay<4?synthesisInventory().getStackInSlot(2+bay):net.minecraft.item.ItemStack.EMPTY;}
    public int batteryEnergy(int bay){return com.vandorlabs.rack.ModuleBatteryData.energy(batteryStack(bay));}
    public int batteryEnergy(){int total=0;for(int bay=0;bay<4;bay++)total+=batteryEnergy(bay);return total;}
    public int batteryCapacity(){int total=0;for(int bay=0;bay<4;bay++)if(com.vandorlabs.rack.ModuleBatteryData.valid(batteryStack(bay))&&!batteryStack(bay).isEmpty())total+=com.vandorlabs.rack.ModuleBatteryData.CAPACITY;return total;}
    public int receiveBatteryEnergy(int amount,boolean simulate){return transferBatteryEnergy(amount,simulate,true);}
    public int extractBatteryEnergy(int amount,boolean simulate){return transferBatteryEnergy(amount,simulate,false);}
    private int batteryPixels(){int packed=0;for(int bay=0;bay<4;bay++)packed=com.vandorlabs.rack.BatteryChargeBar.pack(packed,bay,batteryEnergy(bay),com.vandorlabs.rack.ModuleBatteryData.CAPACITY);return packed;}
    private int transferBatteryEnergy(int amount,boolean simulate,boolean receive){int before=simulate?0:batteryPixels(),total=0;for(int bay=0;bay<4&&total<amount;bay++)total+=receive?com.vandorlabs.rack.ModuleBatteryData.receive(batteryStack(bay),amount-total,simulate):com.vandorlabs.rack.ModuleBatteryData.extract(batteryStack(bay),amount-total,simulate);if(total>0&&!simulate){synthesisChanged();if(before!=batteryPixels())publishSynthesisVisual();}return total;}
    private void publishSynthesisVisual(){if(!readingSynthesis&&world!=null&&!world.isRemote&&world.isBlockLoaded(pos,false)&&world.getTileEntity(pos)==this){synthesisSync=false;publish();}}
    public void refreshSynthesisConnections(){if(world==null||world.isRemote||!world.isBlockLoaded(pos,false)||world.getTileEntity(pos)!=this||!formed()||!builder())return;int mask=0;if(endpoint(synthesisPowerPort(),facing().getOpposite(),true))mask|=1;if(endpoint(synthesisPowerPort(),EnumFacing.DOWN,true))mask|=2;if(endpoint(synthesisItemPort(),facing().getOpposite(),false))mask|=4;if(endpoint(synthesisItemPort(),facing().rotateYCCW(),false))mask|=8;if(endpoint(synthesisItemPort(),EnumFacing.DOWN,false))mask|=16;boolean connected=com.vandorlabs.machines.SynthesisConnections.power(mask);if(mask!=synthesisConnections||connected!=synthesisRuntime.connected){synthesisConnections=mask;synthesisRuntime.connected=connected;publishSynthesisVisual();}}
    private boolean endpoint(BlockPos port,EnumFacing side,boolean energy){BlockPos at=port.offset(side);if(!world.isBlockLoaded(at,false))return false;TileEntity tile=world.getTileEntity(at);if(tile==null||tile.isInvalid())return false;return energy?com.vandorlabs.ports.EnergyEndpointPresence.connected(tile,side.getOpposite()):tile.getCapability(net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,side.getOpposite())!=null||tile instanceof net.minecraft.inventory.IInventory;}
    public boolean synthesisReady(){return synthesisInputsReady;}
    public boolean working(){return synthesisRuntime.working();}
    public boolean synthesisWorking(){return working();}
    public void synthesisSlotChanged(int slot){if(world!=null&&world.isRemote)return;if(readingSynthesis)return;if(slot>=2&&slot<6){batterySequences[slot-2]++;synthesisSequencesPresent=true;}synthesisChanged();if(slot>=2&&slot<6)publishSynthesisVisual();}
    public void selectDesign(int design){if(authoritative()&&formed()&&builder())synthesisRuntime.select(design);}
    public void synthesisChanged(){if(!readingSynthesis)synthesisDataPresent=true;markDirty();synthesisSync=true;}
    private int synthesisActivation;
    private boolean synthesisInput;
    public int synthesisActivation(){return synthesisActivation;}
    public boolean synthesisEnabled(){return synthesisInputsReady&&(synthesisActivation==0||synthesisActivation==1&&synthesisInput||synthesisActivation==2&&!synthesisInput);}
    public void configureSynthesisRedstone(int mode,com.vandorlabs.redstone.ChannelList channels){if(!authoritative()||!builder()||mode<0||mode>2||channels==null)return;synthesisActivationPresent=true;synthesisActivation=mode;setRedstoneChannels(channels);refreshInputs();publish();}
    public boolean synthesisModuleInteraction(net.minecraft.entity.player.EntityPlayer player,EnumHand hand){
        if(!builder()||!formed()||!authoritative()||!isUsableByPlayer(player)||!com.vandorlabs.items.ConfigurationAccess.canEditDialog(player,world,pos))return false;
        com.vandorlabs.machines.SynthesisPresentation.Spec presentation=com.vandorlabs.machines.SynthesisPresentation.forMachine(builderMachine());
        Vec3d local=new Vec3d(presentation.rackX+.5,presentation.rackY+.5,presentation.rackFront());
        MachineMountSurface mount=new MachineMountSurface(MachineMountSurface.rotate(local,facing(),core().width,core().depth).add(new Vec3d(pos.subtract(pivot()))),facing(),1,1);
        Vec3d start=player.getPositionEyes(1),delta=player.getLook(1).scale(player.getEntityAttribute(net.minecraft.entity.player.EntityPlayer.REACH_DISTANCE).getAttributeValue());double divisor=delta.dotProduct(mount.normal);if(divisor>=-1e-8)return false;double time=mount.center.subtract(start).dotProduct(mount.normal)/divisor;if(time<0||time>1)return false;Vec3d point=start.add(delta.scale(time)).subtract(mount.center);
        int bay=presentation.bayAt(local.x-point.dotProduct(mount.u),local.y+point.y);if(bay<0)return false;
        int slot=2+bay;net.minecraft.item.ItemStack held=player.getHeldItem(hand),installed=synthesisInventory().getStackInSlot(slot);
        if(installed.isEmpty()&&com.vandorlabs.rack.ModuleBatteryData.valid(held)){net.minecraft.item.ItemStack one=held.copy();one.setCount(1);synthesisInventory().setStackInSlot(slot,one);if(!player.capabilities.isCreativeMode)held.shrink(1);return true;}
        if(held.isEmpty()&&!installed.isEmpty()){player.setHeldItem(hand,synthesisInventory().extractItem(slot,1,false));return true;}return false;
    }

    public boolean completedCoreHit(net.minecraft.entity.player.EntityPlayer player){return !synthesisInventory().getStackInSlot(1).isEmpty()&&enclosureHit(player);}
    public boolean enclosureHit(net.minecraft.entity.player.EntityPlayer player){
        if(!builder())return false;
        com.vandorlabs.machines.BuilderOutputBounds box=com.vandorlabs.machines.BuilderOutputBounds.forMachine(builderMachine());
        Vec3d origin=new Vec3d(pos.subtract(pivot()));
        Vec3d a=MachineMountSurface.rotate(new Vec3d(box.minX,box.minY,box.minZ),facing(),core().width,core().depth).add(origin);
        Vec3d b=MachineMountSurface.rotate(new Vec3d(box.maxX,box.maxY,box.maxZ),facing(),core().width,core().depth).add(origin);
        AxisAlignedBB bounds=new AxisAlignedBB(Math.min(a.x,b.x),a.y,Math.min(a.z,b.z),Math.max(a.x,b.x),b.y,Math.max(a.z,b.z));
        Vec3d eye=player.getPositionEyes(1);double reach=player.getEntityAttribute(net.minecraft.entity.player.EntityPlayer.REACH_DISTANCE).getAttributeValue();RayTraceResult hit=bounds.calculateIntercept(eye,eye.add(player.getLook(1).scale(reach)));return hit!=null&&eye.squareDistanceTo(hit.hitVec)<=reach*reach;
    }
    public boolean placeDigitizeSample(net.minecraft.entity.player.EntityPlayer player,EnumHand hand){
        if(!digitizing()||processing()||!authoritative()||!isUsableByPlayer(player)||com.vandorlabs.gear.ArmTransactions.locked(world,pos)||!com.vandorlabs.items.ConfigurationAccess.canEditDialog(player,world,pos)||!enclosureHit(player)||!synthesisInventory().getStackInSlot(0).isEmpty())return false;
        net.minecraft.item.ItemStack held=player.getHeldItem(hand);if(!com.vandorlabs.machines.BlockRecipeData.eligible(held))return false;net.minecraft.item.ItemStack remainder=synthesisInventory().insertItem(0,held.copy(),false);int taken=held.getCount()-remainder.getCount();if(taken<=0)return false;if(!player.capabilities.isCreativeMode)held.shrink(taken);publish();return true;
    }
    public boolean takeCompletedCore(net.minecraft.entity.player.EntityPlayer player,EnumHand hand){
        if(!authoritative()||!isUsableByPlayer(player)||com.vandorlabs.gear.ArmTransactions.locked(world,pos)||!com.vandorlabs.items.ConfigurationAccess.canEditDialog(player,world,pos)||!completedCoreHit(player))return false;
        net.minecraft.item.ItemStack stack=synthesisInventory().extractItem(1,1,false);if(stack.isEmpty())return false;
        if(!player.inventory.addItemStackToInventory(stack))player.dropItem(stack,false);publish();return true;
    }
    public <T>T synthesisCapability(net.minecraftforge.common.capabilities.Capability<T> capability,BlockPos at,EnumFacing side){
        if(!formed()||!builder()||side==null||world.isRemote||com.vandorlabs.gear.ArmTransactions.locked(world,pos))return null;
        if(capability==net.minecraftforge.energy.CapabilityEnergy.ENERGY&&at.equals(synthesisPowerPort())&&(side==facing().getOpposite()||side==EnumFacing.DOWN))return net.minecraftforge.energy.CapabilityEnergy.ENERGY.cast(synthesisRuntime.energy);
        if(capability==net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY&&at.equals(synthesisItemPort())){
            if(side==facing().getOpposite())return net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(synthesisRuntime.port(true));
            if(side==facing().rotateYCCW()||side==EnumFacing.DOWN)return net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(synthesisRuntime.port(false));
        }return null;
    }
    @Override public boolean hasCapability(net.minecraftforge.common.capabilities.Capability<?> cap,EnumFacing side){return synthesisCapability(cap,pos,side)!=null||super.hasCapability(cap,side);}
    @Override public <T>T getCapability(net.minecraftforge.common.capabilities.Capability<T> cap,EnumFacing side){T result=synthesisCapability(cap,pos,side);return result!=null?result:super.getCapability(cap,side);}
    @Override public void invalidate(){com.vandorlabs.navigation.NavigationSources.remove(this);super.invalidate();}
    @Override public void onChunkUnload(){com.vandorlabs.navigation.NavigationSources.remove(this);super.onChunkUnload();}
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
    public static boolean validSurface(int choice){return choice==-2||choice!=com.vandorlabs.navigation.NavigationMap.SURFACE&&com.vandorlabs.navigation.NavigationScreenSettings.validTexture(choice);}
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
    @Override protected boolean inputPowered(boolean aggregate){if(builder()){synthesisInput=aggregate;return synthesisEnabled();}if(!isDrive())return aggregate;int next=drive.activation(screenSettings().redstone,Math.max(driveChannelLevel,drivePhysicalLevel)==0&&aggregate?15:Math.max(driveChannelLevel,drivePhysicalLevel));if(driveLevel!=next){driveLevel=next;publish();}return next>0;}
    @Override public void setChannelLevel(int level){int next=com.vandorlabs.drives.DriveSettings.clamp(level);boolean changed=driveChannelLevel!=next;driveChannelLevel=next;setChannelSignal(next>0);if(isDrive()&&changed){refresh();publish();}}
    @Override public void update(){if(world!=null&&!world.isRemote&&formed()&&builder()){if(!com.vandorlabs.gear.ArmTransactions.locked(world,pos))synthesisRuntime.tick();if(synthesisSync&&world.getTotalWorldTime()%5==0){synthesisSync=false;publish();}}if(world!=null&&world.isRemote&&isDrive()&&renderReady()&&screenOn()&&drive.plume(true,driveLevel))com.vandorlabs.drives.DriveParticles.emit(this);}
    private boolean changing;private IBlockState removedState;
    private int coreX,coreZ,observerWidth;private boolean observerInverted;private NBTTagCompound observerSnapshot;
    public com.vandorlabs.gear.GearTileEntity carriedArm;
    public boolean observer(){BlockConstructorCore b=core();return b!=null&&(b.design.equals("standalone_dish")||b.design.equals("standalone_antenna"));}
    public boolean inverted(){return observerInverted&&observer();}
    private java.util.UUID renderIdentity;
    public java.util.UUID renderEpoch;public BlockPos renderOwner;public int renderCell=-1;private boolean renderAppearance;
    public java.util.UUID renderIdentity(){if(renderIdentity==null)renderIdentity=java.util.UUID.randomUUID();return renderIdentity;}
    public void renderIdentity(java.util.UUID identity){renderIdentity=identity;}
    public boolean renderAppearanceReady(){return renderAppearance;}
    private void renderPacket(NBTTagCompound tag){
        synthesisConnections=tag.getInteger("SynthesisConnections");
        renderAppearance=tag.hasKey("CoreRenderReady",1)&&tag.getBoolean("CoreRenderReady");
        if(tag.hasUniqueId("CoreRenderIdentity"))renderIdentity=tag.getUniqueId("CoreRenderIdentity");renderEpoch=null;renderOwner=null;renderCell=-1;
        if(tag.hasUniqueId("CoreRenderEpoch")&&tag.hasKey("CoreRenderOwner",4)&&tag.hasKey("CoreRenderCell",3)){int cell=tag.getInteger("CoreRenderCell");if(cell>=0&&cell<com.vandorlabs.gear.ArmPayload.MAX_BLOCKS){renderEpoch=tag.getUniqueId("CoreRenderEpoch");renderOwner=BlockPos.fromLong(tag.getLong("CoreRenderOwner"));if(Math.abs((long)renderOwner.getX()-pos.getX())>128||Math.abs((long)renderOwner.getY()-pos.getY())>128||Math.abs((long)renderOwner.getZ()-pos.getZ())>128){renderOwner=null;renderEpoch=null;}else renderCell=cell;}}
        if(com.vandorlabs.VandorLabs.proxy!=null)com.vandorlabs.VandorLabs.proxy.coreAppearanceChanged(this);
    }
    private com.vandorlabs.machines.CoreLayout cachedLayout,cachedPivotLayout;private BlockConstructorCore cachedLayoutCore;private BlockPos cachedPivot,cachedOriginOffset;private boolean cachedPivotInverted;
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
    private boolean observerDataValid(){BlockConstructorCore b=core();if(observerSnapshot==null)return true;if(!observerSnapshot.hasKey(com.vandorlabs.navigation.ObserverScale.WIDTH_TAG,3)||b==null||!observer())return false;try{com.vandorlabs.navigation.ObserverScale.width(b.design,observerWidth);observerSnapshot=null;return true;}catch(IllegalArgumentException invalid){return false;}}
    public int structureWidth(){BlockConstructorCore b=core();return com.vandorlabs.navigation.ObserverScale.observer(b.design)?com.vandorlabs.navigation.ObserverScale.width(b.design,observerWidth):b.width;}
    public int structureHeight(){BlockConstructorCore b=core();return com.vandorlabs.navigation.ObserverScale.observer(b.design)?com.vandorlabs.navigation.ObserverScale.height(b.design,observerWidth):b.height;}
    public int structureDepth(){BlockConstructorCore b=core();return com.vandorlabs.navigation.ObserverScale.observer(b.design)?com.vandorlabs.navigation.ObserverScale.width(b.design,observerWidth):b.depth;}
    public double observerScale(){BlockConstructorCore b=core();return b!=null&&com.vandorlabs.navigation.ObserverScale.observer(b.design)?com.vandorlabs.navigation.ObserverScale.linearScale(b.design,observerWidth):1;}
    public boolean formed(){IBlockState state=coreState();return state!=null&&observerDataValid()&&state.getValue(BlockConstructorCore.FORMED);}
    public com.vandorlabs.machines.CoreLayout layout(){
        IBlockState state=coreState();if(state==null||!observerDataValid())return null;BlockConstructorCore core=(BlockConstructorCore)state.getBlock();int face=state.getValue(BlockVandorDirectional.FACING).getIndex();
        boolean observer=com.vandorlabs.navigation.ObserverScale.observer(core.design);int width=observer?com.vandorlabs.navigation.ObserverScale.width(core.design,observerWidth):core.width;
        if(cachedLayout==null||cachedLayoutCore!=core||cachedLayout.facing!=face||cachedLayout.cornerX!=coreX||cachedLayout.cornerZ!=coreZ||cachedLayout.width!=width){cachedLayout=new com.vandorlabs.machines.CoreLayout(width,observer?com.vandorlabs.navigation.ObserverScale.height(core.design,width):core.height,observer?width:core.depth,face,coreX,coreZ);cachedLayoutCore=core;}return cachedLayout;
    }
    public int activity(){if(activity>=0)return activity;BlockConstructorCore core=core();return core!=null&&core.specification().state_labels!=null?2:1;}
    public void cycleActivity(){activity=(activity()+1)%3;markDirty();world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),2);}
    public BlockPos pivot(){com.vandorlabs.machines.CoreLayout shape=layout();if(shape==null)return BlockPos.ORIGIN;boolean inverted=observerInverted&&com.vandorlabs.navigation.ObserverScale.observer(cachedLayoutCore.design);if(cachedPivotLayout!=shape||cachedPivotInverted!=inverted){cachedPivotLayout=shape;cachedPivotInverted=inverted;cachedPivot=new BlockPos(shape.pivotX(),inverted?shape.height-1:0,shape.pivotZ());cachedOriginOffset=BlockPos.ORIGIN.subtract(cachedPivot);}return cachedPivot;}
    public BlockPos renderOriginOffset(){return pivot()==BlockPos.ORIGIN?BlockPos.ORIGIN:cachedOriginOffset;}
    public BlockPos cell(int x,int y,int z){com.vandorlabs.machines.CoreLayout shape=layout();return shape==null?pos:pos.add(shape.x(x,z),inverted()?-y:y,shape.z(x,z));}
    public String assemblyProblem(){return assemblyProblem;}
    public boolean frame(int x,int y,int z){com.vandorlabs.machines.CoreLayout shape=layout();return shape!=null&&shape.frame(x,y,z);}
    public boolean assemble(){
        if(world==null || world.isRemote || changing || coreState()==null || formed()||!observerDataValid())return false;
        Block matter=com.vandorlabs.blocks.ModBlocks.PROGRAMMABLE_BLOCK;
        EnumFacing selected=null;int selectedX=0,selectedZ=0,selectedWidth=core().width,best=Integer.MAX_VALUE;boolean selectedInverted=false;
        EnumFacing preferred=facing();
        // Prefer the placed facing, but infer the footprint rather than requiring a hidden corner convention.
        for(int width=core().width;width<=(observer()?com.vandorlabs.navigation.ObserverScale.maximumWidth(core().design):core().width)&&selected==null;width++){
          int height=observer()?com.vandorlabs.navigation.ObserverScale.height(core().design,width):core().height,depth=observer()?width:core().depth;
          for(int orientation=0;orientation<(observer()?2:1)&&selected==null;orientation++)for(int turn=0;turn<4 && selected==null;turn++){
            EnumFacing direction=EnumFacing.getHorizontal((preferred.getHorizontalIndex()+turn)%4);
            for(int cornerX:new int[]{0,width-1})for(int cornerZ:new int[]{0,depth-1}){
                com.vandorlabs.machines.CoreLayout candidate=new com.vandorlabs.machines.CoreLayout(width,height,depth,direction.getIndex(),cornerX,cornerZ);
                // Reject absent corners before examining the entire volume of each possible size.
                boolean corners=true;if(observer())for(int cy:new int[]{0,height-1})for(int cx:new int[]{0,width-1})for(int cz:new int[]{0,depth-1}){
                    BlockPos at=pos.add(candidate.x(cx,cz),orientation==1?-cy:cy,candidate.z(cx,cz));
                    if(!at.equals(pos)&&(!world.isBlockLoaded(at,false)||world.getBlockState(at).getBlock()!=matter))corners=false;
                }
                if(!corners)continue;
                int errors=0;String first="";
                for(int y=0;y<height;y++)for(int x=0;x<width;x++)for(int z=0;z<depth;z++){
                    BlockPos p=pos.add(candidate.x(x,z),orientation==1?-y:y,candidate.z(x,z));if(p.equals(pos))continue;
                    boolean loaded=world.isBlockLoaded(p,false),frame=candidate.frame(x,y,z);
                    if(!loaded || (frame?world.getBlockState(p).getBlock()!=matter:!world.isAirBlock(p))){
                        if(errors++==0)first=(loaded?(frame?world.getBlockState(p).getBlock() instanceof BlockConstructorCore?"Use a Programmable Block in place of the extra Core":"Programmable Block required":"Interior must be air"):"Chunk not loaded")+" at "+p.getX()+", "+p.getY()+", "+p.getZ();
                    }
                }
                if(errors<best){best=errors;assemblyProblem=first+" ("+errors+" mismatched cells in closest frame).";}
                if(errors==0 && selected==null){selected=direction;selectedX=cornerX;selectedZ=cornerZ;selectedInverted=orientation==1;selectedWidth=width;}
            }
          }
        }
        if(best==Integer.MAX_VALUE)assemblyProblem="Complete a proportional Programmable Block frame: Survey Dish N×N×N (N=3–16), Sentinel Array N×N×2N (N=2–8).";
        if(selected==null)return false;
        changing=true;
        try{
            coreX=selectedX;coreZ=selectedZ;observerInverted=selectedInverted;observerWidth=observer()?selectedWidth:0;observerSnapshot=null;
            world.setBlockState(pos,world.getBlockState(pos).withProperty(BlockVandorDirectional.FACING,selected),2);
            Block part=Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:constructor_part"));
            for(int y=0;y<structureHeight();y++)for(int x=0;x<structureWidth();x++)for(int z=0;z<structureDepth();z++){
                BlockPos p=cell(x,y,z);if(p.equals(pos))continue;
                boolean frameCell=frame(x,y,z);
                EnumFacing facing=frameCell?world.getBlockState(p).getValue(com.vandorlabs.blocks.BlockProgrammableBlock.FACING):EnumFacing.NORTH;
                TileEntity original=frameCell?world.getTileEntity(p):null;
                NBTTagCompound configuration=original==null?null:original.writeToNBT(new NBTTagCompound());
                world.setBlockState(p,part.getDefaultState(),2);
                ((TileEntityConstructorPart)world.getTileEntity(p)).owner(pos,frameCell,facing,configuration);
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
            TokenSlotInteractions.drop(world,pos);if(builder())synthesisRuntime.drop();
            for(int y=0;y<structureHeight();y++)for(int x=0;x<structureWidth();x++)for(int z=0;z<structureDepth();z++){
                BlockPos p=cell(x,y,z);if(p.equals(pos) || !world.isBlockLoaded(p))continue;TileEntity part=world.getTileEntity(p);
                if(part instanceof TileEntityConstructorPart && ((TileEntityConstructorPart)part).owner().equals(pos))((TileEntityConstructorPart)part).restoreFrame(2);
            }
            if(removedState==null)world.setBlockState(pos,world.getBlockState(pos).withProperty(BlockConstructorCore.FORMED,false),3);markDirty();refresh();
        }finally{changing=false;}
    }
    @Override public boolean supportsSecurity(){return false;}
    private final java.util.Set<BlockPos> poweredInputs=new java.util.HashSet<>();
    @Override protected boolean physicalPower(){
        poweredInputs.clear();drivePhysicalInputs.clear();drivePhysicalLevel=0;if(!formed())return false;
        BlockConstructorCore core=core();
        for(int y=0;y<structureHeight();y++)for(int x=0;x<structureWidth();x++)for(int z=0;z<structureDepth();z++){
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
        physicalInput(!poweredInputs.isEmpty());if(builder())refreshSynthesisConnections();if(isDrive())refresh();
    }
    @Override public void refreshInputs(){super.refreshInputs();if(authoritative()&&builder()){synthesisInputsReady=true;refresh();}}
    @Override public void onLoad(){synthesisInputsReady=false;super.onLoad();DeferredTileLoad.schedule(this,()->{com.vandorlabs.navigation.NavigationSources.register(this);refreshInputs();if(com.vandorlabs.VandorLabs.proxy!=null)com.vandorlabs.VandorLabs.proxy.coreAppearanceChanged(this);});}
    public com.vandorlabs.blocks.MachineMountSurface mount(){
        if(!renderReady()||!core().specification().hasChip())return null;FrameMachineDesign spec=core().specification();
        Vec3d local=synthesis()?new Vec3d(com.vandorlabs.machines.SynthesisPresentation.CHIP_X,com.vandorlabs.machines.SynthesisPresentation.CHIP_Y,com.vandorlabs.machines.SynthesisPresentation.CHIP_Z):new Vec3d(spec.chip_anchor[0],spec.chip_anchor[1],spec.chip_anchor[2]);
        Vec3d center=MachineMountSurface.rotate(local,facing(),core().width,core().depth).add(new Vec3d(pos.subtract(pivot())));
        return new MachineMountSurface(center,facing(),TokenSlotGeometry.CHIP_SIZE,TokenSlotGeometry.CHIP_SIZE);
    }
    @Override public boolean isUsableByPlayer(net.minecraft.entity.player.EntityPlayer player){return formed()&&super.isUsableByPlayer(player);}
    @Override public AxisAlignedBB getRenderBoundingBox(){if(!formed())return new AxisAlignedBB(pos);BlockPos origin=pos.subtract(pivot());boolean turn=facing().getAxis()==EnumFacing.Axis.X;return new AxisAlignedBB(origin.getX()-.5,origin.getY()-.5,origin.getZ()-.5,origin.getX()+(turn?structureDepth():structureWidth())+.5,origin.getY()+structureHeight()+.5,origin.getZ()+(turn?structureWidth():structureDepth())+.5);}
    @Override public double getMaxRenderDistanceSquared(){return 65536;}
    @Override public boolean hasFastRenderer(){BlockConstructorCore core=core();return core==null||!core.specification().hasChip()&&surfaceCount()==0;}
    private void writeSynthesisData(NBTTagCompound tag){
        if(synthesisDataPresent)synthesisRuntime.write(tag);else tag.removeTag("Synthesis");
        if(synthesisSequencesPresent){
            NBTTagCompound sequences=new NBTTagCompound();
            for(int bay=0;bay<4;bay++)sequences.setLong("Bay"+bay,batterySequences[bay]);
            tag.setTag("SynthesisBatterySequences",sequences);
        }else tag.removeTag("SynthesisBatterySequences");
        if(synthesisActivationPresent)tag.setInteger("SynthesisActivation",synthesisActivation);
        else tag.removeTag("SynthesisActivation");
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){if(observerSnapshot!=null&&!observerDataValid())return observerSnapshot.copy();super.writeToNBT(tag);if(observerWidth!=0&&observerWidth!=(observer()?core().width:0))tag.setInteger(com.vandorlabs.navigation.ObserverScale.WIDTH_TAG,observerWidth);writeSynthesisData(tag);if(observerInverted)tag.setBoolean("ObserverInverted",true);tag.setInteger("ConstructorCoreX",coreX);tag.setInteger("ConstructorCoreZ",coreZ);tag.setInteger("MachineActivity",activity());tag.setIntArray("MachineSurfaceTextures",surfaceTextures);if(isDrive()||driveConfigPresent){driveSettings().write(tag);tag.setInteger("DriveLevel",driveLevel);}return tag;}
    @Override public void readFromNBT(NBTTagCompound tag){readingSynthesis=true;synthesisInputsReady=false;renderAppearance=false;super.readFromNBT(tag);synthesisDataPresent=tag.hasKey("Synthesis");synthesisSequencesPresent=tag.hasKey("SynthesisBatterySequences");synthesisActivationPresent=tag.hasKey("SynthesisActivation");synthesisRuntime.read(tag);java.util.Arrays.fill(batterySequences,0);NBTTagCompound sequences=tag.getCompoundTag("SynthesisBatterySequences");for(int bay=0;bay<4;bay++)batterySequences[bay]=Math.max(0,sequences.getLong("Bay"+bay));synthesisActivation=Math.max(0,Math.min(2,tag.getInteger("SynthesisActivation")));driveConfigPresent=tag.hasKey("DriveParticles",3);drive=com.vandorlabs.drives.DriveSettings.read(tag);driveLevel=com.vandorlabs.drives.DriveSettings.clamp(tag.hasKey("DriveLevel",3)?tag.getInteger("DriveLevel"):15);observerInverted=tag.getBoolean("ObserverInverted");observerWidth=tag.getInteger(com.vandorlabs.navigation.ObserverScale.WIDTH_TAG);observerSnapshot=tag.hasKey(com.vandorlabs.navigation.ObserverScale.WIDTH_TAG)?tag.copy():null;cachedLayout=null;coreX=Math.max(0,Math.min(15,tag.getInteger("ConstructorCoreX")));coreZ=Math.max(0,Math.min(15,tag.getInteger("ConstructorCoreZ")));surfaceTextures=tag.getIntArray("MachineSurfaceTextures").clone();if(surfaceTextures.length>3)surfaceTextures=new int[0];for(int i=0;i<surfaceTextures.length;i++)if(!validSurface(surfaceTextures[i]))surfaceTextures[i]=-2;activity=tag.hasKey("MachineActivity")?Math.max(0,Math.min(2,tag.getInteger("MachineActivity"))):-1;renderAppearance=true;readingSynthesis=false;}
    @Override public NBTTagCompound getUpdateTag(){NBTTagCompound tag=writeToNBT(new NBTTagCompound());if(!getStackInSlot(0).isEmpty())tag.setTag("InsertedToken",com.vandorlabs.navigation.NavigationChip.summary(getStackInSlot(0)).writeToNBT(new NBTTagCompound()));tag.setInteger("SynthesisConnections",synthesisConnections);tag.setBoolean("SynthesisWorking",working());tag.setBoolean("CoreRenderReady",renderAppearance);tag.setUniqueId("CoreRenderIdentity",renderIdentity());if(renderEpoch!=null){tag.setUniqueId("CoreRenderEpoch",renderEpoch);tag.setLong("CoreRenderOwner",renderOwner.toLong());tag.setInteger("CoreRenderCell",renderCell);}return tag;}
    @Override public void handleUpdateTag(NBTTagCompound tag){readFromNBT(tag);synthesisRuntime.visualWorking(tag.getBoolean("SynthesisWorking"));renderPacket(tag);}
    @Override public SPacketUpdateTileEntity getUpdatePacket(){return new SPacketUpdateTileEntity(pos,0,getUpdateTag());}
    @Override public void onDataPacket(NetworkManager manager,SPacketUpdateTileEntity packet){readFromNBT(packet.getNbtCompound());synthesisRuntime.visualWorking(packet.getNbtCompound().getBoolean("SynthesisWorking"));renderPacket(packet.getNbtCompound());}
}
