package com.vandorlabs.client;

import java.util.*;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.client.MinecraftForgeClient;

/** Collect visible glazing in pass zero; draw its translucent parts back-to-front in pass one. */
final class GlazingRenderOrder {
    private static final int LIMIT=16384;
    private static World world;private static boolean flushing,flushed;
    private static final Map<TileEntity,Draw> visible=new IdentityHashMap<>();
    private static final class Draw {
        TileEntity tile;double x,y,z;float partial,alpha;int stage;double depth;
        void set(TileEntity tile,double x,double y,double z,float partial,int stage,float alpha){this.tile=tile;this.x=x;this.y=y;this.z=z;this.partial=partial;this.stage=stage;this.alpha=alpha;}
    }
    private static final List<Draw> pool=new ArrayList<>(),ordered=new ArrayList<>();
    private static final java.nio.FloatBuffer matrix=org.lwjgl.BufferUtils.createFloatBuffer(16);
    private static void clear(){for(Draw draw:ordered)draw.tile=null;ordered.clear();visible.clear();}
    static void begin(World view){end();world=view;}
    static void end(){clear();world=null;flushing=false;flushed=false;}
    static boolean handle(TileEntity tile,double x,double y,double z,float partial,int stage,float alpha){
        if(flushing||world==null||tile.getWorld()!=world||stage>=0||VehicleRenderPass.shadow())return false;
        int pass=MinecraftForgeClient.getRenderPass();
        if(pass==0){
            // A second eye/render starts a fresh collection within the same render tick.
            if(flushed){clear();flushed=false;}
            if(!visible.containsKey(tile)&&visible.size()<LIMIT&&tile.shouldRenderInPass(1)){
                int index=ordered.size();if(index==pool.size())pool.add(new Draw());Draw draw=pool.get(index);draw.set(tile,x,y,z,partial,stage,alpha);ordered.add(draw);visible.put(tile,draw);
            }
            return false;
        }
        if(pass!=1)return false;
        if(!visible.containsKey(tile))return false; // No matching opaque pass: preserve ordinary direct rendering.
        if(flushed)return true;
        TileEntityRendererDispatcher dispatcher=TileEntityRendererDispatcher.instance;
        matrix.clear();org.lwjgl.opengl.GL11.glGetFloat(org.lwjgl.opengl.GL11.GL_MODELVIEW_MATRIX,matrix);
        for(Draw draw:ordered)draw.depth=depth(draw.tile,dispatcher.entityX,dispatcher.entityY,dispatcher.entityZ,matrix);
        ordered.sort((a,b)->{int distance=Double.compare(a.depth,b.depth);return distance!=0?distance:a.tile.getPos().compareTo(b.tile.getPos());});
        flushing=true;
        try(TurntableRenderState saved=new TurntableRenderState()){
            for(Draw draw:ordered)if(world.getTileEntity(draw.tile.getPos())==draw.tile&&draw.tile.shouldRenderInPass(1))
                dispatcher.render(draw.tile,draw.x,draw.y,draw.z,draw.partial,draw.stage,draw.alpha);
        }finally{flushing=false;flushed=true;}
        return true;
    }
    private static double depth(TileEntity tile,double cx,double cy,double cz,java.nio.FloatBuffer matrix){
        net.minecraft.block.state.IBlockState state=tile.getWorld().getBlockState(tile.getPos());
        int axis=-1;double plane=0,offset=0;
        if(state.getBlock() instanceof com.vandorlabs.blocks.BlockProgrammableGlass&&com.vandorlabs.blocks.PanelJoins.flatCorner(state,tile.getWorld(),tile.getPos())==null){
            boolean rotated=state.getValue(com.vandorlabs.blocks.BlockGlassWall.ROTATED);axis=rotated?0:2;offset=com.vandorlabs.blocks.PanelDepth.offset(state.getValue(com.vandorlabs.blocks.BlockGlassWall.DEPTH))/16D;
            plane=rotated?tile.getPos().getX()+.5-offset:tile.getPos().getZ()+.5+offset;
        }else if(state.getBlock() instanceof com.vandorlabs.blocks.BlockProgrammableWall){
            com.vandorlabs.blocks.BlockProgrammableWall wall=(com.vandorlabs.blocks.BlockProgrammableWall)state.getBlock();
            if(wall.isPortholeShape()&&!wall.isDiagonalShape()&&com.vandorlabs.blocks.PanelJoins.flatCorner(state,tile.getWorld(),tile.getPos())==null){
                net.minecraft.util.EnumFacing facing=state.getValue(com.vandorlabs.blocks.BlockProgrammableWall.FACING);axis=facing.getAxis()==net.minecraft.util.EnumFacing.Axis.X?0:2;
                offset=com.vandorlabs.blocks.PanelDepth.offset(state.getValue(com.vandorlabs.blocks.BlockProgrammableWall.DEPTH))/16D;
                plane=(axis==0?tile.getPos().getX():tile.getPos().getZ())+.5-(axis==0?facing.getFrontOffsetX():facing.getFrontOffsetZ())*offset;
            }
        }
        // Sort joined parallel planes as a whole, rather than interleaving their tiles at oblique angles.
        if(axis>=0){double normal=matrix.get(axis==0?2:10);if(Math.abs(normal)>1e-6)return (plane-(axis==0?cx:cz))/normal;}
        return (tile.getPos().getX()+.5-cx)*matrix.get(2)+(tile.getPos().getY()+.5-cy)*matrix.get(6)+(tile.getPos().getZ()+.5-cz)*matrix.get(10);
    }
    private GlazingRenderOrder(){}
}
