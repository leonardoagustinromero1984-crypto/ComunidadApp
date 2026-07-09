package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import com.comunidapp.app.data.model.AdoptionEvent
import com.comunidapp.app.data.model.DonationCampaign
import com.comunidapp.app.data.model.FosterHomeListing
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.CommunityRepository
import kotlinx.coroutines.flow.StateFlow

class CommunityViewModel(
    private val communityRepository: CommunityRepository = DataProvider.communityRepository
) : ViewModel() {

    val fosterHomes: StateFlow<List<FosterHomeListing>> =
        communityRepository.observeFosterHomes()

    val events: StateFlow<List<AdoptionEvent>> =
        communityRepository.observeEvents()

    val donations: StateFlow<List<DonationCampaign>> =
        communityRepository.observeDonations()
}
