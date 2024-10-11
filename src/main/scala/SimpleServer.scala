import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import scala.util.{Failure, Success}

object SimpleServer extends App {
  // Create the ActorSystem
  implicit val system: ActorSystem = ActorSystem("simple-system")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  import system.dispatcher

  // Define the route
  val route =
    path("hello") {
      get {
        complete("Hello, Akka HTTP!")
      }
    }

  // Start the server using the new method
  val bindingFuture = Http()
    .newServerAt("localhost", 9090)
    .bind(route)

  // Handle the future of the binding
  bindingFuture.onComplete {
    case Success(binding) =>
      println(s"Server started at ${binding.localAddress}")
    case Failure(ex) =>
      println(s"Failed to start server: ${ex.getMessage}")
  }
}
