package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.OfflineImageStore
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], manifest = Config.NONE)
class OfflineImageStoreTest {
    private fun client(type: String, bytes: ByteArray): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(bytes.toResponseBody(type.toMediaType()))
                .build()
        }
        .build()

    @Test fun downloadedImageIsStoredInAppFilesAndCanBeRemoved() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = OfflineImageStore(context, client("image/png", byteArrayOf(1, 2, 3, 4)))
        val path = store.download("story-1", "https://images.example/news.png")
        assertNotNull(path)
        assertTrue(java.io.File(path!!).isFile)
        assertNotNull(store.localUri(path))
        store.delete(path)
        assertFalse(java.io.File(path).exists())
    }

    @Test fun nonRasterContentIsNeverStoredAsAnOfflineImage() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = OfflineImageStore(context, client("text/html", "not an image".toByteArray()))
        assertNull(store.download("story-2", "https://images.example/news"))
    }

    @Test fun imageDownloadsRejectPayloadsLargerThanFiveMegabytes() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = OfflineImageStore(context, client("image/png", ByteArray(5 * 1024 * 1024 + 1)))
        assertNull(store.download("story-3", "https://images.example/large.png"))
    }
}
