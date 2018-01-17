package dybr.kanedias.com.fair

import android.content.Context
import android.widget.Toast
import com.squareup.moshi.Moshi
import com.squareup.moshi.Rfc3339DateJsonAdapter
import com.squareup.moshi.Types
import dybr.kanedias.com.fair.entities.*
import dybr.kanedias.com.fair.misc.HttpApiException
import dybr.kanedias.com.fair.misc.HttpException
import moe.banana.jsonapi2.Document
import moe.banana.jsonapi2.ObjectDocument
import moe.banana.jsonapi2.ResourceAdapterFactory
import moe.banana.jsonapi2.ResourceIdentifier
import okhttp3.*
import okio.BufferedSource
import java.util.concurrent.TimeUnit
import java.io.IOException
import java.util.*
import moe.banana.jsonapi2.ArrayDocument
import java.net.HttpURLConnection.*


/**
 * Singleton for outlining network-related operations. Has heavy dependencies on [Auth].
 * Common network operations and http client routines go here.
 *
 * State machine of API interchange can be roughly described as:
 * ```
 *  Android-client                      Dybr server
 *       |                                  |
 *       |                                  |
 *       |    Register/login to /users      |\
 *       | ------------------------------>  | \
 *       |    User info + access token      |  |
 *       | <------------------------------  |  |
 *       |                                  |  |-- Auth. Without this only limited guest mode is available.
 *       |     Get/create own profiles      |  |   Order of operations here is important.
 *       | ------------------------------>  |  |
 *       |   List of profiles/current       | /
 *       | <------------------------------  |/
 *       |                                  |
 *       |    Get diary entries/comments    |
 *       | ------------------------------>  | -- can be performed without auth, but with it content may be richer
 *       |    Post diary entries/comments   |
 *       | ------------------------------>  | -- guests can't post diary entries but can comment
 *       |      Post comments/pics          |
 *       | ------------------------------>  |\
 *       |      Change profile settings     | \
 *       | ------------------------------>  |  |
 *       |     Administrate communities     |  |-- authorized users only
 *       | ------------------------------>  |  |
 *       |     Like/dislike/emote           | /
 *       | ------------------------------>  |/
 *       |                                  |
 *       |     Responses/confirmations      |
 *       | <------------------------------  |
 *       |     Lists of entries/comments    |
 *       | <------------------------------  |
 *       |         URLs to images           |
 *       | <------------------------------  |
 *       |   Content of entries/comments    |
 *       | <------------------------------  |
 *       |                                  |
 * ```
 *
 * @author Kanedias
 *
 * Created on 12.11.17
 */
object Network {

    private val MAIN_DYBR_API_ENDPOINT = "https://dybr-staging-api.herokuapp.com/v1"

    private val USERS_ENDPOINT = "$MAIN_DYBR_API_ENDPOINT/users"
    private val SESSIONS_ENDPOINT = "$MAIN_DYBR_API_ENDPOINT/sessions"
    private val PROFILES_ENDPOINT = "$MAIN_DYBR_API_ENDPOINT/profiles"

    private val MIME_JSON_API = MediaType.parse("application/vnd.api+json")

    private val jsonApiAdapter = ResourceAdapterFactory.builder()
            .add(LoginRequest::class.java)
            .add(LoginResponse::class.java)
            .add(RegisterRequest::class.java)
            .add(RegisterResponse::class.java)
            .add(ProfileCreateRequest::class.java)
            .add(ProfileResponse::class.java)
            .add(BlogCreateRequest::class.java)
            .add(BlogResponse::class.java)
            .add(EntryCreateRequest::class.java)
            .add(EntryResponse::class.java)
            .add(CreateCommentRequest::class.java)
            .add(CommentResponse::class.java)
            .build()

    private val jsonConverter = Moshi.Builder()
            .add(jsonApiAdapter)
            .add(Date::class.java, Rfc3339DateJsonAdapter())
            .build()

