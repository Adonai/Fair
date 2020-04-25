package com.kanedias.dybr.fair.dto

import com.squareup.moshi.Json
import moe.banana.jsonapi2.HasOne
import moe.banana.jsonapi2.JsonApi
import moe.banana.jsonapi2.Policy
import moe.banana.jsonapi2.Resource
import java.util.*

/**
 * Comment creation request. Text is mandatory and represented in html
 * Example:
 * ```
 * {
 *   "data": {
 *     "type": "comments",
 *     "attributes": {
 *       "content": "<div/>"
 *     },
 *     "relationships": {
 *       "entry": {
 *         "data": {
 *           "type": "entries",
 *           "id": 7
 *         }
 *       },
 *       "profile": {
 *         "data": {
 *           "type": "profiles",
 *           "id": 28
 *         }
 *       }
 *     }
 *   }
 * }
 * ```
 * @author Kanedias
 *
 * Created on 14.01.18
 */
@JsonApi(type = "comments", policy = Policy.SERIALIZATION_ONLY)
class CreateCommentRequest: Resource() {

    /**
     * Content of this comment. Represented in HTML format
     */
    @Json(name = "content")
    lateinit var content: String

    /**
     * Entry for which this comment is being created for
     */
    @Json(name = "entry")
    var entry : HasOne<Entry>? = null

    /**
     * Profile this comment is being created with
     */
    @Json(name = "profile")
    var profile : HasOne<OwnProfile>? = null
}

/**
 * Represents comment in User -> Profile -> Blog -> Entry -> Comment hierarchy.
 * Example:
 * ```
 * {
 *   "data": {
 *     "id": "5",
 *     "type": "comments",
 *     "links": {
 *       "self": "http://www.example.com/v1/comments/5"
 *     },
 *     "attributes": {
 *       "created-at": "2018-01-09T23:10:59.115Z",
 *       "updated-at": "2018-01-09T23:10:59.115Z",
 *       "content": {
 *         "blocks": [],
 *         "entityMap": {}
 *       }
 *     },
 *     "relationships": {
 *       "blog": {
 *         "links": {
 *           "self": "http://www.example.com/v1/comments/5/relationships/blog",
 *           "related": "http://www.example.com/v1/comments/5/blog"
 *         }
 *       },
 *       "entry": {
 *         "links": {
 *           "self": "http://www.example.com/v1/comments/5/relationships/entry",
 *           "related": "http://www.example.com/v1/comments/5/entry"
 *         }
 *       },
 *       "profile": {
 *         "links": {
 *           "self": "http://www.example.com/v1/comments/5/relationships/profile",
 *           "related": "http://www.example.com/v1/comments/5/profile"
 *         }
 *       }
 *     }
 *   }
 * }
 * ```
 *
 * @author Kanedias
 *
 * Created on 14.01.18
 */
@JsonApi(type = "comments", policy = Policy.DESERIALIZATION_ONLY)
class CommentResponse: Authored() {

    /**
     * Text of this comment in HTML format
     */
    @Json(name = "content")
    lateinit var content: String

    /**
     * Entry this comment was posted for
     */
    @Json(name = "entry")
    val entry = HasOne<Entry>()
}

typealias Comment = CommentResponse