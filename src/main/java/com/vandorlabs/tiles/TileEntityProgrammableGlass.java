package com.vandorlabs.tiles;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Glass tint is synchronized independently of the baked frame geometry. */
public final class TileEntityProgrammableGlass extends TileEntity {
    public final FrameAppearance frameAppearance=new FrameAppearance();
    public final GlassPlayback glassPlayback=new GlassPlayback(this);
    /** Client publication only; never serialized into block, item or vehicle data. */
    public volatile Object frameChunkMesh,frameChunkPublication;
    public volatile int frameChunkLight;

    /** World geometry follows loaded chunks and the view frustum, like Programmable Walls. */
    @Override public double getMaxRenderDistanceSquared(){return Double.MAX_VALUE;}
    @Override public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox(){
        net.minecraft.util.math.AxisAlignedBB bounds=super.getRenderBoundingBox();
        return fluids.any()?bounds.union(new net.minecraft.util.math.AxisAlignedBB(pos)):bounds;
    }
    private void frameChanged(){if(com.vandorlabs.VandorLabs.proxy!=null)com.vandorlabs.VandorLabs.proxy.glassFrameAppearanceChanged(this);}
    private int configurationDepth;
    private boolean configurationChanged,configurationRange,configurationLight;
    private boolean pendingLight,lightQueued;
    /** Update deduplication belongs to this installed tile, never to saved/copied data. */
    private boolean receivedSettings;
    private int frameLight(){return framed?frameAppearance.light():0;}
    private void loadedLightChanged(){
        pendingLight=true;
        queuePendingLight();
    }
    private void queuePendingLight(){
        if(world==null)return;
        if(world instanceof com.vandorlabs.vehicle.VehicleWorld){refreshPendingLight();return;}
        if(lightQueued)return;
        lightQueued=true;
        if(com.vandorlabs.VandorLabs.proxy!=null)com.vandorlabs.VandorLabs.proxy.glassLightRefreshNeeded(this);
        else DeferredTileLoad.schedule(this,this::refreshPendingLight);
    }
    /** Called after an installed packet, or by an identity-checked load callback. */
    public void refreshPendingLight(){lightQueued=false;if(pendingLight&&world!=null){pendingLight=false;world.checkLight(pos);}}
    @Override public void setWorld(World world){if(this.world!=world){lightQueued=false;receivedSettings=false;}super.setWorld(world);}
    @Override public void setPos(BlockPos pos){if(!this.pos.equals(pos)){lightQueued=false;receivedSettings=false;}super.setPos(pos);}
    private void changed(boolean range,boolean light){
        if(configurationDepth>0){configurationChanged=true;configurationRange|=range;configurationLight|=light;return;}
        markDirty();
        if(world!=null){
            IBlockState state=world.getBlockState(pos);world.notifyBlockUpdate(pos,state,state,3);
            if(range)world.markBlockRangeForRenderUpdate(pos.add(-1,-1,-1),pos.add(1,1,1));
            if(light){pendingLight=lightQueued=false;world.checkLight(pos);}
        }
    }
    void playbackChanged(){changed(false,false);}
    /** One authorized settings transaction; individual setters retain their public behavior. */
    public void configure(int size,int shade,int material,int layout,boolean join,int tint,boolean framed,int playbackMode,int playbackSpeed){
        if(size<0||size>2||!GlassTextures.valid(shade)||!GlassMaterials.valid(material)
                ||!com.vandorlabs.render.TextureLayoutMode.valid(layout)||!GlassTextures.validTint(tint)
                ||playbackMode>=0&&(!GlassPlayback.valid(playbackMode)||!GlassPlayback.valid(playbackSpeed)))
            throw new IllegalArgumentException("Invalid glass configuration");
        configurationDepth++;
        try{
            setSize(size);if(playbackMode>=0)glassPlayback.set(playbackMode,playbackSpeed);
            setShade(shade);setMaterial(material);setTextureLayout(layout);setJoin(join);setAppearance(tint,framed);
        }finally{
            if(--configurationDepth==0&&configurationChanged){
                boolean range=configurationRange,light=configurationLight;
                configurationChanged=configurationRange=configurationLight=false;changed(range,light);
            }
        }
    }
    @Override public void markDirty(){super.markDirty();GlassMaterials.changed();frameChanged();}
    @Override public void invalidate(){super.invalidate();lightQueued=false;receivedSettings=false;frameChunkMesh=null;frameChunkPublication=null;frameChanged();}
    @Override public void onChunkUnload(){super.onChunkUnload();lightQueued=false;receivedSettings=false;frameChunkMesh=null;frameChunkPublication=null;frameChanged();}
    public final com.vandorlabs.blocks.ContainedFluids fluids=new com.vandorlabs.blocks.ContainedFluids(this);
    private int shade;
    private int material=-1;
    private boolean stretch;
    private int textureLayout=-1;
    public int getTextureLayout(){return textureLayout<0?(stretch?0:1):textureLayout;}
    public void setTextureLayout(int mode){if(!com.vandorlabs.render.TextureLayoutMode.valid(mode))throw new IllegalArgumentException("Invalid glass texture layout");if(getTextureLayout()==mode)return;stretch=mode!=1;textureLayout=mode;changed(true,false);}
    public boolean isStretch(){return stretch;}
    public void setStretch(boolean value){if(stretch==value)return;textureLayout=-1;stretch=value;changed(true,false);}
    public int getMaterial(){return material;}
    public void setMaterial(int value){
        if(!GlassMaterials.valid(value) || material==value)return;
        material=value;changed(true,false);
    }
    private int tint = 1;
    private boolean framed = true;
    private int size = 1;
    private boolean join = true;
    private int panelFacing = -1;
    public net.minecraft.util.EnumFacing getPanelFacing(net.minecraft.util.EnumFacing fallback) {
        net.minecraft.util.EnumFacing facing=panelFacing<0?fallback:net.minecraft.util.EnumFacing.getHorizontal(panelFacing);
        return facing.getAxis()==fallback.getAxis()?facing:fallback;
    }
    public void setPanelFacing(net.minecraft.util.EnumFacing facing) {
        if(!facing.getAxis().isHorizontal() || panelFacing==facing.getHorizontalIndex())return;
        panelFacing=facing.getHorizontalIndex();changed(true,false);
    }
    private boolean legacySize;
    private boolean restored;
    public int getTint() { return tint; }
    public boolean isFramed() { return framed; }
    public void setAppearance(int tint, boolean framed) {
        if (!GlassTextures.validTint(tint) || this.tint==tint && this.framed==framed) return;
        int previousLight=frameLight();this.tint=tint;this.framed=framed;
        changed(true,previousLight!=frameLight());
    }
    public int getShade() { return shade; }
    public int getSize() { return size; }
    public boolean isJoin() { return join; }
    public void setJoin(boolean join) {
        if (this.join == join) return;
        this.join = join;
        changed(true,false);
    }
    public void setSize(int size) {
        if (size < 0 || size > 2) return;
        size=Math.min(size,1);
        if(size==this.size)return;
        this.size = size;
        changed(false,false);
    }
    public void setShade(int shade) {
        if (!GlassTextures.valid(shade) || shade == this.shade) return;
        this.shade = shade;
        changed(false,false);
    }
    public com.vandorlabs.persistence.ProgrammableGlassSettings settings(){
        return new com.vandorlabs.persistence.ProgrammableGlassSettings(shade,material,textureLayout,stretch,tint,framed,size,join,panelFacing,legacySize);
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);frameAppearance.write(tag);glassPlayback.write(tag);fluids.write(tag);
        settings().write(new com.vandorlabs.persistence.NbtPrimitiveData(tag));
        return tag;
    }
    /** Conservative visual dependencies, compared exactly rather than by a hash.
     * Material/layout/tint and playback also control connected artwork groups;
     * frame choices and shape/join/facing control frame and pane topology.
     */
    private int[] renderSettings() {
        return new int[]{shade,material,getTextureLayout(),stretch?1:0,tint,framed?1:0,
                size,join?1:0,panelFacing,legacySize?1:0,
                frameAppearance.front,frameAppearance.interior,frameAppearance.exterior,
                frameAppearance.artworkVersion,frameAppearance.tile?1:0,frameAppearance.displayFit?1:0,
                frameAppearance.split?1:0,frameAppearance.bare?1:0,frameAppearance.splitTop,frameAppearance.splitBottom,
                glassPlayback.mode(),glassPlayback.speed()};
    }
    private NBTTagCompound fluidSettings() {
        if(!fluids.any())return null;
        NBTTagCompound tag=new NBTTagCompound();fluids.write(tag);return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        receivedSettings=false;
        readSettings(tag,true,true);
    }
    private boolean readSettings(NBTTagCompound tag,boolean deferLight,boolean force) {
        int[] previous=force?null:renderSettings();
        NBTTagCompound previousFluids=force?null:fluidSettings();
        BlockPos previousPos=pos;
        int previousLight=frameLight();
        super.readFromNBT(tag);frameAppearance.read(tag);glassPlayback.read(tag);fluids.read(tag);
        restored = true;
        com.vandorlabs.persistence.ProgrammableGlassSettings saved=com.vandorlabs.persistence.ProgrammableGlassSettings.read(new com.vandorlabs.persistence.NbtPrimitiveData(tag),GlassMaterials::clamp);
        material=saved.material;stretch=saved.stretch;textureLayout=saved.layout;tint=saved.tint;framed=saved.framed;
        shade=saved.shade;legacySize=saved.legacySize;size=saved.size;join=saved.join;panelFacing=saved.panelFacing;
        boolean changed=force||!previousPos.equals(pos)||!java.util.Arrays.equals(previous,renderSettings())
                ||!java.util.Objects.equals(previousFluids,fluidSettings());
        // Generic NBT/copy/carried replacement reads are always forced. Only an
        // already-initialized packet may reuse this tile's unchanged topology.
        // Keep fluid-only notifications too: carried views do not forward the
        // render-range event issued independently by ContainedFluids.read.
        if(changed&&world!=null)frameChanged();
        if(previousLight!=frameLight()){if(deferLight)loadedLightChanged();else pendingLight=true;}
        return changed;
    }
    private boolean readUpdateSettings(NBTTagCompound tag,boolean deferLight) {
        boolean changed=readSettings(tag,deferLight,!receivedSettings);
        receivedSettings=true;
        return changed;
    }
    @Override public void onLoad() {
        super.onLoad();
        receivedSettings=false;
        frameChanged();
        // A prior settlement may have skipped this object before installation.
        // Re-arm on load without reading a world or a neighbouring tile.
        if(pendingLight){lightQueued=false;queuePendingLight();}
        if ((!restored || legacySize) && world != null) {
            size = Math.min(1,world.getBlockState(pos).getValue(
                    com.vandorlabs.blocks.BlockProgrammableGlass.SIZE));
            legacySize = false;
        }
    }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        boolean changed=readUpdateSettings(packet.getNbtCompound(),false);
        if(changed&&world!=null)world.markBlockRangeForRenderUpdate(pos.add(-1,-1,-1),pos.add(1,1,1));
        refreshPendingLight();
    }
    @Override public void handleUpdateTag(NBTTagCompound tag) {
        boolean changed=readUpdateSettings(tag,true);
        if (changed&&world != null) world.markBlockRangeForRenderUpdate(
                pos.add(-1, -1, -1), pos.add(1, 1, 1));
    }
    @Override public boolean shouldRefresh(World world, BlockPos pos,
            IBlockState oldState, IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }
    @Override public boolean shouldRenderInPass(int pass) {
        if(pass==0)return true;
        if(pass!=1)return false;
        if(fluids.any()||!GlassMaterials.opaque(material,tint))return true;
        return world!=null && com.vandorlabs.blocks.PanelJoins.flatBar(world.getBlockState(pos),world,pos)!=null;
    }
    /** An empty opaque pass still collects this pane for sorted translucent drawing. */
    @Override public boolean hasFastRenderer(){
        return com.vandorlabs.VandorLabs.proxy!=null
                &&com.vandorlabs.VandorLabs.proxy.glassCollectionOnly(this);
    }
}
