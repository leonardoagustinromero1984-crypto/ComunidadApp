package com.comunidapp.app.domain.onboarding.onb02

enum class TutorialId(val key: String, val version: Int) {
    T00_MULTI_FUNCTION_INTRO("T00_MULTI_FUNCTION_INTRO", 1),
    T01_PROFILE_PERSONAL("T01_PROFILE_PERSONAL", 1),
    T02_RESCUER("T02_RESCUER", 1),
    T03_FOSTER("T03_FOSTER", 1),
    T04_VETERINARY_PROFESSIONAL("T04_VETERINARY_PROFESSIONAL", 1),
    T05_WALKER("T05_WALKER", 1),
    T06_CAREGIVER("T06_CAREGIVER", 1),
    T07_TRAINER("T07_TRAINER", 1),
    T08_GROOMING("T08_GROOMING", 1),
    T09_DAYCARE("T09_DAYCARE", 1),
    T10_ORGANIZATION("T10_ORGANIZATION", 1),
    T10A_VETERINARY_CLINIC("T10A_VETERINARY_CLINIC", 1),
    T10B_SHELTER("T10B_SHELTER", 1),
    T10C_SHOP("T10C_SHOP", 1),
    T10D_ORG_DAYCARE("T10D_ORG_DAYCARE", 1),
    T10E_OTHER_SERVICE("T10E_OTHER_SERVICE", 1),
    T11_USE_LEOVER_AS("T11_USE_LEOVER_AS", 1),
    T12_COMMERCIAL_PROFESSIONAL("T12_COMMERCIAL_PROFESSIONAL", 1),
    T13_COMMERCIAL_ORGANIZATION("T13_COMMERCIAL_ORGANIZATION", 1),
    T14_ORG_JOIN_MEMBER("T14_ORG_JOIN_MEMBER", 1),
    T15_ORG_JOIN_ADMIN("T15_ORG_JOIN_ADMIN", 1),
    T16_GROOMING_ORG("T16_GROOMING_ORG", 1),
    T17_WALKING_CARE_ORG("T17_WALKING_CARE_ORG", 1),
    T18_TRAINING_ORG("T18_TRAINING_ORG", 1),
    T19_BRAND_ORG("T19_BRAND_ORG", 1),
    T20_VITACORA_IMPORT("T20_VITACORA_IMPORT", 1);

    companion object {
        fun fromKey(raw: String): TutorialId? = entries.firstOrNull { it.key == raw }
    }
}

enum class TutorialVisual {
    HOME,
    VITACORA,
    COMMUNITY_HELP,
    SERVICES,
    FUNCTIONS,
    PERSONAL,
    RESCUER,
    FOSTER,
    VET,
    PROVIDER,
    DAYCARE,
    ORGANIZATION,
    SWITCHER,
    GENERIC
}

data class TutorialStep(
    val title: String,
    val body: String,
    val primaryCta: String = "Siguiente",
    val secondaryCta: String? = null,
    val chips: List<String> = emptyList(),
    val visual: TutorialVisual = TutorialVisual.GENERIC,
    val titleIsVitacoraWordmark: Boolean = false,
    val highlight: String? = null
)

data class TutorialDefinition(
    val id: TutorialId,
    val libraryTitle: String,
    val steps: List<TutorialStep>
) {
    init {
        require(steps.isNotEmpty())
    }
}

object TutorialCatalog {

    val all: List<TutorialDefinition> = listOf(
        t00(), t01(), t02(), t03(), t04(), t05(), t06(), t07(), t08(), t09(), t10(),
        t10a(), t10b(), t10c(), t10d(), t10e(), t11(),
        t12(), t13(), t14(), t15(), t16(), t17(), t18(), t19(), t20()
    )

    fun definition(id: TutorialId): TutorialDefinition =
        all.first { it.id == id }

    fun libraryEntries(): List<TutorialDefinition> = all

