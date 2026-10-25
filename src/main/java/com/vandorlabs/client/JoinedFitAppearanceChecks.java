package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import java.awt.image.BufferedImage;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;

/** Vanilla pixels against the released pre-bc9165a4 centered-crop equation and literal face geometry. */
final class JoinedFitAppearanceChecks {
    static void run(Minecraft mc)throws Exception{
        int square=ScreenHousingTextures.screenIndex("corp/ashiro_logo"),wide=-1;
        for(int i=0;i<ScreenHousingTextures.IDS.length;i++){TextureAtlasSprite s=mc.getTextureMapBlocks().getAtlasSprite(ScreenHousingTextures.texture(i));if(s!=mc.getTextureMapBlocks().getMissingSprite()&&UnifiedTextureSprites.aspect(s)>1.4){wide=i;break;}}
        require(square>0&&wide>=0,"square and wide native artwork available");
        String[] panelIds={"ceiling_pale_grey_plain","ceiling_industrial_grey_plain","ceiling_dark_cyan_metallic_plain"};
        String[] panelSources={"ceiling/pale_grey/plain","ceiling/industrial_grey/plain","ceiling/dark_cyan_metallic/plain"};
        int[] textures={square,wide,0,0,0};for(int i=0;i<panelIds.length;i++){int selected=ScreenHousingTextures.screenIndex(panelSources[i]);require(selected>0&&panelIds[i].equals(ScreenHousingTextures.IDS[selected]),"actual Panels catalog selection exists: "+panelIds[i]);textures[i+2]=selected;}
        System.out.println("[vandorlabs][reprolab] joined-fit-vanilla selectedPanelIds="+Arrays.toString(panelIds)+" textureChoices="+Arrays.toString(textures));int cases=0;
        for(int texture:textures)for(int[] size:new int[][]{{1,1},{2,3},{3,2}}){
            int w=size[0],h=size[1];TextureAtlasSprite sprite=mc.getTextureMapBlocks().getAtlasSprite(ScreenHousingTextures.texture(texture));
            IBlockState state=ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState().withProperty(BlockAnimatedScreenSelector.FACING,EnumFacing.NORTH);
            List<VehicleStructure.Cell> cells=new ArrayList<>();
            for(int y=0;y<h;y++)for(int x=0;x<w;x++){BlockPos p=new BlockPos(x,y,0);TileEntityAnimatedScreenSelector tile=new TileEntityAnimatedScreenSelector();tile.setPos(p);tile.setHousingTexture(texture);tile.setMainTextureLayout(2);cells.add(new VehicleStructure.Cell(p,state,tile.writeToNBT(new NBTTagCompound())));}
            VehicleWorld view=new VehicleWorld(mc.world,VehicleStructure.display(cells));List<int[]> actual=new ArrayList<>();
            ProgrammableHousingModel model=new ProgrammableHousingModel(mc.getBlockRendererDispatcher().getModelForState(state),false,false,name->mc.getTextureMapBlocks().getAtlasSprite(name),()->new net.minecraft.client.renderer.vertex.VertexFormat(DefaultVertexFormats.BLOCK));
            for(VehicleStructure.Cell cell:cells)for(BakedQuad quad:model.getQuads(ModBlocks.PROGRAMMABLE_BLOCK.getExtendedState(state,view,cell.pos),null,0)){
                int[] data=quad.getVertexData().clone();int stride=data.length/4;require(stride==7,"vanilla BLOCK records");boolean north=true;for(int v=0;v<4;v++)north&=Math.abs(Float.intBitsToFloat(data[v*stride+2]))<1e-6;if(!north)continue;
                for(int v=0;v<4;v++){data[v*7]=Float.floatToRawIntBits(Float.intBitsToFloat(data[v*7])+cell.pos.getX());data[v*7+1]=Float.floatToRawIntBits(Float.intBitsToFloat(data[v*7+1])+cell.pos.getY());}actual.add(data);
            }
            BufferedImage rendered=pixels(mc,actual,w,h),legacy=pixels(mc,Collections.singletonList(legacyFace(sprite,w,h)),w,h);
            int different=0,covered=0;for(int y=2;y<126;y++)for(int x=2;x<126;x++){int a=rendered.getRGB(x,y)&0xffffff,b=legacy.getRGB(x,y)&0xffffff;if(a!=b)different++;if(b!=0x19334d)covered++;}
            require(covered>12000&&different<40,"vanilla Fit matches independent released crop: texture="+texture+" group="+w+"x"+h+" differingPixels="+different);
            // Deliberate former regression: concrete margins on the long dimension.
            if(w!=h&&texture==square){List<int[]> bars=new ArrayList<>();TextureAtlasSprite black=mc.getTextureMapBlocks().getAtlasSprite("minecraft:blocks/concrete_black");bars.add(legacyFace(black,w,h));int[] image=legacyFace(sprite,Math.min(w,h),Math.min(w,h));for(int v=0;v<4;v++){image[v*7]=Float.floatToRawIntBits(Float.intBitsToFloat(image[v*7])+(w-Math.min(w,h))/2F);image[v*7+1]=Float.floatToRawIntBits(Float.intBitsToFloat(image[v*7+1])+(h-Math.min(w,h))/2F);image[v*7+2]=Float.floatToRawIntBits(-.001F);}bars.add(image);BufferedImage negative=pixels(mc,bars,w,h);int rejected=0;for(int y=2;y<126;y++)for(int x=2;x<126;x++)if(negative.getRGB(x,y)!=legacy.getRGB(x,y))rejected++;require(rejected>1000,"independent pixel oracle rejects added black margins");}
            cases++;
        }
        System.out.println("[vandorlabs][reprolab] joined-fit-vanilla-appearance PASS cases="+cases+" square/wide and three actual Panels textures single/tall/wide groups independent released centered-crop pixels and black-margin negative controls");
    }
    // Frozen released NORTH face: U runs right-to-left, V top-to-bottom.
    // Unlike production, this creates one literal whole-panel quad; it never calls mapJoined or housing meshes.
    private static int[] legacyFace(TextureAtlasSprite sprite,int w,int h){
        int[] data=new int[28];double aspect=UnifiedTextureSprites.aspect(sprite),uFraction=Math.min(1,w/(double)h/aspect),vFraction=Math.min(1,aspect*h/(double)w);
        double[][] p={{w,h},{w,0},{0,0},{0,h}};for(int v=0;v<4;v++){int o=v*7;data[o]=Float.floatToRawIntBits((float)p[v][0]);data[o+1]=Float.floatToRawIntBits((float)p[v][1]);data[o+3]=-1;data[o+4]=Float.floatToRawIntBits(sprite.getInterpolatedU((w==1&&h==1&&Math.abs(aspect-1)>.005?(1-p[v][0])/Math.max(1,aspect):.5+(1-p[v][0]/w-.5)*uFraction)*16));data[o+5]=Float.floatToRawIntBits(sprite.getInterpolatedV((w==1&&h==1&&Math.abs(aspect-1)>.005?(1-p[v][1])*Math.min(1,aspect):.5+(1-p[v][1]/h-.5)*vFraction)*16));}return data;
    }
    private static BufferedImage pixels(Minecraft mc,List<int[]> quads,int w,int h){
        Framebuffer frame=new Framebuffer(128,128,true);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(0,w,0,h,-2,2);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try(TurntableRenderState saved=new TurntableRenderState()){
            frame.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.clearColor(.1F,.2F,.3F,1);GlStateManager.depthMask(true);GlStateManager.clear(16640);GlStateManager.disableLighting();GlStateManager.disableFog();GlStateManager.disableBlend();GlStateManager.disableCull();GlStateManager.enableTexture2D();GlStateManager.enableDepth();GlStateManager.color(1,1,1,1);mc.entityRenderer.disableLightmap();mc.getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);
            BufferBuilder buffer=Tessellator.getInstance().getBuffer();buffer.begin(GL11.GL_QUADS,DefaultVertexFormats.BLOCK);for(int[] data:quads)buffer.addVertexData(data);Tessellator.getInstance().draw();return net.minecraft.util.ScreenShotHelper.createScreenshot(128,128,frame);
        }finally{GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("Joined Fit appearance: "+message);}
    private JoinedFitAppearanceChecks(){}
}
