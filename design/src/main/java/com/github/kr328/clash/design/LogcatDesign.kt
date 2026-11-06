package com.github.kr328.clash.design

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import androidx.core.content.getSystemService
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.kr328.clash.core.model.LogMessage
import com.github.kr328.clash.design.adapter.LogMessageAdapter
import com.github.kr328.clash.design.databinding.DesignLogcatBinding
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LogcatDesign(
    context: Context,
    private val streaming: Boolean,
) : Design<LogcatDesign.Request>(context) {
    enum class Request {
        Close, Delete, Export
    }

    private val binding = DesignLogcatBinding
        .inflate(context.layoutInflater, context.root, false)
    private val adapter = LogMessageAdapter(context) {
        launch {
            val data = ClipData.newPlainText("log_message", it.message)

            context.getSystemService<ClipboardManager>()?.setPrimaryClip(data)

            showToast(R.string.copied, ToastDuration.Short)
        }
    }

    suspend fun patchMessages(messages: List<LogMessage>, removed: Int, appended: Int) {
        withContext(Dispatchers.Main) {
            val oldSize = adapter.messages.size
            adapter.messages = messages

            if (removed in 0..oldSize && appended >= 0 && oldSize - removed + appended == messages.size) {
                if (removed > 0) adapter.notifyItemRangeRemoved(0, removed)
                if (appended > 0) adapter.notifyItemRangeInserted(oldSize - removed, appended)
            } else {
                adapter.notifyDataSetChanged()
            }

            if (streaming && messages.isNotEmpty() && binding.recyclerList.isTop) {
                binding.recyclerList.scrollToPosition(messages.size - 1)
            }
        }
    }

    override val root: View
        get() = binding.root

    init {
        binding.self = this
        binding.streaming = streaming

        binding.activityBarLayout.applyFrom(context)

        binding.recyclerList.bindAppBarElevation(binding.activityBarLayout)

        binding.recyclerList.layoutManager = LinearLayoutManager(context).apply {
            if (streaming) {
                reverseLayout = true
                stackFromEnd = true
            }
        }
        binding.recyclerList.adapter = adapter
    }
}
