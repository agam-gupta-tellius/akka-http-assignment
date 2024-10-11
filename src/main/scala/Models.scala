import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.*

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object Models {

  /* DATE FORMATTER */
  val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
  def getCurrentDate: String = LocalDateTime.now().format(dateFormatter)

  case class Users(id: Int, name: String, password: String, startTime: Option[String], createdAt: Option[String])
  trait UserJsonProtocol extends DefaultJsonProtocol {
    protected implicit val userFormat: RootJsonFormat[Users] = jsonFormat5(Users) // Adjust the number based on fields in Users case class
    implicit val usersListFormat: RootJsonFormat[List[Users]] = new RootJsonFormat[List[Users]] {
      def write(users: List[Users]): JsValue = JsArray(users.map(userFormat.write).toVector)

      def read(json: JsValue): List[Users] = json match {
        case JsArray(elements) =>
          elements.toList.map {
            case obj: JsObject => userFormat.read(obj)
            case _ => throw new DeserializationException("Expected List of Users")
          }
        case _ => throw new DeserializationException("Expected List of Users")
      }
    }}
}
