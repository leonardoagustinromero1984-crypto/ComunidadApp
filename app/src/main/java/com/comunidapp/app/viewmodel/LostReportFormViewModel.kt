package com.comunidapp.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.comunidapp.app.domain.pets.LostReportDraft
import com.comunidapp.app.domain.pets.LostReportDraftCodec

class LostReportFormViewModel(
    private val handle: SavedStateHandle
) : ViewModel() {
    fun read(): LostReportDraft? = LostReportDraftCodec.decode(handle.get<String>(KEY))

    fun write(draft: LostReportDraft) {
        handle[KEY] = LostReportDraftCodec.encode(draft)
    }

    fun clear() {
        handle.remove<String>(KEY)
    }

    companion object {
        const val KEY = "lost_report_draft"

        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                @Suppress("UNCHECKED_CAST")
                return LostReportFormViewModel(extras.createSavedStateHandle()) as T
            }
        }
    }
}
