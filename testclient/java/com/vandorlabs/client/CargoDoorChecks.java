package com.vandorlabs.client;

import com.vandorlabs.tiles.*;
import net.minecraft.nbt.NBTTagCompound;
import java.util.Map;

/** Door catalog filtering and actual tile defaults/persistence without GL. */
final class CargoDoorChecks {
    static void run() {
        try {
            for(java.lang.reflect.Field field:net.minecraft.client.resources.I18n.class.getDeclaredFields())
                if(field.getType()==net.minecraft.client.resources.Locale.class){field.setAccessible(true);field.set(null,new net.minecraft.client.resources.Locale());}
        } catch(ReflectiveOperationException e){throw new AssertionError(e);}
        TileEntitySpaceDoor regular=new TileEntitySpaceDoor();
        TileEntityLargeProgrammableDoor large=new TileEntityLargeProgrammableDoor();
        require(regular.getDesign()==2 && large.getDesign()==23 && !large.isSliding(),"new placement defaults");
        require(large.collisionGeometry(net.minecraft.util.EnumFacing.SOUTH,false)==large.collisionGeometry(net.minecraft.util.EnumFacing.SOUTH,false),"collision cache missed identical assembly");
        java.util.List<net.minecraft.util.math.AxisAlignedBB> closed=large.collisionGeometry(net.minecraft.util.EnumFacing.SOUTH,false);
        require(closed!=large.collisionGeometry(net.minecraft.util.EnumFacing.SOUTH,true),"collision cache retained closed pose");
        NBTTagCompound saved=new NBTTagCompound();large.writeToNBT(saved);
        TileEntityLargeProgrammableDoor restored=new TileEntityLargeProgrammableDoor();restored.readFromNBT(saved);
        require(restored.getDesign()==23,"default survives NBT");
        saved.setInteger("SpaceDesign",16);restored.readFromNBT(saved);
        require(restored.getDesign()==16,"saved artwork preserved");
        for(int detail=0;detail<2;detail++) {
            regular.configure(2,detail,true);
            Map<?,?> small=options(HousingTextureList.forDoors(detail,0,0,100,0));
            Map<?,?> big=options(HousingTextureList.forDoors(detail,0,0,100,0,true));
            for(int design=0;design<30;design++) {
                int choice=ScreenHousingTextures.doorIndex(design,detail);
                require(ScreenHousingTextures.entry(choice).get("design").getAsInt()==design,"lookup preserves appended design");
                require(ScreenHousingTextures.entry(choice).get("detail").getAsInt()==detail,"lookup preserves size");
                require(big.containsKey(choice),"large menu missing artwork");
                require(small.containsKey(choice)==(design<21 || design==29),"double artwork restricted to large doors");
                require(HousingTextureList.doorSizeChoice(choice,(detail+1)%2)==ScreenHousingTextures.doorIndex(design,(detail+1)%2),"size switch");
                if(design>=21 && design!=29) {
                    require(!HousingTextureList.generalTexture(choice),"double artwork leaked to other blocks");
                    require(ScreenHousingTextures.texture(choice).equals(ScreenHousingTextures.fullTexture(choice)),"square art cropped to half thumbnail");
                    regular.configure(design,detail,true);require(regular.getDesign()==2,"regular door accepted double artwork");
                }
                large.configure(design,detail,true);require(large.getDesign()==design,"large artwork rejected");
            }
        }
        System.out.println("PASS: 30 door designs, two size lookups plus legacy aliases, large-only double category, defaults and NBT preservation (no GL)");
    }
    private static Map<?,?> options(HousingTextureList list) {
        try {java.lang.reflect.Field f=HousingTextureList.class.getDeclaredField("options");f.setAccessible(true);return (Map<?,?>)f.get(list);}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
