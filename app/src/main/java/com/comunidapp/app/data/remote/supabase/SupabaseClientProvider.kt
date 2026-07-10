package com.comunidapp.app.data.remote.supabase

import com.comunidapp.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.PropertyConversionMethod
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

object SupabaseTables {
    const val USERS = "users"
    const val PETS = "pets"
    const val POSTS = "posts"
    const val ADOPTIONS = "adoptions"
    const val LOST_FOUND = "lost_found_posts"
    const val POST_LIKES = "post_likes"
    const val POST_COMMENTS = "post_comments"
    const val ADOPTION_REQUESTS = "adoption_requests"
    const val SHELTERS_TABLE = "shelters"
    const val FOSTER_HOMES = "foster_homes"
    const val COMMUNITY_EVENTS = "community_events"
    const val DONATION_CAMPAIGNS = "donation_campaigns"
    const val USER_BADGES = "user_badges"
    const val CONVERSATIONS = "conversations"
    const val CONVERSATION_PARTICIPANTS = "conversation_participants"
    const val MESSAGES = "messages"
    const val FRIEND_CONNECTIONS = "friend_connections"
    const val SERVICE_PROFILES = "service_profiles"
    const val SERVICE_BOOKINGS = "service_bookings"
    const val FOSTER_REQUESTS = "foster_requests"
    const val EVENT_INTERESTS = "event_interests"
    const val POST_SAVES = "post_saves"
    const val USER_BLOCKS = "user_blocks"
    const val CONTENT_REPORTS = "content_reports"
    const val LOST_FOUND_SIGHTINGS = "lost_found_sightings"
    const val NOTIFICATIONS = "notifications"
    const val SERVICE_REVIEWS = "service_reviews"
    const val SHOP_PRODUCTS = "shop_products"
    const val PAYMENT_INTENTS = "payment_intents"
    const val ADOPTION_MATCHES = "adoption_matches"
    const val PET_CLINICAL_RECORDS = "pet_clinical_records"
}

object SupabaseClientProvider {

    val instance: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Auth) {
                scheme = SupabaseAuthConfig.SCHEME
                host = SupabaseAuthConfig.HOST
            }
            install(Postgrest) {
                propertyConversionMethod = PropertyConversionMethod.SERIAL_NAME
            }
            install(Storage)
            install(Realtime)
        }
    }
}

val supabase: SupabaseClient
    get() = SupabaseClientProvider.instance