    private fun t00() = TutorialDefinition(
        id = TutorialId.T00_MULTI_FUNCTION_INTRO,
        libraryTitle = "Conocé LeoVer",
        steps = listOf(
            TutorialStep(
                title = "Bienvenido a LeoVer",
                body = "Ellos nos acompañan todos los días. LeoVer nace para " +
                    "acompañarlos a ellos.\n\n" +
                    "Un lugar para cuidar su historia, compartir momentos, " +
                    "encontrar ayuda y conectar con una comunidad que quiere lo " +
                    "mismo que vos: su bienestar.",
                highlight = "Porque su vida también merece una red que la cuide.",
                visual = TutorialVisual.HOME,
                primaryCta = "Siguiente"
            ),
            TutorialStep(
                title = "VitaCora",
                body = "Su vida. Su historia. Sus cuidados.\n\n" +
                    "Cada mascota deja huellas en nuestra vida y tiene una historia única.\n\n" +
                    "VitaCora nace de vita —vida— y cora —corazón— para " +
                    "acompañar sus cuidados y su salud, y atesorar los momentos " +
                    "que forman parte de su camino.",
                highlight = "Una historia que crece junto a ella.",
                visual = TutorialVisual.VITACORA,
                titleIsVitacoraWordmark = true,
                primaryCta = "Siguiente"
            ),
            TutorialStep(
                title = "Una comunidad que está cuando hace falta",
                body = "A veces cuidar también es pedir ayuda. Y otras veces es " +
                    "estar para alguien que la necesita.\n\n" +
                    "En Sumate podés acompañar adopciones, pérdidas, encuentros " +
                    "y tránsitos.\n\n" +
                    "En Comunidad encontrás personas y servicios para cuidar " +
                    "mejor a tu mascota.",
                highlight = "Porque cuando nos conectamos, podemos hacer mucho más por " +
                    "ellos. Y cada conexión puede cambiar una historia.",
                visual = TutorialVisual.COMMUNITY_HELP,
                primaryCta = "Siguiente"
            ),
            TutorialStep(
                title = Onb02Copy.PROFILE_EXPLANATION_SLIDE_TITLE,
                body = "Persona\n" +
                    "Para tu perfil personal y tus mascotas.\n\n" +
                    "Rescatista independiente\n" +
                    "Si rescatás animales por tu cuenta.\n\n" +
                    "Refugio / Organización de rescate\n" +
                    "Si representás o formás parte de una entidad dedicada al " +
                    "rescate y adopción.\n\n" +
                    "Profesional independiente\n" +
                    "Si ofrecés servicios por tu cuenta, como paseos, peluquería, " +
                    "adiestramiento o atención veterinaria.\n\n" +
                    "Organización / Negocio\n" +
                    "Si representás un local, clínica, tienda, guardería, " +
                    "peluquería u otra empresa o equipo comercial.",
                highlight = "No te preocupes: después vas a poder agregar otros perfiles y cambiar entre ellos.",
                chips = listOf("Persona", "Rescatista", "Refugio", "Profesional", "Organización"),
                visual = TutorialVisual.FUNCTIONS,
                primaryCta = "Empezar"
            )
        )
    )

    private fun t01() = TutorialDefinition(
        id = TutorialId.T01_PROFILE_PERSONAL,
        libraryTitle = "Tu Perfil personal",
        steps = listOf(
            TutorialStep(
                title = "Tu espacio en LeoVer",
                body = "Tu Perfil personal es tu espacio principal en LeoVer. " +
                    "Desde acá podés participar de la comunidad, publicar, " +
                    "seguir perfiles y administrar tus mascotas.",
                chips = listOf("Comunidad", "Publicaciones", "Mascotas"),
                visual = TutorialVisual.PERSONAL
            ),
            TutorialStep(
                title = "Tus mascotas y VitaCora",
                body = "Cada mascota conserva su identidad y su VitaCora, donde " +
                    "puede reunirse su historia, cuidados, salud y otra " +
                    "información importante.\n\n" +
                    "Vos decidís qué información compartir y con quién.",
                chips = listOf("Identidad", "Salud", "Cuidados"),
                visual = TutorialVisual.VITACORA
            ),
            TutorialStep(
                title = "Tu perfil puede crecer con vos",
                body = "Si más adelante empezás a rescatar animales, ofrecer " +
                    "servicios, trabajar como profesional o representar una " +
                    "organización, no necesitás otra cuenta.\n\n" +
                    "Podés agregarlo desde ${Onb02Copy.TUTORIAL_ADD_FUNCTION_PATH}.",
                chips = listOf("Rescate", "Servicios", "Organización"),
                visual = TutorialVisual.FUNCTIONS,
                primaryCta = "Entendido"
            )
        )
    )

