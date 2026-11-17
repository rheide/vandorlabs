package com.vandorlabs.navigation;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.render.ScreenSurface;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.Vec3d;
/** Same display planes and normalized control rectangles as the map raster. */
public final class NavigationInteractions {
    private NavigationInteractions(){}
    private static boolean ordinary(EntityPlayer player,EnumHand hand){return hand==EnumHand.MAIN_HAND&&!player.isSneaking()&&player.getHeldItem(hand).getItem()!=com.vandorlabs.items.ModItems.CONFIGURIZER;}
    public static boolean screen(TileEntityAnimatedScreenSelector tile,EntityPlayer player,EnumHand hand,Vec3d eye,Vec3d end){
        if(!ordinary(player,hand))return false;
        for(int slot=0;slot<2;slot++)if(tile.hasNavigation(slot)&&ScreenChipSupport.navigationAvailable(tile,slot)){
            ScreenSurface.Quad q=RedstoneScreenInteractions.surface(tile.getWorld().getBlockState(tile.getPos()),tile,slot);
            Vec3d a=RedstoneScreenInteractions.local(eye,RedstoneScreenInteractions.facing(tile.getWorld().getBlockState(tile.getPos()))),b=RedstoneScreenInteractions.local(end,RedstoneScreenInteractions.facing(tile.getWorld().getBlockState(tile.getPos())));
            double[] uv=hit(a,b,v(q.topRight),v(q.topLeft),v(q.bottomRight));if(uv==null)continue;uv=NavigationService.screenUV(tile,uv[0],uv[1]);int action=NavigationViewport.canvas(NavigationService.owner(tile),slot).action(uv[0],uv[1]);if(action==7)return true;
            if(NavigationVehicle.physicalWorld(tile.getWorld()).isRemote)com.vandorlabs.VandorLabs.proxy.navigationChanged(NavigationService.owner(tile),action);else NavigationService.action(player,tile,slot,action);return true;
        }return false;
    }
    private static Vec3d v(ScreenSurface.Vertex p){return new Vec3d(p.x,p.y,p.z);}
    private static Vec3d v(NavigationLayout.Vec3 p){return new Vec3d(p.x,p.y,p.z);}
    public static boolean table(TileEntityNavigationTable tile,EntityPlayer player,EnumHand hand){
        if(!ordinary(player,hand)||!tile.formed()||tile.screenSettings().texture!=NavigationMap.SURFACE)return false;
        Vec3d eye=player.getPositionEyes(1),end=eye.add(Vec3d.fromPitchYaw(player.rotationPitch,player.rotationYaw).scale(Math.min(8,player.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue())));
        return table(tile,player,hand,eye,end);
    }
    public static boolean table(TileEntityNavigationTable tile,EntityPlayer player,EnumHand hand,Vec3d eye,Vec3d end){
        if(!ordinary(player,hand)||!tile.formed()||tile.screenSettings().texture!=NavigationMap.SURFACE)return false;NavigationLayout.Screen s=tile.navigationPlane();double[] uv=hit(eye,end,v(s.topLeft),v(s.topRight),v(s.bottomLeft));if(uv==null)return false;int action=NavigationViewport.canvas(NavigationService.owner(tile),0).action(uv[0],uv[1]);
        if(NavigationVehicle.physicalWorld(tile.getWorld()).isRemote)com.vandorlabs.VandorLabs.proxy.navigationChanged(NavigationService.owner(tile),action);else NavigationService.action(player,tile,0,action);return true;
    }
    static double[] hit(Vec3d a,Vec3d b,Vec3d topLeft,Vec3d topRight,Vec3d bottomLeft){
        Vec3d u=topRight.subtract(topLeft),v=bottomLeft.subtract(topLeft),n=u.crossProduct(v),d=b.subtract(a);double divisor=n.dotProduct(d);if(Math.abs(divisor)<1e-9)return null;
        double time=n.dotProduct(topLeft.subtract(a))/divisor;if(time<0||time>1)return null;Vec3d at=a.add(d.scale(time)).subtract(topLeft);double x=at.dotProduct(u)/u.lengthSquared(),y=at.dotProduct(v)/v.lengthSquared();return x<0||x>1||y<0||y>1?null:new double[]{x,y};
    }
}
