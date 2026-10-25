package com.vandorlabs.render;

/** Housing lighting combines sky and block channels independently, without directional shading. */
public final class HousingLightPolicy {
    private HousingLightPolicy(){}
    public static int max(int left,int right){
        return (Math.max(left>>>16,right>>>16)<<16)|Math.max(left&65535,right&65535);
    }
}
