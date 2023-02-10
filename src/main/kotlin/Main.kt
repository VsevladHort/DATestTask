import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.QueryMap
import java.lang.IllegalStateException

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy::class)
data class Tag(var userId: Long, var tagName: String) {
    // zero parameter constructors exist for json deserialization
    constructor() : this(0, "")
}

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy::class)
data class User(
    var answerCount: Long,
    var reputation: Long,
    var userId: Long,
    var location: String,
    var link: String,
    var profileImage: String,
    var displayName: String,
    var questionCount: Long
) {
    constructor() : this(0, 0, 0, "", "", "", "", 0)
}

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy::class)
data class DataWrapper<T>(
    var items: List<T>, var hasMore: Boolean, var quotaMax: Int, var quotaRemaining: Int
) {
    constructor() : this(listOf(), false, 0, 0)
}

interface StackExchangeService {
    @GET("users")
    fun getUsers(@QueryMap map: Map<String, String>): Call<DataWrapper<User>>

    @GET("users/{id}/top-tags")
    fun getTopTags(@Path("id") userId: String, @QueryMap map: Map<String, String>): Call<DataWrapper<Tag>>
}

// filter to get user objects only with fields that we are interested in:
// !)69Ph.wNi1CqQaOS*r9RLa1PrU0k
// tags filter with only what we are interested in:
// !9boy9ZEEx
fun main() {
    val retrofit = Retrofit.Builder().baseUrl("https://api.stackexchange.com/")
        .addConverterFactory(JacksonConverterFactory.create()).build()
    val service: StackExchangeService = retrofit.create(StackExchangeService::class.java)
    val usersPage = 1
    val usersPageMax = 2 // value to control amount of api calls
    val pageSize = 100
    val order = "desc"
    val minReputation = 223
    val sortBy = "reputation"
    val site = "stackoverflow"
    val usersFilter = "!)69Ph.wNi1CqQaOS*r9RLa1PrU0k"
    val tagsFilter = "!9boy9ZEEx"
    val allowedTags = setOf("java", ".net", "docker", "C#")
    val minQuestionsAnswered = 1
    val allowedLocations = listOf("Romania", "Moldova")
    val listOfUsers = getListOfUsersFromAPI(
        usersPage,
        pageSize,
        order,
        minReputation,
        sortBy,
        site,
        usersFilter,
        service,
        usersPageMax,
        allowedLocations,
        minQuestionsAnswered
    )
    val listOfTagAndUserPairs = listOfUsers.asSequence()
        .map { Pair(it, getUserTagList(it.userId, pageSize, site, tagsFilter, service)) }
        .filter { it.second.any { tag -> allowedTags.contains(tag.tagName) } }.toList()
    listOfTagAndUserPairs.forEach {
        println(
            it.first.displayName + " " + it.first.location
                    + " " + it.first.answerCount + " " + it.first.questionCount +
                    " " + stringifyListOfTagsToString(it.second) + " "
                    + it.first.link + " " + it.first.profileImage
        )
    }
}

private fun stringifyListOfTagsToString(list: List<Tag>): String {
    val builder = StringBuilder()
    list.forEach { builder.append(it.tagName).append(", ") }
    builder.removeRange(builder.length - 2, builder.length)
    return builder.toString()
}

private fun getListOfUsersFromAPI(
    usersPage: Int,
    pageSize: Int,
    order: String,
    minReputation: Int,
    sortBy: String,
    site: String,
    usersFilter: String,
    service: StackExchangeService,
    usersPageMax: Int,
    allowedLocations: List<String>,
    minQuestionsAnswered: Int
): List<User> {
    var usersPage1 = usersPage
    val usersRequestMap = HashMap<String, String>()
    usersRequestMap["page"] = usersPage1.toString()
    usersRequestMap["pagesize"] = pageSize.toString()
    usersRequestMap["order"] = order
    usersRequestMap["min"] = minReputation.toString()
    usersRequestMap["sort"] = sortBy
    usersRequestMap["site"] = site
    usersRequestMap["filter"] = usersFilter
    var usersFromStackOverflow = service.getUsers(usersRequestMap).execute().body()
        ?: throw IllegalStateException("No more allowed API calls or no Internet connection")
    val listOfUsers = mutableListOf<User>()
    usersFromStackOverflow.items.forEach { listOfUsers.add(it) }
    while (usersFromStackOverflow.hasMore && usersPage1 <= usersPageMax) {
        usersPage1++
        usersRequestMap["page"] = usersPage1.toString()
        usersFromStackOverflow = service.getUsers(usersRequestMap).execute().body()
            ?: throw IllegalStateException("No more allowed API calls or no Internet connection")
        usersFromStackOverflow.items.forEach { listOfUsers.add(it) }
        println("requests left: " + usersFromStackOverflow.quotaRemaining)
    }
    return listOfUsers.asSequence().filter { it.location.isNotEmpty() }.filter { it.location.isNotBlank() }
        .filter { it.location.contains(allowedLocations[0]) || it.location.contains(allowedLocations[1]) }
        .filter { it.answerCount >= minQuestionsAnswered }.toList()
}

private fun getUserTagList(
    userId: Long, pageSize: Int, site: String, tagsFilter: String, service: StackExchangeService
): List<Tag> {
    var page = 1
    val tagsRequestMap = HashMap<String, String>()
    tagsRequestMap["page"] = page.toString()
    tagsRequestMap["pagesize"] = pageSize.toString()
    tagsRequestMap["site"] = site
    tagsRequestMap["filter"] = tagsFilter
    var pageOfTagsOfUser = service.getTopTags(userId.toString(), tagsRequestMap).execute().body()
        ?: throw IllegalStateException("No more allowed API calls or no Internet connection")
    val listOfTags = mutableListOf<Tag>()
    pageOfTagsOfUser.items.forEach { listOfTags.add(it) }
    while (pageOfTagsOfUser.hasMore) {
        page++
        tagsRequestMap["page"] = page.toString()
        pageOfTagsOfUser = service.getTopTags(userId.toString(), tagsRequestMap).execute().body()
            ?: throw IllegalStateException("No more allowed API calls or no Internet connection")
        pageOfTagsOfUser.items.forEach { listOfTags.add(it) }
        println("requests left: " + pageOfTagsOfUser.quotaRemaining)
    }
    return listOfTags
}