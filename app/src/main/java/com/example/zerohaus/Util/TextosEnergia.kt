package com.example.zerohaus.Util

/**
 * Traducción, SOLO para mostrar, de los valores del dominio energético que se
 * guardan en Firestore en español (opciones de la vivienda, estado de
 * eficiencia y títulos de recomendaciones). Los valores guardados NO cambian:
 * el algoritmo y los informes antiguos siguen comparando contra el español.
 * Un valor desconocido se muestra tal cual.
 */
object TextosEnergia {

    fun opcion(valor: String, c: AppCadenas): String = when (valor) {
        "Vidrio simple"                      -> c.optVidrioSimple
        "Doble acristalamiento"              -> c.optDobleAcristalamiento
        "Triple"                             -> c.optTriple
        "Sin aislamiento"                    -> c.optSinAislamiento
        "Aislamiento parcial"                -> c.optAislParcial
        "Aislamiento completo"               -> c.optAislCompleto
        "Caldera de gas"                     -> c.optCalderaGas
        "Eléctrica"                          -> c.optElectrica
        "Aerotermia"                         -> c.optAerotermia
        "Biomasa"                            -> c.optBiomasa
        "Sin calefacción"                    -> c.optSinCalefaccion
        "Gas"                                -> c.optGas
        "Eléctrico"                          -> c.optElectrico
        "Solar térmica"                      -> c.optSolarTermica
        "Sin ACS"                            -> c.optSinAcs
        "Norte"                              -> c.optNorte
        "Sur"                                -> c.optSur
        "Este"                               -> c.optEste
        "Oeste"                              -> c.optOeste
        "Noreste"                            -> c.optNoreste
        "Noroeste"                           -> c.optNoroeste
        "Sureste"                            -> c.optSureste
        "Suroeste"                           -> c.optSuroeste
        "Mayoría LED"                        -> c.optMayoriaLed
        "Mixta"                              -> c.optIlumMixta
        "Mayoría halógenas o incandescentes" -> c.optHalogenas
        "Piso interior"                      -> c.optPisoInterior
        "Piso esquina o ático"               -> c.optPisoEsquina
        "Adosado o pareado"                  -> c.optAdosado
        "Unifamiliar aislado"                -> c.optUnifamiliar
        "Sin refrigeración"                  -> c.optSinRefrig
        "A/A inverter eficiente"             -> c.optInverter
        "A/A convencional o antiguo"         -> c.optAAConvencional
        "Sin fotovoltaica"                   -> c.optSinFv
        "Pequeña (1-3 kWp)"                  -> c.optFvPequena
        "Mediana (3-5 kWp)"                  -> c.optFvMediana
        "Grande (>5 kWp) o con baterías"     -> c.optFvGrande
        "Mayoría clase A o superior"         -> c.optElectroA
        "Clase B-C"                          -> c.optElectroBC
        "Clase D o antiguos"                 -> c.optElectroD
        else                                 -> valor
    }

    /** El estado se deriva de la letra, así también se traducen informes antiguos. */
    fun estado(etiqueta: String, c: AppCadenas): String = when (etiqueta) {
        "A" -> c.efiA
        "B" -> c.efiB
        "C" -> c.efiC
        "D" -> c.efiD
        "E" -> c.efiE
        "F" -> c.efiF
        else -> c.efiG
    }

    /** Incluye los títulos de versiones anteriores del algoritmo. */
    fun recomendacion(titulo: String, c: AppCadenas): String = when (titulo) {
        "Mejorar ventanas a doble acristalamiento",
        "Mejorar aislamiento de ventanas a doble acristalamiento" -> c.recVentanas
        "Completar el aislamiento térmico de la vivienda"         -> c.recAislamiento
        "Instalar aerotermia como sistema de calefacción"         -> c.recAerotermia
        "Instalar solar térmica para ACS"                         -> c.recSolarTermica
        "Sustituir halógenas e incandescentes por LED"            -> c.recLedHalogenas
        "Sustituir iluminación restante por LED"                  -> c.recLedResto
        "Instalar autoconsumo fotovoltaico"                       -> c.recFotovoltaica
        "Sustituir aire acondicionado por equipo inverter"        -> c.recInverter
        "Renovar electrodomésticos a etiqueta A o superior"       -> c.recElectrodomesticos
        else                                                      -> titulo
    }
}
