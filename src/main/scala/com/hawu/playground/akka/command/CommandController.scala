package com.hawu.playground.akka.command

import java.math.BigInteger
import java.security.MessageDigest

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.hawu.playground.akka.producer.{KafkaMessage, SendKafkaMessageToTopic}
import com.hawu.playground.akka.utils.Serialization
class CommandController(kafkaProducer: ActorRef, replyProxy: ActorRef) extends Actor with ActorLogging {

  val commandTopic = context.system.settings.config.getString("playground.command.kafka.topic")

  def receive = {
    case command: PlaygroundCommand =>
      val timestamp = System.currentTimeMillis()
      command match {
        case cmd: CommandRequiresResponse =>
          val uuid = java.util.UUID.randomUUID().toString
          val hash = String.format(
            "%032x",
            new BigInteger(1, MessageDigest.getInstance("SHA-256")
              .digest(f"$command-$timestamp-$uuid".getBytes("UTF-8")))
          )

          val serializedCommand = Serialization(command)

          replyProxy.tell(KafkaMessage(timestamp, hash, serializedCommand), sender)
          kafkaProducer ! SendKafkaMessageToTopic(commandTopic, KafkaMessage(timestamp, hash, serializedCommand))

        case other =>
          kafkaProducer ! SendKafkaMessageToTopic(commandTopic, KafkaMessage(timestamp, "", Serialization(command)))
      }
  }
}

