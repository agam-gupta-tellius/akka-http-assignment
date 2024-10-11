import Models.*
import akka.actor.ActorSystem
import scala.util.*
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.*
import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigFactory}
import java.sql.{Connection, DriverManager, ResultSet, Statement}
import scala.annotation.tailrec


class UserController(dbUrl: String, username: String, password: String) {
  // establish connection
  private def getConnection: Connection = DriverManager.getConnection(dbUrl, username, password)

  private def getUsersFromQueryResult(resultSet: ResultSet): List[Users] = {
    @tailrec
    def convertToList(accumulator: List[Users]): List[Users] = {
      if(!resultSet.next()) return accumulator
      val userId = resultSet.getInt("id")
      val username = resultSet.getString("name")
      val startTime = resultSet.getString("startTime")
      val password = resultSet.getString("password")
      val createdAt = resultSet.getString("createdAt")
      val user = Users(userId, username, password, Some(startTime), Some(createdAt))
      convertToList(accumulator :+ user)
    }
    convertToList(List.empty[Users])
  }

  def getAllUsers: List[Users] = {
    val connection: Connection = getConnection
    val statement: Statement = connection.createStatement()
    val query: String = "SELECT * FROM USERS"
    val users: List[Users] = getUsersFromQueryResult(statement.executeQuery(query))
    connection.close()
    statement.close()
    users
  }

  def getUserById(userId: Int): Option[Users] = {
    val connection = getConnection
    val statement = connection.createStatement()
    val query = s"SELECT * FROM USERS WHERE id = $userId"
    val resultSet = statement.executeQuery(query)
    val user: Option[Users] =
      if (resultSet.next()) {
        val id: Int = resultSet.getInt("id")
        val name: String = resultSet.getString("name")
        val startTime: String = resultSet.getString("startTime")
        val createdAt: String = resultSet.getString("createdAt")
        val password: String = resultSet.getString("password")
        Some(Users(id, name, password, Some(startTime), Some(createdAt)))
      } else {
        None
      }
    connection.close()
    statement.close()
    user
  }

  def addUser(newUser: Users): Option[Users] = {
    val connection = getConnection
    val query = "INSERT INTO USERS(id, name, password, startTime,createdAt) VALUES (?, ?, ?, ?, ?)"
    val preparedStatement = connection.prepareStatement(query)
//    println("adding user...")
    val currentDate: String = getCurrentDate
    println(currentDate)
    preparedStatement.setInt(1, newUser.id)
    preparedStatement.setString(2, newUser.name)
    preparedStatement.setString(3, newUser.password)
    preparedStatement.setString(4, currentDate)
    preparedStatement.setString(5, currentDate)
//    println(newUser.id)
//    println(newUser.name)
//    println(newUser.password)
//    println(currentDate)
//    println(currentDate)
    val queryString = s"SELECT name FROM USERS WHERE id=${newUser.id}"
    val queryResult = connection.createStatement().executeQuery(queryString)

    // if user exists already
    if (queryResult.next()) {
      preparedStatement.close()
      connection.close()
      None
    } else {
      preparedStatement.executeUpdate()
      preparedStatement.close()
      connection.close()
      Some(newUser)
    }
  }

  def updateUser(userId: Int, updatedUser: Users): Option[Users] = {
    val connection = getConnection
    val query = s"UPDATE USERS SET name = '${updatedUser.name}', password = '${updatedUser.password}', createdAt ='$getCurrentDate' WHERE id = $userId"
    val statement = connection.createStatement()
    val rowsUpdated = statement.executeUpdate(query)
    if (rowsUpdated == 1) {
      println(s"Executed Query: $query")
      println(s"Rows Updated: $rowsUpdated")
      statement.close()
      connection.close()
      Some(updatedUser)
    }
    else {
      None
    }
  }

  def authenticateUser(user: Users): Boolean = {
    val connection = getConnection
    val statement = connection.createStatement()
    val query = s"SELECT * FROM USERS WHERE id= '${user.id}' AND name='${user.name}' AND password='${user.password}'"
    val result = statement.executeQuery(query)

    val isAuthenticated: Boolean = result.next();
    statement.close()
    connection.close()
    isAuthenticated
  }