    private fun t02() = TutorialDefinition(
        id = TutorialId.T02_RESCUER,
        libraryTitle = "Rescatista",
        steps = listOf(
            TutorialStep(
                title = "Tu perfil de Rescatista",
                body = "Hay personas que eligen estar cuando una mascota más lo necesita.\n\n" +
                    "Como Rescatista, podés acompañar casos, organizar ayuda y " +
                    "colaborar con animales que necesitan una mano.\n\n" +
                    "Esta función es personal: seguís siendo vos, con tu misma " +
                    "cuenta y tu Perfil personal siempre disponible.",
                highlight = "Ayudar también es una forma de dejar huella.",
                visual = TutorialVisual.RESCUER,
                primaryCta = "Listo"
            )
        )
    )

    private fun t03() = TutorialDefinition(
        id = TutorialId.T03_FOSTER,
        libraryTitle = "Hogar de tránsito",
        steps = listOf(
            TutorialStep(
                title = "Abrir tu hogar también es ayudar",
                body = "Ser Hogar de tránsito es darle a una mascota un lugar " +
                    "seguro mientras espera el próximo paso de su historia.\n\n" +
                    "Desde LeoVer podés recibir solicitudes, organizar tus " +
                    "tránsitos y acompañar a cada mascota durante el tiempo que " +
                    "esté con vos.",
                highlight = "A veces un hogar por un tiempo puede cambiar una vida para siempre.",
                visual = TutorialVisual.FOSTER,
                primaryCta = "Siguiente"
            ),
            TutorialStep(
                title = "Su historia sigue con ella",
                body = "Cuando una mascota llega a tu hogar, no empieza de cero.\n\n" +
                    "Su identidad y su VitaCora siguen siendo las mismas. Con " +
                    "los permisos correspondientes, podés conocer la información " +
                    "necesaria para cuidarla y dejar registrados momentos " +
                    "importantes de su tránsito.",
                highlight = "Porque cada etapa de su camino también forma parte de su historia.",
                visual = TutorialVisual.VITACORA,
                primaryCta = "Listo"
            )
        )
    )

    private fun t04() = TutorialDefinition(
        id = TutorialId.T04_VETERINARY_PROFESSIONAL,
        libraryTitle = "Profesional veterinario",
        steps = listOf(
            TutorialStep(
                title = "Tu experiencia también acompaña",
                body = "Como Profesional veterinario, podés presentarte en LeoVer, " +
                    "gestionar tu perfil y acompañar el cuidado de las mascotas " +
                    "desde tu práctica profesional.\n\n" +
                    "Esta función es personal: no crea automáticamente una " +
                    "veterinaria ni una organización.",
                highlight = "Tu conocimiento también forma parte de su bienestar.",
                visual = TutorialVisual.VET,
                primaryCta = "Siguiente"
            ),
            TutorialStep(
                title = "Cuidar también es dejar información útil",
                body = "Cuando la persona responsable lo autoriza, podés consultar " +
                    "la información necesaria de una mascota y aportar datos " +
                    "relacionados con su atención.\n\n" +
                    "Todo queda vinculado a su VitaCora, respetando siempre los " +
                    "permisos y dejando claro quién realizó cada aporte.",
                highlight = "Así, cada cuidado puede acompañarla también después de la consulta.",
                visual = TutorialVisual.VITACORA,
                primaryCta = "Listo"
            )
        )
    )

    private fun t05() = TutorialDefinition(
        id = TutorialId.T05_WALKER,
        libraryTitle = "Paseador",
        steps = listOf(
            TutorialStep(
                title = "Tu perfil de Paseador",
                body = "Cada paseo es parte del cuidado, la rutina y el bienestar de una mascota.\n\n" +
                    "Como Paseador, podés mostrar tu servicio, indicar la zona " +
                    "donde trabajás y presentarte ante personas que buscan a " +
                    "alguien de confianza.",
                highlight = "Acompañar sus pasos también es una forma de cuidar.",
                visual = TutorialVisual.PROVIDER,
                primaryCta = "Listo"
            )
        )
    )

