package com.vandorlabs.neoforge.rack.client;

import com.vandorlabs.neoforge.rack.*;
import com.vandorlabs.neoforge.ports.client.NativePortClient;
import com.vandorlabs.rack.ModuleRackPolicy;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Atlas-generation immutable parts plus bounded terrain combination references. */
public final class NativeRackModel extends BakedModelWrapper<BakedModel> {
    private final List<BakedQuad> shell;
    private final int facing;
    private final List<BakedQuad>[][] modules,seated,indicators;
    private final List<BakedQuad>[] empty;
    private final LinkedHashMap<Integer,List<BakedQuad>> combinations=new LinkedHashMap<>();
    private final LinkedHashMap<Integer,NativeRackModel> finishes=new LinkedHashMap<>(16,.75F,true);
    private int combinationBytes;
    @SuppressWarnings("unchecked") public NativeRackModel(BakedModel source,Direction facing,Map<ModelResourceLocation,BakedModel> models){
        super(source);this.facing=facing.get3DDataValue();shell=part("shell",facing,models,false);modules=(List<BakedQuad>[][])new List<?>[4][2];seated=(List<BakedQuad>[][])new List<?>[4][2];indicators=(List<BakedQuad>[][])new List<?>[4][2];empty=(List<BakedQuad>[])new List<?>[4];
        for(int slot=0;slot<4;slot++){modules[slot][0]=part("slot_"+slot,facing,models,false);modules[slot][1]=part("slot_autopilot_"+slot,facing,models,false);seated[slot][0]=part("slot_seated_"+slot,facing,models,false);seated[slot][1]=part("slot_seated_autopilot_"+slot,facing,models,false);empty[slot]=part("slot_empty_"+slot,facing,models,false);for(int state=0;state<2;state++)indicators[slot][state]=part("indicator_"+slot+(state==0?"_off":"_on"),facing,models,true);}
    }
    private NativeRackModel(NativeRackModel original,TextureAtlasSprite sprite){super(original.originalModel);facing=original.facing;var quads=new ArrayList<BakedQuad>();for(var quad:original.shell)quads.add(housingFace(quad,facing)?NativePortClient.retextureQuad(quad,sprite):quad);shell=List.copyOf(quads);modules=original.modules;seated=original.seated;empty=original.empty;indicators=original.indicators;}
    private static boolean housingFace(BakedQuad quad, int facing) {
        int[] data = quad.getVertices(); int stride = data.length / 4;
        if (stride < 3 || data.length % 4 != 0) return false;
        double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
        for(int vertex=0;vertex<4;vertex++) {
            int offset=vertex*stride; double x=Float.intBitsToFloat(data[offset]),y=Float.intBitsToFloat(data[offset+1]),z=Float.intBitsToFloat(data[offset+2]);
            minX=Math.min(minX,x);minY=Math.min(minY,y);minZ=Math.min(minZ,z);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);maxZ=Math.max(maxZ,z);
        }
        return com.vandorlabs.rack.ModuleRackGeometry.housingFace(facing,quad.getDirection().get3DDataValue(),minX,minY,minZ,maxX,maxY,maxZ);
    }

    public List<BakedQuad> moving(int slot,int kind){return modules[slot][kind==2?1:0];}
    private synchronized NativeRackModel housing(int choice){var found=finishes.get(choice);if(found!=null)return found;var sprite=Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ResourceLocation.parse(com.vandorlabs.neoforge.ModernFinishCatalog.option(choice).texture()));found=new NativeRackModel(this,sprite);if(finishes.size()>=16)finishes.remove(finishes.keySet().iterator().next());finishes.put(choice,found);return found;}
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType layer){if(side!=null)return List.of();var visual=data.get(NativeRackEntity.VISUAL);if(visual==null)return combined(0,0,0);if(visual.housing()>=0)return housing(visual.housing()).combined(visual.occupied(),visual.kinds(),visual.visible());return combined(visual.occupied(),visual.kinds(),visual.visible());}
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random){return side==null?shell:List.of();}
    private synchronized List<BakedQuad> combined(int occupied,int kinds,int visible){int key=0,multiplier=1;for(int slot=0;slot<4;slot++){int digit=(occupied&1<<slot)==0?0:(visible&1<<slot)==0?ModuleRackPolicy.slotKind(kinds,slot)==2?4:3:ModuleRackPolicy.slotKind(kinds,slot)==2?2:1;key+=digit*multiplier;multiplier*=5;}var found=combinations.get(key);if(found!=null)return found;var quads=new ArrayList<BakedQuad>(shell);for(int slot=0;slot<4;slot++){boolean present=(occupied&1<<slot)!=0;quads.addAll(indicators[slot][present?1:0]);if(!present)quads.addAll(empty[slot]);else if((visible&1<<slot)!=0)quads.addAll(seated[slot][ModuleRackPolicy.slotKind(kinds,slot)==2?1:0]);}found=List.copyOf(quads);int bytes=64+found.size()*8;while(combinationBytes+bytes>65536&&!combinations.isEmpty()){var iterator=combinations.entrySet().iterator();var old=iterator.next();combinationBytes-=64+old.getValue().size()*8;iterator.remove();}if(bytes<=65536){combinations.put(key,found);combinationBytes+=bytes;}return found;}
    private static List<BakedQuad> part(String name,Direction facing,Map<ModelResourceLocation,BakedModel> models,boolean fullbright){var model=Objects.requireNonNull(models.get(ModelResourceLocation.standalone(NativeRackClient.model(name))),name);var source=new ArrayList<BakedQuad>();var random=RandomSource.create(0);source.addAll(model.getQuads(null,null,random));for(var side:Direction.values())source.addAll(model.getQuads(null,side,random));var result=new ArrayList<BakedQuad>();for(var quad:source)result.add(rotate(quad,facing,fullbright));return List.copyOf(result);}
    private static double x(Direction face,double x,double y,double z){return switch(face){case NORTH->1-x;case WEST->1-z;case EAST->z;default->x;};}
    private static double y(Direction face,double x,double y,double z){return switch(face){case UP->z;case DOWN->1-z;default->y;};}
    private static double z(Direction face,double x,double y,double z){return switch(face){case NORTH->1-z;case WEST->x;case EAST->1-x;case UP->1-y;case DOWN->y;default->z;};}
    static BakedQuad rotate(BakedQuad quad,Direction face,boolean fullbright){int[] data=quad.getVertices().clone();int stride=data.length/4;if(stride<8||data.length%4!=0)throw new IllegalArgumentException("Rack vertex format");for(int v=0;v<4;v++){int offset=v*stride;double x=Float.intBitsToFloat(data[offset]),y=Float.intBitsToFloat(data[offset+1]),z=Float.intBitsToFloat(data[offset+2]);data[offset]=Float.floatToRawIntBits((float)x(face,x,y,z));data[offset+1]=Float.floatToRawIntBits((float)y(face,x,y,z));data[offset+2]=Float.floatToRawIntBits((float)z(face,x,y,z));int normal=data[offset+7];double nx=(byte)normal/127D,ny=(byte)(normal>>>8)/127D,nz=(byte)(normal>>>16)/127D;int xx=(int)Math.round((x(face,.5+nx,.5+ny,.5+nz)-.5)*127),yy=(int)Math.round((y(face,.5+nx,.5+ny,.5+nz)-.5)*127),zz=(int)Math.round((z(face,.5+nx,.5+ny,.5+nz)-.5)*127);data[offset+7]=(normal&0xff000000)|(xx&255)|(yy&255)<<8|(zz&255)<<16;if(fullbright)data[offset+6]=0x00f000f0;}
        var direction=quad.getDirection();var transformed=Direction.getNearest((float)(x(face,.5+direction.getStepX(),.5+direction.getStepY(),.5+direction.getStepZ())-.5),(float)(y(face,.5+direction.getStepX(),.5+direction.getStepY(),.5+direction.getStepZ())-.5),(float)(z(face,.5+direction.getStepX(),.5+direction.getStepY(),.5+direction.getStepZ())-.5));return new BakedQuad(data,quad.getTintIndex(),transformed,quad.getSprite(),!fullbright&&quad.isShade(),!fullbright&&quad.hasAmbientOcclusion());}
}