  def validatePassword(password: String): Boolean = {
    val hasDigit = """\d""".r.findFirstMatchIn(password).isDefined
    val hasChar = """[a-zA-Z]""".r.findFirstMatchIn(password).isDefined
    val hasSpecialChar = """[!@#$%^&*_]""".r.findFirstMatchIn(password).isDefined
    val hasValidLength = password.length > 8
    hasDigit && hasChar && hasSpecialChar && hasValidLength
  }
}

object UserController extends App with SprayJsonSupport with UserJsonProtocol {
  private val config: Config = ConfigFactory.load().getConfig("sql-configuration")
  private val dbUrl = config.getString("url")
  private val username = config.getString("username")
  private val password = config.getString("password")

  private val userController = new UserController(dbUrl, username, password)

  private val requestHandler = path("users")  {
    post{
      entity(as[Users]){
        user =>
          val result = Try[Option[Users]] {
            if(userController.validatePassword(user.password)) userController.addUser(user)
            else throw new IllegalArgumentException("The password should contain a digit,character, special character and length should be greater than 8")
          }

          result match {
            case Success(Some(addedUser)) =>
              complete((StatusCodes.Created, s"User '${addedUser.name}' has been successfully created."))
            case Success(None) =>
              complete((StatusCodes.Conflict, s"User ID '${user.id}' already exists in the database. Please use a different ID."))
            case Failure(ex: IllegalArgumentException) =>
              complete(StatusCodes.BadRequest, ex.getMessage)
            case Failure(ex) =>
              println(s"Error processing request: ${ex.getMessage}")
              complete(StatusCodes.InternalServerError, "An unexpected error occurred. Please try again later.")
          }
      }
    } ~
    get {
      parameter("id".as[Int]) { id =>
        val result = Try[Option[Users]](userController.getUserById(id))

        result match {
          case Success(Some(user: Users)) =>
            complete(StatusCodes.OK, user)
          case Success(None) =>
            complete(StatusCodes.NotFound, "User doesn't exist")
          case Failure(ex) =>
            println(s"Error processing request: ${ex.getMessage}")
            complete(StatusCodes.InternalServerError, "An unexpected error occurred. Please try again later.")
        }
      }
    } ~
    get {
      entity(as[Users]) {
        authUser =>
          val result = Try[List[Users]] {
            if(userController.authenticateUser(authUser)) userController.getAllUsers
            else throw new IllegalAccessException("Username or password is incorrect")
          }

          result match {
            case Success(list: List[Users]) =>
              complete(StatusCodes.OK, list)
            case Failure(ex: IllegalAccessException) =>
              complete(StatusCodes.Unauthorized, ex.getMessage)
            case Failure(ex) =>
              println(s"Error processing request: ${ex.getMessage}")
              complete(StatusCodes.InternalServerError, "An unexpected error occurred. Please try again later.")
          }
      }
    } ~
    patch {
      parameter("id".as[Int]) { id =>
        entity(as[Users]) {updatedUser =>
          val result: Try[Option[Users]] = Try(userController.updateUser(id, updatedUser))

          result match {
            case Success(Some(user: Users)) =>
              complete(StatusCodes.OK, user)
            case Success(None) =>
              complete(StatusCodes.NotFound, "No such user exists")
            case Failure(ex) =>
              println(s"Error processing request: ${ex.getMessage}")
              complete(StatusCodes.InternalServerError, "An unexpected error occurred. Please try again later.")
          }
        }
      }
    }
  }

  implicit val system: ActorSystem = ActorSystem("UserController")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  import system.dispatcher

  val bindingFuture = Http()
    .newServerAt("localhost", 8080)
    .bind(requestHandler)

  bindingFuture.onComplete {
    case Success(binding) =>
      println(s"Server online at http://localhost:8080/")
    case Failure(ex) =>
      println(s"Failed to start server: ${ex.getMessage}")
  }
}