    private fun t06() = TutorialDefinition(
        id = TutorialId.T06_CAREGIVER,
        libraryTitle = "Cuidador de mascotas",
        steps = listOf(
            TutorialStep(
                title = "Tu perfil de Cuidador de mascotas",
                body = "Hay momentos en los que una mascota necesita compañía, " +
                    "atención y alguien en quien confiar.\n\n" +
                    "Como Cuidador de mascotas, podés mostrar tu servicio, " +
                    "contar cómo acompañás su cuidado y conectar con personas " +
                    "que buscan a alguien de confianza cuando no pueden estar.",
                highlight = "Cuidar también es estar presente cuando más lo necesitan.",
                visual = TutorialVisual.PROVIDER,
                primaryCta = "Listo"
            )
        )
    )

    private fun t07() = TutorialDefinition(
        id = TutorialId.T07_TRAINER,
        libraryTitle = "Educador / Adiestrador",
        steps = listOf(
            TutorialStep(
                title = "Tu perfil de Educador / Adiestrador",
                body = "Aprender también es parte de crecer juntos.\n\n" +
                    "Como Educador / Adiestrador, podés mostrar tu servicio, " +
                    "contar cómo trabajás y conectar con personas que buscan " +
                    "acompañar mejor el aprendizaje y bienestar de sus mascotas.",
                highlight = "Cada avance, por pequeño que sea, también forma parte de su camino.",
                visual = TutorialVisual.PROVIDER,
                primaryCta = "Listo"
            )
        )
    )

    private fun t08() = TutorialDefinition(
        id = TutorialId.T08_GROOMING,
        libraryTitle = "Peluquería",
        steps = listOf(
            TutorialStep(
                title = "Tu perfil de Peluquería",
                body = "El cuidado también se nota en esos pequeños detalles que " +
                    "hacen que una mascota se sienta bien.\n\n" +
                    "Como Peluquería, podés mostrar tu servicio, contar cómo " +
                    "trabajás y conectar con personas que buscan cuidar el " +
                    "bienestar y la higiene de sus mascotas.",
                highlight = "Porque sentirse bien también forma parte de estar bien.",
                visual = TutorialVisual.PROVIDER,
                primaryCta = "Listo"
            )
        )
    )

    private fun t09() = TutorialDefinition(
        id = TutorialId.T09_DAYCARE,
        libraryTitle = "Guardería",
        steps = listOf(
            TutorialStep(
                title = "Tu perfil de Guardería",
                body = "Cuando una mascota se queda lejos de casa, sentirse " +
                    "cuidada y acompañada hace toda la diferencia.\n\n" +
                    "Como Guardería, podés mostrar tu servicio, organizar " +
                    "reservas y recibir a las mascotas que van a compartir una " +
                    "estadía con vos.",
                highlight = "Un buen cuidado también puede hacer que se sientan como en casa.",
                visual = TutorialVisual.DAYCARE,
                primaryCta = "Siguiente"
            ),
            TutorialStep(
                title = "Cada estadía tiene su momento",
                body = "En Reservas organizás las próximas estadías y solicitudes.\n\n" +
                    "En Huéspedes encontrás a las mascotas que ya están " +
                    "actualmente bajo tu cuidado.\n\n" +
                    "Cuando corresponde, su estadía puede quedar conectada con " +
                    "su VitaCora para que esa etapa también forme parte de su historia.",
                highlight = "Porque incluso unos días lejos de casa también son parte de su camino.",
                visual = TutorialVisual.DAYCARE,
                primaryCta = "Listo"
            )
        )
    )

