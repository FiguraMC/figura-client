#version 430

// Inputs
in float sphericalVertexDistance; // Fog
in float cylindricalVertexDistance; // Fog
in vec4 vertexColor; // Color multiplier for the vertex
in vec2 albedoUV;
in vec2 specularUV;
in vec2 lightUV; // UV coordinate in the lighting texture
in vec3 light0; // Direction towards Light0 in tangent space
in vec3 light1; // Direction towards Light1 in tangent space

// Samplers
uniform sampler2D Albedo;
uniform sampler2D Specular;
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
    // Check emissivity
    vec4 specularSample = texture(Specular, specularUV);
    float emissivity = specularSample.a; // LabPBR spec stores emissivity in the alpha channel of specular map
    // Apply normal-based lighting only if emissivity isn't 1
    if (emissivity < 1.0) {
        color = mix(minecraft_mix_light(normalize(light0), normalize(light1), vec3(0, 0, 1), color), color, emissivity);
    }
    // Apply overlay color
    color.rgb = mix(OverlayColor.rgb, color.rgb, OverlayColor.a); // Yes, this mix is backwards. Minecraft's is backwards too. Should I fix?
    // Apply light level-based lighting
    vec3 lightMapColor = texture(LightMap, figura_convert_light_uv(lightUV)).rgb;
    lightMapColor = max(lightMapColor, emissivity);
    color.rgb *= lightMapColor;
    // Fog.
    color = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
    // Output
    fragColor = color;
}
