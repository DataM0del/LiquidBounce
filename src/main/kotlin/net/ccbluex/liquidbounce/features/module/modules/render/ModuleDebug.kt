/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2025 CCBlueX
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
package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.config.types.ToggleableConfigurable
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.OverlayRenderEvent
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ModuleScaffold
import net.ccbluex.liquidbounce.render.*
import net.ccbluex.liquidbounce.render.engine.Color4b
import net.ccbluex.liquidbounce.utils.entity.PlayerSimulationCache
import net.ccbluex.liquidbounce.utils.entity.eyes
import net.ccbluex.liquidbounce.utils.math.geometry.AlignedFace
import net.ccbluex.liquidbounce.utils.math.geometry.Line
import net.ccbluex.liquidbounce.utils.math.geometry.LineSegment
import net.ccbluex.liquidbounce.utils.math.toVec3
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import java.awt.Color

/**
 * Rotations module
 *
 * Allows you to see server-sided rotations.
 */

object ModuleDebug : ClientModule("Debug", Category.RENDER) {

    private val parameters by boolean("Parameters", true).onChanged { _ ->
        debugParameters.clear()
    }
    private val geometry by boolean("Geometry", true).onChanged { _ ->
        debuggedGeometry.clear()
    }

    private val expireTime by int("Expires", 5, 1..30, "secs")

    private val fontRenderer
        get() = FontManager.FONT_RENDERER

    object RenderSimulatedPlayer : ToggleableConfigurable(this, "SimulatedPlayer", false) {

        private val ticksToPredict by int("TicksToPredict", 20, 5..100)

        @Suppress("unused")
        private val movementInputHandler = handler<MovementInputEvent> { _ ->
            PlayerSimulationCache.getSimulationForLocalPlayer().simulateUntil(this.ticksToPredict)
        }

        @Suppress("unused")
        private val renderHandler = handler<WorldRenderEvent> { event ->
            val cachedPositions = PlayerSimulationCache
                .getSimulationForLocalPlayer()
                .getSnapshotsBetween(0 until this.ticksToPredict)

            renderEnvironmentForWorld(event.matrixStack) {
                withColor(Color4b.BLUE) {
                    drawLineStrip(positions = cachedPositions.map { relativeToCamera(it.pos).toVec3() })
                }
            }
        }

    }

    init {
        tree(RenderSimulatedPlayer)
    }

    private val debuggedGeometry = hashMapOf<DebuggedGeometryOwner, DebuggedGeometry>()

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event ->
        val matrixStack = event.matrixStack

        if (!geometry) {
            return@handler
        }

