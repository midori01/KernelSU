package me.weishu.kernelsu.ui.screen.kconfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KconfigViewModel : ViewModel() {
    private val _items = MutableStateFlow<List<KconfigItem>>(emptyList())
    val items: StateFlow<List<KconfigItem>> = _items.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val allItems = mutableListOf<KconfigItem>()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    KconfigParser.parse()
                } catch (e: Exception) {
                    listOf(KconfigItem("Error", e.message ?: "unknown", "Other"))
                }
            }
            allItems.clear()
            allItems.addAll(result)
            _items.value = result
            search("")
        }
    }

    fun search(query: String) {
        _searchQuery.value = query
        _items.value = when {
            query.isBlank() -> allItems
            query == "other" -> allItems.filter { it.value !in listOf("y", "m") }
            query.startsWith("=") -> allItems.filter { it.value == query.drop(1) }
            else -> allItems.filter { it.key.contains(query, ignoreCase = true) }
        }
    }
}
