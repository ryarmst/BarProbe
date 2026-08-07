package dev.barcodeworkbench.feature.catalogue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.barcodeworkbench.core.model.CodeFilter
import dev.barcodeworkbench.core.model.CodeLibrary
import dev.barcodeworkbench.core.model.CodeRepository
import dev.barcodeworkbench.core.model.CodeSortOrder
import dev.barcodeworkbench.core.model.SavedCode
import dev.barcodeworkbench.core.model.SymbologyId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CatalogueUiState(
    val libraries: List<CodeLibrary> = emptyList(),
    val selectedLibraryId: Long? = null,
    val query: String = "",
    val symbologyFilter: Set<SymbologyId> = emptySet(),
    val tagFilter: Set<String> = emptySet(),
    val sortOrder: CodeSortOrder = CodeSortOrder.NEWEST_FIRST,
    val allTags: List<String> = emptyList(),
    val message: String? = null,
) {
    val selectedLibrary: CodeLibrary?
        get() = libraries.firstOrNull { it.id == selectedLibraryId }

    val filter: CodeFilter
        get() = CodeFilter(query = query, symbologies = symbologyFilter, tags = tagFilter)

    val hasActiveFilter: Boolean get() = !filter.isEmpty
}

/**
 * Drives the catalogue.
 *
 * The code list is derived from the selected library and the active filter through
 * [flatMapLatest], so switching library or editing the search re-subscribes to a new
 * live query and cancels the old one. Nothing has to be invalidated by hand.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CatalogueViewModel @Inject constructor(
    private val repository: CodeRepository,
    /**
     * Exposed so the detail sheet can re-encode a stored payload. Entries persist
     * their payload rather than a rendered image, so the symbol is rebuilt on demand.
     */
    val encoder: dev.barcodeworkbench.barcode.engine.BarcodeEncoder,
) : ViewModel() {

    private val _state = MutableStateFlow(CatalogueUiState())
    val state: StateFlow<CatalogueUiState> = _state.asStateFlow()

    /** Everything that determines which query to run, collapsed for comparison. */
    private data class CodeQuery(
        val libraryId: Long?,
        val filter: CodeFilter,
        val order: CodeSortOrder,
    )

    val codes: StateFlow<List<SavedCode>> = _state
        .map { CodeQuery(it.selectedLibraryId, it.filter, it.sortOrder) }
        .distinctUntilChanged()
        .flatMapLatest { query ->
            if (query.libraryId == null) {
                flowOf(emptyList())
            } else {
                repository.observeCodes(query.libraryId, query.filter, query.order)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), emptyList())

    init {
        viewModelScope.launch {
            combine(
                repository.observeLibraries(),
                repository.observeAllTags(),
            ) { libraries, tags -> libraries to tags }
                .collect { (libraries, tags) ->
                    val current = _state.value
                    _state.value = current.copy(
                        libraries = libraries,
                        allTags = tags,
                        // Select the first library on arrival, and recover if the
                        // selected one was deleted underneath us.
                        selectedLibraryId = current.selectedLibraryId
                            ?.takeIf { id -> libraries.any { it.id == id } }
                            ?: libraries.firstOrNull()?.id,
                    )
                }
        }
    }

    fun selectLibrary(libraryId: Long) {
        _state.value = _state.value.copy(selectedLibraryId = libraryId, message = null)
    }

    fun setQuery(query: String) {
        _state.value = _state.value.copy(query = query)
    }

    fun toggleSymbologyFilter(id: SymbologyId) {
        val current = _state.value.symbologyFilter
        _state.value = _state.value.copy(
            symbologyFilter = if (id in current) current - id else current + id,
        )
    }

    fun toggleTagFilter(tag: String) {
        val current = _state.value.tagFilter
        _state.value = _state.value.copy(
            tagFilter = if (tag in current) current - tag else current + tag,
        )
    }

    fun setSortOrder(order: CodeSortOrder) {
        _state.value = _state.value.copy(sortOrder = order)
    }

    fun clearFilters() {
        _state.value = _state.value.copy(
            query = "",
            symbologyFilter = emptySet(),
            tagFilter = emptySet(),
        )
    }

    fun createLibrary(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            if (_state.value.libraries.any { it.name.equals(trimmed, ignoreCase = true) }) {
                _state.value = _state.value.copy(message = "A library called '$trimmed' exists")
                return@launch
            }
            val id = repository.createLibrary(trimmed)
            _state.value = _state.value.copy(selectedLibraryId = id, message = null)
        }
    }

    fun renameLibrary(libraryId: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.renameLibrary(libraryId, trimmed) }
    }

    fun deleteLibrary(libraryId: Long) {
        viewModelScope.launch { repository.deleteLibrary(libraryId) }
    }

    fun reorderLibrary(libraryId: Long, delta: Int) {
        val current = _state.value.libraries
        val index = current.indexOfFirst { it.id == libraryId }
        val target = index + delta
        if (index < 0 || target !in current.indices) return
        val reordered = current.toMutableList().apply { add(target, removeAt(index)) }
        viewModelScope.launch { repository.reorderLibraries(reordered.map { it.id }) }
    }

    /**
     * Updates an entry's metadata.
     *
     * The payload and symbology are deliberately not editable here. Changing them would
     * make it a different code, and the generator is the place to author one; silently
     * mutating a saved payload would break any label or note describing it.
     */
    fun updateMetadata(code: SavedCode, label: String?, notes: String?, tags: Set<String>) {
        viewModelScope.launch {
            repository.update(
                code.copy(
                    label = label?.trim()?.takeIf { it.isNotEmpty() },
                    notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                    tags = tags.mapNotNull { it.trim().takeIf(String::isNotEmpty) }.toSet(),
                ),
            )
        }
    }

    fun deleteCode(codeId: Long) {
        viewModelScope.launch { repository.delete(codeId) }
    }

    fun moveCode(codeId: Long, toLibraryId: Long) {
        viewModelScope.launch { repository.move(codeId, toLibraryId) }
    }

    fun copyCode(codeId: Long, toLibraryId: Long) {
        viewModelScope.launch { repository.copy(codeId, toLibraryId) }
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private companion object {
        /** Keeps the query alive briefly across configuration changes. */
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
