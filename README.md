# scala-omg
A Scala library for online multiplayer games

[![Build Status](https://travis-ci.org/scalaomg/scalaomg-core.svg?branch=master)](https://travis-ci.org/scalaomg/scalaomg-core)
[![License](http://img.shields.io/badge/License-MIT-blue.svg)](https://mit-license.org/)
[![codecov](https://codecov.io/gh/scalaomg/scalaomg-core/branch/master/graph/badge.svg)](https://codecov.io/gh/scalaomg/scalaomg-core)
[![version](https://img.shields.io/badge/version-1.0.1-f39f37)](https://img.shields.io/badge/version-1.0.1-f39f37)

## Description

Scala-omg is a library that ease the development of online multiplayer games based on a client-server infrastructure.
The developer will be provided with high-level support that is game logic independent and ease the implementation of communication / synchronization mechanisms between client and server.

 
### Server side API:

- Creation and execution of a gameserver

- Match hosting

- Rooms management (e.g. lobby, chat room) with multiple players connected

- Public rooms

- Private rooms

- Game state management

- State mantainance

- Synchronization/communication between clients

- Matchmaking

- Clients roles management (e.g. game creator, simple player)


### Client side API

- Creation \ deletion of games

- Access \ Exit from existing matches

- Communication and synchronization with the gameserver

## Usage
### Import
If you are using sbt add the following line to your build.sbt file
```scala
libraryDependencies += "com.github.scalaomg" % "scala-omg" % "latest"
```
For other build tools check: https://mvnrepository.com/artifact/com.github.scalaomg/scala-omg/latest 

### GameServer Creation
To start and terminate a gameserver you can use the ```GameServer``` class:

```scala
object Server extends App {
  implicit private val executor: ExecutionContext = ExecutionContext.global
  private val gameServer = GameServer("localhost", 8080)
  gameServer onStart {
    println("GameServer started")
  }
  gameServer.start()
  Thread.sleep(5000)
  gameServer.terminate()
}
  ```

  ### Define new rooms

  You can create new type of rooms extending the ```ServerRoom``` trait.   
   In order to enable this rooms in the server you must use the ```defineRoomType``` method on the GameServer object

```scala
case class MyRoom() extends ServerRoom {
  override def onCreate(): Unit = {
      //define what to do when the room is created
    }
  override def onClose(): Unit = {
      //define what to do when the room is closed
    }
  override def onJoin(client: Client): Unit = {
      //define what to do when a client connects
    }
  override def onLeave(client: Client): Unit = {
      //define what to do when a client leaves
    }
  override def onMessageReceived(client: Client, message: Any)= {
      //define what to do when a messages is received
    }
}
object Server extends App {
  //...
  private val gameServer = GameServer("localhost", 8080)
  gameServer.defineRoom("my_room", MyRoom) //'my_room' is the name that clients will use to join/create this room
  //...
}
```
### Rooms with State
If you want to define a room with a state that needs to be synchronized with clients, you can mixin the ```SynchronizedRoomState``` trait. This trait is generic in the type of the state you want to synchronize and allows to define the update rate. The generic type must be Serializable since it will be sent through the network to clients.

The following example shows a room that can be used for a basic multiplayer game that simply has players moving in a 2D grid.

```scala
case class Player(x: Int, y: Int)
case class World(players: List[Player]) extends java.io.Serializable

case class MyRoom() extends ServerRoom with SynchronizedRoomState[World] {
  override val stateUpdateRate = 60 //update rate in milliseconds
  private var currentPlayers: Map[Client, Player] = Map.empty

  override def onCreate(): Unit = {}
  override def onClose(): Unit = {}
  override def onJoin(client: Client): Unit = this.currentPlayers = this.currentPlayers + (client -> Player(0, 0))
  override def onLeave(client: Client): Unit = this.currentPlayers = this.currentPlayers - client
  override def onMessageReceived(client: Client, message: Any): Unit = {
    //received client's new position
    val newPosition = message.asInstanceOf[(Int,Int)]
    this.currentPlayers = this.currentPlayers.updated(client, Player(newPosition))
  }
  
  override def currentState: World = {
    //this is called at each update tick. it should return the state of the game that needs to be sent to clients
    World(this.currentPlayers.values.toList)
  }
}
```

### Client
You can connect to a GameServer using the  ```Client ``` class: 
  ```scala
  val client = Client("localhost", 8080)
  ```
To join or create a room simply use the ```joinOrCreate``` method. It takes as parameters the name of the room to join/create and room options (that will be explained later)

```scala
object ClientApp extends App {
  implicit private val executor: ExecutionContext = ExecutionContext.global
  private val client = Client("localhost", 8080)
  client.joinOrCreate("my_room", FilterOptions.empty).onComplete {
    case Success(room) => // you have joined the room
    case Failure(_) => // connections failure
  }
}
```

### Interact with a joined room
When you join a room you are provided with a ```JoinedRoom``` class that enables the interaction with it.
```scala
    room.onStateChanged { state =>
        //do something when the game state defined in the room changes
    }
    room.onMessageReceived { msg =>
        //do something with the message received from the room 
    }
    room.send("hello") //send a generic message to the room
    room.leave() //leave the room
}
```

### A complete example: ChatRoom
The following code shows how to create a simple chat room. Clients can connect to the room and send messages; when the room receives a message it automatically broadcasts it to all clients (using the ```broadcast``` method).

```scala
case class ChatRoom() extends ServerRoom {
  override def onCreate(): Unit = println("Room Created")
  override def onClose(): Unit = println("Room Closed")
  override def onJoin(client: Client): Unit = this.broadcast(s"${client.id} Connected")
  override def onLeave(client: Client): Unit = this.broadcast(s"${client.id} Left")
  override def onMessageReceived(client: Client, message: Any): Unit = this.broadcast(s"${client.id}: $message")
}

object ChatRoomServer extends App {
  implicit private val executor: ExecutionContext = ExecutionContext.global
  private val gameServer: GameServer = GameServer("localhost", 8080)
  gameServer.defineRoom("chat_room", ChatRoom) 
  gameServer.start()
}

object ChatRoomClient extends App {
  implicit private val executor: ExecutionContext = ExecutionContext.global
  private val client = Client("localhost", 8080)
  client.joinOrCreate("chat_room", FilterOptions.empty).onComplete {
    case Success(room) =>
      room.onMessageReceived(msg => println(msg))
      while (true) {
        room.send(StdIn.readLine())
      }
    case Failure(_) => println("Something went wrong :-(")
  }
}
  ```
## Deployment
Since this library is based on akka-core and akka-http, reference.conf files must be merged when building fat jars. In order to do so you can use the sbt-assembly plugin https://github.com/sbt/sbt-assembly or follow the akka official guide at https://doc.akka.io/docs/akka/current/additional/packaging.html.

