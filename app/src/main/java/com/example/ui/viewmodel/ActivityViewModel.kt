package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ActivityItem
import com.example.data.model.ExcelReportItem
import com.example.data.model.UserProfile
import com.example.data.repository.ActivityRepository
import com.example.data.util.ParsedExcelSheet
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityViewModel(
    private val repository: ActivityRepository,
    val currentUserId: String,
    val currentUserEmail: String,
    val currentDisplayName: String
) : ViewModel() {

    // Bootstrapped Admin recognition for user: zulpatinnazriel@gmail.com
    val isAdmin: Boolean = currentUserEmail.equals("zulpatinnazriel@gmail.com", ignoreCase = true)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("Semua")
    val selectedCategory = _selectedCategory.asStateFlow()

    private val _selectedStatus = MutableStateFlow("Semua")
    val selectedStatus = _selectedStatus.asStateFlow()

    private val _selectedDate = MutableStateFlow<String?>(null) // YYYY-MM-DD
    val selectedDate = _selectedDate.asStateFlow()

    // Admin filter: null means All Workers (Seluruh Tim), or a specific worker's userId
    private val _selectedWorkerFilter = MutableStateFlow<String?>(null)
    val selectedWorkerFilter = _selectedWorkerFilter.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage = _userMessage.asStateFlow()

    // Active parsed excel preview sheet in memory
    private val _parsedExcelSheet = MutableStateFlow<ParsedExcelSheet?>(null)
    val parsedExcelSheet = _parsedExcelSheet.asStateFlow()

    init {
        // Automatically sync own user profile into Firestore
        viewModelScope.launch {
            val role = if (isAdmin) "ADMIN" else "WORKER"
            repository.syncUserProfile(
                UserProfile(
                    userId = currentUserId,
                    email = currentUserEmail,
                    displayName = currentDisplayName,
                    workerId = if (isAdmin) "ADM-001" else "FLD-${currentUserId.take(4).uppercase(Locale.ROOT)}",
                    department = if (isAdmin) "Koordinator / Administrator Lapangan" else "Teknisi Lapangan",
                    role = role
                )
            )
        }
    }

    // List of registered team workers (for Admin selector)
    val allWorkers: StateFlow<List<UserProfile>> = repository.observeAllWorkers()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    // 1. Raw real-time stream from Firestore (reacts to worker filter)
    val rawActivities: StateFlow<UiState<List<ActivityItem>>> = _selectedWorkerFilter
        .flatMapLatest { targetWorkerId ->
            repository.observeActivities(targetWorkerId, isAdmin)
                .map<List<ActivityItem>, UiState<List<ActivityItem>>> { list -> UiState.Success(list) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    Log.w(TAG, "Gagal mengamati kegiatan real-time", rootError)
                    emit(UiState.Error(rootError.message ?: "Gagal memuat data kegiatan."))
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    // 2. Filtered activities for UI presentation
    val filteredActivities: StateFlow<List<ActivityItem>> = combine(
        rawActivities,
        _searchQuery,
        _selectedCategory,
        _selectedStatus,
        _selectedDate
    ) { state, query, category, status, date ->
        if (state !is UiState.Success) return@combine emptyList()
        var list = state.data

        if (query.isNotBlank()) {
            val q = query.trim().lowercase(Locale.ROOT)
            list = list.filter {
                it.title.lowercase(Locale.ROOT).contains(q) ||
                it.location.lowercase(Locale.ROOT).contains(q) ||
                it.workerName.lowercase(Locale.ROOT).contains(q) ||
                (it.notes?.lowercase(Locale.ROOT)?.contains(q) == true) ||
                (it.workerId?.lowercase(Locale.ROOT)?.contains(q) == true)
            }
        }

        if (category != "Semua") {
            list = list.filter { it.category.equals(category, ignoreCase = true) }
        }

        if (status != "Semua") {
            list = list.filter { it.status.equals(status, ignoreCase = true) }
        }

        if (!date.isNullOrBlank()) {
            list = list.filter { it.date == date }
        }

        list
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    // 3. Excel Reports real-time stream
    val excelReports: StateFlow<UiState<List<ExcelReportItem>>> = _selectedWorkerFilter
        .flatMapLatest { targetWorkerId ->
            repository.observeExcelReports(targetWorkerId, isAdmin)
                .map<List<ExcelReportItem>, UiState<List<ExcelReportItem>>> { UiState.Success(it) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    emit(UiState.Error(rootError.message ?: "Gagal memuat histori laporan Excel."))
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCategory(category: String) {
        _selectedCategory.value = category
    }

    fun setStatus(status: String) {
        _selectedStatus.value = status
    }

    fun setDate(date: String?) {
        _selectedDate.value = date
    }

    fun setWorkerFilter(workerId: String?) {
        _selectedWorkerFilter.value = workerId
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun setParsedExcelSheet(sheet: ParsedExcelSheet?) {
        _parsedExcelSheet.value = sheet
    }

    fun addActivity(activity: ActivityItem, onComplete: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _isSyncing.value = true
            val res = repository.createActivity(activity)
            _isSyncing.value = false
            if (res.isSuccess) {
                _userMessage.value = "Kegiatan berhasil dicatat dan disinkronkan ke cloud!"
                onComplete(true, null)
            } else {
                val err = res.exceptionOrNull()?.localizedMessage ?: "Gagal menyimpan kegiatan"
                _userMessage.value = err
                onComplete(false, err)
            }
        }
    }

    fun updateActivity(activity: ActivityItem, onComplete: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _isSyncing.value = true
            val res = repository.updateActivity(activity)
            _isSyncing.value = false
            if (res.isSuccess) {
                _userMessage.value = "Pembaruan kegiatan berhasil disinkronkan!"
                onComplete(true, null)
            } else {
                val err = res.exceptionOrNull()?.localizedMessage ?: "Gagal memperbarui kegiatan"
                _userMessage.value = err
                onComplete(false, err)
            }
        }
    }

    fun deleteActivity(activityId: String, onComplete: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _isSyncing.value = true
            val res = repository.deleteActivity(activityId)
            _isSyncing.value = false
            if (res.isSuccess) {
                _userMessage.value = "Kegiatan berhasil dihapus."
                onComplete(true, null)
            } else {
                val err = res.exceptionOrNull()?.localizedMessage ?: "Gagal menghapus kegiatan"
                _userMessage.value = err
                onComplete(false, err)
            }
        }
    }

    fun toggleQuickStatus(item: ActivityItem) {
        val nextStatus = when (item.status) {
            "PENDING" -> "IN_PROGRESS"
            "IN_PROGRESS" -> "COMPLETED"
            "ISSUE" -> "COMPLETED"
            else -> "IN_PROGRESS"
        }
        updateActivity(item.copy(status = nextStatus))
    }

    fun verifyExcelReport(reportId: String) {
        viewModelScope.launch {
            _isSyncing.value = true
            val res = repository.verifyExcelReport(reportId)
            _isSyncing.value = false
            if (res.isSuccess) {
                _userMessage.value = "Laporan berhasil diverifikasi oleh Administrator!"
            } else {
                _userMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Gagal memverifikasi laporan"
            }
        }
    }

    fun importActivitiesFromExcel(
        sheet: ParsedExcelSheet,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        onComplete: (Int) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSyncing.value = true
            var count = 0
            val total = sheet.convertedActivities.size

            sheet.convertedActivities.forEachIndexed { index, act ->
                val res = repository.createActivity(act)
                if (res.isSuccess) count++
                onProgress(index + 1, total)
            }

            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            repository.createExcelReport(
                ExcelReportItem(
                    userId = currentUserId,
                    workerName = sheet.convertedActivities.firstOrNull()?.workerName ?: currentDisplayName,
                    fileName = sheet.fileName,
                    fileType = if (sheet.fileName.endsWith(".csv", true)) "CSV" else "XLSX",
                    reportDate = today,
                    totalRows = sheet.rows.size.toLong(),
                    status = if (isAdmin) "VERIFIED" else "PROCESSED",
                    notes = "Diimpor $count dari $total baris kegiatan ke database real-time.",
                    rawContentSummary = "Sheet: ${sheet.sheetName}, Kolom: ${sheet.headers.joinToString(", ")}"
                )
            )

            _isSyncing.value = false
            _userMessage.value = "Sukses mengimpor $count kegiatan dari file ${sheet.fileName}!"
            onComplete(count)
        }
    }

    fun registerUploadedExcelReport(
        sheet: ParsedExcelSheet,
        fileSize: Long,
        onComplete: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            _isSyncing.value = true
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val res = repository.createExcelReport(
                ExcelReportItem(
                    userId = currentUserId,
                    workerName = sheet.convertedActivities.firstOrNull()?.workerName ?: currentDisplayName,
                    fileName = sheet.fileName,
                    fileType = if (sheet.fileName.endsWith(".csv", true)) "CSV" else "XLSX",
                    reportDate = today,
                    totalRows = sheet.rows.size.toLong(),
                    status = if (isAdmin) "VERIFIED" else "UPLOADED",
                    fileSize = fileSize,
                    notes = if (isAdmin) "Laporan diunggah dan diverifikasi langsung oleh Administrator." else "Laporan Excel utama diunggah oleh karyawan.",
                    rawContentSummary = "Sheet: ${sheet.sheetName}, ${sheet.rows.size} baris data"
                )
            )
            _isSyncing.value = false
            if (res.isSuccess) {
                _userMessage.value = "Laporan utama ${sheet.fileName} berhasil diunggah ke server!"
                onComplete(true, null)
            } else {
                val err = res.exceptionOrNull()?.localizedMessage ?: "Gagal mengunggah laporan"
                _userMessage.value = err
                onComplete(false, err)
            }
        }
    }

    fun seedSampleTeamActivities() {
        viewModelScope.launch {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

            // Register dummy team workers in users collection so Admin can filter them
            val workers = listOf(
                UserProfile("worker_budi_01", "budi.lapangan@perusahaan.com", "Budi Pratama", "FLD-014", "Instalasi & FO", "WORKER"),
                UserProfile("worker_siti_02", "siti.survey@perusahaan.com", "Siti Rahmawati", "FLD-022", "Survey & Desain", "WORKER"),
                UserProfile("worker_ahmad_03", "ahmad.maint@perusahaan.com", "Ahmad Fauzi", "FLD-031", "Maintenance BTS", "WORKER")
            )
            workers.forEach { repository.syncUserProfile(it) }

            val samples = listOf(
                ActivityItem(
                    userId = "worker_budi_01",
                    workerName = "Budi Pratama",
                    workerId = "FLD-014",
                    date = today,
                    title = "Penarikan Kabel Fiber Optik Drop Core",
                    category = "Instalasi",
                    location = "Jl. Sudirman Kav 45, Jaksel",
                    startTime = "08:30",
                    endTime = "11:00",
                    status = "COMPLETED",
                    notes = "Panjang kabel 180m, redaman aman -18.4 dBm."
                ),
                ActivityItem(
                    userId = "worker_siti_02",
                    workerName = "Siti Rahmawati",
                    workerId = "FLD-022",
                    date = today,
                    title = "Survey Titik Tiang & Jalur Kabel Baru",
                    category = "Survey",
                    location = "Kawasan Industri Delta Silicon",
                    startTime = "09:00",
                    endTime = "12:00",
                    status = "IN_PROGRESS",
                    notes = "14 tiang terdata, izin kawasan sedang diajukan ke pengelola."
                ),
                ActivityItem(
                    userId = "worker_ahmad_03",
                    workerName = "Ahmad Fauzi",
                    workerId = "FLD-031",
                    date = today,
                    title = "Penggantian Modul Rectifier Power BTS",
                    category = "Maintenance",
                    location = "Tower BTS Kemang Timur",
                    startTime = "13:00",
                    endTime = "15:30",
                    status = "ISSUE",
                    notes = "Modul lama rusak akibat lonjakan arus petir.",
                    issues = "Sparepart modul cadangan di gudang tipe berbeda, butuh approval admin.",
                    resolution = "Admin menyetujui pengadaan darurat modul tipe 48V 50A."
                ),
                ActivityItem(
                    userId = currentUserId,
                    workerName = currentDisplayName,
                    workerId = if (isAdmin) "ADM-001" else "FLD-001",
                    date = today,
                    title = "Supervisi & Koordinasi Proyek Lapangan",
                    category = "Koordinasi",
                    location = "Kantor Operasional & Wilayah Jabodetabek",
                    startTime = "08:00",
                    endTime = "17:00",
                    status = "IN_PROGRESS",
                    notes = "Review laporan Excel harian seluruh tim dan pemantauan kendala."
                )
            )
            samples.forEach { repository.createActivity(it) }
            _userMessage.value = "Data tim lapangan dan kegiatan simulasi berhasil ditambahkan!"
        }
    }

    private companion object {
        const val TAG = "ActivityViewModel"
    }
}
