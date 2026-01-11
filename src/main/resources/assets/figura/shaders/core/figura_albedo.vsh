#version 430

// Inputs
in vec3 Position;
in vec4 RiggingWeights;
in ivec4 RiggingIndices;
in vec2 UV0;
in vec3 Normal;

// Outputs
out float sphericalVertexDistance; // Fog
out float cylindricalVertexDistance; // Fog
out vec3 vertexNormal; // Normal in world space
out vec4 vertexColor; // Color multiplier for the vertex
out vec2 albedoUV;
out vec2 lightUV; // UV coordinate in the lighting texture

// Mojang imports
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:light.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <figura:figura_helpers.glsl>

void main() {
    // Convert pos/normal from part space -> camera-relative world space
    vec4 pos; vec3 normal; vec3 _unused = vec3(0);
    figura_apply_transforms(Position, RiggingWeights, RiggingIndices, Normal, _unused, pos, vertexNormal, _unused, vertexColor, lightUV);
    // Get fog
    sphericalVertexDistance = fog_spherical_distance(pos.xyz);
    cylindricalVertexDistance = fog_cylindrical_distance(pos.xyz);
    // Output UV
    albedoUV = UV0;
    // Convert position using all the matrices.
    // NDC <- View Space <- Camera-relative World Space
    gl_Position = ProjMat * ViewMat * pos;
}