    private fun t10() = TutorialDefinition(
        id = TutorialId.T10_ORGANIZATION,
        libraryTitle = "Organización o negocio",
        steps = listOf(
            TutorialStep(
                title = "Tu organización también puede ser parte",
                body = "Si representás una veterinaria, tienda, guardería, " +
                    "peluquería, equipo profesional, empresa o marca, podés " +
                    "crear su perfil y administrarlo desde tu misma cuenta.\n\n" +
                    "Vos seguís siendo vos. Cuando uses el perfil de la " +
                    "organización, actuás en su nombre.",
                highlight = "Una sola cuenta para conectar también lo que hacés con la comunidad.",
                visual = TutorialVisual.ORGANIZATION,
                primaryCta = "Siguiente"
            ),
            TutorialStep(
                title = "Tu equipo, cada uno con su acceso",
                body = "Podés invitar a otras personas de LeoVer para que también " +
                    "formen parte de la organización.\n\n" +
                    "Elegís quiénes serán Administradores y qué herramientas " +
                    "podrá usar cada Miembro, según las tareas que necesite realizar.\n\n" +
                    "Cada persona entra con su propia cuenta y decide si acepta " +
                    "la invitación.",
                highlight = "Trabajar en equipo, sabiendo siempre quién hizo cada cosa.",
                visual = TutorialVisual.ORGANIZATION,
                primaryCta = "Listo"
            )
        )
    )

    private fun t10a() = TutorialDefinition(
        id = TutorialId.T10A_VETERINARY_CLINIC,
        libraryTitle = "Veterinaria",
        steps = listOf(
            TutorialStep(
                title = "Tu veterinaria, más cerca de la comunidad",
                body = "Desde el perfil de tu Veterinaria podés mostrar quiénes " +
                    "son, dónde están, sus horarios y los servicios que ofrecen.\n\n" +
                    "También podés compartir contenido y conectar con personas " +
                    "que buscan acompañamiento profesional para sus mascotas.",
                highlight = "Que encontrarte y conocerte también sea parte de cuidar.",
                visual = TutorialVisual.VET,
                primaryCta = "Siguiente"
            ),
            TutorialStep(
                title = "Cada atención puede seguir acompañando",
                body = "Cuando la persona responsable lo autoriza, los " +
                    "profesionales habilitados de tu veterinaria pueden consultar " +
                    "la información necesaria y aportar datos relacionados con " +
                    "la atención de una mascota.\n\n" +
                    "Todo puede quedar conectado con su VitaCora, manteniendo " +
                    "siempre quién realizó cada aporte.",
                highlight = "Porque un cuidado de hoy puede ser importante también mañana.",
                visual = TutorialVisual.VITACORA,
                primaryCta = "Listo"
            )
        )
    )

    private fun t10b() = TutorialDefinition(
        id = TutorialId.T10B_SHELTER,
        libraryTitle = "Refugio / ONG",
        steps = listOf(
            TutorialStep(
                title = "Cuando ayudar se convierte en una misión",
                body = "Desde el perfil de tu Refugio u ONG podés organizar la " +
                    "ayuda, acompañar rescates, impulsar adopciones y conectar " +
                    "con personas dispuestas a dar una mano.\n\n" +
                    "LeoVer te ayuda a reunir en un mismo lugar a tu equipo, las " +
                    "mascotas que acompañan y las acciones que llevan adelante.",
                highlight = "Porque detrás de cada rescate hay personas que decidieron no mirar para otro lado.",
                visual = TutorialVisual.ORGANIZATION,
                primaryCta = "Siguiente"
            ),
            TutorialStep(
                title = "Cada historia merece ser acompañada",
                body = "Una mascota puede pasar por un rescate, un tránsito, una " +
                    "recuperación y finalmente encontrar una familia.\n\n" +
                    "En LeoVer, todas esas etapas pueden acompañar a la misma " +
                    "mascota y a su VitaCora, sin perder su identidad en el camino.\n\n" +
                    "Desde la organización pueden coordinar casos, adopciones y " +
                    "ayuda, siempre respetando los accesos que correspondan.",
                highlight = "El objetivo no es solo ayudar hoy, sino acompañarla hasta que empiece una nueva etapa de su vida.",
                visual = TutorialVisual.VITACORA,
                primaryCta = "Listo"
            )
        )
    )

    private fun t10c() = TutorialDefinition(
        id = TutorialId.T10C_SHOP,
        libraryTitle = "Tienda",
        steps = listOf(
            TutorialStep(
                title = "Tu tienda en LeoVer",
                body = "Mostrá tus productos, compartí novedades y ayudá a que más " +
                    "personas conozcan lo que ofrecés para el cuidado de sus mascotas.\n\n" +
                    "Desde el perfil de tu Tienda podés publicar contenido y " +
                    "productos, y formar parte de la comunidad desde una presencia propia.",
                highlight = "Cada producto puede ser parte de una elección hecha pensando en su bienestar.",
                visual = TutorialVisual.SERVICES,
                primaryCta = "Listo"
            )
        )
    )

