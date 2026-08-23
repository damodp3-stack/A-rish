package com.example.core.memory

import com.example.core.database.MemoryDao
import com.example.core.database.MemoryEntity
import com.example.core.model.MemoryCategory
import com.example.core.model.MemoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class MemoryManager(private val memoryDao: MemoryDao) {

    val allMemories: Flow<List<MemoryItem>> = memoryDao.getAllMemories().map { entities ->
        entities.map { it.toModel() }
    }

    suspend fun saveMemory(
        key: String,
        value: String,
        category: MemoryCategory = MemoryCategory.GENERAL,
        importance: Int = 3,
        source: String = "User Manual Input"
    ): Long = withContext(Dispatchers.IO) {
        val trimmedKey = key.trim()
        val trimmedValue = value.trim()
        
        // Deduplicate: check if a memory with exact same key exists and update it
        val existing = memoryDao.searchMemories(trimmedKey).firstOrNull { it.key.equals(trimmedKey, ignoreCase = true) }
        if (existing != null) {
            val updated = existing.copy(
                value = trimmedValue,
                category = category.name,
                importance = importance.coerceIn(1, 5),
                source = source,
                createdAt = System.currentTimeMillis()
            )
            memoryDao.updateMemory(updated)
            return@withContext existing.id
        }

        val entity = MemoryEntity(
            key = trimmedKey,
            value = trimmedValue,
            category = category.name,
            importance = importance.coerceIn(1, 5),
            source = source
        )
        memoryDao.insertMemory(entity)
    }

    suspend fun getRelevantMemoriesForPrompt(userPrompt: String): String = withContext(Dispatchers.IO) {
        val words = userPrompt.split(" ").filter { it.length > 2 }
        val foundMemories = mutableSetOf<MemoryEntity>()

        for (word in words.take(6)) {
            val results = memoryDao.searchMemories(word)
            foundMemories.addAll(results)
        }

        if (foundMemories.isEmpty()) {
            return@withContext ""
        }

        val formatted = foundMemories.sortedByDescending { it.importance }.take(5).joinToString("\n") {
            "- [${it.category}] ${it.key}: ${it.value}"
        }

        "ACTIVE MEMORY RECALL:\n$formatted\n"
    }

    suspend fun autoExtractAndStore(userText: String) = withContext(Dispatchers.IO) {
        val lower = userText.lowercase()
        when {
            lower.contains("my name is") -> {
                val name = userText.substringAfter("name is").trim().split(" ").firstOrNull() ?: ""
                if (name.isNotBlank()) {
                    saveMemory("User Name", name, MemoryCategory.FACT, importance = 5, source = "Auto-Inferred")
                }
            }
            lower.contains("call me") -> {
                val name = userText.substringAfter("call me").trim().split(" ").firstOrNull() ?: ""
                if (name.isNotBlank()) {
                    saveMemory("User Name", name, MemoryCategory.FACT, importance = 5, source = "Auto-Inferred")
                }
            }
            lower.contains("i prefer") || lower.contains("i like") || lower.contains("always use") -> {
                saveMemory("User Preference", userText, MemoryCategory.USER_PREFERENCE, importance = 4, source = "Auto-Inferred")
            }
            lower.contains("my favorite") -> {
                val fav = userText.substringAfter("favorite").trim()
                saveMemory("Favorite Topic", fav, MemoryCategory.USER_PREFERENCE, importance = 3, source = "Auto-Inferred")
            }
            lower.contains("remember that") || lower.contains("note that") || lower.contains("remind me") -> {
                val fact = userText.substringAfter("that", "").substringAfter("me", "").trim()
                if (fact.isNotBlank()) {
                    saveMemory("User Memo", fact, MemoryCategory.GENERAL, importance = 4, source = "Voice / Chat")
                }
            }
            lower.contains("working on") || lower.contains("my project is") || lower.contains("developing") -> {
                saveMemory("Active Project", userText, MemoryCategory.PROJECT, importance = 4, source = "Conversation")
            }
            lower.contains("i am in") || lower.contains("i live in") -> {
                val loc = userText.substringAfter("in").trim().split(" ").firstOrNull() ?: ""
                if (loc.isNotBlank()) {
                    saveMemory("User Location", loc, MemoryCategory.FACT, importance = 4, source = "Auto-Inferred")
                }
            }
        }
    }

    suspend fun updateMemory(item: MemoryItem) = withContext(Dispatchers.IO) {
        memoryDao.updateMemory(item.toEntity())
    }

    suspend fun deleteMemory(id: Long) = withContext(Dispatchers.IO) {
        memoryDao.deleteMemoryById(id)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        memoryDao.deleteAllMemories()
    }

    suspend fun seedInitialDefaults() = withContext(Dispatchers.IO) {
        saveMemory("Identity Directive", "JARVIS Mobile Operating Layer - OnePlus 15R Native Protocol", MemoryCategory.IDENTITY, importance = 5, source = "System Genesis")
        saveMemory("Bilingual Matrix", "Fluency in English and Tamil (தமிழ்), with tolerant Tanglish interpretation.", MemoryCategory.IDENTITY, importance = 5, source = "System Genesis")
        saveMemory("Safety & Security", "Zero key leakage, hardware-level Keystore isolation, explicit user approval for destructive changes.", MemoryCategory.IDENTITY, importance = 5, source = "Security Vault")
    }

    private fun MemoryEntity.toModel(): MemoryItem {
        val cat = try {
            MemoryCategory.valueOf(category)
        } catch (e: Exception) {
            MemoryCategory.GENERAL
        }
        return MemoryItem(
            id = id,
            key = key,
            value = value,
            category = cat,
            importance = importance,
            createdAt = createdAt,
            source = source,
            requiresApproval = requiresApproval
        )
    }

    private fun MemoryItem.toEntity(): MemoryEntity {
        return MemoryEntity(
            id = id,
            key = key,
            value = value,
            category = category.name,
            importance = importance,
            createdAt = createdAt,
            source = source,
            requiresApproval = requiresApproval
        )
    }
}
