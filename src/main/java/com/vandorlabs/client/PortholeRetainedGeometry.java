package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;

/** The existing aperture emitters, with their model transforms baked once. */
final class PortholeRetainedGeometry {
    static final class Surfaces {
        final StaticSurfaceMesh housing,pane;
        final int rotation;
        final boolean single;
        Surfaces(StaticSurfaceMesh housing,StaticSurfaceMesh pane,int rotation,boolean single){
            this.housing=housing;this.pane=pane;this.rotation=rotation;this.single=single;
        }
    }
    static Surfaces capture(TileEntityAnimatedScreenSelector tile,IBlockState state){return capture(tile,state,true);}

    /** False reproduces the original two-sheet path for rendered equivalence checks. */
    static Surfaces capture(TileEntityAnimatedScreenSelector tile,IBlockState state,boolean optimized){
        BlockProgrammableWall block=(BlockProgrammableWall)state.getBlock();
        if(!block.isPortholeShape())throw new IllegalArgumentException("Not a porthole");
        BlockProgrammableWall.FlatCorner bar=PanelJoins.flatBar(state,tile.getWorld(),tile.getPos());
        boolean single=optimized&&PortholePane.retainedPalette(tile,state)!=null;
        if(bar!=null){
            FlatPanelBarRenderer.Material[] materials={FlatPanelBarRenderer.material(tile,state,optimized),
                    FlatPanelBarRenderer.barMaterial(tile,state,bar.frontArm,optimized)};
            StaticSurfaceMesh.Capture housing=StaticSurfaceMesh.independentCapture(),pane=StaticSurfaceMesh.independentCapture();
            for(int i=0;i<2;i++){
                FlatPanelBarRenderer.Material m=materials[i];int turns=i==0?0:3,half=i==0?0:5;
                append(housing,FlatPanelBarRenderer.part(m.opaque,turns,half,bar.frontArm,m.depth,m.left,m.width,m.cap==null?.02:0));
                append(pane,FlatPanelBarRenderer.part(m.transparent,turns,half,bar.frontArm,m.depth,m.left,m.width));
                if(i==0&&m.opaque!=null)append(housing,FlatPanelBarRenderer.stemCap(m,
                        bar.straightLeft?bar.frontArm:bar.frontArm+4,PanelJoins.flatNear(state,tile.getWorld(),tile.getPos()),bar.straightLeft));
            }
            return new Surfaces(housing.finish(),pane.finish(),rotation(PanelJoins.flatFacing(state,tile.getWorld(),tile.getPos())),single);
        }
        TextureAtlasSprite wall=TEAnimatedScreenSelector.wallSprite(tile);
        TextureAtlasSprite metal=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(tile.frameAppearance.interior(
                tile.getSideTexture()>=0?ScreenHousingTextures.texture(tile.getSideTexture()):"vandorlabs:blocks/programmable_glass/metal_side"));
        EnumFacing facing=state.getValue(BlockProgrammableWall.FACING);
        PortholeHex.Slice slice=TEAnimatedScreenSelector.solidDiagonalPortholeJunction(tile,state)?null:
                TEAnimatedScreenSelector.portholeGroup(tile,state).slice(tile.getPos(),facing.rotateY(),
                        block.isDiagonalShape()&&tile.isDiagonalHalfHeight()?facing.getOpposite():EnumFacing.UP);
        StaticSurfaceMesh housing=DiagonalWallMeshCache.get(tile,state,wall,metal,slice);
        if(tile.getMainTextureLayout()!=0)housing=GlassSurfaceMapping.map(housing,wall,GlassSurfaceGroups.mainLayout(tile),false,0,0,tile.getMainTextureLayout());
        StaticSurfaceMesh pane=slice==null?empty():DiagonalWallMeshCache.glass(tile,state,wall,metal,slice);
        if(pane==null)pane=empty();
        else pane=optimized?PortholePane.retained(tile,state,pane):PortholePane.mesh(tile,state,pane);
        BlockProgrammableWall.FlatCorner flat=block.flatCorner(state,tile.getWorld(),tile.getPos());
        double depth=PanelDepth.offset(state.getValue(BlockProgrammableWall.DEPTH));
        if(block instanceof BlockProgrammablePortholeBlock){
            housing=housing.portholeTransform(0,1,-24,4);pane=pane.portholeTransform(0,1,-24,4);
        }else if(flat!=null){
            double left=flat.left(),width=(flat.right()-flat.left())/16;
            housing=housing.portholeTransform(left,width,depth,1);pane=pane.portholeTransform(left,width,depth,1);
            StaticSurfaceMesh.Capture complete=StaticSurfaceMesh.independentCapture();append(complete,housing);
            double near=PanelDepth.start(state.getValue(BlockProgrammableWall.DEPTH));
            if(flat.frontArm!=null)TEAnimatedScreenSelector.drawWallBox(complete,wall,metal,false,flat.frontArm,0,0,flat.frontArm+4,16,near,false);
            if(flat.backArm!=null)TEAnimatedScreenSelector.drawWallBox(complete,wall,metal,false,flat.backArm,0,near+4,flat.backArm+4,16,16,false);
            housing=complete.finish();
        }else if(!block.isDiagonalShape()){
            housing=housing.portholeTransform(0,1,depth,1);pane=pane.portholeTransform(0,1,depth,1);
        }
        return new Surfaces(housing,pane,rotation(facing),single);
    }
    private static void append(StaticSurfaceMesh.Capture capture,StaticSurfaceMesh mesh){if(mesh!=null)mesh.draw(capture,0,0);}
    private static StaticSurfaceMesh empty(){return StaticSurfaceMesh.independentCapture().finish();}
    static int rotation(EnumFacing facing){return ((int)(180-facing.getHorizontalAngle())/90)&3;}
    private PortholeRetainedGeometry(){}
}
