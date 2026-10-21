package ru.ubt.familyshield.server

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.auth.principal
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.header
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.SerializationException
import org.slf4j.event.Level
import ru.ubt.familyshield.server.db.Db
import ru.ubt.familyshield.server.services.ApiException
import ru.ubt.familyshield.server.services.BillingService
import ru.ubt.familyshield.server.services.DevicePrincipal
import ru.ubt.familyshield.server.services.DeviceService
import ru.ubt.familyshield.server.services.FamilyService
import ru.ubt.familyshield.server.services.MessageService
import ru.ubt.familyshield.server.services.PurchaseVerifier
import ru.ubt.familyshield.server.services.PushSender
import ru.ubt.familyshield.server.services.ThreatService
import ru.ubt.familyshield.shared.ShieldJson
import ru.ubt.familyshield.shared.crypto.B64
import ru.ubt.familyshield.shared.model.WebCategory
import ru.ubt.familyshield.shared.protocol.AcceptInviteRequest
import ru.ubt.familyshield.shared.protocol.AckRequest
import ru.ubt.familyshield.shared.protocol.ApiError
import ru.ubt.familyshield.shared.protocol.ApiPaths
import ru.ubt.familyshield.shared.protocol.CreateFamilyResponse
import ru.ubt.familyshield.shared.protocol.CreateInviteRequest
import ru.ubt.familyshield.shared.protocol.EntitlementResponse
import ru.ubt.familyshield.shared.protocol.HealthResponse
import ru.ubt.familyshield.shared.protocol.InviteResponse
import ru.ubt.familyshield.shared.protocol.MemberRemovalResponse
import ru.ubt.familyshield.shared.protocol.MembersResponse
import ru.ubt.familyshield.shared.protocol.PostMessageRequest
import ru.ubt.familyshield.shared.protocol.PostMessageResponse
import ru.ubt.familyshield.shared.protocol.PushTokenRequest
import ru.ubt.familyshield.shared.protocol.RegisterRequest
import ru.ubt.familyshield.shared.protocol.RegisterResponse
import ru.ubt.familyshield.shared.protocol.ReportNumberRequest
import ru.ubt.familyshield.shared.protocol.VerifyPurchaseRequest
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

const val SERVER_VERSION = "1.0.0"

class ShieldServices(
    val config: ServerConfig,
    val db: Db,
    verifier: PurchaseVerifier,
    push: PushSender,
    clock: () -> Long = System::currentTimeMillis,
) {
    val devices = DeviceService(db, clock)
    val billing = BillingService(db, verifier, config.entitlementKeys.private, clock)
    val families = FamilyService(db, billing, clock)
    val messages = MessageService(db, config.maxMessageBytes, push, clock)
    val threats = ThreatService(db, config.numberReportThreshold, clock)
}

private val REGISTER_LIMIT = RateLimitName("register")
private val INVITE_LIMIT = RateLimitName("invite")
private val DEVICE_LIMIT = RateLimitName("device")

fun ApplicationCall.clientIp(trustForwarded: Boolean): String =
    if (trustForwarded) request.header(HttpHeaders.XForwardedFor)?.substringBefore(',')?.trim() ?: request.origin.remoteHost
    else request.origin.remoteHost

