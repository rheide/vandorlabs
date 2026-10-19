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
        BufferedImage montage=new BufferedImage(640*7,360*2,BufferedImage.TYPE_INT_RGB);java.awt.Graphics2D canvas=montage.createGraphics();
        try{for(int kind=0;kind<2;kind++)for(int i=0;i<7;i++){
            String type=kind==0?"card":"chip";ItemStack stack=new ItemStack(Item.REGISTRY.getObject(new ResourceLocation("vandorlabs:data_"+type)),1,i);
            IBakedModel model=mc.getRenderItem().getItemModelWithOverrides(stack,mc.world,mc.player);Set<EnumFacing> faces=EnumSet.noneOf(EnumFacing.class);
            for(BakedQuad quad:model.getQuads(null,null,0)){
                faces.add(quad.getFace());
                if(quad.getFace()!=EnumFacing.NORTH&&quad.getFace()!=EnumFacing.SOUTH&&!quad.getSprite().getIconName().equals("vandorlabs:items/data_"+type+"_edge"))throw new IllegalStateException("Transparent token edge material "+type+designs[i]);
            }
            if(faces.size()!=6)throw new IllegalStateException("Open token silhouette "+type+designs[i]);
            BufferedImage held=WheelItemRuntimeChecks.held(mc,stack);int ink=0;
            for(int x=0;x<held.getWidth();x++)for(int y=0;y<held.getHeight();y++)if((held.getRGB(x,y)&0xffffff)!=0x203040){
                ink++;if(x<8||y<8||x>=held.getWidth()-8||y>=held.getHeight()-8)throw new IllegalStateException("Clipped held token "+type+designs[i]);
            }
            if(ink<300)throw new IllegalStateException("Empty held token "+type+designs[i]);
            canvas.drawImage(held,i*640,kind*360,null);
            javax.imageio.ImageIO.write(held,"png",new File(output,"shot_data_"+type+"_"+designs[i]+"_held.png"));
        }}finally{canvas.dispose();}
        javax.imageio.ImageIO.write(montage,"png",new File(output,"shot_data_tokens_held.png"));
        System.out.println("[vandorlabs][reprolab] data-token-models PASS fourteen closed six-face models opaque edge materials and first-person renders");
    }
}