    /**
     * OkHttp [Interceptor] for populating authorization header.
     *
     * Authorizes all requests if we have access token for account (i.e. logged in).
     */
    private val authorizer = Interceptor { chain ->
        val origRequest = chain.request()
        val alreadyHasAuth = origRequest.headers().names().contains("Authorization")

        // skip if we already have auth header in request
        if (Auth.user.accessToken == null || alreadyHasAuth)
            return@Interceptor chain.proceed(origRequest)

        val authorisedReq  = origRequest.newBuilder()
                .header("Authorization", "Bearer ${Auth.user.accessToken}")
                .build()

        return@Interceptor chain.proceed(authorisedReq)
    }

    private lateinit var httpClient: OkHttpClient

    fun init() {
        httpClient =  OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .connectionPool(ConnectionPool())
                .dispatcher(Dispatcher())
                .addInterceptor(authorizer)
                .build()
    }

    /**
     * Perform registration with specified register request
     * @param regInfo registration request with all necessary fields filled
     */
    fun createAccount(regInfo: RegisterRequest): RegisterResponse {
        val reqBody = RequestBody.create(MIME_JSON_API, toWrappedJson(regInfo))
        val req = Request.Builder().post(reqBody).url(USERS_ENDPOINT).build()
        val resp = httpClient.newCall(req).execute()
        if (!resp.isSuccessful) {
            throw extractErrors(resp)
        }

        // response is successful, should have register response in answer
        return fromWrappedJson(resp.body()!!.source(), RegisterResponse::class.java)!!
    }

    /**
     * Tries to deduce errors from non-successful response.
     * @param resp http response that was not successful
     * @return [HttpApiException] if specific errors were found or generic [HttpException] for this response
     */
    private fun extractErrors(resp: Response) : HttpException {
        if (resp.code() in HTTP_BAD_REQUEST until HTTP_INTERNAL_ERROR) { // 400-499: client error
            // try to get error info
            val errAdapter = jsonConverter.adapter(Document::class.java)
            val body = resp.body()!!.source()
            if (body.exhausted()) // no content
                return HttpException(resp)

            val errDoc = errAdapter.fromJson(body)!!
            if (errDoc.errors.isNotEmpty()) // likely
                return HttpApiException(resp, errDoc.errors)
        }
        return HttpException(resp)
    }

    /**
     * Logs in with specified account, i.e. creates session, obtains access token for this user
     * ands sets [Auth.user] if all went good.
     * After this call succeeds you can be certain that [httpClient] sends requests with correct
     * "Authorization" header.
     *
     * @param acc account to authenticate with
     * @return true if auth was successful, false otherwise
     * @throws IOException on connection fail
     */
    fun login(acc: Account) {
        // clear present access token, we're re-logging
        acc.accessToken = null

        val loginRequest = LoginRequest().apply {
            action = "login"
            email = acc.email
            password = acc.password
        }
        val body = RequestBody.create(MIME_JSON_API, toWrappedJson(loginRequest))
        val req = Request.Builder().post(body).url(SESSIONS_ENDPOINT).build()
        val resp = httpClient.newCall(req).execute()

        // unprocessable entity error can be returned when something is wrong with your input
        if (!resp.isSuccessful) {
            throw extractErrors(resp)
        }

        // if response is successful we should have login response in body
        val response = fromWrappedJson(resp.body()!!.source(), LoginResponse::class.java)!!

        // login successful
        // memorize the access token and use it in next requests
        Auth.updateCurrentUser(acc)
        acc.accessToken = response.accessToken

        // looks like we're logging in for the first time, retrieve user info for later
        if (acc.lastProfileId == null) {
            // get user info
            val reqUser = Request.Builder().url(USERS_ENDPOINT).build()
            val respUser = httpClient.newCall(reqUser).execute() // returns array with only one element
            val user = fromWrappedListJson(respUser.body()!!.source(), User::class.java)

            // memorize account info
            acc.apply {
                serverId = user[0].id
                createdAt = user[0].createdAt
                updatedAt = user[0].updatedAt
                isAdult = user[0].isAdult
            }
        }

        // last profile exists, try to load it
        populateProfile()
        populateBlog()
    }

