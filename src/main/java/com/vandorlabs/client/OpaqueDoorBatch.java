package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.render.DoorLeafTransform;
import com.vandorlabs.render.SpaceDoorControlPanel;
import com.vandorlabs.tiles.TileEntitySpaceDoor;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.ForgeModContainer;
import org.lwjgl.opengl.GL11;
import java.nio.ByteBuffer;
import java.util.*;

/** Immutable item geometry, transformed into Forge's shared world buffer without GL calls. */
public final class OpaqueDoorBatch {
    private static final Map<Integer,Mesh> PANELS=new HashMap<>();
    private OpaqueDoorBatch() { }
    private static RenderItem checkedRenderer;
    private static boolean compatibleRenderer;
    /** CodeChicken's wrapper delegates ordinary immutable JSON models to vanilla. */
    static boolean compatible(RenderItem renderer){
        if(renderer==checkedRenderer)return compatibleRenderer;
        checkedRenderer=renderer;compatibleRenderer=renderer.getClass()==RenderItem.class;
        if(renderer.getClass().getName().equals("codechicken.lib.render.item.CCRenderItem")){
            try{
                java.lang.reflect.Field parent=renderer.getClass().getDeclaredField("parent");parent.setAccessible(true);
                Object delegate=parent.get(renderer);compatibleRenderer=delegate!=null && delegate.getClass()==RenderItem.class;
            }catch(ReflectiveOperationException | SecurityException ignored){compatibleRenderer=false;}
        }
        return compatibleRenderer;
    }
    static void clear(){PANELS.clear();}

