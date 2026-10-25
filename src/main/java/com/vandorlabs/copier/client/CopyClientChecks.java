package com.vandorlabs.copier.client;

import com.vandorlabs.blocks.BlockVandorDirectional;
import com.vandorlabs.client.TurntableRenderState;
import com.vandorlabs.copier.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ScreenShotHelper;
import org.lwjgl.opengl.GL11;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

/** Actual atlas/model/FBO checks, separate from the copier's transactional server fixtures. */
public final class CopyClientChecks {
    private static final EnumFacing[] SIDES={null,EnumFacing.UP,EnumFacing.DOWN,EnumFacing.NORTH,EnumFacing.SOUTH,EnumFacing.EAST,EnumFacing.WEST};
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Copy client: "+message);}
    public static void run(Minecraft mc,File output)throws Exception{
        Block block=Block.getBlockFromName("vandorlabs:copy_controller");require(block instanceof CopyControllerBlock,"registered controller");BufferedImage sheet=new BufferedImage(512,512,BufferedImage.TYPE_INT_ARGB);Set<Integer> pictures=new HashSet<>();int index=0;
        for(EnumFacing facing:EnumFacing.HORIZONTALS){
            IBlockState state=block.getDefaultState().withProperty(BlockVandorDirectional.FACING,facing);IBakedModel model=mc.getBlockRendererDispatcher().getModelForState(state);int faces=0;Set<String> textures=new HashSet<>();
            for(EnumFacing side:SIDES)for(BakedQuad quad:model.getQuads(state,side,0)){
                faces++;String texture=quad.getSprite().getIconName();textures.add(texture);String name=quad.getFace()==EnumFacing.UP?"top":quad.getFace()==EnumFacing.DOWN?"bottom":quad.getFace()==facing?"front":quad.getFace()==facing.getOpposite()?"back":quad.getFace()==facing.rotateYCCW()?"right":"left";
                require(texture.equals("vandorlabs:blocks/copy_controller/"+name),"authored face/facing "+facing+" "+quad.getFace()+" got "+texture);require(quad.getSprite()!=mc.getTextureMapBlocks().getMissingSprite(),"resolved native face "+texture);require(quad.getSprite().getIconWidth()==64&&quad.getSprite().getIconHeight()==64,"native 64px artwork "+texture);
                int[] data=quad.getVertexData();for(int v=0;v<4;v++)for(int axis=0;axis<3;axis++){float coordinate=Float.intBitsToFloat(data[v*data.length/4+axis]);require(coordinate>=-1e-6&&coordinate<=1+1e-6,"native unit cube geometry");}
            }
            require(faces==10&&textures.size()==6,"six distinct authored faces for "+facing);BufferedImage image=block(mc,state,model);int ink=0,hash=1;for(int y=0;y<256;y++)for(int x=0;x<256;x++){int rgb=image.getRGB(x,y);if((rgb&0xffffff)!=0x203040)ink++;hash=31*hash+rgb;sheet.setRGB((index%2)*256+x,(index/2)*256+y,rgb);}require(ink>5000,"native rendered casing visible "+facing);pictures.add(hash);index++;
            direction(mc,block,state,model,facing);
        }
        require(pictures.size()>=3,"facing rotation changes actual rendered authored faces");javax.imageio.ImageIO.write(sheet,"png",new File(output,"shot_copy_controller_facings.png"));
        inventory(mc,output,new ItemStack(block));dialog(mc,output);
        System.out.println("[vandorlabs][reprolab] copy-client PASS four facings six native64 sprites actual FBO casing padded inventory compact320/460 GUI trigger previous/next keyboard Copy validation material-error visibility/clear");
    }
    private static net.minecraft.util.math.Vec3d rotate(net.minecraft.util.math.Vec3d p,EnumFacing facing){double x=p.x,z=p.z;int turns=facing==EnumFacing.EAST?1:facing==EnumFacing.SOUTH?2:facing==EnumFacing.WEST?3:0;for(int i=0;i<turns;i++){double old=x;x=1-z;z=old;}return new net.minecraft.util.math.Vec3d(x,p.y,z);}
    private static net.minecraft.util.math.Vec3d point(List<BakedQuad> quads,String sprite,double u,double v){
        for(BakedQuad quad:quads)if(quad.getSprite().getIconName().endsWith("/"+sprite)){
            int[] data=quad.getVertexData();int stride=data.length/4,uv=quad.getFormat().getUvOffsetById(0)/4;double[][] values=new double[4][5];for(int n=0;n<4;n++){int i=n*stride;for(int axis=0;axis<3;axis++)values[n][axis]=Float.intBitsToFloat(data[i+axis]);values[n][3]=(Float.intBitsToFloat(data[i+uv])-quad.getSprite().getMinU())/(quad.getSprite().getMaxU()-quad.getSprite().getMinU());values[n][4]=(Float.intBitsToFloat(data[i+uv+1])-quad.getSprite().getMinV())/(quad.getSprite().getMaxV()-quad.getSprite().getMinV());}
            double au=values[1][3]-values[0][3],av=values[1][4]-values[0][4],bu=values[3][3]-values[0][3],bv=values[3][4]-values[0][4],det=au*bv-bu*av;if(Math.abs(det)<1e-10)continue;double a=((u-values[0][3])*bv-(v-values[0][4])*bu)/det,b=(au*(v-values[0][4])-av*(u-values[0][3]))/det;if(a<0||a>1||b<0||b>1)continue;
            return new net.minecraft.util.math.Vec3d(values[0][0]+a*(values[1][0]-values[0][0])+b*(values[3][0]-values[0][0]),values[0][1]+a*(values[1][1]-values[0][1])+b*(values[3][1]-values[0][1]),values[0][2]+a*(values[1][2]-values[0][2])+b*(values[3][2]-values[0][2]));
        }throw new IllegalStateException("Copy artwork UV region missing "+sprite+" "+u+","+v);
    }
    private static void direction(Minecraft mc,Block block,IBlockState state,IBakedModel model,EnumFacing facing){
        List<BakedQuad> quads=new ArrayList<>();for(EnumFacing side:SIDES)quads.addAll(model.getQuads(state,side,0));checkArtwork(quads,facing);
        float oldYaw=mc.player.rotationYaw;try{mc.player.rotationYaw=facing.getOpposite().getHorizontalAngle();IBlockState placed=block.getStateForPlacement(mc.world,new net.minecraft.util.math.BlockPos(0,100,0),EnumFacing.UP,.5F,.5F,.5F,0,mc.player);require(placed.getValue(BlockVandorDirectional.FACING)==facing,"normal placement front faces player "+facing);}finally{mc.player.rotationYaw=oldYaw;}
    }
    static void checkArtwork(List<BakedQuad> quads,EnumFacing facing){
        net.minecraft.util.math.Vec3d input=new net.minecraft.util.math.Vec3d(facing.rotateY().getDirectionVec()),output=input.scale(-1),center=new net.minecraft.util.math.Vec3d(.5,.5,.5);
        for(String name:new String[]{"front","back","top","bottom"}){
            net.minecraft.client.renderer.texture.TextureAtlasSprite sprite=quads.stream().filter(q->q.getSprite().getIconName().endsWith("/"+name)).findFirst().get().getSprite();int[] pixels=sprite.getFrameTextureData(0)[0];int cyanPixel=pixels[32*64+21],amberPixel=pixels[32*64+48];require((cyanPixel>>8&255)>(cyanPixel>>16&255)+50&&(cyanPixel&255)>(cyanPixel>>16&255)+50&&(amberPixel>>16&255)>160&&(amberPixel&255)<100,"independent native cyan/amber port pixels "+name);
            net.minecraft.util.math.Vec3d cyan=point(quads,name,21/64D,32/64D),amber=point(quads,name,48/64D,32/64D);require(cyan.subtract(center).dotProduct(input)>.1&&amber.subtract(center).dotProduct(output)>.1,"cyan input and amber output regions agree with player sides on "+name+" "+facing);
            net.minecraft.util.math.Vec3d tail=point(quads,name,28/64D,33/64D),tip=point(quads,name,36/64D,33/64D);require(tip.subtract(tail).dotProduct(output)>.08,"authored paper flow arrow points from input to output on "+name+" "+facing);
        }
        for(String name:new String[]{"front","back","top","bottom","left","right"})for(double u:new double[]{.2,.5,.8})for(double v:new double[]{.2,.5,.8}){
            net.minecraft.util.math.Vec3d expected=name.equals("front")?new net.minecraft.util.math.Vec3d(1-u,1-v,0):name.equals("back")?new net.minecraft.util.math.Vec3d(1-u,1-v,1):name.equals("top")?new net.minecraft.util.math.Vec3d(1-u,1,v):name.equals("bottom")?new net.minecraft.util.math.Vec3d(1-u,0,1-v):name.equals("right")?new net.minecraft.util.math.Vec3d(0,1-v,u):new net.minecraft.util.math.Vec3d(1,1-v,u>=1.5/16&&u<=6.5/16&&v>=6/16D&&v<=10.5/16D?.5+u:1-u);
            require(point(quads,name,u,v).squareDistanceTo(rotate(expected,facing))<2e-5,"native UVs preserve undistorted face art and mirrored input arrow subrect "+name+" "+facing+" "+u+","+v);
        }
        net.minecraft.util.math.Vec3d tip=point(quads,"left",10/64D,31/64D),tail=point(quads,"left",22/64D,31/64D),port=point(quads,"left",47/64D,31/64D);require(tip.squareDistanceTo(port)<tail.squareDistanceTo(port),"cyan input arrow points toward its port "+facing);

    }
    private static BufferedImage block(Minecraft mc,IBlockState state,IBakedModel model){
        Framebuffer frame=new Framebuffer(256,256,true);projection(-.9,.9,-.85,.85);try(TurntableRenderState saved=new TurntableRenderState()){
            clear(frame);GlStateManager.rotate(30,1,0,0);GlStateManager.rotate(225,0,1,0);GlStateManager.translate(-.5,-.5,-.5);mc.getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);List<BakedQuad> quads=new ArrayList<>();for(EnumFacing side:SIDES)quads.addAll(model.getQuads(state,side,0));BufferBuilder buffer=Tessellator.getInstance().getBuffer();buffer.setTranslation(0,0,0);buffer.begin(7,quads.get(0).getFormat());for(BakedQuad quad:quads)buffer.addVertexData(quad.getVertexData());Tessellator.getInstance().draw();GL11.glFinish();return ScreenShotHelper.createScreenshot(256,256,frame);
        }finally{finish(mc,frame);}
    }
    private static void inventory(Minecraft mc,File output,ItemStack stack)throws Exception{
        IBakedModel model=mc.getRenderItem().getItemModelWithOverrides(stack,mc.world,mc.player);ItemTransformVec3f gui=model.getItemCameraTransforms().gui;
        for(EnumFacing side:SIDES)for(BakedQuad quad:model.getQuads(null,side,0)){int[] data=quad.getVertexData();for(int v=0;v<4;v++){int i=v*data.length/4;double x=(Float.intBitsToFloat(data[i])-.5)*gui.scale.x,y=(Float.intBitsToFloat(data[i+1])-.5)*gui.scale.y,z=(Float.intBitsToFloat(data[i+2])-.5)*gui.scale.z,rz=Math.toRadians(gui.rotation.z),ry=Math.toRadians(gui.rotation.y),rx=Math.toRadians(gui.rotation.x);double xx=x*Math.cos(rz)-y*Math.sin(rz);y=x*Math.sin(rz)+y*Math.cos(rz);x=xx;xx=x*Math.cos(ry)+z*Math.sin(ry);z=z*Math.cos(ry)-x*Math.sin(ry);x=xx;y=y*Math.cos(rx)-z*Math.sin(rx);require(Math.abs(x+gui.translation.x)<.42&&Math.abs(y+gui.translation.y)<.42,"inventory padding after GUI transform");}}
        Framebuffer frame=new Framebuffer(128,128,true);projection(0,32,32,0);float previous=mc.getRenderItem().zLevel;try(TurntableRenderState saved=new TurntableRenderState()){
            clear(frame);GlStateManager.enableRescaleNormal();RenderHelper.enableGUIStandardItemLighting();mc.getRenderItem().zLevel=0;mc.getRenderItem().renderItemAndEffectIntoGUI(stack,8,8);GL11.glFinish();BufferedImage image=ScreenShotHelper.createScreenshot(128,128,frame);int ink=0;for(int y=0;y<128;y++)for(int x=0;x<128;x++)if((image.getRGB(x,y)&0xffffff)!=0x203040){ink++;require(x>=35&&x<=92&&y>=35&&y<=92,"actual inventory item padding on every side");}require(ink>200,"inventory item visibly renders");javax.imageio.ImageIO.write(image,"png",new File(output,"shot_copy_controller_inventory.png"));
        }finally{mc.getRenderItem().zLevel=previous;finish(mc,frame);}
    }
    private static void dialog(Minecraft mc,File output)throws Exception{
        Field buttons=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(GuiScreen.class,"buttonList","field_146292_n"),mode=GuiCopyController.class.getDeclaredField("mode"),height=GuiCopyController.class.getDeclaredField("heightSlider"),scanHeight=GuiCopyController.class.getDeclaredField("scanHeight"),increment=GuiCopyController.class.getDeclaredField("increment"),channels=GuiCopyController.class.getDeclaredField("channels"),error=GuiCopyController.class.getDeclaredField("error");for(Field field:new Field[]{buttons,mode,height,scanHeight,increment,channels,error})field.setAccessible(true);net.minecraft.inventory.Container previous=mc.player.openContainer;
        try{for(int[] size:new int[][]{{320,240},{460,340}}){CopyControllerTile tile=new CopyControllerTile();tile.setWorld(new com.vandorlabs.vehicle.VehicleWorld(mc.world,com.vandorlabs.vehicle.VehicleStructure.display(java.util.Collections.emptyList())));GuiCopyController gui=new GuiCopyController(new CopyControllerContainer(tile));gui.setWorldAndResolution(mc,size[0],size[1]);int left=gui.getGuiLeft(),top=gui.getGuiTop();require(left>=0&&top>=0&&left+gui.getXSize()<=size[0]&&top+gui.getYSize()<=size[1],"compact dialog viewport");@SuppressWarnings("unchecked") List<GuiButton> list=(List<GuiButton>)buttons.get(gui);for(GuiButton button:list){require(button.x>=left&&button.y>=top&&button.x+button.width<=left+gui.getXSize()&&button.y+button.height<=top+gui.getYSize(),"button bounds "+button.id);for(GuiButton other:list)if(other.id<button.id)require(button.x>=other.x+other.width||other.x>=button.x+button.width||button.y>=other.y+other.height||other.y>=button.y+button.height,"nonoverlapping actions");}
            GuiButton trigger=list.get(0);gui.mouseClicked(trigger.x+2,trigger.y+2,1);require((Integer)mode.get(gui)==2&&trigger.displayString.contains("OFF edge"),"right click previous trigger");gui.mouseClicked(trigger.x+2,trigger.y+2,0);require((Integer)mode.get(gui)==0&&trigger.displayString.contains("Manual"),"left click next trigger");net.minecraftforge.fml.client.config.GuiSlider slider=(net.minecraftforge.fml.client.config.GuiSlider)height.get(gui);GuiTextField delta=(GuiTextField)increment.get(gui),bank=(GuiTextField)channels.get(gui);gui.mouseClicked(slider.x+4,slider.y+10,0);slider.mouseReleased(slider.x+4,slider.y+10);require((Integer)scanHeight.get(gui)==1,"real height slider selects minimum1");gui.mouseClicked(slider.x+slider.width-4,slider.y+10,0);slider.mouseReleased(slider.x+slider.width-4,slider.y+10);require((Integer)scanHeight.get(gui)==16,"real height slider selects maximum16");require(slider.width==130,"height uses native Turntable slider size");delta.setText("65535");gui.actionPerformed(list.get(1));require(!((String)error.get(gui)).isEmpty(),"Copy action rejects out-of-range signed increment");delta.setText("-65534");bank.setText("1,65535");gui.actionPerformed(list.get(2));require(((String)error.get(gui)).isEmpty(),"valid retry clears local error");delta.setFocused(true);delta.setCursorPositionEnd();gui.keyTyped('\b',org.lwjgl.input.Keyboard.KEY_BACK);require(delta.getText().equals("-6553"),"keyboard edits signed channel change");delta.setFocused(false);Field clientBusy=CopyControllerTile.class.getDeclaredField("clientBusy");clientBusy.setAccessible(true);clientBusy.setBoolean(tile,true);gui.updateScreen();require(!slider.enabled,"busy copy disables real height slider");int busyHeight=(Integer)scanHeight.get(gui);gui.mouseClicked(slider.x+4,slider.y+10,0);require((Integer)scanHeight.get(gui)==busyHeight,"disabled busy slider cannot change scan height");clientBusy.setBoolean(tile,false);gui.updateScreen();require(slider.enabled,"height slider re-enables after copy");
            tile.error="Missing 12 x Glass in supply chest";BufferedImage missing=dialogImage(mc,gui,size[0],size[1]);require(colored(missing,left+12,top+175,gui.getXSize()-24,18,0xff8888)>15,"first missing-material error visibly red within dialog");tile.error="";tile.status="Ready: materials available";BufferedImage ready=dialogImage(mc,gui,size[0],size[1]);require(colored(ready,left+12,top+175,gui.getXSize()-24,18,0xff8888)==0,"cleared material error disappears");require(colored(ready,left+12,top+175,gui.getXSize()-24,18,0xcdd7e4)>15,"ready status readable in compact dialog");javax.imageio.ImageIO.write(missing,"png",new File(output,"shot_copy_controller_dialog_missing_"+size[0]+".png"));javax.imageio.ImageIO.write(ready,"png",new File(output,"shot_copy_controller_dialog_ready_"+size[0]+".png"));
        }}finally{mc.player.openContainer=previous;org.lwjgl.input.Keyboard.enableRepeatEvents(false);}
    }
    private static int colored(BufferedImage image,int x,int y,int width,int height,int rgb){int count=0;for(int py=y;py<y+height;py++)for(int px=x;px<x+width;px++)if((image.getRGB(px,py)&0xffffff)==rgb)count++;return count;}
    private static BufferedImage dialogImage(Minecraft mc,GuiCopyController gui,int width,int height){Framebuffer frame=new Framebuffer(width,height,true);projection(0,width,height,0);try(TurntableRenderState saved=new TurntableRenderState()){clear(frame);GlStateManager.disableDepth();gui.drawScreen(-1,-1,0);GL11.glFinish();return ScreenShotHelper.createScreenshot(width,height,frame);}finally{finish(mc,frame);}}
    private static void projection(double left,double right,double bottom,double top){GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(left,right,bottom,top,-1000,1000);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();}
    private static void clear(Framebuffer frame){frame.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.depthMask(true);GlStateManager.clearColor(32/255F,48/255F,64/255F,1);GlStateManager.clear(16640);GlStateManager.enableDepth();GlStateManager.enableTexture2D();GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.disableBlend();GlStateManager.enableAlpha();GlStateManager.alphaFunc(GL11.GL_GREATER,.1F);GlStateManager.color(1,1,1,1);Minecraft.getMinecraft().entityRenderer.disableLightmap();}
    private static void finish(Minecraft mc,Framebuffer frame){GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    private CopyClientChecks(){}
}
