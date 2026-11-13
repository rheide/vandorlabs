package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockProgrammableGlass;
import com.vandorlabs.blocks.PanelJoins;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import com.vandorlabs.vehicle.VehicleStructure;
import com.vandorlabs.vehicle.VehicleWorld;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;

/** A sorter result reused only during its current flush, never across frames. */
final class GlassPanePreparation {
    private TileEntityProgrammableGlass owner;
    private World world;
    private IBlockState state;
    private long mutation,origin;
    private int x,y,z,tileRevision;
    private boolean joined;
    private EnumFacing facing,canonical;
    private VehicleStructure structure;

    void capture(TileEntityProgrammableGlass tile,IBlockState state,long before) {
        if(before!=GlassSurfaceGroups.mutation()){clear();return;}
        owner=tile;world=tile.getWorld();this.state=state;mutation=before;
        x=tile.getPos().getX();y=tile.getPos().getY();z=tile.getPos().getZ();
        joined=tile.isJoin();canonical=PanelJoins.facing(state);facing=tile.getPanelFacing(canonical);
        if(world instanceof VehicleWorld){
            VehicleWorld view=(VehicleWorld)world;
            structure=view.structure;tileRevision=view.tileRevision;origin=view.origin.toLong();
        }else structure=null;
    }
    private IBlockState current(TileEntityProgrammableGlass tile) {
        if(state==null||owner!=tile||world!=tile.getWorld()||mutation!=GlassSurfaceGroups.mutation()
                ||tile.isInvalid()||x!=tile.getPos().getX()||y!=tile.getPos().getY()||z!=tile.getPos().getZ()
                ||joined!=tile.isJoin()||facing!=tile.getPanelFacing(canonical))return null;
        if(world instanceof VehicleWorld){
            VehicleWorld view=(VehicleWorld)world;
            if(structure!=view.structure||tileRevision!=view.tileRevision||origin!=view.origin.toLong())return null;
        }
        return state;
    }
    static IBlockState flatState(TileEntityProgrammableGlass tile,GlassPanePreparation prepared) {
        // A published anchor represents a whole planar group, not one native
        // block face. Older per-pane batches must never draw it a second time.
        if(GlassPaneChunks.owns(tile))return null;
        IBlockState state=prepared==null?null:prepared.current(tile);
        if(state!=null)return state;
        state=tile.getWorld().getBlockState(tile.getPos());
        return state.getBlock() instanceof BlockProgrammableGlass&&GlassSurfaceGroups.flatCorner(tile,state)==null?state:null;
    }
    void clear(){owner=null;world=null;state=null;facing=canonical=null;structure=null;}
}
