#version 430

// Inputs
in float sphericalVertexDistance; // Fog
in float cylindricalVertexDistance; // Fog
in vec3 vertexNormal; // Vertex normal from vertex shader, in world space
in vec4 vertexColor; // Color multiplier for the vertex
in vec2 albedoUV;
in vec2 lightUV; // UV coordinate in the lighting texture

// Samplers
uniform sampler2D Albedo;
uniform sampler2D LightMap;

// Outputs
out vec4 fragColor;

// Mojang imports
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:light.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <figura:figura_helpers.glsl>

// FIGURA/MAIN

void main() {
    // Calculate main color, quit out if fully transparent.
    vec4 color = texture(Albedo, albedoUV);
    color *= vertexColor;
    if (color.a < (0.5 / 255.0)) discard;
    // Apply normal-based lighting
    color = minecraft_mix_light(Light0_Direction, Light1_Direction, vertexNormal, color);
    // Apply overlay color
    color.rgb = mix(OverlayColor.rgb, color.rgb, OverlayColor.a); // Yes, this mix is backwards. Minecraft's is backwards too. Should I fix?
    // Apply light level-based lighting
    color *= texture(LightMap, figura_convert_light_uv(lightUV));
    // Fog.
    color = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
    // Output
    fragColor = color;
}
