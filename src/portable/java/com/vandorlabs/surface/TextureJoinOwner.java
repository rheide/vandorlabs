package com.vandorlabs.surface;

/** A host's texture-join policy; adapters retain lifecycle and authorization. */
public interface TextureJoinOwner {
    TextureJoinSettings textureJoin();
    void setTextureJoin(TextureJoinSettings settings);
}
