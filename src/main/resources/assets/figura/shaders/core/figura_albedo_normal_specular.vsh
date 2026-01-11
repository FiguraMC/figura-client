#version 430

// Inputs
in vec3 Position;
in vec4 RiggingWeights;
in ivec4 RiggingIndices;
in vec2 UV0;
in vec2 UV1;
in vec2 UV2;
in vec3 Normal;
in vec3 Tangent;

// Outputs
out float sphericalVertexDistance; // Fog
out float cylindricalVertexDistance; // Fog
out vec4 vertexColor; // Color multiplier for the vertex
out vec2 albedoUV;
out vec2 normalUV;
out vec2 specularUV;
out vec2 lightUV; // UV coordinate in the lighting texture
out vec3 light0; // Direction towards Light0 in tangent space
out vec3 light1; // Direction towards Light1 in tangent space

// Mojang imports
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:light.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <figura:figura_helpers.glsl>

void main() {
    // Convert pos/normal/tangent from part space -> camera-relative world space
    vec4 pos; vec3 normal; vec3 tangent;
    figura_apply_transforms(Position, RiggingWeights, RiggingIndices, Normal, Tangent, pos, normal, tangent, vertexColor, lightUV);
    // Find light directions converted to tangent space
    mat3 TBNInverse = figura_get_tbn_inverse_matrix(normal, tangent);
    light0 = TBNInverse * Light0_Direction;
    light1 = TBNInverse * Light1_Direction;
    // Get fog
    sphericalVertexDistance = fog_spherical_distance(pos.xyz);
    cylindricalVertexDistance = fog_cylindrical_distance(pos.xyz);
    // Output UV
    albedoUV = UV0;
    normalUV = UV1;
    specularUV = UV2;
    // Convert position using all the matrices.
    // NDC <- View Space <- Camera-relative World Space
    gl_Position = ProjMat * ViewMat * pos;
}

