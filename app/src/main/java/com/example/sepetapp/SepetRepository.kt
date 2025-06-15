package com.example.sepetapp

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

// --- Firestore Data Models ---
data class FirestoreSepet(
    val id: String = "",
    val durum: String = "BOS", // "BOS", "DOLU", "KULLANIMDA"
    val items: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    // Firestore için parametresiz constructor gerekli
    constructor() : this("")
    
    fun toSepet(): Sepet {
        return Sepet(
            id = id,
            durum = when (durum) {
                "BOS" -> SepetDurumu.BOS
                "DOLU" -> SepetDurumu.DOLU
                "KULLANIMDA" -> SepetDurumu.KULLANIMDA
                else -> SepetDurumu.BOS
            },
            items = items.toMutableList()
        )
    }
}

fun Sepet.toFirestoreSepet(): FirestoreSepet {
    return FirestoreSepet(
        id = id,
        durum = durum.name,
        items = items.toList(),
        updatedAt = System.currentTimeMillis()
    )
}

// --- Repository ---
class SepetRepository {
    private val db = FirebaseFirestore.getInstance()
    private val sepetlerCollection = db.collection("sepetler")

    // Tüm sepetleri real-time olarak dinle
    fun getSepetlerFlow(): Flow<Map<String, Sepet>> = callbackFlow {
        val listener: ListenerRegistration = sepetlerCollection
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val sepetMap = mutableMapOf<String, Sepet>()
                snapshot?.documents?.forEach { document ->
                    try {
                        val firestoreSepet = document.toObject(FirestoreSepet::class.java)
                        firestoreSepet?.let {
                            sepetMap[it.id] = it.toSepet()
                        }
                    } catch (e: Exception) {
                        // Log error but continue with other documents
                        e.printStackTrace()
                    }
                }
                trySend(sepetMap)
            }
        
        awaitClose { listener.remove() }
    }

    // Sepet ekle/güncelle
    suspend fun saveSepet(sepet: Sepet): Result<Unit> {
        return try {
            val firestoreSepet = sepet.toFirestoreSepet()
            sepetlerCollection.document(sepet.id).set(firestoreSepet).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Belirli bir sepeti getir
    suspend fun getSepet(sepetId: String): Result<Sepet?> {
        return try {
            val document = sepetlerCollection.document(sepetId).get().await()
            val firestoreSepet = document.toObject(FirestoreSepet::class.java)
            Result.success(firestoreSepet?.toSepet())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Sepet sil
    suspend fun deleteSepet(sepetId: String): Result<Unit> {
        return try {
            sepetlerCollection.document(sepetId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Ürün ekle
    suspend fun addItemToSepet(sepetId: String, item: String): Result<Unit> {
        return try {
            val sepetResult = getSepet(sepetId)
            if (sepetResult.isSuccess) {
                val sepet = sepetResult.getOrNull()
                if (sepet != null) {
                    sepet.items.add(item)
                    sepet.durum = if (sepet.items.isNotEmpty()) SepetDurumu.DOLU else SepetDurumu.BOS
                    saveSepet(sepet)
                } else {
                    // Yeni sepet oluştur
                    val yeniSepet = Sepet(
                        id = sepetId,
                        durum = SepetDurumu.DOLU,
                        items = mutableListOf(item)
                    )
                    saveSepet(yeniSepet)
                }
            } else {
                sepetResult
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Ürün çıkar
    suspend fun removeItemFromSepet(sepetId: String, item: String): Result<Unit> {
        return try {
            val sepetResult = getSepet(sepetId)
            if (sepetResult.isSuccess) {
                val sepet = sepetResult.getOrNull()
                if (sepet != null) {
                    sepet.items.remove(item)
                    sepet.durum = if (sepet.items.isEmpty()) SepetDurumu.BOS else SepetDurumu.DOLU
                    saveSepet(sepet)
                } else {
                    Result.failure(Exception("Sepet bulunamadı"))
                }
            } else {
                sepetResult
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Demo verileri ekle (ilk çalıştırmada)
    suspend fun initializeDemoData(): Result<Unit> {
        return try {
            val demoSepetler = listOf(
                Sepet("SEPET001", SepetDurumu.DOLU, mutableListOf("Elma", "Ekmek")),
                Sepet("SEPET002", SepetDurumu.BOS),
                Sepet("SEPET003", SepetDurumu.KULLANIMDA, mutableListOf("Süt")),
                Sepet("SEPET004", SepetDurumu.BOS)
            )

            demoSepetler.forEach { sepet ->
                saveSepet(sepet)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
} 