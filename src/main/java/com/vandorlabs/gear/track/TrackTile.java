package com.vandorlabs.gear.track;

import com.vandorlabs.gear.track.core.TrackTopology;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Cached topology/support appearance. Native chunk rendering never scans an assembly. */
public final class TrackTile extends com.vandorlabs.gear.GearTileEntity {
    private int savedStyle=-1;
    public TrackTopology.Style style(){return savedStyle>=0?TrackTopology.Style.values()[savedStyle]:getBlockType() instanceof TrackBlock?((TrackBlock)getBlockType()).style():TrackTopology.Style.COMPACT;}
    public void rememberStyle(){if(savedStyle<0)savedStyle=style().ordinal();}
    public void style(int value){if(value<0||value>=3)throw new IllegalArgumentException("Invalid track style");savedStyle=value;markDirty();if(world!=null){IBlockState state=world.getBlockState(pos);world.notifyBlockUpdate(pos,state,state,3);world.markBlockRangeForRenderUpdate(pos,pos);TrackWorld.requestAround(world,pos);}}
    private volatile TrackAppearance appearance = TrackAppearance.SINGLE;
    private BlockPos leader;
    private List<BlockPos> members = Collections.emptyList();
    private boolean complete;
    private boolean pending = true;
    private long rebuildAfter;
    private long nextCheck;
    private String error = "";

    public TrackAppearance appearance() { return appearance; }
    @Nullable public BlockPos leader() { return leader; }
    public List<BlockPos> trackMembers() { return members; }
    public boolean hasCompleteAssembly() { return complete; }
    public String lastError() { return error; }
    @Override public String error(){
        com.vandorlabs.gear.GearTileEntity controller=loadedController();
        if(controller!=null&&controller!=this){String motion=controller.error();if(!motion.isEmpty())return motion;}
        if(hasRetainedPayload()||motionBlocked()){String retained=super.error();if(!retained.isEmpty())return retained;}
        return error;
    }
    public String status(){
        if(loadedController()!=null)return "Mounted on an arm";
        if(appearance.sideMask!=0||appearance.mount==TrackAppearance.Mount.TOP||appearance.mount==TrackAppearance.Mount.TOP_HIGH)return "Mounted Track ready";
        return "Track ready";
    }

    @Override public void onLoad() { super.onLoad();pending=true;rebuildAfter=0; }

    public void requestRebuild() {
        pending = true;
        rebuildAfter = world == null ? 0 : world.getTotalWorldTime() + 1;
    }

    void defer() {
        pending = false;
        nextCheck = world.getTotalWorldTime() + 40;
    }

    @Override
    public void update() {
        super.update();
        if (world == null || world.isRemote) return;
        long now = world.getTotalWorldTime();
        if (pending && now >= rebuildAfter) {
            TrackWorld.rebuild(this);
        } else if (now >= nextCheck) {
            nextCheck = now + 40;
            // One complete-group leader retries every 2 seconds. A member also
            // detects a replaced/rotated leader that still has a tile but no
            // longer claims it (for example after an external metadata edit).
            TrackTile owner = leader == null ? null : TrackWorld.tile(world, leader);
            if (owner == null || pos.equals(leader) || !owner.hasCompleteAssembly()
                    || !owner.trackMembers().contains(pos)) TrackWorld.rebuild(this);
        }
    }

    void install(TrackAppearance next, BlockPos owner, List<BlockPos> owned, boolean valid, String error) {
        List<BlockPos> immutable = new ArrayList<BlockPos>();
        for (BlockPos member : owned) immutable.add(member.toImmutable());
        boolean changed = !appearance.equals(next) || leader == null || !leader.equals(owner)
                || !members.equals(immutable) || complete != valid || !this.error.equals(error);
        this.appearance = next;
        this.leader = owner.toImmutable();
        this.members = Collections.unmodifiableList(immutable);
        this.complete = valid;
        this.error = error;
        pending = false;
        nextCheck = world.getTotalWorldTime() + 40;
        if (changed) {
            markDirty();
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 2);
        }
    }

    @Override
    public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
        return oldState.getBlock() != newState.getBlock()
                || oldState.getValue(TrackBlock.FACING) != newState.getValue(TrackBlock.FACING);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);tag.setInteger("TrackStyle",style().ordinal());tag.setString("TrackError",error);
        tag.setTag("Appearance", appearance.write());
        if (leader != null) tag.setLong("TrackLeaderOffset", leader.subtract(pos).toLong());
        tag.setBoolean("Complete", complete);
        NBTTagList list = new NBTTagList();
        for (BlockPos member : members) {
            NBTTagCompound entry = new NBTTagCompound(); entry.setLong("Offset", member.subtract(pos).toLong()); list.appendTag(entry);
        }
        tag.setTag("TrackMembers", list);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);error=tag.getString("TrackError");if(error.length()>512)error=error.substring(0,512);savedStyle=tag.hasKey("TrackStyle",3)?Math.max(0,Math.min(2,tag.getInteger("TrackStyle"))):-1;
        appearance = tag.hasKey("Appearance", 10) ? TrackAppearance.read(tag.getCompoundTag("Appearance")) : TrackAppearance.SINGLE;
        leader = tag.hasKey("TrackLeaderOffset", 4) ? pos.add(BlockPos.fromLong(tag.getLong("TrackLeaderOffset"))) : null;
        complete = tag.getBoolean("Complete");
        List<BlockPos> restored = new ArrayList<BlockPos>();
        NBTTagList list = tag.getTagList("TrackMembers", 10);
        if (list.tagCount() <= TrackTopology.MAX_TRACKS) {
            for (int i = 0; i < list.tagCount(); i++) {
                BlockPos member = pos.add(BlockPos.fromLong(list.getCompoundTagAt(i).getLong("Offset")));
                if (member.getY() != pos.getY() || Math.abs((long) member.getX() - pos.getX()) >= TrackTopology.MAX_HORIZONTAL_SPAN
                        || Math.abs((long) member.getZ() - pos.getZ()) >= TrackTopology.MAX_HORIZONTAL_SPAN) {
                    restored.clear(); complete = false; break;
                }
                restored.add(member);
            }
        } else complete = false;
        if (leader != null && (leader.getY() != pos.getY()
                || Math.abs((long) leader.getX() - pos.getX()) >= TrackTopology.MAX_HORIZONTAL_SPAN
                || Math.abs((long) leader.getZ() - pos.getZ()) >= TrackTopology.MAX_HORIZONTAL_SPAN)) {
            leader = null; restored.clear(); complete = false;
        }
        members = Collections.unmodifiableList(restored);
        pending = true;
        rebuildAfter = 0;
        nextCheck = 0;
    }

    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public void handleUpdateTag(NBTTagCompound tag) { readFromNBT(tag); }
    @Override @Nullable public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        readFromNBT(packet.getNbtCompound());
        if (world != null) world.markBlockRangeForRenderUpdate(pos, pos);
    }
}
