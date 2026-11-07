package com.github.kr328.clash.design.preference

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.adapter.EditableTextMapAdapter
import com.github.kr328.clash.design.dialog.requestModelTextInput
import com.github.kr328.clash.design.databinding.DialogEditableMapTextFieldBinding
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.reflect.KMutableProperty0

interface EditableTextMapPreference<K, V> : ClickablePreference {
    var placeholder: CharSequence?
    var map: Map<K, V>?
}

fun <K, V> PreferenceScreen.editableTextMap(
    value: KMutableProperty0<Map<K, V>?>,
    keyAdapter: TextAdapter<K>,
    valueAdapter: TextAdapter<V>,
    @StringRes title: Int,
    @DrawableRes icon: Int? = null,
    @StringRes placeholder: Int? = null,
    configure: EditableTextMapPreference<K, V>.() -> Unit = {}
): EditableTextMapPreference<K, V> {
    val impl =
        object : EditableTextMapPreference<K, V>, ClickablePreference by clickable(title, icon) {
            override var placeholder: CharSequence? = null
            override var map: Map<K, V>? = null
                set(value) {
                    field = value

                    when {
                        value == null -> {
                            this.summary = this.placeholder
                        }
                        value.isEmpty() -> {
                            this.summary = context.getString(R.string.empty)
                        }
                        else -> {
                            this.summary = context.getString(R.string.format_elements, value.size)
                        }
                    }
                }
        }

    if (placeholder != null) {
        impl.placeholder = context.getText(placeholder)
    }

    impl.configure()

    launch(Dispatchers.Main) {
        val v = withContext(Dispatchers.IO) {
            value.get()
        }

        impl.map = v

        impl.clicked {
            this@editableTextMap.launch(Dispatchers.Main) {
                val newMap = requestEditTextMap(
                    impl.map,
                    context,
                    keyAdapter,
                    valueAdapter,
                    impl.title
                )

                withContext(Dispatchers.IO) {
                    value.set(newMap)
                }

                impl.map = newMap
            }
        }
    }

    return impl
}

private suspend fun <K, V> requestEditTextMap(
    initialValue: Map<K, V>?,
    context: Context,
    keyAdapter: TextAdapter<K>,
    valueAdapter: TextAdapter<V>,
    title: CharSequence
): Map<K, V>? {
    val editableValue = withContext(Dispatchers.Default) {
        initialValue?.map { it.key to it.value }?.toMutableList() ?: mutableListOf()
    }

    val recyclerAdapter = EditableTextMapAdapter(
        context,
        editableValue,
        keyAdapter,
        valueAdapter,
    )

    val result = requestEditableListOverlay(context, recyclerAdapter, title) {
        val newItem = requestModelInputEntry(context, title)

        if (newItem != null) {
            recyclerAdapter.addElement(newItem.first, newItem.second)
        }
    }

    return when (result) {
        EditableListOverlayResult.Cancel -> initialValue
        EditableListOverlayResult.Apply -> recyclerAdapter.values.toMap()
        EditableListOverlayResult.Reset -> null
    }
}

private suspend fun requestModelInputEntry(
    context: Context,
    title: CharSequence
): Pair<String, String>? {
    val key = context.requestModelTextInput(initial = "", title = title,
        hint = context.getText(R.string.key)).trim()
    if (key.isEmpty()) return null
    val value = context.requestModelTextInput(initial = "", title = title,
        hint = context.getText(R.string.value)).trim()
    return if (value.isEmpty()) null else key to value
}
