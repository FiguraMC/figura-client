
// Includes code for common Figura shader operations.
// Uses certain #defines:
// - NORMAL_MAP if using normal mapping
// - SPECULAR_MAP if using specular mapping

// Size = 160 bytes
layout(std140) uniform FiguraUniforms {
    // Matrices
    mat4 CamRelWorldMat; // Convert from Model space -> Camera-relative World space
    mat4 ViewMat; // Convert from Camera-relative World space -> View space
    // General uniforms
    vec4 OverlayColor; // The color of the "overlay" value. Sent directly instead of being sampled.
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
    vec2 light; // 8 bytes UV into the light texture (could be shrunk a bit, really just 4 bits each block/sky light)
    // 8 bytes padding
};

layout (binding = 0, std140) readonly buffer PartDataBuffer {
    PartData[] parts;
};

// Compute the TBN inverse matrix given tangent/normal in world space
// Regular TBN converts tangent space -> world space.
// Get TBN inverse matrix, which converts world space to tangent space.
// Apply this matrix to Light0_Direction and Light1_Direction to find the directions to the lights in tangent space.
mat3 figura_get_tbn_inverse_matrix(vec3 normal, vec3 tangent) {
    return transpose(mat3(tangent, cross(tangent, normal), normal));
}

vec2 figura_convert_light_uv(vec2 lightUV) {
    return lightUV * (15.0 / 16.0) + (0.5 / 16.0);
}

// Default definition of hooks
// Always includes tangent vectors, we'll just pass a worthless vector if they aren't present
#ifndef FIGURA_HOOKS
#define FIGURA_HOOKS \
void FIGURA_PART_SPACE_HOOK(inout vec3 pos, inout vec3 normal, inout vec3 tangent) {} \
void FIGURA_MODEL_SPACE_HOOK(inout vec4 pos, inout vec3 normal, inout vec3 tangent, inout vec4 color, inout vec2 lightUV) {}
#endif

FIGURA_HOOKS

// Takes in pos/normal/tangent in part space, applies hooks
// Converts pos/normal/tangent to model space and computes color/light, applies hooks
// Converts pos/normal/tangent to camera-relative world space, applies hooks
void figura_apply_transforms(
    // Inputs
    in vec3 Position, in vec4 RiggingWeights, in ivec4 RiggingIndices, in vec3 Normal, in vec3 Tangent,
    // Outputs
    out vec4 pos, out vec3 normal, out vec3 tangent, out vec4 color, out vec2 lightUV
) {
    // Process inputs in part space
    FIGURA_PART_SPACE_HOOK(Position, Normal, Tangent);
    // Apply transforms by accumulating using weights into zeroes
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

    // Process values in model space
    FIGURA_MODEL_SPACE_HOOK(pos, normal, tangent, color, lightUV);

    // Convert to world space
    pos = CamRelWorldMat * pos;
    normal = mat3(CamRelWorldMat) * normal; // TODO consider using a separate normal matrix for this? Will CamRelWorldMat ever not be just a translation+rotation?
    tangent = mat3(CamRelWorldMat) * tangent;

    // TODO world space hook

}