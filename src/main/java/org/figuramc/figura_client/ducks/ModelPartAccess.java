package org.figuramc.figura_client.ducks;

import org.figuramc.figura_core.minecraft_interop.vanilla_parts.VanillaPart;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

// Accessor to grab custom fields in ModelPartMixin
public interface ModelPartAccess {

    // Get/set the VanillaPart which corresponds to this ModelPart
    @Nullable VanillaPart figura_client$getVanillaPart();
    void figura_client$setVanillaPart(@Nullable VanillaPart part);

    // Enable/disable figura modifications to the model part
    void figura_client$setEnabled(boolean figuraEnabled);

    // Get/set figura variables
    void figura_client$setVisible(boolean figuraVisible);
    Vector3f figura_client$getOrigin();
    Vector3f figura_client$getRotation();
    Vector3f figura_client$getScale();
    Vector3f figura_client$getPosition();

}
