package com.vandorlabs.client;

import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.*;
import net.minecraft.util.EnumFacing;
import java.util.*;

/** Compare prepared groups with Forge's released streaming segment decisions. */
final class DoorQuadPlanChecks {
    static void run() {
        TextureAtlasSprite sprite=new TextureAtlasSprite("door_plan_check"){};
        for(int bits=0;bits<256;bits++) {
            List<BakedQuad> general=new ArrayList<>();Map<EnumFacing,List<BakedQuad>> faces=new EnumMap<>(EnumFacing.class);
            for(EnumFacing face:EnumFacing.values())faces.put(face,new ArrayList<>());
            for(int i=0;i<8;i++) {
                BakedQuad quad=new BakedQuad(new int[28],i%3-1,EnumFacing.NORTH,sprite,(bits&(1<<i))!=0,DefaultVertexFormats.ITEM);
                if(i<6)faces.get(EnumFacing.getFront(i)).add(quad);else general.add(quad);
            }
            SimpleBakedModel model=new SimpleBakedModel(general,faces,false,true,sprite,ItemCameraTransforms.DEFAULT,ItemOverrideList.NONE);
            check(model);check(new ScaledDoorItemModel(model));
            IBakedModel dynamic=new SimpleBakedModel(general,faces,false,true,sprite,ItemCameraTransforms.DEFAULT,ItemOverrideList.NONE){};
            require(DoorQuadPlan.prepare(dynamic)==null && DoorQuadPlan.prepare(new ScaledDoorItemModel(dynamic))==null,"unknown model class cached");
            general.add(new BakedQuad(new int[28],-1,EnumFacing.NORTH,sprite,false,DefaultVertexFormats.ITEM){});
            require(DoorQuadPlan.prepare(model)==null,"custom quad behavior cached");general.remove(general.size()-1);
            general.add(new BakedQuad(new int[32],-1,EnumFacing.NORTH,sprite,false,DefaultVertexFormats.BLOCK));
            require(DoorQuadPlan.prepare(model)==null,"light-bearing format bypassed Forge's live lighting path");
        }
        Map<EnumFacing,List<BakedQuad>> emptyFaces=new EnumMap<>(EnumFacing.class);
        for(EnumFacing face:EnumFacing.values())emptyFaces.put(face,Collections.emptyList());
        DoorQuadPlan empty=DoorQuadPlan.prepare(new SimpleBakedModel(Collections.emptyList(),emptyFaces,false,true,sprite,ItemCameraTransforms.DEFAULT,ItemOverrideList.NONE));
        require(empty!=null && empty.segments.isEmpty() && !empty.restoreLighting,"empty model changed lighting");
        System.out.println("PASS: 512 door quad plans preserve Forge shade transitions, quad identity/order and empty-model state; dynamic and light-bearing models fall back");
    }
    private static void check(IBakedModel model) {
        DoorQuadPlan plan=DoorQuadPlan.prepare(model);require(plan!=null,"known immutable item model rejected");
        List<BakedQuad> all=new ArrayList<>();for(EnumFacing face:EnumFacing.values())all.addAll(model.getQuads(null,face,0));all.addAll(model.getQuads(null,null,0));
        // ForgeHooksClient.renderLitItem's existing loop, recording draws instead of calling GL.
        List<BakedQuad> segment=new ArrayList<>();boolean segmentShading=true,segmentShadingDirty=false,hasLighting=false;
        int draw=0;
        for(int i=0;i<all.size();i++) {
            BakedQuad quad=all.get(i);boolean shade=quad.shouldApplyDiffuseLighting();boolean shadeDirty=shade!=segmentShading;
            if(shadeDirty) {
                if(i>0){same(plan.segments.get(draw++),segment,segmentShading,segmentShadingDirty);segment.clear();}
                segmentShading=shade;segmentShadingDirty=shadeDirty;hasLighting=!segmentShading;
            }
            segment.add(quad);
        }
        same(plan.segments.get(draw++),segment,segmentShading,segmentShadingDirty);
        require(draw==plan.segments.size() && hasLighting==plan.restoreLighting,"Forge draw count or final lighting differs");
        try{plan.segments.get(0).quads.clear();throw new AssertionError("mutable cached quads");}catch(UnsupportedOperationException expected){}
    }
    private static void same(DoorQuadPlan.Segment actual,List<BakedQuad> expected,boolean shade,boolean changeShade) {
        require(actual.shade==shade && actual.changeShade==changeShade && actual.quads.size()==expected.size(),"Forge segment state differs");
        for(int i=0;i<expected.size();i++)require(actual.quads.get(i)==expected.get(i),"quad data/order changed");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