    private fun <T: ResourceIdentifier> toWrappedJson(obj: T): String {
        val wrapped = ObjectDocument<T>().apply { set(obj) }
        val type = Types.newParameterizedType(Document::class.java, obj::class.java)
        val reqAdapter = jsonConverter.adapter<Document<T>>(type)
        return reqAdapter.toJson(wrapped)
    }

    private fun <T: ResourceIdentifier> fromWrappedJson(obj: BufferedSource, clazz: Class<T>): T? {
        val type = Types.newParameterizedType(Document::class.java, clazz)
        val reqAdapter = jsonConverter.adapter<Document<T>>(type)
        val doc = reqAdapter.fromJson(obj)!!
        return doc.asObjectDocument().get()
    }

    private fun <T: ResourceIdentifier> fromWrappedListJson(obj: BufferedSource, clazz: Class<T>): ArrayDocument<T> {
        val type = Types.newParameterizedType(Document::class.java, clazz)
        val reqAdapter = jsonConverter.adapter<Document<T>>(type)
        val doc = reqAdapter.fromJson(obj)!!
        return doc.asArrayDocument()
    }

    /**
     * Pull profile of current user. Account should have selected last profile and relevant access token by this point.
     * After the completion [Auth.profile] will be populated.
     *
     * @throws IOException on connection fail
     */
    private fun populateProfile() {
        if (Auth.user.lastProfileId == null)
            return // no profile selected, nothing to populate

        val req = Request.Builder().url("$PROFILES_ENDPOINT/${Auth.user.lastProfileId}").build()
        val resp = httpClient.newCall(req).execute()
        if (!resp.isSuccessful)
            throw HttpException(resp)

        // response is returned after execute call, body is not null
        val profile = fromWrappedJson(resp.body()!!.source(), OwnProfile::class.java)
        profile?.let { Auth.updateCurrentProfile(profile) } // all steps aren't required here, use this just to have all in one place
    }

    /**
     * Loads blog for requested profile if it's present and sets it in [Auth.blog]
     * Make sure profile is already populated in [populateProfile]
     */
    fun populateBlog() {
        if (Auth.profile?.id == null)
            return // no profile->no blog, nothing to populate

        val req = Request.Builder().url("$PROFILES_ENDPOINT/${Auth.profile?.id}/blog").build()
        val resp = httpClient.newCall(req).execute()
        if (!resp.isSuccessful)
            throw extractErrors(resp)

        val blog = fromWrappedJson(resp.body()!!.source(), Blog::class.java)
        blog?.let { Auth.updateBlog(it) }
    }

    /**
     * Load all profiles for current account. The account must be already set in [Auth.user].
     * Guests can't request any profiles.
     * @return list of profiles that are bound to currently logged in user
     */
    fun loadProfiles(): List<OwnProfile> {
        val req = Request.Builder().url("$USERS_ENDPOINT/${Auth.user.serverId}?include=profiles").build()
        val resp = httpClient.newCall(req).execute()
        if (!resp.isSuccessful)
            throw extractErrors(resp)

        // there's an edge-case when user is deleted on server but we still have Auth.user.serverId set
        val user = fromWrappedJson(resp.body()!!.source(), User::class.java) ?: return emptyList()
        return user.profiles.get(user.document)
    }

    /**
     * Remove own profile. Warning: this deletes diaries, comments and all associated data with it.
     * Use with caution.
     *
     * @warning doesn't work for now
     * @param prof Profile to delete. Only profile id is actually needed.
     */
    fun removeProfile(prof: OwnProfile) {
        val req = Request.Builder().delete().url("$PROFILES_ENDPOINT/${prof.id}").build()
        val resp = httpClient.newCall(req).execute()
        if (!resp.isSuccessful)
            throw HttpException(resp)

        // we don't need answer body
    }

