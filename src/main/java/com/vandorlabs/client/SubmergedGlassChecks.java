package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import java.io.File;
import java.awt.image.BufferedImage;

/** Opaque rails must be composited beneath world water when viewed from above. */
final class SubmergedGlassChecks {
    static void run(Minecraft mc,File output)throws Exception{
        BlockPos pos=new BlockPos(310,36,142);java.util.List<BlockPos> roof=new java.util.ArrayList<>();for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)roof.add(pos.add(x,1,z));int cases=0;
        try{for(int mode=0;mode<3;mode++)for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean inverted:new boolean[]{false,true}){
            mc.world.setBlockState(pos,ModBlocks.PROGRAMMABLE_DIAGONAL_GLASS.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing).withProperty(BlockProgrammableWall.INVERTED,inverted),2);
            TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)mc.world.getTileEntity(pos);tile.setDiagonalGeometry(mode,0);tile.setGlassShade(12);tile.setGlassAppearance(1,true);
            require(tile.shouldRenderInPass(0),"opaque frame must draw before translucent world water");
            String name="submerged-diagonal-"+mode+"-"+facing+"-"+inverted;
            ContactRenderCapture.save(mc,output,name+"-dry",65,pos,pos.up(),roof.toArray(new BlockPos[0]));
            for(BlockPos p:roof)mc.world.setBlockState(p,Blocks.WATER.getDefaultState(),2);
            ContactRenderCapture.save(mc,output,name+"-water",65,pos,pos.up(),roof.toArray(new BlockPos[0]));
            BufferedImage dry=javax.imageio.ImageIO.read(new File(output,name+"-dry.png")),wet=javax.imageio.ImageIO.read(new File(output,name+"-water.png"));int mask=0,tinted=0;
            for(int y=0;y<dry.getHeight();y++)for(int x=0;x<dry.getWidth();x++){int d=dry.getRGB(x,y),w=wet.getRGB(x,y),r=d>>16&255,g=d>>8&255,b=d&255;
                if(Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))>18||Math.abs(r-41)+Math.abs(g-46)+Math.abs(b-51)<24||r<25||r>180)continue;
                mask++;if((w&255)-(w>>16&255)>b-r+12)tinted++;
            }
            require(mask>100&&tinted>mask*.5,"submerged frame bypassed water tint "+name+" mask="+mask+" tinted="+tinted);
            for(BlockPos p:roof)mc.world.setBlockToAir(p);mc.world.setBlockToAir(pos);cases++;
        }}finally{mc.world.setBlockToAir(pos);for(BlockPos p:roof)mc.world.setBlockToAir(p);}
        System.out.println("[vandorlabs][reprolab] submerged-glass-compositing PASS cases="+cases+" three slopes four facings inversions native water above opaque rails pixel tint masks");
    }
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
}
