package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.AdoptionEvent
import com.comunidapp.app.data.model.DonationCampaign
import com.comunidapp.app.data.model.FosterHomeListing
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.model.UserBadge
import com.comunidapp.app.data.mock.InMemoryDataStore
import com.comunidapp.app.data.remote.supabase.CommunitySupabaseDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

interface CommunityRepository {
    fun observeFosterHomes(): StateFlow<List<FosterHomeListing>>
    fun observeEvents(): StateFlow<List<AdoptionEvent>>
    fun observeDonations(): StateFlow<List<DonationCampaign>>
    suspend fun createFosterHome(host: User, listing: FosterHomeListing): Result<String>
    suspend fun createEvent(organizer: User, event: AdoptionEvent): Result<String>
    suspend fun createDonationCampaign(organizer: User, campaign: DonationCampaign): Result<String>
    suspend fun fetchUserBadges(userId: String): List<UserBadge>
}

class MockCommunityRepository : CommunityRepository {
    override fun observeFosterHomes(): StateFlow<List<FosterHomeListing>> =
        InMemoryDataStore.fosterHomes

    override fun observeEvents(): StateFlow<List<AdoptionEvent>> =
        InMemoryDataStore.events

    override fun observeDonations(): StateFlow<List<DonationCampaign>> =
        InMemoryDataStore.donationCampaigns

    override suspend fun createFosterHome(host: User, listing: FosterHomeListing): Result<String> =
        InMemoryDataStore.addFosterHome(listing.copy(hostId = host.id, hostName = host.name))

    override suspend fun createEvent(organizer: User, event: AdoptionEvent): Result<String> =
        InMemoryDataStore.addEvent(event.copy(organizerId = organizer.id, organizerName = organizer.name))

    override suspend fun createDonationCampaign(organizer: User, campaign: DonationCampaign): Result<String> =
        InMemoryDataStore.addDonationCampaign(campaign.copy(organizerId = organizer.id))

    override suspend fun fetchUserBadges(userId: String): List<UserBadge> = emptyList()
}

class SupabaseCommunityRepository(
    private val dataSource: CommunitySupabaseDataSource = CommunitySupabaseDataSource()
) : CommunityRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _fosterHomes = MutableStateFlow<List<FosterHomeListing>>(emptyList())
    private val _events = MutableStateFlow<List<AdoptionEvent>>(emptyList())
    private val _donations = MutableStateFlow<List<DonationCampaign>>(emptyList())

    init {
        scope.launch {
            dataSource.observeFosterHomes().collect { _fosterHomes.value = it }
        }
        scope.launch {
            dataSource.observeEvents().collect { _events.value = it }
        }
        scope.launch {
            dataSource.observeDonations().collect { _donations.value = it }
        }
    }

    override fun observeFosterHomes(): StateFlow<List<FosterHomeListing>> = _fosterHomes.asStateFlow()
    override fun observeEvents(): StateFlow<List<AdoptionEvent>> = _events.asStateFlow()
    override fun observeDonations(): StateFlow<List<DonationCampaign>> = _donations.asStateFlow()

    override suspend fun createFosterHome(host: User, listing: FosterHomeListing): Result<String> =
        dataSource.createFosterHome(host, listing)

    override suspend fun createEvent(organizer: User, event: AdoptionEvent): Result<String> =
        dataSource.createEvent(organizer, event)

    override suspend fun createDonationCampaign(organizer: User, campaign: DonationCampaign): Result<String> =
        dataSource.createDonationCampaign(organizer, campaign)

    override suspend fun fetchUserBadges(userId: String): List<UserBadge> =
        dataSource.fetchUserBadges(userId)
}
