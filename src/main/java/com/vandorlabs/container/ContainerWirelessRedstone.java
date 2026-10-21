package com.vandorlabs.container;
import com.vandorlabs.tiles.TileEntityWirelessRedstone;
public final class ContainerWirelessRedstone extends ContainerRedstoneChannel {
    public final TileEntityWirelessRedstone tile;
    public ContainerWirelessRedstone(TileEntityWirelessRedstone tile){super(tile);this.tile=tile;}
}
