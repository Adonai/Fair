package com.kanedias.dybr.fair.dto

import com.squareup.moshi.Json
import moe.banana.jsonapi2.*
import java.io.Serializable

/**
 * Profile creation request.
 * Example of request body:
 * ```
 * {
 *   "data": {
 *     "type": "profiles",
 *     "attributes": {
 *       "nickname": "olaf",
 *       "birthday": "02-01",
 *       "description": "Repellendus tempore vel qui quia. "
 *     }
 *   }
 * }
 * ```
 */
@JsonApi(type = "profiles", policy = Policy.SERIALIZATION_ONLY)
class ProfileCreateRequest: Resource() {

    /**
     * Chosen nickname
     */
    @Json(name = "nickname")
    var nickname: String? = null

    /**
     * Birthday in DD-MM format
     */
    @Json(name = "birthday")
    var birthday: String? = null

    /**
     * Description of this profile (multiline text)
     */
    @Json(name = "description")
    var description: String? = null

    /**
     * Slug of the blog associated with this profile
     */
    @Json(name = "blog-slug")
    var blogSlug: String? = null

    /**
     * Title of the blog associated with this profile
     */
    @Json(name = "blog-title")
    var blogTitle: String? = null

    /**
     * Community marker. If true, the profile belongs to a community,
     * and anyone participating can post there.
     */
    @Json(name = "is-community")
    var isCommunity: Boolean = false

    /**
     * Preferences structure for this profile
     */
    @Json(name = "settings")
    var settings: ProfileSettings? = null
}

/**
 * OwnProfile is an actual identity that user wants to be associated with.
 * Has description, nickname, readers/world, diary info, address and so on.
 * Example (result of `/v2/profiles/{id}` call):
 * ```
 * {
 *   "data": {
 *     "id": "2",
 *     "type": "profiles",
 *     "links": {
 *       "self": "http://www.example.com/v2/profiles/2"
 *     },
 *     "attributes": {
 *       "created-at": "2017-12-17T18:36:31.555Z",
 *       "updated-at": "2017-12-17T18:36:31.555Z",
 *       "nickname": "lourdes_nader",
 *       "description": "Modi reiciendis accusantium. Mollitia dolores iste. Exercitationem autem velit soluta et. Dolorem voluptas omnis.",
 *       "birthday": "14-06",
 *       "settings": {
 *         "key": "value"
 *       }
 *     },
 *     "relationships": {
 *       "user": {
 *         "links": {
 *           "self": "http://www.example.com/v2/profiles/2/relationships/user",
 *           "related": "http://www.example.com/v2/profiles/2/user"
 *         }
 *       }
 *     }
 *   }
 * }
 * ```
 * @author Kanedias
 *
 * Created on 17.11.17
 */
@JsonApi(type = "profiles", policy = Policy.DESERIALIZATION_ONLY)
class ProfileResponse : Dated() {

    /**
     * Chosen nickname
     */
    @Json(name = "nickname")
    lateinit var nickname: String

    /**
     * Birthday in DD-MM format
     */
    @Json(name = "birthday")
    lateinit var birthday: String

    /**
     * Slug of the blog associated with this profile
     */
    @Json(name = "blog-slug")
    var blogSlug: String? = null

    /**
     * Title of the blog associated with this profile
     */
    @Json(name = "blog-title")
    var blogTitle: String? = null

    /**
     * Preferences structure for this profile
     */
    @Json(name = "settings")
    var settings: ProfileSettings = ProfileSettings()

    /**
     * Tags that this profile is using
     */
    @Json(name = "tags")
    var tags = mutableSetOf<Tag>()

    /**
     * Link to the user this profile belongs to
     */
    @Json(name = "user")
    var user = HasOne<User>()

    /**
     * Link to the readers of this profile
     */
    @Json(name = "readers")
    var readers = HasMany<OwnProfile>()

    /**
     * Community marker. If true, the profile belongs to a community,
     * and anyone participating can post there.
     */
    @Json(name = "is-community")
    var isCommunity: Boolean = false

    /**
     * Link to the subscribers of this profile
     */
    @Json(name = "favorites")
    var favorites = HasMany<OwnProfile>()

    /**
     * Link to the communities this profile participates in
     */
    @Json(name = "communities")
    var communities = HasMany<OwnProfile>()
}

data class Tag(
        val name: String,
        var entries: Int
) : Serializable

typealias OwnProfile = ProfileResponse

fun isBlogWritable(profile: OwnProfile?): Boolean {
    if (profile?.blogSlug == null)
        return false

    if (profile == Auth.profile)
        return true

    if (profile.isCommunity)
        return true

    return false
}

fun isMarkerBlog(profile: OwnProfile?) = profile == Auth.favoritesMarker || profile == Auth.worldMarker
