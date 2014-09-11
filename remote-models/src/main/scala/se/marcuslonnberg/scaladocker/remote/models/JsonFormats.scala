package se.marcuslonnberg.scaladocker.remote.models

import com.github.nscala_time.time.Imports._
import org.joda.time.format.ISODateTimeFormat
import org.json4s.JsonAST.JObject
import org.json4s._
import org.json4s.JsonDSL._
import se.marcuslonnberg.scaladocker.remote.models.json._

object JsonFormatHelpers {
  def extractField[T](fieldName: String)(implicit obj: JObject, manifest: Manifest[T], formats: Formats): T = {
    try {
      (obj \ fieldName).extract[T]
    } catch {
      case e: RuntimeException =>
        throw new MappingException(s"Could not find field '$fieldName' or extract a value of type: $manifest", e)
    }
  }

  def extractFieldOpt[T](fieldName: String)(implicit obj: JObject, manifest: Manifest[T], formats: Formats): Option[T] = {
    (obj \ fieldName).extractOpt[T]
  }
}

object JsonFormats {

  def lowerCamelCase(name: String) = name.head.toLower +: name.substring(1)

  def fromUpperCamelCase: PartialFunction[JField, JField] = {
    case JField(name, value) if name.nonEmpty =>
      JField(lowerCamelCase(name), value)
  }

  def toUpperCamelCase: PartialFunction[(String, Any), Option[(String, Any)]] = {
    case (name, value) if name.nonEmpty =>
      val upperCamelCase = name.head.toUpper +: name.substring(1)
      Some((upperCamelCase, value))
  }

  def camelCaseFieldSerializer[T](serializer: PartialFunction[(String, Any), Option[(String, Any)]] = Map(),
                                  deserializer: PartialFunction[JField, JField] = Map())(implicit mf: Manifest[T]) = {
    FieldSerializer(serializer orElse toUpperCamelCase, deserializer orElse fromUpperCamelCase)
  }

  val ImageNameFormat = new CustomSerializer[ImageName](formats => ( {
    case JString(id) => ImageName(id)
  }, {
    case name: ImageName => JString(name.toString)
  }))

  val ImageIdFormat = new CustomSerializer[ImageId](formats => ( {
    case JString(id) => ImageId(id)
  }, {
    case ImageId(id) => JString(id)
  }))

  val ContainerIdFormat = new CustomSerializer[ContainerId](formats => ( {
    case JString(id) => ContainerId(id)
  }, {
    case ContainerId(id) => JString(id)
  }))

  val ContainerLinkFormat = new CustomSerializer[ContainerLink](formats => ( {
    case JString(ContainerLink(link)) => link
  }, {
    case link: ContainerLink => JString(link.mkString)
  }))

  val DateTimeFormat = new CustomSerializer[DateTime](formats => ( {
    case JInt(seconds) => new DateTime((seconds * 1000).toLong)
    case JString(date) => ISODateTimeFormat.dateTime().parseDateTime(date)
  }, {
    case dateTime: DateTime => JInt(dateTime.millis / 1000)
  }))

  val OptionStringFormat = new CustomSerializer[Option[String]](formats => ( {
    case JString("") => None
    case JString("0001-01-01T00:00:00Z") => None
    case JString(string) => Some(string)
  }, {
    case None => JNothing
    case Some(string: String) => JString(string)
  }))

  def deserializePortBindings(value: JValue)(implicit formats: Formats): Map[Port, Seq[PortBinding]] = {
    value match {
      case JObject(bindings) =>
        bindings.obj.map {
          case (Port(port), value) =>
            val list = value.extractOrElse[List[PortBinding]](List.empty[PortBinding])
            port -> list
        }.toMap
      case _ =>
        Map.empty
    }
  }

  def serializePortBindings(bindings: Map[Port, Seq[PortBinding]])(implicit formats: Formats) = {
    val fields = bindings.map {
      case (port, bindings) =>
        port.toString -> Extraction.decompose(bindings)
    }.toList
    JObject(fields)
  }

  def deserializeVolumes(volumesRaw: JObject, volumesRW: JObject): List[Volume] = {
    volumesRaw.obj.map {
      case (hostPath, JString(containerPath)) =>
        val rw = volumesRW.values.get(containerPath).exists {
          case value: Boolean => value
          case _ => false
        }
        Volume(containerPath, hostPath, rw)
      case _ => sys.error("Could not parse volumes")
    }
  }

  def serializeVolumes(volumes: List[Volume]): (Map[String, String], Map[String, Boolean]) = {
    val (vol, volRw) = volumes.map { volume =>
      (volume.containerPath -> volume.hostPath,
        volume.containerPath -> volume.rw)
    }.unzip

    (vol.toMap, volRw.toMap)
  }

  def apply(): Formats = DefaultFormats +
    camelCaseFieldSerializer[Image]() +
    camelCaseFieldSerializer[CreateContainerResponse]() +
    camelCaseFieldSerializer[ContainerState]() +
    camelCaseFieldSerializer[RestartPolicy]() +
    camelCaseFieldSerializer[DeviceMapping]() +
    ImageNameFormat +
    ImageIdFormat +
    ContainerIdFormat +
    ContainerLinkFormat +
    DateTimeFormat +
    OptionStringFormat +
    NetworkSettingsSerializer +
    HostConfigSerializer +
    ContainerInfoSerializer +
    ContainerConfigSerializer +
    ContainerStatusSerializer +
    PortBindingSerializer
}
