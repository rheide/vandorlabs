package com.vandorlabs.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.item.*;
import net.minecraft.util.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;

/** All fourteen props must have closed, opaque sides and usable held artwork. */
final class DataTokenRuntimeChecks {
    static void run(Minecraft mc,File output)throws Exception{
        String[] designs={"unbranded","pinnacle","ashiro","paladyne","vektor","seyr","vandor"};
        BufferedImage montage=new BufferedImage(640*7,360*4,BufferedImage.TYPE_INT_RGB);java.awt.Graphics2D canvas=montage.createGraphics();
        try{for(int kind=0;kind<2;kind++)for(int i=0;i<7;i++){
            String type=kind==0?"card":"chip";ItemStack stack=new ItemStack(Item.REGISTRY.getObject(new ResourceLocation("vandorlabs:data_"+type)),1,i);
            IBakedModel model=mc.getRenderItem().getItemModelWithOverrides(stack,mc.world,mc.player);Set<EnumFacing> faces=EnumSet.noneOf(EnumFacing.class);
            if(model.getQuads(null,null,0).size()!=8)throw new IllegalStateException("Token must have one closed slab and two artwork faces: "+type+designs[i]);
            int solidFaces=0;
            for(BakedQuad quad:model.getQuads(null,null,0)){
                if(quad.getSprite().getIconName().equals("vandorlabs:items/data_"+type+"_edge")){
                    solidFaces++;
                    for(int pixel:quad.getSprite().getFrameTextureData(0)[0])if((pixel>>>24)!=255)throw new IllegalStateException("Non-opaque token body "+type+designs[i]);
                }
                faces.add(quad.getFace());
                if(quad.getFace()!=EnumFacing.NORTH&&quad.getFace()!=EnumFacing.SOUTH&&!quad.getSprite().getIconName().equals("vandorlabs:items/data_"+type+"_edge"))throw new IllegalStateException("Transparent token edge material "+type+designs[i]);
            }
            if(faces.size()!=6||solidFaces!=6)throw new IllegalStateException("Open token silhouette "+type+designs[i]);
            for(boolean left:new boolean[]{false,true}){
                BufferedImage held=WheelItemRuntimeChecks.held(mc,stack,left);int ink=0;long totalX=0,totalY=0;
                for(int x=0;x<held.getWidth();x++)for(int y=0;y<held.getHeight();y++)if((held.getRGB(x,y)&0xffffff)!=0x203040){ink++;totalX+=x;totalY+=y;}
                // Vanilla held items may extend beyond the lower screen edge.
                // They must remain visible beside the hand, rather than float in the centre.
                if(ink<300||ink>held.getWidth()*held.getHeight()*.18)throw new IllegalStateException("Unusable held token coverage "+type+designs[i]+" left="+left+" pixels="+ink);
                double cx=totalX/(double)ink,cy=totalY/(double)ink;
                if((left?cx>held.getWidth()*.35:cx<held.getWidth()*.65)||cy<held.getHeight()*.5)throw new IllegalStateException("Held token outside hand corner "+type+designs[i]+" left="+left+" centre="+cx+","+cy);
                canvas.drawImage(held,i*640,(kind+(left?2:0))*360,null);
                javax.imageio.ImageIO.write(held,"png",new File(output,"shot_data_"+type+"_"+designs[i]+(left?"_left":"")+"_held.png"));
            }
        }}finally{canvas.dispose();}
        javax.imageio.ImageIO.write(montage,"png",new File(output,"shot_data_tokens_held.png"));
        System.out.println("[vandorlabs][reprolab] data-token-models PASS fourteen solid six-face slabs two artwork faces opaque body materials and both first-person hand corners");
    }
}
