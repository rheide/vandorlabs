package com.vandorlabs.client;

import com.vandorlabs.render.TrapdoorGeometry;
import com.vandorlabs.tiles.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.nbt.NBTTagCompound;
import java.nio.ByteBuffer;

/** Artwork repetition, mirroring, clipping, and saved/copied layout contracts. */
final class TrapdoorTextureChecks {
    static void run() {
        TextureAtlasSprite sprite=new TextureAtlasSprite("trapdoor_texture_check"){};
        sprite.setIconWidth(16);sprite.setIconHeight(32);sprite.initSprite(128,128,16,32,false);
        int choice=ScreenHousingTextures.doorIndex(0,0);
        for(boolean tile:new boolean[]{false,true})for(boolean mirror:new boolean[]{false,true}) {
            double[][] vertices=new double[8][3],uv=new double[8][3];
            for(int i=0;i<8;i++){double x=(i&1)==0?0:2,z=(i&4)==0?0:2;vertices[i]=new double[]{x,(i&2)==0?0:3/16D,z};uv[i]=new double[]{tile?x:x/2,(i&2)==0?0:3/16D,z/2};}
            BufferBuilder buffer=new BufferBuilder(8192);net.minecraft.client.renderer.vertex.VertexFormat format=BlockSurfaceFormat.get();buffer.begin(7,format);
            TrapdoorSurfaceMesh.draw(buffer,sprite,vertices,uv,0xC00050,choice,0,2,tile,tile && mirror);buffer.finishDrawing();
            ByteBuffer bytes=buffer.getByteBuffer();int stride=format.getNextOffset(),tex=format.getUvOffsetById(0);double area=0;
            require(buffer.getVertexCount()==(tile?32:16),"door tiling did not repeat columns");
            for(int i=0;i<buffer.getVertexCount();i++) {
                int offset=i*stride;float x=bytes.getFloat(offset),z=bytes.getFloat(offset+8),u=bytes.getFloat(offset+tex),v=bytes.getFloat(offset+tex+4);
                require(u>=sprite.getMinU()-1e-6 && u<=sprite.getMaxU()+1e-6 && v>=sprite.getMinV()-1e-6 && v<=sprite.getMaxV()+1e-6,"door tiling samples neighboring atlas sprite");
                double expected=!tile?x/2:mirror && x>=1?2-x:x<=1?x:x-1;
                // A shared boundary can belong to either neighboring tile.
                if(Math.abs(x-1)>1e-6 || !tile || mirror)require(Math.abs(sprite.getUnInterpolatedU(u)/16-expected)<1e-5,"alternate door column not mirrored");
                require(Math.abs(sprite.getUnInterpolatedV(v)/16-z/2)<1e-5,"door artwork stretched vertically");
                if(i%4==0){int b=offset+stride,c=b+stride;double bx=bytes.getFloat(b)-x,bz=bytes.getFloat(b+8)-z,cx=bytes.getFloat(c)-x,cz=bytes.getFloat(c+8)-z;area+=Math.abs(bx*cz-bz*cx)/2;}
            }
            require(Math.abs(area-8)<1e-6,"tiled faces leave gaps or duplicate surface area");
        }
        TileEntityProgrammableTrapdoor source=new TileEntityProgrammableTrapdoor();source.setTileTexture(false);source.setCover(true);source.setCoverFacing(net.minecraft.util.EnumFacing.WEST);
        NBTTagCompound values=new NBTTagCompound();values.setBoolean(com.vandorlabs.items.ProgrammableSettings.TRAPDOOR_TILE_TEXTURE,false);values.setBoolean(com.vandorlabs.items.ProgrammableSettings.TRAPDOOR_COVER,true);values.setInteger(com.vandorlabs.items.ProgrammableSettings.TRAPDOOR_COVER_FACING,net.minecraft.util.EnumFacing.WEST.getHorizontalIndex());
        net.minecraft.item.ItemStack item=new net.minecraft.item.ItemStack(com.vandorlabs.blocks.ModBlocks.PROGRAMMABLE_TRAPDOOR);
        net.minecraft.item.ItemStack configured=com.vandorlabs.items.ProgrammableSettings.applyToItem(item,values);
        require(!configured.isEmpty() && !configured.getSubCompound("BlockEntityTag").getBoolean("TrapdoorTileTexture") && configured.getSubCompound("BlockEntityTag").getBoolean("TrapdoorCover"),"copied item layout and coverage lost");
        require(configured.getSubCompound("BlockEntityTag").getInteger("TrapdoorCoverFacing")==net.minecraft.util.EnumFacing.WEST.getHorizontalIndex(),"configured item hinge direction lost");
        System.out.println("PASS: trapdoor Fit/Tile, mirrored door columns, surface coverage, atlas bounds and configured/copy settings");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
