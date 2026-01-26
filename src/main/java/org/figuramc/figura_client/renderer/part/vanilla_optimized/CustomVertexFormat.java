package org.figuramc.figura_client.renderer.part.vanilla_optimized;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import org.figuramc.figura_core.model.rendering.vertex.FiguraVertexElem;
import org.figuramc.figura_core.model.rendering.vertex.FiguraVertexFormat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// We need this in order to use any of Mojang's rendering abstractions... :/
// This is ONLY meant to be used in our own custom rendering.
// Mod compat is not a concern; if we care about mod compat then use CompatibleRenderer
public class CustomVertexFormat extends VertexFormat {

    private final FiguraVertexFormat figuraVertexFormat;

    public CustomVertexFormat(FiguraVertexFormat figuraVertexFormat) {
        super(
                createCustomElements(figuraVertexFormat),
                Arrays.asList(figuraVertexFormat.names),
                null, // Should never index this list
                figuraVertexFormat.vertexSize
        );
        this.figuraVertexFormat = figuraVertexFormat;
    }

    // Override some methods to "ensure" "correct" usage
    @Override
    public int[] getOffsetsByElement() {
        throw new UnsupportedOperationException("Not supported by Figura CustomVertexElement");
    }

    @Override
    public int getOffset(VertexFormatElement vertexFormatElement) {
        for (int i = 0; i < getElements().size(); i++)
            if (getElements().get(i) == vertexFormatElement) // Specifically use == instead of indexOf to avoid record comparisons...
                return figuraVertexFormat.offsets[i];
        return -1;
    }

    @Override
    public boolean contains(VertexFormatElement vertexFormatElement) {
        throw new UnsupportedOperationException("Not supported by Figura CustomVertexElement");
    }

    // Override equals and hashcode
    @Override
    public boolean equals(Object object) {
        return object == this ||
                object instanceof CustomVertexFormat customVertexFormat
                && figuraVertexFormat.equals(customVertexFormat.figuraVertexFormat);
    }

    @Override
    public int hashCode() {
        return figuraVertexFormat.hashCode();
    }

    // Create custom elements corresponding with the figura elements.
    // These custom elements should never be used outside Figura.
    private static List<VertexFormatElement> createCustomElements(FiguraVertexFormat figuraVertexFormat) {
        List<VertexFormatElement> out = new ArrayList<>();
        for (int i = 0; i < figuraVertexFormat.elements.length; i++) {
            FiguraVertexElem figuraElem = figuraVertexFormat.elements[i];
            // Type used to represent it in the vertex buffer
            VertexFormatElement.Type type = switch (figuraElem.type) {
                case FLOAT32, FLOAT32_2, FLOAT32_3, FLOAT32_4 -> VertexFormatElement.Type.FLOAT;
                case UFLOAT8, UFLOAT8_2, UFLOAT8_3, UFLOAT8_4 -> VertexFormatElement.Type.UBYTE;
                case SFLOAT8, SFLOAT8_2, SFLOAT8_3, SFLOAT8_4 -> VertexFormatElement.Type.BYTE;
                case UINT16, UINT16_2, UINT16_3, UINT16_4 -> VertexFormatElement.Type.USHORT;
            };
            // "Normal" to normalize, "Generic" to not normalize
            VertexFormatElement.Usage usage = switch (figuraElem.type) {
                case FLOAT32, FLOAT32_2, FLOAT32_3, FLOAT32_4 -> VertexFormatElement.Usage.GENERIC;
                case UFLOAT8, UFLOAT8_2, UFLOAT8_3, UFLOAT8_4 -> VertexFormatElement.Usage.NORMAL;
                case SFLOAT8, SFLOAT8_2, SFLOAT8_3, SFLOAT8_4 -> VertexFormatElement.Usage.NORMAL;
                case UINT16, UINT16_2, UINT16_3, UINT16_4 -> VertexFormatElement.Usage.GENERIC;
            };
            out.add(new VertexFormatElement(30, 0, type, usage, figuraElem.type.count));
        }
        return out;
    }

}
