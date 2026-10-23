package com.innovation313.roshankhata.ui

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.innovation313.roshankhata.data.ListWindow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A long list read a window at a time ([ListWindow], 23 Oct 2026, the review):
 * [limit] starts at one page and grows by a page when the owner scrolls near
 * the end of what the list holds ([shown] rows). The screen observes its query
 * with `limit.flatMapLatest { dao.xPage(it) }`.
 */
object GrowingList {
    fun attach(list: RecyclerView, limit: MutableStateFlow<Int>, shown: () -> Int) {
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val next = ListWindow.grown(limit.value, shown(), lm.findLastVisibleItemPosition())
                if (next != limit.value) limit.value = next
            }
        })
    }
}
