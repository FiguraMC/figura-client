package org.figuramc.figura_client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import org.figuramc.figura_client.game_data.GameDataProviderImpl;
import org.figuramc.figura_client.game_data.MinecraftEntityImpl;
import org.figuramc.figura_client.renderer.part.vanilla_optimized.OptimizedRenderer;
import org.figuramc.figura_client.text.ConsoleOutputImpl;
import org.figuramc.figura_client.text.GlyphProviderImpl;
import org.figuramc.figura_client.textures.TextureProviderImpl;
import org.figuramc.figura_core.avatars.AvatarModules;
import org.figuramc.figura_core.avatars.AvatarTemplates;
import org.figuramc.figura_core.avatars.components.AvatarProfiling;
import org.figuramc.figura_core.data.importer.v1.ModuleImporter;
import org.figuramc.figura_core.data.materials.ModuleMaterials;
import org.figuramc.figura_core.manage.AvatarManagers;
import org.figuramc.figura_core.manage.AvatarView;
import org.figuramc.figura_core.minecraft_interop.FiguraConnectionPoint;
import org.figuramc.figura_core.minecraft_interop.ItemRenderContext;
import org.figuramc.figura_core.minecraft_interop.game_data.MinecraftIdentifier;
import org.figuramc.figura_core.minecraft_interop.vanilla_parts.VanillaModel;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.UUID;
import java.util.stream.Collectors;

public class FiguraClient implements ClientModInitializer {

	// Maps for important objects
	public static final EnumMap<ItemDisplayContext, ItemRenderContext> RENDER_CONTEXTS = new EnumMap<>(ItemDisplayContext.class);
	static {
		RENDER_CONTEXTS.put(ItemDisplayContext.NONE, new ItemRenderContext("none", false, false, null));
		RENDER_CONTEXTS.put(ItemDisplayContext.THIRD_PERSON_LEFT_HAND, new ItemRenderContext("thirdperson_lefthand", true, false, RENDER_CONTEXTS.get(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)));
		RENDER_CONTEXTS.put(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, new ItemRenderContext("thirdperson_righthand", false, false, null));
		RENDER_CONTEXTS.put(ItemDisplayContext.FIRST_PERSON_LEFT_HAND, new ItemRenderContext("firstperson_lefthand", true, false, RENDER_CONTEXTS.get(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND)));
		RENDER_CONTEXTS.put(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, new ItemRenderContext("firstperson_righthand", false, false, null));
		RENDER_CONTEXTS.put(ItemDisplayContext.HEAD, new ItemRenderContext("head", false, false, null));
		RENDER_CONTEXTS.put(ItemDisplayContext.GUI, new ItemRenderContext("gui", false, true, null));
		RENDER_CONTEXTS.put(ItemDisplayContext.GROUND, new ItemRenderContext("ground", false, true, null));
		RENDER_CONTEXTS.put(ItemDisplayContext.FIXED, new ItemRenderContext("fixed", false, true, null));
		RENDER_CONTEXTS.put(ItemDisplayContext.ON_SHELF, new ItemRenderContext("on_shelf", false, false, null));
	}

	// Mod ID
	public static final String MOD_ID = "figura";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier locate(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static MinecraftIdentifier UNKNOWN = new MinecraftIdentifier("minecraft", "unknown");
	public static MinecraftIdentifier coreIdent(Identifier minecraft) {
		return new MinecraftIdentifier(minecraft.getNamespace(), minecraft.getPath());
	}

	@Override
	public void onInitializeClient() {
		// Initialize Figura connection point
		FiguraConnectionPoint.TEXTURE_PROVIDER = new TextureProviderImpl();
		FiguraConnectionPoint.GLYPH_PROVIDER = new GlyphProviderImpl();
		FiguraConnectionPoint.PART_RENDERER_FACTORY = OptimizedRenderer::new;
		FiguraConnectionPoint.GAME_DATA_PROVIDER = new GameDataProviderImpl();
		FiguraConnectionPoint.CONSOLE_OUTPUT = new ConsoleOutputImpl();
		FiguraConnectionPoint.PATH_PROVIDER = new PathProviderImpl();
		FiguraConnectionPoint.finishInit();

		KeyMapping.Category category = KeyMapping.Category.register(locate("debug"));
		// This is just for debug testing! We'll move away from using fabric api at a later time.
		KeyMapping debugLoadAvatar = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.figura.debug_load_avatar",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_0,
				category
		));
		KeyMapping printProfiling = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.figura.print_profiling_info",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_P,
				category
		));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (debugLoadAvatar.consumeClick()) {
				client.player.displayClientMessage(Component.literal("Opening avatar selection dialog..."), false);
				File avatarsFolder = FiguraConnectionPoint.PATH_PROVIDER.getAvatarsFolder().exceptionally(x -> null).join();
				if (avatarsFolder == null) {
					client.player.displayClientMessage(Component.literal("No figura directory has been chosen. Cancelling."), false);
					return;
				}
				String defaultPath = avatarsFolder.toPath().toString();
				@Nullable String pathString = TinyFileDialogs.tinyfd_selectFolderDialog("Choose an avatar folder to load", defaultPath);
				if (pathString == null) {
					client.player.displayClientMessage(Component.literal("No avatar folder selected. Cancelling."), false);
					return;
				}
				File avatarFile = Path.of(pathString).toFile();
				// Load the avatar
				client.player.displayClientMessage(Component.literal("Loading avatar at " + pathString), false);
				AvatarManagers.ENTITIES.load(client.player.getUUID(), () -> {
					ModuleMaterials materials = ModuleImporter.importFromFile(avatarFile);
					AvatarModules modules = AvatarModules.loadModules(materials);
					VanillaModel vanillaModel = new MinecraftEntityImpl(client.player).getModel();
					return AvatarTemplates.localPlayer(modules, vanillaModel);
				});
			}

			while (printProfiling.consumeClick()) {
				AvatarView<UUID> avatar = AvatarManagers.ENTITIES.get(FiguraConnectionPoint.GAME_DATA_PROVIDER.getLocalUUID());
				if (avatar == null) FiguraConnectionPoint.CONSOLE_OUTPUT.logSimple(null, "No avatar in use");
				String[] out = new String[] { "No message" };
				avatar.use(avi -> {
					AvatarProfiling profiling = avi.getComponent(AvatarProfiling.TYPE);
					if (profiling == null) {
						out[0] = "Avatar doesn't have profiling enabled";
						return;
					}
					out[0] = profiling.measurers.entrySet().stream().map(
							entry -> entry.getKey().fullName + ": " + entry.getValue().runningAverage() + " nanos average")
							.collect(Collectors.joining("\n"));
				});
				FiguraConnectionPoint.CONSOLE_OUTPUT.logSimple(null, out[0]);
			}

		});

	}
}