    private fun t10d() = TutorialDefinition(
        id = TutorialId.T10D_ORG_DAYCARE,
        libraryTitle = "Guardería de organización",
        steps = listOf(
            TutorialStep(
                title = "Tu guardería, más cerca de quienes buscan confianza",
                body = "Mostrá tu espacio, tus servicios, horarios y la forma en " +
                    "que acompañás a cada mascota durante su estadía.\n\n" +
                    "Desde el perfil de tu Guardería podés compartir novedades, " +
                    "recibir reservas y ayudar a que más personas conozcan cómo trabajás.",
                highlight = "Cuando alguien deja a su mascota con vos, también está dejando algo muy importante en tus manos.",
                visual = TutorialVisual.DAYCARE,
                primaryCta = "Siguiente"
            ),
            TutorialStep(
                title = "Cada estadía tiene su momento",
                body = "En Reservas organizás las próximas estadías y solicitudes.\n\n" +
                    "En Huéspedes encontrás a las mascotas que ya están " +
                    "actualmente bajo el cuidado de la guardería.\n\n" +
                    "Cuando corresponde, su estadía puede quedar conectada con " +
                    "su VitaCora para que esa etapa también forme parte de su historia.",
                highlight = "Cuidarlas durante su estadía también es acompañar una parte de su camino.",
                visual = TutorialVisual.DAYCARE,
                primaryCta = "Listo"
            )
        )
    )

    private fun t10e() = TutorialDefinition(
        id = TutorialId.T10E_OTHER_SERVICE,
        libraryTitle = "Otro servicio",
        steps = listOf(
            TutorialStep(
                title = "Otro servicio",
                body = "Configurá el perfil de la organización, su categoría, " +
                    "ubicación y las herramientas de servicio disponibles. " +
                    "Sigue siendo una ORGANIZATION, no otra cuenta personal.",
                chips = listOf("Categoría", "Ubicación", "Organización"),
                visual = TutorialVisual.ORGANIZATION,
                primaryCta = "Entendido"
            )
        )
    )

    private fun t11() = TutorialDefinition(
        id = TutorialId.T11_USE_LEOVER_AS,
        libraryTitle = "Usar LeoVer como",
        steps = listOf(
            TutorialStep(
                title = "LeoVer se adapta a vos",
                body = "En LeoVer siempre sos vos, aunque puedas participar de distintas maneras.\n\n" +
                    "Podés usar tu Perfil personal, ser rescatista, hogar de " +
                    "tránsito, profesional o representar una organización.\n\n" +
                    "Desde ${Onb02Copy.TUTORIAL_SWITCH_PATH} podés cambiar cuando quieras.",
                highlight = "Una sola cuenta, distintas formas de ser parte.",
                visual = TutorialVisual.SWITCHER,
                primaryCta = "Entendido"
            )
        )
    )

    private fun t12() = TutorialDefinition(
        id = TutorialId.T12_COMMERCIAL_PROFESSIONAL,
        libraryTitle = "LeoVer Comercial profesional",
        steps = listOf(
            TutorialStep(
                title = "Que más personas puedan encontrarte",
                body = "Tu trabajo también puede tener un lugar en Comunidad para " +
                    "que personas de tu zona puedan conocerte y encontrar los " +
                    "servicios que ofrecés.\n\n" +
                    "Durante tus primeros 90 días, podés mostrar tu perfil " +
                    "profesional en Comunidad, búsquedas y mapas sin cargo y sin " +
                    "agregar un medio de pago.\n\n" +
                    "Después elegís si querés continuar con LeoVer Comercial. " +
                    "Y si todavía no es el momento, tu perfil queda guardado " +
                    "para cuando quieras volver.",
                highlight = "Primero queremos que puedas probarlo y descubrir si LeoVer también puede ayudarte a hacer crecer lo que hacés.",
                visual = TutorialVisual.SERVICES,
                primaryCta = "Entendido"
            )
        )
    )

