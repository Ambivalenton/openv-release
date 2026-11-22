// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.theme

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import android.view.inspector.WindowInspector
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import java.util.WeakHashMap

/** A final visual filter preserves saved colours, textures, motion and colour-editor values. */
internal object MonochromeWindows {
    private data class Entry(val layer:Int,val owners:MutableSet<Any>)
    private val entries=WeakHashMap<View,Entry>()
    val matrix get()=ColorMatrix().apply {setSaturation(0f)}
    fun attach(view:View,owner:Any) {
        val root=view.rootView
        val entry=entries.getOrPut(root) {Entry(root.layerType,mutableSetOf())}
        if(!entry.owners.add(owner)||entry.owners.size>1)return
        root.setLayerType(View.LAYER_TYPE_HARDWARE,Paint().apply {colorFilter=ColorMatrixColorFilter(matrix)})
        root.invalidate()
    }
    fun release(owner:Any) {
        val iterator=entries.entries.iterator()
        while(iterator.hasNext()) {
            val (view,entry)=iterator.next()
            if(entry.owners.remove(owner)&&entry.owners.isEmpty()) {view.setLayerType(entry.layer,null);view.invalidate();iterator.remove()}
        }
    }
}

@Composable fun MonochromeWindowEffect(enabled:Boolean=LocalAppSettings.current.monochrome) {
    val view=LocalView.current;val owner=remember {Any()}
    DisposableEffect(view,enabled) {
        if(!enabled)return@DisposableEffect onDispose {}
        fun apply() {
            MonochromeWindows.attach(view,owner)
            if(Build.VERSION.SDK_INT>=29)WindowInspector.getGlobalWindowViews().filter {it.context.packageName==view.context.packageName}
                .forEach {MonochromeWindows.attach(it,owner)}
        }
        val listener=ViewTreeObserver.OnPreDrawListener {apply();true}
        apply();view.viewTreeObserver.addOnPreDrawListener(listener)
        onDispose {if(view.viewTreeObserver.isAlive)view.viewTreeObserver.removeOnPreDrawListener(listener);MonochromeWindows.release(owner)}
    }
}
