package com.vandorlabs.client;

import java.util.*;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.*;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;

/** Compare adjacent display runs with the prior per-slot binding and emitted records. */
public final class MachineDisplayBatchChecks {
    private static void need(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void records(BufferBuilder buffer,List<int[]> target){
        int stride=buffer.getVertexFormat().getIntegerSize();
        for(int vertex=0;vertex<buffer.getVertexCount();vertex++){
            int[] row=new int[stride];for(int word=0;word<stride;word++)row[word]=buffer.getByteBuffer().getInt((vertex*stride+word)*4);target.add(row);
        }
    }
    private static void padding(BufferBuilder buffer,VertexFormat format,int slot){
        // Padding is traversable by the vanilla builder. Seed it so complete-byte
        // parity also proves batching does not overwrite an extended record tail.
        int first=buffer.getVertexCount(),stride=format.getNextOffset();
        for(int vertex=0;vertex<4;vertex++)for(int element=0;element<format.getElementCount();element++){
            VertexFormatElement field=format.getElement(element);if(field.getUsage()!=VertexFormatElement.EnumUsage.PADDING)continue;
            for(int part=0;part<field.getSize();part++)buffer.getByteBuffer().put((first+vertex)*stride+format.getOffset(element)+part,(byte)(slot*31+vertex*7+format.getOffset(element)+part));
        }
    }
    private static int compare(ResourceLocation[] textures,VertexFormat format){
        int size=textures.length;Vec3d[][] points=new Vec3d[size][4];Vec3d[] normals=new Vec3d[size];double[][] uv=new double[size][4];
        for(int slot=0;slot<size;slot++){
            points[slot]=new Vec3d[]{new Vec3d(slot-.125,.1,.25),new Vec3d(slot+.875,.15,.25),new Vec3d(slot+.875,1.2,.75),new Vec3d(slot-.125,1.1,.75)};
            normals[slot]=new Vec3d(0,.5,-.5).normalize();
            // Same atlas or animated strip may carry different rectangles/frames.
            uv[slot]=new double[]{slot*.0625,(slot+1)*.0625,.125+slot*.03125,.75-slot*.03125};
        }
        BufferBuilder expected=new BufferBuilder(8192),actual=new BufferBuilder(8192);
        List<int[]> before=new ArrayList<>(),after=new ArrayList<>();List<ResourceLocation> beforeTextures=new ArrayList<>(),afterTextures=new ArrayList<>();int visible=0,draws=0,expectedRuns=0;ResourceLocation previous=null;
        for(int slot=0;slot<size;slot++)if(textures[slot]!=null){
            if(!textures[slot].equals(previous))expectedRuns++;previous=textures[slot];visible++;
            expected.setTranslation(0,0,0);expected.begin(7,format);
            padding(expected,format,slot);
            // Released per-slot emitter, including flipped V and world brightness.
            for(int i=0;i<4;i++){
                Vec3d p=points[slot][i],normal=normals[slot];double u=i==1||i==2?1:0,v=i<2?1:0;
                expected.pos(p.x,p.y,p.z).color(255,255,255,255).tex(uv[slot][2]+u*(uv[slot][3]-uv[slot][2]),uv[slot][0]+v*(uv[slot][1]-uv[slot][0]))
                        .lightmap((int)OpenGlHelper.lastBrightnessY,(int)OpenGlHelper.lastBrightnessX).normal((float)normal.x,(float)normal.y,(float)normal.z).endVertex();
                beforeTextures.add(textures[slot]);
            }
            expected.finishDrawing();records(expected,before);expected.reset();
        }
        for(int first=0;first<size;){
            if(textures[first]==null){first++;continue;}
            int end=MachineDisplayRenderer.runEnd(textures,first);need(end>first,"empty display run");draws++;
            actual.setTranslation(0,0,0);actual.begin(7,format);
            for(int slot=first;slot<end;slot++)if(textures[slot]!=null){
                need(textures[first].equals(textures[slot]),"run crossed a texture binding transition");
                padding(actual,format,slot);
                MachineDisplayRenderer.emitPlane(actual,points[slot],normals[slot],uv[slot]);
                for(int i=0;i<4;i++)afterTextures.add(textures[first]);
            }
            actual.finishDrawing();records(actual,after);actual.reset();first=end;
        }
        need(draws==expectedRuns&&draws<=visible,"draw count is not the number of adjacent visible bindings");
        need(before.size()==after.size()&&beforeTextures.equals(afterTextures),"display order, vertex count or texture association changed");
        for(int i=0;i<before.size();i++)if(!Arrays.equals(before.get(i),after.get(i)))throw new AssertionError("position/UV/color/light/normal/format changed at vertex "+i
                +" format="+format+" textures="+Arrays.toString(textures)+" before="+Arrays.toString(before.get(i))+" after="+Arrays.toString(after.get(i)));
        return draws;
    }
    public static void main(String[] ignored){
        net.minecraft.init.Bootstrap.register();OpenGlHelper.lastBrightnessX=112;OpenGlHelper.lastBrightnessY=208;
        ResourceLocation atlas=TextureMap.LOCATION_BLOCKS_TEXTURE,off=ScreenTextureLocations.off(false),animated=new ResourceLocation("vandorlabs","display_batch_animation");
        ResourceLocation[] choices={null,atlas,off,animated};int cases=0;
        VertexFormat normal=new VertexFormat(BlockSurfaceFormat.get()),extended=new VertexFormat(normal).addElement(new VertexFormatElement(0,VertexFormatElement.EnumType.INT,VertexFormatElement.EnumUsage.PADDING,4));
        for(VertexFormat format:new VertexFormat[]{normal,extended})for(int length=0;length<=5;length++){
            int combinations=1<<(2*length);
            for(int bits=0;bits<combinations;bits++){
                ResourceLocation[] textures=new ResourceLocation[length];for(int slot=0;slot<length;slot++)textures[slot]=choices[(bits>>(2*slot))&3];compare(textures,format);cases++;
            }
        }
        need(compare(new ResourceLocation[]{atlas,new ResourceLocation(atlas.toString()),atlas},normal)==1,"equivalent atlas identifiers were not batched");
        need(compare(new ResourceLocation[]{atlas,animated,atlas},normal)==3,"separated atlas slots were reordered");
        need(compare(new ResourceLocation[]{atlas,null,atlas},normal)==1,"invisible slot split adjacent visible surfaces");
        // An unpatched 1.12 BufferBuilder cannot emit an added GENERIC attribute:
        // endVertex advances the vertex count without resetting its attribute
        // cursor. A five-attribute emitter then drifts through that sixth field.
        // Keep this negative control, rather than treating malformed records as
        // evidence about the shader-aware builder supplied by OptiFine.
        VertexFormat unsupported=new VertexFormat(normal).addElement(new VertexFormatElement(0,VertexFormatElement.EnumType.FLOAT,VertexFormatElement.EnumUsage.GENERIC,4));
        boolean rejected=false;
        try{compare(new ResourceLocation[]{atlas,atlas},unsupported);}
        catch(AssertionError failure){if(!failure.getMessage().startsWith("position/UV/color/light/normal/format changed"))throw failure;rejected=true;}
        need(rejected,"unsupported generic-tail negative control no longer reproduces vanilla attribute-cursor drift");
        double[] uv={9,8,7,6};
        for(int texture:new int[]{-2,-1,0,7})for(boolean active:new boolean[]{false,true})if(texture<0||!active){
            need(TEAnimatedScreenSelector.resolveDisplay(null,texture,active,true,3,uv).equals(off),"off/default surface resolved another texture");
            need(Arrays.equals(uv,new double[]{0,1,0,1}),"off/default resolution kept stale atlas or animation UVs");
            Arrays.fill(uv,99);
        }
        System.out.println("machine-display-batches PASS cases="+cases+" exact complete normal/extended-padding records and texture order; three shared-atlas/off slots 3->1 draws, A/B/A remains 3, invisible slots and distinct UVs preserved; unsupported generic-tail negative control reproduced");
        System.out.println("machine-display-batches NOTE static/animated resource loading and real shader attribute generation/acceptance require the focused live client");
    }
}