    public static boolean available(TileEntitySpaceDoor tile) {
        if (tile.getWorld()==null || !tile.getWorld().isRemote || tile.hasGlass()
                || tile.getFaceTexture()>=0 || net.minecraftforge.fml.client.FMLClientHandler.instance().hasOptifine()
                || !compatible(Minecraft.getMinecraft().getRenderItem())
                || !ForgeModContainer.allowEmissiveItems || DefaultVertexFormats.BLOCK.getNextOffset()!=28) return false;
        IBlockState state=tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof BlockConfigurableSpaceDoor)
                || tile instanceof com.vandorlabs.tiles.TileEntityLargeProgrammableDoor && !((com.vandorlabs.tiles.TileEntityLargeProgrammableDoor)tile).isAnchor()) return false;
        state=state.getBlock().getActualState(state,tile.getWorld(),tile.getPos());
        if(state.getValue(BlockVandorDoor.HALF)!=BlockDoor.EnumDoorHalf.LOWER)return false;
        boolean paired=state.getValue(BlockConnectingDetailedDoor.PAIRED);
        boolean right=state.getValue(BlockVandorDoor.HINGE)==BlockDoor.EnumHingePosition.LEFT;
        return mesh(tile,state,paired,right,0)!=null && mesh(tile,state,paired,right,1)!=null
                && (!(tile instanceof com.vandorlabs.tiles.TileEntityLargeProgrammableDoor) || mesh(tile,state,true,true,0)!=null && mesh(tile,state,true,true,1)!=null);
    }
    private static Mesh mesh(TileEntitySpaceDoor tile,IBlockState state,boolean paired,boolean right,int part){
        return DoorRenderModels.get(state.getBlock(),tile.metadata(paired,right,part)).batchMesh();
    }
    static Mesh prepare(DoorRenderModels.Entry entry) {
        net.minecraft.client.renderer.block.model.ItemTransformVec3f transform=entry.model.getItemCameraTransforms().getTransform(net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType.NONE);
        if(!transform.equals(net.minecraft.client.renderer.block.model.ItemTransformVec3f.DEFAULT) || !entry.model.getOverrides().getOverrides().isEmpty())return null;
        DoorQuadPlan plan=entry.plan(entry.model);
        if(plan==null || entry.stack.hasEffect() || plan.itemFormat!=DefaultVertexFormats.ITEM
                || DefaultVertexFormats.ITEM.getNextOffset()!=28) return null;
        // Diffuse/emissive transitions cannot be replayed as GL state changes in a shared batch.
        for(DoorQuadPlan.Segment segment:plan.segments)if(segment.shade)return null;
        BufferBuilder buffer=new BufferBuilder(2048);
        buffer.begin(GL11.GL_QUADS,DefaultVertexFormats.ITEM);
        for(DoorQuadPlan.Segment segment:plan.segments)
            Minecraft.getMinecraft().getRenderItem().renderQuads(buffer,segment.quads,-1,entry.stack);
        buffer.finishDrawing();
        return copy(buffer,28,1,false);
    }
    private static Mesh copy(BufferBuilder buffer,int stride,float units,boolean panel) {
        ByteBuffer bytes=buffer.getByteBuffer();int count=buffer.getVertexCount();int[] data=new int[count*7];
        for(int v=0;v<count;v++)for(int i=0;i<7;i++)data[v*7+i]=bytes.getInt(v*stride+i*4);
        if(units!=1)for(int v=0;v<count;v++)for(int i=0;i<3;i++)
            data[v*7+i]=Float.floatToRawIntBits(Float.intBitsToFloat(data[v*7+i])/units);
        buffer.reset();return new Mesh(data,panel);
    }
    private static Mesh panel(TileEntitySpaceDoor tile,SpaceDoorControlPanel.Side side) {
        int key=side.ordinal()*4+(tile.isSliding()?2:0)+(tile.getPlacementDepth()==2?1:0);
        Mesh mesh=PANELS.get(key);if(mesh!=null)return mesh;
        BufferBuilder buffer=new BufferBuilder(2048);buffer.begin(GL11.GL_QUADS,BlockSurfaceFormat.get());
        TESlidingDoor.panelGeometry(buffer,tile.isSliding(),tile.getPlacementDepth()==2,side);
        buffer.finishDrawing();mesh=copy(buffer,BlockSurfaceFormat.get().getNextOffset(),16,true);
        PANELS.put(key,mesh);return mesh;
    }
    static void draw(TileEntitySpaceDoor tile,IBlockState state,float progress,double x,double y,double z,BufferBuilder buffer) {
        if(tile instanceof com.vandorlabs.tiles.TileEntityLargeProgrammableDoor){
            for(int hand=0;hand<2;hand++)drawLeaf(tile,state.withProperty(BlockVandorDoor.HINGE,hand==0?BlockDoor.EnumHingePosition.RIGHT:BlockDoor.EnumHingePosition.LEFT),progress,x,y,z,buffer,1.5,hand*1.5);
        }else drawLeaf(tile,state,progress,x,y,z,buffer,1,0);
    }
    static void drawLeaf(TileEntitySpaceDoor tile,IBlockState state,float progress,double x,double y,double z,BufferBuilder buffer,double size,double leafOffset){
        boolean paired=state.getValue(BlockConnectingDetailedDoor.PAIRED);
        boolean right=state.getValue(BlockVandorDoor.HINGE)==BlockDoor.EnumHingePosition.LEFT;
        BlockDetailedDoor motion=tile.model(tile.isSliding()).getVisualModel(state);
        int light=tile.getWorld().getCombinedLight(tile.getPos(),0);
        EnumFacing facing=state.getValue(BlockVandorDoor.FACING);
        double shiftX=0,shiftY=0,pivotX=0,pivotZ=0,angle=0;
        if(tile.isSliding() && tile.getSlideDirection()!=0)shiftY=tile.verticalTravel()*progress*size;
        else {
            DoorLeafTransform pose=DoorLeafTransform.calculate(tile.isSliding(),motion.getSlide(right),
                    motion.getPivot(right),motion.getPivotZ(),motion.getAngle(right),progress);
            shiftX=pose.translateX*size;pivotX=pose.pivotX*size;pivotZ=pose.pivotZ;angle=pose.angleDegrees;
        }
        buffer.setTranslation(0,0,0);
        mesh(tile,state,paired,right,0).draw(buffer,facing,x,y,z,tile.positionOffset(),light,0,0,0,0,0,size,leafOffset);
        mesh(tile,state,paired,right,1).draw(buffer,facing,x,y,z,tile.positionOffset(),light,shiftX,shiftY,pivotX,pivotZ,angle,size,leafOffset);
        SpaceDoorControlPanel.Side side=BlockConfigurableSpaceDoor.panelSide(tile.getWorld(),tile.getPos(),state);
        if(side!=SpaceDoorControlPanel.Side.NONE)
            panel(tile,side).draw(buffer,facing,x,y,z,tile.positionOffset(),light,0,0,0,0,0,size,leafOffset);
    }
    static final class Mesh {
        private final int[] source;
        private final boolean panel;
        private final ThreadLocal<int[]> work;
        Mesh(int[] source,boolean panel){this.source=source;this.panel=panel;work=ThreadLocal.withInitial(()->source.clone());}
        void draw(BufferBuilder buffer,EnumFacing facing,double x,double y,double z,double depth,int light,
                double shiftX,double shiftY,double pivotX,double pivotZ,double angle,double size,double leafOffset) {
            int[] out=work.get();double radians=Math.toRadians(angle),cos=Math.cos(radians),sin=Math.sin(radians);
            // Keep the panel's existing lightmap convention; item leaves use constant block/sky coordinates.
            int packed=panel?(light>>>16)|(light<<16):light;
            for(int i=0;i<source.length;i+=7){
                double px=Float.intBitsToFloat(source[i]),py=Float.intBitsToFloat(source[i+1]),pz=Float.intBitsToFloat(source[i+2]);
                px*=size;py*=size;
                if(panel)py+=SpaceDoorControlPanel.verticalOffset(size);
                double dx=px-pivotX,dz=pz-pivotZ;
                px=cos*dx+sin*dz+pivotX+shiftX+leafOffset;pz=-sin*dx+cos*dz+pivotZ+depth;py+=shiftY;
                double worldX,worldZ;
                switch(facing){
                    case NORTH:worldX=1-px;worldZ=1-pz;break;
                    case EAST:worldX=pz;worldZ=1-px;break;
                    case WEST:worldX=1-pz;worldZ=px;break;
                    default:worldX=px;worldZ=pz;
                }
                out[i]=Float.floatToRawIntBits((float)(worldX+x));out[i+1]=Float.floatToRawIntBits((float)(py+y));
                out[i+2]=Float.floatToRawIntBits((float)(worldZ+z));out[i+6]=packed;
            }
            buffer.addVertexData(out);
        }
    }
}
