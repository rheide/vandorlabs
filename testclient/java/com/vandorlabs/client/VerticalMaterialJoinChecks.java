package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Three-row folds and continuations must have query-order-independent material coordinates. */
final class VerticalMaterialJoinChecks {
    static void run(){
        int cases=0;
        for(BlockProgrammableWall block:new BlockProgrammableWall[]{new BlockProgrammableDiagonalGlass(),new BlockProgrammableWall("vertical_porthole_check",BlockProgrammableWall.Shape.DIAGONAL_PORTHOLE)})
        for(int mode=0;mode<3;mode++)for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean inverted:new boolean[]{false,true})for(boolean fold:new boolean[]{false,true}){
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);
            List<BlockPos> cells=new ArrayList<>();
            for(int row=0;row<3;row++)for(int col=0;col<2;col++){
                BlockPos p=new BlockPos(16,80,16).offset(facing.rotateY(),col);
                if(mode==2){p=p.offset(facing.getOpposite(),row);if(!fold)p=p.up(inverted?-row/2:row/2);}
                else {p=p.up(row);if(!fold)p=p.offset(inverted?facing:facing.getOpposite(),mode==1?row:inverted?(row+1)/2:row/2);}
                boolean opposite=row%2==1 && (mode==2 || mode==0 && !fold);
                boolean reverse=row%2==1 && (mode==0 || mode==1 && fold || mode==2 && !fold);
                IBlockState state=block.getDefaultState().withProperty(BlockProgrammableWall.FACING,opposite?facing.getOpposite():facing)
                    .withProperty(BlockProgrammableWall.INVERTED,reverse?!inverted:inverted);
                world.setBlockState(p,state,2);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(p);
                tile.setDiagonalGeometry(mode,0);tile.setGlassMaterial(0);tile.setGlassLayout(true,true);tile.setJoinPortholes(true);cells.add(p);
            }
            Map<BlockPos,String> reference=null;
            for(BlockPos start:cells){
                GlassSurfaceGroups.invalidate(world);TEAnimatedScreenSelector.unloadGroups(world);
                GlassSurfaceGroups.layout(world.getTileEntity(start));
                if(block.isPortholeShape())TEAnimatedScreenSelector.portholeGroup((TileEntityAnimatedScreenSelector)world.getTileEntity(start),world.getBlockState(start));
                Map<BlockPos,String> actual=new HashMap<>();
                for(BlockPos p:cells){
                    GlassSurfaceGroups.Layout layout=GlassSurfaceGroups.layout(world.getTileEntity(p));
                    require(layout.columns==2 && layout.rows==3,"2x3 extent mode="+mode+" fold="+fold+" facing="+facing+" inverted="+inverted+" actual="+layout.key());
                    actual.put(p,layout.key());
                    if(block.isPortholeShape()){
                        TEAnimatedScreenSelector.PortholeGroup group=TEAnimatedScreenSelector.portholeGroup((TileEntityAnimatedScreenSelector)world.getTileEntity(p),world.getBlockState(p));
                        require(group.columns==2 && group.rows==3,"porthole extent");
                        actual.put(p,actual.get(p)+":"+group.right+":"+group.up+":"+group.minAxis+":"+group.minY);
                    }
                }
                if(reference==null)reference=actual;else require(reference.equals(actual),"query order changed material phase");
            }
            GlassSurfaceGroups.unload(world);cases++;
        }
        System.out.println("PASS: "+cases+" vertical material joins, 2x3 rows, all slopes/facings/inversions, folds and query orders");
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
