package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ActivityItem
import com.example.data.model.ExcelReportItem
import com.example.data.model.UserProfile
import com.example.data.util.OperationType
import com.example.data.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ActivityRepository(
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Pengguna harus masuk dengan akun Google.")
    }

    // 1. Observe activities (All activities if Admin & targetUserId is null, or filtered by user)
    fun observeActivities(targetUserId: String?, isAdmin: Boolean): Flow<List<ActivityItem>> {
        val path = "activities"
        val query = if (isAdmin && targetUserId == null) {
            db.collection(path)
        } else {
            val uid = targetUserId ?: requireUserId()
            db.collection(path).whereEqualTo("userId", uid)
        }

        return query
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    ActivityItem::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedWith(
                    compareByDescending<ActivityItem> { it.date }
                        .thenByDescending { it.createdAt?.seconds ?: 0L }
                )
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    // 2. Create activity
    suspend fun createActivity(activity: ActivityItem): Result<String> {
        val path = "activities"
        return try {
            val uid = requireUserId()
            val docRef = if (activity.id.isNotBlank()) {
                db.collection(path).document(activity.id)
            } else {
                db.collection(path).document()
            }
            // If activity.userId is already set (e.g. assigned by admin), keep it, otherwise default to current uid
            val finalUserId = if (activity.userId.isNotBlank()) activity.userId else uid
            val payload = activity.copy(userId = finalUserId, id = docRef.id).toCreateMap()
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // 3. Update activity
    suspend fun updateActivity(activity: ActivityItem): Result<Unit> {
        val path = "activities/${activity.id}"
        return try {
            require(activity.id.isNotBlank()) { "Activity ID tidak boleh kosong" }
            val docRef = db.collection("activities").document(activity.id)
            val payload = activity.toUpdateMap()
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // 4. Delete activity
    suspend fun deleteActivity(activityId: String): Result<Unit> {
        val path = "activities/$activityId"
        return try {
            require(activityId.isNotBlank()) { "Activity ID tidak boleh kosong" }
            db.collection("activities").document(activityId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // 5. Observe uploaded Excel reports (All reports if Admin & targetUserId is null, or filtered)
    fun observeExcelReports(targetUserId: String?, isAdmin: Boolean): Flow<List<ExcelReportItem>> {
        val path = "excel_reports"
        val query = if (isAdmin && targetUserId == null) {
            db.collection(path)
        } else {
            val uid = targetUserId ?: requireUserId()
            db.collection(path).whereEqualTo("userId", uid)
        }

        return query
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    ExcelReportItem::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    // 6. Create Excel report log
    suspend fun createExcelReport(report: ExcelReportItem): Result<String> {
        val path = "excel_reports"
        return try {
            val uid = requireUserId()
            val docRef = if (report.id.isNotBlank()) {
                db.collection(path).document(report.id)
            } else {
                db.collection(path).document()
            }
            val finalUserId = if (report.userId.isNotBlank()) report.userId else uid
            val payload = report.copy(userId = finalUserId, id = docRef.id).toCreateMap()
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // 7. Verify / Approve Excel Report (Admin action)
    suspend fun verifyExcelReport(reportId: String): Result<Unit> {
        val path = "excel_reports/$reportId"
        return try {
            db.collection("excel_reports").document(reportId)
                .update("status", "VERIFIED")
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // 8. Sync User Profile to Firestore on login
    suspend fun syncUserProfile(profile: UserProfile): Result<Unit> {
        val path = "users/${profile.userId}"
        return try {
            db.collection("users").document(profile.userId).set(profile.toMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // 9. Observe all field workers (For Admin user directory)
    fun observeAllWorkers(): Flow<List<UserProfile>> {
        val path = "users"
        return db.collection(path)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedBy { it.displayName }
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }
}
