package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.persistence.SaveSchema;
import com.vandorlabs.ramp.RampGeometry;
import com.vandorlabs.tiles.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import java.util.Random;

/** Released saved-material selection, including malformed overrides and origin fallback. */
final class RampMaterialChecks {
    static void run() {
        Random random=new Random(0x52414d50);int checks=0;
        TileEntityControlledRamp tile=new TileEntityControlledRamp();
        for(int mode=0;mode<3;mode++){
            tile.source=ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState();NBTTagCompound saved=new NBTTagCompound();saved.setInteger("housingTexture",4);saved.setInteger("MainTextureLayout",mode);tile.sourceTileTags.put(BlockPos.ORIGIN,saved);
            if(tile.sourceMaterial(BlockPos.ORIGIN).textureLayout()!=mode)throw new AssertionError("captured ramp lost texture layout");tile.sourceTileTags.clear();
        }
        net.minecraft.client.renderer.texture.TextureAtlasSprite sprite=new net.minecraft.client.renderer.texture.TextureAtlasSprite("ramp_aspect"){};sprite.setIconWidth(32);sprite.setIconHeight(16);sprite.initSprite(64,64,0,0,false);
        RampGeometry.Box square=new RampGeometry.Box(0,0,0,1,1,1),wide=new RampGeometry.Box(0,0,0,1,.5,1);
        if(TEControlledRamp.layoutU(1,0,square,EnumFacing.NORTH,sprite,2)!=.5 || TEControlledRamp.layoutU(1,0,wide,EnumFacing.NORTH,sprite,2)!=1)throw new AssertionError("moving ramp aspect fit policy");
        java.util.List<double[]> repeated=TEControlledRamp.repeatQuads(java.util.Arrays.asList(new double[]{0,0,0,-.25,0},new double[]{0,1,0,-.25,2},new double[]{1,1,0,1.25,2},new double[]{1,0,0,1.25,0}),1);
        double area=0;for(int i=0;i<repeated.size();i+=4){double[] a=repeated.get(i),b=repeated.get(i+1),c=repeated.get(i+2);area+=Math.abs((b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]))/2;for(int j=0;j<4;j++){double[] p=repeated.get(i+j);if(p[3]<0 || p[3]>1 || p[4]<0 || p[4]>1)throw new AssertionError("ramp UV escaped atlas slot");}}
        if(Math.abs(area-1)>1e-8)throw new AssertionError("ramp repeat clipping changed area");
        for(int sample=0;sample<512;sample++) {
            tile.setPos(new BlockPos(random.nextInt(32)-16,60,random.nextInt(32)-16));
            tile.source=ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState().withProperty(BlockAnimatedScreenSelector.FACING,
                    EnumFacing.getHorizontal(sample&3));
            tile.origins.clear();tile.sourceTileTags.clear();
            for(int i=0;i<(sample&7);i++) {
                BlockPos origin=tile.getPos().add(i-2,-1,0);tile.origins.add(origin);
                NBTTagCompound saved=new NBTTagCompound();
                if((sample+i)%9!=0)saved.setInteger(SaveSchema.Screen.HOUSING_TEXTURE,random.nextInt(150)-20);
                saved.setBoolean("FaceTexturesEnabled",random.nextBoolean());
                int[] values=new int[(sample+i)%13==0?5:6];
                for(int j=0;j<values.length;j++)values[j]=random.nextInt(180)-20;
                saved.setIntArray("FaceTextures",values);
                if((sample+i)%5!=0)tile.sourceTileTags.put(origin,saved);
            }
            RampGeometry.Box box=new RampGeometry.Box(-.5,-1,.2,random.nextDouble()*4,.75,.9);
            TileEntityControlledRamp.SourceMaterial material=tile.sourceMaterial(box,.5);
            for(EnumFacing face:EnumFacing.values()) {
                int expected=reference(tile,box,.5,face);
                if(tile.sourceHousing(box,.5,face)!=expected || material.texture(face)!=expected)throw new AssertionError("ramp material selection changed");
                checks++;
            }
            // Direct mutation is supported: a later query must see the current saved tag.
            if(!tile.sourceTileTags.isEmpty()) {
                NBTTagCompound saved=tile.sourceTileTags.values().iterator().next();
                saved.setInteger(SaveSchema.Screen.HOUSING_TEXTURE,4);saved.setBoolean("FaceTexturesEnabled",false);
                for(EnumFacing face:EnumFacing.values()) {
                    if(tile.sourceHousing(box,.5,face)!=reference(tile,box,.5,face))throw new AssertionError("stale saved ramp material");
                    checks++;
                }
            }
        }
        System.out.println("PASS: "+checks+" saved ramp material queries preserve source orientation, origin fallback, malformed tags and immediate edits");
    }
    static int reference(TileEntityControlledRamp tile,RampGeometry.Box box,double partial,EnumFacing face) {
        if(tile.sourceTileTags.isEmpty())return -1;
        double x=tile.getPos().getX()+(box.minX+box.maxX)*.5;
        double z=tile.getPos().getZ()+(box.minZ+box.maxZ)*.5;
        BlockPos selected=tile.origins.isEmpty()?tile.getPos():tile.origins.get(0);
        for(BlockPos origin:tile.origins)if(x>=origin.getX() && x<origin.getX()+1 && z>=origin.getZ() && z<origin.getZ()+1){selected=origin;break;}
        NBTTagCompound saved=tile.sourceTileTags.get(selected);
        if(saved==null)saved=tile.sourceTileTags.values().iterator().next();
        if(!saved.hasKey(SaveSchema.Screen.HOUSING_TEXTURE))return -1;
        int main=ScreenHousingTextures.clamp(saved.getInteger(SaveSchema.Screen.HOUSING_TEXTURE));
        FaceTextures faces=new FaceTextures(saved.getBoolean("FaceTexturesEnabled"),saved.getIntArray("FaceTextures"));
        EnumFacing facing=tile.source.getValue(BlockAnimatedScreenSelector.FACING);
        int rotation=((int)(180-facing.getHorizontalAngle())/90)&3;
        EnumFacing local=face;
        if(local.getAxis()!=EnumFacing.Axis.Y)for(int i=0;i<rotation;i++)local=local.rotateY();
        return faces.texture(local.getIndex(),main);
    }
}