    /**
     * Create new profile for logged in user
     * @param prof profile creation request with all the information in it
     */
    fun createProfile(prof: ProfileCreateRequest): OwnProfile {
        val reqBody = RequestBody.create(MIME_JSON_API, toWrappedJson(prof))
        val req = Request.Builder().url(PROFILES_ENDPOINT).post(reqBody).build()
        val resp = httpClient.newCall(req).execute()
        if (!resp.isSuccessful)
            throw HttpException(resp)

        // response is returned after execute call, body is not null
        return fromWrappedJson(resp.body()!!.source(), OwnProfile::class.java)!!
    }

    fun confirmRegistration(emailToConfirm: String, tokenFromMail: String): LoginResponse {
        val confirmation = ConfirmRequest().apply {
            action = "confirm"
            email = emailToConfirm
            confirmToken = tokenFromMail
        }

        val body = RequestBody.create(MIME_JSON_API, toWrappedJson(confirmation))
        val req = Request.Builder().post(body).url(SESSIONS_ENDPOINT).build()
        val resp = httpClient.newCall(req).execute()

        // unprocessable entity error can be returned when something is wrong with your input
        if (!resp.isSuccessful) {
            throw extractErrors(resp)
        }

        // if response is successful we should have login response in body
        return fromWrappedJson(resp.body()!!.source(), LoginResponse::class.java)!!
    }

    /**
     * Pull requested diary entries.
     * Examples of urls to pass to this function:
     * ```
     * https://dybr.ru/api/identity/<your-identity-name>/favorites
     * https://dybr.ru/api/entries/<someones-identity-name>
     * ```
     *
     * @param endpointUrl full url of endpoint to pull from
     */
    fun getEntries(endpointUrl: String): List<DiaryEntry> {
        val req = Request.Builder().url(endpointUrl).build()
        val resp = httpClient.newCall(req).execute()
        if (!resp.isSuccessful) {
            throw HttpException(resp)
        }

        // response is returned after execute call, body is not null
        val adapter = jsonConverter.adapter(EntryContainer::class.java)
        val container = adapter.fromJson(resp.body()!!.source())!!
        return container.entries
    }

    /**
     * Create diary entry on server.
     * @param entry entry to create. Should be filled with author, title, body content etc.
     * TODO: rewrite when actual API docs are available, add examples
     */
    fun createEntry(entry: DiaryEntry) {
        val adapter = jsonConverter.adapter(DiaryEntry::class.java)
        val body = RequestBody.create(MIME_JSON_API, adapter.toJson(entry))
        val req = Request.Builder().post(body).url("something").build()
        val resp = httpClient.newCall(req).execute()
        if (!resp.isSuccessful) {
            throw HttpException(resp)
        }
    }

    /**
     * Handle typical network-related problems.
     * * Handles API-level problems - answers with errors from API
     * * Handles HTTP-level problems - answers with strings from [errMapping]
     * * Handles connection IO-level problems
     * @param ctx context to get error string from
     * @param errMapping mapping of ids like `HttpUrlConnection.HTTP_NOT_FOUND -> R.string.not_found`
     */
    fun reportErrors(ctx: Context, ex: Exception, errMapping: Map<Int, Int> = emptyMap()) = when (ex) {
        // API exchange error, most specific
        is HttpApiException -> for (error in ex.errors) {
            Toast.makeText(ctx, error.detail, Toast.LENGTH_SHORT).show()
        }

        // non-200 code, but couldn't deduce any API errors
        is HttpException -> {
            if (errMapping.containsKey(ex.code)) {
                // we have, take value from map
                val key = ctx.getString(errMapping.getValue(ex.code))
                Toast.makeText(ctx, key, Toast.LENGTH_SHORT).show()
            } else {
                // we haven't, show generic error
                val errorText = ctx.getString(R.string.unexpected_website_error)
                Toast.makeText(ctx, "$errorText: ${ex.message}", Toast.LENGTH_SHORT).show()
            }
        }

        // generic connection-level error, show as-is
        is IOException -> {
            val errorText = ctx.getString(R.string.error_connecting)
            Toast.makeText(ctx, "$errorText: ${ex.localizedMessage}", Toast.LENGTH_SHORT).show()
        }

        else -> throw ex
    }
    private class EntryContainer {
        lateinit var entries: List<DiaryEntry>
    }
}