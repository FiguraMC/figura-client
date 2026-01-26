package org.figuramc.figura_client.text;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.SnbtGrammar;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.*;
import net.minecraft.util.parsing.packrat.commands.CommandArgumentParser;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.figuramc.figura_client.FiguraClient;
import org.figuramc.figura_client.ducks.StyleAccess;
import org.figuramc.figura_core.minecraft_interop.ClientTranslatables;
import org.figuramc.figura_core.minecraft_interop.ConsoleOutput;
import org.figuramc.figura_core.text.FormattedText;
import org.figuramc.figura_core.util.exception.FiguraException;
import org.figuramc.figura_translations.Language;
import org.figuramc.figura_translations.TranslatableItems;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class ConsoleOutputImpl implements ConsoleOutput {
    private static final CommandArgumentParser<Tag> TAG_PARSER = SnbtGrammar.createParser(NbtOps.INSTANCE);
    // TODO: Use game language, for all of these
    private static final Component MISSING_ENTITY = Component.literal(ClientTranslatables.LOG_MISSING_ENTITY.translate(Language.EN_US, TranslatableItems.Items0.INSTANCE)).withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY));
    private static final Component NO_SOURCE = Component.literal(ClientTranslatables.LOG_NO_SOURCE.translate(Language.EN_US, TranslatableItems.Items0.INSTANCE)).withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY));
    private static final Component UNKNOWN_SOURCE = Component.literal(ClientTranslatables.LOG_UNKNOWN_SOURCE.translate(Language.EN_US, TranslatableItems.Items0.INSTANCE)).withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY));

    @Override
    public void logSimple(@Nullable Object source, String message) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            Component sourceComponent = getSourceComponent(source);
            MutableComponent root = Component.empty();
            // TODO: decide whether to use custom [lua] color from 0.1.x
            root.append(Component.literal("[lua] ").withStyle(ChatFormatting.BLUE));
            root.append(sourceComponent);
            root.append(Component.literal(" : ").withStyle(ChatFormatting.BLUE));
            root.append(message);
            // defer this or else get a render crash
            Minecraft.getInstance().execute(() -> player.displayClientMessage(root, false));
        }
    }

    @Override
    public void logFormatted(FormattedText text) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            MutableComponent component = Component.literal(new String(text.codepoints, 0, text.codepoints.length));
            Style figuraStyle = new Style(null, null, null, null, null, null, null, null, null, null, null);
            ((StyleAccess) figuraStyle).figura_client$setFiguraStyle(text.style);
            component.withStyle(figuraStyle);
            Minecraft.getInstance().execute(() -> player.displayClientMessage(component, false));
        }
    }

    /**
     * tellraw/sNBT
     * equivalent of printJson on 0.1.x
     */
    @Override
    public void logNativeFormatted(String formatted) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            Component text;
            try {
                Tag t = TAG_PARSER.parseForCommands(new StringReader(formatted));
                DataResult<Pair<Component, Tag>> decode = ComponentSerialization.CODEC.decode(NbtOps.INSTANCE, t);
                text = decode.getOrThrow().getFirst();
            } catch (CommandSyntaxException | IllegalStateException ignored) {
                // fallback to just printing the raw text
                text = Component.literal(formatted);
            }
            final Component finalText = text; // lambda needs this to bind correctly
            Minecraft.getInstance().execute(() -> player.displayClientMessage(finalText, false));
        }
    }

    @Override
    public void logVerbose(@Nullable Object source, String message) {
        String sourceString = getSourceString(source);
        FiguraClient.LOGGER.info("[Lua] {}: {}\n", sourceString, message);
    }

    @Override
    public void reportError(FiguraException e) {
        FiguraClient.LOGGER.error("Figura Exception occurred:", e);
    }

    @Override
    public void reportUnexpectedError(Throwable throwable) {
        FiguraClient.LOGGER.error("Unexpected internal Figura error! Please report to devs!", throwable);
    }

    private static String getSourceString(@Nullable Object source) {
        if (source == null) return "No Source";
        if (source instanceof UUID uuid) {
            Entity e = getEntity(uuid);
            return e == null ? "Missing Entity" : e.getName().getString();
        }
        if (source instanceof String s) return s;
        return "Unknown Source";
    }

    private static Component getSourceComponent(@Nullable Object source) {
        if (source == null) return NO_SOURCE;
        if (source instanceof UUID uuid) return getEntityNameComponent(uuid);
        if (source instanceof String s) return Component.literal(s);
        return UNKNOWN_SOURCE;
    }

    private static Component getEntityNameComponent(@Nullable UUID source) {
        Entity entity = getEntity(source);
        MutableComponent text;
        if (entity != null) text = entity.getName().copy().withStyle(Style.EMPTY.withHoverEvent(
                new HoverEvent.ShowEntity(new HoverEvent.EntityTooltipInfo(entity.getType(), source, entity.getName()))
        ));
        else {
            if (source != null)
                text = MISSING_ENTITY.copy().withStyle(Style.EMPTY.withHoverEvent(
                        new HoverEvent.ShowText(Component.literal(source + " ?"))
                ));
            else
                text = MISSING_ENTITY.copy().withStyle(Style.EMPTY.withHoverEvent(
                        new HoverEvent.ShowText(Component.literal(
                                // TODO: use game language
                                ClientTranslatables.LOG_NO_SOURCE.translate(
                                        Language.EN_US,
                                        TranslatableItems.Items0.INSTANCE
                                )
                        ))
                ));
        }

        return text;
    }

    private static @Nullable Entity getEntity(@Nullable UUID source) {
        if (source == null) return null;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return null;
        Player maybePlayer = level.getPlayerByUUID(source);
        if (maybePlayer != null) {
            return maybePlayer;
        }
        return level.getEntity(source);
    }


}
