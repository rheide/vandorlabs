package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.ForgeHooksClient;
import org.lwjgl.opengl.GL11;

/** Software-renderable contracts; a shader-pack hardware acceptance run is separate. */
final class ShaderMaterialRuntimeChecks {
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("Shader material: "+why);}
    static void run(Minecraft mc,File output)throws Exception{
        tiledHousing(mc,output);extendedHousing(mc);int cases=0;
        for(boolean diagonal:new boolean[]{false,true})for(int material:new int[]{-1,0})for(int shade=0;shade<(material<0?GlassTextures.IDS.length:1);shade++){
            BlockPos pos=BlockPos.ORIGIN;net.minecraft.block.Block block=diagonal?ModBlocks.PROGRAMMABLE_DIAGONAL_GLASS:net.minecraft.block.Block.getBlockFromName("vandorlabs:programmable_glass");
            net.minecraft.block.state.IBlockState state=block.getDefaultState();TileEntity source=block.createTileEntity(mc.world,state);
            if(diagonal){TileEntityAnimatedScreenSelector glass=(TileEntityAnimatedScreenSelector)source;glass.setGlassShade(shade);glass.setGlassMaterial(material);glass.setGlassAppearance(2,false);}
            else{TileEntityProgrammableGlass glass=(TileEntityProgrammableGlass)source;glass.setShade(shade);glass.setMaterial(material);glass.setAppearance(2,false);}
            VehicleWorld view=new VehicleWorld(mc.world,VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(pos,state,source.writeToNBT(new NBTTagCompound())))));
            TileEntity tile=view.getTileEntity(pos);require(tile.shouldRenderInPass(0),"Strong pane participates in opaque pass");
            BufferedImage red=capture(mc,tile,true),blue=capture(mc,tile,false);int covered=0;
            for(int x=24;x<40;x++)for(int y=24;y<40;y++){
                int a=red.getRGB(x,y)&0xffffff,b=blue.getRGB(x,y)&0xffffff;
                if(a==b)covered++;
            }
            require(covered>120,"Strong pane occludes background "+diagonal+" material="+material+" shade="+shade+" pixels="+covered);
            if(material<0&&shade==0){javax.imageio.ImageIO.write(red,"png",new File(output,"strong-glass-"+(diagonal?"diagonal":"flat")+".png"));}
            cases++;
        }
        System.out.println("[vandorlabs][reprolab] shader-materials-live PASS opaque-background-pixel-cases="+cases+" Clear/all native Strong shades/replacement materials flat and diagonal, opaque-pass depth ownership; shader-pack hardware acceptance separate");
    }
    private static BufferedImage capture(Minecraft mc,TileEntity tile,boolean red){
        Framebuffer frame=new Framebuffer(64,64,true);int oldPass=MinecraftForgeClient.getRenderPass();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-.7,.7,-.7,.7,-4,4);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();GlStateManager.loadIdentity();
        try{
            frame.bindFramebuffer(true);GlStateManager.clearColor(red?1:0,0,red?0:1,1);GlStateManager.depthMask(true);GlStateManager.clear(16640);GlStateManager.enableDepth();GlStateManager.disableFog();GlStateManager.disableLighting();mc.entityRenderer.disableLightmap();GlStateManager.color(1,1,1,1);
            for(int pass=0;pass<2;pass++)if(tile.shouldRenderInPass(pass)){
                ForgeHooksClient.setRenderPass(pass);
                if(tile instanceof TileEntityProgrammableGlass){TEProgrammableGlass renderer=new TEProgrammableGlass();renderer.setRendererDispatcher(net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher.instance);renderer.render((TileEntityProgrammableGlass)tile,-.5,-.5,-.5,0,-1,1);}
                else{TEAnimatedScreenSelector renderer=new TEAnimatedScreenSelector();renderer.setRendererDispatcher(net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher.instance);renderer.render((TileEntityAnimatedScreenSelector)tile,-.5,-.5,-.5,0,-1,1);}
            }
            return net.minecraft.util.ScreenShotHelper.createScreenshot(64,64,frame);
        }finally{ForgeHooksClient.setRenderPass(oldPass);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);GL11.glPopAttrib();frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);GlStateManager.depthMask(true);GlStateManager.color(1,1,1,1);}
    }
    private static int[] raw(BakedQuad quad)throws Exception{
        for(Field field:BakedQuad.class.getDeclaredFields())if(field.getType()==int[].class){field.setAccessible(true);int[] value=(int[])field.get(quad);if(value!=null&&value.length>=28)return value;}
        throw new IllegalStateException("Baked quad payload unavailable");
    }
    private static BakedQuad supplied(BakedQuad source,int[] data,VertexFormat format){
        return new BakedQuad(data,source.getTintIndex(),source.getFace(),source.getSprite(),source.shouldApplyDiffuseLighting(),format){@Override public int[] getVertexData(){return data;}};
    }
    private static void tiledHousing(Minecraft mc,File output)throws Exception{
        int cases=0;int texture=ScreenHousingTextures.screenIndex("corp/ashiro_logo");
        for(int family=0;family<3;family++)for(EnumFacing facing:EnumFacing.HORIZONTALS){
            net.minecraft.block.Block block=family==0?ModBlocks.PROGRAMMABLE_BLOCK:ModBlocks.PROGRAMMABLE_SLAB;
            net.minecraftforge.common.property.IExtendedBlockState state=(net.minecraftforge.common.property.IExtendedBlockState)block.getDefaultState().withProperty(BlockAnimatedScreenSelector.FACING,facing);
            if(family>0)state=(net.minecraftforge.common.property.IExtendedBlockState)state.withProperty(BlockProgrammableSlab.HALF,family==2?net.minecraft.block.BlockSlab.EnumBlockHalf.TOP:net.minecraft.block.BlockSlab.EnumBlockHalf.BOTTOM);
            state=state.withProperty(ProgrammableHousingState.FINISH,texture).withProperty(ProgrammableHousingState.TILE_SIDES,1).withProperty(ProgrammableHousingState.VISIBLE,63);
            VertexFormat live=VehicleVertexFormatRuntimeChecks.format(false);
            ProgrammableHousingModel model=new ProgrammableHousingModel(mc.getBlockRendererDispatcher().getModelForState(state),family>0,false,
                    name->mc.getTextureMapBlocks().getAtlasSprite(name),()->live);
            List<BakedQuad> plain=model.getQuads(state,null,0);require(plain.size()==6,"actual Tile builds six faces");
            BufferedImage normal=tilePixels(mc,plain,live);int foreground=0;
            for(int pixel:normal.getRGB(0,0,96,96,null,0,96))if(pixel!=normal.getRGB(0,0))foreground++;
            require(foreground>300,"actual Tile pixel fixture contains geometry");
            live.clear();for(net.minecraft.client.renderer.vertex.VertexFormatElement element:VehicleVertexFormatRuntimeChecks.format(true).getElements())live.addElement(element);
            List<BakedQuad> wide=model.getQuads(state,null,0);require(wide!=plain,"Tile cache invalidates when shader format changes");
            for(int q=0;q<6;q++){
                require(plain.get(q).getFormat().getNextOffset()==28,"old Tile format remains immutable");
                int[] a=raw(plain.get(q)),b=raw(wide.get(q));require(a.length==28&&b.length==56,"actual Tile allocates whole records");
                for(int v=0;v<4;v++){
                    for(int k=0;k<7;k++)require(a[v*7+k]==b[v*14+k],"actual Tile UV/position/color/lightmap parity");
                    int packed=b[v*14+wide.get(q).getFormat().getNormalOffset()/4];int nx=(byte)packed,ny=(byte)(packed>>>8),nz=(byte)(packed>>>16);
                    require(nx*nx+ny*ny+nz*nz==127*127,"actual Tile provides a cardinal shader normal");
                }
                cases++;
            }
            BufferedImage shader=tilePixels(mc,wide,live);
            require(Arrays.equals(normal.getRGB(0,0,96,96,null,0,96),shader.getRGB(0,0,96,96,null,0,96)),"actual Tile 28/56-byte pixel parity "+family+facing);
            if(family==0&&facing==EnumFacing.NORTH){javax.imageio.ImageIO.write(normal,"png",new File(output,"tile-housing-vanilla.png"));javax.imageio.ImageIO.write(shader,"png",new File(output,"tile-housing-extended.png"));}
            live.clear();for(net.minecraft.client.renderer.vertex.VertexFormatElement element:VehicleVertexFormatRuntimeChecks.format(false).getElements())live.addElement(element);
            List<BakedQuad> restored=model.getQuads(state,null,0);for(BakedQuad quad:restored)require(raw(quad).length==28,"Tile cache recovers when shaders turn off");
        }
        System.out.println("[vandorlabs][reprolab] tiled-housing-shader-format PASS faces="+cases+" actual Tile cubes/both slab halves four facings 28/56-byte pixel+UV parity explicit normals immutable cache formats and shader-toggle recovery; hardware shader-pack acceptance separate");
    }
    private static BufferedImage tilePixels(Minecraft mc,List<BakedQuad> quads,VertexFormat format)throws Exception{
        int words=0;for(BakedQuad quad:quads)words+=raw(quad).length;
        java.nio.ByteBuffer data=org.lwjgl.BufferUtils.createByteBuffer(words*4);java.nio.IntBuffer ints=data.asIntBuffer();for(BakedQuad quad:quads)ints.put(raw(quad));
        VertexBuffer buffer=new VertexBuffer(new VertexFormat(format));buffer.bufferData(data);Framebuffer frame=new Framebuffer(96,96,true);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-.9,.9,-.9,.9,-4,4);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try(TurntableRenderState saved=new TurntableRenderState()){
            frame.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.rotate(25,1,0,0);GlStateManager.rotate(-35,0,1,0);GlStateManager.translate(-.5,-.5,-.5);
            GlStateManager.clearColor(.12f,.16f,.2f,1);GlStateManager.depthMask(true);GlStateManager.clear(16640);GlStateManager.enableDepth();GlStateManager.disableFog();GlStateManager.disableLighting();GlStateManager.disableBlend();GlStateManager.disableCull();GlStateManager.enableTexture2D();GlStateManager.color(1,1,1,1);mc.entityRenderer.disableLightmap();
            mc.getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);
            BlockMeshVbo.draw(buffer,format,net.minecraft.util.BlockRenderLayer.SOLID);
            return net.minecraft.util.ScreenShotHelper.createScreenshot(96,96,frame);
        }finally{buffer.deleteGlBuffers();GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }
    private static void extendedHousing(Minecraft mc)throws Exception{
        ProgrammableHousingModel model=new ProgrammableHousingModel(mc.getBlockRendererDispatcher().getModelForState(ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState()),false);
        net.minecraftforge.common.property.IExtendedBlockState state=(net.minecraftforge.common.property.IExtendedBlockState)ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState();
        state=state.withProperty(ProgrammableHousingState.FINISH,0).withProperty(ProgrammableHousingState.TILE_SIDES,1).withProperty(ProgrammableHousingState.VISIBLE,63);
        TextureAtlasSprite margin=mc.getTextureMapBlocks().getAtlasSprite("minecraft:blocks/concrete_black");int cases=0;
        for(BakedQuad quad:model.getQuads(state,null,0)){
            int[] original=quad.getVertexData(),plain=new int[28],wide=new int[56];int sourceStride=original.length/4;
            for(int v=0;v<4;v++){
                System.arraycopy(original,v*sourceStride,plain,v*7,7);System.arraycopy(plain,v*7,wide,v*14,7);
                for(int extra=7;extra<14;extra++)wide[v*14+extra]=0x12340000+v*256+extra;
            }
            for(int mode:new int[]{0,2})for(int[] extent:new int[][]{{2,3,2},{3,2,3}}){
                HousingFaceLayouts.Plane plane=new HousingFaceLayouts.Plane(new int[]{0,0,0},extent);
                List<BakedQuad> expected=ProgrammableHousingModel.mapJoined(supplied(quad,plain,VehicleVertexFormatRuntimeChecks.format(false)),plane,mode,margin);
                List<BakedQuad> actual=ProgrammableHousingModel.mapJoined(supplied(quad,wide,VehicleVertexFormatRuntimeChecks.format(true)),plane,mode,margin);
                require(expected.size()==actual.size(),"extended UV clipping region count");
                for(int i=0;i<expected.size();i++){
                    int[] a=raw(expected.get(i)),b=raw(actual.get(i));require(a.length==28&&b.length==56,"record width retained");
                    for(int v=0;v<4;v++){
                        for(int p=0;p<7;p++)require(a[v*7+p]==b[v*14+p],"position/UV/lighting parity on extended records");
                        for(int p=7;p<14;p++)require(b[v*14+p]==wide[v*14+p],"extended shader component payload retained");
                    }
                }
                cases++;
            }
        }
        require(cases==24,"six real housing faces in extended format");
        System.out.println("[vandorlabs][reprolab] housing-shader-records PASS cases="+cases+" 28/56-byte real-face UV/geometry parity Stretch/Fit clipping and untouched extended payload");
    }
}
