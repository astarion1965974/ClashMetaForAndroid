package com.github.kr328.clash.design.adapter

import android.content.Context
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.design.model.ProfileProvider
import com.github.kr328.clash.design.preference.CouiPreferenceRow
import com.github.kr328.clash.design.preference.CouiPreferenceView

class ProfileProviderAdapter(
    private val context: Context,
    private val select: (ProfileProvider) -> Unit,
    private val detail: (ProfileProvider) -> Boolean,
) : RecyclerView.Adapter<ProfileProviderAdapter.Holder>() {
    class Holder internal constructor(row: CouiPreferenceView) : RecyclerView.ViewHolder(row) {
        var provider: ProfileProvider? by mutableStateOf(null)
    }

    var providers: List<ProfileProvider> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val row = CouiPreferenceView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val holder = Holder(row)
        row.setContent {
            holder.provider?.let { provider ->
                CouiPreferenceRow(
                    title = provider.name,
                    summary = provider.summary,
                    icon = provider.icon,
                    enabled = true,
                    onClick = { select(provider) },
                    onLongClick = if (provider is ProfileProvider.External) ({ detail(provider); Unit }) else null,
                )
            }
        }
        return holder
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.provider = providers[position]
    }

    override fun getItemCount(): Int = providers.size
}
