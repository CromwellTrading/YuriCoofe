package com.cafeteria.gestion.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cafeteria.gestion.CafeteriaApplication
import com.cafeteria.gestion.data.*
import com.cafeteria.gestion.data.local.ProductEntity
import com.cafeteria.gestion.data.remote.CreateAnnouncementRequest
import com.cafeteria.gestion.data.remote.CreateWorkerRequest
import com.cafeteria.gestion.data.remote.InventoryAdjustmentRequest
import com.cafeteria.gestion.data.remote.LocalStationDto
import com.cafeteria.gestion.sync.SalesSyncScheduler
import com.cafeteria.gestion.sync.SyncRunner
import com.cafeteria.gestion.sync.SyncStatus
import kotlinx.coroutines.launch

const val SCREEN_HOME = "HOME"
const val SCREEN_PRODUCTS = "PRODUCTS"
const val SCREEN_SELL = "SELL"
const val SCREEN_SALES = "SALES"
const val SCREEN_REPORTS = "REPORTS"
const val SCREEN_ANNOUNCEMENTS = "ANNOUNCEMENTS"
const val SCREEN_TEAM = "TEAM"
const val SCREEN_CUSTOMER_CATALOG = "CUSTOMER_CATALOG"
const val SCREEN_CUSTOMER_AUTH = "CUSTOMER_AUTH"
const val SCREEN_SETTINGS = "SETTINGS"
const val SCREEN_STATION = "LOCAL_STATION"

data class CafeteriaUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val session: UserSession? = null,
    val products: List<ProductEntity> = emptyList(),
    val sales: List<SaleSummary> = emptyList(),
    val pendingCount: Int = 0,
    val dashboard: DashboardSummary? = null,
    val pendingProductSales: List<PendingProductSalesSummary> = emptyList(),
    val reportPeriod: String = "DAY",
    val announcements: List<Announcement> = emptyList(),
    val team: List<TeamMember> = emptyList(),
    val screen: String = SCREEN_CUSTOMER_AUTH,
    val message: String? = null,
    val error: String? = null,
    val apiConfigured: Boolean = false,
    val online: Boolean = false,
    val search: String = "",
    val category: String = "Todas",
    val sortBy: String = "Nombre",
    val ascending: Boolean = true,
    val appearance: String = "LIGHT",
    val palette: String = "COFFEE",
    val stationPairingCode: String? = null,
    val localStations: List<LocalStationDto> = emptyList(),
    val stationStats: CafeRepository.LocalStationStats? = null
)

class CafeteriaViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as CafeteriaApplication
    private val repository = app.repository
    var state = kotlinx.coroutines.flow.MutableStateFlow(
        CafeteriaUiState(apiConfigured = repository.isBackendConfigured())
    )
        private set

    init {
        viewModelScope.launch {
            app.settingsStore.applyVisualRevampDefaults()
            val savedSession = runCatching { repository.restoreSession() }.getOrNull()
            val activeSession = savedSession
            val cachedAll = runCatching { repository.getCachedProducts() }.getOrDefault(emptyList())
            val cachedProducts = if (savedSession == null || savedSession.role == Roles.CUSTOMER) cachedAll.filter { it.publicVisible } else cachedAll
            val localSales = runCatching { repository.getRecentSales() }.getOrDefault(emptyList())
            val pending = runCatching { repository.pendingCount() }.getOrDefault(0)
            state.value = state.value.copy(
                loading = false,
                session = activeSession,
                products = cachedProducts,
                sales = localSales,
                pendingCount = pending,
                screen = when (savedSession?.role) {
                    Roles.OWNER -> SCREEN_HOME
                    Roles.WORKER -> SCREEN_SELL
                    Roles.CUSTOMER -> SCREEN_CUSTOMER_CATALOG
                    Roles.LOCAL_STATION -> SCREEN_STATION
                    else -> SCREEN_CUSTOMER_AUTH
                },
                online = repository.isNetworkAvailable()
            )
            if (savedSession?.role == Roles.LOCAL_STATION) {
                state.value = state.value.copy(stationStats = runCatching { repository.localStationStats() }.getOrNull())
            } else {
                refreshAll()
            }
            if (savedSession != null && (savedSession.role == Roles.OWNER || savedSession.role == Roles.WORKER)) app.registerPushTokenIfConfigured()
        }
        viewModelScope.launch {
            app.settingsStore.settings.collect { settings ->
                state.value = state.value.copy(appearance = settings.appearance, palette = settings.palette)
            }
        }
    }


    fun clearMessage() { state.value = state.value.copy(message = null, error = null) }
    fun showError(message: String) { state.value = state.value.copy(error = message, message = null) }
    fun setScreen(screen: String) { state.value = state.value.copy(screen = screen, message = null, error = null) }
    fun setSearch(value: String) { state.value = state.value.copy(search = value) }
    fun setCategory(value: String) { state.value = state.value.copy(category = value) }
    fun setSort(value: String) { state.value = state.value.copy(sortBy = value) }
    fun toggleSortDirection() { state.value = state.value.copy(ascending = !state.value.ascending) }

    fun login(identifier: String, password: String) = runBusy("No se pudo iniciar sesión.") {
        require(identifier.isNotBlank()) { "Escribe tu usuario o correo." }
        require(password.isNotBlank()) { "Escribe tu contraseña." }
        val session = repository.login(identifier, password)
        if (session.role != Roles.CUSTOMER) app.registerPushTokenIfConfigured()
        state.value = state.value.copy(
            session = session,
            screen = when (session.role) {
                Roles.OWNER -> SCREEN_HOME
                Roles.WORKER -> SCREEN_SELL
                Roles.LOCAL_STATION -> SCREEN_STATION
                else -> SCREEN_CUSTOMER_CATALOG
            },
            message = "Sesión iniciada.",
            error = null
        )
        refreshAll()
    }


    fun registerCustomer(displayName: String, password: String) = runBusy("No se pudo crear la cuenta.") {
        val session = repository.registerCustomer(displayName, password)
        state.value = state.value.copy(
            session = session,
            screen = SCREEN_CUSTOMER_CATALOG,
            message = if (session.accessToken.isBlank()) "Tu cuenta quedó guardada en este teléfono y se enviará al servidor cuando vuelva la conexión." else "Tu cuenta de cliente está lista.",
            error = null
        )
        SalesSyncScheduler.enqueueNow(getApplication())
        refreshAll()
    }

    fun openCustomerAuth() {
        state.value = state.value.copy(screen = SCREEN_CUSTOMER_AUTH, error = null, message = null)
    }


    fun setAppearance(value: String) {
        viewModelScope.launch { app.settingsStore.setAppearance(value) }
    }

    fun setPalette(value: String) {
        viewModelScope.launch { app.settingsStore.setPalette(value) }
    }

    fun enableNotifications() {
        val configured = app.registerPushTokenIfConfigured()
        state.value = state.value.copy(message = if (configured) "Se solicitó el registro de este teléfono para recibir avisos." else "Las notificaciones todavía necesitan configurar el servicio de mensajería y su conexión con el servidor.")
    }

    fun logout() = runBusy("No se pudo cerrar la sesión.") {
        repository.logout()
        state.value = state.value.copy(session = null, message = "Sesión cerrada.", screen = SCREEN_CUSTOMER_AUTH)
        refreshAll()
    }

    fun refreshAll() {
        viewModelScope.launch {
            if (state.value.session?.role == Roles.LOCAL_STATION) {
                val heartbeat = runCatching { repository.sendStationHeartbeatIfOnline() }
                state.value = state.value.copy(
                    online = repository.isNetworkAvailable(),
                    pendingCount = repository.pendingCount(),
                    stationStats = runCatching { repository.localStationStats() }.getOrNull(),
                    error = heartbeat.exceptionOrNull()?.message,
                    message = if (heartbeat.getOrNull() == true) "Estación autorizada por el servidor." else state.value.message
                )
                return@launch
            }
            val onlineNow = repository.isNetworkAvailable()
            state.value = state.value.copy(online = onlineNow)
            if (onlineNow && repository.isBackendConfigured() && runCatching { repository.hasPendingCustomerRegistration() }.getOrDefault(false)) {
                runCatching { repository.syncPendingCustomerRegistration() }
            }
            if (onlineNow && state.value.session?.role == Roles.OWNER) {
                runCatching { repository.registerPendingStationIfOnline() }
                runCatching { repository.loadLocalStations() }.onSuccess { stations ->
                    state.value = state.value.copy(localStations = stations)
                }
            }
            val storedSession = runCatching { repository.restoreSession() }.getOrNull()
            val currentSession = state.value.session
            if (currentSession?.role == Roles.CUSTOMER && currentSession.userId.startsWith("local-customer-") &&
                storedSession?.role == Roles.CUSTOMER && !storedSession.userId.startsWith("local-customer-")) {
                state.value = state.value.copy(session = storedSession, message = "Tu cuenta de cliente ya está sincronizada.")
            }
            refreshProductsQuietly()
            refreshSalesQuietly()
            refreshAnnouncementsQuietly()
            if (state.value.session?.role == Roles.OWNER) {
                val (from, to) = reportRange(state.value.reportPeriod)
                runCatching { repository.loadDashboard(from, to) }.onSuccess { state.value = state.value.copy(dashboard = it) }
                runCatching { repository.getPendingProductSales(from, to) }.onSuccess { state.value = state.value.copy(pendingProductSales = it) }
                runCatching { repository.loadTeam() }.onSuccess { state.value = state.value.copy(team = it) }
            }
        }
    }

    fun refreshProducts() = runBusy("No se pudo actualizar el catálogo.") {
        val list = repository.refreshProducts()
        state.value = state.value.copy(products = list, message = "Catálogo actualizado.")
    }

    private suspend fun refreshProductsQuietly() {
        if (!repository.isBackendConfigured() || !repository.isNetworkAvailable()) return
        runCatching { repository.refreshProducts() }.onSuccess { state.value = state.value.copy(products = it) }
    }

    fun recordSale(product: ProductEntity, units: Long, subunits: Long, method: String, onSaved: () -> Unit = {}) = runBusy("No se pudo guardar la venta.") {
        repository.recordSale(product, units, subunits, method)
        val (reportFrom, reportTo) = reportRange(state.value.reportPeriod)
        val localPendingProducts = repository.getPendingProductSales(reportFrom, reportTo)
        state.value = state.value.copy(
            products = repository.getCachedProducts(),
            sales = repository.getRecentSales(),
            pendingCount = repository.pendingCount(),
            pendingProductSales = localPendingProducts,
            message = "Venta guardada con su hora original. Se sincronizará al tener conexión.",
            error = null
        )
        SalesSyncScheduler.enqueueNow(getApplication())
        onSaved()
    }


    fun retryProductChanges() = runBusy("No se pudieron reintentar los cambios de catálogo.") {
        val count = repository.retryProductChangesForCurrentOwner()
        state.value = state.value.copy(pendingCount = repository.pendingCount(), message = if (count > 0) "$count cambio(s) de catálogo volverán a sincronizarse." else "No hay cambios de catálogo pendientes de revisión.")
    }

    fun retrySale(saleId: String) = runBusy("No se pudo reintentar la venta.") {
        repository.retrySale(saleId)
        state.value = state.value.copy(sales = repository.getRecentSales(), pendingCount = repository.pendingCount(), message = "Se volverá a enviar la venta con la misma fecha e identificador.")
    }

    fun saveProduct(draft: ProductDraft, onSaved: () -> Unit = {}) = runBusy("No se pudo guardar el producto.") {
        val product = repository.saveProduct(draft)
        state.value = state.value.copy(
            products = repository.getCachedProducts(),
            pendingCount = repository.pendingCount(),
            message = "${product.name} guardado en este teléfono. Se sincronizará automáticamente cuando haya conexión."
        )
        onSaved()
    }

    fun recordPhysicalCount(productId: String, units: Long, subunits: Long?, reason: String, onSaved: () -> Unit = {}) = runBusy("No se pudo guardar el conteo físico.") {
        repository.recordPhysicalCountOffline(productId, units, subunits, reason.trim())
        state.value = state.value.copy(
            products = repository.getCachedProducts(),
            pendingCount = repository.pendingCount(),
            message = "Conteo físico guardado con su revisión base. Se sincronizará o quedará para revisión si el servidor cambió."
        )
        onSaved()
    }

    fun renameCategory(oldName: String, newName: String, onSaved: () -> Unit = {}) = runBusy("No se pudo actualizar la categoría.") {
        val updated = repository.renameCategoryOffline(oldName, newName)
        state.value = state.value.copy(
            products = repository.getCachedProducts(),
            pendingCount = repository.pendingCount(),
            category = if (state.value.category.equals(oldName, true)) newName else state.value.category,
            message = "Categoría actualizada en $updated producto(s). Se sincronizará cuando haya conexión."
        )
        onSaved()
    }

    fun deactivateProduct(productId: String, onSaved: () -> Unit = {}) = runBusy("No se pudo desactivar el producto.") {
        val product = repository.deactivateProductOffline(productId)
        state.value = state.value.copy(
            products = repository.getCachedProducts(),
            pendingCount = repository.pendingCount(),
            message = "${product.name} quedó desactivado en este dispositivo. La baja se enviará al servidor en orden."
        )
        onSaved()
    }

    fun adjustInventory(productId: String, unitsDelta: Long, subunitsDelta: Long?, reason: String, onSaved: () -> Unit = {}) = runBusy("No se pudo ajustar el inventario.") {
        require(reason.isNotBlank()) { "Indica el motivo del ajuste." }
        repository.adjustInventoryOffline(productId, unitsDelta, subunitsDelta, reason.trim())
        state.value = state.value.copy(
            products = repository.getCachedProducts(),
            pendingCount = repository.pendingCount(),
            message = "Ajuste guardado en este teléfono. Se enviará en orden cuando haya conexión."
        )
        onSaved()
    }

    fun refreshSales() = runBusy("No se pudo actualizar el historial.") {
        refreshSalesMerged()
        state.value = state.value.copy(message = "Historial actualizado.")
    }

    private suspend fun refreshSalesQuietly() {
        runCatching { refreshSalesMerged() }
    }

    private suspend fun refreshSalesMerged() {
        val local = repository.getRecentSales()
        val remote = if (repository.isBackendConfigured() && repository.isNetworkAvailable()) {
            runCatching { repository.getRemoteSales() }.getOrDefault(emptyList())
        } else emptyList()
        val merged = (remote + local).distinctBy { it.id }.sortedByDescending { it.soldAt }
        state.value = state.value.copy(sales = merged, pendingCount = repository.pendingCount())
    }

    fun refreshReports(period: String = state.value.reportPeriod) = runBusy("No se pudieron cargar las estadísticas.") {
        val selectedPeriod = period.takeIf { it in setOf("DAY", "WEEK", "MONTH") } ?: "DAY"
        state.value = state.value.copy(reportPeriod = selectedPeriod, dashboard = null, pendingProductSales = emptyList())
        val (from, to) = reportRange(selectedPeriod)
        val summary = runCatching { repository.loadDashboard(from, to) }.getOrNull()
        val pending = repository.getPendingProductSales(from, to)
        state.value = state.value.copy(dashboard = summary, pendingProductSales = pending)
        if (summary == null) throw IllegalStateException(
            if (pending.isNotEmpty()) "No se pudo consultar el servidor. Se muestran aparte las ventas pendientes guardadas en este teléfono."
            else "No se pudo consultar el servidor. Comprueba la conexión e inténtalo de nuevo."
        )
        state.value = state.value.copy(message = "Estadísticas actualizadas.")
    }

    private suspend fun refreshAnnouncementsQuietly() {
        if (!repository.isBackendConfigured() || !repository.isNetworkAvailable()) return
        runCatching { repository.refreshAnnouncements() }.onSuccess { state.value = state.value.copy(announcements = it) }
    }

    fun refreshAnnouncements() = runBusy("No se pudieron cargar los avisos.") {
        state.value = state.value.copy(announcements = repository.refreshAnnouncements(), message = "Avisos actualizados.")
    }

    fun publishAnnouncement(title: String, message: String, publishAt: String, expiresAt: String?, onSaved: () -> Unit = {}) = runBusy("No se pudo publicar el aviso.") {
        require(title.isNotBlank()) { "Escribe un título." }
        require(message.isNotBlank()) { "Escribe el contenido del aviso." }
        val parsed = runCatching { java.time.OffsetDateTime.parse(publishAt) }.getOrNull()
            ?: throw IllegalArgumentException("La fecha debe incluir zona horaria, por ejemplo 2026-10-27T08:00:00-04:00.")
        val expiry = expiresAt?.takeIf { it.isNotBlank() }?.let {
            runCatching { java.time.OffsetDateTime.parse(it) }.getOrNull()
                ?: throw IllegalArgumentException("La fecha de vencimiento no tiene un formato válido.")
        }
        if (expiry != null) require(expiry.isAfter(parsed)) { "El vencimiento debe ser posterior a la publicación." }
        val created = repository.publishAnnouncement(CreateAnnouncementRequest(title.trim(), message.trim(), parsed.toString(), expiry?.toString(), true))
        state.value = state.value.copy(announcements = listOf(created) + state.value.announcements, message = "Aviso guardado.")
        onSaved()
    }

    fun cancelAnnouncement(id: String) = runBusy("No se pudo cancelar el aviso.") {
        repository.cancelAnnouncement(id)
        state.value = state.value.copy(announcements = state.value.announcements.filterNot { it.id == id }, message = "Aviso cancelado.")
    }

    fun refreshTeam() = runBusy("No se pudo cargar el equipo.") {
        state.value = state.value.copy(team = repository.loadTeam(), message = "Equipo actualizado.")
    }

    fun addWorker(name: String, identifier: String, password: String, onSaved: () -> Unit = {}) = runBusy("No se pudo crear la cuenta.") {
        require(name.isNotBlank()) { "Escribe el nombre de la trabajadora." }
        require(identifier.isNotBlank()) { "Escribe un usuario o correo." }
        require(password.length >= 8) { "La contraseña temporal debe tener al menos 8 caracteres." }
        val created = repository.createWorker(CreateWorkerRequest(name.trim(), identifier.trim(), password))
        state.value = state.value.copy(team = listOf(created) + state.value.team, message = "Cuenta creada para ${created.displayName}.")
        onSaved()
    }

    fun setWorkerActive(workerId: String, active: Boolean) = runBusy("No se pudo actualizar la cuenta.") {
        val updated = repository.setWorkerActive(workerId, active)
        state.value = state.value.copy(
            team = state.value.team.map { if (it.id == updated.id) updated else it },
            message = if (updated.active) "Cuenta activada." else "Cuenta desactivada."
        )
    }

    fun createStationPairingCode() = runBusy("No se pudo crear la estación local.") {
        val code = repository.generateStationPairingCode()
        val registered = runCatching { repository.registerPendingStationIfOnline() }.getOrDefault(false)
        val stations = if (registered) runCatching { repository.loadLocalStations() }.getOrDefault(state.value.localStations) else state.value.localStations
        state.value = state.value.copy(
            stationPairingCode = code,
            localStations = stations,
            message = if (registered) "Código creado y estación registrada en el servidor. La transferencia entre teléfonos sigue funcionando sin internet." else "Código creado localmente. El registro del servidor queda pendiente hasta recuperar internet."
        )
    }

    fun loadStationPairingCode() {
        viewModelScope.launch {
            state.value = state.value.copy(stationPairingCode = runCatching { repository.currentStationPairingCode() }.getOrNull())
        }
    }

    fun revokeStationPairingCode() = runBusy("No se pudo revocar la estación local.") {
        val revokedRemotely = repository.revokeStationPairingCode()
        val stations = if (revokedRemotely) runCatching { repository.loadLocalStations() }.getOrDefault(state.value.localStations) else state.value.localStations
        state.value = state.value.copy(
            stationPairingCode = null,
            localStations = stations,
            message = if (revokedRemotely) "Estación revocada en el servidor. El dispositivo ya no podrá autenticarse cuando vuelva a conectarse." else "Código local eliminado. No había una estación registrada en el servidor."
        )
    }

    fun revokeManagedStation(stationId: String) = runBusy("No se pudo revocar la estación.") {
        repository.revokeLocalStation(stationId)
        state.value = state.value.copy(
            localStations = repository.loadLocalStations(),
            stationPairingCode = if (state.value.stationPairingCode?.split('|')?.getOrNull(2) == stationId) null else state.value.stationPairingCode,
            message = "Estación revocada. El servidor rechazará futuras conexiones de ese dispositivo."
        )
    }

    fun activateStation(code: String) = runBusy("No se pudo activar la estación local.") {
        val session = repository.activateLocalStation(code)
        state.value = state.value.copy(
            session = session, screen = SCREEN_STATION, online = false,
            stationStats = repository.localStationStats(),
            message = "Estación local activada. Este dispositivo solo recibirá, conservará y entregará paquetes."
        )
    }

    fun exportTransferPackage(uri: Uri, code: String) = runBusy("No se pudo exportar el paquete.") {
        val result = repository.exportTransferPackage(uri, code)
        state.value = state.value.copy(message = result)
    }

    fun importTransferPackage(uri: Uri, code: String) = runBusy("No se pudo importar el paquete.") {
        val result = repository.importTransferPackage(uri, code)
        state.value = state.value.copy(
            stationStats = if (state.value.session?.role == Roles.LOCAL_STATION) repository.localStationStats() else state.value.stationStats,
            pendingCount = repository.pendingCount(),
            message = result.message + " · ${result.salesAdded + result.productOperationsAdded + result.inventoryOperationsAdded} operación(es) nuevas."
        )
        if (state.value.session?.role != Roles.LOCAL_STATION) SalesSyncScheduler.enqueueNow(getApplication())
    }

    /**
     * Traspaso por QR. No usa runBusy a propósito: el resultado vuelve al diálogo del traspaso (que tapa el
     * Snackbar global) y así los errores se muestran donde la persona está mirando.
     */
    fun prepareQrTransfer(onResult: (Result<CafeRepository.QrOutgoing>) -> Unit) {
        viewModelScope.launch {
            onResult(runCatching { repository.prepareQrTransfer() })
            state.value = state.value.copy(pendingCount = runCatching { repository.pendingCount() }.getOrDefault(state.value.pendingCount))
        }
    }

    fun receiveQrTransfer(encrypted: ByteArray, pin: String, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            state.value = state.value.copy(busy = true)
            val result = runCatching { repository.importQrTransfer(encrypted, pin) }
            if (result.isSuccess) {
                runCatching {
                    val cached = repository.getCachedProducts()
                    val current = state.value.session
                    state.value = state.value.copy(
                        products = if (current == null || current.role == Roles.CUSTOMER) cached.filter { it.publicVisible } else cached
                    )
                    refreshSalesMerged()
                }
                SalesSyncScheduler.enqueueNow(getApplication())
            }
            state.value = state.value.copy(
                busy = false,
                pendingCount = runCatching { repository.pendingCount() }.getOrDefault(state.value.pendingCount)
            )
            onResult(result.map { it.message })
        }
    }

    fun exportStationCollection(uri: Uri) = runBusy("No se pudo consolidar la estación.") {
        val result = repository.exportCollectedStationPackage(uri)
        state.value = state.value.copy(stationStats = repository.localStationStats(), message = result)
    }

    fun syncNow() {
        if (state.value.session?.role == Roles.LOCAL_STATION) {
            state.value = state.value.copy(message = "La estación local no se conecta al servidor. Entrega el paquete a Yuri o a un dispositivo autorizado cuando sea posible.")
            return
        }
        if (state.value.busy) return
        viewModelScope.launch {
            state.value = state.value.copy(busy = true, message = "Sincronizando con el servidor… Si estaba dormido puede tardar hasta un minuto.")
            val outcome = runCatching { SyncRunner.run(app) }
                .getOrElse { com.cafeteria.gestion.sync.SyncOutcome.Retry(SyncRunner.describe(it)) }
            SyncStatus.record(app, outcome)
            runCatching {
                val cached = repository.getCachedProducts()
                val current = state.value.session
                state.value = state.value.copy(
                    products = if (current == null || current.role == Roles.CUSTOMER) cached.filter { it.publicVisible } else cached
                )
                refreshSalesMerged()
            }
            state.value = state.value.copy(
                busy = false,
                online = repository.isNetworkAvailable(),
                pendingCount = repository.pendingCount(),
                message = outcome.userMessage()
            )
        }
    }

    private fun reportRange(period: String): Pair<String, String> {
        val zone = runCatching { java.time.ZoneId.of(state.value.session?.businessTimeZone ?: "America/Havana") }
            .getOrDefault(java.time.ZoneId.of("America/Havana"))
        val today = java.time.LocalDate.now(zone)
        val start = when (period) {
            "WEEK" -> today.with(java.time.DayOfWeek.MONDAY)
            "MONTH" -> today.withDayOfMonth(1)
            else -> today
        }
        val end = when (period) {
            "WEEK" -> start.plusWeeks(1)
            "MONTH" -> start.plusMonths(1)
            else -> start.plusDays(1)
        }
        val from = start.atStartOfDay(zone).toOffsetDateTime().toString()
        val to = end.atStartOfDay(zone).toOffsetDateTime().toString()
        return from to to
    }

    private fun runBusy(defaultError: String, action: suspend () -> Unit) {
        viewModelScope.launch {
            state.value = state.value.copy(busy = true, error = null, message = null)
            try {
                action()
            } catch (e: Exception) {
                state.value = state.value.copy(error = e.message?.takeIf { it.isNotBlank() } ?: defaultError)
            } finally {
                state.value = state.value.copy(busy = false, online = repository.isNetworkAvailable())
                val cached = runCatching { repository.getCachedProducts() }.getOrNull()
                if (cached != null) {
                    val current = state.value.session
                    val visible = if (current == null || current.role == Roles.CUSTOMER) cached.filter { it.publicVisible } else cached
                    state.value = state.value.copy(products = visible)
                }
                state.value = state.value.copy(pendingCount = runCatching { repository.pendingCount() }.getOrDefault(state.value.pendingCount))
            }
        }
    }
}
