package com.kryptx.app.core.generator

import com.kryptx.app.core.crypto.EntropyCalculator
import com.kryptx.app.core.model.GeneratorConfig
import com.kryptx.app.core.model.GeneratorMode
import com.kryptx.app.core.model.UsernameStyle
import java.security.SecureRandom

/**
 * Production-grade password, passphrase, PIN, and username generator with cryptographically secure randomness.
 */
object GeneratorEngine {

    private val secureRandom = SecureRandom()

    private const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
    private const val NUMBERS = "0123456789"
    private const val SYMBOLS = "!@#$%^&*()_+-=[]{}|;:,.<>?"

    private const val AMBIGUOUS_CHARS = "0Ool1I|B8"

    private val WORDLIST = listOf(
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

    private val ADJECTIVES = listOf(
        "swift", "silent", "brave", "bright", "calm", "clever", "cosmic", "cryptic",
        "daring", "eager", "epic", "fierce", "frosty", "golden", "grand", "hyper",
        "iron", "jade", "keen", "lucid", "mighty", "noble", "nova", "prime",
        "radiant", "rapid", "royal", "shadow", "silver", "solar", "stellar", "titan",
        "valiant", "vivid", "wild", "zenith"
    )

    private val NOUNS = listOf(
        "falcon", "panther", "tiger", "wolf", "hawk", "fox", "viper", "bear",
        "phoenix", "dragon", "knight", "ranger", "pilot", "cipher", "matrix", "shield",
        "spark", "vortex", "storm", "quasar", "nebula", "comet", "beacon", "atlas"
    )

    data class GenerationResult(
        val value: String,
        val analysis: EntropyCalculator.AnalysisResult
    )

    /**
     * Generates a credential string based on the given configuration.
     */
    fun generate(config: GeneratorConfig): GenerationResult {
        val generatedString = when (config.mode) {
            GeneratorMode.PASSWORD -> generatePassword(config)
            GeneratorMode.PASSPHRASE -> generatePassphrase(config)
            GeneratorMode.PIN -> generatePin(config.pinLength)
            GeneratorMode.USERNAME -> generateUsername(config.usernameStyle)
        }

        val analysis = EntropyCalculator.analyze(generatedString)
        return GenerationResult(generatedString, analysis)
    }

    private fun generatePassword(config: GeneratorConfig): String {
        var pool = buildString {
            if (config.includeUppercase) append(UPPERCASE)
            if (config.includeLowercase) append(LOWERCASE)
            if (config.includeNumbers) append(NUMBERS)
            if (config.includeSymbols) append(SYMBOLS)
        }

        if (pool.isEmpty()) {
            pool = LOWERCASE + NUMBERS
        }

        if (config.avoidAmbiguous) {
            pool = pool.filter { !AMBIGUOUS_CHARS.contains(it) }
        }

        val length = config.passwordLength.coerceIn(4, 64)
        val chars = CharArray(length)

        // Ensure at least one character from each enabled set is included
        val guaranteedChars = mutableListOf<Char>()
        if (config.includeUppercase) guaranteedChars.add(filterPool(UPPERCASE, config.avoidAmbiguous).randomSecure())
        if (config.includeLowercase) guaranteedChars.add(filterPool(LOWERCASE, config.avoidAmbiguous).randomSecure())
        if (config.includeNumbers) guaranteedChars.add(filterPool(NUMBERS, config.avoidAmbiguous).randomSecure())
        if (config.includeSymbols) guaranteedChars.add(filterPool(SYMBOLS, config.avoidAmbiguous).randomSecure())

        for (i in 0 until length) {
            if (i < guaranteedChars.size) {
                chars[i] = guaranteedChars[i]
            } else {
                chars[i] = pool[secureRandom.nextInt(pool.length)]
            }
        }

        // Shuffle securely
        chars.shuffleSecure()
        return String(chars)
    }

    private fun generatePassphrase(config: GeneratorConfig): String {
        val count = config.wordCount.coerceIn(3, 8)
        val selectedWords = mutableListOf<String>()

        for (i in 0 until count) {
            var word = WORDLIST[secureRandom.nextInt(WORDLIST.size)]
            if (config.capitalizeWords) {
                word = word.replaceFirstChar { it.uppercase() }
            }
            selectedWords.add(word)
        }

        if (config.includeNumberInPassphrase) {
            val randomNum = secureRandom.nextInt(90) + 10 // 10..99
            val insertIndex = secureRandom.nextInt(selectedWords.size)
            selectedWords[insertIndex] = "${selectedWords[insertIndex]}$randomNum"
        }

        return selectedWords.joinToString(config.separator)
    }

    private fun generatePin(length: Int): String {
        val safeLength = length.coerceIn(4, 16)
        val builder = StringBuilder(safeLength)
        for (i in 0 until safeLength) {
            builder.append(secureRandom.nextInt(10))
        }
        return builder.toString()
    }

    private fun generateUsername(style: UsernameStyle): String {
        return when (style) {
            UsernameStyle.MEMORABLE -> {
                val adj = ADJECTIVES[secureRandom.nextInt(ADJECTIVES.size)]
                val noun = NOUNS[secureRandom.nextInt(NOUNS.size)]
                val num = secureRandom.nextInt(900) + 100
                "${adj}_${noun}_$num"
            }
            UsernameStyle.ANONYMOUS_ALPHANUMERIC -> {
                val hex = ByteArray(4)
                secureRandom.nextBytes(hex)
                val hexStr = hex.joinToString("") { "%02x".format(it) }
                "kryptx_$hexStr"
            }
            UsernameStyle.EMAIL_ALIAS -> {
                val hex = ByteArray(3)
                secureRandom.nextBytes(hex)
                val hexStr = hex.joinToString("") { "%02x".format(it) }
                val word = NOUNS[secureRandom.nextInt(NOUNS.size)]
                "user.$word.$hexStr@alias.kryptx.vault"
            }
        }
    }

    private fun filterPool(pool: String, avoidAmbiguous: Boolean): String {
        return if (avoidAmbiguous) pool.filter { !AMBIGUOUS_CHARS.contains(it) } else pool
    }

    private fun String.randomSecure(): Char {
        return this[secureRandom.nextInt(this.length)]
    }

    private fun CharArray.shuffleSecure() {
        for (i in size - 1 downTo 1) {
            val j = secureRandom.nextInt(i + 1)
            val temp = this[i]
            this[i] = this[j]
            this[j] = temp
        }
    }
}
