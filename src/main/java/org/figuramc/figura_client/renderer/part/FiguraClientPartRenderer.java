package org.figuramc.figura_client.renderer.part;

import net.minecraft.client.renderer.MultiBufferSource;
import org.figuramc.figura_core.avatars.errors.AvatarError;
import org.figuramc.figura_core.minecraft_interop.render.PartRenderer;
import org.figuramc.figura_core.model.rendering.RenderingRoot;
import org.figuramc.figura_core.util.data_structures.FiguraTransformStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Rendering state connected to a RenderingRoot.
 */
public abstract class FiguraClientPartRenderer extends PartRenderer {

    public FiguraClientPartRenderer(RenderingRoot<?> root) {
        super(root);
    }

    /**
     * Render method, drawing the root from the given info.
     */
    public abstract void render(MultiBufferSource bufferSource, Matrix4f transform, int light, int overlay) throws AvatarError;

}
