package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntitySpaceDoor;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

final class DoorMaterialChecks {
    static void run() {
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntitySpaceDoor.class,new net.minecraft.util.ResourceLocation("minecraft:vandorlabs_door_material_check"));
        TileEntitySpaceDoor tile=new TileEntitySpaceDoor();require(!tile.isTileTexture(),"legacy default is Fit");tile.setTileTexture(true);
        NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());TileEntitySpaceDoor restored=new TileEntitySpaceDoor();restored.readFromNBT(saved);require(restored.isTileTexture(),"saved Tile");
        restored=new TileEntitySpaceDoor();restored.applyItemSettings(tile.itemSettings());require(restored.isTileTexture(),"configured item Tile");
        saved.removeTag("DoorTileTexture");restored.readFromNBT(saved);require(!restored.isTileTexture(),"old save Fit default");
        TextureAtlasSprite sprite=new TextureAtlasSprite("door_material_check"){};sprite.setIconWidth(16);sprite.setIconHeight(16);sprite.initSprite(64,64,16,16,false);
        for(double inset:new double[]{0,1/16D}) {
            BufferBuilder buffer=new BufferBuilder(4096);net.minecraft.client.renderer.vertex.VertexFormat format=BlockSurfaceFormat.get();buffer.begin(7,format);
            double[] bounds={inset,inset,12.24/16D,1-inset,2-inset,14.24/16D};DoorFaceTextureMesh.draw(buffer,sprite,bounds,(192<<16)|80);buffer.finishDrawing();
            require(buffer.getVertexCount()==16,"two one-block-high tiles on each door face");java.nio.ByteBuffer data=buffer.getByteBuffer();int stride=format.getNextOffset(),uv=format.getUvOffsetById(0);
            double minY=10,maxY=-10;
            for(int i=0;i<buffer.getVertexCount();i++) {
                int offset=i*stride;float y=data.getFloat(offset+4);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
                float u=data.getFloat(offset+uv),v=data.getFloat(offset+uv+4);
                require(u>=sprite.getMinU()-1e-6 && u<=sprite.getMaxU()+1e-6 && v>=sprite.getMinV()-1e-6 && v<=sprite.getMaxV()+1e-6,"tile samples outside atlas sprite");
                float z=data.getFloat(offset+8);require(Math.abs(z-bounds[2])<1e-6 || Math.abs(z-bounds[5])<1e-6,"material changed leaf depth");
            }
            require(Math.abs(minY-bounds[1])<1e-6 && Math.abs(maxY-bounds[4])<1e-6,"tiles do not cover complete native leaf");
        }
        System.out.println("PASS: door Tile/Fit persistence and configured items; tiled face coverage, native depth and atlas bounds");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
