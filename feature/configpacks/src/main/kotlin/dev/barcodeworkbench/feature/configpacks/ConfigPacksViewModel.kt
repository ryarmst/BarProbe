package dev.barcodeworkbench.feature.configpacks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.barcodeworkbench.core.model.config.ConfigCategory
import dev.barcodeworkbench.core.model.config.ConfigEntry
import dev.barcodeworkbench.core.model.config.ConfigPackError
import dev.barcodeworkbench.core.model.config.ConfigPackInfo
import dev.barcodeworkbench.feature.configpacks.data.ConfigPackRepository
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ConfigPacksUiState(
    val packs: List<ConfigPackInfo> = emptyList(),
    val vendors: List<String> = emptyList(),
    val selectedVendor: String? = null,
    val selectedCategory: String? = null,
    val categories: List<ConfigCategory> = emptyList(),
    val defaults: List<ConfigEntry> = emptyList(),
    val query: String = "",
    val isLoading: Boolean = true,
    val message: String? = null,
    val isError: Boolean = false,
) {
    val isSearching: Boolean get() = query.isNotBlank()

    /** Pack backing the selected vendor, used to explain an empty one. */
    val selectedPack: ConfigPackInfo?
        get() = packs.firstOrNull { it.vendor == selectedVendor }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ConfigPacksViewModel @Inject constructor(
    private val repository: ConfigPackRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ConfigPacksUiState())
    val state: StateFlow<ConfigPacksUiState> = _state.asStateFlow()

    /** Entries for the current selection, or search results when a query is active. */
    val entries: StateFlow<List<ConfigEntry>> = _state
        .map { Selection(it.selectedVendor, it.selectedCategory, it.query) }
        .distinctUntilChanged()
        .flatMapLatest { selection ->
            when {
                selection.query.isNotBlank() ->
                    if (repository.isSearchable(selection.query)) {
                        repository.search(selection.query)
                    } else {
                        flowOf(emptyList())
                    }

                selection.vendor != null && selection.category != null ->
                    repository.observeEntries(selection.vendor, selection.category)

                else -> flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private data class Selection(
        val vendor: String?,
        val category: String?,
        val query: String,
    )

    init {
        viewModelScope.launch {
            // Bundled packs are re-read each launch so a corrected entry in a release
            // actually reaches existing installs.
            runCatching { repository.syncBundledPacks() }
            _state.value = _state.value.copy(isLoading = false)
        }
        viewModelScope.launch {
            repository.observePacks().collect { packs ->
                val vendors = packs.map { it.vendor }.distinct()
                val current = _state.value
                _state.value = current.copy(
                    packs = packs,
                    vendors = vendors,
                    selectedVendor = current.selectedVendor?.takeIf { it in vendors }
                        ?: vendors.firstOrNull(),
                )
            }
        }
        viewModelScope.launch {
            _state.map { it.selectedVendor }.distinctUntilChanged().collect { vendor ->
                if (vendor == null) return@collect
                launch {
                    repository.observeCategories(vendor).collect { categories ->
                        _state.value = _state.value.copy(
                            categories = categories,
                            selectedCategory = _state.value.selectedCategory
                                ?.takeIf { name -> categories.any { it.name == name } },
                        )
                    }
                }
                launch {
                    repository.observeDefaults(vendor).collect { defaults ->
                        _state.value = _state.value.copy(defaults = defaults)
                    }
                }
            }
        }
    }

    fun selectVendor(vendor: String) {
        _state.value = _state.value.copy(
            selectedVendor = vendor,
            selectedCategory = null,
            message = null,
        )
    }

    fun selectCategory(category: String?) {
        _state.value = _state.value.copy(selectedCategory = category)
    }

    fun setQuery(query: String) {
        _state.value = _state.value.copy(query = query)
    }

    fun importPack(text: String) {
        viewModelScope.launch {
            val outcome = runCatching { repository.importPack(text) }
            outcome.fold(
                onSuccess = { result ->
                    _state.value = _state.value.copy(
                        selectedVendor = result.vendor,
                        message = "Imported ${result.entryCount} entries for ${result.vendor}",
                        isError = false,
                    )
                },
                onFailure = { error ->
                    _state.value = _state.value.copy(
                        // A pack is content the user will scan at hardware, so every
                        // validation problem is reported rather than summarised.
                        message = when (error) {
                            is ConfigPackError.Invalid ->
                                "Pack rejected:\n" + error.problems.joinToString("\n")
                            is ConfigPackError -> error.message
                            else -> "Could not read that pack: ${error.message}"
                        },
                        isError = true,
                    )
                },
            )
        }
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null, isError = false)
    }
}
