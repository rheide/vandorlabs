package com.vandorlabs.client;

import com.vandorlabs.cockpit.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

final class CockpitVisualFixtures {
    static void build(World world){
        for(int row=0;row<2;row++)for(int i=0;i<CockpitData.IDS.length;i++){
            BlockPos root=new BlockPos(222+i*6,30,120+row*8);for(int x=-2;x<4;x++)for(int z=-2;z<3;z++)world.setBlockState(root.add(x,-1,z),Blocks.STONE.getDefaultState(),2);
            world.setBlockState(root,Block.getBlockFromName("vandorlabs:"+CockpitData.IDS[i]).getDefaultState(),2);CockpitAssembly.install(world,root,null);TileEntityCockpit tile=(TileEntityCockpit)world.getTileEntity(root);tile.setTaxiMode(false);
            if(row==1){tile.setSideTexture(14);tile.changed();continue;}
            for(TileEntityCockpit.Display display:tile.displays){for(int choice=0;choice<ScreenHousingTextures.IDS.length;choice++){com.google.gson.JsonObject e=ScreenHousingTextures.entry(choice);if(e!=null && "Screens".equals(ScreenHousingTextures.category(choice)) && e.get("source").getAsString().startsWith("console_inputs/")=="touch".equals(display.role)){display.texture=choice;break;}}}
            int c=0;for(TileEntityCockpit.Control control:tile.controls){control.kind=1;control.level=(c++%4)*5;control.label="Channel "+c;}tile.changed();
        }
    }
}
