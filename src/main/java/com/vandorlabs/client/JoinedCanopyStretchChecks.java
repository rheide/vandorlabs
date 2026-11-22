package com.vandorlabs.client;

import com.vandorlabs.canopy.CanopyMesh;
import com.vandorlabs.canopy.LargeCanopySettings;
import com.vandorlabs.tiles.ScreenHousingTextures;
import com.vandorlabs.tiles.TileEntityCanopy;
import java.io.File;
import java.util.concurrent.Future;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

final class JoinedCanopyStretchChecks {
    private static final BlockPos ORIGIN=new BlockPos(40,80,40);
    private static final LiveGallery gallery=new LiveGallery("canopy-stretch",ORIGIN)
            .shot("canopy",1,4,-2,0,40)
            .shot("long",6,4,-2,0,40);
    private static Future<?> placed;
    private static int ticks;

    private static void place(Minecraft mc) {
        EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
        WorldServer world=player.getServerWorld();
        player.rotationYaw=0;
        player.setPositionAndUpdate(41,81,39);
        for (int kind=0;kind<2;kind++) {
            Block block=Block.getBlockFromName("vandorlabs:"+(kind==0?"canopy":"long_canopy"));
            for (int col=0;col<2;col++) {
                BlockPos pos=ORIGIN.east(kind*5+col);
                world.setBlockState(pos.down(),Blocks.STONE.getDefaultState(),3);
                LargeCanopySettings settings=new LargeCanopySettings();
                settings.material=ScreenHousingTextures.screenIndex("industrial_grate");
                settings.tint=3;
                ItemStack stack=new ItemStack(block);
                NBTTagCompound tag=new NBTTagCompound();
                settings.write(tag);
                stack.setTagCompound(tag);
                if (!LiveGallery.place(world,player,stack,pos.down(),EnumFacing.UP,.5F,1,.5F))
                    throw new IllegalStateException("Failed placing canopy at "+pos);
            }
        }
    }

    static boolean tick(Minecraft mc,File out) throws Exception {
        if (placed==null) {
            placed=mc.getIntegratedServer().addScheduledTask(()->place(mc));
            return false;
        }
        if (!placed.isDone()) return false;
        placed.get();
        for (int kind=0;kind<2;kind++) {
            TileEntityCanopy canopy=TileEntityCanopy.at(mc.world,ORIGIN.east(kind*5));
            if (canopy==null || canopy.owner().width()!=2) {
                if (++ticks>300) throw new IllegalStateException("Joined canopy never synchronized: "+kind);
                return false;
            }
            boolean left=false,right=false;
            for (CanopyMesh.Face face:canopy.owner().baseFaces()) {
                if (!face.glazing) continue;
                double min=1,max=0;
                for (double[] uv:face.uv) {min=Math.min(min,uv[0]); max=Math.max(max,uv[0]);}
                left|=min<.01 && max<=.501 && max>.4;
                right|=max>.99 && min>=.499 && min<.6;
            }
            if (!left || !right) throw new IllegalStateException("Main glass repeats its texture: "+kind);
        }
        if (!gallery.tick(mc,out)) return false;
        System.out.println("[vandorlabs] canopy-stretch PASS placed side-by-side Canopy and Long Canopy");
        return true;
    }
    private JoinedCanopyStretchChecks() {}
}
