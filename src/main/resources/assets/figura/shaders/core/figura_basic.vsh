#version 430

// Inputs
in vec3 Position;
in vec4 RiggingWeights;
in ivec4 RiggingIndices;
in vec2 UV;
in vec3 Normal;
in vec3 Tangent;

// Outputs
out float sphericalVertexDistance; // Fog
out float cylindricalVertexDistance; // Fog
out vec4 vertexColor; // Color multiplier for the vertex
out vec2 uv; // Passthrough UV value. Will be converted in the fragment shader
out vec2 lightUV; // UV coordinate in the lighting texture
out vec3 light0; // Direction towards Light0 in tangent space
out vec3 light1; // Direction towards Light1 in tangent space

// FOG
float fog_spherical_distance(vec3 pos) {
    return length(pos);
}
float fog_cylindrical_distance(vec3 pos) {
    float distXZ = length(pos.xz);
    float distY = abs(pos.y);
    return max(distXZ, distY);
}

// UNIFORMS
// FiguraUniforms is our custom one. The others are provided by MC for MC's functions.
layout(std140) uniform Lighting {
    vec3 Light0_Direction;
    vec3 Light1_Direction;
};
layout(std140) uniform Fog {
    vec4 FogColor;
    float FogEnvironmentalStart;
    float FogEnvironmentalEnd;
    float FogRenderDistanceStart;
    float FogRenderDistanceEnd;
    float FogSkyEnd;
    float FogCloudsEnd;
};
layout(std140) uniform Projection {
    mat4 ProjMat; // Convert from View space -> NDC
};

// Size = 224 bytes
layout(std140) uniform FiguraUniforms {
    // Matrices
    mat4 CamRelWorldMat; // Convert from Model space -> Camera-relative World space
    mat4 ViewMat; // Convert from Camera-relative World space -> View space
    // General uniforms
    vec4 OverlayColor; // Color of "overlay" value. Sent directly instead of being sampled.
    // UV modifiers for builtin textures
    vec4 Main_uvModifier;
    vec4 NormalMap_uvModifier;
    vec4 SpecularMap_uvModifier;
    vec4 Lightmap_uvModifier;
    // Screen size and game time, useful for custom fun shader effects
    vec2 ScreenSize;
    float GameTime;
    // 4 bytes padding
};

// FIGURA/MAIN

// Size = 144, align = 16
struct PartData {
    mat4 transform; // 64 bytes
    mat3 normalMat; // 48 bytes
    vec4 color; // 16 bytes
    vec2 light; // 8 bytes UV into the light texture
    // 8 bytes padding
};

layout (binding = 0, std140) readonly buffer PartDataBuffer {
    PartData[] parts;
};

// Default definition:
// #define FIGURA_HOOKS \
// void FIGURA_PART_SPACE_HOOK(inout vec3 pos, inout vec3 normal, inout vec3 tangent) {} \
// void FIGURA_MODEL_SPACE_HOOK(inout vec4 pos, inout vec3 normal, inout vec3 tangent, inout vec4 color, inout vec2 lightUV) {}

FIGURA_HOOKS

// Takes in values in part space, outputs values in model space
void figura_compute_weights(
    in vec3 Position, in vec4 RiggingWeights, in ivec4 RiggingIndices, in vec3 Normal, in vec3 Tangent,
    out vec4 pos, out vec3 normal, out vec3 tangent, out vec4 color, out vec2 lightUV
) {
    // Process inputs in part space
    FIGURA_PART_SPACE_HOOK(Position, Normal, Tangent);
    // Apply transforms
    pos = vec4(0.0);
    normal = vec3(0.0);
    tangent = vec3(0.0);
    color = vec4(0.0);
    lightUV = vec2(0.0);
    if (RiggingIndices.x != 65535) {
        PartData part = parts[RiggingIndices.x];
        pos += RiggingWeights.x * part.transform * vec4(Position, 1.0);
        normal += RiggingWeights.x * part.normalMat * Normal;
        tangent += RiggingWeights.x * mat3(part.transform) * Tangent;
        color += RiggingWeights.x * part.color;
        lightUV += RiggingWeights.x * part.light;
    }
    if (RiggingIndices.y != 65535) {
        PartData part = parts[RiggingIndices.y];
        pos += RiggingWeights.y * part.transform * vec4(Position, 1.0);
        normal += RiggingWeights.y * part.normalMat * Normal;
        tangent += RiggingWeights.y * mat3(part.transform) * Tangent;
        color += RiggingWeights.y * part.color;
        lightUV += RiggingWeights.y * part.light;
    }
    if (RiggingIndices.z != 65535) {
        PartData part = parts[RiggingIndices.z];
        pos += RiggingWeights.z * part.transform * vec4(Position, 1.0);
        normal += RiggingWeights.z * part.normalMat * Normal;
        tangent += RiggingWeights.z * mat3(part.transform) * Tangent;
        color += RiggingWeights.z * part.color;
        lightUV += RiggingWeights.z * part.light;
    }
    if (RiggingIndices.w != 65535) {
        PartData part = parts[RiggingIndices.w];
        pos += RiggingWeights.w * part.transform * vec4(Position, 1.0);
        normal += RiggingWeights.w * part.normalMat * Normal;
        tangent += RiggingWeights.w * mat3(part.transform) * Tangent;
        color += RiggingWeights.w * part.color;
        lightUV += RiggingWeights.w * part.light;
    }
    normal = normalize(normal);
    tangent = normalize(tangent);
    // Process outputs in model space
    FIGURA_MODEL_SPACE_HOOK(pos, normal, tangent, color, lightUV);
}

void main() {
    // Convert pos/normal/tangent from part space -> model space
    vec4 pos; vec3 normal; vec3 tangent;
    figura_compute_weights(Position, RiggingWeights, RiggingIndices, Normal, Tangent, pos, normal, tangent, vertexColor, lightUV);
    // Convert values to camera-relative world space
    pos = CamRelWorldMat * pos;
    normal = mat3(CamRelWorldMat) * normal; // TODO consider using a separate normal matrix for this? Will CamRelWorldMat ever not be just a translation+rotation?
    tangent = mat3(CamRelWorldMat) * tangent;
    // Regular TBN converts tangent space -> world space.
    // Get TBN inverse matrix, which converts world space to tangent space, then apply it to light direction uniforms which are passed in world space.
    mat3 TBNInverse = transpose(mat3(tangent, cross(tangent, normal), normal));
    light0 = TBNInverse * Light0_Direction;
    light1 = TBNInverse * Light1_Direction;
    // Get fog
    sphericalVertexDistance = fog_spherical_distance(pos.xyz);
    cylindricalVertexDistance = fog_cylindrical_distance(pos.xyz);
    // Get UV
    uv = UV;
    // Convert position using all the matrices.
    // NDC <- View Space <- Camera-relative World Space
    gl_Position = ProjMat * ViewMat * pos;
}

