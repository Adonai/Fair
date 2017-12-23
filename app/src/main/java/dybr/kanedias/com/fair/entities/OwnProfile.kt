package dybr.kanedias.com.fair.entities

import com.squareup.moshi.Json
import moe.banana.jsonapi2.HasOne
import moe.banana.jsonapi2.JsonApi
import moe.banana.jsonapi2.Policy
import moe.banana.jsonapi2.Resource
import java.util.*

/**
 * OwnProfile is essentially a profile with readers/favorites, diary info, address and so on.
 * Example (result of `/v1/own-profiles/{id}` call):
 * ```
 * {
 *   "data": {
 *     "id": "2",
 *     "type": "own-profiles",
 *     "links": {
 *       "self": "http://www.example.com/v1/own-profiles/2"
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
 *           "self": "http://www.example.com/v1/own-profiles/2/relationships/user",
 *           "related": "http://www.example.com/v1/own-profiles/2/user"
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
@JsonApi(type = "own-profiles", policy = Policy.DESERIALIZATION_ONLY)
class OwnProfile : Resource() {

    /**
     * Chosen nickname
     */
    @field:Json(name = "nickname")
    lateinit var nickname: String

    /**
     * Date this profile was created, as returned by users API request
     */
    @field:Json(name = "created-at")
    lateinit var createdAt: Date

    /**
     * Date this profile was updated, as returned by users API request
     */
    @field:Json(name = "updated-at")
    lateinit var updatedAt: Date

    /**
     * Birthday in DD-MM format
     */
    @field:Json(name = "birthday")
    lateinit var birthday: String

    /**
     * Key-value settings for this profile
     */
    @field:Json(name = "settings")
    var settings: Map<String, String> = emptyMap()

    /**
     * Link to the user this profile belongs to
     */
    var user = HasOne<User>()
}