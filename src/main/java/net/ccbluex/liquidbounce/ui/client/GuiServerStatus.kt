/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.ui.client

import kotlinx.coroutines.launch
import net.ccbluex.liquidbounce.lang.translationMenu
import net.ccbluex.liquidbounce.ui.font.AWTFontRenderer.Companion.assumeNonVolatile
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.io.HttpUtils.responseCode
import net.ccbluex.liquidbounce.utils.kotlin.SharedScopes
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRect
import net.ccbluex.liquidbounce.utils.ui.AbstractScreen
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiScreen
import org.lwjgl.input.Keyboard
import java.awt.Color
import java.io.IOException

class GuiServerStatus(private val prevGui: GuiScreen) : AbstractScreen() {
    private val status = hashMapOf<String, String?>(
        "https://api.mojang.com" to null,
        "http://session.minecraft.net" to null,
        "https://textures.minecraft.net" to null,
        "http://minecraft.net" to null,
        "https://account.mojang.com" to null,
        "https://sessionserver.mojang.com" to null,
        "http://mojang.com" to null
    )

    override fun initGui() {
        +GuiButton(1, width / 2 - 100, height / 4 + 145, "Back")

        loadInformation()
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        assumeNonVolatile {
            drawBackground(0)

            var i = height / 4 + 40
            drawRect(
                width / 2f - 115,
                i - 5f,
                width / 2f + 115,
                height / 4f + 43 + if (status.keys.isEmpty()) 10 else status.keys.size * Fonts.font40.fontHeight,
                Integer.MIN_VALUE
            )

            for (server in status.keys) {
                val color = status[server] ?: "yellow"
                Fonts.font40.drawCenteredString(
                    "${server.replaceFirst("^http[s]?://".toRegex(), "")}: ${
                        if (color.equals(
                                "red",
                                ignoreCase = true
                            )
                        ) "§c" else if (color.equals("yellow", ignoreCase = true)) "§e" else "§a"
                    }${
                        if (color.equals("red", ignoreCase = true)) "Offline" else if (color.equals(
                                "yellow",
                                ignoreCase = true
                            )
                        ) "Loading..." else "Online"
                    }", width / 2f, i.toFloat(), Color.WHITE.rgb
                )
                i += Fonts.font40.fontHeight
            }

            Fonts.fontBold180.drawCenteredString(
                translationMenu("serverStatus"),
                width / 2F,
                height / 8f + 5F,
                4673984,
                true
            )

        }

        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    private fun loadInformation() {
        for (url in status.keys) {
            status[url] = null
            SharedScopes.IO.launch {
                try {
                    val responseCode = responseCode(url, "GET")
                    status[url] = if (responseCode in 200..499) "green" else "red"
                } catch (e: IOException) {
                    status[url] = "red"
                }
            }
        }
    }

    override fun actionPerformed(button: GuiButton) {
        if (button.id == 1) mc.displayGuiScreen(prevGui)
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        if (Keyboard.KEY_ESCAPE == keyCode) {
            mc.displayGuiScreen(prevGui)
            return
        }

        super.keyTyped(typedChar, keyCode)
    }
}
