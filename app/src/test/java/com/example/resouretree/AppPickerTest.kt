package com.example.resouretree

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.resouretree.data.apps.InstalledApp
import com.example.resouretree.ui.components.AppPicker
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppPickerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun searchByNameAndPackageThenSelect() {
        val apps = listOf(InstalledApp("相册", "com.test.photos"), InstalledApp("浏览器", "com.test.browser"))
        var selected: InstalledApp? = null
        compose.setContent { MaterialTheme { AppPicker(apps, false, null, "", {}, { selected = it }, {}) } }
        compose.onNodeWithText("搜索应用").performTextInput("相册")
        compose.onNodeWithTag("app-com.test.photos").assertExists()
        compose.onNodeWithTag("app-com.test.browser").assertDoesNotExist()
        compose.onNodeWithText("搜索应用").performTextReplacement("unmatched")
        compose.onNodeWithText("没有找到匹配的应用").assertExists()
        compose.onNodeWithText("搜索应用").performTextReplacement("BROWSER")
        compose.onNodeWithTag("app-com.test.browser").performClick()
        compose.runOnIdle { assertEquals(apps[1], selected) }
    }

    @Test fun failedListAllowsRetryAndDismiss() {
        var reloads = 0
        var dismissed = false
        compose.setContent { MaterialTheme { AppPicker(emptyList(), false, "读取失败", "", { reloads++ }, {}, { dismissed = true }) } }
        compose.onNodeWithText("读取失败").assertExists()
        compose.onNodeWithText("重试").performClick()
        compose.onNodeWithText("返回").performClick()
        compose.runOnIdle { assertEquals(1, reloads); assertTrue(dismissed) }
    }
}
