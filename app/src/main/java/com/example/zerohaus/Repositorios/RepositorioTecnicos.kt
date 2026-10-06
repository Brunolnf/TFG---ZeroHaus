package com.example.zerohaus.Repositorios

import com.example.zerohaus.Modelos.Resena
import com.example.zerohaus.Modelos.Tecnico
import com.example.zerohaus.Util.getOrTimeout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * Directorio de profesionales (`/tecnicos`): listado en tiempo real, perfil
 * individual, perfil propio y edición. Incluye las coordenadas de las
 * ciudades españolas para situarlos en el mapa.
 */
class RepositorioTecnicos {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun uid() = auth.currentUser?.uid ?: ""

    companion object {
        // Coordenadas aproximadas del centro de las ciudades españolas más comunes
        private val CIUDADES_COORDS = mapOf(
            "madrid"         to (40.4168 to -3.7038),
            "barcelona"      to (41.3851 to 2.1734),
            "valencia"       to (39.4699 to -0.3763),
            "sevilla"        to (37.3882 to -5.9823),
            "zaragoza"       to (41.6488 to -0.8891),
            "málaga"         to (36.7196 to -4.4200),
            "malaga"         to (36.7196 to -4.4200),
            "murcia"         to (37.9922 to -1.1307),
            "palma"          to (39.5696 to 2.6502),
            "las palmas"     to (28.1235 to -15.4363),
            "bilbao"         to (43.2630 to -2.9350),
            "alicante"       to (38.3452 to -0.4815),
            "córdoba"        to (37.8882 to -4.7794),
            "cordoba"        to (37.8882 to -4.7794),
            "valladolid"     to (41.6523 to -4.7245),
            "vigo"           to (42.2314 to -8.7124),
            "gijón"          to (43.5453 to -5.6615),
            "gijon"          to (43.5453 to -5.6615),
            "granada"        to (37.1773 to -3.5986),
            "pamplona"       to (42.8125 to -1.6458),
            "santander"      to (43.4623 to -3.8099),
            "san sebastián"  to (43.3128 to -1.9761),
            "donostia"       to (43.3128 to -1.9761),
            "badajoz"        to (38.8794 to -6.9706),
            "almería"        to (36.8340 to -2.4637),
            "almeria"        to (36.8340 to -2.4637),
            "logroño"        to (42.4627 to -2.4449),
            "burgos"         to (42.3439 to -3.6969),
            "salamanca"      to (40.9701 to -5.6635),
            "albacete"       to (38.9943 to -1.8585),
            "a coruña"       to (43.3713 to -8.3962),
            "huelva"         to (37.2614 to -6.9447),
            "jaén"           to (37.7796 to -3.7849),
            "jaen"           to (37.7796 to -3.7849),
            "toledo"         to (39.8628 to -4.0273),
            "cáceres"        to (39.4753 to -6.3723),
            "caceres"        to (39.4753 to -6.3723),
            "lleida"         to (41.6176 to 0.6200),
            "tarragona"      to (41.1189 to 1.2445),
            "girona"         to (41.9794 to 2.8214),
            "lugo"           to (43.0097 to -7.5567),
            "ourense"        to (42.3364 to -7.8641),
            "pontevedra"     to (42.4328 to -8.6459),
            "oviedo"         to (43.3619 to -5.8494),
            "león"           to (42.5987 to -5.5671),
            "leon"           to (42.5987 to -5.5671),
            "tenerife"       to (28.4636 to -16.2518),
            "santa cruz"     to (28.4636 to -16.2518),
            "vitoria"        to (42.8467 to -2.6716),
            "gasteiz"        to (42.8467 to -2.6716),
            "castellón"      to (39.9860 to -0.0513),
            "castellon"      to (39.9860 to -0.0513),
            "palencia"       to (42.0095 to -4.5288),
            "soria"          to (41.7665 to -2.4790),
            "teruel"         to (40.3440 to -1.1069),
            "cuenca"         to (40.0704 to -2.1374),
            "guadalajara"    to (40.6334 to -3.1669),
            "ávila"          to (40.6566 to -4.6812),
            "avila"          to (40.6566 to -4.6812),
            "segovia"        to (40.9429 to -4.1088),
            "ciudad real"    to (38.9848 to -3.9274),
            "mérida"         to (38.9165 to -6.3437),
            "merida"         to (38.9165 to -6.3437),
            "ceuta"          to (35.8894 to -5.3198),
            "melilla"        to (35.2923 to -2.9381),
            "huesca"         to (42.1401 to -0.4089),
            "zamora"         to (41.5033 to -5.7446),
            // Falta histórica: Cádiz NO estaba y técnicos de allí caían al fallback
            // hash (con suerte aparecían en Bilbao o Granada). Añadida con las
            // demás capitales que se nos quedaron fuera y los grandes municipios
            // que la gente escribe en su perfil aunque no sean capital de provincia.
            "cádiz"          to (36.5298 to -6.2924),
            "cadiz"          to (36.5298 to -6.2924),
            "santiago de compostela" to (42.8782 to -8.5448),
            "santiago"       to (42.8782 to -8.5448),
            "cartagena"      to (37.6056 to -0.9966),
            "elche"          to (38.2682 to -0.7104),
            "elx"            to (38.2682 to -0.7104),
            "jerez de la frontera" to (36.6850 to -6.1261),
            "jerez"          to (36.6850 to -6.1261),
            "marbella"       to (36.5097 to -4.8854),
            "algeciras"      to (36.1408 to -5.4562),
            "hospitalet de llobregat" to (41.3596 to 2.0997),
            "l'hospitalet"   to (41.3596 to 2.0997),
            "hospitalet"     to (41.3596 to 2.0997),
            "badalona"       to (41.4500 to 2.2474),
            "terrassa"       to (41.5640 to 2.0089),
            "sabadell"       to (41.5483 to 2.1075),
            "mataró"         to (41.5388 to 2.4449),
            "mataro"         to (41.5388 to 2.4449),
            "reus"           to (41.1561 to 1.1069),
            "móstoles"       to (40.3223 to -3.8649),
            "mostoles"       to (40.3223 to -3.8649),
            "alcalá de henares" to (40.4818 to -3.3645),
            "alcala de henares" to (40.4818 to -3.3645),
            "fuenlabrada"    to (40.2842 to -3.7944),
            "leganés"        to (40.3267 to -3.7635),
            "leganes"        to (40.3267 to -3.7635),
            "getafe"         to (40.3057 to -3.7329),
            "alcorcón"       to (40.3458 to -3.8246),
            "alcorcon"       to (40.3458 to -3.8246),
            "torrejón de ardoz" to (40.4596 to -3.4737),
            "torrejon de ardoz" to (40.4596 to -3.4737),
            "parla"          to (40.2370 to -3.7681),
            "alcobendas"     to (40.5408 to -3.6418),
            "san sebastián de los reyes" to (40.5538 to -3.6233),
            "las rozas"      to (40.4929 to -3.8730),
            "majadahonda"    to (40.4731 to -3.8728),
            "pozuelo de alarcón" to (40.4318 to -3.8136),
            "vélez-málaga"   to (36.7825 to -4.1009),
            "velez-malaga"   to (36.7825 to -4.1009),
            "vélez málaga"   to (36.7825 to -4.1009),
            "fuengirola"     to (36.5397 to -4.6245),
            "mijas"          to (36.5957 to -4.6373),
            "torremolinos"   to (36.6203 to -4.4998),
            "estepona"       to (36.4283 to -5.1453),
            "benidorm"       to (38.5380 to -0.1316),
            "torrevieja"     to (37.9799 to -0.6826),
            "orihuela"       to (38.0848 to -0.9447),
            "elda"           to (38.4774 to -0.7929),
            "alcoy"          to (38.6989 to -0.4734),
            "alcoi"          to (38.6989 to -0.4734),
            "gandía"         to (38.9669 to -0.1813),
            "gandia"         to (38.9669 to -0.1813),
            "lorca"          to (37.6709 to -1.7019),
            "molina de segura" to (38.0541 to -1.2120),
            "dos hermanas"   to (37.2826 to -5.9237),
            "alcalá de guadaíra" to (37.3372 to -5.8429),
            "alcala de guadaira" to (37.3372 to -5.8429),
            "utrera"         to (37.1856 to -5.7805),
            "écija"          to (37.5409 to -5.0824),
            "ecija"          to (37.5409 to -5.0824),
            "linares"        to (38.0951 to -3.6360),
            "úbeda"          to (38.0152 to -3.3702),
            "ubeda"          to (38.0152 to -3.3702),
            "motril"         to (36.7510 to -3.5187),
            "roquetas de mar" to (36.7644 to -2.6147),
            "el ejido"       to (36.7768 to -2.8128),
            "talavera de la reina" to (39.9637 to -4.8323),
            "talavera"       to (39.9637 to -4.8323),
            "ferrol"         to (43.4823 to -8.2335),
            "narón"          to (43.5070 to -8.1556),
            "naron"          to (43.5070 to -8.1556),
            "lalín"          to (42.6611 to -8.1129),
            "lalin"          to (42.6611 to -8.1129),
            "miranda de ebro" to (42.6864 to -2.9476),
            "ponferrada"     to (42.5462 to -6.5919),
            "torrelavega"    to (43.3503 to -4.0479),
            "irún"           to (43.3389 to -1.7886),
            "irun"           to (43.3389 to -1.7886),
            "barakaldo"      to (43.2974 to -2.9883),
            "getxo"          to (43.3568 to -3.0118),
            "santa cruz de tenerife" to (28.4636 to -16.2518),
            "la laguna"      to (28.4853 to -16.3208),
            "san cristóbal de la laguna" to (28.4853 to -16.3208),
            "arona"          to (28.0995 to -16.6809),
            "telde"          to (27.9933 to -15.4197),
            "arrecife"       to (28.9637 to -13.5477),
            "ibiza"          to (38.9067 to 1.4206),
            "eivissa"        to (38.9067 to 1.4206),
            "manacor"        to (39.5703 to 3.2089),
            "vilanova i la geltrú" to (41.2237 to 1.7252),
            "vilanova"       to (41.2237 to 1.7252),
            "vic"            to (41.9301 to 2.2546),
            "manresa"        to (41.7287 to 1.8235),
            "igualada"       to (41.5807 to 1.6175)
        )

        /**
         * Devuelve coordenadas para el nombre de ciudad introducido por el
         * técnico. Intenta varias tolerancias antes de rendirse:
         *   1) match exacto con la clave normalizada (lowercase + trim).
         *   2) match sin tildes (Á → a) por si el usuario las omite y la
         *      clave canónica las lleva (o viceversa).
         *   3) match por contención: si el texto incluye o está incluido
         *      en una clave conocida (ej. "Palencia capital" → "palencia",
         *      "Pza. Mayor, Madrid" → "madrid"). Devuelve el match más
         *      largo para evitar falsos positivos.
         */
        fun coordenadasDeCiudad(ciudad: String): Pair<Double, Double>? {
            val raw = ciudad.trim().lowercase()
            if (raw.isEmpty()) return null
            CIUDADES_COORDS[raw]?.let { return it }

            val sinTildes = quitarTildes(raw)
            CIUDADES_COORDS[sinTildes]?.let { return it }

            val claveCoincidente = CIUDADES_COORDS.keys
                .filter { clave ->
                    val claveSinT = quitarTildes(clave)
                    sinTildes.contains(claveSinT) || claveSinT.contains(sinTildes)
                }
                .maxByOrNull { it.length }
            return claveCoincidente?.let { CIUDADES_COORDS[it] }
        }

        // Provincias cuyo nombre no es el de su capital (el resto coincide)
        private val CAPITAL_DE_PROVINCIA = mapOf(
            "Álava" to "vitoria", "Asturias" to "oviedo", "Bizkaia" to "bilbao",
            "Cantabria" to "santander", "Gipuzkoa" to "donostia",
            "Illes Balears" to "palma", "La Rioja" to "logroño",
            "Navarra" to "pamplona", "Santa Cruz de Tenerife" to "santa cruz"
        )

        /**
         * Coordenadas de la capital de una provincia (con el nombre de
         * [AlgoritmoEnergetico.provinciasOrdenadas]), o null si no se conoce.
         */
        fun coordenadasDeProvincia(provincia: String): Pair<Double, Double>? =
            coordenadasDeCiudad(CAPITAL_DE_PROVINCIA[provincia.trim()] ?: provincia)

        private fun quitarTildes(s: String): String = s
            .replace('á', 'a').replace('é', 'e').replace('í', 'i')
            .replace('ó', 'o').replace('ú', 'u').replace('ü', 'u')
            .replace('ñ', 'n')
    }

