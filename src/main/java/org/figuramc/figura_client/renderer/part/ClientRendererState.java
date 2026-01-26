package org.figuramc.figura_client.renderer.part;

import org.joml.Matrix4f;

/**
 * State object passed through model part rendering which leads to OptimizedRenderer.
 * Contains additional drawing info which is not conveyed through the normal FiguraTransformStack arg.
 */
public record ClientRendererState(Matrix4f rootAndViewMatrix, int overlay) {

}
