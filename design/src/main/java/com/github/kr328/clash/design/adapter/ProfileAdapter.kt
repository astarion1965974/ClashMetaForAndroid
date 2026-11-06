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
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.model.ProfilePageState
import com.github.kr328.clash.design.preference.CouiPreferenceRow
import com.github.kr328.clash.design.preference.CouiPreferenceView
import com.github.kr328.clash.design.preference.CouiRowAction
import com.github.kr328.clash.design.util.elapsedIntervalString
import com.github.kr328.clash.design.util.toBytesString
import com.github.kr328.clash.design.util.toDateStr
import com.github.kr328.clash.design.util.toString
import com.github.kr328.clash.service.model.Profile
import com.itos.coui.radiobutton.RadioButton

class ProfileAdapter(
    private val context: Context,
    private val onClicked: (Profile) -> Unit,
    private val onMenuClicked: (Profile) -> Unit,
) : RecyclerView.Adapter<ProfileAdapter.Holder>() {
    class Holder internal constructor(row: CouiPreferenceView) : RecyclerView.ViewHolder(row) {
        var profile: Profile? by mutableStateOf(null, neverEqualPolicy())
    }

    private var currentTime by mutableLongStateOf(System.currentTimeMillis())
    var profiles: List<Profile> = emptyList()
    val states = ProfilePageState()

    fun updateElapsed() {
        currentTime = System.currentTimeMillis()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val row = CouiPreferenceView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val holder = Holder(row)
        row.setContent {
            holder.profile?.let { profile ->
                val type = if (profile.pending) {
                    context.getString(R.string.format_type_unsaved, profile.type.toString(context))
                } else {
                    profile.type.toString(context)
                }
                val elapsed = (currentTime - profile.updatedAt).elapsedIntervalString(context)
                val quota = if (profile.total > 1) {
                    val uploaded = profile.upload.coerceAtLeast(0)
                    val downloaded = profile.download.coerceAtLeast(0)
                    val used = uploaded + downloaded.coerceAtMost(Long.MAX_VALUE - uploaded)
                    "\n${used.toBytesString()} / ${profile.total.toBytesString()}"
                } else {
                    ""
                }
                val expiry = if (profile.expire > 0) "\n${profile.expire.toDateStr()}" else ""
                CouiPreferenceRow(
                    title = profile.name,
                    summary = "$type · $elapsed$quota$expiry",
                    icon = null,
                    leading = { RadioButton(selected = profile.active) },
                    enabled = true,
                    onClick = { onClicked(profile) },
                    trailing = {
                        CouiRowAction(R.drawable.ic_baseline_more_vert, stringResource(R.string.more)) {
                            onMenuClicked(profile)
                        }
                    },
                )
            }
        }
        return holder
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.profile = profiles[position]
    }

    override fun getItemCount(): Int = profiles.size
}
