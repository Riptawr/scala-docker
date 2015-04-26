package se.marcuslonnberg.scaladocker.remote.api

import java.io._

import org.kamranzafar.jtar.{TarEntry, TarOutputStream}

import scala.annotation.tailrec

object TarArchive {
  def apply(inDir: File, out: File): File = {
    require(inDir.isDirectory, "inDir must be a directory")

    def traverse(directory: File, dirPath: String = ""): Map[String, File] = {
      val files = directory.listFiles() match {
        case null =>
          Map.empty[String, File]
        case files =>
          val nextPath = dirPath + directory.getName + "/"
          files.flatMap { file =>
            val path = dirPath + file.getName
            if (file.isDirectory) {
              traverse(file, nextPath)
            } else {
              Map(path -> file)
            }
          }.toMap
      }
      files + ((dirPath + directory.getName) -> directory)
    }

    val files = traverse(inDir)
    apply(files, out)
  }

  def apply(files: Map[String, File], outFile: File): File = {
    val entries = files.map {
      case (path, file) =>
        createEntry(path, file)
    }
    apply(entries, outFile)
  }

  private[api] def apply(entries: Iterable[TarEntry], outFile: File): File = {
    val tarFile = new FileOutputStream(outFile)
    val tarStream = new TarOutputStream(new BufferedOutputStream(tarFile))

    val buffer = Array.ofDim[Byte](2048)

    def copyFile(file: File) = {
      val fileStream = new BufferedInputStream(new FileInputStream(file))

      @tailrec
      def copy(input: InputStream) {
        val len = input.read(buffer)
        if (len != -1) {
          tarStream.write(buffer, 0, len)
          copy(input)
        }
      }
      copy(fileStream)

      fileStream.close()
    }

    entries.foreach { entry =>
      tarStream.putNextEntry(entry)

      if (entry.getFile.isFile)
        copyFile(entry.getFile)

      tarStream.flush()
    }

    tarStream.close()
    tarFile.close()
    outFile
  }

  private[api] def createEntry(path: String, file: File) = {
    val entry = new TarEntry(file, path)
    entry.setUserName("")
    entry.setGroupName("")
    entry.setIds(0, 0)
    entry
  }
}
