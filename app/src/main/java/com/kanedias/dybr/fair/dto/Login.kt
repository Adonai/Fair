package com.kanedias.dybr.fair.dto

import com.squareup.moshi.Json
import moe.banana.jsonapi2.JsonApi
import moe.banana.jsonapi2.Policy
import moe.banana.jsonapi2.Resource

/**
 * Login call request body. Used in network API logging in procedure.
 * All fields are necessary.
 * Example:
 * ```
 * {
 *     "email": "example@example.com",
 *     "password":"sample"
 * }
 * ```
 *
 * @author Kanedias
 *
 * Created on 16.11.17
 */
@JsonApi(type = "sessions", policy = Policy.SERIALIZATION_ONLY)
class LoginRequest : Resource() {
        @Json(name = "action")
        lateinit var action: String

        @Json(name = "email")
        lateinit var email: String

        @Json(name = "password")
        lateinit var password: String

        /**
         * Profile id to login with
         */
        @Json(name = "profile")
        var profile: String? = null

        /**
         * Needed only in confirmation request
         */
        @Json(name = "confirmation-token")
        var confirmToken: String? = null
}

@JsonApi(type = "sessions", policy = Policy.DESERIALIZATION_ONLY)
class LoginResponse : Resource() {
        @Json(name = "action")
        lateinit var action: String

        @Json(name = "access-token")
        lateinit var accessToken: String
}

// Confirmation of registration - not directly used in app
typealias ConfirmRequest = LoginRequest