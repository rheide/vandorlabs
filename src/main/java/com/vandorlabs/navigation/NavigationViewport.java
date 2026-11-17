package com.vandorlabs.navigation;

import com.vandorlabs.tiles.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.render.ScreenSurface;
import net.minecraft.tileentity.TileEntity;

/** Forge adapter: viewport dimensions come from the actual loaded display, never the request. */
public final class NavigationViewport {
    private NavigationViewport(){}
    public static NavigationCanvas canvas(TileEntity tile,int slot){
        if(tile instanceof TileEntityNavigationTable){NavigationLayout.Screen s=((TileEntityNavigationTable)tile).navigationPlane();return new NavigationCanvas(s.widthBlocks,s.heightBlocks,true);}
        TileEntityAnimatedScreenSelector screen=(TileEntityAnimatedScreenSelector)tile;ScreenSurface.Quad q=RedstoneScreenInteractions.surface(tile.getWorld().getBlockState(tile.getPos()),screen,slot);
        double w=(q.topRight.x-q.topLeft.x)/16,h=Math.hypot(q.bottomRight.y-q.topRight.y,q.bottomRight.z-q.topRight.z)/16;
        if(tile.getBlockType().getClass()==BlockAnimatedScreenSelector.class){ScreenChipGroups.Group group=ScreenChipGroups.group(screen);if(group!=null){w*=group.columns;h*=group.rows;}}
        return new NavigationCanvas(w,h,false);
    }
}
