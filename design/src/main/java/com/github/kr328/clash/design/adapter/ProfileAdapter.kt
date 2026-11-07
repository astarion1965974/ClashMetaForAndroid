package com.github.kr328.clash.design.adapter

import android.content.Context
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
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
import com.itos.coui.theme.CouiTheme

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
                ProfileListItem(profile, currentTime, onClicked, onMenuClicked)
            }
        }
        return holder
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.profile = profiles[position]
    }

    override fun getItemCount(): Int = profiles.size
}

/** One COUI profile row shared by the Compose tab and the legacy adapter. */
@Composable
internal fun ProfileListItem(
    profile: Profile,
    currentTime: Long,
    onClicked: (Profile) -> Unit,
    onMenuClicked: (Profile) -> Unit,
) {
    val context = LocalContext.current
    val type = if (profile.pending) {
        context.getString(R.string.format_type_unsaved, profile.type.toString(context))
    } else {
        profile.type.toString(context)
    }
    val elapsed = (currentTime - profile.updatedAt).elapsedIntervalString(context)
    val uploaded = profile.upload.coerceAtLeast(0)
    val downloaded = profile.download.coerceAtLeast(0)
    val used = uploaded + downloaded.coerceAtMost(Long.MAX_VALUE - uploaded)
    val quota = if (profile.total > 1) {
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
        afterSummary = if (profile.total > 1) {
            {
                val progress = (used.toDouble() / profile.total).toFloat().coerceIn(0f, 1f)
                val colors = CouiTheme.colorScheme
                Box(
                    Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)
                        .progressSemantics(progress).background(colors.outlineVariant),
                ) {
                    Box(Modifier.fillMaxWidth(progress).height(4.dp).background(colors.accent))
                }
            }
        } else null,
        trailing = {
            CouiRowAction(R.drawable.ic_baseline_more_vert, stringResource(R.string.more)) {
                onMenuClicked(profile)
            }
        },
    )
}
