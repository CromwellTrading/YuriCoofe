package com.cafeteria.gestion.data.remote

import com.cafeteria.gestion.data.SalePayload
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @POST("api/v1/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<ApiEnvelope<AuthResponse>>

    @POST("api/v1/auth/logout")
    suspend fun logout(): Response<ApiEnvelope<Boolean>>

    @GET("api/v1/auth/me")
    suspend fun me(): Response<ApiEnvelope<AuthResponse>>

    @GET("api/v1/products")
    suspend fun getProducts(
        @Query("visibility") visibility: String? = null,
        @Query("category") category: String? = null,
        @Query("sort") sort: String? = "name",
        @Query("direction") direction: String? = "asc"
    ): Response<ApiEnvelope<List<ProductDto>>>

    @Multipart
    @POST("api/v1/products")
    suspend fun createProduct(
        @Part("product") productJson: RequestBody,
        @Part image: MultipartBody.Part?
    ): Response<ApiEnvelope<ProductDto>>

    @Multipart
    @PUT("api/v1/products/{id}")
    suspend fun updateProduct(
        @Path("id") id: String,
        @Part("product") productJson: RequestBody,
        @Part image: MultipartBody.Part?
    ): Response<ApiEnvelope<ProductDto>>

    @DELETE("api/v1/products/{id}")
    suspend fun deleteProduct(@Path("id") id: String): Response<ApiEnvelope<Boolean>>

    @POST("api/v1/sales")
    suspend fun submitSale(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body payload: SalePayload
    ): Response<ApiEnvelope<SaleAcceptedResponse>>

    @GET("api/v1/sales")
    suspend fun getSales(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("workerId") workerId: String? = null,
        @Query("limit") limit: Int = 100
    ): Response<ApiEnvelope<List<SaleHistoryDto>>>

    @GET("api/v1/dashboard")
    suspend fun getDashboard(@Query("from") from: String? = null, @Query("to") to: String? = null): Response<ApiEnvelope<DashboardDto>>

    @GET("api/v1/announcements")
    suspend fun getAnnouncements(@Query("includeScheduled") includeScheduled: Boolean = false): Response<ApiEnvelope<List<AnnouncementDto>>>

    @POST("api/v1/announcements")
    suspend fun createAnnouncement(@Body body: CreateAnnouncementRequest): Response<ApiEnvelope<AnnouncementDto>>

    @PATCH("api/v1/announcements/{id}/cancel")
    suspend fun cancelAnnouncement(@Path("id") id: String): Response<ApiEnvelope<Boolean>>

    @GET("api/v1/team")
    suspend fun getTeam(): Response<ApiEnvelope<List<TeamMemberDto>>>

    @POST("api/v1/team")
    suspend fun createWorker(@Body body: CreateWorkerRequest): Response<ApiEnvelope<TeamMemberDto>>

    @PATCH("api/v1/team/{id}/status")
    suspend fun updateWorkerStatus(@Path("id") id: String, @Body body: Map<String, Boolean>): Response<ApiEnvelope<TeamMemberDto>>

    @POST("api/v1/inventory/adjustments")
    suspend fun createInventoryAdjustment(@Body body: InventoryAdjustmentRequest): Response<ApiEnvelope<ProductDto>>

    @POST("api/v1/devices/push-token")
    suspend fun registerPushToken(@Body body: Map<String, String>): Response<ApiEnvelope<Boolean>>
}

data class SaleAcceptedResponse(
    val saleId: String,
    val clientSaleId: String,
    val status: String,
    val soldAt: String
)

data class SaleHistoryDto(
    val id: String,
    val clientSaleId: String? = null,
    val soldAt: String,
    val workerId: String,
    val workerName: String,
    val summary: String,
    val paymentMethod: String,
    val totalCUP: Long,
    val status: String = "SYNCED"
)