        renderEnvironmentForWorld(matrixStack) {
            debuggedGeometry.values.forEach { geometry ->
                geometry.render(this)
            }
        }
    }

    @Suppress("unused")
    private val scaffoldDebugging = tickHandler {
        if (!ModuleScaffold.running) {
            return@tickHandler
        }

        val pos0 = Vec3d(77.0, 75.0, -52.0)
        val face = AlignedFace(pos0, pos0.add(1.0, 1.0, 0.0))

        debugGeometry(
            ModuleScaffold,
            "targetFace",
            DebuggedBox(Box(face.from, face.to), Color4b(255, 0, 0, 64))
        )

        val line = LineSegment(player.eyes, player.rotationVector, 0.0..10.0)

        debugGeometry(
            ModuleScaffold,
            "daLine",
            DebuggedLineSegment(line.endPoints.first, line.endPoints.second, Color4b(0, 0, 255, 255))
        )

        val pointTo = face.nearestPointTo(line)

        debugGeometry(
            ModuleScaffold,
            "targetPoint",
            DebuggedPoint(pointTo, Color4b(0, 0, 255, 255), size = 0.05)
        )
    }

    @Suppress("unused")
    private val expireHandler = tickHandler {
        val currentTime = System.currentTimeMillis()

        debugParameters.entries.removeIf { (parameter, capture) ->
            (currentTime - capture.time) / 1000 >= expireTime
        }
    }

    @Suppress("unused")
    private val screenRenderHandler = handler<OverlayRenderEvent> { event ->
        val context = event.context

        if (mc.options.playerListKey.isPressed || !parameters) {
            return@handler
        }

        val width = mc.window.scaledWidth

        renderEnvironmentForGUI {
            fontRenderer.withBuffers { buffers ->
                /**
                 * Separate the debugged owner from its parameter
                 * Structure should be like this:
                 * Owner ->
                 *   Parameter Name: Parameter Value
                 *   Parameter Name: Parameter Value
                 *   Parameter Name: Parameter Value
                 */
                val textList = mutableListOf<Text>()

                val debuggedOwners = debugParameters.keys.groupBy { it.owner }

                val currentTime = System.currentTimeMillis()

                debuggedOwners.onEach { (owner, parameter) ->
                    val ownerName = when (owner) {
                        is ClientModule -> owner.name
                        is EventListener -> "${owner.parent()?.javaClass?.simpleName}::${owner.javaClass.simpleName}"
                        else -> owner.javaClass.simpleName
                    }

                    textList += Text.literal(ownerName).styled {
                        it.withColor(Formatting.GOLD).withBold(true)
                    }

                    parameter.forEach { debuggedParameter ->
                        val parameterName = debuggedParameter.name
                        val parameterCapture = debugParameters[debuggedParameter] ?: return@forEach
                        textList += Text.literal("$parameterName: ").styled {
                            it.withColor(Formatting.WHITE)
                        }.append(Text.literal(parameterCapture.value.toString()).styled {
                            it.withColor(Formatting.GREEN)
                        }).append(" [${((currentTime - parameterCapture.time) / 1000).toInt()}s ago]").styled {
                            it.withColor(Formatting.GRAY)
                        }
                    }
                }

                // Draw
                with(context) {
                    draw(
                        process(Text.literal("Debugging")),
                        120f,
                        22f,
                        shadow = true,
                        scale = 0.3f
                    )

                    // Draw text line one by one
                    textList.forEachIndexed { index, text ->
                        draw(
                            process(text),
                            120f,
                            40 + ((fontRenderer.height * 0.17f) * index).toFloat(),
                            shadow = true,
                            scale = 0.17f
                        )
                    }

                    commit(buffers)
                }


            }
        }
    }

    inline fun debugGeometry(owner: Any, name: String, lazyGeometry: () -> DebuggedGeometry) {
        if (!running) {
            return
        }

        debugGeometry(owner, name, lazyGeometry.invoke())
    }

    fun debugGeometry(owner: Any, name: String, geometry: DebuggedGeometry) {
        // Do not take any new debugging while the module is off
        if (!running) {
            return
        }

        debuggedGeometry[DebuggedGeometryOwner(owner, name)] = geometry
    }

    private data class DebuggedGeometryOwner(val owner: Any, val name: String)

    private data class DebuggedParameter(val owner: Any, val name: String)

    private data class ParameterCapture(val time: Long = System.currentTimeMillis(), val value: Any)

    private val debugParameters = hashMapOf<DebuggedParameter, ParameterCapture>()

    inline fun debugParameter(owner: Any, name: String, lazyValue: () -> Any) {
        if (!running) {
            return
        }

        debugParameter(owner, name, lazyValue.invoke())
    }

    fun debugParameter(owner: Any, name: String, value: Any) {
        if (!running) {
            return
        }

        debugParameters[DebuggedParameter(owner, name)] = ParameterCapture(value = value)
    }

    fun getArrayEntryColor(idx: Int, length: Int): Color4b {
        val hue = idx.toFloat() / length.toFloat()
        return Color4b(Color.getHSBColor(hue, 1f, 1f)).with(a = 32)
    }

    sealed class DebuggedGeometry(val color: Color4b) {
        abstract fun render(env: WorldRenderEnvironment)
    }

    class DebuggedLine(line: Line, color: Color4b) : DebuggedGeometry(color) {
        val from: Vec3d
        val to: Vec3d

        init {
            val normalizedDirection = line.direction.normalize()

            this.from = line.position.subtract(normalizedDirection.multiply(100.0))
            this.to = line.position.add(normalizedDirection.multiply(100.0))
        }

        override fun render(env: WorldRenderEnvironment) {
            env.withColor(color) {
                this.drawLineStrip(relativeToCamera(from).toVec3(), relativeToCamera(to).toVec3())
            }
        }
    }

    class DebuggedQuad(val p1: Vec3d, val p2: Vec3d, color: Color4b) : DebuggedGeometry(color) {
        override fun render(env: WorldRenderEnvironment) {
            env.withColor(color) {
                this.drawQuad(relativeToCamera(p1).toVec3(), relativeToCamera(p2).toVec3())
            }
        }
    }

    class DebuggedLineSegment(val from: Vec3d, val to: Vec3d, color: Color4b) : DebuggedGeometry(color) {
        override fun render(env: WorldRenderEnvironment) {
            env.withColor(color) {
                this.drawLineStrip(relativeToCamera(from).toVec3(), relativeToCamera(to).toVec3())
            }
        }
    }

    open class DebuggedBox(val box: Box, color: Color4b) : DebuggedGeometry(color) {
        override fun render(env: WorldRenderEnvironment) {
            env.withColor(color) {
                this.drawSolidBox(box.offset(env.camera.pos.negate()))
            }
        }
    }

    class DebuggedPoint(point: Vec3d, color: Color4b, size: Double = 0.2) : DebuggedBox(
        Box.of(point, size, size, size),
        color
    )

    class DebugCollection(val geometry: Collection<DebuggedGeometry>) : DebuggedGeometry(Color4b.WHITE) {
        override fun render(env: WorldRenderEnvironment) {
            this.geometry.forEach { it.render(env) }
        }
    }

    override fun disable() {
        // Might clean up some memory if we disable the module
        debuggedGeometry.clear()
        debugParameters.clear()
        super.disable()
    }

}
