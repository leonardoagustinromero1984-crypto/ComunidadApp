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