    fun obtenerTecnicos(callback: (List<Tecnico>) -> Unit) {
        db.collection("tecnicos").getOrTimeout { snap ->
            if (snap == null) { callback(emptyList()); return@getOrTimeout }
            val tecnicos = snap.documents.mapNotNull { doc ->
                doc.toObject(Tecnico::class.java)?.let { t ->
                    if (t.id.isBlank()) t.copy(id = doc.id) else t
                }
            }
            db.collection("resenas").getOrTimeout { resenasSnap ->
                if (resenasSnap == null) { callback(tecnicos); return@getOrTimeout }
                val porTecnico = resenasSnap.documents
                    .mapNotNull { it.toObject(Resena::class.java) }
                    .groupBy { it.tecnicoId }
                callback(tecnicos.map { t ->
                    val lista = porTecnico[t.id].orEmpty()
                    if (lista.isEmpty()) t.copy(rating = 0.0, opiniones = 0)
                    else t.copy(
                        rating = Math.round(lista.map { it.puntuacion }.average() * 10.0) / 10.0,
                        opiniones = lista.size
                    )
                })
            }
        }
    }

    /**
     * Tiempo real: directorio completo de técnicos. El callback se dispara con la
     * caché al instante y luego en cada alta/baja/modificación. Necesario para que
     * un técnico recién creado aparezca de inmediato en el listado del cliente
     * (sin reabrir la app). El recálculo de rating con las reseñas vive en una
     * segunda suscripción para que ambos lados se mantengan en vivo.
     */
    fun escucharTecnicos(callback: (List<Tecnico>) -> Unit): Pair<ListenerRegistration, ListenerRegistration> {
        var tecnicosBase: List<Tecnico> = emptyList()
        var resenasPorTecnico: Map<String, List<Resena>> = emptyMap()

        // rating/opiniones se recalculan aquí sobre las reseñas vivas; el valor
        // persistente del doc lo mantiene la Cloud Function `on_resena_changed`.
        fun emitir() {
            callback(tecnicosBase.map { t ->
                val lista = resenasPorTecnico[t.id].orEmpty()
                if (lista.isEmpty()) t.copy(rating = 0.0, opiniones = 0)
                else t.copy(
                    rating = Math.round(lista.map { it.puntuacion }.average() * 10.0) / 10.0,
                    opiniones = lista.size
                )
            })
        }

        val regTec = db.collection("tecnicos").addSnapshotListener { snap, err ->
            if (err != null) {
                // Loguea para verlo en logcat y emite vacío; el VM detectará uid stale
                // en la próxima invocación y reenganchará con el auth correcto.
                android.util.Log.w("RepoTecnicos", "Listener técnicos cancelado: ${err.message}")
                tecnicosBase = emptyList(); emitir(); return@addSnapshotListener
            }
            tecnicosBase = snap?.documents?.mapNotNull { doc ->
                doc.toObject(Tecnico::class.java)?.let { t ->
                    if (t.id.isBlank()) t.copy(id = doc.id) else t
                }
            } ?: emptyList()
            emitir()
        }
        val regRes = db.collection("resenas").addSnapshotListener { snap, err ->
            if (err != null) {
                android.util.Log.w("RepoTecnicos", "Listener reseñas cancelado: ${err.message}")
                resenasPorTecnico = emptyMap(); emitir(); return@addSnapshotListener
            }
            resenasPorTecnico = snap?.documents
                ?.mapNotNull { it.toObject(Resena::class.java) }
                ?.groupBy { it.tecnicoId } ?: emptyMap()
            emitir()
        }
        return regTec to regRes
    }

