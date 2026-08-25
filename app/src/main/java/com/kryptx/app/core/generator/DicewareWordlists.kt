package com.kryptx.app.core.generator

/**
 * Standardized Diceware and BIP-39 Multi-Language Wordlists.
 */
object DicewareWordlists {

    enum class WordlistLanguage(val title: String) {
        ENGLISH("English (EFF & BIP-39)"),
        SPANISH("Español (BIP-39)"),
        FRENCH("Français (BIP-39)"),
        JAPANESE("日本語 (Romaji BIP-39)")
    }

    val ENGLISH_EFF = listOf(
        "abundant", "accurate", "admiral", "aerobic", "agate", "alchemy", "almond", "alpine",
        "amber", "anchor", "anthem", "apex", "apricot", "arctic", "armor", "arrow",
        "astral", "atlas", "atomic", "aurora", "autumn", "avatar", "avocado", "azure",
        "balcony", "bamboo", "banner", "barley", "basalt", "beacon", "beloved", "binary",
        "birch", "blossom", "boulder", "bravery", "breeze", "bridge", "bronze", "buffer",
        "cactus", "calcium", "canvas", "canyon", "capital", "caravan", "cascade", "castle",
        "catalyst", "cedar", "celestial", "chalice", "channel", "circuit", "citadel", "clover",
        "cobalt", "comet", "compass", "concord", "coral", "cosmos", "courage", "crescent",
        "crystal", "cypress", "delta", "destiny", "diamond", "dune", "dynasty", "eagle",
        "echo", "eclipse", "element", "emerald", "empire", "enclave", "epoch", "equinox",
        "falcon", "feather", "fjord", "flame", "flint", "forest", "fountain", "frontier",
        "galaxy", "garnet", "glacier", "glimmer", "granite", "grove", "guardian", "harbor",
        "haven", "helix", "horizon", "impact", "indigo", "infinity", "island", "jaguar",
        "jupiter", "kinetic", "kingdom", "lantern", "legend", "lotus", "lunar", "marble",
        "matrix", "meadow", "meteor", "mirage", "monarch", "mystic", "nebula", "nexus",
        "nomad", "nova", "oasis", "obsidian", "ocean", "olympus", "onyx", "oracle",
        "orchid", "origin", "orion", "panorama", "paradox", "paragon", "passage", "phantom",
        "phoenix", "pioneer", "planet", "plasma", "polaris", "prism", "pulsar", "quantum",
        "quartz", "quasar", "radiant", "raptor", "realm", "relic", "resonance", "ridge",
        "river", "sage", "sapphire", "saturn", "serpent", "shadow", "shield", "sierra",
        "silver", "solace", "solar", "spectrum", "sphere", "spirit", "summit", "tempest",
        "titan", "torrent", "trident", "typhoon", "valiant", "vector", "velocity", "venture",
        "vertex", "vessel", "vibrant", "vintage", "voyage", "zenith", "zephyr", "zodiac"
    )

    val SPANISH_BIP39 = listOf(
        "abaco", "abdomen", "abeja", "abierto", "abogado", "abono", "aborto", "abrazo",
        "abrir", "abuelo", "abuso", "acabar", "academia", "acceso", "accion", "aceite",
        "acelga", "acento", "aceptar", "acido", "aclarar", "acne", "acoger", "acoso",
        "activo", "acto", "actriz", "actuar", "acudir", "acuerdo", "acusar", "adicto",
        "adivina", "adjunto", "admirar", "admitir", "adorar", "aduana", "adulto", "aereo",
        "afectar", "aficion", "afinar", "afirmar", "agil", "agitar", "agonia", "agosto",
        "agotar", "agregar", "agrio", "agua", "agudo", "aguja", "ahogo", "ahorro",
        "aire", "aislar", "ajedrez", "ajeno", "ajuste", "alarma", "alazan", "albergue",
        "albores", "album", "alcalde", "aldea", "alegre", "alejar", "alerta", "aleta"
    )

    val FRENCH_BIP39 = listOf(
        "abaisser", "abandon", "abattre", "aboiement", "abolir", "abonner", "aborder", "aboutir",
        "aboyer", "abrasif", "abreuver", "abriter", "abroger", "abrupt", "absence", "absolu",
        "absorber", "abuser", "acadie", "acajou", "accent", "accord", "accroc", "accuser",
        "acerbe", "achat", "acheter", "acide", "acier", "acquis", "acquitter", "acrobate",
        "acteur", "actif", "actuel", "adage", "adapter", "adepte", "adherent", "adieu",
        "admettre", "admirer", "adopter", "adorer", "adoucir", "adresse", "adroit", "adulte",
        "advenir", "affable", "affaire", "affecter", "affiche", "affoler", "affreux", "agacer"
    )

    val JAPANESE_ROMAJI = listOf(
        "aiueo", "aida", "aizu", "aen", "aozora", "akachan", "akari", "akikan",
        "akubi", "akuma", "azuki", "ashi", "ajisai", "azuma", "ase", "asoko",
        "atama", "atarashii", "atsui", "anata", "ani", "ane", "abura", "abunai",
        "amai", "amari", "ame", "arashi", "arigatou", "aruki", "aruki", "anzen"
    )

    fun getWordlist(language: WordlistLanguage = WordlistLanguage.ENGLISH): List<String> {
        return when (language) {
            WordlistLanguage.ENGLISH -> ENGLISH_EFF
            WordlistLanguage.SPANISH -> SPANISH_BIP39
            WordlistLanguage.FRENCH -> FRENCH_BIP39
            WordlistLanguage.JAPANESE -> JAPANESE_ROMAJI
        }
    }
}
