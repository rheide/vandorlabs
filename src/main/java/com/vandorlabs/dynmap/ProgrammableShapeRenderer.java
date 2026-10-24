package com.vandorlabs.dynmap;

import com.vandorlabs.blocks.ProgrammableStairGeometry;
import net.minecraft.block.*;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import org.dynmap.renderer.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Map geometry uses the same saved stair settings and block states as collision. */
public final class ProgrammableShapeRenderer extends HousingRenderer {
    private final Map<DynmapBlockState,IBlockState> states=new ConcurrentHashMap<>();
    @Override public String[] getTileEntityFieldsNeeded(){return fields("StairTread","StairRoll","DiagonalFullWidth","DiagonalHalfHeight","DiagonalFill");}
    @SuppressWarnings({"unchecked","rawtypes"})
    private static IBlockState state(DynmapBlockState source){
        Block block=Block.getBlockFromName(source.blockName);
        if(block==null)throw new IllegalStateException("Unknown mapped block "+source.blockName);
        IBlockState state=block.getDefaultState();
        for(IProperty property:state.getPropertyKeys())for(Object value:property.getAllowedValues())
            if(source.isStateMatch(property.getName(),property.getName((Comparable)value))){state=state.withProperty(property,(Comparable)value);break;}
        return state;
    }
    @Override public RenderPatch[] getRenderPatchList(MapDataContext context){
        IBlockState state=states.computeIfAbsent(context.getBlockType(),ProgrammableShapeRenderer::state);
        List<AxisAlignedBB> boxes;
        if(state.getBlock() instanceof BlockStairs){
            int tread=integer(context.getBlockTileEntityField("StairTread"),8);
            if(tread!=1&&tread!=2&&tread!=4&&tread!=8&&tread!=16)tread=8;
            int roll=Math.max(0,Math.min(2,integer(context.getBlockTileEntityField("StairRoll"),0)));
            boxes=ProgrammableStairGeometry.boxes(state,tread|(roll<<5));
        }else if(context.getBlockType().blockName.endsWith("programmable_wall")){
            int depth=state.getValue(com.vandorlabs.blocks.BlockProgrammableWall.DEPTH);
            double z=com.vandorlabs.blocks.PanelDepth.start(depth)/16D;
            boxes=Collections.singletonList(rotate(new AxisAlignedBB(0,0,z,1,1,z+.25),state.getValue(com.vandorlabs.blocks.BlockProgrammableWall.FACING)));
        }else if(context.getBlockType().blockName.endsWith("programmable_diagonal_wall")){
            return DiagonalWallRenderer.patches(context);
        }else boxes=Collections.singletonList(new AxisAlignedBB(0,0,0,1,1,1));
        int[] textures=faceTextures(context,state);
        List<RenderPatch> patches=new ArrayList<>();
        for(AxisAlignedBB box:boxes)addBox(context.getPatchFactory(),patches,box.minX,box.maxX,box.minY,box.maxY,box.minZ,box.maxZ,textures);
        return patches.toArray(new RenderPatch[0]);
    }
    static AxisAlignedBB rotate(AxisAlignedBB box,EnumFacing facing){
        switch(facing){
            case EAST:return new AxisAlignedBB(1-box.maxZ,box.minY,box.minX,1-box.minZ,box.maxY,box.maxX);
            case SOUTH:return new AxisAlignedBB(1-box.maxX,box.minY,1-box.maxZ,1-box.minX,box.maxY,1-box.minZ);
            case WEST:return new AxisAlignedBB(box.minZ,box.minY,1-box.maxX,box.maxZ,box.maxY,1-box.minX);
            default:return box;
        }
    }
    static int[] faceTextures(MapDataContext context,IBlockState state){
        EnumFacing front=EnumFacing.NORTH;
        for(IProperty<?> p:state.getPropertyKeys())if(p.getName().equals("facing"))front=(EnumFacing)state.getValue(p);
        if(state.getBlock() instanceof BlockStairs)front=front.getOpposite();
        EnumFacing[] order={EnumFacing.DOWN,EnumFacing.UP,EnumFacing.WEST,EnumFacing.EAST,EnumFacing.NORTH,EnumFacing.SOUTH};
        int[] textures=new int[6];
        int turns=front.getAxis()==EnumFacing.Axis.Y?0:(front.getHorizontalIndex()+2)&3;
        for(int i=0;i<6;i++){
            EnumFacing local=order[i];if(local.getAxis()!=EnumFacing.Axis.Y)for(int j=0;j<turns;j++)local=local.rotateYCCW();
            textures[i]=selected(context,local.getIndex(),state.getBlock() instanceof BlockStairs);
        }
        return textures;
    }
}
