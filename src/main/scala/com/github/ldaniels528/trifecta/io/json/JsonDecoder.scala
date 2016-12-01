package com.github.ldaniels528.trifecta.io.json

import com.github.ldaniels528.trifecta.messages.logic.Expressions._
import com.github.ldaniels528.trifecta.messages.logic.MessageEvaluation._
import com.github.ldaniels528.trifecta.messages.logic.{Condition, MessageEvaluation}
import com.github.ldaniels528.trifecta.messages.{BinaryMessage, MessageDecoder}
import net.liftweb.json.JsonAST.{JNull, JValue}
import net.liftweb.json._
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

/**
  * JSON Message Decoder
  * @author lawrence.daniels@gmail.com
  */
object JsonDecoder extends MessageDecoder[JValue] with MessageEvaluation {
  private lazy val logger = LoggerFactory.getLogger(getClass)

  /**
    * Compiles the given operation into a condition
    * @param operation the given operation
    * @return a condition
    */
  override def compile(operation: Expression): Condition = {
    operation match {
      case EQ(field, value) => JsonEQ(this, field, value)
      case GE(field, value) => JsonGE(this, field, value)
      case GT(field, value) => JsonGT(this, field, value)
      case LE(field, value) => JsonLE(this, field, value)
      case LT(field, value) => JsonLT(this, field, value)
      case NE(field, value) => JsonNE(this, field, value)
      case _ => throw new IllegalArgumentException(s"Illegal operation '$operation'")
    }
  }

  /**
    * Decodes the binary message into a typed object
    * @param message the given binary message
    * @return a decoded message wrapped in a Try-monad
    */
  override def decode(message: Array[Byte]): Try[JValue] = Try(parse(new String(message)))

  /**
    * Evaluates the message; returning the resulting field and values
    * @param msg    the given [[BinaryMessage binary message]]
    * @param fields the given subset of fields to return
    * @return the mapping of fields and values
    */
  override def evaluate(msg: BinaryMessage, fields: Seq[String]): Map[String, Any] = {
    decode(msg.message) match {
      case Success(JObject(mapping)) =>
        Map(mapping.map(f => f.name -> unwrap(f.value)): _*) filter {
          case (k, v) => fields.isAllFields || fields.contains(k)
        }
      case Success(_) => Map.empty
      case Failure(e) =>
        throw new IllegalStateException("Malformed JSON message", e)
    }
  }

  private def unwrap(jv: JValue): Any = {
    jv match {
      case JArray(values) => values map unwrap
      case JBool(value) => value
      case JDouble(num) => num
      case JObject(fields) => Map(fields.map(f => f.name -> unwrap(f.value)): _*)
      case JNull => null
      case JString(s) => s
      case unknown =>
        logger.warn(s"Unrecognized typed '$unknown' (${unknown.getClass.getName})")
        null
    }
  }

  /**
    * Json Field-Value Equality Condition
    * @author lawrence.daniels@gmail.com
    */
  case class JsonEQ(decoder: MessageDecoder[JValue], field: String, value: String) extends Condition {
    override def satisfies(message: Array[Byte], key: Array[Byte]): Boolean = {
      decoder.decode(message) match {
        case Success(js) =>
          js \ field match {
            case JNull => value == null
            case JBool(b) => Try(value.toBoolean).toOption.contains(b)
            case JDouble(n) => Try(value.toDouble).toOption.contains(n)
            case JString(s) => s == value
            case x =>
              throw new IllegalStateException(s"Value '$x' (${Option(x).map(_.getClass.getName).orNull}) for field '$field' was not recognized")
          }
        case Failure(e) => false
      }
    }

    override def toString = s"$field == '$value'"
  }

  /**
    * Json Field-Value Greater-Than Condition
    * @author lawrence.daniels@gmail.com
    */
  case class JsonGT(decoder: MessageDecoder[JValue], field: String, value: String) extends Condition {
    override def satisfies(message: Array[Byte], key: Array[Byte]): Boolean = {
      decoder.decode(message) match {
        case Success(js) =>
          js \ field match {
            case JNull => false
            case JBool(b) => false
            case JDouble(n) => Try(value.toDouble).toOption.exists(n > _)
            case JString(s) => s > value
            case x => throw new IllegalStateException(s"Value '$x' for field '$field' was not recognized")
          }
        case Failure(e) => false
      }
    }

    override def toString = s"$field > $value'"
  }

  /**
    * Json Field-Value Greater-Than-Or-Equal Condition
    * @author lawrence.daniels@gmail.com
    */
  case class JsonGE(decoder: MessageDecoder[JValue], field: String, value: String) extends Condition {
    override def satisfies(message: Array[Byte], key: Array[Byte]): Boolean = {
      decoder.decode(message) match {
        case Success(js) =>
          js \ field match {
            case JNull => false
            case JBool(b) => false
            case JDouble(n) => Try(value.toDouble).toOption.exists(n >= _)
            case JString(s) => s >= value
            case x => throw new IllegalStateException(s"Value '$x' for field '$field' was not recognized")
          }
        case Failure(e) => false
      }
    }

    override def toString = s"$field >= '$value'"
  }

  /**
    * Json Field-Value Less-Than Condition
    * @author lawrence.daniels@gmail.com
    */
  case class JsonLT(decoder: MessageDecoder[JValue], field: String, value: String) extends Condition {
    override def satisfies(message: Array[Byte], key: Array[Byte]): Boolean = {
      decoder.decode(message) match {
        case Success(js) =>
          js \ field match {
            case JNull => false
            case JBool(b) => false
            case JDouble(n) => Try(value.toDouble).toOption.exists(n < _)
            case JString(s) => s < value
            case x => throw new IllegalStateException(s"Value '$x' for field '$field' was not recognized")
          }
        case Failure(e) => false
      }
    }

    override def toString = s"$field < '$value'"
  }

  /**
    * Json Field-Value Less-Than-Or-Equal Condition
    * @author lawrence.daniels@gmail.com
    */
  case class JsonLE(decoder: MessageDecoder[JValue], field: String, value: String) extends Condition {
    override def satisfies(message: Array[Byte], key: Array[Byte]): Boolean = {
      decoder.decode(message) match {
        case Success(js) =>
          js \ field match {
            case JNull => false
            case JBool(b) => false
            case JDouble(n) => Try(value.toDouble).toOption.exists(n <= _)
            case JString(s) => s <= value
            case x => throw new IllegalStateException(s"Value '$x' for field '$field' was not recognized")
          }
        case Failure(e) => false
      }
    }

    override def toString = s"$field <= '$value'"
  }

  /**
    * Json Field-Value Inequality Condition
    * @author lawrence.daniels@gmail.com
    */
  case class JsonNE(decoder: MessageDecoder[JValue], field: String, value: String) extends Condition {
    override def satisfies(message: Array[Byte], key: Array[Byte]): Boolean = {
      decoder.decode(message) match {
        case Success(js) =>
          js \ field match {
            case JNull => value != null
            case JBool(b) => !Try(value.toBoolean).toOption.contains(b)
            case JDouble(n) => !Try(value.toDouble).toOption.contains(n)
            case JString(s) => s != value
            case x => throw new IllegalStateException(s"Value '$x' for field '$field' was not recognized")
          }
        case Failure(e) => false
      }
    }

    override def toString = s"$field != '$value'"
  }

}