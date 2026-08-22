package com.comunidapp.app.data.model

internal fun argentinaLocationSeed(): List<LocationNode> {
    val nodes = mutableListOf<LocationNode>()
    fun add(
        id: String,
        name: String,
        level: LocationLevel,
        parentId: String? = null,
        order: Int,
        code: String? = null,
        aliases: List<String> = emptyList(),
        lat: Double? = null,
        lng: Double? = null
    ) {
        nodes += LocationNode(
            id = id,
            name = name,
            level = level,
            parentId = parentId,
            order = order,
            code = code,
            aliases = aliases,
            centroidLat = lat,
            centroidLng = lng
        )
    }

    add("loc-ar", "Argentina", LocationLevel.COUNTRY, order = 0, code = "AR", aliases = listOf("AR", "ARG"))

    val provinces = listOf(
        Triple("loc-ar-prov-caba", "Ciudad Autónoma de Buenos Aires", listOf("CABA", "Capital Federal")),
        Triple("loc-ar-prov-buenos-aires", "Buenos Aires", listOf("Provincia de Buenos Aires", "PBA", "Bs As")),
        Triple("loc-ar-prov-catamarca", "Catamarca", emptyList()),
        Triple("loc-ar-prov-chaco", "Chaco", emptyList()),
        Triple("loc-ar-prov-chubut", "Chubut", emptyList()),
        Triple("loc-ar-prov-cordoba", "Córdoba", listOf("Cordoba")),
        Triple("loc-ar-prov-corrientes", "Corrientes", emptyList()),
        Triple("loc-ar-prov-entre-rios", "Entre Ríos", listOf("Entre Rios")),
        Triple("loc-ar-prov-formosa", "Formosa", emptyList()),
        Triple("loc-ar-prov-jujuy", "Jujuy", emptyList()),
        Triple("loc-ar-prov-la-pampa", "La Pampa", emptyList()),
        Triple("loc-ar-prov-la-rioja", "La Rioja", emptyList()),
        Triple("loc-ar-prov-mendoza", "Mendoza", emptyList()),
        Triple("loc-ar-prov-misiones", "Misiones", emptyList()),
        Triple("loc-ar-prov-neuquen", "Neuquén", listOf("Neuquen")),
        Triple("loc-ar-prov-rio-negro", "Río Negro", listOf("Rio Negro")),
        Triple("loc-ar-prov-salta", "Salta", emptyList()),
        Triple("loc-ar-prov-san-juan", "San Juan", emptyList()),
        Triple("loc-ar-prov-san-luis", "San Luis", emptyList()),
        Triple("loc-ar-prov-santa-cruz", "Santa Cruz", emptyList()),
        Triple("loc-ar-prov-santa-fe", "Santa Fe", emptyList()),
        Triple("loc-ar-prov-santiago", "Santiago del Estero", emptyList()),
        Triple("loc-ar-prov-tierra-del-fuego", "Tierra del Fuego", listOf("Tierra del Fuego, Antártida e Islas del Atlántico Sur")),
        Triple("loc-ar-prov-tucuman", "Tucumán", listOf("Tucuman"))
    )
    provinces.forEachIndexed { index, (id, name, aliases) ->
        add(id, name, LocationLevel.PROVINCE, parentId = "loc-ar", order = index + 1, code = "AR-${id.removePrefix("loc-ar-prov-")}", aliases = aliases)
    }

    val ba = "loc-ar-prov-buenos-aires"
    val caba = "loc-ar-prov-caba"
    val cordoba = "loc-ar-prov-cordoba"
    val santaFe = "loc-ar-prov-santa-fe"

    val baMunicipalities = listOf(
        "loc-ar-mun-san-vicente" to "San Vicente",
        "loc-ar-mun-almirante-brown" to "Almirante Brown",
        "loc-ar-mun-la-plata" to "La Plata",
        "loc-ar-mun-lomas" to "Lomas de Zamora",
        "loc-ar-mun-quilmes" to "Quilmes",
        "loc-ar-mun-avellaneda" to "Avellaneda",
        "loc-ar-mun-lanus" to "Lanús",
        "loc-ar-mun-esteban-echeverria" to "Esteban Echeverría",
        "loc-ar-mun-ezeiza" to "Ezeiza",
        "loc-ar-mun-presidente-peron" to "Presidente Perón",
        "loc-ar-mun-canuelas" to "Cañuelas",
        "loc-ar-mun-brandsen" to "Brandsen",
        "loc-ar-mun-berazategui" to "Berazategui",
        "loc-ar-mun-florencio-varela" to "Florencio Varela"
    )
    baMunicipalities.forEachIndexed { index, (id, name) ->
        add(id, name, LocationLevel.MUNICIPALITY, parentId = ba, order = index + 1)
    }
    add("loc-ar-mun-caba", "CABA", LocationLevel.MUNICIPALITY, parentId = caba, order = 1, aliases = listOf("Ciudad Autónoma de Buenos Aires"))
    add("loc-ar-mun-cordoba", "Córdoba", LocationLevel.MUNICIPALITY, parentId = cordoba, order = 1, aliases = listOf("Capital"))
    add("loc-ar-mun-rosario", "Rosario", LocationLevel.MUNICIPALITY, parentId = santaFe, order = 1)

    val sanVicenteLocs = listOf(
        "loc-ar-loc-san-vicente" to "San Vicente",
        "loc-ar-loc-alejandro-korn" to "Alejandro Korn",
        "loc-ar-loc-domselaar" to "Domselaar"
    )
    sanVicenteLocs.forEachIndexed { index, (id, name) ->
        add(id, name, LocationLevel.LOCALITY, parentId = "loc-ar-mun-san-vicente", order = index + 1)
    }
    val brownLocs = listOf(
        "loc-ar-loc-adrogué" to "Adrogué",
        "loc-ar-loc-burzaco" to "Burzaco",
        "loc-ar-loc-claypole" to "Claypole",
        "loc-ar-loc-glew" to "Glew",
        "loc-ar-loc-jose-marmol" to "José Mármol",
        "loc-ar-loc-longchamps" to "Longchamps",
        "loc-ar-loc-ministro-rivadavia" to "Ministro Rivadavia",
        "loc-ar-loc-rafael-calzada" to "Rafael Calzada",
        "loc-ar-loc-san-jose" to "San José"
    )
    brownLocs.forEachIndexed { index, (id, name) ->
        add(id, name, LocationLevel.LOCALITY, parentId = "loc-ar-mun-almirante-brown", order = index + 1)
    }
    listOf(
        "loc-ar-loc-la-plata" to "La Plata",
        "loc-ar-loc-city-bell" to "City Bell",
        "loc-ar-loc-gonnet" to "Gonnet"
    ).forEachIndexed { index, (id, name) ->
        add(id, name, LocationLevel.LOCALITY, parentId = "loc-ar-mun-la-plata", order = index + 1)
    }
    listOf(
        "loc-ar-loc-lomas" to "Lomas de Zamora",
        "loc-ar-loc-banfield" to "Banfield",
        "loc-ar-loc-temperley" to "Temperley"
    ).forEachIndexed { index, (id, name) ->
        add(id, name, LocationLevel.LOCALITY, parentId = "loc-ar-mun-lomas", order = index + 1)
    }
    listOf(
        "loc-ar-loc-avellaneda" to "Avellaneda",
        "loc-ar-loc-wilde" to "Wilde",
        "loc-ar-loc-dock-sud" to "Dock Sud"
    ).forEachIndexed { index, (id, name) ->
        add(id, name, LocationLevel.LOCALITY, parentId = "loc-ar-mun-avellaneda", order = index + 1)
    }
    listOf(
        "loc-ar-loc-quilmes" to "Quilmes",
        "loc-ar-loc-bernal" to "Bernal"
    ).forEachIndexed { index, (id, name) ->
        add(id, name, LocationLevel.LOCALITY, parentId = "loc-ar-mun-quilmes", order = index + 1)
    }

    val cabaBarrios = listOf(
        "Palermo", "Villa Crespo", "Caballito", "Recoleta", "Belgrano",
        "Almagro", "Flores", "San Telmo", "Núñez", "Colegiales"
    )
    cabaBarrios.forEachIndexed { index, name ->
        val slug = name.lowercase()
            .replace("ú", "u")
            .replace("é", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("á", "a")
            .replace(" ", "-")
        add("loc-ar-loc-$slug", name, LocationLevel.LOCALITY, parentId = "loc-ar-mun-caba", order = index + 1)
    }
    add("loc-ar-loc-cordoba-cap", "Córdoba", LocationLevel.LOCALITY, parentId = "loc-ar-mun-cordoba", order = 1)
    add("loc-ar-loc-rosario", "Rosario", LocationLevel.LOCALITY, parentId = "loc-ar-mun-rosario", order = 1)

    add(
        "loc-ar-zone-korn-centro",
        "Centro",
        LocationLevel.ZONE,
        parentId = "loc-ar-loc-alejandro-korn",
        order = 1
    )
    add(
        "loc-ar-zone-korn-parque",
        "Barrio Parque",
        LocationLevel.ZONE,
        parentId = "loc-ar-loc-alejandro-korn",
        order = 2
    )
    add(
        "loc-ar-zone-adrogué-centro",
        "Centro",
        LocationLevel.ZONE,
        parentId = "loc-ar-loc-adrogué",
        order = 1
    )
    add(
        "loc-ar-zone-palermo-soho",
        "Palermo Soho",
        LocationLevel.ZONE,
        parentId = "loc-ar-loc-palermo",
        order = 1,
        aliases = listOf("Soho")
    )

    return nodes
}
