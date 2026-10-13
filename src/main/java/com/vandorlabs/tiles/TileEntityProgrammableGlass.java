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
    private int shade;
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
        panelFacing=facing.getHorizontalIndex();markDirty();
        if(world!=null) {
            world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),3);
            world.markBlockRangeForRenderUpdate(pos.add(-1,-1,-1),pos.add(1,1,1));
        }
    }
    private boolean legacySize;
    private boolean restored;
    public int getTint() { return tint; }
    public boolean isFramed() { return framed; }
    public void setAppearance(int tint, boolean framed) {
        if (!GlassTextures.validTint(tint) || this.tint==tint && this.framed==framed) return;
        this.tint=tint; this.framed=framed; markDirty();
        if(world!=null) {
            IBlockState state=world.getBlockState(pos);world.notifyBlockUpdate(pos,state,state,3);
            world.markBlockRangeForRenderUpdate(pos.add(-1,-1,-1),pos.add(1,1,1));
        }
    }
    public int getShade() { return shade; }
    public int getSize() { return size; }
    public boolean isJoin() { return join; }
    public void setJoin(boolean join) {
        if (this.join == join) return;
        this.join = join;
        markDirty();
        if (world != null) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
            world.markBlockRangeForRenderUpdate(pos.add(-1, -1, -1),
                    pos.add(1, 1, 1));
        }
    }
    public void setSize(int size) {
        if (size < 0 || size > 2) return;
        size=Math.min(size,1);
        if(size==this.size)return;
        this.size = size;
        markDirty();
        if (world != null) world.notifyBlockUpdate(pos, world.getBlockState(pos),
                world.getBlockState(pos), 3);
    }
    public void setShade(int shade) {
        if (!GlassTextures.valid(shade) || shade == this.shade) return;
        this.shade = shade;
        markDirty();
        if (world != null) world.notifyBlockUpdate(pos, world.getBlockState(pos),
                world.getBlockState(pos), 3);
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("GlassShade", shade);
        tag.setInteger("GlassTint", tint);
        tag.setBoolean("GlassFramed", framed);
        tag.setInteger("GlassSize", size);
        tag.setBoolean("GlassJoin", join);
        if(panelFacing>=0)tag.setInteger("GlassPanelFacing",panelFacing);
        return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        restored = true;
        tint=tag.hasKey("GlassTint",3)?GlassTextures.clampTint(tag.getInteger("GlassTint")):1;
        framed=!tag.hasKey("GlassFramed") || tag.getBoolean("GlassFramed");
        shade = GlassTextures.clamp(tag.getInteger("GlassShade"));
        legacySize = !tag.hasKey("GlassSize");
        size = legacySize ? 1 : Math.max(0, Math.min(1, tag.getInteger("GlassSize")));
        join = !tag.hasKey("GlassJoin") || tag.getBoolean("GlassJoin");
        panelFacing=tag.hasKey("GlassPanelFacing",3)?Math.floorMod(tag.getInteger("GlassPanelFacing"),4):-1;
    }
    @Override public void onLoad() {
        super.onLoad();
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
        handleUpdateTag(packet.getNbtCompound());
    }
    @Override public void handleUpdateTag(NBTTagCompound tag) {
        readFromNBT(tag);
        if (world != null) world.markBlockRangeForRenderUpdate(
                pos.add(-1, -1, -1), pos.add(1, 1, 1));
    }
    @Override public boolean shouldRefresh(World world, BlockPos pos,
            IBlockState oldState, IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }
    @Override public boolean shouldRenderInPass(int pass) {
        if(world!=null && com.vandorlabs.blocks.PanelJoins.flatBar(world.getBlockState(pos),world,pos)!=null)return pass==0 || pass==1; return pass == 1; }
}
