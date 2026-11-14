package com.vandorlabs.client;

import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Actual item submissions for every canopy family, including both hands and dropped-item transforms. */
final class CanopyHeldItemChecks {
    private static final String[] KINDS={"angled_front_1x1","angled_front_1x2","canopy","long_canopy","regular_canopy_glass","front_canopy_glass","large_front_canopy_glass","space_visor_2x1","space_visor_3x1","space_visor_5x1","space_visor_5x1_double_height"};
    private static final int[] BEFORE={728,912,1728,2056,1236,782,782,402,422,482,482};
    static void run(Minecraft mc, File output) throws Exception {
        IBakedModel stone=mc.getRenderItem().getItemModelWithOverrides(new ItemStack(Blocks.STONE),mc.world,mc.player);
        List<ItemStack> stacks=new ArrayList<>();int rendered=0,before=0,after=0;
        for(int kind=0;kind<KINDS.length;kind++)for(int profile=0;profile<3;profile++){
            ItemStack item=new ItemStack(Block.getBlockFromName("vandorlabs:"+KINDS[kind]));
            if(profile>0){NBTTagCompound tag=new NBTTagCompound();tag.setInteger("CanopyDepth",profile==1?0:80);tag.setInteger("CanopyScale",profile==1?10:100);tag.setInteger("CanopyShade",8);tag.setInteger("CanopyTint",3);if(profile==2){tag.setInteger("CanopyMaterial",64);tag.setInteger("CanopyTextureLayout",2);}tag.setByteArray("Opaque",new byte[65536]);item.setTagCompound(tag);}
            stacks.add(item);IBakedModel model=mc.getRenderItem().getItemModelWithOverrides(item,mc.world,mc.player);
            List<BakedQuad> quads=model.getQuads(null,null,0);require(!quads.isEmpty(),"authored geometry "+KINDS[kind]);
            if(profile==0){before+=BEFORE[kind];after+=quads.size();require(quads.size()<BEFORE[kind]*3/4,"reduced item submission "+KINDS[kind]+" before="+BEFORE[kind]+" after="+quads.size());System.out.println("[vandorlabs][reprolab] canopy-item-cost "+KINDS[kind]+" before="+BEFORE[kind]+" after="+quads.size());}
            for(BakedQuad quad:quads){require(!quad.getSprite().getIconName().contains("missingno"),"authored atlas sprite");int[] words=quad.getVertexData();for(int i=0;i<4;i++)for(int axis=0;axis<3;axis++){float point=Float.intBitsToFloat(words[i*7+axis]);require(point>=-1E-5&&point<=1.00001,"one-block item envelope "+KINDS[kind]+" profile="+profile);}}
            for(ItemCameraTransforms.TransformType type:new ItemCameraTransforms.TransformType[]{ItemCameraTransforms.TransformType.FIRST_PERSON_RIGHT_HAND,ItemCameraTransforms.TransformType.FIRST_PERSON_LEFT_HAND,ItemCameraTransforms.TransformType.THIRD_PERSON_RIGHT_HAND,ItemCameraTransforms.TransformType.THIRD_PERSON_LEFT_HAND,ItemCameraTransforms.TransformType.GUI,ItemCameraTransforms.TransformType.GROUND,ItemCameraTransforms.TransformType.FIXED}){
                ItemTransformVec3f actual=model.getItemCameraTransforms().getTransform(type);
                if(type!=ItemCameraTransforms.TransformType.GUI){ItemTransformVec3f expected=stone.getItemCameraTransforms().getTransform(type);require((!KINDS[kind].equals("regular_canopy_glass")||type==ItemCameraTransforms.TransformType.GROUND||type==ItemCameraTransforms.TransformType.FIXED||actual.rotation.equals(expected.rotation))&&actual.translation.equals(expected.translation)&&actual.scale.equals(expected.scale),"normal block transform "+type+" "+KINDS[kind]);}
                BufferedImage frame=SurfaceClientChecks.itemFrame(mc,item,type);int minX=64,minY=64,maxX=-1,maxY=-1,ink=0;
                for(int y=0;y<64;y++)for(int x=0;x<64;x++)if((frame.getRGB(x,y)&0xffffff)!=0x203040){minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);ink++;}
                require(ink>2&&minX>0&&minY>0&&maxX<63&&maxY<63,"visible padded item "+KINDS[kind]+" profile="+profile+" "+type+" ink="+ink);
                ImageIO.write(frame,"png",new File(output,"canopy-item-"+KINDS[kind]+"-"+profile+"-"+type+".png"));rendered++;
            }
        }
        for(int n=0;n<30000;n++)mc.getRenderItem().getItemModelWithOverrides(stacks.get(n%stacks.size()),mc.world,mc.player);
        com.sun.management.ThreadMXBean meter=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();meter.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId(),start=meter.getThreadAllocatedBytes(thread);
        for(int n=0;n<10000;n++)mc.getRenderItem().getItemModelWithOverrides(stacks.get(n%stacks.size()),mc.world,mc.player);
        long bytes=meter.getThreadAllocatedBytes(thread)-start;require(bytes<=128,"warm selector allocates "+bytes);
        ItemStack mutable=stacks.get(16);IBakedModel original=mc.getRenderItem().getItemModelWithOverrides(mutable,mc.world,mc.player);mutable.getTagCompound().setInteger("CanopyScale",99);require(mc.getRenderItem().getItemModelWithOverrides(mutable,mc.world,mc.player)!=original,"in-place item edits invalidate selection");
        require(CanopyModels.Icon.retainedBytes()<=8*1024*1024,"weighted mesh budget");
        System.out.println("[vandorlabs][reprolab] canopy-item-presentation PASS rendered="+rendered+" default-quads-before="+before+" after="+after+" warm10000="+bytes+" retained="+CanopyModels.Icon.retainedBytes()+" all-families/both-hands/ground/fixed/padded-GUI/mutable-NBT");
    }
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("Canopy item: "+message);}
    private CanopyHeldItemChecks(){}
}
