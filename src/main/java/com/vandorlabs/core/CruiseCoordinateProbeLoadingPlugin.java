package com.vandorlabs.core;

import java.util.Map;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

/** Installed only in the explicitly requested coordinate experiment JAR. */
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.SortingIndex(1001)
@IFMLLoadingPlugin.TransformerExclusions("com.vandorlabs.core.")
public final class CruiseCoordinateProbeLoadingPlugin implements IFMLLoadingPlugin {
    @Override public String[] getASMTransformerClass() {
        return "projection".equals(System.getProperty("vandorlabs.cruiseCoordinateProbe"))
            ? new String[]{"com.vandorlabs.core.CruiseCoordinateProbeTransformer"} : new String[0];
    }
    @Override public String getModContainerClass() { return null; }
    @Override public String getSetupClass() { return null; }
    @Override public void injectData(Map<String,Object> data) {}
    @Override public String getAccessTransformerClass() { return null; }
}
