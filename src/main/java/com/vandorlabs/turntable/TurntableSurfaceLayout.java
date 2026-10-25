package com.vandorlabs.turntable;

import com.vandorlabs.surface.SquareLayout;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.nbt.NBTTagCompound;
import java.util.Collection;

/** Legacy turntable API and saved layout shape, backed by shared square discovery. */
public final class TurntableSurfaceLayout extends SquareLayout {
    private TurntableSurfaceLayout(Collection<BlockPos> cells,EnumFacing normal){super(cells,normal);}
    @Override public TurntableSurfaceLayout reversed(){return new TurntableSurfaceLayout(cells,normal.getOpposite());}
    public static TurntableSurfaceLayout scan(World world,BlockPos seed){SquareLayout found=SquareLayout.scan(world,seed);return new TurntableSurfaceLayout(found.cells,found.normal);}
    public static TurntableSurfaceLayout read(NBTTagCompound tag){SquareLayout saved=SquareLayout.read(tag);return new TurntableSurfaceLayout(saved.cells,saved.normal);}
}