    fun obtenerTecnico(id: String, callback: (Tecnico?) -> Unit) {
        db.collection("tecnicos").document(id).get()
            .addOnSuccessListener { doc ->
                val t = doc.toObject(Tecnico::class.java)
                callback(if (t != null && t.id.isBlank()) t.copy(id = doc.id) else t)
            }
            .addOnFailureListener { callback(null) }
    }

    fun obtenerRanking(callback: (List<Tecnico>) -> Unit) = obtenerTecnicos(callback)

    /** Devuelve el perfil de técnico vinculado al uid de Auth actual. */
    fun obtenerMiPerfilTecnico(callback: (Tecnico?) -> Unit) {
        db.collection("tecnicos")
            .whereEqualTo("uid", uid())
            .limit(1)
            .getOrTimeout { snap ->
                val doc = snap?.documents?.firstOrNull()
                val tec = doc?.toObject(Tecnico::class.java)
                callback(if (tec != null && tec.id.isBlank()) tec.copy(id = doc.id) else tec)
            }
    }

    fun actualizarPerfilTecnico(
        tecnicoId: String,
        nombre: String,
        ciudad: String,
        descripcion: String,
        telefono: String,
        emailContacto: String,
        especialidades: List<String>,
        callback: (Result<Unit>) -> Unit
    ) {
        // Ciudad no reconocida: sin coordenadas (0 = desconocida), para no
        // dejarle en el mapa en la ciudad que tenía antes
        val (latitud, longitud) = coordenadasDeCiudad(ciudad) ?: (0.0 to 0.0)
        val datos = mapOf(
            "nombre" to nombre,
            "ciudad" to ciudad,
            "descripcion" to descripcion,
            "telefono" to telefono,
            "emailContacto" to emailContacto,
            "especialidades" to especialidades,
            "latitud" to latitud,
            "longitud" to longitud
        )
        db.collection("tecnicos").document(tecnicoId).update(datos)
            .addOnSuccessListener { callback(Result.success(Unit)) }
            .addOnFailureListener { e ->
                callback(Result.failure(Exception(e.message ?: "Error actualizando perfil")))
            }
    }

    /** Crea el perfil profesional del usuario actual (las reglas exigen id del documento = uid). */
    fun registrarTecnico(tecnico: Tecnico, callback: (Result<Unit>) -> Unit) {
        val uid = uid()
        if (uid.isEmpty()) { callback(Result.failure(Exception("Sin sesión"))); return }
        val (latitud, longitud) = coordenadasDeCiudad(tecnico.ciudad) ?: (0.0 to 0.0)
        val t = tecnico.copy(id = uid, uid = uid, latitud = latitud, longitud = longitud)
        db.collection("tecnicos").document(uid).set(t)
            .addOnSuccessListener { callback(Result.success(Unit)) }
            .addOnFailureListener { e ->
                callback(Result.failure(Exception(e.message ?: "Error registrando técnico")))
            }
    }

}
