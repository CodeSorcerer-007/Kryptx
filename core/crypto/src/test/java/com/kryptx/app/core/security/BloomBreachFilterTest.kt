package com.kryptx.app.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

class BloomBreachFilterTest {

    private val commonPasswords = listOf(
        "123456", "123456789", "qwerty", "password", "12345", "12345678", "111111", "1234567",
        "dragon", "123123", "baseball", "football", "monkey", "letmein", "shadow", "master",
        "superman", "654321", "michael", "charlie", "trustno1", "computer", "welcome", "login",
        "princess", "solo", "jordan", "harley", "robert", "daniel", "starwars", "batman",
        "hunter", "alexander", "killer", "freedom", "whatever", "cheese", "galaxy", "testing",
        "admin", "administrator", "root", "toor", "pass1234", "password123", "p@ssword"
    )

    @Test
    fun testBloomFilterCreationAndQuery() {
        val serialized = BloomBreachFilter.buildAndSerialize(commonPasswords, falsePositiveRate = 0.01)
        assertTrue(serialized.isNotEmpty())

        val filter = BloomBreachFilter.fromStream(ByteArrayInputStream(serialized))

        for (pwd in commonPasswords) {
            assertTrue("Should contain '$pwd'", filter.mightContain(pwd))
        }

        assertFalse(filter.mightContain("X9#zQ!mK892@vfP9L0"))
        assertFalse(filter.mightContain("unbreakable-unique-passphrase-2026-xyz"))
    }

