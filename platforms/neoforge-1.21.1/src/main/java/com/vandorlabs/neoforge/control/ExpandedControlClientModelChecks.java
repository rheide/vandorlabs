package com.vandorlabs.neoforge.control;

import com.vandorlabs.neoforge.ModernClientRuntimeChecks;
import com.vandorlabs.render.ControlItemProjection;
import com.vandorlabs.render.ControlMount.Point;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Actual native baked geometry and configured ItemRenderer models, followed by exhaustive padded icon pages. */
public final class ExpandedControlClientModelChecks {
    private static boolean verified;private static int page,stage,started;
    private ExpandedControlClientModelChecks(){}
    private static void need(boolean value,String message){if(!value)throw new IllegalStateException("Expanded control models: "+message);}
    public static String[] captures(){String[] names=new String[8];for(int i=0;i<8;i++)names[i]="modern-expanded-control-icons-"+i+".png";return names;}
    private static List<BakedQuad> quads(net.minecraft.client.resources.model.BakedModel model,BlockState state,ModelData data){List<BakedQuad> result=new ArrayList<>();for(Direction side:new Direction[]{null,Direction.DOWN,Direction.UP,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST})result.addAll(model.getQuads(state,side,RandomSource.create(0),data,null));return result;}
    private static ItemStack item(int family,int mount,int size){MountedControlBlock block=ExpandedControlRuntimeChecks.block(family);ItemStack item=new ItemStack(block.itemBlock());var tag=new net.minecraft.nbt.CompoundTag();tag.putInt("ControlMountVersion",2);tag.putInt("BaseHeight",mount%4);tag.putInt("BaseTilt",mount/4%4);tag.putInt("TiltDirection",mount/16);if(block instanceof MountedPowerLeverBlock)tag.putInt("PowerLeverSize",size);if(block instanceof MountedSignalControlBlock signal&&signal.selectableType())tag.putInt("ControlType",signal.controlType);item.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(MountedControlBlock.wrap(tag)));return item;}
    public static void verify(Minecraft mc){
        if(verified)return;int worldCases=0,itemCases=0;
        for(int family=0;family<8;family++){
            MountedControlBlock block=ExpandedControlRuntimeChecks.block(family);
            for(BlockState state:block.getStateDefinition().getPossibleStates())for(int mount:new int[]{0,1,12,63})for(int size=0;size<(block instanceof MountedPowerLeverBlock?2:1);size++){
                var model=mc.getBlockRenderer().getBlockModel(state);var data=ModelData.builder().with(ControlGeometry.MOUNT,mount).with(ControlGeometry.SIZE,size).build();var faces=quads(model,state,data);need(!faces.isEmpty(),"empty baked pose "+state+"/"+mount+"/"+size);
                var bounds=ControlGeometry.shape(state,mount,size,false).bounds();double[] lo={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY},hi={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
                for(var quad:faces){need(!quad.getSprite().contents().name().getPath().contains("missingno"),"missing authored sprite "+state);int[] v=quad.getVertices();int stride=v.length/4;for(int i=0;i<4;i++)for(int axis=0;axis<3;axis++){double value=Float.intBitsToFloat(v[i*stride+axis]);need(Double.isFinite(value),"nonfinite native vertex");lo[axis]=Math.min(lo[axis],value);hi[axis]=Math.max(hi[axis],value);}}
                double[] expectedLo={bounds.minX,bounds.minY,bounds.minZ},expectedHi={bounds.maxX,bounds.maxY,bounds.maxZ};for(int axis=0;axis<3;axis++)need(Math.abs(lo[axis]-expectedLo[axis])<.0002&&Math.abs(hi[axis]-expectedHi[axis])<.0002,"native emitted mesh differs from authored shared collision "+state+"/"+mount+"/"+size+" axis"+axis+" actual"+lo[axis]+","+hi[axis]+" expected"+expectedLo[axis]+","+expectedHi[axis]);worldCases++;
            }
            for(int mount=0;mount<64;mount++)for(int size=0;size<(block instanceof MountedPowerLeverBlock?2:1);size++){
                ItemStack item=item(family,mount,size);var model=mc.getItemRenderer().getModel(item,mc.level,mc.player,0);need(model instanceof ControlModel,"actual item override bypassed control wrapper");need(model.getRenderPasses(item,false).get(0)==model,"render pass bypasses configured wrapper");var faces=quads(model,null,ModelData.EMPTY);need(!faces.isEmpty(),"configured item empty");var transform=model.getTransforms().gui;
                double x0=Double.POSITIVE_INFINITY,y0=x0,x1=Double.NEGATIVE_INFINITY,y1=x1;
                for(var quad:faces){need(!quad.getSprite().contents().name().getPath().contains("missingno"),"configured sprite");int[] v=quad.getVertices();int stride=v.length/4;for(int i=0;i<4;i++){int at=i*stride;Point p=ControlItemProjection.project(new Point(Float.intBitsToFloat(v[at]),Float.intBitsToFloat(v[at+1]),Float.intBitsToFloat(v[at+2])),transform.rotation.x,transform.rotation.y,transform.rotation.z,transform.scale.x,transform.scale.y,transform.scale.z);x0=Math.min(x0,p.x+transform.translation.x);x1=Math.max(x1,p.x+transform.translation.x);y0=Math.min(y0,p.y+transform.translation.y);y1=Math.max(y1,p.y+transform.translation.y);}}
                need(x0>-.5&&x1<.5&&y0>-.5&&y1<.5,"configured GUI icon exceeds16pixel slot "+family+"/"+mount+"/"+size+" bounds"+x0+","+x1+","+y0+","+y1);itemCases++;
            }
        }
        verified=true;System.out.println("[vandorlabs][modern] expanded-controls-models PASS "+worldCases+" actual emitted native geometry cases and "+itemCases+" configured ItemRenderer models with authored sprites and padding");
    }
    public static boolean tick(Minecraft mc,int tick){if(mc.player==null||mc.level==null)return false;verify(mc);if(page==8)return true;if(stage==0){mc.player.closeContainer();mc.options.guiScale().set(2);mc.resizeDisplay();mc.setScreen(new Icons(page));stage=1;started=tick;return false;}need(tick-started<200,"icon capture timeout "+page);if(stage==1){if(tick-started<10)return false;ModernClientRuntimeChecks.capture(captures()[page]);stage=2;return false;}if(!ModernClientRuntimeChecks.captured(captures()[page]))return false;page++;stage=0;if(page==8){mc.setScreen(null);System.out.println("[vandorlabs][modern] expanded-controls-icons PASS768 actual configured icon cells across eight authored IDs");return true;}return false;}
    private static final class Icons extends Screen {
        private final List<ItemStack> items=new ArrayList<>();
        Icons(int family){super(Component.literal("Configured mounted controls"));for(int size=0;size<(ExpandedControlRuntimeChecks.block(family) instanceof MountedPowerLeverBlock?2:1);size++)for(int h=0;h<4;h++)for(int t=0;t<4;t++)for(int d=0;d<4;d++)items.add(item(family,h+4*(t+4*d),size));}
        @Override public void renderBackground(GuiGraphics graphics,int x,int y,float partial){}
        @Override public void render(GuiGraphics g,int x,int y,float partial){g.fill(0,0,width,height,0xFF101012);int left=(width-352)/2,top=(height-176)/2;for(int i=0;i<items.size();i++){int xx=left+i%16*22,yy=top+i/16*22;g.fill(xx,yy,xx+20,yy+20,0xFF282C32);g.renderItem(items.get(i),xx+2,yy+2);}super.render(g,x,y,partial);}
        @Override public boolean isPauseScreen(){return false;}
    }
}
