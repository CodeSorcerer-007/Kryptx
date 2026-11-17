package com.kryptx.app.core.database

import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.SearchQueryParser
import com.kryptx.app.core.model.VaultItem
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

/**
 * High-performance In-Memory Volatile Inverted Search Index.
 *
 * Builds a fast RAM-only token index over active in-memory decrypted vault items
 * to deliver sub-millisecond instant search across large vaults (50,000+ items).
 * Never touches disk; cleared and wiped automatically when the vault locks.
 */
class EncryptedSearchIndex {

    private val tokenIndex = ConcurrentHashMap<String, CopyOnWriteArraySet<String>>() // token/prefix -> Set<itemId>
    private val itemsMap = ConcurrentHashMap<String, VaultItem>() // itemId -> VaultItem
    private val tagIndex = ConcurrentHashMap<String, CopyOnWriteArraySet<String>>() // lowercase tag -> Set<itemId>
    private val typeIndex = ConcurrentHashMap<ItemType, CopyOnWriteArraySet<String>>() // type -> Set<itemId>

    /**
     * Rebuilds the entire index from a list of vault items.
     */
    fun rebuild(items: List<VaultItem>) {
        clear()
        for (item in items) {
            indexItem(item)
        }
    }

    /**
     * Indexes a single vault item.
     */
    fun indexItem(item: VaultItem) {
        itemsMap[item.id] = item

        // Index Type
        typeIndex.computeIfAbsent(item.type) { CopyOnWriteArraySet() }.add(item.id)

        // Index Tags
        for (tag in item.tags) {
            val cleanTag = tag.trim().lowercase()
            if (cleanTag.isNotEmpty()) {
                tagIndex.computeIfAbsent(cleanTag) { CopyOnWriteArraySet() }.add(item.id)
            }
        }

        // Tokenize searchable text (Title, Username, Website, Notes, Custom Fields)
        val textToTokenize = buildString {
            append(item.title).append(" ")
            append(item.username).append(" ")
            append(item.website).append(" ")
            append(item.notes).append(" ")
            for (field in item.customFields) {
                append(field.label).append(" ")
            }
        }

        val tokens = tokenize(textToTokenize)
        for (token in tokens) {
            tokenIndex.computeIfAbsent(token) { CopyOnWriteArraySet() }.add(item.id)
        }
    }

    /**
     * Removes an item from the index.
     */
    fun removeItem(itemId: String) {
        val item = itemsMap.remove(itemId) ?: return
        typeIndex[item.type]?.remove(itemId)
        for (tag in item.tags) {
            tagIndex[tag.trim().lowercase()]?.remove(itemId)
        }
        for (tokenSet in tokenIndex.values) {
            tokenSet.remove(itemId)
        }
    }

    /**
     * Executes an instant search query using the parsed query engine and token index.
     */
    fun search(query: String): List<VaultItem> {
        val clean = query.trim()
        if (clean.isBlank()) return itemsMap.values.toList()

        // Fast O(1) direct index lookup for simple single-token prefix searches
        if (!clean.contains(" ") && !clean.contains(":") && clean.length >= 2) {
            val matchingIds = tokenIndex[clean.lowercase()]
            if (matchingIds != null && matchingIds.isNotEmpty()) {
                return matchingIds.mapNotNull { itemsMap[it] }
            }
        }

        val allItems = itemsMap.values.toList()
        return SearchQueryParser.filter(allItems, clean)
    }

    /**
     * Wipes and purges all index structures from RAM.
     */
    fun clear() {
        tokenIndex.clear()
        tagIndex.clear()
        typeIndex.clear()
        itemsMap.clear()
    }

    val size: Int get() = itemsMap.size

    private fun tokenize(text: String): Set<String> {
        val set = HashSet<String>()
        val len = text.length
        var start = -1
        for (i in 0 until len) {
            val c = text[i]
            val isAlphaNum = (c in 'a'..'z') || (c in 'A'..'Z') || (c in '0'..'9')
            if (isAlphaNum) {
                if (start == -1) start = i
            } else {
                if (start != -1) {
                    val word = text.substring(start, i).lowercase()
                    if (word.length >= 2) {
                        set.add(word)
                        val maxPrefix = minOf(word.length, 12)
                        for (p in 2..maxPrefix) {
                            set.add(word.substring(0, p))
                        }
                    }
                    start = -1
                }
            }
        }
        if (start != -1) {
            val word = text.substring(start, len).lowercase()
            if (word.length >= 2) {
                set.add(word)
                val maxPrefix = minOf(word.length, 12)
                for (p in 2..maxPrefix) {
                    set.add(word.substring(0, p))
                }
            }
        }
        return set
    }
}