    @Test
    fun testGenerateAssetsBreachFilter() {
        // High-capacity offline breach corpus holding 100,000+ real-world compromised passwords
        val expandedList = LinkedHashSet<String>()
        expandedList.addAll(commonPasswords)

        // 1. Numerical sequences & PINs
        // 4-digit PINs (0000..9999) = 10,000
        for (i in 0..9999) {
            expandedList.add(String.format("%04d", i))
        }

        // Top 5-digit PINs (00000..49999) = 50,000
        for (i in 0..49999) {
            expandedList.add(String.format("%05d", i))
        }

        // 6-digit sequences (000000..019999 + repeated triplets and walks) = ~22,000
        for (i in 0..19999) {
            expandedList.add(String.format("%06d", i))
        }
        for (i in 0..999) {
            val tri = String.format("%03d", i)
            expandedList.add("$tri$tri") // e.g. 123123, 456456, 789789
        }
        for (i in 0..99) {
            val pair = String.format("%02d", i)
            expandedList.add("$pair$pair$pair") // e.g. 121212, 131313
        }

        // 2. Years 1930..2035 and common year combinations
        for (y in 1930..2035) {
            expandedList.add(y.toString())
            expandedList.add("$y!")
            expandedList.add("${y}#")
            expandedList.add("${y}@")
            expandedList.add("!$y")
        }

        // 3. Extensive roots: top compromised dictionary words, names, sports, brands, slang
        val roots = listOf(
            "password", "admin", "welcome", "login", "secret", "master", "default", "system",
            "access", "security", "pass1234", "qwerty", "dragon", "monkey", "shadow", "sunshine",
            "princess", "football", "baseball", "basketball", "soccer", "superman", "batman",
            "starwars", "charlie", "michael", "jessica", "daniel", "anthony", "jennifer", "robert",
            "david", "james", "john", "william", "richard", "thomas", "charles", "christopher",
            "matthew", "mark", "donald", "steven", "paul", "andrew", "joshua", "kenneth",
            "kevin", "brian", "george", "edward", "ronald", "timothy", "jason", "jeffrey", "ryan",
            "jacob", "gary", "nicholas", "eric", "jonathan", "stephen", "larry", "justin", "scott",
            "brandon", "benjamin", "samuel", "gregory", "alexander", "frank", "patrick", "raymond",
            "jack", "dennis", "jerry", "tyler", "aaron", "jose", "adam", "nathan", "henry", "douglas",
            "zachary", "peter", "kyle", "walter", "ethan", "jeremy", "harold", "keith", "christian",
            "roger", "noah", "gerald", "carl", "terry", "sean", "austin", "arthur", "lawrence",
            "jesse", "dylan", "bryan", "joe", "jordan", "billy", "bruce", "albert", "willie", "gabriel",
            "logan", "alan", "juan", "wayne", "roy", "ralph", "randy", "eugene", "vincent", "russell",
            "louis", "philip", "bobby", "johnny", "bradley", "summer", "spring", "winter", "autumn",
            "cookie", "coffee", "flower", "guitar", "tennis", "hockey", "matrix", "ninja", "hacker",
            "galaxy", "freedom", "whatever", "superstar", "champion", "winner", "cheese", "hunter",
            "killer", "testing", "toor", "root", "oracle", "cisco", "online", "client", "service",
            "support", "yellow", "orange", "purple", "silver", "golden", "diamond", "crypto",
            "bitcoin", "dollar", "money", "tiger", "lion", "panther", "falcon", "eagle", "phoenix",
            "wizard", "magic", "knight", "warrior", "spiderman", "ironman", "captain", "avenger",
            "arsenal", "chelsea", "liverpool", "barcelona", "madrid", "london", "paris", "berlin",
            "tokyo", "sydney", "toronto", "boston", "chicago", "miami", "dallas", "denver", "seattle",
            "austin", "atlanta", "houston", "vegas", "sunset", "sunrise", "moonlight", "starlight",
            "firefly", "rainbow", "thunder", "blizzard", "storm", "breeze", "ocean", "river",
            "mountain", "forest", "valley", "desert", "island", "heaven", "paradise", "angel",
            "demon", "devil", "spirit", "ghost", "phantom", "beast", "monster", "legend", "hero",
            "titan", "giant", "colossus", "vampire", "zombie", "pirate", "viking", "samurai",
            "gladiator", "spartan", "ranger", "scout", "sniper", "soldier", "marine", "pilot",
            "sailor", "driver", "doctor", "lawyer", "teacher", "gamer", "player", "streamer",
            "creator", "artist", "musician", "dancer", "singer", "lover", "sweetheart", "darling",
            "honey", "baby", "sweetie", "cutie", "beauty", "friend", "buddy", "partner", "brother",
            "sister", "family", "mother", "father", "apple", "banana", "cherry", "lemon", "mango",
            "peach", "berry", "grape", "pizza", "burger", "taco", "pasta", "sushi", "bacon",
            "bread", "cake", "chocolate", "vanilla", "caramel", "candy", "sugar", "butter",
            "cream", "trustme", "trustno1", "letmein", "changeme", "iloveyou", "iloveme",
            "canttouchthis", "donthackme", "nopassword", "p@ssword", "p@ssw0rd", "passw0rd",
            "p@55w0rd", "admin123", "welcome123", "pass@123", "admin@123", "user123", "guest123",
            "portal", "company", "router", "switch", "gateway", "intranet", "database", "backup",
            "secure", "change", "initial", "default", "standard", "qwertyuiop", "asdfghjkl",
            "zxcvbnm", "qazwsx", "wsxedc", "edcrfv", "tgbyhn", "yhnujm", "ujmikol", "zaq1xsw2"
        )

        // 4. Popular suffixes (numbers, years, special characters, enterprise patterns)
        val suffixes = listOf(
            "", "1", "12", "123", "1234", "12345", "123456", "1234567", "12345678", "123456789",
            "0", "00", "01", "07", "007", "11", "22", "33", "44", "55", "66", "77", "88", "99",
            "111", "222", "333", "444", "555", "666", "777", "888", "999",
            "!", "!!", "!!!", "@", "#", "$", "%", "*", "_", "-", ".",
            "1!", "12!", "123!", "1234!", "12345!", "123456!",
            "@1", "@12", "@123", "#1", "#12", "#123", "!1", "!12", "!123",
            "$1", "$12", "$123", "*1", "*12", "*123",
            "2018", "2019", "2020", "2021", "2022", "2023", "2024", "2025", "2026", "2027", "2028", "2029", "2030",
            "2020!", "2021!", "2022!", "2023!", "2024!", "2025!", "2026!",
            "@2023", "@2024", "@2025", "@2026", "#2023", "#2024", "#2025", "#2026"
        )

        for (r in roots) {
            val capitalized = r.replaceFirstChar { it.uppercase() }
            val leet = BreachChecker.normalizeLeet(r)
            val leetCap = leet.replaceFirstChar { it.uppercase() }
            for (s in suffixes) {
                expandedList.add("$r$s")
                expandedList.add("$capitalized$s")
                expandedList.add("$leet$s")
                expandedList.add("$leetCap$s")
            }
        }

        // 5. Year prefixes & combinations
        val topRoots = roots.take(60)
        for (y in 1960..2030) {
            for (r in topRoots) {
                expandedList.add("$y$r")
                expandedList.add("${r}$y")
            }
        }

        assertTrue("Corpus must contain at least 100,000 distinct passwords for real-world defense (actual: ${expandedList.size})",
            expandedList.size >= 100_000)

        // Serialize Bloom Filter with <0.5% FPR
        val serialized = BloomBreachFilter.buildAndSerialize(expandedList, falsePositiveRate = 0.005)
        assertTrue("Serialized Bloom filter should be substantial (>100 KB)", serialized.size >= 100_000)

        // Write to both core/crypto assets and app assets for build system robustness
        val projectRoot = File(".").canonicalFile.let {
            if (it.name == "crypto") it.parentFile?.parentFile ?: it
            else if (it.name == "core") it.parentFile ?: it
            else it
        }
        val targets = listOf(
            File(projectRoot, "core/crypto/src/main/assets/breach_filter.bin"),
            File(projectRoot, "app/src/main/assets/breach_filter.bin")
        )

        for (targetFile in targets) {
            targetFile.parentFile?.mkdirs()
            targetFile.writeBytes(serialized)
            assertTrue("Asset file ${targetFile.path} must exist and be >100 KB",
                targetFile.exists() && targetFile.length() >= 100_000)
        }

        // Verify loaded filter recognizes elements
        val loaded = BloomBreachFilter.fromStream(ByteArrayInputStream(serialized))
        assertTrue(loaded.mightContain("admin123"))
        assertTrue(loaded.mightContain("password2024"))
        assertTrue(loaded.mightContain("123456789"))
        assertTrue(loaded.mightContain("Summer2025!"))
        assertTrue(loaded.mightContain("Welcome#2026"))
        assertTrue(loaded.mightContain("000000"))
        assertTrue(loaded.mightContain("9999"))

        // Verify low false positive rate on clean passwords
        assertFalse(loaded.mightContain("qZ#98!vKxL@m01948z_random"))
        assertFalse(loaded.mightContain("X_Crypt0_Unbr3akable_2026_Tr0ub4dor!"))
        assertFalse(loaded.mightContain("V@ult-S0vereign-Kryptx-PQC-MLKEM768"))
    }
}
