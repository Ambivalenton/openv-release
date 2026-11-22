// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.repository

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.ambivalenton.openv.data.model.MapTileId
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl

fun interface WalkingMapTileSource {suspend fun tile(id:MapTileId,template:String):ImageBitmap}

/** Shared raster tiles, never a MapView per instruction. Only visible viewports request tiles.
 * OkHttp honours server expiry/ETag. If absent, a seven-day cache lifetime is added.
 * 32 MiB disk + 16 MiB memory, two downloads, fixed lock striping, no prefetch/offline jobs. */
class WalkingMapTiles(context:Context,baseClient:OkHttpClient):WalkingMapTileSource {
    private val client=baseClient.newBuilder().cache(Cache(File(context.cacheDir,"walking-map-tiles"),32L*1024*1024))
        .callTimeout(10,java.util.concurrent.TimeUnit.SECONDS)
        .addNetworkInterceptor {chain->
            val response=chain.proceed(chain.request())
            if(response.code==200 && response.header("Cache-Control")==null && response.header("Expires")==null)
                response.newBuilder().header("Cache-Control","public, max-age=604800").build() else response
        }.build()
    private val memory=object:LruCache<String,ImageBitmap>(16*1024*1024) {
        override fun sizeOf(key:String,value:ImageBitmap)=value.width*value.height*4
    }
    private val stripes=Array(32) {Mutex()};private val slots=Semaphore(2)
    private val blocked=ConcurrentHashMap<String,Long>()
    override suspend fun tile(id:MapTileId,template:String):ImageBitmap {
        require(id.z in 0..19 && id.x in 0 until (1 shl id.z) && id.y in 0 until (1 shl id.z))
        require(listOf("{z}","{x}","{y}").all {it in template})
        val url=template.replace("{z}",id.z.toString()).replace("{x}",id.x.toString()).replace("{y}",id.y.toString()).toHttpUrl()
        require(url.isHttps && url.username.isEmpty() && url.password.isEmpty() && url.fragment==null)
        val key=url.toString();memory.get(key)?.let {return it}
        return stripes[(key.hashCode() and Int.MAX_VALUE)%stripes.size].withLock {
            memory.get(key)?.let {return@withLock it}
            slots.withPermit {
                if(System.currentTimeMillis()<(blocked[url.host] ?: 0)) throw IOException("Map service rate limited")
                val bytes=suspendCancellableCoroutine<ByteArray> {continuation->
                    val call=client.newCall(Request.Builder().url(url).header("User-Agent",NetworkClient.userAgent).build())
                    continuation.invokeOnCancellation {call.cancel()}
                    call.enqueue(object:Callback {
                        override fun onFailure(call:Call,e:IOException) {if(continuation.isActive) continuation.resumeWithException(e)}
                        override fun onResponse(call:Call,response:Response) {response.use {
                            if(!continuation.isActive) return
                            try {
                                if(response.code==429) blocked[url.host]=System.currentTimeMillis()+
                                    (response.header("Retry-After")?.toLongOrNull()?.coerceIn(5,300) ?: 30)*1000
                                if(!response.isSuccessful) throw IOException("Map service HTTP ${response.code}")
                                val source=response.body?.source() ?: throw IOException("Empty map tile")
                                if(source.request(1_000_001)) throw IOException("Map tile too large")
                                continuation.resume(source.readByteArray())
                            } catch(e:Exception) {if(continuation.isActive) continuation.resumeWithException(e)}
                        }}
                    })
                }
                val bitmap=withContext(Dispatchers.IO) {
                    val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
                    BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                    require(bounds.outWidth in 1..1024 && bounds.outHeight in 1..1024)
                    BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply {inScaled=false})
                        ?.asImageBitmap() ?: throw IOException("Invalid map tile")
                }
                memory.put(key,bitmap);bitmap
            }
        }
    }
}
