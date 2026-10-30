package me.misa198.airmedy

import android.content.pm.ActivityInfo
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerOrientationTest {
    @Test fun manifestUsesConfigurationIndependentOrientation() {
        val manifest = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
        val activities = manifest.getElementsByTagName("activity")
        val mainActivity = (0 until activities.length).map { activities.item(it) }
            .single { it.attributes.getNamedItem("android:name").nodeValue == ".MainActivity" }
        assertEquals("user", mainActivity.attributes.getNamedItem("android:screenOrientation").nodeValue)
    }

    @Test fun phonesStayPortraitAndLargeScreensRespectUserRotation() {
        listOf(0, 320, 475, 599).forEach { width ->
            assertEquals("smallest width $width", ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, appRequestedOrientation(width))
        }
        listOf(600, 840, 1200).forEach { width ->
            assertEquals("smallest width $width", ActivityInfo.SCREEN_ORIENTATION_USER, appRequestedOrientation(width))
        }
    }
}
