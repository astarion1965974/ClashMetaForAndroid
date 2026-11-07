package com.github.kr328.clash.design.adapter

import android.content.Context
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.core.model.Provider
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.model.ProviderState
import com.github.kr328.clash.design.preference.CouiPreferenceRow
import com.github.kr328.clash.design.preference.CouiPreferenceView
import com.github.kr328.clash.design.preference.CouiRowAction
import com.github.kr328.clash.design.util.elapsedIntervalString
import com.github.kr328.clash.design.util.type
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme

class ProviderAdapter(
    private val context: Context,
    providers: List<Provider>,
    private val requestUpdate: (Int, Provider) -> Unit,
) : RecyclerView.Adapter<ProviderAdapter.Holder>() {
    class Holder internal constructor(row: CouiPreferenceView) : RecyclerView.ViewHolder(row) {
        var state: ProviderState? by mutableStateOf(null, neverEqualPolicy())
    }

    private var currentTime by mutableLongStateOf(System.currentTimeMillis())
    val states = providers.map { ProviderState(it, it.updatedAt, false) }

    fun updateElapsed() {
        currentTime = System.currentTimeMillis()
    }

    fun notifyUpdated(index: Int) {
        states[index].updating = false
        notifyItemChanged(index)
    }

    fun notifyChanged(index: Int) {
        states[index].apply {
            updating = false
            updatedAt = System.currentTimeMillis()
        }
        notifyItemChanged(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val row = CouiPreferenceView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val holder = Holder(row)
        row.setContent {
            holder.state?.let { state ->
                val provider = state.provider
                val inline = provider.vehicleType == Provider.VehicleType.Inline
                val summary = if (inline) provider.type(context) else {
                    "${provider.type(context)} · ${(currentTime - state.updatedAt).elapsedIntervalString(context)}"
                }
                CouiPreferenceRow(
                    title = provider.name,
                    summary = summary,
                    icon = null,
                    enabled = true,
                    onClick = null,
                    trailing = {
                        if (state.updating) {
                            Text(
                                text = stringResource(R.string.coui_provider_updating),
                                kind = CouiTextKind.PreferenceSummary,
                                color = CouiTheme.colorScheme.onSurfaceVariant,
                            )
                        } else if (!inline) {
                            CouiRowAction(R.drawable.ic_baseline_update, stringResource(R.string.update)) {
                                val index = holder.bindingAdapterPosition
                                if (index != RecyclerView.NO_POSITION && !states[index].updating) {
                                    states[index].updating = true
                                    notifyItemChanged(index)
                                    requestUpdate(index, states[index].provider)
                                }
                            }
                        }
                    },
                )
            }
        }
        return holder
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.state = states[position]
    }

    override fun getItemCount(): Int = states.size
}
