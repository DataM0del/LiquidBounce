/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.ui.client

import net.ccbluex.liquidbounce.features.module.modules.render.ClickGUI
import net.ccbluex.liquidbounce.ui.font.AWTFontRenderer.Companion.assumeNonVolatile
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.ui.AbstractScreen
import net.minecraft.client.gui.GuiButton
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11.glScalef
import java.awt.Color

class GuiWelcome : AbstractScreen() {

    override fun initGui() {
        +GuiButton(1, width / 2 - 100, height - 40, "OK")
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) = assumeNonVolatile {
        drawBackground(0)

        Fonts.fontSemibold35.run {
            drawCenteredString("Thank you for downloading and installing our client!", width / 2F, 70f, 0xffffff, true)
            drawCenteredString(
                "Here is some information you might find useful if you are using LiquidBounce for the first time.",
                width / 2F,
                70f + fontHeight,
                0xffffff,
                true
            )

            drawCenteredString("§lClickGUI:", width / 2F, 80f + fontHeight * 3, 0xffffff, true)
            drawCenteredString(
                "Press ${Keyboard.getKeyName(ClickGUI.keyBind)} to open up the ClickGUI",
                width / 2F,
                80f + fontHeight * 4,
                0xffffff,
                true
            )
            drawCenteredString(
                "Right-click modules with a '+' next to them to edit their settings.",
                width / 2F,
                80f + fontHeight * 5,
                0xffffff,
                true
            )
            drawCenteredString(
                "Hover a module to see it's description.",
                width / 2F,
                80f + fontHeight * 6,
                0xffffff,
                true
            )

            drawCenteredString("§lImportant Commands:", width / 2F, 80f + fontHeight * 8, 0xffffff, true)
            drawCenteredString(
                ".bind <module> <key> / .bind <module> none",
                width / 2F,
                80f + fontHeight * 9,
                0xffffff,
                true
            )
            drawCenteredString(
                ".autosettings load <name> / .autosettings list",
                width / 2F,
                80f + fontHeight * 10,
                0xffffff,
                true
            )

            drawCenteredString(
                "§lNeed help? Feel free to contact us!",
                width / 2F,
                80f + fontHeight * 12,
                0xffffff,
                true
            )
            drawCenteredString(
                "YouTube: https://youtube.com/ccbluex",
                width / 2F,
                80f + fontHeight * 13,
                0xffffff,
                true
            )
            drawCenteredString(
                "Twitter: https://twitter.com/ccbluex",
                width / 2F,
                80f + fontHeight * 14,
                0xffffff,
                true
            )
            drawCenteredString("Forum: https://forums.ccbluex.net/", width / 2F, 80f + fontHeight * 15, 0xffffff, true)
        }

        super.drawScreen(mouseX, mouseY, partialTicks)

        // Title
        glScalef(2F, 2F, 2F)
        Fonts.fontSemibold40.drawCenteredString("Welcome!", width / 2 / 2F, 20.0f, Color(0, 140, 255).rgb, true)
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        if (Keyboard.KEY_ESCAPE == keyCode)
            return

        super.keyTyped(typedChar, keyCode)
    }

    override fun actionPerformed(button: GuiButton) {
        if (button.id == 1) {
            mc.displayGuiScreen(GuiMainMenu())
        }
    }
}