    private fun t13() = TutorialDefinition(
        id = TutorialId.T13_COMMERCIAL_ORGANIZATION,
        libraryTitle = "LeoVer Comercial organización",
        steps = listOf(
            TutorialStep(
                title = "Que más personas puedan encontrarte",
                body = "LeoVer también puede ayudarte a acercar tu trabajo a " +
                    "quienes buscan cuidar mejor a sus mascotas.\n\n" +
                    "Durante tus primeros 90 días, tu organización puede " +
                    "aparecer en Comunidad, búsquedas y mapas sin cargo y sin " +
                    "agregar un medio de pago.\n\n" +
                    "Después elegís si querés continuar con LeoVer Comercial. " +
                    "Y si todavía no es el momento, tu perfil queda guardado " +
                    "para cuando quieras volver.",
                highlight = "Queremos que primero puedas probarlo y descubrir si LeoVer también puede crecer junto a vos.",
                visual = TutorialVisual.ORGANIZATION,
                primaryCta = "Entendido"
            )
        )
    )

    private fun t14() = TutorialDefinition(
        id = TutorialId.T14_ORG_JOIN_MEMBER,
        libraryTitle = "Unirte a una organización",
        steps = listOf(
            TutorialStep(
                title = "Ahora también sos parte de la organización",
                body = "Desde ahora podés usar LeoVer en nombre de la organización " +
                    "con las herramientas que te hayan habilitado.\n\n" +
                    "Tu Perfil personal sigue siendo el mismo y podés cambiar " +
                    "entre ambos desde Perfil → Usar LeoVer como.\n\n" +
                    "Tus accesos pueden incluir publicaciones, ficha, servicios, " +
                    "reservas u otras tareas, según lo que la organización necesite.",
                highlight = "Una misma cuenta, un nuevo lugar desde donde participar.",
                visual = TutorialVisual.ORGANIZATION,
                primaryCta = "Entendido"
            )
        )
    )

    private fun t15() = TutorialDefinition(
        id = TutorialId.T15_ORG_JOIN_ADMIN,
        libraryTitle = "Administrar una organización",
        steps = listOf(
            TutorialStep(
                title = "Ahora también administrás la organización",
                body = "Como Administrador, podés gestionar la organización, su " +
                    "equipo y las herramientas que tenga disponibles en LeoVer.\n\n" +
                    "Tu Perfil personal sigue siendo el mismo y podés cambiar " +
                    "entre ambos desde Perfil → Usar LeoVer como.\n\n" +
                    "También podés invitar personas, definir sus accesos y " +
                    "acompañar el trabajo del equipo desde un mismo lugar.",
                highlight = "Una misma cuenta para organizar, compartir y crecer en equipo.",
                visual = TutorialVisual.ORGANIZATION,
                primaryCta = "Entendido"
            )
        )
    )

    private fun t16() = TutorialDefinition(
        id = TutorialId.T16_GROOMING_ORG,
        libraryTitle = "Peluquería de organización",
        steps = listOf(
            TutorialStep(
                title = "Tu peluquería en LeoVer",
                body = "Mostrá tu espacio, tus servicios y la forma en que " +
                    "acompañás el cuidado y bienestar de cada mascota.\n\n" +
                    "Desde el perfil de tu Peluquería podés compartir trabajos, " +
                    "novedades y toda la información que ayude a las personas a " +
                    "conocerte y elegirte con confianza.",
                highlight = "Porque sentirse bien también forma parte de estar bien.",
                visual = TutorialVisual.PROVIDER,
                primaryCta = "Listo"
            )
        )
    )

    private fun t17() = TutorialDefinition(
        id = TutorialId.T17_WALKING_CARE_ORG,
        libraryTitle = "Paseos y cuidado",
        steps = listOf(
            TutorialStep(
                title = "Tu equipo de paseos y cuidado en LeoVer",
                body = "Mostrá quiénes son, dónde trabajan y los servicios que " +
                    "ofrecen para acompañar a las mascotas en su día a día.\n\n" +
                    "Desde el perfil de tu organización podés compartir " +
                    "información, novedades y presentar a tu equipo para que más " +
                    "personas puedan conocerlos y elegirlos con confianza.",
                highlight = "Cuidar también es estar presentes cuando alguien necesita una mano.",
                visual = TutorialVisual.PROVIDER,
                primaryCta = "Listo"
            )
        )
    )

