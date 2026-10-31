package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.canopy.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.*;

/** Replacement artwork keeps its opacity through posing, surface clipping and tiled UVs. */
final class CanopyOpacityRuntimeChecks {
    static final String[] KINDS={"canopy","front_canopy_glass","large_front_canopy_glass"};
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("canopy opacity: "+message);}
    static void run(){
        int variants=0;
        for(Block raw:ModBlocks.BLOCKS)if(raw instanceof BlockCanopy){
            BlockCanopy block=(BlockCanopy)raw;
            for(int tint=0;tint<4;tint++)for(boolean tile:new boolean[]{false,true})for(double progress:new double[]{0,.5,1}){
                LargeCanopySettings settings=new LargeCanopySettings();settings.material=0;settings.tint=tint;settings.tileMaterial=tile;settings.openable=true;
                List<CanopyMesh.Face> source=block.opening()?OpeningCanopyGeometry.faces(1,1,false):LargeCanopySettings.supports(block.kind)?LargeCanopyGeometry.faces(block.width,settings,true):CanopyMesh.MODELS.get(block.kind.equals("regular_canopy_glass")?"regular_1x1_00":block.kind);
                List<CanopyMesh.Face> faces=CanopyFinishes.faces(source,0,tint,0,tile);
                faces=CanopyMaterialUv.drawable(CanopySurfaces.visible(CanopyPose.faces(faces,block.kind,block.width,settings,progress)));
                int panels=0;
                for(CanopyMesh.Face face:faces){boolean panel=face.baseMaterial.equals("panel");float expected=panel?GlassMaterials.opacity(0,tint):1;
                    require(CanopyFinishes.opacity(face,0,tint)==expected,"posed/tiled alpha "+block.kind);
                    require(CanopyFinishes.translucent(face,0,tint)==(panel && tint<GlassTextures.OPAQUE),"render bucket "+block.kind);
                    if(panel)panels++;
                }
                require(panels>0,"missing material panels "+block.kind);
                for(CanopyMesh.Face face:CanopyFinishes.faces(source,0,tint,-1))require(CanopyFinishes.opacity(face,-1,tint)==1,"native glass alpha changed");variants++;
            }
        }
        System.out.println("[vandorlabs][reprolab] canopy-material-opacity PASS variants="+variants+" all-canopies poses Fit/Tile frame-opaque native-glass-unchanged");
    }
    static void icons(Minecraft mc){
        int count=0;
        for(Block block:ModBlocks.BLOCKS)if(block instanceof BlockCanopy)for(int tint=0;tint<4;tint++)for(boolean tile:new boolean[]{false,true}){
            LargeCanopySettings settings=new LargeCanopySettings();settings.material=0;settings.tint=tint;settings.tileMaterial=tile;ItemStack stack=new ItemStack(block);NBTTagCompound tag=new NBTTagCompound();settings.write(tag);stack.setTagCompound(tag);
            IBakedModel model=mc.getRenderItem().getItemModelWithOverrides(stack,mc.world,mc.player);int panels=0;
            for(BakedQuad quad:model.getQuads(null,null,0)){
                boolean panel=quad.getSprite().getIconName().equals(ScreenHousingTextures.texture(0));int expected=panel?Math.round(GlassMaterials.opacity(0,tint)*255):255;int[] vertices=quad.getVertexData();int stride=vertices.length/4;
                for(int v=0;v<4;v++){
                    require((vertices[v*stride+3]>>>24)==expected,"configured icon alpha");
                    Vec3d p=ControlItemPadding.project(new Vec3d(Float.intBitsToFloat(vertices[v*stride]),Float.intBitsToFloat(vertices[v*stride+1]),Float.intBitsToFloat(vertices[v*stride+2])),model.getItemCameraTransforms().gui).addVector(model.getItemCameraTransforms().gui.translation.x,model.getItemCameraTransforms().gui.translation.y,0);
                    require(Math.abs(p.x)<.49 && Math.abs(p.y)<.49,"material icon padding");
                }
                if(panel)panels++;
            }
            require(panels>0,"configured icon lost replacement artwork");count++;
        }
        System.out.println("[vandorlabs][reprolab] canopy-opacity-icons PASS variants="+count+" alpha-and-padding");
    }
    static BlockPos position(int type,int tint){int width=type==0?1:type==1?3:5;return new BlockPos(60+tint*(width+3),20,40+type*14);}
    static void gallery(World world){
        for(int type=0;type<3;type++)for(int tint=0;tint<4;tint++){
            BlockCanopy block=(BlockCanopy)Block.getBlockFromName("vandorlabs:"+KINDS[type]);BlockPos pos=position(type,tint);world.setBlockState(pos,block.getDefaultState(),2);
            LargeCanopySettings settings=new LargeCanopySettings();settings.material=0;settings.tint=tint;settings.depth=16;
            ItemStack stack=new ItemStack(block);NBTTagCompound tag=new NBTTagCompound();settings.write(tag);stack.setTagCompound(tag);block.onBlockPlacedBy(world,pos,world.getBlockState(pos),null,stack);
            for(int x=0;x<block.width;x++)for(int y=0;y<block.height;y++)world.setBlockState(pos.add(x,y,4),net.minecraft.init.Blocks.GOLD_BLOCK.getDefaultState(),3);
        }
    }
    static void prepared(Minecraft mc,int type)throws Exception{
        java.lang.reflect.Field cacheField=TECanopy.class.getDeclaredField("CACHE");cacheField.setAccessible(true);Map<?,?> cache=(Map<?,?>)cacheField.get(null);
        for(int tint=0;tint<4;tint++){
            TileEntityCanopy tile=TileEntityCanopy.at(mc.world,position(type,tint));require(tile!=null,"gallery tile synchronized");Object prepared=cache.get(tile);require(prepared!=null,"gallery tile was not rendered");
            java.lang.reflect.Field opaque=prepared.getClass().getDeclaredField("opaque"),glass=prepared.getClass().getDeclaredField("glass");opaque.setAccessible(true);glass.setAccessible(true);
            require(!((List<?>)opaque.get(prepared)).isEmpty(),"frame missing from opaque pass");List<?> translucent=(List<?>)glass.get(prepared);
            require(tint==3?translucent.isEmpty():!translucent.isEmpty(),"material render pass");
            for(Object face:translucent){java.lang.reflect.Field alpha=face.getClass().getDeclaredField("opacity");alpha.setAccessible(true);require(alpha.getFloat(face)==GlassMaterials.opacity(0,tint),"prepared vertex opacity");}
        }
        System.out.println("[vandorlabs][reprolab] canopy-opacity-render PASS "+KINDS[type]+" opacity=35/65/85/100 frame-opaque");
    }
    private CanopyOpacityRuntimeChecks(){}
}
