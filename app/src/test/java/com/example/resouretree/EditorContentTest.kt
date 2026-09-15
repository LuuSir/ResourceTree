package com.example.resouretree

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.*
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import com.example.resouretree.data.apps.AppCatalog
import com.example.resouretree.data.local.database.ResourceDatabase
import com.example.resouretree.data.repository.NodeRepository
import com.example.resouretree.data.transfer.DocumentStore
import com.example.resouretree.domain.model.*
import com.example.resouretree.ui.screens.EditorScreen
import com.example.resouretree.ui.viewmodel.EditorViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EditorContentTest {
    @get:Rule val compose = createComposeRule()
    @Test fun imagePickerContractStoresContentAndShareNeedsNoTarget() {
        val context = RuntimeEnvironment.getApplication()
        val source = File(context.filesDir, "media/editor-source.png").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1,2,3)) }
        val uri = android.net.Uri.parse("content://editor.test/picture.png")
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("editor.test", object : android.content.ContentProvider() {
            override fun onCreate() = true
            override fun getType(uri: android.net.Uri) = "image/png"
            override fun query(uri: android.net.Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, order: String?): android.database.Cursor? = null
            override fun insert(uri: android.net.Uri, values: android.content.ContentValues?): android.net.Uri? = null
            override fun delete(uri: android.net.Uri, selection: String?, args: Array<out String>?) = 0
            override fun update(uri: android.net.Uri, values: android.content.ContentValues?, selection: String?, args: Array<out String>?) = 0
        })
        org.robolectric.Shadows.shadowOf(context.contentResolver).registerInputStream(uri, source.inputStream())
        var launched: Intent? = null
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                    launched = contract.createIntent(context, input)
                    dispatchResult(requestCode, contract.parseResult(Activity.RESULT_OK, Intent().setData(uri)))
                }
            }
        }
        val db = Room.inMemoryDatabaseBuilder(context, ResourceDatabase::class.java).build()
        val repository = NodeRepository(db)
        lateinit var vm: EditorViewModel
        try {
            compose.setContent {
                vm = androidx.lifecycle.viewmodel.compose.viewModel {
                    EditorViewModel(repository, SavedStateHandle(), null, null, NodeType.ITEM, AppCatalog { emptyList() }, DocumentStore(context.contentResolver, source.parentFile))
                }
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                    MaterialTheme { EditorScreen(vm, false) {} }
                }
            }
            compose.waitUntil(10000) { compose.waitForIdle(); !vm.state.value.loading }
            compose.onNodeWithText("名称 *").performTextInput("图片条目")
            compose.onNodeWithText("IMAGE", substring = false).performClick()
            compose.onNodeWithText("动作复制文本").assertDoesNotExist()
            compose.onNodeWithText("选择媒体文件").performScrollTo().performClick()
            try {
                compose.waitUntil(10000) { compose.waitForIdle(); !vm.state.value.importingMedia && (vm.state.value.content.path.isNotEmpty() || vm.state.value.error != null) }
            } catch (e: Throwable) { throw AssertionError("launch=$launched state=${vm.state.value}", e) }
            assertNull(vm.state.value.error, vm.state.value.error)
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, launched!!.action)
            assertArrayEquals(arrayOf("image/*"), launched!!.getStringArrayExtra(Intent.EXTRA_MIME_TYPES))
            compose.onNodeWithText("保存", substring = false).performClick()
            compose.waitUntil(10000) { compose.waitForIdle(); vm.state.value.saved }
            val saved = runBlocking { repository.children(null).first().single() }
            assertEquals(ContentType.IMAGE, saved.content.type); assertEquals("image/png", saved.content.mimeType)
            assertEquals("", saved.content.text); assertTrue(File(context.filesDir, saved.content.path).isFile)
            assertEquals(ResourceAction(ActionType.SHARE), saved.action)
        } finally { db.close() }
    }
}
