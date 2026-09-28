package everyui.monaco

import fabric.*
import fabric.io.{JsonFormatter, JsonParser}

/** The JSON the bridge carries, built and read: objects that leave out what is not set, as Monaco reads a missing
  * option as "leave it", and the text the embed channel carries. */
object Wire {
  /** An object of the fields that are set (not null). */
  def obj(fields: (String, Json)*): Json = fabric.obj(fields.filterNot(_._2.isNull)*)

  /** `value` as JSON, or null to be left out. */
  def opt[T](value: Option[T])(f: T => Json): Json = value.map(f).getOrElse(Null)

  /** An object's fields merged over another's, the second's winning. */
  def merge(base: Json, over: Json): Json =
    if (base.isObj && over.isObj) fabric.obj((base.asMap ++ over.asMap).toSeq*) else over

  def text(json: Json): String = JsonFormatter.Compact(json)
  def parse(text: String): Json = JsonParser(text)

  def string(json: Json, key: String): String = json.get(key).filter(_.isStr).map(_.asString).getOrElse("")
  def int(json: Json, key: String): Int = json.get(key).filter(_.isNum).map(_.asInt).getOrElse(0)
  def double(json: Json, key: String): Double = json.get(key).filter(_.isNum).map(_.asDouble).getOrElse(0.0)
  def bool(json: Json, key: String): Boolean = json.get(key).filter(_.isBool).exists(_.asBoolean)
}
