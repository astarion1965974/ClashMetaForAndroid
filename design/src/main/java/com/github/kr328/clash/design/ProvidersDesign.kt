package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import com.github.kr328.clash.core.model.Provider
import com.github.kr328.clash.design.preference.CouiPreferenceRow
import com.github.kr328.clash.design.preference.CouiRowAction
import com.github.kr328.clash.design.util.elapsedIntervalString
import com.github.kr328.clash.design.util.type
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Native COUI provider list, with Compose-observed update and elapsed states. */
class ProvidersDesign(
    context: Context,
    providers: List<Provider>,
) : Design<ProvidersDesign.Request>(context) {
    sealed class Request {
        data class Update(val index: Int, val provider: Provider) : Request()
    }

    private data class ProviderRowState(
        val provider: Provider,
        val updatedAt: Long,
        val updating: Boolean,
    )
    private var rows by mutableStateOf(providers.map {
        ProviderRowState(it, it.updatedAt, false)
    })
    private var now by mutableLongStateOf(System.currentTimeMillis())

    override val root: View = ComposeView(context).apply {
        setContent {
            ColorOsLazySubPage(
                title = stringResource(R.string.providers),
                onBack = { (context as? AppCompatActivity)?.onSupportNavigateUp() },
                actions = {
                    ToolbarIcon(
                        ImageVector.vectorResource(R.drawable.ic_baseline_sync),
                        stringResource(R.string.update_all),
                    ) { requestUpdateAll() }
                },
            ) {
                itemsIndexed(rows, key = { index, row ->
                    "${row.provider.type}-${row.provider.name}-${index}"
                }) { index, row ->
                    val inline = row.provider.vehicleType == Provider.VehicleType.Inline
                    val summary = if (inline) row.provider.type(context) else {
                        "${row.provider.type(context)} · ${(now - row.updatedAt).elapsedIntervalString(context)}"
                    }
                    CouiPreferenceRow(
                        title = row.provider.name,
                        summary = summary,
                        icon = null,
                        enabled = true,
                        onClick = null,
                        trailing = {
                            if (row.updating) {
                                Text(
                                    text = stringResource(R.string.coui_provider_updating),
                                    kind = CouiTextKind.PreferenceSummary,
                                    color = CouiTheme.colorScheme.onSurfaceVariant,
                                )
                            } else if (!inline) {
                                CouiRowAction(R.drawable.ic_baseline_update, stringResource(R.string.update)) {
                                    requestSingleUpdate(index)
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    fun updateElapsed() {
        now = System.currentTimeMillis()
    }

    suspend fun notifyUpdated(index: Int) = withContext(Dispatchers.Main) {
        mutateRow(index) { copy(updating = false) }
    }

    suspend fun notifyChanged(index: Int) = withContext(Dispatchers.Main) {
        mutateRow(index) { copy(updating = false, updatedAt = System.currentTimeMillis()) }
    }

    private fun mutateRow(index: Int, update: ProviderRowState.() -> ProviderRowState) {
        if (index !in rows.indices) return
        rows = rows.toMutableList().also { it[index] = update(it[index]) }
    }

    private fun requestSingleUpdate(index: Int) {
        val state = rows.getOrNull(index) ?: return
        if (state.updating || state.provider.vehicleType == Provider.VehicleType.Inline) return
        mutateRow(index) { copy(updating = true) }
        requests.trySend(Request.Update(index, state.provider))
    }

    fun requestUpdateAll() {
        rows.toList().forEachIndexed { index, state ->
            if (!state.updating && state.provider.vehicleType != Provider.VehicleType.Inline) {
                requestSingleUpdate(index)
            }
        }
    }
}