    private fun t18() = TutorialDefinition(
        id = TutorialId.T18_TRAINING_ORG,
        libraryTitle = "Educación / Adiestramiento",
        steps = listOf(
            TutorialStep(
                title = "Tu espacio de educación y adiestramiento en LeoVer",
                body = "Mostrá cómo trabajan, qué servicios ofrecen y de qué manera " +
                    "acompañan a las mascotas y a sus familias durante cada " +
                    "proceso de aprendizaje.\n\n" +
                    "Desde el perfil de la organización podés compartir " +
                    "información, novedades y presentar a tu equipo para que más " +
                    "personas puedan conocer su forma de trabajar.",
                highlight = "Cada aprendizaje compartido también puede fortalecer el vínculo.",
                visual = TutorialVisual.PROVIDER,
                primaryCta = "Listo"
            )
        )
    )

    private fun t19() = TutorialDefinition(
        id = TutorialId.T19_BRAND_ORG,
        libraryTitle = "Empresa o marca",
        steps = listOf(
            TutorialStep(
                title = "Tu marca también puede ser parte",
                body = "Una marca no es solo lo que vende. También tiene una " +
                    "historia, una forma de hacer las cosas y una manera de " +
                    "acompañar a quienes eligen sus productos.\n\n" +
                    "Desde el perfil de tu Empresa o marca podés presentar " +
                    "quiénes son, compartir productos, lanzamientos, novedades y " +
                    "contenido útil para la comunidad.\n\n" +
                    "Mostrá qué los inspira, qué los diferencia y cómo sus " +
                    "productos pueden formar parte del cuidado y bienestar de " +
                    "las mascotas.",
                highlight = "Porque cuando una marca conecta de verdad, también puede formar parte de su historia.",
                visual = TutorialVisual.ORGANIZATION,
                primaryCta = "Listo"
            )
        )
    )

    private fun t20() = TutorialDefinition(
        id = TutorialId.T20_VITACORA_IMPORT,
        libraryTitle = "Importar mascotas",
        steps = listOf(
            TutorialStep("Descargá la plantilla", "Usá el botón Descargar plantilla Excel. Es la única plantilla válida de LeoVer para crear VitaCoras en lote.", visual = TutorialVisual.ORGANIZATION),
            TutorialStep("Una mascota por fila", "Cada fila de la hoja MASCOTAS es una mascota. No combines varias en la misma línea.", visual = TutorialVisual.VITACORA),
            TutorialStep("No cambies los encabezados", "Dejá los títulos de columna como están. Si los modificás, el archivo no se va a poder analizar.", visual = TutorialVisual.ORGANIZATION),
            TutorialStep("Completá lo obligatorio", "Referencia interna, nombre, especie, sexo, estado, provincia y localidad son obligatorios.", visual = TutorialVisual.ORGANIZATION),
            TutorialStep("Referencia interna única", "Usá un código de tu organización, por ejemplo PATITAS-001. No se puede repetir dentro de la misma organización.", visual = TutorialVisual.ORGANIZATION),
            TutorialStep("Subí el archivo", "Elegí el .xlsx. Subirlo no crea mascotas: primero LeoVer analiza y te muestra un resumen.", visual = TutorialVisual.ORGANIZATION),
            TutorialStep("Revisá los resultados", "Vas a ver cuántas están listas, con observaciones, con errores o ya existentes.", visual = TutorialVisual.ORGANIZATION),
            TutorialStep("Corregí si hace falta", "Si hay errores, corregí el Excel y volvé a subirlo. Las filas con error no se importan.", visual = TutorialVisual.ORGANIZATION),
            TutorialStep("Confirmá la creación", "Solo ahí se crean las VitaCoras, cada una con su número público. El número no se elige en Excel.", visual = TutorialVisual.VITACORA),
            TutorialStep("Agregá las fotos después", "La importación deja las VitaCoras creadas, con foto pendiente.", visual = TutorialVisual.VITACORA),
            TutorialStep("Publicá en adopción cuando esté listo", "Importar no publica avisos. Cuando la mascota esté lista, publicá desde adopción.", visual = TutorialVisual.ORGANIZATION, primaryCta = "Listo")
        )
    )
}
