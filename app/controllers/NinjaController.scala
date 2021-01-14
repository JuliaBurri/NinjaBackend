package controllers

import java.util

import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import akka.actor.{ Actor, ActorRef, ActorSystem, Props }
import akka.stream.Materializer
import de.htwg.se.ninja.NinjaGame
import de.htwg.se.ninja.controller.ControllerInterface
import de.htwg.se.ninja.model.component.component.component.component.Direction
import javax.inject._
import org.webjars.play.WebJarsUtil
import play.api.libs.json.{ JsObject, JsValue, Json }
import play.api.libs.streams.ActorFlow
import play.api.mvc.{ Action, _ }
import utils.auth.DefaultEnv
import com.mohiva.play.silhouette.api.{ HandlerResult, Silhouette }
import models.User

import scala.concurrent.{ ExecutionContext, Future }

import scala.concurrent.Future
import scala.swing.Reactor

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */

@Singleton
class NinjaController @Inject() (cc: ControllerComponents, silhouette: Silhouette[DefaultEnv])(implicit ec: ExecutionContext, webJarsUtil: WebJarsUtil, assets: AssetsFinder, system: ActorSystem, mat: Materializer) extends AbstractController(cc) {
  val controller: ControllerInterface = NinjaGame.controller
  val observer = new NinjaObserver();

  var socketToSend: NinjaWebSocketActorFactory.type = null;

  def interaction(message: String): Unit = {
    System.out.println("Message" + message)
    val body = Json.parse(message)
    val type1 = (body \ "type").get.as[String]
    val result = type1 match {
      //case "createGame" => ninja()
      case "state" => state()
      case "player1" => player((body \ "name").get.as[String])
      case "player2" => player((body \ "name").get.as[String])
      case "setFlag1" => setFlag((body \ "row").get.as[String], (body \ "col").get.as[String])
      case "setFlag2" => setFlag((body \ "row").get.as[String], (body \ "col").get.as[String])
      case "walk" => walk((body \ "row").get.as[String], (body \ "col").get.as[String], (body \ "d").get.as[String])
      case "next" => changeTurn()
    }
    observer.notifyObservers();
    Ok(result.toString)
  }

  def ninjaAsText = NinjaGame.tui.stateToString()

  def state = silhouette.SecuredAction.async { implicit request: SecuredRequest[DefaultEnv, AnyContent] =>
    Future.successful(Ok(controller.state.toString))
  }

  //  def ninja = Action {
  //    Ok(views.html.ninja(controller))
  //  }

  def player(name: String): JsObject = {
    controller.setName(name)
    controller.toJson
  }

  def setFlag(row: String, col: String): JsObject = {
    val rowInt = row.toInt
    val colInt = col.toInt
    controller.setFlag(rowInt, colInt)
    controller.toJson
  }

  def walk(row: String, col: String, d: String): JsObject = {
    val rowInt = row.toInt
    val colInt = col.toInt
    val dir = StringToDirection(d)
    controller.walk(rowInt, colInt, dir)
    controller.toJson
  }

  def changeTurn() = {
    controller.changeTurns()
    controller.toJson
  }

  def StringToDirection(d: String): Direction.direction = d match {
    case "r" => Direction.right
    case "l" => Direction.left
    case "u" => Direction.up
    case "d" => Direction.down
    case _ => Direction.up
  }

  def json = silhouette.SecuredAction.async { implicit request: SecuredRequest[DefaultEnv, AnyContent] =>
    Future.successful(Ok(controller.toJson.toString()))
  }

  def socket = WebSocket.acceptOrResult[String, String] { request =>
    implicit val req = Request(request, AnyContentAsEmpty)
    silhouette.SecuredRequestHandler { securedRequest =>
      Future.successful(HandlerResult(Ok, Some(securedRequest.identity)))
    }.map {
      case HandlerResult(r, Some(user)) => Right(
        ActorFlow.actorRef { out =>
          val tmp = NinjaWebSocketActorFactory;
          socketToSend = tmp;
          tmp.create(user, out)
        })
      case HandlerResult(r, None) => Left(r)
    }
  }

  object NinjaWebSocketActorFactory {
    def create(user: User, out: ActorRef) = {
      Props(new NinjaWebSocketActor(user, out))
    }
  }

  class NinjaWebSocketActor(user: User, out: ActorRef) extends Actor with Reactor with NinjaObservable {
    listenTo(controller)
    observer.addObserver(this)

    def receive = {
      case msg: String =>
        out ! (controller.toJson.toString)
        interaction(msg)
        println("Sent Json to Client" + msg)
        sendJsonToClient
    }

    def sendJsonToClient() = {
      println("Received event from Controller")
      out ! (controller.toJson.toString)
    }

    override def update(): Unit = {
      sendJsonToClient
    }
  }
}

class NinjaObserver {
  var list = new util.ArrayList[NinjaObservable]();
  def addObserver(o: NinjaObservable): Unit = {
    list.add(o)
  }
  def notifyObservers(): Unit = { list.forEach(e => e.update()) }

}

trait NinjaObservable {
  def update(): Unit
}

