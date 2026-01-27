package org.figuramc.figura_client.util;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import org.figuramc.figura_client.FiguraClient;
import org.figuramc.figura_client.renderer.submit.FiguraCallbackSubmit;
import org.figuramc.figura_client.textures.AtlasedMinecraftTextureImpl;
import org.figuramc.figura_client.textures.StandaloneMinecraftTextureImpl;
import org.figuramc.figura_client.textures.OwnedMinecraftTextureImpl;
import org.figuramc.figura_core.avatars.Avatar;
import org.figuramc.figura_core.avatars.components.AvatarEvents;
import org.figuramc.figura_core.manage.AvatarView;
import org.figuramc.figura_core.minecraft_interop.texture.MinecraftTexture;
import org.figuramc.figura_core.script_hooks.Event;
import org.figuramc.figura_core.script_hooks.callback.items.CallbackItem;
import org.figuramc.figura_core.script_hooks.callback.items.CallbackView;
import org.figuramc.figura_core.script_hooks.timing.AvatarTimeTracker;
import org.figuramc.figura_core.script_hooks.timing.ProfilingCategory;
import org.figuramc.figura_core.util.data_structures.NullEmptyStack;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Supplier;

public class RenderUtils {

    // Stack of views of currently-submitting avatars.
    public static final NullEmptyStack<AvatarView<?>> AVATAR_SUBMITTING_STACK = new NullEmptyStack<>();
    // Whether said avatar view is for a living entity
    public static final NullEmptyStack<Boolean> IS_LIVING_ENTITY_STACK = new NullEmptyStack<>(); // TODO look for a maybe better way to do this?

    @Contract("null -> null;!null -> !null")
    public static GpuTextureView texToGpuTextureView(@Nullable MinecraftTexture texture) {
        return switch (texture) {
            case StandaloneMinecraftTextureImpl impl -> impl.textureView();
            case AtlasedMinecraftTextureImpl impl -> impl.atlas.getTextureView();
            case OwnedMinecraftTextureImpl ownedImpl -> ownedImpl.getTextureView();
            case null -> null;
            default -> throw new IllegalStateException("Unexpected implementation of MinecraftTexture: " + texture.getClass());
        };
    }

    // Single pixels of various colors to use as placeholders for no texture
    public static final DynamicTexture ZERO_PIXEL = new DynamicTexture(() -> "Zero Pixel", net.minecraft.util.Util.make(() -> {
        NativeImage image = new NativeImage(1, 1, false);
        image.setPixel(0, 0, ARGB.color(0, 0, 0, 0));
        return image;
    }));
    public static final Identifier ZERO_PIXEL_LOC = Util.make(() -> {
        Identifier loc = FiguraClient.locate("zero_pixel");
        Minecraft.getInstance().getTextureManager().register(loc, ZERO_PIXEL);
        return loc;
    });

    public static final DynamicTexture DEFAULT_NORMAL_MAP = new DynamicTexture(() -> "Default Normal Map", Util.make(() -> {
        NativeImage image = new NativeImage(1, 1, false);
        image.setPixel(0, 0, ARGB.color(0, 128, 128, 0));
        return image;
    }));
    public static final Identifier DEFAULT_NORMAL_MAP_LOC = Util.make(() -> {
        Identifier loc = FiguraClient.locate("default_normal_map");
        Minecraft.getInstance().getTextureManager().register(loc, DEFAULT_NORMAL_MAP);
        return loc;
    });

    // Run tasks on the render thread.
    // Errors will complete the CompletableFuture exceptionally,
    // but you shouldn't throw errors here if you can avoid it.
    public static final Queue<Runnable> TASKS = new ConcurrentLinkedDeque<>();
    public static CompletableFuture<Void> runOnRenderThread(Runnable task) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        TASKS.add(() -> {
            try {
                task.run();
                future.complete(null);
            } catch (Throwable anyError) {
                future.completeExceptionally(anyError);
            }
        });
        return future;
    }
    // Anything thrown by the Supplier will complete the future exceptionally
    public static <T> CompletableFuture<T> makeOnRenderThread(Supplier<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        TASKS.add(() -> {
            try {
                future.complete(task.get());
            } catch (Throwable anyError) {
                future.completeExceptionally(anyError);
            }
        });
        return future;
    }


    // Running rendering events, a couple mixins use this
    public static <Args extends CallbackItem> @Nullable FiguraCallbackSubmit invokeRenderEvent(Avatar<?> avatar, ProfilingCategory category, ProfilingCategory callbacksCategory, Event<Args, CallbackItem.Tuple2<CallbackItem.Optional<CallbackView<CallbackItem, CallbackItem.Unit>>, CallbackItem>> renderEvent, Args args) {
        @Nullable AvatarEvents events = avatar.getComponent(AvatarEvents.TYPE);
        if (events == null) return null;
        var eventListener = events.getEventListener(renderEvent);

        // Invoke the event and get some render-thread callbacks
        // Give this 1 second to run by default (TODO: configurable)
        var callbacks = AvatarTimeTracker.getInstance().runTimedFor(avatar, category, 1_000_000_000L, () -> eventListener.invokeToList(args));

        if (callbacks == null || callbacks.isEmpty()) return null;
        AvatarView<?> view = new AvatarView<>(avatar);
        return () -> view.use(renderThreadAvatar -> {
            // Run all the callbacks for this avatar.
            // Give this 1 second to run by default (TODO: configurable)
            AvatarTimeTracker.getInstance().runTimed(renderThreadAvatar, callbacksCategory, 1_000_000_000L, () -> {
                for (var callback : callbacks) {
                    var funcView = callback.a().value();
                    if (funcView == null) continue;
                    var data = callback.b();
                    var func = funcView.getValue();
                    if (func == null) continue; // Skip if it was revoked (I don't think it *can* be revoked? But we'll check anyway)
                    func.call(data);
                }
            });
        });
    }


}
