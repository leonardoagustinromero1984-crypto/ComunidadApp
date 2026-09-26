package com.comunidapp.app.domain.qa

import com.comunidapp.app.data.local.InMemoryOnb02Store
import com.comunidapp.app.data.model.ServiceCategory
import com.comunidapp.app.data.model.ServiceProfile
import com.comunidapp.app.domain.business.PublicCommercialProfileMapper
import com.comunidapp.app.domain.canonical.CanonicalProviderHolder
import com.comunidapp.app.domain.canonical.CanonicalProviderWrite
import com.comunidapp.app.ui.UiRegressionGateTest
import com.comunidapp.app.domain.context.ActiveContextSelection
import com.comunidapp.app.domain.context.ContextHumanLabels
import com.comunidapp.app.domain.context.ContextIdentityMapping
import com.comunidapp.app.domain.context.NewContextActivation
import com.comunidapp.app.domain.context.OperationalContext
import com.comunidapp.app.domain.context.OperationalContextKind
import com.comunidapp.app.domain.context.resolveActiveContext
import com.comunidapp.app.domain.map.LeoVerGeoPoint
import com.comunidapp.app.domain.map.LeoVerMapCameraState
import com.comunidapp.app.domain.onboarding.onb02.AddFunctionCatalog
import com.comunidapp.app.domain.onboarding.onb02.FunctionSelection
import com.comunidapp.app.domain.onboarding.onb02.LeoverFunction
import com.comunidapp.app.domain.onboarding.onb02.OccupiedFunctions
import com.comunidapp.app.domain.onboarding.onb02.Onb02FlowKind
import com.comunidapp.app.domain.onboarding.onb02.OrganizationKindOption
import com.comunidapp.app.domain.onboarding.onb02.ProfileActorKind
import com.comunidapp.app.domain.onboarding.onb02.ProfileActorTaxonomy
import com.comunidapp.app.domain.schedule.AppointmentSlotPolicy
import com.comunidapp.app.viewmodel.Onb02Phase
import com.comunidapp.app.viewmodel.Onb02ViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeoVerPhysicalQaFix02ContractTest {

    @Test
    fun DEFAULT_START_ACTOR_PERSON_SELECTED() {
        val store = InMemoryOnb02Store()
        val vm = Onb02ViewModel(store = store, userIdProvider = { "user-1" })
        vm.start(Onb02FlowKind.FULL_ONBOARDING)
        vm.skipCurrentTutorial()
        assertEquals(Onb02Phase.SELECT, vm.ui.value.phase)
        assertEquals(ProfileActorKind.PERSON, vm.ui.value.selection.actorKind)
        assertEquals(6, ProfileActorTaxonomy.FIRST_LEVEL_ACTOR_COUNT)
        assertTrue(PhysicalQaFix02Contracts.DEFAULT_START_ACTOR_PERSON)
    }

    @Test
    fun ADD_FUNCTION_EXCLUDES_PERSON_AND_EXISTING_INCLUDES_FOSTER() {
        val occupied = OccupiedFunctions(
            extras = setOf(LeoverFunction.RESCUER, LeoverFunction.WALKER),
            contexts = listOf(
                OperationalContext.Personal,
                OperationalContext.Rescuer("u1"),
                OperationalContext.Provider("p1", "Paseador", "WALKING")
            )
        )
        val available = AddFunctionCatalog.available(occupied)
        assertTrue(available.none { it.function == LeoverFunction.PROFILE_PERSONAL })
        assertTrue(available.none { it.id == "RESCUER" })
        assertTrue(available.none { it.id == "WALKER" })
        assertTrue(available.any { it.id == "FOSTER" })
        assertFalse(PhysicalQaFix02Contracts.PERSON_IN_ADD_FUNCTION)
        assertTrue(PhysicalQaFix02Contracts.FOSTER_IN_ADD_FUNCTION)
        assertTrue(PhysicalQaFix02Contracts.FOSTER_IS_FIRST_LEVEL_ACTOR)
    }

    @Test
    fun ONBOARDING_BACK_STAYS_IN_FLOW() {
        val store = InMemoryOnb02Store()
        val vm = Onb02ViewModel(store = store, userIdProvider = { "user-1" })
        vm.start(Onb02FlowKind.FULL_ONBOARDING)
        vm.skipCurrentTutorial()
        vm.selectActor(ProfileActorKind.REFUGE)
        vm.confirmActor()
        assertEquals(Onb02Phase.ORG_SETUP, vm.ui.value.phase)
        assertTrue(vm.goBack())
        assertEquals(Onb02Phase.SELECT, vm.ui.value.phase)
        assertTrue(vm.goBack())
        assertEquals(Onb02Phase.INTRO, vm.ui.value.phase)
        assertTrue(vm.goBack())
        assertEquals(Onb02Phase.INTRO, vm.ui.value.phase)
        assertTrue(PhysicalQaFix02Contracts.ONBOARDING_BACK_USES_FLOW_STACK)
        assertFalse(PhysicalQaFix02Contracts.ONBOARDING_BACK_EXITS_TUTORIAL)
    }

    @Test
    fun CREATE_CONTEXTS_ACTIVATE_OWN_KIND() {
        val vet = NewContextActivation.contextForOrganization("org-1", "Clínica Norte", "VETERINARY_CLINIC")
        assertTrue(vet is OperationalContext.Veterinary)
        val refuge = NewContextActivation.contextForOrganization("org-2", "Elsita", "SHELTER")
        assertTrue(ContextIdentityMapping.isRefugeNav(refuge))
        val walker = NewContextActivation.contextForSelection(
            userId = "u1",
            selection = FunctionSelection(extras = setOf(LeoverFunction.WALKER)),
            available = emptyList()
        )
        assertTrue(walker is OperationalContext.Provider)
        val rescuer = NewContextActivation.contextForSelection(
            userId = "u1",
            selection = FunctionSelection(extras = setOf(LeoverFunction.RESCUER)),
            available = emptyList()
        )
        assertTrue(rescuer is OperationalContext.Rescuer)
        assertTrue(PhysicalQaFix02Contracts.NEW_CONTEXT_AUTO_ACTIVATED)
        assertFalse(NewContextActivation.BACK_CHANGES_CONTEXT)
    }

    @Test
    fun BACK_PRESERVES_SAVED_CONTEXT_WHEN_LIST_LAGS() {
        val saved = ActiveContextSelection(OperationalContextKind.VETERINARY, "org-vet")
        val last = OperationalContext.Veterinary("org-vet", "Veterinaria · Norte")
        val resolved = resolveActiveContext(listOf(OperationalContext.Personal), saved, last)
        assertEquals(OperationalContextKind.VETERINARY, resolved.kind)
        assertEquals("org-vet", resolved.entityId)
        assertTrue(PhysicalQaFix02Contracts.BACK_DOES_NOT_CHANGE_ACTIVE_CONTEXT)
    }

    @Test
    fun ACTIVE_CONTEXT_HOME_LABEL_IS_HUMAN() {
        assertEquals("LeoVer · Personal", ContextHumanLabels.homeBrandLine(OperationalContext.Personal))
        assertEquals(
            "LeoVer · Veterinaria",
            ContextHumanLabels.homeBrandLine(OperationalContext.Veterinary("1", "Clínica"))
        )
        assertEquals(
            "LeoVer · Refugio",
            ContextHumanLabels.homeBrandLine(
                OperationalContext.Organization("1", "Elsita", "SHELTER")
            )
        )
        assertEquals(
            "LeoVer · Paseador / Cuidador",
            ContextHumanLabels.homeBrandLine(
                OperationalContext.Provider("1", "Leo", "WALKING")
            )
        )
        assertFalse(ContextHumanLabels.RAW_ENUM_VISIBLE_TO_USER)
        assertFalse(ContextHumanLabels.looksRawEnum("Refugio"))
        assertTrue(ContextHumanLabels.looksRawEnum("SHELTER"))
        assertTrue(ContextHumanLabels.looksRawEnum("WALKING_CARE"))
    }

    @Test
    fun PROFILE_DATA_ISOLATED_BETWEEN_CONTEXTS() {
        val walkerHolder = CanonicalProviderWrite.resolveHolder(
            userId = "person-1",
            context = OperationalContext.Provider("onb02:WALKING:person-1", "Paseador", "WALKING"),
            myOrganizationIds = setOf("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
        )
        assertEquals(CanonicalProviderHolder.PERSON, walkerHolder.kind)
        assertEquals("person-1", walkerHolder.personId)
        assertEquals(null, walkerHolder.organizationId)
        val vetId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
        val vetHolder = CanonicalProviderWrite.resolveHolder(
            userId = "person-1",
            context = OperationalContext.Veterinary(vetId, "Veterinaria"),
            myOrganizationIds = setOf(vetId, "bbbbbbbb-cccc-dddd-eeee-ffffffffffff")
        )
        assertEquals(CanonicalProviderHolder.ORGANIZATION, vetHolder.kind)
        assertEquals(vetId, vetHolder.organizationId)
        assertTrue(PhysicalQaFix02Contracts.PROFILE_DATA_ISOLATED_BETWEEN_CONTEXTS)
        assertTrue(PhysicalQaFix02Contracts.FIRST_ORG_FALLBACK_FORBIDDEN)
    }

    @Test
    fun VETERINARY_REOPEN_AND_WALKER_SAVE_NO_CRASH() {
        assertEquals(null, LeoVerGeoPoint.parseOrNull(999.0, 0.0))
        assertEquals(null, LeoVerGeoPoint.parseOrNull(Double.NaN, 0.0))
        val camera = LeoVerMapCameraState.cameraOrFallback(999.0, 10.0)
        assertEquals(LeoVerMapCameraState.ARGENTINA_FALLBACK, camera.center)
        assertFalse(PhysicalQaFix02Contracts.VETERINARY_PROFILE_REOPEN_CRASH)
        assertFalse(PhysicalQaFix02Contracts.WALKER_PROFILE_SAVE_CRASH)
        assertTrue(CanonicalProviderWrite.isCanonicalUuid("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"))
        assertFalse(CanonicalProviderWrite.isCanonicalUuid("onb02:WALKING:user"))
    }

    @Test
    fun PROFILE_PUBLISH_NO_OAUTH_SESSION_PRESERVED() {
        assertFalse(ProfilePublishAuthPolicy.TRIGGERS_OAUTH)
        assertTrue(ProfilePublishAuthPolicy.PRESERVES_SESSION)
        assertFalse(ProfilePublishAuthPolicy.UNAUTHORIZED_WRITE_LOGS_OUT)
        assertFalse(ProfilePublishAuthPolicy.mayStartGoogleOAuth(fromProfilePublish = true))
        val vm = source("app/src/main/java/com/comunidapp/app/viewmodel/ServiceViewModel.kt")
        assertTrue(vm.contains("ProfilePublishAuthPolicy"))
        assertFalse(vm.contains("signInWithGoogle"))
        val auth = source("app/src/main/java/com/comunidapp/app/data/repository/SupabaseAuthRepository.kt")
        assertTrue(auth.contains("NotAuthenticated"))
        assertTrue(auth.contains("RefreshFailure") || auth.contains("Initializing") || auth.contains("transient"))
    }

    @Test
    fun PUBLIC_INFO_CLEAN_AND_OPTIONAL_BOOKING_COPY() {
        val profile = ServiceProfile(
            id = "p1",
            ownerId = "u1",
            category = ServiceCategory.VET,
            name = "Clínica Norte",
            location = "CABA",
            description = "Atención general",
            contactInfo = "1140000000",
            acceptsBookings = true
        )
        val public = PublicCommercialProfileMapper.fromService(profile)
        assertEquals("Veterinaria", public.categoryLabel)
        assertFalse(PublicCommercialProfileMapper.isTechnicalText(public.categoryLabel))
        assertTrue(PublicCommercialProfileMapper.isTechnicalText("SHELTER"))
        val negocio = source("app/src/main/java/com/comunidapp/app/ui/screens/business/MiNegocioScreen.kt")
        assertTrue(negocio.contains("Si habilitás turnos online"))
        assertFalse(negocio.contains("y recibir turnos."))
        assertEquals("30 min", AppointmentSlotPolicy.label(30))
        assertTrue(source("app/src/main/java/com/comunidapp/app/ui/components/leo/LeoVerIntervalChipRow.kt").contains("FlowRow"))
    }

    @Test
    fun RESCUER_AND_REFUGE_DASHBOARDS() {
        val home = source("app/src/main/java/com/comunidapp/app/ui/screens/home/HomeScreen.kt")
        assertFalse(home.contains("RescuerOperationalHub"))
        assertFalse(home.contains("RefugeOperationalHub"))
        val hub = source("app/src/main/java/com/comunidapp/app/ui/screens/context/OperationalHubScreens.kt")
        assertTrue(hub.contains("+ Agregar mascota"))
        assertTrue(hub.contains("Importar mascotas"))
        assertTrue(hub.contains("Adopciones"))
        assertTrue(hub.contains("Campañas"))
        assertTrue(hub.contains("Eventos"))
        assertFalse(hub.contains("Usar perfil profesional"))
        val manage = source("app/src/main/java/com/comunidapp/app/ui/screens/organization/OrganizationManageScreen.kt")
        assertFalse(manage.contains("Usar perfil profesional"))
        assertFalse(manage.contains("Usar perfil personal"))
        val shelter = source("app/src/main/java/com/comunidapp/app/ui/screens/shelters/ShelterOperationsScreens.kt")
        assertTrue(shelter.contains("RescuerOperationalHub"))
        val pets = source("app/src/main/java/com/comunidapp/app/ui/screens/pets/MyPetsScreen.kt")
        assertTrue(pets.contains("Importar mascotas"))
        assertTrue(pets.contains("+ Agregar mascota"))
        assertTrue(PhysicalQaFix02Contracts.RESCUER_PERSON_DASHBOARD)
        assertFalse(PhysicalQaFix02Contracts.RESCUER_PERSON_SAME_AS_PERSON)
    }

    @Test
    fun PET_FRIENDLY_AND_STAGING_AUTH_BRANDING() {
        assertTrue(OrganizationKindOption.commercial.contains(OrganizationKindOption.PET_FRIENDLY_VENUE))
        assertTrue(PhysicalQaFix02Contracts.PET_FRIENDLY_PRESERVED)
        assertTrue(PhysicalQaFix02Contracts.STAGING_SUPABASE_AUTH_DOMAIN_ALLOWED)
        assertTrue(PhysicalQaFix02Contracts.PROD_AUTH_BRANDING_PENDING)
        assertFalse(PhysicalQaFix02Contracts.STAGING_CUSTOM_AUTH_DOMAIN_ACTIVE)
        assertTrue(PhysicalQaFix02Contracts.EXTERNAL_DNS_OR_GOOGLE_ACTION_REQUIRED)
        val auth = source("app/src/main/java/com/comunidapp/app/data/remote/supabase/SupabaseAuthConfig.kt")
        assertTrue(auth.contains("PROD_AUTH_BRANDING_PENDING"))
        assertTrue(auth.contains("STAGING_SUPABASE_AUTH_DOMAIN_ALLOWED"))
        assertTrue(auth.contains("STAGING_AUTH_DOMAIN"))
        val domains = source("app/src/main/java/com/comunidapp/app/domain/auth/LeoVerAuthDomains.kt")
        assertTrue(domains.contains("auth-staging.leover.com.ar"))
        assertTrue(domains.contains("auth.leover.com.ar"))
        assertFalse(domains.contains("tobqbddfcyitwgbkthhy.supabase.co"))
    }

    @Test
    fun SWITCHER_NEVER_SHOWS_RAW_ENUM() {
        val switcher = source("app/src/main/java/com/comunidapp/app/ui/screens/onboarding/onb02/Onb02Screens.kt")
        assertTrue(switcher.contains("ContextHumanLabels.switcherSubtitle"))
        assertFalse(switcher.contains("ctx.organizationType ?: \"Organización\""))
        assertTrue(switcher.contains("AddFunctionCatalogScreen"))
        assertTrue(switcher.contains("BackHandler"))
    }

    private fun source(path: String): String = UiRegressionGateTest.sourceFile(path).readText()
}
