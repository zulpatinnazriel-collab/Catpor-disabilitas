package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class ActivityItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val workerName: String = "",
    val workerId: String? = null,
    val date: String = "",
    val title: String = "",
    val category: String = "Lainnya",
    val location: String = "",
    val startTime: String? = null,
    val endTime: String? = null,
    val status: String = "PENDING",
    val notes: String? = null,
    val issues: String? = null,
    val resolution: String? = null,
    val photoBase64: String? = null,
    val excelSource: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any?>(
            "userId" to userId,
            "workerName" to workerName,
            "workerId" to workerId,
            "date" to date,
            "title" to title,
            "category" to category,
            "location" to location,
            "startTime" to startTime,
            "endTime" to endTime,
            "status" to status,
            "notes" to notes,
            "issues" to issues,
            "resolution" to resolution,
            "photoBase64" to photoBase64,
            "excelSource" to excelSource,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return map.filterValues { it != null } as Map<String, Any>
    }

    fun toUpdateMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any?>(
            "workerName" to workerName,
            "workerId" to workerId,
            "date" to date,
            "title" to title,
            "category" to category,
            "location" to location,
            "startTime" to startTime,
            "endTime" to endTime,
            "status" to status,
            "notes" to notes,
            "issues" to issues,
            "resolution" to resolution,
            "photoBase64" to photoBase64,
            "excelSource" to excelSource,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return map.filterValues { it != null } as Map<String, Any>
    }
}

@IgnoreExtraProperties
data class ExcelReportItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val workerName: String = "",
    val fileName: String = "",
    val fileType: String = "XLSX",
    val reportDate: String = "",
    val totalRows: Long = 0L,
    val status: String = "UPLOADED",
    val notes: String? = null,
    val fileSize: Long = 0L,
    val rawContentSummary: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any?>(
            "userId" to userId,
            "workerName" to workerName,
            "fileName" to fileName,
            "fileType" to fileType,
            "reportDate" to reportDate,
            "totalRows" to totalRows,
            "status" to status,
            "notes" to notes,
            "fileSize" to fileSize,
            "rawContentSummary" to rawContentSummary,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return map.filterValues { it != null } as Map<String, Any>
    }
}
