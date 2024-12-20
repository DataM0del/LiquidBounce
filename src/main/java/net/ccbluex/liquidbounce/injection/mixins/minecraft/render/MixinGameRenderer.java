/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2024 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.injection.mixins.minecraft.render;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderSystem;
import net.ccbluex.liquidbounce.event.EventManager;
import net.ccbluex.liquidbounce.event.events.GameRenderEvent;
import net.ccbluex.liquidbounce.event.events.PerspectiveEvent;
import net.ccbluex.liquidbounce.event.events.ScreenRenderEvent;
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent;
import net.ccbluex.liquidbounce.features.module.modules.combat.aimbot.ModuleDroneControl;
import net.ccbluex.liquidbounce.features.module.modules.fun.ModuleDankBobbing;
import net.ccbluex.liquidbounce.features.module.modules.render.*;
import net.ccbluex.liquidbounce.features.module.modules.world.ModuleLiquidPlace;
import net.ccbluex.liquidbounce.interfaces.LightmapTextureManagerAddition;
import net.ccbluex.liquidbounce.interfaces.PostEffectPassTextureAddition;
import net.ccbluex.liquidbounce.render.engine.UIRenderer;
import net.ccbluex.liquidbounce.render.shader.shaders.OutlineEffectShader;
import net.ccbluex.liquidbounce.utils.aiming.RaytracingExtensionsKt;
import net.ccbluex.liquidbounce.utils.aiming.Rotation;
import net.ccbluex.liquidbounce.utils.aiming.RotationManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.*;
import net.minecraft.client.util.Pool;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {

    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    public abstract MinecraftClient getClient();

    /**
     * UI Blur Post-Effect Processor
     *
     * @author superblaubeere27
     */
    @Final
    @Unique
    private final Identifier liquid_bounce$BLUR = Identifier.of("liquidbounce", "ui_blur");

    @Shadow
    @Final
    private Camera camera;

    @Shadow
    public abstract void tick();

    @Shadow
    @Final
    private LightmapTextureManager lightmapTextureManager;

    @Shadow
    @Final
    private Pool pool;

    /**
     * Hook game render event
     */
    @Inject(method = "render", at = @At("HEAD"))
    public void hookGameRender(CallbackInfo callbackInfo) {
        EventManager.INSTANCE.callEvent(new GameRenderEvent());
    }

    /**
     * We change crossHairTarget according to server side rotations
     */
    @ModifyExpressionValue(method = "findCrosshairTarget", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;raycast(DFZ)Lnet/minecraft/util/hit/HitResult;"))
    private HitResult hookRaycast(HitResult original, Entity camera, double blockInteractionRange, double entityInteractionRange, float tickDelta) {
        if (camera != client.player) {
            return original;
        }

        var rotation = (RotationManager.INSTANCE.getCurrentRotation() != null) ?
                RotationManager.INSTANCE.getCurrentRotation() :
                ModuleFreeCam.INSTANCE.getRunning() ?
                        RotationManager.INSTANCE.getServerRotation() :
                        new Rotation(camera.getYaw(tickDelta), camera.getPitch(tickDelta), true);

        return RaytracingExtensionsKt.raycast(rotation, Math.max(blockInteractionRange, entityInteractionRange),
                ModuleLiquidPlace.INSTANCE.getRunning(), tickDelta);
    }

    @ModifyExpressionValue(method = "findCrosshairTarget", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getRotationVec(F)Lnet/minecraft/util/math/Vec3d;"))
    private Vec3d hookRotationVector(Vec3d original, Entity camera, double blockInteractionRange, double entityInteractionRange, float tickDelta) {
        if (camera != client.player) {
            return original;
        }

        var rotation = RotationManager.INSTANCE.getCurrentRotation();
        return rotation != null ? rotation.getRotationVec() : original;
    }

    /**
     * Hook world render event
     */
    @Inject(method = "renderWorld", at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/GameRenderer;renderHand:Z", opcode = Opcodes.GETFIELD, ordinal = 0))
    public void hookWorldRender(RenderTickCounter tickCounter, CallbackInfo ci, @Local(ordinal = 2) Matrix4f matrix4f2) {
        // TODO: Improve this
        var newMatStack = new MatrixStack();

        newMatStack.multiplyPositionMatrix(matrix4f2);

        EventManager.INSTANCE.callEvent(new WorldRenderEvent(newMatStack, this.camera, tickCounter.getTickDelta(false)));
    }

    @Inject(method = "renderHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/LightmapTextureManager;enable()V", shift = At.Shift.AFTER))
    public void prepareItemCharms(Camera camera, float tickDelta, Matrix4f matrix4f, CallbackInfo ci) {
        if (ModuleItemChams.INSTANCE.getRunning()) {
            ModuleItemChams.INSTANCE.setData();
            OutlineEffectShader.INSTANCE.prepare();
        }
    }

    @Inject(method = "renderHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V", shift = At.Shift.AFTER))
    public void drawItemCharms(Camera camera, float tickDelta, Matrix4f matrix4f, CallbackInfo ci) {
        if (ModuleItemChams.INSTANCE.getActive()) {
            ModuleItemChams.INSTANCE.setActive(false);
            OutlineEffectShader.INSTANCE.apply();
        }
    }

    /**
     * Hook screen render event
     */
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screen/Screen;renderWithTooltip(Lnet/minecraft/client/gui/DrawContext;IIF)V",
            shift = At.Shift.AFTER))
    public void hookScreenRender(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci, @Local DrawContext drawContext) {
        EventManager.INSTANCE.callEvent(new ScreenRenderEvent(drawContext, tickCounter.getTickDelta(false)));
    }

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void injectHurtCam(MatrixStack matrixStack, float f, CallbackInfo callbackInfo) {
        if (ModuleNoHurtCam.INSTANCE.getRunning()) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void injectBobView(MatrixStack matrixStack, float f, CallbackInfo callbackInfo) {
        if (ModuleNoBob.INSTANCE.getRunning() || ModuleTracers.INSTANCE.getRunning()) {
            callbackInfo.cancel();
            return;
        }

        if (!ModuleDankBobbing.INSTANCE.getRunning()) {
            return;
        }

        if (!(client.getCameraEntity() instanceof AbstractClientPlayerEntity playerEntity)) {
            return;
        }

        float additionalBobbing = ModuleDankBobbing.INSTANCE.getMotion();

        float g = playerEntity.distanceMoved - playerEntity.lastDistanceMoved;
        float h = -(playerEntity.distanceMoved + g * f);
        float i = MathHelper.lerp(f, playerEntity.prevStrideDistance, playerEntity.strideDistance);
        matrixStack.translate((MathHelper.sin(h * MathHelper.PI) * i * 0.5F), -Math.abs(MathHelper.cos(h * MathHelper.PI) * i), 0.0D);
        matrixStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(MathHelper.sin(h * MathHelper.PI) * i * (3.0F + additionalBobbing)));
        matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(Math.abs(MathHelper.cos(h * MathHelper.PI - (0.2F + additionalBobbing)) * i) * 5.0F));

        callbackInfo.cancel();
    }

    @Inject(method = "onResized", at = @At("HEAD"))
    private void injectResizeUIBlurShader(int width, int height, CallbackInfo ci) {
        UIRenderer.INSTANCE.setupDimensions(width, height);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;drawEntityOutlinesFramebuffer()V", shift = At.Shift.AFTER))
    private void injectUIBlurRender(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        if (!ModuleHud.INSTANCE.isBlurable()) {
            return;
        }

        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();

        var overlayFramebuffer = UIRenderer.INSTANCE.getOverlayFramebuffer();
        var overlayTexture = overlayFramebuffer.getColorAttachment();

        overlayFramebuffer.beginRead();

        PostEffectProcessor postEffectProcessor = this.client.getShaderLoader().loadPostEffect(liquid_bounce$BLUR, DefaultFramebufferSet.MAIN_ONLY);

        RenderSystem.setShaderTexture(0, overlayTexture);
        ((PostEffectPassTextureAddition) postEffectProcessor.passes.getFirst()).liquid_bounce$setTextureSampler("Overlay", overlayTexture);
        postEffectProcessor.passes.getFirst().getProgram().getUniform("Radius").set(UIRenderer.INSTANCE.getBlurRadius());

        postEffectProcessor.render(this.client.getFramebuffer(), pool);
    }

    @Inject(method = "render", at = @At(value = "RETURN"))
    private void hookRenderEventStop(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        UIRenderer.INSTANCE.endUIOverlayDrawing();
    }

    @Inject(method = "renderBlur", at = @At("HEAD"))
    private void injectRenderBlur(CallbackInfo ci) {
        UIRenderer.INSTANCE.endUIOverlayDrawing();
    }

    @Inject(method = "showFloatingItem", at = @At("HEAD"), cancellable = true)
    private void hookShowFloatingItem(ItemStack floatingItem, CallbackInfo ci) {
        if (ModuleAntiBlind.INSTANCE.getRunning() && ModuleAntiBlind.INSTANCE.getFloatingItems()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderWorld", at = @At(value = "RETURN"))
    private void hookRestoreLightMap(RenderTickCounter tickCounter, CallbackInfo ci) {
        ((LightmapTextureManagerAddition) lightmapTextureManager).liquid_bounce$restoreLightMap();
    }

    @ModifyExpressionValue(method = "getFov", at = @At(value = "INVOKE", target = "Ljava/lang/Integer;intValue()I", remap = false))
    private int hookGetFov(int original) {
        int result;

        if (ModuleZoom.INSTANCE.getRunning()) {
            return ModuleZoom.INSTANCE.getFov(true, 0);
        } else {
            result = ModuleZoom.INSTANCE.getFov(false, original);
        }

        if (ModuleNoFov.INSTANCE.getRunning() && result == original) {
            return ModuleNoFov.INSTANCE.getFov(result);
        }

        return result;
    }

    @ModifyExpressionValue(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;lerp(FFF)F"))
    private float hookNausea(float original) {
        var antiBlind = ModuleAntiBlind.INSTANCE;
        if (antiBlind.getRunning() && antiBlind.getAntiNausea()) {
            return 0f;
        }

        return original;
    }

    @ModifyExpressionValue(method = "renderWorld",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/option/GameOptions;getPerspective()Lnet/minecraft/client/option/Perspective;"
            )
    )
    private Perspective hookPerspectiveEventOnCamera(Perspective original) {
        return EventManager.INSTANCE.callEvent(new PerspectiveEvent(original)).getPerspective();
    }

    @ModifyExpressionValue(method = "renderHand",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/option/GameOptions;getPerspective()Lnet/minecraft/client/option/Perspective;"
            )
    )
    private Perspective hookPerspectiveEventOnHand(Perspective original) {
        return EventManager.INSTANCE.callEvent(new PerspectiveEvent(original)).getPerspective();
    }

    @ModifyReturnValue(method = "getFov", at = @At("RETURN"))
    private float injectShit(float original) {
        var screen = ModuleDroneControl.INSTANCE.getScreen();

        if (screen != null) {
            return Math.min(120f, original / screen.getZoomFactor());
        }

        return original;
    }

}
