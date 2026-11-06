package com.vandorlabs.neoforge.door;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
/** Inactive Large structural role adapter. Gameplay services are deliberately not registered here. */
public class NativeLargeDoorEntity extends NativeProgrammableDoorEntity {
    private boolean assigned;private int column,row;
    public NativeLargeDoorEntity(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state,true,false);}
    public final void assign(BlockPos anchor){column=Math.abs(worldPosition.getX()-anchor.getX())+Math.abs(worldPosition.getZ()-anchor.getZ());row=worldPosition.getY()-anchor.getY();assigned=true;setChanged();}
    public final boolean isAnchor(){return assigned&&column==0&&row==0;}
    public final int column(){return column;}public final int row(){return row;}
    public final BlockPos anchorPos(){return level==null||!(getBlockState().getBlock() instanceof NativeLargeDoorBlock)?worldPosition:worldPosition.relative(getBlockState().getValue(NativeLargeDoorBlock.FACING).getCounterClockWise(),-column).below(row);}
    private void writeRole(CompoundTag tag){tag.putBoolean("LargeDoorAssigned",assigned);tag.putInt("LargeDoorColumn",column);tag.putInt("LargeDoorRow",row);}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){assigned=tag.getBoolean("LargeDoorAssigned");column=tag.getInt("LargeDoorColumn");row=tag.getInt("LargeDoorRow");if(column<0||column>2||row<0||row>2)assigned=false;super.loadAdditional(tag,registries);}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag,registries);writeRole(tag);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){var tag=super.getUpdateTag(registries);writeRole(tag);return tag;}
    /** Root channel/gameplay integration is a separate activation prerequisite. */
    @Override public boolean isChannelMemberActive(){return false;}
}