fun Application.shieldModule(services: ShieldServices) {
    val config = services.config
    install(DefaultHeaders) {
        header("X-Content-Type-Options", "nosniff")
        header("Referrer-Policy", "no-referrer")
        header("Cache-Control", "no-store")
    }
    install(CallLogging) {
        level = Level.INFO
        // Paths only: no query strings, bodies or tokens in logs.
        format { call -> "${call.request.local.method.value} ${call.request.path()} -> ${call.response.status()?.value}" }
    }
    install(ContentNegotiation) { json(ShieldJson) }
    install(StatusPages) {
        exception<ApiException> { call, e -> call.respond(e.status, ApiError(e.code, e.message)) }
        exception<BadRequestException> { call, _ -> call.respond(HttpStatusCode.BadRequest, ApiError("bad_request", "Некорректный запрос")) }
        exception<SerializationException> { call, _ -> call.respond(HttpStatusCode.BadRequest, ApiError("bad_json", "Некорректный JSON")) }
        exception<IllegalArgumentException> { call, _ -> call.respond(HttpStatusCode.BadRequest, ApiError("bad_request", "Некорректный запрос")) }
        exception<Throwable> { call, e ->
            this@shieldModule.environment.log.error("Unhandled error on ${call.request.path()}", e)
            call.respond(HttpStatusCode.InternalServerError, ApiError("internal", "Внутренняя ошибка сервера"))
        }
        status(HttpStatusCode.TooManyRequests) { call, status ->
            call.respond(status, ApiError("rate_limited", "Слишком много запросов, попробуйте позже"))
        }
        status(HttpStatusCode.Unauthorized) { call, status ->
            call.respond(status, ApiError("unauthorized", "Требуется авторизация устройства"))
        }
    }
    install(RateLimit) {
        register(REGISTER_LIMIT) {
            rateLimiter(limit = 10, refillPeriod = 10.minutes)
            requestKey { it.clientIp(config.trustForwardedFor) }
        }
        register(INVITE_LIMIT) {
            rateLimiter(limit = 10, refillPeriod = 5.minutes)
            requestKey { it.clientIp(config.trustForwardedFor) }
        }
        register(DEVICE_LIMIT) {
            rateLimiter(limit = 240, refillPeriod = 60.seconds)
            requestKey { it.request.header(HttpHeaders.Authorization) ?: it.clientIp(config.trustForwardedFor) }
        }
    }
    install(Authentication) {
        bearer("device") {
            authenticate { credential -> services.devices.authenticate(credential.token) }
        }
    }

    routing {
        get(ApiPaths.HEALTH) { call.respond(HealthResponse("ok", SERVER_VERSION)) }
        // Public half of the entitlement signing key, baked into app builds as shield.entitlementPublicKey.
        get(ApiPaths.ENTITLEMENT_PUBLIC_KEY) { call.respondText(B64.encode(config.entitlementKeys.public.encoded)) }

        rateLimit(REGISTER_LIMIT) {
            post(ApiPaths.REGISTER) {
                val req = call.receive<RegisterRequest>()
                val (id, token) = services.devices.register(req.appVersion)
                call.respond(RegisterResponse(id, token))
            }
        }

        authenticate("device") {
            rateLimit(INVITE_LIMIT) {
                post(ApiPaths.ACCEPT_INVITE) {
                    val req = call.receive<AcceptInviteRequest>()
                    call.respond(services.families.acceptInvite(call.device(), req.inviteId))
                }
            }
            rateLimit(DEVICE_LIMIT) {
                put(ApiPaths.PUSH_TOKEN) {
                    val req = call.receive<PushTokenRequest>()
                    services.devices.setPushToken(call.device().deviceId, req.token.ifBlank { null })
                    call.respond(HttpStatusCode.NoContent)
                }
                delete(ApiPaths.DELETE_DEVICE) {
                    val device = call.device()
                    services.families.leave(device)
                    services.devices.delete(device.deviceId)
                    call.respond(HttpStatusCode.NoContent)
                }
                post(ApiPaths.FAMILIES) {
                    call.respond(CreateFamilyResponse(services.families.create(call.device())))
                }
                get(ApiPaths.MEMBERS) {
                    val device = call.device()
                    call.respond(MembersResponse(device.familyId.orEmpty(), services.families.members(device)))
                }
                delete("${ApiPaths.MEMBERS}/{deviceId}") {
                    val removed = services.families.removeMember(call.device(), call.parameters["deviceId"].orEmpty())
                    call.respond(MemberRemovalResponse(removed))
                }
                post(ApiPaths.LEAVE_FAMILY) {
                    services.families.leave(call.device())
                    call.respond(HttpStatusCode.NoContent)
                }
                post(ApiPaths.INVITES) {
                    val req = call.receive<CreateInviteRequest>()
                    val (id, expires) = services.families.createInvite(call.device(), req.role, req.wrappedSecrets, req.ttlSeconds, req.inviteId)
                    call.respond(InviteResponse(id, expires))
                }
                route(ApiPaths.MESSAGES) {
                    post {
                        val req = call.receive<PostMessageRequest>()
                        val delivered = services.messages.post(call.device(), req)
                        call.respond(PostMessageResponse(req.id, delivered))
                    }
                    get {
                        val after = call.request.queryParameters["after"]?.toLongOrNull() ?: 0L
                        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
                        call.respond(services.messages.pull(call.device(), after, limit))
                    }
                }
                post(ApiPaths.ACK) {
                    val req = call.receive<AckRequest>()
                    services.messages.ack(call.device(), req.upToSeq)
                    call.respond(HttpStatusCode.NoContent)
                }
                post(ApiPaths.VERIFY_PURCHASE) {
                    val req = call.receive<VerifyPurchaseRequest>()
                    val token = services.billing.verifyAndStore(call.device(), req.productId, req.purchaseId, req.invoiceId, req.sandbox)
                    call.respond(EntitlementResponse(token))
                }
                get(ApiPaths.ENTITLEMENT) {
                    call.respond(EntitlementResponse(services.billing.token(call.device())))
                }
                get(ApiPaths.THREAT_NUMBERS) {
                    val since = call.request.queryParameters["since"]?.toLongOrNull() ?: 0L
                    call.respond(services.threats.numbersSince(since))
                }
                post(ApiPaths.REPORT_NUMBER) {
                    val req = call.receive<ReportNumberRequest>()
                    services.threats.report(call.device(), req.number, req.category)
                    call.respond(HttpStatusCode.NoContent)
                }
                get(ApiPaths.THREAT_DOMAINS) {
                    val category = call.request.queryParameters["category"]?.let { runCatching { WebCategory.valueOf(it) }.getOrNull() }
                        ?: throw ApiException(HttpStatusCode.BadRequest, "bad_category", "Неизвестная категория")
                    val since = call.request.queryParameters["since"]?.toLongOrNull() ?: 0L
                    call.respond(services.threats.domainsSince(category, since, call.request.queryParameters["cursor"]))
                }
            }
        }
    }
}

private fun RoutingCall.device(): DevicePrincipal =
    principal<DevicePrincipal>() ?: throw ApiException(HttpStatusCode.Unauthorized, "unauthorized", "Требуется авторизация")
