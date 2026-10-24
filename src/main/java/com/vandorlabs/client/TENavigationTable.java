package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.navigation.*;
import com.vandorlabs.tiles.TileEntityNavigationTable;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Chunk-cached housing, one native reader and a continuous image across visible cells. */
public final class TENavigationTable extends TileEntitySpecialRenderer<TileEntityNavigationTable> {
    private static final java.util.Map<TileEntityNavigationTable,Surface> SURFACES=new java.util.WeakHashMap<>();
    private static final class Surface {
        final BlockPos origin;final int width,depth,mask;final EnumFacing facing,display;final NavigationLayout.DisplayPatch patch;
        Surface(TileEntityNavigationTable tile,EnumFacing facing,NavigationLayout.Kind kind){
            origin=tile.origin();width=tile.assemblyWidth();depth=tile.assemblyDepth();mask=tile.connections();this.facing=facing;display=tile.displayFacing();
            BlockPos at=tile.getPos();patch=NavigationLayout.displayPatch(kind,NavigationLayout.Facing.valueOf(facing.name()),new NavigationLayout.Cell(at.getX(),at.getY(),at.getZ()),mask,NavigationLayout.Facing.valueOf(display.name()),new NavigationLayout.Cell(origin.getX(),origin.getY(),origin.getZ()),width,depth);
        }
        boolean matches(TileEntityNavigationTable tile,EnumFacing facing){return this.facing==facing&&display==tile.displayFacing()&&mask==tile.connections()&&width==tile.assemblyWidth()&&depth==tile.assemblyDepth()&&origin.equals(tile.origin());}
    }
    @Override public void render(TileEntityNavigationTable tile,double x,double y,double z,float partial,int stage,float alpha){
        if(!(tile.getBlockType() instanceof BlockNavigationTable))return;
        TileEntityNavigationTable root=tile;
        if(root!=null&&root.screenOn()&&root.screenSettings().texture>=0&&tile.formed()){
            NavigationScreenSettings config=root.screenSettings();BlockNavigationTable block=(BlockNavigationTable)tile.getBlockType();
            EnumFacing facing=tile.getWorld().getBlockState(tile.getPos()).getValue(BlockVandorDirectional.FACING);
            Surface surface=SURFACES.get(tile);if(surface==null||!surface.matches(tile,facing)){surface=new Surface(tile,facing,block.kind);SURFACES.put(tile,surface);}
            NavigationLayout.DisplayPatch patch=surface.patch;
            GlStateManager.pushMatrix();try(TurntableRenderState saved=new TurntableRenderState()){
                GlStateManager.translate(x-tile.getPos().getX(),y-tile.getPos().getY(),z-tile.getPos().getZ());GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.color(1,1,1,1);
                int light=tile.getWorld().getCombinedLight(tile.getPos(),0);OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,light&65535,light>>>16);
                double[] uv=TEAnimatedScreenSelector.bindDisplay(root,config.texture,true,config.animated,config.speed);
                BufferBuilder buffer=Tessellator.getInstance().getBuffer();buffer.setTranslation(0,0,0);buffer.begin(7,BlockSurfaceFormat.get());
                for(int i=0;i<4;i++){NavigationLayout.Vec3 point=patch.points[i];WorldSurface.vertex(buffer,point.x,point.y,point.z,uv[2]+patch.u[i]*(uv[3]-uv[2]),uv[0]+patch.v[i]*(uv[1]-uv[0]),(float)patch.normal.x,(float)patch.normal.y,(float)patch.normal.z);}
                Tessellator.getInstance().draw();
            }finally{GlStateManager.popMatrix();}
        }
        MachineMountSurface mount=tile.mount();if(mount!=null)TETokenSlot.nativeMount(tile,mount,x-tile.getPos().getX(),y-tile.getPos().getY(),z-tile.getPos().getZ(),partial);
    }
}
