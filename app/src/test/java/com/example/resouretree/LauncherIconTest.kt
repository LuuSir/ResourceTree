package com.example.resouretree

import android.graphics.*
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherIconTest {
    @Test fun renderIconVariantsAndLegacyDensityAssets() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val output = File("build/icon-preview").apply { mkdirs() }
        fun render(size: Int, round: Boolean, mono: Boolean = false): Bitmap {
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val mask = Path().apply {
                if (round) addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW)
                else addRoundRect(0f, 0f, size.toFloat(), size.toFloat(), size * .23f, size * .23f, Path.Direction.CW)
            }
            canvas.clipPath(mask)
            canvas.drawColor(if (mono) Color.rgb(70, 57, 45) else Color.rgb(255, 241, 219))
            val mark = context.getDrawable(if (mono) R.drawable.ic_launcher_monochrome else R.drawable.ic_launcher_foreground)!!
            mark.setBounds(0, 0, size, size); mark.draw(canvas)
            return bitmap
        }
        val sheet = Bitmap.createBitmap(768, 256, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        for (i in 0..2) canvas.drawBitmap(render(256, i == 1, i == 2), i * 256f, 0f, null)
        File(output, "icon-variants.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertEquals(Color.rgb(223, 120, 62), sheet.getPixel(75, 150))
        for ((density, size) in listOf("mdpi" to 48, "hdpi" to 72, "xhdpi" to 96, "xxhdpi" to 144, "xxxhdpi" to 192)) {
            for (round in listOf(false, true)) {
                val dir = File(output, "mipmap-$density").apply { mkdirs() }
                File(dir, if (round) "ic_launcher_round.webp" else "ic_launcher.webp").outputStream().use {
                    render(size, round).compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, it)
                }
            }
        }
    }
}
