package com.cmac.opscommand.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cmac.opscommand.BuildConfig
import com.cmac.opscommand.data.CmacApi
import com.cmac.opscommand.data.DashboardRepository
import com.cmac.opscommand.data.DashboardState
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The three boards, in rotation order. */
enum class Page { OVERVIEW, JOBS, COMMS }

data class ShellState(
    val page: Page = Page.OVERVIEW,
    val locked: Boolean = false,
    val jobsSubPage: Int = 0,
)

class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val serverUrl = BuildConfig.CMAC_SERVER_URL.trim()

    private val repo = DashboardRepository(
        api = CmacApi(serverUrl),
        baseUrl = serverUrl,
        cacheDir = app.filesDir,
    )

    val data: StateFlow<DashboardState> = repo.state

    private val _shell = MutableStateFlow(ShellState())
    val shell: StateFlow<ShellState> = _shell.asStateFlow()

    /**
     * Kept separate from [shell] so the once-a-second clock tick only recomposes
     * the clock itself instead of invalidating the whole dashboard tree.
     */
    private val _now = MutableStateFlow(Instant.now())
    val now: StateFlow<Instant> = _now.asStateFlow()

    /** Rows of the assignment table that fit one screen in the 1920x1080 design. */
    var jobsPerPage: Int = 24
        private set

    init {
        repo.start(viewModelScope)
        viewModelScope.launch { clockLoop() }
        viewModelScope.launch { rotateLoop() }
        viewModelScope.launch { jobsPagerLoop() }
    }

    private suspend fun clockLoop() {
        while (true) {
            _now.value = Instant.now()
            // Re-align to the top of the next wall-clock second so the seconds
            // digit never visibly stutters or skips.
            delay(1_000L - (System.currentTimeMillis() % 1_000L))
        }
    }

    private suspend fun rotateLoop() {
        while (true) {
            delay(PAGE_INTERVAL_MS)
            if (!_shell.value.locked) advance(+1)
        }
    }

    /**
     * A 381-job roster is ~16 screens. The web dashboard clipped everything past
     * the first ~19 rows with no way to see the rest; here the table advances
     * through the roster on its own and keeps its place between visits, so the
     * whole day's work eventually shows on the wall.
     */
    private suspend fun jobsPagerLoop() {
        while (true) {
            delay(JOBS_SUBPAGE_MS)
            val s = _shell.value
            if (s.locked || s.page != Page.JOBS) continue
            val total = totalJobPages()
            if (total > 1) {
                _shell.update { it.copy(jobsSubPage = (it.jobsSubPage + 1) % total) }
            }
        }
    }

    fun totalJobPages(): Int {
        val n = data.value.jobs.size
        if (n == 0) return 1
        return ((n + jobsPerPage - 1) / jobsPerPage).coerceAtLeast(1)
    }

    fun setJobsPerPage(rows: Int) {
        if (rows > 0 && rows != jobsPerPage) {
            jobsPerPage = rows
            val total = totalJobPages()
            _shell.update { it.copy(jobsSubPage = it.jobsSubPage.coerceIn(0, total - 1)) }
        }
    }

    // -- Remote-control actions -------------------------------------------

    fun advance(delta: Int) {
        val all = Page.entries
        _shell.update {
            val next = ((it.page.ordinal + delta) % all.size + all.size) % all.size
            it.copy(page = all[next])
        }
    }

    fun goTo(page: Page) = _shell.update { it.copy(page = page) }

    fun toggleLock() = _shell.update { it.copy(locked = !it.locked) }

    /** UP/DOWN pages the assignment table by hand; implies a manual hold. */
    fun nudgeJobsPage(delta: Int) {
        val total = totalJobPages()
        if (total <= 1) return
        _shell.update {
            val next = ((it.jobsSubPage + delta) % total + total) % total
            it.copy(jobsSubPage = next)
        }
    }

    companion object {
        const val PAGE_INTERVAL_MS = 15_000L
        const val JOBS_SUBPAGE_MS = 5_000L
    }
}
