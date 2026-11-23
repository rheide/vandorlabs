package com.vandorlabs.core;

import java.util.Map;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

@IFMLLoadingPlugin.Name("VandorFlight")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.SortingIndex(1100)
@IFMLLoadingPlugin.TransformerExclusions({"com.vandorlabs.core."})
public final class FlightLoadingPlugin implements IFMLLoadingPlugin {
    public String[] getASMTransformerClass() { return new String[]{FlightViewTransformer.class.getName(),CruiseTrackingTransformer.class.getName(),CruiseLayerTransformer.class.getName()}; }
    public String getModContainerClass() { return null; }
    public String getSetupClass() { return null; }
    public String getAccessTransformerClass() { return null; }
    public void injectData(Map<String,Object> data) {}
}
