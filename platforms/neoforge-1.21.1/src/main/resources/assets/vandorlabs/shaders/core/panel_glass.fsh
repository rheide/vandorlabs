#version 150
#moj_import <fog.glsl>
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
out vec4 fragColor;
void main() {
    vec4 pane = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    // Matches the existing panel renderer's GL alpha-test boundary.
    if (pane.a <= 0.003) discard;
    fragColor = linear_fog(pane, vertexDistance, FogStart, FogEnd, FogColor);
}
