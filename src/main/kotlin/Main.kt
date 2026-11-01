import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.reactivestreams.KMongo
import com.mongodb.client.model.Filters
import java.util.Scanner
import java.util.regex.Pattern

@Serializable
data class University(
    val name: String,
    val country: String,
    @SerialName("web_pages")
    val webPages: List<String> = emptyList()
)

fun main() = runBlocking {
    val scanner = Scanner(System.`in`)

    println("Введите название страны:")
    val countryInput = scanner.nextLine().trim()

    if (countryInput.isEmpty()) {
        println("Страна не может быть пустой")
        return@runBlocking
    }

    val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    val mongoClient = KMongo.createClient("mongodb://localhost:27017").coroutine
    val database = mongoClient.getDatabase("university_catalog")

    val safeName = countryInput.replace(" ", "_").replace("'", "")
    val collection = database.getCollection<University>("universities_$safeName")

    println("Запрос к API...")

    try {
        val universities: List<University> = httpClient.get("http://universities.hipolabs.com/search") {
            parameter("country", countryInput)
        }.body()

        println("Получено ${universities.size} вузов")

        collection.deleteMany(Filters.empty())

        if (universities.isNotEmpty()) {
            collection.insertMany(universities)
            println("Данные сохранены")
        } else {
            println("В ответе API нет вузов для этой страны")
            return@runBlocking
        }

    } catch (e: Exception) {
        println("Ошибка: ${e.message}")
        e.printStackTrace()
        return@runBlocking
    }

    while (true) {
        print("\nВведите название вуза для поиска (или 'exit'): ")
        val query = scanner.nextLine().trim()

        if (query.equals("exit", ignoreCase = true)) {
            println("Пока!")
            break
        }

        val found = collection.find(
            Filters.regex("name", Pattern.quote(query), "i")
        ).toList().firstOrNull()

        if (found != null) {
            println("Найдено: ${found.name} (${found.country})")
            if (found.webPages.isNotEmpty()) {
                println("Сайты:")
                found.webPages.forEach { println("   - $it") }
            }
        } else {
            println("Ничего не найдено по запросу '$query'.")
        }
    }
}
