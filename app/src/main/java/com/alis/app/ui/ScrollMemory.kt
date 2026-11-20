package com.alis.app.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/** Klasörlerde kaldığın kaydırma konumu: geri dönünce aynı yerden açılır (uygulama açık kaldıkça). */
object ScrollMemory {
    private val map = HashMap<String, Pair<Int, Int>>()
    fun get(key: String): Pair<Int, Int> = map[key] ?: (0 to 0)
    fun put(key: String, index: Int, offset: Int) {
        map[key] = index to offset
    }
}

@Composable
fun rememberListMemory(key: String): LazyListState {
    val (i, o) = ScrollMemory.get(key)
    val st = rememberLazyListState(i, o)
    DisposableEffect(key) { onDispose { ScrollMemory.put(key, st.firstVisibleItemIndex, st.firstVisibleItemScrollOffset) } }
    return st
}

@Composable
fun rememberGridMemory(key: String): LazyGridState {
    val (i, o) = ScrollMemory.get(key)
    val st = rememberLazyGridState(i, o)
    DisposableEffect(key) { onDispose { ScrollMemory.put(key, st.firstVisibleItemIndex, st.firstVisibleItemScrollOffset) } }
    return st
}
